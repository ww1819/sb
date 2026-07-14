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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 老租户更新时幂等执行 V1/V2 结构脚本：
 * <ul>
 *   <li>没有的表 → {@code CREATE TABLE IF NOT EXISTS} 创建（含完整字段）</li>
 *   <li>已有的表 → 不改表结构（缺列由 {@code R__columns_audit.sql} / {@code R__columns_biz.sql} 逐列补）</li>
 *   <li>不执行 COMMENT ON（空注释由 {@link SchemaCommentFiller} 补全，避免覆盖租户自定义）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaTableEnsuring {

    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)^CREATE\\s+TABLE\\s+(?!IF\\s+NOT\\s+EXISTS)");
    private static final Pattern CREATE_VIEW =
            Pattern.compile("(?i)^CREATE\\s+VIEW\\s+");
    private static final Pattern CREATE_UNIQUE_INDEX =
            Pattern.compile("(?i)^CREATE\\s+UNIQUE\\s+INDEX\\s+(?!IF\\s+NOT\\s+EXISTS)");
    private static final Pattern CREATE_INDEX =
            Pattern.compile("(?i)^CREATE\\s+INDEX\\s+(?!IF\\s+NOT\\s+EXISTS)");
    /** 将 CREATE TABLE IF NOT EXISTS name 限定到指定 schema，避免 public 同名表导致跳过建表。 */
    private static final Pattern QUALIFY_TABLE =
            Pattern.compile("(?i)^(CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+)(?!\\w+\\.)(\\w+)");
    /** 将 CREATE INDEX ... ON name 限定到指定 schema。 */
    private static final Pattern QUALIFY_INDEX_ON =
            Pattern.compile("(?i)^(CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+IF\\s+NOT\\s+EXISTS\\s+\\w+\\s+ON\\s+)(?!\\w+\\.)(\\w+)");
    private static final Pattern QUALIFY_VIEW =
            Pattern.compile("(?i)^(CREATE\\s+OR\\s+REPLACE\\s+VIEW\\s+)(?!\\w+\\.)(\\w+)");

    private final JdbcTemplate jdbc;

    public void ensureFromMigrations(String schemaName) {
        validateSchema(schemaName);
        List<String> statements = new ArrayList<>();
        // 1) V1 全量建表（CREATE TABLE → IF NOT EXISTS，并 schema 限定）
        statements.addAll(loadIdempotentStatements("classpath:db/migrations/tenant/V1__tables.sql", schemaName));
        // 2) V2 索引
        statements.addAll(loadIdempotentStatements("classpath:db/migrations/tenant/V2__indexes.sql", schemaName));
        if (statements.isEmpty()) {
            log.warn("Schema {}: no structure statements loaded from V1/V2", schemaName);
            return;
        }
        ensureDatabaseExtensions();
        // 清理误写入 public 的租户表影子，避免 REFERENCES / IF NOT EXISTS 命中 public
        jdbc.execute("DROP TABLE IF EXISTS public.metrology_type CASCADE");
        // public 仅用于解析 uuid_generate_v4 等扩展函数；建表/索引已 schema 限定，不会误命中 public 同名对象
        String previousPath = jdbc.queryForObject("SHOW search_path", String.class);
        jdbc.execute("SET search_path TO " + schemaName + ", public");
        int ok = 0;
        int skip = 0;
        try {
            for (String sql : statements) {
                try {
                    jdbc.execute(sql);
                    ok++;
                } catch (Exception e) {
                    skip++;
                    log.warn("Schema {} skip statement: {} ({})", schemaName,
                            abbreviate(sql), e.getMessage());
                }
            }
        } finally {
            // 归还连接前恢复 search_path，避免污染连接池影响后续 Flyway
            if (previousPath != null && !previousPath.isBlank()) {
                jdbc.execute("SET search_path TO " + previousPath);
            } else {
                jdbc.execute("SET search_path TO DEFAULT");
            }
        }
        log.info("Schema {}: ensured tables/indexes before column sync (applied={}, skipped={})",
                schemaName, ok, skip);
    }

    private List<String> loadIdempotentStatements(String classpath, String schemaName) {
        List<String> raw = splitSql(readResource(classpath));
        List<String> out = new ArrayList<>();
        for (String stmt : raw) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) continue;
            String upper = trimmed.toUpperCase(Locale.ROOT);
            // 跳过注释语句，避免覆盖租户已有注释
            if (upper.startsWith("COMMENT ON")) continue;
            if (upper.startsWith("ALTER TABLE")) continue;
            String idempotent = toIdempotent(trimmed, schemaName);
            if (idempotent != null) {
                out.add(idempotent);
            }
        }
        return out;
    }

    private static String toIdempotent(String sql, String schemaName) {
        String upper = sql.toUpperCase(Locale.ROOT);
        if (upper.startsWith("CREATE TABLE")) {
            String s = CREATE_TABLE.matcher(sql).replaceFirst("CREATE TABLE IF NOT EXISTS ");
            s = qualify(QUALIFY_TABLE, s, schemaName);
            // REFERENCES bare_table(...) 必须限定到租户 schema，否则会绑到 public 同名表
            return qualifyReferences(s, schemaName);
        }
        if (upper.startsWith("CREATE VIEW") || upper.startsWith("CREATE OR REPLACE VIEW")) {
            String s = CREATE_VIEW.matcher(sql).replaceFirst("CREATE OR REPLACE VIEW ");
            return qualify(QUALIFY_VIEW, s, schemaName);
        }
        if (upper.startsWith("CREATE UNIQUE INDEX")) {
            String s = CREATE_UNIQUE_INDEX.matcher(sql).replaceFirst("CREATE UNIQUE INDEX IF NOT EXISTS ");
            return qualify(QUALIFY_INDEX_ON, s, schemaName);
        }
        if (upper.startsWith("CREATE INDEX")) {
            String s = CREATE_INDEX.matcher(sql).replaceFirst("CREATE INDEX IF NOT EXISTS ");
            return qualify(QUALIFY_INDEX_ON, s, schemaName);
        }
        // 其它语句（如无意义的）不执行
        return null;
    }

    private static String qualify(Pattern pattern, String sql, String schemaName) {
        Matcher m = pattern.matcher(sql);
        if (m.find()) {
            return m.replaceFirst(Matcher.quoteReplacement(m.group(1) + schemaName + "." + m.group(2)));
        }
        return sql;
    }

    private static final Pattern UNQUALIFIED_REFERENCES =
            Pattern.compile("(?i)REFERENCES\\s+(?!\\w+\\.)(\\w+)\\s*\\(");

    private static String qualifyReferences(String sql, String schemaName) {
        Matcher m = UNQUALIFIED_REFERENCES.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("REFERENCES " + schemaName + "." + m.group(1) + "("));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** uuid-ossp / pgcrypto 为库级扩展，须在租户建表前确保可用（V1 头部 DDL 不会进入 statements）。 */
    private void ensureDatabaseExtensions() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS \"pgcrypto\"");
    }

    private static String readResource(String location) {
        try {
            Resource res = new PathMatchingResourcePatternResolver().getResource(location);
            if (!res.exists()) return "";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + location, e);
        }
    }

    /** 按分号拆分，忽略行内注释与空行。 */
    static List<String> splitSql(String script) {
        List<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            // 简单按行尾 ; 切分（V1/V2 无过程体）
            if (trimmed.endsWith(";")) {
                String stmt = current.toString().trim();
                if (stmt.endsWith(";")) {
                    stmt = stmt.substring(0, stmt.length() - 1).trim();
                }
                if (!stmt.isEmpty()) {
                    list.add(stmt);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            list.add(tail);
        }
        return list;
    }

    private static String abbreviate(String sql) {
        String one = sql.replaceAll("\\s+", " ").trim();
        return one.length() > 120 ? one.substring(0, 120) + "..." : one;
    }

    private static void validateSchema(String schemaName) {
        if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName);
        }
    }
}
