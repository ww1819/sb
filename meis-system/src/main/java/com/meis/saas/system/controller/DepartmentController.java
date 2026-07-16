package com.meis.saas.system.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.cache.CacheKeys;
import com.meis.saas.common.cache.MeisCacheEviction;
import com.meis.saas.common.cache.MeisCacheProperties;
import com.meis.saas.common.cache.RedisJsonCache;
import com.meis.saas.common.excel.CsvExportHelper;
import com.meis.saas.common.excel.ExcelImportHelper;
import com.meis.saas.common.excel.ImportFieldRegistry;
import com.meis.saas.common.excel.ImportProfileService;
import com.meis.saas.common.excel.ImportResult;
import com.meis.saas.common.excel.ImportValueParser;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.util.PinyinCodeUtil;
import com.meis.saas.common.web.PinyinCodeBatchUpdater;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/system/departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final JdbcTemplate jdbc;
    private final RedisJsonCache cache;
    private final MeisCacheProperties cacheProps;
    private final MeisCacheEviction cacheEviction;
    private final ImportProfileService importProfileService;
    private final EntityChangeLogService changeLog;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        String schema = schema();
        return Result.ok(cache.getOrLoad(
                CacheKeys.deptList(schema),
                cacheProps.getOrgTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                () -> jdbc.queryForList(
                        "SELECT d.*, c.campus_name FROM department d LEFT JOIN campus c ON d.campus_id = c.id WHERE 1=1 "
                                + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d")
                                + " ORDER BY d.sort_order, d.dept_code")));
    }

    @GetMapping("/lookup")
    public Result<List<Map<String, Object>>> lookup(@RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "20") int limit) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "department", "d"));
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            where.append(" AND (d.dept_name ILIKE ? OR d.dept_code ILIKE ? OR COALESCE(d.pinyin_code, '') ILIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        int lim = Math.min(Math.max(limit, 1), 50);
        args.add(lim);
        return Result.ok(jdbc.queryForList(
                "SELECT d.id, d.dept_code, d.dept_name, d.pinyin_code FROM department d "
                        + where + " ORDER BY d.dept_name ASC LIMIT ?",
                args.toArray()));
    }

    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree() {
        String schema = schema();
        return Result.ok(cache.getOrLoad(
                CacheKeys.deptTree(schema),
                cacheProps.getOrgTtl(),
                new TypeReference<List<Map<String, Object>>>() {},
                this::loadDeptTree));
    }

    private List<Map<String, Object>> loadDeptTree() {
        List<Map<String, Object>> all = jdbc.queryForList(
                "SELECT id, dept_code, dept_name, parent_id, campus_id, is_clinical, sort_order, is_active FROM department WHERE 1=1 "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "department", null)
                        + " ORDER BY sort_order, dept_code");
        return buildTree(all, null);
    }

    @PostMapping
    @OperationLog(module = "system", description = "创建科室")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        SoftDeleteSupport.applyInsertAudit(jdbc, "department", body);
        var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "department", body);
        String pinyin = resolvePinyin(body);
        if (softDeletedId.isPresent()) {
            UUID existingId = UUID.fromString(softDeletedId.get());
            Map<String, Object> before = changeLog.loadRow("department", existingId);
            jdbc.update("""
                    UPDATE department SET dept_code=?, dept_name=?, pinyin_code=?, parent_id=?::uuid, campus_id=?::uuid,
                    floor_number=?, room_number=?, is_clinical=?, sort_order=?, is_active=?,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, body.get("dept_code"), body.get("dept_name"), pinyin, body.get("parent_id"),
                    body.get("campus_id"), body.get("floor_number"), body.get("room_number"),
                    body.getOrDefault("is_clinical", false), body.getOrDefault("sort_order", 0),
                    body.getOrDefault("is_active", true), TenantContext.getUserId(), existingId);
            cacheEviction.evictSchemaOrg(schema());
            Map<String, Object> after = changeLog.loadRow("department", existingId);
            changeLog.recordUpdate("department", existingId, before, after);
            return Result.ok(jdbc.queryForList("SELECT * FROM department WHERE id = ?::uuid", existingId).get(0));
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO department (id, dept_code, dept_name, pinyin_code, parent_id, campus_id, floor_number, room_number, is_clinical, sort_order, is_active, created_by, is_deleted) VALUES (?::uuid,?,?,?,?::uuid,?::uuid,?,?,?,?,?,?::uuid,?)",
                id, body.get("dept_code"), body.get("dept_name"), pinyin, body.get("parent_id"),
                body.get("campus_id"), body.get("floor_number"), body.get("room_number"),
                body.getOrDefault("is_clinical", false), body.getOrDefault("sort_order", 0),
                body.getOrDefault("is_active", true), SoftDeleteSupport.currentUserId(), 0);
        cacheEviction.evictSchemaOrg(schema());
        Map<String, Object> created = jdbc.queryForList("SELECT * FROM department WHERE id = ?::uuid", id).get(0);
        changeLog.recordCreate("department", id, created);
        return Result.ok(created);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "system", description = "更新科室")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> before = changeLog.loadRow("department", id);
        String pinyin = resolvePinyin(body);
        jdbc.update(
                "UPDATE department SET dept_code=?, dept_name=?, pinyin_code=?, parent_id=?::uuid, campus_id=?::uuid, floor_number=?, room_number=?, is_clinical=?, sort_order=?, is_active=?, updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid",
                body.get("dept_code"), body.get("dept_name"), pinyin, body.get("parent_id"),
                body.get("campus_id"), body.get("floor_number"), body.get("room_number"),
                body.getOrDefault("is_clinical", false), body.getOrDefault("sort_order", 0),
                body.getOrDefault("is_active", true), SoftDeleteSupport.currentUserId(), id);
        cacheEviction.evictSchemaOrg(schema());
        Map<String, Object> after = changeLog.loadRow("department", id);
        changeLog.recordUpdate("department", id, before, after);
        return Result.ok(jdbc.queryForList("SELECT * FROM department WHERE id = ?::uuid", id).get(0));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "system", description = "删除科室")
    public Result<Void> delete(@PathVariable UUID id) {
        Map<String, Object> before = changeLog.loadRow("department", id);
        SoftDeleteSupport.softDelete(jdbc, "department", id.toString());
        changeLog.recordDelete("department", id, before);
        cacheEviction.evictSchemaOrg(schema());
        return Result.ok();
    }

    @GetMapping("/import/template")
    public void importTemplate(@RequestParam(required = false) String profile, HttpServletResponse resp) throws IOException {
        var fields = importProfileService.resolveFields(ImportFieldRegistry.DEPARTMENT, profile);
        ExcelImportHelper.writeTemplate(resp, "department_import_template.xlsx", fields);
    }

    @GetMapping("/export")
    public void export(@RequestParam(required = false) String ids,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse resp) throws IOException {
        StringBuilder sql = new StringBuilder(
                "SELECT d.dept_code, d.dept_name, d.pinyin_code, pd.dept_code AS parent_dept_code, c.campus_name, d.is_clinical, d.sort_order, d.is_active "
                        + "FROM department d "
                        + "LEFT JOIN department pd ON d.parent_id = pd.id"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "department", "pd")
                        + " LEFT JOIN campus c ON d.campus_id = c.id"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "campus", "c")
                        + " WHERE 1=1 "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d"));
        List<Object> args = new ArrayList<>();
        if (ids != null && !ids.isBlank()) {
            String[] parts = ids.split(",");
            sql.append(" AND d.id IN (");
            boolean first = true;
            for (String p : parts) {
                String id = p.trim();
                if (id.isEmpty()) continue;
                if (!first) sql.append(',');
                sql.append("?::uuid");
                args.add(id);
                first = false;
            }
            sql.append(") ");
            if (first) {
                CsvExportHelper.writeRows(resp, "department_export.csv",
                        new String[]{"dept_code", "dept_name", "pinyin_code", "parent_dept_code", "campus_name", "is_clinical", "sort_order", "is_active"},
                        List.of());
                return;
            }
        } else if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (d.dept_code ILIKE ? OR d.dept_name ILIKE ? OR d.pinyin_code ILIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        sql.append(" ORDER BY d.sort_order, d.dept_code");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        CsvExportHelper.writeRows(resp, "department_export.csv",
                new String[]{"dept_code", "dept_name", "pinyin_code", "parent_dept_code", "campus_name", "is_clinical", "sort_order", "is_active"}, rows);
    }

    @PostMapping("/generate-pinyin")
    @OperationLog(module = "system", description = "生成科室拼音简码")
    public Result<Map<String, Object>> generatePinyin(@RequestBody Map<String, Object> body) {
        int count;
        if (Boolean.TRUE.equals(body.get("all"))) {
            count = PinyinCodeBatchUpdater.updateByKeyword(jdbc, "department", "dept_name", "dept_code",
                    body.get("keyword") == null ? null : body.get("keyword").toString());
        } else {
            @SuppressWarnings("unchecked")
            List<String> idStrs = (List<String>) body.get("ids");
            List<UUID> ids = idStrs == null ? List.of() : idStrs.stream().map(UUID::fromString).toList();
            count = PinyinCodeBatchUpdater.updateByIds(jdbc, "department", "dept_name", ids);
        }
        cacheEviction.evictSchemaOrg(schema());
        return Result.ok(Map.of("updated", count));
    }

    @PostMapping("/import")
    @OperationLog(module = "system", description = "导入科室")
    public Result<ImportResult> importFile(@RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String profile) throws IOException {
        var fields = importProfileService.resolveFields(ImportFieldRegistry.DEPARTMENT, profile);
        List<Map<String, String>> rows = ExcelImportHelper.parseRows(file, fields);
        ImportResult result = new ImportResult();
        Map<String, UUID> campusByName = loadCampusByName();
        Map<String, UUID> deptByCode = loadDeptByCode();
        int rowNum = 1;
        for (Map<String, String> raw : rows) {
            rowNum++;
            try {
                String code = ImportValueParser.require(raw.get("dept_code"), "科室编码");
                String name = ImportValueParser.require(raw.get("dept_name"), "科室名称");
                String parentCode = raw.get("parent_dept_code");
                UUID parentId = parentCode == null || parentCode.isBlank() ? null : deptByCode.get(parentCode.trim());
                if (parentCode != null && !parentCode.isBlank() && parentId == null) {
                    throw new IllegalArgumentException("上级科室编码不存在: " + parentCode);
                }
                String campusName = raw.get("campus_name");
                UUID campusId = campusName == null || campusName.isBlank() ? null : campusByName.get(campusName.trim());
                if (campusName != null && !campusName.isBlank() && campusId == null) {
                    throw new IllegalArgumentException("院区不存在: " + campusName);
                }
                Boolean clinical = raw.containsKey("is_clinical") && !raw.get("is_clinical").isBlank()
                        ? ImportValueParser.parseBoolean(raw.get("is_clinical")) : false;
                Integer sortOrder = raw.containsKey("sort_order") && !raw.get("sort_order").isBlank()
                        ? ImportValueParser.parseInteger(raw.get("sort_order")) : 0;
                Boolean active = raw.containsKey("is_active") && !raw.get("is_active").isBlank()
                        ? ImportValueParser.parseBoolean(raw.get("is_active")) : true;
                String pinyin = raw.containsKey("pinyin_code") && !raw.get("pinyin_code").isBlank()
                        ? raw.get("pinyin_code").trim() : PinyinCodeUtil.toShortCode(name);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("dept_code", code);
                body.put("dept_name", name);
                body.put("pinyin_code", pinyin);
                body.put("parent_id", parentId);
                body.put("campus_id", campusId);
                body.put("is_clinical", clinical);
                body.put("sort_order", sortOrder);
                body.put("is_active", active);
                SoftDeleteSupport.applyInsertAudit(jdbc, "department", body);
                var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "department", body);
                if (softDeletedId.isPresent()) {
                    UUID existingId = UUID.fromString(softDeletedId.get());
                    jdbc.update("""
                            UPDATE department SET dept_code=?, dept_name=?, pinyin_code=?, parent_id=?::uuid, campus_id=?::uuid,
                            is_clinical=?, sort_order=?, is_active=?,
                            is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                            WHERE id=?::uuid
                            """, code, name, pinyin, parentId, campusId, clinical, sortOrder, active,
                            SoftDeleteSupport.currentUserId(), existingId);
                    deptByCode.put(code, existingId);
                } else {
                    UUID id = UUID.randomUUID();
                    jdbc.update(
                            "INSERT INTO department (id, dept_code, dept_name, pinyin_code, parent_id, campus_id, is_clinical, sort_order, is_active, created_by, is_deleted) VALUES (?::uuid,?,?,?,?::uuid,?::uuid,?,?,?,?::uuid,?)",
                            id, code, name, pinyin, parentId, campusId, clinical, sortOrder, active,
                            SoftDeleteSupport.currentUserId(), 0);
                    deptByCode.put(code, id);
                }
                result.addSuccess();
            } catch (Exception e) {
                result.addError(rowNum, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }
        cacheEviction.evictSchemaOrg(schema());
        return Result.ok(result);
    }

    private Map<String, UUID> loadCampusByName() {
        Map<String, UUID> map = new HashMap<>();
        jdbc.queryForList("SELECT id, campus_name FROM campus WHERE 1=1 "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "campus", null))
                .forEach(r -> map.put(String.valueOf(r.get("campus_name")), (UUID) r.get("id")));
        return map;
    }

    private Map<String, UUID> loadDeptByCode() {
        Map<String, UUID> map = new HashMap<>();
        jdbc.queryForList("SELECT id, dept_code FROM department WHERE 1=1 "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "department", null))
                .forEach(r -> map.put(String.valueOf(r.get("dept_code")), (UUID) r.get("id")));
        return map;
    }

    private String schema() {
        String s = TenantContext.getSchemaName();
        return s == null || s.isBlank() ? "public" : s;
    }

    private String resolvePinyin(Map<String, Object> body) {
        Object raw = body.get("pinyin_code");
        if (raw != null && !raw.toString().isBlank()) return raw.toString().trim();
        Object name = body.get("dept_name");
        return name == null ? "" : PinyinCodeUtil.toShortCode(name.toString());
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> all, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> d : all) {
            Object pid = d.get("parent_id");
            String p = pid == null ? null : pid.toString();
            if (Objects.equals(parentId, p) || (parentId == null && pid == null)) {
                Map<String, Object> node = new LinkedHashMap<>(d);
                node.put("children", buildTree(all, d.get("id").toString()));
                result.add(node);
            }
        }
        return result;
    }
}
