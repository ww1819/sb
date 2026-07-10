package com.meis.saas.tenant.flyway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫描租户 schema：仅当表/字段/视图/索引注释为空时，用 V1/V2 中的默认注释补全。
 * 已有注释一律不覆盖，避免不同租户对同一字段自定义含义被冲掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaCommentFiller {

    private static final Pattern COMMENT_LINE = Pattern.compile(
            "^COMMENT ON (TABLE|COLUMN|VIEW|INDEX)\\s+([\\w.]+)\\s+IS\\s+'((?:''|[^'])*)'\\s*;\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;

    private volatile Map<String, DefaultComment> defaults;

    public void fillEmptyComments(String schemaName) {
        validateSchema(schemaName);
        Map<String, DefaultComment> catalog = loadDefaults();
        int applied = 0;
        applied += fillTables(schemaName, catalog);
        applied += fillViews(schemaName, catalog);
        applied += fillColumns(schemaName, catalog);
        applied += fillIndexes(schemaName, catalog);
        if (applied > 0) {
            log.info("Schema {}: filled {} empty comment(s)", schemaName, applied);
        } else {
            log.debug("Schema {}: no empty comments to fill", schemaName);
        }
    }

    private int fillTables(String schema, Map<String, DefaultComment> catalog) {
        List<String> empty = jdbc.queryForList("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind = 'r' AND NOT c.relispartition
                  AND (obj_description(c.oid, 'pg_class') IS NULL
                       OR btrim(obj_description(c.oid, 'pg_class')) = '')
                """, String.class, schema);
        int n = 0;
        for (String table : empty) {
            DefaultComment def = catalog.get(key("TABLE", table));
            if (def == null) continue;
            apply("TABLE", schema + "." + table, def.comment());
            n++;
        }
        return n;
    }

    private int fillViews(String schema, Map<String, DefaultComment> catalog) {
        List<String> empty = jdbc.queryForList("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind = 'v'
                  AND (obj_description(c.oid, 'pg_class') IS NULL
                       OR btrim(obj_description(c.oid, 'pg_class')) = '')
                """, String.class, schema);
        int n = 0;
        for (String view : empty) {
            DefaultComment def = catalog.get(key("VIEW", view));
            if (def == null) continue;
            apply("VIEW", schema + "." + view, def.comment());
            n++;
        }
        return n;
    }

    private int fillColumns(String schema, Map<String, DefaultComment> catalog) {
        List<Map<String, Object>> empty = jdbc.queryForList("""
                SELECT c.relname AS rel, a.attname AS col
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
                WHERE n.nspname = ? AND c.relkind IN ('r', 'v')
                  AND (col_description(c.oid, a.attnum) IS NULL
                       OR btrim(col_description(c.oid, a.attnum)) = '')
                """, schema);
        int n = 0;
        for (Map<String, Object> row : empty) {
            String rel = String.valueOf(row.get("rel"));
            String col = String.valueOf(row.get("col"));
            DefaultComment def = catalog.get(key("COLUMN", rel + "." + col));
            if (def == null) continue;
            apply("COLUMN", schema + "." + rel + "." + col, def.comment());
            n++;
        }
        return n;
    }

    private int fillIndexes(String schema, Map<String, DefaultComment> catalog) {
        List<String> empty = jdbc.queryForList("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind = 'i'
                  AND (obj_description(c.oid, 'pg_class') IS NULL
                       OR btrim(obj_description(c.oid, 'pg_class')) = '')
                """, String.class, schema);
        int n = 0;
        for (String index : empty) {
            DefaultComment def = catalog.get(key("INDEX", index));
            if (def == null) continue;
            apply("INDEX", schema + "." + index, def.comment());
            n++;
        }
        return n;
    }

    private void apply(String kind, String qualifiedName, String comment) {
        String sql = "COMMENT ON " + kind + " " + qualifiedName + " IS '" + escapeLiteral(comment) + "'";
        jdbc.execute(sql);
    }

    private static String escapeLiteral(String comment) {
        return comment.replace("'", "''");
    }

    private static String key(String kind, String name) {
        return kind.toUpperCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
    }

    private Map<String, DefaultComment> loadDefaults() {
        Map<String, DefaultComment> cached = defaults;
        if (cached != null) return cached;
        synchronized (this) {
            if (defaults != null) return defaults;
            Map<String, DefaultComment> map = new LinkedHashMap<>();
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (String path : List.of(
                    "classpath:db/migrations/tenant/V1__tables.sql",
                    "classpath:db/migrations/tenant/V2__extensions.sql")) {
                try {
                    Resource res = resolver.getResource(path);
                    if (!res.exists()) continue;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            parseCommentLine(line.trim(), map);
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to load comment defaults from " + path, e);
                }
            }
            defaults = Collections.unmodifiableMap(map);
            log.info("Loaded {} default schema comments from V1/V2", defaults.size());
            return defaults;
        }
    }

    private static void parseCommentLine(String line, Map<String, DefaultComment> map) {
        if (!line.regionMatches(true, 0, "COMMENT ON", 0, 10)) return;
        Matcher m = COMMENT_LINE.matcher(line);
        if (!m.find()) return;
        String kind = m.group(1).toUpperCase(Locale.ROOT);
        String object = m.group(2).toLowerCase(Locale.ROOT);
        String comment = m.group(3).replace("''", "'");
        map.put(key(kind, object), new DefaultComment(kind, object, comment));
    }

    private static void validateSchema(String schemaName) {
        if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName);
        }
    }

    private record DefaultComment(String kind, String objectName, String comment) {}
}
