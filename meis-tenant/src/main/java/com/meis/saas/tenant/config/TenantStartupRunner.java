package com.meis.saas.tenant.config;

import com.meis.saas.common.flyway.TenantFlywayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantStartupRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TenantFlywayService tenantFlyway;

    @Value("${meis.flyway.public-locations:classpath:db/migrations/public}")
    private String publicLocations;

    @Override
    public void run(ApplicationArguments args) {
        tenantFlyway.migratePublic(publicLocations);
        log.info("Public schema migrated");
        ensurePlatformAdmin();
        ensureDemoTenant();

        List<Map<String, Object>> tenants = jdbc.queryForList(
                "SELECT tenant_code, schema_name FROM public.sys_tenant WHERE status = 'active'");
        for (Map<String, Object> t : tenants) {
            String schema = t.get("schema_name").toString();
            String code = t.get("tenant_code").toString();
            try {
                tenantFlyway.createSchema(schema);
                tenantFlyway.migrate(schema);
                ensureDemoAdmin(schema, code);
                log.info("Tenant schema ready: {} ({})", code, schema);
            } catch (Exception e) {
                log.error("Tenant schema migration skipped for {} ({}): {}", code, schema, e.getMessage());
            }
        }
    }

    private void ensureDemoTenant() {
        jdbc.update(
                "INSERT INTO public.sys_tenant (id, tenant_code, tenant_name, schema_name, status, package_code) "
                        + "VALUES ('00000000-0000-0000-0000-000000000001', 'demo', '演示医院', 'tenant_demo', 'active', 'standard') "
                        + "ON CONFLICT (tenant_code) DO NOTHING");
    }

    private void ensurePlatformAdmin() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT password_hash FROM platform_user WHERE username = 'platform'");
        if (rows.isEmpty()) return;
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        String hash = rows.get(0).get("password_hash").toString();
        if (!enc.matches("admin123", hash)) {
            jdbc.update("UPDATE platform_user SET password_hash = ? WHERE username = 'platform'", enc.encode("admin123"));
            log.info("Platform admin password reset to default (platform / admin123)");
        }
    }

    private void ensureDemoAdmin(String schema, String code) {
        if (!"demo".equals(code)) return;
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        String hash = enc.encode("admin123");
        jdbc.update("UPDATE " + schema + ".sys_user SET password_hash = ? WHERE username = 'admin'", hash);
    }
}
