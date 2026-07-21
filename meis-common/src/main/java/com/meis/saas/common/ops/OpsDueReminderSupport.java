package com.meis.saas.common.ops;

import com.meis.saas.common.notify.NotificationHelper;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/** 运维计划明细到期推送（附录 OPS.11 / MT-F-01）。 */
public final class OpsDueReminderSupport {
    private OpsDueReminderSupport() {}

    public static int scanAndNotify(JdbcTemplate jdbc) {
        int n = 0;
        n += scanOne(jdbc, "保养", "maintain_due", """
                SELECT i.device_code, i.device_name, i.next_due_date, p.plan_no, p.plan_name,
                       COALESCE(p.reminder_days_before, 7) AS reminder_days
                FROM maintenance_plan_item i
                INNER JOIN maintenance_plan p ON p.id = i.plan_id
                WHERE p.status = 'active' AND p.approval_status = 'approved'
                  AND COALESCE(i.item_status,'active') = 'active'
                  AND i.next_due_date IS NOT NULL
                  AND i.next_due_date <= CURRENT_DATE + COALESCE(p.reminder_days_before, 7)
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_plan_item", "i")
                + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_plan", "p")
                + " ORDER BY i.next_due_date LIMIT 100");
        n += scanOne(jdbc, "巡检", "inspect_due", """
                SELECT i.device_code, i.device_name, i.next_due_date, p.plan_no, p.plan_name, 7 AS reminder_days
                FROM inspection_plan_item i
                INNER JOIN inspection_plan p ON p.id = i.plan_id
                WHERE COALESCE(p.status,'active') IN ('active','pending') AND p.approval_status = 'approved'
                  AND COALESCE(i.item_status,'active') = 'active'
                  AND i.next_due_date IS NOT NULL
                  AND i.next_due_date <= CURRENT_DATE + 7
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan_item", "i")
                + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan", "p")
                + " ORDER BY i.next_due_date LIMIT 100");
        n += scanOne(jdbc, "预防性维护", "pm_due", """
                SELECT i.device_code, i.device_name, i.next_due_date, p.plan_no, p.plan_name,
                       COALESCE(p.reminder_days_before, 7) AS reminder_days
                FROM pm_plan_item i
                INNER JOIN pm_plan p ON p.id = i.plan_id
                WHERE p.status = 'active' AND p.approval_status = 'approved'
                  AND COALESCE(i.item_status,'active') = 'active'
                  AND i.next_due_date IS NOT NULL
                  AND i.next_due_date <= CURRENT_DATE + COALESCE(p.reminder_days_before, 7)
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "pm_plan_item", "i")
                + SoftDeleteSupport.notDeletedClause(jdbc, "pm_plan", "p")
                + " ORDER BY i.next_due_date LIMIT 100");
        return n;
    }

    private static int scanOne(JdbcTemplate jdbc, String label, String type, String sql) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            for (Map<String, Object> r : rows) {
                String title = label + "到期提醒";
                String content = r.get("device_name") + " (" + r.get("device_code") + ") 计划 "
                        + r.get("plan_no") + " 将于 " + r.get("next_due_date") + " 到期";
                NotificationHelper.push(jdbc, title, content, type);
            }
            return rows.size();
        } catch (Exception ignored) {
            return 0;
        }
    }
}
