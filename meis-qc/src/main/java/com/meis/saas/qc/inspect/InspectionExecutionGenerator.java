package com.meis.saas.qc.inspect;

import com.meis.saas.common.audit.DocChangeLogService;
import com.meis.saas.common.biz.CycleDaysSupport;
import com.meis.saas.common.code.DailyBizNoSupport;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.ops.OpsClientChannel;
import com.meis.saas.common.ops.OpsPlanExecutionSupport;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class InspectionExecutionGenerator {
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
        Map<String, Object> req = body != null ? body : Map.of();
        try {
            var plan = loadApprovedPlan(planId);
            var items = OpsPlanExecutionSupport.loadDueOrSelectedItems(
                    jdbc, "inspection_plan_item", planId, req);
            return insertFromPlan(plan, planId, items, OpsPlanExecutionSupport.KIND_DUE,
                    req.getOrDefault("planned_date", items.get(0).get("next_due_date")),
                    null, null, null, "generate_from_plan", req);
        } catch (BizException ex) {
            return Map.of("planId", planId, "error", ex.getMessage());
        }
    }

    public Map<String, Object> backfillOne(UUID planId, Map<String, Object> body) {
        Map<String, Object> req = body != null ? body : Map.of();
        var plan = loadApprovedPlan(planId);
        String plannedDate = OpsPlanExecutionSupport.requireDate(req, "planned_date", "执行日期");
        String start = OpsPlanExecutionSupport.requireDate(req, "execute_start_time", "开始时间");
        String end = OpsPlanExecutionSupport.requireDate(req, "execute_end_time", "结束时间");
        Object nextDue = OpsPlanExecutionSupport.optionalBackfillNextDue(req);
        var items = OpsPlanExecutionSupport.loadAllOrSelectedItems(
                jdbc, "inspection_plan_item", planId, req);
        return insertFromPlan(plan, planId, items, OpsPlanExecutionSupport.KIND_BACKFILL,
                plannedDate, start, end, nextDue, "backfill_from_plan", req);
    }

    private Map<String, Object> loadApprovedPlan(UUID planId) {
        var plan = jdbc.queryForList("""
                SELECT p.*, t.template_name AS t_name, t.inspection_type_id AS t_type_id
                FROM inspection_plan p
                LEFT JOIN inspection_template t ON t.id = p.template_id
                WHERE p.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan", "p"), planId);
        if (plan.isEmpty()) throw new BizException(404, "plan not found");
        OpsPlanExecutionSupport.assertApproved(plan.get(0));
        return plan.get(0);
    }

    private Map<String, Object> insertFromPlan(
            Map<String, Object> p, UUID planId, List<Map<String, Object>> items,
            String kind, Object plannedDate, Object start, Object end, Object backfillNext,
            String event, Map<String, Object> body) {
        UUID execId = UUID.randomUUID();
        String execNo = DailyBizNoSupport.next(jdbc, "inspection_execution", "execution_no", "IE-");
        String planNo = Objects.toString(p.get("plan_no") != null ? p.get("plan_no") : p.get("plan_code"), null);
        String templateName = Objects.toString(
                p.get("template_name") != null ? p.get("template_name") : p.get("t_name"), null);
        Object typeId = p.get("inspection_type_id") != null ? p.get("inspection_type_id") : p.get("t_type_id");
        String userId = TenantContext.getUserId();
        String createdByName = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);

        jdbc.update("""
                INSERT INTO inspection_execution (id, execution_no, plan_id, plan_no, source_type, template_id,
                    template_name, inspection_type_id, planned_date,
                    execute_start_time, execute_end_time, execution_kind, backfill_next_due_date,
                    assigned_inspector_id, status, created_by, created_by_name, create_channel)
                VALUES (?::uuid,?,?::uuid,?, 'from_plan', ?::uuid, ?, ?::uuid, ?::date,
                    CAST(? AS timestamptz), CAST(? AS timestamptz), ?, ?::date,
                    ?::uuid, 'draft', ?::uuid, ?, ?)
                """, execId, execNo, planId, planNo, p.get("template_id"), templateName, typeId,
                plannedDate, start, end, kind, backfillNext,
                p.get("assigned_inspector_id"), userId, createdByName,
                OpsClientChannel.of(body));

        for (Map<String, Object> di : items) {
            insertItemWithResults(execId, execNo, planId, di, p.get("template_id"));
        }

        docLog.event("inspect", "execution", execId, execNo, event,
                body.get("client") != null ? body.get("client").toString() : "web",
                "plan=" + planNo);
        return jdbc.queryForList(
                "SELECT * FROM inspection_execution WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution", null),
                execId).get(0);
    }

    public Map<String, Object> createAdHoc(Map<String, Object> body) {
        Object templateId = body.get("template_id");
        if (templateId == null || templateId.toString().isBlank()) {
            throw new BizException(400, "无计划直开须选择模板");
        }
        Object typeId = blankToNull(body.get("inspection_type_id"));
        Object inspectionType = body.get("inspection_type");
        if (typeId == null && (inspectionType == null || inspectionType.toString().isBlank())) {
            throw new BizException(400, "无计划直开须填写巡检类型");
        }
        if (typeId == null && inspectionType != null) {
            var found = jdbc.queryForList(
                    "SELECT id FROM inspection_type WHERE type_code = ? OR type_name = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_type", null) + " LIMIT 1",
                    inspectionType.toString(), inspectionType.toString());
            if (!found.isEmpty()) typeId = found.get(0).get("id");
        }
        if (typeId == null) {
            throw new BizException(400, "无计划直开须填写巡检类型");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> devices = body.get("items") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        if (devices.isEmpty() && body.get("device_id") != null) {
            devices = List.of(Map.of("device_id", body.get("device_id")));
        }
        if (devices.isEmpty()) throw new BizException(400, "请选择设备");

        var template = jdbc.queryForList(
                "SELECT * FROM inspection_template WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_template", null), templateId);
        if (template.isEmpty()) throw new BizException(404, "模板不存在");

        if (body.get("cycle_type") == null || body.get("cycle_type").toString().isBlank()
                || body.get("cycle_value") == null || body.get("cycle_value").toString().isBlank()) {
            throw new BizException(400, "无计划直开须填写周期类型与周期值");
        }
        if (body.get("planned_date") == null || body.get("planned_date").toString().isBlank()) {
            throw new BizException(400, "无计划直开须填写执行日期");
        }
        var cycle = CycleDaysSupport.resolveFromTemplate(body, template.get(0), "month", 1);

        UUID execId = UUID.randomUUID();
        String execNo = DailyBizNoSupport.next(jdbc, "inspection_execution", "execution_no", "IE-");
        String userId = TenantContext.getUserId();
        String createdByName = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        String templateName = body.get("template_name") != null
                ? body.get("template_name").toString()
                : Objects.toString(template.get(0).get("template_name"), null);
        Object startTime = firstNonBlank(body.get("execute_start_time"), body.get("planned_date") + " 00:00:00");
        Object endTime = firstNonBlank(body.get("execute_end_time"), body.get("planned_date") + " 23:59:59");

        jdbc.update("""
                INSERT INTO inspection_execution (id, execution_no, plan_id, plan_no, source_type, template_id,
                    template_name, inspection_type_id, planned_date, cycle_type, cycle_value, cycle_days,
                    execute_start_time, execute_end_time, status, created_by, created_by_name, remark, create_channel)
                VALUES (?::uuid,?, NULL, NULL, 'ad_hoc', ?::uuid, ?, ?::uuid, ?::date, ?, ?, ?,
                    CAST(? AS timestamptz), CAST(? AS timestamptz), 'draft', ?::uuid, ?, ?, ?)
                """, execId, execNo, templateId, templateName, typeId,
                body.get("planned_date"), cycle.type(), cycle.value(), cycle.days(),
                startTime, endTime, userId, createdByName, body.get("remark"),
                OpsClientChannel.of(body));

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

        docLog.event("inspect", "execution", execId, execNo, "create_ad_hoc",
                body.get("client") != null ? body.get("client").toString() : "web", null);
        return jdbc.queryForList(
                "SELECT * FROM inspection_execution WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution", null),
                execId).get(0);
    }

    private void insertItemWithResults(UUID execId, String execNo, UUID planId,
                                       Map<String, Object> di, Object templateId) {
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inspection_execution_item
                (id, execution_id, execution_no, device_id, device_code, device_name, dept_id, dept_name,
                 plan_id, plan_item_id, status, row_version)
                VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?::uuid,?,?::uuid,?::uuid,'pending',1)
                """, itemId, execId, execNo, di.get("device_id"), di.get("device_code"), di.get("device_name"),
                di.get("dept_id"), resolveDeptName(di.get("dept_id"), di.get("dept_name")), planId, di.get("id"));

        if (templateId == null) return;
        var templateItems = jdbc.queryForList("""
                SELECT * FROM inspection_template_item WHERE template_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_template_item", null) + """
                 ORDER BY sort_order NULLS LAST, created_at
                """, templateId);
        for (Map<String, Object> ti : templateItems) {
            jdbc.update("""
                    INSERT INTO inspection_execution_result
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

    private static Object firstNonBlank(Object primary, Object fallback) {
        if (primary != null && !primary.toString().isBlank()) return primary;
        return fallback;
    }
}
