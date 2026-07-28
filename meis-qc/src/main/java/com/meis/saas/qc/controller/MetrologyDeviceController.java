package com.meis.saas.qc.controller;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 计量按设备查询（AST-UI-14：台账查看「计量计划 / 计量记录」关联）
 */
@RestController
@RequestMapping("/api/metrology/device")
@RequiredArgsConstructor
public class MetrologyDeviceController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{deviceId}/plans")
    public Result<List<Map<String, Object>>> plans(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT p.*, o.org_name, t.template_name, c.category_name
                FROM metrology_plan p
                LEFT JOIN metrology_org o ON o.id = p.org_id
                LEFT JOIN metrology_template t ON t.id = p.template_id
                LEFT JOIN metrology_category c ON c.id = p.category_id
                WHERE p.device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_plan", "p")
                + " ORDER BY p.next_due_date NULLS LAST, p.created_at DESC", deviceId));
    }

    @GetMapping("/{deviceId}/executions")
    public Result<List<Map<String, Object>>> executions(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT ei.*, e.execution_no, e.status AS execution_status, e.plan_id, e.planned_date,
                       o.org_name, t.template_name
                FROM metrology_execution_item ei
                INNER JOIN metrology_execution e ON e.id = ei.execution_id
                LEFT JOIN metrology_org o ON o.id = e.org_id
                LEFT JOIN metrology_template t ON t.id = e.template_id
                WHERE ei.device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution_item", "ei")
                + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution", "e")
                + " ORDER BY e.created_at DESC", deviceId));
    }
}
