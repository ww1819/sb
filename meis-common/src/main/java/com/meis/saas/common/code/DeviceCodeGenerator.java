package com.meis.saas.common.code;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceCodeGenerator {
    private final JdbcTemplate jdbc;

    /** V2.0: 院区+楼+科室+国别+分类+序号 */
    public String generate(String campusCode, String buildingCode, String deptCode,
                           String countryCode, String categoryCode) {
        String prefix = safe(campusCode, 1) + safe(buildingCode, 1)
                + safe(deptCode, 3) + safe(countryCode, 1) + safe(categoryCode, 2);
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CAST(RIGHT(device_code, 4) AS INTEGER)), 0) FROM medical_device WHERE device_code LIKE ?",
                Integer.class, prefix + "%");
        int seq = (max != null ? max : 0) + 1;
        return prefix + String.format("%04d", seq);
    }

    public boolean exists(String deviceCode) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM medical_device WHERE device_code = ?", Integer.class, deviceCode);
        return c != null && c > 0;
    }

    private String safe(String v, int len) {
        if (v == null || v.isBlank()) return "0".repeat(len);
        String s = v.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (s.length() >= len) return s.substring(0, len);
        return s + "0".repeat(len - s.length());
    }
}
