package com.meis.saas.qc.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.code.DailyBizNoSupport;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.qc.metrology.MetrologyExecutionGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/metrology/plan")
@RequiredArgsConstructor
public class MetrologyPlanController {
    private final JdbcTemplate jdbc;
    private final MetrologyExecutionGenerator executionGenerator;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT p.*, t.template_name, c.category_name, o.org_name, d.device_name, d.device_code, dept.dept_name
                FROM metrology_plan p
                LEFT JOIN metrology_template t ON t.id = p.template_id
                LEFT JOIN metrology_category c ON c.id = p.category_id
                LEFT JOIN metrology_org o ON o.id = p.org_id
                LEFT JOIN medical_device d ON d.id = p.device_id
                LEFT JOIN department dept ON dept.id = d.dept_id
                WHERE p.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_plan", "p"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "metrology", description = "保存计量计划")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM metrology_plan WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_plan", null), id).isEmpty();
        if (exists) {
            // OPS.14：更新不改 plan_code
            jdbc.update("""
                UPDATE metrology_plan SET plan_name=?, template_id=?::uuid, device_id=?::uuid, category_id=?::uuid, org_id=?::uuid,
                cycle_days=?, next_due_date=?, last_calibrated_at=?, status=?, approval_status=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("plan_name"), body.get("template_id"), body.get("device_id"), body.get("category_id"),
                    body.get("org_id"), body.get("cycle_days"), body.get("next_due_date"), body.get("last_calibrated_at"),
                    body.getOrDefault("status", "active"), body.getOrDefault("approval_status", "draft"),
                    body.get("remark"), id);
        } else {
            String planCode = DailyBizNoSupport.next(jdbc, "metrology_plan", "plan_code", "JL-");
            jdbc.update("""
                INSERT INTO metrology_plan (id, plan_code, plan_name, template_id, device_id, category_id, org_id,
                cycle_days, next_due_date, status, approval_status, created_by, remark)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?)
                """, id, planCode, body.get("plan_name"),
                    body.get("template_id"), body.get("device_id"), body.get("category_id"), body.get("org_id"),
                    body.get("cycle_days"), body.get("next_due_date"), body.getOrDefault("status", "active"),
                    body.getOrDefault("approval_status", "draft"), body.get("created_by"), body.get("remark"));
        }
        return get(id);
    }

    @PostMapping("/{id}/approve")
    @Transactional
    @OperationLog(module = "metrology", description = "审核计量计划")
    public Result<Map<String, Object>> approve(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String action = String.valueOf(body.getOrDefault("action", "approve"));
        String status = "reject".equals(action) ? "rejected" : "approved";
        jdbc.update("""
                UPDATE metrology_plan SET approval_status=?, approved_by=?::uuid, approved_at=NOW(),
                status=CASE WHEN ?='approved' THEN 'active' ELSE status END, updated_at=NOW()
                WHERE id=?::uuid
                """, status, body.get("approved_by"), status, id);
        return get(id);
    }

    @PostMapping("/{id}/generate-execution")
    @Transactional
    @OperationLog(module = "metrology", description = "从计划生成计量执行")
    public Result<Map<String, Object>> generateExecution(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body != null ? new HashMap<>(body) : new HashMap<>();
        req.put("planIds", List.of(id.toString()));
        var list = executionGenerator.generateBatch(req);
        if (list == null || list.isEmpty()) throw new BizException(400, "generate failed");
        Object err = list.get(0).get("error");
        if (err != null) throw new BizException(400, err.toString());
        return Result.ok(list.get(0));
    }

    @PostMapping("/generate")
    @Transactional
    @OperationLog(module = "metrology", description = "从模板生成计量计划")
    public Result<List<Map<String, Object>>> generate(@RequestBody Map<String, Object> body) {
        UUID templateId = UUID.fromString(body.get("templateId").toString());
        var template = jdbc.queryForList(
                "SELECT * FROM metrology_template WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_template", null), templateId);
        if (template.isEmpty()) throw new BizException(404, "template not found");
        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.getOrDefault("deviceIds", List.of());
        List<Map<String, Object>> created = new ArrayList<>();
        for (String deviceId : deviceIds) {
            UUID id = UUID.randomUUID();
            String planCode = DailyBizNoSupport.next(jdbc, "metrology_plan", "plan_code", "JL-");
            jdbc.update("""
                INSERT INTO metrology_plan (id, plan_code, plan_name, template_id, device_id, category_id, org_id,
                    cycle_days, next_due_date, status, approval_status, created_by)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?::uuid,?,?,?,?,?)
                """, id, planCode, template.get(0).get("template_name") + "计划",
                    templateId, deviceId, template.get(0).get("category_id"), body.get("org_id"),
                    body.getOrDefault("cycle_days", 365),
                    body.getOrDefault("next_due_date", LocalDate.now().plusDays(365)),
                    "active", "draft", body.get("created_by"));
            created.add(jdbc.queryForList(
                    "SELECT * FROM metrology_plan WHERE id = ?::uuid "
                            + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_plan", null), id).get(0));
        }
        return Result.ok(created);
    }

    @GetMapping("/due")
    public Result<List<Map<String, Object>>> due() {
        return Result.ok(jdbc.queryForList("""
                SELECT p.*, d.device_name, d.device_code, o.org_name
                FROM metrology_plan p
                LEFT JOIN medical_device d ON d.id = p.device_id
                LEFT JOIN metrology_org o ON o.id = p.org_id
                WHERE p.status = 'active' AND p.approval_status = 'approved'
                  AND p.next_due_date <= CURRENT_DATE + 30
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_plan", "p") + """
                ORDER BY p.next_due_date
                """));
    }
}
