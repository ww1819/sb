package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/asset/outbound")
@RequiredArgsConstructor
public class DeviceOutboundController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM device_outbound WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> o = rows.get(0);
        o.put("items", jdbc.queryForList("SELECT * FROM device_outbound_item WHERE outbound_id = ?::uuid", id));
        return Result.ok(o);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存出库单")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM device_outbound WHERE id = ?::uuid", id).isEmpty();
        if (!exists) {
            jdbc.update("INSERT INTO device_outbound (id, outbound_no, dept_id, receiver_id, outbound_date, purpose, doc_status) VALUES (?::uuid,?,?,?::uuid,?::uuid,?,?,'draft')",
                    id, body.getOrDefault("outbound_no", "OUT" + System.currentTimeMillis()),
                    body.get("dept_id"), body.get("receiver_id"), body.get("outbound_date"), body.get("purpose"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        jdbc.update("DELETE FROM device_outbound_item WHERE outbound_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            jdbc.update("INSERT INTO device_outbound_item (id, outbound_id, device_id, device_code, device_name, quantity) VALUES (?::uuid,?::uuid,?::uuid,?,?,?)",
                    UUID.randomUUID(), id, item.get("device_id"), item.get("device_code"), item.get("device_name"), item.getOrDefault("quantity", 1));
        }
        return get(id);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "asset", description = "提交出库审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> o = get(id).getData();
        approvalService.submit("device_outbound", id, o.get("outbound_no").toString(), "设备出库 " + o.get("outbound_no"),
                UUID.fromString(body.get("applicantId").toString()), 0);
        return get(id);
    }

    @PostMapping("/{id}/issue")
    @Transactional
    @OperationLog(module = "asset", description = "出库发放")
    public Result<Map<String, Object>> issue(@PathVariable UUID id) {
        var items = jdbc.queryForList("SELECT device_id FROM device_outbound_item WHERE outbound_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            if (item.get("device_id") != null) {
                jdbc.update("UPDATE medical_device SET device_status = 'in_use', updated_at = NOW() WHERE id = ?::uuid", item.get("device_id"));
            }
        }
        jdbc.update("UPDATE device_outbound SET doc_status = 'issued', updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }
}
