package com.meis.saas.purchase.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalInstanceService;
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

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM purchase_contract WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> c = rows.get(0);
        c.put("payments", jdbc.queryForList("SELECT * FROM contract_payment WHERE contract_id = ?::uuid", id));
        return Result.ok(c);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "purchase", description = "保存采购合同")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM purchase_contract WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE purchase_contract SET contract_name=?, project_id=?::uuid, supplier_id=?::uuid,
                contract_amount=?, sign_date=?, approval_status=?, updated_at=NOW() WHERE id=?::uuid
                """, body.get("contract_name"), body.get("project_id"), body.get("supplier_id"),
                    body.get("contract_amount"), body.get("sign_date"), body.getOrDefault("approval_status", "draft"), id);
        } else {
            jdbc.update("""
                INSERT INTO purchase_contract (id, contract_code, contract_name, project_id, supplier_id, contract_amount, sign_date, approval_status)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?,?)
                """, id, body.getOrDefault("contract_code", "CT" + System.currentTimeMillis()),
                    body.get("contract_name"), body.get("project_id"), body.get("supplier_id"),
                    body.get("contract_amount"), body.get("sign_date"), "draft");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> payments = (List<Map<String, Object>>) body.getOrDefault("payments", List.of());
        jdbc.update("DELETE FROM contract_payment WHERE contract_id = ?::uuid", id);
        for (Map<String, Object> p : payments) {
            jdbc.update("INSERT INTO contract_payment (id, contract_id, payment_no, payment_stage, payment_amount, payment_date, invoice_no, status) VALUES (?::uuid,?::uuid,?,?,?,?,?,?)",
                    UUID.randomUUID(), id, p.getOrDefault("payment_no", "PAY" + System.nanoTime()),
                    p.get("payment_stage"), p.get("payment_amount"), p.get("payment_date"), p.get("invoice_no"), p.getOrDefault("status", "pending"));
        }
        return get(id);
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
}
