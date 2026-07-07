package com.meis.saas.common.notify;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public final class NotificationHelper {
    private NotificationHelper() {}

    public static void push(JdbcTemplate jdbc, String title, String content, String notificationType) {
        try {
            jdbc.update("""
                INSERT INTO sys_notification (id, title, content, notification_type, is_read)
                VALUES (?::uuid, ?, ?, ?, false)
                """, UUID.randomUUID(), title, content, notificationType);
        } catch (Exception ignored) {
            // notification table may be unavailable in some profiles
        }
    }
}
