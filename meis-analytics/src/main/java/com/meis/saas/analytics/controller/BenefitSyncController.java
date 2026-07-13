package com.meis.saas.analytics.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/analytics/sync")
@RequiredArgsConstructor
public class BenefitSyncController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String systemCode) {
        StringBuilder where = new StringBuilder(" WHERE task_type LIKE 'benefit_%' ");
        List<Object> args = new ArrayList<>();
        if (systemCode != null && !systemCode.isBlank()) {
            where.append(" AND system_code = ? ");
            args.add(systemCode.toUpperCase());
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM integration_sync_task" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList(
                "SELECT * FROM integration_sync_task" + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @PostMapping("/trigger")
    @Transactional
    @OperationLog(module = "analytics", description = "触发效益数据抓取")
    public Result<Map<String, Object>> trigger(@RequestBody Map<String, Object> body) {
        String system = body.getOrDefault("systemCode", "HIS").toString().toUpperCase();
        LocalDate syncDate = body.containsKey("syncDate")
                ? LocalDate.parse(body.get("syncDate").toString())
                : LocalDate.now().minusDays(1);
        UUID taskId = UUID.randomUUID();
        int imported = importUsageFromMappings(system, syncDate);
        jdbc.update("""
                INSERT INTO integration_sync_task (id, system_code, task_type, status, payload, result, finished_at)
                VALUES (?::uuid, ?, 'benefit_usage_sync', 'completed', ?::jsonb, ?::jsonb, NOW())
                """, taskId, system,
                "{\"syncDate\":\"" + syncDate + "\"}",
                "{\"imported\":" + imported + ",\"message\":\"stub sync completed\"}");
        return Result.ok(Map.of("taskId", taskId, "system", system, "syncDate", syncDate.toString(), "imported", imported));
    }

    private int importUsageFromMappings(String system, LocalDate syncDate) {
        var mappings = jdbc.queryForList("SELECT * FROM benefit_mapping WHERE is_active = true");
        int count = 0;
        for (Map<String, Object> m : mappings) {
            UUID deviceId = (UUID) m.get("device_id");
            if (deviceId == null) continue;
            BigDecimal unitPrice = m.get("unit_price") != null
                    ? new BigDecimal(m.get("unit_price").toString()) : BigDecimal.ZERO;
            int patients = 5 + (int) (Math.random() * 15);
            BigDecimal revenue = unitPrice.multiply(BigDecimal.valueOf(patients));
            jdbc.update("""
                    INSERT INTO device_usage_record (device_id, device_code, device_name, usage_date, usage_hours,
                    patient_count, examination_count, revenue, data_source, source_record_id)
                    VALUES (?::uuid,?,?,?,?,?,?,?,?,?)
                    """, deviceId, m.get("device_code"), m.get("device_name"), syncDate,
                    BigDecimal.valueOf(4 + Math.random() * 4), patients, patients, revenue, system,
                    system + "-" + syncDate + "-" + m.get("id"));
            count++;
        }
        return count;
    }
}
