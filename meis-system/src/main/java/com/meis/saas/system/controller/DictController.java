package com.meis.saas.system.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.cache.CacheKeys;
import com.meis.saas.common.cache.MeisCacheEviction;
import com.meis.saas.common.cache.MeisCacheProperties;
import com.meis.saas.common.cache.RedisJsonCache;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class DictController {
    private final JdbcTemplate jdbc;
    private final RedisJsonCache cache;
    private final MeisCacheProperties cacheProps;
    private final MeisCacheEviction cacheEviction;

    @GetMapping("/types")
    public Result<List<Map<String, Object>>> types() {
        String schema = schema();
        return Result.ok(cache.getOrLoad(
                CacheKeys.dictTypes(schema),
                cacheProps.getDictTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                () -> jdbc.queryForList(
                        "SELECT dict_type, COUNT(*) as item_count FROM sys_dict WHERE 1=1 "
                                + SoftDeleteSupport.notDeletedClause(jdbc, "sys_dict", null)
                                + " GROUP BY dict_type ORDER BY dict_type")));
    }

    @GetMapping("/type/{dictType}")
    public Result<List<Map<String, Object>>> byType(@PathVariable String dictType) {
        String schema = schema();
        return Result.ok(cache.getOrLoad(
                CacheKeys.dictByType(schema, dictType),
                cacheProps.getDictTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                () -> jdbc.queryForList(
                        "SELECT * FROM sys_dict WHERE dict_type = ? " + SoftDeleteSupport.notDeletedClause(jdbc, "sys_dict", null)
                                + " ORDER BY sort_order, dict_code", dictType)));
    }

    @PostMapping
    @OperationLog(module = "system", description = "创建字典项")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        SoftDeleteSupport.applyInsertAudit(jdbc, "sys_dict", body);
        var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "sys_dict", body);
        String dictType = String.valueOf(body.get("dict_type"));
        if (softDeletedId.isPresent()) {
            UUID existingId = UUID.fromString(softDeletedId.get());
            jdbc.update("""
                    UPDATE sys_dict SET dict_type=?, dict_code=?, dict_label=?, dict_value=?, sort_order=?, is_active=?, remark=?,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, body.get("dict_type"), body.get("dict_code"), body.get("dict_label"),
                    body.get("dict_value"), body.getOrDefault("sort_order", 0),
                    body.getOrDefault("is_active", true), body.get("remark"),
                    TenantContext.getUserId(), existingId);
            cacheEviction.evictDictType(schema(), dictType);
            return Result.ok(jdbc.queryForList("SELECT * FROM sys_dict WHERE id = ?::uuid", existingId).get(0));
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_value, sort_order, is_active, remark, created_by, is_deleted) VALUES (?::uuid,?,?,?,?,?,?,?,?::uuid,?)",
                id, body.get("dict_type"), body.get("dict_code"), body.get("dict_label"),
                body.get("dict_value"), body.getOrDefault("sort_order", 0),
                body.getOrDefault("is_active", true), body.get("remark"),
                SoftDeleteSupport.currentUserId(), 0);
        cacheEviction.evictDictType(schema(), dictType);
        return Result.ok(jdbc.queryForList("SELECT * FROM sys_dict WHERE id = ?::uuid", id).get(0));
    }

    @PutMapping("/{id}")
    @OperationLog(module = "system", description = "更新字典项")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update(
                "UPDATE sys_dict SET dict_type=?, dict_code=?, dict_label=?, dict_value=?, sort_order=?, is_active=?, remark=?, updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid",
                body.get("dict_type"), body.get("dict_code"), body.get("dict_label"),
                body.get("dict_value"), body.getOrDefault("sort_order", 0),
                body.getOrDefault("is_active", true), body.get("remark"),
                SoftDeleteSupport.currentUserId(), id);
        cacheEviction.evictDictType(schema(), String.valueOf(body.get("dict_type")));
        return Result.ok(jdbc.queryForList("SELECT * FROM sys_dict WHERE id = ?::uuid", id).get(0));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "system", description = "删除字典项")
    public Result<Void> delete(@PathVariable UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT dict_type FROM sys_dict WHERE id = ?::uuid", id);
        SoftDeleteSupport.softDelete(jdbc, "sys_dict", id.toString());
        if (!rows.isEmpty()) {
            cacheEviction.evictDictType(schema(), String.valueOf(rows.get(0).get("dict_type")));
        }
        return Result.ok();
    }

    private String schema() {
        String s = TenantContext.getSchemaName();
        return s == null || s.isBlank() ? "public" : s;
    }
}
