package com.meis.saas.asset.controller;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/asset/inspection")
@RequiredArgsConstructor
public class InspectionController {
    private final JdbcTemplate jdbc;

    @GetMapping("/plans")
    public Result<List<Map<String, Object>>> plans(@RequestParam(defaultValue = "pending") String status) {
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM inspection_plan WHERE status = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan", null)
                        + " ORDER BY plan_date LIMIT 100", status));
    }

    @PostMapping("/records")
    public Result<Map<String, Object>> createRecord(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        Object deviceId = body.get("deviceId") != null ? body.get("deviceId") : body.get("device_id");
        String deviceCode = body.get("device_code") != null ? String.valueOf(body.get("device_code")) : null;
        String deviceName = body.get("device_name") != null ? String.valueOf(body.get("device_name")) : null;
        if (deviceId != null && (deviceCode == null || deviceCode.isBlank() || deviceName == null || deviceName.isBlank())) {
            var d = jdbc.queryForList(
                    "SELECT device_code, device_name FROM medical_device WHERE id = ?::uuid"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null),
                    deviceId);
            if (!d.isEmpty()) {
                if (deviceCode == null || deviceCode.isBlank()) {
                    deviceCode = d.get(0).get("device_code") != null ? String.valueOf(d.get(0).get("device_code")) : null;
                }
                if (deviceName == null || deviceName.isBlank()) {
                    deviceName = d.get(0).get("device_name") != null ? String.valueOf(d.get(0).get("device_name")) : null;
                }
            }
        }
        jdbc.update("""
                INSERT INTO inspection_record (id, plan_id, device_id, device_code, device_name, inspector_id, result, remark, status)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,
                id, body.get("planId"), deviceId, deviceCode, deviceName, body.get("inspectorId"),
                body.get("result"), body.get("remark"), "completed");
        if (body.get("planId") != null) {
            jdbc.update("UPDATE inspection_plan SET status = 'completed' WHERE id = ?::uuid", body.get("planId"));
        }
        return Result.ok(Map.of("id", id.toString()));
    }
}
