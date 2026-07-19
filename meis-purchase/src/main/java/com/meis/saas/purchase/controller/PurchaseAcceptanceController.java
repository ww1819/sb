package com.meis.saas.purchase.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import com.meis.saas.purchase.support.AcceptanceChecklistService;
import com.meis.saas.purchase.support.PurchasePageQueries;
import com.meis.saas.purchase.support.PurchaseValidators;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/purchase/acceptance")
@RequiredArgsConstructor
public class PurchaseAcceptanceController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String acceptance_status,
            @RequestParam(required = false) String approval_status) {
        return Result.ok(PurchasePageQueries.acceptancePage(jdbc, query, acceptance_status, approval_status));
    }

    /** 列表快捷审核：验收状态→已经验收，审批状态→已审核，并记录审核人/日期 */
    @PostMapping("/review")
    @OperationLog(module = "purchase", description = "安装验收列表审核")
    public Result<Map<String, Object>> review(@RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new BizException(400, "请先勾选要审核的验收单");
        }
        var ctx = com.meis.saas.common.rbac.PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
        int ok = 0;
        int skipped = 0;
        for (Object o : list) {
            if (o == null || o.toString().isBlank()) continue;
            UUID id = UUID.fromString(o.toString());
            var rows = jdbc.queryForList(
                    "SELECT acceptance_status, approval_status FROM purchase_acceptance WHERE id = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", null), id);
            if (rows.isEmpty()) continue;
            if ("approved".equals(String.valueOf(rows.get(0).get("approval_status")))) {
                skipped++;
                continue;
            }
            int updated = jdbc.update("""
                    UPDATE purchase_acceptance
                    SET acceptance_status = 'passed',
                        approval_status = 'approved',
                        acceptance_date = COALESCE(acceptance_date, CURRENT_DATE),
                        approved_by = ?,
                        approved_by_name = ?,
                        approved_at = CURRENT_DATE,
                        updated_at = NOW()
                    WHERE id = ? AND COALESCE(approval_status, '') <> 'approved'
                    """, actorId, actorName, id);
            if (updated <= 0) {
                skipped++;
                continue;
            }
            try {
                jdbc.update("""
                        UPDATE purchase_contract c
                        SET acceptance_status = 'passed', updated_at = NOW()
                        FROM purchase_acceptance a
                        WHERE a.id = ? AND a.contract_id = c.id AND a.contract_id IS NOT NULL
                        """, id);
            } catch (Exception ignored) {
                // ignore
            }
            ok++;
        }
        if (ok == 0 && skipped > 0) {
            throw new BizException(400, "勾选的验收单均已审核，无需重复审核");
        }
        if (ok == 0) {
            throw new BizException(400, "没有可审核的验收单");
        }
        return Result.ok(Map.of("approved", ok, "skipped", skipped));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM purchase_acceptance WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> acc = rows.get(0);
        AcceptanceChecklistService.seedDefaultItems(jdbc, id);
        acc.put("items", jdbc.queryForList(
                "SELECT * FROM purchase_acceptance_item WHERE acceptance_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance_item", null)
                        + " ORDER BY sort_order", id));
        acc.put("members", jdbc.queryForList(
                "SELECT * FROM purchase_acceptance_member WHERE acceptance_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance_member", null), id));
        acc.put("devices", jdbc.queryForList("""
                SELECT id, acceptance_id, device_name, specification, brand, quantity, unit_price, amount,
                       manufacturer_id, manufacturer_name, sort_order
                FROM purchase_acceptance_device
                WHERE acceptance_id = ?
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance_device", null) + """
                ORDER BY sort_order ASC NULLS LAST, created_at ASC NULLS LAST
                """, id));
        return Result.ok(acc);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "purchase", description = "保存安装验收")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null && !body.get("id").toString().isBlank()
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        UUID contractId = parseUuid(body.get("contract_id"));
        UUID projectId = parseUuid(body.get("project_id"));
        UUID supplierId = parseUuid(body.get("supplier_id"));
        if (supplierId == null && contractId != null) {
            var c = jdbc.queryForList(
                    "SELECT supplier_id, project_id, business_chain_no FROM purchase_contract WHERE id = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null), contractId);
            if (!c.isEmpty()) {
                if (c.get(0).get("supplier_id") != null) {
                    supplierId = UUID.fromString(c.get(0).get("supplier_id").toString());
                }
                if (projectId == null && c.get(0).get("project_id") != null) {
                    projectId = UUID.fromString(c.get(0).get("project_id").toString());
                }
            }
        }
        Object chainNo = blankToNull(body.get("business_chain_no"));
        if (chainNo == null && contractId != null) {
            var c = jdbc.queryForList(
                    "SELECT business_chain_no FROM purchase_contract WHERE id = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null), contractId);
            if (!c.isEmpty()) chainNo = c.get(0).get("business_chain_no");
        }
        boolean exists = !jdbc.queryForList(
                "SELECT 1 FROM purchase_acceptance WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", null), id).isEmpty();
        if (exists) {
            var st = jdbc.queryForList(
                    "SELECT approval_status FROM purchase_acceptance WHERE id = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", null), id);
            if (!st.isEmpty() && "approved".equals(String.valueOf(st.get(0).get("approval_status")))) {
                throw new BizException(400, "已审核的验收单不可编辑");
            }
            jdbc.update("""
                UPDATE purchase_acceptance SET contract_id=?, project_id=?, supplier_id=?,
                acceptance_date=?, acceptance_status=?, quality_check_passed=?,
                quality_checker_id=?, quality_check_date=?, quality_check_report_url=?,
                installation_completed=?, installer_id=?, installation_date=?, installation_report_url=?,
                clinical_checker_id=?, argument_summary=?, report_url=?, remark=?, updated_at=NOW()
                WHERE id=?
                """,
                    contractId, projectId, supplierId,
                    toDateOrNull(body.get("acceptance_date")),
                    body.getOrDefault("acceptance_status", "pending"),
                    body.get("quality_check_passed"),
                    parseUuid(body.get("quality_checker_id")), toDateOrNull(body.get("quality_check_date")),
                    blankToNull(body.get("quality_check_report_url")),
                    body.get("installation_completed"), parseUuid(body.get("installer_id")),
                    toDateOrNull(body.get("installation_date")), blankToNull(body.get("installation_report_url")),
                    parseUuid(body.get("clinical_checker_id")), blankToNull(body.get("argument_summary")),
                    blankToNull(body.get("report_url")), blankToNull(body.get("remark")), id);
        } else {
            String acceptanceNo = blankToNull(body.get("acceptance_no"));
            if (acceptanceNo == null) acceptanceNo = "AC" + System.currentTimeMillis();
            jdbc.update("""
                INSERT INTO purchase_acceptance (
                    id, acceptance_no, contract_id, project_id, supplier_id, acceptance_date, acceptance_status,
                    quality_check_passed, quality_checker_id, quality_check_date, quality_check_report_url,
                    installation_completed, installer_id, installation_date, installation_report_url,
                    clinical_checker_id, argument_summary, report_url, remark, approval_status, business_chain_no
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'draft',?)
                """,
                    id, acceptanceNo, contractId, projectId, supplierId,
                    toDateOrNull(body.get("acceptance_date")),
                    body.getOrDefault("acceptance_status", "pending"),
                    body.get("quality_check_passed"),
                    parseUuid(body.get("quality_checker_id")), toDateOrNull(body.get("quality_check_date")),
                    blankToNull(body.get("quality_check_report_url")),
                    body.get("installation_completed"), parseUuid(body.get("installer_id")),
                    toDateOrNull(body.get("installation_date")), blankToNull(body.get("installation_report_url")),
                    parseUuid(body.get("clinical_checker_id")), blankToNull(body.get("argument_summary")),
                    blankToNull(body.get("report_url")), blankToNull(body.get("remark")), chainNo);
            AcceptanceChecklistService.seedDefaultItems(jdbc, id);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items != null) AcceptanceChecklistService.saveItems(jdbc, id, items);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) body.get("members");
        if (members != null) AcceptanceChecklistService.saveMembers(jdbc, id, members);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> devices = (List<Map<String, Object>>) body.get("devices");
        if (devices != null) AcceptanceChecklistService.saveDevices(jdbc, id, devices);
        return get(id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "purchase", description = "删除安装验收")
    public Result<Void> delete(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT approval_status FROM purchase_acceptance WHERE id = ?"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        if ("approved".equals(String.valueOf(rows.get(0).get("approval_status")))) {
            throw new BizException(400, "已审核的验收单不可删除");
        }
        SoftDeleteSupport.softDelete(jdbc, "purchase_acceptance", id.toString());
        return Result.ok();
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "purchase", description = "提交安装验收审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        AcceptanceChecklistService.seedDefaultItems(jdbc, id);
        if (!AcceptanceChecklistService.allItemsPassed(jdbc, id)) {
            throw new BizException(400, "验收清单存在未通过项，请完成全部检查后再提交");
        }
        Map<String, Object> acc = get(id).getData();
        approvalService.submit("purchase_acceptance", id,
                acc.get("acceptance_no").toString(),
                "安装验收 " + acc.get("acceptance_no"),
                UUID.fromString(body.get("applicantId").toString()), 0);
        return get(id);
    }

    @GetMapping("/{id}/entry")
    public Result<Map<String, Object>> entry(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT de.* FROM device_entry de
                JOIN purchase_acceptance a ON a.entry_id = de.id
                WHERE a.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", "de")
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", "a"), id);
        if (rows.isEmpty()) return Result.ok(Map.of());
        Map<String, Object> entry = new LinkedHashMap<>(rows.get(0));
        entry.put("items", jdbc.queryForList(
                "SELECT * FROM device_entry_item WHERE entry_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry_item", null), entry.get("id")));
        return Result.ok(entry);
    }

    @PostMapping("/{id}/pass")
    @Transactional
    @OperationLog(module = "purchase", description = "验收通过生成入库单")
    public Result<Map<String, Object>> pass(@PathVariable UUID id) {
        var accRows = jdbc.queryForList(
                "SELECT * FROM purchase_acceptance WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", null), id);
        if (accRows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> acc = accRows.get(0);
        if (acc.get("entry_id") != null) throw new BizException(400, "已生成入库单");
        if (!"approved".equals(String.valueOf(acc.get("approval_status")))) {
            throw new BizException(400, "请先完成验收审批再生成入库单");
        }

        UUID contractId = parseUuid(acc.get("contract_id"));
        UUID supplierId = parseUuid(acc.get("supplier_id"));
        UUID projectId = parseUuid(acc.get("project_id"));
        UUID planId = null;
        Object chainNo = acc.get("business_chain_no");
        String traceNo = null;
        Map<String, Object> contract = null;

        if (contractId != null) {
            var contractRows = jdbc.queryForList(
                    "SELECT * FROM purchase_contract WHERE id = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null), contractId);
            if (contractRows.isEmpty()) throw new BizException(400, "关联合同不存在");
            contract = contractRows.get(0);
            if (supplierId == null) supplierId = parseUuid(contract.get("supplier_id"));
            if (projectId == null) projectId = parseUuid(contract.get("project_id"));
            if (chainNo == null) chainNo = contract.get("business_chain_no");
            traceNo = PurchaseValidators.buildTraceNo(jdbc, contractId);
            if (chainNo == null) chainNo = traceNo;
            if (projectId != null) {
                var pj = jdbc.queryForList(
                        "SELECT plan_id FROM purchase_project WHERE id = ?"
                                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null), projectId);
                if (!pj.isEmpty() && pj.get(0).get("plan_id") != null) {
                    planId = UUID.fromString(pj.get(0).get("plan_id").toString());
                }
            }
        } else {
            traceNo = "AC-" + acc.get("acceptance_no");
            if (chainNo == null) chainNo = traceNo;
        }

        UUID entryId = UUID.randomUUID();
        String entryNo = "EN" + System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO device_entry (id, entry_no, contract_id, supplier_id, acceptance_id, project_id, plan_id,
            trace_no, business_chain_no, entry_date, entry_type, status)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """,
                entryId, entryNo, contractId, supplierId, id, projectId, planId,
                traceNo, chainNo, LocalDate.now(), "purchase", "draft");

        var devices = jdbc.queryForList("""
            SELECT device_name, specification, brand, quantity, unit_price
            FROM purchase_acceptance_device
            WHERE acceptance_id = ?
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance_device", null) + """
            ORDER BY sort_order ASC NULLS LAST, created_at ASC NULLS LAST
            """, id);

        if (!devices.isEmpty()) {
            for (Map<String, Object> item : devices) {
                jdbc.update("""
                    INSERT INTO device_entry_item (id, entry_id, device_name, brand, model, quantity, unit_price)
                    VALUES (?,?,?,?,?,?,?)
                    """,
                        UUID.randomUUID(), entryId, item.get("device_name"), item.get("brand"),
                        item.get("specification"), toIntQuantity(item.get("quantity")), item.get("unit_price"));
            }
        } else if (contractId != null && contract != null) {
            var planItems = jdbc.queryForList("""
                SELECT pi.device_name, pi.specification, pi.quantity, pi.estimated_price
                FROM purchase_plan_item pi
                JOIN purchase_project pp ON pp.plan_id = pi.plan_id
                JOIN purchase_contract pc ON pc.project_id = pp.id
                WHERE pc.id = ?
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", "pi")
                    + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pp")
                    + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc"), contractId);
            if (planItems.isEmpty()) {
                jdbc.update("""
                    INSERT INTO device_entry_item (id, entry_id, device_name, quantity, unit_price)
                    VALUES (?,?,?,?,?)
                    """,
                        UUID.randomUUID(), entryId, contract.get("contract_name"), 1, contract.get("contract_amount"));
            } else {
                for (Map<String, Object> item : planItems) {
                    jdbc.update("""
                        INSERT INTO device_entry_item (id, entry_id, device_name, model, quantity, unit_price)
                        VALUES (?,?,?,?,?,?)
                        """,
                            UUID.randomUUID(), entryId, item.get("device_name"), item.get("specification"),
                            toIntQuantity(item.get("quantity")), item.get("estimated_price"));
                }
            }
        } else {
            throw new BizException(400, "请先维护验收设备明细后再生成入库单");
        }

        jdbc.update("""
            UPDATE purchase_acceptance SET acceptance_status='passed', entry_id=?, updated_at=NOW()
            WHERE id=?
            """, entryId, id);
        if (contractId != null) {
            jdbc.update("UPDATE purchase_contract SET acceptance_status='passed', updated_at=NOW() WHERE id=?", contractId);
        }
        Map<String, Object> result = get(id).getData();
        result.put("entry_id", entryId);
        result.put("entry_no", entryNo);
        result.put("trace_no", traceNo);
        return Result.ok(result);
    }

    private static Integer toIntQuantity(Object v) {
        if (v == null) return 1;
        if (v instanceof Number n) return Math.max(1, n.intValue());
        try {
            return Math.max(1, (int) Double.parseDouble(v.toString().trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static UUID parseUuid(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String blankToNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static java.sql.Date toDateOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof java.sql.Date d) return d;
        if (v instanceof java.util.Date d) return new java.sql.Date(d.getTime());
        if (v instanceof LocalDate ld) return java.sql.Date.valueOf(ld);
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        if (s.length() >= 10) s = s.substring(0, 10);
        try {
            return java.sql.Date.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
