package com.meis.saas.common.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** 缓存表上的 UNIQUE 约束列组合（不含主键）。 */
public final class UniqueConstraintCache {
    private static final ConcurrentHashMap<String, List<List<String>>> CACHE = new ConcurrentHashMap<>();

    private UniqueConstraintCache() {}

    public static List<List<String>> uniqueColumnGroups(JdbcTemplate jdbc, String table) {
        String key = jdbc.queryForObject("SELECT current_schema()", String.class) + "." + table;
        return CACHE.computeIfAbsent(key, k -> load(jdbc, table));
    }

    public static void invalidate() {
        CACHE.clear();
    }

    private static List<List<String>> load(JdbcTemplate jdbc, String table) {
        var rows = jdbc.queryForList("""
                SELECT tc.constraint_name,
                       array_agg(kcu.column_name ORDER BY kcu.ordinal_position) AS cols
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_schema = kcu.constraint_schema
                 AND tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                 AND tc.table_name = kcu.table_name
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = ?
                  AND tc.constraint_type = 'UNIQUE'
                GROUP BY tc.constraint_name
                """, table);
        List<List<String>> result = new ArrayList<>();
        for (var row : rows) {
            result.add(parsePgArray(row.get("cols")));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> parsePgArray(Object cols) {
        if (cols == null) return List.of();
        if (cols instanceof String[] arr) return List.of(arr);
        if (cols instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        if (cols instanceof Array sqlArray) {
            try {
                Object arr = sqlArray.getArray();
                if (arr instanceof String[] s) return List.of(s);
                if (arr instanceof Object[] o) {
                    return Arrays.stream(o).map(String::valueOf).toList();
                }
            } catch (SQLException ignored) {
                // fall through
            }
        }
        String s = cols.toString();
        if (s.startsWith("{") && s.endsWith("}")) {
            return Arrays.stream(s.substring(1, s.length() - 1).split(","))
                    .map(String::trim)
                    .filter(x -> !x.isEmpty())
                    .toList();
        }
        return List.of(s);
    }
}
