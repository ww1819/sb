package com.meis.saas.maintain.controller;

import com.meis.saas.common.ops.OpsDevicePageSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

/**
 * 保养设备管理：台账过滤视图（附录 O.5 / OPS.4 / OPS.13 / PLT-UI-02 / OPS.16.11）
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
            @RequestParam(required = false) Integer due_within_days,
            @RequestParam(required = false) String category_id,
            @RequestParam(required = false) String asset_category_id,
            @RequestParam(required = false) String finance_category_id,
            @RequestParam(required = false) String category_kw,
            @RequestParam(required = false) String asset_category_kw,
            @RequestParam(required = false) String finance_category_kw,
            @RequestParam(required = false) String ids) {
        return Result.ok(OpsDevicePageSupport.page(
                jdbc, OpsDevicePageSupport.MAINTAIN, query,
                device_status, dept_id, manage_dept_id,
                device_code, device_name, serial_number, specification, model, brand,
                due_within_days,
                category_id, asset_category_id, finance_category_id,
                category_kw, asset_category_kw, finance_category_kw, ids));
    }

    @GetMapping("/export")
    public void export(
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
            @RequestParam(required = false) Integer due_within_days,
            @RequestParam(required = false) String category_id,
            @RequestParam(required = false) String asset_category_id,
            @RequestParam(required = false) String finance_category_id,
            @RequestParam(required = false) String category_kw,
            @RequestParam(required = false) String asset_category_kw,
            @RequestParam(required = false) String finance_category_kw,
            @RequestParam(required = false) String ids,
            HttpServletResponse resp) throws IOException {
        OpsDevicePageSupport.export(
                jdbc, OpsDevicePageSupport.MAINTAIN, resp, "ops_maintain_device_export.csv", query,
                device_status, dept_id, manage_dept_id,
                device_code, device_name, serial_number, specification, model, brand,
                due_within_days,
                category_id, asset_category_id, finance_category_id,
                category_kw, asset_category_kw, finance_category_kw, ids);
    }

    @GetMapping("/{deviceId}/plans")
    public Result<List<Map<String, Object>>> plans(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT i.*, p.id AS plan_id, p.plan_name, p.plan_no, p.approval_status, p.status AS plan_status,
                       p.template_id, p.template_name
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
