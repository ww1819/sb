package com.meis.saas.common.ops;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;

/** OPS.16.12：计划生成/补录执行单共用辅助 */
public final class OpsPlanExecutionSupport {
    private OpsPlanExecutionSupport() {}

    public static final String KIND_DUE = "due";
    public static final String KIND_BACKFILL = "backfill";

    @SuppressWarnings("unchecked")
    public static List<String> planItemIds(Map<String, Object> body) {
        if (body == null) return List.of();
        Object raw = body.get("plan_item_ids");
        if (raw == null) raw = body.get("planItemIds");
        if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();
        List<String> ids = new ArrayList<>();
        for (Object o : list) {
            if (o != null && StringUtils.hasText(o.toString())) ids.add(o.toString().trim());
        }
        return ids;
    }

    /** 到期明细；若 body 带 plan_item_ids 则按 id 取（不要求已到期） */
    public static List<Map<String, Object>> loadDueOrSelectedItems(
            JdbcTemplate jdbc, String itemTable, UUID planId, Map<String, Object> body) {
        List<String> ids = planItemIds(body);
        String soft = SoftDeleteSupport.notDeletedClause(jdbc, itemTable, null);
        if (!ids.isEmpty()) {
            StringBuilder in = new StringBuilder();
            List<Object> args = new ArrayList<>();
            args.add(planId);
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) in.append(',');
                in.append("?::uuid");
                args.add(ids.get(i));
            }
            var rows = jdbc.queryForList("""
                    SELECT * FROM %s WHERE plan_id = ?::uuid AND id IN (%s)
                      AND COALESCE(item_status,'active') = 'active'
                    %s ORDER BY next_due_date NULLS LAST, device_code
                    """.formatted(itemTable, in, soft), args.toArray());
            if (rows.isEmpty()) throw new BizException(400, "未找到所选计划明细");
            return rows;
        }
        var due = jdbc.queryForList("""
                SELECT * FROM %s WHERE plan_id = ?::uuid AND COALESCE(item_status,'active') = 'active'
                  AND next_due_date IS NOT NULL AND next_due_date <= CURRENT_DATE
                %s ORDER BY next_due_date, device_code
                """.formatted(itemTable, soft), planId);
        if (due.isEmpty()) throw new BizException(400, "无到期明细可生成执行单");
        return due;
    }

    /** 补录：指定明细或全部有效明细 */
    public static List<Map<String, Object>> loadAllOrSelectedItems(
            JdbcTemplate jdbc, String itemTable, UUID planId, Map<String, Object> body) {
        List<String> ids = planItemIds(body);
        String soft = SoftDeleteSupport.notDeletedClause(jdbc, itemTable, null);
        if (!ids.isEmpty()) {
            return loadDueOrSelectedItems(jdbc, itemTable, planId, body);
        }
        var rows = jdbc.queryForList("""
                SELECT * FROM %s WHERE plan_id = ?::uuid AND COALESCE(item_status,'active') = 'active'
                %s ORDER BY next_due_date NULLS LAST, device_code
                """.formatted(itemTable, soft), planId);
        if (rows.isEmpty()) throw new BizException(400, "计划无有效设备明细");
        return rows;
    }

    public static void assertApproved(Map<String, Object> plan) {
        if (!"approved".equals(Objects.toString(plan.get("approval_status"), ""))) {
            throw new BizException(400, "仅已审核计划可操作");
        }
    }

    public static String requireDate(Map<String, Object> body, String key, String label) {
        Object v = body != null ? body.get(key) : null;
        if (v == null || !StringUtils.hasText(v.toString())) {
            throw new BizException(400, "请填写" + label);
        }
        return v.toString().trim();
    }

    public static Object optionalBackfillNextDue(Map<String, Object> body) {
        if (body == null) return null;
        boolean update = Boolean.TRUE.equals(body.get("update_next_due"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("update_next_due")));
        if (!update) return null;
        Object v = body.get("next_due_date");
        if (v == null || !StringUtils.hasText(v.toString())) {
            throw new BizException(400, "已勾选修改下次执行日期，请填写日期");
        }
        return v.toString().trim();
    }

    /**
     * 审核通过后回写计划明细。
     * due：last_done + 按周期重算 next_due；
     * backfill：last_done=执行日期；仅当 backfill_next_due_date 非空时改 next_due。
     */
    public static void updatePlansAfterAudit(
            JdbcTemplate jdbc,
            String execTable,
            String execItemTable,
            String planTable,
            String planItemTable,
            String planLastDoneColumn,
            UUID execId) {
        var execRows = jdbc.queryForList(
                "SELECT execution_kind, planned_date, backfill_next_due_date FROM " + execTable
                        + " WHERE id=?::uuid" + SoftDeleteSupport.notDeletedClause(jdbc, execTable, null),
                execId);
        if (execRows.isEmpty()) return;
        Map<String, Object> exec = execRows.get(0);
        String kind = Objects.toString(exec.get("execution_kind"), KIND_DUE);
        LocalDate doneDate = exec.get("planned_date") != null
                ? LocalDate.parse(exec.get("planned_date").toString().substring(0, 10))
                : LocalDate.now();
        LocalDate backfillNext = exec.get("backfill_next_due_date") != null
                ? LocalDate.parse(exec.get("backfill_next_due_date").toString().substring(0, 10))
                : null;

        var items = jdbc.queryForList("""
                SELECT plan_item_id, plan_id FROM %s
                WHERE execution_id=?::uuid AND plan_item_id IS NOT NULL
                %s
                """.formatted(execItemTable, SoftDeleteSupport.notDeletedClause(jdbc, execItemTable, null)),
                execId);
        for (Map<String, Object> row : items) {
            UUID planItemId = (UUID) row.get("plan_item_id");
            UUID planId = (UUID) row.get("plan_id");
            if (KIND_BACKFILL.equals(kind)) {
                if (backfillNext != null) {
                    jdbc.update("""
                            UPDATE %s SET last_done_date=?::date, next_due_date=?::date, updated_at=NOW()
                            WHERE id=?::uuid
                            """.formatted(planItemTable), doneDate, backfillNext, planItemId);
                } else {
                    jdbc.update("""
                            UPDATE %s SET last_done_date=?::date, updated_at=NOW()
                            WHERE id=?::uuid
                            """.formatted(planItemTable), doneDate, planItemId);
                }
            } else {
                Integer cycleDays = null;
                if (planId != null) {
                    var plan = jdbc.queryForList(
                            "SELECT cycle_days FROM " + planTable + " WHERE id=?::uuid"
                                    + SoftDeleteSupport.notDeletedClause(jdbc, planTable, null), planId);
                    if (!plan.isEmpty() && plan.get(0).get("cycle_days") != null) {
                        cycleDays = ((Number) plan.get(0).get("cycle_days")).intValue();
                    }
                }
                LocalDate next = cycleDays != null && cycleDays > 0
                        ? doneDate.plusDays(cycleDays) : doneDate.plusMonths(1);
                jdbc.update("""
                        UPDATE %s SET last_done_date=?::date, next_due_date=?::date, updated_at=NOW()
                        WHERE id=?::uuid
                        """.formatted(planItemTable), doneDate, next, planItemId);
            }
            if (planId != null) {
                jdbc.update("""
                        UPDATE %s SET
                          next_due_date = COALESCE(
                            (SELECT MIN(next_due_date) FROM %s
                              WHERE plan_id=?::uuid AND COALESCE(is_deleted,0)=0 AND next_due_date IS NOT NULL),
                            next_due_date,
                            CURRENT_DATE + COALESCE(cycle_days, 30)
                          ),
                          %s = ?::date, updated_at=NOW()
                        WHERE id=?::uuid
                        """.formatted(planTable, planItemTable, planLastDoneColumn),
                        planId, doneDate, planId);
            }
        }
    }
}
