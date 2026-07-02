-- MEIS V2.0 tenant schema extensions

-- device outbound
CREATE TABLE IF NOT EXISTS device_outbound (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    outbound_no VARCHAR(30) UNIQUE NOT NULL,
    outbound_type VARCHAR(30) DEFAULT 'requisition',
    dept_id UUID REFERENCES department(id),
    receiver_id UUID REFERENCES sys_user(id),
    outbound_date DATE,
    purpose TEXT,
    is_urgent BOOLEAN DEFAULT FALSE,
    doc_status VARCHAR(20) DEFAULT 'draft',
    operator_id UUID REFERENCES sys_user(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS device_outbound_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    outbound_id UUID NOT NULL REFERENCES device_outbound(id) ON DELETE CASCADE,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(50),
    device_name VARCHAR(200),
    quantity INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- approval workflow
CREATE TABLE IF NOT EXISTS sys_approval_flow (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_code VARCHAR(50) UNIQUE NOT NULL,
    flow_name VARCHAR(100) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_approval_node (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id UUID NOT NULL REFERENCES sys_approval_flow(id) ON DELETE CASCADE,
    node_order INTEGER NOT NULL,
    node_name VARCHAR(100) NOT NULL,
    approver_role VARCHAR(50),
    amount_threshold NUMERIC(18,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_approval_instance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id UUID REFERENCES sys_approval_flow(id),
    business_type VARCHAR(50) NOT NULL,
    business_id UUID NOT NULL,
    business_no VARCHAR(50),
    title VARCHAR(200),
    applicant_id UUID REFERENCES sys_user(id),
    status VARCHAR(20) DEFAULT 'pending',
    current_node_order INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_approval_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id UUID NOT NULL REFERENCES sys_approval_instance(id) ON DELETE CASCADE,
    node_order INTEGER NOT NULL,
    approver_id UUID REFERENCES sys_user(id),
    action VARCHAR(20) NOT NULL,
    comment TEXT,
    acted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- inspection (mobile)
CREATE TABLE IF NOT EXISTS inspection_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_name VARCHAR(200) NOT NULL,
    device_id UUID REFERENCES medical_device(id),
    inspection_type VARCHAR(50),
    plan_date DATE,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inspection_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID REFERENCES inspection_plan(id),
    device_id UUID REFERENCES medical_device(id),
    inspector_id UUID REFERENCES sys_user(id),
    inspection_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    result VARCHAR(20),
    remark TEXT,
    status VARCHAR(20) DEFAULT 'completed'
);

CREATE TABLE IF NOT EXISTS inspection_record_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id UUID NOT NULL REFERENCES inspection_record(id) ON DELETE CASCADE,
    item_name VARCHAR(200),
    check_result VARCHAR(20),
    remark TEXT
);

-- spare part transactions
CREATE TABLE IF NOT EXISTS spare_part_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    spare_part_id UUID REFERENCES spare_part(id),
    txn_type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(18,2),
    workorder_id UUID REFERENCES repair_workorder(id),
    operator_id UUID REFERENCES sys_user(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- integration sync task
CREATE TABLE IF NOT EXISTS integration_sync_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    system_code VARCHAR(20) NOT NULL,
    task_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'pending',
    payload JSONB,
    result JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP WITH TIME ZONE
);

-- unify notification (use sys_notification if exists, ensure columns)
CREATE TABLE IF NOT EXISTS sys_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    content TEXT,
    message_type VARCHAR(50) DEFAULT 'system',
    priority VARCHAR(20) DEFAULT 'normal',
    is_read BOOLEAN DEFAULT FALSE,
    user_id UUID REFERENCES sys_user(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- dict seeds V2.0
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('device_status', 'normal', '正常', 'normal', 1),
('device_status', 'in_use', '在用', 'in_use', 2),
('device_status', 'maintenance', '维修中', 'maintenance', 3),
('device_status', 'scrap', '已报废', 'scrap', 4),
('risk_level', 'high', '高风险', 'high', 1),
('risk_level', 'medium', '中风险', 'medium', 2),
('risk_level', 'low', '低风险', 'low', 3),
('urgency', 'urgent', '紧急', 'urgent', 1),
('urgency', 'high', '高', 'high', 2),
('urgency', 'normal', '普通', 'normal', 3),
('urgency', 'low', '低', 'low', 4),
('purchase_method', 'public_bidding', '公开招标', 'public_bidding', 1),
('purchase_method', 'inquiry', '询价采购', 'inquiry', 2),
('approval_status', 'draft', '草稿', 'draft', 1),
('approval_status', 'pending', '审批中', 'pending', 2),
('approval_status', 'approved', '已通过', 'approved', 3),
('approval_status', 'rejected', '已驳回', 'rejected', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- role templates (tenant admin configures permissions within platform grant)
INSERT INTO sys_role (role_code, role_name, permissions) VALUES
('tenant_admin', '租户管理员', '{"menus":["*"],"buttons":["*"],"dataScope":"all"}'::jsonb),
('hospital_leader', '院级领导', '{"menus":[],"buttons":[],"dataScope":"all"}'::jsonb),
('equipment_head', '装备部负责人', '{"menus":[],"buttons":[],"dataScope":"all"}'::jsonb),
('purchase_staff', '采购科人员', '{"menus":["mod_purchase","purchase_plan","purchase_project","purchase_contract"],"buttons":[],"dataScope":"dept"}'::jsonb),
('warehouse_keeper', '库管员', '{"menus":["mod_asset","asset_entry","asset_outbound","asset_inventory"],"buttons":[],"dataScope":"dept"}'::jsonb),
('engineer', '维修工程师', '{"menus":["repair_workorder","maintain_plan","maintain_record"],"buttons":[],"dataScope":"self"}'::jsonb),
('dept_admin', '科室设备管理员', '{"menus":["asset_device","repair_workorder","asset_transfer"],"buttons":[],"dataScope":"dept"}'::jsonb),
('clinical_user', '临床使用人员', '{"menus":["repair_workorder"],"buttons":[],"dataScope":"self"}'::jsonb)
ON CONFLICT (role_code) DO NOTHING;

-- default purchase approval flow
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('purchase_plan_default', '采购计划审批', 'purchase_plan')
ON CONFLICT (flow_code) DO NOTHING;

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '科室主任', 'dept_admin', 0 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_plan_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 2, '装备部审核', 'equipment_head', 10000 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_plan_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 2);

-- assign admin role to demo admin user
UPDATE sys_user SET role_ids = ARRAY(SELECT id FROM sys_role WHERE role_code IN ('admin', 'tenant_admin') LIMIT 1)
WHERE username = 'admin' AND (role_ids IS NULL OR role_ids = '{}');
UPDATE sys_role SET permissions = '{"menus":["*"],"buttons":["*"],"dataScope":"all"}'::jsonb
WHERE role_code = 'admin' AND permissions::text = '["*"]';
