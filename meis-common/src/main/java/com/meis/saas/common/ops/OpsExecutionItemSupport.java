package com.meis.saas.common.ops;

import com.meis.saas.common.audit.DocChangeLogService;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * OPS.16.14 / 16.15：执行明细确认/删除。
 * 16.15：未完成亦可确认（自动记 completed）；取消全确认自动提交；审核仅要求全部 confirmed。
 */
public final class OpsExecutionItemSupport {
    private OpsExecutionItemSupport() {}

    private static final Set<String> EDITABLE_HEADER = Set.of("draft", "in_progress", "pending");

    public static Map<String, Object> loadItem(JdbcTemplate jdbc, String itemTable, UUID itemId) {
        var rows = jdbc.queryForList(
                "SELECT * FROM " + itemTable + " WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, itemTable, null), itemId);
        if (rows.isEmpty()) throw new BizException(404, "明细不存在");
        return rows.get(0);
    }

    public static void assertHeaderEditable(JdbcTemplate jdbc, String execTable, UUID execId) {
        var rows = jdbc.queryForList(
                "SELECT status FROM " + execTable + " WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, execTable, null), execId);
        if (rows.isEmpty()) throw new BizException(404, "执行单不存在");
        String st = Objects.toString(rows.get(0).get("status"), "");
        if ("submitted".equals(st) || "audited".equals(st) || "cancelled".equals(st)) {
            throw new BizException(400, "已提交/已审核不可修改，请先撤回（若允许）");
        }
        if (!EDITABLE_HEADER.contains(st)) {
            throw new BizException(400, "当前状态不可操作明细");
        }
    }

    /**
     * 确认明细：若尚未 completed，先自动记为 completed（允许空结果），再确认。
     * 不自动提交头表（OPS.16.15）。
     */
    public static void confirmItem(
            JdbcTemplate jdbc,
            DocChangeLogService docLog,
            String module,
            String execTable,
            String itemTable,
            UUID itemId,
            Map<String, Object> body,
            String execNo) {
        Map<String, Object> item = loadItem(jdbc, itemTable, itemId);
        UUID execId = (UUID) item.get("execution_id");
        assertHeaderEditable(jdbc, execTable, execId);
        String st = Objects.toString(item.get("status"), "");
        if ("confirmed".equals(st)) {
            throw new BizException(400, "明细已确认");
        }
        String channel = OpsClientChannel.of(body);
        String userId = TenantContext.getUserId();
        String name = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        if (!"completed".equals(st)) {
            jdbc.update("""
                    UPDATE %s SET status='completed',
                    overall_result=COALESCE(overall_result, 'pass'),
                    execution_channel=COALESCE(execution_channel, ?),
                    end_time=COALESCE(end_time, NOW()),
                    executor_id=COALESCE(executor_id, ?::uuid),
                    executor_name=COALESCE(executor_name, ?),
                    row_version=COALESCE(row_version,1)+1, updated_at=NOW()
                    WHERE id=?::uuid AND status <> 'confirmed'
                    """.formatted(itemTable), channel, userId, name, itemId);
            docLog.event(module, "execution", execId, execNo, "auto_complete_before_confirm", channel, itemId.toString());
        }
        jdbc.update("""
                UPDATE %s SET status='confirmed', confirm_channel=?,
                confirmed_by=?::uuid, confirmed_by_name=?, confirmed_at=NOW(),
                row_version=COALESCE(row_version,1)+1, updated_at=NOW()
                WHERE id=?::uuid AND status='completed'
                """.formatted(itemTable), channel, userId, name, itemId);
        docLog.event(module, "execution", execId, execNo, "confirm_item", channel, itemId.toString());
    }

    /** 未确认明细可软删 */
    public static void deleteItem(
            JdbcTemplate jdbc,
            DocChangeLogService docLog,
            String module,
            String execTable,
            String itemTable,
            UUID itemId,
            Map<String, Object> body,
            String execNo) {
        Map<String, Object> item = loadItem(jdbc, itemTable, itemId);
        UUID execId = (UUID) item.get("execution_id");
        assertHeaderEditable(jdbc, execTable, execId);
        if ("confirmed".equals(Objects.toString(item.get("status"), ""))) {
            throw new BizException(400, "已确认明细不可删除");
        }
        String channel = OpsClientChannel.of(body);
        SoftDeleteSupport.softDelete(jdbc, itemTable, itemId.toString(), channel);
        docLog.event(module, "execution", execId, execNo, "delete_item", channel, itemId.toString());
    }

    public static void assertAllItemsConfirmed(JdbcTemplate jdbc, String itemTable, UUID execId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1)::int FROM %s
                WHERE execution_id=?::uuid AND COALESCE(is_deleted,0)=0 AND status <> 'confirmed'
                """.formatted(itemTable), Integer.class, execId);
        if (n != null && n > 0) {
            throw new BizException(400, "存在未确认明细，不可审核");
        }
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(1)::int FROM %s
                WHERE execution_id=?::uuid AND COALESCE(is_deleted,0)=0
                """.formatted(itemTable), Integer.class, execId);
        if (total == null || total == 0) {
            throw new BizException(400, "无设备明细，不可审核");
        }
    }
}
