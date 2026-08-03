package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AST-DISP-FIN / P-12：外部财务处置预留（手工录入；金蝶双向同步另议）。
 */
@RestController
@RequestMapping("/api/asset/external-disposition")
@RequiredArgsConstructor
public class ExternalAssetDispositionController {
    private final JdbcTemplate jdbc;

    @GetMapping("/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> byDevice(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT * FROM external_asset_disposition
                WHERE device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "external_asset_disposition", null)
                + " ORDER BY COALESCE(occurred_at, created_at) DESC NULLS LAST", deviceId));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM external_asset_disposition WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "external_asset_disposition", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存外部资产处置")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        if (body.get("device_id") == null || body.get("device_id").toString().isBlank()) {
            throw new BizException(400, "请选择设备");
        }
        UUID deviceId = UUID.fromString(body.get("device_id").toString());
        var devices = jdbc.queryForList(
                "SELECT device_code, device_name FROM medical_device WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), deviceId);
        if (devices.isEmpty()) throw new BizException(404, "设备不存在");

        UUID id = body.containsKey("id") && body.get("id") != null && !body.get("id").toString().isBlank()
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM external_asset_disposition WHERE id = ?::uuid", id).isEmpty();
        String userId = TenantContext.getUserId();
        UUID actor = userId != null ? UUID.fromString(userId) : null;
        String actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actor);
        String code = Objects.toString(body.getOrDefault("device_code", devices.get(0).get("device_code")), null);
        String name = Objects.toString(body.getOrDefault("device_name", devices.get(0).get("device_name")), null);
        String source = Objects.toString(body.getOrDefault("source_system", "manual"), "manual");
        String type = Objects.toString(body.getOrDefault("disposition_type", "scrap"), "scrap");
        String typeLabel = Objects.toString(body.getOrDefault("disposition_type_label", "外部处置"), "外部处置");
        String syncStatus = Objects.toString(body.getOrDefault("sync_status", "pending"), "pending");

        if (exists) {
            jdbc.update("""
                    UPDATE external_asset_disposition SET
                      device_id=?::uuid, device_code=?, device_name=?, source_system=?, external_ref=?,
                      disposition_type=?, disposition_type_label=?, amount=?, occurred_at=?,
                      sync_status=?, sync_message=?, remark=?,
                      updated_by=?::uuid, updated_by_name=?, updated_at=NOW()
                    WHERE id=?::uuid
                    """, deviceId, code, name, source, body.get("external_ref"), type, typeLabel,
                    body.get("amount"), body.get("occurred_at"), syncStatus, body.get("sync_message"),
                    body.get("remark"), actor, actorName, id);
        } else {
            jdbc.update("""
                    INSERT INTO external_asset_disposition
                    (id, device_id, device_code, device_name, source_system, external_ref, disposition_type,
                     disposition_type_label, amount, occurred_at, sync_status, sync_message, remark,
                     created_by, created_by_name)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?,?,?::uuid,?)
                    """, id, deviceId, code, name, source, body.get("external_ref"), type, typeLabel,
                    body.get("amount"), body.get("occurred_at"), syncStatus, body.get("sync_message"),
                    body.get("remark"), actor, actorName);
        }
        return get(id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除外部资产处置")
    public Result<Void> delete(@PathVariable UUID id) {
        int n = SoftDeleteSupport.softDelete(jdbc, "external_asset_disposition", id.toString());
        if (n == 0) throw new BizException(404, "not found");
        return Result.ok();
    }
}
