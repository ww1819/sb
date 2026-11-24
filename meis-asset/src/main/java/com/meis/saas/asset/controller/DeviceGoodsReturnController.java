package com.meis.saas.asset.controller;

import com.meis.saas.common.asset.DeviceSnapshotSupport;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.DetailLineUpsertSupport;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/asset/goods-return")
@RequiredArgsConstructor
public class DeviceGoodsReturnController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    /** 预览下一退货单号：TH-yyyyMMdd + 4 位当日流水 */
    @GetMapping("/next-no")
    public Result<Map<String, Object>> nextNo() {
        return Result.ok(Map.of("return_no", nextReturnNo()));
    }

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String approval_status,
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
        if (approval_status != null && !approval_status.isBlank()) {
            if (approval_status.contains(",")) {
                FilterCsvSupport.appendStrIn(where, args, "COALESCE(r.approval_status, 'draft')", approval_status);
            } else {
                where.append(" AND COALESCE(r.approval_status, 'draft') = ? ");
                args.add(approval_status);
            }
        } else if (doc_status != null && !doc_status.isBlank()) {
            FilterCsvSupport.appendStrIn(where, args, "r.doc_status", doc_status);
        }
        String from = " FROM device_goods_return r "
                + " LEFT JOIN supplier s ON s.id = r.supplier_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s")
                + " LEFT JOIN warehouse w ON w.id = r.warehouse_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w")
                + " LEFT JOIN device_entry e ON e.id = r.entry_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", "e")
                + " LEFT JOIN ("
                + "   SELECT i.return_id, COALESCE(SUM(i.total_price), 0) AS total_amount"
                + "   FROM device_goods_return_item i WHERE 1=1"
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return_item", "i")
                + "   GROUP BY i.return_id"
                + " ) amt ON amt.return_id = r.id";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT r.*, s.supplier_name, w.warehouse_name, e.entry_no,
                   COALESCE(amt.total_amount, 0) AS total_amount
            """ + from + where + " ORDER BY r.created_at DESC NULLS LAST LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
            SELECT r.*, s.supplier_name, w.warehouse_name, e.entry_no,
                   COALESCE(amt.total_amount, 0) AS total_amount
            FROM device_goods_return r
            LEFT JOIN supplier s ON s.id = r.supplier_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s") + """
            LEFT JOIN warehouse w ON w.id = r.warehouse_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w") + """
            LEFT JOIN device_entry e ON e.id = r.entry_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", "e") + """
            LEFT JOIN (
              SELECT i.return_id, COALESCE(SUM(i.total_price), 0) AS total_amount
              FROM device_goods_return_item i WHERE 1=1
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return_item", "i") + """
              GROUP BY i.return_id
            ) amt ON amt.return_id = r.id
            WHERE r.id = ?
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return", "r"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> r = rows.get(0);
        r.put("items", jdbc.queryForList("""
                SELECT i.*,
                       dept.dept_name,
                """ + com.meis.saas.common.asset.DeviceLedgerSelectSupport.SELECT_FIELDS + """
                FROM device_goods_return_item i
                """ + com.meis.saas.common.asset.DeviceLedgerSelectSupport.joins("i.device_id") + """
                WHERE i.return_id = ?
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return_item", "i")
                + " ORDER BY i.created_at ASC NULLS LAST", id));
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
        if (exists) {
            var cur = jdbc.queryForList(
                    "SELECT approval_status, status FROM device_goods_return WHERE id = ?", id);
            if (!cur.isEmpty()) {
                if ("approved".equals(String.valueOf(cur.get(0).get("approval_status")))
                        || "returned".equals(String.valueOf(cur.get(0).get("status")))) {
                    throw new BizException(400, "已审核的退货单不可编辑");
                }
            }
        }
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
        String returnNo;
        if (!exists) {
            returnNo = blankToNull(body.get("return_no"));
            if (returnNo == null) returnNo = nextReturnNo();
            jdbc.update("""
                INSERT INTO device_goods_return (
                    id, return_no, warehouse_id, supplier_id, entry_id, return_date, reason,
                    doc_status, status, approval_status, remark,
                    created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?::date, ?, 'draft', 'draft', 'draft', ?, NOW(), NOW(), ?, ?, ?, ?, 0)
                """,
                    id, returnNo,
                    warehouseId, supplierId, entryId,
                    toDateParam(body.get("return_date")), blankToNull(body.get("reason")),
                    blankToNull(body.get("remark")),
                    actorId, actorName, actorId, actorName);
            SoftDeleteSupport.applyChannels(jdbc, "device_goods_return", id, body, "create_channel", "update_channel");
        } else {
            var nos = jdbc.queryForList("SELECT return_no FROM device_goods_return WHERE id = ?", id);
            returnNo = nos.isEmpty() ? null : blankToNull(nos.get(0).get("return_no"));
            jdbc.update("""
                UPDATE device_goods_return
                SET warehouse_id = ?, supplier_id = ?, entry_id = ?, return_date = ?::date, reason = ?, remark = ?,
                    updated_at = NOW(), updated_by = ?, updated_by_name = ?
                WHERE id = ?
                """,
                    warehouseId, supplierId, entryId,
                    toDateParam(body.get("return_date")), blankToNull(body.get("reason")), blankToNull(body.get("remark")),
                    actorId, actorName, id);
            SoftDeleteSupport.applyChannels(jdbc, "device_goods_return", id, body, "update_channel");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        upsertItems(id, returnNo, items, actorId, actorName);
        return get(id);
    }

    private void upsertItems(UUID returnId, String returnNo, List<Map<String, Object>> items,
                             UUID actorId, String actorName) {
        try {
        Set<UUID> keep = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            UUID itemId = DetailLineUpsertSupport.resolveLineId(
                    jdbc, "device_goods_return_item", "return_id", returnId, item.get("id"), item.get("device_id"));
            keep.add(itemId);
            boolean exists = DetailLineUpsertSupport.existsUnderParent(
                    jdbc, "device_goods_return_item", "return_id", returnId, itemId);
            Object deviceIdObj = item.get("device_id");
            Map<String, Object> deviceSnap = DeviceSnapshotSupport.resolve(
                    jdbc, deviceIdObj, item.get("device_code"), item.get("device_name"));
            if (exists) {
                List<String> sets = new ArrayList<>();
                List<Object> args = new ArrayList<>();
                sets.add("return_no = COALESCE(?, return_no)");
                args.add(returnNo);
                sets.add("device_id = ?");
                args.add(parseUuid(deviceIdObj));
                sets.add("device_code = ?");
                args.add(deviceSnap.get("device_code"));
                sets.add("device_name = ?");
                args.add(deviceSnap.get("device_name"));
                sets.add("specification = ?");
                args.add(blankToNull(item.get("specification")));
                sets.add("unit = ?");
                args.add(blankToNull(item.get("unit")));
                sets.add("quantity = ?");
                args.add(item.getOrDefault("quantity", 1));
                sets.add("unit_price = ?");
                args.add(item.get("unit_price"));
                sets.add("total_price = ?");
                args.add(item.get("total_price"));
                sets.add("manufacturer_id = ?");
                args.add(parseUuid(item.get("manufacturer_id")));
                sets.add("serial_number = ?");
                args.add(blankToNull(item.get("serial_number")));
                sets.add("brand = ?");
                args.add(blankToNull(item.get("brand")));
                sets.add("category_id = ?");
                args.add(parseUuid(item.get("category_id")));
                sets.add("category_name = ?");
                args.add(blankToNull(item.get("category_name")));
                sets.add("asset_category_id = ?");
                args.add(parseUuid(item.get("asset_category_id")));
                sets.add("asset_category_name = ?");
                args.add(blankToNull(item.get("asset_category_name")));
                sets.add("finance_category_id = ?");
                args.add(parseUuid(item.get("finance_category_id")));
                sets.add("finance_category_name = ?");
                args.add(blankToNull(item.get("finance_category_name")));
                DetailLineUpsertSupport.appendRestoreIfNeeded(jdbc, "device_goods_return_item", itemId, sets);
                SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, "device_goods_return_item"), sets, args);
                DetailLineUpsertSupport.appendRowVersionBump(jdbc, "device_goods_return_item", sets);
                DetailLineUpsertSupport.updateWithOptionalLock(
                        jdbc, "UPDATE device_goods_return_item SET " + String.join(", ", sets),
                        args, itemId, DetailLineUpsertSupport.parseRowVersion(item.get("row_version")));
            } else {
                jdbc.update("""
                    INSERT INTO device_goods_return_item (
                        id, return_id, return_no, device_id, device_code, device_name, specification, unit,
                        quantity, unit_price, total_price, manufacturer_id, serial_number,
                        brand, category_id, category_name, asset_category_id, asset_category_name,
                        finance_category_id, finance_category_name,
                        created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?, ?, ?, 0)
                    """,
                        itemId, returnId, returnNo, parseUuid(deviceIdObj),
                        deviceSnap.get("device_code"), deviceSnap.get("device_name"),
                        blankToNull(item.get("specification")), blankToNull(item.get("unit")),
                        item.getOrDefault("quantity", 1),
                        item.get("unit_price"), item.get("total_price"),
                        parseUuid(item.get("manufacturer_id")), blankToNull(item.get("serial_number")),
                        blankToNull(item.get("brand")),
                        parseUuid(item.get("category_id")), blankToNull(item.get("category_name")),
                        parseUuid(item.get("asset_category_id")), blankToNull(item.get("asset_category_name")),
                        parseUuid(item.get("finance_category_id")), blankToNull(item.get("finance_category_name")),
                        actorId, actorName, actorId, actorName);
            }
        }
        DetailLineUpsertSupport.softDeleteNotIn(jdbc, "device_goods_return_item", "return_id", returnId, keep);
        } catch (RuntimeException e) {
            DetailLineUpsertSupport.rethrowDeviceUniqueConflict(e);
        }
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "asset", description = "提交设备退货审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> r = get(id).getData();
        SoftDeleteSupport.applyChannels(jdbc, "device_goods_return", id, body, "submit_channel", "update_channel");
        approvalService.submit("device_goods_return", id, r.get("return_no").toString(),
                "设备退货 " + r.get("return_no"),
                UUID.fromString(body.get("applicantId").toString()), 0);
        return get(id);
    }

    @PostMapping("/{id}/complete")
    @Transactional
    @OperationLog(module = "asset", description = "审核设备退货")
    public Result<Map<String, Object>> complete(@PathVariable UUID id) {
        var row = jdbc.queryForList(
                "SELECT * FROM device_goods_return WHERE id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return", null), id);
        if (row.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> gr = row.get(0);
        if ("returned".equals(String.valueOf(gr.get("status")))
                || "approved".equals(String.valueOf(gr.get("approval_status")))) {
            return get(id);
        }
        if (gr.get("warehouse_id") == null) {
            throw new BizException(400, "请先选择仓库后再审核");
        }
        var items = jdbc.queryForList(
                "SELECT device_id FROM device_goods_return_item WHERE return_id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return_item", null), id);
        if (items.isEmpty()) {
            throw new BizException(400, "请先添加退货明细");
        }
        var ctx = com.meis.saas.common.rbac.PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
        int claimed = jdbc.update("""
                UPDATE device_goods_return
                SET status = 'returned',
                    doc_status = 'approved',
                    approval_status = 'approved',
                    approved_by = COALESCE(approved_by, ?),
                    approved_by_name = COALESCE(approved_by_name, ?),
                    approved_at = COALESCE(approved_at, CURRENT_DATE),
                    updated_at = NOW(),
                    updated_by = ?,
                    updated_by_name = ?
                WHERE id = ? AND COALESCE(status, '') <> 'returned'
                  AND COALESCE(approval_status, '') <> 'approved'
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return", null),
                actorId, actorName, actorId, actorName, id);
        if (claimed == 0) {
            return get(id);
        }
        SoftDeleteSupport.applyChannels(jdbc, "device_goods_return", id, null, "confirm_channel", "update_channel");
        for (Map<String, Object> item : items) {
            if (item.get("device_id") != null) {
                jdbc.update("""
                    UPDATE medical_device
                    SET device_status = 'returned', warehouse_id = NULL, updated_at = NOW()
                    WHERE id = ?
                    """, item.get("device_id"));
            }
        }
        return get(id);
    }

    private String nextReturnNo() {
        String prefix = "TH-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Integer maxSeq = jdbc.queryForObject("""
                SELECT COALESCE(MAX(CAST(RIGHT(return_no, 4) AS INTEGER)), 0)
                FROM device_goods_return
                WHERE return_no LIKE ?
                  AND LENGTH(return_no) = ?
                  AND RIGHT(return_no, 4) ~ '^[0-9]{4}$'
                """, Integer.class, prefix + "%", prefix.length() + 4);
        int seq = (maxSeq != null ? maxSeq : 0) + 1;
        for (int i = 0; i < 20; i++) {
            String candidate = prefix + String.format("%04d", seq + i);
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM device_goods_return WHERE return_no = ?", Integer.class, candidate);
            if (cnt == null || cnt == 0) return candidate;
        }
        return prefix + String.format("%04d", seq) + System.currentTimeMillis() % 100;
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

    private static String toDateParam(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        String s = v.toString().trim();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }
}
