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
@RequestMapping("/api/asset/goods-return")
@RequiredArgsConstructor
public class DeviceGoodsReturnController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String doc_status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return", "r"));
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (r.return_no ILIKE ? OR s.supplier_name ILIKE ? OR e.entry_no ILIKE ?) ");
            String kw = "%" + query.getKeyword() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (doc_status != null && !doc_status.isBlank()) {
            where.append(" AND r.doc_status = ? ");
            args.add(doc_status);
        }
        String from = " FROM device_goods_return r "
                + " LEFT JOIN supplier s ON s.id = r.supplier_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s")
                + " LEFT JOIN warehouse w ON w.id = r.warehouse_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w")
                + " LEFT JOIN device_entry e ON e.id = r.entry_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", "e");
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT r.*, s.supplier_name, w.warehouse_name, e.entry_no
            """ + from + where + " ORDER BY r.created_at DESC NULLS LAST LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
            SELECT r.*, s.supplier_name, w.warehouse_name, e.entry_no
            FROM device_goods_return r
            LEFT JOIN supplier s ON s.id = r.supplier_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s") + """
            LEFT JOIN warehouse w ON w.id = r.warehouse_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w") + """
            LEFT JOIN device_entry e ON e.id = r.entry_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", "e") + """
            WHERE r.id = ?
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return", "r"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> r = rows.get(0);
        r.put("items", jdbc.queryForList(
                "SELECT * FROM device_goods_return_item WHERE return_id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return_item", null)
                        + " ORDER BY created_at ASC NULLS LAST", id));
        return Result.ok(r);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存设备退货单")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null && !body.get("id").toString().isBlank()
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM device_goods_return WHERE id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return", null), id).isEmpty();
        UUID warehouseId = parseUuid(body.get("warehouse_id"));
        UUID supplierId = parseUuid(body.get("supplier_id"));
        UUID entryId = parseUuid(body.get("entry_id"));
        var ctx = com.meis.saas.common.rbac.PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
        if (!exists) {
            jdbc.update("""
                INSERT INTO device_goods_return (
                    id, return_no, warehouse_id, supplier_id, entry_id, return_date, reason,
                    doc_status, status, approval_status, remark,
                    created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'draft', 'draft', 'draft', ?, NOW(), NOW(), ?, ?, ?, ?, 0)
                """,
                    id, body.getOrDefault("return_no", "GR" + System.currentTimeMillis()),
                    warehouseId, supplierId, entryId,
                    body.get("return_date"), blankToNull(body.get("reason")),
                    blankToNull(body.get("remark")),
                    actorId, actorName, actorId, actorName);
        } else {
            jdbc.update("""
                UPDATE device_goods_return
                SET warehouse_id = ?, supplier_id = ?, entry_id = ?, return_date = ?, reason = ?, remark = ?,
                    updated_at = NOW(), updated_by = ?, updated_by_name = ?
                WHERE id = ?
                """,
                    warehouseId, supplierId, entryId,
                    body.get("return_date"), blankToNull(body.get("reason")), blankToNull(body.get("remark")),
                    actorId, actorName, id);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        jdbc.update("""
                UPDATE device_goods_return_item
                SET is_deleted = 1, deleted_at = NOW(), deleted_by = ?, deleted_by_name = ?,
                    updated_at = NOW(), updated_by = ?, updated_by_name = ?
                WHERE return_id = ? AND COALESCE(is_deleted, 0) = 0
                """, actorId, actorName, actorId, actorName, id);
        for (Map<String, Object> item : items) {
            jdbc.update("""
                INSERT INTO device_goods_return_item (
                    id, return_id, device_id, device_code, device_name, quantity,
                    created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?, ?, ?, 0)
                """,
                    UUID.randomUUID(), id, parseUuid(item.get("device_id")),
                    blankToNull(item.get("device_code")), blankToNull(item.get("device_name")),
                    item.getOrDefault("quantity", 1),
                    actorId, actorName, actorId, actorName);
        }
        return get(id);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "asset", description = "提交设备退货审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> r = get(id).getData();
        approvalService.submit("device_goods_return", id, r.get("return_no").toString(),
                "设备退货 " + r.get("return_no"),
                UUID.fromString(body.get("applicantId").toString()), 0);
        return get(id);
    }

    @PostMapping("/{id}/complete")
    @Transactional
    @OperationLog(module = "asset", description = "确认设备退货")
    public Result<Map<String, Object>> complete(@PathVariable UUID id) {
        var row = jdbc.queryForList(
                "SELECT * FROM device_goods_return WHERE id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return", null), id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        if ("returned".equals(String.valueOf(row.get(0).get("status")))) {
            throw new BizException(400, "退货单已完成");
        }
        var items = jdbc.queryForList(
                "SELECT device_id FROM device_goods_return_item WHERE return_id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return_item", null), id);
        for (Map<String, Object> item : items) {
            if (item.get("device_id") != null) {
                jdbc.update("""
                    UPDATE medical_device
                    SET device_status = 'returned', warehouse_id = NULL, updated_at = NOW()
                    WHERE id = ?
                    """, item.get("device_id"));
            }
        }
        jdbc.update("""
                UPDATE device_goods_return
                SET status = 'returned', doc_status = 'returned', updated_at = NOW()
                WHERE id = ?
                """, id);
        return get(id);
    }

    private static UUID parseUuid(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        return UUID.fromString(v.toString());
    }

    private static String blankToNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
