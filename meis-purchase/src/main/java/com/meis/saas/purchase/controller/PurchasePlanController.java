package com.meis.saas.purchase.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
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
@RequestMapping("/api/purchase/plan")
@RequiredArgsConstructor
public class PurchasePlanController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String approval_status,
            @RequestParam(required = false) Integer plan_year) {
        return Result.ok(PurchasePageQueries.planPage(jdbc, query, approval_status, plan_year));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        List<Map<String, Object>> plans = jdbc.queryForList("SELECT * FROM purchase_plan WHERE id = ?::uuid", id);
        if (plans.isEmpty()) throw new BizException(404, "plan not found");
        Map<String, Object> plan = plans.get(0);
        plan.put("items", jdbc.queryForList("SELECT * FROM purchase_plan_item WHERE plan_id = ?::uuid", id));
        return Result.ok(plan);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "purchase", description = "保存采购计划")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM purchase_plan WHERE id = ?::uuid", id).isEmpty();
        String planCode = body.getOrDefault("plan_code", "PP" + System.currentTimeMillis()).toString();
        if (exists) {
            PurchaseValidators.checkVersion(jdbc, "purchase_plan", id, body.get("version"));
            jdbc.update("""
                UPDATE purchase_plan SET plan_code=?, plan_year=?, dept_id=?::uuid, applicant_id=?::uuid,
                total_budget=?, justification=?, remark=?, plan_type=?, fund_source=?,
                is_large_equipment=?, large_equipment_class=?, benefit_analysis_url=?, dept_argument_url=?,
                approval_status=?, version=COALESCE(version,1)+1, updated_at=NOW() WHERE id=?::uuid
                """,
                    planCode, body.get("plan_year"), body.get("dept_id"), body.get("applicant_id"),
                    body.get("total_budget"), body.get("justification"), body.get("remark"),
                    body.getOrDefault("plan_type", "annual"), body.get("fund_source"),
                    body.getOrDefault("is_large_equipment", false), body.get("large_equipment_class"),
                    body.get("benefit_analysis_url"), body.get("dept_argument_url"),
                    body.getOrDefault("approval_status", "draft"), id);
        } else {
            jdbc.update("""
                INSERT INTO purchase_plan (id, plan_code, plan_year, dept_id, applicant_id, total_budget,
                justification, remark, plan_type, fund_source, approval_status,
                is_large_equipment, large_equipment_class, benefit_analysis_url, dept_argument_url, business_chain_no)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?,?)
                """,
                    id, planCode, body.get("plan_year"), body.get("dept_id"), body.get("applicant_id"),
                    body.get("total_budget"), body.get("justification"), body.get("remark"),
                    body.getOrDefault("plan_type", "annual"), body.get("fund_source"), "draft",
                    body.getOrDefault("is_large_equipment", false), body.get("large_equipment_class"),
                    body.get("benefit_analysis_url"), body.get("dept_argument_url"),
                    PurchaseChainService.newChainNo(planCode));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        double totalBudget = 0;
        for (Map<String, Object> item : items) {
            Number qty = item.get("quantity") instanceof Number n ? n : 1;
            Number price = item.get("estimated_price") instanceof Number n ? n : 0;
            double lineTotal = qty.doubleValue() * price.doubleValue();
            item.put("total_price", lineTotal);
            totalBudget += lineTotal;
        }
        if (body.get("plan_year") != null) {
            int year = ((Number) body.get("plan_year")).intValue();
            var dup = jdbc.queryForList(
                    "SELECT id FROM purchase_plan WHERE plan_year = ? AND id != ?::uuid AND is_active = true LIMIT 1",
                    year, id);
            if (!dup.isEmpty() && !exists) {
                throw new BizException(400, "该年度已有采购计划");
            }
        }
        jdbc.update("DELETE FROM purchase_plan_item WHERE plan_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            jdbc.update("""
                INSERT INTO purchase_plan_item (id, plan_id, device_name, category_id, quantity, estimated_price,
                total_price, specification, priority, justification, use_dept_id, is_imported, registration_no,
                unit, brand_intent, is_metrology, udi_code)
                VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?,?,?,?,?::uuid,?,?,?,?,?,?)
                """,
                    UUID.randomUUID(), id, item.get("device_name"), item.get("category_id"), item.get("quantity"),
                    item.get("estimated_price"), item.get("total_price"), item.get("specification"),
                    item.getOrDefault("priority", 1), item.get("justification"), item.get("use_dept_id"),
                    item.getOrDefault("is_imported", false), item.get("registration_no"),
                    item.get("unit"), item.get("brand_intent"), item.getOrDefault("is_metrology", false),
                    item.get("udi_code"));
        }
        jdbc.update("UPDATE purchase_plan SET total_budget = ?, updated_at = NOW() WHERE id = ?::uuid", totalBudget, id);
        if (!exists) {
            PurchaseChainService.ensurePlanChain(jdbc, id, planCode);
        }
        return get(id);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "purchase", description = "提交采购计划审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> plan = get(id).getData();
        approvalService.submit("purchase_plan", id,
                plan.get("plan_code") != null ? plan.get("plan_code").toString() : id.toString(),
                "采购计划 " + plan.get("plan_code"),
                UUID.fromString(body.get("applicantId").toString()),
                plan.get("total_budget") != null ? ((Number) plan.get("total_budget")).doubleValue() : 0);
        return get(id);
    }

    @PostMapping("/{id}/withdraw")
    @OperationLog(module = "purchase", description = "撤回采购计划审批")
    public Result<Map<String, Object>> withdraw(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var inst = approvalService.getByBusiness("purchase_plan", id);
        if (inst != null) {
            approvalService.withdraw(UUID.fromString(inst.get("id").toString()),
                    UUID.fromString(body.get("applicantId").toString()));
        }
        return get(id);
    }
}
