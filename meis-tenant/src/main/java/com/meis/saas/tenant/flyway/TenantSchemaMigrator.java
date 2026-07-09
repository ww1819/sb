package com.meis.saas.tenant.flyway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 各租户独立 schema 的 Flyway 迁移。
 * public schema 由 Spring Boot {@code spring.flyway.*} 自动执行，此处仅处理 tenant_*。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSchemaMigrator {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Value("${meis.flyway.tenant-locations:classpath:db/migrations/tenant}")
    private String tenantLocations;

    public void migrateAllActiveTenants() {
        List<Map<String, Object>> tenants = jdbcTemplate.queryForList(
                "SELECT tenant_code, schema_name FROM public.sys_tenant WHERE status = 'active'");
        if (tenants.isEmpty()) {
            throw new IllegalStateException("No active tenants after public Flyway migration");
        }
        for (Map<String, Object> tenant : tenants) {
            String schema = tenant.get("schema_name").toString();
            String code = tenant.get("tenant_code").toString();
            migrate(schema);
            log.info("Tenant schema ready: {} ({})", code, schema);
        }
    }

    public void migrate(String schemaName) {
        validateSchemaName(schemaName);
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .locations(tenantLocations)
                .baselineOnMigrate(true)
                .ignoreMigrationPatterns("*:missing")
                .load();
        if (isDevProfile()) {
            log.info("dev profile: Flyway repair + migrate (tenant schema {})", schemaName);
            flyway.repair();
        }
        flyway.migrate();
        log.info("Flyway migrated tenant schema {}", schemaName);
    }

    private boolean isDevProfile() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("dev"::equals);
    }

    private static void validateSchemaName(String schemaName) {
        if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName);
        }
    }
}
