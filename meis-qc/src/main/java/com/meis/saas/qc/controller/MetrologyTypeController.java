package com.meis.saas.qc.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/metrology/type")
@RequiredArgsConstructor
public class MetrologyTypeController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String classification_group,
            @RequestParam(required = false) String classificationGroup) {
        String group = classification_group != null && !classification_group.isBlank()
                ? classification_group
                : classificationGroup;
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "metrology_type", null));
        List<Object> args = new ArrayList<>();
        if (group != null && !group.isBlank()) {
            where.append(" AND classification_group = ? ");
            args.add(group.trim());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (type_code ILIKE ? OR type_name ILIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM metrology_type" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList(
                "SELECT * FROM metrology_type" + where
                        + " ORDER BY sort_order, type_code LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String classificationGroup) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.*, p.type_name AS parent_type_name
                FROM metrology_type t
                LEFT JOIN metrology_type p ON p.id = t.parent_id
                WHERE 1=1
                """);
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, "metrology_type", "t"));
        List<Object> args = new ArrayList<>();
        if (classificationGroup != null && !classificationGroup.isBlank()) {
            sql.append(" AND t.classification_group = ? ");
            args.add(classificationGroup.trim());
        }
        sql.append(" ORDER BY t.classification_group, t.sort_order, t.type_code");
        return Result.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM metrology_type WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_type", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "metrology", description = "保存计量检定类型")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        normalizeUuidFields(body);
        boolean hasId = body.containsKey("id") && body.get("id") != null;
        UUID id = hasId ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = hasId && !jdbc.queryForList(
                "SELECT 1 FROM metrology_type WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_type", null), id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE metrology_type SET type_code=?, type_name=?, parent_id=?::uuid, classification_group=?,
                regulatory_attr=?, traceability_mode=?, timing_kind=?, location_kind=?, management_grade=?,
                cycle_rule=?, legal_basis=?, executor_scope=?, certificate_kind=?, sort_order=?, description=?, is_active=?,
                updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid
                """, body.get("type_code"), body.get("type_name"), body.get("parent_id"),
                    body.get("classification_group"), body.get("regulatory_attr"), body.get("traceability_mode"),
                    body.get("timing_kind"), body.get("location_kind"), body.get("management_grade"),
                    body.get("cycle_rule"), body.get("legal_basis"), body.get("executor_scope"),
                    body.get("certificate_kind"), body.getOrDefault("sort_order", 0), body.get("description"),
                    body.getOrDefault("is_active", true), SoftDeleteSupport.currentUserId(), id);
            return get(id);
        }
        SoftDeleteSupport.applyInsertAudit(jdbc, "metrology_type", body);
        var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "metrology_type", body);
        if (softDeletedId.isPresent()) {
            id = UUID.fromString(softDeletedId.get());
            jdbc.update("""
                UPDATE metrology_type SET type_code=?, type_name=?, parent_id=?::uuid, classification_group=?,
                regulatory_attr=?, traceability_mode=?, timing_kind=?, location_kind=?, management_grade=?,
                cycle_rule=?, legal_basis=?, executor_scope=?, certificate_kind=?, sort_order=?, description=?, is_active=?,
                is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                WHERE id=?::uuid
                """, body.get("type_code"), body.get("type_name"), body.get("parent_id"),
                    body.get("classification_group"), body.get("regulatory_attr"), body.get("traceability_mode"),
                    body.get("timing_kind"), body.get("location_kind"), body.get("management_grade"),
                    body.get("cycle_rule"), body.get("legal_basis"), body.get("executor_scope"),
                    body.get("certificate_kind"), body.getOrDefault("sort_order", 0), body.get("description"),
                    body.getOrDefault("is_active", true), SoftDeleteSupport.currentUserId(), id);
            return get(id);
        }
        jdbc.update("""
            INSERT INTO metrology_type (id, type_code, type_name, parent_id, classification_group,
            regulatory_attr, traceability_mode, timing_kind, location_kind, management_grade,
            cycle_rule, legal_basis, executor_scope, certificate_kind, sort_order, description, is_active, created_by, is_deleted)
            VALUES (?::uuid,?,?,?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?,?::uuid,?)
            """, id, body.get("type_code"), body.get("type_name"), body.get("parent_id"),
                body.get("classification_group"), body.get("regulatory_attr"), body.get("traceability_mode"),
                body.get("timing_kind"), body.get("location_kind"), body.get("management_grade"),
                body.get("cycle_rule"), body.get("legal_basis"), body.get("executor_scope"),
                body.get("certificate_kind"), body.getOrDefault("sort_order", 0), body.get("description"),
                body.getOrDefault("is_active", true), SoftDeleteSupport.currentUserId(), 0);
        return get(id);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "metrology", description = "删除计量检定类型")
    public Result<Void> delete(@PathVariable UUID id) {
        SoftDeleteSupport.softDelete(jdbc, "metrology_type", id.toString());
        return Result.ok();
    }

    private static void normalizeUuidFields(Map<String, Object> body) {
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = e.getKey();
            if (!"id".equals(key) && !key.endsWith("_id") && !key.endsWith("_by")) continue;
            Object v = e.getValue();
            if (v instanceof String s && s.isBlank()) e.setValue(null);
        }
    }
}
