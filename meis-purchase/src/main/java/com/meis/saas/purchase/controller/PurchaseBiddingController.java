package com.meis.saas.purchase.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.rbac.PermissionContext;
import com.meis.saas.common.rbac.PermissionInterceptor;
import com.meis.saas.common.result.Result;
import com.meis.saas.purchase.support.PurchasePageQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/** PUR-UI-14/15：招标管理（议价通过明细 + 供应商） */
@RestController
@RequestMapping("/api/purchase/bidding")
@RequiredArgsConstructor
public class PurchaseBiddingController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query) {
        return Result.ok(PurchasePageQueries.bargainPassedPlanItemPage(jdbc, query));
    }

    /** PUR-UI-15：招标供应商明细 */
    @GetMapping("/approved-items/{itemId}/suppliers")
    public Result<List<Map<String, Object>>> listSuppliers(@PathVariable UUID itemId) {
        assertBargainPassed(itemId);
        var rows = jdbc.queryForList("""
                SELECT id, plan_item_id, supplier_name, contact_person, contact_phone, brand, specification,
                       final_amount, warranty_period, preferential_terms, sort_order
                FROM purchase_plan_item_bid_supplier
                WHERE plan_item_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item_bid_supplier", null) + """
                ORDER BY sort_order ASC NULLS LAST, created_at ASC NULLS LAST
                """, itemId);
        return Result.ok(rows);
    }

    @PutMapping("/approved-items/{itemId}/suppliers")
    @Transactional
    @OperationLog(module = "purchase", description = "保存招标供应商明细")
    public Result<List<Map<String, Object>>> saveSuppliers(@PathVariable UUID itemId,
            @RequestBody Map<String, Object> body) {
        assertBargainPassed(itemId);
        Object raw = body.get("items");
        if (raw == null) raw = body.get("suppliers");
        if (!(raw instanceof List<?> list)) {
            throw new BizException(400, "请提交供应商明细列表");
        }
        PermissionContext ctx = PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
        // 软删旧行后整表替换插入
        jdbc.update("""
                UPDATE purchase_plan_item_bid_supplier
                SET is_deleted = 1,
                    deleted_at = NOW(),
                    deleted_by = ?::uuid,
                    deleted_by_name = ?,
                    updated_at = NOW(),
                    updated_by = ?::uuid,
                    updated_by_name = ?
                WHERE plan_item_id = ?::uuid AND is_deleted = 0
                """, actorId, actorName, actorId, actorName, itemId);

        int order = 1;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) m;
            String name = blankToNull(row.get("supplier_name"));
            if (name == null) continue;
            jdbc.update("""
                    INSERT INTO purchase_plan_item_bid_supplier (
                        id, plan_item_id, supplier_name, contact_person, contact_phone, brand, specification,
                        final_amount, warranty_period, preferential_terms, sort_order,
                        created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                    ) VALUES (
                        ?::uuid, ?::uuid, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?,
                        NOW(), NOW(), ?::uuid, ?, ?::uuid, ?, 0
                    )
                    """,
                    UUID.randomUUID(), itemId, name,
                    blankToNull(row.get("contact_person")),
                    blankToNull(row.get("contact_phone")),
                    blankToNull(row.get("brand")),
                    blankToNull(row.get("specification")),
                    toDecimal(row.get("final_amount")),
                    blankToNull(row.get("warranty_period")),
                    blankToNull(row.get("preferential_terms")),
                    order++,
                    actorId, actorName, actorId, actorName);
        }
        return listSuppliers(itemId);
    }

    private void assertBargainPassed(UUID itemId) {
        var rows = jdbc.queryForList("""
                SELECT i.id, i.bargain_review_result, p.approval_status
                FROM purchase_plan_item i
                JOIN purchase_plan p ON p.id = i.plan_id
                WHERE i.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", "i")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p"), itemId);
        if (rows.isEmpty()) throw new BizException(404, "明细不存在");
        if (!"approved".equals(String.valueOf(rows.get(0).get("approval_status")))) {
            throw new BizException(400, "仅已审批通过的明细可维护招标供应商");
        }
        if (!"passed".equals(String.valueOf(rows.get(0).get("bargain_review_result")))) {
            throw new BizException(400, "仅议价审核通过的明细可维护招标供应商");
        }
    }

    private static String blankToNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new BizException(400, "最终金额格式不正确");
        }
    }
}
