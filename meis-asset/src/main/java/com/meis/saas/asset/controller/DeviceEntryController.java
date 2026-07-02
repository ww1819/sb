package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.code.DeviceCodeGenerator;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalInstanceService;
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
            jdbc.update("INSERT INTO device_entry (id, entry_no, entry_type, supplier_id, entry_date, status) VALUES (?::uuid,?,?,?::uuid,?,?)",
                    id, body.getOrDefault("entry_no", "EN" + System.currentTimeMillis()),
                    body.getOrDefault("entry_type", "purchase"), body.get("supplier_id"), body.get("entry_date"), "draft");
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
    public Result<Map<String, Object>> complete(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var items = jdbc.queryForList("SELECT * FROM device_entry_item WHERE entry_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            String code = codeGenerator.generate(
                    str(body, "campusCode"), str(body, "buildingCode"), str(body, "deptCode"),
                    str(body, "countryCode"), str(body, "categoryCode"));
            jdbc.update("""
                INSERT INTO medical_device (device_code, device_name, brand, model, dept_id, device_status, purchase_price)
                VALUES (?,?,?,?,?::uuid,'normal',?)
                """, code, item.get("device_name"), item.get("brand"), item.get("model"), body.get("dept_id"), item.get("unit_price"));
        }
        jdbc.update("UPDATE device_entry SET status = 'completed', updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }

    private String str(Map<String, Object> m, String k) {
        return m.get(k) != null ? m.get(k).toString() : "0";
    }
}
