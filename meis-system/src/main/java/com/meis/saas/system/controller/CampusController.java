package com.meis.saas.system.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/campuses")
@RequiredArgsConstructor
public class CampusController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(jdbc.queryForList("SELECT * FROM campus ORDER BY campus_code"));
    }

    @PostMapping
    @OperationLog(module = "system", description = "创建院区")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO campus (id, campus_code, campus_name, address, contact_phone, is_active) VALUES (?::uuid,?,?,?,?,?)",
                id, body.get("campus_code"), body.get("campus_name"), body.get("address"),
                body.get("contact_phone"), body.getOrDefault("is_active", true));
        return Result.ok(jdbc.queryForList("SELECT * FROM campus WHERE id = ?::uuid", id).get(0));
    }

    @PutMapping("/{id}")
    @OperationLog(module = "system", description = "更新院区")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update(
                "UPDATE campus SET campus_code=?, campus_name=?, address=?, contact_phone=?, is_active=?, updated_at=NOW() WHERE id=?::uuid",
                body.get("campus_code"), body.get("campus_name"), body.get("address"),
                body.get("contact_phone"), body.getOrDefault("is_active", true), id);
        return Result.ok(jdbc.queryForList("SELECT * FROM campus WHERE id = ?::uuid", id).get(0));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "system", description = "删除院区")
    public Result<Void> delete(@PathVariable UUID id) {
        jdbc.update("UPDATE campus SET is_active = false, updated_at = NOW() WHERE id = ?::uuid", id);
        return Result.ok();
    }
}
