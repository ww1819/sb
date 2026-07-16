package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
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
@RequestMapping("/api/asset/inventory")
@RequiredArgsConstructor
public class InventoryCheckController {
    private static final String UUID_PATH =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String audit_status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (check_no ILIKE ? OR check_name ILIKE ?) ");
            String kw = "%" + query.getKeyword() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (audit_status != null && !audit_status.isBlank()) {
            where.append(" AND audit_status = ? ");
            args.add(audit_status);
        }
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", null));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_check" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList(
                "SELECT * FROM inventory_check" + where + " ORDER BY created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/devices/candidates")
    public Result<List<Map<String, Object>>> candidateDevices(
            @RequestParam(required = false) String deptId,
            @RequestParam(required = false) String campusId,
            @RequestParam(required = false) String checkId,
            @RequestParam(required = false) List<String> excludeIds) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, device_code, device_name, brand, model, specification,
                       dept_id, campus_id, location_detail, device_status
                FROM medical_device
                WHERE is_active = true
                """);
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null));
        List<Object> args = new ArrayList<>();
        if (deptId != null && !deptId.toString().isBlank()) {
            sql.append(" AND dept_id = ?::uuid ");
            args.add(deptId);
        }
        if (campusId != null && !campusId.toString().isBlank()) {
            sql.append(" AND campus_id = ?::uuid ");
            args.add(campusId);
        }
        if (checkId != null && !checkId.toString().isBlank()) {
            sql.append("""
                     AND id NOT IN (
                         SELECT device_id FROM inventory_check_item
                         WHERE check_id = ?::uuid AND device_id IS NOT NULL
                    """);
            sql.append(SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", null));
            sql.append(")");
            args.add(checkId);
        }
        List<String> excludes = excludeIds == null ? List.of()
                : excludeIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (!excludes.isEmpty()) {
            sql.append(" AND id NOT IN (");
            sql.append(String.join(",", Collections.nCopies(excludes.size(), "?::uuid")));
            sql.append(") ");
            args.addAll(excludes);
        }
        sql.append(" ORDER BY device_code LIMIT 500");
        return Result.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @GetMapping("/{id:" + UUID_PATH + "}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM inventory_check WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> t = rows.get(0);
        t.put("items", jdbc.queryForList(
                "SELECT * FROM inventory_check_item WHERE check_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", null)
                        + " ORDER BY device_code", id));
        return Result.ok(t);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存盘点任务")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null && !body.get("id").toString().isBlank()
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM inventory_check WHERE id = ?::uuid", id).isEmpty();
        String userId = TenantContext.getUserId();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());

        if (exists) {
            assertMutable(id);
            jdbc.update("""
                UPDATE inventory_check SET check_name=?, check_year=?, check_type=?, campus_id=?::uuid, dept_id=?::uuid,
                warehouse_id=?::uuid, start_date=?, end_date=?, checker_id=?::uuid, supervisor_id=?::uuid, remark=?, total_count=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("check_name"), body.get("check_year"), body.get("check_type"), body.get("campus_id"),
                    body.get("dept_id"), body.get("warehouse_id"), body.get("start_date"), body.get("end_date"), body.get("checker_id"),
                    body.get("supervisor_id"), body.get("remark"), items.size(), id);
        } else {
            jdbc.update("""
                INSERT INTO inventory_check (id, check_no, check_name, check_year, check_type, campus_id, dept_id,
                warehouse_id, start_date, end_date, checker_id, supervisor_id, status, audit_status, total_count, created_by)
                VALUES (?::uuid,?,?,?,?,?::uuid,?::uuid,?::uuid,?,?,?::uuid,?::uuid,?,?,?,?::uuid)
                """, id, body.getOrDefault("check_no", "IC" + System.currentTimeMillis()), body.get("check_name"),
                    body.get("check_year"), body.getOrDefault("check_type", "annual"), body.get("campus_id"),
                    body.get("dept_id"), body.get("warehouse_id"), body.get("start_date"), body.get("end_date"), body.get("checker_id"),
                    body.get("supervisor_id"), body.getOrDefault("status", "planning"),
                    body.getOrDefault("audit_status", "pending"), items.size(),
                    userId != null ? UUID.fromString(userId) : null);
        }

        jdbc.update("DELETE FROM inventory_check_item WHERE check_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            jdbc.update("""
                    INSERT INTO inventory_check_item (id, check_id, device_id, device_code, device_name,
                    expected_location, actual_location, is_found, is_matched, condition_status, remark)
                    VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), id, blankToNull(item.get("device_id")), item.get("device_code"),
                    item.get("device_name"), item.get("expected_location"), item.get("actual_location"),
                    item.getOrDefault("is_found", false), item.getOrDefault("is_matched", false),
                    item.get("condition_status"), item.get("remark"));
        }
        return get(id);
    }

    @DeleteMapping("/{id:" + UUID_PATH + "}")
    @Transactional
    @OperationLog(module = "asset", description = "删除盘点任务")
    public Result<Void> delete(@PathVariable UUID id) {
        assertMutable(id);
        jdbc.update("DELETE FROM inventory_check_item WHERE check_id = ?::uuid", id);
        int n = SoftDeleteSupport.softDelete(jdbc, "inventory_check", id.toString());
        if (n == 0) throw new BizException(404, "not found");
        return Result.ok();
    }

    @PostMapping("/{id:" + UUID_PATH + "}/approve")
    @Transactional
    @OperationLog(module = "asset", description = "审核盘点任务")
    public Result<Map<String, Object>> approve(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT audit_status FROM inventory_check WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        if ("approved".equals(String.valueOf(rows.get(0).get("audit_status")))) {
            throw new BizException(400, "盘点单已审核");
        }
        String userId = TenantContext.getUserId();
        UUID approver = userId != null ? UUID.fromString(userId) : null;
        jdbc.update("""
                UPDATE inventory_check
                SET audit_status = 'approved', approved_by = ?::uuid, approved_at = NOW(), updated_at = NOW()
                WHERE id = ?::uuid
                """, approver, id);
        return get(id);
    }

    @PostMapping("/{id:" + UUID_PATH + "}/start")
    @OperationLog(module = "asset", description = "开始盘点")
    public Result<Map<String, Object>> start(@PathVariable UUID id) {
        jdbc.update("UPDATE inventory_check SET status = 'in_progress', actual_start_at = NOW(), updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }

    @PostMapping("/{id:" + UUID_PATH + "}/scan")
    @OperationLog(module = "asset", description = "扫码盘点")
    public Result<Map<String, Object>> scan(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Object deviceId = body.get("device_id");
        var existing = jdbc.queryForList(
                "SELECT id FROM inventory_check_item WHERE check_id = ?::uuid AND device_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", null), id, deviceId);
        if (!existing.isEmpty()) {
            jdbc.update("""
                UPDATE inventory_check_item SET is_found = true, is_matched = ?,
                actual_location = COALESCE(?, actual_location), check_date = NOW()
                WHERE check_id = ?::uuid AND device_id = ?::uuid
                """, body.getOrDefault("is_matched", true), body.get("actual_location"), id, deviceId);
        } else {
            jdbc.update("""
                INSERT INTO inventory_check_item (id, check_id, device_id, device_code, device_name,
                is_found, is_matched, actual_location, check_date)
                VALUES (?::uuid,?::uuid,?::uuid,?,?,true,?,?,NOW())
                """, UUID.randomUUID(), id, deviceId, body.get("device_code"), body.get("device_name"),
                    body.getOrDefault("is_matched", true), body.get("actual_location"));
        }
        jdbc.update("""
            UPDATE inventory_check SET checked_count = (
                SELECT COUNT(*) FROM inventory_check_item WHERE check_id = ?::uuid AND is_found = true
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", null) + """
            ), updated_at = NOW() WHERE id = ?::uuid
            """, id, id);
        return get(id);
    }

    @PostMapping("/{id:" + UUID_PATH + "}/complete")
    @OperationLog(module = "asset", description = "完成盘点")
    public Result<Map<String, Object>> complete(@PathVariable UUID id) {
        jdbc.update("""
                UPDATE inventory_check
                SET status = 'completed', actual_end_at = NOW(), updated_at = NOW()
                WHERE id = ?::uuid
                """, id);
        return get(id);
    }

    private void assertMutable(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT audit_status FROM inventory_check WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", null), id);
        if (!rows.isEmpty() && "approved".equals(String.valueOf(rows.get(0).get("audit_status")))) {
            throw new BizException(400, "已审核的盘点单不可修改或删除");
        }
    }

    private static Object blankToNull(Object value) {
        if (value == null) return null;
        if (value instanceof String s && s.isBlank()) return null;
        return value;
    }
}
