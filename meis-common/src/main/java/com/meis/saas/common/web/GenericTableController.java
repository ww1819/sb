package com.meis.saas.common.web;

import com.meis.saas.common.asset.MedicalDeviceDeleteGuard;
import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.common.excel.ExcelExportHelper;
import com.meis.saas.common.excel.ExcelImportHelper;
import com.meis.saas.common.excel.ImportFieldDef;
import com.meis.saas.common.excel.ImportFieldRegistry;
import com.meis.saas.common.excel.ImportProfileService;
import com.meis.saas.common.excel.ImportResult;
import com.meis.saas.common.excel.MedicalDeviceFieldHelper;
import com.meis.saas.common.excel.SimpleTableImporter;
import com.meis.saas.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 通用表 CRUD（租户 Schema 内），供各业务微服务快速暴露 API。
 */
public abstract class GenericTableController {
    @Autowired(required = false)
    private ImportProfileService importProfileService;

    @Autowired(required = false)
    private EntityChangeLogService changeLogService;

    protected void setImportProfileService(ImportProfileService service) {
        this.importProfileService = service;
    }

    protected abstract JdbcTemplate jdbc();
    protected abstract Set<String> tables();

    @GetMapping("/tables")
    public Result<List<String>> tableList() {
        return Result.ok(new ArrayList<>(tables()));
    }

    @GetMapping("/{table}/page")
    public Result<com.meis.saas.common.page.PageResult<Map<String, Object>>> page(
            @PathVariable String table,
            com.meis.saas.common.page.PageQuery query,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam Map<String, String> allParams) {
        check(table);
        if (sortBy != null && !sortBy.isBlank()) query.setSortBy(sortBy.trim());
        if (sortOrder != null && !sortOrder.isBlank()) query.setSortOrder(sortOrder.trim());
        // 扁平查询参数写入 filters（如 parent_id），供树选/列表筛选使用
        if (allParams != null) {
            Set<String> reserved = Set.of("page", "size", "keyword", "sortBy", "sortOrder");
            for (Map.Entry<String, String> e : allParams.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();
                if (key == null || reserved.contains(key) || val == null || val.isBlank()) continue;
                if (!key.matches("^[a-z][a-z0-9_]*$")) continue;
                query.getFilters().put(key, val);
            }
        }
        return Result.ok(com.meis.saas.common.page.PageableJdbc.query(jdbc(), table, query));
    }

    @GetMapping("/{table}/list")
    public Result<List<Map<String, Object>>> list(@PathVariable String table,
                                                   @RequestParam(defaultValue = "50") int limit) {
        check(table);
        String where = " WHERE 1=1 " + SoftDeleteSupport.notDeletedClause(jdbc(), table, null);
        return Result.ok(jdbc().queryForList("SELECT * FROM " + table + where + " LIMIT " + Math.min(limit, 500)));
    }

    @GetMapping("/{table}/lookup")
    public Result<List<Map<String, Object>>> lookup(@PathVariable String table,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "20") int limit) {
        check(table);
        String[] columns = lookupColumns(table);
        if (columns == null) throw new BizException(400, "lookup not supported: " + table);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc(), table, null));
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            where.append(" AND (");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) where.append(" OR ");
                if ("pinyin_code".equals(columns[i])) {
                    where.append("COALESCE(").append(columns[i]).append(", '') ILIKE ?");
                } else {
                    where.append(columns[i]).append(" ILIKE ?");
                }
                args.add(kw);
            }
            where.append(") ");
        }
        int lim = Math.min(Math.max(limit, 1), 50);
        args.add(lim);
        List<Map<String, Object>> rows = jdbc().queryForList(
                "SELECT * FROM " + table + where + " ORDER BY " + columns[0] + " ASC LIMIT ?",
                args.toArray());
        return Result.ok(rows);
    }

    private static String[] lookupColumns(String table) {
        return switch (table) {
            case "supplier" -> new String[]{"supplier_name", "supplier_code", "pinyin_code"};
            case "manufacturer" -> new String[]{"manufacturer_name", "manufacturer_code", "pinyin_code"};
            default -> null;
        };
    }

    @GetMapping("/{table}/{id:[0-9a-fA-F\\-]{36}}")
    public Result<Map<String, Object>> get(
            @PathVariable String table,
            @PathVariable String id) {
        check(table);
        String where = " WHERE id = ?::uuid " + SoftDeleteSupport.notDeletedClause(jdbc(), table, null);
        List<Map<String, Object>> rows = jdbc().queryForList("SELECT * FROM " + table + where, id);
        return Result.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/{table}")
    public Result<Map<String, Object>> create(@PathVariable String table, @RequestBody Map<String, Object> body) {
        check(table);
        denyRepairWorkorderBypass(table);
        if ("medical_device".equals(table)) {
            MedicalDeviceFieldHelper.applyDerivedFields(body);
        }
        prepareInsertDefaults(table, body);
        normalizeUuidFields(body);
        SoftDeleteSupport.applyInsertAudit(jdbc(), table, body);
        var cols = TableColumnCache.columns(jdbc(), table);
        var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc(), table, body);
        if (softDeletedId.isPresent()) {
            String existingId = softDeletedId.get();
            Map<String, Object> before = loadTracked(table, existingId);
            body.put("id", existingId);
            SoftDeleteSupport.prepareRestore(body, cols);
            executeUpdate(table, existingId, body);
            Map<String, Object> after = loadTracked(table, existingId);
            if (changeLogService != null) {
                changeLogService.recordUpdate(table, existingId, before, after);
            }
            return Result.ok(body);
        }
        if (!body.containsKey("id") || isBlank(body.get("id"))) {
            body.put("id", UUID.randomUUID().toString());
        }
        String colNames = String.join(",", body.keySet());
        String vals = String.join(",", body.keySet().stream().map(GenericTableController::placeholder).toList());
        jdbc().update("INSERT INTO " + table + " (" + colNames + ") VALUES (" + vals + ")", body.values().toArray());
        if (changeLogService != null) {
            changeLogService.recordCreate(table, body.get("id"), body);
        }
        return Result.ok(body);
    }

    @PutMapping("/{table}/{id:[0-9a-fA-F\\-]{36}}")
    public Result<Void> update(@PathVariable String table, @PathVariable String id, @RequestBody Map<String, Object> body) {
        check(table);
        denyRepairWorkorderBypass(table);
        guardInventoryCheckMutable(table, id);
        SoftDeleteSupport.stripClientUpdateFields(body);
        if ("medical_device".equals(table)) {
            MedicalDeviceFieldHelper.applyDerivedFields(body);
            // 附录 P：设备编码创建后禁止修改
            body.remove("device_code");
        }
        normalizeUuidFields(body);
        if (body.isEmpty()) return Result.ok();
        Map<String, Object> before = loadTracked(table, id);
        executeUpdate(table, id, body);
        Map<String, Object> after = loadTracked(table, id);
        if (changeLogService != null) {
            changeLogService.recordUpdate(table, id, before, after);
        }
        return Result.ok();
    }

    @GetMapping("/{table}/export")
    public void export(@PathVariable String table,
                       @RequestParam(required = false) String ids,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse resp) throws IOException {
        check(table);
        ExcelExportHelper.exportCsv(jdbc(), table, resp, ids, keyword);
    }

    @GetMapping("/{table}/import/template")
    public void importTemplate(@PathVariable String table,
                               @RequestParam(required = false) String profile,
                               HttpServletResponse resp) throws IOException {
        check(table);
        String biz = importBusinessType(table);
        if (biz == null) throw new BizException(400, "table import not supported: " + table);
        var fields = resolveImportFields(biz, profile);
        ExcelImportHelper.writeTemplate(resp, table + "_import_template.xlsx", fields);
    }

    @PostMapping("/{table}/import")
    public Result<ImportResult> importFile(@PathVariable String table,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String profile) throws IOException {
        check(table);
        String biz = importBusinessType(table);
        if (biz == null) throw new BizException(400, "table import not supported: " + table);
        var fields = resolveImportFields(biz, profile);
        var columns = importProfileService != null
                ? importProfileService.standardColumns(biz, fields)
                : new LinkedHashSet<>(fields.stream().map(ImportFieldDef::effectiveColumn).filter(Objects::nonNull).toList());
        ImportResult result = SimpleTableImporter.importRows(jdbc(), table, ExcelImportHelper.parseRows(file, fields), columns);
        return Result.ok(result);
    }

    private List<ImportFieldDef> resolveImportFields(String businessType, String profile) {
        if (importProfileService != null) {
            return importProfileService.resolveFields(businessType, profile);
        }
        return ImportFieldRegistry.get(businessType);
    }

    @PostMapping("/{table}/generate-pinyin")
    public Result<Map<String, Object>> generatePinyin(@PathVariable String table, @RequestBody Map<String, Object> body) {
        check(table);
        Map<String, String> meta = PinyinCodeBatchUpdater.pinyinMeta(table);
        if (meta == null) throw new BizException(400, "table pinyin not supported: " + table);
        int count;
        if (Boolean.TRUE.equals(body.get("all"))) {
            count = PinyinCodeBatchUpdater.updateByKeyword(jdbc(), meta.get("table"), meta.get("nameColumn"), meta.get("codeColumn"),
                    body.get("keyword") == null ? null : body.get("keyword").toString());
        } else {
            @SuppressWarnings("unchecked")
            List<String> idStrs = (List<String>) body.get("ids");
            List<UUID> ids = idStrs == null ? List.of() : idStrs.stream().map(UUID::fromString).toList();
            count = PinyinCodeBatchUpdater.updateByIds(jdbc(), meta.get("table"), meta.get("nameColumn"), ids);
        }
        return Result.ok(Map.of("updated", count));
    }

    @DeleteMapping("/{table}/{id:[0-9a-fA-F\\-]{36}}")
    public Result<Void> delete(@PathVariable String table, @PathVariable String id) {
        check(table);
        denyRepairWorkorderBypass(table);
        guardInventoryCheckMutable(table, id);
        if ("medical_device".equals(table)) {
            MedicalDeviceDeleteGuard.assertDeletable(jdbc(), id);
        }
        Map<String, Object> before = loadTracked(table, id);
        int n = SoftDeleteSupport.softDelete(jdbc(), table, id);
        if (n == 0 && SoftDeleteSupport.supportsSoftDelete(jdbc(), table)) {
            throw new BizException(404, "not found");
        }
        if (changeLogService != null && before != null) {
            changeLogService.recordDelete(table, id, before);
        }
        return Result.ok();
    }

    private Map<String, Object> loadTracked(String table, String id) {
        if (changeLogService == null || !changeLogService.tracks(table)) return null;
        return changeLogService.loadRow(table, id);
    }

    private static void denyRepairWorkorderBypass(String table) {
        if ("repair_workorder".equals(table)) {
            throw new BizException(400, "报修请使用 /api/repair/workorder 专用接口（草稿/提交/撤回）");
        }
    }

    private void check(String table) {
        if (!tables().contains(table)) throw new BizException(400, "table not allowed: " + table);
        if ("public".equals(TenantContext.getSchemaName())) throw new BizException(403, "tenant context required");
    }

    /** 补齐单据号等必填默认值，避免通用 CRUD 插入因 NOT NULL 失败。 */
    private static void prepareInsertDefaults(String table, Map<String, Object> body) {
        String ts = String.valueOf(System.currentTimeMillis());
        switch (table) {
            case "inventory_check" -> {
                if (isBlank(body.get("check_no"))) body.put("check_no", "IC" + ts);
                if (isBlank(body.get("status"))) body.put("status", "planning");
                if (isBlank(body.get("audit_status"))) body.put("audit_status", "pending");
            }
            case "device_entry" -> {
                if (isBlank(body.get("entry_no"))) body.put("entry_no", "EN" + ts);
                if (isBlank(body.get("status"))) body.put("status", "draft");
            }
            case "device_outbound" -> {
                if (isBlank(body.get("outbound_no"))) body.put("outbound_no", "OB" + ts);
            }
            case "asset_transfer" -> {
                if (isBlank(body.get("transfer_no"))) body.put("transfer_no", "TF" + ts);
            }
            case "device_scrap" -> {
                if (isBlank(body.get("scrap_no"))) body.put("scrap_no", "SC" + ts);
            }
            default -> { }
        }
    }

    /** UUID 列空串转 null，避免 PostgreSQL 类型错误。 */
    private static void normalizeUuidFields(Map<String, Object> body) {
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!isUuidColumn(e.getKey())) continue;
            Object v = e.getValue();
            if (v instanceof String s && s.isBlank()) e.setValue(null);
        }
    }

    private static boolean isUuidColumn(String column) {
        return "id".equals(column)
                || column.endsWith("_id")
                || column.endsWith("_by");
    }

    private static String placeholder(String column) {
        return isUuidColumn(column) ? "?::uuid" : "?";
    }

    private static boolean isBlank(Object v) {
        return v == null || (v instanceof String s && s.isBlank());
    }

    /** 由 SoftDeleteSupport.appendUpdateAuditSets 统一写入，禁止进入 SET 以免列重复。 */
    private void executeUpdate(String table, String id, Map<String, Object> body) {
        var cols = TableColumnCache.columns(jdbc(), table);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        body.forEach((k, v) -> {
            if (!cols.contains(k) || SoftDeleteSupport.isUpdateSkipColumn(k)) return;
            sets.add(k + " = " + placeholder(k));
            args.add(v);
        });
        SoftDeleteSupport.appendUpdateAuditSets(cols, sets, args);
        if (sets.isEmpty()) return;
        args.add(id);
        jdbc().update("UPDATE " + table + " SET " + String.join(",", sets) + " WHERE id = ?::uuid", args.toArray());
    }

    private void guardInventoryCheckMutable(String table, String id) {
        if (!"inventory_check".equals(table)) return;
        List<Map<String, Object>> rows = jdbc().queryForList(
                "SELECT audit_status FROM inventory_check WHERE id = ?::uuid", id);
        if (!rows.isEmpty() && "approved".equals(String.valueOf(rows.get(0).get("audit_status")))) {
            throw new BizException(400, "已审核的盘点单不可修改或删除");
        }
    }

    private static String importBusinessType(String table) {
        return switch (table) {
            case "supplier" -> ImportFieldRegistry.SUPPLIER;
            case "manufacturer" -> ImportFieldRegistry.MANUFACTURER;
            default -> null;
        };
    }
}
