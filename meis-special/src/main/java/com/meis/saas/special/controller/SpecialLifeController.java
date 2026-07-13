package com.meis.saas.special.controller;

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
@RequestMapping("/api/special/life")
@RequiredArgsConstructor
public class SpecialLifeController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) Boolean expiringOnly) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "life_support_device", "l"));
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (l.device_code ILIKE ? OR l.device_name ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (Boolean.TRUE.equals(expiringOnly)) {
            where.append(" AND l.next_test_date IS NOT NULL AND l.next_test_date <= CURRENT_DATE + INTERVAL '30 days' ");
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM life_support_device l" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT l.*, dept.dept_name, u.real_name AS responsible_person_name
            FROM life_support_device l
            LEFT JOIN medical_device d ON d.id = l.device_id
            LEFT JOIN department dept ON dept.id = d.dept_id
            LEFT JOIN sys_user u ON u.id = l.responsible_person_id
            """ + where + " ORDER BY l.next_test_date NULLS LAST, l.device_code LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM life_support_device WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "life_support_device", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "special", description = "登记生命支持设备")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        boolean hasId = body.containsKey("id") && body.get("id") != null;
        UUID id = hasId ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        fillDeviceSnapshot(body);
        boolean exists = hasId && !jdbc.queryForList(
                "SELECT 1 FROM life_support_device WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "life_support_device", null), id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE life_support_device SET criticality_level=?, backup_required=?, standby_status=?,
                last_test_date=?, next_test_date=?, emergency_protocol=?, responsible_person_id=?::uuid,
                remark=?, updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid
                """, body.get("criticality_level"), body.get("backup_required"), body.get("standby_status"),
                    body.get("last_test_date"), body.get("next_test_date"), body.get("emergency_protocol"),
                    body.get("responsible_person_id"), body.get("remark"), SoftDeleteSupport.currentUserId(), id);
            return get(id);
        }
        SoftDeleteSupport.applyInsertAudit(jdbc, "life_support_device", body);
        var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "life_support_device", body);
        if (softDeletedId.isPresent()) {
            id = UUID.fromString(softDeletedId.get());
            jdbc.update("""
                UPDATE life_support_device SET device_id=?::uuid, device_code=?, device_name=?, criticality_level=?,
                backup_required=?, standby_status=?, last_test_date=?, next_test_date=?, emergency_protocol=?,
                responsible_person_id=?::uuid, remark=?,
                is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                WHERE id=?::uuid
                """, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                    body.getOrDefault("criticality_level", "high"), body.getOrDefault("backup_required", true),
                    body.getOrDefault("standby_status", "ready"), body.get("last_test_date"),
                    body.get("next_test_date"), body.get("emergency_protocol"),
                    body.get("responsible_person_id"), body.get("remark"),
                    SoftDeleteSupport.currentUserId(), id);
        } else {
            jdbc.update("""
                INSERT INTO life_support_device (id, device_id, device_code, device_name, criticality_level,
                backup_required, standby_status, last_test_date, next_test_date, emergency_protocol,
                responsible_person_id, remark, created_by, is_deleted)
                VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?,?::uuid,?,?::uuid,?)
                """, id, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                    body.getOrDefault("criticality_level", "high"), body.getOrDefault("backup_required", true),
                    body.getOrDefault("standby_status", "ready"), body.get("last_test_date"),
                    body.get("next_test_date"), body.get("emergency_protocol"),
                    body.get("responsible_person_id"), body.get("remark"),
                    SoftDeleteSupport.currentUserId(), 0);
        }
        if (body.get("device_id") != null) {
            jdbc.update("UPDATE medical_device SET is_life_support = true, updated_at = NOW(), updated_by = ?::uuid WHERE id = ?::uuid",
                    SoftDeleteSupport.currentUserId(), body.get("device_id"));
        }
        return get(id);
    }

    private void fillDeviceSnapshot(Map<String, Object> body) {
        if (body.get("device_id") == null) return;
        var devices = jdbc.queryForList(
                "SELECT device_code, device_name FROM medical_device WHERE id = ?::uuid", body.get("device_id"));
        if (!devices.isEmpty()) {
            if (body.get("device_code") == null) body.put("device_code", devices.get(0).get("device_code"));
            if (body.get("device_name") == null) body.put("device_name", devices.get(0).get("device_name"));
        }
    }
}
