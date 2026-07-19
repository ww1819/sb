package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/asset/return")
@RequiredArgsConstructor
public class DeviceReturnController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "device_return", "r"));
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (r.return_no ILIKE ? OR o.outbound_no ILIKE ?) ");
            String kw = "%" + query.getKeyword() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status = ? ");
            args.add(status);
        }
        String from = " FROM device_return r "
                + " LEFT JOIN device_outbound o ON o.id = r.outbound_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound", "o")
                + " LEFT JOIN warehouse w ON w.id = r.warehouse_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w");
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT r.*, o.outbound_no, w.warehouse_name
            """ + from + where + " ORDER BY r.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
            SELECT r.*, o.outbound_no, w.warehouse_name
            FROM device_return r
            LEFT JOIN device_outbound o ON o.id = r.outbound_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound", "o") + """
            LEFT JOIN warehouse w ON w.id = r.warehouse_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w") + """
            WHERE r.id = ?::uuid
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_return", "r"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> r = rows.get(0);
        r.put("items", jdbc.queryForList(
                "SELECT * FROM device_return_item WHERE return_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_return_item", null), id));
        return Result.ok(r);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存退库单")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM device_return WHERE id = ?::uuid", id).isEmpty();
        Object returnerId = body.get("returner_id") != null ? body.get("returner_id") : body.get("recipient_id");
        if (!exists) {
            jdbc.update("""
                INSERT INTO device_return (id, return_no, outbound_id, warehouse_id, dept_id, returner_id,
                return_date, return_type, reason, doc_status, status, approval_status, operator_id, remark)
                VALUES (?::uuid,?,?::uuid,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?::uuid,?)
                """,
                    id, body.getOrDefault("return_no", "RT" + System.currentTimeMillis()),
                    body.get("outbound_id"), body.get("warehouse_id"), body.get("dept_id"), returnerId,
                    body.get("return_date"), body.getOrDefault("return_type", "unused"), body.get("reason"),
                    "draft", "draft", "draft", body.get("operator_id"), body.get("remark"));
        } else {
            jdbc.update("""
                UPDATE device_return SET outbound_id=?::uuid, warehouse_id=?::uuid, dept_id=?::uuid,
                returner_id=?::uuid, return_date=?, return_type=?, reason=?, operator_id=?::uuid, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """,
                    body.get("outbound_id"), body.get("warehouse_id"), body.get("dept_id"), returnerId,
                    body.get("return_date"), body.get("return_type"), body.get("reason"),
                    body.get("operator_id"), body.get("remark"), id);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        jdbc.update("DELETE FROM device_return_item WHERE return_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            jdbc.update("""
                INSERT INTO device_return_item (id, return_id, device_id, device_code, device_name, quantity, condition_note)
                VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?)
                """,
                    UUID.randomUUID(), id, item.get("device_id"), item.get("device_code"),
                    item.get("device_name"), item.getOrDefault("quantity", 1), item.get("condition_note"));
        }
        return get(id);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "asset", description = "提交退库审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> r = get(id).getData();
        approvalService.submit("device_return", id, r.get("return_no").toString(), "设备退库 " + r.get("return_no"),
                UUID.fromString(body.get("applicantId").toString()), 0);
        return get(id);
    }

    @PostMapping("/{id}/complete")
    @Transactional
    @OperationLog(module = "asset", description = "确认退库")
    public Result<Map<String, Object>> complete(@PathVariable UUID id) {
        var row = jdbc.queryForList(
                "SELECT * FROM device_return WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_return", null), id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        if ("returned".equals(String.valueOf(row.get(0).get("status")))) {
            throw new BizException(400, "退库单已完成");
        }
        Object warehouseId = row.get(0).get("warehouse_id");
        var items = jdbc.queryForList(
                "SELECT device_id FROM device_return_item WHERE return_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_return_item", null), id);
        for (Map<String, Object> item : items) {
            if (item.get("device_id") != null) {
                jdbc.update("""
                    UPDATE medical_device SET device_status = 'normal', warehouse_id = ?::uuid, updated_at = NOW()
                    WHERE id = ?::uuid
                    """, warehouseId, item.get("device_id"));
            }
        }
        jdbc.update("UPDATE device_return SET status = 'returned', doc_status = 'returned', updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }
}
