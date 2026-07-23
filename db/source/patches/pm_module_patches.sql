-- 模块12：预防性维护 — 租户补丁（幂等）

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS pm_type (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_code VARCHAR(30) NOT NULL UNIQUE,
    type_name VARCHAR(100) NOT NULL,
    risk_level VARCHAR(20) DEFAULT 'medium',
    sort_order INTEGER DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pm_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code VARCHAR(30),
    template_name VARCHAR(200) NOT NULL,
    pm_type VARCHAR(30),
    pm_type_id UUID REFERENCES pm_type(id),
    category_id UUID REFERENCES medical_device_category(id),
    items JSONB NOT NULL DEFAULT '[]'::jsonb,
    description TEXT,
    estimated_duration INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pm_template_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES pm_template(id) ON DELETE CASCADE,
    item_code VARCHAR(30),
    item_name VARCHAR(200) NOT NULL,
    item_content TEXT,
    standard_value VARCHAR(200),
    check_method VARCHAR(200),
    sort_order INTEGER DEFAULT 0,
    is_required BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pm_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_code VARCHAR(30),
    plan_name VARCHAR(200),
    device_id UUID REFERENCES medical_device(id),
    template_id UUID REFERENCES pm_template(id),
    pm_type VARCHAR(30),
    pm_type_id UUID REFERENCES pm_type(id),
    cycle_type VARCHAR(20) NOT NULL DEFAULT 'month',
    cycle_value INTEGER,
    cycle_days INTEGER,
    next_due_date DATE NOT NULL,
    last_maintained_at DATE,
    reminder_days_before INTEGER DEFAULT 7,
    assigned_engineer_id UUID REFERENCES engineer(id),
    dept_id UUID REFERENCES department(id),
    status VARCHAR(20) DEFAULT 'active',
    approval_status VARCHAR(20) DEFAULT 'draft',
    created_by UUID REFERENCES sys_user(id),
    approved_by UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pm_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_no VARCHAR(30) UNIQUE NOT NULL,
    plan_id UUID REFERENCES pm_plan(id),
    template_id UUID REFERENCES pm_template(id),
    pm_type_id UUID REFERENCES pm_type(id),
    planned_date DATE,
    assigned_engineer_id UUID REFERENCES engineer(id),
    executor_id UUID REFERENCES sys_user(id),
    execute_start_time TIMESTAMP WITH TIME ZONE,
    execute_end_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'pending',
    created_by UUID REFERENCES sys_user(id),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pm_execution_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES pm_execution(id) ON DELETE CASCADE,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    dept_id UUID REFERENCES department(id),
    plan_id UUID REFERENCES pm_plan(id),
    status VARCHAR(20) DEFAULT 'pending',
    overall_result VARCHAR(20),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pm_execution_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_item_id UUID NOT NULL REFERENCES pm_execution_item(id) ON DELETE CASCADE,
    template_item_id UUID REFERENCES pm_template_item(id),
    item_name VARCHAR(200) NOT NULL,
    item_content TEXT,
    result_value VARCHAR(500),
    result_status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_pm_device BOOLEAN DEFAULT FALSE;

INSERT INTO pm_type (type_code, type_name, risk_level, sort_order, description) VALUES
('PM_ROUTINE', '例行预防维护', 'low', 1, '常规预防性维护'),
('PM_CRITICAL', '关键设备PM', 'high', 2, '生命支持/高风险设备'),
('PM_SEASONAL', '季节性维护', 'medium', 3, '换季或环境变化维护')
ON CONFLICT (type_code) DO NOTHING;

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('pm_risk_level', 'low', '低', 'low', 1),
('pm_risk_level', 'medium', '中', 'medium', 2),
('pm_risk_level', 'high', '高', 'high', 3),
('pm_risk_level', 'critical', '极高', 'critical', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
