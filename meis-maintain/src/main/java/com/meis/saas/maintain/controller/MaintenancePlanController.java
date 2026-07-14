package com.meis.saas.maintain.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.maintain.maintain.MaintenanceExecutionGenerator;
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
    private final MaintenanceExecutionGenerator executionGenerator;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT p.*, t.template_name, d.device_name, d.device_code, dept.dept_name
                FROM maintenance_plan p
                LEFT JOIN maintenance_template t ON t.id = p.template_id
                LEFT JOIN medical_device d ON d.id = p.device_id
                LEFT JOIN department dept ON dept.id = p.dept_id
                WHERE p.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_plan", "p"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "maintain", description = "保存保养计划")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM maintenance_plan WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_plan", null), id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE maintenance_plan SET plan_name=?, template_id=?::uuid, device_id=?::uuid, dept_id=?::uuid,
                maintenance_level=?, cycle_type=?, cycle_value=?, cycle_days=?, next_due_date=?, last_maintained_at=?,
                status=?, approval_status=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("plan_name"), body.get("template_id"), body.get("device_id"), body.get("dept_id"),
                    body.get("maintenance_level"), body.get("cycle_type"), body.get("cycle_value"),
                    body.get("cycle_days"), body.get("next_due_date"), body.get("last_maintained_at"),
                    body.getOrDefault("status", "active"), body.getOrDefault("approval_status", "draft"),
                    body.get("remark"), id);
        } else {
            jdbc.update("""
                INSERT INTO maintenance_plan (id, plan_code, plan_name, template_id, device_id, dept_id, maintenance_level,
                cycle_type, cycle_value, cycle_days, next_due_date, status, approval_status, created_by, remark)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?,?,?)
                """, id, body.getOrDefault("plan_code", "MP" + System.currentTimeMillis()), body.get("plan_name"),
                    body.get("template_id"), body.get("device_id"), body.get("dept_id"),
                    body.getOrDefault("maintenance_level", "L1"), body.get("cycle_type"), body.get("cycle_value"),
                    body.get("cycle_days"), body.get("next_due_date"), body.getOrDefault("status", "active"),
                    body.getOrDefault("approval_status", "draft"), body.get("created_by"), body.get("remark"));
        }
        return get(id);
    }

    @PostMapping("/{id}/approve")
    @Transactional
    @OperationLog(module = "maintain", description = "审核保养计划")
    public Result<Map<String, Object>> approve(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String action = String.valueOf(body.getOrDefault("action", "approve"));
        String status = "reject".equals(action) ? "rejected" : "approved";
        jdbc.update("""
                UPDATE maintenance_plan SET approval_status=?, approved_by=?::uuid, approved_at=NOW(),
                status=CASE WHEN ?='approved' THEN 'active' ELSE status END, updated_at=NOW()
                WHERE id=?::uuid
                """, status, body.get("approved_by"), status, id);
        return get(id);
    }

    @PostMapping("/{id}/generate-execution")
    @Transactional
    @OperationLog(module = "maintain", description = "从计划生成保养执行")
    public Result<Map<String, Object>> generateExecution(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body != null ? body : Map.of();
        req = new HashMap<>(req);
        req.put("planIds", List.of(id.toString()));
        var list = executionGenerator.generateBatch(req);
        if (list == null || list.isEmpty()) throw new BizException(400, "generate failed");
        Object err = list.get(0).get("error");
        if (err != null) throw new BizException(400, err.toString());
        return Result.ok(list.get(0));
    }

    @PostMapping("/generate")
    @Transactional
    @OperationLog(module = "maintain", description = "从模板生成保养计划")
    public Result<List<Map<String, Object>>> generate(@RequestBody Map<String, Object> body) {
        UUID templateId = UUID.fromString(body.get("templateId").toString());
        var template = jdbc.queryForList(
                "SELECT * FROM maintenance_template WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_template", null), templateId);
        if (template.isEmpty()) throw new BizException(404, "template not found");
        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.getOrDefault("deviceIds", List.of());
        List<Map<String, Object>> created = new ArrayList<>();
        for (String deviceId : deviceIds) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO maintenance_plan (id, plan_code, plan_name, template_id, device_id, maintenance_level,
                    cycle_type, cycle_value, cycle_days, next_due_date, status, approval_status, created_by)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?,?,?,?,?,?,?)
                """, id, "MP" + System.nanoTime(), template.get(0).get("template_name") + "计划",
                    templateId, deviceId, template.get(0).get("maintenance_level"),
                    body.getOrDefault("cycle_type", "month"), body.getOrDefault("cycle_value", 1),
                    body.getOrDefault("cycle_days", 30),
                    body.getOrDefault("next_due_date", LocalDate.now().plusMonths(1)),
                    "active", "draft", body.get("created_by"));
            created.add(jdbc.queryForList(
                    "SELECT * FROM maintenance_plan WHERE id = ?::uuid "
                            + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_plan", null), id).get(0));
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
        return Result.ok(jdbc.queryForList("""
                SELECT p.*, d.device_name, d.device_code
                FROM maintenance_plan p
                LEFT JOIN medical_device d ON d.id = p.device_id
                WHERE p.status = 'active' AND p.approval_status = 'approved'
                  AND p.next_due_date <= CURRENT_DATE + 7
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_plan", "p") + """
                ORDER BY p.next_due_date
                """));
    }
}
