package com.meis.saas.maintain.controller;

import com.meis.saas.common.ops.OpsDevicePageSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 保养设备管理：台账过滤视图（附录 O.5 / OPS.4 / OPS.13）
 */
@RestController
@RequestMapping("/api/maintain/device")
@RequiredArgsConstructor
public class MaintenanceDeviceController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String device_status,
            @RequestParam(required = false) String dept_id,
            @RequestParam(required = false) String manage_dept_id,
            @RequestParam(required = false) String device_code,
            @RequestParam(required = false) String device_name,
            @RequestParam(required = false) String serial_number,
            @RequestParam(required = false) String specification,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Integer due_within_days) {
        return Result.ok(OpsDevicePageSupport.page(
                jdbc, OpsDevicePageSupport.MAINTAIN, query,
                device_status, dept_id, manage_dept_id,
                device_code, device_name, serial_number, specification, model, brand,
                due_within_days));
    }

    @GetMapping("/{deviceId}/plans")
    public Result<List<Map<String, Object>>> plans(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT i.*, p.plan_name, p.plan_no, p.approval_status, p.status AS plan_status, p.template_id, p.template_name
                FROM maintenance_plan_item i
                INNER JOIN maintenance_plan p ON p.id = i.plan_id
                WHERE i.device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_plan_item", "i")
                + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_plan", "p")
                + " ORDER BY i.next_due_date NULLS LAST", deviceId));
    }

    @GetMapping("/{deviceId}/executions")
    public Result<List<Map<String, Object>>> executions(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT ei.*, e.execution_no, e.status AS execution_status, e.source_type, e.plan_no, e.planned_date
                FROM maintenance_execution_item ei
                INNER JOIN maintenance_execution e ON e.id = ei.execution_id
                WHERE ei.device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_execution_item", "ei")
                + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_execution", "e")
                + " ORDER BY e.created_at DESC", deviceId));
    }
}
