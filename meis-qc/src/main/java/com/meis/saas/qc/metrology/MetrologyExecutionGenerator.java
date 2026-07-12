package com.meis.saas.qc.metrology;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class MetrologyExecutionGenerator {
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> generateBatch(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> planIds = (List<String>) body.getOrDefault("planIds", List.of());
        List<Map<String, Object>> created = new ArrayList<>();
        for (String planId : planIds) {
            created.add(generateOne(UUID.fromString(planId), body));
        }
        return created;
    }

    public Map<String, Object> generateOne(UUID planId, Map<String, Object> body) {
        var plan = jdbc.queryForList("""
                SELECT p.*, t.template_name
                FROM metrology_plan p
                LEFT JOIN metrology_template t ON t.id = p.template_id
                WHERE p.id = ?::uuid
                """, planId);
        if (plan.isEmpty()) return Map.of("planId", planId, "error", "plan not found");
        Map<String, Object> p = plan.get(0);
        if (!"approved".equals(p.get("approval_status"))) {
            return Map.of("planId", planId, "error", "plan not approved");
        }
        UUID execId = UUID.randomUUID();
        String execNo = "ME" + System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO metrology_execution (id, execution_no, plan_id, template_id, category_id, org_id,
                    planned_date, assigned_inspector_id, status, created_by)
                VALUES (?::uuid,?,?,?::uuid,?::uuid,?::uuid,?::uuid,?,?::uuid,?,?)
                """, execId, execNo, planId, p.get("template_id"), p.get("category_id"), p.get("org_id"),
                body.getOrDefault("planned_date", p.get("next_due_date")),
                p.get("assigned_inspector_id"), "pending", body.get("created_by"));

        UUID itemId = UUID.randomUUID();
        var device = jdbc.queryForList("SELECT device_code, device_name, dept_id FROM medical_device WHERE id=?::uuid", p.get("device_id"));
        String deviceCode = device.isEmpty() ? null : (String) device.get(0).get("device_code");
        String deviceName = device.isEmpty() ? null : (String) device.get(0).get("device_name");
        Object deptId = device.isEmpty() ? null : device.get(0).get("dept_id");
        jdbc.update("""
                INSERT INTO metrology_execution_item (id, execution_id, device_id, device_code, device_name, dept_id, plan_id, status)
                VALUES (?::uuid,?::uuid,?::uuid,?,?,?::uuid,?::uuid,'pending')
                """, itemId, execId, p.get("device_id"), deviceCode, deviceName, deptId, planId);

        var templateItems = jdbc.queryForList("""
                SELECT * FROM metrology_template_item WHERE template_id = ?::uuid ORDER BY sort_order, created_at
                """, p.get("template_id"));
        for (Map<String, Object> ti : templateItems) {
            jdbc.update("""
                    INSERT INTO metrology_execution_result (id, execution_item_id, template_item_id, item_name, item_content, result_status)
                    VALUES (?::uuid,?::uuid,?::uuid,?,?,?)
                    """, UUID.randomUUID(), itemId, ti.get("id"), ti.get("item_name"), ti.get("item_content"), "pending");
        }
        return jdbc.queryForList("SELECT * FROM metrology_execution WHERE id=?::uuid", execId).get(0);
    }
}
