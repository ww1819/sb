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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/asset/outbound")
@RequiredArgsConstructor
public class DeviceOutboundController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    /** 预览下一出库单号：CK-yyyyMMdd + 4 位当日流水 */
    @GetMapping("/next-no")
    public Result<Map<String, Object>> nextNo() {
        return Result.ok(Map.of("outbound_no", nextOutboundNo()));
    }

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String approval_status,
            @RequestParam(required = false) String doc_status,
            @RequestParam(required = false) String dept_id) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound", "o"));
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (o.outbound_no ILIKE ? OR d.dept_name ILIKE ?) ");
            String kw = "%" + query.getKeyword() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (approval_status != null && !approval_status.isBlank()) {
            if (approval_status.contains(",")) {
                FilterCsvSupport.appendStrIn(where, args, "COALESCE(o.approval_status, 'draft')", approval_status);
            } else {
                where.append(" AND COALESCE(o.approval_status, 'draft') = ? ");
                args.add(approval_status);
            }
        } else if (doc_status != null && !doc_status.isBlank()) {
            FilterCsvSupport.appendStrIn(where, args, "o.doc_status", doc_status);
        }
        FilterCsvSupport.appendUuidIn(where, args, "o.dept_id", dept_id);
        String from = " FROM device_outbound o "
                + " LEFT JOIN department d ON d.id = o.dept_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d")
                + " LEFT JOIN warehouse w ON w.id = o.warehouse_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w")
                + " LEFT JOIN ("
                + "   SELECT i.outbound_id, COALESCE(SUM(i.total_price), 0) AS total_amount"
                + "   FROM device_outbound_item i WHERE 1=1"
                + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound_item", "i")
                + "   GROUP BY i.outbound_id"
                + " ) amt ON amt.outbound_id = o.id";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT o.*, d.dept_name, w.warehouse_name,
                   COALESCE(amt.total_amount, 0) AS total_amount
            """ + from + where + " ORDER BY o.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
            SELECT o.*, d.dept_name, w.warehouse_name,
                   COALESCE(NULLIF(TRIM(ru.real_name), ''), ru.username) AS receiver_name,
                   COALESCE(amt.total_amount, 0) AS total_amount
            FROM device_outbound o
            LEFT JOIN department d ON d.id = o.dept_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d") + """
            LEFT JOIN warehouse w ON w.id = o.warehouse_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w") + """
            LEFT JOIN sys_user ru ON ru.id = o.receiver_id
            LEFT JOIN (
              SELECT i.outbound_id, COALESCE(SUM(i.total_price), 0) AS total_amount
              FROM device_outbound_item i WHERE 1=1
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound_item", "i") + """
              GROUP BY i.outbound_id
            ) amt ON amt.outbound_id = o.id
            WHERE o.id = ?
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound", "o"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> o = rows.get(0);
        o.put("items", jdbc.queryForList(
                "SELECT * FROM device_outbound_item WHERE outbound_id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound_item", null)
                        + " ORDER BY created_at ASC NULLS LAST", id));
        return Result.ok(o);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存出库单")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null && !body.get("id").toString().isBlank()
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM device_outbound WHERE id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound", null), id).isEmpty();
        if (exists) {
            var cur = jdbc.queryForList(
                    "SELECT approval_status, status FROM device_outbound WHERE id = ?", id);
            if (!cur.isEmpty()) {
                String st = String.valueOf(cur.get(0).get("status"));
                String ap = String.valueOf(cur.get(0).get("approval_status"));
                if ("issued".equals(st) || "approved".equals(ap)) {
                    throw new BizException(400, "已出库/已审核单据不可编辑");
                }
            }
        }
        UUID deptId = parseUuid(body.get("dept_id"));
        UUID receiverId = parseUuid(body.get("receiver_id") != null ? body.get("receiver_id") : body.get("recipient_id"));
        UUID warehouseId = parseUuid(body.get("warehouse_id"));
        var ctx = com.meis.saas.common.rbac.PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
        String outboundNo;
        if (!exists) {
            outboundNo = blankToNull(body.get("outbound_no"));
            if (outboundNo == null) outboundNo = nextOutboundNo();
            jdbc.update("""
                INSERT INTO device_outbound (
                    id, outbound_no, dept_id, receiver_id, warehouse_id, outbound_date, purpose, remark,
                    doc_status, status, approval_status,
                    created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?::date, ?, ?, 'draft', 'draft', 'draft', NOW(), NOW(), ?, ?, ?, ?, 0)
                """,
                    id, outboundNo,
                    deptId, receiverId, warehouseId,
                    toDateParam(body.get("outbound_date")), blankToNull(body.get("purpose")), blankToNull(body.get("remark")),
                    actorId, actorName, actorId, actorName);
        } else {
            var nos = jdbc.queryForList("SELECT outbound_no FROM device_outbound WHERE id = ?", id);
            outboundNo = nos.isEmpty() ? null : blankToNull(nos.get(0).get("outbound_no"));
            jdbc.update("""
                UPDATE device_outbound
                SET dept_id = ?, receiver_id = ?, warehouse_id = ?, outbound_date = ?::date,
                    purpose = ?, remark = ?,
                    updated_at = NOW(), updated_by = ?, updated_by_name = ?
                WHERE id = ?
                """,
                    deptId, receiverId, warehouseId,
                    toDateParam(body.get("outbound_date")), blankToNull(body.get("purpose")), blankToNull(body.get("remark")),
                    actorId, actorName, id);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        jdbc.update("""
                UPDATE device_outbound_item
                SET is_deleted = 1, deleted_at = NOW(), deleted_by = ?, deleted_by_name = ?,
                    updated_at = NOW(), updated_by = ?, updated_by_name = ?
                WHERE outbound_id = ? AND COALESCE(is_deleted, 0) = 0
                """, actorId, actorName, actorId, actorName, id);
        for (Map<String, Object> item : items) {
            jdbc.update("""
                INSERT INTO device_outbound_item (
                    id, outbound_id, outbound_no, device_id, device_code, device_name, specification, unit,
                    quantity, unit_price, total_price, manufacturer_id, supplier_id, serial_number,
                    brand, category_id, category_name, asset_category_id, asset_category_name,
                    finance_category_id, finance_category_name,
                    created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?, ?, ?, 0)
                """,
                    UUID.randomUUID(), id, outboundNo, parseUuid(item.get("device_id")),
                    blankToNull(item.get("device_code")), blankToNull(item.get("device_name")),
                    blankToNull(item.get("specification")), blankToNull(item.get("unit")),
                    item.getOrDefault("quantity", 1),
                    item.get("unit_price"), item.get("total_price"),
                    parseUuid(item.get("manufacturer_id")), parseUuid(item.get("supplier_id")),
                    blankToNull(item.get("serial_number")),
                    blankToNull(item.get("brand")),
                    parseUuid(item.get("category_id")), blankToNull(item.get("category_name")),
                    parseUuid(item.get("asset_category_id")), blankToNull(item.get("asset_category_name")),
                    parseUuid(item.get("finance_category_id")), blankToNull(item.get("finance_category_name")),
                    actorId, actorName, actorId, actorName);
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
        var outs = jdbc.queryForList(
                "SELECT dept_id FROM device_outbound WHERE id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound", null), id);
        if (outs.isEmpty()) throw new BizException(404, "not found");
        UUID deptId = parseUuid(outs.get(0).get("dept_id"));
        var items = jdbc.queryForList(
                "SELECT device_id FROM device_outbound_item WHERE outbound_id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound_item", null), id);
        for (Map<String, Object> item : items) {
            if (item.get("device_id") != null) {
                jdbc.update("""
                    UPDATE medical_device
                    SET device_status = 'in_use', warehouse_id = NULL, dept_id = COALESCE(?, dept_id),
                        updated_at = NOW()
                    WHERE id = ?
                    """, deptId, item.get("device_id"));
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
                UPDATE device_outbound
                SET doc_status = 'issued', status = 'issued', approval_status = 'approved',
                    approved_by = COALESCE(approved_by, ?),
                    approved_by_name = COALESCE(approved_by_name, ?),
                    approved_at = COALESCE(approved_at, CURRENT_DATE),
                    updated_at = NOW()
                WHERE id = ?
                """, actorId, actorName, id);
        return get(id);
    }

    private String nextOutboundNo() {
        String prefix = "CK-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Integer maxSeq = jdbc.queryForObject("""
                SELECT COALESCE(MAX(CAST(RIGHT(outbound_no, 4) AS INTEGER)), 0)
                FROM device_outbound
                WHERE outbound_no LIKE ?
                  AND LENGTH(outbound_no) = ?
                  AND RIGHT(outbound_no, 4) ~ '^[0-9]{4}$'
                """, Integer.class, prefix + "%", prefix.length() + 4);
        int seq = (maxSeq != null ? maxSeq : 0) + 1;
        for (int i = 0; i < 20; i++) {
            String candidate = prefix + String.format("%04d", seq + i);
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM device_outbound WHERE outbound_no = ?", Integer.class, candidate);
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
