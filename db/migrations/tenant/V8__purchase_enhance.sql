-- MEIS V8: 采购医院标准字段 + 安装验收表 + 付款审批 + 角色权限

-- 采购计划扩展
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS plan_type VARCHAR(20) DEFAULT 'annual';
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS fund_source VARCHAR(30);

-- 计划明细扩展
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS use_dept_id UUID REFERENCES department(id);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS is_imported BOOLEAN DEFAULT false;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS registration_no VARCHAR(100);

-- 采购项目扩展
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS argument_report_url VARCHAR(500);
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS budget_amount DECIMAL(15,2);

-- 采购合同扩展
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS delivery_deadline DATE;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS acceptance_report_url VARCHAR(500);

-- 付款明细审批状态
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';

-- 入库单关联供应商
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS supplier_id UUID REFERENCES supplier(id);

-- 安装验收表
CREATE TABLE IF NOT EXISTS purchase_acceptance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    acceptance_no VARCHAR(30) UNIQUE NOT NULL,
    contract_id UUID REFERENCES purchase_contract(id),
    project_id UUID REFERENCES purchase_project(id),
    supplier_id UUID REFERENCES supplier(id),
    acceptance_date DATE,
    acceptance_status VARCHAR(20) DEFAULT 'pending',
    quality_check_passed BOOLEAN,
    quality_checker_id UUID REFERENCES sys_user(id),
    quality_check_date DATE,
    quality_check_report_url VARCHAR(500),
    installation_completed BOOLEAN,
    installer_id UUID REFERENCES sys_user(id),
    installation_date DATE,
    installation_report_url VARCHAR(500),
    clinical_checker_id UUID REFERENCES sys_user(id),
    argument_summary TEXT,
    report_url VARCHAR(500),
    remark TEXT,
    entry_id UUID REFERENCES device_entry(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_purchase_acceptance_contract ON purchase_acceptance(contract_id);
CREATE INDEX IF NOT EXISTS idx_purchase_acceptance_status ON purchase_acceptance(acceptance_status);

-- 字典
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('plan_type', 'annual', '年度计划', 'annual', 1),
('plan_type', 'supplement', '增补计划', 'supplement', 2),
('plan_type', 'emergency', '应急采购', 'emergency', 3),
('fund_source', 'fiscal', '财政资金', 'fiscal', 1),
('fund_source', 'self', '自筹资金', 'self', 2),
('fund_source', 'research', '科研经费', 'research', 3),
('fund_source', 'donation', '捐赠', 'donation', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- 付款审批流
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('contract_payment_default', '合同付款审批', 'contract_payment')
ON CONFLICT (flow_code) DO NOTHING;

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '科室主任', 'dept_admin', 0 FROM sys_approval_flow f WHERE f.flow_code = 'contract_payment_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 2, '装备部审核', 'equipment_head', 10000 FROM sys_approval_flow f WHERE f.flow_code = 'contract_payment_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 2);

-- 采购科角色补菜单
UPDATE sys_role SET permissions = '{"menus":["mod_purchase","purchase_plan","purchase_project","purchase_contract","purchase_supplier","purchase_category","purchase_acceptance"],"buttons":[],"dataScope":"dept"}'::jsonb
WHERE role_code = 'purchase_staff';
