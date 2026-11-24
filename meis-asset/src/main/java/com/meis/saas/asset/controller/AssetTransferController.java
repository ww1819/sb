package com.meis.saas.asset.controller;

import com.meis.saas.asset.service.DeviceOwnershipPeriodService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
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
    private final DeviceOwnershipPeriodService ownershipService;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM asset_transfer WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "asset_transfer", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存资产流转")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM asset_transfer WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "asset_transfer", null), id).isEmpty();

        Map<String, Object> deviceSnap = resolveDeviceSnap(body.get("device_id"), body.get("device_code"), body.get("device_name"));
        Object applicantId = body.get("applicant_id") != null ? body.get("applicant_id") : TenantContext.getUserId();
        String applicantName = SoftDeleteSupport.resolveUserDisplayName(jdbc, applicantId);

        if (exists) {
            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            sets.add("transfer_type=?");
            args.add(body.get("transfer_type"));
            sets.add("device_id=?::uuid");
            args.add(body.get("device_id"));
            sets.add("device_code=?");
            args.add(deviceSnap.get("device_code"));
            sets.add("device_name=?");
            args.add(deviceSnap.get("device_name"));
            sets.add("from_dept_id=?::uuid");
            args.add(body.get("from_dept_id"));
            sets.add("to_dept_id=?::uuid");
            args.add(body.get("to_dept_id"));
            sets.add("from_campus_id=?::uuid");
            args.add(body.get("from_campus_id"));
            sets.add("to_campus_id=?::uuid");
            args.add(body.get("to_campus_id"));
            sets.add("from_warehouse_id=?::uuid");
            args.add(body.get("from_warehouse_id"));
            sets.add("to_warehouse_id=?::uuid");
            args.add(body.get("to_warehouse_id"));
            sets.add("transfer_date=?");
            args.add(body.get("transfer_date"));
            sets.add("reason=?");
            args.add(body.get("reason"));
            sets.add("remark=?");
            args.add(body.get("remark"));
            if (applicantId != null) {
                sets.add("applicant_id=?::uuid");
                args.add(applicantId);
                sets.add("applicant_name=COALESCE(?, applicant_name)");
                args.add(applicantName);
            }
            SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, "asset_transfer"), sets, args);
            SoftDeleteSupport.appendUpdateChannelFromBody(jdbc, "asset_transfer", sets, args, body);
            args.add(id);
            jdbc.update("UPDATE asset_transfer SET " + String.join(", ", sets)
                    + " WHERE id=?::uuid"
                    + SoftDeleteSupport.notDeletedClause(jdbc, "asset_transfer", null), args.toArray());
        } else {
            Map<String, Object> audit = new LinkedHashMap<>(body);
            SoftDeleteSupport.applyInsertAudit(jdbc, "asset_transfer", audit);
            jdbc.update("""
                INSERT INTO asset_transfer (id, transfer_no, transfer_type, device_id, device_code, device_name,
                from_dept_id, to_dept_id, from_campus_id, to_campus_id, from_warehouse_id, to_warehouse_id,
                transfer_date, reason, applicant_id, applicant_name, status, approval_status,
                created_by, created_by_name, is_deleted)
                VALUES (?::uuid,?,?,?::uuid,?,?,?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?,?,?::uuid,?,?,?,?::uuid,?,0)
                """, id, body.getOrDefault("transfer_no", "TR" + System.currentTimeMillis()), body.get("transfer_type"),
                    body.get("device_id"), deviceSnap.get("device_code"), deviceSnap.get("device_name"),
                    body.get("from_dept_id"), body.get("to_dept_id"),
                    body.get("from_campus_id"), body.get("to_campus_id"), body.get("from_warehouse_id"),
                    body.get("to_warehouse_id"), body.get("transfer_date"),
                    body.get("reason"), applicantId, applicantName, "pending", "draft",
                    audit.get("created_by"), audit.get("created_by_name"));
            SoftDeleteSupport.applyChannels(jdbc, "asset_transfer", id, body, "create_channel", "update_channel");
        }
        return get(id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除资产流转")
    public Result<Void> delete(@PathVariable UUID id, @RequestParam(required = false) String client) {
        int n = SoftDeleteSupport.softDelete(jdbc, "asset_transfer", id.toString(), client);
        if (n == 0) throw new BizException(404, "not found");
        return Result.ok();
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "asset", description = "提交流转审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var row = jdbc.queryForList(
                "SELECT * FROM asset_transfer WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "asset_transfer", null), id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        approvalService.submit("asset_transfer", id, row.get(0).get("transfer_no").toString(),
                "资产流转 " + row.get(0).get("transfer_no"), UUID.fromString(body.get("applicantId").toString()), 0);
        return get(id);
    }

    @PostMapping("/{id}/execute")
    @OperationLog(module = "asset", description = "执行流转")
    public Result<Map<String, Object>> execute(@PathVariable UUID id) {
        var row = jdbc.queryForList(
                "SELECT * FROM asset_transfer WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "asset_transfer", null), id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> t = row.get(0);
        UUID deviceId = t.get("device_id") != null ? UUID.fromString(t.get("device_id").toString()) : null;
        if (t.get("to_dept_id") != null && deviceId != null) {
            jdbc.update("UPDATE medical_device SET dept_id = ?::uuid, warehouse_id = NULL, updated_at = NOW() WHERE id = ?::uuid",
                    t.get("to_dept_id"), deviceId);
        }
        if (t.get("to_warehouse_id") != null && deviceId != null) {
            jdbc.update("UPDATE medical_device SET warehouse_id = ?::uuid, updated_at = NOW() WHERE id = ?::uuid",
                    t.get("to_warehouse_id"), deviceId);
        }
        if (t.get("to_campus_id") != null && deviceId != null) {
            jdbc.update("UPDATE medical_device SET campus_id = ?::uuid, updated_at = NOW() WHERE id = ?::uuid",
                    t.get("to_campus_id"), deviceId);
        }
        if (deviceId != null && (t.get("to_dept_id") != null || t.get("to_warehouse_id") != null
                || t.get("to_campus_id") != null)) {
            String transferNo = t.get("transfer_no") != null ? String.valueOf(t.get("transfer_no")) : null;
            ownershipService.openPeriodFromLedger(
                    deviceId, "biz_doc", "biz_doc", "asset_transfer", id, transferNo);
        }
        jdbc.update("UPDATE asset_transfer SET status = 'completed', updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }

    private Map<String, Object> resolveDeviceSnap(Object deviceId, Object codeHint, Object nameHint) {
        Map<String, Object> snap = new LinkedHashMap<>();
        String code = blankToNull(codeHint);
        String name = blankToNull(nameHint);
        if (deviceId != null && !String.valueOf(deviceId).isBlank()) {
            var rows = jdbc.queryForList(
                    "SELECT device_code, device_name FROM medical_device WHERE id = ?::uuid"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null),
                    deviceId);
            if (!rows.isEmpty()) {
                if (code == null) code = blankToNull(rows.get(0).get("device_code"));
                if (name == null) name = blankToNull(rows.get(0).get("device_name"));
            }
        }
        snap.put("device_code", code);
        snap.put("device_name", name);
        return snap;
    }

    private static String blankToNull(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }
}
