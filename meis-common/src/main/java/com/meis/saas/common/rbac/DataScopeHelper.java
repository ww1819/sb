package com.meis.saas.common.rbac;

import java.util.List;
import java.util.stream.Collectors;

public final class DataScopeHelper {
    private DataScopeHelper() {}

    public static String clause(PermissionContext ctx, String userCol, String deptCol) {
        if (ctx == null || "all".equals(ctx.getDataScope())) return "1=1";
        if ("self".equals(ctx.getDataScope()) && ctx.getUserId() != null) {
            return userCol + " = '" + ctx.getUserId() + "'::uuid";
        }
        if ("dept".equals(ctx.getDataScope()) && ctx.getDeptId() != null) {
            return deptCol + " = '" + ctx.getDeptId() + "'::uuid";
        }
        if ("custom".equals(ctx.getDataScope()) && ctx.getDeptIds() != null && !ctx.getDeptIds().isEmpty()) {
            String in = ctx.getDeptIds().stream()
                    .map(id -> "'" + id + "'::uuid")
                    .collect(Collectors.joining(","));
            return deptCol + " IN (" + in + ")";
        }
        return "1=1";
    }

    public static String warehouseClause(PermissionContext ctx, String warehouseCol) {
        if (ctx == null || ctx.getWarehouseIds() == null || ctx.getWarehouseIds().isEmpty()) return "1=1";
        String in = ctx.getWarehouseIds().stream()
                .map(id -> "'" + id + "'::uuid")
                .collect(Collectors.joining(","));
        return warehouseCol + " IN (" + in + ")";
    }
}
