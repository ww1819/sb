package com.meis.saas.common.persistence;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * 软删除与审计字段填充。
 * <p>标准七列（强制）：{@code created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by}</p>
 * <p>配套姓名快照（附录 W.5）：{@code created_by_name, updated_by_name, deleted_by_name}</p>
 * <ul>
 *   <li>删除：写 is_deleted=1、deleted_at / deleted_by / deleted_by_name，有 is_active 时同步置 false</li>
 *   <li>查询：默认 is_deleted=0（兼容仅有 deleted_at 的旧表）</li>
 *   <li>新建：若命中已软删行的唯一键，恢复为未删除并走 UPDATE</li>
 *   <li>UPDATE：id / created_* / updated_* 禁止进入业务 SET，updated_* 仅由 appendUpdateAuditSets 写入</li>
 * </ul>
 */
public final class SoftDeleteSupport {
    private SoftDeleteSupport() {}

    /**
     * 通用 UPDATE 禁止写入的列（避免与 appendUpdateAuditSets 重复，或篡改主键/创建人）。
     * 软删恢复字段 is_deleted / deleted_* 允许在 prepareRestore 后写入。
     */
    public static final Set<String> UPDATE_SKIP_COLUMNS = Set.of(
            "id", "created_at", "created_by", "created_by_name",
            "updated_at", "updated_by", "updated_by_name");

    /** 普通 PUT 时从请求体剔除，防止客户端伪造软删状态或审计字段。 */
    public static final Set<String> CLIENT_UPDATE_STRIP = Set.of(
            "id", "created_at", "created_by", "created_by_name",
            "updated_at", "updated_by", "updated_by_name",
            "deleted_at", "deleted_by", "deleted_by_name", "is_deleted");

    public static boolean isUpdateSkipColumn(String column) {
        return column != null && UPDATE_SKIP_COLUMNS.contains(column);
    }

    public static void stripClientUpdateFields(Map<String, Object> body) {
        if (body == null) return;
        for (String col : CLIENT_UPDATE_STRIP) {
            body.remove(col);
        }
    }

    /**
     * 工作人员展示名：优先 real_name，空则 username（附录 W.5）。
     */
    public static String resolveUserDisplayName(JdbcTemplate jdbc, Object userId) {
        if (jdbc == null || userId == null) return null;
        String id = String.valueOf(userId).trim();
        if (id.isEmpty() || "null".equalsIgnoreCase(id)) return null;
        try {
            List<String> names = jdbc.query(
                    "SELECT COALESCE(NULLIF(TRIM(real_name), ''), username) FROM sys_user WHERE id = ?::uuid",
                    (rs, rowNum) -> rs.getString(1), id);
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean supportsSoftDelete(JdbcTemplate jdbc, String table) {
        return hasIsDeleted(jdbc, table) || TableColumnCache.hasColumn(jdbc, table, "deleted_at");
    }

    public static boolean hasIsDeleted(JdbcTemplate jdbc, String table) {
        return TableColumnCache.hasColumn(jdbc, table, "is_deleted");
    }

    public static String notDeletedClause(JdbcTemplate jdbc, String table, String alias) {
        String prefix = alias != null && !alias.isBlank() ? alias + "." : "";
        if (hasIsDeleted(jdbc, table)) {
            return " AND " + prefix + "is_deleted = 0 ";
        }
        if (TableColumnCache.hasColumn(jdbc, table, "deleted_at")) {
            return " AND " + prefix + "deleted_at IS NULL ";
        }
        return "";
    }

    public static int softDelete(JdbcTemplate jdbc, String table, String id) {
        return softDelete(jdbc, table, id, null);
    }

    /** @param client 可选；表有 delete_channel 时写入（OPS.16.10） */
    public static int softDelete(JdbcTemplate jdbc, String table, String id, String client) {
        Set<String> cols = TableColumnCache.columns(jdbc, table);
        if (!cols.contains("deleted_at") && !cols.contains("is_deleted")) {
            throw new BizException(500,
                    "表 " + table + " 缺少软删列，拒绝物理删除；请按附录 G.0 在 V1/R__ 补齐 is_deleted");
        }
        String userId = TenantContext.getUserId();
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (cols.contains("is_deleted")) {
            sets.add("is_deleted = 1");
        }
        if (cols.contains("deleted_at")) {
            sets.add("deleted_at = NOW()");
        }
        if (cols.contains("deleted_by")) {
            sets.add("deleted_by = ?::uuid");
            args.add(userId);
        }
        if (cols.contains("deleted_by_name")) {
            sets.add("deleted_by_name = ?");
            args.add(resolveUserDisplayName(jdbc, userId));
        }
        if (cols.contains("is_active")) {
            sets.add("is_active = FALSE");
        }
        if (cols.contains("delete_channel")) {
            sets.add("delete_channel = ?");
            args.add(com.meis.saas.common.ops.OpsClientChannel.normalize(client));
        }
        appendUpdateAuditSets(jdbc, cols, sets, args);
        args.add(id);
        String notDeletedWhere = cols.contains("is_deleted")
                ? "is_deleted = 0"
                : "deleted_at IS NULL";
        return jdbc.update(
                "UPDATE " + table + " SET " + String.join(", ", sets) + " WHERE id = ?::uuid AND " + notDeletedWhere,
                args.toArray());
    }

    public static void applyInsertAudit(JdbcTemplate jdbc, String table, Map<String, Object> body) {
        Set<String> cols = TableColumnCache.columns(jdbc, table);
        String userId = TenantContext.getUserId();
        if (cols.contains("created_by") && !body.containsKey("created_by") && userId != null) {
            body.put("created_by", userId);
        }
        if (cols.contains("created_by_name") && body.get("created_by") != null
                && (body.get("created_by_name") == null || String.valueOf(body.get("created_by_name")).isBlank())) {
            body.put("created_by_name", resolveUserDisplayName(jdbc, body.get("created_by")));
        }
        // updated_by / updated_at 不在 INSERT body 中预填：INSERT 可省略；UPDATE 恢复由 appendUpdateAuditSets 写入，避免 SET 列重复
        if (cols.contains("is_deleted") && !body.containsKey("is_deleted")) {
            body.put("is_deleted", 0);
        }
        if (cols.contains("deleted_at")) {
            body.put("deleted_at", null);
        }
        if (cols.contains("deleted_by")) {
            body.put("deleted_by", null);
        }
        if (cols.contains("deleted_by_name")) {
            body.put("deleted_by_name", null);
        }
        if (cols.contains("is_active") && !body.containsKey("is_active")) {
            body.put("is_active", true);
        }
    }

    /**
     * 新建入口：填充插入审计，并查找可恢复的软删行。
     * @return 软删行 id（应 UPDATE 恢复）；empty 表示应 INSERT
     */
    public static Optional<String> prepareCreate(JdbcTemplate jdbc, String table, Map<String, Object> body) {
        applyInsertAudit(jdbc, table, body);
        return findSoftDeletedId(jdbc, table, body);
    }

    public static String currentUserId() {
        return TenantContext.getUserId();
    }

    /** UPDATE SET 片段：updated_at=NOW()[, updated_by=?::uuid]；有 updated_by 时调用方需追加 userId 参数。 */
    public static String updatedAuditSetSql(JdbcTemplate jdbc, String table) {
        Set<String> cols = TableColumnCache.columns(jdbc, table);
        List<String> parts = new ArrayList<>();
        if (cols.contains("updated_at")) {
            parts.add("updated_at = NOW()");
        }
        if (cols.contains("updated_by") && TenantContext.getUserId() != null) {
            parts.add("updated_by = ?::uuid");
        }
        if (cols.contains("updated_by_name") && TenantContext.getUserId() != null) {
            parts.add("updated_by_name = ?");
        }
        return String.join(", ", parts);
    }

    /** 向 UPDATE 的 SET 列表追加 updated_at / updated_by[/name]。调用方勿再把这些列放入 sets。 */
    public static void appendUpdateAuditSets(JdbcTemplate jdbc, Set<String> cols, List<String> sets, List<Object> args) {
        if (cols.contains("updated_at")) {
            sets.add("updated_at = NOW()");
        }
        String userId = TenantContext.getUserId();
        if (cols.contains("updated_by") && userId != null) {
            sets.add("updated_by = ?::uuid");
            args.add(userId);
            if (cols.contains("updated_by_name")) {
                sets.add("updated_by_name = ?");
                args.add(resolveUserDisplayName(jdbc, userId));
            }
        }
    }

    /** @deprecated 无 JdbcTemplate 时无法写 updated_by_name；请改用带 jdbc 的重载。 */
    @Deprecated
    public static void appendUpdateAuditSets(Set<String> cols, List<String> sets, List<Object> args) {
        appendUpdateAuditSets(null, cols, sets, args);
    }

    /** 手工 SQL 恢复软删行时追加的 SET 片段（不含尾逗号）；updated_by 需另绑参。 */
    public static String restoreSetSql(Set<String> cols, boolean includeUpdatedByPlaceholder) {
        List<String> parts = new ArrayList<>();
        if (cols.contains("is_deleted")) parts.add("is_deleted = 0");
        if (cols.contains("deleted_at")) parts.add("deleted_at = NULL");
        if (cols.contains("deleted_by")) parts.add("deleted_by = NULL");
        if (cols.contains("deleted_by_name")) parts.add("deleted_by_name = NULL");
        if (cols.contains("updated_at")) parts.add("updated_at = NOW()");
        if (includeUpdatedByPlaceholder && cols.contains("updated_by")) {
            parts.add("updated_by = ?::uuid");
        }
        return String.join(", ", parts);
    }

    /** 恢复软删行时追加到 UPDATE SET 的列（无占位参数）。 */
    public static void appendRestoreSets(Set<String> cols, List<String> sets) {
        if (cols.contains("is_deleted")) {
            sets.add("is_deleted = 0");
        }
        if (cols.contains("deleted_at")) {
            sets.add("deleted_at = NULL");
        }
        if (cols.contains("deleted_by")) {
            sets.add("deleted_by = NULL");
        }
        if (cols.contains("deleted_by_name")) {
            sets.add("deleted_by_name = NULL");
        }
    }

    /**
     * 软删恢复前写入恢复标记，并剔除会导致 SET 重复的审计字段。
     */
    public static void prepareRestore(Map<String, Object> body, Set<String> cols) {
        body.remove("id");
        body.remove("created_at");
        body.remove("created_by");
        body.remove("created_by_name");
        body.remove("updated_at");
        body.remove("updated_by");
        body.remove("updated_by_name");
        if (cols.contains("is_deleted")) {
            body.put("is_deleted", 0);
        }
        if (cols.contains("deleted_at")) {
            body.put("deleted_at", null);
        }
        if (cols.contains("deleted_by")) {
            body.put("deleted_by", null);
        }
        if (cols.contains("deleted_by_name")) {
            body.put("deleted_by_name", null);
        }
        if (cols.contains("is_active")) {
            body.put("is_active", true);
        }
    }

    /** 按唯一键查找已软删行 id，用于新建时恢复。 */
    public static Optional<String> findSoftDeletedId(JdbcTemplate jdbc, String table, Map<String, Object> body) {
        if (!supportsSoftDelete(jdbc, table)) return Optional.empty();
        for (List<String> group : UniqueConstraintCache.uniqueColumnGroups(jdbc, table)) {
            if (!bodyHasValues(body, group)) continue;
            StringBuilder where = new StringBuilder(" WHERE ");
            where.append(deletedWhereClause(jdbc, table));
            List<Object> args = new ArrayList<>();
            for (String col : group) {
                where.append(" AND ").append(col).append(" = ").append(bindPlaceholder(col, body.get(col)));
                args.add(body.get(col));
            }
            var rows = jdbc.queryForList("SELECT id FROM " + table + where + " LIMIT 1", args.toArray());
            if (!rows.isEmpty()) {
                return Optional.of(String.valueOf(rows.get(0).get("id")));
            }
        }
        return Optional.empty();
    }

    private static String deletedWhereClause(JdbcTemplate jdbc, String table) {
        if (hasIsDeleted(jdbc, table)) {
            return "is_deleted = 1";
        }
        return "deleted_at IS NOT NULL";
    }

    private static boolean bodyHasValues(Map<String, Object> body, List<String> columns) {
        for (String col : columns) {
            Object v = body.get(col);
            if (v == null || (v instanceof String s && s.isBlank())) return false;
        }
        return true;
    }

    static String bindPlaceholder(String column, Object value) {
        if (column.equals("id") || column.endsWith("_id") || column.endsWith("_by")) return "?::uuid";
        if (value instanceof UUID) return "?::uuid";
        return "?";
    }
}
