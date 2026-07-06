package com.meis.saas.maintain.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/maintain/plan")
@RequiredArgsConstructor
public class MaintenancePlanController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM maintenance_plan WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "maintain", description = "保存保养计划")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM maintenance_plan WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE maintenance_plan SET plan_name=?, template_id=?::uuid, device_id=?::uuid, dept_id=?::uuid,
                maintenance_level=?, cycle_type=?, cycle_value=?, next_due_date=?, last_maintained_at=?, status=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("plan_name"), body.get("template_id"), body.get("device_id"), body.get("dept_id"),
                    body.get("maintenance_level"), body.get("cycle_type"), body.get("cycle_value"),
                    body.get("next_due_date"), body.get("last_maintained_at"), body.getOrDefault("status", "active"),
                    body.get("remark"), id);
        } else {
            jdbc.update("""
                INSERT INTO maintenance_plan (id, plan_code, plan_name, template_id, device_id, dept_id, maintenance_level,
                cycle_type, cycle_value, next_due_date, status) VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?,?,?,?,?)
                """, id, body.getOrDefault("plan_code", "MP" + System.currentTimeMillis()), body.get("plan_name"),
                    body.get("template_id"), body.get("device_id"), body.get("dept_id"),
                    body.getOrDefault("maintenance_level", "daily"), body.get("cycle_type"), body.get("cycle_value"),
                    body.get("next_due_date"), body.getOrDefault("status", "active"));
        }
        return get(id);
    }

    @PostMapping("/generate")
    @Transactional
    @OperationLog(module = "maintain", description = "从模板生成保养计划")
    public Result<List<Map<String, Object>>> generate(@RequestBody Map<String, Object> body) {
        UUID templateId = UUID.fromString(body.get("templateId").toString());
        var template = jdbc.queryForList("SELECT * FROM maintenance_template WHERE id = ?::uuid", templateId);
        if (template.isEmpty()) throw new BizException(404, "template not found");
        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.getOrDefault("deviceIds", List.of());
        List<Map<String, Object>> created = new ArrayList<>();
        for (String deviceId : deviceIds) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO maintenance_plan (id, plan_code, plan_name, template_id, device_id, maintenance_level, cycle_type, cycle_value, next_due_date, status)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?,?,?,?)
                """, id, "MP" + System.nanoTime(), template.get(0).get("template_name") + "计划",
                    templateId, deviceId, template.get(0).get("maintenance_level"),
                    body.getOrDefault("cycle_type", "month"), body.getOrDefault("cycle_value", 1),
                    body.getOrDefault("next_due_date", LocalDate.now().plusMonths(1)), "active");
            created.add(jdbc.queryForList("SELECT * FROM maintenance_plan WHERE id = ?::uuid", id).get(0));
        }
        return Result.ok(created);
    }

    @PostMapping("/{id}/activate")
    @OperationLog(module = "maintain", description = "激活保养计划")
    public Result<Map<String, Object>> activate(@PathVariable UUID id) {
        jdbc.update("UPDATE maintenance_plan SET status = 'active', updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }

    @GetMapping("/due")
    public Result<List<Map<String, Object>>> due() {
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM maintenance_plan WHERE status = 'active' AND next_due_date <= CURRENT_DATE + 7 ORDER BY next_due_date"));
    }
}
