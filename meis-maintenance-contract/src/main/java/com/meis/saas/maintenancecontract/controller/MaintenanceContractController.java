package com.meis.saas.maintenancecontract.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/maintenance-contract")
@RequiredArgsConstructor
public class MaintenanceContractController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM maintenance_contract WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> c = rows.get(0);
        c.put("fulfillments", jdbc.queryForList("SELECT * FROM maintenance_contract_fulfillment WHERE contract_id = ?::uuid", id));
        c.put("payments", jdbc.queryForList("SELECT * FROM maintenance_contract_payment WHERE contract_id = ?::uuid", id));
        return Result.ok(c);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "mcontract", description = "保存维保合同")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM maintenance_contract WHERE id = ?::uuid", id).isEmpty();
        if (!exists) {
            jdbc.update("INSERT INTO maintenance_contract (id, contract_no, contract_name, vendor_id, start_date, end_date, contract_amount, status) VALUES (?::uuid,?,?,?::uuid,?,?,?,?)",
                    id, body.getOrDefault("contract_no", "MC" + System.currentTimeMillis()), body.get("contract_name"),
                    body.get("vendor_id"), body.get("start_date"), body.get("end_date"), body.get("contract_amount"), "active");
        }
        return get(id);
    }

    @PostMapping("/{id}/renew")
    @OperationLog(module = "mcontract", description = "续约")
    public Result<Map<String, Object>> renew(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE maintenance_contract SET end_date = ?, updated_at = NOW() WHERE id = ?::uuid", body.get("end_date"), id);
        return get(id);
    }
}
