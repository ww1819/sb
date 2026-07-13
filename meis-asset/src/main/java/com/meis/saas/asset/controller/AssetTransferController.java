package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/asset/transfer")
@RequiredArgsConstructor
public class AssetTransferController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM asset_transfer WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存资产流转")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM asset_transfer WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE asset_transfer SET transfer_type=?, device_id=?::uuid, from_dept_id=?::uuid, to_dept_id=?::uuid,
                from_campus_id=?::uuid, to_campus_id=?::uuid, from_warehouse_id=?::uuid, to_warehouse_id=?::uuid,
                transfer_date=?, reason=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("transfer_type"), body.get("device_id"), body.get("from_dept_id"), body.get("to_dept_id"),
                    body.get("from_campus_id"), body.get("to_campus_id"), body.get("from_warehouse_id"),
                    body.get("to_warehouse_id"), body.get("transfer_date"),
                    body.get("reason"), body.get("remark"), id);
        } else {
            jdbc.update("""
                INSERT INTO asset_transfer (id, transfer_no, transfer_type, device_id, from_dept_id, to_dept_id,
                from_campus_id, to_campus_id, from_warehouse_id, to_warehouse_id, transfer_date, reason, applicant_id, status, approval_status)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?,?::uuid,?,?,?)
                """, id, body.getOrDefault("transfer_no", "TR" + System.currentTimeMillis()), body.get("transfer_type"),
                    body.get("device_id"), body.get("from_dept_id"), body.get("to_dept_id"),
                    body.get("from_campus_id"), body.get("to_campus_id"), body.get("from_warehouse_id"),
                    body.get("to_warehouse_id"), body.get("transfer_date"),
                    body.get("reason"), body.get("applicant_id"), "pending", "draft");
        }
        return get(id);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "asset", description = "提交流转审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var row = jdbc.queryForList("SELECT * FROM asset_transfer WHERE id = ?::uuid", id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        approvalService.submit("asset_transfer", id, row.get(0).get("transfer_no").toString(),
                "资产流转 " + row.get(0).get("transfer_no"), UUID.fromString(body.get("applicantId").toString()), 0);
        return get(id);
    }

    @PostMapping("/{id}/execute")
    @OperationLog(module = "asset", description = "执行流转")
    public Result<Map<String, Object>> execute(@PathVariable UUID id) {
        var row = jdbc.queryForList("SELECT * FROM asset_transfer WHERE id = ?::uuid", id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> t = row.get(0);
        if (t.get("to_dept_id") != null && t.get("device_id") != null) {
            jdbc.update("UPDATE medical_device SET dept_id = ?::uuid, updated_at = NOW() WHERE id = ?::uuid",
                    t.get("to_dept_id"), t.get("device_id"));
        }
        if (t.get("to_warehouse_id") != null && t.get("device_id") != null) {
            jdbc.update("UPDATE medical_device SET warehouse_id = ?::uuid, updated_at = NOW() WHERE id = ?::uuid",
                    t.get("to_warehouse_id"), t.get("device_id"));
        }
        jdbc.update("UPDATE asset_transfer SET status = 'completed', updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }
}
