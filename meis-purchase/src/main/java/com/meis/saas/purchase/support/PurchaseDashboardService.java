package com.meis.saas.purchase.support;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

public final class PurchaseDashboardService {
    private PurchaseDashboardService() {}

    public static Map<String, Object> buildStats(JdbcTemplate jdbc) {
        Map<String, Object> data = new LinkedHashMap<>();
        long planTotal = count(jdbc, "SELECT COUNT(*) FROM purchase_plan WHERE is_active IS NOT FALSE"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null));
        long planApproved = count(jdbc, "SELECT COUNT(*) FROM purchase_plan WHERE approval_status = 'approved'"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null));
        long contractActive = count(jdbc, "SELECT COUNT(*) FROM purchase_contract WHERE status = 'active'"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null));

        data.put("planTotal", planTotal);
        data.put("planPending", count(jdbc, "SELECT COUNT(*) FROM purchase_plan WHERE approval_status = 'pending'"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null)));
        data.put("planApproved", planApproved);
        data.put("projectTotal", count(jdbc, "SELECT COUNT(*) FROM purchase_project WHERE 1=1"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null)));
        data.put("projectBidding", count(jdbc, "SELECT COUNT(*) FROM purchase_project WHERE status = 'bidding'"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null)));
        data.put("projectAwarded", count(jdbc, "SELECT COUNT(*) FROM purchase_project WHERE status = 'awarded'"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null)));
        data.put("contractActive", contractActive);
        data.put("contractCompleted", count(jdbc, "SELECT COUNT(*) FROM purchase_contract WHERE status = 'completed'"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null)));
        data.put("acceptancePending", count(jdbc, "SELECT COUNT(*) FROM purchase_acceptance WHERE acceptance_status = 'pending'"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", null)));
        data.put("entryDraft", count(jdbc, "SELECT COUNT(*) FROM device_entry WHERE status = 'draft'"
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", null)));
        data.put("overBudgetCount", count(jdbc, """
            SELECT COUNT(*) FROM purchase_project pj
            JOIN purchase_plan pl ON pl.id = pj.plan_id
            WHERE pl.total_budget > 0 AND pj.total_amount > pl.total_budget
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "pl")));
        data.put("overdueContractCount", count(jdbc, """
            SELECT COUNT(*) FROM purchase_contract
            WHERE delivery_deadline < CURRENT_DATE AND acceptance_status != 'passed'
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null)));
        data.put("paymentPendingAmount", sum(jdbc, """
            SELECT COALESCE(SUM(payment_amount), 0) FROM contract_payment WHERE status = 'pending'
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "contract_payment", null)));
        data.put("paymentProgressAvg", avg(jdbc, """
            SELECT AVG(payment_progress) FROM purchase_contract WHERE contract_amount > 0
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null)));

        double totalBudget = sum(jdbc, "SELECT COALESCE(SUM(total_budget),0) FROM purchase_plan WHERE is_active IS NOT FALSE"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null));
        double totalContract = sum(jdbc, """
            SELECT COALESCE(SUM(pc.contract_amount),0) FROM purchase_contract pc
            JOIN purchase_project pj ON pj.id = pc.project_id
            WHERE 1=1
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj"));
        data.put("totalBudget", totalBudget);
        data.put("totalContractAmount", totalContract);
        data.put("executionRate", totalBudget > 0 ? Math.round(totalContract / totalBudget * 1000) / 10.0 : 0);
        data.put("planConversionRate", planTotal > 0 ? Math.round(planApproved * 1000.0 / planTotal) / 10.0 : 0);

        data.put("funnel", List.of(
                Map.of("name", "采购计划", "value", planTotal),
                Map.of("name", "已批计划", "value", planApproved),
                Map.of("name", "采购项目", "value", count(jdbc, "SELECT COUNT(*) FROM purchase_project WHERE 1=1"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null))),
                Map.of("name", "生效合同", "value", contractActive),
                Map.of("name", "验收通过", "value", count(jdbc, "SELECT COUNT(*) FROM purchase_acceptance WHERE acceptance_status = 'passed'"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", null))),
                Map.of("name", "已入库", "value", count(jdbc, "SELECT COUNT(*) FROM device_entry WHERE status != 'draft'"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", null)))
        ));

        data.put("planStatusChart", jdbc.queryForList("""
            SELECT approval_status AS name, COUNT(*) AS value
            FROM purchase_plan WHERE is_active IS NOT FALSE
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null) + """
            GROUP BY approval_status
            """));
        data.put("projectStatusChart", jdbc.queryForList("""
            SELECT status AS name, COUNT(*) AS value FROM purchase_project WHERE 1=1
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null) + """
            GROUP BY status
            """));
        data.put("budgetTopPlans", jdbc.queryForList("""
            SELECT p.plan_code AS name, COALESCE(p.total_budget,0) AS budget,
                   COALESCE(SUM(pc.contract_amount),0) AS contracted
            FROM purchase_plan p
            LEFT JOIN purchase_project pj ON pj.plan_id = p.id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj") + """
            LEFT JOIN purchase_contract pc ON pc.project_id = pj.id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc") + """
            WHERE p.is_active IS NOT FALSE
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p") + """
            GROUP BY p.id, p.plan_code, p.total_budget
            ORDER BY p.total_budget DESC NULLS LAST LIMIT 8
            """));

        data.put("pipeline", jdbc.queryForList("""
            SELECT pl.plan_code, pl.business_chain_no, pl.approval_status AS plan_status,
                   pj.project_code, pj.status AS project_status,
                   pc.contract_code, pc.approval_status AS contract_status, pc.payment_progress,
                   pa.acceptance_no, pa.acceptance_status,
                   de.entry_no, de.status AS entry_status
            FROM purchase_plan pl
            LEFT JOIN purchase_project pj ON pj.plan_id = pl.id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj") + """
            LEFT JOIN purchase_contract pc ON pc.project_id = pj.id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc") + """
            LEFT JOIN purchase_acceptance pa ON pa.contract_id = pc.id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", "pa") + """
            LEFT JOIN device_entry de ON de.contract_id = pc.id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", "de") + """
            WHERE pl.is_active IS NOT FALSE
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "pl") + """
            ORDER BY pl.created_at DESC LIMIT 20
            """));

        PurchaseAlertService.scanAndNotify(jdbc);
        data.put("alerts", PurchaseAlertService.listActive(jdbc));
        return data;
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v != null ? v : 0;
    }

    private static double sum(JdbcTemplate jdbc, String sql) {
        Double v = jdbc.queryForObject(sql, Double.class);
        return v != null ? v : 0;
    }

    private static double avg(JdbcTemplate jdbc, String sql) {
        Double v = jdbc.queryForObject(sql, Double.class);
        return v != null ? Math.round(v * 10) / 10.0 : 0;
    }
}
