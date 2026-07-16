package com.meis.saas.maintain.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/maintain/record")
@RequiredArgsConstructor
public class MaintenanceRecordController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM maintenance_record WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_record", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @OperationLog(module = "maintain", description = "提交保养记录")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) throws Exception {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        String itemsJson = mapper.writeValueAsString(body.getOrDefault("items_result", List.of()));
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM maintenance_record WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_record", null), id).isEmpty();
        if (!exists) {
            jdbc.update("""
                INSERT INTO maintenance_record (id, record_no, plan_id, device_id, maintenance_level, template_id,
                    executor_id, execute_start_time, items_result, overall_result, signature_url, status)
                VALUES (?::uuid,?,?::uuid,?::uuid,?,?::uuid,?::uuid,?::timestamptz,?::jsonb,?,?,?)
                """, id, body.getOrDefault("record_no", "MR" + System.currentTimeMillis()), body.get("plan_id"),
                    body.get("device_id"), body.getOrDefault("maintenance_level", "L1"), body.get("template_id"),
                    body.get("executor_id"), Instant.now(), itemsJson, body.get("overall_result"),
                    body.get("signature_url"), body.getOrDefault("status", "completed"));
        } else {
            jdbc.update("UPDATE maintenance_record SET items_result=?::jsonb, overall_result=?, signature_url=?, status=?, updated_at=NOW() WHERE id=?::uuid",
                    itemsJson, body.get("overall_result"), body.get("signature_url"), body.get("status"), id);
        }
        if (body.get("plan_id") != null && "submitted".equals(body.get("status"))) {
            jdbc.update("UPDATE maintenance_plan SET last_maintained_at = CURRENT_DATE, status = 'active', updated_at = NOW() WHERE id = ?::uuid",
                    body.get("plan_id"));
        }
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM maintenance_record WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_record", null), id).get(0));
    }
}
