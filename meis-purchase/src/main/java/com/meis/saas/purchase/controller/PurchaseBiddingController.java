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

/** PUR-UI-14/15/16/21：招标管理（议价通过明细 + 供应商 + 招标审核） */
@RestController
@RequestMapping("/api/purchase/bidding")
@RequiredArgsConstructor
public class PurchaseBiddingController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String bidding_review_result) {
        return Result.ok(PurchasePageQueries.bargainPassedPlanItemPage(jdbc, query, bidding_review_result));
    }

    /** PUR-UI-15/16：招标供应商明细 */
    @GetMapping("/approved-items/{itemId}/suppliers")
    public Result<List<Map<String, Object>>> listSuppliers(@PathVariable UUID itemId) {
        assertBargainPassed(itemId);
        var rows = jdbc.queryForList("""
                SELECT id, plan_item_id, supplier_id, supplier_name, contact_person, contact_phone, brand, specification,
                       final_amount, warranty_period, preferential_terms, bid_doc_url, is_winner, sort_order
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
        assertNotBiddingReviewed(itemId);
        Object raw = body.get("items");
        if (raw == null) raw = body.get("suppliers");
        if (!(raw instanceof List<?> list)) {
            throw new BizException(400, "请提交供应商明细列表");
        }
        int winnerCount = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            if (truthy(m.get("is_winner"))) winnerCount++;
        }
        if (winnerCount > 1) {
            throw new BizException(400, "同一明细只能选择一个中标供应商");
        }
        PermissionContext ctx = PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
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
            UUID supplierId = parseUuid(row.get("supplier_id"));
            String name = blankToNull(row.get("supplier_name"));
            String contactPerson = blankToNull(row.get("contact_person"));
            String contactPhone = blankToNull(row.get("contact_phone"));
            if (supplierId != null) {
                var suppliers = jdbc.queryForList("""
                        SELECT supplier_name, contact_person, contact_phone
                        FROM supplier WHERE id = ?::uuid
                        """ + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", null), supplierId);
                if (suppliers.isEmpty()) {
                    throw new BizException(400, "供应商不存在或已删除");
                }
                Map<String, Object> s = suppliers.get(0);
                name = blankToNull(s.get("supplier_name"));
                contactPerson = blankToNull(s.get("contact_person"));
                contactPhone = blankToNull(s.get("contact_phone"));
            }
            if (name == null) continue;
            boolean winner = truthy(row.get("is_winner"));
            jdbc.update("""
                    INSERT INTO purchase_plan_item_bid_supplier (
                        id, plan_item_id, supplier_id, supplier_name, contact_person, contact_phone, brand, specification,
                        final_amount, warranty_period, preferential_terms, bid_doc_url, is_winner, sort_order,
                        created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                    ) VALUES (
                        ?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?,
                        NOW(), NOW(), ?::uuid, ?, ?::uuid, ?, 0
                    )
                    """,
                    UUID.randomUUID(), itemId, supplierId, name,
                    contactPerson, contactPhone,
                    blankToNull(row.get("brand")),
                    blankToNull(row.get("specification")),
                    toDecimal(row.get("final_amount")),
                    blankToNull(row.get("warranty_period")),
                    blankToNull(row.get("preferential_terms")),
                    blankToNull(row.get("bid_doc_url")),
                    winner, order++,
                    actorId, actorName, actorId, actorName);
        }
        return listSuppliers(itemId);
    }

    /** PUR-UI-21：招标审核 */
    @PostMapping("/approved-items/{itemId}/bidding-review")
    @Transactional
    @OperationLog(module = "purchase", description = "招标审核")
    public Result<Map<String, Object>> biddingReview(@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
        assertBargainPassed(itemId);
        assertNotBiddingReviewed(itemId);
        assertReadyForBiddingReview(itemId);
        String result = blankToNull(body.get("result"));
        if (result == null || (!"passed".equals(result) && !"rejected".equals(result))) {
            throw new BizException(400, "请选择招标通过或招标未通过");
        }
        String comment = blankToNull(body.get("comment"));
        if (comment == null) {
            throw new BizException(400, "请填写招标建议");
        }
        PermissionContext ctx = PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
        jdbc.update("""
                UPDATE purchase_plan_item
                SET bidding_review_result = ?,
                    bidding_review_comment = ?,
                    bidding_reviewed_at = NOW(),
                    bidding_reviewed_by = ?::uuid,
                    bidding_reviewed_by_name = ?,
                    updated_at = NOW()
                WHERE id = ?::uuid
                """, result, comment, actorId, actorName, itemId);
        var out = jdbc.queryForList("""
                SELECT id, order_no, bidding_no, bidding_review_result, bidding_review_comment,
                       bidding_reviewed_at, bidding_reviewed_by, bidding_reviewed_by_name
                FROM purchase_plan_item WHERE id = ?::uuid
                """, itemId);
        Map<String, Object> row = out.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(out.get(0));
        row.put("bidding_status", "passed".equals(String.valueOf(row.get("bidding_review_result")))
                ? "已招标" : "未招标");
        return Result.ok(row);
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

    private void assertNotBiddingReviewed(UUID itemId) {
        var rows = jdbc.queryForList("""
                SELECT bidding_reviewed_at FROM purchase_plan_item WHERE id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", null), itemId);
        if (rows.isEmpty()) throw new BizException(404, "明细不存在");
        if (rows.get(0).get("bidding_reviewed_at") != null) {
            throw new BizException(400, "该明细已招标审核，不能重复审核或修改供应商");
        }
    }

    private void assertReadyForBiddingReview(UUID itemId) {
        var suppliers = jdbc.queryForList("""
                SELECT id, is_winner FROM purchase_plan_item_bid_supplier
                WHERE plan_item_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item_bid_supplier", null), itemId);
        if (suppliers.isEmpty()) {
            throw new BizException(400, "请先维护招标供应商后再招标审核");
        }
        long winners = suppliers.stream().filter(r -> truthy(r.get("is_winner"))).count();
        if (winners == 0) {
            throw new BizException(400, "请先选定中标供应商后再招标审核");
        }
        if (winners > 1) {
            throw new BizException(400, "同一明细只能选择一个中标供应商");
        }
    }

    private static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        String s = v.toString().trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    private static UUID parseUuid(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "供应商ID格式不正确");
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
