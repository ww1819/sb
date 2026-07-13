package com.meis.saas.maintain.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/pm/template")
@RequiredArgsConstructor
public class PmTemplateController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT t.*, l.type_name AS pm_type_name
                FROM pm_template t
                LEFT JOIN pm_type l ON l.id = t.pm_type_id
                WHERE t.id = ?::uuid
                """, id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("items", jdbc.queryForList(
                "SELECT * FROM pm_template_item WHERE template_id = ?::uuid ORDER BY sort_order, created_at", id));
        return Result.ok(result);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "pm", description = "保存预防性维护模板")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) throws Exception {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM pm_template WHERE id = ?::uuid", id).isEmpty();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        String itemsJson = mapper.writeValueAsString(items);
        if (!exists) {
            jdbc.update("""
                INSERT INTO pm_template (id, template_code, template_name, pm_type, pm_type_id,
                    category_id, items, description, estimated_duration, is_active)
                VALUES (?::uuid,?,?,?,?::uuid,?::uuid,?::jsonb,?,?,?)
                """, id, body.get("template_code"), body.get("template_name"),
                    body.getOrDefault("pm_type", "L1"), body.get("pm_type_id"),
                    body.get("category_id"), itemsJson, body.get("description"),
                    body.get("estimated_duration"), body.getOrDefault("is_active", true));
        } else {
            jdbc.update("""
                UPDATE pm_template SET template_code=?, template_name=?, pm_type=?, pm_type_id=?::uuid,
                    category_id=?::uuid, items=?::jsonb, description=?, estimated_duration=?, is_active=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("template_code"), body.get("template_name"), body.get("pm_type"),
                    body.get("pm_type_id"), body.get("category_id"), itemsJson, body.get("description"),
                    body.get("estimated_duration"), body.get("is_active"), id);
        }
        saveItems(id, items);
        return get(id);
    }

    private void saveItems(UUID templateId, List<Map<String, Object>> items) {
        jdbc.update("DELETE FROM pm_template_item WHERE template_id = ?::uuid", templateId);
        int sort = 0;
        for (Map<String, Object> item : items) {
            UUID itemId = item.containsKey("id") ? UUID.fromString(item.get("id").toString()) : UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO pm_template_item (id, template_id, item_code, item_name, item_content,
                        standard_value, check_method, sort_order, is_required, remark)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?)
                    """, itemId, templateId, item.get("item_code"), item.get("item_name"), item.get("item_content"),
                    item.get("standard_value"), item.get("check_method"),
                    item.getOrDefault("sort_order", sort++), item.getOrDefault("is_required", true), item.get("remark"));
        }
    }
}
