package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/asset/inventory")
@RequiredArgsConstructor
public class InventoryCheckController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM inventory_check WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> t = rows.get(0);
        t.put("items", jdbc.queryForList("SELECT * FROM inventory_check_item WHERE check_id = ?::uuid", id));
        return Result.ok(t);
    }

    @PostMapping("/{id}/start")
    @OperationLog(module = "asset", description = "开始盘点")
    public Result<Map<String, Object>> start(@PathVariable UUID id) {
        jdbc.update("UPDATE inventory_check SET status = 'in_progress', actual_start_at = NOW(), updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }

    @PostMapping("/{id}/scan")
    @OperationLog(module = "asset", description = "扫码盘点")
    public Result<Map<String, Object>> scan(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("""
            INSERT INTO inventory_check_item (id, check_id, device_id, device_code, book_status, actual_status, check_result)
            VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?)
            ON CONFLICT DO NOTHING
            """, UUID.randomUUID(), id, body.get("device_id"), body.get("device_code"),
                body.getOrDefault("book_status", "normal"), body.getOrDefault("actual_status", "normal"), "matched");
        jdbc.update("UPDATE inventory_check SET checked_count = COALESCE(checked_count,0)+1, updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }

    @PostMapping("/{id}/complete")
    @OperationLog(module = "asset", description = "完成盘点")
    public Result<Map<String, Object>> complete(@PathVariable UUID id) {
        jdbc.update("UPDATE inventory_check SET status = 'completed', actual_end_at = NOW(), updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }
}
