package com.meis.saas.maintain.pm;

import com.meis.saas.common.audit.DocChangeLogService;
import com.meis.saas.common.code.DailyBizNoSupport;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class PmExecutionGenerator {
    private final JdbcTemplate jdbc;
    private final DocChangeLogService docLog;

    public List<Map<String, Object>> generateBatch(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> planIds = (List<String>) body.getOrDefault("planIds", List.of());
        List<Map<String, Object>> created = new ArrayList<>();
        for (String planId : planIds) {
            created.add(generateOne(UUID.fromString(planId), body));
        }
        return created;
    }

    public Map<String, Object> generateOne(UUID planId, Map<String, Object> body) {
        var plan = jdbc.queryForList("""
                SELECT p.*, t.template_name AS t_name, t.pm_type_id AS t_type_id, t.pm_type AS t_pm_type
                FROM pm_plan p
                LEFT JOIN pm_template t ON t.id = p.template_id
                WHERE p.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "pm_plan", "p"), planId);
        if (plan.isEmpty()) return Map.of("planId", planId, "error", "plan not found");
        Map<String, Object> p = plan.get(0);
        if (!"approved".equals(p.get("approval_status"))) {
            return Map.of("planId", planId, "error", "plan not approved");
        }

        var dueItems = jdbc.queryForList("""
                SELECT * FROM pm_plan_item
                WHERE plan_id = ?::uuid AND COALESCE(item_status,'active') = 'active'
                  AND next_due_date IS NOT NULL AND next_due_date <= CURRENT_DATE
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "pm_plan_item", null)
                + " ORDER BY next_due_date, device_code", planId);
        if (dueItems.isEmpty()) {
            return Map.of("planId", planId, "error", "无到期明细可生成执行单");
        }

        UUID execId = UUID.randomUUID();
        String execNo = DailyBizNoSupport.next(jdbc, "pm_execution", "execution_no", "PX-");
        String planNo = Objects.toString(p.get("plan_no") != null ? p.get("plan_no") : p.get("plan_code"), null);
        String templateName = Objects.toString(
                p.get("template_name") != null ? p.get("template_name") : p.get("t_name"), null);
        Object typeId = p.get("pm_type_id") != null ? p.get("pm_type_id") : p.get("t_type_id");
        String userId = TenantContext.getUserId();
        String createdByName = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);

        jdbc.update("""
                INSERT INTO pm_execution (id, execution_no, plan_id, plan_no, source_type, template_id,
                    template_name, pm_type_id, planned_date,
                    assigned_user_id, assigned_user_name, status, created_by, created_by_name)
                VALUES (?::uuid,?,?::uuid,?, 'from_plan', ?::uuid, ?, ?::uuid, ?, ?::uuid, ?, 'draft', ?::uuid, ?)
                """, execId, execNo, planId, planNo, p.get("template_id"), templateName, typeId,
                body.getOrDefault("planned_date", dueItems.get(0).get("next_due_date")),
                p.get("assigned_user_id"), p.get("assigned_user_name"), userId, createdByName);

        for (Map<String, Object> di : dueItems) {
            insertItemWithResults(execId, execNo, planId, di, p.get("template_id"));
        }

        docLog.event("pm", "execution", execId, execNo, "generate_from_plan",
                body.get("client") != null ? body.get("client").toString() : "web",
                "plan=" + planNo);
        return jdbc.queryForList(
                "SELECT * FROM pm_execution WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "pm_execution", null),
                execId).get(0);
    }

    public Map<String, Object> createAdHoc(Map<String, Object> body) {
        Object templateId = body.get("template_id");
        if (templateId == null || templateId.toString().isBlank()) {
            throw new BizException(400, "无计划直开须选择模板");
        }
        Object typeId = blankToNull(body.get("pm_type_id"));
        Object pmType = body.get("pm_type");
        if (typeId == null && (pmType == null || pmType.toString().isBlank())) {
            throw new BizException(400, "无计划直开须填写PM类型");
        }
        if (typeId == null && pmType != null) {
            var found = jdbc.queryForList(
                    "SELECT id FROM pm_type WHERE type_code = ? OR type_name = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "pm_type", null) + " LIMIT 1",
                    pmType.toString(), pmType.toString());
            if (!found.isEmpty()) typeId = found.get(0).get("id");
        }
        if (typeId == null) {
            throw new BizException(400, "无计划直开须填写PM类型");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> devices = body.get("items") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        if (devices.isEmpty() && body.get("device_id") != null) {
            devices = List.of(Map.of("device_id", body.get("device_id")));
        }
        if (devices.isEmpty()) throw new BizException(400, "请选择设备");

        var template = jdbc.queryForList(
                "SELECT * FROM pm_template WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "pm_template", null), templateId);
        if (template.isEmpty()) throw new BizException(404, "模板不存在");

        UUID execId = UUID.randomUUID();
        String execNo = DailyBizNoSupport.next(jdbc, "pm_execution", "execution_no", "PX-");
        String userId = TenantContext.getUserId();
        String createdByName = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        String templateName = body.get("template_name") != null
                ? body.get("template_name").toString()
                : Objects.toString(template.get(0).get("template_name"), null);

        jdbc.update("""
                INSERT INTO pm_execution (id, execution_no, plan_id, plan_no, source_type, template_id,
                    template_name, pm_type_id, planned_date, status, created_by, created_by_name, remark)
                VALUES (?::uuid,?, NULL, NULL, 'ad_hoc', ?::uuid, ?, ?::uuid, ?, 'draft', ?::uuid, ?, ?)
                """, execId, execNo, templateId, templateName, typeId,
                body.get("planned_date"), userId, createdByName, body.get("remark"));

        for (Map<String, Object> di : devices) {
            Map<String, Object> row = new LinkedHashMap<>(di);
            if (row.get("device_code") == null && row.get("device_id") != null) {
                var d = jdbc.queryForList(
                        "SELECT device_code, device_name, dept_id FROM medical_device WHERE id=?::uuid"
                                + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null),
                        row.get("device_id"));
                if (d.isEmpty()) throw new BizException(404, "设备不存在");
                row.put("device_code", d.get(0).get("device_code"));
                row.put("device_name", d.get(0).get("device_name"));
                row.put("dept_id", d.get(0).get("dept_id"));
            }
            insertItemWithResults(execId, execNo, null, row, templateId);
        }

        docLog.event("pm", "execution", execId, execNo, "create_ad_hoc",
                body.get("client") != null ? body.get("client").toString() : "web", null);
        return jdbc.queryForList(
                "SELECT * FROM pm_execution WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "pm_execution", null),
                execId).get(0);
    }

    private void insertItemWithResults(UUID execId, String execNo, UUID planId,
                                       Map<String, Object> di, Object templateId) {
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pm_execution_item
                (id, execution_id, execution_no, device_id, device_code, device_name, dept_id, dept_name,
                 plan_id, plan_item_id, status, row_version)
                VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?::uuid,?,?::uuid,?::uuid,'pending',1)
                """, itemId, execId, execNo, di.get("device_id"), di.get("device_code"), di.get("device_name"),
                di.get("dept_id"), resolveDeptName(di.get("dept_id"), di.get("dept_name")), planId, di.get("id"));

        if (templateId == null) return;
        var templateItems = jdbc.queryForList("""
                SELECT * FROM pm_template_item WHERE template_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "pm_template_item", null) + """
                 ORDER BY sort_order NULLS LAST, created_at
                """, templateId);
        for (Map<String, Object> ti : templateItems) {
            jdbc.update("""
                    INSERT INTO pm_execution_result
                    (id, execution_item_id, execution_no, template_item_id, item_name, item_content,
                     standard_value, check_method, sort_order, is_required, result_status, row_version)
                    VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?,?,?,?, 'pending', 1)
                    """, UUID.randomUUID(), itemId, execNo, ti.get("id"), ti.get("item_name"), ti.get("item_content"),
                    ti.get("standard_value"), ti.get("check_method"),
                    ti.getOrDefault("sort_order", 0), ti.getOrDefault("is_required", true));
        }
    }

    private String resolveDeptName(Object deptId, Object existing) {
        if (existing != null && !String.valueOf(existing).isBlank()) return String.valueOf(existing);
        if (deptId == null || String.valueOf(deptId).isBlank()) return null;
        var rows = jdbc.queryForList(
                "SELECT dept_name FROM department WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "department", null), deptId);
        return rows.isEmpty() ? null : Objects.toString(rows.get(0).get("dept_name"), null);
    }

    private static Object blankToNull(Object value) {
        if (value == null) return null;
        if (value instanceof String s && s.isBlank()) return null;
        return value;
    }
}
