package com.meis.saas.system.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/approval-config")
@RequiredArgsConstructor
public class ApprovalFlowConfigController {
    private final JdbcTemplate jdbc;

    @GetMapping("/flows")
    public Result<List<Map<String, Object>>> listFlows() {
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM sys_approval_flow WHERE 1=1 " + SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_flow", null)
                        + " ORDER BY flow_code"));
    }

    @GetMapping("/flows/{flowId}")
    public Result<Map<String, Object>> getFlow(@PathVariable UUID flowId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM sys_approval_flow WHERE id = ?::uuid " + SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_flow", null),
                flowId);
        if (rows.isEmpty()) throw new BizException(404, "flow not found");
        Map<String, Object> flow = rows.get(0);
        flow.put("nodes", jdbc.queryForList(
                "SELECT * FROM sys_approval_node WHERE flow_id = ?::uuid " + SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_node", null)
                        + " ORDER BY node_order", flowId));
        return Result.ok(flow);
    }

    @PostMapping("/flows")
    @OperationLog(module = "system", description = "保存审批流程")
    public Result<Map<String, Object>> saveFlow(@RequestBody Map<String, Object> body) {
        boolean hasId = body.containsKey("id") && body.get("id") != null;
        UUID id = hasId ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = hasId && !jdbc.queryForList(
                "SELECT 1 FROM sys_approval_flow WHERE id = ?::uuid " + SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_flow", null),
                id).isEmpty();
        if (exists) {
            jdbc.update("""
                    UPDATE sys_approval_flow SET flow_name=?, business_type=?, is_active=?,
                    updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid
                    """, body.get("flow_name"), body.get("business_type"), body.getOrDefault("is_active", true),
                    SoftDeleteSupport.currentUserId(), id);
            return getFlow(id);
        }
        SoftDeleteSupport.applyInsertAudit(jdbc, "sys_approval_flow", body);
        var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "sys_approval_flow", body);
        if (softDeletedId.isPresent()) {
            id = UUID.fromString(softDeletedId.get());
            jdbc.update("""
                    UPDATE sys_approval_flow SET flow_code=?, flow_name=?, business_type=?, is_active=?,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, body.get("flow_code"), body.get("flow_name"), body.get("business_type"),
                    body.getOrDefault("is_active", true), SoftDeleteSupport.currentUserId(), id);
            return getFlow(id);
        }
        jdbc.update("""
                INSERT INTO sys_approval_flow (id, flow_code, flow_name, business_type, is_active, created_by, is_deleted)
                VALUES (?::uuid,?,?,?,?,?::uuid,?)
                """, id, body.get("flow_code"), body.get("flow_name"), body.get("business_type"),
                body.getOrDefault("is_active", true), SoftDeleteSupport.currentUserId(), 0);
        return getFlow(id);
    }

    @DeleteMapping("/flows/{flowId}")
    @OperationLog(module = "system", description = "删除审批流程")
    public Result<Void> deleteFlow(@PathVariable UUID flowId) {
        for (var row : jdbc.queryForList(
                "SELECT id FROM sys_approval_node WHERE flow_id = ?::uuid " + SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_node", null),
                flowId)) {
            SoftDeleteSupport.softDelete(jdbc, "sys_approval_node", String.valueOf(row.get("id")));
        }
        SoftDeleteSupport.softDelete(jdbc, "sys_approval_flow", flowId.toString());
        return Result.ok();
    }

    @PostMapping("/flows/{flowId}/nodes")
    @OperationLog(module = "system", description = "保存审批节点")
    public Result<Map<String, Object>> saveNode(@PathVariable UUID flowId, @RequestBody Map<String, Object> body) {
        boolean hasId = body.containsKey("id") && body.get("id") != null;
        UUID id = hasId ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = hasId && !jdbc.queryForList(
                "SELECT 1 FROM sys_approval_node WHERE id = ?::uuid " + SoftDeleteSupport.notDeletedClause(jdbc, "sys_approval_node", null),
                id).isEmpty();
        if (exists) {
            jdbc.update("""
                    UPDATE sys_approval_node SET node_order=?, node_name=?, approver_role=?, amount_threshold=?,
                    updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid
                    """, body.get("node_order"), body.get("node_name"), body.get("approver_role"),
                    body.get("amount_threshold"), SoftDeleteSupport.currentUserId(), id);
        } else {
            jdbc.update("""
                    INSERT INTO sys_approval_node (id, flow_id, node_order, node_name, approver_role, amount_threshold, created_by, is_deleted)
                    VALUES (?::uuid,?::uuid,?,?,?,?,?::uuid,?)
                    """, id, flowId, body.get("node_order"), body.get("node_name"), body.get("approver_role"),
                    body.get("amount_threshold"), SoftDeleteSupport.currentUserId(), 0);
        }
        return Result.ok(jdbc.queryForList("SELECT * FROM sys_approval_node WHERE id = ?::uuid", id).get(0));
    }

    @DeleteMapping("/nodes/{nodeId}")
    @OperationLog(module = "system", description = "删除审批节点")
    public Result<Void> deleteNode(@PathVariable UUID nodeId) {
        SoftDeleteSupport.softDelete(jdbc, "sys_approval_node", nodeId.toString());
        return Result.ok();
    }
}
