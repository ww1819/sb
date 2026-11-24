package com.meis.saas.common.asset;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W.6 / AST-W01：从台账解析设备编码/名称快照（有 id 时优先台账，hint 可补空）。
 */
public final class DeviceSnapshotSupport {
    private DeviceSnapshotSupport() {}

    public static Map<String, Object> resolve(JdbcTemplate jdbc, Object deviceId, Object codeHint, Object nameHint) {
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
