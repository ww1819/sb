-- 模块4：保养管理 — 新表 + 补列（手工执行用，镜像 V1 + R__）

CREATE TABLE IF NOT EXISTS maintenance_level (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    level_code VARCHAR(30) NOT NULL UNIQUE,
    level_name VARCHAR(100) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE maintenance_template ADD COLUMN IF NOT EXISTS template_code VARCHAR(30);
ALTER TABLE maintenance_template ADD COLUMN IF NOT EXISTS maintenance_level_id UUID;
ALTER TABLE maintenance_template ADD COLUMN IF NOT EXISTS description TEXT;

CREATE TABLE IF NOT EXISTS maintenance_template_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES maintenance_template(id) ON DELETE CASCADE,
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

ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS cycle_days INTEGER;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS maintenance_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_no VARCHAR(30) UNIQUE NOT NULL,
    plan_id UUID REFERENCES maintenance_plan(id),
    template_id UUID REFERENCES maintenance_template(id),
    maintenance_level_id UUID REFERENCES maintenance_level(id),
    planned_date DATE,
    assigned_engineer_id UUID REFERENCES engineer(id),
    executor_id UUID REFERENCES engineer(id),
    execute_start_time TIMESTAMP WITH TIME ZONE,
    execute_end_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'pending',
    created_by UUID REFERENCES sys_user(id),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS maintenance_execution_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES maintenance_execution(id) ON DELETE CASCADE,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    dept_id UUID REFERENCES department(id),
    plan_id UUID REFERENCES maintenance_plan(id),
    status VARCHAR(20) DEFAULT 'pending',
    overall_result VARCHAR(20),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS maintenance_execution_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_item_id UUID NOT NULL REFERENCES maintenance_execution_item(id) ON DELETE CASCADE,
    template_item_id UUID REFERENCES maintenance_template_item(id),
    item_name VARCHAR(200) NOT NULL,
    item_content TEXT,
    result_value VARCHAR(500),
    result_status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO maintenance_level (level_code, level_name, sort_order, description) VALUES
('L1', '日常保养', 1, '每日或每周例行保养'),
('L2', '一级保养', 2, '月度基础保养'),
('L3', '二级保养', 3, '季度深度保养'),
('L4', '三级保养', 4, '年度全面保养')
ON CONFLICT (level_code) DO NOTHING;
