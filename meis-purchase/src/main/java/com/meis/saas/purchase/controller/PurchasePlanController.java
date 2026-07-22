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
            @RequestParam(required = false) String plan_type,
            @RequestParam(required = false) String dept_id) {
        return Result.ok(PurchasePageQueries.planPage(jdbc, query, approval_status, plan_year, plan_type, dept_id));
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

    @GetMapping("/next-code")
    public Result<Map<String, Object>> nextCode() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plan_code", nextPlanCode());
        data.put("plan_year", java.time.LocalDate.now().getYear());
        return Result.ok(data);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "purchase", description = "保存采购计划")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM purchase_plan WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null), id).isEmpty();
        String planCode;
        if (exists) {
            // 编辑保留原编号
            Object existingCode = jdbc.queryForList(
                    "SELECT plan_code FROM purchase_plan WHERE id = ?::uuid", id).stream()
                    .findFirst().map(r -> r.get("plan_code")).orElse(null);
            planCode = existingCode != null ? existingCode.toString()
                    : (isBlank(body.get("plan_code")) ? nextPlanCode() : body.get("plan_code").toString().trim());
        } else {
            planCode = isBlank(body.get("plan_code")) ? nextPlanCode() : body.get("plan_code").toString().trim();
            if (!isValidCgPlanCode(planCode) || planCodeExists(planCode, null)) {
                planCode = nextPlanCode();
            }
        }
        if (isBlank(body.get("fill_date"))) {
            body.put("fill_date", java.time.LocalDate.now().toString());
        }
        if (isBlank(body.get("plan_year"))) {
            body.put("plan_year", java.time.LocalDate.now().getYear());
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
            // 并发兜底：编号冲突则重取
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    jdbc.update("""
                        INSERT INTO purchase_plan (id, plan_code, plan_year, campus_id, dept_id, applicant_id, total_budget,
                        justification, remark, plan_type, fund_source, approval_status,
                        is_large_equipment, large_equipment_class, benefit_analysis_url, dept_argument_url, business_chain_no,
                        device_name, unit, model, fill_date, existing_device_status, existing_device_usage_freq,
                        reference_manufacturer, specification, brand, quantity, similar_device_count, demand_level,
                        product_attribute_req, other_condition_confirm, unit_budget_price, category_id, demand_nature,
                        prefer_import)
                        VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::date,?,?,?,?,?,?,?,?,?,?,?,?::uuid,?,?)
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
                    break;
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    if (attempt == 4) throw e;
                    planCode = nextPlanCode();
                }
            }
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = new ArrayList<>(
                (List<Map<String, Object>>) body.getOrDefault("items", List.of()));
        validateAndNormalizeItems(items);
        double totalBudget = resolveTotalBudget(body, items);
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
                    qty, price, lineTotal, blankToNull(item.get("specification")),
                    intOrDefault(item.get("priority"), 1), blankToNull(item.get("justification")),
                    uuidOrNull(item.get("use_dept_id")),
                    boolOrDefault(item.get("is_imported"), false), blankToNull(item.get("registration_no")),
                    blankToNull(item.get("unit")), blankToNull(item.get("brand_intent")),
                    boolOrDefault(item.get("is_metrology"), false),
                    blankToNull(item.get("udi_code")),
                    intOrNull(item.get("similar_device_count")), blankToNull(item.get("demand_level")),
                    blankToNull(item.get("product_attribute_req")),
                    blankToNull(item.get("fund_source")), blankToNull(item.get("demand_nature")),
                    blankToNull(item.get("existing_device_status")), blankToNull(item.get("existing_device_usage_freq")),
                    blankToNull(item.get("other_condition_confirm")),
                    boolOrDefault(item.get("is_large_equipment"), false),
                    blankToNull(item.get("large_equipment_class")));
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

    /** 生成计划编号：CG-yyyyMMdd + 当日4位流水，如 CG-202607161234 */
    private String nextPlanCode() {
        String day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "CG-" + day;
        Integer maxSeq = jdbc.queryForObject("""
                SELECT MAX(CAST(RIGHT(plan_code, 4) AS INTEGER))
                FROM purchase_plan
                WHERE plan_code LIKE ? AND LENGTH(plan_code) = ?
                """, Integer.class, prefix + "%", prefix.length() + 4);
        int next = (maxSeq == null ? 0 : maxSeq) + 1;
        if (next > 9999) {
            throw new BizException(400, "当日计划编号已用尽，请明日再试");
        }
        return prefix + String.format("%04d", next);
    }

    private static boolean isValidCgPlanCode(String code) {
        return code != null && code.matches("^CG-\\d{8}\\d{4}$");
    }

    private boolean planCodeExists(String planCode, UUID excludeId) {
        if (excludeId == null) {
            return !jdbc.queryForList("SELECT 1 FROM purchase_plan WHERE plan_code = ? LIMIT 1", planCode).isEmpty();
        }
        return !jdbc.queryForList(
                "SELECT 1 FROM purchase_plan WHERE plan_code = ? AND id != ?::uuid LIMIT 1",
                planCode, excludeId).isEmpty();
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
        double total = 0;
        boolean hasItems = false;
        for (Map<String, Object> item : items) {
            if (isBlank(item.get("device_name"))) continue;
            hasItems = true;
            double qty = toDouble(item.get("quantity"), 1);
            double price = toDouble(item.get("estimated_price"), 0);
            double lineTotal = Math.round(qty * price * 100.0) / 100.0;
            item.put("total_price", lineTotal);
            total += lineTotal;
        }
        if (hasItems) {
            return Math.round(total * 100.0) / 100.0;
        }
        Object budget = plan.get("total_budget");
        if (budget instanceof Number n) {
            return n.doubleValue();
        }
        if (budget != null && !budget.toString().isBlank()) {
            try {
                return Double.parseDouble(budget.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (plan.get("quantity") instanceof Number qty && plan.get("unit_budget_price") instanceof Number price) {
            return qty.doubleValue() * price.doubleValue();
        }
        return 0;
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

    private static Integer intOrNull(Object value) {
        if (isBlank(value)) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int intOrDefault(Object value, int defaultValue) {
        Integer n = intOrNull(value);
        return n != null ? n : defaultValue;
    }

    private static boolean boolOrDefault(Object value, boolean defaultValue) {
        if (value == null || (value instanceof String s && s.isBlank())) return defaultValue;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        String s = value.toString().trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
        return defaultValue;
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
