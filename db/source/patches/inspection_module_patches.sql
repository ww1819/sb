-- 模块5：巡检管理 — 新表 + 补列（手工执行用）

CREATE TABLE IF NOT EXISTS inspection_type (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_code VARCHAR(30) NOT NULL UNIQUE,
    type_name VARCHAR(100) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inspection_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code VARCHAR(30),
    template_name VARCHAR(200) NOT NULL,
    inspection_type_id UUID REFERENCES inspection_type(id),
    category_id UUID REFERENCES medical_device_category(id),
    description TEXT,
    estimated_duration INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inspection_template_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES inspection_template(id) ON DELETE CASCADE,
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

ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS template_id UUID;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS inspection_type_id UUID;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS cycle_days INTEGER;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS next_due_date DATE;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS last_inspected_at DATE;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS assigned_inspector_id UUID;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS remark TEXT;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS inspection_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_no VARCHAR(30) UNIQUE NOT NULL,
    plan_id UUID REFERENCES inspection_plan(id),
    template_id UUID REFERENCES inspection_template(id),
    inspection_type_id UUID REFERENCES inspection_type(id),
    planned_date DATE,
    assigned_inspector_id UUID REFERENCES sys_user(id),
    executor_id UUID REFERENCES sys_user(id),
    execute_start_time TIMESTAMP WITH TIME ZONE,
    execute_end_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'pending',
    created_by UUID REFERENCES sys_user(id),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inspection_execution_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES inspection_execution(id) ON DELETE CASCADE,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    dept_id UUID REFERENCES department(id),
    plan_id UUID REFERENCES inspection_plan(id),
    status VARCHAR(20) DEFAULT 'pending',
    overall_result VARCHAR(20),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inspection_execution_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_item_id UUID NOT NULL REFERENCES inspection_execution_item(id) ON DELETE CASCADE,
    template_item_id UUID REFERENCES inspection_template_item(id),
    item_name VARCHAR(200) NOT NULL,
    item_content TEXT,
    result_value VARCHAR(500),
    result_status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO inspection_type (type_code, type_name, sort_order, description) VALUES
('ROUTINE', '日常巡检', 1, '每日或每周例行巡检'),
('SAFETY', '安全巡检', 2, '安全隐患排查'),
('DEPT', '科室巡检', 3, '科室责任区巡检'),
('SPECIAL', '专项巡检', 4, '专项检查巡检')
ON CONFLICT (type_code) DO NOTHING;
