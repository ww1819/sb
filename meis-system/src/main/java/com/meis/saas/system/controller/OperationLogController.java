package com.meis.saas.system.controller;

import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/logs")
@RequiredArgsConstructor
public class OperationLogController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "sys_operation_log", null));
        List<Object> args = new ArrayList<>();
        if (module != null && !module.isBlank()) {
            where.append(" AND module_name = ? ");
            args.add(module);
        }
        if (userId != null && !userId.isBlank()) {
            where.append(" AND user_id = ?::uuid ");
            args.add(userId);
        }
        if (startDate != null && !startDate.isBlank()) {
            where.append(" AND created_at >= ?::timestamptz ");
            args.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            where.append(" AND created_at <= ?::timestamptz ");
            args.add(endDate);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM sys_operation_log" + where + " ORDER BY created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM sys_operation_log WHERE id = ? "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_operation_log", null),
                id);
        if (rows.isEmpty()) return Result.fail("not found");
        return Result.ok(rows.get(0));
    }
}
