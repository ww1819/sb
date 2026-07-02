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
@RequestMapping("/api/asset/transfer")
@RequiredArgsConstructor
public class AssetTransferController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @PostMapping
    @OperationLog(module = "asset", description = "资产流转申请")
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO asset_transfer (id, transfer_no, transfer_type, device_id, from_dept_id, to_dept_id, applicant_id, reason, status, approval_status)
            VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?::uuid,?,?,?)
            """, id, body.getOrDefault("transfer_no", "TR" + System.currentTimeMillis()), body.get("transfer_type"),
                body.get("device_id"), body.get("from_dept_id"), body.get("to_dept_id"), body.get("applicant_id"),
                body.get("reason"), "pending", "draft");
        return Result.ok(jdbc.queryForList("SELECT * FROM asset_transfer WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "asset", description = "提交流转审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var row = jdbc.queryForList("SELECT * FROM asset_transfer WHERE id = ?::uuid", id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        approvalService.submit("asset_transfer", id, row.get(0).get("transfer_no").toString(),
                "资产流转 " + row.get(0).get("transfer_no"), UUID.fromString(body.get("applicantId").toString()), 0);
        return Result.ok(row.get(0));
    }

    @PostMapping("/{id}/execute")
    @OperationLog(module = "asset", description = "执行流转")
    public Result<Map<String, Object>> execute(@PathVariable UUID id) {
        var row = jdbc.queryForList("SELECT * FROM asset_transfer WHERE id = ?::uuid", id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> t = row.get(0);
        jdbc.update("UPDATE medical_device SET dept_id = ?::uuid, updated_at = NOW() WHERE id = ?::uuid",
                t.get("to_dept_id"), t.get("device_id"));
        jdbc.update("UPDATE asset_transfer SET status = 'completed', updated_at = NOW() WHERE id = ?::uuid", id);
        return Result.ok(t);
    }
}
