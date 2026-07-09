package com.meis.saas.common.web;

import com.meis.saas.common.exception.BizException;
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
            @PathVariable String table, com.meis.saas.common.page.PageQuery query) {
        check(table);
        return Result.ok(com.meis.saas.common.page.PageableJdbc.query(jdbc(), table, query));
    }

    @GetMapping("/{table}/list")
    public Result<List<Map<String, Object>>> list(@PathVariable String table,
                                                   @RequestParam(defaultValue = "50") int limit) {
        check(table);
        return Result.ok(jdbc().queryForList("SELECT * FROM " + table + " LIMIT " + Math.min(limit, 500)));
    }

    @GetMapping("/{table}/{id}")
    public Result<Map<String, Object>> get(@PathVariable String table, @PathVariable String id) {
        check(table);
        List<Map<String, Object>> rows = jdbc().queryForList("SELECT * FROM " + table + " WHERE id = ?::uuid", id);
        return Result.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/{table}")
    public Result<Map<String, Object>> create(@PathVariable String table, @RequestBody Map<String, Object> body) {
        check(table);
        if ("medical_device".equals(table)) {
            MedicalDeviceFieldHelper.applyDerivedFields(body);
        }
        prepareInsertDefaults(table, body);
        normalizeUuidFields(body);
        if (!body.containsKey("id") || isBlank(body.get("id"))) {
            body.put("id", UUID.randomUUID().toString());
        }
        String cols = String.join(",", body.keySet());
        String vals = String.join(",", body.keySet().stream().map(GenericTableController::placeholder).toList());
        jdbc().update("INSERT INTO " + table + " (" + cols + ") VALUES (" + vals + ")", body.values().toArray());
        return Result.ok(body);
    }

    @PutMapping("/{table}/{id}")
    public Result<Void> update(@PathVariable String table, @PathVariable String id, @RequestBody Map<String, Object> body) {
        check(table);
        guardInventoryCheckMutable(table, id);
        body.remove("id");
        if ("medical_device".equals(table)) {
            MedicalDeviceFieldHelper.applyDerivedFields(body);
        }
        normalizeUuidFields(body);
        if (body.isEmpty()) return Result.ok();
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        body.forEach((k, v) -> {
            sets.add(k + " = " + placeholder(k));
            args.add(v);
        });
        args.add(id);
        jdbc().update("UPDATE " + table + " SET " + String.join(",", sets) + " WHERE id = ?::uuid", args.toArray());
        return Result.ok();
    }

    @GetMapping("/{table}/export")
    public void export(@PathVariable String table, HttpServletResponse resp) throws IOException {
        check(table);
        ExcelExportHelper.exportCsv(jdbc(), table, resp);
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

    @DeleteMapping("/{table}/{id}")
    public Result<Void> delete(@PathVariable String table, @PathVariable String id) {
        check(table);
        guardInventoryCheckMutable(table, id);
        jdbc().update("DELETE FROM " + table + " WHERE id = ?::uuid", id);
        return Result.ok();
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
        return "id".equals(column) || column.endsWith("_id");
    }

    private static String placeholder(String column) {
        return isUuidColumn(column) ? "?::uuid" : "?";
    }

    private static boolean isBlank(Object v) {
        return v == null || (v instanceof String s && s.isBlank());
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
