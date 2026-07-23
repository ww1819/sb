package com.meis.saas.maintain.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.biz.CycleDaysSupport;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/maintain/template")
@RequiredArgsConstructor
public class MaintenanceTemplateController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT t.*, l.level_name AS maintenance_level_name
                FROM maintenance_template t
                LEFT JOIN maintenance_level l ON l.id = t.maintenance_level_id
                WHERE t.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_template", "t"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("items", jdbc.queryForList(
                "SELECT * FROM maintenance_template_item WHERE template_id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_template_item", null)
                        + " ORDER BY sort_order, created_at", id));
        return Result.ok(result);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "maintain", description = "保存保养模板")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) throws Exception {
        UUID id = resolveUuid(body.get("id"));
        if (id == null) id = UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM maintenance_template WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_template", null), id).isEmpty();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        String itemsJson = mapper.writeValueAsString(items == null ? List.of() : items);
        String levelCode = resolveMaintenanceLevel(body);
        CycleDaysSupport.applyToBody(body);
        if (!exists) {
            jdbc.update("""
                INSERT INTO maintenance_template (id, template_code, template_name, maintenance_level, maintenance_level_id,
                    category_id, items, description, estimated_duration, cycle_type, cycle_value, cycle_days, is_active)
                VALUES (?::uuid,?,?,?,?::uuid,?::uuid,?::jsonb,?,?,?,?,?,?)
                """, id, body.get("template_code"), body.get("template_name"),
                    levelCode, blankToNull(body.get("maintenance_level_id")),
                    blankToNull(body.get("category_id")), itemsJson, body.get("description"),
                    body.get("estimated_duration"), body.get("cycle_type"), body.get("cycle_value"), body.get("cycle_days"),
                    body.getOrDefault("is_active", true));
        } else {
            jdbc.update("""
                UPDATE maintenance_template SET template_code=?, template_name=?, maintenance_level=?, maintenance_level_id=?::uuid,
                    category_id=?::uuid, items=?::jsonb, description=?, estimated_duration=?,
                    cycle_type=?, cycle_value=?, cycle_days=?, is_active=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("template_code"), body.get("template_name"), levelCode,
                    blankToNull(body.get("maintenance_level_id")), blankToNull(body.get("category_id")),
                    itemsJson, body.get("description"),
                    body.get("estimated_duration"), body.get("cycle_type"), body.get("cycle_value"), body.get("cycle_days"),
                    body.get("is_active"), id);
        }
        saveItems(id, items == null ? List.of() : items);
        return get(id);
    }

    private String resolveMaintenanceLevel(Map<String, Object> body) {
        Object raw = body.get("maintenance_level");
        if (raw != null && !raw.toString().isBlank()) return raw.toString().trim();
        Object levelId = blankToNull(body.get("maintenance_level_id"));
        if (levelId != null) {
            var rows = jdbc.queryForList(
                    "SELECT level_code FROM maintenance_level WHERE id = ?::uuid "
                            + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_level", null),
                    levelId);
            if (!rows.isEmpty() && rows.get(0).get("level_code") != null) {
                return rows.get(0).get("level_code").toString();
            }
        }
        return "L1";
    }

    private void saveItems(UUID templateId, List<Map<String, Object>> items) {
        jdbc.update("DELETE FROM maintenance_template_item WHERE template_id = ?::uuid", templateId);
        int sort = 0;
        for (Map<String, Object> item : items) {
            UUID itemId = resolveUuid(item.get("id"));
            if (itemId == null) itemId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO maintenance_template_item (id, template_id, item_code, item_name, item_content,
                        standard_value, check_method, sort_order, is_required, remark)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?)
                    """, itemId, templateId, item.get("item_code"), item.get("item_name"), item.get("item_content"),
                    item.get("standard_value"), item.get("check_method"),
                    item.getOrDefault("sort_order", sort++), item.getOrDefault("is_required", true), item.get("remark"));
        }
    }

    private static UUID resolveUuid(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        return UUID.fromString(s);
    }

    private static Object blankToNull(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }
}
