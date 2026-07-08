package com.meis.saas.tenant.config;

import com.meis.saas.common.cache.MeisCacheEviction;
import com.meis.saas.tenant.flyway.TenantSchemaMigrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class TenantStartupRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final TenantSchemaMigrator tenantSchemaMigrator;
    private final MeisCacheEviction cacheEviction;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureDemoTenant();
            tenantSchemaMigrator.migrateAllActiveTenants();
            ensurePlatformAdmin();
            List<Map<String, Object>> tenants = jdbc.queryForList(
                    "SELECT tenant_code, schema_name FROM public.sys_tenant WHERE status = 'active'");
            for (Map<String, Object> t : tenants) {
                ensureTenantAdminPassword(t.get("schema_name").toString(), t.get("tenant_code").toString());
            }
            evictMenuCaches();
        } catch (Exception e) {
            log.error("Tenant startup failed: {}", e.getMessage(), e);
            throw new IllegalStateException(
                    "meis-tenant 启动失败：租户 schema 迁移未完成。原因: " + e.getMessage(), e);
        }
    }

    private void evictMenuCaches() {
        List<String> tenantIds = jdbc.queryForList(
                "SELECT id::text FROM public.sys_tenant WHERE status = 'active'", String.class);
        cacheEviction.evictPlatformMenus();
        for (String tenantId : tenantIds) {
            cacheEviction.evictTenantMenus(tenantId);
            cacheEviction.evictTenantPermissions(tenantId);
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

    private void ensureTenantAdminPassword(String schema, String code) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT password_hash FROM " + schema + ".sys_user WHERE username = 'admin'");
        if (rows.isEmpty()) return;
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        String hash = rows.get(0).get("password_hash").toString();
        if (!enc.matches("admin123", hash)) {
            jdbc.update("UPDATE " + schema + ".sys_user SET password_hash = ? WHERE username = 'admin'", enc.encode("admin123"));
            log.info("Tenant admin password reset to default for {} (admin / admin123)", code);
        }
    }
}
