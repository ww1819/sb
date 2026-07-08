package com.meis.saas.common.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ApprovalFlowService {
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listFlows() {
        return jdbc.queryForList("SELECT * FROM sys_approval_flow ORDER BY business_type");
    }

    public List<Map<String, Object>> listNodes(UUID flowId) {
        return jdbc.queryForList(
                "SELECT * FROM sys_approval_node WHERE flow_id = ?::uuid ORDER BY node_order", flowId);
    }

    public Map<String, Object> getFlowByBusinessType(String businessType) {
        List<Map<String, Object>> flows = jdbc.queryForList(
                "SELECT * FROM sys_approval_flow WHERE business_type = ? AND is_active = true LIMIT 1",
                businessType);
        return flows.isEmpty() ? null : flows.get(0);
    }
}
