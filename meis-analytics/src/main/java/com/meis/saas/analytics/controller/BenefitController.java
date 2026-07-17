package com.meis.saas.analytics.controller;

import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
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

    @GetMapping("/device/page")
    public Result<PageResult<Map<String, Object>>> devicePage(PageQuery query) {
        StringBuilder where = new StringBuilder(" WHERE d.is_active = true ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (d.device_code ILIKE ? OR d.device_name ILIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM medical_device d" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT d.id, d.device_code, d.device_name, d.original_value, d.net_value, d.purchase_date,
                       dept.dept_name,
                       latest.benefit_level, latest.net_profit, latest.profit_rate, latest.summary_year, latest.summary_month
                FROM medical_device d
                LEFT JOIN department dept ON dept.id = d.dept_id
                LEFT JOIN LATERAL (
                    SELECT benefit_level, net_profit, profit_rate, summary_year, summary_month
                    FROM device_benefit_summary s WHERE s.device_id = d.id
                    ORDER BY summary_year DESC, summary_month DESC LIMIT 1
                ) latest ON true
                """ + where + " ORDER BY d.device_code LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/device/{deviceId}")
    public Result<Map<String, Object>> deviceBenefit(@PathVariable UUID deviceId) {
        var device = jdbc.queryForList("""
                SELECT d.*, dept.dept_name FROM medical_device d
                LEFT JOIN department dept ON dept.id = d.dept_id
                WHERE d.id = ?::uuid
                """, deviceId);
        if (device.isEmpty()) return Result.ok(null);
        Map<String, Object> d = new LinkedHashMap<>(device.get(0));
        d.put("summaries", jdbc.queryForList(
                "SELECT * FROM device_benefit_summary WHERE device_id = ?::uuid ORDER BY summary_year DESC, summary_month DESC LIMIT 24", deviceId));
        d.put("usage", jdbc.queryForList(
                "SELECT * FROM device_usage_record WHERE device_id = ?::uuid ORDER BY usage_date DESC LIMIT 30", deviceId));
        d.put("costs", jdbc.queryForList(
                "SELECT * FROM device_cost_record WHERE device_id = ?::uuid ORDER BY cost_date DESC LIMIT 30", deviceId));
        return Result.ok(d);
    }

    @GetMapping("/dept-ranking")
    public Result<List<Map<String, Object>>> deptRanking() {
        return Result.ok(jdbc.queryForList("""
            SELECT dept.dept_name, COALESCE(SUM(s.net_profit),0) AS net_profit, COALESCE(AVG(s.profit_rate),0) AS avg_profit_rate
            FROM department dept
            LEFT JOIN medical_device d ON d.dept_id = dept.id
            LEFT JOIN device_benefit_summary s ON s.device_id = d.id
            GROUP BY dept.dept_name ORDER BY net_profit DESC
            """));
    }
}
