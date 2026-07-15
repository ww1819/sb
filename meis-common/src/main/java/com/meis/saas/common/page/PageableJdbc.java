package com.meis.saas.common.page;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PageableJdbc {
    private PageableJdbc() {}

    public static PageResult<Map<String, Object>> query(JdbcTemplate jdbc, String table, PageQuery q) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, table, null));
        List<Object> args = new ArrayList<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            appendKeywordFilter(jdbc, table, where, args, q.getKeyword().trim());
        }
        q.getFilters().forEach((k, v) -> {
            if (v == null || v.isBlank()) return;
            if (!k.matches("^[a-z][a-z0-9_]*$")) return;
            // 树选节点：本级 + 直接下级（避免叶子节点右侧无数据）
            if ("tree_node_id".equals(k)) {
                if (TableColumnCache.hasColumn(jdbc, table, "parent_id")) {
                    where.append(" AND (id = ?::uuid OR parent_id = ?::uuid) ");
                    args.add(v);
                    args.add(v);
                } else {
                    where.append(" AND id = ?::uuid ");
                    args.add(v);
                }
                return;
            }
            boolean uuidCol = "id".equals(k) || k.endsWith("_id") || k.endsWith("_by");
            where.append(" AND ").append(k).append(uuidCol ? " = ?::uuid " : " = ? ");
            args.add(v);
        });
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.limit());
        pageArgs.add(q.offset());
        String orderBy = resolveOrderBy(jdbc, table, q);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + where + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return PageResult.of(rows, total != null ? total : 0, q.getPage(), q.getSize());
    }

    /** 仅允许表内已有列名，防止注入；默认 created_at DESC */
    static String resolveOrderBy(JdbcTemplate jdbc, String table, PageQuery q) {
        String sortBy = q.getSortBy();
        String sortOrder = q.getSortOrder();
        if (sortBy == null || sortBy.isBlank() || sortOrder == null || sortOrder.isBlank()) {
            return "created_at DESC NULLS LAST";
        }
        if (!sortBy.matches("^[a-z][a-z0-9_]*$")) {
            return "created_at DESC NULLS LAST";
        }
        if (!TableColumnCache.hasColumn(jdbc, table, sortBy)) {
            return "created_at DESC NULLS LAST";
        }
        String dir = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        return sortBy + " " + dir + " NULLS LAST";
    }

    private static void appendKeywordFilter(
            JdbcTemplate jdbc, String table, StringBuilder where, List<Object> args, String keyword) {
        String[] cols = keywordColumns(table);
        if (cols == null) {
            where.append(" AND (CAST(id AS TEXT) ILIKE ?) ");
            args.add("%" + keyword + "%");
            return;
        }
        Set<String> existing = TableColumnCache.columns(jdbc, table);
        List<String> parts = new ArrayList<>();
        String kw = "%" + keyword + "%";
        for (String col : cols) {
            if (!existing.contains(col)) continue;
            parts.add("COALESCE(CAST(" + col + " AS TEXT), '') ILIKE ?");
            args.add(kw);
        }
        if (parts.isEmpty()) {
            where.append(" AND (CAST(id AS TEXT) ILIKE ?) ");
            args.add(kw);
            return;
        }
        where.append(" AND (").append(String.join(" OR ", parts)).append(") ");
    }

    private static String[] keywordColumns(String table) {
        return switch (table) {
            case "supplier" -> new String[]{"supplier_code", "supplier_name", "pinyin_code", "contact_person", "contact_phone"};
            case "manufacturer" -> new String[]{"manufacturer_code", "manufacturer_name", "pinyin_code"};
            case "medical_device_category" -> new String[]{"category_code", "category_name"};
            case "asset_category" -> new String[]{"category_code", "category_name"};
            case "finance_category" -> new String[]{"finance_code", "finance_name"};
            case "unit_dict" -> new String[]{"unit_code", "unit_name"};
            case "warehouse" -> new String[]{"warehouse_code", "warehouse_name"};
            case "campus" -> new String[]{"campus_code", "campus_name"};
            default -> null;
        };
    }
}
