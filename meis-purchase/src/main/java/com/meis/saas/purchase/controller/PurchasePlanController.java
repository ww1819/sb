package com.meis.saas.purchase.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
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
            @RequestParam(required = false) Integer plan_year,
            @RequestParam(required = false) String plan_type) {
        return Result.ok(PurchasePageQueries.planPage(jdbc, query, approval_status, plan_year, plan_type));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        List<Map<String, Object>> plans = jdbc.queryForList(
                "SELECT * FROM purchase_plan WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null), id);
        if (plans.isEmpty()) throw new BizException(404, "plan not found");
        Map<String, Object> plan = plans.get(0);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT * FROM purchase_plan_item WHERE plan_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", null), id);
        backfillPlanFromFirstItem(plan, items);
        if (plan.get("fill_date") == null && plan.get("created_at") != null) {
            Object created = plan.get("created_at");
            if (created instanceof java.sql.Timestamp ts) {
                plan.put("fill_date", ts.toLocalDateTime().toLocalDate().toString());
            } else if (created instanceof java.time.OffsetDateTime odt) {
                plan.put("fill_date", odt.toLocalDate().toString());
            } else if (created instanceof java.time.LocalDateTime ldt) {
                plan.put("fill_date", ldt.toLocalDate().toString());
            }
        }
        plan.put("items", items);
        return Result.ok(plan);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "purchase", description = "保存采购计划")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM purchase_plan WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null), id).isEmpty();
        String planCode = body.getOrDefault("plan_code", "PP" + System.currentTimeMillis()).toString();
        if (isBlank(body.get("fill_date"))) {
            body.put("fill_date", java.time.LocalDate.now().toString());
        }
        if (exists) {
            PurchaseValidators.checkVersion(jdbc, "purchase_plan", id, body.get("version"));
            jdbc.update("""
                UPDATE purchase_plan SET plan_code=?, plan_year=?, campus_id=?::uuid, dept_id=?::uuid, applicant_id=?::uuid,
                total_budget=?, justification=?, remark=?, plan_type=?, fund_source=?,
                is_large_equipment=?, large_equipment_class=?, benefit_analysis_url=?, dept_argument_url=?,
                device_name=?, unit=?, model=?, fill_date=?::date, existing_device_status=?,
                existing_device_usage_freq=?, reference_manufacturer=?, specification=?, brand=?,
                quantity=?, similar_device_count=?, demand_level=?, product_attribute_req=?,
                other_condition_confirm=?, unit_budget_price=?, category_id=?::uuid, demand_nature=?,
                prefer_import=?, approval_status=?, version=COALESCE(version,1)+1, updated_at=NOW()
                WHERE id=?::uuid
                """,
                    planCode, body.get("plan_year"), uuidOrNull(body.get("campus_id")), uuidOrNull(body.get("dept_id")),
                    uuidOrNull(body.get("applicant_id")),
                    body.get("total_budget"), body.get("justification"), body.get("remark"),
                    body.getOrDefault("plan_type", "annual"), body.get("fund_source"),
                    body.getOrDefault("is_large_equipment", false), body.get("large_equipment_class"),
                    body.get("benefit_analysis_url"), body.get("dept_argument_url"),
                    body.get("device_name"), body.get("unit"), body.get("model"), blankToNull(body.get("fill_date")),
                    body.get("existing_device_status"), body.get("existing_device_usage_freq"),
                    body.get("reference_manufacturer"), body.get("specification"), body.get("brand"),
                    body.get("quantity"), body.get("similar_device_count"), body.get("demand_level"),
                    body.get("product_attribute_req"), body.get("other_condition_confirm"),
                    body.get("unit_budget_price"), uuidOrNull(body.get("category_id")), body.get("demand_nature"),
                    body.getOrDefault("prefer_import", false),
                    body.getOrDefault("approval_status", "draft"), id);
        } else {
            jdbc.update("""
                INSERT INTO purchase_plan (id, plan_code, plan_year, campus_id, dept_id, applicant_id, total_budget,
                justification, remark, plan_type, fund_source, approval_status,
                is_large_equipment, large_equipment_class, benefit_analysis_url, dept_argument_url, business_chain_no,
                device_name, unit, model, fill_date, existing_device_status, existing_device_usage_freq,
                reference_manufacturer, specification, brand, quantity, similar_device_count, demand_level,
                product_attribute_req, other_condition_confirm, unit_budget_price, category_id, demand_nature,
                prefer_import)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?,?::date,?,?,?,?,?,?,?,?,?,?,?,?::uuid,?,?)
                """,
                    id, planCode, body.get("plan_year"), uuidOrNull(body.get("campus_id")), uuidOrNull(body.get("dept_id")),
                    uuidOrNull(body.get("applicant_id")),
                    body.get("total_budget"), body.get("justification"), body.get("remark"),
                    body.getOrDefault("plan_type", "annual"), body.get("fund_source"), "draft",
                    body.getOrDefault("is_large_equipment", false), body.get("large_equipment_class"),
                    body.get("benefit_analysis_url"), body.get("dept_argument_url"),
                    PurchaseChainService.newChainNo(planCode),
                    body.get("device_name"), body.get("unit"), body.get("model"), blankToNull(body.get("fill_date")),
                    body.get("existing_device_status"), body.get("existing_device_usage_freq"),
                    body.get("reference_manufacturer"), body.get("specification"), body.get("brand"),
                    body.get("quantity"), body.get("similar_device_count"), body.get("demand_level"),
                    body.get("product_attribute_req"), body.get("other_condition_confirm"),
                    body.get("unit_budget_price"), uuidOrNull(body.get("category_id")), body.get("demand_nature"),
                    body.getOrDefault("prefer_import", false));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = new ArrayList<>(
                (List<Map<String, Object>>) body.getOrDefault("items", List.of()));
        validateAndNormalizeItems(items);
        double totalBudget = resolveTotalBudget(body, items);
        if (body.get("plan_year") != null && !body.get("plan_year").toString().isBlank()) {
            int year = parseInt(body.get("plan_year"));
            var dup = jdbc.queryForList(
                    "SELECT id FROM purchase_plan WHERE plan_year = ? AND id != ?::uuid AND is_active = true"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null) + " LIMIT 1",
                    year, id);
            if (!dup.isEmpty() && !exists) {
                throw new BizException(400, "该年度已有采购计划");
            }
        }
        jdbc.update("DELETE FROM purchase_plan_item WHERE plan_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            if (isBlank(item.get("device_name"))) continue;
            double qty = toDouble(item.get("quantity"), 0);
            double price = toDouble(item.get("estimated_price"), 0);
            double lineTotal = Math.round(qty * price * 100.0) / 100.0;
            item.put("total_price", lineTotal);
            jdbc.update("""
                INSERT INTO purchase_plan_item (id, plan_id, device_name, category_id, quantity, estimated_price,
                total_price, specification, priority, justification, use_dept_id, is_imported, registration_no,
                unit, brand_intent, is_metrology, udi_code,
                similar_device_count, demand_level, product_attribute_req, fund_source, demand_nature,
                existing_device_status, existing_device_usage_freq, other_condition_confirm,
                is_large_equipment, large_equipment_class)
                VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?,?,?,?,?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                    UUID.randomUUID(), id, item.get("device_name"), uuidOrNull(item.get("category_id")),
                    qty, price, lineTotal, item.get("specification"),
                    item.getOrDefault("priority", 1), item.get("justification"), uuidOrNull(item.get("use_dept_id")),
                    item.getOrDefault("is_imported", false), item.get("registration_no"),
                    item.get("unit"), item.get("brand_intent"), item.getOrDefault("is_metrology", false),
                    item.get("udi_code"),
                    item.get("similar_device_count"), item.get("demand_level"), item.get("product_attribute_req"),
                    item.get("fund_source"), item.get("demand_nature"),
                    item.get("existing_device_status"), item.get("existing_device_usage_freq"),
                    item.get("other_condition_confirm"),
                    item.getOrDefault("is_large_equipment", false), item.get("large_equipment_class"));
        }
        jdbc.update("UPDATE purchase_plan SET total_budget = ?, updated_at = NOW() WHERE id = ?::uuid", totalBudget, id);
        if (!exists) {
            PurchaseChainService.ensurePlanChain(jdbc, id, planCode);
        }
        return get(id);
    }

    @PatchMapping("/{id}/attachment")
    @Transactional
    @OperationLog(module = "purchase", description = "更新采购计划附件")
    public Result<Map<String, Object>> updateAttachment(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Object fieldObj = body.get("field");
        if (fieldObj == null || fieldObj.toString().isBlank()) {
            throw new BizException(400, "缺少附件字段");
        }
        String field = fieldObj.toString();
        if (!Set.of("benefit_analysis_url", "dept_argument_url").contains(field)) {
            throw new BizException(400, "不支持的附件字段");
        }
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM purchase_plan WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null), id).isEmpty();
        if (!exists) throw new BizException(404, "plan not found");
        Object url = body.get("url");
        String urlVal = url == null || url.toString().isBlank() ? null : url.toString();
        jdbc.update("UPDATE purchase_plan SET " + field + " = ?, updated_at = NOW() WHERE id = ?::uuid", urlVal, id);
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

    private static void backfillPlanFromFirstItem(Map<String, Object> plan, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return;
        Map<String, Object> item = items.get(0);
        copyIfBlank(plan, item, "device_name");
        copyIfBlank(plan, item, "unit");
        copyIfBlank(plan, item, "specification");
        copyIfBlank(plan, item, "quantity");
        copyIfBlank(plan, item, "category_id");
        if (isBlank(plan.get("brand")) && item.get("brand_intent") != null) {
            plan.put("brand", item.get("brand_intent"));
        }
        if (plan.get("unit_budget_price") == null && item.get("estimated_price") != null) {
            plan.put("unit_budget_price", item.get("estimated_price"));
        }
        if (plan.get("prefer_import") == null && item.get("is_imported") != null) {
            plan.put("prefer_import", item.get("is_imported"));
        }
    }

    private static double resolveTotalBudget(Map<String, Object> plan, List<Map<String, Object>> items) {
        Object budget = plan.get("total_budget");
        if (budget instanceof Number n) {
            return n.doubleValue();
        }
        if (budget != null && !budget.toString().isBlank()) {
            try {
                return Double.parseDouble(budget.toString());
            } catch (NumberFormatException ignored) {
                // fall through to item sum
            }
        }
        double total = 0;
        for (Map<String, Object> item : items) {
            if (isBlank(item.get("device_name"))) continue;
            Number qty = item.get("quantity") instanceof Number n ? n : 1;
            Number price = item.get("estimated_price") instanceof Number n ? n : 0;
            double lineTotal = qty.doubleValue() * price.doubleValue();
            item.put("total_price", lineTotal);
            total += lineTotal;
        }
        if (total == 0 && plan.get("quantity") instanceof Number qty && plan.get("unit_budget_price") instanceof Number price) {
            total = qty.doubleValue() * price.doubleValue();
        }
        return total;
    }

    private static void validateAndNormalizeItems(List<Map<String, Object>> items) {
        int line = 0;
        for (Map<String, Object> item : items) {
            if (isBlank(item.get("device_name"))) continue;
            line++;
            if (isBlank(item.get("specification"))) {
                throw new BizException(400, "第 " + line + " 行请填写规格型号");
            }
            double qty = toDouble(item.get("quantity"), Double.NaN);
            if (Double.isNaN(qty) || !(qty > 0)) {
                throw new BizException(400, "第 " + line + " 行数量须大于 0");
            }
            Double price = nullableDouble(item.get("estimated_price"));
            if (price == null) {
                throw new BizException(400, "第 " + line + " 行请填写预估单价");
            }
            double lineTotal = Math.round(qty * price * 100.0) / 100.0;
            item.put("quantity", qty);
            item.put("estimated_price", price);
            item.put("total_price", lineTotal);
        }
    }

    private static double toDouble(Object value, double defaultValue) {
        Double n = nullableDouble(value);
        return n != null ? n : defaultValue;
    }

    private static Double nullableDouble(Object value) {
        if (value == null || (value instanceof String s && s.isBlank())) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void copyIfBlank(Map<String, Object> plan, Map<String, Object> item, String key) {
        if (isBlank(plan.get(key)) && item.get(key) != null) {
            plan.put(key, item.get(key));
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private static Object blankToNull(Object value) {
        return isBlank(value) ? null : value;
    }

    private static Object uuidOrNull(Object value) {
        if (isBlank(value)) return null;
        return value.toString();
    }

    private static int parseInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString().trim());
    }
}
