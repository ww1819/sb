package com.meis.saas.common.web;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.common.excel.ExcelExportHelper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

/**
 * 通用表 CRUD（租户 Schema 内），供各业务微服务快速暴露 API。
 */
public abstract class GenericTableController {
    protected abstract JdbcTemplate jdbc();
    protected abstract Set<String> tables();

    @GetMapping("/tables")
    public Result<List<String>> tableList() {
        return Result.ok(new ArrayList<>(tables()));
    }

    @GetMapping("/{table}/page")
    public Result<com.meis.saas.common.page.PageResult<Map<String, Object>>> page(
            @PathVariable String table, com.meis.saas.common.page.PageQuery query) {
        check(table);
        return Result.ok(com.meis.saas.common.page.PageableJdbc.query(jdbc(), table, query));
    }

    @GetMapping("/{table}/list")
    public Result<List<Map<String, Object>>> list(@PathVariable String table,
                                                   @RequestParam(defaultValue = "50") int limit) {
        check(table);
        return Result.ok(jdbc().queryForList("SELECT * FROM " + table + " LIMIT " + Math.min(limit, 500)));
    }

    @GetMapping("/{table}/{id}")
    public Result<Map<String, Object>> get(@PathVariable String table, @PathVariable String id) {
        check(table);
        List<Map<String, Object>> rows = jdbc().queryForList("SELECT * FROM " + table + " WHERE id = ?::uuid", id);
        return Result.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/{table}")
    public Result<Map<String, Object>> create(@PathVariable String table, @RequestBody Map<String, Object> body) {
        check(table);
        if (!body.containsKey("id")) body.put("id", UUID.randomUUID().toString());
        String cols = String.join(",", body.keySet());
        String vals = String.join(",", body.keySet().stream().map(k -> "?").toList());
        jdbc().update("INSERT INTO " + table + " (" + cols + ") VALUES (" + vals + ")", body.values().toArray());
        return Result.ok(body);
    }

    @PutMapping("/{table}/{id}")
    public Result<Void> update(@PathVariable String table, @PathVariable String id, @RequestBody Map<String, Object> body) {
        check(table);
        body.remove("id");
        if (body.isEmpty()) return Result.ok();
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        body.forEach((k, v) -> { sets.add(k + " = ?"); args.add(v); });
        args.add(id);
        jdbc().update("UPDATE " + table + " SET " + String.join(",", sets) + " WHERE id = ?::uuid", args.toArray());
        return Result.ok();
    }

    @GetMapping("/{table}/export")
    public void export(@PathVariable String table, HttpServletResponse resp) throws IOException {
        check(table);
        ExcelExportHelper.exportCsv(jdbc(), table, resp);
    }

    @DeleteMapping("/{table}/{id}")
    public Result<Void> delete(@PathVariable String table, @PathVariable String id) {
        check(table);
        jdbc().update("DELETE FROM " + table + " WHERE id = ?::uuid", id);
        return Result.ok();
    }

    private void check(String table) {
        if (!tables().contains(table)) throw new BizException(400, "table not allowed: " + table);
        if ("public".equals(TenantContext.getSchemaName())) throw new BizException(403, "tenant context required");
    }
}
