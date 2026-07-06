package com.meis.saas.common.workflow;

import com.meis.saas.common.exception.BizException;
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
        return jdbc.queryForList(
                "SELECT * FROM sys_approval_record WHERE instance_id = ?::uuid ORDER BY acted_at", instanceId);
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
            updateBusinessStatus(inst.get("business_type").toString(), UUID.fromString(inst.get("business_id").toString()), "rejected");
            return getInstance(instanceId);
        }

        List<Map<String, Object>> nextNodes = jdbc.queryForList(
                "SELECT * FROM sys_approval_node WHERE flow_id = ?::uuid AND node_order > ? ORDER BY node_order LIMIT 1",
                inst.get("flow_id"), nodeOrder);
        if (nextNodes.isEmpty()) {
            jdbc.update("UPDATE sys_approval_instance SET status = 'approved', updated_at = NOW() WHERE id = ?::uuid", instanceId);
            updateBusinessStatus(inst.get("business_type").toString(), UUID.fromString(inst.get("business_id").toString()), "approved");
        } else {
            int next = ((Number) nextNodes.get(0).get("node_order")).intValue();
            jdbc.update("UPDATE sys_approval_instance SET current_node_order = ?, updated_at = NOW() WHERE id = ?::uuid", next, instanceId);
        }
        return getInstance(instanceId);
    }

    private void updateBusinessStatus(String businessType, UUID businessId, String status) {
        switch (businessType) {
            case "purchase_plan" -> jdbc.update("UPDATE purchase_plan SET approval_status = ? WHERE id = ?::uuid", status, businessId);
            case "purchase_contract" -> jdbc.update("UPDATE purchase_contract SET approval_status = ? WHERE id = ?::uuid", status, businessId);
            case "device_scrap" -> {
                jdbc.update("UPDATE device_scrap SET approval_status = ?, status = ? WHERE id = ?::uuid", status, status, businessId);
                if ("approved".equals(status)) autoDisposeScrap(businessId);
            }
            case "asset_transfer" -> {
                jdbc.update("UPDATE asset_transfer SET approval_status = ?, status = ? WHERE id = ?::uuid", status, status, businessId);
                if ("approved".equals(status)) autoExecuteTransfer(businessId);
            }
            case "device_outbound" -> {
                jdbc.update("UPDATE device_outbound SET doc_status = ? WHERE id = ?::uuid", status, businessId);
                if ("approved".equals(status)) autoIssueOutbound(businessId);
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
        jdbc.update("UPDATE asset_transfer SET status = 'completed', updated_at = NOW() WHERE id = ?::uuid", id);
    }

    private void autoIssueOutbound(UUID id) {
        var items = jdbc.queryForList("SELECT device_id FROM device_outbound_item WHERE outbound_id = ?::uuid", id);
        for (Map<String, Object> item : items) {
            if (item.get("device_id") != null) {
                jdbc.update("UPDATE medical_device SET device_status = 'in_use', updated_at = NOW() WHERE id = ?::uuid",
                        item.get("device_id"));
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
