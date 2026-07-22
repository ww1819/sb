package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.code.DeviceCodeGenerator;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/asset/entry")
@RequiredArgsConstructor
public class DeviceEntryController {
    private final JdbcTemplate jdbc;
    private final DeviceCodeGenerator codeGenerator;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approval_status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", "e"));
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (e.entry_no ILIKE ? OR e.trace_no ILIKE ? OR pc.contract_code ILIKE ?"
                    + " OR s.supplier_name ILIKE ? OR w.warehouse_name ILIKE ?) ");
            String kw = "%" + query.getKeyword() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        FilterCsvSupport.appendStrIn(where, args, "e.status", status);
        if (approval_status != null && !approval_status.isBlank()) {
            if (approval_status.contains(",")) {
                FilterCsvSupport.appendStrIn(where, args, "COALESCE(e.approval_status, 'draft')", approval_status);
            } else {
                where.append(" AND COALESCE(e.approval_status, 'draft') = ? ");
                args.add(approval_status);
            }
        }
        String from = " FROM device_entry e "
                + " LEFT JOIN purchase_contract pc ON pc.id = e.contract_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc")
                + " LEFT JOIN supplier s ON s.id = e.supplier_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s")
                + " LEFT JOIN warehouse w ON w.id = e.warehouse_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w")
                + " LEFT JOIN ("
                + "   SELECT i.entry_id, COALESCE(SUM(i.total_price), 0) AS total_amount"
                + "   FROM device_entry_item i WHERE 1=1"
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry_item", "i")
                + "   GROUP BY i.entry_id"
                + " ) amt ON amt.entry_id = e.id";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT e.*, pc.contract_code, s.supplier_name, w.warehouse_name,
                   COALESCE(amt.total_amount, 0) AS total_amount
            """ + from + where + " ORDER BY e.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    /** 预览下一入库单号：RK-yyyyMMdd + 4 位当日流水 */
    @GetMapping("/next-no")
    public Result<Map<String, Object>> nextNo() {
        return Result.ok(Map.of("entry_no", nextEntryNo()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM device_entry WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> e = rows.get(0);
        e.put("items", jdbc.queryForList(
                "SELECT * FROM device_entry_item WHERE entry_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry_item", null), id));
        return Result.ok(e);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存入库单")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null && !body.get("id").toString().isBlank()
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM device_entry WHERE id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", null), id).isEmpty();
        UUID contractId = parseUuid(body.get("contract_id"));
        UUID supplierId = parseUuid(body.get("supplier_id"));
        UUID warehouseId = parseUuid(body.get("warehouse_id"));
        if (warehouseId == null) {
            throw new BizException(400, "请选择仓库");
        }
        if (supplierId == null && contractId != null) {
            var c = jdbc.queryForList(
                    "SELECT supplier_id FROM purchase_contract WHERE id = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null), contractId);
            if (!c.isEmpty() && c.get(0).get("supplier_id") != null) {
                supplierId = UUID.fromString(c.get(0).get("supplier_id").toString());
            }
        }
        String entryNo;
        if (!exists) {
            entryNo = blankToNull(body.get("entry_no"));
            if (entryNo == null) entryNo = nextEntryNo();
            Map<String, Object> audit = new HashMap<>();
            SoftDeleteSupport.applyInsertAudit(jdbc, "device_entry", audit);
            UUID createdBy = parseUuid(audit.get("created_by"));
            String createdByName = blankToNull(audit.get("created_by_name"));
            jdbc.update("""
                INSERT INTO device_entry (
                    id, entry_no, contract_id, supplier_id, entry_date, entry_type, operator_id,
                    quality_check_passed, quality_checker_id, quality_check_date, quality_check_report_url,
                    installation_completed, installation_date, status, remark, trace_no, warehouse_id,
                    invoice_amount, invoice_no, approval_status,
                    created_by, created_by_name, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """,
                    id, entryNo,
                    contractId, supplierId, toDateOrNull(body.get("entry_date")),
                    body.getOrDefault("entry_type", "purchase"), parseUuid(body.get("operator_id")),
                    body.get("quality_check_passed"), parseUuid(body.get("quality_checker_id")),
                    toDateOrNull(body.get("quality_check_date")), blankToNull(body.get("quality_check_report_url")),
                    body.get("installation_completed"), toDateOrNull(body.get("installation_date")),
                    body.getOrDefault("status", "draft"), blankToNull(body.get("remark")),
                    blankToNull(body.get("trace_no")), warehouseId,
                    toDouble(body.get("invoice_amount")), blankToNull(body.get("invoice_no")),
                    body.getOrDefault("approval_status", "draft"),
                    createdBy, createdByName);
        } else {
            var nos = jdbc.queryForList("SELECT entry_no FROM device_entry WHERE id = ?", id);
            entryNo = nos.isEmpty() ? null : blankToNull(nos.get(0).get("entry_no"));
            if ("completed".equals(String.valueOf(
                    jdbc.queryForList("SELECT status FROM device_entry WHERE id = ?", id).stream()
                            .findFirst().map(r -> r.get("status")).orElse("")))) {
                throw new BizException(400, "已完成的入库单不可编辑");
            }
            if ("approved".equals(String.valueOf(
                    jdbc.queryForList("SELECT approval_status FROM device_entry WHERE id = ?", id).stream()
                            .findFirst().map(r -> r.get("approval_status")).orElse("")))) {
                throw new BizException(400, "已审核的入库单不可编辑");
            }
            String updatedBy = SoftDeleteSupport.currentUserId();
            String updatedByName = updatedBy != null
                    ? SoftDeleteSupport.resolveUserDisplayName(jdbc, updatedBy) : null;
            jdbc.update("""
                UPDATE device_entry SET
                    contract_id=?, supplier_id=?, warehouse_id=?, entry_date=?, entry_type=?, operator_id=?,
                    quality_check_passed=?, quality_checker_id=?, quality_check_date=?, quality_check_report_url=?,
                    installation_completed=?, installation_date=?, status=?, remark=?,
                    invoice_amount=?, invoice_no=?,
                    updated_by=?::uuid, updated_by_name=?, updated_at=NOW()
                WHERE id=?
                """,
                    contractId, supplierId, warehouseId, toDateOrNull(body.get("entry_date")),
                    body.get("entry_type"), parseUuid(body.get("operator_id")),
                    body.get("quality_check_passed"), parseUuid(body.get("quality_checker_id")),
                    toDateOrNull(body.get("quality_check_date")), blankToNull(body.get("quality_check_report_url")),
                    body.get("installation_completed"), toDateOrNull(body.get("installation_date")),
                    body.getOrDefault("status", "draft"), blankToNull(body.get("remark")),
                    toDouble(body.get("invoice_amount")), blankToNull(body.get("invoice_no")),
                    updatedBy, updatedByName, id);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        jdbc.update("DELETE FROM device_entry_item WHERE entry_id = ?", id);
        for (Map<String, Object> item : items) {
            String deviceName = blankToNull(item.get("device_name"));
            if (deviceName == null) continue;
            UUID manufacturerId = parseUuid(item.get("manufacturer_id"));
            String manufacturerName = blankToNull(item.get("manufacturer_name"));
            if (manufacturerId != null) {
                var mf = jdbc.queryForList(
                        "SELECT manufacturer_name FROM manufacturer WHERE id = ?"
                                + SoftDeleteSupport.notDeletedClause(jdbc, "manufacturer", null),
                        manufacturerId);
                if (!mf.isEmpty()) {
                    manufacturerName = blankToNull(mf.get(0).get("manufacturer_name"));
                }
            }
            Object qty = item.getOrDefault("quantity", 1);
            Object price = item.get("unit_price");
            Object amount = item.get("total_price");
            if (amount == null) {
                Double qn = toDouble(qty);
                Double pn = toDouble(price);
                if (qn != null && pn != null) {
                    amount = Math.round(qn * pn * 100.0) / 100.0;
                }
            }
            String specification = blankToNull(item.get("specification"));
            if (specification == null) specification = blankToNull(item.get("model"));
            String deviceCode = blankToNull(item.get("device_code"));
            if (deviceCode == null) deviceCode = blankToNull(item.get("factory_code"));
            jdbc.update("""
                INSERT INTO device_entry_item (
                    id, entry_id, entry_no, device_code, device_name, brand, model, specification, unit, serial_number,
                    quantity, unit_price, total_price, dept_id, manufacturer_id, manufacturer_name,
                    factory_code, financial_code, depreciation_years, production_date, warranty_period,
                    purchase_method, storage_location, category_id, finance_category_id, asset_category_id,
                    created_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    NOW()
                )
                """,
                    UUID.randomUUID(), id, entryNo, deviceCode, deviceName, blankToNull(item.get("brand")),
                    specification, specification, blankToNull(item.get("unit")),
                    blankToNull(item.get("serial_number")),
                    qty, price, amount, parseUuid(item.get("dept_id")), manufacturerId, manufacturerName,
                    blankToNull(item.get("factory_code")), blankToNull(item.get("financial_code")),
                    toIntOrNull(item.get("depreciation_years")), toDateOrNull(item.get("production_date")),
                    blankToNull(item.get("warranty_period")), blankToNull(item.get("purchase_method")),
                    blankToNull(item.get("storage_location")), parseUuid(item.get("category_id")),
                    parseUuid(item.get("finance_category_id")), parseUuid(item.get("asset_category_id")));
        }
        return get(id);
    }

    @PostMapping("/{id}/complete")
    @Transactional
  @OperationLog(module = "asset", description = "审核入库并生成台账")
  public Result<Map<String, Object>> complete(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        body = body != null ? body : Map.of();
        var entryRows = jdbc.queryForList(
                "SELECT * FROM device_entry WHERE id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", null), id);
        if (entryRows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> entry = entryRows.get(0);
        if ("completed".equals(String.valueOf(entry.get("status")))) throw new BizException(400, "入库单已完成");
        if (entry.get("warehouse_id") == null && body.get("warehouse_id") == null) {
            throw new BizException(400, "请先选择仓库后再完成入库");
        }

        Object deptId = body.get("dept_id");
        if (deptId == null && entry.get("plan_id") != null) {
            var plan = jdbc.queryForList(
                    "SELECT dept_id FROM purchase_plan WHERE id = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null), entry.get("plan_id"));
            if (!plan.isEmpty()) deptId = plan.get(0).get("dept_id");
        }
        Object supplierId = entry.get("supplier_id");
        if (supplierId == null && entry.get("contract_id") != null) {
            var c = jdbc.queryForList(
                    "SELECT supplier_id FROM purchase_contract WHERE id = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null), entry.get("contract_id"));
            if (!c.isEmpty()) supplierId = c.get(0).get("supplier_id");
        }

        Object warehouseId = body.get("warehouse_id") != null ? body.get("warehouse_id") : entry.get("warehouse_id");

        var items = jdbc.queryForList(
                "SELECT * FROM device_entry_item WHERE entry_id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry_item", null), id);
        List<UUID> deviceIds = new ArrayList<>();
        for (Map<String, Object> item : items) {
            int qty = item.get("quantity") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            Object itemDept = item.get("dept_id") != null ? item.get("dept_id") : deptId;
            String firstCode = null;
            for (int i = 0; i < qty; i++) {
                String code = codeGenerator.generate(
                        str(body, "campusCode"), str(body, "buildingCode"), str(body, "deptCode"),
                        str(body, "countryCode"), str(body, "categoryCode"));
                if (firstCode == null) firstCode = code;
                UUID deviceId = UUID.randomUUID();
                String model = blankToNull(item.get("specification"));
                if (model == null) model = blankToNull(item.get("model"));
                jdbc.update("""
                    INSERT INTO medical_device (
                        id, device_code, device_name, brand, model, specification, serial_number,
                        dept_id, supplier_id, manufacturer_id, category_id, finance_category_id, asset_category_id,
                        device_status, original_value, contract_price, contract_id, warehouse_id, financial_code,
                        depreciation_years, production_date, location_detail, created_at, updated_at
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?,
                        'normal', ?, ?, ?, ?, ?,
                        ?, ?, ?, NOW(), NOW()
                    )
                    """,
                        deviceId, code, item.get("device_name"), item.get("brand"), model, model,
                        item.get("serial_number"),
                        itemDept, supplierId, item.get("manufacturer_id"), item.get("category_id"),
                        item.get("finance_category_id"), item.get("asset_category_id"),
                        item.get("unit_price"), item.get("unit_price"), entry.get("contract_id"), warehouseId,
                        item.get("financial_code"),
                        toIntOrNull(item.get("depreciation_years")), toDateOrNull(item.get("production_date")),
                        item.get("storage_location"));
                deviceIds.add(deviceId);
            }
            if (firstCode != null && item.get("id") != null) {
                jdbc.update("UPDATE device_entry_item SET device_code = COALESCE(NULLIF(TRIM(device_code), ''), ?) WHERE id = ?",
                        firstCode, item.get("id"));
            }
        }
        var ctx = com.meis.saas.common.rbac.PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
        jdbc.update("""
            UPDATE device_entry SET
                status = 'completed',
                approval_status = 'approved',
                approved_by = ?,
                approved_by_name = ?,
                approved_at = COALESCE(approved_at, CURRENT_DATE),
                updated_at = NOW()
            WHERE id = ?
            """, actorId, actorName, id);
        Map<String, Object> result = get(id).getData();
        result.put("device_ids", deviceIds);
        result.put("device_count", deviceIds.size());
        return Result.ok(result);
    }

    private String nextEntryNo() {
        String prefix = "RK-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Integer maxSeq = jdbc.queryForObject("""
                SELECT COALESCE(MAX(CAST(RIGHT(entry_no, 4) AS INTEGER)), 0)
                FROM device_entry
                WHERE entry_no LIKE ?
                  AND LENGTH(entry_no) = ?
                  AND RIGHT(entry_no, 4) ~ '^[0-9]{4}$'
                """, Integer.class, prefix + "%", prefix.length() + 4);
        int seq = (maxSeq != null ? maxSeq : 0) + 1;
        for (int i = 0; i < 20; i++) {
            String candidate = prefix + String.format("%04d", seq + i);
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM device_entry WHERE entry_no = ?", Integer.class, candidate);
            if (cnt == null || cnt == 0) return candidate;
        }
        return prefix + String.format("%04d", seq) + System.currentTimeMillis() % 100;
    }

    private String str(Map<String, Object> m, String k) {
        return m.get(k) != null ? m.get(k).toString() : "0";
    }

    private static UUID parseUuid(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        return UUID.fromString(v.toString());
    }

    private static String blankToNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Double toDouble(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toIntOrNull(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static java.sql.Date toDateOrNull(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        if (v instanceof java.sql.Date d) return d;
        if (v instanceof java.util.Date d) return new java.sql.Date(d.getTime());
        if (v instanceof LocalDate ld) return java.sql.Date.valueOf(ld);
        if (v instanceof java.time.LocalDateTime ldt) return java.sql.Date.valueOf(ldt.toLocalDate());
        String s = v.toString().trim();
        if (s.length() >= 10) s = s.substring(0, 10);
        try {
            return java.sql.Date.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
