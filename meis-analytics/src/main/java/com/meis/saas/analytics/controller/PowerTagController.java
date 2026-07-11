package com.meis.saas.analytics.controller;

import com.meis.saas.analytics.service.PowerReadingQueryService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/power/tag")
@RequiredArgsConstructor
public class PowerTagController {
    private static final String TAG_SELECT = """
            SELECT t.id, t.tag_code, t.tag_name, t.device_id, t.station_id,
                   t.rated_power, t.install_date, t.is_active, t.remark, t.created_at, t.updated_at,
                   COALESCE(t.device_code, d.device_code) AS device_code,
                   COALESCE(t.device_name, d.device_name) AS device_name,
                   s.station_name, s.station_code,
                   d.specification, d.model, d.serial_number,
                   m.manufacturer_name, dept.dept_name,
                   d.standby_current_max_ma, d.standby_current_min_ma
            FROM power_tag t
            LEFT JOIN power_base_station s ON s.id = t.station_id
            LEFT JOIN medical_device d ON d.id = t.device_id
            LEFT JOIN manufacturer m ON m.id = d.manufacturer_id
            LEFT JOIN department dept ON dept.id = d.dept_id
            """;

    private final JdbcTemplate jdbc;
    private final PowerReadingQueryService readingQuery;

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
            where.append("""
                     AND (t.tag_code ILIKE ? OR t.tag_name ILIKE ? OR t.device_code ILIKE ?
                     OR d.device_name ILIKE ? OR d.specification ILIKE ? OR d.model ILIKE ?)
                    """);
            for (int i = 0; i < 6; i++) {
                args.add(kw);
            }
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM power_tag t"
                + " LEFT JOIN medical_device d ON d.id = t.device_id" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList(TAG_SELECT + where + " ORDER BY t.tag_code LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(TAG_SELECT + " WHERE t.id = ?::uuid", id);
        if (rows.isEmpty()) {
            throw new BizException(404, "not found");
        }
        return Result.ok(rows.get(0));
    }

    @GetMapping("/{id}/readings/page")
    public Result<PageResult<Map<String, Object>>> readingsPage(@PathVariable UUID id, PageQuery query,
            @RequestParam(required = false) LocalDateTime readAtFrom,
            @RequestParam(required = false) LocalDateTime readAtTo,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder) {
        ensureTag(id);
        return Result.ok(readingQuery.pageByTag(id, query, readAtFrom, readAtTo, sortOrder));
    }

    @GetMapping("/{id}/readings/export")
    public void exportReadings(@PathVariable UUID id, HttpServletResponse response,
            @RequestParam(required = false) LocalDateTime readAtFrom,
            @RequestParam(required = false) LocalDateTime readAtTo,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder) throws IOException {
        ensureTag(id);
        var rows = readingQuery.listByTagForExport(id, readAtFrom, readAtTo, sortOrder);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=tag_readings_" + id + ".csv");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try (PrintWriter w = response.getWriter()) {
            w.write('\ufeff');
            w.println("标签编码,基站编码,设备ID,设备编码,电流(mA),读取时间,插入时间");
            for (Map<String, Object> row : rows) {
                w.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        csv(row.get("tag_code")),
                        csv(row.get("station_code")),
                        csv(row.get("device_id")),
                        csv(row.get("device_code")),
                        csv(row.get("current_ma")),
                        formatTime(row.get("read_at"), fmt),
                        formatTime(row.get("created_at"), fmt));
            }
        }
    }

    @GetMapping("/{id}/bind-log")
    public Result<List<Map<String, Object>>> bindLog(@PathVariable UUID id) {
        ensureTag(id);
        var rows = jdbc.queryForList("""
                SELECT id, tag_id, device_id, device_code, device_name, bound_at, unbound_at, operator_id, remark
                FROM power_tag_bind_log
                WHERE tag_id = ?::uuid
                ORDER BY bound_at DESC
                """, id);
        return Result.ok(rows);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "power", description = "保存监测标签")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = resolveTagId(body);
        boolean exists = tagExists(id);
        UUID oldDeviceId = null;
        if (exists) {
            var oldRows = jdbc.queryForList("SELECT device_id FROM power_tag WHERE id = ?::uuid", id);
            if (!oldRows.isEmpty()) {
                oldDeviceId = parseUuidFromDb(oldRows.get(0).get("device_id"));
            }
        }
        fillDeviceInfo(body);
        UUID newDeviceId = parseUuid(body.get("device_id"));
        normalizeTagName(body);
        if (exists) {
            int updated = jdbc.update("""
                    UPDATE power_tag SET tag_code=?, tag_name=?, device_id=?::uuid, station_id=?::uuid,
                    device_code=?, device_name=?, rated_power=?, install_date=?, is_active=?, remark=?, updated_at=NOW()
                    WHERE id=?::uuid
                    """, body.get("tag_code"), body.get("tag_name"), body.get("device_id"), body.get("station_id"),
                    body.get("device_code"), body.get("device_name"), body.get("rated_power"), body.get("install_date"),
                    body.getOrDefault("is_active", true), body.get("remark"), id);
            if (updated == 0) {
                throw new BizException(404, "tag not found");
            }
        } else {
            jdbc.update("""
                    INSERT INTO power_tag (id, tag_code, tag_name, device_id, station_id, device_code, device_name,
                    rated_power, install_date, is_active, remark)
                    VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?,?,?,?,?)
                    """, id, body.get("tag_code"), body.get("tag_name"), body.get("device_id"), body.get("station_id"),
                    body.get("device_code"), body.get("device_name"), body.get("rated_power"), body.get("install_date"),
                    body.getOrDefault("is_active", true), body.get("remark"));
        }
        recordBindChange(id, oldDeviceId, newDeviceId, body);
        return get(id);
    }

    @PutMapping("/{id}/standby-limits")
    @Transactional
    @OperationLog(module = "power", description = "维护关联设备待机电流上下限")
    public Result<Map<String, Object>> updateStandbyLimits(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        ensureTag(id);
        var tagRows = jdbc.queryForList("SELECT device_id FROM power_tag WHERE id = ?::uuid", id);
        if (tagRows.isEmpty()) {
            throw new BizException(404, "not found");
        }
        UUID deviceId = parseUuidFromDb(tagRows.get(0).get("device_id"));
        if (deviceId == null) {
            throw new BizException(400, "tag has no linked device");
        }
        jdbc.update("""
                UPDATE medical_device SET standby_current_max_ma=?, standby_current_min_ma=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("standby_current_max_ma"), body.get("standby_current_min_ma"), deviceId);
        return get(id);
    }

    private void recordBindChange(UUID tagId, UUID oldDeviceId, UUID newDeviceId, Map<String, Object> body) {
        if (deviceIdChanged(oldDeviceId, newDeviceId)) {
            if (oldDeviceId != null) {
                jdbc.update("""
                        UPDATE power_tag_bind_log SET unbound_at = NOW()
                        WHERE tag_id = ?::uuid AND device_id = ?::uuid AND unbound_at IS NULL
                        """, tagId, oldDeviceId);
            }
            if (newDeviceId != null) {
                insertBindLog(tagId, newDeviceId, body, body.get("bind_remark"));
            }
            return;
        }
        if (newDeviceId == null) {
            return;
        }
        Long active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM power_tag_bind_log
                WHERE tag_id = ?::uuid AND device_id = ?::uuid AND unbound_at IS NULL
                """, Long.class, tagId, newDeviceId);
        if (active == null || active == 0) {
            insertBindLog(tagId, newDeviceId, body, "history-sync");
        }
    }

    private void insertBindLog(UUID tagId, UUID deviceId, Map<String, Object> body, Object remark) {
        UUID safeDeviceId = resolveExistingDeviceId(deviceId);
        jdbc.update("""
                INSERT INTO power_tag_bind_log (tag_id, device_id, device_code, device_name, remark)
                VALUES (?::uuid, ?::uuid, ?, ?, ?)
                """, tagId, safeDeviceId, body.get("device_code"), body.get("device_name"), remark);
    }

    private UUID resolveExistingDeviceId(UUID deviceId) {
        if (deviceId == null) {
            return null;
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM medical_device WHERE id = ?::uuid", Long.class, deviceId);
        if (count == null || count == 0) {
            return null;
        }
        return deviceId;
    }

    /** 优先按 tag_code 解析已有标签，避免编辑时 id 缺失导致误 INSERT。 */
    private UUID resolveTagId(Map<String, Object> body) {
        Object code = body.get("tag_code");
        if (code != null && !code.toString().isBlank()) {
            var rows = jdbc.queryForList(
                    "SELECT id FROM power_tag WHERE tag_code = ?", code.toString().trim());
            if (!rows.isEmpty()) {
                UUID existing = parseUuidFromDb(rows.get(0).get("id"));
                body.put("id", existing.toString());
                return existing;
            }
        }
        Object idRaw = body.get("id");
        if (idRaw != null && !idRaw.toString().isBlank()) {
            try {
                UUID parsed = UUID.fromString(idRaw.toString().trim());
                if (tagExists(parsed)) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                // 非 UUID 格式，走新建
            }
        }
        UUID newId = UUID.randomUUID();
        body.put("id", newId.toString());
        return newId;
    }

    private boolean tagExists(UUID id) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM power_tag WHERE id = ?::uuid", Long.class, id);
        return count != null && count > 0;
    }

    private static void normalizeTagName(Map<String, Object> body) {
        Object name = body.get("tag_name");
        Object code = body.get("tag_code");
        if (name == null || name.toString().isBlank()) {
            if (code != null && !code.toString().isBlank()) {
                body.put("tag_name", code.toString().trim());
            }
            return;
        }
        body.put("tag_name", name.toString().trim());
    }

    private static boolean deviceIdChanged(UUID oldId, UUID newId) {
        String oldKey = oldId == null ? "" : oldId.toString();
        String newKey = newId == null ? "" : newId.toString();
        return !oldKey.equals(newKey);
    }

    private static UUID parseUuidFromDb(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof UUID uuid) {
            return uuid;
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return UUID.fromString(s);
    }

    private void fillDeviceInfo(Map<String, Object> body) {
        Object deviceIdRaw = body.get("device_id");
        if (deviceIdRaw == null || deviceIdRaw.toString().isBlank()) {
            body.put("device_id", null);
            body.put("device_code", null);
            body.put("device_name", null);
            return;
        }
        UUID deviceId = UUID.fromString(deviceIdRaw.toString());
        var rows = jdbc.queryForList("""
                SELECT device_code, device_name
                FROM medical_device WHERE id = ?::uuid
                """, deviceId);
        if (!rows.isEmpty()) {
            var d = rows.get(0);
            body.put("device_id", deviceId.toString());
            body.put("device_code", d.get("device_code"));
            body.put("device_name", d.get("device_name"));
        } else {
            throw new BizException(400, "关联设备不存在，请重新选择");
        }
    }

    private void ensureTag(UUID id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM power_tag WHERE id = ?::uuid", Long.class, id) == 0) {
            throw new BizException(404, "not found");
        }
    }

    private static UUID parseUuid(Object v) {
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return UUID.fromString(v.toString());
    }

    private static String csv(Object v) {
        if (v == null) {
            return "";
        }
        String s = v.toString();
        if (s.contains(",") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String formatTime(Object v, DateTimeFormatter fmt) {
        if (v == null) {
            return "";
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(fmt);
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.format(fmt);
        }
        return v.toString();
    }
}
