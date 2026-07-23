package com.meis.saas.common.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meis.saas.common.asset.MedicalDeviceDeleteGuard;
import com.meis.saas.common.asset.SparePartDeleteGuard;
import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.code.DailyBizNoSupport;
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
import com.meis.saas.common.excel.MedicalDeviceCategoryImporter;
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
    private static final ObjectMapper JSON = new ObjectMapper();
    /** JSONB 列：Map/List 回写须序列化为字符串并以 ?::jsonb 绑定，否则驱动误走 hstore。 */
    private static final Set<String> JSONB_COLUMNS = Set.of(
            "extension_data", "manual_files", "certificate_files",
            "changed_fields", "snapshot_json", "permissions");

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
        int lim = Math.min(Math.max(limit, 1), 5000);
        return Result.ok(jdbc().queryForList("SELECT * FROM " + table + where + " LIMIT " + lim));
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

    /**
     * 按设备/资产名称模糊匹配 68 分类：先三级、再二级、再一级；均无命中回落未识别分类。
     * AST-UI-11
     */
    @GetMapping("/{table}/match-by-device-name")
    public Result<Map<String, Object>> matchByDeviceName(
            @PathVariable String table,
            @RequestParam String name) {
        check(table);
        if (!"medical_device_category".equals(table)) {
            throw new BizException(400, "match-by-device-name only supports medical_device_category");
        }
        String notDeleted = SoftDeleteSupport.notDeletedClause(jdbc(), table, null);
        Map<String, Object> matched = null;
        List<String> keys = buildCategoryMatchKeys(name);
        if (!keys.isEmpty()) {
            // 自最低级（三级）逐级向上匹配
            for (int level = 3; level >= 1 && matched == null; level--) {
                matched = matchCategoryAtLevel(notDeleted, level, keys);
            }
        }
        if (matched == null) {
            List<Map<String, Object>> fallback = jdbc().queryForList(
                    """
                    SELECT id, category_code, category_name, level
                    FROM medical_device_category
                    WHERE 1=1
                    """ + notDeleted + """
                      AND (
                        category_code = '6890'
                        OR category_name IN ('未识别分类', '未知分类')
                      )
                    ORDER BY
                      CASE
                        WHEN category_code = '6890' THEN 0
                        WHEN category_name = '未识别分类' THEN 1
                        ELSE 2
                      END
                    LIMIT 1
                    """);
            if (fallback.isEmpty()) {
                throw new BizException(404, "未配置未识别/未知分类（建议编码 6890）");
            }
            matched = fallback.get(0);
            matched.put("unmatched", true);
        } else {
            matched.put("unmatched", false);
        }
        String code = matched.get("category_code") == null ? "" : String.valueOf(matched.get("category_code"));
        String cname = matched.get("category_name") == null ? "" : String.valueOf(matched.get("category_name"));
        matched.put("label", (code + " " + cname).trim());
        return Result.ok(matched);
    }

    /** 去掉括号备注，并生成同义/截断关键词（长词优先） */
    private static List<String> buildCategoryMatchKeys(String raw) {
        if (raw == null) return List.of();
        String n = raw.trim()
                .replaceAll("[（(][^）)]*[）)]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (n.isEmpty()) return List.of();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        addMatchKey(set, n);
        addMatchKey(set, n.replace("仪", "设备"));
        addMatchKey(set, n.replace("仪", "机"));
        addMatchKey(set, n.replace("机", "设备"));
        addMatchKey(set, n.replace("设备", "仪"));
        for (String suffix : List.of("仪", "机", "器", "设备", "系统", "装置", "床")) {
            if (n.endsWith(suffix) && n.length() > suffix.length() + 1) {
                addMatchKey(set, n.substring(0, n.length() - suffix.length()));
            }
        }
        // 「病人监护」这类去掉尾缀后的核心词再试
        String core = n.replaceAll("(仪|机|器|设备|系统|装置)$", "");
        addMatchKey(set, core);
        List<String> keys = new ArrayList<>(set);
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return keys;
    }

    private static void addMatchKey(Set<String> set, String key) {
        if (key == null) return;
        String k = key.trim();
        if (k.length() >= 2) set.add(k);
    }

    private Map<String, Object> matchCategoryAtLevel(String notDeleted, int level, List<String> keys) {
        if (keys.isEmpty()) return null;
        StringBuilder sql = new StringBuilder(
                """
                SELECT id, category_code, category_name, level
                FROM medical_device_category
                WHERE 1=1
                """);
        sql.append(notDeleted);
        sql.append(" AND COALESCE(level, 0) = ? ");
        sql.append(" AND COALESCE(category_code, '') <> '6890' ");
        sql.append(" AND COALESCE(category_name, '') NOT IN ('未识别分类', '未知分类') ");
        sql.append(" AND (");
        List<Object> args = new ArrayList<>();
        args.add(level);
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append(" category_name ILIKE ? ");
            args.add("%" + keys.get(i) + "%");
        }
        sql.append(") ");
        List<Map<String, Object>> rows = jdbc().queryForList(sql.toString(), args.toArray());
        if (rows.isEmpty()) return null;
        Map<String, Object> best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map<String, Object> row : rows) {
            int score = scoreCategoryMatch(String.valueOf(row.get("category_name")), keys);
            if (score > bestScore) {
                bestScore = score;
                best = row;
            }
        }
        return bestScore > 0 ? best : null;
    }

    /** 评分：精确/末段命中 > 包含长词 > 包含短词 */
    private static int scoreCategoryMatch(String categoryName, List<String> keys) {
        if (categoryName == null || categoryName.isBlank()) return 0;
        String full = categoryName.trim();
        String leaf = full;
        int dash = Math.max(full.lastIndexOf('-'), full.lastIndexOf('—'));
        if (dash >= 0 && dash + 1 < full.length()) {
            leaf = full.substring(dash + 1).trim();
        }
        int best = 0;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            int base = key.length() * 10;
            if (full.equals(key) || leaf.equals(key)) {
                best = Math.max(best, 10000 + base);
            } else if (leaf.endsWith(key) || full.endsWith(key)) {
                best = Math.max(best, 8000 + base);
            } else if (leaf.contains(key)) {
                best = Math.max(best, 6000 + base);
            } else if (full.contains(key)) {
                best = Math.max(best, 4000 + base);
            }
        }
        return best;
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
        applyOpsSystemDocNosOnCreate(table, body);
        normalizeUuidFields(body);
        normalizeTemporalFields(body);
        normalizeJsonbFields(body);
        applyCategoryHierarchyDefaults(table, body);
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
        guardOpsPlanApprovedImmutable(table, id);
        SoftDeleteSupport.stripClientUpdateFields(body);
        stripImmutableDocNos(body);
        if ("medical_device".equals(table)) {
            MedicalDeviceFieldHelper.applyDerivedFields(body);
            // 附录 P：设备编码创建后禁止修改
            body.remove("device_code");
        }
        normalizeUuidFields(body);
        normalizeTemporalFields(body);
        normalizeJsonbFields(body);
        applyCategoryHierarchyDefaults(table, body);
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
        List<Map<String, String>> parsed = ExcelImportHelper.parseRows(file, fields);
        if ("medical_device_category".equals(table)) {
            parsed = ExcelImportHelper.ensureCategoryTwoColumnRows(parsed);
            return Result.ok(MedicalDeviceCategoryImporter.importRows(jdbc(), parsed));
        }
        var columns = importProfileService != null
                ? importProfileService.standardColumns(biz, fields)
                : new LinkedHashSet<>(fields.stream().map(ImportFieldDef::effectiveColumn).filter(Objects::nonNull).toList());
        ImportResult result = SimpleTableImporter.importRows(jdbc(), table, parsed, columns);
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
    public Result<Void> delete(@PathVariable String table, @PathVariable String id,
                               @RequestParam(required = false) String client) {
        check(table);
        denyRepairWorkorderBypass(table);
        guardInventoryCheckMutable(table, id);
        guardOpsPlanApprovedImmutable(table, id);
        guardOpsExecutionDeletable(table, id);
        if ("medical_device".equals(table)) {
            MedicalDeviceDeleteGuard.assertDeletable(jdbc(), id);
        }
        if ("spare_part".equals(table)) {
            SparePartDeleteGuard.assertDeletable(jdbc(), id);
        }
        Map<String, Object> before = loadTracked(table, id);
        int n = SoftDeleteSupport.softDelete(jdbc(), table, id, client);
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

    /** OPS.14：运维计划/执行单号系统生成，忽略客户端传入。 */
    private void applyOpsSystemDocNosOnCreate(String table, Map<String, Object> body) {
        switch (table) {
            case "maintenance_plan" -> {
                String no = DailyBizNoSupport.next(jdbc(), table, "plan_no", "MP-");
                body.put("plan_no", no);
                body.put("plan_code", no);
            }
            case "inspection_plan" -> {
                String no = DailyBizNoSupport.next(jdbc(), table, "plan_no", "IP-");
                body.put("plan_no", no);
                body.put("plan_code", no);
            }
            case "pm_plan" -> {
                String no = DailyBizNoSupport.next(jdbc(), table, "plan_no", "PP-");
                body.put("plan_no", no);
                body.put("plan_code", no);
            }
            case "maintenance_execution" ->
                    body.put("execution_no", DailyBizNoSupport.next(jdbc(), table, "execution_no", "ME-"));
            case "inspection_execution" ->
                    body.put("execution_no", DailyBizNoSupport.next(jdbc(), table, "execution_no", "IE-"));
            case "pm_execution" ->
                    body.put("execution_no", DailyBizNoSupport.next(jdbc(), table, "execution_no", "PX-"));
            case "metrology_plan" -> {
                String no = DailyBizNoSupport.next(jdbc(), table, "plan_code", "JL-");
                body.put("plan_code", no);
            }
            case "metrology_execution" ->
                    body.put("execution_no", DailyBizNoSupport.next(jdbc(), table, "execution_no", "JX-"));
            default -> { }
        }
    }

    /** 计划单号 / 执行单号创建后禁止通过通用 CRUD 修改。 */
    private static void stripImmutableDocNos(Map<String, Object> body) {
        body.remove("plan_no");
        body.remove("plan_code");
        body.remove("execution_no");
    }

    /** UUID 列空串转 null，避免 PostgreSQL 类型错误。 */
    private static void normalizeUuidFields(Map<String, Object> body) {
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!isUuidColumn(e.getKey())) continue;
            Object v = e.getValue();
            if (v instanceof String s && s.isBlank()) e.setValue(null);
        }
    }

    /** 日期/时间列空串转 null；前端常回传 ""。 */
    private static void normalizeTemporalFields(Map<String, Object> body) {
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!isDateColumn(e.getKey()) && !isTimestampColumn(e.getKey())) continue;
            Object v = e.getValue();
            if (v instanceof String s && s.isBlank()) e.setValue(null);
        }
    }

    /** Map/List 形态的 JSONB 字段序列化为 JSON 字符串，避免 JDBC 按 hstore 绑定失败。 */
    private static void normalizeJsonbFields(Map<String, Object> body) {
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!isJsonbColumn(e.getKey())) continue;
            Object v = e.getValue();
            if (v == null || v instanceof String || v instanceof org.postgresql.util.PGobject) continue;
            if (v instanceof Map || v instanceof Collection || v.getClass().isArray()) {
                try {
                    e.setValue(JSON.writeValueAsString(v));
                } catch (JsonProcessingException ex) {
                    throw new BizException(400, "无法序列化 JSON 字段：" + e.getKey());
                }
            }
        }
    }

    private static boolean isUuidColumn(String column) {
        return "id".equals(column)
                || column.endsWith("_id")
                || column.endsWith("_by");
    }

    private static boolean isDateColumn(String column) {
        return column.endsWith("_date") || "delivery_deadline".equals(column);
    }

    private static boolean isTimestampColumn(String column) {
        return column.endsWith("_at")
                || column.endsWith("_time")
                || column.endsWith("_datetime");
    }

    private static boolean isJsonbColumn(String column) {
        return JSONB_COLUMNS.contains(column) || column.endsWith("_json") || column.endsWith("_files");
    }

    private static String placeholder(String column) {
        if (isUuidColumn(column)) return "?::uuid";
        if (isJsonbColumn(column)) return "?::jsonb";
        if (isDateColumn(column)) return "?::date";
        if (isTimestampColumn(column)) return "?::timestamptz";
        return "?";
    }

    private static boolean isBlank(Object v) {
        return v == null || (v instanceof String s && s.isBlank());
    }

    /**
     * 设备分类（parent_code 树）：空上级=一级；补齐 level / full_path，避免 NOT NULL 失败。
     */
    private void applyCategoryHierarchyDefaults(String table, Map<String, Object> body) {
        if (!"medical_device_category".equals(table)) return;
        Object pc = body.get("parent_code");
        if (pc instanceof String s && s.isBlank()) {
            body.put("parent_code", null);
            pc = null;
        }
        String parentCode = pc == null ? null : String.valueOf(pc).trim();
        if (parentCode != null && parentCode.isEmpty()) {
            body.put("parent_code", null);
            parentCode = null;
        }
        String name = body.get("category_name") == null ? null : String.valueOf(body.get("category_name")).trim();

        if (parentCode == null) {
            if (isBlank(body.get("level"))) body.put("level", 1);
            if (isBlank(body.get("full_path")) && name != null && !name.isEmpty()) {
                body.put("full_path", name);
            }
            return;
        }
        List<Map<String, Object>> parents = jdbc().queryForList(
                """
                SELECT level, full_path, category_name FROM medical_device_category
                WHERE category_code = ? AND COALESCE(is_deleted, 0) = 0 LIMIT 1
                """,
                parentCode);
        if (parents.isEmpty()) {
            throw new BizException(400, "上级分类不存在：" + parentCode);
        }
        Map<String, Object> parent = parents.get(0);
        if (isBlank(body.get("level"))) {
            int parentLevel = parent.get("level") instanceof Number n ? n.intValue() : 1;
            body.put("level", parentLevel + 1);
        }
        if (isBlank(body.get("full_path")) && name != null && !name.isEmpty()) {
            Object pfp = parent.get("full_path");
            String prefix = pfp != null && !String.valueOf(pfp).isBlank()
                    ? String.valueOf(pfp)
                    : String.valueOf(parent.get("category_name"));
            body.put("full_path", prefix + "/" + name);
        }
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
        SoftDeleteSupport.appendUpdateAuditSets(jdbc(), cols, sets, args);
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

    /** OPS.16.6：已审核运维计划禁止删除整单（明细改走专用 save） */
    private void guardOpsPlanApprovedImmutable(String table, String id) {
        if (!Set.of("maintenance_plan", "pm_plan", "inspection_plan").contains(table)) return;
        List<Map<String, Object>> rows = jdbc().queryForList(
                "SELECT approval_status FROM " + table + " WHERE id = ?::uuid AND COALESCE(is_deleted,0)=0", id);
        if (!rows.isEmpty() && "approved".equals(String.valueOf(rows.get(0).get("approval_status")))) {
            throw new BizException(400, "已审核计划不可删除");
        }
    }

    /** OPS.16.9：任一明细非 pending（已开始/已完成）则禁止删除执行单 */
    private void guardOpsExecutionDeletable(String table, String id) {
        String itemTable = switch (table) {
            case "maintenance_execution" -> "maintenance_execution_item";
            case "pm_execution" -> "pm_execution_item";
            case "inspection_execution" -> "inspection_execution_item";
            default -> null;
        };
        if (itemTable == null) return;
        Integer n = jdbc().queryForObject(
                "SELECT COUNT(1)::int FROM " + itemTable
                        + " WHERE execution_id=?::uuid AND COALESCE(is_deleted,0)=0 AND status <> 'pending'",
                Integer.class, id);
        if (n != null && n > 0) {
            throw new BizException(400, "明细已执行，不可删除执行单");
        }
    }

    private static String importBusinessType(String table) {
        return switch (table) {
            case "supplier" -> ImportFieldRegistry.SUPPLIER;
            case "manufacturer" -> ImportFieldRegistry.MANUFACTURER;
            case "medical_device_category" -> ImportFieldRegistry.DEVICE_CATEGORY;
            default -> null;
        };
    }
}
