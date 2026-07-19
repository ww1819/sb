package com.meis.saas.common.workflow;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.notify.NotificationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ApprovalInstanceService {
    private final JdbcTemplate jdbc;
    private final ApprovalFlowService flowService;

    @Transactional
    public Map<String, Object> submit(String businessType, UUID businessId, String businessNo,
                                       String title, UUID applicantId, double amount) {
        Map<String, Object> flow = flowService.getFlowByBusinessType(businessType);
        if (flow == null) throw new BizException(400, "no approval flow for " + businessType);
        UUID flowId = UUID.fromString(flow.get("id").toString());
        UUID instanceId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sys_approval_instance (id, flow_id, business_type, business_id, business_no, title, applicant_id, status, current_node_order) VALUES (?,?,?,?,?,?,?,?,?)",
                instanceId, flowId, businessType, businessId, businessNo, title, applicantId, "pending", 1);
        updateBusinessStatus(businessType, businessId, "pending");
        NotificationHelper.push(jdbc, "审批待办", title + " 已提交，等待审批", "approval");
        return getInstance(instanceId);
    }

    @Transactional
    public Map<String, Object> approve(UUID instanceId, UUID approverId, String comment) {
        return act(instanceId, approverId, "approve", comment);
    }

    @Transactional
    public Map<String, Object> reject(UUID instanceId, UUID approverId, String comment) {
        return act(instanceId, approverId, "reject", comment);
    }

    @Transactional
    public Map<String, Object> withdraw(UUID instanceId, UUID applicantId) {
        Map<String, Object> inst = getInstance(instanceId);
        if (!applicantId.toString().equals(String.valueOf(inst.get("applicant_id")))) {
            throw new BizException(403, "only applicant can withdraw");
        }
        jdbc.update("UPDATE sys_approval_instance SET status = 'withdrawn', updated_at = NOW() WHERE id = ?::uuid", instanceId);
        updateBusinessStatus(inst.get("business_type").toString(), UUID.fromString(inst.get("business_id").toString()), "draft");
        return getInstance(instanceId);
    }

    public List<Map<String, Object>> pendingForUser(UUID userId, List<String> roleCodes) {
        if (roleCodes.isEmpty()) return List.of();
        String roles = roleCodes.stream().map(r -> "'" + r + "'").collect(java.util.stream.Collectors.joining(","));
        return jdbc.queryForList("""
            SELECT i.* FROM sys_approval_instance i
            JOIN sys_approval_node n ON n.flow_id = i.flow_id AND n.node_order = i.current_node_order
            WHERE i.status = 'pending' AND n.approver_role IN (%s)
            ORDER BY i.created_at DESC LIMIT 50
            """.formatted(roles));
    }

    public List<Map<String, Object>> records(UUID instanceId) {
        return jdbc.queryForList("""
                SELECT r.*, COALESCE(u.real_name, u.username) AS approver_name
                FROM sys_approval_record r
                LEFT JOIN sys_user u ON u.id = r.approver_id
                WHERE r.instance_id = ?::uuid
                ORDER BY r.acted_at
                """, instanceId);
    }

    /**
     * 业务单据审批进度：流程节点 + 实例状态 + 审批记录（供列表「进度」抽屉）。
     */
    public Map<String, Object> progressByBusiness(String businessType, UUID businessId) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> flow = flowService.getFlowByBusinessType(businessType);
        Map<String, Object> instance = getByBusiness(businessType, businessId);
        List<Map<String, Object>> nodes = List.of();
        if (flow != null && flow.get("id") != null) {
            nodes = flowService.listNodes(UUID.fromString(flow.get("id").toString()));
        } else if (instance != null && instance.get("flow_id") != null) {
            nodes = flowService.listNodes(UUID.fromString(instance.get("flow_id").toString()));
        }
        String status = instance == null ? null : String.valueOf(instance.get("status"));
        int currentOrder = 0;
        if (instance != null && instance.get("current_node_order") instanceof Number n) {
            currentOrder = n.intValue();
        }
        List<Map<String, Object>> records = instance != null && instance.get("id") != null
                ? records(UUID.fromString(instance.get("id").toString()))
                : List.of();
        Map<Integer, Map<String, Object>> lastRecordByNode = new HashMap<>();
        for (Map<String, Object> rec : records) {
            if (rec.get("node_order") instanceof Number no) {
                lastRecordByNode.put(no.intValue(), rec);
            }
        }
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            Map<String, Object> row = new LinkedHashMap<>(node);
            int order = node.get("node_order") instanceof Number n ? n.intValue() : 0;
            String nodeStatus = resolveNodeStatus(status, currentOrder, order, lastRecordByNode.get(order));
            row.put("node_status", nodeStatus);
            row.put("node_status_label", nodeStatusLabel(nodeStatus));
            Map<String, Object> rec = lastRecordByNode.get(order);
            if (rec != null) {
                row.put("acted_at", rec.get("acted_at"));
                row.put("approver_name", rec.get("approver_name"));
                row.put("action", rec.get("action"));
                row.put("comment", rec.get("comment"));
            }
            enriched.add(row);
        }
        out.put("flow", flow);
        out.put("instance", instance);
        out.put("nodes", enriched);
        out.put("records", records);
        return out;
    }

    private static String resolveNodeStatus(String instanceStatus, int currentOrder, int nodeOrder,
            Map<String, Object> lastRecord) {
        if (instanceStatus == null || instanceStatus.isBlank()
                || "draft".equals(instanceStatus) || "withdrawn".equals(instanceStatus)) {
            return "pending_submit";
        }
        if ("approved".equals(instanceStatus)) {
            return "approved";
        }
        if ("rejected".equals(instanceStatus)) {
            if (lastRecord != null && "reject".equals(String.valueOf(lastRecord.get("action")))) {
                return "rejected";
            }
            if (nodeOrder < currentOrder) return "approved";
            if (nodeOrder == currentOrder) return "rejected";
            return "cancelled";
        }
        // pending
        if (nodeOrder < currentOrder) return "approved";
        if (nodeOrder == currentOrder) return "current";
        return "waiting";
    }

    private static String nodeStatusLabel(String status) {
        return switch (status) {
            case "pending_submit" -> "未提交";
            case "approved" -> "已通过";
            case "rejected" -> "已驳回";
            case "current" -> "审批中";
            case "waiting" -> "待审批";
            case "cancelled" -> "已跳过";
            default -> status;
        };
    }

    private Map<String, Object> act(UUID instanceId, UUID approverId, String action, String comment) {
        Map<String, Object> inst = getInstance(instanceId);
        if (!"pending".equals(inst.get("status"))) throw new BizException(400, "instance not pending");
        int nodeOrder = ((Number) inst.get("current_node_order")).intValue();
        jdbc.update(
                "INSERT INTO sys_approval_record (id, instance_id, node_order, approver_id, action, comment) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), instanceId, nodeOrder, approverId, action, comment);

        if ("reject".equals(action)) {
            jdbc.update("UPDATE sys_approval_instance SET status = 'rejected', updated_at = NOW() WHERE id = ?::uuid", instanceId);
            updateBusinessStatus(inst.get("business_type").toString(), UUID.fromString(inst.get("business_id").toString()), "rejected", approverId);
            NotificationHelper.push(jdbc, "审批驳回", inst.get("title") + " 已被驳回", "approval");
            return getInstance(instanceId);
        }

        List<Map<String, Object>> nextNodes = jdbc.queryForList(
                "SELECT * FROM sys_approval_node WHERE flow_id = ?::uuid AND node_order > ? ORDER BY node_order LIMIT 1",
                inst.get("flow_id"), nodeOrder);
        if (nextNodes.isEmpty()) {
            jdbc.update("UPDATE sys_approval_instance SET status = 'approved', updated_at = NOW() WHERE id = ?::uuid", instanceId);
            updateBusinessStatus(inst.get("business_type").toString(), UUID.fromString(inst.get("business_id").toString()), "approved", approverId);
            NotificationHelper.push(jdbc, "审批通过", inst.get("title") + " 审批已通过", "approval");
        } else {
            int next = ((Number) nextNodes.get(0).get("node_order")).intValue();
            jdbc.update("UPDATE sys_approval_instance SET current_node_order = ?, updated_at = NOW() WHERE id = ?::uuid", next, instanceId);
        }
        return getInstance(instanceId);
    }

    private void updateBusinessStatus(String businessType, UUID businessId, String status) {
        updateBusinessStatus(businessType, businessId, status, null);
    }

    /** 业务侧快捷审核：直接置为已审批并触发通过后副作用（如合同生成验收单） */
    @Transactional
    public void forceApprove(String businessType, UUID businessId) {
        updateBusinessStatus(businessType, businessId, "approved", null);
    }

    /** 合同已审批后确保生成验收单（幂等；失败不影响已审批落库） */
    public void ensurePurchaseAcceptance(UUID contractId) {
        try {
            createPurchaseAcceptance(contractId);
        } catch (Exception ignored) {
            // 验收单创建失败不回滚合同已审批状态
        }
    }

    private void updateBusinessStatus(String businessType, UUID businessId, String status, UUID actorId) {
        switch (businessType) {
            case "purchase_plan" -> {
                if ("approved".equals(status) && actorId != null) {
                    jdbc.update("""
                            UPDATE purchase_plan SET approval_status = ?, approved_by = ?::uuid, approved_at = NOW()
                            WHERE id = ?::uuid
                            """, status, actorId, businessId);
                } else {
                    jdbc.update("UPDATE purchase_plan SET approval_status = ? WHERE id = ?::uuid", status, businessId);
                }
                if ("approved".equals(status)) {
                    com.meis.saas.common.purchase.PurchasePlanItemOrderNos.allocateForPlan(jdbc, businessId);
                }
            }
            case "purchase_contract" -> {
                jdbc.update("UPDATE purchase_contract SET approval_status = ?, updated_at = NOW() WHERE id = ?", status, businessId);
                if ("approved".equals(status)) createPurchaseAcceptance(businessId);
            }
            case "purchase_project" -> jdbc.update("UPDATE purchase_project SET approval_status = ? WHERE id = ?::uuid", status, businessId);
            case "purchase_acceptance" -> jdbc.update("UPDATE purchase_acceptance SET approval_status = ? WHERE id = ?::uuid", status, businessId);
            case "contract_payment" -> {
                jdbc.update("UPDATE contract_payment SET approval_status = ? WHERE id = ?::uuid", status, businessId);
                if ("approved".equals(status)) markPaymentPaid(businessId);
            }
            case "device_scrap" -> {
                jdbc.update("UPDATE device_scrap SET approval_status = ?, status = ? WHERE id = ?::uuid", status, status, businessId);
                if ("approved".equals(status)) autoDisposeScrap(businessId);
            }
            case "asset_transfer" -> {
                jdbc.update("UPDATE asset_transfer SET approval_status = ?, status = ? WHERE id = ?::uuid", status, status, businessId);
                if ("approved".equals(status)) autoExecuteTransfer(businessId);
            }
            case "device_outbound" -> {
                jdbc.update("UPDATE device_outbound SET doc_status = ?, approval_status = ? WHERE id = ?::uuid", status, status, businessId);
                if ("approved".equals(status)) autoIssueOutbound(businessId);
            }
            case "device_return" -> {
                jdbc.update("UPDATE device_return SET approval_status = ?, doc_status = ? WHERE id = ?::uuid", status, status, businessId);
                if ("approved".equals(status)) autoCompleteReturn(businessId);
            }
            case "shared_device_loan" -> {
                if ("approved".equals(status)) {
                    jdbc.update("""
                        UPDATE shared_device_loan SET approval_status = ?, status = ?,
                        approved_at = COALESCE(approved_at, NOW()), billing_start_at = COALESCE(billing_start_at, NOW()),
                        updated_at = NOW() WHERE id = ?::uuid
                        """, status, "approved", businessId);
                } else {
                    jdbc.update("""
                        UPDATE shared_device_loan SET approval_status = ?, status = ?, updated_at = NOW()
                        WHERE id = ?::uuid
                        """, status, status, businessId);
                }
            }
            case "shared_device_return" -> {
                jdbc.update("""
                    UPDATE shared_device_return SET approval_status = ?, status = ? WHERE id = ?::uuid
                    """, status, "approved".equals(status) ? "approved" : status, businessId);
                if ("approved".equals(status)) autoCompleteSharedReturn(businessId);
            }
            default -> {}
        }
    }

    private void autoExecuteTransfer(UUID id) {
        var row = jdbc.queryForList("SELECT * FROM asset_transfer WHERE id = ?::uuid", id);
        if (row.isEmpty()) return;
        Map<String, Object> t = row.get(0);
        if (t.get("to_dept_id") != null && t.get("device_id") != null) {
            jdbc.update("UPDATE medical_device SET dept_id = ?::uuid, updated_at = NOW() WHERE id = ?::uuid",
                    t.get("to_dept_id"), t.get("device_id"));
        }
        if (t.get("to_warehouse_id") != null && t.get("device_id") != null) {
            jdbc.update("UPDATE medical_device SET warehouse_id = ?::uuid, updated_at = NOW() WHERE id = ?::uuid",
                    t.get("to_warehouse_id"), t.get("device_id"));
        }
        jdbc.update("UPDATE asset_transfer SET status = 'completed', updated_at = NOW() WHERE id = ?::uuid", id);
    }

    private void autoCompleteReturn(UUID id) {
        var row = jdbc.queryForList("SELECT warehouse_id FROM device_return WHERE id = ?::uuid", id);
        if (row.isEmpty()) return;
        Object warehouseId = row.get(0).get("warehouse_id");
        var items = jdbc.queryForList("SELECT device_id FROM device_return_item WHERE return_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            if (item.get("device_id") != null) {
                jdbc.update("""
                    UPDATE medical_device SET device_status = 'normal', warehouse_id = ?::uuid, updated_at = NOW()
                    WHERE id = ?::uuid
                    """, warehouseId, item.get("device_id"));
            }
        }
        jdbc.update("UPDATE device_return SET status = 'returned', doc_status = 'returned', updated_at = NOW() WHERE id = ?::uuid", id);
    }

    private void autoCompleteSharedReturn(UUID returnId) {
        var ret = jdbc.queryForList("SELECT * FROM shared_device_return WHERE id = ?::uuid", returnId);
        if (ret.isEmpty()) return;
        var loans = jdbc.queryForList("SELECT * FROM shared_device_loan WHERE id = ?::uuid", ret.get(0).get("loan_id"));
        if (loans.isEmpty()) return;
        Map<String, Object> loan = loans.get(0);
        java.time.Instant billingEnd = java.time.Instant.now();
        jdbc.update("""
            UPDATE shared_device_loan SET status='returned', return_time=NOW(),
            billing_end_at=?, updated_at=NOW() WHERE id=?::uuid
            """, java.sql.Timestamp.from(billingEnd), loan.get("id"));
        if (loan.get("device_id") != null && loan.get("from_dept_id") != null) {
            jdbc.update("UPDATE medical_device SET dept_id = ?::uuid, updated_at = NOW() WHERE id = ?::uuid",
                    loan.get("from_dept_id"), loan.get("device_id"));
        }
        var existingFee = jdbc.queryForList(
                "SELECT 1 FROM shared_device_fee WHERE loan_id = ?::uuid LIMIT 1", loan.get("id"));
        if (existingFee.isEmpty()) {
            java.time.Instant billingStart = toInstant(loan.get("billing_start_at"));
            if (billingStart == null) billingStart = toInstant(loan.get("approved_at"));
            java.math.BigDecimal unitPrice = loan.get("fee_unit_price") instanceof java.math.BigDecimal bd ? bd
                    : loan.get("fee_unit_price") != null
                    ? new java.math.BigDecimal(loan.get("fee_unit_price").toString()) : java.math.BigDecimal.ZERO;
            java.math.BigDecimal amount = com.meis.saas.common.shared.SharedFeeCalculator.calculate(
                    String.valueOf(loan.get("fee_mode")),
                    loan.get("fee_time_unit") != null ? String.valueOf(loan.get("fee_time_unit")) : null,
                    unitPrice, billingStart, billingEnd);
            jdbc.update("""
                INSERT INTO shared_device_fee (id, fee_no, loan_id, fee_amount, fee_date, paid_status, remark)
                VALUES (?::uuid,?,?::uuid,?,?,?,?)
                """, java.util.UUID.randomUUID(), "SF" + System.currentTimeMillis(), loan.get("id"), amount,
                    java.time.LocalDate.now(), "unpaid", "归还审批自动生成");
        }
    }

    private static java.time.Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.util.Date d) return d.toInstant();
        return null;
    }

    private void autoIssueOutbound(UUID id) {
        var items = jdbc.queryForList("SELECT device_id FROM device_outbound_item WHERE outbound_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            if (item.get("device_id") != null) {
                jdbc.update("""
                    UPDATE medical_device SET device_status = 'in_use', warehouse_id = NULL, updated_at = NOW()
                    WHERE id = ?::uuid
                    """, item.get("device_id"));
            }
        }
        jdbc.update("UPDATE device_outbound SET status = 'issued', doc_status = 'approved', updated_at = NOW() WHERE id = ?::uuid", id);
    }

    private void autoDisposeScrap(UUID id) {
        var row = jdbc.queryForList("SELECT device_id FROM device_scrap WHERE id = ?::uuid", id);
        if (!row.isEmpty() && row.get(0).get("device_id") != null) {
            jdbc.update("UPDATE medical_device SET device_status = 'scrap', updated_at = NOW() WHERE id = ?::uuid",
                    row.get(0).get("device_id"));
            jdbc.update("UPDATE device_scrap SET status = 'approved', updated_at = NOW() WHERE id = ?::uuid", id);
        }
    }

    private void createPurchaseAcceptance(UUID contractId) {
        var existing = jdbc.queryForList(
                "SELECT id FROM purchase_acceptance WHERE contract_id = ? LIMIT 1", contractId);
        if (!existing.isEmpty()) return;
        var contracts = jdbc.queryForList("SELECT * FROM purchase_contract WHERE id = ?", contractId);
        if (contracts.isEmpty()) return;
        Map<String, Object> contract = contracts.get(0);
        UUID id = UUID.randomUUID();
        Object chainNo = contract.get("business_chain_no");
        UUID projectId = toUuidOrNull(contract.get("project_id"));
        UUID supplierId = toUuidOrNull(contract.get("supplier_id"));
        jdbc.update("""
            INSERT INTO purchase_acceptance (id, acceptance_no, contract_id, project_id, supplier_id, acceptance_status, business_chain_no)
            VALUES (?, ?, ?, ?, ?, 'pending', ?)
            """,
                id, "AC" + System.currentTimeMillis(), contractId, projectId, supplierId, chainNo);
        jdbc.update("UPDATE purchase_contract SET acceptance_status = 'pending', updated_at = NOW() WHERE id = ?", contractId);
        // 尽量回写合同设备明细到验收设备明细（表可能尚未迁库时静默跳过）
        try {
            var items = jdbc.queryForList("""
                    SELECT device_name, specification, brand, quantity, unit_price, amount,
                           manufacturer_id, manufacturer_name, sort_order
                    FROM purchase_contract_item
                    WHERE contract_id = ? AND COALESCE(is_deleted, 0) = 0
                    ORDER BY sort_order ASC NULLS LAST, created_at ASC NULLS LAST
                    """, contractId);
            int order = 1;
            for (Map<String, Object> row : items) {
                if (row.get("device_name") == null || row.get("device_name").toString().isBlank()) continue;
                jdbc.update("""
                        INSERT INTO purchase_acceptance_device (
                            id, acceptance_id, device_name, specification, brand, quantity, unit_price, amount,
                            manufacturer_id, manufacturer_name, sort_order, is_deleted
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                        UUID.randomUUID(), id, row.get("device_name"), row.get("specification"), row.get("brand"),
                        row.get("quantity"), row.get("unit_price"), row.get("amount"),
                        toUuidOrNull(row.get("manufacturer_id")), row.get("manufacturer_name"),
                        row.getOrDefault("sort_order", order++));
            }
        } catch (Exception ignored) {
            // 老租户未迁 purchase_acceptance_device 时不影响合同审批通过
        }
    }

    private static UUID toUuidOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void markPaymentPaid(UUID paymentId) {
        var rows = jdbc.queryForList("SELECT contract_id FROM contract_payment WHERE id = ?::uuid", paymentId);
        if (rows.isEmpty()) return;
        jdbc.update("UPDATE contract_payment SET status = 'paid', updated_at = NOW() WHERE id = ?::uuid", paymentId);
        Object contractId = rows.get(0).get("contract_id");
        if (contractId == null) return;
        var contract = jdbc.queryForList("SELECT contract_amount FROM purchase_contract WHERE id = ?::uuid", contractId);
        if (contract.isEmpty()) return;
        double contractAmount = ((Number) contract.get(0).getOrDefault("contract_amount", 0)).doubleValue();
        var paid = jdbc.queryForList("""
            SELECT COALESCE(SUM(payment_amount), 0) AS paid FROM contract_payment
            WHERE contract_id = ?::uuid AND status = 'paid'
            """, contractId);
        double paidAmount = ((Number) paid.get(0).get("paid")).doubleValue();
        double progress = contractAmount > 0 ? Math.min(100, paidAmount / contractAmount * 100) : 0;
        jdbc.update("""
            UPDATE purchase_contract SET paid_amount = ?, payment_progress = ?, updated_at = NOW()
            WHERE id = ?::uuid
            """, paidAmount, progress, contractId);
        if (progress >= 99.9) {
            jdbc.update("UPDATE purchase_contract SET status = 'completed', updated_at = NOW() WHERE id = ?::uuid", contractId);
        }
    }

    public Map<String, Object> getInstance(UUID instanceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sys_approval_instance WHERE id = ?::uuid", instanceId);
        if (rows.isEmpty()) throw new BizException(404, "instance not found");
        return rows.get(0);
    }

    public Map<String, Object> getByBusiness(String businessType, UUID businessId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM sys_approval_instance WHERE business_type = ? AND business_id = ?::uuid ORDER BY created_at DESC LIMIT 1",
                businessType, businessId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
