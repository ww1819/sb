package com.meis.saas.asset.controller;

import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/asset/inspection")
@RequiredArgsConstructor
public class InspectionController {
    private final JdbcTemplate jdbc;

    @GetMapping("/plans")
    public Result<List<Map<String, Object>>> plans(@RequestParam(defaultValue = "pending") String status) {
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM inspection_plan WHERE status = ? ORDER BY plan_date LIMIT 100", status));
    }

    @PostMapping("/records")
    public Result<Map<String, Object>> createRecord(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO inspection_record (id, plan_id, device_id, inspector_id, result, remark, status) VALUES (?,?,?,?,?,?,?)",
                id, body.get("planId"), body.get("deviceId"), body.get("inspectorId"),
                body.get("result"), body.get("remark"), "completed");
        if (body.get("planId") != null) {
            jdbc.update("UPDATE inspection_plan SET status = 'completed' WHERE id = ?::uuid", body.get("planId"));
        }
        return Result.ok(Map.of("id", id.toString()));
    }
}
