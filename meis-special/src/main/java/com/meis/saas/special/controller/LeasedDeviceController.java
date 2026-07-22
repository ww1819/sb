package com.meis.saas.special.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/special/leased")
@RequiredArgsConstructor
public class LeasedDeviceController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean expiringOnly) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (l.device_code ILIKE ? OR l.device_name ILIKE ? OR l.contract_no ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        FilterCsvSupport.appendStrIn(where, args, "l.status", status);
        if (Boolean.TRUE.equals(expiringOnly)) {
            where.append(" AND l.lease_end_date IS NOT NULL AND l.lease_end_date <= CURRENT_DATE + INTERVAL '30 days' ");
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM leased_device l" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT l.*, s.supplier_name AS lessor_name, dept.dept_name
            FROM leased_device l
            LEFT JOIN supplier s ON s.id = l.lessor_id
            LEFT JOIN medical_device d ON d.id = l.device_id
            LEFT JOIN department dept ON dept.id = d.dept_id
            """ + where + " ORDER BY l.lease_end_date NULLS LAST, l.device_code LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }

    @PostMapping("/{id}/renew")
    @OperationLog(module = "special", description = "租赁续租")
    public Result<Map<String, Object>> renew(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("""
            UPDATE leased_device SET lease_end_date=?, monthly_rent=?, status='active', updated_at=NOW()
            WHERE id=?::uuid
            """, body.get("lease_end_date"), body.get("monthly_rent"), id);
        return Result.ok(jdbc.queryForList("SELECT * FROM leased_device WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/return")
    @OperationLog(module = "special", description = "租赁退租")
    public Result<Map<String, Object>> returnDevice(@PathVariable UUID id) {
        jdbc.update("UPDATE leased_device SET status = 'returned', updated_at = NOW() WHERE id = ?::uuid", id);
        return Result.ok(jdbc.queryForList("SELECT * FROM leased_device WHERE id = ?::uuid", id).get(0));
    }
}
