package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.code.DeviceCodeGenerator;
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
@RequestMapping("/api/asset/entry")
@RequiredArgsConstructor
public class DeviceEntryController {
    private final JdbcTemplate jdbc;
    private final DeviceCodeGenerator codeGenerator;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (e.entry_no ILIKE ? OR e.trace_no ILIKE ? OR pc.contract_code ILIKE ?) ");
            String kw = "%" + query.getKeyword() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND e.status = ? ");
            args.add(status);
        }
        String from = """
            FROM device_entry e
            LEFT JOIN purchase_contract pc ON pc.id = e.contract_id
            LEFT JOIN supplier s ON s.id = e.supplier_id
            """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT e.*, pc.contract_code, s.supplier_name
            """ + from + where + " ORDER BY e.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM device_entry WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> e = rows.get(0);
        e.put("items", jdbc.queryForList("SELECT * FROM device_entry_item WHERE entry_id = ?::uuid", id));
        return Result.ok(e);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存入库单")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM device_entry WHERE id = ?::uuid", id).isEmpty();
        if (!exists) {
            jdbc.update("""
                INSERT INTO device_entry (id, entry_no, contract_id, supplier_id, entry_date, entry_type, status, trace_no, warehouse_id)
                VALUES (?::uuid,?,?::uuid,?::uuid,?,?,?,?,?::uuid)
                """,
                    id, body.getOrDefault("entry_no", "EN" + System.currentTimeMillis()),
                    body.get("contract_id"), body.get("supplier_id"), body.get("entry_date"),
                    body.getOrDefault("entry_type", "purchase"), "draft", body.get("trace_no"), body.get("warehouse_id"));
        } else {
            jdbc.update("""
                UPDATE device_entry SET warehouse_id=?::uuid, entry_date=?, entry_type=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("warehouse_id"), body.get("entry_date"), body.get("entry_type"), body.get("remark"), id);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        jdbc.update("DELETE FROM device_entry_item WHERE entry_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            jdbc.update("INSERT INTO device_entry_item (id, entry_id, device_name, brand, model, quantity, unit_price) VALUES (?::uuid,?::uuid,?,?,?,?,?)",
                    UUID.randomUUID(), id, item.get("device_name"), item.get("brand"), item.get("model"), item.get("quantity"), item.get("unit_price"));
        }
        return get(id);
    }

    @PostMapping("/{id}/complete")
    @Transactional
    @OperationLog(module = "asset", description = "完成入库生成台账")
    public Result<Map<String, Object>> complete(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        body = body != null ? body : Map.of();
        var entryRows = jdbc.queryForList("SELECT * FROM device_entry WHERE id = ?::uuid", id);
        if (entryRows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> entry = entryRows.get(0);
        if ("completed".equals(entry.get("status"))) throw new BizException(400, "入库单已完成");

        Object deptId = body.get("dept_id");
        if (deptId == null && entry.get("plan_id") != null) {
            var plan = jdbc.queryForList("SELECT dept_id FROM purchase_plan WHERE id = ?::uuid", entry.get("plan_id"));
            if (!plan.isEmpty()) deptId = plan.get(0).get("dept_id");
        }
        Object supplierId = entry.get("supplier_id");
        if (supplierId == null && entry.get("contract_id") != null) {
            var c = jdbc.queryForList("SELECT supplier_id FROM purchase_contract WHERE id = ?::uuid", entry.get("contract_id"));
            if (!c.isEmpty()) supplierId = c.get(0).get("supplier_id");
        }

        Object warehouseId = body.get("warehouse_id") != null ? body.get("warehouse_id") : entry.get("warehouse_id");

        var items = jdbc.queryForList("SELECT * FROM device_entry_item WHERE entry_id = ?::uuid", id);
        List<UUID> deviceIds = new ArrayList<>();
        for (Map<String, Object> item : items) {
            int qty = item.get("quantity") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            for (int i = 0; i < qty; i++) {
                String code = codeGenerator.generate(
                        str(body, "campusCode"), str(body, "buildingCode"), str(body, "deptCode"),
                        str(body, "countryCode"), str(body, "categoryCode"));
                UUID deviceId = UUID.randomUUID();
                jdbc.update("""
                    INSERT INTO medical_device (id, device_code, device_name, brand, model, dept_id, supplier_id,
                    device_status, purchase_price, contract_id, warehouse_id)
                    VALUES (?::uuid,?,?,?,?::uuid,?::uuid,'normal',?,?::uuid,?::uuid)
                    """,
                        deviceId, code, item.get("device_name"), item.get("brand"), item.get("model"),
                        deptId, supplierId, item.get("unit_price"), entry.get("contract_id"), warehouseId);
                deviceIds.add(deviceId);
            }
        }
        jdbc.update("UPDATE device_entry SET status = 'completed', updated_at = NOW() WHERE id = ?::uuid", id);
        Map<String, Object> result = get(id).getData();
        result.put("device_ids", deviceIds);
        result.put("device_count", deviceIds.size());
        return Result.ok(result);
    }

    private String str(Map<String, Object> m, String k) {
        return m.get(k) != null ? m.get(k).toString() : "0";
    }
}
