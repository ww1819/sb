package com.meis.saas.analytics.service;

import com.meis.saas.analytics.power.PowerWorkStateResolver;
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
                SELECT t.*, d.device_code, d.device_name,
                       d.standby_current_max_ma, d.standby_current_min_ma,
                       s.station_code
                FROM power_tag t
                LEFT JOIN medical_device d ON d.id = t.device_id
                LEFT JOIN power_base_station s ON s.id = t.station_id
                WHERE t.is_active = true
                """);
        int count = 0;
        for (Map<String, Object> tag : tags) {
            UUID tagId = (UUID) tag.get("id");
            UUID deviceId = (UUID) tag.get("device_id");
            UUID stationId = (UUID) tag.get("station_id");
            BigDecimal maxMa = toDecimal(tag.get("standby_current_max_ma"));
            BigDecimal minMa = toDecimal(tag.get("standby_current_min_ma"));
            BigDecimal currentMa = mockCurrentMa(maxMa, minMa);
            String workState = PowerWorkStateResolver.resolve(currentMa, maxMa, minMa);
            BigDecimal currentAmp = currentMa.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            BigDecimal voltage = BigDecimal.valueOf(218 + Math.random() * 10).setScale(2, RoundingMode.HALF_UP);
            BigDecimal powerWatt = currentAmp.multiply(voltage).setScale(2, RoundingMode.HALF_UP);

            insertReading(tag, tagId, stationId, deviceId, currentMa);
            if (deviceId != null) {
                upsertStatus(tagId, deviceId, tag, currentAmp, voltage, powerWatt, workState);
                upsertDailyRecord(tagId, deviceId, tag, currentAmp, workState);
            }
            count++;
        }
        return count;
    }

    private BigDecimal mockCurrentMa(BigDecimal maxMa, BigDecimal minMa) {
        boolean hasMax = maxMa != null;
        boolean hasMin = minMa != null;
        double roll = Math.random();
        if (hasMax && hasMin) {
            double max = maxMa.doubleValue();
            double min = minMa.doubleValue();
            if (roll > 0.6) {
                return BigDecimal.valueOf(max + 10 + Math.random() * max).setScale(3, RoundingMode.HALF_UP);
            }
            if (roll > 0.25) {
                return BigDecimal.valueOf(min + Math.random() * Math.max(max - min, 1)).setScale(3, RoundingMode.HALF_UP);
            }
            return roll > 0.1
                    ? BigDecimal.valueOf(Math.random() * Math.max(min * 0.8, 1)).setScale(3, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }
        if (!hasMax && !hasMin) {
            return roll > 0.2
                    ? BigDecimal.valueOf(5 + Math.random() * 80).setScale(3, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }
        if (hasMax) {
            double max = maxMa.doubleValue();
            if (roll > 0.65) {
                return BigDecimal.valueOf(max + 5 + Math.random() * max).setScale(3, RoundingMode.HALF_UP);
            }
            return roll > 0.15
                    ? BigDecimal.valueOf(1 + Math.random() * max).setScale(3, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }
        double min = minMa.doubleValue();
        return roll > 0.15
                ? BigDecimal.valueOf(min + Math.random() * 120).setScale(3, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private void insertReading(Map<String, Object> tag, UUID tagId, UUID stationId, UUID deviceId, BigDecimal currentMa) {
        jdbc.update("""
                INSERT INTO power_current_reading (tag_id, tag_code, station_id, station_code, device_id, device_code, current_ma, read_at)
                VALUES (?::uuid, ?, ?::uuid, ?, ?::uuid, ?, ?, NOW())
                """,
                tagId,
                tag.get("tag_code"),
                stationId,
                tag.get("station_code"),
                deviceId,
                tag.get("device_code"),
                currentMa);
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
                    energy_kwh = energy_kwh + ?, tag_id = ?::uuid
                    WHERE device_id = ?::uuid AND record_date = ?
                    """, runInc, idleInc, offlineInc, current, current, energyInc, tagId, deviceId, today);
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

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(v.toString());
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
