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

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存盘点任务")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM inventory_check WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE inventory_check SET check_name=?, check_year=?, check_type=?, campus_id=?::uuid, dept_id=?::uuid,
                start_date=?, end_date=?, checker_id=?::uuid, supervisor_id=?::uuid, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("check_name"), body.get("check_year"), body.get("check_type"), body.get("campus_id"),
                    body.get("dept_id"), body.get("start_date"), body.get("end_date"), body.get("checker_id"),
                    body.get("supervisor_id"), body.get("remark"), id);
        } else {
            jdbc.update("""
                INSERT INTO inventory_check (id, check_no, check_name, check_year, check_type, campus_id, dept_id,
                start_date, end_date, checker_id, supervisor_id, status)
                VALUES (?::uuid,?,?,?,?,?::uuid,?::uuid,?,?,?::uuid,?::uuid,?)
                """, id, body.getOrDefault("check_no", "IC" + System.currentTimeMillis()), body.get("check_name"),
                    body.get("check_year"), body.getOrDefault("check_type", "annual"), body.get("campus_id"),
                    body.get("dept_id"), body.get("start_date"), body.get("end_date"), body.get("checker_id"),
                    body.get("supervisor_id"), "planning");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        if (!items.isEmpty()) {
            jdbc.update("DELETE FROM inventory_check_item WHERE check_id = ?::uuid", id);
            for (Map<String, Object> item : items) {
                jdbc.update("""
                    INSERT INTO inventory_check_item (id, check_id, device_code, device_name, expected_location,
                    actual_location, is_found, is_matched, condition_status, remark)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), id, item.get("device_code"), item.get("device_name"),
                        item.get("expected_location"), item.get("actual_location"), item.get("is_found"),
                        item.get("is_matched"), item.get("condition_status"), item.get("remark"));
            }
        }
        return get(id);
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
