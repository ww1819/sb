package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.FilterCsvSupport;
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

    /** 预览下一退库单号：TK-yyyyMMdd + 4 位当日流水 */
    @GetMapping("/next-no")
    public Result<Map<String, Object>> nextNo() {
        return Result.ok(Map.of("return_no", nextReturnNo()));
    }

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String approval_status,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dept_id) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "device_return", "r"));
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (r.return_no ILIKE ? OR d.dept_name ILIKE ? OR w.warehouse_name ILIKE ?) ");
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
        } else if (status != null && !status.isBlank()) {
            FilterCsvSupport.appendStrIn(where, args, "r.status", status);
        }
        FilterCsvSupport.appendUuidIn(where, args, "r.dept_id", dept_id);
        String from = " FROM device_return r "
                + " LEFT JOIN device_outbound o ON o.id = r.outbound_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound", "o")
                + " LEFT JOIN warehouse w ON w.id = r.warehouse_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w")
                + " LEFT JOIN department d ON d.id = r.dept_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d")
                + " LEFT JOIN ("
                + "   SELECT i.return_id, COALESCE(SUM(i.total_price), 0) AS total_amount"
                + "   FROM device_return_item i WHERE 1=1"
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_return_item", "i")
                + "   GROUP BY i.return_id"
                + " ) amt ON amt.return_id = r.id";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT r.*, o.outbound_no, w.warehouse_name, d.dept_name,
                   COALESCE(amt.total_amount, 0) AS total_amount
            """ + from + where + " ORDER BY r.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
            SELECT r.*, o.outbound_no, w.warehouse_name, d.dept_name,
                   COALESCE(amt.total_amount, 0) AS total_amount
            FROM device_return r
            LEFT JOIN device_outbound o ON o.id = r.outbound_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound", "o") + """
            LEFT JOIN warehouse w ON w.id = r.warehouse_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w") + """
            LEFT JOIN department d ON d.id = r.dept_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d") + """
            LEFT JOIN (
              SELECT i.return_id, COALESCE(SUM(i.total_price), 0) AS total_amount
              FROM device_return_item i WHERE 1=1
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_return_item", "i") + """
              GROUP BY i.return_id
            ) amt ON amt.return_id = r.id
            WHERE r.id = ?::uuid
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_return", "r"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> r = rows.get(0);
        r.put("items", jdbc.queryForList(
                "SELECT * FROM device_return_item WHERE return_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_return_item", null)
                        + " ORDER BY created_at ASC NULLS LAST", id));
        return Result.ok(r);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存退库单")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null && !body.get("id").toString().isBlank()
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM device_return WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_return", null), id).isEmpty();
        if (exists) {
            var cur = jdbc.queryForList(
                    "SELECT approval_status, status FROM device_return WHERE id = ?::uuid", id);
            if (!cur.isEmpty()) {
                String st = String.valueOf(cur.get(0).get("status"));
                String ap = String.valueOf(cur.get(0).get("approval_status"));
                if ("returned".equals(st) || "approved".equals(ap)) {
                    throw new BizException(400, "已退库/已审核单据不可编辑");
                }
            }
        }
        UUID outboundId = parseUuid(body.get("outbound_id"));
        UUID warehouseId = parseUuid(body.get("warehouse_id"));
        UUID deptId = parseUuid(body.get("dept_id"));
        UUID returnerId = parseUuid(body.get("returner_id") != null ? body.get("returner_id") : body.get("recipient_id"));
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
                INSERT INTO device_return (
                    id, return_no, outbound_id, warehouse_id, dept_id, returner_id,
                    return_date, reason, remark, doc_status, status, approval_status,
                    created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                ) VALUES (?::uuid, ?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::date, ?, ?,
                    'draft', 'draft', 'draft', NOW(), NOW(), ?, ?, ?, ?, 0)
                """,
                    id, returnNo, outboundId, warehouseId, deptId, returnerId,
                    toDateParam(body.get("return_date")), blankToNull(body.get("reason")), blankToNull(body.get("remark")),
                    actorId, actorName, actorId, actorName);
        } else {
            var nos = jdbc.queryForList("SELECT return_no FROM device_return WHERE id = ?::uuid", id);
            returnNo = nos.isEmpty() ? null : blankToNull(nos.get(0).get("return_no"));
            jdbc.update("""
                UPDATE device_return
                SET outbound_id = ?::uuid, warehouse_id = ?::uuid, dept_id = ?::uuid, returner_id = ?::uuid,
                    return_date = ?::date, reason = ?, remark = ?,
                    updated_at = NOW(), updated_by = ?, updated_by_name = ?
                WHERE id = ?::uuid
                """,
                    outboundId, warehouseId, deptId, returnerId,
                    toDateParam(body.get("return_date")), blankToNull(body.get("reason")), blankToNull(body.get("remark")),
                    actorId, actorName, id);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        jdbc.update("""
                UPDATE device_return_item
                SET is_deleted = 1, deleted_at = NOW(), deleted_by = ?, deleted_by_name = ?,
                    updated_at = NOW(), updated_by = ?, updated_by_name = ?
                WHERE return_id = ?::uuid AND COALESCE(is_deleted, 0) = 0
                """, actorId, actorName, actorId, actorName, id);
        for (Map<String, Object> item : items) {
            jdbc.update("""
                INSERT INTO device_return_item (
                    id, return_id, return_no, device_id, device_code, device_name, specification, unit,
                    quantity, unit_price, total_price, manufacturer_id, supplier_id, serial_number, brand,
                    category_id, category_name, asset_category_id, asset_category_name,
                    finance_category_id, finance_category_name, condition_note,
                    created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                ) VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?::uuid, ?::uuid, ?, ?,
                    ?::uuid, ?, ?::uuid, ?, ?::uuid, ?, ?,
                    NOW(), NOW(), ?, ?, ?, ?, 0)
                """,
                    UUID.randomUUID(), id, returnNo, parseUuid(item.get("device_id")),
                    blankToNull(item.get("device_code")), blankToNull(item.get("device_name")),
                    blankToNull(item.get("specification")), blankToNull(item.get("unit")),
                    item.getOrDefault("quantity", 1),
                    item.get("unit_price"), item.get("total_price"),
                    parseUuid(item.get("manufacturer_id")), parseUuid(item.get("supplier_id")),
                    blankToNull(item.get("serial_number")), blankToNull(item.get("brand")),
                    parseUuid(item.get("category_id")), blankToNull(item.get("category_name")),
                    parseUuid(item.get("asset_category_id")), blankToNull(item.get("asset_category_name")),
                    parseUuid(item.get("finance_category_id")), blankToNull(item.get("finance_category_name")),
                    blankToNull(item.get("condition_note")),
                    actorId, actorName, actorId, actorName);
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
        var ctx = com.meis.saas.common.rbac.PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
        jdbc.update("""
                UPDATE device_return
                SET status = 'returned', doc_status = 'returned', approval_status = 'approved',
                    approved_by = COALESCE(approved_by, ?),
                    approved_by_name = COALESCE(approved_by_name, ?),
                    approved_at = COALESCE(approved_at, CURRENT_DATE),
                    updated_at = NOW()
                WHERE id = ?::uuid
                """, actorId, actorName, id);
        return get(id);
    }

    private String nextReturnNo() {
        String prefix = "TK-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        Integer maxSeq = jdbc.queryForObject("""
                SELECT COALESCE(MAX(CAST(RIGHT(return_no, 4) AS INTEGER)), 0)
                FROM device_return
                WHERE return_no LIKE ?
                  AND LENGTH(return_no) = ?
                  AND RIGHT(return_no, 4) ~ '^[0-9]{4}$'
                """, Integer.class, prefix + "%", prefix.length() + 4);
        int seq = (maxSeq != null ? maxSeq : 0) + 1;
        for (int i = 0; i < 20; i++) {
            String candidate = prefix + String.format("%04d", seq + i);
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM device_return WHERE return_no = ?", Integer.class, candidate);
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
