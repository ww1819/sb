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
@RequestMapping("/api/pm/type")
@RequiredArgsConstructor
public class PmTypeController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM pm_type WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "pm_type", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "pm", description = "保存PM类型")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        boolean hasId = body.containsKey("id") && body.get("id") != null;
        UUID id = hasId ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = hasId && !jdbc.queryForList(
                "SELECT 1 FROM pm_type WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "pm_type", null), id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE pm_type SET type_code=?, type_name=?, risk_level=?, sort_order=?, description=?, is_active=?,
                updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid
                """, body.get("type_code"), body.get("type_name"), body.getOrDefault("risk_level", "medium"),
                    body.getOrDefault("sort_order", 0), body.get("description"), body.getOrDefault("is_active", true),
                    SoftDeleteSupport.currentUserId(), id);
            return get(id);
        }
        SoftDeleteSupport.applyInsertAudit(jdbc, "pm_type", body);
        var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "pm_type", body);
        if (softDeletedId.isPresent()) {
            id = UUID.fromString(softDeletedId.get());
            jdbc.update("""
                UPDATE pm_type SET type_code=?, type_name=?, risk_level=?, sort_order=?, description=?, is_active=?,
                is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                WHERE id=?::uuid
                """, body.get("type_code"), body.get("type_name"), body.getOrDefault("risk_level", "medium"),
                    body.getOrDefault("sort_order", 0), body.get("description"), body.getOrDefault("is_active", true),
                    SoftDeleteSupport.currentUserId(), id);
            return get(id);
        }
        jdbc.update("""
            INSERT INTO pm_type (id, type_code, type_name, risk_level, sort_order, description, is_active, created_by, is_deleted)
            VALUES (?::uuid,?,?,?,?,?,?,?::uuid,?)
            """, id, body.get("type_code"), body.get("type_name"), body.getOrDefault("risk_level", "medium"),
                body.getOrDefault("sort_order", 0), body.get("description"), body.getOrDefault("is_active", true),
                SoftDeleteSupport.currentUserId(), 0);
        return get(id);
    }
}
