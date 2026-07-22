package com.meis.saas.special.controller;

import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/shared/record")
@RequiredArgsConstructor
public class SharedRecordController {
    private final JdbcTemplate jdbc;

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("device_count", jdbc.queryForObject(
                "SELECT COUNT(*) FROM medical_device WHERE is_shared_device = TRUE "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), Long.class));
        result.put("loan_total", jdbc.queryForObject("SELECT COUNT(*) FROM shared_device_loan", Long.class));
        result.put("on_loan_count", jdbc.queryForObject("SELECT COUNT(*) FROM shared_device_loan WHERE status = 'on_loan'", Long.class));
        result.put("pending_loan", jdbc.queryForObject("SELECT COUNT(*) FROM shared_device_loan WHERE status = 'pending'", Long.class));
        result.put("pending_return", jdbc.queryForObject("SELECT COUNT(*) FROM shared_device_return WHERE status = 'pending'", Long.class));
        result.put("unpaid_fee", jdbc.queryForObject("SELECT COUNT(*) FROM shared_device_fee WHERE paid_status = 'unpaid'", Long.class));
        result.put("fee_total", jdbc.queryForObject(
                "SELECT COALESCE(SUM(fee_amount),0) FROM shared_device_fee WHERE paid_status = 'paid'", java.math.BigDecimal.class));
        result.put("recent_loans", jdbc.queryForList("""
            SELECT l.id, l.loan_no, l.device_code, l.device_name, l.status, l.loan_start, l.loan_end,
                   fd.dept_name AS from_dept_name, td.dept_name AS to_dept_name
            FROM shared_device_loan l
            LEFT JOIN department fd ON fd.id = l.from_dept_id
            LEFT JOIN department td ON td.id = l.to_dept_id
            ORDER BY l.created_at DESC LIMIT 20
            """));
        return Result.ok(result);
    }

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID deviceId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (l.loan_no ILIKE ? OR l.device_name ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        FilterCsvSupport.appendStrIn(where, args, "l.status", status);
        if (deviceId != null) {
            where.append(" AND l.device_id = ?::uuid ");
            args.add(deviceId);
        }
        String from = """
            FROM shared_device_loan l
            LEFT JOIN department fd ON fd.id = l.from_dept_id
            LEFT JOIN department td ON td.id = l.to_dept_id
            """;
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT l.*, fd.dept_name AS from_dept_name, td.dept_name AS to_dept_name,
                   (SELECT COUNT(*) FROM shared_device_fee f WHERE f.loan_id = l.id) AS fee_count,
                   (SELECT COALESCE(SUM(f.fee_amount),0) FROM shared_device_fee f WHERE f.loan_id = l.id) AS fee_total
            """ + from + where + " ORDER BY l.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }
}
