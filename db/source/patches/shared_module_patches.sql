-- 模块11：公用设备借调 — 租户补丁（幂等）

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS shared_device (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID REFERENCES medical_device(id) UNIQUE,
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    owner_dept_id UUID REFERENCES department(id),
    location VARCHAR(200),
    fee_standard DECIMAL(12,2) DEFAULT 0,
    availability_status VARCHAR(20) DEFAULT 'available',
    is_active BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS shared_device_loan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_no VARCHAR(30) UNIQUE NOT NULL,
    device_id UUID REFERENCES medical_device(id),
    shared_device_id UUID REFERENCES shared_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    from_dept_id UUID REFERENCES department(id),
    to_dept_id UUID REFERENCES department(id),
    applicant_id UUID REFERENCES sys_user(id),
    loan_start DATE,
    loan_end DATE,
    fee_standard DECIMAL(12,2),
    reason TEXT,
    status VARCHAR(20) DEFAULT 'draft',
    approval_status VARCHAR(20) DEFAULT 'draft',
    approved_by UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    loan_time TIMESTAMP WITH TIME ZONE,
    return_time TIMESTAMP WITH TIME ZONE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS shared_device_return (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_no VARCHAR(30) UNIQUE NOT NULL,
    loan_id UUID REFERENCES shared_device_loan(id),
    device_id UUID REFERENCES medical_device(id),
    return_date DATE,
    condition_desc TEXT,
    applicant_id UUID REFERENCES sys_user(id),
    status VARCHAR(20) DEFAULT 'pending',
    approval_status VARCHAR(20) DEFAULT 'pending',
    approved_by UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS shared_device_fee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_no VARCHAR(30) UNIQUE NOT NULL,
    loan_id UUID REFERENCES shared_device_loan(id),
    fee_amount DECIMAL(12,2) NOT NULL,
    fee_date DATE NOT NULL,
    paid_status VARCHAR(20) DEFAULT 'unpaid',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_shared_device BOOLEAN DEFAULT FALSE;

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('shared_availability', 'available', '可借', 'available', 1),
('shared_availability', 'on_loan', '借出中', 'on_loan', 2),
('shared_availability', 'maintenance', '维护中', 'maintenance', 3),
('loan_status', 'draft', '草稿', 'draft', 1),
('loan_status', 'pending', '待审批', 'pending', 2),
('loan_status', 'approved', '已审批', 'approved', 3),
('loan_status', 'on_loan', '借出中', 'on_loan', 4),
('loan_status', 'returned', '已归还', 'returned', 5),
('loan_status', 'rejected', '已驳回', 'rejected', 6),
('return_status', 'pending', '待审批', 'pending', 1),
('return_status', 'approved', '已审批', 'approved', 2),
('return_status', 'rejected', '已驳回', 'rejected', 3),
('paid_status', 'unpaid', '未收费', 'unpaid', 1),
('paid_status', 'paid', '已收费', 'paid', 2),
('paid_status', 'waived', '已减免', 'waived', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('shared_loan_default', '公用设备借调审批', 'shared_device_loan'),
('shared_return_default', '公用设备归还审批', 'shared_device_return')
ON CONFLICT (flow_code) DO NOTHING;

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '装备部审核', 'equipment_head', 0 FROM sys_approval_flow f
WHERE f.flow_code = 'shared_loan_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '装备部审核', 'equipment_head', 0 FROM sys_approval_flow f
WHERE f.flow_code = 'shared_return_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);
