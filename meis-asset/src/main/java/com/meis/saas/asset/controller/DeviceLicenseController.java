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
 * AST-GAP O-02：设备证照 CRUD。
 */
@RestController
@RequestMapping("/api/asset/device-license")
@RequiredArgsConstructor
public class DeviceLicenseController {
    private static final String TABLE = "device_license";

    private final JdbcTemplate jdbc;
    private final DeviceChildEntitySupport child;
    private final EntityChangeLogService changeLog;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "license_type", required = false) String licenseType) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, TABLE, "t"));
        List<Object> args = new ArrayList<>();
        if (DeviceChildEntitySupport.hasText(deviceId)) {
            where.append(" AND t.device_id = ?::uuid ");
            args.add(deviceId.trim());
        }
        if (DeviceChildEntitySupport.hasText(licenseType)) {
            where.append(" AND t.license_type = ? ");
            args.add(licenseType.trim());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (t.device_code ILIKE ? OR t.device_name ILIKE ? OR t.license_no ILIKE ? OR t.issuer_name ILIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        String from = " FROM device_license t ";
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
                SELECT * FROM device_license
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
    @OperationLog(module = "asset", description = "新增设备证照")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID deviceId = DeviceChildEntitySupport.parseUuid(body.get("device_id"));
        String licenseType = DeviceChildEntitySupport.str(body.get("license_type"));
        if (licenseType == null) throw new BizException(400, "请选择证照类型");
        Map<String, Object> row = new LinkedHashMap<>();
        UUID id = UUID.randomUUID();
        row.put("id", id);
        child.fillDeviceSnapshot(row, deviceId);
        row.put("license_type", licenseType);
        row.put("license_no", DeviceChildEntitySupport.str(body.get("license_no")));
        row.put("issue_date", DeviceChildEntitySupport.str(body.get("issue_date")));
        row.put("expiry_date", DeviceChildEntitySupport.str(body.get("expiry_date")));
        row.put("issuer_name", DeviceChildEntitySupport.str(body.get("issuer_name")));
        row.put("file_url", DeviceChildEntitySupport.str(body.get("file_url")));
        row.put("file_name", DeviceChildEntitySupport.str(body.get("file_name")));
        row.put("remark", body.get("remark"));
        SoftDeleteSupport.applyInsertAudit(jdbc, TABLE, row);
        jdbc.update("""
                INSERT INTO device_license (
                  id, device_id, device_code, device_name, license_type, license_no,
                  issue_date, expiry_date, issuer_name, file_url, file_name, remark,
                  created_by, created_by_name, is_deleted
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?, ?, ?,
                  ?::date, ?::date, ?, ?, ?, ?,
                  ?::uuid, ?, 0
                )
                """,
                id, row.get("device_id"), row.get("device_code"), row.get("device_name"),
                row.get("license_type"), row.get("license_no"),
                row.get("issue_date"), row.get("expiry_date"), row.get("issuer_name"),
                row.get("file_url"), row.get("file_name"), row.get("remark"),
                row.get("created_by"), row.get("created_by_name"));
        Map<String, Object> after = load(id);
        changeLog.recordCreate(TABLE, id, after);
        return Result.ok(after);
    }

    @PutMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "修改设备证照")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> before = changeLog.loadRow(TABLE, id);
        if (before == null || before.isEmpty()) throw new BizException(404, "证照不存在");
        SoftDeleteSupport.stripClientUpdateFields(body);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (body.containsKey("license_type")) {
            String v = DeviceChildEntitySupport.str(body.get("license_type"));
            if (v == null) throw new BizException(400, "证照类型不能为空");
            sets.add("license_type = ?");
            args.add(v);
        }
        if (body.containsKey("license_no")) {
            sets.add("license_no = ?");
            args.add(DeviceChildEntitySupport.str(body.get("license_no")));
        }
        if (body.containsKey("issue_date")) {
            sets.add("issue_date = ?::date");
            args.add(DeviceChildEntitySupport.str(body.get("issue_date")));
        }
        if (body.containsKey("expiry_date")) {
            sets.add("expiry_date = ?::date");
            args.add(DeviceChildEntitySupport.str(body.get("expiry_date")));
        }
        if (body.containsKey("issuer_name")) {
            sets.add("issuer_name = ?");
            args.add(DeviceChildEntitySupport.str(body.get("issuer_name")));
        }
        if (body.containsKey("file_url")) {
            sets.add("file_url = ?");
            args.add(DeviceChildEntitySupport.str(body.get("file_url")));
        }
        if (body.containsKey("file_name")) {
            sets.add("file_name = ?");
            args.add(DeviceChildEntitySupport.str(body.get("file_name")));
        }
        if (body.containsKey("remark")) {
            sets.add("remark = ?");
            args.add(body.get("remark"));
        }
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
        if (sets.isEmpty()) return Result.ok(load(id));
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, TABLE), sets, args);
        args.add(id);
        jdbc.update("UPDATE device_license SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), args.toArray());
        Map<String, Object> after = load(id);
        changeLog.recordUpdate(TABLE, id, before, after);
        return Result.ok(after);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除设备证照")
    public Result<Void> delete(@PathVariable UUID id) {
        Map<String, Object> before = changeLog.loadRow(TABLE, id);
        if (before == null || before.isEmpty()) throw new BizException(404, "证照不存在");
        SoftDeleteSupport.softDelete(jdbc, TABLE, id.toString());
        changeLog.recordDelete(TABLE, id, before);
        return Result.ok();
    }

    private Map<String, Object> load(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM device_license WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), id);
        if (rows.isEmpty()) throw new BizException(404, "证照不存在");
        return rows.get(0);
    }
}
