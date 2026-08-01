package com.meis.saas.asset.service;

import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * AST-GAP O-01：UDI 变更历史；台账 udi_di/udi_pi 变更时闭合开放段并新开。
 */
@Service
@RequiredArgsConstructor
public class DeviceUdiHistoryService {
    private static final String TABLE = "device_udi_history";

    private final JdbcTemplate jdbc;
    private final DeviceChildEntitySupport child;
    private final EntityChangeLogService changeLog;

    public List<Map<String, Object>> listByDevice(UUID deviceId) {
        child.requireDevice(deviceId);
        return jdbc.queryForList("""
                SELECT * FROM device_udi_history
                WHERE device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null) + """
                ORDER BY effective_from DESC NULLS LAST, created_at DESC NULLS LAST
                """, deviceId);
    }

    public Map<String, Object> get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM device_udi_history WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), id);
        if (rows.isEmpty()) throw new BizException(404, "UDI 历史不存在");
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        UUID deviceId = DeviceChildEntitySupport.parseUuid(body.get("device_id"));
        Map<String, Object> device = child.requireDevice(deviceId);
        String udiDi = DeviceChildEntitySupport.str(body.get("udi_di"));
        String udiPi = DeviceChildEntitySupport.str(body.get("udi_pi"));
        if (udiDi == null && udiPi == null) {
            throw new BizException(400, "请填写 UDI DI 或 PI");
        }
        closeOpenPeriod(deviceId);
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("device_id", deviceId);
        row.put("device_code", device.get("device_code"));
        row.put("device_name", device.get("device_name"));
        row.put("udi_di", udiDi);
        row.put("udi_pi", udiPi);
        row.put("effective_from", body.get("effective_from") != null ? body.get("effective_from") : null);
        row.put("effective_to", body.get("effective_to"));
        row.put("change_reason", DeviceChildEntitySupport.str(body.get("change_reason")));
        row.put("remark", body.get("remark"));
        SoftDeleteSupport.applyInsertAudit(jdbc, TABLE, row);
        jdbc.update("""
                INSERT INTO device_udi_history (
                  id, device_id, device_code, device_name, udi_di, udi_pi,
                  effective_from, effective_to, change_reason, remark,
                  created_by, created_by_name, is_deleted
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?, ?, ?,
                  COALESCE(?::timestamptz, NOW()), ?::timestamptz, ?, ?,
                  ?::uuid, ?, 0
                )
                """,
                id, deviceId, row.get("device_code"), row.get("device_name"), udiDi, udiPi,
                row.get("effective_from") == null ? null : String.valueOf(row.get("effective_from")),
                row.get("effective_to") == null ? null : String.valueOf(row.get("effective_to")),
                row.get("change_reason"), row.get("remark"),
                row.get("created_by"), row.get("created_by_name"));
        Map<String, Object> after = get(id);
        changeLog.recordCreate(TABLE, id, after);
        return after;
    }

    @Transactional
    public void softDelete(UUID id) {
        Map<String, Object> before = changeLog.loadRow(TABLE, id);
        if (before == null || before.isEmpty()) throw new BizException(404, "UDI 历史不存在");
        SoftDeleteSupport.softDelete(jdbc, TABLE, id.toString());
        changeLog.recordDelete(TABLE, id, before);
    }

    /**
     * 台账更新后调用：若 udi_di/udi_pi 相对更新前有变化，则闭合开放段并写入新开放段。
     */
    @Transactional
    public void onDeviceUdiChanged(UUID deviceId, String beforeDi, String beforePi, String afterDi, String afterPi) {
        String bDi = normalize(beforeDi);
        String bPi = normalize(beforePi);
        String aDi = normalize(afterDi);
        String aPi = normalize(afterPi);
        if (Objects.equals(bDi, aDi) && Objects.equals(bPi, aPi)) return;
        if (aDi == null && aPi == null) {
            closeOpenPeriod(deviceId);
            return;
        }
        Map<String, Object> device = child.requireDevice(deviceId);
        closeOpenPeriod(deviceId);
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        SoftDeleteSupport.applyInsertAudit(jdbc, TABLE, row);
        jdbc.update("""
                INSERT INTO device_udi_history (
                  id, device_id, device_code, device_name, udi_di, udi_pi,
                  effective_from, effective_to, change_reason, remark,
                  created_by, created_by_name, is_deleted
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?, ?, ?,
                  NOW(), NULL, 'ledger_update', NULL,
                  ?::uuid, ?, 0
                )
                """,
                id, deviceId, device.get("device_code"), device.get("device_name"), aDi, aPi,
                row.get("created_by"), row.get("created_by_name"));
        changeLog.recordCreate(TABLE, id, changeLog.loadRow(TABLE, id));
    }

    private void closeOpenPeriod(UUID deviceId) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        sets.add("effective_to = NOW()");
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, TABLE), sets, args);
        args.add(deviceId);
        jdbc.update("UPDATE device_udi_history SET " + String.join(", ", sets)
                        + " WHERE device_id = ?::uuid AND effective_to IS NULL"
                        + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null),
                args.toArray());
    }

    private static String normalize(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }
}
