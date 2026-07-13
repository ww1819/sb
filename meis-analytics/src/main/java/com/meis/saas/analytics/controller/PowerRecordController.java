package com.meis.saas.analytics.controller;

import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/power/record")
@RequiredArgsConstructor
public class PowerRecordController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) LocalDate recordDateFrom,
            @RequestParam(required = false) LocalDate recordDateTo) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (deviceCode != null && !deviceCode.isBlank()) {
            where.append(" AND r.device_code ILIKE ? ");
            args.add("%" + deviceCode.trim() + "%");
        }
        if (recordDateFrom != null) {
            where.append(" AND r.record_date >= ? ");
            args.add(recordDateFrom);
        }
        if (recordDateTo != null) {
            where.append(" AND r.record_date <= ? ");
            args.add(recordDateTo);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (r.device_name ILIKE ? OR r.device_code ILIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM power_monitor_record r" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT r.*, dept.dept_name, t.tag_code
                FROM power_monitor_record r
                LEFT JOIN medical_device d ON d.id = r.device_id
                LEFT JOIN department dept ON dept.id = d.dept_id
                LEFT JOIN power_tag t ON t.id = r.tag_id
                """ + where + " ORDER BY r.record_date DESC, r.device_code LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }
}
