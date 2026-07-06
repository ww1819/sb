package com.meis.saas.system.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.cache.MeisCacheEviction;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.rbac.PermissionService;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.sql.Array;
import java.util.*;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {
    private static final String UUID_PATH = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final JdbcTemplate jdbc;
    private final PermissionService permissionService;
    private final MeisCacheEviction cacheEviction;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT u.id, u.username, u.real_name, u.employee_no, u.phone, u.email, u.dept_id, u.role_ids, u.is_active, u.permission_mode, u.last_login_at, u.created_at, d.dept_name FROM sys_user u LEFT JOIN department d ON u.dept_id = d.id ORDER BY u.created_at DESC");
        for (Map<String, Object> u : users) {
            normalizeUser(u);
            enrichRoleName(u);
        }
        return Result.ok(users);
    }

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (u.username ILIKE ? OR u.real_name ILIKE ? OR u.employee_no ILIKE ? OR u.phone ILIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (isActive != null) {
            where.append(" AND u.is_active = ? ");
            args.add(isActive);
        }
        String from = " FROM sys_user u LEFT JOIN department d ON u.dept_id = d.id ";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT u.id, u.username, u.real_name, u.employee_no, u.phone, u.email, u.dept_id, u.role_ids, u.is_active, u.permission_mode, u.last_login_at, u.created_at, d.dept_name"
                        + from + where + " ORDER BY u.created_at DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        for (Map<String, Object> u : users) {
            normalizeUser(u);
            enrichRoleName(u);
        }
        return Result.ok(PageResult.of(users, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id:" + UUID_PATH + "}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT u.*, d.dept_name FROM sys_user u LEFT JOIN department d ON u.dept_id = d.id WHERE u.id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "user not found");
        normalizeUser(rows.get(0));
        enrichRoleName(rows.get(0));
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @OperationLog(module = "system", description = "创建用户")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String pwd = body.getOrDefault("password", "123456").toString();
        UUID roleId = parseRoleId(body.get("role_id"));
        if (roleId == null) {
            jdbc.update(
                    "INSERT INTO sys_user (id, username, password_hash, real_name, employee_no, phone, email, dept_id, role_ids, is_active, permission_mode) VALUES (?::uuid,?,?,?,?,?,?,?::uuid,'{}'::uuid[],true,'synced')",
                    id, body.get("username"), encoder.encode(pwd), body.get("real_name"),
                    body.get("employee_no"), body.get("phone"), body.get("email"), body.get("dept_id"));
        } else {
            jdbc.update(
                    "INSERT INTO sys_user (id, username, password_hash, real_name, employee_no, phone, email, dept_id, role_ids, is_active, permission_mode) VALUES (?::uuid,?,?,?,?,?,?,?::uuid,ARRAY[?::uuid]::uuid[],true,'synced')",
                    id, body.get("username"), encoder.encode(pwd), body.get("real_name"),
                    body.get("employee_no"), body.get("phone"), body.get("email"), body.get("dept_id"), roleId);
            copyRolePermissions(id, roleId);
        }
        return get(id);
    }

    @PutMapping("/{id:" + UUID_PATH + "}")
    @OperationLog(module = "system", description = "更新用户")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        if (jdbc.queryForList("SELECT 1 FROM sys_user WHERE id = ?::uuid", id).isEmpty()) {
            throw new BizException(404, "user not found");
        }
        jdbc.update(
                "UPDATE sys_user SET real_name=?, employee_no=?, phone=?, email=?, dept_id=?::uuid, is_active=?, updated_at=NOW() WHERE id=?::uuid",
                body.get("real_name"), body.get("employee_no"), body.get("phone"), body.get("email"),
                body.get("dept_id"), body.getOrDefault("is_active", true), id);
        return get(id);
    }

    @PutMapping("/{id:" + UUID_PATH + "}/role")
    @OperationLog(module = "system", description = "分配角色")
    public Result<Void> assignRole(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID roleId = UUID.fromString(body.get("role_id").toString());
        jdbc.update("UPDATE sys_user SET role_ids = ARRAY[?::uuid]::uuid[], updated_at = NOW() WHERE id = ?::uuid", roleId, id);
        if (Boolean.TRUE.equals(body.get("syncPermissions"))) {
            copyRolePermissions(id, roleId);
        }
        evictUserPerm(id);
        return Result.ok();
    }

    @GetMapping("/{id:" + UUID_PATH + "}/permissions")
    public Result<Map<String, Object>> getPermissions(@PathVariable UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT permissions, permission_mode FROM sys_user WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "user not found");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("permissions", permissionService.parsePermissions(rows.get(0).get("permissions")));
        result.put("permission_mode", rows.get(0).get("permission_mode"));
        return Result.ok(result);
    }

    @PutMapping("/{id:" + UUID_PATH + "}/permissions")
    @OperationLog(module = "system", description = "调整用户权限")
    public Result<Void> updatePermissions(@PathVariable UUID id,
                                          @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
                                          @RequestBody Map<String, Object> permissions) {
        if (tenantId == null) tenantId = TenantContext.getTenantId();
        permissionService.validatePermissions(tenantId, TenantContext.getSchemaName(), permissions);
        jdbc.update("UPDATE sys_user SET permissions = ?::jsonb, permission_mode = 'custom', updated_at = NOW() WHERE id = ?::uuid",
                permissionService.toJson(permissions), id);
        evictUserPerm(id);
        return Result.ok();
    }

    @PostMapping("/{id:" + UUID_PATH + "}/reset-from-role")
    @OperationLog(module = "system", description = "从角色恢复用户权限")
    public Result<Void> resetFromRole(@PathVariable UUID id) {
        List<Map<String, Object>> users = jdbc.queryForList("SELECT role_ids FROM sys_user WHERE id = ?::uuid", id);
        if (users.isEmpty()) throw new BizException(404, "user not found");
        UUID[] roleIds = toRoleIds(users.get(0).get("role_ids"));
        if (roleIds.length == 0) throw new BizException(400, "user has no role");
        copyRolePermissions(id, roleIds[0]);
        evictUserPerm(id);
        return Result.ok();
    }

    @PostMapping("/{id:" + UUID_PATH + "}/reset-password")
    @OperationLog(module = "system", description = "重置密码")
    public Result<Void> resetPassword(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        jdbc.update("UPDATE sys_user SET password_hash = ?, updated_at = NOW() WHERE id = ?::uuid",
                encoder.encode(body.getOrDefault("password", "123456")), id);
        return Result.ok();
    }

    private void copyRolePermissions(UUID userId, UUID roleId) {
        List<Map<String, Object>> roles = jdbc.queryForList("SELECT permissions FROM sys_role WHERE id = ?::uuid", roleId);
        if (roles.isEmpty()) return;
        String json = permissionService.toJson(permissionService.parsePermissions(roles.get(0).get("permissions")));
        jdbc.update("UPDATE sys_user SET permissions = ?::jsonb, permission_mode = 'synced', updated_at = NOW() WHERE id = ?::uuid",
                json, userId);
        evictUserPerm(userId);
    }

    private void evictUserPerm(UUID userId) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            cacheEviction.evictUserPermission(tenantId, userId.toString());
        }
    }

    private void enrichRoleName(Map<String, Object> user) {
        UUID[] roleIds = toRoleIds(user.get("role_ids"));
        if (roleIds.length == 0) return;
        List<Map<String, Object>> roles = jdbc.queryForList(
                "SELECT role_code, role_name FROM sys_role WHERE id = ?::uuid", roleIds[0]);
        if (!roles.isEmpty()) {
            user.put("role_id", roleIds[0].toString());
            user.put("role_code", roles.get(0).get("role_code"));
            user.put("role_name", roles.get(0).get("role_name"));
        }
    }

    private void normalizeUser(Map<String, Object> user) {
        if (user.get("id") instanceof UUID id) {
            user.put("id", id.toString());
        }
        if (user.get("dept_id") instanceof UUID deptId) {
            user.put("dept_id", deptId.toString());
        }
        UUID[] roleIds = toRoleIds(user.get("role_ids"));
        user.put("role_ids", roleIds.length > 0
                ? List.of(roleIds[0].toString())
                : List.of());
        if (user.containsKey("permissions")) {
            user.put("permissions", permissionService.parsePermissions(user.get("permissions")));
        }
    }

    private UUID parseRoleId(Object roleId) {
        if (roleId == null || roleId.toString().isBlank()) return null;
        return UUID.fromString(roleId.toString());
    }

    private UUID[] toRoleIds(Object raw) {
        if (raw == null) return new UUID[0];
        if (raw instanceof UUID[] uuids) return uuids.length > 1 ? new UUID[]{uuids[0]} : uuids;
        if (raw instanceof Array arr) {
            try {
                Object[] objs = (Object[]) arr.getArray();
                if (objs.length == 0) return new UUID[0];
                return new UUID[]{UUID.fromString(objs[0].toString())};
            } catch (Exception e) {
                return new UUID[0];
            }
        }
        return new UUID[0];
    }
}
