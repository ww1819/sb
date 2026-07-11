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
@RequestMapping("/api/power/station")
@RequiredArgsConstructor
public class PowerStationController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) Boolean activeOnly) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (Boolean.TRUE.equals(activeOnly)) {
            where.append(" AND s.is_active = true ");
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (s.station_code ILIKE ? OR s.station_name ILIKE ? OR s.location ILIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM power_base_station s" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT s.*, c.campus_name,
                       (SELECT COUNT(*) FROM power_tag t WHERE t.station_id = s.id AND t.is_active = true) AS tag_count
                FROM power_base_station s
                LEFT JOIN campus c ON c.id = s.campus_id
                """ + where + " ORDER BY s.station_code LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM power_base_station WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "power", description = "保存监测基站")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM power_base_station WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("""
                    UPDATE power_base_station SET station_code=?, station_name=?, campus_id=?::uuid, location=?,
                    ip_address=?, protocol_type=?, status=?, is_active=?, remark=?, updated_at=NOW()
                    WHERE id=?::uuid
                    """, body.get("station_code"), body.get("station_name"), body.get("campus_id"),
                    body.get("location"), body.get("ip_address"), body.getOrDefault("protocol_type", "mqtt"),
                    body.getOrDefault("status", "online"), body.getOrDefault("is_active", true),
                    body.get("remark"), id);
        } else {
            jdbc.update("""
                    INSERT INTO power_base_station (id, station_code, station_name, campus_id, location, ip_address,
                    protocol_type, status, is_active, remark)
                    VALUES (?::uuid,?,?,?::uuid,?,?,?,?,?,?)
                    """, id, body.get("station_code"), body.get("station_name"), body.get("campus_id"),
                    body.get("location"), body.get("ip_address"), body.getOrDefault("protocol_type", "mqtt"),
                    body.getOrDefault("status", "online"), body.getOrDefault("is_active", true), body.get("remark"));
        }
        return get(id);
    }
}
