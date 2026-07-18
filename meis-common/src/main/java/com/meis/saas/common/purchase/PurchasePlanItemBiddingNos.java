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
 * 采购计划明细招标单号：ZB-yyyyMMdd + 4 位日流水（PUR-UI-20）。
 */
public final class PurchasePlanItemBiddingNos {
    private PurchasePlanItemBiddingNos() {}

    /** 议价审核通过时为指定明细分配招标单号（已有则跳过）。 */
    public static void assignIfAbsent(JdbcTemplate jdbc, UUID itemId) {
        if (jdbc == null || itemId == null) return;
        assignBiddingNo(jdbc, itemId);
    }

    /**
     * 补齐议价已通过但缺招标单号的明细（历史数据 / 列表兜底）。
     */
    public static int allocateMissingPassed(JdbcTemplate jdbc) {
        if (jdbc == null) return 0;
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT i.id
                FROM purchase_plan_item i
                JOIN purchase_plan p ON p.id = i.plan_id
                WHERE p.approval_status = 'approved'
                  AND i.bargain_review_result = 'passed'
                  AND (i.bidding_no IS NULL OR TRIM(i.bidding_no) = '')
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", "i") + """
                ORDER BY i.bargain_reviewed_at ASC NULLS LAST, i.created_at ASC NULLS LAST, i.id
                """);
        int n = 0;
        for (Map<String, Object> item : items) {
            assignBiddingNo(jdbc, UUID.fromString(item.get("id").toString()));
            n++;
        }
        return n;
    }

    static void assignBiddingNo(JdbcTemplate jdbc, UUID itemId) {
        String biddingNo = nextBiddingNo(jdbc);
        jdbc.update("""
                UPDATE purchase_plan_item
                SET bidding_no = ?, updated_at = NOW()
                WHERE id = ?::uuid
                  AND (bidding_no IS NULL OR TRIM(bidding_no) = '')
                """, biddingNo, itemId);
    }

    public static String nextBiddingNo(JdbcTemplate jdbc) {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "ZB-" + day;
        Integer maxSeq = jdbc.queryForObject("""
                SELECT MAX(CAST(RIGHT(bidding_no, 4) AS INTEGER))
                FROM purchase_plan_item
                WHERE bidding_no LIKE ? AND LENGTH(bidding_no) = ?
                """, Integer.class, prefix + "%", prefix.length() + 4);
        int next = (maxSeq == null ? 0 : maxSeq) + 1;
        if (next > 9999) {
            throw new BizException(400, "当日招标单号已用尽，请明日再试");
        }
        return prefix + String.format("%04d", next);
    }
}
