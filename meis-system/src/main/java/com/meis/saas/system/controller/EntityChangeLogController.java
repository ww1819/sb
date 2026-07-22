package com.meis.saas.system.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/system/entity-change-log")
@RequiredArgsConstructor
public class EntityChangeLogController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam String entityType,
            @RequestParam String entityId) {
        if ("public".equals(TenantContext.getSchemaName())) {
            throw new BizException(403, "tenant context required");
        }
        if (!EntityChangeLogService.TRACKED_TABLES.contains(entityType)) {
            throw new BizException(400, "entityType not tracked: " + entityType);
        }
        UUID id;
        try {
            id = UUID.fromString(entityId);
        } catch (Exception e) {
            throw new BizException(400, "invalid entityId");
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_entity_change_log WHERE entity_type = ? AND entity_id = ?",
                Long.class, entityType, id);
        List<Object> args = new ArrayList<>();
        args.add(entityType);
        args.add(id);
        args.add(query.limit());
        args.add(query.offset());
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, entity_type, entity_id, action, changed_fields, snapshot_json,
                       operator_id, operator_name, remark, created_at
                FROM sys_entity_change_log
                WHERE entity_type = ? AND entity_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, args.toArray());
        for (Map<String, Object> row : rows) {
            row.put("changed_fields", parseJson(row.get("changed_fields"), List.of()));
            row.put("snapshot_json", parseJson(row.get("snapshot_json"), null));
        }
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    private Object parseJson(Object raw, Object fallback) {
        if (raw == null) return fallback;
        try {
            String text;
            if (raw instanceof PGobject pg) {
                text = pg.getValue();
            } else if (raw instanceof String s) {
                text = s;
            } else if (raw instanceof List || raw instanceof Map) {
                return raw;
            } else {
                text = String.valueOf(raw);
            }
            if (text == null || text.isBlank()) return fallback;
            return mapper.readValue(text, new TypeReference<>() {});
        } catch (Exception e) {
            return fallback;
        }
    }
}
