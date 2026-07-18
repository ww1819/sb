package com.meis.saas.common.purchase;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 采购计划明细订单号：DD-yyyyMMdd + 4 位日流水（PUR-UI-09）。
 */
public final class PurchasePlanItemOrderNos {
    private PurchasePlanItemOrderNos() {}

    /** 为指定计划下尚无订单号的明细分配订单号（审批通过时调用）。 */
    public static int allocateForPlan(JdbcTemplate jdbc, UUID planId) {
        if (jdbc == null || planId == null) return 0;
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT id FROM purchase_plan_item
                WHERE plan_id = ?::uuid
                  AND (order_no IS NULL OR TRIM(order_no) = '')
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", null) + """
                ORDER BY created_at ASC NULLS LAST, id
                """, planId);
        int n = 0;
        for (Map<String, Object> item : items) {
            assignOrderNo(jdbc, UUID.fromString(item.get("id").toString()));
            n++;
        }
        return n;
    }

    /**
     * 补齐已审批通过但缺订单号的明细（历史数据 / 列表兜底）。
     */
    public static int allocateMissingApproved(JdbcTemplate jdbc) {
        if (jdbc == null) return 0;
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT i.id
                FROM purchase_plan_item i
                JOIN purchase_plan p ON p.id = i.plan_id
                WHERE p.approval_status = 'approved'
                  AND (i.order_no IS NULL OR TRIM(i.order_no) = '')
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", "i") + """
                ORDER BY p.approved_at ASC NULLS LAST, i.created_at ASC NULLS LAST, i.id
                """);
        int n = 0;
        for (Map<String, Object> item : items) {
            assignOrderNo(jdbc, UUID.fromString(item.get("id").toString()));
            n++;
        }
        return n;
    }

    static void assignOrderNo(JdbcTemplate jdbc, UUID itemId) {
        String orderNo = nextOrderNo(jdbc);
        int updated = jdbc.update("""
                UPDATE purchase_plan_item
                SET order_no = ?, updated_at = NOW()
                WHERE id = ?::uuid
                  AND (order_no IS NULL OR TRIM(order_no) = '')
                """, orderNo, itemId);
        if (updated == 0) return;
    }

    public static String nextOrderNo(JdbcTemplate jdbc) {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "DD-" + day;
        Integer maxSeq = jdbc.queryForObject("""
                SELECT MAX(CAST(RIGHT(order_no, 4) AS INTEGER))
                FROM purchase_plan_item
                WHERE order_no LIKE ? AND LENGTH(order_no) = ?
                """, Integer.class, prefix + "%", prefix.length() + 4);
        int next = (maxSeq == null ? 0 : maxSeq) + 1;
        if (next > 9999) {
            throw new BizException(400, "当日订单号已用尽，请明日再试");
        }
        return prefix + String.format("%04d", next);
    }
}
