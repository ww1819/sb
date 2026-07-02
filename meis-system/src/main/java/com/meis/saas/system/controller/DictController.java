package com.meis.saas.system.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class DictController {
    private final JdbcTemplate jdbc;

    @GetMapping("/types")
    public Result<List<Map<String, Object>>> types() {
        return Result.ok(jdbc.queryForList(
                "SELECT dict_type, COUNT(*) as item_count FROM sys_dict GROUP BY dict_type ORDER BY dict_type"));
    }

    @GetMapping("/type/{dictType}")
    public Result<List<Map<String, Object>>> byType(@PathVariable String dictType) {
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM sys_dict WHERE dict_type = ? ORDER BY sort_order, dict_code", dictType));
    }

    @PostMapping
    @OperationLog(module = "system", description = "创建字典项")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, is_active, remark) VALUES (?::uuid,?,?,?,?,?,?,?)",
                id, body.get("dict_type"), body.get("dict_code"), body.get("dict_label"),
                body.get("dict_value"), body.getOrDefault("sort_order", 0),
                body.getOrDefault("is_active", true), body.get("remark"));
        return Result.ok(jdbc.queryForList("SELECT * FROM sys_dict WHERE id = ?::uuid", id).get(0));
    }

    @PutMapping("/{id}")
    @OperationLog(module = "system", description = "更新字典项")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update(
                "UPDATE sys_dict SET dict_type=?, dict_code=?, dict_label=?, dict_value=?, sort_order=?, is_active=?, remark=? WHERE id=?::uuid",
                body.get("dict_type"), body.get("dict_code"), body.get("dict_label"),
                body.get("dict_value"), body.getOrDefault("sort_order", 0),
                body.getOrDefault("is_active", true), body.get("remark"), id);
        return Result.ok(jdbc.queryForList("SELECT * FROM sys_dict WHERE id = ?::uuid", id).get(0));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "system", description = "删除字典项")
    public Result<Void> delete(@PathVariable UUID id) {
        jdbc.update("UPDATE sys_dict SET is_active = false WHERE id = ?::uuid", id);
        return Result.ok();
    }
}
