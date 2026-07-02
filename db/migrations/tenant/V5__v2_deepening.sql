-- MEIS V2.0 deepening patch (tenant schema)

ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_scrap ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);
UPDATE device_scrap SET approval_status = status WHERE approval_status IS NULL;
UPDATE asset_transfer SET approval_status = status WHERE approval_status IS NULL;

-- migrate notification_message -> sys_notification (V1 schema uses notification_type)
INSERT INTO sys_notification (title, content, notification_type, is_read, created_at)
SELECT title, content, COALESCE(message_type, 'system'), COALESCE(is_read, false), created_at
FROM notification_message nm
WHERE NOT EXISTS (
    SELECT 1 FROM sys_notification sn WHERE sn.title = nm.title AND sn.created_at = nm.created_at
);

-- default approval flows
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('purchase_contract_default', '采购合同审批', 'purchase_contract'),
('asset_transfer_default', '资产流转审批', 'asset_transfer'),
('device_scrap_default', '设备报废审批', 'device_scrap'),
('device_outbound_default', '设备出库审批', 'device_outbound')
ON CONFLICT (flow_code) DO NOTHING;

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '科室主任', 'dept_admin', 0 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_contract_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 2, '装备部审核', 'equipment_head', 50000 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_contract_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 2);

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '装备部审核', 'equipment_head', 0 FROM sys_approval_flow f WHERE f.flow_code IN ('asset_transfer_default','device_scrap_default','device_outbound_default')
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

-- demo purchase plan seed
INSERT INTO purchase_plan (plan_code, plan_year, total_budget, justification, approval_status)
SELECT 'PP2026-001', 2026, 500000.00, '年度设备采购计划演示', 'draft'
WHERE NOT EXISTS (SELECT 1 FROM purchase_plan WHERE plan_code = 'PP2026-001');
