package com.meis.saas.qc.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/metrology/template")
@RequiredArgsConstructor
public class MetrologyTemplateController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT t.*, c.category_name
                FROM metrology_template t
                LEFT JOIN metrology_category c ON c.id = t.category_id
                WHERE t.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_template", "t"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("items", jdbc.queryForList(
                "SELECT * FROM metrology_template_item WHERE template_id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_template_item", null)
                        + " ORDER BY sort_order, created_at", id));
        return Result.ok(result);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "metrology", description = "保存计量模板")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = resolveUuid(body.get("id"));
        if (id == null) id = UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM metrology_template WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_template", null), id).isEmpty();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        if (!exists) {
            jdbc.update("""
                INSERT INTO metrology_template (id, template_code, template_name, category_id, description, estimated_duration, is_active)
                VALUES (?::uuid,?,?,?::uuid,?,?,?)
                """, id, body.get("template_code"), body.get("template_name"), blankToNull(body.get("category_id")),
                    body.get("description"), body.get("estimated_duration"), body.getOrDefault("is_active", true));
        } else {
            jdbc.update("""
                UPDATE metrology_template SET template_code=?, template_name=?, category_id=?::uuid, description=?, estimated_duration=?, is_active=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("template_code"), body.get("template_name"), blankToNull(body.get("category_id")),
                    body.get("description"), body.get("estimated_duration"), body.get("is_active"), id);
        }
        saveItems(id, items == null ? List.of() : items);
        return get(id);
    }

    private void saveItems(UUID templateId, List<Map<String, Object>> items) {
        jdbc.update("DELETE FROM metrology_template_item WHERE template_id = ?::uuid", templateId);
        int sort = 0;
        for (Map<String, Object> item : items) {
            UUID itemId = resolveUuid(item.get("id"));
            if (itemId == null) itemId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO metrology_template_item (id, template_id, item_code, item_name, item_content,
                        standard_value, tolerance_range, sort_order, is_required, remark)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?)
                    """, itemId, templateId, item.get("item_code"), item.get("item_name"), item.get("item_content"),
                    item.get("standard_value"), item.get("tolerance_range"),
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
