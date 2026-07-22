package com.meis.saas.qc.controller;

import com.meis.saas.common.audit.DocChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.code.DailyBizNoSupport;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.qc.inspect.InspectionExecutionGenerator;
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
    private final InspectionExecutionGenerator executionGenerator;
    private final DocChangeLogService docLog;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT p.*, t.template_name AS join_template_name, it.type_name AS inspection_type_name
                FROM inspection_plan p
                LEFT JOIN inspection_template t ON t.id = p.template_id
                LEFT JOIN inspection_type it ON it.id = p.inspection_type_id
                WHERE p.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan", "p"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        if (result.get("plan_no") == null && result.get("plan_code") != null) {
            result.put("plan_no", result.get("plan_code"));
        }
        if (result.get("template_name") == null) {
            result.put("template_name", result.get("join_template_name"));
        }
        result.put("items", jdbc.queryForList("""
                SELECT i.*, dept.dept_name
                FROM inspection_plan_item i
                LEFT JOIN department dept ON dept.id = i.dept_id
                WHERE i.plan_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan_item", "i")
                + " ORDER BY i.device_code NULLS LAST, i.created_at", id));
        return Result.ok(result);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "inspect", description = "保存巡检计划")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.get("id") != null ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM inspection_plan WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan", null), id).isEmpty();

        // OPS.14：计划单号系统生成；更新时保留原号，忽略客户端传入
        String planNo;
        if (exists) {
            var old = jdbc.queryForList(
                    "SELECT plan_no, plan_code FROM inspection_plan WHERE id = ?::uuid", id);
            planNo = old.isEmpty() ? null : firstNonBlank(old.get(0).get("plan_no"), old.get(0).get("plan_code"));
            if (planNo == null || planNo.isBlank()) {
                planNo = DailyBizNoSupport.next(jdbc, "inspection_plan", "plan_no", "IP-");
            }
        } else {
            planNo = DailyBizNoSupport.next(jdbc, "inspection_plan", "plan_no", "IP-");
        }
        String templateName = body.get("template_name") != null ? body.get("template_name").toString() : null;
        if ((templateName == null || templateName.isBlank()) && body.get("template_id") != null) {
            var t = jdbc.queryForList(
                    "SELECT template_name FROM inspection_template WHERE id = ?::uuid"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_template", null),
                    body.get("template_id"));
            if (!t.isEmpty()) templateName = Objects.toString(t.get(0).get("template_name"), null);
        }

        String userId = TenantContext.getUserId();
        Object assignedId = blankToNull(body.get("assigned_inspector_id"));
        if (assignedId == null) assignedId = blankToNull(body.get("assigned_user_id"));

        if (exists) {
            jdbc.update("""
                    UPDATE inspection_plan SET plan_name=?, plan_no=?, plan_code=COALESCE(?, plan_code),
                    template_id=?::uuid, template_name=?, inspection_type=?, inspection_type_id=?::uuid,
                    cycle_days=?, start_date=?, end_date=?, frequency=?,
                    status=?, approval_status=?, dept_id=?::uuid,
                    assigned_inspector_id=?::uuid, remark=?, updated_at=NOW()
                    WHERE id=?::uuid
                    """, body.get("plan_name"), planNo, planNo,
                    body.get("template_id"), templateName,
                    body.get("inspection_type"), blankToNull(body.get("inspection_type_id")),
                    body.get("cycle_days"), body.get("start_date"), body.get("end_date"), body.get("frequency"),
                    body.getOrDefault("status", "active"), body.getOrDefault("approval_status", "draft"),
                    blankToNull(body.get("dept_id")),
                    assignedId, body.get("remark"), id);
        } else {
            jdbc.update("""
                    INSERT INTO inspection_plan (id, plan_code, plan_no, plan_name, template_id, template_name,
                    inspection_type, inspection_type_id, cycle_days, next_due_date,
                    start_date, end_date, frequency, status, approval_status, dept_id,
                    assigned_inspector_id, created_by, remark)
                    VALUES (?::uuid,?,?,?,?::uuid,?,?,?::uuid,?,?,?,?,?,?,?,?::uuid,?::uuid,?,?)
                    """, id, planNo, planNo, body.get("plan_name"), body.get("template_id"), templateName,
                    body.get("inspection_type"), blankToNull(body.get("inspection_type_id")),
                    body.getOrDefault("cycle_days", 7), LocalDate.now().plusDays(7),
                    body.get("start_date"), body.get("end_date"), body.get("frequency"),
                    body.getOrDefault("status", "active"), body.getOrDefault("approval_status", "draft"),
                    blankToNull(body.get("dept_id")),
                    assignedId, userId, body.get("remark"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = body.get("items") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        upsertItems(id, planNo, items);
        refreshPlanDueCache(id);
        docLog.event("inspect", "plan", id, planNo, exists ? "update" : "create", clientOf(body), null);
        return get(id);
    }

    @PostMapping("/{id}/approve")
    @Transactional
    @OperationLog(module = "inspect", description = "审核巡检计划")
    public Result<Map<String, Object>> approve(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String action = String.valueOf(body.getOrDefault("action", "approve"));
        String status = "reject".equals(action) ? "rejected" : "approved";
        String userId = TenantContext.getUserId();
        jdbc.update("""
                UPDATE inspection_plan SET approval_status=?, approved_by=?::uuid,
                approved_at=NOW(),
                status=CASE WHEN ?='approved' THEN 'active' ELSE status END, updated_at=NOW()
                WHERE id=?::uuid
                """, status, userId, status, id);
        Result<Map<String, Object>> loaded = get(id);
        Map<String, Object> plan = loaded.getData();
        docLog.event("inspect", "plan", id, Objects.toString(plan.get("plan_no"), null),
                status, clientOf(body), null);
        return Result.ok(plan);
    }

    @PostMapping("/{id}/generate-execution")
    @Transactional
    @OperationLog(module = "inspect", description = "从计划生成巡检执行")
    public Result<Map<String, Object>> generateExecution(@PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
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
    @OperationLog(module = "inspect", description = "从模板生成巡检计划（一单多设备）")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        UUID templateId = UUID.fromString(body.get("templateId").toString());
        var template = jdbc.queryForList(
                "SELECT * FROM inspection_template WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_template", null), templateId);
        if (template.isEmpty()) throw new BizException(404, "template not found");
        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.getOrDefault("deviceIds", List.of());
        if (deviceIds.isEmpty()) throw new BizException(400, "请选择设备");

        UUID id = UUID.randomUUID();
        String planNo = DailyBizNoSupport.next(jdbc, "inspection_plan", "plan_no", "IP-");
        Map<String, Object> t = template.get(0);
        String userId = TenantContext.getUserId();
        LocalDate nextDue = body.get("next_due_date") != null
                ? LocalDate.parse(body.get("next_due_date").toString())
                : LocalDate.now().plusDays(((Number) body.getOrDefault("cycle_days", 7)).intValue());

        jdbc.update("""
                INSERT INTO inspection_plan (id, plan_code, plan_no, plan_name, template_id, template_name,
                inspection_type_id, cycle_days, next_due_date, status, approval_status, created_by)
                VALUES (?::uuid,?,?,?,?::uuid,?,?::uuid,?,?,?,?,?)
                """, id, planNo, planNo, t.get("template_name") + "计划", templateId, t.get("template_name"),
                t.get("inspection_type_id"),
                body.getOrDefault("cycle_days", 7), nextDue, "active", "draft", userId);

        List<Map<String, Object>> items = new ArrayList<>();
        for (String deviceId : deviceIds) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("device_id", deviceId);
            item.put("next_due_date", nextDue.toString());
            items.add(item);
        }
        upsertItems(id, planNo, items);
        refreshPlanDueCache(id);
        docLog.event("inspect", "plan", id, planNo, "create", clientOf(body), "from_template");
        return get(id);
    }

    @PostMapping("/{id}/activate")
    @OperationLog(module = "inspect", description = "激活巡检计划")
    public Result<Map<String, Object>> activate(@PathVariable UUID id) {
        jdbc.update("UPDATE inspection_plan SET status = 'active', updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }

    @GetMapping("/due")
    public Result<List<Map<String, Object>>> due() {
        return Result.ok(jdbc.queryForList("""
                SELECT i.*, p.plan_name, p.plan_no, p.template_id, p.inspection_type, p.approval_status
                FROM inspection_plan_item i
                INNER JOIN inspection_plan p ON p.id = i.plan_id
                WHERE p.status = 'active' AND p.approval_status = 'approved'
                  AND i.item_status = 'active'
                  AND i.next_due_date IS NOT NULL
                  AND i.next_due_date <= CURRENT_DATE + 7
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan_item", "i")
                + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan", "p") + """
                ORDER BY i.next_due_date
                """));
    }

    private void upsertItems(UUID planId, String planNo, List<Map<String, Object>> items) {
        Set<UUID> keep = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            UUID itemId = parseUuid(item.get("id")).orElse(UUID.randomUUID());
            keep.add(itemId);
            Object deviceId = blankToNull(item.get("device_id"));
            if (deviceId == null) throw new BizException(400, "计划明细缺少设备");
            String code = item.get("device_code") != null ? item.get("device_code").toString() : null;
            String name = item.get("device_name") != null ? item.get("device_name").toString() : null;
            Object deptId = blankToNull(item.get("dept_id"));
            if (code == null || name == null || deptId == null) {
                var d = jdbc.queryForList(
                        "SELECT device_code, device_name, dept_id FROM medical_device WHERE id = ?::uuid"
                                + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), deviceId);
                if (d.isEmpty()) throw new BizException(404, "设备不存在: " + deviceId);
                if (code == null) code = Objects.toString(d.get(0).get("device_code"), null);
                if (name == null) name = Objects.toString(d.get(0).get("device_name"), null);
                if (deptId == null) deptId = d.get(0).get("dept_id");
            }
            boolean exists = !jdbc.queryForList(
                    "SELECT 1 FROM inspection_plan_item WHERE id = ?::uuid AND plan_id = ?::uuid",
                    itemId, planId).isEmpty();
            String assignedId = blankToNull(item.get("assigned_user_id")) != null
                    ? item.get("assigned_user_id").toString() : null;
            String assignedName = item.get("assigned_user_name") != null
                    ? item.get("assigned_user_name").toString()
                    : SoftDeleteSupport.resolveUserDisplayName(jdbc, assignedId);
            if (exists) {
                jdbc.update("""
                        UPDATE inspection_plan_item SET plan_no=?, device_id=?::uuid, device_code=?, device_name=?,
                        dept_id=?::uuid, last_done_date=?, next_due_date=?, assigned_user_id=?::uuid,
                        assigned_user_name=?, item_status=?, remark=?, updated_at=NOW()
                        WHERE id=?::uuid AND plan_id=?::uuid
                        """, planNo, deviceId, code, name, deptId,
                        item.get("last_done_date"), item.get("next_due_date"), assignedId, assignedName,
                        item.getOrDefault("item_status", "active"), item.get("remark"), itemId, planId);
            } else {
                jdbc.update("""
                        INSERT INTO inspection_plan_item (id, plan_id, plan_no, device_id, device_code, device_name,
                        dept_id, last_done_date, next_due_date, assigned_user_id, assigned_user_name, item_status, remark)
                        VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?::uuid,?,?,?::uuid,?,?,?)
                        """, itemId, planId, planNo, deviceId, code, name, deptId,
                        item.get("last_done_date"), item.get("next_due_date"), assignedId, assignedName,
                        item.getOrDefault("item_status", "active"), item.get("remark"));
            }
        }
        if (keep.isEmpty()) {
            jdbc.update("UPDATE inspection_plan_item SET is_deleted=1, deleted_at=NOW(), updated_at=NOW() WHERE plan_id=?::uuid AND COALESCE(is_deleted,0)=0", planId);
        } else {
            String placeholders = String.join(",", Collections.nCopies(keep.size(), "?::uuid"));
            List<Object> args = new ArrayList<>();
            args.add(planId);
            args.addAll(keep);
            jdbc.update("UPDATE inspection_plan_item SET is_deleted=1, deleted_at=NOW(), updated_at=NOW() "
                    + "WHERE plan_id=?::uuid AND COALESCE(is_deleted,0)=0 AND id NOT IN (" + placeholders + ")",
                    args.toArray());
        }
    }

    private void refreshPlanDueCache(UUID planId) {
        jdbc.update("""
                UPDATE inspection_plan SET
                  next_due_date = (SELECT MIN(next_due_date) FROM inspection_plan_item
                    WHERE plan_id = ?::uuid AND COALESCE(is_deleted,0)=0 AND next_due_date IS NOT NULL),
                  last_inspected_at = (SELECT MAX(last_done_date) FROM inspection_plan_item
                    WHERE plan_id = ?::uuid AND COALESCE(is_deleted,0)=0),
                  updated_at = NOW()
                WHERE id = ?::uuid
                """, planId, planId, planId);
    }

    private static String clientOf(Map<String, Object> body) {
        return body != null && body.get("client") != null ? body.get("client").toString() : "web";
    }

    private static String firstNonBlank(Object a, Object b) {
        if (a != null && !a.toString().isBlank()) return a.toString().trim();
        if (b != null && !b.toString().isBlank()) return b.toString().trim();
        return null;
    }

    private static Object blankToNull(Object value) {
        if (value == null) return null;
        if (value instanceof String s && s.isBlank()) return null;
        return value;
    }

    private static Optional<UUID> parseUuid(Object value) {
        if (value == null) return Optional.empty();
        String s = value.toString().trim();
        if (s.isEmpty()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
