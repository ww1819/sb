package com.meis.saas.purchase.controller;

import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/purchase/approval")
@RequiredArgsConstructor
public class PurchaseApprovalController {
    private static final List<String> PURCHASE_TYPES = List.of(
            "purchase_plan", "purchase_project", "purchase_contract",
            "purchase_acceptance", "contract_payment");

    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessType) {
        // 同一业务单据可能有多条审批实例（撤回重提等），列表按单据去重只保留最新一条
        StringBuilder instWhere = new StringBuilder(" WHERE i0.business_type IN (");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < PURCHASE_TYPES.size(); i++) {
            if (i > 0) instWhere.append(",");
            instWhere.append("?");
            args.add(PURCHASE_TYPES.get(i));
        }
        instWhere.append(") ");
        instWhere.append(SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_instance", "i0"));
        FilterCsvSupport.appendStrIn(instWhere, args, "i0.status", status);
        FilterCsvSupport.appendStrIn(instWhere, args, "i0.business_type", businessType);

        StringBuilder outerWhere = new StringBuilder(" WHERE 1=1 ");
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            outerWhere.append("""
                     AND (i.business_no ILIKE ? OR i.title ILIKE ?
                      OR p.plan_code ILIKE ? OR d.dept_name ILIKE ? OR c.campus_name ILIKE ?
                      OR u.real_name ILIKE ?)
                    """);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }

        String from = """
                FROM (
                    SELECT DISTINCT ON (i0.business_type, i0.business_id)
                           i0.id, i0.business_type, i0.business_id, i0.business_no, i0.title,
                           i0.status, i0.created_at, i0.applicant_id, i0.flow_id, i0.current_node_order
                    FROM sys_approval_instance i0
                    """ + instWhere + """
                    ORDER BY i0.business_type, i0.business_id, i0.created_at DESC NULLS LAST
                ) i
                LEFT JOIN sys_user u ON u.id = i.applicant_id
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u") + """
                LEFT JOIN purchase_plan p ON i.business_type = 'purchase_plan' AND p.id = i.business_id
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p") + """
                LEFT JOIN campus c ON c.id = p.campus_id
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "campus", "c") + """
                LEFT JOIN department d ON d.id = p.dept_id
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d") + """
                LEFT JOIN sys_user au ON au.id = p.approved_by
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "au") + """
                LEFT JOIN LATERAL (
                    SELECT r.comment
                    FROM sys_approval_record r
                    WHERE r.instance_id = i.id
                    ORDER BY r.acted_at DESC NULLS LAST
                    LIMIT 1
                ) last_rec ON TRUE
                """;

        long total = jdbc.queryForObject("SELECT COUNT(*) " + from + outerWhere, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
                SELECT i.id, i.business_type, i.business_id, i.business_no, i.title, i.status,
                       i.created_at AS submitted_at,
                       i.applicant_id, u.real_name AS applicant_name,
                       COALESCE(p.plan_code, i.business_no) AS plan_code,
                       p.campus_id, c.campus_name,
                       p.dept_id, d.dept_name,
                       p.total_budget, p.plan_year, p.plan_type,
                       p.approved_by, au.real_name AS approved_by_name, p.approved_at,
                       COALESCE(p.approval_status, i.status) AS approval_status,
                       last_rec.comment AS approval_comment,
                       p.benefit_analysis_url, p.dept_argument_url
                """ + from + outerWhere + " ORDER BY i.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String type : PURCHASE_TYPES) {
            long pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_approval_instance WHERE business_type=? AND status='pending'"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_instance", null),
                    Long.class, type);
            result.put(type, pending);
        }
        long totalPending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_approval_instance
                WHERE business_type IN ('purchase_plan','purchase_project','purchase_contract','purchase_acceptance','contract_payment')
                  AND status='pending'
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_instance", null), Long.class);
        result.put("totalPending", totalPending);
        return Result.ok(result);
    }
}
