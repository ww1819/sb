package com.meis.saas.analytics.controller;

import com.meis.saas.analytics.service.PowerReadingQueryService;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/power/device")
@RequiredArgsConstructor
public class PowerDeviceReadingController {
    private final PowerReadingQueryService readingQuery;
    private final JdbcTemplate jdbc;

    @GetMapping("/{deviceId}/readings/page")
    public Result<PageResult<Map<String, Object>>> readingsPage(
            @PathVariable UUID deviceId,
            PageQuery query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate readAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate readAtTo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime readAtFromTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime readAtToTime,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder) {
        LocalDateTime from = readAtFromTime != null
                ? readAtFromTime
                : (readAtFrom != null ? readAtFrom.atStartOfDay() : null);
        LocalDateTime to = readAtToTime != null
                ? readAtToTime
                : (readAtTo != null ? readAtTo.atTime(LocalTime.MAX) : null);
        return Result.ok(readingQuery.pageByDevice(deviceId, query, from, to, sortOrder));
    }

    /** AST-UI-14：按设备查电流标签绑定记录 */
    @GetMapping("/{deviceId}/bind-log")
    public Result<List<Map<String, Object>>> bindLog(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT l.id, l.tag_id, t.tag_code, t.tag_name,
                       l.device_id, l.device_code, l.device_name,
                       l.bound_at, l.unbound_at, l.operator_id, l.remark
                FROM power_tag_bind_log l
                LEFT JOIN power_tag t ON t.id = l.tag_id
                WHERE l.device_id = ?::uuid
                ORDER BY l.bound_at DESC
                """, deviceId));
    }

    /** AST-UI-17：当前设备绑定的未软删电流监测标签（无则 data=null） */
    @GetMapping("/{deviceId}/tag")
    public Result<Map<String, Object>> currentTag(@PathVariable UUID deviceId) {
        String sql = """
                SELECT t.id, t.tag_code, t.tag_name, t.device_id, t.station_id,
                       t.rated_power, t.install_date, t.is_active, t.remark,
                       COALESCE(t.device_code, d.device_code) AS device_code,
                       COALESCE(t.device_name, d.device_name) AS device_name,
                       d.standby_current_max_ma, d.standby_current_min_ma
                FROM power_tag t
                LEFT JOIN medical_device d ON d.id = t.device_id
                WHERE t.device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "power_tag", "t") + """
                ORDER BY t.updated_at DESC NULLS LAST, t.created_at DESC NULLS LAST
                LIMIT 1
                """;
        var rows = jdbc.queryForList(sql, deviceId);
        return Result.ok(rows.isEmpty() ? null : rows.get(0));
    }
}
