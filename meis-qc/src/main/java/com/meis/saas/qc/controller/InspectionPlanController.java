package com.meis.saas.qc.controller;

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
@RequestMapping("/api/inspect/plan")
@RequiredArgsConstructor
public class InspectionPlanController {
    private final JdbcTemplate jdbc;
    private final InspectionDeviceController deviceController;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT p.*, t.template_name, d.device_name, d.device_code, dept.dept_name
                FROM inspection_plan p
                LEFT JOIN inspection_template t ON t.id = p.template_id
                LEFT JOIN medical_device d ON d.id = p.device_id
                LEFT JOIN department dept ON dept.id = p.dept_id
                WHERE p.id = ?::uuid
                """, id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "inspect", description = "保存巡检计划")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM inspection_plan WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE inspection_plan SET plan_name=?, template_id=?::uuid, device_id=?::uuid, dept_id=?::uuid,
                inspection_type_id=?::uuid, inspection_type=?, cycle_days=?, next_due_date=?, last_inspected_at=?,
                start_date=?, end_date=?, frequency=?, status=?, approval_status=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("plan_name"), body.get("template_id"), body.get("device_id"), body.get("dept_id"),
                    body.get("inspection_type_id"), body.get("inspection_type"), body.get("cycle_days"),
                    body.get("next_due_date"), body.get("last_inspected_at"), body.get("start_date"),
                    body.get("end_date"), body.get("frequency"), body.getOrDefault("status", "active"),
                    body.getOrDefault("approval_status", "draft"), body.get("remark"), id);
        } else {
            jdbc.update("""
                INSERT INTO inspection_plan (id, plan_code, plan_name, template_id, device_id, dept_id, inspection_type_id,
                inspection_type, cycle_days, next_due_date, start_date, end_date, frequency, status, approval_status, created_by, remark)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?)
                """, id, body.getOrDefault("plan_code", "IP" + System.currentTimeMillis()), body.get("plan_name"),
                    body.get("template_id"), body.get("device_id"), body.get("dept_id"), body.get("inspection_type_id"),
                    body.get("inspection_type"), body.get("cycle_days"),
                    body.getOrDefault("next_due_date", body.get("plan_date")),
                    body.get("start_date"), body.get("end_date"), body.get("frequency"),
                    body.getOrDefault("status", "active"), body.getOrDefault("approval_status", "draft"),
                    body.get("created_by"), body.get("remark"));
        }
        return get(id);
    }

    @PostMapping("/{id}/approve")
    @Transactional
    @OperationLog(module = "inspect", description = "审核巡检计划")
    public Result<Map<String, Object>> approve(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String action = String.valueOf(body.getOrDefault("action", "approve"));
        String status = "reject".equals(action) ? "rejected" : "approved";
        jdbc.update("""
                UPDATE inspection_plan SET approval_status=?, approved_by=?::uuid, approved_at=NOW(),
                status=CASE WHEN ?='approved' THEN 'active' ELSE status END, updated_at=NOW()
                WHERE id=?::uuid
                """, status, body.get("approved_by"), status, id);
        return get(id);
    }

    @PostMapping("/{id}/generate-execution")
    @Transactional
    @OperationLog(module = "inspect", description = "从计划生成巡检执行")
    public Result<Map<String, Object>> generateExecution(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body != null ? new HashMap<>(body) : new HashMap<>();
        req.put("planIds", List.of(id.toString()));
        var list = deviceController.generateExecution(req).getData();
        if (list == null || list.isEmpty()) throw new BizException(400, "generate failed");
        Object err = list.get(0).get("error");
        if (err != null) throw new BizException(400, err.toString());
        return Result.ok(list.get(0));
    }

    @PostMapping("/generate")
    @Transactional
    @OperationLog(module = "inspect", description = "从模板生成巡检计划")
    public Result<List<Map<String, Object>>> generate(@RequestBody Map<String, Object> body) {
        UUID templateId = UUID.fromString(body.get("templateId").toString());
        var template = jdbc.queryForList("SELECT * FROM inspection_template WHERE id = ?::uuid", templateId);
        if (template.isEmpty()) throw new BizException(404, "template not found");
        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.getOrDefault("deviceIds", List.of());
        List<Map<String, Object>> created = new ArrayList<>();
        for (String deviceId : deviceIds) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO inspection_plan (id, plan_code, plan_name, template_id, device_id, inspection_type_id,
                    cycle_days, next_due_date, status, approval_status, created_by)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?,?,?,?,?)
                """, id, "IP" + System.nanoTime(), template.get(0).get("template_name") + "计划",
                    templateId, deviceId, template.get(0).get("inspection_type_id"),
                    body.getOrDefault("cycle_days", 7),
                    body.getOrDefault("next_due_date", LocalDate.now().plusDays(7)),
                    "active", "draft", body.get("created_by"));
            created.add(jdbc.queryForList("SELECT * FROM inspection_plan WHERE id = ?::uuid", id).get(0));
        }
        return Result.ok(created);
    }

    @GetMapping("/due")
    public Result<List<Map<String, Object>>> due() {
        return Result.ok(jdbc.queryForList("""
                SELECT p.*, d.device_name, d.device_code
                FROM inspection_plan p
                LEFT JOIN medical_device d ON d.id = p.device_id
                WHERE p.status = 'active' AND p.approval_status = 'approved'
                  AND COALESCE(p.next_due_date, p.plan_date) <= CURRENT_DATE + 7
                ORDER BY COALESCE(p.next_due_date, p.plan_date)
                """));
    }
}
