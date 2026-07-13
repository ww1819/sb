-- 模块8：库房管理 — 退货表与库房关联补列（幂等）

ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS from_warehouse_id UUID;
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS to_warehouse_id UUID;
ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS warehouse_id UUID;

CREATE TABLE IF NOT EXISTS device_return (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_no VARCHAR(30) UNIQUE NOT NULL,
    outbound_id UUID REFERENCES device_outbound(id),
    warehouse_id UUID REFERENCES warehouse(id),
    dept_id UUID REFERENCES department(id),
    returner_id UUID REFERENCES sys_user(id),
    return_date DATE,
    return_type VARCHAR(20) DEFAULT 'unused',
    reason TEXT,
    doc_status VARCHAR(20) DEFAULT 'draft',
    status VARCHAR(20) DEFAULT 'draft',
    approval_status VARCHAR(20) DEFAULT 'draft',
    operator_id UUID REFERENCES sys_user(id),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS device_return_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id UUID NOT NULL REFERENCES device_return(id) ON DELETE CASCADE,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(50),
    device_name VARCHAR(200),
    quantity INTEGER DEFAULT 1,
    condition_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('return_type', 'unused', '未使用退回', 'unused', 1),
('return_type', 'quality', '质量问题', 'quality', 2),
('return_type', 'damage', '损坏退回', 'damage', 3),
('return_type', 'other', '其他', 'other', 4),
('return_status', 'draft', '草稿', 'draft', 1),
('return_status', 'returned', '已退库', 'returned', 2),
('transfer_type', 'warehouse', '库房间调拨', 'warehouse', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('device_return_default', '设备退货审批', 'device_return')
ON CONFLICT (flow_code) DO NOTHING;

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '装备部审核', 'equipment_head', 0 FROM sys_approval_flow f WHERE f.flow_code = 'device_return_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);
