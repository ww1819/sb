package com.meis.saas.analytics.controller;

import com.meis.saas.analytics.service.PowerReadingQueryService;
import com.meis.saas.common.audit.DocChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.ops.OpsClientChannel;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
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
    private final DocChangeLogService docLog;

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
        var rows = jdbc.queryForList(stationSelect() + where + " ORDER BY s.station_code LIMIT ? OFFSET ?", args.toArray());
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
        String where = " WHERE s.id = ?::uuid " + SoftDeleteSupport.notDeletedClause(jdbc, "power_base_station", "s");
        var rows = jdbc.queryForList(stationSelect() + where, id);
        if (rows.isEmpty()) {
            throw new BizException(404, "not found");
        }
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "power", description = "保存监测基站")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        normalizeStationCode(body);
        String channel = OpsClientChannel.of(body);
        boolean hasCreate = TableColumnCache.hasColumn(jdbc, "power_base_station", "create_channel");
        boolean hasUpdate = TableColumnCache.hasColumn(jdbc, "power_base_station", "update_channel");

        UUID id = null;
        boolean exists = false;
        String existingCode = null;

        Object idRaw = body.get("id");
        if (idRaw != null && !idRaw.toString().isBlank()) {
            id = UUID.fromString(idRaw.toString().trim());
            var rows = jdbc.queryForList(
                    "SELECT station_code FROM power_base_station WHERE id = ?::uuid", id);
            if (!rows.isEmpty()) {
                exists = true;
                existingCode = rows.get(0).get("station_code") != null
                        ? rows.get(0).get("station_code").toString() : null;
            }
        }

        // 按编码命中（含软删）优先，便于同码恢复
        Object codeObj = body.get("station_code");
        if (codeObj != null && !codeObj.toString().isBlank()) {
            var byCode = jdbc.queryForList(
                    "SELECT id, station_code FROM power_base_station WHERE station_code = ? "
                            + "ORDER BY is_deleted ASC, deleted_at NULLS FIRST LIMIT 1",
                    codeObj.toString().trim());
            if (!byCode.isEmpty()) {
                id = UUID.fromString(byCode.get(0).get("id").toString());
                exists = true;
                existingCode = byCode.get(0).get("station_code") != null
                        ? byCode.get(0).get("station_code").toString() : null;
                body.put("id", id.toString());
            }
        }

        if (id == null) {
            id = UUID.randomUUID();
            body.put("id", id.toString());
        }

        enforceStationCodeImmutable(exists, existingCode, body);

        Object campusId = blankToNull(body.get("campus_id"));
        Object protocol = body.getOrDefault("protocol_type", "mqtt");
        Object status = body.getOrDefault("status", "online");
        Object active = body.getOrDefault("is_active", true);

        if (exists) {
            String sql = """
                    UPDATE power_base_station SET station_code=?, station_name=?, campus_id=?::uuid, location=?,
                    ip_address=?, protocol_type=?, status=?, is_active=?, remark=?,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    """;
            List<Object> args = new ArrayList<>();
            args.add(body.get("station_code"));
            args.add(body.get("station_name"));
            args.add(campusId);
            args.add(body.get("location"));
            args.add(body.get("ip_address"));
            args.add(protocol);
            args.add(status);
            args.add(active);
            args.add(body.get("remark"));
            args.add(SoftDeleteSupport.currentUserId());
            if (hasUpdate) {
                sql += ", update_channel=? ";
                args.add(channel);
            }
            sql += " WHERE id=?::uuid";
            args.add(id);
            jdbc.update(sql, args.toArray());
            docLog.event("power", "station", id, Objects.toString(body.get("station_code"), null),
                    "update", channel, null);
            return get(id);
        }

        SoftDeleteSupport.applyInsertAudit(jdbc, "power_base_station", body);
        List<String> cols = new ArrayList<>();
        cols.add("id");
        cols.add("station_code");
        cols.add("station_name");
        cols.add("campus_id");
        cols.add("location");
        cols.add("ip_address");
        cols.add("protocol_type");
        cols.add("status");
        cols.add("is_active");
        cols.add("remark");
        cols.add("created_by");
        cols.add("is_deleted");
        List<String> ph = new ArrayList<>();
        ph.add("?::uuid");
        ph.add("?");
        ph.add("?");
        ph.add("?::uuid");
        ph.add("?");
        ph.add("?");
        ph.add("?");
        ph.add("?");
        ph.add("?");
        ph.add("?");
        ph.add("?::uuid");
        ph.add("?");
        List<Object> args = new ArrayList<>();
        args.add(id);
        args.add(body.get("station_code"));
        args.add(body.get("station_name"));
        args.add(campusId);
        args.add(body.get("location"));
        args.add(body.get("ip_address"));
        args.add(protocol);
        args.add(status);
        args.add(active);
        args.add(body.get("remark"));
        args.add(SoftDeleteSupport.currentUserId());
        args.add(0);
        if (hasCreate) {
            cols.add("create_channel");
            ph.add("?");
            args.add(channel);
        }
        if (hasUpdate) {
            cols.add("update_channel");
            ph.add("?");
            args.add(channel);
        }
        jdbc.update("INSERT INTO power_base_station (" + String.join(", ", cols) + ") VALUES ("
                + String.join(", ", ph) + ")", args.toArray());
        docLog.event("power", "station", id, Objects.toString(body.get("station_code"), null),
                "create", channel, null);
        return get(id);
    }

    private String stationSelect() {
        boolean hasChannel = TableColumnCache.hasColumn(jdbc, "power_base_station", "create_channel");
        String channels = hasChannel
                ? " s.create_channel, s.update_channel, "
                : " CAST(NULL AS VARCHAR) AS create_channel, CAST(NULL AS VARCHAR) AS update_channel, ";
        return """
                SELECT s.id, s.station_code, s.station_name, s.campus_id, s.location, s.ip_address,
                       s.protocol_type, s.status, s.is_active, s.remark, s.created_at, s.updated_at,
                """ + channels + """
                       c.campus_name,
                       (SELECT COUNT(*) FROM power_tag t WHERE t.station_id = s.id AND t.is_active = true
                        AND COALESCE(t.is_deleted, 0) = 0) AS tag_count
                FROM power_base_station s
                LEFT JOIN campus c ON c.id = s.campus_id
                """;
    }

    private static void normalizeStationCode(Map<String, Object> body) {
        Object code = body.get("station_code");
        if (code == null || code.toString().isBlank()) {
            throw new BizException(400, "请填写基站编码");
        }
        body.put("station_code", code.toString().trim());
        Object name = body.get("station_name");
        if (name == null || name.toString().isBlank()) {
            throw new BizException(400, "请填写基站名称");
        }
        body.put("station_name", name.toString().trim());
    }

    private static void enforceStationCodeImmutable(boolean exists, String existingCode, Map<String, Object> body) {
        if (!exists || existingCode == null || existingCode.isBlank()) {
            return;
        }
        String bodyCode = body.get("station_code") != null ? body.get("station_code").toString().trim() : "";
        if (!existingCode.equals(bodyCode)) {
            throw new BizException(400, "基站编码不可修改");
        }
    }

    private static Object blankToNull(Object v) {
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return v;
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
