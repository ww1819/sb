package com.meis.saas.purchase.controller;

import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
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
        StringBuilder where = new StringBuilder(" WHERE i.business_type IN (");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < PURCHASE_TYPES.size(); i++) {
            if (i > 0) where.append(",");
            where.append("?");
            args.add(PURCHASE_TYPES.get(i));
        }
        where.append(") ");
        if (status != null && !status.isBlank()) {
            where.append(" AND i.status = ? ");
            args.add(status);
        } else {
            where.append(" AND i.status = 'pending' ");
        }
        if (businessType != null && !businessType.isBlank()) {
            where.append(" AND i.business_type = ? ");
            args.add(businessType);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (i.business_no ILIKE ? OR i.title ILIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_approval_instance i" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
                SELECT i.*, u.real_name AS applicant_name, n.node_name AS current_node_name, n.approver_role
                FROM sys_approval_instance i
                LEFT JOIN sys_user u ON u.id = i.applicant_id
                LEFT JOIN sys_approval_node n ON n.flow_id = i.flow_id AND n.node_order = i.current_node_order
                """ + where + " ORDER BY i.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String type : PURCHASE_TYPES) {
            long pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_approval_instance WHERE business_type=? AND status='pending'",
                    Long.class, type);
            result.put(type, pending);
        }
        long totalPending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_approval_instance
                WHERE business_type IN ('purchase_plan','purchase_project','purchase_contract','purchase_acceptance','contract_payment')
                  AND status='pending'
                """, Long.class);
        result.put("totalPending", totalPending);
        return Result.ok(result);
    }
}
