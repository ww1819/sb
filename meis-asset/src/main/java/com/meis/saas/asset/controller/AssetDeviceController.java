package com.meis.saas.asset.controller;

import com.meis.saas.common.code.DeviceCodeGenerator;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/asset/device")
@RequiredArgsConstructor
public class AssetDeviceController {
    private final JdbcTemplate jdbc;
    private final DeviceCodeGenerator codeGenerator;

    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> detail(@PathVariable UUID id) {
        var device = jdbc.queryForList("SELECT * FROM medical_device WHERE id = ?::uuid", id);
        if (device.isEmpty()) return Result.ok(null);
        Map<String, Object> d = device.get(0);
        d.put("repairs", jdbc.queryForList("SELECT * FROM repair_workorder WHERE device_id = ?::uuid ORDER BY created_at DESC LIMIT 20", id));
        d.put("maintenance", jdbc.queryForList("SELECT * FROM maintenance_record WHERE device_id = ?::uuid ORDER BY created_at DESC LIMIT 20", id));
        d.put("transfers", jdbc.queryForList("SELECT * FROM asset_transfer WHERE device_id = ?::uuid ORDER BY created_at DESC LIMIT 10", id));
        d.put("qc", jdbc.queryForList("""
            SELECT 'risk' AS type, id, created_at FROM risk_assessment WHERE device_id = ?::uuid
            UNION ALL SELECT 'metrology', id, created_at FROM metrology_record WHERE device_id = ?::uuid
            UNION ALL SELECT 'performance', id, created_at FROM performance_test WHERE device_id = ?::uuid
            ORDER BY created_at DESC LIMIT 20
            """, id, id, id));
        d.put("benefit", jdbc.queryForList("SELECT * FROM device_benefit_summary WHERE device_id = ?::uuid ORDER BY summary_year DESC LIMIT 12", id));
        d.put("logs", jdbc.queryForList("SELECT * FROM sys_operation_log WHERE request_params::text LIKE ? ORDER BY created_at DESC LIMIT 20", "%" + id + "%"));
        return Result.ok(d);
    }

    @PostMapping("/generate-code")
    public Result<Map<String, String>> generateCode(@RequestBody Map<String, String> body) {
        String code = codeGenerator.generate(
                body.get("campusCode"), body.get("buildingCode"), body.get("deptCode"),
                body.get("countryCode"), body.get("categoryCode"));
        return Result.ok(Map.of("deviceCode", code));
    }
}
