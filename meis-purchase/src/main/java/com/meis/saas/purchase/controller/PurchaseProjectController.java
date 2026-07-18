package com.meis.saas.purchase.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.rbac.PermissionContext;
import com.meis.saas.common.rbac.PermissionInterceptor;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import com.meis.saas.purchase.support.PurchaseChainService;
import com.meis.saas.purchase.support.PurchasePageQueries;
import com.meis.saas.purchase.support.PurchaseValidators;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/purchase/project")
@RequiredArgsConstructor
public class PurchaseProjectController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "draft", Set.of("bidding"), "bidding", Set.of("awarded"), "awarded", Set.of("closed"));

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plan_id) {
        // PUR-UI-08：列表改为已审批计划明细；原项目分页仍可用 /page/legacy
        return Result.ok(PurchasePageQueries.approvedPlanItemPage(jdbc, query));
    }

    @GetMapping("/page/legacy")
    public Result<PageResult<Map<String, Object>>> pageLegacy(PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plan_id) {
        return Result.ok(PurchasePageQueries.projectPage(jdbc, query, status, plan_id));
    }

    /** PUR-UI-09：订单审核意见 */
    @PostMapping("/approved-items/{itemId}/order-review")
    @Transactional
    @OperationLog(module = "purchase", description = "订单审核意见")
    public Result<Map<String, Object>> orderReview(@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
        Object commentObj = body.get("comment");
        if (commentObj == null || commentObj.toString().isBlank()) {
            throw new BizException(400, "请填写订单审核意见");
        }
        String comment = commentObj.toString().trim();
        var rows = jdbc.queryForList("""
                SELECT i.id, i.order_no, p.approval_status
                FROM purchase_plan_item i
                JOIN purchase_plan p ON p.id = i.plan_id
                WHERE i.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", "i")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "p"), itemId);
        if (rows.isEmpty()) throw new BizException(404, "明细不存在");
        if (!"approved".equals(String.valueOf(rows.get(0).get("approval_status")))) {
            throw new BizException(400, "仅已审批通过的明细可填写订单审核意见");
        }
        PermissionContext ctx = PermissionInterceptor.CTX.get();
        UUID reviewerId = null;
        String reviewerName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            reviewerId = UUID.fromString(ctx.getUserId());
            reviewerName = SoftDeleteSupport.resolveUserDisplayName(jdbc, reviewerId);
        }
        jdbc.update("""
                UPDATE purchase_plan_item
                SET order_review_comment = ?,
                    order_reviewed_at = NOW(),
                    order_reviewed_by = ?::uuid,
                    order_reviewed_by_name = ?,
                    updated_at = NOW()
                WHERE id = ?::uuid
                """, comment, reviewerId, reviewerName, itemId);
        var out = jdbc.queryForList("""
                SELECT id, order_no, order_review_comment, order_reviewed_at, order_reviewed_by, order_reviewed_by_name
                FROM purchase_plan_item WHERE id = ?::uuid
                """, itemId);
        return Result.ok(out.isEmpty() ? Map.of() : out.get(0));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM purchase_project WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "purchase", description = "保存采购项目")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        validatePlanApproved(body.get("plan_id"));
        PurchaseValidators.validateProjectAmount(jdbc, body.get("plan_id"), body.get("total_amount"));
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM purchase_project WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null), id).isEmpty();
        String chainNo = PurchaseChainService.resolvePlanChainNo(jdbc, body.get("plan_id"));
        if (exists) {
            PurchaseValidators.checkVersion(jdbc, "purchase_project", id, body.get("version"));
            jdbc.update("""
                UPDATE purchase_project SET project_name=?, plan_id=?::uuid, purchase_method=?, supplier_id=?::uuid,
                total_amount=?, budget_amount=?, bid_open_date=?, award_date=?, bid_sections=?, bid_evaluation=?,
                argument_report_url=?, bid_agency=?, notice_date=?, control_price=?,
                status=?, version=COALESCE(version,1)+1, updated_at=NOW()
                WHERE id=?::uuid
                """,
                    body.get("project_name"), body.get("plan_id"), body.get("purchase_method"), body.get("supplier_id"),
                    body.get("total_amount"), body.get("budget_amount"), body.get("bid_open_date"), body.get("award_date"),
                    body.get("bid_sections"), body.get("bid_evaluation"), body.get("argument_report_url"),
                    body.get("bid_agency"), body.get("notice_date"), body.get("control_price"),
                    body.getOrDefault("status", "draft"), id);
        } else {
            jdbc.update("""
                INSERT INTO purchase_project (id, project_code, project_name, plan_id, purchase_method, supplier_id,
                total_amount, budget_amount, argument_report_url, bid_agency, notice_date, control_price,
                status, approval_status, business_chain_no)
                VALUES (?::uuid,?,?,?::uuid,?,?::uuid,?,?,?,?,?,?,?,?,?)
                """,
                    id, body.getOrDefault("project_code", "PJ" + System.currentTimeMillis()),
                    body.get("project_name"), body.get("plan_id"), body.get("purchase_method"),
                    body.get("supplier_id"), body.get("total_amount"), body.get("budget_amount"),
                    body.get("argument_report_url"), body.get("bid_agency"), body.get("notice_date"),
                    body.get("control_price"), "draft", "draft", chainNo);
        }
        return get(id);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "purchase", description = "提交采购项目审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> project = get(id).getData();
        approvalService.submit("purchase_project", id,
                project.get("project_code").toString(),
                "采购项目 " + project.get("project_code"),
                UUID.fromString(body.get("applicantId").toString()),
                project.get("total_amount") != null ? ((Number) project.get("total_amount")).doubleValue() : 0);
        return get(id);
    }

    @PostMapping("/{id}/transition")
    @OperationLog(module = "purchase", description = "采购项目状态流转")
    public Result<Map<String, Object>> transition(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        var rows = jdbc.queryForList(
                "SELECT status, approval_status FROM purchase_project WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        String cur = rows.get(0).get("status").toString();
        String target = body.get("status");
        if (!TRANSITIONS.getOrDefault(cur, Set.of()).contains(target)) {
            throw new BizException(400, "不允许从「" + cur + "」流转到「" + target + "」");
        }
        if ("bidding".equals(target) && !"approved".equals(String.valueOf(rows.get(0).get("approval_status")))) {
            throw new BizException(400, "请先完成项目审批再启动招标");
        }
        jdbc.update("UPDATE purchase_project SET status = ?, updated_at = NOW() WHERE id = ?::uuid", target, id);
        return get(id);
    }

    @PostMapping("/{id}/create-contract")
    @Transactional
    @OperationLog(module = "purchase", description = "定标项目生成合同草稿")
    public Result<Map<String, Object>> createContract(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM purchase_project WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> project = rows.get(0);
        if (!"awarded".equals(String.valueOf(project.get("status")))) {
            throw new BizException(400, "仅已定标项目可生成合同");
        }
        var existing = jdbc.queryForList(
                "SELECT id FROM purchase_contract WHERE project_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null) + " LIMIT 1", id);
        if (!existing.isEmpty()) throw new BizException(400, "该项目已有关联合同");
        UUID contractId = UUID.randomUUID();
        String code = "CT" + System.currentTimeMillis();
        Object chainNo = project.get("business_chain_no");
        jdbc.update("""
            INSERT INTO purchase_contract (id, contract_code, contract_name, project_id, supplier_id,
            contract_amount, approval_status, status, acceptance_status, business_chain_no)
            VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?,?,?,?)
            """,
                contractId, code, project.get("project_name") + " 采购合同",
                id, project.get("supplier_id"), project.get("total_amount"), "draft", "active", "pending", chainNo);
        return Result.ok(Map.of("id", contractId, "contract_code", code));
    }

    private void validatePlanApproved(Object planId) {
        if (planId == null) return;
        var plan = jdbc.queryForList(
                "SELECT approval_status FROM purchase_plan WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null), planId);
        if (plan.isEmpty()) throw new BizException(400, "关联采购计划不存在");
        if (!"approved".equals(String.valueOf(plan.get(0).get("approval_status")))) {
            throw new BizException(400, "仅审批通过的采购计划可创建项目");
        }
    }
}
