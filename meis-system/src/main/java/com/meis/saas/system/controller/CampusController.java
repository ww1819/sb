package com.meis.saas.system.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.cache.CacheKeys;
import com.meis.saas.common.cache.MeisCacheEviction;
import com.meis.saas.common.cache.MeisCacheProperties;
import com.meis.saas.common.cache.RedisJsonCache;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/campuses")
@RequiredArgsConstructor
public class CampusController {
    private final JdbcTemplate jdbc;
    private final RedisJsonCache cache;
    private final MeisCacheProperties cacheProps;
    private final MeisCacheEviction cacheEviction;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        String schema = schema();
        return Result.ok(cache.getOrLoad(
                CacheKeys.campuses(schema),
                cacheProps.getOrgTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                () -> jdbc.queryForList("SELECT * FROM campus ORDER BY campus_code")));
    }

    @PostMapping
    @OperationLog(module = "system", description = "创建院区")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO campus (id, campus_code, campus_name, address, contact_phone, is_active) VALUES (?::uuid,?,?,?,?,?)",
                id, body.get("campus_code"), body.get("campus_name"), body.get("address"),
                body.get("contact_phone"), body.getOrDefault("is_active", true));
        cacheEviction.evictSchemaOrg(schema());
        return Result.ok(jdbc.queryForList("SELECT * FROM campus WHERE id = ?::uuid", id).get(0));
    }

    @PutMapping("/{id}")
    @OperationLog(module = "system", description = "更新院区")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update(
                "UPDATE campus SET campus_code=?, campus_name=?, address=?, contact_phone=?, is_active=?, updated_at=NOW() WHERE id=?::uuid",
                body.get("campus_code"), body.get("campus_name"), body.get("address"),
                body.get("contact_phone"), body.getOrDefault("is_active", true), id);
        cacheEviction.evictSchemaOrg(schema());
        return Result.ok(jdbc.queryForList("SELECT * FROM campus WHERE id = ?::uuid", id).get(0));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "system", description = "删除院区")
    public Result<Void> delete(@PathVariable UUID id) {
        jdbc.update("UPDATE campus SET is_active = false, updated_at = NOW() WHERE id = ?::uuid", id);
        cacheEviction.evictSchemaOrg(schema());
        return Result.ok();
    }

    private String schema() {
        String s = TenantContext.getSchemaName();
        return s == null || s.isBlank() ? "public" : s;
    }
}
