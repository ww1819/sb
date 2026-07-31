package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.EntityChangeLogService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * AST-WRN-02：维保信息头 + 覆盖设备明细；金额校验；回写台账 warranty_end_date；变更记录。
 */
@RestController
@RequestMapping("/api/asset/warranty")
@RequiredArgsConstructor
public class DeviceWarrantyController {
    private final JdbcTemplate jdbc;
    private final EntityChangeLogService changeLog;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "supplier_id", required = false) String supplierId,
            @RequestParam(value = "under_warranty", required = false) String underWarranty,
            @RequestParam(value = "end_dateFrom", required = false) String endFrom,
            @RequestParam(value = "end_dateTo", required = false) String endTo) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty", "w"));
        List<Object> args = new ArrayList<>();
        if (hasText(deviceId)) {
            where.append("""
                     AND EXISTS (
                       SELECT 1 FROM device_warranty_device wd
                       WHERE wd.warranty_id = w.id AND wd.device_id = ?::uuid
                    """).append(SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", "wd"))
                    .append(") ");
            args.add(deviceId.trim());
        }
        if (hasText(supplierId)) {
            where.append(" AND w.supplier_id = ?::uuid ");
            args.add(supplierId.trim());
        }
        if (hasText(endFrom)) {
            where.append(" AND w.end_date >= ?::date ");
            args.add(endFrom.trim());
        }
        if (hasText(endTo)) {
            where.append(" AND w.end_date <= ?::date ");
            args.add(endTo.trim());
        }
        if ("true".equalsIgnoreCase(underWarranty) || "1".equals(underWarranty) || "是".equals(underWarranty)) {
            where.append(" AND w.start_date <= CURRENT_DATE AND w.end_date >= CURRENT_DATE ");
        } else if ("false".equalsIgnoreCase(underWarranty) || "0".equals(underWarranty) || "否".equals(underWarranty)) {
            where.append(" AND NOT (w.start_date <= CURRENT_DATE AND w.end_date >= CURRENT_DATE) ");
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append("""
                     AND (w.supplier_name ILIKE ? OR w.coverage_content ILIKE ?
                          OR EXISTS (
                            SELECT 1 FROM device_warranty_device wd
                            WHERE wd.warranty_id = w.id
                              AND (wd.device_code ILIKE ? OR wd.device_name ILIKE ?)
                    """).append(SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", "wd"))
                    .append(")) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        String from = " FROM device_warranty w ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        var rows = jdbc.queryForList("""
                SELECT w.*,
                       (SELECT COUNT(*) FROM device_warranty_device wd
                        WHERE wd.warranty_id = w.id
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", "wd") + """
                       ) AS device_count,
                       (SELECT COALESCE(SUM(wd.unit_price), 0) FROM device_warranty_device wd
                        WHERE wd.warranty_id = w.id
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", "wd") + """
                       ) AS unit_price_sum,
                       CASE WHEN w.start_date <= CURRENT_DATE AND w.end_date >= CURRENT_DATE THEN TRUE ELSE FALSE END AS under_warranty,
                       CASE
                         WHEN w.start_date <= CURRENT_DATE AND w.end_date >= CURRENT_DATE THEN 'in_warranty'
                         WHEN w.start_date > CURRENT_DATE THEN 'not_started'
                         ELSE 'expired'
                       END AS warranty_status
                """ + from + where + " ORDER BY w.end_date DESC NULLS LAST, w.created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(new PageResult<>(rows, total != null ? total : 0L, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        return Result.ok(loadDetail(id));
    }

    @GetMapping("/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> byDevice(@PathVariable UUID deviceId) {
        return Result.ok(jdbc.queryForList("""
                SELECT w.*,
                       wd.id AS link_id,
                       wd.unit_price,
                       wd.remark AS link_remark,
                       CASE WHEN w.start_date <= CURRENT_DATE AND w.end_date >= CURRENT_DATE THEN TRUE ELSE FALSE END AS under_warranty,
                       CASE
                         WHEN w.start_date <= CURRENT_DATE AND w.end_date >= CURRENT_DATE THEN 'in_warranty'
                         WHEN w.start_date > CURRENT_DATE THEN 'not_started'
                         ELSE 'expired'
                       END AS warranty_status
                FROM device_warranty_device wd
                JOIN device_warranty w ON w.id = wd.warranty_id
                WHERE wd.device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", "wd")
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty", "w") + """
                ORDER BY w.end_date DESC NULLS LAST, w.start_date DESC
                """, deviceId));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存设备维保信息")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        LocalDate start = parseDate(body.get("start_date"));
        LocalDate end = parseDate(body.get("end_date"));
        if (start == null || end == null) {
            throw new BizException(400, "请填写维保起止日期");
        }
        if (end.isBefore(start)) {
            throw new BizException(400, "结束日期不能早于开始日期");
        }
        BigDecimal totalAmount = parseAmount(body.get("total_amount"), "维保总价");
        fillSupplierSnapshot(body);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> devices = body.get("devices") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
        validateDevicesAndAmount(devices, totalAmount);

        UUID id = parseUuid(body.get("id"));
        boolean exists = id != null && !jdbc.queryForList(
                "SELECT 1 FROM device_warranty WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty", null),
                id).isEmpty();

        Set<UUID> affectedDevices = new HashSet<>();
        if (exists) {
            for (var row : jdbc.queryForList(
                    "SELECT device_id FROM device_warranty_device WHERE warranty_id = ?::uuid"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", null),
                    id)) {
                UUID did = parseUuid(row.get("device_id"));
                if (did != null) affectedDevices.add(did);
            }
            Map<String, Object> before = changeLog.loadRow("device_warranty", id);
            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            sets.add("supplier_id = ?::uuid");
            args.add(body.get("supplier_id"));
            sets.add("supplier_name = ?");
            args.add(body.get("supplier_name"));
            sets.add("start_date = ?::date");
            args.add(start.toString());
            sets.add("end_date = ?::date");
            args.add(end.toString());
            sets.add("total_amount = ?");
            args.add(totalAmount);
            sets.add("coverage_content = ?");
            args.add(body.get("coverage_content"));
            sets.add("remark = ?");
            args.add(body.get("remark"));
            SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, "device_warranty"), sets, args);
            args.add(id);
            jdbc.update("UPDATE device_warranty SET " + String.join(", ", sets)
                    + " WHERE id = ?::uuid"
                    + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty", null), args.toArray());
            changeLog.recordUpdate("device_warranty", id, before, changeLog.loadRow("device_warranty", id));
        } else {
            id = id != null ? id : UUID.randomUUID();
            SoftDeleteSupport.applyInsertAudit(jdbc, "device_warranty", body);
            jdbc.update("""
                    INSERT INTO device_warranty (
                      id, supplier_id, supplier_name, start_date, end_date, total_amount,
                      coverage_content, remark, created_by, created_by_name, is_deleted
                    ) VALUES (
                      ?::uuid, ?::uuid, ?, ?::date, ?::date, ?,
                      ?, ?, ?::uuid, ?, 0
                    )
                    """,
                    id, body.get("supplier_id"), body.get("supplier_name"),
                    start.toString(), end.toString(), totalAmount,
                    body.get("coverage_content"), body.get("remark"),
                    body.get("created_by"), body.get("created_by_name"));
            changeLog.recordCreate("device_warranty", id, changeLog.loadRow("device_warranty", id));
        }

        syncDevices(id, devices, affectedDevices);
        for (UUID deviceId : affectedDevices) {
            syncDeviceWarrantyEndDate(deviceId);
        }
        return Result.ok(loadDetail(id));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除设备维保信息")
    public Result<Void> delete(@PathVariable UUID id) {
        Map<String, Object> before = changeLog.loadRow("device_warranty", id);
        if (before == null || before.isEmpty()) {
            throw new BizException(404, "维保信息不存在");
        }
        Set<UUID> affected = new HashSet<>();
        for (var row : jdbc.queryForList(
                "SELECT id, device_id FROM device_warranty_device WHERE warranty_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", null),
                id)) {
            UUID linkId = parseUuid(row.get("id"));
            UUID deviceId = parseUuid(row.get("device_id"));
            if (linkId != null) {
                Map<String, Object> linkBefore = changeLog.loadRow("device_warranty_device", linkId);
                SoftDeleteSupport.softDelete(jdbc, "device_warranty_device", linkId.toString());
                changeLog.recordDelete("device_warranty_device", linkId, linkBefore);
            }
            if (deviceId != null) affected.add(deviceId);
        }
        SoftDeleteSupport.softDelete(jdbc, "device_warranty", id.toString());
        changeLog.recordDelete("device_warranty", id, before);
        for (UUID deviceId : affected) {
            syncDeviceWarrantyEndDate(deviceId);
        }
        return Result.ok();
    }

    /** 台账 Tab：将本机加入已有维保包（可带单价） */
    @PostMapping("/{id}/devices")
    @Transactional
    @OperationLog(module = "asset", description = "维保信息添加覆盖设备")
    public Result<Map<String, Object>> addDevice(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        ensureWarranty(id);
        UUID deviceId = parseUuid(body.get("device_id"));
        if (deviceId == null) {
            throw new BizException(400, "请选择设备");
        }
        BigDecimal unitPrice = parseAmount(body.get("unit_price"), "单价");
        Map<String, Object> snap = deviceSnapshot(deviceId);
        List<Map<String, Object>> projected = loadActiveDevices(id);
        boolean found = false;
        for (var d : projected) {
            if (deviceId.equals(parseUuid(d.get("device_id")))) {
                d.put("unit_price", unitPrice);
                found = true;
                break;
            }
        }
        if (!found) {
            Map<String, Object> line = new LinkedHashMap<>(snap);
            line.put("unit_price", unitPrice);
            line.put("remark", body.get("remark"));
            projected.add(line);
        }
        BigDecimal total = parseAmount(
                jdbc.queryForObject(
                        "SELECT total_amount FROM device_warranty WHERE id = ?::uuid"
                                + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty", null),
                        Object.class, id),
                "维保总价");
        validateDevicesAndAmount(projected, total);

        Set<UUID> affected = new HashSet<>();
        syncDevices(id, projected, affected);
        for (UUID did : affected) {
            syncDeviceWarrantyEndDate(did);
        }
        return Result.ok(loadDetail(id));
    }

    @DeleteMapping("/{id}/devices/{linkId}")
    @Transactional
    @OperationLog(module = "asset", description = "维保信息移除覆盖设备")
    public Result<Void> removeDevice(@PathVariable UUID id, @PathVariable UUID linkId) {
        ensureWarranty(id);
        var rows = jdbc.queryForList("""
                SELECT * FROM device_warranty_device
                WHERE id = ?::uuid AND warranty_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", null),
                linkId, id);
        if (rows.isEmpty()) {
            throw new BizException(404, "覆盖设备不存在");
        }
        UUID deviceId = parseUuid(rows.get(0).get("device_id"));
        Map<String, Object> before = changeLog.loadRow("device_warranty_device", linkId);
        SoftDeleteSupport.softDelete(jdbc, "device_warranty_device", linkId.toString());
        changeLog.recordDelete("device_warranty_device", linkId, before);
        if (deviceId != null) {
            syncDeviceWarrantyEndDate(deviceId);
        }
        return Result.ok();
    }

    private Map<String, Object> loadDetail(UUID id) {
        var rows = jdbc.queryForList("""
                SELECT w.*,
                       CASE WHEN w.start_date <= CURRENT_DATE AND w.end_date >= CURRENT_DATE THEN TRUE ELSE FALSE END AS under_warranty
                FROM device_warranty w
                WHERE w.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty", "w"), id);
        if (rows.isEmpty()) {
            throw new BizException(404, "维保信息不存在");
        }
        Map<String, Object> detail = new LinkedHashMap<>(rows.get(0));
        detail.put("devices", loadActiveDevices(id));
        return detail;
    }

    private List<Map<String, Object>> loadActiveDevices(UUID warrantyId) {
        return jdbc.queryForList("""
                SELECT wd.*,
                       COALESCE(wd.device_code, d.device_code) AS device_code,
                       COALESCE(wd.device_name, d.device_name) AS device_name,
                       dept.dept_name,
                """ + com.meis.saas.common.asset.DeviceLedgerSelectSupport.SELECT_FIELDS + """
                FROM device_warranty_device wd
                """ + com.meis.saas.common.asset.DeviceLedgerSelectSupport.joins("wd.device_id") + """
                WHERE wd.warranty_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", "wd") + """
                ORDER BY wd.device_code NULLS LAST, wd.created_at
                """, warrantyId);
    }

    private void ensureWarranty(UUID id) {
        if (jdbc.queryForList(
                "SELECT 1 FROM device_warranty WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty", null),
                id).isEmpty()) {
            throw new BizException(404, "维保信息不存在");
        }
    }

    private void syncDevices(UUID warrantyId, List<Map<String, Object>> devices, Set<UUID> affectedDevices) {
        Map<UUID, Map<String, Object>> existingByDevice = new HashMap<>();
        for (var row : loadActiveDevices(warrantyId)) {
            UUID did = parseUuid(row.get("device_id"));
            if (did != null) existingByDevice.put(did, row);
        }
        Set<UUID> keep = new HashSet<>();
        for (Map<String, Object> line : devices) {
            UUID deviceId = parseUuid(line.get("device_id"));
            if (deviceId == null) {
                throw new BizException(400, "覆盖设备缺少 device_id");
            }
            if (!keep.add(deviceId)) {
                throw new BizException(400, "同一维保包内设备重复");
            }
            affectedDevices.add(deviceId);
            Map<String, Object> snap = deviceSnapshot(deviceId);
            BigDecimal unitPrice = parseAmount(line.get("unit_price"), "单价");
            Map<String, Object> existing = existingByDevice.get(deviceId);
            if (existing != null) {
                UUID linkId = parseUuid(existing.get("id"));
                Map<String, Object> before = changeLog.loadRow("device_warranty_device", linkId);
                List<String> sets = new ArrayList<>();
                List<Object> args = new ArrayList<>();
                sets.add("device_code = ?");
                args.add(snap.get("device_code"));
                sets.add("device_name = ?");
                args.add(snap.get("device_name"));
                sets.add("unit_price = ?");
                args.add(unitPrice);
                sets.add("remark = ?");
                args.add(line.get("remark"));
                SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, "device_warranty_device"), sets, args);
                args.add(linkId);
                jdbc.update("UPDATE device_warranty_device SET " + String.join(", ", sets)
                        + " WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", null), args.toArray());
                changeLog.recordUpdate("device_warranty_device", linkId, before, changeLog.loadRow("device_warranty_device", linkId));
            } else {
                UUID linkId = UUID.randomUUID();
                Map<String, Object> insert = new LinkedHashMap<>();
                SoftDeleteSupport.applyInsertAudit(jdbc, "device_warranty_device", insert);
                jdbc.update("""
                        INSERT INTO device_warranty_device (
                          id, warranty_id, device_id, device_code, device_name, unit_price, remark,
                          created_by, created_by_name, is_deleted
                        ) VALUES (
                          ?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?,
                          ?::uuid, ?, 0
                        )
                        """,
                        linkId, warrantyId, deviceId, snap.get("device_code"), snap.get("device_name"),
                        unitPrice, line.get("remark"),
                        insert.get("created_by"), insert.get("created_by_name"));
                changeLog.recordCreate("device_warranty_device", linkId, changeLog.loadRow("device_warranty_device", linkId));
            }
        }
        for (Map.Entry<UUID, Map<String, Object>> e : existingByDevice.entrySet()) {
            if (keep.contains(e.getKey())) continue;
            UUID linkId = parseUuid(e.getValue().get("id"));
            affectedDevices.add(e.getKey());
            if (linkId == null) continue;
            Map<String, Object> before = changeLog.loadRow("device_warranty_device", linkId);
            SoftDeleteSupport.softDelete(jdbc, "device_warranty_device", linkId.toString());
            changeLog.recordDelete("device_warranty_device", linkId, before);
        }
    }

    private void validateDevicesAndAmount(List<Map<String, Object>> devices, BigDecimal totalAmount) {
        BigDecimal sum = BigDecimal.ZERO;
        Set<UUID> seen = new HashSet<>();
        for (Map<String, Object> line : devices) {
            UUID deviceId = parseUuid(line.get("device_id"));
            if (deviceId == null) {
                throw new BizException(400, "覆盖设备缺少 device_id");
            }
            if (!seen.add(deviceId)) {
                throw new BizException(400, "同一维保包内设备重复");
            }
            BigDecimal unit = parseAmount(line.get("unit_price"), "单价");
            if (unit != null) {
                sum = sum.add(unit);
            }
        }
        if (totalAmount != null && sum.compareTo(totalAmount) > 0) {
            throw new BizException(400, "覆盖设备单价合计不得超过维保总价");
        }
    }

    private void syncDeviceWarrantyEndDate(UUID deviceId) {
        LocalDate maxEnd = jdbc.query("""
                SELECT MAX(w.end_date) AS max_end
                FROM device_warranty_device wd
                JOIN device_warranty w ON w.id = wd.warranty_id
                WHERE wd.device_id = ?::uuid
                  AND w.start_date <= CURRENT_DATE AND w.end_date >= CURRENT_DATE
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty_device", "wd")
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_warranty", "w"),
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

    private Map<String, Object> deviceSnapshot(UUID deviceId) {
        var rows = jdbc.queryForList("""
                SELECT d.device_code, d.device_name
                FROM medical_device d
                WHERE d.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d"), deviceId);
        if (rows.isEmpty()) {
            throw new BizException(400, "设备不存在或已删除");
        }
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("device_id", deviceId.toString());
        snap.put("device_code", rows.get(0).get("device_code"));
        snap.put("device_name", rows.get(0).get("device_name"));
        return snap;
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

    private static BigDecimal parseAmount(Object v, String label) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            BigDecimal n = new BigDecimal(String.valueOf(v).trim());
            if (n.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(400, label + "不能为负数");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new BizException(400, label + "格式不正确");
        }
    }
}
