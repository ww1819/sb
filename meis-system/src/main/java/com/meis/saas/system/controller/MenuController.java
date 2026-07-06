package com.meis.saas.system.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.meis.saas.common.cache.CacheKeys;
import com.meis.saas.common.cache.MeisCacheProperties;
import com.meis.saas.common.cache.RedisJsonCache;
import com.meis.saas.common.rbac.PermissionService;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class MenuController {
    private final MenuService menuService;
    private final PermissionService permissionService;
    private final JdbcTemplate jdbc;
    private final RedisJsonCache cache;
    private final MeisCacheProperties cacheProps;

    @GetMapping("/menus")
    public Result<List<Map<String, Object>>> menusGet(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return menus(tenantId, Map.of("menus", List.of("*")));
    }

    @PostMapping("/menus")
    public Result<List<Map<String, Object>>> menus(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        if (tenantId == null) tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = "00000000-0000-0000-0000-000000000001";
        @SuppressWarnings("unchecked")
        List<String> effectiveMenus = body != null && body.get("menus") != null
                ? (List<String>) body.get("menus") : List.of("*");
        if (effectiveMenus.isEmpty() || effectiveMenus.contains("*")) {
            effectiveMenus = permissionService.tenantAuthorizedMenus(tenantId);
        }
        return Result.ok(menuService.tenantMenus(tenantId, effectiveMenus));
    }

    @GetMapping("/menus/effective")
    public Result<List<Map<String, Object>>> effectiveMenus(
            @RequestParam String tenantId,
            @RequestParam String schema,
            @RequestParam(required = false) String userId) {
        UUID[] roleIds = new UUID[0];
        if (userId != null) {
            List<Map<String, Object>> users = jdbc.queryForList(
                    "SELECT role_ids FROM " + schema + ".sys_user WHERE id = ?::uuid", userId);
            if (!users.isEmpty()) roleIds = parseRoleIds(users.get(0).get("role_ids"));
        }
        Map<String, Object> rolePerms = permissionService.loadRolePermissions(schema, roleIds);
        @SuppressWarnings("unchecked")
        List<String> menus = (List<String>) permissionService.effectivePermissions(tenantId, rolePerms).get("menus");
        return Result.ok(menuService.tenantMenus(tenantId, menus));
    }

    @GetMapping("/menus/permission-tree")
    public Result<List<Map<String, Object>>> permissionTree(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        if (tenantId == null) tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = "00000000-0000-0000-0000-000000000001";
        return Result.ok(menuService.permissionMenuTree(tenantId));
    }

    @GetMapping("/permission/buttons")
    public Result<List<Map<String, Object>>> buttonPermissions() {
        String schema = TenantContext.getSchemaName();
        if (schema == null || schema.isBlank()) schema = "public";
        String finalSchema = schema;
        return Result.ok(cache.getOrLoad(
                CacheKeys.buttonPerms(finalSchema),
                cacheProps.getDictTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                () -> jdbc.queryForList(
                        "SELECT dict_code as code, dict_label as label FROM sys_dict WHERE dict_type = 'button_perm' AND is_active = true ORDER BY sort_order")));
    }

    @GetMapping("/menus/platform-nav")
    public Result<List<Map<String, Object>>> platformNav() {
        return Result.ok(menuService.platformNavMenus());
    }

    @GetMapping("/platform/menus")
    public Result<List<Map<String, Object>>> platformMenus() {
        return Result.ok(menuService.platformMenuTree());
    }

    @SuppressWarnings("unchecked")
    private UUID[] parseRoleIds(Object raw) {
        if (raw instanceof UUID[] u) return u;
        if (raw instanceof java.sql.Array arr) {
            try {
                Object[] objs = (Object[]) arr.getArray();
                return java.util.Arrays.stream(objs).map(o -> UUID.fromString(o.toString())).toArray(UUID[]::new);
            } catch (Exception e) { return new UUID[0]; }
        }
        return new UUID[0];
    }
}
