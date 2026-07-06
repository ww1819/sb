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
@RequestMapping("/api/system/departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final JdbcTemplate jdbc;
    private final RedisJsonCache cache;
    private final MeisCacheProperties cacheProps;
    private final MeisCacheEviction cacheEviction;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        String schema = schema();
        return Result.ok(cache.getOrLoad(
                CacheKeys.deptList(schema),
                cacheProps.getOrgTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                () -> jdbc.queryForList(
                        "SELECT d.*, c.campus_name FROM department d LEFT JOIN campus c ON d.campus_id = c.id ORDER BY d.sort_order, d.dept_code")));
    }

    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree() {
        String schema = schema();
        return Result.ok(cache.getOrLoad(
                CacheKeys.deptTree(schema),
                cacheProps.getOrgTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                this::loadDeptTree));
    }

    private List<Map<String, Object>> loadDeptTree() {
        List<Map<String, Object>> all = jdbc.queryForList(
                "SELECT id, dept_code, dept_name, parent_id, campus_id, is_clinical, sort_order, is_active FROM department ORDER BY sort_order, dept_code");
        return buildTree(all, null);
    }

    @PostMapping
    @OperationLog(module = "system", description = "创建科室")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO department (id, dept_code, dept_name, parent_id, campus_id, floor_number, room_number, is_clinical, sort_order, is_active) VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?,?,?,?)",
                id, body.get("dept_code"), body.get("dept_name"), body.get("parent_id"),
                body.get("campus_id"), body.get("floor_number"), body.get("room_number"),
                body.getOrDefault("is_clinical", false), body.getOrDefault("sort_order", 0),
                body.getOrDefault("is_active", true));
        cacheEviction.evictSchemaOrg(schema());
        return Result.ok(jdbc.queryForList("SELECT * FROM department WHERE id = ?::uuid", id).get(0));
    }

    @PutMapping("/{id}")
    @OperationLog(module = "system", description = "更新科室")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update(
                "UPDATE department SET dept_code=?, dept_name=?, parent_id=?::uuid, campus_id=?::uuid, floor_number=?, room_number=?, is_clinical=?, sort_order=?, is_active=?, updated_at=NOW() WHERE id=?::uuid",
                body.get("dept_code"), body.get("dept_name"), body.get("parent_id"),
                body.get("campus_id"), body.get("floor_number"), body.get("room_number"),
                body.getOrDefault("is_clinical", false), body.getOrDefault("sort_order", 0),
                body.getOrDefault("is_active", true), id);
        cacheEviction.evictSchemaOrg(schema());
        return Result.ok(jdbc.queryForList("SELECT * FROM department WHERE id = ?::uuid", id).get(0));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "system", description = "删除科室")
    public Result<Void> delete(@PathVariable UUID id) {
        jdbc.update("UPDATE department SET is_active = false, updated_at = NOW() WHERE id = ?::uuid", id);
        cacheEviction.evictSchemaOrg(schema());
        return Result.ok();
    }

    private String schema() {
        String s = TenantContext.getSchemaName();
        return s == null || s.isBlank() ? "public" : s;
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> all, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> d : all) {
            Object pid = d.get("parent_id");
            String p = pid == null ? null : pid.toString();
            if (Objects.equals(parentId, p) || (parentId == null && pid == null)) {
                Map<String, Object> node = new LinkedHashMap<>(d);
                node.put("children", buildTree(all, d.get("id").toString()));
                result.add(node);
            }
        }
        return result;
    }
}
