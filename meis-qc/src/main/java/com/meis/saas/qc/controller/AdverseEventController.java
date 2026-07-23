package com.meis.saas.qc.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/qc/adverse")
@RequiredArgsConstructor
public class AdverseEventController {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "reported", Set.of("handling"),
            "handling", Set.of("reviewed"),
            "reviewed", Set.of("closed"));

    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severityLevel,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) Boolean openOnly) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "adverse_event", "a"));
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (a.event_no ILIKE ? OR a.device_name ILIKE ? OR a.device_code ILIKE ? OR a.event_description ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        FilterCsvSupport.appendStrIn(where, args, "a.status", status);
        FilterCsvSupport.appendStrIn(where, args, "a.severity_level", severityLevel);
        FilterCsvSupport.appendStrIn(where, args, "a.event_type", eventType);
        if (deviceId != null && !deviceId.isBlank()) {
            where.append(" AND a.device_id = ?::uuid ");
            args.add(deviceId);
        }
        if (deviceCode != null && !deviceCode.isBlank()) {
            where.append(" AND a.device_code ILIKE ? ");
            args.add("%" + deviceCode.trim() + "%");
        }
        if (Boolean.TRUE.equals(openOnly)) {
            where.append(" AND a.status <> 'closed' ");
        }
        String from = """
            FROM adverse_event a
            LEFT JOIN medical_device d ON d.id = a.device_id
            LEFT JOIN department dept ON dept.id = d.dept_id
            LEFT JOIN sys_user reporter ON reporter.id = a.reporter_id
            """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT a.*, dept.dept_name,
                   COALESCE(NULLIF(TRIM(a.reporter_name), ''), reporter.real_name, reporter.username) AS reporter_name
            """ + from + where + " ORDER BY a.report_time DESC NULLS LAST, a.created_at DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        String nd = SoftDeleteSupport.notDeletedClause(jdbc, "adverse_event", null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", jdbc.queryForObject("SELECT COUNT(*) FROM adverse_event WHERE 1=1" + nd, Long.class));
        result.put("open_count", jdbc.queryForObject(
                "SELECT COUNT(*) FROM adverse_event WHERE status <> 'closed'" + nd, Long.class));
        result.put("reported_count", jdbc.queryForObject(
                "SELECT COUNT(*) FROM adverse_event WHERE status = 'reported'" + nd, Long.class));
        result.put("authority_count", jdbc.queryForObject(
                "SELECT COUNT(*) FROM adverse_event WHERE reported_to_authority = true" + nd, Long.class));
        result.put("by_status", jdbc.queryForList(
                "SELECT status, COUNT(*) AS count FROM adverse_event WHERE 1=1" + nd
                        + " GROUP BY status ORDER BY count DESC"));
        result.put("by_severity", jdbc.queryForList(
                "SELECT severity_level, COUNT(*) AS count FROM adverse_event WHERE 1=1" + nd
                        + " GROUP BY severity_level ORDER BY count DESC"));
        result.put("by_type", jdbc.queryForList(
                "SELECT event_type, COUNT(*) AS count FROM adverse_event WHERE 1=1" + nd
                        + " GROUP BY event_type ORDER BY count DESC"));
        return Result.ok(result);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
            SELECT a.*, dept.dept_name,
                   COALESCE(NULLIF(TRIM(a.reporter_name), ''), reporter.real_name, reporter.username) AS reporter_name,
                   COALESCE(NULLIF(TRIM(a.handler_name), ''), handler.real_name, handler.username) AS handler_name,
                   COALESCE(NULLIF(TRIM(a.reviewer_name), ''), reviewer.real_name, reviewer.username) AS reviewer_name
            FROM adverse_event a
            LEFT JOIN medical_device d ON d.id = a.device_id
            LEFT JOIN department dept ON dept.id = d.dept_id
            LEFT JOIN sys_user reporter ON reporter.id = a.reporter_id
            LEFT JOIN sys_user handler ON handler.id = a.handler_id
            LEFT JOIN sys_user reviewer ON reviewer.id = a.reviewer_id
            WHERE a.id = ?::uuid
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "adverse_event", "a"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "qc", description = "保存不良事件")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null && !body.get("id").toString().isBlank()
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM adverse_event WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "adverse_event", null), id).isEmpty();
        fillDeviceSnapshot(body);
        String userId = TenantContext.getUserId();
        Object reporterId = body.get("reporter_id") != null ? body.get("reporter_id")
                : (userId != null ? userId : null);
        String reporterName = SoftDeleteSupport.resolveUserDisplayName(jdbc, reporterId);

        if (!exists) {
            jdbc.update("""
                INSERT INTO adverse_event (id, event_no, device_id, device_code, device_name,
                reporter_id, reporter_name, report_time,
                event_type, severity_level, event_description, cause_analysis, impact_description, photos, status, remark)
                VALUES (?::uuid,?,?::uuid,?,?,?::uuid,?,COALESCE(?::timestamptz, NOW()),?,?,?,?,?,?,'reported',?)
                """,
                    id, body.getOrDefault("event_no", "AE" + System.currentTimeMillis()),
                    body.get("device_id"), body.get("device_code"), body.get("device_name"), reporterId, reporterName,
                    body.get("report_time"), body.get("event_type"), body.get("severity_level"),
                    body.get("event_description"), body.get("cause_analysis"), body.get("impact_description"),
                    body.get("photos"), body.get("remark"));
        } else {
            jdbc.update("""
                UPDATE adverse_event SET device_id=?::uuid, device_code=?, device_name=?,
                reporter_id=?::uuid, reporter_name=?,
                report_time=COALESCE(?::timestamptz, report_time), event_type=?, severity_level=?,
                event_description=?, cause_analysis=?, impact_description=?, photos=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid AND status = 'reported'
                """,
                    body.get("device_id"), body.get("device_code"), body.get("device_name"), reporterId, reporterName,
                    body.get("report_time"), body.get("event_type"), body.get("severity_level"),
                    body.get("event_description"), body.get("cause_analysis"), body.get("impact_description"),
                    body.get("photos"), body.get("remark"), id);
        }
        return get(id);
    }

    @PostMapping("/{id}/transition")
    @Transactional
    @OperationLog(module = "qc", description = "不良事件状态流转")
    public Result<Map<String, Object>> transition(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var rows = jdbc.queryForList(
                "SELECT status FROM adverse_event WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "adverse_event", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        String cur = String.valueOf(rows.get(0).get("status"));
        String target = String.valueOf(body.get("status"));
        if (!TRANSITIONS.getOrDefault(cur, Set.of()).contains(target)) {
            throw new BizException(400, "invalid transition");
        }
        String userId = TenantContext.getUserId();
        UUID actor = userId != null ? UUID.fromString(userId) : null;
        switch (target) {
            case "handling" -> {
                Object handlerId = body.get("handler_id") != null ? body.get("handler_id") : actor;
                String handlerName = SoftDeleteSupport.resolveUserDisplayName(jdbc, handlerId);
                jdbc.update("""
                    UPDATE adverse_event SET status='handling', handler_id=COALESCE(?::uuid, handler_id),
                    handler_name=COALESCE(?, handler_name),
                    handle_measures=?, handle_time=NOW(), updated_at=NOW() WHERE id=?::uuid
                    """, handlerId, handlerName, body.get("handle_measures"), id);
            }
            case "reviewed" -> {
                Object reviewerId = body.get("reviewer_id") != null ? body.get("reviewer_id") : actor;
                String reviewerName = SoftDeleteSupport.resolveUserDisplayName(jdbc, reviewerId);
                jdbc.update("""
                    UPDATE adverse_event SET status='reviewed', reviewer_id=COALESCE(?::uuid, reviewer_id),
                    reviewer_name=COALESCE(?, reviewer_name),
                    review_comment=?, review_time=NOW(), updated_at=NOW() WHERE id=?::uuid
                    """, reviewerId, reviewerName, body.get("review_comment"), id);
            }
            case "closed" -> jdbc.update("UPDATE adverse_event SET status='closed', updated_at=NOW() WHERE id=?::uuid", id);
            default -> jdbc.update("UPDATE adverse_event SET status=?, updated_at=NOW() WHERE id=?::uuid", target, id);
        }
        return get(id);
    }

    @PostMapping("/{id}/report-regulator")
    @Transactional
    @OperationLog(module = "qc", description = "上报监管")
    public Result<Map<String, Object>> reportRegulator(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        body = body != null ? body : Map.of();
        jdbc.update("""
            UPDATE adverse_event SET reported_to_authority = true, report_date = COALESCE(?::date, CURRENT_DATE),
            authority_feedback = COALESCE(?, authority_feedback), updated_at = NOW() WHERE id = ?::uuid
            """, body.get("report_date"), body.get("authority_feedback"), id);
        return get(id);
    }

    private void fillDeviceSnapshot(Map<String, Object> body) {
        if (body.get("device_id") == null) return;
        var devices = jdbc.queryForList(
                "SELECT device_code, device_name FROM medical_device WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), body.get("device_id"));
        if (!devices.isEmpty()) {
            if (body.get("device_code") == null) body.put("device_code", devices.get(0).get("device_code"));
            if (body.get("device_name") == null) body.put("device_name", devices.get(0).get("device_name"));
        }
    }
}
