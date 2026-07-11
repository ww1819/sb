package com.meis.saas.purchase.support;

import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PurchasePageQueries {
    private PurchasePageQueries() {}

    public static PageResult<Map<String, Object>> planPage(JdbcTemplate jdbc, PageQuery q,
            String approvalStatus, Integer planYear) {
        StringBuilder where = new StringBuilder(" WHERE p.is_active IS NOT FALSE ");
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
        PurchaseDataScope.applyPlanFilter(where, args);
        String from = """
            FROM purchase_plan p
            LEFT JOIN department d ON d.id = p.dept_id
            LEFT JOIN sys_user u ON u.id = p.applicant_id
            """;
        return page(jdbc, from, where, args, q, "p.created_at DESC NULLS LAST",
                "p.*, d.dept_name, u.real_name AS applicant_name");
    }

    public static PageResult<Map<String, Object>> projectPage(JdbcTemplate jdbc, PageQuery q,
            String status, String planId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
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
        PurchaseDataScope.applyProjectFilter(where, args);
        String from = """
            FROM purchase_project pj
            LEFT JOIN purchase_plan pl ON pl.id = pj.plan_id
            LEFT JOIN supplier s ON s.id = pj.supplier_id
            """;
        return page(jdbc, from, where, args, q, "pj.created_at DESC NULLS LAST",
                "pj.*, pl.plan_code, s.supplier_name");
    }

    public static PageResult<Map<String, Object>> contractPage(JdbcTemplate jdbc, PageQuery q,
            String approvalStatus, String acceptanceStatus) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, q.getKeyword(), "c.contract_code", "c.contract_name", "pj.project_name", "s.supplier_name");
        if (approvalStatus != null && !approvalStatus.isBlank()) {
            where.append(" AND c.approval_status = ? ");
            args.add(approvalStatus);
        }
        if (acceptanceStatus != null && !acceptanceStatus.isBlank()) {
            where.append(" AND c.acceptance_status = ? ");
            args.add(acceptanceStatus);
        }
        String from = """
            FROM purchase_contract c
            LEFT JOIN purchase_project pj ON pj.id = c.project_id
            LEFT JOIN supplier s ON s.id = c.supplier_id
            LEFT JOIN purchase_acceptance pa ON pa.contract_id = c.id
            """;
        return page(jdbc, from, where, args, q, "c.created_at DESC NULLS LAST",
                "c.*, pj.project_name, s.supplier_name, pa.acceptance_no, pa.acceptance_status AS acc_status, pa.entry_id");
    }

    public static PageResult<Map<String, Object>> acceptancePage(JdbcTemplate jdbc, PageQuery q,
            String acceptanceStatus) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, q.getKeyword(), "a.acceptance_no", "c.contract_code", "s.supplier_name");
        if (acceptanceStatus != null && !acceptanceStatus.isBlank()) {
            where.append(" AND a.acceptance_status = ? ");
            args.add(acceptanceStatus);
        }
        String from = """
            FROM purchase_acceptance a
            LEFT JOIN purchase_contract c ON c.id = a.contract_id
            LEFT JOIN supplier s ON s.id = a.supplier_id
            LEFT JOIN device_entry de ON de.id = a.entry_id
            """;
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
