package com.meis.saas.common.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 缓存当前 schema 下表的列名，避免重复查 information_schema。 */
public final class TableColumnCache {
    private static final ConcurrentHashMap<String, Set<String>> CACHE = new ConcurrentHashMap<>();

    private TableColumnCache() {}

    public static Set<String> columns(JdbcTemplate jdbc, String table) {
        String key = cacheKey(jdbc, table);
        return CACHE.computeIfAbsent(key, k -> loadColumns(jdbc, table));
    }

    public static boolean hasColumn(JdbcTemplate jdbc, String table, String column) {
        return columns(jdbc, table).contains(column);
    }

    public static boolean hasTable(JdbcTemplate jdbc, String table) {
        return !columns(jdbc, table).isEmpty();
    }

    public static void invalidate() {
        CACHE.clear();
    }

    private static String cacheKey(JdbcTemplate jdbc, String table) {
        String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
        return schema + "." + table;
    }

    private static Set<String> loadColumns(JdbcTemplate jdbc, String table) {
        var cols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?",
                String.class, table);
        return Collections.unmodifiableSet(new HashSet<>(cols));
    }
}
