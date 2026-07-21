package com.meis.saas.notification.controller;

import com.meis.saas.common.ops.OpsDueReminderSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
@EnableScheduling
public class NotificationController {
    private final JdbcTemplate jdbc;

    @GetMapping("/messages")
    public Result<List<Map<String, Object>>> messages() {
        return Result.ok(jdbc.queryForList(
                "SELECT id, title, content, notification_type AS message_type, is_read, created_at FROM sys_notification ORDER BY created_at DESC LIMIT 100"));
    }

    @PostMapping("/messages")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO sys_notification (id, title, content, notification_type, is_read) VALUES (?::uuid,?,?,?,false)",
                id, body.get("title"), body.get("content"), body.getOrDefault("messageType", "system"));
        return Result.ok(Map.of("id", id.toString()));
    }

    @PostMapping("/messages/{id}/read")
    public Result<Void> markRead(@PathVariable UUID id) {
        jdbc.update("UPDATE sys_notification SET is_read = true, read_at = NOW() WHERE id = ?::uuid", id);
        return Result.ok();
    }

    @GetMapping("/expiry-alerts")
    public Result<List<Map<String, Object>>> expiryAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        alerts.addAll(jdbc.queryForList(
                "SELECT device_code, device_name, warranty_end_date AS due_date, 'warranty' AS alert_type FROM medical_device WHERE warranty_end_date IS NOT NULL AND warranty_end_date <= CURRENT_DATE + 30 ORDER BY warranty_end_date LIMIT 50"));
        alerts.addAll(jdbc.queryForList(
                "SELECT m.device_code, m.device_name, r.next_due_date AS due_date, 'metrology' AS alert_type FROM metrology_record r JOIN medical_device m ON m.id = r.device_id WHERE r.next_due_date IS NOT NULL AND r.next_due_date <= CURRENT_DATE + 30 ORDER BY r.next_due_date LIMIT 50"));
        return Result.ok(alerts);
    }

    /** 手动触发运维到期扫描（调试/运维）；日常由定时任务调用。 */
    @PostMapping("/ops-due/scan")
    public Result<Map<String, Object>> scanOpsDue() {
        int n = OpsDueReminderSupport.scanAndNotify(jdbc);
        return Result.ok(Map.of("notified", n));
    }

    @Scheduled(cron = "0 0 8 * * ?")
    public void dailyExpiryScan() {
        if (TenantContext.getSchemaName() == null) return;
        List<Map<String, Object>> due = jdbc.queryForList(
                "SELECT device_code, device_name, warranty_end_date FROM medical_device WHERE warranty_end_date = CURRENT_DATE + 7 LIMIT 20");
        for (Map<String, Object> d : due) {
            jdbc.update("INSERT INTO sys_notification (title, content, notification_type, is_read) VALUES (?,?,?,false)",
                    "保修到期提醒", d.get("device_name") + " (" + d.get("device_code") + ") 将于7天后到期", "warranty_due");
        }
        OpsDueReminderSupport.scanAndNotify(jdbc);
    }
}
