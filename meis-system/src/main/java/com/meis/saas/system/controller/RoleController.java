package com.meis.saas.system.controller;

import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.cache.MeisCacheEviction;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.rbac.PermissionService;
import com.meis.saas.common.persistence.SoftDeleteSupport;
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
    private final MeisCacheEviction cacheEviction;
    private final EntityChangeLogService changeLog;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, role_code, role_name, description, permissions, sort_order, is_active, created_at FROM sys_role WHERE 1=1 "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_role", null)
                        + " ORDER BY sort_order, role_code");
        rows.forEach(this::normalizePermissions);
        return Result.ok(rows);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM sys_role WHERE id = ?::uuid " + SoftDeleteSupport.notDeletedClause(jdbc, "sys_role", null),
                id);
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
        String code = body.get("role_code").toString();
        body.putIfAbsent("role_code", code);
        var softDeletedId = SoftDeleteSupport.prepareCreate(jdbc, "sys_role", body);
        String emptyPerms = permissionService.toJson(permissionService.emptyPermissions());
        if (softDeletedId.isPresent()) {
            UUID existingId = UUID.fromString(softDeletedId.get());
            Map<String, Object> before = changeLog.loadRow("sys_role", existingId);
            jdbc.update("""
                    UPDATE sys_role SET role_code=?, role_name=?, description=?, permissions=?::jsonb,
                    sort_order=?, is_active=?,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, code, body.get("role_name"), body.get("description"), emptyPerms,
                    body.getOrDefault("sort_order", 0), body.getOrDefault("is_active", true),
                    SoftDeleteSupport.currentUserId(), existingId);
            changeLog.recordUpdate("sys_role", existingId, before, changeLog.loadRow("sys_role", existingId));
            return get(existingId);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sys_role (id, role_code, role_name, description, permissions, sort_order, is_active, created_by, is_deleted)
                VALUES (?::uuid,?,?,?,?::jsonb,?,?,?::uuid,?)
                """, id, code, body.get("role_name"), body.get("description"), emptyPerms,
                body.getOrDefault("sort_order", 0), body.getOrDefault("is_active", true),
                SoftDeleteSupport.currentUserId(), 0);
        changeLog.recordCreate("sys_role", id, changeLog.loadRow("sys_role", id));
        return get(id);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "system", description = "更新角色")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> before = changeLog.loadRow("sys_role", id);
        if (before == null) {
            throw new BizException(404, "role not found");
        }
        jdbc.update("""
                UPDATE sys_role SET role_name=?, description=?, sort_order=?, is_active=?,
                updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid
                """, body.get("role_name"), body.get("description"), body.getOrDefault("sort_order", 0),
                body.getOrDefault("is_active", true), SoftDeleteSupport.currentUserId(), id);
        changeLog.recordUpdate("sys_role", id, before, changeLog.loadRow("sys_role", id));
        return get(id);
    }

    @PutMapping("/{id}/permissions")
    @OperationLog(module = "system", description = "保存角色权限")
    public Result<Void> updatePermissions(@PathVariable UUID id,
                                          @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
                                          @RequestBody Map<String, Object> permissions) {
        if (tenantId == null) tenantId = TenantContext.getTenantId();
        permissionService.validatePermissions(tenantId, TenantContext.getSchemaName(), permissions);
        Map<String, Object> before = changeLog.loadRow("sys_role", id);
        jdbc.update("UPDATE sys_role SET permissions = ?::jsonb, updated_at = NOW(), updated_by = ?::uuid WHERE id = ?::uuid",
                permissionService.toJson(permissions), SoftDeleteSupport.currentUserId(), id);
        changeLog.recordAction("sys_role", id, "update", before, changeLog.loadRow("sys_role", id), "更新权限");
        cacheEviction.evictTenantPermissions(tenantId);
        return Result.ok();
    }

    @PostMapping("/{id}/sync-permissions")
    @OperationLog(module = "system", description = "同步角色权限到用户")
    public Result<Map<String, Object>> syncPermissions(@PathVariable UUID id,
                                                       @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        if (tenantId == null) tenantId = TenantContext.getTenantId();
        List<Map<String, Object>> roles = jdbc.queryForList(
                "SELECT permissions FROM sys_role WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_role", null),
                id);
        if (roles.isEmpty()) throw new BizException(404, "role not found");
        String permsJson = permissionService.toJson(
                permissionService.parsePermissions(roles.get(0).get("permissions")));
        int updated = jdbc.update(
                "UPDATE sys_user SET permissions = ?::jsonb, permission_mode = 'synced', updated_at = NOW(), updated_by = ?::uuid WHERE role_ids IS NOT NULL AND ?::uuid = ANY(role_ids)"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null),
                permsJson, SoftDeleteSupport.currentUserId(), id);
        cacheEviction.evictTenantPermissions(tenantId);
        return Result.ok(Map.of("updatedCount", updated));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "system", description = "删除角色")
    public Result<Void> delete(@PathVariable UUID id) {
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT 1 FROM sys_user WHERE role_ids IS NOT NULL AND ?::uuid = ANY(role_ids) "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null)
                        + " LIMIT 1", id);
        if (!users.isEmpty()) throw new BizException(400, "role in use by users");
        Map<String, Object> before = changeLog.loadRow("sys_role", id);
        SoftDeleteSupport.softDelete(jdbc, "sys_role", id.toString());
        changeLog.recordDelete("sys_role", id, before);
        return Result.ok();
    }
}
