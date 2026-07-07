package com.meis.saas.purchase.support;

import com.meis.saas.common.rbac.PermissionContext;
import com.meis.saas.common.rbac.PermissionInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PurchaseDataScope {
    private PurchaseDataScope() {}

    public static void applyPlanFilter(StringBuilder where, List<Object> args) {
        PermissionContext ctx = PermissionInterceptor.CTX.get();
        if (ctx == null) return;
        String scope = ctx.getDataScope();
        if (scope == null || "all".equals(scope)) return;
        if ("self".equals(scope) && ctx.getUserId() != null) {
            where.append(" AND p.applicant_id = ?::uuid ");
            args.add(UUID.fromString(ctx.getUserId()));
            return;
        }
        if ("dept".equals(scope) && ctx.getUserId() != null) {
            where.append(" AND p.dept_id = (SELECT dept_id FROM sys_user WHERE id = ?::uuid) ");
            args.add(UUID.fromString(ctx.getUserId()));
            return;
        }
        if ("custom".equals(scope) && ctx.getDeptIds() != null && !ctx.getDeptIds().isEmpty()) {
            appendDeptIn(where, args, "p.dept_id", ctx.getDeptIds());
        }
    }

    public static void applyProjectFilter(StringBuilder where, List<Object> args) {
        PermissionContext ctx = PermissionInterceptor.CTX.get();
        if (ctx == null) return;
        String scope = ctx.getDataScope();
        if (scope == null || "all".equals(scope)) return;
        if ("dept".equals(scope) && ctx.getUserId() != null) {
            where.append("""
                 AND pj.plan_id IN (
                   SELECT pl.id FROM purchase_plan pl
                   WHERE pl.dept_id = (SELECT dept_id FROM sys_user WHERE id = ?::uuid)
                 ) """);
            args.add(UUID.fromString(ctx.getUserId()));
            return;
        }
        if ("custom".equals(scope) && ctx.getDeptIds() != null && !ctx.getDeptIds().isEmpty()) {
            where.append(" AND pj.plan_id IN (SELECT pl.id FROM purchase_plan pl WHERE ");
            appendDeptIn(where, args, "pl.dept_id", ctx.getDeptIds());
            where.append(") ");
        }
    }

    private static void appendDeptIn(StringBuilder where, List<Object> args, String col, List<String> deptIds) {
        List<String> ids = new ArrayList<>(deptIds);
        if (ids.isEmpty()) return;
        where.append(col).append(" IN (");
        where.append(String.join(",", ids.stream().map(d -> "?::uuid").toList()));
        where.append(") ");
        ids.forEach(args::add);
    }
}
