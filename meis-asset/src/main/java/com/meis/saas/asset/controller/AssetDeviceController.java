package com.meis.saas.asset.controller;

import com.meis.saas.common.asset.MedicalDeviceDeleteGuard;
import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.code.DeviceCodeGenerator;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.rbac.PermissionContext;
import com.meis.saas.common.rbac.PermissionInterceptor;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/asset/device")
@RequiredArgsConstructor
public class AssetDeviceController {
    private static final int BATCH_LIMIT = 5000;
    private static final Set<String> BATCH_UUID_FIELDS = Set.of(
            "dept_id", "manage_dept_id", "campus_id", "building_id", "warehouse_id");
    private static final Set<String> BATCH_STR_FIELDS = Set.of("device_status", "risk_level");
    private static final Set<String> BATCH_BOOL_FIELDS = Set.of(
            "is_life_support", "is_emergency", "is_metrology", "is_shared_device",
            "is_maintain_device", "is_inspection_device", "is_pm_device");

    private final JdbcTemplate jdbc;
    private final DeviceCodeGenerator codeGenerator;
    private final EntityChangeLogService changeLog;

    /** App 台账增量同步（MOB.8）：按 updated_at 水位 + 数据权限过滤。 */
    @GetMapping("/sync")
    public Result<Map<String, Object>> sync(
            @RequestParam(required = false) String updatedAfter,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "500") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 1000) size = 500;
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d"));
        List<Object> args = new ArrayList<>();
        if (updatedAfter != null && !updatedAfter.isBlank()) {
            where.append(" AND d.updated_at > ?::timestamptz ");
            args.add(updatedAfter.trim());
        }
        PermissionContext ctx = PermissionInterceptor.CTX.get();
        if (ctx != null && ctx.getDataScope() != null && !"all".equals(ctx.getDataScope())) {
            List<String> deptIds = ctx.getDeptIds();
            if (deptIds != null && !deptIds.isEmpty()) {
                String ph = String.join(",", Collections.nCopies(deptIds.size(), "?::uuid"));
                where.append(" AND d.dept_id IN (").append(ph).append(") ");
                args.addAll(deptIds);
            } else if (ctx.getDeptId() != null && !ctx.getDeptId().isBlank()) {
                where.append(" AND d.dept_id = ?::uuid ");
                args.add(ctx.getDeptId());
            }
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM medical_device d" + where, Long.class, args.toArray());
        int offset = (page - 1) * size;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(offset);
        var rows = jdbc.queryForList("""
                SELECT d.id, d.device_code, d.device_name, d.specification, d.serial_number,
                       d.dept_id, d.device_status, d.updated_at, dept.dept_name
                FROM medical_device d
                LEFT JOIN department dept ON dept.id = d.dept_id
                """ + where + " ORDER BY d.updated_at ASC NULLS FIRST, d.id ASC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        String watermark = rows.isEmpty() ? updatedAfter : Objects.toString(rows.get(rows.size() - 1).get("updated_at"), updatedAfter);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", rows);
        data.put("total", total != null ? total : 0);
        data.put("page", page);
        data.put("size", size);
        data.put("sync_at", Instant.now().toString());
        data.put("watermark", watermark);
        return Result.ok(data);
    }

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(value = "enable_dateFrom", required = false) String enable_dateFrom,
            @RequestParam(value = "enable_dateTo", required = false) String enable_dateTo,
            @RequestParam(value = "created_atFrom", required = false) String created_atFrom,
            @RequestParam(value = "created_atTo", required = false) String created_atTo,
            @RequestParam(value = "production_dateFrom", required = false) String production_dateFrom,
            @RequestParam(value = "production_dateTo", required = false) String production_dateTo,
            @RequestParam(value = "acceptance_dateFrom", required = false) String acceptance_dateFrom,
            @RequestParam(value = "acceptance_dateTo", required = false) String acceptance_dateTo,
            @RequestParam(value = "has_power_tag", required = false) String has_power_tag,
            @RequestParam(value = "power_tag_code", required = false) String power_tag_code,
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
            @RequestParam(value = "device_code", required = false) String device_code,
            @RequestParam(value = "device_status", required = false) String device_status,
            @RequestParam(value = "warehouse_id", required = false) String warehouse_id,
            @RequestParam(value = "category_id", required = false) String category_id,
            @RequestParam(value = "asset_category_id", required = false) String asset_category_id,
            @RequestParam(value = "finance_category_id", required = false) String finance_category_id,
            @RequestParam(value = "category_kw", required = false) String category_kw,
            @RequestParam(value = "asset_category_kw", required = false) String asset_category_kw,
            @RequestParam(value = "finance_category_kw", required = false) String finance_category_kw,
            @RequestParam(value = "stock_scope", required = false) String stock_scope,
            @RequestParam(value = "hide_returned", required = false) Boolean hide_returned) {
        FilterBuild fb = buildListFilter(query, enable_dateFrom, enable_dateTo,
                created_atFrom, created_atTo, production_dateFrom, production_dateTo,
                acceptance_dateFrom, acceptance_dateTo,
                has_power_tag, power_tag_code,
                supplier_id, manufacturer_id, supplier_name, manufacturer_name,
                device_name, specification, model, dept_id, manage_dept_id,
                dept_name, manage_dept_name, serial_number, device_code, device_status,
                warehouse_id, category_id, asset_category_id, finance_category_id,
                category_kw, asset_category_kw, finance_category_kw, stock_scope, hide_returned);
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COUNT(*) " + fb.from + fb.where, Long.class, fb.args.toArray())).orElse(0L);
        List<Object> args = new ArrayList<>(fb.args);
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT d.*,
                       use_dept.dept_name AS dept_name,
                       mgr_dept.dept_name AS manage_dept_name,
                       wh.warehouse_name AS warehouse_name,
                       mdc.category_name AS category_name,
                       ac.category_name AS asset_category_name,
                       fc.finance_name AS finance_category_name,
                       u.unit_name AS unit_name,
                       mfr.manufacturer_code AS manufacturer_code,
                       mfr.manufacturer_name AS manufacturer_name,
                       sup.supplier_code AS supplier_code,
                       sup.supplier_name AS supplier_name,
                       CASE WHEN d.warehouse_id IS NOT NULL THEN 1 ELSE 0 END AS stock_quantity,
                       CASE WHEN d.service_expiry_date IS NULL THEN NULL
                            WHEN d.service_expiry_date <= CURRENT_DATE THEN TRUE
                            ELSE FALSE END AS service_expiry_reached,
                       CASE WHEN d.service_expiry_date IS NULL THEN NULL
                            ELSE GREATEST(0, (d.service_expiry_date - CURRENT_DATE)) END AS service_expiry_remaining_days,
                       CASE WHEN pt.tag_code IS NOT NULL THEN TRUE ELSE FALSE END AS has_power_tag,
                       pt.tag_code AS power_tag_code,
                       EXISTS (
                         SELECT 1 FROM device_warranty_device wd
                         JOIN device_warranty w ON w.id = wd.warranty_id
                         WHERE wd.device_id = d.id
                           AND w.start_date <= CURRENT_DATE AND w.end_date >= CURRENT_DATE
                           AND wd.is_deleted = 0 AND w.is_deleted = 0
                       ) AS under_warranty
                """ + fb.from + powerTagLateralJoin() + fb.where + buildOrderBy(query)
                + " LIMIT ? OFFSET ?", args.toArray());
        MedicalDeviceDeleteGuard.enrichCanDelete(jdbc, rows);
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    /** AST-UI-15：当前筛选条件下全部设备 id（全选查询结果） */
    @GetMapping("/ids")
    public Result<List<String>> ids(
            PageQuery query,
            @RequestParam(value = "enable_dateFrom", required = false) String enable_dateFrom,
            @RequestParam(value = "enable_dateTo", required = false) String enable_dateTo,
            @RequestParam(value = "created_atFrom", required = false) String created_atFrom,
            @RequestParam(value = "created_atTo", required = false) String created_atTo,
            @RequestParam(value = "production_dateFrom", required = false) String production_dateFrom,
            @RequestParam(value = "production_dateTo", required = false) String production_dateTo,
            @RequestParam(value = "acceptance_dateFrom", required = false) String acceptance_dateFrom,
            @RequestParam(value = "acceptance_dateTo", required = false) String acceptance_dateTo,
            @RequestParam(value = "has_power_tag", required = false) String has_power_tag,
            @RequestParam(value = "power_tag_code", required = false) String power_tag_code,
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
            @RequestParam(value = "device_code", required = false) String device_code,
            @RequestParam(value = "device_status", required = false) String device_status,
            @RequestParam(value = "warehouse_id", required = false) String warehouse_id,
            @RequestParam(value = "category_id", required = false) String category_id,
            @RequestParam(value = "asset_category_id", required = false) String asset_category_id,
            @RequestParam(value = "finance_category_id", required = false) String finance_category_id,
            @RequestParam(value = "category_kw", required = false) String category_kw,
            @RequestParam(value = "asset_category_kw", required = false) String asset_category_kw,
            @RequestParam(value = "finance_category_kw", required = false) String finance_category_kw,
            @RequestParam(value = "stock_scope", required = false) String stock_scope,
            @RequestParam(value = "hide_returned", required = false) Boolean hide_returned) {
        FilterBuild fb = buildListFilter(query, enable_dateFrom, enable_dateTo,
                created_atFrom, created_atTo, production_dateFrom, production_dateTo,
                acceptance_dateFrom, acceptance_dateTo,
                has_power_tag, power_tag_code,
                supplier_id, manufacturer_id, supplier_name, manufacturer_name,
                device_name, specification, model, dept_id, manage_dept_id,
                dept_name, manage_dept_name, serial_number, device_code, device_status, warehouse_id,
                category_id, asset_category_id, finance_category_id, category_kw, asset_category_kw,
                finance_category_kw, stock_scope, hide_returned);
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COUNT(*) " + fb.from + fb.where, Long.class, fb.args.toArray())).orElse(0L);
        if (total > BATCH_LIMIT) {
            throw new BizException(400, "当前查询结果超过 " + BATCH_LIMIT + " 条，请缩小条件后再全选");
        }
        var rows = jdbc.queryForList("SELECT d.id " + fb.from + fb.where + " ORDER BY d.created_at DESC NULLS LAST",
                fb.args.toArray());
        List<String> ids = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row.get("id") != null) ids.add(String.valueOf(row.get("id")));
        }
        return Result.ok(ids);
    }

    /** AST-UI-15：批量修改白名单字段 */
    @PostMapping("/batch-update")
    @Transactional
    @OperationLog(module = "asset", description = "批量修改设备")
    public Result<Map<String, Object>> batchUpdate(@RequestBody Map<String, Object> body) {
        List<String> ids = Boolean.TRUE.equals(body.get("all"))
                ? resolveIdsByFilterBody(body)
                : parseIds(body.get("ids"));
        if (ids.isEmpty()) {
            throw new BizException(400, "请选择设备或确认当前查询条件下有结果");
        }
        if (ids.size() > BATCH_LIMIT) {
            throw new BizException(400, "单次批量修改不能超过 " + BATCH_LIMIT + " 条，请缩小查询条件");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = body.get("fields") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        if (fields.isEmpty()) {
            throw new BizException(400, "请至少勾选一项要修改的字段");
        }
        Set<String> cols = TableColumnCache.columns(jdbc, "medical_device");
        List<String> sets = new ArrayList<>();
        List<Object> baseArgs = new ArrayList<>();
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            String key = e.getKey();
            if (BATCH_UUID_FIELDS.contains(key)) {
                if (!cols.contains(key)) continue;
                sets.add(key + " = ?::uuid");
                Object v = e.getValue();
                baseArgs.add(v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v));
            } else if (BATCH_STR_FIELDS.contains(key)) {
                if (!cols.contains(key)) continue;
                sets.add(key + " = ?");
                Object v = e.getValue();
                baseArgs.add(v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v));
            } else if (BATCH_BOOL_FIELDS.contains(key)) {
                if (!cols.contains(key)) continue;
                sets.add(key + " = ?");
                baseArgs.add(Boolean.TRUE.equals(e.getValue()) || "true".equalsIgnoreCase(String.valueOf(e.getValue())));
            }
        }
        if (sets.isEmpty()) {
            throw new BizException(400, "没有可更新的字段");
        }
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, cols, sets, baseArgs);
        int updated = 0;
        for (String idStr : ids) {
            Map<String, Object> before = changeLog.loadRow("medical_device", idStr);
            if (before == null) continue;
            List<Object> args = new ArrayList<>(baseArgs);
            args.add(idStr);
            jdbc.update("UPDATE medical_device SET " + String.join(", ", sets)
                    + " WHERE id = ?::uuid"
                    + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), args.toArray());
            changeLog.recordUpdate("medical_device", idStr, before, changeLog.loadRow("medical_device", idStr));
            updated++;
        }
        return Result.ok(Map.of("updated", updated));
    }

    private FilterBuild buildListFilter(
            PageQuery query,
            String enable_dateFrom, String enable_dateTo,
            String created_atFrom, String created_atTo,
            String production_dateFrom, String production_dateTo,
            String acceptance_dateFrom, String acceptance_dateTo,
            String has_power_tag, String power_tag_code,
            String supplier_id, String manufacturer_id,
            String supplier_name, String manufacturer_name,
            String device_name, String specification, String model,
            String dept_id, String manage_dept_id,
            String dept_name, String manage_dept_name,
            String serial_number, String device_code, String device_status,
            String warehouse_id, String category_id, String asset_category_id, String finance_category_id,
            String category_kw, String asset_category_kw, String finance_category_kw,
            String stock_scope, Boolean hide_returned) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d"));
        List<Object> args = new ArrayList<>();
        if ("warehouse".equalsIgnoreCase(stock_scope)) {
            where.append(" AND d.warehouse_id IS NOT NULL ");
        } else if ("dept".equalsIgnoreCase(stock_scope)) {
            where.append(" AND d.warehouse_id IS NULL ");
        }
        if (Boolean.TRUE.equals(hide_returned)) {
            where.append(" AND COALESCE(d.device_status, '') <> 'returned' ");
        }
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
        FilterCsvSupport.appendUuidIn(where, args, "d.dept_id", dept_id);
        FilterCsvSupport.appendUuidIn(where, args, "d.manage_dept_id", manage_dept_id);
        appendUuidEq(where, args, "d.warehouse_id", warehouse_id);
        FilterCsvSupport.appendUuidIn(where, args, "d.category_id", category_id);
        FilterCsvSupport.appendUuidIn(where, args, "d.asset_category_id", asset_category_id);
        FilterCsvSupport.appendUuidIn(where, args, "d.finance_category_id", finance_category_id);
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
        appendLike(where, args, "d.device_code", device_code);
        FilterCsvSupport.appendStrIn(where, args, "d.device_status", device_status);
        FilterCsvSupport.appendCodeNamePinyin(where, args, "mdc.category_code", "mdc.category_name", null, category_kw);
        FilterCsvSupport.appendCodeNamePinyin(where, args, "ac.category_code", "ac.category_name", null, asset_category_kw);
        FilterCsvSupport.appendCodeNamePinyin(where, args, "fc.finance_code", "fc.finance_name", null, finance_category_kw);
        appendDateGe(where, args, "d.enable_date", enable_dateFrom);
        appendDateLe(where, args, "d.enable_date", enable_dateTo);
        appendDateGe(where, args, "d.production_date", production_dateFrom);
        appendDateLe(where, args, "d.production_date", production_dateTo);
        appendDateGe(where, args, "d.acceptance_date", acceptance_dateFrom);
        appendDateLe(where, args, "d.acceptance_date", acceptance_dateTo);
        if (hasText(created_atFrom)) {
            where.append(" AND d.created_at >= ?::date ");
            args.add(created_atFrom.trim());
        }
        if (hasText(created_atTo)) {
            where.append(" AND d.created_at < (?::date + INTERVAL '1 day') ");
            args.add(created_atTo.trim());
        }
        appendPowerTagFilters(where, args, has_power_tag, power_tag_code);
        String from = buildFrom(true, true, true, true, true, true);
        return new FilterBuild(from, where.toString(), args);
    }

    private List<String> resolveIdsByFilterBody(Map<String, Object> body) {
        PageQuery query = new PageQuery();
        Object kw = body.get("keyword");
        if (kw != null) query.setKeyword(String.valueOf(kw));
        FilterBuild fb = buildListFilter(
                query,
                str(body.get("enable_dateFrom")), str(body.get("enable_dateTo")),
                str(body.get("created_atFrom")), str(body.get("created_atTo")),
                str(body.get("production_dateFrom")), str(body.get("production_dateTo")),
                str(body.get("acceptance_dateFrom")), str(body.get("acceptance_dateTo")),
                str(body.get("has_power_tag")), str(body.get("power_tag_code")),
                str(body.get("supplier_id")), str(body.get("manufacturer_id")),
                str(body.get("supplier_name")), str(body.get("manufacturer_name")),
                str(body.get("device_name")), str(body.get("specification")), str(body.get("model")),
                str(body.get("dept_id")), str(body.get("manage_dept_id")),
                str(body.get("dept_name")), str(body.get("manage_dept_name")),
                str(body.get("serial_number")), str(body.get("device_code")), str(body.get("device_status")),
                str(body.get("warehouse_id")), str(body.get("category_id")),
                str(body.get("asset_category_id")), str(body.get("finance_category_id")),
                str(body.get("category_kw")), str(body.get("asset_category_kw")), str(body.get("finance_category_kw")),
                str(body.get("stock_scope")),
                body.get("hide_returned") == null ? null : Boolean.valueOf(String.valueOf(body.get("hide_returned"))));
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COUNT(*) " + fb.from + fb.where, Long.class, fb.args.toArray())).orElse(0L);
        if (total > BATCH_LIMIT) {
            throw new BizException(400, "单次批量修改不能超过 " + BATCH_LIMIT + " 条，请缩小查询条件");
        }
        var rows = jdbc.queryForList("SELECT d.id " + fb.from + fb.where, fb.args.toArray());
        List<String> ids = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row.get("id") != null) ids.add(String.valueOf(row.get("id")));
        }
        return ids;
    }

    private static List<String> parseIds(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof Collection<?> c) {
            List<String> out = new ArrayList<>();
            for (Object o : c) {
                if (o != null && !String.valueOf(o).isBlank()) out.add(String.valueOf(o));
            }
            return out;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private record FilterBuild(String from, String where, List<Object> args) {}

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void appendLike(StringBuilder where, List<Object> args, String column, String value) {
        if (!hasText(value)) return;
        where.append(" AND ").append(column).append(" ILIKE ? ");
        args.add("%" + value.trim() + "%");
    }

    private static void appendDateGe(StringBuilder where, List<Object> args, String column, String value) {
        if (!hasText(value)) return;
        where.append(" AND ").append(column).append(" >= ?::date ");
        args.add(value.trim());
    }

    private static void appendDateLe(StringBuilder where, List<Object> args, String column, String value) {
        if (!hasText(value)) return;
        where.append(" AND ").append(column).append(" <= ?::date ");
        args.add(value.trim());
    }

    /** AST-UI-16：电流监测标签筛选（未软删绑定） */
    private void appendPowerTagFilters(StringBuilder where, List<Object> args,
                                       String has_power_tag, String power_tag_code) {
        String notDel = SoftDeleteSupport.notDeletedClause(jdbc, "power_tag", "pt");
        if (hasText(has_power_tag)) {
            String v = has_power_tag.trim().toLowerCase(Locale.ROOT);
            boolean wantYes = "true".equals(v) || "1".equals(v) || "yes".equals(v) || "是".equals(has_power_tag.trim());
            boolean wantNo = "false".equals(v) || "0".equals(v) || "no".equals(v) || "否".equals(has_power_tag.trim());
            if (wantYes) {
                where.append(" AND EXISTS (SELECT 1 FROM power_tag pt WHERE pt.device_id = d.id ")
                        .append(notDel).append(") ");
            } else if (wantNo) {
                where.append(" AND NOT EXISTS (SELECT 1 FROM power_tag pt WHERE pt.device_id = d.id ")
                        .append(notDel).append(") ");
            }
        }
        if (hasText(power_tag_code)) {
            where.append(" AND EXISTS (SELECT 1 FROM power_tag pt WHERE pt.device_id = d.id ")
                    .append(notDel).append(" AND pt.tag_code ILIKE ?) ");
            args.add("%" + power_tag_code.trim() + "%");
        }
    }

    /** AST-UI-16：列表带出一条有效标签编码 */
    private String powerTagLateralJoin() {
        String notDel = SoftDeleteSupport.notDeletedClause(jdbc, "power_tag", "pt");
        return """
                 LEFT JOIN LATERAL (
                   SELECT pt.tag_code
                   FROM power_tag pt
                   WHERE pt.device_id = d.id
                """ + notDel + """
                   ORDER BY pt.updated_at DESC NULLS LAST, pt.created_at DESC NULLS LAST
                   LIMIT 1
                 ) pt ON TRUE
                """;
    }

    /** @deprecated 使用 {@link FilterCsvSupport#appendStrIn} */
    private static void appendStatusIn(StringBuilder where, List<Object> args, String column, String csv) {
        FilterCsvSupport.appendStrIn(where, args, column, csv);
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
                             boolean needUseDept, boolean needManageDept, boolean needWarehouse,
                             boolean needCategory) {
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
        if (needWarehouse) {
            from.append(" LEFT JOIN warehouse wh ON d.warehouse_id = wh.id ")
                    .append(SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "wh"));
        }
        if (needCategory) {
            from.append(" LEFT JOIN medical_device_category mdc ON d.category_id = mdc.id ")
                    .append(SoftDeleteSupport.notDeletedClause(jdbc, "medical_device_category", "mdc"));
            from.append(" LEFT JOIN asset_category ac ON d.asset_category_id = ac.id ")
                    .append(SoftDeleteSupport.notDeletedClause(jdbc, "asset_category", "ac"));
            from.append(" LEFT JOIN finance_category fc ON d.finance_category_id = fc.id ")
                    .append(SoftDeleteSupport.notDeletedClause(jdbc, "finance_category", "fc"));
            from.append(" LEFT JOIN unit_dict u ON d.unit_id = u.id ")
                    .append(SoftDeleteSupport.notDeletedClause(jdbc, "unit_dict", "u"));
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
            case "dept_id", "dept_name" -> "use_dept.dept_name";
            default -> null;
        };
        if (column == null) {
            return " ORDER BY d.created_at DESC NULLS LAST, d.device_code ASC";
        }
        return " ORDER BY " + column + " " + dir + " NULLS LAST, d.device_code ASC";
    }

    /**
     * 精确按设备编码查台账（小程序扫码报修用）。
     * 返回 can_report / cannot_report_reason，便于前端提示。
     */
    @GetMapping("/by-code/{deviceCode}")
    public Result<Map<String, Object>> byCode(@PathVariable String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new BizException(400, "设备编码不能为空");
        }
        String code = deviceCode.trim();
        String notDeleted = SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d");
        var rows = jdbc.queryForList("""
                SELECT d.*, dept.dept_name
                FROM medical_device d
                LEFT JOIN department dept ON dept.id = d.dept_id
                WHERE d.device_code = ?
                """ + notDeleted + " LIMIT 1", code);
        if (rows.isEmpty()) {
            throw new BizException(404, "未找到设备: " + code);
        }
        Map<String, Object> d = new LinkedHashMap<>(rows.get(0));
        String status = Objects.toString(d.get("device_status"), "");
        boolean blockedByStatus = Set.of("maintenance", "pending_verify", "scrap").contains(status);
        boolean busy = !jdbc.queryForList("""
                SELECT id FROM repair_workorder
                WHERE device_id = ?::uuid
                  AND status IN ('reported','dispatching','pending_accept','accepted','repairing','pending_verify','suspended','verify_rejected')
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null) + " LIMIT 1",
                d.get("id")).isEmpty();
        boolean canReport = !blockedByStatus && !busy
                && !Boolean.FALSE.equals(d.get("is_active"));
        d.put("can_report", canReport);
        if (!canReport) {
            if (blockedByStatus) {
                d.put("cannot_report_reason", "设备当前不可报修: " + status);
            } else if (busy) {
                d.put("cannot_report_reason", "该设备已有进行中的报修单");
            } else {
                d.put("cannot_report_reason", "设备未启用，不可报修");
            }
        }
        return Result.ok(d);
    }

    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> detail(@PathVariable UUID id) {
        var device = jdbc.queryForList("""
                SELECT d.*, dept.dept_name
                FROM medical_device d
                LEFT JOIN department dept ON dept.id = d.dept_id
                WHERE d.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d"), id);
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
        String printedByName = null;
        if (printedBy != null && !printedBy.toString().isBlank()) {
            var nameRows = jdbc.queryForList(
                    "SELECT COALESCE(NULLIF(TRIM(real_name), ''), username) AS name FROM sys_user WHERE id = ?::uuid",
                    printedBy);
            if (!nameRows.isEmpty() && nameRows.get(0).get("name") != null) {
                printedByName = nameRows.get(0).get("name").toString();
            }
        }
        jdbc.update("""
                INSERT INTO device_label_print_log
                (id, device_id, device_code, device_name, printed_by, printed_by_name, template_code, biz_type, biz_id, remark)
                VALUES (?::uuid, ?::uuid, ?, ?, ?::uuid, ?, ?, 'device', ?::uuid, ?)
                """, logId, id, code, device.get("device_name"), printedBy, printedByName, template, id, remark);
        jdbc.update("""
                UPDATE medical_device SET label_printed = TRUE, qr_code_url = ?, updated_at = NOW() WHERE id = ?::uuid
                """, code, id);
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM device_label_print_log WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_label_print_log", null), logId).get(0));
    }
}
