package com.meis.saas.analytics.controller;

import com.meis.saas.common.result.Result;
import com.meis.saas.analytics.service.PowerMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/power/stats")
@RequiredArgsConstructor
public class PowerStatsController {
    private final JdbcTemplate jdbc;
    private final PowerMonitorService monitorService;

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(monitorService.statsSummary());
    }

    @GetMapping("/device-ranking")
    public Result<List<Map<String, Object>>> deviceRanking(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(monitorService.deviceRanking(limit));
    }

    @GetMapping("/daily-trend")
    public Result<List<Map<String, Object>>> dailyTrend() {
        return Result.ok(jdbc.queryForList("""
                SELECT record_date,
                       SUM(run_hours) AS run_hours,
                       SUM(idle_hours) AS idle_hours,
                       SUM(offline_hours) AS offline_hours,
                       SUM(energy_kwh) AS energy_kwh
                FROM power_monitor_record
                WHERE record_date >= CURRENT_DATE - INTERVAL '30 days'
                GROUP BY record_date
                ORDER BY record_date
                """));
    }
}
