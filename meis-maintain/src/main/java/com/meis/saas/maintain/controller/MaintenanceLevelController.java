package com.meis.saas.maintain.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/maintain/level")
@RequiredArgsConstructor
public class MaintenanceLevelController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM maintenance_level WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_level", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "maintain", description = "保存保养级别")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        boolean hasId = body.containsKey("id") && body.get("id") != null;
        UUID id = hasId ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = hasId && !jdbc.queryForList(
                "SELECT 1 FROM maintenance_level WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_level", null), id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE maintenance_level SET level_code=?, level_name=?, sort_order=?, description=?, is_active=?,
                updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid
                """, body.get("level_code"), body.get("level_name"), body.getOrDefault("sort_order", 0),
                    body.get("description"), body.getOrDefault("is_active", true),
                    SoftDeleteSupport.currentUserId(), id);
            return get(id);
        }
        SoftDeleteSupport.applyInsertAudit(jdbc, "maintenance_level", body);
        var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "maintenance_level", body);
        if (softDeletedId.isPresent()) {
            id = UUID.fromString(softDeletedId.get());
            jdbc.update("""
                UPDATE maintenance_level SET level_code=?, level_name=?, sort_order=?, description=?, is_active=?,
                is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                WHERE id=?::uuid
                """, body.get("level_code"), body.get("level_name"), body.getOrDefault("sort_order", 0),
                    body.get("description"), body.getOrDefault("is_active", true),
                    SoftDeleteSupport.currentUserId(), id);
            return get(id);
        }
        jdbc.update("""
            INSERT INTO maintenance_level (id, level_code, level_name, sort_order, description, is_active, created_by, is_deleted)
            VALUES (?::uuid,?,?,?,?,?,?::uuid,?)
            """, id, body.get("level_code"), body.get("level_name"), body.getOrDefault("sort_order", 0),
                body.get("description"), body.getOrDefault("is_active", true),
                SoftDeleteSupport.currentUserId(), 0);
        return get(id);
    }
}
