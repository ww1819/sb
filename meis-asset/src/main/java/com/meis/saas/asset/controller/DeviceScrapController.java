package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/asset/scrap")
@RequiredArgsConstructor
public class DeviceScrapController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @PostMapping
    @OperationLog(module = "asset", description = "报废申请")
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO device_scrap (id, scrap_no, device_id, device_code, device_name, scrap_reason, applicant_id, application_date, status, approval_status)
            VALUES (?::uuid,?,?,?::uuid,?,?,?::uuid,?,?,?)
            """, id, body.getOrDefault("scrap_no", "SC" + System.currentTimeMillis()), body.get("device_id"),
                body.get("device_code"), body.get("device_name"), body.get("scrap_reason"),
                body.get("applicant_id"), body.get("application_date"), "pending", "draft");
        return Result.ok(jdbc.queryForList("SELECT * FROM device_scrap WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/evaluate")
    @OperationLog(module = "asset", description = "报废评估")
    public Result<Map<String, Object>> evaluate(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE device_scrap SET evaluator_id=?::uuid, evaluation_result=?, residual_value=?, updated_at=NOW() WHERE id=?::uuid",
                body.get("evaluator_id"), body.get("evaluation_result"), body.get("residual_value"), id);
        return Result.ok(jdbc.queryForList("SELECT * FROM device_scrap WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "asset", description = "提交报废审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var row = jdbc.queryForList("SELECT * FROM device_scrap WHERE id = ?::uuid", id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        approvalService.submit("device_scrap", id, row.get(0).get("scrap_no").toString(),
                "设备报废 " + row.get(0).get("scrap_no"), UUID.fromString(body.get("applicantId").toString()), 0);
        return Result.ok(row.get(0));
    }

    @PostMapping("/{id}/dispose")
    @OperationLog(module = "asset", description = "报废处置")
    public Result<Map<String, Object>> dispose(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var row = jdbc.queryForList("SELECT * FROM device_scrap WHERE id = ?::uuid", id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        jdbc.update("UPDATE medical_device SET device_status = 'scrap', updated_at = NOW() WHERE id = ?::uuid", row.get(0).get("device_id"));
        jdbc.update("UPDATE device_scrap SET disposal_method=?, disposal_date=?, status='disposed', updated_at=NOW() WHERE id=?::uuid",
                body.get("disposal_method"), body.get("disposal_date"), id);
        return Result.ok(row.get(0));
    }
}
