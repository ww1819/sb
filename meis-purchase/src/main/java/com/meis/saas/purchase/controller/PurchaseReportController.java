package com.meis.saas.purchase.controller;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/purchase/report")
@RequiredArgsConstructor
public class PurchaseReportController {
    private final JdbcTemplate jdbc;

    @GetMapping("/budget")
    public Result<Map<String, Object>> budget(@RequestParam(required = false) Integer planYear) {
        int year = planYear != null ? planYear : java.time.Year.now().getValue();
        var rows = jdbc.queryForList("""
            SELECT p.plan_code, p.plan_year, d.dept_name, p.total_budget, p.approval_status,
                   COALESCE(SUM(pj.total_amount), 0) AS project_amount,
                   COALESCE(SUM(pc.contract_amount), 0) AS contract_amount,
                   COALESCE(SUM(pc.paid_amount), 0) AS paid_amount,
                   CASE WHEN p.total_budget > 0
                        THEN ROUND(COALESCE(SUM(pc.contract_amount), 0) / p.total_budget * 100, 1)
                        ELSE 0 END AS execution_rate
            FROM purchase_plan p
            LEFT JOIN department d ON d.id = p.dept_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d") + """
            LEFT JOIN purchase_project pj ON pj.plan_id = p.id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj") + """
            LEFT JOIN purchase_contract pc ON pc.project_id = pj.id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc") + """
            WHERE p.plan_year = ? AND p.is_active IS NOT FALSE
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p") + """
            GROUP BY p.id, p.plan_code, p.plan_year, d.dept_name, p.total_budget, p.approval_status
            ORDER BY p.plan_code
            """, year);
        double totalBudget = rows.stream().mapToDouble(r -> toDouble(r.get("total_budget"))).sum();
        double totalContract = rows.stream().mapToDouble(r -> toDouble(r.get("contract_amount"))).sum();
        double totalPaid = rows.stream().mapToDouble(r -> toDouble(r.get("paid_amount"))).sum();
        return Result.ok(Map.of(
                "planYear", year,
                "totalBudget", totalBudget,
                "totalContract", totalContract,
                "totalPaid", totalPaid,
                "executionRate", totalBudget > 0 ? Math.round(totalContract / totalBudget * 1000) / 10.0 : 0,
                "rows", rows));
    }

    @GetMapping("/audit")
    public Result<List<Map<String, Object>>> audit(
            @RequestParam(defaultValue = "purchase") String module,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(jdbc.queryForList("""
            SELECT l.*, u.real_name AS operator_name
            FROM sys_operation_log l
            LEFT JOIN sys_user u ON u.id = l.user_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u") + """
            WHERE l.module_name = ?
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "sys_operation_log", "l") + """
            ORDER BY l.created_at DESC LIMIT ?
            """, module, Math.min(limit, 200)));
    }

    private static double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0; }
    }
}
