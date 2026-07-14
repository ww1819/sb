package com.meis.saas.qc.controller;

import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/metrology/query")
@RequiredArgsConstructor
public class MetrologyQueryController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) String resultStatus) {
        StringBuilder where = new StringBuilder(" WHERE ei.status = 'completed' ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution_item", "ei"));
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution", "e"));
        List<Object> args = new ArrayList<>();
        if (deviceCode != null && !deviceCode.isBlank()) {
            where.append(" AND ei.device_code ILIKE ? ");
            args.add("%" + deviceCode.trim() + "%");
        }
        if (resultStatus != null && !resultStatus.isBlank()) {
            where.append(" AND ei.overall_result = ? ");
            args.add(resultStatus);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (ei.device_name ILIKE ? OR e.execution_no ILIKE ? OR ei.certificate_no ILIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM metrology_execution_item ei
                JOIN metrology_execution e ON e.id = ei.execution_id
                """ + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT ei.id, ei.device_code, ei.device_name, ei.overall_result, ei.certificate_no, ei.cost,
                       ei.updated_at AS completed_at, e.execution_no, e.planned_date,
                       t.template_name, c.category_name, o.org_name, dept.dept_name
                FROM metrology_execution_item ei
                JOIN metrology_execution e ON e.id = ei.execution_id
                LEFT JOIN metrology_template t ON t.id = e.template_id
                LEFT JOIN metrology_category c ON c.id = e.category_id
                LEFT JOIN metrology_org o ON o.id = e.org_id
                LEFT JOIN department dept ON dept.id = ei.dept_id
                """ + where + " ORDER BY ei.updated_at DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{itemId}")
    public Result<Map<String, Object>> detail(@PathVariable UUID itemId) {
        var rows = jdbc.queryForList("""
                SELECT ei.*, e.execution_no, e.planned_date, t.template_name, c.category_name, o.org_name
                FROM metrology_execution_item ei
                JOIN metrology_execution e ON e.id = ei.execution_id
                LEFT JOIN metrology_template t ON t.id = e.template_id
                LEFT JOIN metrology_category c ON c.id = e.category_id
                LEFT JOIN metrology_org o ON o.id = e.org_id
                WHERE ei.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution_item", "ei")
                + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution", "e"), itemId);
        if (rows.isEmpty()) return Result.ok(Map.of());
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("results", jdbc.queryForList(
                "SELECT * FROM metrology_execution_result WHERE execution_item_id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution_result", null)
                        + " ORDER BY created_at", itemId));
        return Result.ok(result);
    }
}
