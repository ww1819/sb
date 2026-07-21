package com.meis.saas.qc.controller;

import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 巡检设备管理：台账过滤视图（附录 O.5 / OPS.4）——设备须出现在计划明细或执行明细中。
 */
@RestController
@RequestMapping("/api/inspect/device")
@RequiredArgsConstructor
public class InspectionDeviceController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query) {
        String md = SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d");
        String pi = SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan_item", "pi");
        String ei = SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_item", "ei");
        StringBuilder where = new StringBuilder(
                " WHERE ("
                + " EXISTS (SELECT 1 FROM inspection_plan_item pi WHERE pi.device_id = d.id " + pi + ")"
                + " OR EXISTS (SELECT 1 FROM inspection_execution_item ei WHERE ei.device_id = d.id " + ei + ")"
                + " )");
        where.append(md);
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (d.device_code ILIKE ? OR d.device_name ILIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM medical_device d " + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList(
                "SELECT d.id, d.device_code, d.device_name, d.device_status, d.dept_id, dept.dept_name,"
                + " d.is_inspection_device,"
                + " (SELECT COUNT(*) FROM inspection_plan_item pi WHERE pi.device_id=d.id " + pi + ") AS plan_count,"
                + " (SELECT COUNT(*) FROM inspection_execution_item ei WHERE ei.device_id=d.id " + ei + ") AS execution_item_count"
                + " FROM medical_device d"
                + " LEFT JOIN department dept ON dept.id = d.dept_id"
                + where + " ORDER BY d.device_code LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{deviceId}/plans")
    public Result<List<Map<String, Object>>> plans(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT i.*, p.plan_name, p.plan_no, p.approval_status, p.status AS plan_status, p.template_id, p.template_name
                FROM inspection_plan_item i
                INNER JOIN inspection_plan p ON p.id = i.plan_id
                WHERE i.device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan_item", "i")
                + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan", "p")
                + " ORDER BY i.next_due_date NULLS LAST", deviceId));
    }

    @GetMapping("/{deviceId}/executions")
    public Result<List<Map<String, Object>>> executions(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT ei.*, e.execution_no, e.status AS execution_status, e.source_type, e.plan_no, e.planned_date
                FROM inspection_execution_item ei
                INNER JOIN inspection_execution e ON e.id = ei.execution_id
                WHERE ei.device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_item", "ei")
                + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution", "e")
                + " ORDER BY e.created_at DESC", deviceId));
    }
}
