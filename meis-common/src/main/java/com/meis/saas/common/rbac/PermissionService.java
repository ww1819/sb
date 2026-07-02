package com.meis.saas.common.rbac;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<String> tenantAuthorizedMenus(String tenantId) {
        return jdbc.queryForList(
                "SELECT menu_code FROM sys_tenant_menu WHERE tenant_id = ?::uuid",
                String.class, tenantId);
    }

    public Map<String, Object> loadRolePermissions(String schema, UUID[] roleIds) {
        if (roleIds == null || roleIds.length == 0) {
            return emptyPermissions();
        }
        String in = Arrays.stream(roleIds).map(id -> "'" + id + "'").collect(Collectors.joining(","));
        List<Map<String, Object>> roles = jdbc.queryForList(
                "SELECT permissions FROM " + schema + ".sys_role WHERE id IN (" + in + ") AND is_active = true");
        return mergePermissionMaps(roles.stream().map(r -> parsePermissions(r.get("permissions"))).toList());
    }

    public Map<String, Object> parsePermissions(Object raw) {
        if (raw == null) return emptyPermissions();
        try {
            if (raw instanceof PGobject pg && pg.getValue() != null) {
                return parsePermissions(pg.getValue());
            }
            if (raw instanceof String s) {
                if (s.isBlank()) return emptyPermissions();
                if (s.startsWith("[")) {
                    List<String> all = mapper.readValue(s, new TypeReference<>() {});
                    if (all.contains("*")) return fullPermissions();
                    return Map.of("menus", all, "buttons", List.of(), "dataScope", "self",
                            "deptIds", List.of(), "warehouseIds", List.of());
                }
                Map<String, Object> m = mapper.readValue(s, new TypeReference<>() {});
                return normalizePermissionMap(m);
            }
            if (raw instanceof Map<?, ?> m) return normalizePermissionMap((Map<String, Object>) m);
            return normalizePermissionMap(mapper.convertValue(raw, new TypeReference<>() {}));
        } catch (Exception e) {
            return emptyPermissions();
        }
    }

    public List<String> effectiveMenus(String tenantId, Map<String, Object> perms) {
        List<String> tenantMenus = tenantAuthorizedMenus(tenantId);
        @SuppressWarnings("unchecked")
        List<String> userMenus = (List<String>) perms.getOrDefault("menus", List.of());
        if (userMenus.contains("*")) return tenantMenus;
        Set<String> tenantSet = new HashSet<>(tenantMenus);
        return userMenus.stream().filter(tenantSet::contains).distinct().toList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> effectivePermissions(String tenantId, Map<String, Object> perms) {
        List<String> menus = effectiveMenus(tenantId, perms);
        List<String> buttons = (List<String>) perms.getOrDefault("buttons", List.of());
        if (buttons.contains("*")) buttons = List.of("*");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menus", menus);
        result.put("buttons", buttons);
        result.put("dataScope", perms.getOrDefault("dataScope", "self"));
        result.put("deptIds", perms.getOrDefault("deptIds", List.of()));
        result.put("warehouseIds", perms.getOrDefault("warehouseIds", List.of()));
        return result;
    }

    public void validatePermissions(String tenantId, String schema, Map<String, Object> permissions) {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("tenant schema required");
        }
        @SuppressWarnings("unchecked")
        List<String> menus = (List<String>) permissions.getOrDefault("menus", List.of());
        validateRoleMenus(tenantId, menus);
        @SuppressWarnings("unchecked")
        List<String> deptIds = (List<String>) permissions.getOrDefault("deptIds", List.of());
        validateDepts(schema, deptIds);
        @SuppressWarnings("unchecked")
        List<String> warehouseIds = (List<String>) permissions.getOrDefault("warehouseIds", List.of());
        validateWarehouses(schema, warehouseIds);
    }

    public void validateRoleMenus(String tenantId, List<String> roleMenus) {
        if (roleMenus.contains("*")) return;
        Set<String> allowed = new HashSet<>(tenantAuthorizedMenus(tenantId));
        for (String m : roleMenus) {
            if (!allowed.contains(m)) {
                throw new IllegalArgumentException("menu not authorized for tenant: " + m);
            }
        }
    }

    public void validateDepts(String schema, List<String> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) return;
        for (String id : deptIds) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT 1 FROM " + schema + ".department WHERE id = ?::uuid AND is_active = true", id);
            if (rows.isEmpty()) throw new IllegalArgumentException("invalid dept id: " + id);
        }
    }

    public void validateWarehouses(String schema, List<String> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty()) return;
        for (String id : warehouseIds) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT 1 FROM " + schema + ".warehouse WHERE id = ?::uuid AND is_active = true", id);
            if (rows.isEmpty()) throw new IllegalArgumentException("invalid warehouse id: " + id);
        }
    }

    public String toJson(Map<String, Object> permissions) {
        try {
            return mapper.writeValueAsString(normalizePermissionMap(permissions));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Map<String, Object> normalizePermissionMap(Map<String, Object> m) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menus", m.getOrDefault("menus", List.of()));
        result.put("buttons", m.getOrDefault("buttons", List.of()));
        result.put("dataScope", m.getOrDefault("dataScope", "self"));
        result.put("deptIds", m.getOrDefault("deptIds", List.of()));
        result.put("warehouseIds", m.getOrDefault("warehouseIds", List.of()));
        return result;
    }

    private Map<String, Object> mergePermissionMaps(List<Map<String, Object>> list) {
        if (list.isEmpty()) return emptyPermissions();
        if (list.size() == 1) return normalizePermissionMap(list.get(0));
        Set<String> menus = new HashSet<>();
        Set<String> buttons = new HashSet<>();
        Set<String> deptIds = new HashSet<>();
        Set<String> warehouseIds = new HashSet<>();
        String dataScope = "self";
        for (Map<String, Object> p : list) {
            mergeMenus(menus, p);
            mergeButtons(buttons, p);
            dataScope = mergeDataScope(dataScope, (String) p.getOrDefault("dataScope", "self"));
            @SuppressWarnings("unchecked")
            List<String> ds = (List<String>) p.getOrDefault("deptIds", List.of());
            deptIds.addAll(ds);
            @SuppressWarnings("unchecked")
            List<String> ws = (List<String>) p.getOrDefault("warehouseIds", List.of());
            warehouseIds.addAll(ws);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menus", new ArrayList<>(menus));
        result.put("buttons", new ArrayList<>(buttons));
        result.put("dataScope", dataScope);
        result.put("deptIds", new ArrayList<>(deptIds));
        result.put("warehouseIds", new ArrayList<>(warehouseIds));
        return result;
    }

    public Map<String, Object> emptyPermissions() {
        return Map.of("menus", List.of(), "buttons", List.of(), "dataScope", "self",
                "deptIds", List.of(), "warehouseIds", List.of());
    }

    public Map<String, Object> fullPermissions() {
        return Map.of("menus", List.of("*"), "buttons", List.of("*"), "dataScope", "all",
                "deptIds", List.of(), "warehouseIds", List.of());
    }

    @SuppressWarnings("unchecked")
    private void mergeMenus(Set<String> target, Map<String, Object> p) {
        List<String> menus = (List<String>) p.getOrDefault("menus", List.of());
        if (menus.contains("*")) { target.clear(); target.add("*"); return; }
        if (!target.contains("*")) target.addAll(menus);
    }

    @SuppressWarnings("unchecked")
    private void mergeButtons(Set<String> target, Map<String, Object> p) {
        List<String> buttons = (List<String>) p.getOrDefault("buttons", List.of());
        if (buttons.contains("*")) { target.clear(); target.add("*"); return; }
        if (!target.contains("*")) target.addAll(buttons);
    }

    private String mergeDataScope(String a, String b) {
        if ("all".equals(a) || "all".equals(b)) return "all";
        if ("custom".equals(a) || "custom".equals(b)) return "custom";
        if ("dept".equals(a) || "dept".equals(b)) return "dept";
        return "self";
    }
}
