package com.meis.saas.common.tenant;

public final class TenantContext {
    private static final ThreadLocal<TenantInfo> HOLDER = new ThreadLocal<>();

    public static void set(TenantInfo info) { HOLDER.set(info); }
    public static TenantInfo get() { return HOLDER.get(); }
    public static String getSchemaName() {
        TenantInfo i = HOLDER.get();
        return i != null && i.getSchemaName() != null ? i.getSchemaName() : "public";
    }
    public static String getUserId() {
        TenantInfo i = HOLDER.get();
        return i != null ? i.getUserId() : null;
    }
    public static String getTenantId() {
        TenantInfo i = HOLDER.get();
        return i != null ? i.getTenantId() : null;
    }
    public static void clear() { HOLDER.remove(); }
}
