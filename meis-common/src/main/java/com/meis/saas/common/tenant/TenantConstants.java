package com.meis.saas.common.tenant;

public final class TenantConstants {
    public static final String HEADER_TENANT_SCHEMA = "X-Tenant-Schema";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_USER_TYPE = "X-User-Type";
    public static final String SCHEMA_PREFIX = "tenant_";

    private TenantConstants() {}

    public static String toSchemaName(String tenantCode) {
        return SCHEMA_PREFIX + tenantCode.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }
}
