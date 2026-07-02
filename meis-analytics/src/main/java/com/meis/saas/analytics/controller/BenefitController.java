package com.meis.saas.analytics.controller;

import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/analytics/benefit")
@RequiredArgsConstructor
public class BenefitController {
    private final JdbcTemplate jdbc;

    @GetMapping("/device/{deviceId}")
    public Result<Map<String, Object>> deviceBenefit(@PathVariable UUID deviceId) {
        var device = jdbc.queryForList("SELECT * FROM medical_device WHERE id = ?::uuid", deviceId);
        if (device.isEmpty()) return Result.ok(null);
        Map<String, Object> d = device.get(0);
        d.put("summaries", jdbc.queryForList(
                "SELECT * FROM device_benefit_summary WHERE device_id = ?::uuid ORDER BY summary_year DESC, summary_month DESC LIMIT 24", deviceId));
        d.put("usage", jdbc.queryForList(
                "SELECT * FROM device_usage_record WHERE device_id = ?::uuid ORDER BY record_date DESC LIMIT 30", deviceId));
        d.put("costs", jdbc.queryForList(
                "SELECT * FROM device_cost_record WHERE device_id = ?::uuid ORDER BY record_date DESC LIMIT 30", deviceId));
        return Result.ok(d);
    }

    @GetMapping("/dept-ranking")
    public Result<List<Map<String, Object>>> deptRanking() {
        return Result.ok(jdbc.queryForList("""
            SELECT d.dept_name, COALESCE(SUM(s.net_profit),0) AS net_profit, COALESCE(AVG(s.profit_rate),0) AS avg_profit_rate
            FROM department d
            LEFT JOIN device_benefit_summary s ON s.dept_id = d.id
            GROUP BY d.dept_name ORDER BY net_profit DESC
            """));
    }
}
