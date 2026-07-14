package com.meis.saas.system.controller;

import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.cache.MeisCacheEviction;
import com.meis.saas.common.persistence.SoftDeleteSupport;
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
    private final MeisCacheEviction cacheEviction;
    private final EntityChangeLogService changeLog;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM campus WHERE 1=1 " + SoftDeleteSupport.notDeletedClause(jdbc, "campus", null) + " ORDER BY campus_code"));
    }

    @PostMapping
    @OperationLog(module = "system", description = "创建院区")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        SoftDeleteSupport.applyInsertAudit(jdbc, "campus", body);
        var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "campus", body);
        if (softDeletedId.isPresent()) {
            UUID existingId = UUID.fromString(softDeletedId.get());
            Map<String, Object> before = changeLog.loadRow("campus", existingId);
            jdbc.update("""
                    UPDATE campus SET campus_code=?, campus_name=?, address=?, contact_phone=?, is_active=?,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, body.get("campus_code"), body.get("campus_name"), body.get("address"),
                    body.get("contact_phone"), body.getOrDefault("is_active", true),
                    TenantContext.getUserId(), existingId);
            cacheEviction.evictSchemaOrg(schema());
            Map<String, Object> after = changeLog.loadRow("campus", existingId);
            changeLog.recordUpdate("campus", existingId, before, after);
            return Result.ok(jdbc.queryForList("SELECT * FROM campus WHERE id = ?::uuid", existingId).get(0));
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO campus (id, campus_code, campus_name, address, contact_phone, is_active, created_by, is_deleted) VALUES (?::uuid,?,?,?,?,?,?::uuid,?)",
                id, body.get("campus_code"), body.get("campus_name"), body.get("address"),
                body.get("contact_phone"), body.getOrDefault("is_active", true),
                SoftDeleteSupport.currentUserId(), 0);
        cacheEviction.evictSchemaOrg(schema());
        Map<String, Object> created = jdbc.queryForList("SELECT * FROM campus WHERE id = ?::uuid", id).get(0);
        changeLog.recordCreate("campus", id, created);
        return Result.ok(created);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "system", description = "更新院区")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> before = changeLog.loadRow("campus", id);
        jdbc.update(
                "UPDATE campus SET campus_code=?, campus_name=?, address=?, contact_phone=?, is_active=?, updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid",
                body.get("campus_code"), body.get("campus_name"), body.get("address"),
                body.get("contact_phone"), body.getOrDefault("is_active", true),
                SoftDeleteSupport.currentUserId(), id);
        cacheEviction.evictSchemaOrg(schema());
        Map<String, Object> after = changeLog.loadRow("campus", id);
        changeLog.recordUpdate("campus", id, before, after);
        return Result.ok(jdbc.queryForList("SELECT * FROM campus WHERE id = ?::uuid", id).get(0));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "system", description = "删除院区")
    public Result<Void> delete(@PathVariable UUID id) {
        Map<String, Object> before = changeLog.loadRow("campus", id);
        SoftDeleteSupport.softDelete(jdbc, "campus", id.toString());
        changeLog.recordDelete("campus", id, before);
        cacheEviction.evictSchemaOrg(schema());
        return Result.ok();
    }

    private String schema() {
        String s = TenantContext.getSchemaName();
        return s == null || s.isBlank() ? "public" : s;
    }
}
