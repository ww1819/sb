package com.meis.saas.common.persistence;

import com.meis.saas.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * 软删除与审计字段填充。
 * <ul>
 *   <li>删除：写 is_deleted=1、deleted_at / deleted_by，有 is_active 时同步置 false</li>
 *   <li>查询：默认 is_deleted=0（兼容仅有 deleted_at 的旧表）</li>
 *   <li>新建：若命中已软删行的唯一键，恢复为未删除并走 UPDATE</li>
 * </ul>
 */
public final class SoftDeleteSupport {
    private SoftDeleteSupport() {}

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
        Set<String> cols = TableColumnCache.columns(jdbc, table);
        if (!cols.contains("deleted_at") && !cols.contains("is_deleted")) {
            return jdbc.update("DELETE FROM " + table + " WHERE id = ?::uuid", id);
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
        if (cols.contains("is_active")) {
            sets.add("is_active = FALSE");
        }
        appendUpdateAuditSets(cols, sets, args);
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
        if (cols.contains("updated_by") && !body.containsKey("updated_by") && userId != null) {
            body.put("updated_by", userId);
        }
        if (cols.contains("is_deleted") && !body.containsKey("is_deleted")) {
            body.put("is_deleted", 0);
        }
        if (cols.contains("deleted_at")) {
            body.put("deleted_at", null);
        }
        if (cols.contains("deleted_by")) {
            body.put("deleted_by", null);
        }
        if (cols.contains("is_active") && !body.containsKey("is_active")) {
            body.put("is_active", true);
        }
    }

    /** 向 UPDATE 的 SET 列表追加 updated_at / updated_by。 */
    public static void appendUpdateAuditSets(Set<String> cols, List<String> sets, List<Object> args) {
        if (cols.contains("updated_at")) {
            sets.add("updated_at = NOW()");
        }
        String userId = TenantContext.getUserId();
        if (cols.contains("updated_by") && userId != null) {
            sets.add("updated_by = ?::uuid");
            args.add(userId);
        }
    }

    /** 恢复软删行时追加到 UPDATE SET 的列。 */
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
    }

    public static void prepareRestore(Map<String, Object> body, Set<String> cols) {
        if (cols.contains("is_deleted")) {
            body.put("is_deleted", 0);
        }
        if (cols.contains("deleted_at")) {
            body.put("deleted_at", null);
        }
        if (cols.contains("deleted_by")) {
            body.put("deleted_by", null);
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
        if (column.equals("id") || column.endsWith("_id")) return "?::uuid";
        if (value instanceof UUID) return "?::uuid";
        return "?";
    }
}
