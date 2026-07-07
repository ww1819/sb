package com.meis.saas.purchase.controller;



import com.meis.saas.common.audit.OperationLog;

import com.meis.saas.common.exception.BizException;

import com.meis.saas.common.page.PageQuery;

import com.meis.saas.common.page.PageResult;

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

            @RequestParam(required = false) String acceptance_status) {

        return Result.ok(PurchasePageQueries.acceptancePage(jdbc, query, acceptance_status));

    }



    @GetMapping("/{id}")

    public Result<Map<String, Object>> get(@PathVariable UUID id) {

        var rows = jdbc.queryForList("SELECT * FROM purchase_acceptance WHERE id = ?::uuid", id);

        if (rows.isEmpty()) throw new BizException(404, "not found");

        Map<String, Object> acc = rows.get(0);

        AcceptanceChecklistService.seedDefaultItems(jdbc, id);

        acc.put("items", jdbc.queryForList(

                "SELECT * FROM purchase_acceptance_item WHERE acceptance_id = ?::uuid ORDER BY sort_order", id));

        acc.put("members", jdbc.queryForList(

                "SELECT * FROM purchase_acceptance_member WHERE acceptance_id = ?::uuid", id));

        return Result.ok(acc);

    }



    @PostMapping

    @Transactional

    @OperationLog(module = "purchase", description = "保存安装验收")

    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {

        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();

        boolean exists = !jdbc.queryForList("SELECT 1 FROM purchase_acceptance WHERE id = ?::uuid", id).isEmpty();

        if (exists) {

            jdbc.update("""

                UPDATE purchase_acceptance SET acceptance_date=?, acceptance_status=?, quality_check_passed=?,

                quality_checker_id=?::uuid, quality_check_date=?, quality_check_report_url=?,

                installation_completed=?, installer_id=?::uuid, installation_date=?, installation_report_url=?,

                clinical_checker_id=?::uuid, argument_summary=?, report_url=?, remark=?, updated_at=NOW()

                WHERE id=?::uuid

                """,

                    body.get("acceptance_date"), body.get("acceptance_status"), body.get("quality_check_passed"),

                    body.get("quality_checker_id"), body.get("quality_check_date"), body.get("quality_check_report_url"),

                    body.get("installation_completed"), body.get("installer_id"), body.get("installation_date"),

                    body.get("installation_report_url"), body.get("clinical_checker_id"), body.get("argument_summary"),

                    body.get("report_url"), body.get("remark"), id);

        } else {

            throw new BizException(400, "验收单由合同审批通过后自动创建");

        }

        @SuppressWarnings("unchecked")

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

        if (items != null) AcceptanceChecklistService.saveItems(jdbc, id, items);

        @SuppressWarnings("unchecked")

        List<Map<String, Object>> members = (List<Map<String, Object>>) body.get("members");

        if (members != null) AcceptanceChecklistService.saveMembers(jdbc, id, members);

        return get(id);

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



    @PostMapping("/{id}/pass")

    @Transactional

    @OperationLog(module = "purchase", description = "验收通过生成入库单")

    public Result<Map<String, Object>> pass(@PathVariable UUID id) {

        var accRows = jdbc.queryForList("SELECT * FROM purchase_acceptance WHERE id = ?::uuid", id);

        if (accRows.isEmpty()) throw new BizException(404, "not found");

        Map<String, Object> acc = accRows.get(0);

        if (acc.get("entry_id") != null) throw new BizException(400, "已生成入库单");

        if (!"approved".equals(String.valueOf(acc.get("approval_status")))) {

            throw new BizException(400, "请先完成验收审批再生成入库单");

        }

        UUID contractId = UUID.fromString(acc.get("contract_id").toString());

        var contractRows = jdbc.queryForList("SELECT * FROM purchase_contract WHERE id = ?::uuid", contractId);

        if (contractRows.isEmpty()) throw new BizException(400, "关联合同不存在");

        Map<String, Object> contract = contractRows.get(0);

        String traceNo = PurchaseValidators.buildTraceNo(jdbc, contractId);

        Object chainNo = contract.get("business_chain_no");

        if (chainNo == null) chainNo = traceNo;

        UUID projectId = contract.get("project_id") != null ? UUID.fromString(contract.get("project_id").toString()) : null;

        UUID planId = null;

        if (projectId != null) {

            var pj = jdbc.queryForList("SELECT plan_id FROM purchase_project WHERE id = ?::uuid", projectId);

            if (!pj.isEmpty() && pj.get(0).get("plan_id") != null) {

                planId = UUID.fromString(pj.get(0).get("plan_id").toString());

            }

        }



        UUID entryId = UUID.randomUUID();

        String entryNo = "EN" + System.currentTimeMillis();

        jdbc.update("""

            INSERT INTO device_entry (id, entry_no, contract_id, supplier_id, acceptance_id, project_id, plan_id,

            trace_no, business_chain_no, entry_date, entry_type, status)

            VALUES (?::uuid,?,?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?,?,?,?,?)

            """,

                entryId, entryNo, contractId, contract.get("supplier_id"), id, projectId, planId,

                traceNo, chainNo, LocalDate.now(), "purchase", "draft");



        var planItems = jdbc.queryForList("""

            SELECT pi.device_name, pi.specification, pi.quantity, pi.estimated_price

            FROM purchase_plan_item pi

            JOIN purchase_project pp ON pp.plan_id = pi.plan_id

            JOIN purchase_contract pc ON pc.project_id = pp.id

            WHERE pc.id = ?::uuid

            """, contractId);

        if (planItems.isEmpty()) {

            jdbc.update("""

                INSERT INTO device_entry_item (id, entry_id, device_name, quantity, unit_price)

                VALUES (?::uuid,?::uuid,?,?,?)

                """,

                    UUID.randomUUID(), entryId, contract.get("contract_name"), 1, contract.get("contract_amount"));

        } else {

            for (Map<String, Object> item : planItems) {

                jdbc.update("""

                    INSERT INTO device_entry_item (id, entry_id, device_name, model, quantity, unit_price)

                    VALUES (?::uuid,?::uuid,?,?,?,?)

                    """,

                        UUID.randomUUID(), entryId, item.get("device_name"), item.get("specification"),

                        item.get("quantity"), item.get("estimated_price"));

            }

        }



        jdbc.update("""

            UPDATE purchase_acceptance SET acceptance_status='passed', entry_id=?::uuid, updated_at=NOW()

            WHERE id=?::uuid

            """, entryId, id);

        jdbc.update("UPDATE purchase_contract SET acceptance_status='passed', updated_at=NOW() WHERE id=?::uuid", contractId);

        Map<String, Object> result = get(id).getData();

        result.put("entry_id", entryId);

        result.put("entry_no", entryNo);

        result.put("trace_no", traceNo);

        return Result.ok(result);

    }

}

