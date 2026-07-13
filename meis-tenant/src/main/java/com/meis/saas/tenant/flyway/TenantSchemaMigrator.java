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
 * <p>老租户启动顺序（每个 {@code tenant_*} schema）：
 * <ol>
 *   <li>{@link SchemaTableEnsuring}：幂等执行 V1/V2 建表与索引（先扩展，再 {@code CREATE TABLE IF NOT EXISTS}）</li>
 *   <li>Flyway {@code migrate}：新租户跑 V1–V4；老租户主要跑 {@code R__tenant_schema_sync.sql} 逐列 {@code ADD COLUMN}</li>
 *   <li>{@link SchemaCommentFiller}：仅补空注释</li>
 *   <li>{@link TenantSchemaShadowGuard}：V1 缺表串写检测，仍缺表则启动失败</li>
 * </ol>
 * 对老租户执行建表语句<strong>没有问题</strong>：{@code IF NOT EXISTS} 不会覆盖已有表与数据；缺列由 R__ 在之后补全。
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
    private final TenantSchemaShadowGuard schemaShadowGuard;

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
        // 1) 幂等建表：V1/V2 + R__ 建表段（老租户缺表时创建；已有表跳过）
        schemaTableEnsuring.ensureFromMigrations(schemaName);
        // 2) Flyway 补列与数据修正：R__ 中 ALTER / INSERT / UPDATE
        flyway.migrate();
        // 3) 仅补空注释
        schemaCommentFiller.fillEmptyComments(schemaName);
        // 4) 缺表串写检测：租户无表但 public 有时，运行时 search_path 会写入 public
        schemaShadowGuard.ensureNoShadowGaps(schemaName);
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
