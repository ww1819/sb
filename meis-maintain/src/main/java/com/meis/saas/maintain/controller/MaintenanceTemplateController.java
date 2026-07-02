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
@RequestMapping("/api/maintain/template")
@RequiredArgsConstructor
public class MaintenanceTemplateController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM maintenance_template WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "maintain", description = "保存保养模板")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) throws Exception {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM maintenance_template WHERE id = ?::uuid", id).isEmpty();
        String itemsJson = mapper.writeValueAsString(body.getOrDefault("items", List.of()));
        if (!exists) {
            jdbc.update("INSERT INTO maintenance_template (id, template_name, maintenance_level, category_id, items, is_active) VALUES (?::uuid,?,?,?::uuid,?::jsonb,?)",
                    id, body.get("template_name"), body.getOrDefault("maintenance_level", "L1"),
                    body.get("category_id"), itemsJson, body.getOrDefault("is_active", true));
        } else {
            jdbc.update("UPDATE maintenance_template SET template_name=?, items=?::jsonb, is_active=?, updated_at=NOW() WHERE id=?::uuid",
                    body.get("template_name"), itemsJson, body.get("is_active"), id);
        }
        return get(id);
    }
}
