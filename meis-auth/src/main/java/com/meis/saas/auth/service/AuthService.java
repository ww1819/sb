package com.meis.saas.auth.service;

import com.meis.saas.api.dto.LoginRequest;
import com.meis.saas.api.dto.LoginResponse;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.rbac.PermissionService;
import com.meis.saas.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JdbcTemplate jdbc;
    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public LoginResponse login(LoginRequest req) {
        if (req.getTenantCode() == null || req.getUsername() == null || req.getPassword() == null) {
            throw new BizException(400, "tenantCode/username/password required");
        }
        List<Map<String, Object>> tenants = jdbc.queryForList(
                "SELECT id, tenant_code, schema_name, status FROM sys_tenant WHERE tenant_code = ?",
                req.getTenantCode());
        if (tenants.isEmpty()) throw new BizException(404, "tenant not found");
        Map<String, Object> tenant = tenants.get(0);
        if (!"active".equals(tenant.get("status"))) throw new BizException(403, "tenant disabled");

        String schema = tenant.get("schema_name").toString();
        String tenantId = tenant.get("id").toString();
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT id, username, password_hash, real_name, is_active, role_ids, dept_id, permissions FROM " + schema + ".sys_user WHERE username = ?",
                req.getUsername());
        if (users.isEmpty()) throw new BizException(401, "invalid credentials");
        Map<String, Object> user = users.get(0);
        if (!Boolean.TRUE.equals(user.get("is_active"))) throw new BizException(403, "user disabled");

        String hash = user.get("password_hash").toString();
        if (!encoder.matches(req.getPassword(), hash)) throw new BizException(401, "invalid credentials");

        String userId = user.get("id").toString();
        UUID[] roleIds = toRoleIds(user.get("role_ids"));
        Map<String, Object> userPerms = permissionService.parsePermissions(user.get("permissions"));
        if (user.get("permissions") == null || isEmptyPerms(userPerms)) {
            userPerms = permissionService.loadRolePermissions(schema, roleIds);
        }
        Map<String, Object> permissions = permissionService.effectivePermissions(tenantId, userPerms);
        List<String> roles = loadRoleCodes(schema, roleIds);

        String token = jwtUtil.generate(userId, req.getUsername(), tenantId, req.getTenantCode(), schema, roles, permissions, "tenant");

        return LoginResponse.builder()
                .token(token)
                .userId(userId)
                .username(req.getUsername())
                .realName(user.get("real_name") != null ? user.get("real_name").toString() : req.getUsername())
                .tenantId(tenantId)
                .tenantCode(req.getTenantCode())
                .schemaName(schema)
                .roles(roles)
                .permissions(permissions)
                .userType("tenant")
                .build();
    }

    /** 平台管理员登录（public schema，仅系统管理） */
    public LoginResponse platformLogin(LoginRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            throw new BizException(400, "username/password required");
        }
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT id, username, password_hash, real_name, is_active FROM platform_user WHERE username = ?",
                req.getUsername());
        if (users.isEmpty()) throw new BizException(401, "invalid credentials");
        Map<String, Object> user = users.get(0);
        if (!Boolean.TRUE.equals(user.get("is_active"))) throw new BizException(403, "user disabled");

        String hash = user.get("password_hash").toString();
        if (!encoder.matches(req.getPassword(), hash)) throw new BizException(401, "invalid credentials");

        String userId = user.get("id").toString();
        Map<String, Object> permissions = Map.of(
                "menus", List.of("mod_platform", "platform_tenant", "platform_tenant_menu", "platform_package"),
                "buttons", List.of("*"),
                "dataScope", "all");
        List<String> roles = List.of("platform_admin");

        String token = jwtUtil.generate(userId, req.getUsername(), "", "platform", "public", roles, permissions, "platform");

        return LoginResponse.builder()
                .token(token)
                .userId(userId)
                .username(req.getUsername())
                .realName(user.get("real_name") != null ? user.get("real_name").toString() : "平台管理员")
                .tenantId("")
                .tenantCode("platform")
                .schemaName("public")
                .roles(roles)
                .permissions(permissions)
                .userType("platform")
                .build();
    }

    public Map<String, Object> permissions(String tenantId, String schema, UUID[] roleIds) {
        Map<String, Object> rolePerms = permissionService.loadRolePermissions(schema, roleIds);
        return permissionService.effectivePermissions(tenantId, rolePerms);
    }

    private List<String> loadRoleCodes(String schema, UUID[] roleIds) {
        if (roleIds == null || roleIds.length == 0) return List.of();
        String in = Arrays.stream(roleIds).map(id -> "'" + id + "'").reduce((a, b) -> a + "," + b).orElse("");
        return jdbc.queryForList("SELECT role_code FROM " + schema + ".sys_role WHERE id IN (" + in + ")", String.class);
    }

    private UUID[] toRoleIds(Object raw) {
        if (raw == null) return new UUID[0];
        if (raw instanceof UUID[] uuids) return uuids;
        if (raw instanceof Array arr) {
            try {
                Object[] objs = (Object[]) arr.getArray();
                return Arrays.stream(objs).map(o -> UUID.fromString(o.toString())).toArray(UUID[]::new);
            } catch (Exception e) {
                return new UUID[0];
            }
        }
        if (raw instanceof Object[] objs) {
            return Arrays.stream(objs).map(o -> UUID.fromString(o.toString())).toArray(UUID[]::new);
        }
        return new UUID[0];
    }

    @SuppressWarnings("unchecked")
    private boolean isEmptyPerms(Map<String, Object> p) {
        List<String> menus = (List<String>) p.getOrDefault("menus", List.of());
        return menus.isEmpty();
    }
}
