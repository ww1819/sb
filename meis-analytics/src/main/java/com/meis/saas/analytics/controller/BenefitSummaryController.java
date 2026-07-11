package com.meis.saas.analytics.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import com.meis.saas.analytics.service.BenefitSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/analytics/summary")
@RequiredArgsConstructor
public class BenefitSummaryController {
    private final JdbcTemplate jdbc;
    private final BenefitSummaryService summaryService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String benefitLevel) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (year != null) {
            where.append(" AND s.summary_year = ? ");
            args.add(year);
        }
        if (month != null) {
            where.append(" AND s.summary_month = ? ");
            args.add(month);
        }
        if (benefitLevel != null && !benefitLevel.isBlank()) {
            where.append(" AND s.benefit_level = ? ");
            args.add(benefitLevel);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (s.device_code ILIKE ? OR s.device_name ILIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM device_benefit_summary s" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT s.*, dept.dept_name, d.purchase_price, d.net_value
                FROM device_benefit_summary s
                LEFT JOIN medical_device d ON d.id = s.device_id
                LEFT JOIN department dept ON dept.id = d.dept_id
                """ + where + " ORDER BY s.summary_year DESC, s.summary_month DESC, s.net_profit DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend() {
        return Result.ok(jdbc.queryForList("""
                SELECT summary_year, summary_month,
                       SUM(total_revenue) AS revenue, SUM(total_cost) AS cost, SUM(net_profit) AS profit
                FROM device_benefit_summary
                GROUP BY summary_year, summary_month
                ORDER BY summary_year DESC, summary_month DESC LIMIT 24
                """));
    }

    @GetMapping("/dept-ranking")
    public Result<List<Map<String, Object>>> deptRanking(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (year != null) {
            where.append(" AND s.summary_year = ? ");
            args.add(year);
        }
        if (month != null) {
            where.append(" AND s.summary_month = ? ");
            args.add(month);
        }
        return Result.ok(jdbc.queryForList("""
                SELECT dept.dept_name,
                       COALESCE(SUM(s.net_profit), 0) AS net_profit,
                       COALESCE(AVG(s.profit_rate), 0) AS avg_profit_rate,
                       COALESCE(SUM(s.total_revenue), 0) AS total_revenue
                FROM department dept
                LEFT JOIN medical_device d ON d.dept_id = dept.id
                LEFT JOIN device_benefit_summary s ON s.device_id = d.id
                """ + where + " GROUP BY dept.dept_name ORDER BY net_profit DESC", args.toArray()));
    }

    @PostMapping("/recompute")
    @OperationLog(module = "analytics", description = "重算效益汇总")
    public Result<Map<String, Object>> recompute(@RequestBody Map<String, Object> body) {
        int year = Integer.parseInt(body.getOrDefault("year", java.time.LocalDate.now().getYear()).toString());
        int month = Integer.parseInt(body.getOrDefault("month", java.time.LocalDate.now().getMonthValue()).toString());
        int count = summaryService.recompute(year, month);
        return Result.ok(Map.of("year", year, "month", month, "deviceCount", count));
    }
}
