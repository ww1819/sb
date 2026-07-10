package com.meis.saas.repair.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/repair/workorder")
@RequiredArgsConstructor
public class RepairWorkorderController {
    private static final String UUID_PATH =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final JdbcTemplate jdbc;
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "reported", Set.of("dispatched"),
            "dispatched", Set.of("in_progress"),
            "in_progress", Set.of("completed"),
            "completed", Set.of("accepted"),
            "accepted", Set.of("closed")
    );

    @GetMapping("/devices/candidates")
    public Result<List<Map<String, Object>>> deviceCandidates(
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) String specification,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) String financialCode,
            @RequestParam(required = false) String serialNumber) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.device_code, d.device_name, d.specification, d.serial_number,
                       d.financial_code, d.dept_id, d.device_status, dept.dept_name
                FROM medical_device d
                LEFT JOIN department dept ON dept.id = d.dept_id
                WHERE d.is_active = true
                  AND COALESCE(d.device_status, '') NOT IN ('maintenance', 'scrap')
                  AND d.id NOT IN (
                      SELECT device_id FROM repair_workorder
                      WHERE device_id IS NOT NULL
                        AND status IN ('reported', 'dispatched', 'in_progress')
                  )
                """);
        List<Object> args = new ArrayList<>();
        if (deptName != null && !deptName.isBlank()) {
            sql.append(" AND dept.dept_name ILIKE ? ");
            args.add("%" + deptName.trim() + "%");
        }
        if (deviceName != null && !deviceName.isBlank()) {
            sql.append(" AND d.device_name ILIKE ? ");
            args.add("%" + deviceName.trim() + "%");
        }
        if (specification != null && !specification.isBlank()) {
            sql.append(" AND d.specification ILIKE ? ");
            args.add("%" + specification.trim() + "%");
        }
        if (deviceCode != null && !deviceCode.isBlank()) {
            sql.append(" AND d.device_code ILIKE ? ");
            args.add("%" + deviceCode.trim() + "%");
        }
        if (financialCode != null && !financialCode.isBlank()) {
            sql.append(" AND d.financial_code ILIKE ? ");
            args.add("%" + financialCode.trim() + "%");
        }
        if (serialNumber != null && !serialNumber.isBlank()) {
            sql.append(" AND d.serial_number ILIKE ? ");
            args.add("%" + serialNumber.trim() + "%");
        }
        sql.append(" ORDER BY d.device_code LIMIT 500");
        return Result.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @GetMapping("/{id:" + UUID_PATH + "}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM repair_workorder WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "workorder not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @OperationLog(module = "repair", description = "创建报修工单")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String woNo = "WO" + System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO repair_workorder (id, wo_no, device_id, device_code, device_name, reporter_id, report_dept_id,
                report_method, report_time, fault_description, urgency_level, status)
            VALUES (?::uuid,?,?::uuid,?,?,?::uuid,?::uuid,?,?::timestamptz,?,?,?)
            """,
                id, woNo, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                body.get("reporter_id"), body.get("report_dept_id"),
                body.getOrDefault("report_method", "web"), body.get("report_time"),
                body.get("fault_description"), body.getOrDefault("urgency_level", "normal"), "reported");
        if (body.get("device_id") != null) {
            jdbc.update("UPDATE medical_device SET device_status = 'maintenance', updated_at = NOW() WHERE id = ?::uuid",
                    body.get("device_id"));
        }
        return Result.ok(jdbc.queryForList("SELECT * FROM repair_workorder WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/transition")
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

    @PostMapping("/{id:" + UUID_PATH + "}/dispatch")
    @OperationLog(module = "repair", description = "派工")
    public Result<Map<String, Object>> dispatch(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE repair_workorder SET assigned_engineer_id = ?::uuid, assigned_at = NOW(), status = 'dispatched', updated_at = NOW() WHERE id = ?::uuid",
                body.get("engineerId"), id);
        return Result.ok(jdbc.queryForList("SELECT * FROM repair_workorder WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/accept")
    @OperationLog(module = "repair", description = "工程师接单")
    public Result<Map<String, Object>> accept(@PathVariable UUID id) {
        return transition(id, Map.of("status", "in_progress"));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/complete")
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

    @PostMapping("/{id:" + UUID_PATH + "}/verify")
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
