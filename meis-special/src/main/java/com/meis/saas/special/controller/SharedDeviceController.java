package com.meis.saas.special.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/shared/device")
@RequiredArgsConstructor
public class SharedDeviceController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String availabilityStatus,
            @RequestParam(required = false) Boolean activeOnly) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (s.device_code ILIKE ? OR s.device_name ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (availabilityStatus != null && !availabilityStatus.isBlank()) {
            where.append(" AND s.availability_status = ? ");
            args.add(availabilityStatus);
        }
        if (Boolean.TRUE.equals(activeOnly)) {
            where.append(" AND s.is_active = true ");
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM shared_device s" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT s.*, dept.dept_name AS owner_dept_name
            FROM shared_device s
            LEFT JOIN department dept ON dept.id = s.owner_dept_id
            """ + where + " ORDER BY s.device_code LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM shared_device WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "shared", description = "登记公用设备")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        fillDeviceSnapshot(body);
        boolean exists = !jdbc.queryForList("SELECT 1 FROM shared_device WHERE id = ?::uuid", id).isEmpty();
        if (!exists) {
            jdbc.update("""
                INSERT INTO shared_device (id, device_id, device_code, device_name, owner_dept_id, location,
                fee_standard, availability_status, is_active, remark)
                VALUES (?::uuid,?::uuid,?,?,?::uuid,?,?,?,?,?)
                """, id, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                    body.get("owner_dept_id"), body.get("location"),
                    body.getOrDefault("fee_standard", 0), body.getOrDefault("availability_status", "available"),
                    body.getOrDefault("is_active", true), body.get("remark"));
            if (body.get("device_id") != null) {
                jdbc.update("UPDATE medical_device SET is_shared_device = true, updated_at = NOW() WHERE id = ?::uuid",
                        body.get("device_id"));
            }
        } else {
            jdbc.update("""
                UPDATE shared_device SET owner_dept_id=?::uuid, location=?, fee_standard=?, availability_status=?,
                is_active=?, remark=?, updated_at=NOW() WHERE id=?::uuid
                """, body.get("owner_dept_id"), body.get("location"), body.get("fee_standard"),
                    body.get("availability_status"), body.get("is_active"), body.get("remark"), id);
        }
        return get(id);
    }

    private void fillDeviceSnapshot(Map<String, Object> body) {
        if (body.get("device_id") == null) return;
        var devices = jdbc.queryForList(
                "SELECT device_code, device_name, dept_id FROM medical_device WHERE id = ?::uuid", body.get("device_id"));
        if (!devices.isEmpty()) {
            if (body.get("device_code") == null) body.put("device_code", devices.get(0).get("device_code"));
            if (body.get("device_name") == null) body.put("device_name", devices.get(0).get("device_name"));
            if (body.get("owner_dept_id") == null) body.put("owner_dept_id", devices.get(0).get("dept_id"));
        }
    }
}
