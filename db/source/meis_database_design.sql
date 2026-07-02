-- ================================================================================
-- 医院设备固定资产管理系统 (MEIS) - PostgreSQL 数据库设计
-- 版本: V1.0
-- 日期: 2026-06-23
-- 数据库: PostgreSQL 15+
-- ================================================================================

-- 启用扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ================================================================================
-- 1. 基础数据表
-- ================================================================================

-- 1.1 院区表
CREATE TABLE campus (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    campus_code VARCHAR(1) UNIQUE NOT NULL,
    campus_name VARCHAR(100) NOT NULL,
    address VARCHAR(500),
    contact_phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.2 建筑物表
CREATE TABLE building (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    campus_id UUID REFERENCES campus(id),
    building_code VARCHAR(1) UNIQUE NOT NULL,
    building_name VARCHAR(100) NOT NULL,
    floor_count INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.3 科室表
CREATE TABLE department (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dept_code VARCHAR(3) UNIQUE NOT NULL,
    dept_name VARCHAR(100) NOT NULL,
    parent_id UUID REFERENCES department(id),
    campus_id UUID REFERENCES campus(id),
    building_id UUID REFERENCES building(id),
    floor_number INTEGER,
    room_number VARCHAR(20),
    manager_id UUID,
    contact_phone VARCHAR(20),
    is_clinical BOOLEAN DEFAULT FALSE,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.4 用户表
CREATE TABLE sys_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    employee_no VARCHAR(20) UNIQUE,
    phone VARCHAR(20),
    email VARCHAR(100),
    dept_id UUID REFERENCES department(id),
    role_ids UUID[],
    avatar_url VARCHAR(500),
    last_login_at TIMESTAMP WITH TIME ZONE,
    last_login_ip VARCHAR(45),
    is_locked BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.5 角色表
CREATE TABLE sys_role (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_code VARCHAR(50) UNIQUE NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description TEXT,
    permissions JSONB,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.6 操作日志表
CREATE TABLE sys_operation_log (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES sys_user(id),
    operation_type VARCHAR(50) NOT NULL,
    module_name VARCHAR(100),
    operation_desc TEXT,
    request_method VARCHAR(10),
    request_url VARCHAR(500),
    request_params JSONB,
    response_result JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    execution_time INTEGER,
    status VARCHAR(20),
    error_msg TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_op_log_user ON sys_operation_log(user_id);
CREATE INDEX idx_op_log_time ON sys_operation_log(created_at DESC);

-- ================================================================================
-- 2. 医疗器械分类与供应商
-- ================================================================================

-- 2.1 医疗器械分类目录
CREATE TABLE medical_device_category (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    category_code VARCHAR(6) UNIQUE NOT NULL,
    category_name VARCHAR(200) NOT NULL,
    parent_code VARCHAR(6),
    level INTEGER NOT NULL,
    full_path VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_category_parent ON medical_device_category(parent_code);

-- 2.2 供应商表
CREATE TABLE supplier (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    supplier_code VARCHAR(20) UNIQUE NOT NULL,
    supplier_name VARCHAR(200) NOT NULL,
    unified_social_credit_code VARCHAR(18),
    legal_representative VARCHAR(50),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    address TEXT,
    bank_account VARCHAR(50),
    bank_name VARCHAR(100),
    qualification_files JSONB,
    rating INTEGER,
    is_authorized BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.3 生产厂商表
CREATE TABLE manufacturer (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    manufacturer_code VARCHAR(20) UNIQUE NOT NULL,
    manufacturer_name VARCHAR(200) NOT NULL,
    country VARCHAR(50),
    is_domestic BOOLEAN,
    contact_phone VARCHAR(20),
    website VARCHAR(200),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================================
-- 3. 采购管理模块
-- ================================================================================

-- 3.1 采购计划表
CREATE TABLE purchase_plan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_code VARCHAR(30) UNIQUE NOT NULL,
    plan_year INTEGER NOT NULL,
    dept_id UUID REFERENCES department(id),
    applicant_id UUID REFERENCES sys_user(id),
    total_budget DECIMAL(15,2),
    justification TEXT,
    approval_status VARCHAR(20) DEFAULT 'draft',
    approved_by UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    remark TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3.2 采购计划明细表
CREATE TABLE purchase_plan_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id UUID REFERENCES purchase_plan(id) ON DELETE CASCADE,
    device_name VARCHAR(200) NOT NULL,
    category_id UUID REFERENCES medical_device_category(id),
    quantity INTEGER NOT NULL,
    estimated_price DECIMAL(15,2),
    total_price DECIMAL(15,2),
    specification TEXT,
    justification TEXT,
    priority INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3.3 采购项目表
CREATE TABLE purchase_project (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_code VARCHAR(30) UNIQUE NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    plan_id UUID REFERENCES purchase_plan(id),
    purchase_method VARCHAR(50),
    supplier_id UUID REFERENCES supplier(id),
    total_amount DECIMAL(15,2),
    bid_open_date DATE,
    award_date DATE,
    status VARCHAR(20) DEFAULT 'planning',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3.4 采购合同表
CREATE TABLE purchase_contract (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_code VARCHAR(30) UNIQUE NOT NULL,
    contract_name VARCHAR(200),
    project_id UUID REFERENCES purchase_project(id),
    supplier_id UUID REFERENCES supplier(id),
    sign_date DATE,
    start_date DATE,
    end_date DATE,
    contract_amount DECIMAL(15,2),
    warranty_period INTEGER,
    contract_file_url VARCHAR(500),
    payment_terms TEXT,
    status VARCHAR(20) DEFAULT 'active',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3.5 合同付款记录表
CREATE TABLE contract_payment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID REFERENCES purchase_contract(id),
    payment_no VARCHAR(30) UNIQUE NOT NULL,
    payment_stage VARCHAR(50),
    payment_amount DECIMAL(15,2),
    payment_date DATE,
    invoice_no VARCHAR(50),
    invoice_url VARCHAR(500),
    payee_account VARCHAR(50),
    status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================================
-- 4. 资产管理模块
-- ================================================================================

-- 4.1 设备档案主表
CREATE TABLE medical_device (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- 唯一编码
    device_code VARCHAR(20) UNIQUE NOT NULL,
    
    -- 基本信息
    device_name VARCHAR(200) NOT NULL,
    brand VARCHAR(100),
    model VARCHAR(100),
    serial_number VARCHAR(100),
    category_id UUID REFERENCES medical_device_category(id),
    
    -- 来源信息
    manufacturer_id UUID REFERENCES manufacturer(id),
    supplier_id UUID REFERENCES supplier(id),
    country_of_origin VARCHAR(50),
    is_imported BOOLEAN,
    
    -- 财务信息
    original_value DECIMAL(15,2),
    net_value DECIMAL(15,2),
    depreciation_years INTEGER,
    monthly_depreciation DECIMAL(15,2),
    accumulated_depreciation DECIMAL(15,2) DEFAULT 0,
    financial_code VARCHAR(30),
    
    -- 位置信息
    campus_id UUID REFERENCES campus(id),
    building_id UUID REFERENCES building(id),
    dept_id UUID REFERENCES department(id),
    location_detail VARCHAR(200),
    
    -- 时间信息
    purchase_date DATE,
    acceptance_date DATE,
    enable_date DATE,
    warranty_end_date DATE,
    
    -- 状态信息
    device_status VARCHAR(20) DEFAULT 'normal',
    risk_level VARCHAR(20),
    is_life_support BOOLEAN DEFAULT FALSE,
    is_emergency BOOLEAN DEFAULT FALSE,
    
    -- 二维码标签
    qr_code_url VARCHAR(500),
    label_printed BOOLEAN DEFAULT FALSE,
    
    -- 附件
    manual_files JSONB,
    certificate_files JSONB,
    
    -- 其他
    remark TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES sys_user(id),
    updated_by UUID REFERENCES sys_user(id)
);

CREATE INDEX idx_device_dept ON medical_device(dept_id);
CREATE INDEX idx_device_status ON medical_device(device_status);
CREATE INDEX idx_device_category ON medical_device(category_id);
CREATE INDEX idx_device_enable_date ON medical_device(enable_date);

-- 4.2 设备附属低值品表
CREATE TABLE device_accessory (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES medical_device(id) ON DELETE CASCADE,
    accessory_name VARCHAR(200) NOT NULL,
    accessory_code VARCHAR(50),
    quantity INTEGER DEFAULT 1,
    unit_price DECIMAL(10,2),
    purchase_date DATE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4.3 设备入库记录表
CREATE TABLE device_entry (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entry_no VARCHAR(30) UNIQUE NOT NULL,
    contract_id UUID REFERENCES purchase_contract(id),
    entry_date DATE NOT NULL,
    entry_type VARCHAR(20) DEFAULT 'purchase',
    operator_id UUID REFERENCES sys_user(id),
    
    quality_check_passed BOOLEAN,
    quality_checker_id UUID REFERENCES sys_user(id),
    quality_check_date DATE,
    quality_check_report_url VARCHAR(500),
    
    installation_completed BOOLEAN,
    installer_id UUID,
    installation_date DATE,
    installation_report_url VARCHAR(500),
    
    status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4.4 设备入库明细表
CREATE TABLE device_entry_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entry_id UUID REFERENCES device_entry(id) ON DELETE CASCADE,
    device_id UUID REFERENCES medical_device(id),
    device_name VARCHAR(200) NOT NULL,
    brand VARCHAR(100),
    model VARCHAR(100),
    serial_number VARCHAR(100),
    quantity INTEGER DEFAULT 1,
    unit_price DECIMAL(15,2),
    total_price DECIMAL(15,2),
    is_accepted BOOLEAN DEFAULT FALSE,
    accepted_device_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4.5 资产流转记录表
CREATE TABLE asset_transfer (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_no VARCHAR(30) UNIQUE NOT NULL,
    transfer_type VARCHAR(20) NOT NULL,
    
    device_id UUID REFERENCES medical_device(id),
    from_dept_id UUID REFERENCES department(id),
    to_dept_id UUID REFERENCES department(id),
    from_campus_id UUID REFERENCES campus(id),
    to_campus_id UUID REFERENCES campus(id),
    
    applicant_id UUID REFERENCES sys_user(id),
    approver_id UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    
    transfer_date DATE,
    reason TEXT,
    
    status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4.6 资产盘点表
CREATE TABLE inventory_check (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    check_no VARCHAR(30) UNIQUE NOT NULL,
    check_name VARCHAR(200),
    check_year INTEGER,
    check_type VARCHAR(20) DEFAULT 'annual',
    
    campus_id UUID REFERENCES campus(id),
    dept_id UUID REFERENCES department(id),
    
    start_date DATE,
    end_date DATE,
    actual_start_at TIMESTAMP WITH TIME ZONE,
    actual_end_at TIMESTAMP WITH TIME ZONE,
    
    checker_id UUID REFERENCES sys_user(id),
    supervisor_id UUID REFERENCES sys_user(id),
    
    total_count INTEGER,
    checked_count INTEGER DEFAULT 0,
    matched_count INTEGER DEFAULT 0,
    mismatch_count INTEGER DEFAULT 0,
    missing_count INTEGER DEFAULT 0,
    
    status VARCHAR(20) DEFAULT 'planning',
    report_url VARCHAR(500),
    
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4.7 资产盘点明细表
CREATE TABLE inventory_check_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    check_id UUID REFERENCES inventory_check(id) ON DELETE CASCADE,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    expected_location VARCHAR(200),
    actual_location VARCHAR(200),
    
    is_found BOOLEAN,
    is_matched BOOLEAN,
    condition_status VARCHAR(20),
    
    check_date TIMESTAMP WITH TIME ZONE,
    checker_id UUID REFERENCES sys_user(id),
    
    photos JSONB,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_check_item_device ON inventory_check_item(device_id);

-- 4.8 设备报废表
CREATE TABLE device_scrap (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    scrap_no VARCHAR(30) UNIQUE NOT NULL,
    
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    scrap_reason TEXT NOT NULL,
    scrap_type VARCHAR(20),
    
    applicant_id UUID REFERENCES sys_user(id),
    application_date DATE,
    
    evaluator_id UUID REFERENCES sys_user(id),
    evaluation_result TEXT,
    residual_value DECIMAL(15,2),
    
    approver_id UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    
    scrap_date DATE,
    disposal_method VARCHAR(50),
    disposal_date DATE,
    
    status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================================
-- 5. 维修管理模块
-- ================================================================================

-- 5.1 故障类型字典表
CREATE TABLE fault_type_dict (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    fault_code VARCHAR(20) UNIQUE NOT NULL,
    fault_name VARCHAR(100) NOT NULL,
    parent_id UUID REFERENCES fault_type_dict(id),
    level INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5.2 工程师表
CREATE TABLE engineer (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES sys_user(id),
    engineer_no VARCHAR(20) UNIQUE NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    specialty VARCHAR(100),
    skill_levels JSONB,
    phone VARCHAR(20),
    email VARCHAR(100),
    is_on_duty BOOLEAN DEFAULT TRUE,
    workload_score DECIMAL(5,2) DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5.3 维修工单表
CREATE TABLE repair_workorder (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wo_no VARCHAR(30) UNIQUE NOT NULL,
    
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    reporter_id UUID REFERENCES sys_user(id),
    report_dept_id UUID REFERENCES department(id),
    report_method VARCHAR(20),
    report_time TIMESTAMP WITH TIME ZONE NOT NULL,
    fault_description TEXT NOT NULL,
    fault_photos JSONB,
    urgency_level VARCHAR(20) DEFAULT 'normal',
    
    fault_type_id UUID REFERENCES fault_type_dict(id),
    fault_category VARCHAR(50),
    
    assigned_engineer_id UUID REFERENCES engineer(id),
    assigned_at TIMESTAMP WITH TIME ZONE,
    assigner_id UUID REFERENCES sys_user(id),
    
    response_time TIMESTAMP WITH TIME ZONE,
    arrival_time TIMESTAMP WITH TIME ZONE,
    
    repair_type VARCHAR(20),
    repair_start_time TIMESTAMP WITH TIME ZONE,
    repair_end_time TIMESTAMP WITH TIME ZONE,
    repair_duration_hours DECIMAL(10,2),
    downtime_hours DECIMAL(10,2),
    
    solution_description TEXT,
    spare_parts_used JSONB,
    
    labor_cost DECIMAL(10,2) DEFAULT 0,
    parts_cost DECIMAL(10,2) DEFAULT 0,
    total_cost DECIMAL(10,2) DEFAULT 0,
    invoice_no VARCHAR(50),
    invoice_url VARCHAR(500),
    
    verifier_id UUID REFERENCES sys_user(id),
    verify_time TIMESTAMP WITH TIME ZONE,
    verify_result VARCHAR(20),
    verify_comment TEXT,
    
    satisfaction_rating INTEGER,
    satisfaction_comment TEXT,
    
    status VARCHAR(20) DEFAULT 'reported',
    
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wo_device ON repair_workorder(device_id);
CREATE INDEX idx_wo_status ON repair_workorder(status);
CREATE INDEX idx_wo_report_time ON repair_workorder(report_time DESC);
CREATE INDEX idx_wo_engineer ON repair_workorder(assigned_engineer_id);

-- 5.4 备件库表
CREATE TABLE spare_part (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    part_code VARCHAR(30) UNIQUE NOT NULL,
    part_name VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    specification TEXT,
    applicable_devices JSONB,
    supplier_id UUID REFERENCES supplier(id),
    unit_price DECIMAL(10,2),
    stock_quantity INTEGER DEFAULT 0,
    min_stock INTEGER,
    max_stock INTEGER,
    storage_location VARCHAR(200),
    remark TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5.5 备件使用记录表
CREATE TABLE spare_part_usage (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workorder_id UUID REFERENCES repair_workorder(id),
    part_id UUID REFERENCES spare_part(id),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10,2),
    total_price DECIMAL(10,2),
    used_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    operator_id UUID REFERENCES sys_user(id)
);

-- ================================================================================
-- 6. 保养管理模块
-- ================================================================================

-- 6.1 保养模板表
CREATE TABLE maintenance_template (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_name VARCHAR(200) NOT NULL,
    maintenance_level VARCHAR(20) NOT NULL,
    category_id UUID REFERENCES medical_device_category(id),
    items JSONB NOT NULL,
    estimated_duration INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6.2 保养计划表
CREATE TABLE maintenance_plan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_name VARCHAR(200),
    device_id UUID REFERENCES medical_device(id),
    template_id UUID REFERENCES maintenance_template(id),
    maintenance_level VARCHAR(20) NOT NULL,
    
    cycle_type VARCHAR(20) NOT NULL,
    cycle_value INTEGER,
    
    next_due_date DATE NOT NULL,
    reminder_days_before INTEGER DEFAULT 7,
    
    assigned_engineer_id UUID REFERENCES engineer(id),
    status VARCHAR(20) DEFAULT 'active',
    
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_maint_plan_device ON maintenance_plan(device_id);
CREATE INDEX idx_maint_plan_due ON maintenance_plan(next_due_date);

-- 6.3 保养执行记录表
CREATE TABLE maintenance_record (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    record_no VARCHAR(30) UNIQUE NOT NULL,
    
    plan_id UUID REFERENCES maintenance_plan(id),
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    maintenance_level VARCHAR(20) NOT NULL,
    template_id UUID REFERENCES maintenance_template(id),
    
    executor_id UUID REFERENCES engineer(id),
    execute_start_time TIMESTAMP WITH TIME ZONE,
    execute_end_time TIMESTAMP WITH TIME ZONE,
    duration_minutes INTEGER,
    
    items_result JSONB,
    overall_result VARCHAR(20),
    issues_found TEXT,
    
    photos JSONB,
    signature_url VARCHAR(500),
    
    reviewer_id UUID REFERENCES sys_user(id),
    review_time TIMESTAMP WITH TIME ZONE,
    review_comment TEXT,
    
    status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_maint_record_device ON maintenance_record(device_id);
CREATE INDEX idx_maint_record_time ON maintenance_record(execute_start_time DESC);

-- ================================================================================
-- 7. 质量控制模块
-- ================================================================================

-- 7.1 风险评估表
CREATE TABLE risk_assessment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    assessment_no VARCHAR(30) UNIQUE NOT NULL,
    
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    assessor_id UUID REFERENCES sys_user(id),
    assessment_date DATE,
    
    risk_level VARCHAR(20),                  -- high/medium/low
    risk_factors JSONB,                      -- 风险因素
    
    assessment_result TEXT,
    recommendations TEXT,                    -- 建议措施
    
    report_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'completed',
    
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7.2 不良事件表
CREATE TABLE adverse_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_no VARCHAR(30) UNIQUE NOT NULL,
    
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    reporter_id UUID REFERENCES sys_user(id),
    report_time TIMESTAMP WITH TIME ZONE NOT NULL,
    
    event_type VARCHAR(50),                  -- malfunction/injury/near_miss
    severity_level VARCHAR(20),              -- critical/major/minor
    
    event_description TEXT NOT NULL,
    cause_analysis TEXT,
    impact_description TEXT,
    
    photos JSONB,
    
    -- 处理信息
    handler_id UUID REFERENCES sys_user(id),
    handle_measures TEXT,
    handle_time TIMESTAMP WITH TIME ZONE,
    
    -- 上报信息
    reported_to_authority BOOLEAN DEFAULT FALSE,
    report_date DATE,
    authority_feedback TEXT,
    
    -- 审核
    reviewer_id UUID REFERENCES sys_user(id),
    review_time TIMESTAMP WITH TIME ZONE,
    review_comment TEXT,
    
    status VARCHAR(20) DEFAULT 'reported',   -- reported/handling/reviewed/closed
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_adverse_event_device ON adverse_event(device_id);
CREATE INDEX idx_adverse_event_time ON adverse_event(report_time DESC);

-- 7.3 计量管理表
CREATE TABLE metrology_record (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    metrology_no VARCHAR(30) UNIQUE NOT NULL,
    
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    metrology_type VARCHAR(50),              -- calibration/verification/testing
    metrology_org VARCHAR(200),              -- 检定机构
    
    scheduled_date DATE,                     -- 计划日期
    actual_date DATE,                        -- 实际日期
    next_due_date DATE,                      -- 下次到期日
    
    certificate_no VARCHAR(100),             -- 证书编号
    certificate_url VARCHAR(500),            -- 证书文件
    
    result VARCHAR(20),                      -- pass/fail/conditional
    measurement_data JSONB,                  -- 测量数据
    
    inspector_id UUID,                       -- 检定员
    cost DECIMAL(10,2),                      -- 费用
    
    status VARCHAR(20) DEFAULT 'pending',    -- pending/completed/overdue
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_metrology_device ON metrology_record(device_id);
CREATE INDEX idx_metrology_due ON metrology_record(next_due_date);

-- 7.4 性能检测表
CREATE TABLE performance_test (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    test_no VARCHAR(30) UNIQUE NOT NULL,
    
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    test_type VARCHAR(50),                   -- acceptance/periodic/special
    test_standard VARCHAR(200),              -- 检测标准
    
    tester_id UUID REFERENCES sys_user(id),
    test_date DATE,
    
    test_items JSONB,                        -- 检测项目及结果
    overall_result VARCHAR(20),              -- pass/fail
    
    test_report_url VARCHAR(500),
    
    status VARCHAR(20) DEFAULT 'completed',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_perf_test_device ON performance_test(device_id);

-- ================================================================================
-- 8. 维保管理模块
-- ================================================================================

-- 8.1 维保合同表
CREATE TABLE maintenance_contract (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_code VARCHAR(30) UNIQUE NOT NULL,
    contract_name VARCHAR(200),
    
    supplier_id UUID REFERENCES supplier(id),
    
    start_date DATE,
    end_date DATE,
    contract_amount DECIMAL(15,2),
    
    coverage_type VARCHAR(20),               -- full/partial/labor_only
    covered_devices JSONB,                   -- 覆盖设备列表
    
    response_time_hours INTEGER,             -- 响应时间(小时)
    preventive_visits_per_year INTEGER,      -- 年度预防性维护次数
    
    contract_file_url VARCHAR(500),
    
    status VARCHAR(20) DEFAULT 'active',     -- active/expired/terminated
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8.2 维保履约记录表
CREATE TABLE maintenance_contract_fulfillment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID REFERENCES maintenance_contract(id),
    
    fulfillment_type VARCHAR(20),            -- preventive_visit/emergency_repair
    scheduled_date DATE,
    actual_date DATE,
    
    engineer_id UUID REFERENCES engineer(id),
    
    service_content TEXT,
    result_description TEXT,
    
    photos JSONB,
    signature_url VARCHAR(500),
    
    evaluation_rating INTEGER,               -- 评价评分
    evaluation_comment TEXT,
    
    status VARCHAR(20) DEFAULT 'completed',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8.3 维保付款记录表
CREATE TABLE maintenance_contract_payment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID REFERENCES maintenance_contract(id),
    
    payment_no VARCHAR(30) UNIQUE NOT NULL,
    payment_stage VARCHAR(50),
    payment_amount DECIMAL(15,2),
    payment_date DATE,
    
    invoice_no VARCHAR(50),
    invoice_url VARCHAR(500),
    
    status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================================
-- 9. 特殊设备管理模块
-- ================================================================================

-- 9.1 生命支持类设备表
CREATE TABLE life_support_device (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES medical_device(id) UNIQUE,
    
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    criticality_level VARCHAR(20),           -- critical/high/medium
    backup_required BOOLEAN DEFAULT TRUE,    -- 是否需要备用设备
    
    standby_status VARCHAR(20) DEFAULT 'ready', -- ready/in_use/maintenance
    
    last_test_date DATE,                     -- 最后测试日期
    next_test_date DATE,                     -- 下次测试日期
    
    emergency_protocol TEXT,                 -- 紧急替代流程
    
    responsible_person_id UUID REFERENCES sys_user(id),
    
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 9.2 应急设备库表
CREATE TABLE emergency_device_pool (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pool_name VARCHAR(200) NOT NULL,
    
    campus_id UUID REFERENCES campus(id),
    location VARCHAR(200),                   -- 存放位置
    
    manager_id UUID REFERENCES sys_user(id), -- 管理人
    contact_phone VARCHAR(20),
    
    devices JSONB,                           -- 设备列表 [{device_id, status}]
    
    is_available BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 9.3 应急设备调配记录表
CREATE TABLE emergency_device_allocation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    allocation_no VARCHAR(30) UNIQUE NOT NULL,
    
    device_id UUID REFERENCES medical_device(id),
    from_pool_id UUID REFERENCES emergency_device_pool(id),
    to_dept_id UUID REFERENCES department(id),
    
    applicant_id UUID REFERENCES sys_user(id),
    application_time TIMESTAMP WITH TIME ZONE NOT NULL,
    
    reason TEXT NOT NULL,                    -- 调配原因
    urgency_level VARCHAR(20),               -- urgent/high/normal
    
    approved_by UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    
    allocation_time TIMESTAMP WITH TIME ZONE, -- 实际调配时间
    return_time TIMESTAMP WITH TIME ZONE,     -- 归还时间
    
    status VARCHAR(20) DEFAULT 'pending',    -- pending/approved/allocated/returned
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 9.4 特种设备表（放射、辐射类等）
CREATE TABLE special_device (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES medical_device(id) UNIQUE,
    
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    special_type VARCHAR(50),                -- radiation/laser/pressure/etc
    
    license_no VARCHAR(100),                 -- 许可证号
    license_expiry_date DATE,                -- 许可证到期日
    
    operator_cert_required BOOLEAN DEFAULT TRUE, -- 是否需要操作证
    certified_operators UUID[],              -- 持证操作人员
    
    safety_measures TEXT,                    -- 安全措施
    
    last_inspection_date DATE,
    next_inspection_date DATE,
    
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 9.5 租赁设备表
CREATE TABLE leased_device (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES medical_device(id) UNIQUE,
    
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    lessor_id UUID REFERENCES supplier(id),  -- 出租方
    
    lease_start_date DATE,
    lease_end_date DATE,
    monthly_rent DECIMAL(10,2),              -- 月租金
    
    contract_no VARCHAR(50),
    contract_url VARCHAR(500),
    
    auto_renewal BOOLEAN DEFAULT FALSE,      -- 是否自动续租
    renewal_notice_days INTEGER DEFAULT 30,  -- 续租提前通知天数
    
    status VARCHAR(20) DEFAULT 'active',     -- active/expired/terminated
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================================
-- 10. 效益分析模块
-- ================================================================================

-- 10.1 设备使用记录表（从HIS/PACS/LIS采集）
CREATE TABLE device_usage_record (
    id BIGSERIAL PRIMARY KEY,
    
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    usage_date DATE NOT NULL,
    usage_hours DECIMAL(10,2),               -- 使用时长(小时)
    
    patient_count INTEGER DEFAULT 0,         -- 患者数量
    examination_count INTEGER DEFAULT 0,     -- 检查次数
    
    revenue DECIMAL(15,2) DEFAULT 0,         -- 收入
    
    data_source VARCHAR(20),                 -- his/pacs/lis/manual
    source_record_id VARCHAR(100),           -- 源系统记录ID
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usage_device ON device_usage_record(device_id);
CREATE INDEX idx_usage_date ON device_usage_record(usage_date DESC);

-- 10.2 设备成本记录表
CREATE TABLE device_cost_record (
    id BIGSERIAL PRIMARY KEY,
    
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    cost_date DATE NOT NULL,
    cost_type VARCHAR(50) NOT NULL,          -- depreciation/maintenance/repair/consumable/utility/labor/other
    
    cost_amount DECIMAL(15,2) NOT NULL,
    
    description TEXT,
    invoice_no VARCHAR(50),
    
    data_source VARCHAR(20) DEFAULT 'manual', -- manual/hrp/auto
    source_record_id VARCHAR(100),
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cost_device ON device_cost_record(device_id);
CREATE INDEX idx_cost_date ON device_cost_record(cost_date DESC);
CREATE INDEX idx_cost_type ON device_cost_record(cost_type);

-- 10.3 设备效益分析汇总表（按月聚合）
CREATE TABLE device_benefit_summary (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    
    summary_year INTEGER NOT NULL,
    summary_month INTEGER NOT NULL,
    
    total_revenue DECIMAL(15,2) DEFAULT 0,   -- 总收入
    total_cost DECIMAL(15,2) DEFAULT 0,      -- 总支出
    net_profit DECIMAL(15,2) DEFAULT 0,      -- 净利润
    
    profit_rate DECIMAL(10,4),               -- 利润率
    
    usage_hours DECIMAL(10,2),               -- 使用时长
    patient_count INTEGER,                   -- 患者数
    utilization_rate DECIMAL(10,4),          -- 使用率
    
    maintenance_cost DECIMAL(15,2),          -- 维保费用
    repair_cost DECIMAL(15,2),               -- 维修费用
    depreciation_cost DECIMAL(15,2),         -- 折旧费用
    
    benefit_level VARCHAR(20),               -- excellent/good/average/poor
    benefit_score DECIMAL(10,2),             -- 效益评分
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(device_id, summary_year, summary_month)
);

CREATE INDEX idx_benefit_device ON device_benefit_summary(device_id);
CREATE INDEX idx_benefit_period ON device_benefit_summary(summary_year, summary_month);

-- ================================================================================
-- 11. 系统配置与字典表
-- ================================================================================

-- 11.1 系统参数表
CREATE TABLE sys_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    config_type VARCHAR(20),                 -- string/number/boolean/json
    description TEXT,
    is_system BOOLEAN DEFAULT FALSE,         -- 是否系统参数（不可修改）
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 11.2 数据字典表
CREATE TABLE sys_dict (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dict_type VARCHAR(50) NOT NULL,
    dict_code VARCHAR(50) NOT NULL,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100),
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(dict_type, dict_code)
);

CREATE INDEX idx_dict_type ON sys_dict(dict_type);

-- 11.3 消息通知表
CREATE TABLE sys_notification (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    title VARCHAR(200) NOT NULL,
    content TEXT,
    notification_type VARCHAR(20),           -- maintenance_due/metrology_due/warranty_due/inventory/etc
    
    target_users UUID[],                     -- 目标用户
    target_roles UUID[],                     -- 目标角色
    target_depts UUID[],                     -- 目标科室
    
    priority VARCHAR(20) DEFAULT 'normal',   -- urgent/high/normal/low
    
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    
    related_entity_type VARCHAR(50),         -- 关联实体类型
    related_entity_id UUID,                  -- 关联实体ID
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_user ON sys_notification USING GIN(target_users);
CREATE INDEX idx_notification_created ON sys_notification(created_at DESC);

-- ================================================================================
-- 12. 视图和函数
-- ================================================================================

-- 12.1 设备完整信息视图
CREATE VIEW v_device_full_info AS
SELECT 
    d.id,
    d.device_code,
    d.device_name,
    d.brand,
    d.model,
    d.serial_number,
    mc.category_name,
    m.manufacturer_name,
    s.supplier_name,
    d.original_value,
    d.net_value,
    c.campus_name,
    b.building_name,
    dept.dept_name,
    d.location_detail,
    d.enable_date,
    d.warranty_end_date,
    d.device_status,
    d.risk_level,
    d.is_life_support,
    d.is_emergency,
    d.created_at
FROM medical_device d
LEFT JOIN medical_device_category mc ON d.category_id = mc.id
LEFT JOIN manufacturer m ON d.manufacturer_id = m.id
LEFT JOIN supplier s ON d.supplier_id = s.id
LEFT JOIN campus c ON d.campus_id = c.id
LEFT JOIN building b ON d.building_id = b.id
LEFT JOIN department dept ON d.dept_id = dept.id;

-- 12.2 设备效益分析视图
CREATE VIEW v_device_benefit AS
SELECT 
    d.device_code,
    d.device_name,
    d.dept_id,
    dept.dept_name,
    bs.summary_year,
    bs.summary_month,
    bs.total_revenue,
    bs.total_cost,
    bs.net_profit,
    bs.profit_rate,
    bs.utilization_rate,
    bs.benefit_level
FROM device_benefit_summary bs
JOIN medical_device d ON bs.device_id = d.id
LEFT JOIN department dept ON d.dept_id = dept.id;

-- ================================================================================
-- 完成
-- ================================================================================

COMMENT ON DATABASE meis IS '医院设备固定资产管理系统数据库';
