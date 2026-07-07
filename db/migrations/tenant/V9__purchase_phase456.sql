-- MEIS V9: 采购456阶段 — 付款回写、追溯、厂商菜单、审批流与字典

-- 合同付款进度
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS paid_amount DECIMAL(15,2) DEFAULT 0;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS payment_progress DECIMAL(5,2) DEFAULT 0;

-- 付款财务字段
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS finance_auditor_id UUID REFERENCES sys_user(id);
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS finance_audit_date DATE;
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS invoice_type VARCHAR(30);
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(15,2);
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS voucher_no VARCHAR(50);

-- 入库单追溯
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS acceptance_id UUID;
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS project_id UUID REFERENCES purchase_project(id);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS plan_id UUID REFERENCES purchase_plan(id);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS trace_no VARCHAR(60);

-- 台账关联采购合同
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS contract_id UUID REFERENCES purchase_contract(id);

-- 项目/验收审批状态
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE purchase_acceptance ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';

-- 采购方式字典补全
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('purchase_method', 'competitive_negotiation', '竞争性谈判', 'competitive_negotiation', 3),
('purchase_method', 'single_source', '单一来源', 'single_source', 4),
('purchase_method', 'framework', '框架协议', 'framework', 5),
('invoice_type', 'special', '增值税专用发票', 'special', 1),
('invoice_type', 'normal', '增值税普通发票', 'normal', 2),
('invoice_type', 'electronic', '电子发票', 'electronic', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- 采购项目审批流
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('purchase_project_default', '采购项目审批', 'purchase_project')
ON CONFLICT (flow_code) DO NOTHING;

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '科室主任', 'dept_admin', 0 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_project_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 2, '装备部审核', 'equipment_head', 50000 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_project_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 2);

-- 安装验收审批流
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('purchase_acceptance_default', '安装验收审批', 'purchase_acceptance')
ON CONFLICT (flow_code) DO NOTHING;

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '设备科验收', 'equipment_head', 0 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_acceptance_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 2, '临床科室确认', 'dept_admin', 0 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_acceptance_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 2);

-- 台账关联采购合同
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS contract_id UUID REFERENCES purchase_contract(id);

-- 采购科角色补看板菜单
UPDATE sys_role SET permissions = '{"menus":["mod_purchase","purchase_plan","purchase_project","purchase_contract","purchase_supplier","purchase_category","purchase_acceptance","purchase_manufacturer","purchase_dashboard"],"buttons":[],"dataScope":"dept"}'::jsonb
WHERE role_code = 'purchase_staff';
