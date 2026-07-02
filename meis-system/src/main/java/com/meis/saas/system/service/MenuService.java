package com.meis.saas.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> tenantMenus(String tenantId, List<String> effectiveMenuCodes) {
        Set<String> allowed = new HashSet<>(effectiveMenuCodes);
        if (allowed.contains("*")) {
            allowed.addAll(jdbc.queryForList(
                    "SELECT menu_code FROM public.sys_tenant_menu WHERE tenant_id = ?::uuid", String.class, tenantId));
        }
        List<Map<String, Object>> allMenus = jdbc.queryForList(
                "SELECT * FROM public.sys_menu WHERE is_active = true ORDER BY sort_order");
        expandAllowedWithParents(allowed, allMenus);
        allowed.remove("mod_platform");
        allowed.removeIf(code -> code.startsWith("platform_"));

        List<Map<String, Object>> modules = allMenus.stream()
                .filter(m -> "module".equals(m.get("menu_type")) && isModuleVisible(m.get("menu_code").toString(), allowed, allMenus))
                .toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> mod : modules) {
            String modCode = mod.get("menu_code").toString();
            List<Map<String, Object>> children = allMenus.stream()
                    .filter(m -> modCode.equals(m.get("parent_code")) && "menu".equals(m.get("menu_type"))
                            && allowed.contains(m.get("menu_code")))
                    .toList();
            if (children.isEmpty() && mod.get("path") != null) {
                result.add(singleModule(mod));
                continue;
            }
            Map<String, Object> module = new LinkedHashMap<>();
            module.put("id", modCode.replace("mod_", ""));
            module.put("title", mod.get("menu_name"));
            if (mod.get("path") != null) module.put("path", mod.get("path"));
            module.put("groups", buildGroups(modCode, children, allMenus));
            result.add(module);
        }
        return result;
    }

    private void expandAllowedWithParents(Set<String> allowed, List<Map<String, Object>> allMenus) {
        Map<String, String> parentOf = new HashMap<>();
        for (Map<String, Object> m : allMenus) {
            Object parent = m.get("parent_code");
            if (parent != null) parentOf.put(m.get("menu_code").toString(), parent.toString());
        }
        Set<String> expanded = new HashSet<>(allowed);
        for (String code : allowed) {
            String parent = parentOf.get(code);
            while (parent != null) {
                expanded.add(parent);
                parent = parentOf.get(parent);
            }
        }
        allowed.clear();
        allowed.addAll(expanded);
    }

    private boolean isModuleVisible(String modCode, Set<String> allowed, List<Map<String, Object>> allMenus) {
        if (allowed.contains(modCode)) return true;
        return allMenus.stream()
                .anyMatch(m -> modCode.equals(m.get("parent_code"))
                        && "menu".equals(m.get("menu_type"))
                        && allowed.contains(m.get("menu_code").toString()));
    }

    public List<Map<String, Object>> platformMenuTree() {
        List<Map<String, Object>> all = jdbc.queryForList(
                "SELECT * FROM public.sys_menu WHERE is_active = true ORDER BY sort_order");
        return buildTree(all, null);
    }

    /** 权限配置用菜单树（租户已授权，不含平台菜单） */
    public List<Map<String, Object>> permissionMenuTree(String tenantId) {
        Set<String> allowed = new HashSet<>(jdbc.queryForList(
                "SELECT menu_code FROM public.sys_tenant_menu WHERE tenant_id = ?::uuid", String.class, tenantId));
        List<Map<String, Object>> allMenus = jdbc.queryForList(
                "SELECT menu_code, parent_code, menu_name, menu_type, sort_order FROM public.sys_menu WHERE is_active = true ORDER BY sort_order");
        expandAllowedWithParents(allowed, allMenus);
        allowed.remove("mod_platform");
        allowed.removeIf(c -> c.startsWith("platform_"));
        List<Map<String, Object>> filtered = allMenus.stream()
                .filter(m -> allowed.contains(m.get("menu_code").toString()))
                .toList();
        return buildPermTree(filtered, null);
    }

    private List<Map<String, Object>> buildPermTree(List<Map<String, Object>> all, String parent) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> m : all) {
            Object pc = m.get("parent_code");
            String p = pc == null ? null : pc.toString();
            if (Objects.equals(parent, p) || (parent == null && pc == null)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", m.get("menu_code"));
                node.put("label", m.get("menu_name"));
                node.put("menuCode", m.get("menu_code"));
                node.put("menuType", m.get("menu_type"));
                node.put("children", buildPermTree(all, m.get("menu_code").toString()));
                result.add(node);
            }
        }
        return result;
    }

    /** 平台管理员顶栏导航（仅 mod_platform 子树） */
    public List<Map<String, Object>> platformNavMenus() {
        Set<String> allowed = Set.of(
                "mod_platform", "platform_tenant", "platform_tenant_menu", "platform_package");
        List<Map<String, Object>> allMenus = jdbc.queryForList(
                "SELECT * FROM public.sys_menu WHERE is_active = true ORDER BY sort_order");
        List<Map<String, Object>> modules = allMenus.stream()
                .filter(m -> "module".equals(m.get("menu_type")) && allowed.contains(m.get("menu_code")))
                .toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> mod : modules) {
            String modCode = mod.get("menu_code").toString();
            List<Map<String, Object>> children = allMenus.stream()
                    .filter(m -> modCode.equals(m.get("parent_code")) && "menu".equals(m.get("menu_type"))
                            && allowed.contains(m.get("menu_code")))
                    .toList();
            Map<String, Object> module = new LinkedHashMap<>();
            module.put("id", modCode.replace("mod_", ""));
            module.put("title", mod.get("menu_name"));
            if (mod.get("path") != null) module.put("path", mod.get("path"));
            module.put("groups", List.of(Map.of("title", "", "items", children.stream().map(this::toLeaf).toList())));
            result.add(module);
        }
        return result;
    }

    private List<Map<String, Object>> buildGroups(String modCode, List<Map<String, Object>> menus,
                                                   List<Map<String, Object>> allMenus) {
        Map<String, List<Map<String, Object>>> byParent = menus.stream()
                .collect(Collectors.groupingBy(m -> m.get("parent_code").toString()));
        List<Map<String, Object>> groups = new ArrayList<>();
        List<Map<String, Object>> items = menus.stream().map(this::toLeaf).toList();
        if (!items.isEmpty()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("title", groupTitle(modCode));
            g.put("items", items);
            groups.add(g);
        }
        return groups;
    }

    private String groupTitle(String modCode) {
        return switch (modCode) {
            case "mod_ops" -> "";
            case "mod_quality" -> "";
            default -> "";
        };
    }

    private Map<String, Object> singleModule(Map<String, Object> mod) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", mod.get("menu_code").toString().replace("mod_", ""));
        m.put("title", mod.get("menu_name"));
        m.put("path", mod.get("path"));
        m.put("groups", List.of());
        return m;
    }

    private Map<String, Object> toLeaf(Map<String, Object> menu) {
        Map<String, Object> m = new LinkedHashMap<>();
        String code = menu.get("menu_code").toString();
        m.put("id", code.replace("_", "-"));
        m.put("title", menu.get("menu_name"));
        m.put("path", menu.get("path"));
        m.put("menuCode", code);
        return m;
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> all, String parent) {
        return all.stream()
                .filter(m -> Objects.equals(parent, m.get("parent_code") != null ? m.get("parent_code").toString() : null)
                        || (parent == null && m.get("parent_code") == null))
                .map(m -> {
                    Map<String, Object> node = new LinkedHashMap<>(m);
                    node.put("children", buildTree(all, m.get("menu_code").toString()));
                    return node;
                }).toList();
    }
}
