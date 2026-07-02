package com.meis.saas.repair.controller;

import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repair/engineer")
@RequiredArgsConstructor
public class EngineerController {
    private final JdbcTemplate jdbc;

    @GetMapping("/workload")
    public Result<List<Map<String, Object>>> workload() {
        return Result.ok(jdbc.queryForList("""
            SELECT e.id, e.engineer_name, COUNT(w.id) AS workorder_count,
            COALESCE(SUM(w.total_cost),0) AS total_cost
            FROM engineer e LEFT JOIN repair_workorder w ON w.assigned_engineer_id = e.id
            GROUP BY e.id, e.engineer_name ORDER BY workorder_count DESC
            """));
    }
}
