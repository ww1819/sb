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
@RequestMapping("/api/power/tag")
@RequiredArgsConstructor
public class PowerTagController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) UUID stationId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (Boolean.TRUE.equals(activeOnly)) {
            where.append(" AND t.is_active = true ");
        }
        if (stationId != null) {
            where.append(" AND t.station_id = ?::uuid ");
            args.add(stationId);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (t.tag_code ILIKE ? OR t.tag_name ILIKE ? OR t.device_code ILIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM power_tag t" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT t.*, s.station_name, d.device_name AS linked_device_name, dept.dept_name
                FROM power_tag t
                LEFT JOIN power_base_station s ON s.id = t.station_id
                LEFT JOIN medical_device d ON d.id = t.device_id
                LEFT JOIN department dept ON dept.id = d.dept_id
                """ + where + " ORDER BY t.tag_code LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM power_tag WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "power", description = "保存监测标签")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        fillDeviceInfo(body);
        boolean exists = !jdbc.queryForList("SELECT 1 FROM power_tag WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("""
                    UPDATE power_tag SET tag_code=?, tag_name=?, device_id=?::uuid, station_id=?::uuid,
                    rated_power=?, install_date=?, is_active=?, remark=?, updated_at=NOW()
                    WHERE id=?::uuid
                    """, body.get("tag_code"), body.get("tag_name"), body.get("device_id"), body.get("station_id"),
                    body.get("rated_power"), body.get("install_date"), body.getOrDefault("is_active", true),
                    body.get("remark"), id);
        } else {
            jdbc.update("""
                    INSERT INTO power_tag (id, tag_code, tag_name, device_id, station_id, rated_power, install_date, is_active, remark)
                    VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?,?,?)
                    """, id, body.get("tag_code"), body.get("tag_name"), body.get("device_id"), body.get("station_id"),
                    body.get("rated_power"), body.get("install_date"), body.getOrDefault("is_active", true), body.get("remark"));
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
