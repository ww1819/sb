package com.meis.saas.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BenefitSummaryService {
    private final JdbcTemplate jdbc;

    @Transactional
    public int recompute(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        var devices = jdbc.queryForList("""
                SELECT DISTINCT device_id FROM (
                    SELECT device_id FROM device_usage_record WHERE usage_date BETWEEN ? AND ? AND device_id IS NOT NULL
                    UNION
                    SELECT device_id FROM device_cost_record WHERE cost_date BETWEEN ? AND ? AND device_id IS NOT NULL
                ) t
                """, start, end, start, end);
        int count = 0;
        for (Map<String, Object> row : devices) {
            UUID deviceId = (UUID) row.get("device_id");
            if (deviceId == null) continue;
            upsertSummary(deviceId, year, month, start, end);
            count++;
        }
        return count;
    }

    private void upsertSummary(UUID deviceId, int year, int month, LocalDate start, LocalDate end) {
        var device = jdbc.queryForList("SELECT device_code, device_name FROM medical_device WHERE id = ?::uuid", deviceId);
        if (device.isEmpty()) return;
        String deviceCode = (String) device.get(0).get("device_code");
        String deviceName = (String) device.get(0).get("device_name");

        BigDecimal revenue = jdbc.queryForObject("""
                SELECT COALESCE(SUM(revenue), 0) FROM device_usage_record
                WHERE device_id = ?::uuid AND usage_date BETWEEN ? AND ?
                """, BigDecimal.class, deviceId, start, end);
        BigDecimal usageHours = jdbc.queryForObject("""
                SELECT COALESCE(SUM(usage_hours), 0) FROM device_usage_record
                WHERE device_id = ?::uuid AND usage_date BETWEEN ? AND ?
                """, BigDecimal.class, deviceId, start, end);
        Integer patientCount = jdbc.queryForObject("""
                SELECT COALESCE(SUM(patient_count), 0)::int FROM device_usage_record
                WHERE device_id = ?::uuid AND usage_date BETWEEN ? AND ?
                """, Integer.class, deviceId, start, end);

        BigDecimal repairCost = sumCost(deviceId, start, end, "repair");
        BigDecimal maintainCost = sumCost(deviceId, start, end, "maintain");
        BigDecimal depreciationCost = sumCost(deviceId, start, end, "depreciation");
        BigDecimal powerCost = sumCost(deviceId, start, end, "power");
        BigDecimal otherCost = jdbc.queryForObject("""
                SELECT COALESCE(SUM(cost_amount), 0) FROM device_cost_record
                WHERE device_id = ?::uuid AND cost_date BETWEEN ? AND ?
                AND cost_type NOT IN ('repair','maintain','depreciation','power')
                """, BigDecimal.class, deviceId, start, end);
        BigDecimal totalCost = repairCost.add(maintainCost).add(depreciationCost).add(powerCost).add(otherCost);
        BigDecimal netProfit = revenue.subtract(totalCost);
        BigDecimal profitRate = revenue.compareTo(BigDecimal.ZERO) > 0
                ? netProfit.divide(revenue, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal utilizationRate = usageHours.compareTo(BigDecimal.ZERO) > 0
                ? usageHours.divide(BigDecimal.valueOf(end.getDayOfMonth() * 8L), 4, RoundingMode.HALF_UP).min(BigDecimal.ONE)
                : BigDecimal.ZERO;
        String benefitLevel = calcLevel(profitRate);
        BigDecimal benefitScore = profitRate.multiply(BigDecimal.valueOf(100))
                .add(utilizationRate.multiply(BigDecimal.valueOf(20)));

        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM device_benefit_summary WHERE device_id = ?::uuid AND summary_year = ? AND summary_month = ?",
                deviceId, year, month).isEmpty();
        if (exists) {
            jdbc.update("""
                    UPDATE device_benefit_summary SET device_code=?, device_name=?, total_revenue=?, total_cost=?,
                    net_profit=?, profit_rate=?, usage_hours=?, patient_count=?, utilization_rate=?,
                    maintenance_cost=?, repair_cost=?, depreciation_cost=?, benefit_level=?, benefit_score=?, updated_at=NOW()
                    WHERE device_id=?::uuid AND summary_year=? AND summary_month=?
                    """, deviceCode, deviceName, revenue, totalCost, netProfit, profitRate, usageHours, patientCount,
                    utilizationRate, maintainCost, repairCost, depreciationCost, benefitLevel, benefitScore,
                    deviceId, year, month);
        } else {
            jdbc.update("""
                    INSERT INTO device_benefit_summary (device_id, device_code, device_name, summary_year, summary_month,
                    total_revenue, total_cost, net_profit, profit_rate, usage_hours, patient_count, utilization_rate,
                    maintenance_cost, repair_cost, depreciation_cost, benefit_level, benefit_score)
                    VALUES (?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, deviceId, deviceCode, deviceName, year, month, revenue, totalCost, netProfit, profitRate,
                    usageHours, patientCount, utilizationRate, maintainCost, repairCost, depreciationCost,
                    benefitLevel, benefitScore);
        }
    }

    private BigDecimal sumCost(UUID deviceId, LocalDate start, LocalDate end, String type) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(cost_amount), 0) FROM device_cost_record
                WHERE device_id = ?::uuid AND cost_date BETWEEN ? AND ? AND cost_type = ?
                """, BigDecimal.class, deviceId, start, end, type);
    }

    private String calcLevel(BigDecimal profitRate) {
        double rate = profitRate.doubleValue();
        if (rate >= 0.3) return "excellent";
        if (rate >= 0.15) return "good";
        if (rate >= 0) return "normal";
        return "poor";
    }
}
