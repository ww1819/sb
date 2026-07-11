package com.meis.saas.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PowerMonitorService {
    private final JdbcTemplate jdbc;

    @Transactional
    public int collectSnapshot() {
        var tags = jdbc.queryForList("""
                SELECT t.*, d.device_code, d.device_name
                FROM power_tag t
                LEFT JOIN medical_device d ON d.id = t.device_id
                WHERE t.is_active = true AND t.device_id IS NOT NULL
                """);
        int count = 0;
        for (Map<String, Object> tag : tags) {
            UUID tagId = (UUID) tag.get("id");
            UUID deviceId = (UUID) tag.get("device_id");
            if (deviceId == null) continue;
            double roll = Math.random();
            String workState = roll > 0.75 ? "running" : roll > 0.45 ? "idle" : roll > 0.15 ? "offline" : "alarm";
            BigDecimal current = "running".equals(workState)
                    ? BigDecimal.valueOf(2 + Math.random() * 8).setScale(3, RoundingMode.HALF_UP)
                    : "idle".equals(workState)
                    ? BigDecimal.valueOf(0.2 + Math.random() * 0.8).setScale(3, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal voltage = BigDecimal.valueOf(218 + Math.random() * 10).setScale(2, RoundingMode.HALF_UP);
            BigDecimal powerWatt = current.multiply(voltage).setScale(2, RoundingMode.HALF_UP);
            upsertStatus(tagId, deviceId, tag, current, voltage, powerWatt, workState);
            upsertDailyRecord(tagId, deviceId, tag, current, workState);
            count++;
        }
        return count;
    }

    private void upsertStatus(UUID tagId, UUID deviceId, Map<String, Object> tag,
                              BigDecimal current, BigDecimal voltage, BigDecimal powerWatt, String workState) {
        boolean exists = !jdbc.queryForList("SELECT 1 FROM power_device_status WHERE tag_id = ?::uuid", tagId).isEmpty();
        if (exists) {
            jdbc.update("""
                    UPDATE power_device_status SET device_id=?::uuid, device_code=?, device_name=?,
                    current_amp=?, voltage=?, power_watt=?, work_state=?, collected_at=NOW(), updated_at=NOW()
                    WHERE tag_id=?::uuid
                    """, deviceId, tag.get("device_code"), tag.get("device_name"),
                    current, voltage, powerWatt, workState, tagId);
        } else {
            jdbc.update("""
                    INSERT INTO power_device_status (device_id, tag_id, device_code, device_name,
                    current_amp, voltage, power_watt, work_state)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?,?)
                    """, deviceId, tagId, tag.get("device_code"), tag.get("device_name"),
                    current, voltage, powerWatt, workState);
        }
    }

    private void upsertDailyRecord(UUID tagId, UUID deviceId, Map<String, Object> tag,
                                   BigDecimal current, String workState) {
        LocalDate today = LocalDate.now();
        double runInc = "running".equals(workState) ? 0.25 : 0;
        double idleInc = "idle".equals(workState) ? 0.25 : 0;
        double offlineInc = "offline".equals(workState) ? 0.25 : 0;
        double energyInc = powerKw(current) * 0.25;
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM power_monitor_record WHERE device_id = ?::uuid AND record_date = ?",
                deviceId, today).isEmpty();
        if (exists) {
            jdbc.update("""
                    UPDATE power_monitor_record SET run_hours = run_hours + ?, idle_hours = idle_hours + ?,
                    offline_hours = offline_hours + ?, avg_current = ?, peak_current = GREATEST(COALESCE(peak_current,0), ?),
                    energy_kwh = energy_kwh + ?
                    WHERE device_id = ?::uuid AND record_date = ?
                    """, runInc, idleInc, offlineInc, current, current, energyInc, deviceId, today);
        } else {
            jdbc.update("""
                    INSERT INTO power_monitor_record (device_id, tag_id, device_code, device_name, record_date,
                    run_hours, idle_hours, offline_hours, avg_current, peak_current, energy_kwh)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?,?)
                    """, deviceId, tagId, tag.get("device_code"), tag.get("device_name"), today,
                    runInc, idleInc, offlineInc, current, current, energyInc);
        }
    }

    private double powerKw(BigDecimal currentAmp) {
        return currentAmp.doubleValue() * 220 / 1000.0;
    }

    public Map<String, Object> statsSummary() {
        var stateCounts = jdbc.queryForList(
                "SELECT work_state, COUNT(*) AS count FROM power_device_status GROUP BY work_state");
        BigDecimal todayEnergy = jdbc.queryForObject("""
                SELECT COALESCE(SUM(energy_kwh), 0) FROM power_monitor_record WHERE record_date = CURRENT_DATE
                """, BigDecimal.class);
        long tagCount = jdbc.queryForObject("SELECT COUNT(*) FROM power_tag WHERE is_active = true", Long.class);
        long stationCount = jdbc.queryForObject("SELECT COUNT(*) FROM power_base_station WHERE is_active = true", Long.class);
        long alarmCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM power_device_status WHERE work_state = 'alarm'", Long.class);
        return Map.of(
                "tagCount", tagCount,
                "stationCount", stationCount,
                "alarmCount", alarmCount,
                "todayEnergyKwh", todayEnergy,
                "stateCounts", stateCounts);
    }

    public List<Map<String, Object>> deviceRanking(int limit) {
        return jdbc.queryForList("""
                SELECT r.device_code, r.device_name, dept.dept_name,
                       SUM(r.run_hours) AS run_hours, SUM(r.energy_kwh) AS energy_kwh
                FROM power_monitor_record r
                LEFT JOIN medical_device d ON d.id = r.device_id
                LEFT JOIN department dept ON dept.id = d.dept_id
                WHERE r.record_date >= CURRENT_DATE - INTERVAL '30 days'
                GROUP BY r.device_code, r.device_name, dept.dept_name
                ORDER BY run_hours DESC LIMIT ?
                """, limit);
    }
}
