package com.meis.saas.maintain.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/maintain/device")
@RequiredArgsConstructor
public class MaintenanceDeviceController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) Boolean maintainOnly) {
        StringBuilder where = new StringBuilder(" WHERE d.is_active = true ");
        List<Object> args = new ArrayList<>();
        if (Boolean.TRUE.equals(maintainOnly)) {
            where.append(" AND d.is_maintain_device = true ");
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (d.device_code ILIKE ? OR d.device_name ILIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM medical_device d" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT d.id, d.device_code, d.device_name, d.specification, d.is_maintain_device,
                       dept.dept_name, p.id AS plan_id, p.plan_name, p.next_due_date, p.approval_status AS plan_approval_status
                FROM medical_device d
                LEFT JOIN department dept ON dept.id = d.dept_id
                LEFT JOIN maintenance_plan p ON p.device_id = d.id AND p.status = 'active'
                """ + where + " ORDER BY d.device_code LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @PostMapping("/toggle")
    @Transactional
    @OperationLog(module = "maintain", description = "切换保养设备标记")
    public Result<Void> toggle(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.getOrDefault("deviceIds", List.of());
        boolean flag = Boolean.TRUE.equals(body.get("is_maintain_device"));
        for (String deviceId : deviceIds) {
            jdbc.update("UPDATE medical_device SET is_maintain_device=?, updated_at=NOW() WHERE id=?::uuid", flag, deviceId);
        }
        return Result.ok(null);
    }

    @PostMapping("/generate-execution")
    @Transactional
    @OperationLog(module = "maintain", description = "批量生成保养执行单")
    public Result<List<Map<String, Object>>> generateExecution(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> planIds = (List<String>) body.getOrDefault("planIds", List.of());
        List<Map<String, Object>> created = new ArrayList<>();
        for (String planId : planIds) {
            created.add(generateOneExecution(UUID.fromString(planId), body));
        }
        return Result.ok(created);
    }

    private Map<String, Object> generateOneExecution(UUID planId, Map<String, Object> body) {
        var plan = jdbc.queryForList("""
                SELECT p.*, t.template_name, t.maintenance_level_id
                FROM maintenance_plan p
                LEFT JOIN maintenance_template t ON t.id = p.template_id
                WHERE p.id = ?::uuid
                """, planId);
        if (plan.isEmpty()) return Map.of("planId", planId, "error", "plan not found");
        Map<String, Object> p = plan.get(0);
        if (!"approved".equals(p.get("approval_status"))) {
            return Map.of("planId", planId, "error", "plan not approved");
        }
        UUID execId = UUID.randomUUID();
        String execNo = MaintenanceExecutionController.nextNo();
        jdbc.update("""
                INSERT INTO maintenance_execution (id, execution_no, plan_id, template_id, maintenance_level_id,
                    planned_date, assigned_engineer_id, status, created_by)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?,?::uuid,?,?)
                """, execId, execNo, planId, p.get("template_id"), p.get("maintenance_level_id"),
                body.getOrDefault("planned_date", p.get("next_due_date")),
                p.get("assigned_engineer_id"), "pending", body.get("created_by"));

        UUID itemId = UUID.randomUUID();
        var device = jdbc.queryForList("SELECT device_code, device_name, dept_id FROM medical_device WHERE id=?::uuid", p.get("device_id"));
        String deviceCode = device.isEmpty() ? null : (String) device.get(0).get("device_code");
        String deviceName = device.isEmpty() ? null : (String) device.get(0).get("device_name");
        Object deptId = device.isEmpty() ? p.get("dept_id") : device.get(0).get("dept_id");
        jdbc.update("""
                INSERT INTO maintenance_execution_item (id, execution_id, device_id, device_code, device_name, dept_id, plan_id, status)
                VALUES (?::uuid,?::uuid,?::uuid,?,?,?::uuid,?::uuid,'pending')
                """, itemId, execId, p.get("device_id"), deviceCode, deviceName, deptId, planId);

        var templateItems = jdbc.queryForList("""
                SELECT * FROM maintenance_template_item WHERE template_id = ?::uuid ORDER BY sort_order, created_at
                """, p.get("template_id"));
        if (templateItems.isEmpty() && p.get("template_id") != null) {
            templateItems = jdbc.queryForList(
                    "SELECT id AS template_item_id, item_name, item_content FROM maintenance_template_item WHERE template_id=?::uuid",
                    p.get("template_id"));
        }
        for (Map<String, Object> ti : templateItems) {
            jdbc.update("""
                    INSERT INTO maintenance_execution_result (id, execution_item_id, template_item_id, item_name, item_content, result_status)
                    VALUES (?::uuid,?::uuid,?::uuid,?,?,?)
                    """, UUID.randomUUID(), itemId, ti.get("id"), ti.get("item_name"), ti.get("item_content"), "pending");
        }
        return jdbc.queryForList("SELECT * FROM maintenance_execution WHERE id=?::uuid", execId).get(0);
    }
}
