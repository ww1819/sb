package com.meis.saas.tenant.service;

import com.meis.saas.api.dto.TenantCreateRequest;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.flyway.TenantFlywayService;
import com.meis.saas.common.tenant.TenantConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
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

    public List<Map<String, Object>> list() {
        return jdbc.queryForList(
                "SELECT id, tenant_code, tenant_name, schema_name, status, package_code, credit_code, created_at "
                        + "FROM public.sys_tenant ORDER BY created_at");
    }

    public void update(String id, Map<String, Object> body) {
        jdbc.update("UPDATE sys_tenant SET tenant_name=?, status=?, package_code=?, credit_code=? WHERE id=?::uuid",
                body.get("tenant_name"), body.get("status"), body.get("package_code"), body.get("credit_code"), id);
    }

    @Transactional
    public Map<String, Object> create(TenantCreateRequest req) {
        if (req.getTenantCode() == null || req.getTenantName() == null) {
            throw new BizException(400, "tenantCode and tenantName required");
        }
        String code = req.getTenantCode().toLowerCase().replaceAll("[^a-z0-9_]", "");
        String schema = "tenant_" + code;
        String packageCode = req.getPackageCode() != null && !req.getPackageCode().isBlank()
                ? req.getPackageCode() : "standard";
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sys_tenant (id, tenant_code, tenant_name, schema_name, hospital_level, contact_name, contact_phone, status, package_code) VALUES (?::uuid,?,?,?,?,?,?,'active',?)",
                id.toString(), code, req.getTenantName(), schema,
                req.getHospitalLevel(), req.getContactName(), req.getContactPhone(), packageCode);
        tenantFlyway.createSchema(schema);
        tenantFlyway.migrate(schema);

        List<String> menuCodes = tenantMenuService.packageMenus(packageCode);
        if (!menuCodes.isEmpty()) {
            tenantMenuService.saveAuthorizedMenus(id, menuCodes);
        }
        assignTenantAdminRole(schema);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id.toString());
        result.put("tenantCode", code);
        result.put("schemaName", schema);
        result.put("packageCode", packageCode);
        result.put("adminUsername", DEFAULT_ADMIN_USER);
        result.put("adminPassword", DEFAULT_ADMIN_PASS);
        return result;
    }

    private void assignTenantAdminRole(String schema) {
        List<Map<String, Object>> roles = jdbc.queryForList(
                "SELECT id FROM " + schema + ".sys_role WHERE role_code = 'tenant_admin'");
        if (roles.isEmpty()) return;
        String roleId = roles.get(0).get("id").toString();
        jdbc.update("UPDATE " + schema + ".sys_user SET role_ids = ARRAY[?::uuid], real_name = '租户管理员' WHERE username = ?",
                roleId, DEFAULT_ADMIN_USER);
    }

    public void requirePlatformAdmin(String userType) {
        if (!"platform".equals(userType)) {
            throw new BizException(403, "仅平台管理员可操作");
        }
    }
}
