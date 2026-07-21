package com.meis.saas.common.ops;

import com.meis.saas.common.audit.DocChangeLogService;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * 运维执行异常项自动报修（附录 OPS.11 / INS-F-02）。
 * 冲突（设备不可报修或已有进行中工单）时跳过，不抛错。
 */
public final class OpsAutoRepairSupport {
    private static final Set<String> ACTIVE = Set.of(
            "reported", "dispatching", "pending_accept", "accepted",
            "repairing", "pending_verify", "suspended", "verify_rejected"
    );
    private static final Set<String> BLOCKED_DEVICE = Set.of("maintenance", "pending_verify", "scrap");

    private OpsAutoRepairSupport() {}

    /**
     * @return created workorder id, or empty if skipped
     */
    public static Optional<UUID> tryCreateFromFail(
            JdbcTemplate jdbc,
            DocChangeLogService docLog,
            String module,
            UUID executionId,
            String executionNo,
            UUID executionItemId,
            Object deviceId,
            List<Map<String, Object>> failedResults) {
        if (deviceId == null || failedResults == null || failedResults.isEmpty()) {
            return Optional.empty();
        }
        try {
            var devices = jdbc.queryForList(
                    "SELECT id, device_code, device_name, dept_id, device_status FROM medical_device WHERE id=?::uuid"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null),
                    deviceId);
            if (devices.isEmpty()) return Optional.empty();
            Map<String, Object> d = devices.get(0);
            String status = Objects.toString(d.get("device_status"), "");
            if (BLOCKED_DEVICE.contains(status)) {
                logSkip(docLog, module, executionId, executionNo, "device_blocked:" + status);
                return Optional.empty();
            }
            String placeholders = String.join(",", ACTIVE.stream().map(s -> "?").toList());
            List<Object> args = new ArrayList<>();
            args.add(deviceId);
            args.addAll(ACTIVE);
            Long busy = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM repair_workorder WHERE device_id=?::uuid AND status IN (" + placeholders + ")"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null),
                    Long.class, args.toArray());
            if (busy != null && busy > 0) {
                logSkip(docLog, module, executionId, executionNo, "busy_workorder");
                return Optional.empty();
            }

            StringBuilder desc = new StringBuilder();
            desc.append("【运维自动报修】模块=").append(module)
                    .append(" 执行单=").append(executionNo != null ? executionNo : executionId);
            for (Map<String, Object> r : failedResults) {
                desc.append("\n- ").append(Objects.toString(r.get("item_name"), "检查项"))
                        .append("：").append(Objects.toString(r.get("result_value"), "不合格"));
                if (r.get("remark") != null && !r.get("remark").toString().isBlank()) {
                    desc.append("（").append(r.get("remark")).append("）");
                }
            }

            UUID woId = UUID.randomUUID();
            String woNo = "WO" + System.currentTimeMillis();
            String userId = TenantContext.getUserId();
            String userName = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
            String remark = "source=" + module + ";execution=" + executionId + ";item=" + executionItemId;

            if (TableColumnCache.hasColumn(jdbc, "repair_workorder", "reporter_name")) {
                jdbc.update("""
                        INSERT INTO repair_workorder
                        (id, wo_no, device_id, device_code, device_name, reporter_id, reporter_name, report_dept_id,
                         report_method, report_time, fault_description, urgency_level, remark, status)
                        VALUES (?::uuid,?,?::uuid,?,?,?::uuid,?,?::uuid,'ops_auto',NOW(),?,'normal',?, 'draft')
                        """, woId, woNo, deviceId, d.get("device_code"), d.get("device_name"),
                        userId, userName, d.get("dept_id"), desc.toString(), remark);
            } else {
                jdbc.update("""
                        INSERT INTO repair_workorder
                        (id, wo_no, device_id, device_code, device_name, reporter_id, report_dept_id,
                         report_method, report_time, fault_description, urgency_level, remark, status)
                        VALUES (?::uuid,?,?::uuid,?,?,?::uuid,?::uuid,'ops_auto',NOW(),?,'normal',?, 'draft')
                        """, woId, woNo, deviceId, d.get("device_code"), d.get("device_name"),
                        userId, d.get("dept_id"), desc.toString(), remark);
            }

            jdbc.update("UPDATE repair_workorder SET status='reported', updated_at=NOW() WHERE id=?::uuid", woId);
            jdbc.update("UPDATE medical_device SET device_status='maintenance', updated_at=NOW() WHERE id=?::uuid", deviceId);

            if (docLog != null) {
                docLog.event(module, "execution", executionId, executionNo, "auto_repair",
                        "system", "wo_no=" + woNo);
            }
            return Optional.of(woId);
        } catch (Exception e) {
            if (docLog != null) {
                docLog.event(module, "execution", executionId, executionNo, "auto_repair_skip",
                        "system", e.getMessage());
            }
            return Optional.empty();
        }
    }

    private static void logSkip(DocChangeLogService docLog, String module, UUID execId, String execNo, String reason) {
        if (docLog != null) {
            docLog.event(module, "execution", execId, execNo, "auto_repair_skip", "system", reason);
        }
    }
}
