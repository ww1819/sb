package com.meis.saas.maintain.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/maintain/plan")
@RequiredArgsConstructor
public class MaintenancePlanController {
    private final JdbcTemplate jdbc;

    @PostMapping("/{id}/activate")
    @OperationLog(module = "maintain", description = "激活保养计划")
    public Result<Map<String, Object>> activate(@PathVariable UUID id) {
        jdbc.update("UPDATE maintenance_plan SET status = 'active', updated_at = NOW() WHERE id = ?::uuid", id);
        return Result.ok(jdbc.queryForList("SELECT * FROM maintenance_plan WHERE id = ?::uuid", id).get(0));
    }

    @GetMapping("/due")
    public Result<List<Map<String, Object>>> due() {
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM maintenance_plan WHERE status = 'active' AND next_due_date <= CURRENT_DATE + 7 ORDER BY next_due_date"));
    }
}
