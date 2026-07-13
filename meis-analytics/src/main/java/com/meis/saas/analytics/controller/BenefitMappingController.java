package com.meis.saas.analytics.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/analytics/mapping")
@RequiredArgsConstructor
public class BenefitMappingController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) Boolean activeOnly) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (Boolean.TRUE.equals(activeOnly)) {
            where.append(" AND m.is_active = true ");
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (m.device_code ILIKE ? OR m.his_item_name ILIKE ? OR m.charge_code ILIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM benefit_mapping m" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT m.*, d.device_name AS linked_device_name, dept.dept_name
                FROM benefit_mapping m
                LEFT JOIN medical_device d ON d.id = m.device_id
                LEFT JOIN department dept ON dept.id = d.dept_id
                """ + where + " ORDER BY m.created_at DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM benefit_mapping WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "analytics", description = "保存收费对照")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        fillDeviceInfo(body);
        boolean exists = !jdbc.queryForList("SELECT 1 FROM benefit_mapping WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("""
                    UPDATE benefit_mapping SET device_id=?::uuid, device_code=?, device_name=?, his_item_code=?,
                    his_item_name=?, pacs_modality=?, charge_code=?, charge_name=?, unit_price=?, is_active=?, remark=?, updated_at=NOW()
                    WHERE id=?::uuid
                    """, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                    body.get("his_item_code"), body.get("his_item_name"), body.get("pacs_modality"),
                    body.get("charge_code"), body.get("charge_name"), body.getOrDefault("unit_price", 0),
                    body.getOrDefault("is_active", true), body.get("remark"), id);
        } else {
            jdbc.update("""
                    INSERT INTO benefit_mapping (id, device_id, device_code, device_name, his_item_code, his_item_name,
                    pacs_modality, charge_code, charge_name, unit_price, is_active, remark)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?)
                    """, id, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                    body.get("his_item_code"), body.get("his_item_name"), body.get("pacs_modality"),
                    body.get("charge_code"), body.get("charge_name"), body.getOrDefault("unit_price", 0),
                    body.getOrDefault("is_active", true), body.get("remark"));
        }
        return get(id);
    }

    private void fillDeviceInfo(Map<String, Object> body) {
        if (body.get("device_id") == null) return;
        var rows = jdbc.queryForList("SELECT device_code, device_name FROM medical_device WHERE id = ?::uuid",
                UUID.fromString(body.get("device_id").toString()));
        if (!rows.isEmpty()) {
            body.putIfAbsent("device_code", rows.get(0).get("device_code"));
            body.putIfAbsent("device_name", rows.get(0).get("device_name"));
        }
    }
}
