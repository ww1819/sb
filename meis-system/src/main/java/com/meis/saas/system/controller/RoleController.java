package com.meis.saas.system.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.rbac.PermissionService;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {
    private final JdbcTemplate jdbc;
    private final PermissionService permissionService;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, role_code, role_name, description, permissions, sort_order, is_active, created_at FROM sys_role ORDER BY sort_order, role_code");
        rows.forEach(this::normalizePermissions);
        return Result.ok(rows);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sys_role WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "role not found");
        normalizePermissions(rows.get(0));
        return Result.ok(rows.get(0));
    }

    private void normalizePermissions(Map<String, Object> row) {
        row.put("permissions", permissionService.parsePermissions(row.get("permissions")));
    }

    @PostMapping
    @OperationLog(module = "system", description = "创建角色")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String code = body.get("role_code").toString();
        jdbc.update(
                "INSERT INTO sys_role (id, role_code, role_name, description, permissions, sort_order, is_active) VALUES (?::uuid,?,?,?::jsonb,?,?,?)",
                id, code, body.get("role_name"), body.get("description"),
                permissionService.toJson(permissionService.emptyPermissions()),
                body.getOrDefault("sort_order", 0), body.getOrDefault("is_active", true));
        return get(id);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "system", description = "更新角色")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        if (jdbc.queryForList("SELECT 1 FROM sys_role WHERE id = ?::uuid", id).isEmpty()) {
            throw new BizException(404, "role not found");
        }
        jdbc.update(
                "UPDATE sys_role SET role_name=?, description=?, sort_order=?, is_active=?, updated_at=NOW() WHERE id=?::uuid",
                body.get("role_name"), body.get("description"), body.getOrDefault("sort_order", 0),
                body.getOrDefault("is_active", true), id);
        return get(id);
    }

    @PutMapping("/{id}/permissions")
    @OperationLog(module = "system", description = "保存角色权限")
    public Result<Void> updatePermissions(@PathVariable UUID id,
                                          @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
                                          @RequestBody Map<String, Object> permissions) {
        if (tenantId == null) tenantId = TenantContext.getTenantId();
        permissionService.validatePermissions(tenantId, TenantContext.getSchemaName(), permissions);
        jdbc.update("UPDATE sys_role SET permissions = ?::jsonb, updated_at = NOW() WHERE id = ?::uuid",
                permissionService.toJson(permissions), id);
        return Result.ok();
    }

    @PostMapping("/{id}/sync-permissions")
    @OperationLog(module = "system", description = "同步角色权限到用户")
    public Result<Map<String, Object>> syncPermissions(@PathVariable UUID id) {
        List<Map<String, Object>> roles = jdbc.queryForList("SELECT permissions FROM sys_role WHERE id = ?::uuid", id);
        if (roles.isEmpty()) throw new BizException(404, "role not found");
        String permsJson = permissionService.toJson(
                permissionService.parsePermissions(roles.get(0).get("permissions")));
        int updated = jdbc.update(
                "UPDATE sys_user SET permissions = ?::jsonb, permission_mode = 'synced', updated_at = NOW() WHERE role_ids IS NOT NULL AND ?::uuid = ANY(role_ids)",
                permsJson, id);
        return Result.ok(Map.of("updatedCount", updated));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "system", description = "删除角色")
    public Result<Void> delete(@PathVariable UUID id) {
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT 1 FROM sys_user WHERE role_ids IS NOT NULL AND ?::uuid = ANY(role_ids) LIMIT 1", id);
        if (!users.isEmpty()) throw new BizException(400, "role in use by users");
        jdbc.update("UPDATE sys_role SET is_active = false, updated_at = NOW() WHERE id = ?::uuid", id);
        return Result.ok();
    }
}
