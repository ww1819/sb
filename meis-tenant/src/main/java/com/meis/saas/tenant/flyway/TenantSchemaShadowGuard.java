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
 * 检测租户 schema 缺表但 public 有同名表的「串写」风险，并在启动时强制补齐 V1 表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSchemaShadowGuard {

    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)^CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)");

    private final JdbcTemplate jdbc;
    private final SchemaTableEnsuring schemaTableEnsuring;

    public void ensureNoShadowGaps(String schemaName) {
        validateSchema(schemaName);
        List<String> v1Tables = loadV1TableNames();
        List<String> missing = findMissingTables(schemaName, v1Tables);
        if (missing.isEmpty()) {
            log.info("Schema {}: all {} V1 tables present", schemaName, v1Tables.size());
            return;
        }

        List<String> shadowRisks = missing.stream()
                .filter(this::existsInPublic)
                .toList();
        if (!shadowRisks.isEmpty()) {
            log.warn("Schema {}: {} tables missing but exist in public (runtime may write wrong schema): {}",
                    schemaName, shadowRisks.size(), shadowRisks);
        }

        log.warn("Schema {}: missing {} V1 tables, re-running SchemaTableEnsuring", schemaName, missing.size());
        schemaTableEnsuring.ensureFromMigrations(schemaName);

        List<String> stillMissing = findMissingTables(schemaName, v1Tables);
        if (!stillMissing.isEmpty()) {
            throw new IllegalStateException(
                    "Tenant schema " + schemaName + " still missing tables after ensure: " + stillMissing);
        }
        log.info("Schema {}: shadow gap resolved, {} tables ensured", schemaName, v1Tables.size());
    }

    private List<String> findMissingTables(String schemaName, List<String> expected) {
        List<String> missing = new ArrayList<>();
        for (String table : expected) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = ? AND table_name = ? AND table_type = 'BASE TABLE'",
                    Long.class, schemaName, table);
            if (count == null || count == 0) {
                missing.add(table);
            }
        }
        return missing;
    }

    private boolean existsInPublic(String table) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = ? AND table_type = 'BASE TABLE'",
                Long.class, table);
        return count != null && count > 0;
    }

    private static List<String> loadV1TableNames() {
        try {
            Resource res = new PathMatchingResourcePatternResolver()
                    .getResource("classpath:db/migrations/tenant/V1__tables.sql");
            if (!res.exists()) {
                throw new IllegalStateException("V1__tables.sql not found");
            }
            List<String> tables = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = CREATE_TABLE.matcher(line.trim());
                    if (m.find()) {
                        tables.add(m.group(1).toLowerCase(Locale.ROOT));
                    }
                }
            }
            return tables;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read V1 table list", e);
        }
    }

    private static void validateSchema(String schemaName) {
        if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName);
        }
    }
}
