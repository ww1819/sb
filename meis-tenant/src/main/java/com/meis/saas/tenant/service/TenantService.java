package com.meis.saas.tenant.service;

import com.meis.saas.api.dto.TenantCreateRequest;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.flyway.TenantFlywayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {
    private final JdbcTemplate jdbc;
    private final TenantFlywayService tenantFlyway;
    private final TenantMenuService tenantMenuService;

    private static final String DEFAULT_ADMIN_USER = "admin";
    private static final String DEFAULT_ADMIN_PASS = "admin123";
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public List<Map<String, Object>> list() {
        return jdbc.queryForList(
                "SELECT id, tenant_code, tenant_name, schema_name, status, package_code, credit_code, created_at "
                        + "FROM public.sys_tenant ORDER BY created_at");
    }

    public void update(String id, Map<String, Object> body) {
        List<Map<String, Object>> before = jdbc.queryForList(
                "SELECT package_code FROM public.sys_tenant WHERE id = ?::uuid", id);
        String oldPackage = before.isEmpty() || before.get(0).get("package_code") == null
                ? "standard" : before.get(0).get("package_code").toString();
        String newPackage = body.get("package_code") != null ? body.get("package_code").toString() : oldPackage;

        jdbc.update("UPDATE sys_tenant SET tenant_name=?, status=?, package_code=?, credit_code=? WHERE id=?::uuid",
                body.get("tenant_name"), body.get("status"), newPackage, body.get("credit_code"), id);

        if (!newPackage.equals(oldPackage)) {
            mergePackageMenus(UUID.fromString(id), newPackage);
        }
    }

    private void mergePackageMenus(UUID tenantId, String packageCode) {
        List<String> packageMenus = tenantMenuService.packageMenus(packageCode);
        if (packageMenus.isEmpty()) return;
        List<String> current = tenantMenuService.getAuthorizedMenus(tenantId);
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(current);
        merged.addAll(packageMenus);
        tenantMenuService.saveAuthorizedMenus(tenantId, new java.util.ArrayList<>(merged));
        log.info("Merged package {} menus into tenant {}", packageCode, tenantId);
    }

    public Map<String, Object> create(TenantCreateRequest req) {
        if (req.getTenantCode() == null || req.getTenantName() == null) {
            throw new BizException(400, "tenantCode and tenantName required");
        }
        String code = req.getTenantCode().toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (code.isBlank()) {
            throw new BizException(400, "invalid tenantCode");
        }
        if (!jdbc.queryForList("SELECT 1 FROM public.sys_tenant WHERE tenant_code = ?", code).isEmpty()) {
            throw new BizException(400, "租户编码已存在");
        }
        String schema = "tenant_" + code;
        String packageCode = req.getPackageCode() != null && !req.getPackageCode().isBlank()
                ? req.getPackageCode() : "standard";
        UUID id = UUID.randomUUID();

        log.info("Creating tenant code={} schema={} package={}", code, schema, packageCode);
        try {
            jdbc.update(
                    "INSERT INTO public.sys_tenant (id, tenant_code, tenant_name, schema_name, hospital_level, contact_name, contact_phone, status, package_code) VALUES (?::uuid,?,?,?,?,?,?,'active',?)",
                    id.toString(), code, req.getTenantName(), schema,
                    req.getHospitalLevel(), req.getContactName(), req.getContactPhone(), packageCode);

            tenantFlyway.createSchema(schema);
            tenantFlyway.migrate(schema);

            List<String> menuCodes = tenantMenuService.packageMenus(packageCode);
            if (!menuCodes.isEmpty()) {
                tenantMenuService.saveAuthorizedMenus(id, menuCodes);
            }
            assignTenantAdminRole(schema);
            ensureAdminPassword(schema);

            log.info("Tenant created successfully: {} ({})", code, schema);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id.toString());
            result.put("tenantCode", code);
            result.put("schemaName", schema);
            result.put("packageCode", packageCode);
            result.put("adminUsername", DEFAULT_ADMIN_USER);
            result.put("adminPassword", DEFAULT_ADMIN_PASS);
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tenant create failed for {}: {}", code, e.getMessage(), e);
            cleanupFailedTenant(id, schema);
            throw new BizException(500, "开户失败，请查看 meis-tenant 日志: " + e.getMessage());
        }
    }

    private void cleanupFailedTenant(UUID id, String schema) {
        try {
            jdbc.update("DELETE FROM public.sys_tenant_menu WHERE tenant_id = ?::uuid", id.toString());
            jdbc.update("DELETE FROM public.sys_tenant WHERE id = ?::uuid", id.toString());
        } catch (Exception e) {
            log.warn("Failed to remove tenant row {}: {}", id, e.getMessage());
        }
        if (schema != null && schema.matches("^tenant_[a-z0-9_]+$")) {
            try {
                jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                log.info("Dropped schema {} after failed create", schema);
            } catch (Exception e) {
                log.warn("Failed to drop schema {}: {}", schema, e.getMessage());
            }
        }
    }

    private void assignTenantAdminRole(String schema) {
        List<Map<String, Object>> roles = jdbc.queryForList(
                "SELECT id FROM " + schema + ".sys_role WHERE role_code IN ('tenant_admin', 'admin') "
                        + "ORDER BY CASE role_code WHEN 'tenant_admin' THEN 0 ELSE 1 END LIMIT 1");
        if (roles.isEmpty()) return;
        String roleId = roles.get(0).get("id").toString();
        jdbc.update("UPDATE " + schema + ".sys_user SET role_ids = ARRAY[?::uuid], real_name = '租户管理员' WHERE username = ?",
                roleId, DEFAULT_ADMIN_USER);
    }

    private void ensureAdminPassword(String schema) {
        jdbc.update("UPDATE " + schema + ".sys_user SET password_hash = ? WHERE username = ?",
                passwordEncoder.encode(DEFAULT_ADMIN_PASS), DEFAULT_ADMIN_USER);
    }

    public void requirePlatformAdmin(String userType) {
        if (!"platform".equals(userType)) {
            throw new BizException(403, "仅平台管理员可操作");
        }
    }
}
