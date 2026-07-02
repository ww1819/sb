package com.meis.saas.special.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/special/emergency")
@RequiredArgsConstructor
public class EmergencyPoolController {
    private final JdbcTemplate jdbc;

    @PostMapping("/allocate")
    @OperationLog(module = "special", description = "应急设备调配")
    public Result<Map<String, Object>> allocate(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO emergency_device_allocation (id, pool_id, device_id, request_dept_id, applicant_id, allocate_date, status, reason)
            VALUES (?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?,?,?)
            """, id, body.get("pool_id"), body.get("device_id"), body.get("request_dept_id"),
                body.get("applicant_id"), body.get("allocate_date"), "pending", body.get("reason"));
        return Result.ok(jdbc.queryForList("SELECT * FROM emergency_device_allocation WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/allocate/{id}/return")
    @OperationLog(module = "special", description = "应急设备归还")
    public Result<Map<String, Object>> returnDevice(@PathVariable UUID id) {
        jdbc.update("UPDATE emergency_device_allocation SET status = 'returned', return_date = CURRENT_DATE, updated_at = NOW() WHERE id = ?::uuid", id);
        return Result.ok(jdbc.queryForList("SELECT * FROM emergency_device_allocation WHERE id = ?::uuid", id).get(0));
    }
}
