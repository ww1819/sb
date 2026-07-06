package com.meis.saas.common.cache;

import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MeisCacheEviction {
    private final RedisJsonCache cache;

    public void evictUserPermission(String tenantId, String userId) {
        if (tenantId == null || userId == null) return;
        cache.evict(CacheKeys.userPerms(tenantId, userId));
    }

    public void evictTenantPermissions(String tenantId) {
        if (tenantId == null) return;
        cache.evictByPattern(CacheKeys.tenantUserPermPattern(tenantId));
    }

    public void evictTenantMenus(String tenantId) {
        if (tenantId == null) return;
        cache.evict(CacheKeys.tenantMenus(tenantId));
        cache.evict(CacheKeys.menuPermTree(tenantId));
        cache.evictByPattern(CacheKeys.menuNavPattern(tenantId));
    }

    public void evictPlatformMenus() {
        cache.evict(CacheKeys.platformNav());
        cache.evict(CacheKeys.platformMenuTree());
    }

    public void evictSchemaDict(String schema) {
        if (schema == null) schema = TenantContext.getSchemaName();
        if (schema == null || schema.isBlank()) return;
        cache.evict(CacheKeys.dictTypes(schema));
        cache.evict(CacheKeys.buttonPerms(schema));
        cache.evictByPattern("meis:dict:" + schema + ":*");
    }

    public void evictSchemaOrg(String schema) {
        if (schema == null) schema = TenantContext.getSchemaName();
        if (schema == null || schema.isBlank()) return;
        cache.evict(CacheKeys.campuses(schema));
        cache.evict(CacheKeys.deptList(schema));
        cache.evict(CacheKeys.deptTree(schema));
        cache.evict(CacheKeys.orgPermTree(schema));
    }

    public void evictDictType(String schema, String dictType) {
        if (schema == null) schema = TenantContext.getSchemaName();
        if (schema == null || dictType == null) return;
        cache.evict(CacheKeys.dictTypes(schema));
        cache.evict(CacheKeys.dictByType(schema, dictType));
        if ("button_perm".equals(dictType)) {
            cache.evict(CacheKeys.buttonPerms(schema));
        }
    }
}
