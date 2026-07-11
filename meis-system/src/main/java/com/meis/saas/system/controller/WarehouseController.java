package com.meis.saas.system.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/warehouses")
@RequiredArgsConstructor
public class WarehouseController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(jdbc.queryForList(
                "SELECT w.*, c.campus_name, d.dept_name FROM warehouse w "
                        + "LEFT JOIN campus c ON w.campus_id = c.id "
                        + "LEFT JOIN department d ON w.dept_id = d.id WHERE 1=1 "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w")
                        + " ORDER BY w.sort_order, w.warehouse_code"));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM warehouse WHERE id = ?::uuid " + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", null),
                id);
        if (rows.isEmpty()) throw new BizException(404, "warehouse not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @OperationLog(module = "system", description = "创建库房")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        var softDeletedId = SoftDeleteSupport.prepareCreate(jdbc, "warehouse", body);
        if (softDeletedId.isPresent()) {
            UUID existingId = UUID.fromString(softDeletedId.get());
            jdbc.update("""
                    UPDATE warehouse SET warehouse_code=?, warehouse_name=?, campus_id=?::uuid, dept_id=?::uuid,
                    address=?, manager_id=?::uuid, is_active=?, sort_order=?,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, body.get("warehouse_code"), body.get("warehouse_name"), body.get("campus_id"),
                    body.get("dept_id"), body.get("address"), body.get("manager_id"),
                    body.getOrDefault("is_active", true), body.getOrDefault("sort_order", 0),
                    SoftDeleteSupport.currentUserId(), existingId);
            return get(existingId);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouse (id, warehouse_code, warehouse_name, campus_id, dept_id, address, manager_id,
                is_active, sort_order, created_by, is_deleted)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?::uuid,?,?,?::uuid,?)
                """, id, body.get("warehouse_code"), body.get("warehouse_name"), body.get("campus_id"),
                body.get("dept_id"), body.get("address"), body.get("manager_id"),
                body.getOrDefault("is_active", true), body.getOrDefault("sort_order", 0),
                SoftDeleteSupport.currentUserId(), 0);
        return get(id);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "system", description = "更新库房")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("""
                UPDATE warehouse SET warehouse_code=?, warehouse_name=?, campus_id=?::uuid, dept_id=?::uuid,
                address=?, manager_id=?::uuid, is_active=?, sort_order=?, updated_at=NOW(), updated_by=?::uuid
                WHERE id=?::uuid
                """, body.get("warehouse_code"), body.get("warehouse_name"), body.get("campus_id"),
                body.get("dept_id"), body.get("address"), body.get("manager_id"),
                body.getOrDefault("is_active", true), body.getOrDefault("sort_order", 0),
                SoftDeleteSupport.currentUserId(), id);
        return get(id);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "system", description = "删除库房")
    public Result<Void> delete(@PathVariable UUID id) {
        SoftDeleteSupport.softDelete(jdbc, "warehouse", id.toString());
        return Result.ok();
    }
}
