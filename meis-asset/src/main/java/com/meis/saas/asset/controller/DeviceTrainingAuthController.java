package com.meis.saas.asset.controller;

import com.meis.saas.asset.service.DeviceChildEntitySupport;
import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AST-GAP O-03：设备培训授权 CRUD。
 */
@RestController
@RequestMapping("/api/asset/device-training-auth")
@RequiredArgsConstructor
public class DeviceTrainingAuthController {
    private static final String TABLE = "device_training_auth";

    private final JdbcTemplate jdbc;
    private final DeviceChildEntitySupport child;
    private final EntityChangeLogService changeLog;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "user_id", required = false) String userId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, TABLE, "t"));
        List<Object> args = new ArrayList<>();
        if (DeviceChildEntitySupport.hasText(deviceId)) {
            where.append(" AND t.device_id = ?::uuid ");
            args.add(deviceId.trim());
        }
        if (DeviceChildEntitySupport.hasText(userId)) {
            where.append(" AND t.user_id = ?::uuid ");
            args.add(userId.trim());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (t.device_code ILIKE ? OR t.device_name ILIKE ? OR t.user_name ILIKE ? OR t.cert_name ILIKE ? OR t.cert_no ILIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        String from = " FROM device_training_auth t ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        var rows = jdbc.queryForList(
                "SELECT t.* " + from + where
                        + " ORDER BY t.expiry_date NULLS LAST, t.created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(new PageResult<>(rows, total != null ? total : 0L, query.getPage(), query.getSize()));
    }

    @GetMapping("/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> byDevice(@PathVariable UUID deviceId) {
        child.requireDevice(deviceId);
        return Result.ok(jdbc.queryForList("""
                SELECT * FROM device_training_auth
                WHERE device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null) + """
                ORDER BY expiry_date NULLS LAST, created_at DESC
                """, deviceId));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        return Result.ok(load(id));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "新增培训授权")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID deviceId = DeviceChildEntitySupport.parseUuid(body.get("device_id"));
        UUID userId = DeviceChildEntitySupport.parseUuid(body.get("user_id"));
        Map<String, Object> row = new LinkedHashMap<>();
        UUID id = UUID.randomUUID();
        row.put("id", id);
        child.fillDeviceSnapshot(row, deviceId);
        row.put("user_id", userId);
        row.put("user_name", child.resolveUserName(userId, body.get("user_name")));
        row.put("cert_name", DeviceChildEntitySupport.str(body.get("cert_name")));
        row.put("cert_no", DeviceChildEntitySupport.str(body.get("cert_no")));
        row.put("trained_at", DeviceChildEntitySupport.str(body.get("trained_at")));
        row.put("expiry_date", DeviceChildEntitySupport.str(body.get("expiry_date")));
        row.put("auth_scope", DeviceChildEntitySupport.str(body.get("auth_scope")));
        row.put("remark", body.get("remark"));
        SoftDeleteSupport.applyInsertAudit(jdbc, TABLE, row);
        jdbc.update("""
                INSERT INTO device_training_auth (
                  id, device_id, device_code, device_name, user_id, user_name,
                  cert_name, cert_no, trained_at, expiry_date, auth_scope, remark,
                  created_by, created_by_name, is_deleted
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?, ?::uuid, ?,
                  ?, ?, ?::date, ?::date, ?, ?,
                  ?::uuid, ?, 0
                )
                """,
                id, row.get("device_id"), row.get("device_code"), row.get("device_name"),
                row.get("user_id"), row.get("user_name"),
                row.get("cert_name"), row.get("cert_no"), row.get("trained_at"), row.get("expiry_date"),
                row.get("auth_scope"), row.get("remark"),
                row.get("created_by"), row.get("created_by_name"));
        Map<String, Object> after = load(id);
        changeLog.recordCreate(TABLE, id, after);
        return Result.ok(after);
    }

    @PutMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "修改培训授权")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> before = changeLog.loadRow(TABLE, id);
        if (before == null || before.isEmpty()) throw new BizException(404, "培训授权不存在");
        SoftDeleteSupport.stripClientUpdateFields(body);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (body.containsKey("device_id")) {
            UUID deviceId = DeviceChildEntitySupport.parseUuid(body.get("device_id"));
            Map<String, Object> snap = new LinkedHashMap<>();
            child.fillDeviceSnapshot(snap, deviceId);
            sets.add("device_id = ?::uuid");
            args.add(snap.get("device_id"));
            sets.add("device_code = ?");
            args.add(snap.get("device_code"));
            sets.add("device_name = ?");
            args.add(snap.get("device_name"));
        }
        if (body.containsKey("user_id") || body.containsKey("user_name")) {
            UUID userId = body.containsKey("user_id")
                    ? DeviceChildEntitySupport.parseUuid(body.get("user_id"))
                    : DeviceChildEntitySupport.parseUuid(before.get("user_id"));
            sets.add("user_id = ?::uuid");
            args.add(userId);
            sets.add("user_name = ?");
            args.add(child.resolveUserName(userId, body.get("user_name")));
        }
        if (body.containsKey("cert_name")) {
            sets.add("cert_name = ?");
            args.add(DeviceChildEntitySupport.str(body.get("cert_name")));
        }
        if (body.containsKey("cert_no")) {
            sets.add("cert_no = ?");
            args.add(DeviceChildEntitySupport.str(body.get("cert_no")));
        }
        if (body.containsKey("trained_at")) {
            sets.add("trained_at = ?::date");
            args.add(DeviceChildEntitySupport.str(body.get("trained_at")));
        }
        if (body.containsKey("expiry_date")) {
            sets.add("expiry_date = ?::date");
            args.add(DeviceChildEntitySupport.str(body.get("expiry_date")));
        }
        if (body.containsKey("auth_scope")) {
            sets.add("auth_scope = ?");
            args.add(DeviceChildEntitySupport.str(body.get("auth_scope")));
        }
        if (body.containsKey("remark")) {
            sets.add("remark = ?");
            args.add(body.get("remark"));
        }
        if (sets.isEmpty()) return Result.ok(load(id));
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, TABLE), sets, args);
        args.add(id);
        jdbc.update("UPDATE device_training_auth SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), args.toArray());
        Map<String, Object> after = load(id);
        changeLog.recordUpdate(TABLE, id, before, after);
        return Result.ok(after);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除培训授权")
    public Result<Void> delete(@PathVariable UUID id) {
        Map<String, Object> before = changeLog.loadRow(TABLE, id);
        if (before == null || before.isEmpty()) throw new BizException(404, "培训授权不存在");
        SoftDeleteSupport.softDelete(jdbc, TABLE, id.toString());
        changeLog.recordDelete(TABLE, id, before);
        return Result.ok();
    }

    private Map<String, Object> load(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM device_training_auth WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), id);
        if (rows.isEmpty()) throw new BizException(404, "培训授权不存在");
        return rows.get(0);
    }
}
