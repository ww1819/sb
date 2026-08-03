package com.meis.saas.system.controller;

import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.cache.MeisCacheEviction;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.rbac.PermissionService;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
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
    private final EntityChangeLogService changeLog;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int QUICK_ENTRY_MAX = 45;

    private static final String USER_LIST_COLS =
            "u.id, u.username, u.real_name, u.employee_no, u.phone, u.email, u.dept_id, u.role_ids,"
                    + " u.is_active, u.is_repair_engineer, u.permission_mode, u.last_login_at, u.created_at, d.dept_name";

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT " + USER_LIST_COLS
                        + " FROM sys_user u LEFT JOIN department d ON u.dept_id = d.id"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d")
                        + " WHERE 1=1 "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u")
                        + " ORDER BY u.created_at DESC");
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
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String deptId,
            @RequestParam(required = false) Boolean isRepairEngineer,
            @RequestParam(required = false) String roleId,
            @RequestParam(required = false) String permissionMode) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u"));
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
        if (deptId != null && !deptId.isBlank()) {
            where.append(" AND u.dept_id = ?::uuid ");
            args.add(deptId);
        }
        if (isRepairEngineer != null) {
            where.append(" AND COALESCE(u.is_repair_engineer, false) = ? ");
            args.add(isRepairEngineer);
        }
        if (roleId != null && !roleId.isBlank()) {
            where.append(" AND u.role_ids IS NOT NULL AND ?::uuid = ANY(u.role_ids) ");
            args.add(roleId);
        }
        if (permissionMode != null && !permissionMode.isBlank()) {
            where.append(" AND COALESCE(u.permission_mode, 'synced') = ? ");
            args.add(permissionMode);
        }
        String from = " FROM sys_user u LEFT JOIN department d ON u.dept_id = d.id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d") + " ";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT " + USER_LIST_COLS + from + where + " ORDER BY u.created_at DESC LIMIT ? OFFSET ?",
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
                "SELECT u.*, d.dept_name FROM sys_user u LEFT JOIN department d ON u.dept_id = d.id"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d")
                        + " WHERE u.id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u"),
                id);
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
        boolean repairEngineer = Boolean.TRUE.equals(body.get("is_repair_engineer"));
        Object isActive = body.getOrDefault("is_active", true);
        if (roleId == null) {
            jdbc.update(
                    "INSERT INTO sys_user (id, username, password_hash, real_name, employee_no, phone, email, dept_id, role_ids, is_active, is_repair_engineer, permission_mode) VALUES (?::uuid,?,?,?,?,?,?,?::uuid,'{}'::uuid[],?,?, 'synced')",
                    id, body.get("username"), encoder.encode(pwd), body.get("real_name"),
                    body.get("employee_no"), body.get("phone"), body.get("email"), body.get("dept_id"),
                    isActive, repairEngineer);
        } else {
            jdbc.update(
                    "INSERT INTO sys_user (id, username, password_hash, real_name, employee_no, phone, email, dept_id, role_ids, is_active, is_repair_engineer, permission_mode) VALUES (?::uuid,?,?,?,?,?,?,?::uuid,ARRAY[?::uuid]::uuid[],?,?, 'synced')",
                    id, body.get("username"), encoder.encode(pwd), body.get("real_name"),
                    body.get("employee_no"), body.get("phone"), body.get("email"), body.get("dept_id"), roleId,
                    isActive, repairEngineer);
            copyRolePermissions(id, roleId);
        }
        Result<Map<String, Object>> created = get(id);
        changeLog.recordCreate("sys_user", id, created.getData());
        return created;
    }

    @PutMapping("/{id:" + UUID_PATH + "}")
    @OperationLog(module = "system", description = "更新用户")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> before = changeLog.loadRow("sys_user", id);
        if (before == null) {
            throw new BizException(404, "user not found");
        }
        jdbc.update(
                "UPDATE sys_user SET real_name=?, employee_no=?, phone=?, email=?, dept_id=?::uuid, is_active=?, is_repair_engineer=?, updated_at=NOW() WHERE id=?::uuid",
                body.get("real_name"), body.get("employee_no"), body.get("phone"), body.get("email"),
                body.get("dept_id"), body.getOrDefault("is_active", true),
                Boolean.TRUE.equals(body.get("is_repair_engineer")), id);
        Result<Map<String, Object>> after = get(id);
        changeLog.recordUpdate("sys_user", id, before, changeLog.loadRow("sys_user", id));
        return after;
    }

    @PostMapping("/batch-update")
    @OperationLog(module = "system", description = "批量更新用户")
    public Result<Map<String, Object>> batchUpdate(@RequestBody Map<String, Object> body) {
        List<String> ids = Boolean.TRUE.equals(body.get("all"))
                ? resolveUserIdsByFilter(body)
                : parseUserIds(body);
        if (ids.isEmpty()) {
            throw new BizException(400, "请选择用户或确认当前查询条件下有结果");
        }
        if (ids.size() > 5000) {
            throw new BizException(400, "单次批量修改不能超过 5000 人，请缩小查询条件");
        }
        boolean setDept = Boolean.TRUE.equals(body.get("setDept"));
        boolean setActive = Boolean.TRUE.equals(body.get("setActive"));
        boolean setRepairEngineer = Boolean.TRUE.equals(body.get("setRepairEngineer"));
        if (!setDept && !setActive && !setRepairEngineer) {
            throw new BizException(400, "请至少勾选一项要修改的字段");
        }

        int updated = 0;
        for (String idStr : ids) {
            UUID id = UUID.fromString(idStr);
            Map<String, Object> before = changeLog.loadRow("sys_user", id.toString());
            if (before == null) continue;

            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            if (setDept) {
                sets.add("dept_id = ?::uuid");
                Object deptId = body.get("dept_id");
                args.add(deptId == null || String.valueOf(deptId).isBlank() ? null : String.valueOf(deptId));
            }
            if (setActive) {
                sets.add("is_active = ?");
                args.add(Boolean.TRUE.equals(body.get("is_active")));
            }
            if (setRepairEngineer) {
                sets.add("is_repair_engineer = ?");
                args.add(Boolean.TRUE.equals(body.get("is_repair_engineer")));
            }
            sets.add("updated_at = NOW()");
            args.add(id);
            jdbc.update("UPDATE sys_user SET " + String.join(", ", sets) + " WHERE id = ?::uuid", args.toArray());
            changeLog.recordUpdate("sys_user", id, before, changeLog.loadRow("sys_user", id.toString()));
            updated++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", updated);
        return Result.ok(result);
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
                "SELECT permissions, permission_mode FROM sys_user WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null),
                id);
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
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT role_ids FROM sys_user WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null),
                id);
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

    /** 当前登录用户修改密码（MOB-UI-02：我的 → 修改密码） */
    @PostMapping("/me/change-password")
    @OperationLog(module = "system", description = "修改本人密码")
    public Result<Void> changeMyPassword(@RequestBody Map<String, String> body) {
        UUID userId = requireCurrentUserId();
        String oldPassword = body == null ? null : body.get("oldPassword");
        String newPassword = body == null ? null : body.get("newPassword");
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BizException(400, "请输入原密码");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException(400, "请输入新密码");
        }
        if (newPassword.length() < 6) {
            throw new BizException(400, "新密码至少 6 位");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BizException(400, "新密码不能与原密码相同");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT password_hash FROM sys_user WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null),
                userId);
        if (rows.isEmpty()) throw new BizException(404, "user not found");
        String hash = String.valueOf(rows.get(0).get("password_hash"));
        if (!encoder.matches(oldPassword, hash)) {
            throw new BizException(400, "原密码不正确");
        }
        jdbc.update(
                "UPDATE sys_user SET password_hash = ?, updated_at = NOW() WHERE id = ?::uuid",
                encoder.encode(newPassword), userId);
        return Result.ok();
    }

    /** DASH-UI-06：当前登录用户 UI 偏好 */
    @GetMapping("/me/preferences")
    public Result<Map<String, Object>> myPreferences() {
        UUID userId = requireCurrentUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT preferences FROM sys_user WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null),
                userId);
        if (rows.isEmpty()) throw new BizException(404, "user not found");
        Map<String, Object> prefs = parsePreferences(rows.get(0).get("preferences"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("quickEntryPaths", prefs.get("quickEntryPaths"));
        return Result.ok(result);
    }

    /** DASH-UI-06/07：保存快捷入口（仅本人；path 须属有效菜单；最多 45） */
    @PutMapping("/me/preferences/quick-entry")
    @OperationLog(module = "system", description = "保存快捷入口")
    public Result<Map<String, Object>> saveQuickEntry(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID userId = requireCurrentUserId();
        if (tenantId == null || tenantId.isBlank()) tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BizException(400, "缺少租户信息");
        }

        List<String> paths = normalizeQuickEntryPaths(body.get("paths"));
        if (paths.size() > QUICK_ENTRY_MAX) {
            throw new BizException(400, "快捷入口最多选择 " + QUICK_ENTRY_MAX + " 项");
        }

        Set<String> allowedPaths = currentUserAllowedMenuPaths(tenantId, userId);
        for (String path : paths) {
            if (!allowedPaths.contains(path)) {
                throw new BizException(400, "无权限设置菜单快捷入口：" + path);
            }
        }

        try {
            String pathsJson = objectMapper.writeValueAsString(paths);
            jdbc.update(
                    "UPDATE sys_user SET preferences = jsonb_set(COALESCE(preferences, '{}'::jsonb), '{quickEntryPaths}', ?::jsonb, true),"
                            + " updated_at = NOW() WHERE id = ?::uuid",
                    pathsJson, userId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "保存快捷入口失败");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("quickEntryPaths", paths);
        return Result.ok(result);
    }

    private UUID requireCurrentUserId() {
        String raw = TenantContext.getUserId();
        if (raw == null || raw.isBlank()) {
            throw new BizException(401, "未登录");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BizException(401, "无效用户");
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> currentUserAllowedMenuPaths(String tenantId, UUID userId) {
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT permissions, role_ids FROM sys_user WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null),
                userId);
        if (users.isEmpty()) throw new BizException(404, "user not found");
        Map<String, Object> user = users.get(0);
        Map<String, Object> perms = permissionService.resolveUserEffectivePermissions(
                tenantId,
                TenantContext.getSchemaName(),
                userId.toString(),
                user.get("permissions"),
                allRoleIds(user.get("role_ids")));
        List<String> menuCodes = (List<String>) perms.getOrDefault("menus", List.of());
        if (menuCodes.isEmpty()) return Set.of();

        String placeholders = String.join(",", Collections.nCopies(menuCodes.size(), "?"));
        List<Object> args = new ArrayList<>(menuCodes);
        List<Map<String, Object>> menus = jdbc.queryForList(
                "SELECT path FROM public.sys_menu WHERE is_active = true AND menu_type = 'menu'"
                        + " AND path IS NOT NULL AND btrim(path::text) <> ''"
                        + " AND menu_code IN (" + placeholders + ")",
                args.toArray());
        Set<String> paths = new LinkedHashSet<>();
        for (Map<String, Object> m : menus) {
            Object path = m.get("path");
            if (path != null && !path.toString().isBlank()) {
                paths.add(path.toString().trim());
            }
        }
        return paths;
    }

    private List<String> normalizeQuickEntryPaths(Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) {
            throw new BizException(400, "paths 须为数组");
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (Object o : list) {
            if (o == null) continue;
            String path = String.valueOf(o).trim();
            if (path.isEmpty()) continue;
            if (!path.startsWith("/")) {
                throw new BizException(400, "无效菜单路径：" + path);
            }
            ordered.add(path);
        }
        return new ArrayList<>(ordered);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePreferences(Object raw) {
        if (raw == null) return new LinkedHashMap<>();
        try {
            if (raw instanceof PGobject pg && pg.getValue() != null) {
                return objectMapper.readValue(pg.getValue(), new TypeReference<>() {});
            }
            if (raw instanceof String s) {
                if (s.isBlank()) return new LinkedHashMap<>();
                return objectMapper.readValue(s, new TypeReference<>() {});
            }
            if (raw instanceof Map<?, ?> m) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() != null) copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                return copy;
            }
            return objectMapper.convertValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void copyRolePermissions(UUID userId, UUID roleId) {
        List<Map<String, Object>> roles = jdbc.queryForList(
                "SELECT permissions FROM sys_role WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_role", null),
                roleId);
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
                "SELECT role_code, role_name FROM sys_role WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_role", null),
                roleIds[0]);
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

    /** 按与列表 page 相同的筛选条件解析用户 id（用于「全部查询结果」批量修改） */
    private List<String> resolveUserIdsByFilter(Map<String, Object> body) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u"));
        List<Object> args = new ArrayList<>();
        Object keyword = body.get("keyword");
        if (keyword != null && !String.valueOf(keyword).isBlank()) {
            where.append(" AND (u.username ILIKE ? OR u.real_name ILIKE ? OR u.employee_no ILIKE ? OR u.phone ILIKE ?) ");
            String kw = "%" + String.valueOf(keyword).trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        Object isActive = body.get("isActive");
        if (isActive instanceof Boolean b) {
            where.append(" AND u.is_active = ? ");
            args.add(b);
        }
        Object deptId = body.get("deptId");
        if (deptId != null && !String.valueOf(deptId).isBlank()) {
            where.append(" AND u.dept_id = ?::uuid ");
            args.add(String.valueOf(deptId));
        }
        Object isRepairEngineer = body.get("isRepairEngineer");
        if (isRepairEngineer instanceof Boolean b) {
            where.append(" AND COALESCE(u.is_repair_engineer, false) = ? ");
            args.add(b);
        }
        Object roleId = body.get("roleId");
        if (roleId != null && !String.valueOf(roleId).isBlank()) {
            where.append(" AND u.role_ids IS NOT NULL AND ?::uuid = ANY(u.role_ids) ");
            args.add(String.valueOf(roleId));
        }
        Object permissionMode = body.get("permissionMode");
        if (permissionMode != null && !String.valueOf(permissionMode).isBlank()) {
            where.append(" AND COALESCE(u.permission_mode, 'synced') = ? ");
            args.add(String.valueOf(permissionMode));
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT u.id FROM sys_user u " + where + " ORDER BY u.created_at DESC LIMIT 5000",
                args.toArray());
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            if (id != null) ids.add(String.valueOf(id));
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseUserIds(Map<String, Object> body) {
        Object raw = body.get("userIds") != null ? body.get("userIds") : body.get("user_ids");
        if (raw == null) return List.of();
        if (raw instanceof List<?> list) {
            List<String> ids = new ArrayList<>();
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) ids.add(String.valueOf(o));
            }
            return ids;
        }
        return List.of();
    }

    private UUID[] allRoleIds(Object raw) {
        if (raw == null) return new UUID[0];
        if (raw instanceof UUID[] uuids) return uuids;
        if (raw instanceof Array arr) {
            try {
                Object[] objs = (Object[]) arr.getArray();
                UUID[] ids = new UUID[objs.length];
                for (int i = 0; i < objs.length; i++) {
                    ids[i] = UUID.fromString(objs[i].toString());
                }
                return ids;
            } catch (Exception e) {
                return new UUID[0];
            }
        }
        if (raw instanceof Object[] objs) {
            UUID[] ids = new UUID[objs.length];
            for (int i = 0; i < objs.length; i++) {
                ids[i] = UUID.fromString(objs[i].toString());
            }
            return ids;
        }
        return new UUID[0];
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
