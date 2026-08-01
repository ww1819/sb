package com.meis.saas.asset.service;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 设备从表公共：校验设备、冗余编码名称、UUID 解析。
 */
@Component
@RequiredArgsConstructor
public class DeviceChildEntitySupport {
    private final JdbcTemplate jdbc;

    public Map<String, Object> requireDevice(UUID deviceId) {
        if (deviceId == null) throw new BizException(400, "请指定设备");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, device_code, device_name FROM medical_device WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null),
                deviceId);
        if (rows.isEmpty()) throw new BizException(404, "设备不存在");
        return rows.get(0);
    }

    public void fillDeviceSnapshot(Map<String, Object> row, UUID deviceId) {
        Map<String, Object> device = requireDevice(deviceId);
        row.put("device_id", deviceId);
        row.put("device_code", device.get("device_code"));
        row.put("device_name", device.get("device_name"));
    }

    public String resolveUserName(Object userId, Object fallbackName) {
        if (fallbackName != null && !String.valueOf(fallbackName).isBlank()) {
            return String.valueOf(fallbackName).trim();
        }
        return SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
    }

    public static UUID parseUuid(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public static Long parseLong(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static Integer parseInt(Object v, Integer defaultVal) {
        if (v == null || String.valueOf(v).isBlank()) return defaultVal;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
