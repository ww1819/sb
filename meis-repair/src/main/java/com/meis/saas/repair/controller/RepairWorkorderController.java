package com.meis.saas.repair.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/repair/workorder")
@RequiredArgsConstructor
public class RepairWorkorderController {
    private final JdbcTemplate jdbc;
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "reported", Set.of("dispatched"),
            "dispatched", Set.of("in_progress"),
            "in_progress", Set.of("completed"),
            "completed", Set.of("accepted"),
            "accepted", Set.of("closed")
    );

    @PostMapping
    @OperationLog(module = "repair", description = "创建报修工单")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String woNo = "WO" + System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO repair_workorder (id, wo_no, device_id, device_code, device_name, reporter_id, report_dept_id,
                report_method, report_time, fault_description, urgency_level, status)
            VALUES (?::uuid,?,?::uuid,?,?,?::uuid,?::uuid,?,?,?,?)
            """,
                id, woNo, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                body.get("reporter_id"), body.get("report_dept_id"),
                body.getOrDefault("report_method", "web"), Instant.now(),
                body.get("fault_description"), body.getOrDefault("urgency_level", "normal"), "reported");
        return Result.ok(jdbc.queryForList("SELECT * FROM repair_workorder WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/transition")
    @OperationLog(module = "repair", description = "工单状态流转")
    public Result<Map<String, Object>> transition(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String target = body.get("status");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM repair_workorder WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "workorder not found");
        String current = rows.get(0).get("status").toString();
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BizException(400, "invalid transition: " + current + " -> " + target);
        }
        jdbc.update("UPDATE repair_workorder SET status = ?, updated_at = NOW() WHERE id = ?::uuid", target, id);
        return Result.ok(jdbc.queryForList("SELECT * FROM repair_workorder WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/dispatch")
    @OperationLog(module = "repair", description = "派工")
    public Result<Map<String, Object>> dispatch(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE repair_workorder SET assigned_engineer_id = ?::uuid, assigned_at = NOW(), status = 'dispatched', updated_at = NOW() WHERE id = ?::uuid",
                body.get("engineerId"), id);
        return Result.ok(jdbc.queryForList("SELECT * FROM repair_workorder WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/accept")
    @OperationLog(module = "repair", description = "工程师接单")
    public Result<Map<String, Object>> accept(@PathVariable UUID id) {
        return transition(id, Map.of("status", "in_progress"));
    }

    @PostMapping("/{id}/complete")
    @Transactional
    @OperationLog(module = "repair", description = "维修完成")
    public Result<Map<String, Object>> complete(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("""
            UPDATE repair_workorder SET solution_description=?, parts_cost=?, labor_cost=?, total_cost=?,
            repair_end_time=NOW(), status='completed', updated_at=NOW() WHERE id=?::uuid
            """,
                body.get("solution_description"), body.getOrDefault("parts_cost", 0),
                body.getOrDefault("labor_cost", 0), body.getOrDefault("total_cost", 0), id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) body.getOrDefault("spareParts", List.of());
        for (Map<String, Object> p : parts) {
            jdbc.update("INSERT INTO spare_part_usage (id, workorder_id, part_id, quantity, unit_price) VALUES (?::uuid,?::uuid,?::uuid,?,?)",
                    UUID.randomUUID(), id, p.get("part_id"), p.get("quantity"), p.get("unit_price"));
            jdbc.update("INSERT INTO spare_part_transaction (spare_part_id, txn_type, quantity, workorder_id) VALUES (?::uuid,'out',?,?::uuid)",
                    p.get("spare_part_id"), p.get("quantity"), id);
        }
        return Result.ok(jdbc.queryForList("SELECT * FROM repair_workorder WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/verify")
    @OperationLog(module = "repair", description = "验收评价")
    public Result<Map<String, Object>> verify(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("""
            UPDATE repair_workorder SET verifier_id=?::uuid, verify_time=NOW(), verify_result=?, verify_comment=?,
            satisfaction_rating=?, satisfaction_comment=?, status='accepted', updated_at=NOW() WHERE id=?::uuid
            """,
                body.get("verifier_id"), body.getOrDefault("verify_result", "pass"), body.get("verify_comment"),
                body.get("satisfaction_rating"), body.get("satisfaction_comment"), id);
        return transition(id, Map.of("status", "closed"));
    }
}
