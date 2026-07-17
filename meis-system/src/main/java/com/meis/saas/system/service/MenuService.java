package com.meis.saas.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.meis.saas.common.cache.CacheKeys;
import com.meis.saas.common.cache.MeisCacheProperties;
import com.meis.saas.common.cache.RedisJsonCache;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {
    private final JdbcTemplate jdbc;
    private final RedisJsonCache cache;
    private final MeisCacheProperties cacheProps;

    public List<Map<String, Object>> tenantMenus(String tenantId, List<String> effectiveMenuCodes) {
        String hash = CacheKeys.menusHash(effectiveMenuCodes);
        return cache.getOrLoad(
                CacheKeys.menuNav(tenantId, hash),
                cacheProps.getMenuNavTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                () -> buildTenantMenus(tenantId, effectiveMenuCodes));
    }

    private List<Map<String, Object>> buildTenantMenus(String tenantId, List<String> effectiveMenuCodes) {
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
            List<Map<String, Object>> groups = buildGroups(modCode, allMenus, allowed);
            if (groups.isEmpty() && mod.get("path") != null) {
                result.add(singleModule(mod));
                continue;
            }
            if (groups.isEmpty()) continue;
            Map<String, Object> module = new LinkedHashMap<>();
            module.put("id", modCode.replace("mod_", ""));
            module.put("title", mod.get("menu_name"));
            if (mod.get("path") != null) module.put("path", mod.get("path"));
            module.put("groups", groups);
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
        Map<String, String> parentOf = parentMap(allMenus);
        return allMenus.stream().anyMatch(m -> {
            if (!"menu".equals(m.get("menu_type"))) return false;
            if (!allowed.contains(m.get("menu_code").toString())) return false;
            Object parent = m.get("parent_code");
            if (parent == null) return false;
            return isUnderAncestor(parent.toString(), modCode, parentOf);
        });
    }

    private Map<String, String> parentMap(List<Map<String, Object>> allMenus) {
        Map<String, String> parentOf = new HashMap<>();
        for (Map<String, Object> m : allMenus) {
            Object parent = m.get("parent_code");
            if (parent != null) parentOf.put(m.get("menu_code").toString(), parent.toString());
        }
        return parentOf;
    }

    /** parentCode 或其任意上级是否等于 ancestorCode */
    private boolean isUnderAncestor(String parentCode, String ancestorCode, Map<String, String> parentOf) {
        String p = parentCode;
        int guard = 0;
        while (p != null && guard++ < 32) {
            if (ancestorCode.equals(p)) return true;
            p = parentOf.get(p);
        }
        return false;
    }

    public List<Map<String, Object>> platformMenuTree() {
        return cache.getOrLoad(
                CacheKeys.platformMenuTree(),
                cacheProps.getPlatformMenuTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                this::loadPlatformMenuTree);
    }

    private List<Map<String, Object>> loadPlatformMenuTree() {
        List<Map<String, Object>> all = jdbc.queryForList(
                "SELECT * FROM public.sys_menu WHERE is_active = true ORDER BY sort_order");
        return buildTree(all, null);
    }

    /** 权限配置用菜单树（租户已授权，不含平台菜单） */
    public List<Map<String, Object>> permissionMenuTree(String tenantId) {
        return cache.getOrLoad(
                CacheKeys.menuPermTree(tenantId),
                cacheProps.getMenuTreeTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                () -> buildPermissionMenuTree(tenantId));
    }

    private List<Map<String, Object>> buildPermissionMenuTree(String tenantId) {
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
        return cache.getOrLoad(
                CacheKeys.platformNav(),
                cacheProps.getPlatformMenuTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                this::buildPlatformNavMenus);
    }

    private List<Map<String, Object>> buildPlatformNavMenus() {
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
            Map<String, Object> module = new LinkedHashMap<>();
            module.put("id", modCode.replace("mod_", ""));
            module.put("title", mod.get("menu_name"));
            if (mod.get("path") != null) module.put("path", mod.get("path"));
            module.put("groups", buildGroups(modCode, allMenus, allowed));
            result.add(module);
        }
        return result;
    }

    /**
     * 组装导航分组：模块下可直接挂 menu，也可挂 menu_type=group 的二级分组；
     * 分组下可再挂 menu，也可再挂嵌套 group（如保养 → 巡检）。
     */
    private List<Map<String, Object>> buildGroups(String parentCode, List<Map<String, Object>> allMenus,
                                                   Set<String> allowed) {
        List<Map<String, Object>> directChildren = allMenus.stream()
                .filter(m -> parentCode.equals(String.valueOf(m.get("parent_code"))))
                .filter(m -> {
                    String type = String.valueOf(m.get("menu_type"));
                    String code = m.get("menu_code").toString();
                    if ("menu".equals(type)) return allowed.contains(code);
                    if ("group".equals(type)) {
                        return allowed.contains(code) || hasVisibleMenuUnder(code, allMenus, allowed);
                    }
                    return false;
                })
                .sorted(Comparator.comparingInt(m -> ((Number) m.getOrDefault("sort_order", 0)).intValue()))
                .toList();

        List<Map<String, Object>> groups = new ArrayList<>();
        List<Map<String, Object>> pendingDirect = new ArrayList<>();

        for (Map<String, Object> child : directChildren) {
            String type = String.valueOf(child.get("menu_type"));
            if ("menu".equals(type)) {
                pendingDirect.add(child);
                continue;
            }
            flushUntitledGroup(groups, pendingDirect);
            pendingDirect.clear();
            Map<String, Object> node = buildGroupNode(child, allMenus, allowed);
            if (node != null) groups.add(node);
        }
        flushUntitledGroup(groups, pendingDirect);
        return groups;
    }

    private Map<String, Object> buildGroupNode(Map<String, Object> groupMenu, List<Map<String, Object>> allMenus,
                                               Set<String> allowed) {
        String groupCode = groupMenu.get("menu_code").toString();
        List<Map<String, Object>> items = allMenus.stream()
                .filter(m -> groupCode.equals(String.valueOf(m.get("parent_code")))
                        && "menu".equals(m.get("menu_type"))
                        && allowed.contains(m.get("menu_code").toString()))
                .sorted(Comparator.comparingInt(m -> ((Number) m.getOrDefault("sort_order", 0)).intValue()))
                .map(this::toLeaf)
                .collect(Collectors.toCollection(ArrayList::new));
        List<Map<String, Object>> nested = allMenus.stream()
                .filter(m -> groupCode.equals(String.valueOf(m.get("parent_code")))
                        && "group".equals(m.get("menu_type")))
                .filter(m -> allowed.contains(m.get("menu_code").toString())
                        || hasVisibleMenuUnder(m.get("menu_code").toString(), allMenus, allowed))
                .sorted(Comparator.comparingInt(m -> ((Number) m.getOrDefault("sort_order", 0)).intValue()))
                .map(m -> buildGroupNode(m, allMenus, allowed))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        if (items.isEmpty() && nested.isEmpty()) return null;
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", groupCode.replace('_', '-'));
        g.put("title", groupMenu.get("menu_name"));
        g.put("items", items);
        if (!nested.isEmpty()) g.put("groups", nested);
        return g;
    }

    private void flushUntitledGroup(List<Map<String, Object>> groups, List<Map<String, Object>> pendingDirect) {
        if (pendingDirect.isEmpty()) return;
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("title", "");
        g.put("items", pendingDirect.stream().map(this::toLeaf).toList());
        groups.add(g);
    }

    private boolean hasVisibleMenuUnder(String groupCode, List<Map<String, Object>> allMenus, Set<String> allowed) {
        for (Map<String, Object> m : allMenus) {
            if (!groupCode.equals(String.valueOf(m.get("parent_code")))) continue;
            String type = String.valueOf(m.get("menu_type"));
            String code = m.get("menu_code").toString();
            if ("menu".equals(type) && allowed.contains(code)) return true;
            if ("group".equals(type) && hasVisibleMenuUnder(code, allMenus, allowed)) return true;
        }
        return false;
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
