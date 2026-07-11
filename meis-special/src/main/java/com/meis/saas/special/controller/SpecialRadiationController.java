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
@RequestMapping("/api/special/radiation")
@RequiredArgsConstructor
public class SpecialRadiationController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String specialType,
            @RequestParam(required = false) Boolean expiringOnly) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (s.device_code ILIKE ? OR s.device_name ILIKE ? OR s.license_no ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (specialType != null && !specialType.isBlank()) {
            where.append(" AND s.special_type = ? ");
            args.add(specialType);
        }
        if (Boolean.TRUE.equals(expiringOnly)) {
            where.append(" AND s.license_expiry_date IS NOT NULL AND s.license_expiry_date <= CURRENT_DATE + INTERVAL '30 days' ");
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM special_device s" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT s.*, dept.dept_name
            FROM special_device s
            LEFT JOIN medical_device d ON d.id = s.device_id
            LEFT JOIN department dept ON dept.id = d.dept_id
            """ + where + " ORDER BY s.license_expiry_date NULLS LAST, s.device_code LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM special_device WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "special", description = "登记特种设备")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        fillDeviceSnapshot(body);
        boolean exists = !jdbc.queryForList("SELECT 1 FROM special_device WHERE id = ?::uuid", id).isEmpty();
        if (!exists) {
            jdbc.update("""
                INSERT INTO special_device (id, device_id, device_code, device_name, special_type, license_no,
                license_expiry_date, operator_cert_required, safety_measures, last_inspection_date,
                next_inspection_date, remark)
                VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?)
                """, id, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                    body.getOrDefault("special_type", "radiation"), body.get("license_no"),
                    body.get("license_expiry_date"), body.getOrDefault("operator_cert_required", true),
                    body.get("safety_measures"), body.get("last_inspection_date"),
                    body.get("next_inspection_date"), body.get("remark"));
        } else {
            jdbc.update("""
                UPDATE special_device SET special_type=?, license_no=?, license_expiry_date=?,
                operator_cert_required=?, safety_measures=?, last_inspection_date=?, next_inspection_date=?,
                remark=?, updated_at=NOW() WHERE id=?::uuid
                """, body.get("special_type"), body.get("license_no"), body.get("license_expiry_date"),
                    body.get("operator_cert_required"), body.get("safety_measures"),
                    body.get("last_inspection_date"), body.get("next_inspection_date"),
                    body.get("remark"), id);
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
