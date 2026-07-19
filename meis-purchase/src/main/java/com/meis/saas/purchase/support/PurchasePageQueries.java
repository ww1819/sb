package com.meis.saas.purchase.support;

import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.purchase.PurchasePlanItemBiddingNos;
import com.meis.saas.common.purchase.PurchasePlanItemOrderNos;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PurchasePageQueries {
    private PurchasePageQueries() {}

    public static PageResult<Map<String, Object>> planPage(JdbcTemplate jdbc, PageQuery q,
            String approvalStatus, Integer planYear, String planType) {
        StringBuilder where = new StringBuilder(" WHERE p.is_active IS NOT FALSE ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p"));
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, q.getKeyword(), "p.plan_code", "d.dept_name", "u.real_name");
        if (approvalStatus != null && !approvalStatus.isBlank()) {
            where.append(" AND p.approval_status = ? ");
            args.add(approvalStatus);
        }
        if (planYear != null) {
            where.append(" AND p.plan_year = ? ");
            args.add(planYear);
        }
        if (planType != null && !planType.isBlank()) {
            where.append(" AND p.plan_type = ? ");
            args.add(planType);
        }
        PurchaseDataScope.applyPlanFilter(where, args, jdbc);
        String from = """
            FROM purchase_plan p
            LEFT JOIN campus c ON c.id = p.campus_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "campus", "c") + """
            LEFT JOIN department d ON d.id = p.dept_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d") + """
            LEFT JOIN sys_user u ON u.id = p.applicant_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u") + """
            LEFT JOIN sys_user au ON au.id = p.approved_by
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "au");
        PageResult<Map<String, Object>> result = page(jdbc, from, where, args, q, "p.created_at DESC NULLS LAST",
                "p.*, c.campus_name, d.dept_name, u.real_name AS applicant_name, au.real_name AS approved_by_name");
        for (Map<String, Object> row : result.getRecords()) {
            fillDateFallback(row);
        }
        return result;
    }

    /** 申报日期为空时回退创建日期，避免列表空白 */
    static void fillDateFallback(Map<String, Object> row) {
        if (row.get("fill_date") != null) return;
        Object created = row.get("created_at");
        if (created == null) return;
        if (created instanceof java.sql.Timestamp ts) {
            row.put("fill_date", ts.toLocalDateTime().toLocalDate());
        } else if (created instanceof java.time.OffsetDateTime odt) {
            row.put("fill_date", odt.toLocalDate());
        } else if (created instanceof java.time.LocalDateTime ldt) {
            row.put("fill_date", ldt.toLocalDate());
        } else if (created instanceof java.time.Instant instant) {
            row.put("fill_date", java.time.LocalDate.ofInstant(instant, java.time.ZoneId.systemDefault()));
        }
    }

    public static PageResult<Map<String, Object>> projectPage(JdbcTemplate jdbc, PageQuery q,
            String status, String planId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj"));
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, q.getKeyword(), "pj.project_code", "pj.project_name", "pl.plan_code", "s.supplier_name");
        if (status != null && !status.isBlank()) {
            where.append(" AND pj.status = ? ");
            args.add(status);
        }
        if (planId != null && !planId.isBlank()) {
            where.append(" AND pj.plan_id = ?::uuid ");
            args.add(planId);
        }
        PurchaseDataScope.applyProjectFilter(where, args, jdbc);
        String from = """
            FROM purchase_project pj
            LEFT JOIN purchase_plan pl ON pl.id = pj.plan_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "pl") + """
            LEFT JOIN supplier s ON s.id = pj.supplier_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s");
        return page(jdbc, from, where, args, q, "pj.created_at DESC NULLS LAST",
                "pj.*, pl.plan_code, s.supplier_name");
    }

    /**
     * 设备采购计划表：仅展示审批已通过计划的明细行（PUR-UI-08）。
     */
    public static PageResult<Map<String, Object>> approvedPlanItemPage(JdbcTemplate jdbc, PageQuery q) {
        // 历史已通过明细补齐订单号（PUR-UI-09）
        List<Integer> missing = jdbc.query("""
                SELECT 1
                FROM purchase_plan_item i
                JOIN purchase_plan p ON p.id = i.plan_id
                WHERE p.approval_status = 'approved'
                  AND (i.order_no IS NULL OR TRIM(i.order_no) = '')
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", "i") + """
                LIMIT 1
                """, (rs, rowNum) -> 1);
        if (!missing.isEmpty()) {
            PurchasePlanItemOrderNos.allocateMissingApproved(jdbc);
        }
        StringBuilder where = new StringBuilder(" WHERE p.approval_status = 'approved' ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p"));
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", "i"));
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, q.getKeyword(),
                "i.order_no", "p.plan_code", "d.dept_name", "i.device_name", "i.specification", "i.brand_intent");
        PurchaseDataScope.applyPlanFilter(where, args, jdbc);
        String from = """
            FROM purchase_plan_item i
            JOIN purchase_plan p ON p.id = i.plan_id
            LEFT JOIN department d ON d.id = p.dept_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d") + """
            LEFT JOIN LATERAL (
                SELECT DISTINCT ON (i0.business_id)
                       i0.id, i0.created_at
                FROM sys_approval_instance i0
                WHERE i0.business_type = 'purchase_plan'
                  AND i0.business_id = p.id
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_instance", "i0") + """
                ORDER BY i0.business_id, i0.created_at DESC NULLS LAST
            ) inst ON TRUE
            LEFT JOIN LATERAL (
                SELECT r.comment
                FROM sys_approval_record r
                WHERE r.instance_id = inst.id
                ORDER BY r.acted_at DESC NULLS LAST
                LIMIT 1
            ) last_rec ON TRUE
            """;
        PageResult<Map<String, Object>> result = page(jdbc, from, where, args, q,
                "p.approved_at DESC NULLS LAST, p.created_at DESC NULLS LAST, i.created_at ASC NULLS LAST",
                """
                i.id, i.plan_id, i.device_name, i.specification, i.estimated_price, i.quantity, i.unit, i.total_price,
                i.fund_source, i.justification AS purchase_purpose, i.brand_intent,
                i.order_no, i.order_review_comment, i.order_reviewed_at, i.order_reviewed_by_name,
                i.bargain_meeting_location, i.bargain_meeting_time, i.bargain_participant_depts,
                i.bargain_dept_opinion, i.bargain_meeting_content, i.bargain_meeting_conclusion,
                i.bargain_record_url, i.bargain_review_result, i.bargain_review_comment,
                i.bargain_reviewed_at, i.bargain_reviewed_by_name,
                i.bargain_at, i.bargain_by_name,
                p.plan_code, p.plan_year, p.fill_date, p.created_at, p.remark AS plan_remark,
                d.dept_name,
                inst.created_at AS submitted_at,
                last_rec.comment AS approval_comment
                """);
        for (Map<String, Object> row : result.getRecords()) {
            fillDateFallback(row);
            if (row.get("total_price") == null) {
                Object qty = row.get("quantity");
                Object price = row.get("estimated_price");
                if (qty instanceof Number qn && price instanceof Number pn) {
                    row.put("total_price", qn.doubleValue() * pn.doubleValue());
                }
            }
        }
        return result;
    }

    /**
     * 招标管理：仅议价审核通过（passed）的已审批计划明细（PUR-UI-14/20/21/22）。
     *
     * @param biddingReviewResult 可选；传入 {@code passed} 时仅返回招标审核通过明细（引用招标计划）
     */
    public static PageResult<Map<String, Object>> bargainPassedPlanItemPage(JdbcTemplate jdbc, PageQuery q) {
        return bargainPassedPlanItemPage(jdbc, q, null);
    }

    public static PageResult<Map<String, Object>> bargainPassedPlanItemPage(JdbcTemplate jdbc, PageQuery q,
            String biddingReviewResult) {
        // 历史议价通过明细补齐招标单号（PUR-UI-20）
        List<Integer> missing = jdbc.query("""
                SELECT 1
                FROM purchase_plan_item i
                JOIN purchase_plan p ON p.id = i.plan_id
                WHERE p.approval_status = 'approved'
                  AND i.bargain_review_result = 'passed'
                  AND (i.bidding_no IS NULL OR TRIM(i.bidding_no) = '')
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", "i") + """
                LIMIT 1
                """, (rs, rowNum) -> 1);
        if (!missing.isEmpty()) {
            PurchasePlanItemBiddingNos.allocateMissingPassed(jdbc);
        }
        StringBuilder where = new StringBuilder(" WHERE p.approval_status = 'approved' ");
        where.append(" AND i.bargain_review_result = 'passed' ");
        List<Object> args = new ArrayList<>();
        if (biddingReviewResult != null && !biddingReviewResult.isBlank()) {
            where.append(" AND i.bidding_review_result = ? ");
            args.add(biddingReviewResult.trim());
        }
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p"));
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", "i"));
        appendKeyword(where, args, q.getKeyword(),
                "i.bidding_no", "i.order_no", "p.plan_code", "i.device_name", "d.dept_name", "i.specification");
        PurchaseDataScope.applyPlanFilter(where, args, jdbc);
        String from = """
            FROM purchase_plan_item i
            JOIN purchase_plan p ON p.id = i.plan_id
            LEFT JOIN department d ON d.id = p.dept_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d");
        PageResult<Map<String, Object>> result = page(jdbc, from, where, args, q,
                "i.bargain_reviewed_at DESC NULLS LAST, p.approved_at DESC NULLS LAST, i.created_at ASC NULLS LAST",
                """
                i.id, i.plan_id, i.device_name, i.specification, i.estimated_price, i.quantity, i.total_price,
                i.order_no, i.bidding_no, i.bargain_review_result, i.bargain_reviewed_at,
                i.bidding_review_result, i.bidding_review_comment, i.bidding_reviewed_at, i.bidding_reviewed_by_name,
                p.plan_code, p.created_at, p.fill_date,
                d.dept_name
                """);
        for (Map<String, Object> row : result.getRecords()) {
            fillDateFallback(row);
            if (row.get("total_price") == null) {
                Object qty = row.get("quantity");
                Object price = row.get("estimated_price");
                if (qty instanceof Number qn && price instanceof Number pn) {
                    row.put("total_price", qn.doubleValue() * pn.doubleValue());
                }
            }
            row.put("bidding_status", "passed".equals(String.valueOf(row.get("bidding_review_result")))
                    ? "已招标" : "未招标");
        }
        return result;
    }

    public static PageResult<Map<String, Object>> contractPage(JdbcTemplate jdbc, PageQuery q,
            String approvalStatus, String acceptanceStatus) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "c"));
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, q.getKeyword(), "c.contract_code", "c.contract_name", "pj.project_name", "s.supplier_name");
        if (approvalStatus != null && !approvalStatus.isBlank()) {
            if ("unapproved".equalsIgnoreCase(approvalStatus.trim())) {
                where.append(" AND (c.approval_status IS NULL OR c.approval_status <> 'approved') ");
            } else {
                where.append(" AND c.approval_status = ? ");
                args.add(approvalStatus.trim());
            }
        }
        if (acceptanceStatus != null && !acceptanceStatus.isBlank()) {
            where.append(" AND c.acceptance_status = ? ");
            args.add(acceptanceStatus);
        }
        String from = """
            FROM purchase_contract c
            LEFT JOIN purchase_project pj ON pj.id = c.project_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj") + """
            LEFT JOIN supplier s ON s.id = c.supplier_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s") + """
            LEFT JOIN purchase_acceptance pa ON pa.contract_id = c.id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", "pa");
        PageResult<Map<String, Object>> result = page(jdbc, from, where, args, q, "c.created_at DESC NULLS LAST",
                "c.*, pj.project_name, s.supplier_name, pa.acceptance_no, pa.acceptance_status AS acc_status, pa.entry_id");
        for (Map<String, Object> row : result.getRecords()) {
            Object st = row.get("approval_status");
            if (st == null || st.toString().isBlank()) {
                row.put("approval_status", "draft");
            }
        }
        return result;
    }

    /** 已审批合同设备明细（验收引入合同） */
    public static PageResult<Map<String, Object>> contractRefItemPage(JdbcTemplate jdbc, PageQuery q) {
        StringBuilder where = new StringBuilder(" WHERE c.approval_status = 'approved' ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "c"));
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract_item", "ci"));
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, q.getKeyword(),
                "c.contract_code", "c.contract_name", "ci.device_name", "ci.specification");
        String from = """
            FROM purchase_contract_item ci
            JOIN purchase_contract c ON c.id = ci.contract_id
            """;
        PageResult<Map<String, Object>> result = page(jdbc, from, where, args, q,
                "c.created_at DESC NULLS LAST, ci.sort_order ASC NULLS LAST, ci.created_at ASC NULLS LAST",
                """
                ci.id, ci.contract_id, c.contract_code, c.contract_name, c.supplier_id,
                ci.device_name, ci.specification, ci.brand, ci.quantity, ci.unit_price, ci.amount,
                ci.manufacturer_id, ci.manufacturer_name, ci.sort_order
                """);
        for (Map<String, Object> row : result.getRecords()) {
            if (row.get("amount") == null) {
                Double qn = toDouble(row.get("quantity"));
                Double pn = toDouble(row.get("unit_price"));
                if (qn != null && pn != null) {
                    row.put("amount", Math.round(qn * pn * 100.0) / 100.0);
                }
            }
        }
        return result;
    }

    private static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static PageResult<Map<String, Object>> acceptancePage(JdbcTemplate jdbc, PageQuery q,
            String acceptanceStatus, String approvalStatus) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", "a"));
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, q.getKeyword(), "a.acceptance_no", "c.contract_code", "s.supplier_name");
        if (acceptanceStatus != null && !acceptanceStatus.isBlank()) {
            where.append(" AND a.acceptance_status = ? ");
            args.add(acceptanceStatus);
        }
        if (approvalStatus != null && !approvalStatus.isBlank()) {
            where.append(" AND a.approval_status = ? ");
            args.add(approvalStatus);
        }
        String from = """
            FROM purchase_acceptance a
            LEFT JOIN purchase_contract c ON c.id = a.contract_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "c") + """
            LEFT JOIN supplier s ON s.id = a.supplier_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s") + """
            LEFT JOIN device_entry de ON de.id = a.entry_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", "de");
        return page(jdbc, from, where, args, q, "a.created_at DESC NULLS LAST",
                "a.*, c.contract_code, c.contract_name, s.supplier_name, de.entry_no, de.status AS entry_status");
    }

    private static void appendKeyword(StringBuilder where, List<Object> args, String keyword, String... cols) {
        if (keyword == null || keyword.isBlank()) return;
        where.append(" AND (");
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) where.append(" OR ");
            where.append(cols[i]).append(" ILIKE ? ");
            args.add("%" + keyword + "%");
        }
        where.append(") ");
    }

    private static PageResult<Map<String, Object>> page(JdbcTemplate jdbc, String from, StringBuilder where,
            List<Object> args, PageQuery q, String orderBy, String selectCols) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.limit());
        pageArgs.add(q.offset());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + selectCols + " " + from + where + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return PageResult.of(rows, total != null ? total : 0, q.getPage(), q.getSize());
    }
}
