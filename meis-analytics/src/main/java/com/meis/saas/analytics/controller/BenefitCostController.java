package com.meis.saas.analytics.controller;

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
@RequestMapping("/api/analytics/cost")
@RequiredArgsConstructor
public class BenefitCostController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) String costType) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (deviceCode != null && !deviceCode.isBlank()) {
            where.append(" AND c.device_code ILIKE ? ");
            args.add("%" + deviceCode.trim() + "%");
        }
        if (costType != null && !costType.isBlank()) {
            where.append(" AND c.cost_type = ? ");
            args.add(costType);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (c.device_name ILIKE ? OR c.description ILIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM device_cost_record c" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT c.*, dept.dept_name FROM device_cost_record c
                LEFT JOIN medical_device d ON d.id = c.device_id
                LEFT JOIN department dept ON dept.id = d.dept_id
                """ + where + " ORDER BY c.cost_date DESC, c.id DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "analytics", description = "保存成本记录")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        fillDeviceInfo(body);
        Long id = body.containsKey("id") ? Long.parseLong(body.get("id").toString()) : null;
        if (id != null && !jdbc.queryForList("SELECT 1 FROM device_cost_record WHERE id = ?", id).isEmpty()) {
            jdbc.update("""
                    UPDATE device_cost_record SET device_id=?::uuid, device_code=?, device_name=?, cost_date=?,
                    cost_type=?, cost_amount=?, description=?, invoice_no=?, data_source=?
                    WHERE id=?
                    """, body.get("device_id"), body.get("device_code"), body.get("device_name"), body.get("cost_date"),
                    body.get("cost_type"), body.get("cost_amount"), body.get("description"),
                    body.get("invoice_no"), body.getOrDefault("data_source", "manual"), id);
        } else {
            jdbc.update("""
                    INSERT INTO device_cost_record (device_id, device_code, device_name, cost_date, cost_type,
                    cost_amount, description, invoice_no, data_source, source_record_id)
                    VALUES (?::uuid,?,?,?,?,?,?,?,?,?)
                    """, body.get("device_id"), body.get("device_code"), body.get("device_name"), body.get("cost_date"),
                    body.get("cost_type"), body.get("cost_amount"), body.get("description"),
                    body.get("invoice_no"), body.getOrDefault("data_source", "manual"), body.get("source_record_id"));
            id = jdbc.queryForObject("SELECT MAX(id) FROM device_cost_record", Long.class);
        }
        var rows = jdbc.queryForList("SELECT * FROM device_cost_record WHERE id = ?", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    private void fillDeviceInfo(Map<String, Object> body) {
        if (body.get("device_id") == null) return;
        var rows = jdbc.queryForList("SELECT device_code, device_name FROM medical_device WHERE id = ?::uuid",
                UUID.fromString(body.get("device_id").toString()));
        if (!rows.isEmpty()) {
            body.putIfAbsent("device_code", rows.get(0).get("device_code"));
            body.putIfAbsent("device_name", rows.get(0).get("device_name"));
        }
    }
}
