-- 模块6：计量管理 — 新表 + 种子（手工执行用）

CREATE TABLE IF NOT EXISTS metrology_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_code VARCHAR(30) NOT NULL UNIQUE,
    category_name VARCHAR(100) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS metrology_org (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_code VARCHAR(30) NOT NULL UNIQUE,
    org_name VARCHAR(200) NOT NULL,
    qualification_no VARCHAR(100),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(30),
    address VARCHAR(300),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS metrology_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code VARCHAR(30),
    template_name VARCHAR(200) NOT NULL,
    category_id UUID REFERENCES metrology_category(id),
    description TEXT,
    estimated_duration INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS metrology_template_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES metrology_template(id) ON DELETE CASCADE,
    item_code VARCHAR(30),
    item_name VARCHAR(200) NOT NULL,
    item_content TEXT,
    standard_value VARCHAR(200),
    tolerance_range VARCHAR(100),
    sort_order INTEGER DEFAULT 0,
    is_required BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS metrology_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_code VARCHAR(30),
    plan_name VARCHAR(200) NOT NULL,
    device_id UUID REFERENCES medical_device(id),
    template_id UUID REFERENCES metrology_template(id),
    category_id UUID REFERENCES metrology_category(id),
    org_id UUID REFERENCES metrology_org(id),
    cycle_days INTEGER,
    next_due_date DATE,
    last_calibrated_at DATE,
    assigned_inspector_id UUID REFERENCES sys_user(id),
    approval_status VARCHAR(20) DEFAULT 'draft',
    created_by UUID REFERENCES sys_user(id),
    approved_by UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'active',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS metrology_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_no VARCHAR(30) UNIQUE NOT NULL,
    plan_id UUID REFERENCES metrology_plan(id),
    template_id UUID REFERENCES metrology_template(id),
    category_id UUID REFERENCES metrology_category(id),
    org_id UUID REFERENCES metrology_org(id),
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

CREATE TABLE IF NOT EXISTS metrology_execution_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES metrology_execution(id) ON DELETE CASCADE,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    dept_id UUID REFERENCES department(id),
    plan_id UUID REFERENCES metrology_plan(id),
    certificate_no VARCHAR(100),
    certificate_url VARCHAR(500),
    cost DECIMAL(10,2),
    measurement_data JSONB DEFAULT '{}'::jsonb,
    status VARCHAR(20) DEFAULT 'pending',
    overall_result VARCHAR(20),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS metrology_execution_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_item_id UUID NOT NULL REFERENCES metrology_execution_item(id) ON DELETE CASCADE,
    template_item_id UUID REFERENCES metrology_template_item(id),
    item_name VARCHAR(200) NOT NULL,
    item_content TEXT,
    result_value VARCHAR(500),
    result_status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO metrology_category (category_code, category_name, sort_order, description) VALUES
('FORCE', '力学计量', 1, '压力、力值等'),
('ELECTRIC', '电学计量', 2, '电压、电流、电阻等'),
('TEMP', '热学计量', 3, '温度、湿度等'),
('LENGTH', '长度计量', 4, '尺寸、量具等')
ON CONFLICT (category_code) DO NOTHING;
