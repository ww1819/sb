package com.meis.saas.asset.controller;

import com.meis.saas.common.asset.MedicalDeviceDeleteGuard;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.code.DeviceCodeGenerator;
import com.meis.saas.common.exception.BizException;
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
@RequestMapping("/api/asset/device")
@RequiredArgsConstructor
public class AssetDeviceController {
    private final JdbcTemplate jdbc;
    private final DeviceCodeGenerator codeGenerator;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(value = "enable_dateFrom", required = false) String enable_dateFrom,
            @RequestParam(value = "enable_dateTo", required = false) String enable_dateTo,
            @RequestParam(value = "supplier_id", required = false) String supplier_id,
            @RequestParam(value = "manufacturer_id", required = false) String manufacturer_id,
            @RequestParam(value = "supplier_name", required = false) String supplier_name,
            @RequestParam(value = "manufacturer_name", required = false) String manufacturer_name,
            @RequestParam(value = "device_name", required = false) String device_name,
            @RequestParam(value = "specification", required = false) String specification,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "dept_id", required = false) String dept_id,
            @RequestParam(value = "manage_dept_id", required = false) String manage_dept_id,
            @RequestParam(value = "dept_name", required = false) String dept_name,
            @RequestParam(value = "manage_dept_name", required = false) String manage_dept_name,
            @RequestParam(value = "serial_number", required = false) String serial_number,
            @RequestParam(value = "warehouse_id", required = false) String warehouse_id) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d"));
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append("""
                     AND (d.device_code ILIKE ? OR d.device_name ILIKE ? OR d.specification ILIKE ?
                          OR d.financial_code ILIKE ? OR d.serial_number ILIKE ?)
                    """);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        appendUuidEq(where, args, "d.supplier_id", supplier_id);
        appendUuidEq(where, args, "d.manufacturer_id", manufacturer_id);
        appendUuidEq(where, args, "d.dept_id", dept_id);
        appendUuidEq(where, args, "d.manage_dept_id", manage_dept_id);
        appendUuidEq(where, args, "d.warehouse_id", warehouse_id);
        boolean needSupplier = hasText(supplier_name);
        boolean needManufacturer = hasText(manufacturer_name) && !hasText(manufacturer_id);
        boolean needUseDept = hasText(dept_name) || "dept_id".equals(query.getSortBy());
        boolean needManageDept = hasText(manage_dept_name) && !hasText(manage_dept_id);
        if (hasText(supplier_name)) {
            appendSupplierSearch(where, args, supplier_name);
        }
        if (!hasText(manufacturer_id)) {
            appendNameOrPinyin(where, args, "mfr.manufacturer_name", "mfr.pinyin_code", manufacturer_name);
        }
        appendNameOrPinyin(where, args, "d.device_name", "d.pinyin_code", device_name);
        appendLike(where, args, "d.specification", specification);
        appendLike(where, args, "d.model", model);
        if (!hasText(dept_id)) {
            appendNameOrPinyin(where, args, "use_dept.dept_name", "use_dept.pinyin_code", dept_name);
        }
        if (!hasText(manage_dept_id)) {
            appendNameOrPinyin(where, args, "mgr_dept.dept_name", "mgr_dept.pinyin_code", manage_dept_name);
        }
        appendLike(where, args, "d.serial_number", serial_number);
        if (enable_dateFrom != null && !enable_dateFrom.isBlank()) {
            where.append(" AND d.enable_date >= ?::date ");
            args.add(enable_dateFrom.trim());
        }
        if (enable_dateTo != null && !enable_dateTo.isBlank()) {
            where.append(" AND d.enable_date <= ?::date ");
            args.add(enable_dateTo.trim());
        }
        String from = buildFrom(needSupplier, needManufacturer, needUseDept, needManageDept);
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COUNT(*) " + from + where, Long.class, args.toArray())).orElse(0L);
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT d.*
                """ + from + where + buildOrderBy(query) + " LIMIT ? OFFSET ?", args.toArray());
        MedicalDeviceDeleteGuard.enrichCanDelete(jdbc, rows);
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void appendLike(StringBuilder where, List<Object> args, String column, String value) {
        if (!hasText(value)) return;
        where.append(" AND ").append(column).append(" ILIKE ? ");
        args.add("%" + value.trim() + "%");
    }

    private static void appendUuidEq(StringBuilder where, List<Object> args, String column, String value) {
        if (!hasText(value)) return;
        where.append(" AND ").append(column).append(" = ?::uuid ");
        args.add(value.trim());
    }

    private static void appendSupplierSearch(StringBuilder where, List<Object> args, String value) {
        if (!hasText(value)) return;
        String kw = "%" + value.trim() + "%";
        where.append("""
                 AND (sup.supplier_name ILIKE ? OR sup.pinyin_code ILIKE ? OR sup.supplier_code ILIKE ?)
                """);
        args.add(kw);
        args.add(kw);
        args.add(kw);
    }

    private static void appendNameOrPinyin(StringBuilder where, List<Object> args,
                                           String nameColumn, String pinyinColumn, String value) {
        if (!hasText(value)) return;
        String kw = "%" + value.trim() + "%";
        where.append(" AND (").append(nameColumn).append(" ILIKE ? OR ").append(pinyinColumn).append(" ILIKE ?) ");
        args.add(kw);
        args.add(kw);
    }

    private String buildFrom(boolean needSupplier, boolean needManufacturer,
                             boolean needUseDept, boolean needManageDept) {
        StringBuilder from = new StringBuilder(" FROM medical_device d ");
        if (needSupplier) {
            from.append(" LEFT JOIN supplier sup ON d.supplier_id = sup.id ")
                    .append(SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "sup"));
        }
        if (needManufacturer) {
            from.append(" LEFT JOIN manufacturer mfr ON d.manufacturer_id = mfr.id ")
                    .append(SoftDeleteSupport.notDeletedClause(jdbc, "manufacturer", "mfr"));
        }
        if (needUseDept) {
            from.append(" LEFT JOIN department use_dept ON d.dept_id = use_dept.id ")
                    .append(SoftDeleteSupport.notDeletedClause(jdbc, "department", "use_dept"));
        }
        if (needManageDept) {
            from.append(" LEFT JOIN department mgr_dept ON d.manage_dept_id = mgr_dept.id ")
                    .append(SoftDeleteSupport.notDeletedClause(jdbc, "department", "mgr_dept"));
        }
        return from.toString();
    }

    private static String buildOrderBy(PageQuery query) {
        String sortBy = query.getSortBy();
        String sortOrder = query.getSortOrder();
        if (sortBy == null || sortBy.isBlank() || sortOrder == null || sortOrder.isBlank()) {
            return " ORDER BY d.created_at DESC NULLS LAST, d.device_code ASC";
        }
        String dir = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        String column = switch (sortBy) {
            case "device_code" -> "d.device_code";
            case "device_name" -> "d.device_name";
            case "specification" -> "d.specification";
            case "dept_id" -> "use_dept.dept_name";
            default -> null;
        };
        if (column == null) {
            return " ORDER BY d.created_at DESC NULLS LAST, d.device_code ASC";
        }
        return " ORDER BY " + column + " " + dir + " NULLS LAST, d.device_code ASC";
    }

    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> detail(@PathVariable UUID id) {
        var device = jdbc.queryForList(
                "SELECT * FROM medical_device WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), id);
        if (device.isEmpty()) return Result.ok(null);
        Map<String, Object> d = device.get(0);
        d.put("can_delete", !MedicalDeviceDeleteGuard.hasBusinessData(jdbc, id.toString()));
        d.put("repairs", jdbc.queryForList(
                "SELECT * FROM repair_workorder WHERE device_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null)
                        + " ORDER BY created_at DESC LIMIT 20", id));
        d.put("maintenance", jdbc.queryForList(
                "SELECT * FROM maintenance_record WHERE device_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "maintenance_record", null)
                        + " ORDER BY created_at DESC LIMIT 20", id));
        d.put("transfers", jdbc.queryForList(
                "SELECT * FROM asset_transfer WHERE device_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "asset_transfer", null)
                        + " ORDER BY created_at DESC LIMIT 10", id));
        d.put("qc", jdbc.queryForList("""
            SELECT 'risk' AS type, id, created_at FROM risk_assessment WHERE device_id = ?::uuid
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "risk_assessment", null) + """
            UNION ALL SELECT 'metrology', id, created_at FROM metrology_record WHERE device_id = ?::uuid
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_record", null) + """
            UNION ALL SELECT 'performance', id, created_at FROM performance_test WHERE device_id = ?::uuid
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "performance_test", null) + """
            ORDER BY created_at DESC LIMIT 20
            """, id, id, id));
        d.put("benefit", jdbc.queryForList(
                "SELECT * FROM device_benefit_summary WHERE device_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_benefit_summary", null)
                        + " ORDER BY summary_year DESC LIMIT 12", id));
        d.put("logs", jdbc.queryForList(
                "SELECT * FROM sys_operation_log WHERE request_params::text LIKE ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_operation_log", null)
                        + " ORDER BY created_at DESC LIMIT 20", "%" + id + "%"));
        d.put("label_prints", jdbc.queryForList("""
                SELECT * FROM device_label_print_log WHERE device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_label_print_log", null) + """
                 ORDER BY printed_at DESC LIMIT 50
                """, id));
        return Result.ok(d);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除资产台账")
    public Result<Void> delete(@PathVariable UUID id) {
        MedicalDeviceDeleteGuard.assertDeletable(jdbc, id);
        int n = SoftDeleteSupport.softDelete(jdbc, "medical_device", id.toString());
        if (n == 0) throw new BizException(404, "not found");
        return Result.ok();
    }

    @PostMapping("/generate-code")
    public Result<Map<String, String>> generateCode(@RequestBody Map<String, String> body) {
        String code = codeGenerator.generate(
                body.get("campusCode"), body.get("buildingCode"), body.get("deptCode"),
                body.get("countryCode"), body.get("categoryCode"));
        return Result.ok(Map.of("deviceCode", code));
    }

    @GetMapping("/{id}/label")
    public Result<Map<String, Object>> labelInfo(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT id, device_code, device_name, brand, model, specification, dept_id, label_printed, qr_code_url FROM medical_device WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null),
                id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> d = new LinkedHashMap<>(rows.get(0));
        String code = Objects.toString(d.get("device_code"), "");
        d.put("qr_payload", code);
        d.put("prints", jdbc.queryForList("""
                SELECT id, device_code, device_name, printed_by, printed_at, template_code, remark
                FROM device_label_print_log WHERE device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_label_print_log", null) + """
                 ORDER BY printed_at DESC LIMIT 50
                """, id));
        return Result.ok(d);
    }

    @PostMapping("/{id}/label/print")
    @Transactional
    @OperationLog(module = "asset", description = "打印资产标签")
    public Result<Map<String, Object>> printLabel(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        var rows = jdbc.queryForList(
                "SELECT id, device_code, device_name FROM medical_device WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> device = rows.get(0);
        String code = Objects.toString(device.get("device_code"), "");
        if (code.isBlank()) throw new BizException(400, "设备编码为空，无法生成标签二维码");
        UUID logId = UUID.randomUUID();
        Object printedBy = body != null ? body.get("printed_by") : null;
        if (printedBy == null) printedBy = TenantContext.getUserId();
        String template = body != null && body.get("template_code") != null
                ? body.get("template_code").toString() : "default";
        String remark = body != null && body.get("remark") != null ? body.get("remark").toString() : null;
        jdbc.update("""
                INSERT INTO device_label_print_log (id, device_id, device_code, device_name, printed_by, template_code, remark)
                VALUES (?::uuid, ?::uuid, ?, ?, ?::uuid, ?, ?)
                """, logId, id, code, device.get("device_name"), printedBy, template, remark);
        jdbc.update("""
                UPDATE medical_device SET label_printed = TRUE, qr_code_url = ?, updated_at = NOW() WHERE id = ?::uuid
                """, code, id);
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM device_label_print_log WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_label_print_log", null), logId).get(0));
    }
}
