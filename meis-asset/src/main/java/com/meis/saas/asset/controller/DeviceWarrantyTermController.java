package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * AST-WRN-01：设备维保时段（一台多条）；写后回写台账 warranty_end_date。
 */
@RestController
@RequestMapping("/api/asset/warranty-term")
@RequiredArgsConstructor
public class DeviceWarrantyTermController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "supplier_id", required = false) String supplierId,
            @RequestParam(value = "under_warranty", required = false) String underWarranty,
            @RequestParam(value = "end_dateFrom", required = false) String endFrom,
            @RequestParam(value = "end_dateTo", required = false) String endTo) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_term", "t"));
        List<Object> args = new ArrayList<>();
        if (hasText(deviceId)) {
            where.append(" AND t.device_id = ?::uuid ");
            args.add(deviceId.trim());
        }
        if (hasText(supplierId)) {
            where.append(" AND t.supplier_id = ?::uuid ");
            args.add(supplierId.trim());
        }
        if (hasText(endFrom)) {
            where.append(" AND t.end_date >= ?::date ");
            args.add(endFrom.trim());
        }
        if (hasText(endTo)) {
            where.append(" AND t.end_date <= ?::date ");
            args.add(endTo.trim());
        }
        if ("true".equalsIgnoreCase(underWarranty) || "1".equals(underWarranty) || "是".equals(underWarranty)) {
            where.append(" AND t.start_date <= CURRENT_DATE AND t.end_date >= CURRENT_DATE ");
        } else if ("false".equalsIgnoreCase(underWarranty) || "0".equals(underWarranty) || "否".equals(underWarranty)) {
            where.append(" AND NOT (t.start_date <= CURRENT_DATE AND t.end_date >= CURRENT_DATE) ");
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append("""
                     AND (t.device_code ILIKE ? OR t.device_name ILIKE ? OR t.supplier_name ILIKE ?
                          OR t.coverage_content ILIKE ?)
                    """);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        String from = """
                FROM device_warranty_term t
                LEFT JOIN medical_device d ON d.id = t.device_id
                LEFT JOIN supplier s ON s.id = t.supplier_id
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        var rows = jdbc.queryForList("""
                SELECT t.*,
                       CASE WHEN t.start_date <= CURRENT_DATE AND t.end_date >= CURRENT_DATE THEN TRUE ELSE FALSE END AS under_warranty,
                       CASE
                         WHEN t.start_date <= CURRENT_DATE AND t.end_date >= CURRENT_DATE THEN 'in_warranty'
                         WHEN t.start_date > CURRENT_DATE THEN 'not_started'
                         ELSE 'expired'
                       END AS warranty_status
                """ + from + where + " ORDER BY t.end_date DESC NULLS LAST, t.created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(new PageResult<>(rows, total != null ? total : 0L, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT t.*,
                       CASE WHEN t.start_date <= CURRENT_DATE AND t.end_date >= CURRENT_DATE THEN TRUE ELSE FALSE END AS under_warranty
                FROM device_warranty_term t
                WHERE t.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_term", "t"), id);
        if (rows.isEmpty()) {
            throw new BizException(404, "维保记录不存在");
        }
        return Result.ok(rows.get(0));
    }

    @GetMapping("/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> byDevice(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT t.*,
                       CASE WHEN t.start_date <= CURRENT_DATE AND t.end_date >= CURRENT_DATE THEN TRUE ELSE FALSE END AS under_warranty,
                       CASE
                         WHEN t.start_date <= CURRENT_DATE AND t.end_date >= CURRENT_DATE THEN 'in_warranty'
                         WHEN t.start_date > CURRENT_DATE THEN 'not_started'
                         ELSE 'expired'
                       END AS warranty_status
                FROM device_warranty_term t
                WHERE t.device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_term", "t") + """
                ORDER BY t.end_date DESC NULLS LAST, t.start_date DESC
                """, deviceId));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存设备维保时段")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID deviceId = parseUuid(body.get("device_id"));
        if (deviceId == null) {
            throw new BizException(400, "请选择设备");
        }
        LocalDate start = parseDate(body.get("start_date"));
        LocalDate end = parseDate(body.get("end_date"));
        if (start == null || end == null) {
            throw new BizException(400, "请填写维保起止日期");
        }
        if (end.isBefore(start)) {
            throw new BizException(400, "结束日期不能早于开始日期");
        }
        fillDeviceSnapshot(body, deviceId);
        fillSupplierSnapshot(body);

        UUID id = parseUuid(body.get("id"));
        boolean exists = id != null && !jdbc.queryForList(
                "SELECT 1 FROM device_warranty_term WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_term", null),
                id).isEmpty();

        if (exists) {
            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            sets.add("device_id = ?::uuid");
            args.add(deviceId);
            sets.add("device_code = ?");
            args.add(body.get("device_code"));
            sets.add("device_name = ?");
            args.add(body.get("device_name"));
            sets.add("supplier_id = ?::uuid");
            args.add(body.get("supplier_id"));
            sets.add("supplier_name = ?");
            args.add(body.get("supplier_name"));
            sets.add("start_date = ?::date");
            args.add(start.toString());
            sets.add("end_date = ?::date");
            args.add(end.toString());
            sets.add("amount = ?");
            args.add(body.get("amount"));
            sets.add("coverage_content = ?");
            args.add(body.get("coverage_content"));
            sets.add("remark = ?");
            args.add(body.get("remark"));
            SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, "device_warranty_term"), sets, args);
            args.add(id);
            jdbc.update("UPDATE device_warranty_term SET " + String.join(", ", sets)
                    + " WHERE id = ?::uuid"
                    + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_term", null), args.toArray());
        } else {
            id = id != null ? id : UUID.randomUUID();
            SoftDeleteSupport.applyInsertAudit(jdbc, "device_warranty_term", body);
            jdbc.update("""
                    INSERT INTO device_warranty_term (
                      id, device_id, device_code, device_name, supplier_id, supplier_name,
                      start_date, end_date, amount, coverage_content, remark,
                      created_by, created_by_name, is_deleted
                    ) VALUES (
                      ?::uuid, ?::uuid, ?, ?, ?::uuid, ?,
                      ?::date, ?::date, ?, ?, ?,
                      ?::uuid, ?, 0
                    )
                    """,
                    id, deviceId, body.get("device_code"), body.get("device_name"),
                    body.get("supplier_id"), body.get("supplier_name"),
                    start.toString(), end.toString(), body.get("amount"), body.get("coverage_content"), body.get("remark"),
                    body.get("created_by"), body.get("created_by_name"));
        }
        syncDeviceWarrantyEndDate(deviceId);
        return get(id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除设备维保时段")
    public Result<Void> delete(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT device_id FROM device_warranty_term WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_term", null),
                id);
        if (rows.isEmpty()) {
            throw new BizException(404, "维保记录不存在");
        }
        SoftDeleteSupport.softDelete(jdbc, "device_warranty_term", id.toString());
        UUID deviceId = parseUuid(rows.get(0).get("device_id"));
        if (deviceId != null) {
            syncDeviceWarrantyEndDate(deviceId);
        }
        return Result.ok();
    }

    /** 当前覆盖时段中最晚结束日；无覆盖则清空台账 warranty_end_date */
    private void syncDeviceWarrantyEndDate(UUID deviceId) {
        LocalDate maxEnd = jdbc.query("""
                SELECT MAX(end_date) AS max_end
                FROM device_warranty_term
                WHERE device_id = ?::uuid
                  AND start_date <= CURRENT_DATE AND end_date >= CURRENT_DATE
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_term", null),
                rs -> rs.next() ? rs.getObject("max_end", LocalDate.class) : null,
                deviceId);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        sets.add("warranty_end_date = ?::date");
        args.add(maxEnd != null ? maxEnd.toString() : null);
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, "medical_device"), sets, args);
        args.add(deviceId);
        jdbc.update("UPDATE medical_device SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), args.toArray());
    }

    private void fillDeviceSnapshot(Map<String, Object> body, UUID deviceId) {
        var rows = jdbc.queryForList("""
                SELECT device_code, device_name, manufacturer_name
                FROM medical_device WHERE id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), deviceId);
        if (rows.isEmpty()) {
            throw new BizException(400, "设备不存在或已删除");
        }
        body.put("device_id", deviceId.toString());
        body.put("device_code", rows.get(0).get("device_code"));
        body.put("device_name", rows.get(0).get("device_name"));
    }

    private void fillSupplierSnapshot(Map<String, Object> body) {
        UUID supplierId = parseUuid(body.get("supplier_id"));
        if (supplierId == null) {
            body.put("supplier_id", null);
            body.put("supplier_name", "院内自主");
            return;
        }
        var rows = jdbc.queryForList("""
                SELECT supplier_name FROM supplier WHERE id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", null), supplierId);
        if (rows.isEmpty()) {
            throw new BizException(400, "维保公司（供应商）不存在");
        }
        body.put("supplier_id", supplierId.toString());
        body.put("supplier_name", rows.get(0).get("supplier_name"));
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }

    private static UUID parseUuid(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return UUID.fromString(String.valueOf(v).trim());
    }

    private static LocalDate parseDate(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return LocalDate.parse(String.valueOf(v).trim().substring(0, 10));
    }
}
