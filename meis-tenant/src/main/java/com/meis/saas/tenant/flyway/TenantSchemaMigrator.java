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
 *
 * <p>脚本约定：
 * <ul>
 *   <li>全量建表：{@code V1__tables.sql}；老租户更新时由 {@link SchemaTableEnsuring} 幂等执行（缺表则建）</li>
 *   <li>已有表补列：{@code R__tenant_schema_sync.sql}（每条 ALTER 只加一列；勿再新增 V20+）</li>
 *   <li>新增字段时：同时改 V1 建表语句 + 在 R__ 追加一条 ADD COLUMN</li>
 *   <li>空注释补全：{@link SchemaCommentFiller}（仅补空，不覆盖已有注释）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSchemaMigrator {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final SchemaTableEnsuring schemaTableEnsuring;
    private final SchemaCommentFiller schemaCommentFiller;

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
        // 1) Flyway：新租户走 V1 建表；老租户走 R__ 逐列补字段
        flyway.migrate();
        // 2) 再幂等执行 V1/V2：老租户更新后 V1 新增的表在此创建（已有表不动）
        schemaTableEnsuring.ensureFromMigrations(schemaName);
        // 3) 仅补空注释
        schemaCommentFiller.fillEmptyComments(schemaName);
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
