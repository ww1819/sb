package com.meis.saas.common.persistence;

import com.meis.saas.common.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * 主从明细 upsert：保留明细 id；按业务键（device_id）撞已软删行时恢复；未保留行软删。
 * 约定包 §6.1 / §6.6 / AST-DUP-IDEMP-01 / AST-DUP-03。
 */
public final class DetailLineUpsertSupport {
    private DetailLineUpsertSupport() {}

    public static UUID parseUuid(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 解析本行应使用的明细 id：优先请求 id（同主单下存在）；否则按 device_id 找未删/已软删行；否则新 UUID。
     */
    public static UUID resolveLineId(JdbcTemplate jdbc, String table, String parentCol, UUID parentId,
                                     Object itemIdObj, Object deviceIdObj) {
        UUID itemId = parseUuid(itemIdObj);
        if (itemId != null) {
            var byId = jdbc.queryForList(
                    "SELECT id FROM " + table + " WHERE id = ?::uuid AND " + parentCol + " = ?::uuid LIMIT 1",
                    itemId, parentId);
            if (!byId.isEmpty()) {
                return itemId;
            }
        }
        UUID deviceId = parseUuid(deviceIdObj);
        if (deviceId != null) {
            var alive = jdbc.queryForList(
                    "SELECT id FROM " + table + " WHERE " + parentCol + " = ?::uuid AND device_id = ?::uuid"
                            + SoftDeleteSupport.notDeletedClause(jdbc, table, null)
                            + " ORDER BY created_at ASC NULLS LAST LIMIT 1",
                    parentId, deviceId);
            if (!alive.isEmpty()) {
                return UUID.fromString(String.valueOf(alive.get(0).get("id")));
            }
            var deleted = jdbc.queryForList(
                    "SELECT id FROM " + table + " WHERE " + parentCol + " = ?::uuid AND device_id = ?::uuid"
                            + " AND COALESCE(is_deleted, 0) = 1"
                            + " ORDER BY deleted_at DESC NULLS LAST LIMIT 1",
                    parentId, deviceId);
            if (!deleted.isEmpty()) {
                return UUID.fromString(String.valueOf(deleted.get(0).get("id")));
            }
        }
        return itemId != null ? itemId : UUID.randomUUID();
    }

    /** 行是否仍存活（未软删）。 */
    public static boolean isAlive(JdbcTemplate jdbc, String table, UUID id) {
        return !jdbc.queryForList(
                "SELECT 1 FROM " + table + " WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, table, null), id).isEmpty();
    }

    /** 行是否存在于该主单下（含已软删）。 */
    public static boolean existsUnderParent(JdbcTemplate jdbc, String table, String parentCol,
                                            UUID parentId, UUID id) {
        return !jdbc.queryForList(
                "SELECT 1 FROM " + table + " WHERE id = ?::uuid AND " + parentCol + " = ?::uuid LIMIT 1",
                id, parentId).isEmpty();
    }

    /** 软删主单下未出现在 keep 中的存活明细。 */
    public static void softDeleteNotIn(JdbcTemplate jdbc, String table, String parentCol,
                                       UUID parentId, Set<UUID> keep) {
        String sql = "SELECT id FROM " + table + " WHERE " + parentCol + " = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, table, null);
        List<Object> args = new ArrayList<>();
        args.add(parentId);
        if (keep != null && !keep.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(keep.size(), "?::uuid"));
            sql += " AND id NOT IN (" + placeholders + ")";
            args.addAll(keep);
        }
        for (Map<String, Object> row : jdbc.queryForList(sql, args.toArray())) {
            SoftDeleteSupport.softDelete(jdbc, table, String.valueOf(row.get("id")));
        }
    }

    /** UPDATE 前若已软删则追加恢复列。 */
    public static void appendRestoreIfNeeded(JdbcTemplate jdbc, String table, UUID id,
                                             List<String> sets) {
        if (!isAlive(jdbc, table, id)) {
            SoftDeleteSupport.appendRestoreSets(TableColumnCache.columns(jdbc, table), sets);
        }
    }

    public static Integer parseRowVersion(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 有 row_version 列则追加自增。 */
    public static void appendRowVersionBump(JdbcTemplate jdbc, String table, List<String> sets) {
        if (TableColumnCache.hasColumn(jdbc, table, "row_version")) {
            sets.add("row_version = COALESCE(row_version, 1) + 1");
        }
    }

    /**
     * 执行明细 UPDATE；若请求带 expectedRv 则乐观锁，冲突 409。
     * @param updateSql SET 子句前的完整 UPDATE…SET…（不含 WHERE）
     */
    public static void updateWithOptionalLock(JdbcTemplate jdbc, String updateSql, List<Object> args,
                                              UUID id, Integer expectedRv) {
        String where = " WHERE id = ?::uuid";
        List<Object> all = new ArrayList<>(args);
        all.add(id);
        if (expectedRv != null) {
            where += " AND COALESCE(row_version, 1) = ?";
            all.add(expectedRv);
        }
        int n = jdbc.update(updateSql + where, all.toArray());
        if (expectedRv != null && n == 0) {
            throw new BizException(409, "明细已被他人修改，请刷新后重试");
        }
    }

    /** 活跃明细业务键唯一冲突 → 409。 */
    public static void rethrowDeviceUniqueConflict(RuntimeException e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains("_device_alive")) {
                throw new BizException(409, "同一单据内设备不能重复");
            }
            cur = cur.getCause();
        }
        if (e instanceof DataIntegrityViolationException dive) {
            throw dive;
        }
        throw e;
    }
}
