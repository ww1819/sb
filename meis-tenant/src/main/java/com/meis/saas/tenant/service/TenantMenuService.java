package com.meis.saas.tenant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantMenuService {
    private final JdbcTemplate jdbc;

    public List<String> getAuthorizedMenus(UUID tenantId) {
        return jdbc.queryForList(
                "SELECT menu_code FROM sys_tenant_menu WHERE tenant_id = ?::uuid ORDER BY menu_code",
                String.class, tenantId);
    }

    @Transactional
    public void saveAuthorizedMenus(UUID tenantId, List<String> menuCodes) {
        jdbc.update("DELETE FROM sys_tenant_menu WHERE tenant_id = ?::uuid", tenantId);
        for (String code : menuCodes) {
            jdbc.update("INSERT INTO sys_tenant_menu (tenant_id, menu_code) VALUES (?::uuid, ?)",
                    tenantId, code);
        }
    }

    public List<Map<String, Object>> listPackages() {
        return jdbc.queryForList("SELECT * FROM sys_package WHERE is_active = true ORDER BY package_code");
    }

    public List<String> packageMenus(String packageCode) {
        return jdbc.queryForList(
                "SELECT menu_code FROM sys_package_menu WHERE package_code = ? ORDER BY menu_code",
                String.class, packageCode);
    }
}
