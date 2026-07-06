package com.meis.saas.common.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public final class CacheKeys {
    private static final String PREFIX = "meis:";

    private CacheKeys() {}

    public static String userPerms(String tenantId, String userId) {
        return PREFIX + "perm:user:" + tenantId + ":" + userId;
    }

    public static String tenantMenus(String tenantId) {
        return PREFIX + "tenant:menus:" + tenantId;
    }

    public static String menuPermTree(String tenantId) {
        return PREFIX + "menu:perm-tree:" + tenantId;
    }

    public static String menuNav(String tenantId, String menusHash) {
        return PREFIX + "menu:nav:" + tenantId + ":" + menusHash;
    }

    public static String platformNav() {
        return PREFIX + "menu:platform-nav";
    }

    public static String platformMenuTree() {
        return PREFIX + "menu:platform-tree";
    }

    public static String dictTypes(String schema) {
        return PREFIX + "dict:types:" + schema;
    }

    public static String dictByType(String schema, String dictType) {
        return PREFIX + "dict:" + schema + ":" + dictType;
    }

    public static String buttonPerms(String schema) {
        return PREFIX + "dict:" + schema + ":button_perm";
    }

    public static String campuses(String schema) {
        return PREFIX + "org:campus:" + schema;
    }

    public static String deptList(String schema) {
        return PREFIX + "org:dept-list:" + schema;
    }

    public static String deptTree(String schema) {
        return PREFIX + "org:dept-tree:" + schema;
    }

    public static String orgPermTree(String schema) {
        return PREFIX + "org:perm-tree:" + schema;
    }

    public static String loginFail(String tenantCode, String username) {
        return PREFIX + "login:fail:" + tenantCode + ":" + username;
    }

    public static String tokenBlacklist(String jti) {
        return PREFIX + "token:blacklist:" + jti;
    }

    public static String tenantUserPermPattern(String tenantId) {
        return PREFIX + "perm:user:" + tenantId + ":*";
    }

    public static String menuNavPattern(String tenantId) {
        return PREFIX + "menu:nav:" + tenantId + ":*";
    }

    public static String menusHash(List<String> menus) {
        if (menus == null || menus.isEmpty()) return "all";
        List<String> sorted = menus.stream().sorted().toList();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(String.join(",", sorted).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(sorted.hashCode());
        }
    }
}
