package com.meis.saas.analytics.controller;

import com.meis.saas.analytics.service.PowerReadingQueryService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/power/station")
@RequiredArgsConstructor
public class PowerStationController {
    private final JdbcTemplate jdbc;
    private final PowerReadingQueryService readingQuery;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) Boolean activeOnly) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "power_base_station", "s"));
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

    @GetMapping("/{id}/tags")
    public Result<List<Map<String, Object>>> tags(@PathVariable UUID id) {
        ensureStation(id);
        var rows = jdbc.queryForList("""
                SELECT t.id, t.tag_code, t.tag_name, t.device_code, t.device_name, t.is_active, t.install_date,
                       d.specification, d.model
                FROM power_tag t
                LEFT JOIN medical_device d ON d.id = t.device_id
                WHERE t.station_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "power_tag", "t") + """
                ORDER BY t.tag_code
                """, id);
        return Result.ok(rows);
    }

    @GetMapping("/{id}/readings/page")
    public Result<PageResult<Map<String, Object>>> readingsPage(@PathVariable UUID id, PageQuery query,
            @RequestParam(required = false) LocalDateTime readAtFrom,
            @RequestParam(required = false) LocalDateTime readAtTo,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder) {
        ensureStation(id);
        return Result.ok(readingQuery.pageByStation(id, query, readAtFrom, readAtTo, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM power_base_station WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "power_base_station", null), id);
        if (rows.isEmpty()) {
            throw new BizException(404, "not found");
        }
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "power", description = "保存监测基站")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        boolean hasId = body.containsKey("id") && body.get("id") != null;
        UUID id = hasId ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = hasId && !jdbc.queryForList(
                "SELECT 1 FROM power_base_station WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "power_base_station", null), id).isEmpty();
        if (exists) {
            jdbc.update("""
                    UPDATE power_base_station SET station_code=?, station_name=?, campus_id=?::uuid, location=?,
                    ip_address=?, protocol_type=?, status=?, is_active=?, remark=?,
                    updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, body.get("station_code"), body.get("station_name"), body.get("campus_id"),
                    body.get("location"), body.get("ip_address"), body.getOrDefault("protocol_type", "mqtt"),
                    body.getOrDefault("status", "online"), body.getOrDefault("is_active", true),
                    body.get("remark"), SoftDeleteSupport.currentUserId(), id);
            return get(id);
        }
        SoftDeleteSupport.applyInsertAudit(jdbc, "power_base_station", body);
        var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "power_base_station", body);
        if (softDeletedId.isPresent()) {
            id = UUID.fromString(softDeletedId.get());
            jdbc.update("""
                    UPDATE power_base_station SET station_code=?, station_name=?, campus_id=?::uuid, location=?,
                    ip_address=?, protocol_type=?, status=?, is_active=?, remark=?,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, body.get("station_code"), body.get("station_name"), body.get("campus_id"),
                    body.get("location"), body.get("ip_address"), body.getOrDefault("protocol_type", "mqtt"),
                    body.getOrDefault("status", "online"), body.getOrDefault("is_active", true),
                    body.get("remark"), SoftDeleteSupport.currentUserId(), id);
            return get(id);
        }
        jdbc.update("""
                INSERT INTO power_base_station (id, station_code, station_name, campus_id, location, ip_address,
                protocol_type, status, is_active, remark, created_by, is_deleted)
                VALUES (?::uuid,?,?,?::uuid,?,?,?,?,?,?,?::uuid,?)
                """, id, body.get("station_code"), body.get("station_name"), body.get("campus_id"),
                body.get("location"), body.get("ip_address"), body.getOrDefault("protocol_type", "mqtt"),
                body.getOrDefault("status", "online"), body.getOrDefault("is_active", true), body.get("remark"),
                SoftDeleteSupport.currentUserId(), 0);
        return get(id);
    }

    private void ensureStation(UUID id) {
        if (jdbc.queryForObject(
                "SELECT COUNT(*) FROM power_base_station WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "power_base_station", null),
                Long.class, id) == 0) {
            throw new BizException(404, "not found");
        }
    }
}
