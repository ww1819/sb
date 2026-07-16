package com.meis.saas.purchase.support;

import com.meis.saas.common.notify.NotificationHelper;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

public final class PurchaseAlertService {
    private PurchaseAlertService() {}

    public static List<Map<String, Object>> scanAndNotify(JdbcTemplate jdbc) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        scanOverBudget(jdbc, alerts);
        scanOverdueDelivery(jdbc, alerts);
        scanPendingApproval(jdbc, alerts);
        scanAcceptanceDelay(jdbc, alerts);
        scanPaymentPending(jdbc, alerts);
        for (Map<String, Object> alert : alerts) {
            persistAndNotify(jdbc, alert);
        }
        return alerts;
    }

    public static List<Map<String, Object>> listActive(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT alert_type AS type, level, title, message, ref_code, notified_at
            FROM purchase_alert_snapshot
            WHERE resolved_at IS NULL
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_alert_snapshot", null) + """
            ORDER BY
              CASE level WHEN 'danger' THEN 0 WHEN 'warning' THEN 1 ELSE 2 END,
              notified_at DESC
            LIMIT 30
            """);
    }

    private static void scanOverBudget(JdbcTemplate jdbc, List<Map<String, Object>> alerts) {
        var rows = jdbc.queryForList("""
            SELECT pj.project_code, pl.plan_code
            FROM purchase_project pj
            JOIN purchase_plan pl ON pl.id = pj.plan_id
            WHERE pl.total_budget > 0 AND pj.total_amount > pl.total_budget
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "pl") + """
            LIMIT 15
            """);
        for (Map<String, Object> row : rows) {
            String code = String.valueOf(row.get("project_code"));
            alerts.add(alert("over_budget", "warning", "超预算项目",
                    code + " 金额超出计划 " + row.get("plan_code"), code));
        }
    }

    private static void scanOverdueDelivery(JdbcTemplate jdbc, List<Map<String, Object>> alerts) {
        var rows = jdbc.queryForList("""
            SELECT contract_code FROM purchase_contract
            WHERE delivery_deadline < CURRENT_DATE AND acceptance_status != 'passed'
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null) + """
            LIMIT 15
            """);
        for (Map<String, Object> row : rows) {
            String code = String.valueOf(row.get("contract_code"));
            alerts.add(alert("overdue", "danger", "交货超期",
                    code + " 已过交货期限", code));
        }
    }

    private static void scanPendingApproval(JdbcTemplate jdbc, List<Map<String, Object>> alerts) {
        var rows = jdbc.queryForList("""
            SELECT business_no, title FROM sys_approval_instance
            WHERE status = 'pending' AND business_type LIKE 'purchase%'
              AND created_at < CURRENT_TIMESTAMP - INTERVAL '7 days'
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_instance", null) + """
            LIMIT 10
            """);
        for (Map<String, Object> row : rows) {
            String no = String.valueOf(row.get("business_no"));
            alerts.add(alert("approval_stale:" + no, "warning", "审批滞留",
                    row.get("title") + " 超过7天未处理", no));
        }
    }

    private static void scanAcceptanceDelay(JdbcTemplate jdbc, List<Map<String, Object>> alerts) {
        var rows = jdbc.queryForList("""
            SELECT pc.contract_code FROM purchase_contract pc
            WHERE pc.approval_status = 'approved'
              AND pc.acceptance_status != 'passed'
              AND pc.updated_at < CURRENT_TIMESTAMP - INTERVAL '30 days'
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc") + """
            LIMIT 10
            """);
        for (Map<String, Object> row : rows) {
            String code = String.valueOf(row.get("contract_code"));
            alerts.add(alert("acceptance_delay:" + code, "warning", "验收滞后",
                    code + " 合同已批30天仍未完成验收", code));
        }
    }

    private static void scanPaymentPending(JdbcTemplate jdbc, List<Map<String, Object>> alerts) {
        var rows = jdbc.queryForList("""
            SELECT cp.payment_no, cp.payment_amount, pc.contract_code
            FROM contract_payment cp
            JOIN purchase_contract pc ON pc.id = cp.contract_id
            WHERE cp.status = 'pending' AND cp.approval_status = 'approved'
              AND cp.payment_date IS NOT NULL AND cp.payment_date < CURRENT_DATE
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "contract_payment", "cp")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc") + """
            LIMIT 10
            """);
        for (Map<String, Object> row : rows) {
            String no = String.valueOf(row.get("payment_no"));
            alerts.add(alert("payment_overdue:" + no, "danger", "付款超期",
                    no + "（" + row.get("contract_code") + "）已过计划付款日", no));
        }
    }

    private static Map<String, Object> alert(String key, String level, String title, String message, String refCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alert_key", key);
        m.put("type", key.contains(":") ? key.substring(0, key.indexOf(':')) : key);
        m.put("level", level);
        m.put("title", title);
        m.put("message", message);
        m.put("ref_code", refCode);
        return m;
    }

    private static void persistAndNotify(JdbcTemplate jdbc, Map<String, Object> alert) {
        String key = alert.get("alert_key").toString();
        var existing = jdbc.queryForList(
                "SELECT id FROM purchase_alert_snapshot WHERE alert_key = ? AND resolved_at IS NULL"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_alert_snapshot", null), key);
        if (!existing.isEmpty()) return;
        jdbc.update("""
            INSERT INTO purchase_alert_snapshot (id, alert_key, alert_type, title, message, level, ref_code)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (alert_key) DO UPDATE SET
              title = EXCLUDED.title, message = EXCLUDED.message,
              level = EXCLUDED.level, notified_at = CURRENT_TIMESTAMP, resolved_at = NULL
            """,
                UUID.randomUUID(), key, alert.get("type"), alert.get("title"),
                alert.get("message"), alert.get("level"), alert.get("ref_code"));
        NotificationHelper.push(jdbc,
                String.valueOf(alert.get("title")),
                String.valueOf(alert.get("message")),
                "purchase_alert");
    }
}
