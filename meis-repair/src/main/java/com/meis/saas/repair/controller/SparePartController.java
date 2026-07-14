package com.meis.saas.repair.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/repair/spare")
@RequiredArgsConstructor
public class SparePartController {
    private final JdbcTemplate jdbc;

    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> alerts() {
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM spare_part WHERE stock_quantity <= min_stock"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "spare_part", null)
                        + " ORDER BY stock_quantity"));
    }

    @GetMapping("/transactions")
    public Result<List<Map<String, Object>>> transactions() {
        return Result.ok(jdbc.queryForList(
                "SELECT t.*, p.part_name FROM spare_part_transaction t LEFT JOIN spare_part p ON p.id = t.spare_part_id"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "spare_part", "p")
                        + " WHERE 1=1"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "spare_part_transaction", "t")
                        + " ORDER BY t.created_at DESC LIMIT 100"));
    }

    @PostMapping("/stock-in")
    @Transactional
    @OperationLog(module = "repair", description = "备件入库")
    public Result<Map<String, Object>> stockIn(@RequestBody Map<String, Object> body) {
        UUID partId = UUID.fromString(body.get("spare_part_id").toString());
        int qty = ((Number) body.get("quantity")).intValue();
        jdbc.update("UPDATE spare_part SET stock_quantity = COALESCE(stock_quantity,0) + ?, updated_at = NOW() WHERE id = ?::uuid", qty, partId);
        jdbc.update("INSERT INTO spare_part_transaction (id, spare_part_id, txn_type, quantity, remark) VALUES (?::uuid,?::uuid,'in',?,?)",
                UUID.randomUUID(), partId, qty, body.get("remark"));
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM spare_part WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "spare_part", null), partId).get(0));
    }

    @PostMapping("/stock-out")
    @Transactional
    @OperationLog(module = "repair", description = "备件出库")
    public Result<Map<String, Object>> stockOut(@RequestBody Map<String, Object> body) {
        UUID partId = UUID.fromString(body.get("spare_part_id").toString());
        int qty = ((Number) body.get("quantity")).intValue();
        jdbc.update("UPDATE spare_part SET stock_quantity = GREATEST(COALESCE(stock_quantity,0) - ?, 0), updated_at = NOW() WHERE id = ?::uuid", qty, partId);
        jdbc.update("INSERT INTO spare_part_transaction (id, spare_part_id, txn_type, quantity, workorder_id, remark) VALUES (?::uuid,?::uuid,'out',?,?::uuid,?)",
                UUID.randomUUID(), partId, qty, body.get("workorder_id"), body.get("remark"));
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM spare_part WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "spare_part", null), partId).get(0));
    }
}
