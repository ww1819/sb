package com.meis.saas.purchase.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import com.meis.saas.purchase.support.PurchasePageQueries;
import com.meis.saas.purchase.support.PurchaseValidators;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/purchase/contract")
@RequiredArgsConstructor
public class PurchaseContractController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String approval_status,
            @RequestParam(required = false) String acceptance_status) {
        return Result.ok(PurchasePageQueries.contractPage(jdbc, query, approval_status, acceptance_status));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM purchase_contract WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> c = rows.get(0);
        c.put("payments", jdbc.queryForList(
                "SELECT * FROM contract_payment WHERE contract_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "contract_payment", null), id));
        c.put("items", jdbc.queryForList("""
                SELECT id, contract_id, device_name, specification, brand, quantity, unit_price, amount,
                       manufacturer_id, manufacturer_name, sort_order
                FROM purchase_contract_item
                WHERE contract_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract_item", null) + """
                ORDER BY sort_order ASC NULLS LAST, created_at ASC NULLS LAST
                """, id));
        return Result.ok(c);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "purchase", description = "保存采购合同")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        PurchaseValidators.validateContractAmount(jdbc, body.get("project_id"), body.get("contract_amount"), id);
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM purchase_contract WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null), id).isEmpty();
        Object chainNo = null;
        if (body.get("project_id") != null) {
            var pj = jdbc.queryForList(
                    "SELECT business_chain_no FROM purchase_project WHERE id = ?::uuid"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null), body.get("project_id"));
            if (!pj.isEmpty()) chainNo = pj.get(0).get("business_chain_no");
        }
        if (exists) {
            PurchaseValidators.checkVersion(jdbc, "purchase_contract", id, body.get("version"));
            jdbc.update("""
                UPDATE purchase_contract SET contract_name=?, project_id=?::uuid, supplier_id=?::uuid,
                contract_amount=?, sign_date=?, start_date=?, end_date=?, delivery_deadline=?, warranty_period=?,
                payment_terms=?, contract_file_url=?, acceptance_status=?, acceptance_report_url=?,
                invoice_summary=?, contract_type=?, performance_bond=?, registration_cert_url=?, fund_source=?,
                approval_status=?, status=?, remark=?, version=COALESCE(version,1)+1, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("contract_name"), body.get("project_id"), body.get("supplier_id"),
                    body.get("contract_amount"), body.get("sign_date"), body.get("start_date"), body.get("end_date"),
                    body.get("delivery_deadline"), body.get("warranty_period"), body.get("payment_terms"),
                    body.get("contract_file_url"), body.get("acceptance_status"), body.get("acceptance_report_url"),
                    body.get("invoice_summary"), body.getOrDefault("contract_type", "purchase"),
                    body.get("performance_bond"), body.get("registration_cert_url"), body.get("fund_source"),
                    body.getOrDefault("approval_status", "draft"),
                    body.getOrDefault("status", "active"), body.get("remark"), id);
        } else {
            jdbc.update("""
                INSERT INTO purchase_contract (id, contract_code, contract_name, project_id, supplier_id, contract_amount,
                sign_date, start_date, end_date, delivery_deadline, warranty_period, payment_terms, contract_file_url,
                contract_type, performance_bond, registration_cert_url, fund_source, approval_status, status, acceptance_status,
                invoice_summary, acceptance_report_url, remark, business_chain_no)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, body.getOrDefault("contract_code", "CT" + System.currentTimeMillis()),
                    body.get("contract_name"), body.get("project_id"), body.get("supplier_id"),
                    body.get("contract_amount"), body.get("sign_date"), body.get("start_date"), body.get("end_date"),
                    body.get("delivery_deadline"), body.get("warranty_period"), body.get("payment_terms"),
                    body.get("contract_file_url"), body.getOrDefault("contract_type", "purchase"),
                    body.get("performance_bond"), body.get("registration_cert_url"), body.get("fund_source"),
                    "draft", "active", body.getOrDefault("acceptance_status", "pending"),
                    body.get("invoice_summary"), body.get("acceptance_report_url"), body.get("remark"), chainNo);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> payments = (List<Map<String, Object>>) body.getOrDefault("payments", List.of());
        PurchaseValidators.validatePaymentTotal(jdbc, id, payments);
        jdbc.update("DELETE FROM contract_payment WHERE contract_id = ?::uuid", id);
        for (Map<String, Object> p : payments) {
            jdbc.update("""
                INSERT INTO contract_payment (id, contract_id, payment_no, payment_stage, payment_amount,
                payment_date, invoice_no, invoice_url, payee_account, status, approval_status,
                finance_auditor_id, finance_audit_date, invoice_type, tax_amount, voucher_no, remark)
                VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?::uuid,?,?,?,?,?)
                """,
                    p.containsKey("id") ? UUID.fromString(p.get("id").toString()) : UUID.randomUUID(),
                    id, p.getOrDefault("payment_no", "PAY" + System.nanoTime()),
                    p.get("payment_stage"), p.get("payment_amount"), p.get("payment_date"), p.get("invoice_no"),
                    p.get("invoice_url"), p.get("payee_account"), p.getOrDefault("status", "pending"),
                    p.getOrDefault("approval_status", "draft"), p.get("finance_auditor_id"),
                    p.get("finance_audit_date"), p.get("invoice_type"), p.get("tax_amount"),
                    p.get("voucher_no"), p.get("remark"));
        }
        saveContractItems(id, body);
        PurchaseValidators.recalcContractPaymentProgress(jdbc, id);
        return get(id);
    }

    private void saveContractItems(UUID contractId, Map<String, Object> body) {
        Object raw = body.get("items");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        var ctx = com.meis.saas.common.rbac.PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
        jdbc.update("""
                UPDATE purchase_contract_item
                SET is_deleted = 1, deleted_at = NOW(), deleted_by = ?::uuid, deleted_by_name = ?,
                    updated_at = NOW(), updated_by = ?::uuid, updated_by_name = ?
                WHERE contract_id = ?::uuid AND is_deleted = 0
                """, actorId, actorName, actorId, actorName, contractId);
        int order = 1;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) m;
            String deviceName = blankToNull(row.get("device_name"));
            if (deviceName == null) continue;
            UUID manufacturerId = parseUuid(row.get("manufacturer_id"));
            String manufacturerName = blankToNull(row.get("manufacturer_name"));
            if (manufacturerId != null) {
                var mf = jdbc.queryForList(
                        "SELECT manufacturer_name FROM manufacturer WHERE id = ?::uuid"
                                + SoftDeleteSupport.notDeletedClause(jdbc, "manufacturer", null),
                        manufacturerId);
                if (!mf.isEmpty()) {
                    manufacturerName = blankToNull(mf.get(0).get("manufacturer_name"));
                }
            }
            Object qty = row.get("quantity");
            Object price = row.get("unit_price");
            Object amount = row.get("amount");
            if (amount == null) {
                Double qn = toDouble(qty);
                Double pn = toDouble(price);
                if (qn != null && pn != null) {
                    amount = Math.round(qn * pn * 100.0) / 100.0;
                }
            }
            jdbc.update("""
                    INSERT INTO purchase_contract_item (
                        id, contract_id, device_name, specification, brand, quantity, unit_price, amount,
                        manufacturer_id, manufacturer_name, sort_order,
                        created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                    ) VALUES (
                        ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?,
                        ?::uuid, ?, ?,
                        NOW(), NOW(), ?::uuid, ?, ?::uuid, ?, 0
                    )
                    """,
                    UUID.randomUUID(), contractId, deviceName,
                    blankToNull(row.get("specification")),
                    blankToNull(row.get("brand")),
                    qty, price, amount,
                    manufacturerId, manufacturerName, order++,
                    actorId, actorName, actorId, actorName);
        }
    }

    private static String blankToNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static UUID parseUuid(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        return UUID.fromString(s);
    }

    private static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "purchase", description = "提交合同审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> c = get(id).getData();
        approvalService.submit("purchase_contract", id, c.get("contract_code").toString(),
                "采购合同 " + c.get("contract_code"), UUID.fromString(body.get("applicantId").toString()),
                c.get("contract_amount") != null ? ((Number) c.get("contract_amount")).doubleValue() : 0);
        return get(id);
    }

    @PostMapping("/{contractId}/payments/{paymentId}/submit")
    @OperationLog(module = "purchase", description = "提交付款审批")
    public Result<Map<String, Object>> submitPayment(@PathVariable UUID contractId, @PathVariable UUID paymentId,
            @RequestBody Map<String, Object> body) {
        var rows = jdbc.queryForList(
                "SELECT * FROM contract_payment WHERE id = ?::uuid AND contract_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "contract_payment", null), paymentId, contractId);
        if (rows.isEmpty()) throw new BizException(404, "payment not found");
        Map<String, Object> payment = rows.get(0);
        approvalService.submit("contract_payment", paymentId,
                payment.get("payment_no").toString(),
                "合同付款 " + payment.get("payment_no"),
                UUID.fromString(body.get("applicantId").toString()),
                payment.get("payment_amount") != null ? ((Number) payment.get("payment_amount")).doubleValue() : 0);
        jdbc.update("UPDATE contract_payment SET approval_status = 'pending' WHERE id = ?::uuid", paymentId);
        return get(contractId);
    }
}
