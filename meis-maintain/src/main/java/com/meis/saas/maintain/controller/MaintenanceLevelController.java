package com.meis.saas.maintain.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/maintain/level")
@RequiredArgsConstructor
public class MaintenanceLevelController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM maintenance_level WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "maintain", description = "保存保养级别")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM maintenance_level WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE maintenance_level SET level_code=?, level_name=?, sort_order=?, description=?, is_active=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("level_code"), body.get("level_name"), body.getOrDefault("sort_order", 0),
                    body.get("description"), body.getOrDefault("is_active", true), id);
        } else {
            jdbc.update("""
                INSERT INTO maintenance_level (id, level_code, level_name, sort_order, description, is_active)
                VALUES (?::uuid,?,?,?,?,?)
                """, id, body.get("level_code"), body.get("level_name"), body.getOrDefault("sort_order", 0),
                    body.get("description"), body.getOrDefault("is_active", true));
        }
        return get(id);
    }
}
