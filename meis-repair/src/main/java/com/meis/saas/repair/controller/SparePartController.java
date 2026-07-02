package com.meis.saas.repair.controller;

import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repair/spare")
@RequiredArgsConstructor
public class SparePartController {
    private final JdbcTemplate jdbc;

    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> alerts() {
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM spare_part WHERE stock_quantity <= min_stock ORDER BY stock_quantity"));
    }

    @GetMapping("/transactions")
    public Result<List<Map<String, Object>>> transactions() {
        return Result.ok(jdbc.queryForList(
                "SELECT t.*, p.part_name FROM spare_part_transaction t LEFT JOIN spare_part p ON p.id = t.spare_part_id ORDER BY t.created_at DESC LIMIT 100"));
    }
}
