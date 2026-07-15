package com.meis.saas.repair.service;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 维修工单流程业务记录：派工/接单/转派/维修/验收等明细落 {@code repair_workorder_process}，
 * 主单 {@code repair_workorder} 仅同步当前状态（status / 当前工程师 / 子状态）。
 */
@Service
@RequiredArgsConstructor
public class RepairWorkorderProcessService {

    private static final Set<String> WORKFLOW_STARTED_ACTIONS = Set.of(
            "dispatch", "accept", "grab", "start_repair", "transfer");

    private final JdbcTemplate jdbc;

    private boolean processTableReady() {
        return TableColumnCache.hasTable(jdbc, "repair_workorder_process");
    }

    public UUID insertProcess(UUID workorderId, ProcessRecord record) {
        if (!processTableReady()) {
            throw new IllegalStateException("repair_workorder_process 表不存在，请重启 meis-tenant 完成租户迁移");
        }
        UUID id = UUID.randomUUID();
        String userId = TenantContext.getUserId();
        Map<String, Object> wo = jdbc.queryForList(
                "SELECT device_id, device_code, device_name FROM repair_workorder WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null), workorderId)
                .stream().findFirst().orElse(Map.of());
        boolean hasDevice = TableColumnCache.hasColumn(jdbc, "repair_workorder_process", "device_id");
        if (hasDevice) {
            jdbc.update("""
                    INSERT INTO repair_workorder_process
                    (id, workorder_id, action_type, from_status, to_status, from_sub_status, to_sub_status,
                     user_id, from_user_id, to_user_id, operator_id,
                     solution_description, labor_cost, parts_cost, total_cost,
                     verify_result, verify_comment, satisfaction_rating, satisfaction_comment,
                     skip_verify, remark, extra_json, device_id, device_code, device_name, created_by, updated_by)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?,
                            ?::uuid,?::uuid,?::uuid,?::uuid,
                            ?,?,?,?,
                            ?,?,?,?,
                            ?,?,CAST(? AS jsonb),?::uuid,?,?,?::uuid,?::uuid)
                    """,
                    id, workorderId, record.actionType(),
                    blankToNull(record.fromStatus()), blankToNull(record.toStatus()),
                    blankToNull(record.fromSubStatus()), blankToNull(record.toSubStatus()),
                    blankToNull(record.userId()), blankToNull(record.fromUserId()),
                    blankToNull(record.toUserId()), blankToNull(record.operatorId()),
                    record.solutionDescription(), record.laborCost(), record.partsCost(), record.totalCost(),
                    blankToNull(record.verifyResult()), record.verifyComment(),
                    record.satisfactionRating(), record.satisfactionComment(),
                    record.skipVerify(), blankToNull(record.remark()), record.extraJson(),
                    blankToNull(wo.get("device_id")), blankToNull(wo.get("device_code")), blankToNull(wo.get("device_name")),
                    blankToNull(userId), blankToNull(userId));
        } else {
            jdbc.update("""
                    INSERT INTO repair_workorder_process
                    (id, workorder_id, action_type, from_status, to_status, from_sub_status, to_sub_status,
                     user_id, from_user_id, to_user_id, operator_id,
                     solution_description, labor_cost, parts_cost, total_cost,
                     verify_result, verify_comment, satisfaction_rating, satisfaction_comment,
                     skip_verify, remark, extra_json, created_by, updated_by)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?,
                            ?::uuid,?::uuid,?::uuid,?::uuid,
                            ?,?,?,?,
                            ?,?,?,?,
                            ?,?,CAST(? AS jsonb),?::uuid,?::uuid)
                    """,
                    id, workorderId, record.actionType(),
                    blankToNull(record.fromStatus()), blankToNull(record.toStatus()),
                    blankToNull(record.fromSubStatus()), blankToNull(record.toSubStatus()),
                    blankToNull(record.userId()), blankToNull(record.fromUserId()),
                    blankToNull(record.toUserId()), blankToNull(record.operatorId()),
                    record.solutionDescription(), record.laborCost(), record.partsCost(), record.totalCost(),
                    blankToNull(record.verifyResult()), record.verifyComment(),
                    record.satisfactionRating(), record.satisfactionComment(),
                    record.skipVerify(), blankToNull(record.remark()), record.extraJson(),
                    blankToNull(userId), blankToNull(userId));
        }
        return id;
    }

    /**
     * 主单仅更新当前流程状态（不写费用/方案/验收明细等业务字段）。
     */
    public void syncWorkorderState(UUID workorderId, String status, String repairSubStatus, Object userId) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sets.add("status = ?");
            args.add(status);
        }
        if (repairSubStatus != null) {
            sets.add("repair_sub_status = ?");
            args.add(repairSubStatus.isBlank() ? null : repairSubStatus);
        } else if (status != null && !Set.of("repairing", "suspended", "verify_rejected").contains(status)) {
            sets.add("repair_sub_status = NULL");
        }
        if (userId != null) {
            sets.add("assigned_user_id = ?::uuid");
            args.add(blankToNull(userId));
        }
        sets.add("updated_at = NOW()");
        args.add(workorderId);
        jdbc.update("UPDATE repair_workorder SET " + String.join(", ", sets) + " WHERE id = ?::uuid", args.toArray());
    }

    public void syncStatusOnly(UUID workorderId, String status) {
        jdbc.update("UPDATE repair_workorder SET status = ?, updated_at = NOW() WHERE id = ?::uuid", status, workorderId);
    }

    public boolean hasWorkflowStarted(UUID workorderId) {
        if (!processTableReady()) return false;
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_process", null);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_workorder_process WHERE workorder_id = ?::uuid"
                        + " AND action_type IN ('dispatch','accept','grab','start_repair','transfer')" + clause,
                Long.class, workorderId);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> listByWorkorder(UUID workorderId) {
        if (!processTableReady()) return List.of();
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_process", null);
        return jdbc.queryForList(
                "SELECT * FROM repair_workorder_process WHERE workorder_id = ?::uuid" + clause
                        + " ORDER BY created_at ASC, id ASC",
                workorderId);
    }

    public Optional<Map<String, Object>> latestByAction(UUID workorderId, String... actionTypes) {
        if (actionTypes == null || actionTypes.length == 0) return Optional.empty();
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_process", null);
        String placeholders = String.join(",", Collections.nCopies(actionTypes.length, "?"));
        List<Object> args = new ArrayList<>();
        args.add(workorderId);
        args.addAll(Arrays.asList(actionTypes));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM repair_workorder_process WHERE workorder_id = ?::uuid" + clause
                        + " AND action_type IN (" + placeholders + ") ORDER BY created_at DESC LIMIT 1",
                args.toArray());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<Object> firstActionAt(UUID workorderId, String... actionTypes) {
        if (actionTypes == null || actionTypes.length == 0) return Optional.empty();
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_process", null);
        String placeholders = String.join(",", Collections.nCopies(actionTypes.length, "?"));
        List<Object> args = new ArrayList<>();
        args.add(workorderId);
        args.addAll(Arrays.asList(actionTypes));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT created_at FROM repair_workorder_process WHERE workorder_id = ?::uuid"
                        + " AND action_type IN (" + placeholders + ")" + clause
                        + " ORDER BY created_at ASC LIMIT 1",
                args.toArray());
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0).get("created_at"));
    }

    public Optional<Object> firstOnSiteArrival(UUID workorderId) {
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_process", null);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT created_at FROM repair_workorder_process WHERE workorder_id = ?::uuid"
                        + " AND action_type = 'sub_status' AND to_sub_status = 'on_site'" + clause
                        + " ORDER BY created_at ASC LIMIT 1",
                workorderId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0).get("created_at"));
    }

    public void enrichWorkorder(Map<String, Object> wo) {
        if (!processTableReady() || wo == null || wo.get("id") == null) return;
        UUID id = UUID.fromString(String.valueOf(wo.get("id")));

        latestByAction(id, "complete").ifPresent(p -> {
            putIfPresent(wo, "solution_description", p.get("solution_description"));
            putIfPresent(wo, "labor_cost", p.get("labor_cost"));
            putIfPresent(wo, "parts_cost", p.get("parts_cost"));
            putIfPresent(wo, "total_cost", p.get("total_cost"));
            putIfPresent(wo, "repair_end_time", p.get("created_at"));
        });

        latestByAction(id, "verify_pass", "verify_fail").ifPresent(p -> {
            putIfPresent(wo, "verify_result", p.get("verify_result"));
            putIfPresent(wo, "verify_comment", p.get("verify_comment"));
            putIfPresent(wo, "satisfaction_rating", p.get("satisfaction_rating"));
            putIfPresent(wo, "satisfaction_comment", p.get("satisfaction_comment"));
            putIfPresent(wo, "verify_time", p.get("created_at"));
            putIfPresent(wo, "verifier_id", p.get("operator_id"));
        });

        firstActionAt(id, "dispatch", "grab").ifPresent(at -> putIfPresent(wo, "dispatch_started_at", at));
        firstActionAt(id, "dispatch", "grab").ifPresent(at -> putIfPresent(wo, "assigned_at", at));
        firstActionAt(id, "accept").ifPresent(at -> putIfPresent(wo, "accepted_at", at));
        firstActionAt(id, "start_repair").ifPresent(at -> {
            putIfPresent(wo, "repair_start_time", at);
            putIfPresent(wo, "response_time", at);
        });
        latestByAction(id, "close", "cancel").ifPresent(p -> putIfPresent(wo, "closed_at", p.get("created_at")));
        firstOnSiteArrival(id).ifPresent(at -> putIfPresent(wo, "arrival_time", at));
    }

    public void enrichWorkorders(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            enrichWorkorder(row);
        }
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private static Object blankToNull(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    public record ProcessRecord(
            String actionType,
            String fromStatus,
            String toStatus,
            String fromSubStatus,
            String toSubStatus,
            Object userId,
            Object fromUserId,
            Object toUserId,
            Object operatorId,
            String solutionDescription,
            Object laborCost,
            Object partsCost,
            Object totalCost,
            String verifyResult,
            String verifyComment,
            Object satisfactionRating,
            String satisfactionComment,
            Boolean skipVerify,
            String remark,
            String extraJson
    ) {
        public static Builder builder(String actionType) {
            return new Builder(actionType);
        }

        public static final class Builder {
            private final String actionType;
            private String fromStatus;
            private String toStatus;
            private String fromSubStatus;
            private String toSubStatus;
            private Object userId;
            private Object fromUserId;
            private Object toUserId;
            private Object operatorId;
            private String solutionDescription;
            private Object laborCost;
            private Object partsCost;
            private Object totalCost;
            private String verifyResult;
            private String verifyComment;
            private Object satisfactionRating;
            private String satisfactionComment;
            private Boolean skipVerify;
            private String remark;
            private String extraJson;

            private Builder(String actionType) {
                this.actionType = actionType;
            }

            public Builder fromStatus(String v) { this.fromStatus = v; return this; }
            public Builder toStatus(String v) { this.toStatus = v; return this; }
            public Builder fromSubStatus(String v) { this.fromSubStatus = v; return this; }
            public Builder toSubStatus(String v) { this.toSubStatus = v; return this; }
            public Builder userId(Object v) { this.userId = v; return this; }
            public Builder fromUserId(Object v) { this.fromUserId = v; return this; }
            public Builder toUserId(Object v) { this.toUserId = v; return this; }
            /** @deprecated 兼容旧参数名 */
            public Builder engineerId(Object v) { return userId(v); }
            /** @deprecated 兼容旧参数名 */
            public Builder fromEngineerId(Object v) { return fromUserId(v); }
            /** @deprecated 兼容旧参数名 */
            public Builder toEngineerId(Object v) { return toUserId(v); }
            public Builder operatorId(Object v) { this.operatorId = v; return this; }
            public Builder solutionDescription(String v) { this.solutionDescription = v; return this; }
            public Builder laborCost(Object v) { this.laborCost = v; return this; }
            public Builder partsCost(Object v) { this.partsCost = v; return this; }
            public Builder totalCost(Object v) { this.totalCost = v; return this; }
            public Builder verifyResult(String v) { this.verifyResult = v; return this; }
            public Builder verifyComment(String v) { this.verifyComment = v; return this; }
            public Builder satisfactionRating(Object v) { this.satisfactionRating = v; return this; }
            public Builder satisfactionComment(String v) { this.satisfactionComment = v; return this; }
            public Builder skipVerify(Boolean v) { this.skipVerify = v; return this; }
            public Builder remark(String v) { this.remark = v; return this; }
            public Builder extraJson(String v) { this.extraJson = v; return this; }

            public ProcessRecord build() {
                return new ProcessRecord(actionType, fromStatus, toStatus, fromSubStatus, toSubStatus,
                        userId, fromUserId, toUserId, operatorId,
                        solutionDescription, laborCost, partsCost, totalCost,
                        verifyResult, verifyComment, satisfactionRating, satisfactionComment,
                        skipVerify, remark, extraJson);
            }
        }
    }
}
