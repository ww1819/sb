-- MEIS tenant: CREATE TABLE + COMMENT ON (visible in database catalog)

-- MEIS tenant business schema (per-tenant Flyway)
-- ================================================================================
-- 医院设备固定资产管理系统 (MEIS) - PostgreSQL 数据库设计
-- 版本: V1.0
-- 日期: 2026-06-23
-- 数据库: PostgreSQL 15+
-- ================================================================================
-- 【标准审计/软删字段 — 强制约定】
-- 每张业务表（含新建表）必须包含以下七列，缺一不可：
--   created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP  -- 创建时间
--   updated_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP  -- 更新时间
--   created_by  UUID                                   -- 创建者
--   updated_by  UUID                                   -- 更新者
--   is_deleted  SMALLINT NOT NULL DEFAULT 0            -- 删除标志：0未删除 / 1已删除
--   deleted_at  TIMESTAMPTZ                            -- 删除时间
--   deleted_by  UUID                                   -- 删除者
-- 老租户缺列由 R__columns_audit.sql 幂等补全。
-- 详见 docs/meis-requirements.md 附录 G.0。
-- ================================================================================
-- 启用扩展
-- UUID 生成扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- 加密/随机数扩展
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
COMMENT ON TABLE campus IS '院区表';
COMMENT ON COLUMN campus.id IS '主键';
COMMENT ON COLUMN campus.campus_code IS '院区编码';
COMMENT ON COLUMN campus.campus_name IS '院区名称';
COMMENT ON COLUMN campus.address IS '地址';
COMMENT ON COLUMN campus.contact_phone IS '联系电话';
COMMENT ON COLUMN campus.is_active IS '是否启用';
COMMENT ON COLUMN campus.created_at IS '创建时间';
COMMENT ON COLUMN campus.updated_at IS '更新时间';

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
COMMENT ON TABLE building IS '建筑物表';
COMMENT ON COLUMN building.id IS '主键';
COMMENT ON COLUMN building.campus_id IS '所属院区';
COMMENT ON COLUMN building.building_code IS '建筑物编码';
COMMENT ON COLUMN building.building_name IS '建筑物名称';
COMMENT ON COLUMN building.floor_count IS '楼层数';
COMMENT ON COLUMN building.is_active IS '是否启用';
COMMENT ON COLUMN building.created_at IS '创建时间';
COMMENT ON COLUMN building.updated_at IS '更新时间';

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    pinyin_code VARCHAR(50)
);
COMMENT ON TABLE department IS '科室表';
COMMENT ON COLUMN department.id IS '主键';
COMMENT ON COLUMN department.dept_code IS '科室编码';
COMMENT ON COLUMN department.dept_name IS '科室名称';
COMMENT ON COLUMN department.parent_id IS '关联上级';
COMMENT ON COLUMN department.campus_id IS '所属院区';
COMMENT ON COLUMN department.building_id IS '所属建筑物';
COMMENT ON COLUMN department.floor_number IS '所在楼层';
COMMENT ON COLUMN department.room_number IS '房间号';
COMMENT ON COLUMN department.manager_id IS '关联负责人';
COMMENT ON COLUMN department.contact_phone IS '联系电话';
COMMENT ON COLUMN department.is_clinical IS '是否临床科室';
COMMENT ON COLUMN department.sort_order IS '排序号';
COMMENT ON COLUMN department.is_active IS '是否启用';
COMMENT ON COLUMN department.created_at IS '创建时间';
COMMENT ON COLUMN department.updated_at IS '更新时间';
COMMENT ON COLUMN department.pinyin_code IS '拼音简码（检索）';

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
    is_repair_engineer BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    permissions JSONB,
    permission_mode VARCHAR(20) DEFAULT 'synced'
);
COMMENT ON TABLE sys_user IS '用户表';
COMMENT ON COLUMN sys_user.id IS '主键';
COMMENT ON COLUMN sys_user.username IS '登录用户名';
COMMENT ON COLUMN sys_user.password_hash IS '密码哈希';
COMMENT ON COLUMN sys_user.real_name IS '真实姓名';
COMMENT ON COLUMN sys_user.employee_no IS '工号';
COMMENT ON COLUMN sys_user.phone IS '手机号';
COMMENT ON COLUMN sys_user.email IS '电子邮箱';
COMMENT ON COLUMN sys_user.dept_id IS '所属科室';
COMMENT ON COLUMN sys_user.role_ids IS '角色ID列表';
COMMENT ON COLUMN sys_user.avatar_url IS '头像地址';
COMMENT ON COLUMN sys_user.last_login_at IS '最后登录时间';
COMMENT ON COLUMN sys_user.last_login_ip IS '最后登录IP';
COMMENT ON COLUMN sys_user.is_locked IS '是否锁定';
COMMENT ON COLUMN sys_user.is_active IS '是否启用';
COMMENT ON COLUMN sys_user.created_at IS '创建时间';
COMMENT ON COLUMN sys_user.updated_at IS '更新时间';
COMMENT ON COLUMN sys_user.permissions IS '权限JSON';
COMMENT ON COLUMN sys_user.permission_mode IS '权限模式（synced/custom）';

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
COMMENT ON TABLE sys_role IS '角色表';
COMMENT ON COLUMN sys_role.id IS '主键';
COMMENT ON COLUMN sys_role.role_code IS '角色编码';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';
COMMENT ON COLUMN sys_role.description IS '描述';
COMMENT ON COLUMN sys_role.permissions IS '权限JSON';
COMMENT ON COLUMN sys_role.sort_order IS '排序号';
COMMENT ON COLUMN sys_role.is_active IS '是否启用';
COMMENT ON COLUMN sys_role.created_at IS '创建时间';
COMMENT ON COLUMN sys_role.updated_at IS '更新时间';

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
COMMENT ON TABLE sys_operation_log IS '操作日志表';
COMMENT ON COLUMN sys_operation_log.id IS '主键';
COMMENT ON COLUMN sys_operation_log.user_id IS '关联用户';
COMMENT ON COLUMN sys_operation_log.operation_type IS '操作类型';
COMMENT ON COLUMN sys_operation_log.module_name IS '模块名称';
COMMENT ON COLUMN sys_operation_log.operation_desc IS '操作说明';
COMMENT ON COLUMN sys_operation_log.request_method IS 'HTTP方法';
COMMENT ON COLUMN sys_operation_log.request_url IS '请求URL';
COMMENT ON COLUMN sys_operation_log.request_params IS '请求参数JSON';
COMMENT ON COLUMN sys_operation_log.response_result IS '响应结果JSON';
COMMENT ON COLUMN sys_operation_log.ip_address IS '客户端IP';
COMMENT ON COLUMN sys_operation_log.user_agent IS '浏览器UA';
COMMENT ON COLUMN sys_operation_log.execution_time IS '执行耗时(ms)';
COMMENT ON COLUMN sys_operation_log.status IS '状态';
COMMENT ON COLUMN sys_operation_log.error_msg IS '错误信息';
COMMENT ON COLUMN sys_operation_log.created_at IS '创建时间';

-- 1.7 实体变更记录（附录 T）
CREATE TABLE sys_entity_change_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    changed_fields JSONB,
    snapshot_json JSONB,
    operator_id UUID,
    operator_name VARCHAR(100),
    remark TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID
);
COMMENT ON TABLE sys_entity_change_log IS '主数据实体变更记录';
COMMENT ON COLUMN sys_entity_change_log.entity_type IS '实体类型';
COMMENT ON COLUMN sys_entity_change_log.entity_id IS '实体主键';
COMMENT ON COLUMN sys_entity_change_log.action IS '动作（CREATE/UPDATE/DELETE 等）';
COMMENT ON COLUMN sys_entity_change_log.changed_fields IS '变更字段 JSON';
COMMENT ON COLUMN sys_entity_change_log.snapshot_json IS '精简快照 JSON';
COMMENT ON COLUMN sys_entity_change_log.operator_id IS '操作人ID';
COMMENT ON COLUMN sys_entity_change_log.operator_name IS '操作人姓名';
COMMENT ON COLUMN sys_entity_change_log.remark IS '备注';

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
COMMENT ON TABLE medical_device_category IS '医疗器械分类目录';
COMMENT ON COLUMN medical_device_category.id IS '主键';
COMMENT ON COLUMN medical_device_category.category_code IS '分类编码';
COMMENT ON COLUMN medical_device_category.category_name IS '分类名称';
COMMENT ON COLUMN medical_device_category.parent_code IS '上级分类编码';
COMMENT ON COLUMN medical_device_category.level IS '层级';
COMMENT ON COLUMN medical_device_category.full_path IS '层级完整路径';
COMMENT ON COLUMN medical_device_category.sort_order IS '排序号';
COMMENT ON COLUMN medical_device_category.is_active IS '是否启用';
COMMENT ON COLUMN medical_device_category.created_at IS '创建时间';
COMMENT ON COLUMN medical_device_category.updated_at IS '更新时间';

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    pinyin_code VARCHAR(50)
);
COMMENT ON TABLE supplier IS '供应商表';
COMMENT ON COLUMN supplier.id IS '主键';
COMMENT ON COLUMN supplier.supplier_code IS '供应商编码';
COMMENT ON COLUMN supplier.supplier_name IS '供应商名称';
COMMENT ON COLUMN supplier.unified_social_credit_code IS 'unifiedsocialcredit编码';
COMMENT ON COLUMN supplier.legal_representative IS 'legal representative';
COMMENT ON COLUMN supplier.contact_person IS 'contact person';
COMMENT ON COLUMN supplier.contact_phone IS '联系电话';
COMMENT ON COLUMN supplier.address IS '地址';
COMMENT ON COLUMN supplier.bank_account IS 'bank account';
COMMENT ON COLUMN supplier.bank_name IS 'bank名称';
COMMENT ON COLUMN supplier.qualification_files IS 'qualification files';
COMMENT ON COLUMN supplier.rating IS 'rating';
COMMENT ON COLUMN supplier.is_authorized IS '是否authorized';
COMMENT ON COLUMN supplier.is_active IS '是否启用';
COMMENT ON COLUMN supplier.created_at IS '创建时间';
COMMENT ON COLUMN supplier.updated_at IS '更新时间';
COMMENT ON COLUMN supplier.pinyin_code IS '拼音简码（检索）';

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    pinyin_code VARCHAR(50)
);
COMMENT ON TABLE manufacturer IS '生产厂商表';
COMMENT ON COLUMN manufacturer.id IS '主键';
COMMENT ON COLUMN manufacturer.manufacturer_code IS '生产厂商编码';
COMMENT ON COLUMN manufacturer.manufacturer_name IS '生产厂商名称';
COMMENT ON COLUMN manufacturer.country IS 'country';
COMMENT ON COLUMN manufacturer.is_domestic IS '是否domestic';
COMMENT ON COLUMN manufacturer.contact_phone IS '联系电话';
COMMENT ON COLUMN manufacturer.website IS 'website';
COMMENT ON COLUMN manufacturer.is_active IS '是否启用';
COMMENT ON COLUMN manufacturer.created_at IS '创建时间';
COMMENT ON COLUMN manufacturer.updated_at IS '更新时间';
COMMENT ON COLUMN manufacturer.pinyin_code IS '拼音简码（检索）';

-- 2.4 资产分类（院内）
CREATE TABLE IF NOT EXISTS asset_category (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    category_code VARCHAR(50) UNIQUE NOT NULL,
    category_name VARCHAR(200) NOT NULL,
    parent_id UUID REFERENCES asset_category(id),
    depreciation_years INTEGER,
    residual_rate DECIMAL(5,2),
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE asset_category IS '资产分类';
COMMENT ON COLUMN asset_category.id IS '主键';
COMMENT ON COLUMN asset_category.category_code IS '资产分类编码';
COMMENT ON COLUMN asset_category.category_name IS '资产分类名称';
COMMENT ON COLUMN asset_category.parent_id IS '上级分类';
COMMENT ON COLUMN asset_category.depreciation_years IS '折旧年限';
COMMENT ON COLUMN asset_category.residual_rate IS '残值率(%)';
COMMENT ON COLUMN asset_category.sort_order IS '排序号';
COMMENT ON COLUMN asset_category.is_active IS '是否启用';
COMMENT ON COLUMN asset_category.created_at IS '创建时间';
COMMENT ON COLUMN asset_category.updated_at IS '更新时间';

-- 2.5 财务分类
CREATE TABLE IF NOT EXISTS finance_category (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    finance_code VARCHAR(50) UNIQUE NOT NULL,
    finance_name VARCHAR(200) NOT NULL,
    parent_id UUID REFERENCES finance_category(id),
    account_subject VARCHAR(50),
    fund_source VARCHAR(50),
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE finance_category IS '财务分类';
COMMENT ON COLUMN finance_category.id IS '主键';
COMMENT ON COLUMN finance_category.finance_code IS '财务分类编码';
COMMENT ON COLUMN finance_category.finance_name IS '财务分类名称';
COMMENT ON COLUMN finance_category.parent_id IS '上级分类';
COMMENT ON COLUMN finance_category.account_subject IS '会计科目';
COMMENT ON COLUMN finance_category.fund_source IS '资金来源';
COMMENT ON COLUMN finance_category.sort_order IS '排序号';
COMMENT ON COLUMN finance_category.is_active IS '是否启用';
COMMENT ON COLUMN finance_category.created_at IS '创建时间';
COMMENT ON COLUMN finance_category.updated_at IS '更新时间';

-- 2.6 计量单位
CREATE TABLE IF NOT EXISTS unit_dict (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    unit_code VARCHAR(20) UNIQUE NOT NULL,
    unit_name VARCHAR(50) NOT NULL,
    unit_type VARCHAR(20) DEFAULT 'quantity',
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE unit_dict IS '计量单位';
COMMENT ON COLUMN unit_dict.id IS '主键';
COMMENT ON COLUMN unit_dict.unit_code IS '单位编码';
COMMENT ON COLUMN unit_dict.unit_name IS '单位名称';
COMMENT ON COLUMN unit_dict.unit_type IS '单位类型';
COMMENT ON COLUMN unit_dict.sort_order IS '排序号';
COMMENT ON COLUMN unit_dict.is_active IS '是否启用';
COMMENT ON COLUMN unit_dict.created_at IS '创建时间';
COMMENT ON COLUMN unit_dict.updated_at IS '更新时间';

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    plan_type VARCHAR(20) DEFAULT 'annual',
    fund_source VARCHAR(30),
    business_chain_no VARCHAR(40),
    is_large_equipment BOOLEAN DEFAULT false,
    large_equipment_class VARCHAR(20),
    benefit_analysis_url VARCHAR(500),
    dept_argument_url VARCHAR(500),
    version INTEGER DEFAULT 1
);
COMMENT ON TABLE purchase_plan IS '采购计划表';
COMMENT ON COLUMN purchase_plan.id IS '主键';
COMMENT ON COLUMN purchase_plan.plan_code IS '订阅计划编码';
COMMENT ON COLUMN purchase_plan.plan_year IS '计划年度';
COMMENT ON COLUMN purchase_plan.dept_id IS '所属科室';
COMMENT ON COLUMN purchase_plan.applicant_id IS '关联申请人';
COMMENT ON COLUMN purchase_plan.total_budget IS '总预算';
COMMENT ON COLUMN purchase_plan.justification IS '论证/申请理由';
COMMENT ON COLUMN purchase_plan.approval_status IS '审批状态';
COMMENT ON COLUMN purchase_plan.approved_by IS '审核人';
COMMENT ON COLUMN purchase_plan.approved_at IS '审核时间';
COMMENT ON COLUMN purchase_plan.remark IS '备注';
COMMENT ON COLUMN purchase_plan.is_active IS '是否启用';
COMMENT ON COLUMN purchase_plan.created_at IS '创建时间';
COMMENT ON COLUMN purchase_plan.updated_at IS '更新时间';
COMMENT ON COLUMN purchase_plan.plan_type IS '计划类型';
COMMENT ON COLUMN purchase_plan.fund_source IS '资金来源';
COMMENT ON COLUMN purchase_plan.business_chain_no IS '采购业务链编号（计划→入库追溯）';
COMMENT ON COLUMN purchase_plan.is_large_equipment IS '是否大型医用设备';
COMMENT ON COLUMN purchase_plan.large_equipment_class IS '大型设备分类';
COMMENT ON COLUMN purchase_plan.benefit_analysis_url IS '效益分析附件URL';
COMMENT ON COLUMN purchase_plan.dept_argument_url IS '科室论证附件URL';
COMMENT ON COLUMN purchase_plan.version IS '乐观锁版本号';

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
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    use_dept_id UUID REFERENCES department(id),
    is_imported BOOLEAN DEFAULT false,
    registration_no VARCHAR(100),
    unit VARCHAR(20),
    brand_intent VARCHAR(100),
    is_metrology BOOLEAN DEFAULT false,
    udi_code VARCHAR(100)
);
COMMENT ON TABLE purchase_plan_item IS '采购计划明细表';
COMMENT ON COLUMN purchase_plan_item.id IS '主键';
COMMENT ON COLUMN purchase_plan_item.plan_id IS '采购计划';
COMMENT ON COLUMN purchase_plan_item.device_name IS '设备名称';
COMMENT ON COLUMN purchase_plan_item.category_id IS '设备分类';
COMMENT ON COLUMN purchase_plan_item.quantity IS '数量';
COMMENT ON COLUMN purchase_plan_item.estimated_price IS '预估单价';
COMMENT ON COLUMN purchase_plan_item.total_price IS '合计金额';
COMMENT ON COLUMN purchase_plan_item.specification IS '规格型号';
COMMENT ON COLUMN purchase_plan_item.justification IS '论证/申请理由';
COMMENT ON COLUMN purchase_plan_item.priority IS '优先级';
COMMENT ON COLUMN purchase_plan_item.created_at IS '创建时间';
COMMENT ON COLUMN purchase_plan_item.use_dept_id IS '使用科室';
COMMENT ON COLUMN purchase_plan_item.is_imported IS '是否进口设备';
COMMENT ON COLUMN purchase_plan_item.registration_no IS '医疗器械注册证号';
COMMENT ON COLUMN purchase_plan_item.unit IS '计量单位';
COMMENT ON COLUMN purchase_plan_item.brand_intent IS '意向品牌';
COMMENT ON COLUMN purchase_plan_item.is_metrology IS '是否计量器具';
COMMENT ON COLUMN purchase_plan_item.udi_code IS 'UDI唯一器械标识';

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    bid_sections TEXT,
    bid_evaluation TEXT,
    argument_report_url VARCHAR(500),
    budget_amount DECIMAL(15,2),
    approval_status VARCHAR(20) DEFAULT 'draft',
    business_chain_no VARCHAR(40),
    bid_agency VARCHAR(200),
    notice_date DATE,
    control_price DECIMAL(15,2),
    version INTEGER DEFAULT 1
);
COMMENT ON TABLE purchase_project IS '采购项目表';
COMMENT ON COLUMN purchase_project.id IS '主键';
COMMENT ON COLUMN purchase_project.project_code IS '项目编码';
COMMENT ON COLUMN purchase_project.project_name IS '项目名称';
COMMENT ON COLUMN purchase_project.plan_id IS '采购计划';
COMMENT ON COLUMN purchase_project.purchase_method IS 'purchase method';
COMMENT ON COLUMN purchase_project.supplier_id IS '供应商';
COMMENT ON COLUMN purchase_project.total_amount IS 'total金额';
COMMENT ON COLUMN purchase_project.bid_open_date IS 'bidopen日期';
COMMENT ON COLUMN purchase_project.award_date IS 'award日期';
COMMENT ON COLUMN purchase_project.status IS '状态';
COMMENT ON COLUMN purchase_project.created_at IS '创建时间';
COMMENT ON COLUMN purchase_project.updated_at IS '更新时间';
COMMENT ON COLUMN purchase_project.bid_sections IS '招标标段说明';
COMMENT ON COLUMN purchase_project.bid_evaluation IS '评标结果摘要';
COMMENT ON COLUMN purchase_project.argument_report_url IS '论证报告URL';
COMMENT ON COLUMN purchase_project.budget_amount IS '预算金额';
COMMENT ON COLUMN purchase_project.approval_status IS '审批状态';
COMMENT ON COLUMN purchase_project.business_chain_no IS '采购业务链编号（计划→入库追溯）';
COMMENT ON COLUMN purchase_project.bid_agency IS '招标代理机构';
COMMENT ON COLUMN purchase_project.notice_date IS '招标公告日期';
COMMENT ON COLUMN purchase_project.control_price IS '招标控制价';
COMMENT ON COLUMN purchase_project.version IS '乐观锁版本号';

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approval_status VARCHAR(20) DEFAULT 'draft',
    acceptance_status VARCHAR(20) DEFAULT 'pending',
    invoice_summary TEXT,
    delivery_deadline DATE,
    acceptance_report_url VARCHAR(500),
    paid_amount DECIMAL(15,2) DEFAULT 0,
    payment_progress DECIMAL(5,2) DEFAULT 0,
    business_chain_no VARCHAR(40),
    contract_type VARCHAR(30) DEFAULT 'purchase',
    performance_bond DECIMAL(15,2),
    registration_cert_url VARCHAR(500),
    version INTEGER DEFAULT 1
);
COMMENT ON TABLE purchase_contract IS '采购合同表';
COMMENT ON COLUMN purchase_contract.id IS '主键';
COMMENT ON COLUMN purchase_contract.contract_code IS '合同编码';
COMMENT ON COLUMN purchase_contract.contract_name IS '合同名称';
COMMENT ON COLUMN purchase_contract.project_id IS '采购项目';
COMMENT ON COLUMN purchase_contract.supplier_id IS '供应商';
COMMENT ON COLUMN purchase_contract.sign_date IS 'sign日期';
COMMENT ON COLUMN purchase_contract.start_date IS '开始日期';
COMMENT ON COLUMN purchase_contract.end_date IS '结束日期';
COMMENT ON COLUMN purchase_contract.contract_amount IS 'contract金额';
COMMENT ON COLUMN purchase_contract.warranty_period IS 'warranty period';
COMMENT ON COLUMN purchase_contract.contract_file_url IS 'contractfile附件地址';
COMMENT ON COLUMN purchase_contract.payment_terms IS 'payment terms';
COMMENT ON COLUMN purchase_contract.status IS '状态';
COMMENT ON COLUMN purchase_contract.remark IS '备注';
COMMENT ON COLUMN purchase_contract.created_at IS '创建时间';
COMMENT ON COLUMN purchase_contract.updated_at IS '更新时间';
COMMENT ON COLUMN purchase_contract.approval_status IS '审批状态';
COMMENT ON COLUMN purchase_contract.acceptance_status IS '安装验收状态';
COMMENT ON COLUMN purchase_contract.invoice_summary IS '发票汇总说明';
COMMENT ON COLUMN purchase_contract.delivery_deadline IS '交货期限';
COMMENT ON COLUMN purchase_contract.acceptance_report_url IS '验收报告URL';
COMMENT ON COLUMN purchase_contract.paid_amount IS '已付金额';
COMMENT ON COLUMN purchase_contract.payment_progress IS '付款进度（%）';
COMMENT ON COLUMN purchase_contract.business_chain_no IS '采购业务链编号（计划→入库追溯）';
COMMENT ON COLUMN purchase_contract.contract_type IS '合同类型';
COMMENT ON COLUMN purchase_contract.performance_bond IS '履约保证金';
COMMENT ON COLUMN purchase_contract.registration_cert_url IS '注册证附件URL';
COMMENT ON COLUMN purchase_contract.version IS '乐观锁版本号';

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approval_status VARCHAR(20) DEFAULT 'draft',
    finance_auditor_id UUID REFERENCES sys_user(id),
    finance_audit_date DATE,
    invoice_type VARCHAR(30),
    tax_amount DECIMAL(15,2),
    voucher_no VARCHAR(50)
);
COMMENT ON TABLE contract_payment IS '合同付款记录表';
COMMENT ON COLUMN contract_payment.id IS '主键';
COMMENT ON COLUMN contract_payment.contract_id IS '采购合同';
COMMENT ON COLUMN contract_payment.payment_no IS '付款编号';
COMMENT ON COLUMN contract_payment.payment_stage IS 'payment stage';
COMMENT ON COLUMN contract_payment.payment_amount IS 'payment金额';
COMMENT ON COLUMN contract_payment.payment_date IS 'payment日期';
COMMENT ON COLUMN contract_payment.invoice_no IS '发票编号';
COMMENT ON COLUMN contract_payment.invoice_url IS 'invoice附件地址';
COMMENT ON COLUMN contract_payment.payee_account IS 'payee account';
COMMENT ON COLUMN contract_payment.status IS '状态';
COMMENT ON COLUMN contract_payment.remark IS '备注';
COMMENT ON COLUMN contract_payment.created_at IS '创建时间';
COMMENT ON COLUMN contract_payment.updated_at IS '更新时间';
COMMENT ON COLUMN contract_payment.approval_status IS '审批状态';
COMMENT ON COLUMN contract_payment.finance_auditor_id IS '财务审核人';
COMMENT ON COLUMN contract_payment.finance_audit_date IS '财务审核日期';
COMMENT ON COLUMN contract_payment.invoice_type IS '发票类型';
COMMENT ON COLUMN contract_payment.tax_amount IS '税额';
COMMENT ON COLUMN contract_payment.voucher_no IS '财务凭证号';

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
    asset_category_id UUID REFERENCES asset_category(id),
    finance_category_id UUID REFERENCES finance_category(id),
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
    warehouse_id UUID REFERENCES warehouse(id),
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
    is_metrology BOOLEAN DEFAULT FALSE,
    metrology_type_code VARCHAR(50),
    is_maintain_device BOOLEAN DEFAULT FALSE,
    is_inspection_device BOOLEAN DEFAULT FALSE,
    pinyin_code VARCHAR(50),
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
    updated_by UUID REFERENCES sys_user(id),
    contract_id UUID REFERENCES purchase_contract(id),
    extension_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    specification VARCHAR(200),
    registration_no VARCHAR(100),
    production_date DATE,
    service_life_years INTEGER,
    calibration_period_days INTEGER,
    last_calibration_date DATE,
    next_calibration_date DATE,
    service_expiry_date DATE,
    is_shared_device BOOLEAN DEFAULT FALSE,
    shared_fee_mode VARCHAR(20),
    shared_fee_time_unit VARCHAR(10),
    shared_fee_unit_price DECIMAL(12,2),
    is_pm_device BOOLEAN DEFAULT FALSE,
    standby_current_max_ma DECIMAL(10,2),
    standby_current_min_ma DECIMAL(10,2)
);
COMMENT ON TABLE medical_device IS '设备档案主表';
COMMENT ON COLUMN medical_device.id IS '主键';
COMMENT ON COLUMN medical_device.device_code IS '设备编码';
COMMENT ON COLUMN medical_device.device_name IS '设备名称';
COMMENT ON COLUMN medical_device.brand IS '品牌';
COMMENT ON COLUMN medical_device.model IS '型号';
COMMENT ON COLUMN medical_device.serial_number IS '出厂序列号';
COMMENT ON COLUMN medical_device.category_id IS '设备分类';
COMMENT ON COLUMN medical_device.manufacturer_id IS '生产厂商';
COMMENT ON COLUMN medical_device.supplier_id IS '供应商';
COMMENT ON COLUMN medical_device.country_of_origin IS '原产国';
COMMENT ON COLUMN medical_device.is_imported IS '是否进口设备';
COMMENT ON COLUMN medical_device.original_value IS '原值';
COMMENT ON COLUMN medical_device.net_value IS '净值';
COMMENT ON COLUMN medical_device.depreciation_years IS '折旧年限';
COMMENT ON COLUMN medical_device.monthly_depreciation IS '月折旧额';
COMMENT ON COLUMN medical_device.accumulated_depreciation IS '累计折旧';
COMMENT ON COLUMN medical_device.financial_code IS '财务编码';
COMMENT ON COLUMN medical_device.campus_id IS '所属院区';
COMMENT ON COLUMN medical_device.building_id IS '所属建筑物';
COMMENT ON COLUMN medical_device.dept_id IS '所属科室';
COMMENT ON COLUMN medical_device.location_detail IS '详细位置';
COMMENT ON COLUMN medical_device.purchase_date IS '采购日期';
COMMENT ON COLUMN medical_device.acceptance_date IS '验收日期';
COMMENT ON COLUMN medical_device.enable_date IS '启用日期';
COMMENT ON COLUMN medical_device.warranty_end_date IS '保修截止日期';
COMMENT ON COLUMN medical_device.device_status IS '设备运行状态';
COMMENT ON COLUMN medical_device.risk_level IS '风险等级';
COMMENT ON COLUMN medical_device.is_life_support IS '是否生命支持设备';
COMMENT ON COLUMN medical_device.is_emergency IS '是否应急设备';
COMMENT ON COLUMN medical_device.qr_code_url IS '二维码地址';
COMMENT ON COLUMN medical_device.label_printed IS '是否已打印标签';
COMMENT ON COLUMN medical_device.manual_files IS '说明书附件JSON';
COMMENT ON COLUMN medical_device.certificate_files IS '证书附件JSON';
COMMENT ON COLUMN medical_device.remark IS '备注';
COMMENT ON COLUMN medical_device.is_active IS '是否启用';
COMMENT ON COLUMN medical_device.created_at IS '创建时间';
COMMENT ON COLUMN medical_device.updated_at IS '更新时间';
COMMENT ON COLUMN medical_device.created_by IS '制单人';
COMMENT ON COLUMN medical_device.updated_by IS '最后修改人';
COMMENT ON COLUMN medical_device.contract_id IS '采购合同';
COMMENT ON COLUMN medical_device.extension_data IS '扩展字段JSON（未建模列）';
COMMENT ON COLUMN medical_device.specification IS '规格型号';
COMMENT ON COLUMN medical_device.registration_no IS '医疗器械注册证号';
COMMENT ON COLUMN medical_device.production_date IS '生产日期';
COMMENT ON COLUMN medical_device.service_life_years IS '设计使用年限（年）';
COMMENT ON COLUMN medical_device.calibration_period_days IS '计量检定周期（天）';
COMMENT ON COLUMN medical_device.last_calibration_date IS '上次检定日期';
COMMENT ON COLUMN medical_device.next_calibration_date IS '下次检定日期';
COMMENT ON COLUMN medical_device.service_expiry_date IS '使用年限到期日';
COMMENT ON COLUMN medical_device.is_shared_device IS '是否公用设备';
COMMENT ON COLUMN medical_device.is_pm_device IS '是否预防性维护设备';
COMMENT ON COLUMN medical_device.standby_current_max_ma IS '待机电流上限(mA)';
COMMENT ON COLUMN medical_device.standby_current_min_ma IS '待机电流下限(mA)';

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
COMMENT ON TABLE device_accessory IS '设备附属低值品表';
COMMENT ON COLUMN device_accessory.id IS '主键';
COMMENT ON COLUMN device_accessory.device_id IS '关联设备';
COMMENT ON COLUMN device_accessory.accessory_name IS 'accessory名称';
COMMENT ON COLUMN device_accessory.accessory_code IS 'accessory编码';
COMMENT ON COLUMN device_accessory.quantity IS '数量';
COMMENT ON COLUMN device_accessory.unit_price IS 'unit price';
COMMENT ON COLUMN device_accessory.purchase_date IS '采购日期';
COMMENT ON COLUMN device_accessory.remark IS '备注';
COMMENT ON COLUMN device_accessory.created_at IS '创建时间';

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    supplier_id UUID REFERENCES supplier(id),
    acceptance_id UUID,
    project_id UUID REFERENCES purchase_project(id),
    plan_id UUID REFERENCES purchase_plan(id),
    trace_no VARCHAR(60),
    business_chain_no VARCHAR(40),
    warehouse_id UUID REFERENCES warehouse(id)
);
COMMENT ON TABLE device_entry IS '设备入库记录表';
COMMENT ON COLUMN device_entry.id IS '主键';
COMMENT ON COLUMN device_entry.entry_no IS '入库编号';
COMMENT ON COLUMN device_entry.contract_id IS '采购合同';
COMMENT ON COLUMN device_entry.entry_date IS 'entry日期';
COMMENT ON COLUMN device_entry.entry_type IS 'entry type';
COMMENT ON COLUMN device_entry.operator_id IS '关联操作人';
COMMENT ON COLUMN device_entry.quality_check_passed IS 'quality check passed';
COMMENT ON COLUMN device_entry.quality_checker_id IS '关联qualitychecker';
COMMENT ON COLUMN device_entry.quality_check_date IS 'qualitycheck日期';
COMMENT ON COLUMN device_entry.quality_check_report_url IS 'qualitycheckreport附件地址';
COMMENT ON COLUMN device_entry.installation_completed IS 'installation completed';
COMMENT ON COLUMN device_entry.installer_id IS '关联安装人';
COMMENT ON COLUMN device_entry.installation_date IS 'installation日期';
COMMENT ON COLUMN device_entry.installation_report_url IS 'installationreport附件地址';
COMMENT ON COLUMN device_entry.status IS '状态';
COMMENT ON COLUMN device_entry.remark IS '备注';
COMMENT ON COLUMN device_entry.created_at IS '创建时间';
COMMENT ON COLUMN device_entry.updated_at IS '更新时间';
COMMENT ON COLUMN device_entry.supplier_id IS '供应商';
COMMENT ON COLUMN device_entry.acceptance_id IS '安装验收单';
COMMENT ON COLUMN device_entry.project_id IS '采购项目';
COMMENT ON COLUMN device_entry.plan_id IS '采购计划';
COMMENT ON COLUMN device_entry.trace_no IS '入库追溯编号';
COMMENT ON COLUMN device_entry.business_chain_no IS '采购业务链编号（计划→入库追溯）';

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
COMMENT ON TABLE device_entry_item IS '设备入库明细表';
COMMENT ON COLUMN device_entry_item.id IS '主键';
COMMENT ON COLUMN device_entry_item.entry_id IS '关联入库';
COMMENT ON COLUMN device_entry_item.device_id IS '关联设备';
COMMENT ON COLUMN device_entry_item.device_name IS '设备名称';
COMMENT ON COLUMN device_entry_item.brand IS '品牌';
COMMENT ON COLUMN device_entry_item.model IS '型号';
COMMENT ON COLUMN device_entry_item.serial_number IS '出厂序列号';
COMMENT ON COLUMN device_entry_item.quantity IS '数量';
COMMENT ON COLUMN device_entry_item.unit_price IS 'unit price';
COMMENT ON COLUMN device_entry_item.total_price IS '合计金额';
COMMENT ON COLUMN device_entry_item.is_accepted IS '是否accepted';
COMMENT ON COLUMN device_entry_item.accepted_device_id IS '关联accepteddevice';
COMMENT ON COLUMN device_entry_item.created_at IS '创建时间';

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
    from_warehouse_id UUID REFERENCES warehouse(id),
    to_warehouse_id UUID REFERENCES warehouse(id),
    applicant_id UUID REFERENCES sys_user(id),
    approver_id UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    transfer_date DATE,
    reason TEXT,
    status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approval_status VARCHAR(20)
);
COMMENT ON TABLE asset_transfer IS '资产流转记录表';
COMMENT ON COLUMN asset_transfer.id IS '主键';
COMMENT ON COLUMN asset_transfer.transfer_no IS '流转编号';
COMMENT ON COLUMN asset_transfer.transfer_type IS 'transfer type';
COMMENT ON COLUMN asset_transfer.device_id IS '关联设备';
COMMENT ON COLUMN asset_transfer.from_dept_id IS '关联fromdept';
COMMENT ON COLUMN asset_transfer.to_dept_id IS '关联todept';
COMMENT ON COLUMN asset_transfer.from_campus_id IS '关联fromcampus';
COMMENT ON COLUMN asset_transfer.to_campus_id IS '关联tocampus';
COMMENT ON COLUMN asset_transfer.applicant_id IS '关联申请人';
COMMENT ON COLUMN asset_transfer.approver_id IS '关联审批人';
COMMENT ON COLUMN asset_transfer.approved_at IS '审核时间';
COMMENT ON COLUMN asset_transfer.transfer_date IS 'transfer日期';
COMMENT ON COLUMN asset_transfer.reason IS 'reason';
COMMENT ON COLUMN asset_transfer.status IS '状态';
COMMENT ON COLUMN asset_transfer.remark IS '备注';
COMMENT ON COLUMN asset_transfer.created_at IS '创建时间';
COMMENT ON COLUMN asset_transfer.updated_at IS '更新时间';
COMMENT ON COLUMN asset_transfer.approval_status IS '审批状态';

-- 4.6 资产盘点表
CREATE TABLE inventory_check (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    check_no VARCHAR(30) UNIQUE NOT NULL,
    check_name VARCHAR(200),
    check_year INTEGER,
    check_type VARCHAR(20) DEFAULT 'annual',
    campus_id UUID REFERENCES campus(id),
    dept_id UUID REFERENCES department(id),
    warehouse_id UUID REFERENCES warehouse(id),
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
    audit_status VARCHAR(20) DEFAULT 'pending',
    report_url VARCHAR(500),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES sys_user(id),
    approved_by UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE
);
COMMENT ON TABLE inventory_check IS '资产盘点表';
COMMENT ON COLUMN inventory_check.id IS '主键';
COMMENT ON COLUMN inventory_check.check_no IS '盘点单号';
COMMENT ON COLUMN inventory_check.check_name IS '盘点名称';
COMMENT ON COLUMN inventory_check.check_year IS '盘点年度';
COMMENT ON COLUMN inventory_check.check_type IS '盘点类型';
COMMENT ON COLUMN inventory_check.campus_id IS '所属院区';
COMMENT ON COLUMN inventory_check.dept_id IS '所属科室';
COMMENT ON COLUMN inventory_check.start_date IS '开始日期';
COMMENT ON COLUMN inventory_check.end_date IS '结束日期';
COMMENT ON COLUMN inventory_check.actual_start_at IS '实际开始时间';
COMMENT ON COLUMN inventory_check.actual_end_at IS '实际结束时间';
COMMENT ON COLUMN inventory_check.checker_id IS '盘点人';
COMMENT ON COLUMN inventory_check.supervisor_id IS '监盘人';
COMMENT ON COLUMN inventory_check.total_count IS '应盘数量';
COMMENT ON COLUMN inventory_check.checked_count IS '已盘数量';
COMMENT ON COLUMN inventory_check.matched_count IS '盘实相符数量';
COMMENT ON COLUMN inventory_check.mismatch_count IS '盘实不符数量';
COMMENT ON COLUMN inventory_check.missing_count IS '盘亏数量';
COMMENT ON COLUMN inventory_check.status IS '状态';
COMMENT ON COLUMN inventory_check.audit_status IS '审核状态';
COMMENT ON COLUMN inventory_check.report_url IS '报告附件URL';
COMMENT ON COLUMN inventory_check.remark IS '备注';
COMMENT ON COLUMN inventory_check.created_at IS '创建时间';
COMMENT ON COLUMN inventory_check.updated_at IS '更新时间';
COMMENT ON COLUMN inventory_check.created_by IS '制单人';
COMMENT ON COLUMN inventory_check.approved_by IS '审核人';
COMMENT ON COLUMN inventory_check.approved_at IS '审核时间';

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
COMMENT ON TABLE inventory_check_item IS '资产盘点明细表';
COMMENT ON COLUMN inventory_check_item.id IS '主键';
COMMENT ON COLUMN inventory_check_item.check_id IS '所属盘点单';
COMMENT ON COLUMN inventory_check_item.device_id IS '关联设备';
COMMENT ON COLUMN inventory_check_item.device_code IS '设备编码';
COMMENT ON COLUMN inventory_check_item.device_name IS '设备名称';
COMMENT ON COLUMN inventory_check_item.expected_location IS '账面位置';
COMMENT ON COLUMN inventory_check_item.actual_location IS '实际位置';
COMMENT ON COLUMN inventory_check_item.is_found IS '是否找到设备';
COMMENT ON COLUMN inventory_check_item.is_matched IS '是否盘实相符';
COMMENT ON COLUMN inventory_check_item.condition_status IS '设备状况';
COMMENT ON COLUMN inventory_check_item.check_date IS '盘点/巡检日期';
COMMENT ON COLUMN inventory_check_item.checker_id IS '盘点人';
COMMENT ON COLUMN inventory_check_item.photos IS '现场照片JSON';
COMMENT ON COLUMN inventory_check_item.remark IS '备注';
COMMENT ON COLUMN inventory_check_item.created_at IS '创建时间';

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approval_status VARCHAR(20)
);
COMMENT ON TABLE device_scrap IS '设备报废表';
COMMENT ON COLUMN device_scrap.id IS '主键';
COMMENT ON COLUMN device_scrap.scrap_no IS '报废编号';
COMMENT ON COLUMN device_scrap.device_id IS '关联设备';
COMMENT ON COLUMN device_scrap.device_code IS '设备编码';
COMMENT ON COLUMN device_scrap.device_name IS '设备名称';
COMMENT ON COLUMN device_scrap.scrap_reason IS 'scrap reason';
COMMENT ON COLUMN device_scrap.scrap_type IS 'scrap type';
COMMENT ON COLUMN device_scrap.applicant_id IS '关联申请人';
COMMENT ON COLUMN device_scrap.application_date IS 'application日期';
COMMENT ON COLUMN device_scrap.evaluator_id IS '关联evaluator';
COMMENT ON COLUMN device_scrap.evaluation_result IS 'evaluation result';
COMMENT ON COLUMN device_scrap.residual_value IS 'residual value';
COMMENT ON COLUMN device_scrap.approver_id IS '关联审批人';
COMMENT ON COLUMN device_scrap.approved_at IS '审核时间';
COMMENT ON COLUMN device_scrap.scrap_date IS 'scrap日期';
COMMENT ON COLUMN device_scrap.disposal_method IS 'disposal method';
COMMENT ON COLUMN device_scrap.disposal_date IS 'disposal日期';
COMMENT ON COLUMN device_scrap.status IS '状态';
COMMENT ON COLUMN device_scrap.remark IS '备注';
COMMENT ON COLUMN device_scrap.created_at IS '创建时间';
COMMENT ON COLUMN device_scrap.updated_at IS '更新时间';
COMMENT ON COLUMN device_scrap.approval_status IS '审批状态';

-- 4.x 资产标签打印记录（附录 P）
CREATE TABLE device_label_print_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID NOT NULL REFERENCES medical_device(id),
    device_code VARCHAR(20) NOT NULL,
    device_name VARCHAR(200),
    printed_by UUID REFERENCES sys_user(id),
    printed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    template_code VARCHAR(50) DEFAULT 'default',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);
COMMENT ON TABLE device_label_print_log IS '资产标签打印记录';
COMMENT ON COLUMN device_label_print_log.device_code IS '打印时设备编码快照（二维码载荷）';

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
COMMENT ON TABLE fault_type_dict IS '故障类型字典表';
COMMENT ON COLUMN fault_type_dict.id IS '主键';
COMMENT ON COLUMN fault_type_dict.fault_code IS 'fault编码';
COMMENT ON COLUMN fault_type_dict.fault_name IS 'fault名称';
COMMENT ON COLUMN fault_type_dict.parent_id IS '关联上级';
COMMENT ON COLUMN fault_type_dict.level IS '层级';
COMMENT ON COLUMN fault_type_dict.is_active IS '是否启用';
COMMENT ON COLUMN fault_type_dict.created_at IS '创建时间';

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
COMMENT ON TABLE engineer IS '工程师表';
COMMENT ON COLUMN engineer.id IS '主键';
COMMENT ON COLUMN engineer.user_id IS '关联用户';
COMMENT ON COLUMN engineer.engineer_no IS '工程师编号';
COMMENT ON COLUMN engineer.real_name IS '真实姓名';
COMMENT ON COLUMN engineer.specialty IS 'specialty';
COMMENT ON COLUMN engineer.skill_levels IS 'skill levels';
COMMENT ON COLUMN engineer.phone IS '手机号';
COMMENT ON COLUMN engineer.email IS '电子邮箱';
COMMENT ON COLUMN engineer.is_on_duty IS '是否onduty';
COMMENT ON COLUMN engineer.workload_score IS 'workload score';
COMMENT ON COLUMN engineer.created_at IS '创建时间';
COMMENT ON COLUMN engineer.updated_at IS '更新时间';

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
    assigned_user_id UUID REFERENCES sys_user(id),
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
    repair_sub_status VARCHAR(30),
    dispatch_started_at TIMESTAMP WITH TIME ZONE,
    accepted_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE repair_workorder IS '维修工单表';
COMMENT ON COLUMN repair_workorder.id IS '主键';
COMMENT ON COLUMN repair_workorder.wo_no IS '工单编号';
COMMENT ON COLUMN repair_workorder.device_id IS '关联设备';
COMMENT ON COLUMN repair_workorder.device_code IS '设备编码';
COMMENT ON COLUMN repair_workorder.device_name IS '设备名称';
COMMENT ON COLUMN repair_workorder.reporter_id IS '关联reporter';
COMMENT ON COLUMN repair_workorder.report_dept_id IS '关联reportdept';
COMMENT ON COLUMN repair_workorder.report_method IS 'report method';
COMMENT ON COLUMN repair_workorder.report_time IS '报修时间';
COMMENT ON COLUMN repair_workorder.fault_description IS 'fault description';
COMMENT ON COLUMN repair_workorder.fault_photos IS 'fault photos';
COMMENT ON COLUMN repair_workorder.urgency_level IS 'urgency level';
COMMENT ON COLUMN repair_workorder.fault_type_id IS '关联faulttype';
COMMENT ON COLUMN repair_workorder.fault_category IS 'fault category';
COMMENT ON COLUMN repair_workorder.assigned_user_id IS '指派维修负责人';
COMMENT ON COLUMN repair_workorder.assigned_at IS 'assigned时间';
COMMENT ON COLUMN repair_workorder.assigner_id IS '关联assigner';
COMMENT ON COLUMN repair_workorder.response_time IS 'response time';
COMMENT ON COLUMN repair_workorder.arrival_time IS 'arrival time';
COMMENT ON COLUMN repair_workorder.repair_type IS 'repair type';
COMMENT ON COLUMN repair_workorder.repair_start_time IS 'repair start time';
COMMENT ON COLUMN repair_workorder.repair_end_time IS 'repair end time';
COMMENT ON COLUMN repair_workorder.repair_duration_hours IS 'repair duration hours';
COMMENT ON COLUMN repair_workorder.downtime_hours IS 'downtime hours';
COMMENT ON COLUMN repair_workorder.solution_description IS 'solution description';
COMMENT ON COLUMN repair_workorder.spare_parts_used IS 'spare parts used';
COMMENT ON COLUMN repair_workorder.labor_cost IS 'labor cost';
COMMENT ON COLUMN repair_workorder.parts_cost IS 'parts cost';
COMMENT ON COLUMN repair_workorder.total_cost IS 'total cost';
COMMENT ON COLUMN repair_workorder.invoice_no IS '发票编号';
COMMENT ON COLUMN repair_workorder.invoice_url IS 'invoice附件地址';
COMMENT ON COLUMN repair_workorder.verifier_id IS '关联verifier';
COMMENT ON COLUMN repair_workorder.verify_time IS 'verify time';
COMMENT ON COLUMN repair_workorder.verify_result IS 'verify result';
COMMENT ON COLUMN repair_workorder.verify_comment IS 'verify comment';
COMMENT ON COLUMN repair_workorder.satisfaction_rating IS 'satisfaction rating';
COMMENT ON COLUMN repair_workorder.satisfaction_comment IS 'satisfaction comment';
COMMENT ON COLUMN repair_workorder.status IS '状态';
COMMENT ON COLUMN repair_workorder.repair_sub_status IS '维修子状态（仅 repairing 时有效）';
COMMENT ON COLUMN repair_workorder.dispatch_started_at IS '开始派单时间';
COMMENT ON COLUMN repair_workorder.accepted_at IS '工程师接单时间';
COMMENT ON COLUMN repair_workorder.closed_at IS '工单关闭时间';
COMMENT ON COLUMN repair_workorder.remark IS '备注';
COMMENT ON COLUMN repair_workorder.created_at IS '创建时间';
COMMENT ON COLUMN repair_workorder.updated_at IS '更新时间';

-- 5.3.1 维修工单事件流水（时间轴）
CREATE TABLE repair_workorder_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workorder_id UUID NOT NULL REFERENCES repair_workorder(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    from_sub_status VARCHAR(30),
    to_sub_status VARCHAR(30),
    operator_id UUID,
    user_id UUID,
    from_user_id UUID,
    to_user_id UUID,
    remark TEXT,
    extra_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE repair_workorder_event IS '维修工单事件流水（时间轴）';
COMMENT ON COLUMN repair_workorder_event.id IS '主键';
COMMENT ON COLUMN repair_workorder_event.workorder_id IS '关联维修工单';
COMMENT ON COLUMN repair_workorder_event.event_type IS '事件类型';
COMMENT ON COLUMN repair_workorder_event.from_status IS '变更前主状态';
COMMENT ON COLUMN repair_workorder_event.to_status IS '变更后主状态';
COMMENT ON COLUMN repair_workorder_event.from_sub_status IS '变更前子状态';
COMMENT ON COLUMN repair_workorder_event.to_sub_status IS '变更后子状态';
COMMENT ON COLUMN repair_workorder_event.operator_id IS '操作人';
COMMENT ON COLUMN repair_workorder_event.user_id IS '相关维修负责人';
COMMENT ON COLUMN repair_workorder_event.from_user_id IS '转派前负责人';
COMMENT ON COLUMN repair_workorder_event.to_user_id IS '转派后负责人';
COMMENT ON COLUMN repair_workorder_event.remark IS '备注';
COMMENT ON COLUMN repair_workorder_event.extra_json IS '扩展JSON';
COMMENT ON COLUMN repair_workorder_event.created_at IS '事件时间';

-- 5.3.2 维修工单流程业务记录（派工/接单/转派/维修/验收等操作明细，主单仅维护状态）
CREATE TABLE repair_workorder_process (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workorder_id UUID NOT NULL REFERENCES repair_workorder(id) ON DELETE CASCADE,
    action_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    from_sub_status VARCHAR(30),
    to_sub_status VARCHAR(30),
    user_id UUID,
    from_user_id UUID,
    to_user_id UUID,
    operator_id UUID,
    solution_description TEXT,
    labor_cost DECIMAL(10,2),
    parts_cost DECIMAL(10,2),
    total_cost DECIMAL(10,2),
    verify_result VARCHAR(20),
    verify_comment TEXT,
    satisfaction_rating INTEGER,
    satisfaction_comment TEXT,
    skip_verify BOOLEAN,
    remark TEXT,
    extra_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);
COMMENT ON TABLE repair_workorder_process IS '维修工单流程业务记录（派工/维修/验收等操作明细）';
COMMENT ON COLUMN repair_workorder_process.id IS '主键';
COMMENT ON COLUMN repair_workorder_process.workorder_id IS '关联维修工单';
COMMENT ON COLUMN repair_workorder_process.action_type IS '操作类型：dispatch/accept/transfer/start_repair/sub_status/complete/verify_pass/verify_fail/suspend/resume/cancel';
COMMENT ON COLUMN repair_workorder_process.from_status IS '操作前主状态';
COMMENT ON COLUMN repair_workorder_process.to_status IS '操作后主状态';
COMMENT ON COLUMN repair_workorder_process.from_sub_status IS '操作前子状态';
COMMENT ON COLUMN repair_workorder_process.to_sub_status IS '操作后子状态';
COMMENT ON COLUMN repair_workorder_process.user_id IS '相关维修负责人';
COMMENT ON COLUMN repair_workorder_process.from_user_id IS '转派前负责人';
COMMENT ON COLUMN repair_workorder_process.to_user_id IS '转派后负责人';
COMMENT ON COLUMN repair_workorder_process.operator_id IS '操作人';
COMMENT ON COLUMN repair_workorder_process.solution_description IS '处理方案（完工）';
COMMENT ON COLUMN repair_workorder_process.labor_cost IS '人工费';
COMMENT ON COLUMN repair_workorder_process.parts_cost IS '配件费';
COMMENT ON COLUMN repair_workorder_process.total_cost IS '总费用';
COMMENT ON COLUMN repair_workorder_process.verify_result IS '验收结果';
COMMENT ON COLUMN repair_workorder_process.verify_comment IS '验收意见';
COMMENT ON COLUMN repair_workorder_process.satisfaction_rating IS '满意度';
COMMENT ON COLUMN repair_workorder_process.satisfaction_comment IS '满意度备注';
COMMENT ON COLUMN repair_workorder_process.skip_verify IS '是否跳过验收直接结案';
COMMENT ON COLUMN repair_workorder_process.remark IS '备注';
COMMENT ON COLUMN repair_workorder_process.extra_json IS '扩展JSON';
COMMENT ON COLUMN repair_workorder_process.created_at IS '操作时间';
COMMENT ON COLUMN repair_workorder_process.updated_at IS '更新时间';
COMMENT ON COLUMN repair_workorder_process.created_by IS '创建者';
COMMENT ON COLUMN repair_workorder_process.updated_by IS '更新者';
COMMENT ON COLUMN repair_workorder_process.is_deleted IS '删除标志';
COMMENT ON COLUMN repair_workorder_process.deleted_at IS '删除时间';
COMMENT ON COLUMN repair_workorder_process.deleted_by IS '删除者';

-- 5.3.3 维修进程类型（主数据）
CREATE TABLE repair_process_type (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type_code VARCHAR(40) NOT NULL UNIQUE,
    type_name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    can_add_parts BOOLEAN NOT NULL DEFAULT FALSE,
    can_engineer_add BOOLEAN NOT NULL DEFAULT FALSE,
    engineer_add_rule VARCHAR(40),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);
COMMENT ON TABLE repair_process_type IS '维修进程类型主数据';
COMMENT ON COLUMN repair_process_type.type_code IS '类型编码';
COMMENT ON COLUMN repair_process_type.type_name IS '类型名称';
COMMENT ON COLUMN repair_process_type.can_add_parts IS '是否允许添加配件';
COMMENT ON COLUMN repair_process_type.can_engineer_add IS '工程师是否可主动新增';
COMMENT ON COLUMN repair_process_type.engineer_add_rule IS '工程师新增规则扩展';

-- 5.3.4 维修工单进程段
CREATE TABLE repair_workorder_segment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workorder_id UUID NOT NULL REFERENCES repair_workorder(id) ON DELETE CASCADE,
    process_type_id UUID NOT NULL REFERENCES repair_process_type(id),
    user_id UUID REFERENCES sys_user(id),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP WITH TIME ZONE,
    remark TEXT,
    verify_comment TEXT,
    auto_created BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);
COMMENT ON TABLE repair_workorder_segment IS '维修工单进程段';
COMMENT ON COLUMN repair_workorder_segment.workorder_id IS '关联维修工单';
COMMENT ON COLUMN repair_workorder_segment.process_type_id IS '进程类型';
COMMENT ON COLUMN repair_workorder_segment.user_id IS '负责人';
COMMENT ON COLUMN repair_workorder_segment.started_at IS '开始时间';
COMMENT ON COLUMN repair_workorder_segment.ended_at IS '结束时间';

-- 5.4 备件库表
CREATE TABLE spare_part (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    part_code VARCHAR(30) UNIQUE NOT NULL,
    part_name VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    specification TEXT,
    applicable_devices JSONB,
    supplier_id UUID REFERENCES supplier(id),
    manufacturer_id UUID REFERENCES manufacturer(id),
    unit_id UUID REFERENCES unit_dict(id),
    model VARCHAR(100),
    unit_price DECIMAL(10,2),
    warehouse_id UUID REFERENCES warehouse(id),
    stock_quantity INTEGER DEFAULT 0,
    min_stock INTEGER,
    max_stock INTEGER,
    storage_location VARCHAR(200),
    remark TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE spare_part IS '备件库表';
COMMENT ON COLUMN spare_part.id IS '主键';
COMMENT ON COLUMN spare_part.part_code IS 'part编码';
COMMENT ON COLUMN spare_part.part_name IS 'part名称';
COMMENT ON COLUMN spare_part.category IS 'category';
COMMENT ON COLUMN spare_part.specification IS '规格型号';
COMMENT ON COLUMN spare_part.applicable_devices IS 'applicable devices';
COMMENT ON COLUMN spare_part.supplier_id IS '供应商';
COMMENT ON COLUMN spare_part.unit_price IS 'unit price';
COMMENT ON COLUMN spare_part.stock_quantity IS 'stock quantity';
COMMENT ON COLUMN spare_part.min_stock IS 'min stock';
COMMENT ON COLUMN spare_part.max_stock IS 'max stock';
COMMENT ON COLUMN spare_part.storage_location IS 'storage location';
COMMENT ON COLUMN spare_part.remark IS '备注';
COMMENT ON COLUMN spare_part.is_active IS '是否启用';
COMMENT ON COLUMN spare_part.created_at IS '创建时间';
COMMENT ON COLUMN spare_part.updated_at IS '更新时间';

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
COMMENT ON TABLE spare_part_usage IS '备件使用记录表';
COMMENT ON COLUMN spare_part_usage.id IS '主键';
COMMENT ON COLUMN spare_part_usage.workorder_id IS '关联workorder';
COMMENT ON COLUMN spare_part_usage.part_id IS '关联part';
COMMENT ON COLUMN spare_part_usage.quantity IS '数量';
COMMENT ON COLUMN spare_part_usage.unit_price IS 'unit price';
COMMENT ON COLUMN spare_part_usage.total_price IS '合计金额';
COMMENT ON COLUMN spare_part_usage.used_at IS 'used时间';
COMMENT ON COLUMN spare_part_usage.operator_id IS '关联操作人';

-- 5.5.1 进程段配件明细
CREATE TABLE repair_workorder_segment_part (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    segment_id UUID NOT NULL REFERENCES repair_workorder_segment(id) ON DELETE CASCADE,
    spare_part_id UUID REFERENCES spare_part(id),
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price DECIMAL(10,2),
    total_price DECIMAL(10,2),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);
COMMENT ON TABLE repair_workorder_segment_part IS '维修进程段配件明细';

-- ================================================================================
-- 6. 保养管理模块
-- ================================================================================
-- 6.0 保养级别表
CREATE TABLE maintenance_level (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    level_code VARCHAR(30) NOT NULL UNIQUE,
    level_name VARCHAR(100) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE maintenance_level IS '保养级别表';
COMMENT ON COLUMN maintenance_level.level_code IS '级别编码';
COMMENT ON COLUMN maintenance_level.level_name IS '级别名称';

-- 6.1 保养模板表
CREATE TABLE maintenance_template (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_code VARCHAR(30),
    template_name VARCHAR(200) NOT NULL,
    maintenance_level VARCHAR(20) NOT NULL,
    maintenance_level_id UUID REFERENCES maintenance_level(id),
    category_id UUID REFERENCES medical_device_category(id),
    items JSONB NOT NULL DEFAULT '[]'::jsonb,
    description TEXT,
    estimated_duration INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE maintenance_template IS '保养模板表';
COMMENT ON COLUMN maintenance_template.id IS '主键';
COMMENT ON COLUMN maintenance_template.template_name IS 'template名称';
COMMENT ON COLUMN maintenance_template.maintenance_level IS 'maintenance level';
COMMENT ON COLUMN maintenance_template.category_id IS '设备分类';
COMMENT ON COLUMN maintenance_template.items IS 'items';
COMMENT ON COLUMN maintenance_template.estimated_duration IS 'estimated duration';
COMMENT ON COLUMN maintenance_template.is_active IS '是否启用';
COMMENT ON COLUMN maintenance_template.created_at IS '创建时间';
COMMENT ON COLUMN maintenance_template.updated_at IS '更新时间';

-- 6.1.1 保养模板内容项
CREATE TABLE maintenance_template_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE maintenance_template_item IS '保养模板内容项';

-- 6.2 保养计划表
CREATE TABLE maintenance_plan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_name VARCHAR(200),
    device_id UUID REFERENCES medical_device(id),
    template_id UUID REFERENCES maintenance_template(id),
    maintenance_level VARCHAR(20) NOT NULL,
    cycle_type VARCHAR(20) NOT NULL,
    cycle_value INTEGER,
    cycle_days INTEGER,
    next_due_date DATE NOT NULL,
    reminder_days_before INTEGER DEFAULT 7,
    assigned_engineer_id UUID REFERENCES engineer(id),
    status VARCHAR(20) DEFAULT 'active',
    approval_status VARCHAR(20) DEFAULT 'draft',
    created_by UUID REFERENCES sys_user(id),
    approved_by UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    plan_code VARCHAR(30),
    last_maintained_at DATE,
    dept_id UUID REFERENCES department(id)
);
COMMENT ON TABLE maintenance_plan IS '保养计划表';
COMMENT ON COLUMN maintenance_plan.id IS '主键';
COMMENT ON COLUMN maintenance_plan.plan_name IS '计划名称';
COMMENT ON COLUMN maintenance_plan.device_id IS '关联设备';
COMMENT ON COLUMN maintenance_plan.template_id IS '关联template';
COMMENT ON COLUMN maintenance_plan.maintenance_level IS 'maintenance level';
COMMENT ON COLUMN maintenance_plan.cycle_type IS 'cycle type';
COMMENT ON COLUMN maintenance_plan.cycle_value IS 'cycle value';
COMMENT ON COLUMN maintenance_plan.next_due_date IS '下次到期日';
COMMENT ON COLUMN maintenance_plan.reminder_days_before IS 'reminder days before';
COMMENT ON COLUMN maintenance_plan.assigned_engineer_id IS '指派工程师';
COMMENT ON COLUMN maintenance_plan.status IS '状态';
COMMENT ON COLUMN maintenance_plan.remark IS '备注';
COMMENT ON COLUMN maintenance_plan.created_at IS '创建时间';
COMMENT ON COLUMN maintenance_plan.updated_at IS '更新时间';
COMMENT ON COLUMN maintenance_plan.plan_code IS '订阅计划编码';
COMMENT ON COLUMN maintenance_plan.last_maintained_at IS '上次保养日期';
COMMENT ON COLUMN maintenance_plan.dept_id IS '所属科室';

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
COMMENT ON TABLE maintenance_record IS '保养执行记录表';
COMMENT ON COLUMN maintenance_record.id IS '主键';
COMMENT ON COLUMN maintenance_record.record_no IS '记录编号';
COMMENT ON COLUMN maintenance_record.plan_id IS '采购计划';
COMMENT ON COLUMN maintenance_record.device_id IS '关联设备';
COMMENT ON COLUMN maintenance_record.device_code IS '设备编码';
COMMENT ON COLUMN maintenance_record.device_name IS '设备名称';
COMMENT ON COLUMN maintenance_record.maintenance_level IS 'maintenance level';
COMMENT ON COLUMN maintenance_record.template_id IS '关联template';
COMMENT ON COLUMN maintenance_record.executor_id IS '关联执行人';
COMMENT ON COLUMN maintenance_record.execute_start_time IS '执行开始时间';
COMMENT ON COLUMN maintenance_record.execute_end_time IS 'execute end time';
COMMENT ON COLUMN maintenance_record.duration_minutes IS 'duration minutes';
COMMENT ON COLUMN maintenance_record.items_result IS 'items result';
COMMENT ON COLUMN maintenance_record.overall_result IS 'overall result';
COMMENT ON COLUMN maintenance_record.issues_found IS 'issues found';
COMMENT ON COLUMN maintenance_record.photos IS '现场照片JSON';
COMMENT ON COLUMN maintenance_record.signature_url IS 'signature附件地址';
COMMENT ON COLUMN maintenance_record.reviewer_id IS '关联reviewer';
COMMENT ON COLUMN maintenance_record.review_time IS 'review time';
COMMENT ON COLUMN maintenance_record.review_comment IS 'review comment';
COMMENT ON COLUMN maintenance_record.status IS '状态';
COMMENT ON COLUMN maintenance_record.remark IS '备注';
COMMENT ON COLUMN maintenance_record.created_at IS '创建时间';
COMMENT ON COLUMN maintenance_record.updated_at IS '更新时间';

-- 6.4 保养执行单
CREATE TABLE maintenance_execution (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE maintenance_execution IS '保养执行单';

CREATE TABLE maintenance_execution_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE maintenance_execution_item IS '保养执行明细（按设备）';

CREATE TABLE maintenance_execution_result (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE maintenance_execution_result IS '保养执行结果（按内容项）';

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
    risk_level VARCHAR(20),
    risk_factors JSONB,
    assessment_result TEXT,
    recommendations TEXT,
    report_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'completed',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE risk_assessment IS '风险评估表';
COMMENT ON COLUMN risk_assessment.id IS '主键';
COMMENT ON COLUMN risk_assessment.assessment_no IS 'assessment编号';
COMMENT ON COLUMN risk_assessment.device_id IS '关联设备';
COMMENT ON COLUMN risk_assessment.device_code IS '设备编码';
COMMENT ON COLUMN risk_assessment.device_name IS '设备名称';
COMMENT ON COLUMN risk_assessment.assessor_id IS '关联assessor';
COMMENT ON COLUMN risk_assessment.assessment_date IS 'assessment日期';
COMMENT ON COLUMN risk_assessment.risk_level IS '风险等级';
COMMENT ON COLUMN risk_assessment.risk_factors IS 'risk factors';
COMMENT ON COLUMN risk_assessment.assessment_result IS 'assessment result';
COMMENT ON COLUMN risk_assessment.recommendations IS 'recommendations';
COMMENT ON COLUMN risk_assessment.report_url IS '报告附件URL';
COMMENT ON COLUMN risk_assessment.status IS '状态';
COMMENT ON COLUMN risk_assessment.remark IS '备注';
COMMENT ON COLUMN risk_assessment.created_at IS '创建时间';
COMMENT ON COLUMN risk_assessment.updated_at IS '更新时间';

-- 7.2 不良事件表
CREATE TABLE adverse_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_no VARCHAR(30) UNIQUE NOT NULL,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    reporter_id UUID REFERENCES sys_user(id),
    report_time TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type VARCHAR(50),
    severity_level VARCHAR(20),
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
    status VARCHAR(20) DEFAULT 'reported',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE adverse_event IS '不良事件表';
COMMENT ON COLUMN adverse_event.id IS '主键';
COMMENT ON COLUMN adverse_event.event_no IS 'event编号';
COMMENT ON COLUMN adverse_event.device_id IS '关联设备';
COMMENT ON COLUMN adverse_event.device_code IS '设备编码';
COMMENT ON COLUMN adverse_event.device_name IS '设备名称';
COMMENT ON COLUMN adverse_event.reporter_id IS '关联reporter';
COMMENT ON COLUMN adverse_event.report_time IS '报修时间';
COMMENT ON COLUMN adverse_event.event_type IS 'event type';
COMMENT ON COLUMN adverse_event.severity_level IS 'severity level';
COMMENT ON COLUMN adverse_event.event_description IS 'event description';
COMMENT ON COLUMN adverse_event.cause_analysis IS 'cause analysis';
COMMENT ON COLUMN adverse_event.impact_description IS 'impact description';
COMMENT ON COLUMN adverse_event.photos IS '现场照片JSON';
COMMENT ON COLUMN adverse_event.handler_id IS '关联handler';
COMMENT ON COLUMN adverse_event.handle_measures IS 'handle measures';
COMMENT ON COLUMN adverse_event.handle_time IS 'handle time';
COMMENT ON COLUMN adverse_event.reported_to_authority IS 'reported to authority';
COMMENT ON COLUMN adverse_event.report_date IS 'report日期';
COMMENT ON COLUMN adverse_event.authority_feedback IS 'authority feedback';
COMMENT ON COLUMN adverse_event.reviewer_id IS '关联reviewer';
COMMENT ON COLUMN adverse_event.review_time IS 'review time';
COMMENT ON COLUMN adverse_event.review_comment IS 'review comment';
COMMENT ON COLUMN adverse_event.status IS '状态';
COMMENT ON COLUMN adverse_event.remark IS '备注';
COMMENT ON COLUMN adverse_event.created_at IS '创建时间';
COMMENT ON COLUMN adverse_event.updated_at IS '更新时间';

-- 7.3 计量管理表
CREATE TABLE metrology_record (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    metrology_no VARCHAR(30) UNIQUE NOT NULL,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    metrology_type VARCHAR(50),
    metrology_org VARCHAR(200),
    scheduled_date DATE,
    actual_date DATE,
    next_due_date DATE,
    certificate_no VARCHAR(100),
    certificate_url VARCHAR(500),
    result VARCHAR(20),
    measurement_data JSONB,
    inspector_id UUID,
    cost DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE metrology_record IS '计量管理表';
COMMENT ON COLUMN metrology_record.id IS '主键';
COMMENT ON COLUMN metrology_record.metrology_no IS 'metrology编号';
COMMENT ON COLUMN metrology_record.device_id IS '关联设备';
COMMENT ON COLUMN metrology_record.device_code IS '设备编码';
COMMENT ON COLUMN metrology_record.device_name IS '设备名称';
COMMENT ON COLUMN metrology_record.metrology_type IS 'metrology type';
COMMENT ON COLUMN metrology_record.metrology_org IS 'metrology org';
COMMENT ON COLUMN metrology_record.scheduled_date IS 'scheduled日期';
COMMENT ON COLUMN metrology_record.actual_date IS 'actual日期';
COMMENT ON COLUMN metrology_record.next_due_date IS '下次到期日';
COMMENT ON COLUMN metrology_record.certificate_no IS 'certificate编号';
COMMENT ON COLUMN metrology_record.certificate_url IS 'certificate附件地址';
COMMENT ON COLUMN metrology_record.result IS 'result';
COMMENT ON COLUMN metrology_record.measurement_data IS 'measurement data';
COMMENT ON COLUMN metrology_record.inspector_id IS '关联巡检人';
COMMENT ON COLUMN metrology_record.cost IS 'cost';
COMMENT ON COLUMN metrology_record.status IS '状态';
COMMENT ON COLUMN metrology_record.remark IS '备注';
COMMENT ON COLUMN metrology_record.created_at IS '创建时间';
COMMENT ON COLUMN metrology_record.updated_at IS '更新时间';

-- 7.3.1 计量类别
CREATE TABLE metrology_category (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    category_code VARCHAR(30) NOT NULL UNIQUE,
    category_name VARCHAR(100) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE metrology_category IS '计量类别表';

-- 7.3.1b 计量检定类型（法规/时机/地点/分级）
CREATE TABLE metrology_type (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type_code VARCHAR(50) NOT NULL UNIQUE,
    type_name VARCHAR(200) NOT NULL,
    parent_id UUID REFERENCES metrology_type(id),
    classification_group VARCHAR(30) NOT NULL,
    regulatory_attr VARCHAR(20),
    traceability_mode VARCHAR(20),
    timing_kind VARCHAR(30),
    location_kind VARCHAR(20),
    management_grade VARCHAR(10),
    cycle_rule VARCHAR(200),
    legal_basis TEXT,
    executor_scope VARCHAR(300),
    certificate_kind VARCHAR(50),
    sort_order INTEGER DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);
COMMENT ON TABLE metrology_type IS '计量检定类型（法规/时机/地点/分级）';
COMMENT ON COLUMN metrology_type.type_code IS '类型编码';
COMMENT ON COLUMN metrology_type.type_name IS '类型名称';
COMMENT ON COLUMN metrology_type.parent_id IS '上级类型';
COMMENT ON COLUMN metrology_type.classification_group IS '分类维度：regulatory/timing/location/grade/device_scope';
COMMENT ON COLUMN metrology_type.regulatory_attr IS '法规属性：mandatory/voluntary';
COMMENT ON COLUMN metrology_type.traceability_mode IS '溯源方式：verification/calibration';
COMMENT ON COLUMN metrology_type.timing_kind IS '实施时机：first_only/periodic/after_repair/arbitration/interim';
COMMENT ON COLUMN metrology_type.location_kind IS '执行地点：lab/onsite/both';
COMMENT ON COLUMN metrology_type.management_grade IS '管理分级：A/B/C';
COMMENT ON COLUMN metrology_type.cycle_rule IS '周期规则说明';
COMMENT ON COLUMN metrology_type.legal_basis IS '法规依据';
COMMENT ON COLUMN metrology_type.executor_scope IS '执行机构范围';
COMMENT ON COLUMN metrology_type.certificate_kind IS '证书类型：verification_cert/calibration_cert/none';

-- 7.3.2 检定机构
CREATE TABLE metrology_org (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE metrology_org IS '检定机构表';

-- 7.3.3 计量模板
CREATE TABLE metrology_template (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_code VARCHAR(30),
    template_name VARCHAR(200) NOT NULL,
    category_id UUID REFERENCES metrology_category(id),
    description TEXT,
    estimated_duration INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE metrology_template IS '计量模板表';

CREATE TABLE metrology_template_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE metrology_template_item IS '计量模板内容项';

-- 7.3.4 计量计划
CREATE TABLE metrology_plan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE metrology_plan IS '计量计划表';

-- 7.3.5 计量执行
CREATE TABLE metrology_execution (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE metrology_execution IS '计量执行单';

CREATE TABLE metrology_execution_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE metrology_execution_item IS '计量执行明细（按设备）';

CREATE TABLE metrology_execution_result (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE metrology_execution_result IS '计量执行结果（按内容项）';

-- 7.4 性能检测表
CREATE TABLE performance_test (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    test_no VARCHAR(30) UNIQUE NOT NULL,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    test_type VARCHAR(50),
    test_standard VARCHAR(200),
    tester_id UUID REFERENCES sys_user(id),
    test_date DATE,
    test_items JSONB,
    overall_result VARCHAR(20),
    test_report_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'completed',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE performance_test IS '性能检测表';
COMMENT ON COLUMN performance_test.id IS '主键';
COMMENT ON COLUMN performance_test.test_no IS 'test编号';
COMMENT ON COLUMN performance_test.device_id IS '关联设备';
COMMENT ON COLUMN performance_test.device_code IS '设备编码';
COMMENT ON COLUMN performance_test.device_name IS '设备名称';
COMMENT ON COLUMN performance_test.test_type IS 'test type';
COMMENT ON COLUMN performance_test.test_standard IS 'test standard';
COMMENT ON COLUMN performance_test.tester_id IS '关联tester';
COMMENT ON COLUMN performance_test.test_date IS 'test日期';
COMMENT ON COLUMN performance_test.test_items IS 'test items';
COMMENT ON COLUMN performance_test.overall_result IS 'overall result';
COMMENT ON COLUMN performance_test.test_report_url IS 'testreport附件地址';
COMMENT ON COLUMN performance_test.status IS '状态';
COMMENT ON COLUMN performance_test.remark IS '备注';
COMMENT ON COLUMN performance_test.created_at IS '创建时间';
COMMENT ON COLUMN performance_test.updated_at IS '更新时间';

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
    coverage_type VARCHAR(20),
    covered_devices JSONB,
    response_time_hours INTEGER,
    preventive_visits_per_year INTEGER,
    contract_file_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'active',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE maintenance_contract IS '维保合同表';
COMMENT ON COLUMN maintenance_contract.id IS '主键';
COMMENT ON COLUMN maintenance_contract.contract_code IS '合同编码';
COMMENT ON COLUMN maintenance_contract.contract_name IS '合同名称';
COMMENT ON COLUMN maintenance_contract.supplier_id IS '供应商';
COMMENT ON COLUMN maintenance_contract.start_date IS '开始日期';
COMMENT ON COLUMN maintenance_contract.end_date IS '结束日期';
COMMENT ON COLUMN maintenance_contract.contract_amount IS 'contract金额';
COMMENT ON COLUMN maintenance_contract.coverage_type IS 'coverage type';
COMMENT ON COLUMN maintenance_contract.covered_devices IS 'covered devices';
COMMENT ON COLUMN maintenance_contract.response_time_hours IS 'response time hours';
COMMENT ON COLUMN maintenance_contract.preventive_visits_per_year IS 'preventive visits per year';
COMMENT ON COLUMN maintenance_contract.contract_file_url IS 'contractfile附件地址';
COMMENT ON COLUMN maintenance_contract.status IS '状态';
COMMENT ON COLUMN maintenance_contract.remark IS '备注';
COMMENT ON COLUMN maintenance_contract.created_at IS '创建时间';
COMMENT ON COLUMN maintenance_contract.updated_at IS '更新时间';

-- 8.2 维保履约记录表
CREATE TABLE maintenance_contract_fulfillment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID REFERENCES maintenance_contract(id),
    fulfillment_type VARCHAR(20),
    scheduled_date DATE,
    actual_date DATE,
    engineer_id UUID REFERENCES engineer(id),
    service_content TEXT,
    result_description TEXT,
    photos JSONB,
    signature_url VARCHAR(500),
    evaluation_rating INTEGER,
    evaluation_comment TEXT,
    status VARCHAR(20) DEFAULT 'completed',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE maintenance_contract_fulfillment IS '维保履约记录表';
COMMENT ON COLUMN maintenance_contract_fulfillment.id IS '主键';
COMMENT ON COLUMN maintenance_contract_fulfillment.contract_id IS '采购合同';
COMMENT ON COLUMN maintenance_contract_fulfillment.fulfillment_type IS 'fulfillment type';
COMMENT ON COLUMN maintenance_contract_fulfillment.scheduled_date IS 'scheduled日期';
COMMENT ON COLUMN maintenance_contract_fulfillment.actual_date IS 'actual日期';
COMMENT ON COLUMN maintenance_contract_fulfillment.engineer_id IS '关联工程师';
COMMENT ON COLUMN maintenance_contract_fulfillment.service_content IS 'service content';
COMMENT ON COLUMN maintenance_contract_fulfillment.result_description IS 'result description';
COMMENT ON COLUMN maintenance_contract_fulfillment.photos IS '现场照片JSON';
COMMENT ON COLUMN maintenance_contract_fulfillment.signature_url IS 'signature附件地址';
COMMENT ON COLUMN maintenance_contract_fulfillment.evaluation_rating IS 'evaluation rating';
COMMENT ON COLUMN maintenance_contract_fulfillment.evaluation_comment IS 'evaluation comment';
COMMENT ON COLUMN maintenance_contract_fulfillment.status IS '状态';
COMMENT ON COLUMN maintenance_contract_fulfillment.remark IS '备注';
COMMENT ON COLUMN maintenance_contract_fulfillment.created_at IS '创建时间';
COMMENT ON COLUMN maintenance_contract_fulfillment.updated_at IS '更新时间';

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
COMMENT ON TABLE maintenance_contract_payment IS '维保付款记录表';
COMMENT ON COLUMN maintenance_contract_payment.id IS '主键';
COMMENT ON COLUMN maintenance_contract_payment.contract_id IS '采购合同';
COMMENT ON COLUMN maintenance_contract_payment.payment_no IS '付款编号';
COMMENT ON COLUMN maintenance_contract_payment.payment_stage IS 'payment stage';
COMMENT ON COLUMN maintenance_contract_payment.payment_amount IS 'payment金额';
COMMENT ON COLUMN maintenance_contract_payment.payment_date IS 'payment日期';
COMMENT ON COLUMN maintenance_contract_payment.invoice_no IS '发票编号';
COMMENT ON COLUMN maintenance_contract_payment.invoice_url IS 'invoice附件地址';
COMMENT ON COLUMN maintenance_contract_payment.status IS '状态';
COMMENT ON COLUMN maintenance_contract_payment.remark IS '备注';
COMMENT ON COLUMN maintenance_contract_payment.created_at IS '创建时间';
COMMENT ON COLUMN maintenance_contract_payment.updated_at IS '更新时间';

-- ================================================================================
-- 9. 特殊设备管理模块
-- ================================================================================
-- 9.1 生命支持类设备表
CREATE TABLE life_support_device (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES medical_device(id) UNIQUE,
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    criticality_level VARCHAR(20),
    backup_required BOOLEAN DEFAULT TRUE,
    standby_status VARCHAR(20) DEFAULT 'ready',
    last_test_date DATE,
    next_test_date DATE,
    emergency_protocol TEXT,
    responsible_person_id UUID REFERENCES sys_user(id),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE life_support_device IS '生命支持类设备表';
COMMENT ON COLUMN life_support_device.id IS '主键';
COMMENT ON COLUMN life_support_device.device_id IS '关联设备';
COMMENT ON COLUMN life_support_device.device_code IS '设备编码';
COMMENT ON COLUMN life_support_device.device_name IS '设备名称';
COMMENT ON COLUMN life_support_device.criticality_level IS 'criticality level';
COMMENT ON COLUMN life_support_device.backup_required IS 'backup required';
COMMENT ON COLUMN life_support_device.standby_status IS 'standby status';
COMMENT ON COLUMN life_support_device.last_test_date IS 'lasttest日期';
COMMENT ON COLUMN life_support_device.next_test_date IS 'nexttest日期';
COMMENT ON COLUMN life_support_device.emergency_protocol IS 'emergency protocol';
COMMENT ON COLUMN life_support_device.responsible_person_id IS '关联responsibleperson';
COMMENT ON COLUMN life_support_device.remark IS '备注';
COMMENT ON COLUMN life_support_device.created_at IS '创建时间';
COMMENT ON COLUMN life_support_device.updated_at IS '更新时间';

-- 9.2 应急设备库表
CREATE TABLE emergency_device_pool (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pool_name VARCHAR(200) NOT NULL,
    campus_id UUID REFERENCES campus(id),
    location VARCHAR(200),
    manager_id UUID REFERENCES sys_user(id),
    contact_phone VARCHAR(20),
    devices JSONB,
    is_available BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE emergency_device_pool IS '应急设备库表';
COMMENT ON COLUMN emergency_device_pool.id IS '主键';
COMMENT ON COLUMN emergency_device_pool.pool_name IS 'pool名称';
COMMENT ON COLUMN emergency_device_pool.campus_id IS '所属院区';
COMMENT ON COLUMN emergency_device_pool.location IS 'location';
COMMENT ON COLUMN emergency_device_pool.manager_id IS '关联负责人';
COMMENT ON COLUMN emergency_device_pool.contact_phone IS '联系电话';
COMMENT ON COLUMN emergency_device_pool.devices IS 'devices';
COMMENT ON COLUMN emergency_device_pool.is_available IS '是否available';
COMMENT ON COLUMN emergency_device_pool.remark IS '备注';
COMMENT ON COLUMN emergency_device_pool.created_at IS '创建时间';
COMMENT ON COLUMN emergency_device_pool.updated_at IS '更新时间';

-- 9.3 应急设备调配记录表
CREATE TABLE emergency_device_allocation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    allocation_no VARCHAR(30) UNIQUE NOT NULL,
    device_id UUID REFERENCES medical_device(id),
    from_pool_id UUID REFERENCES emergency_device_pool(id),
    to_dept_id UUID REFERENCES department(id),
    applicant_id UUID REFERENCES sys_user(id),
    application_time TIMESTAMP WITH TIME ZONE NOT NULL,
    reason TEXT NOT NULL,
    urgency_level VARCHAR(20),
    approved_by UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    allocation_time TIMESTAMP WITH TIME ZONE,
    return_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE emergency_device_allocation IS '应急设备调配记录表';
COMMENT ON COLUMN emergency_device_allocation.id IS '主键';
COMMENT ON COLUMN emergency_device_allocation.allocation_no IS 'allocation编号';
COMMENT ON COLUMN emergency_device_allocation.device_id IS '关联设备';
COMMENT ON COLUMN emergency_device_allocation.from_pool_id IS '关联frompool';
COMMENT ON COLUMN emergency_device_allocation.to_dept_id IS '关联todept';
COMMENT ON COLUMN emergency_device_allocation.applicant_id IS '关联申请人';
COMMENT ON COLUMN emergency_device_allocation.application_time IS 'application time';
COMMENT ON COLUMN emergency_device_allocation.reason IS 'reason';
COMMENT ON COLUMN emergency_device_allocation.urgency_level IS 'urgency level';
COMMENT ON COLUMN emergency_device_allocation.approved_by IS '审核人';
COMMENT ON COLUMN emergency_device_allocation.approved_at IS '审核时间';
COMMENT ON COLUMN emergency_device_allocation.allocation_time IS 'allocation time';
COMMENT ON COLUMN emergency_device_allocation.return_time IS 'return time';
COMMENT ON COLUMN emergency_device_allocation.status IS '状态';
COMMENT ON COLUMN emergency_device_allocation.remark IS '备注';
COMMENT ON COLUMN emergency_device_allocation.created_at IS '创建时间';
COMMENT ON COLUMN emergency_device_allocation.updated_at IS '更新时间';

-- 9.4 特种设备表（放射、辐射类等）
CREATE TABLE special_device (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES medical_device(id) UNIQUE,
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    special_type VARCHAR(50),
    license_no VARCHAR(100),
    license_expiry_date DATE,
    operator_cert_required BOOLEAN DEFAULT TRUE,
    certified_operators UUID[],
    safety_measures TEXT,
    last_inspection_date DATE,
    next_inspection_date DATE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE special_device IS '特种设备表（放射、辐射类等）';
COMMENT ON COLUMN special_device.id IS '主键';
COMMENT ON COLUMN special_device.device_id IS '关联设备';
COMMENT ON COLUMN special_device.device_code IS '设备编码';
COMMENT ON COLUMN special_device.device_name IS '设备名称';
COMMENT ON COLUMN special_device.special_type IS 'special type';
COMMENT ON COLUMN special_device.license_no IS 'license编号';
COMMENT ON COLUMN special_device.license_expiry_date IS 'licenseexpiry日期';
COMMENT ON COLUMN special_device.operator_cert_required IS 'operator cert required';
COMMENT ON COLUMN special_device.certified_operators IS 'certified operators';
COMMENT ON COLUMN special_device.safety_measures IS 'safety measures';
COMMENT ON COLUMN special_device.last_inspection_date IS 'lastinspection日期';
COMMENT ON COLUMN special_device.next_inspection_date IS 'nextinspection日期';
COMMENT ON COLUMN special_device.remark IS '备注';
COMMENT ON COLUMN special_device.created_at IS '创建时间';
COMMENT ON COLUMN special_device.updated_at IS '更新时间';

-- 9.5 租赁设备表
CREATE TABLE leased_device (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES medical_device(id) UNIQUE,
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    lessor_id UUID REFERENCES supplier(id),
    lease_start_date DATE,
    lease_end_date DATE,
    monthly_rent DECIMAL(10,2),
    contract_no VARCHAR(50),
    contract_url VARCHAR(500),
    auto_renewal BOOLEAN DEFAULT FALSE,
    renewal_notice_days INTEGER DEFAULT 30,
    status VARCHAR(20) DEFAULT 'active',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE leased_device IS '租赁设备表';
COMMENT ON COLUMN leased_device.id IS '主键';
COMMENT ON COLUMN leased_device.device_id IS '关联设备';
COMMENT ON COLUMN leased_device.device_code IS '设备编码';
COMMENT ON COLUMN leased_device.device_name IS '设备名称';
COMMENT ON COLUMN leased_device.lessor_id IS '关联lessor';
COMMENT ON COLUMN leased_device.lease_start_date IS 'leasestart日期';
COMMENT ON COLUMN leased_device.lease_end_date IS 'leaseend日期';
COMMENT ON COLUMN leased_device.monthly_rent IS 'monthly rent';
COMMENT ON COLUMN leased_device.contract_no IS '合同编号';
COMMENT ON COLUMN leased_device.contract_url IS 'contract附件地址';
COMMENT ON COLUMN leased_device.auto_renewal IS 'auto renewal';
COMMENT ON COLUMN leased_device.renewal_notice_days IS 'renewal notice days';
COMMENT ON COLUMN leased_device.status IS '状态';
COMMENT ON COLUMN leased_device.remark IS '备注';
COMMENT ON COLUMN leased_device.created_at IS '创建时间';
COMMENT ON COLUMN leased_device.updated_at IS '更新时间';

-- 9.6 公用设备借调单
CREATE TABLE shared_device_loan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    loan_no VARCHAR(30) UNIQUE NOT NULL,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    from_dept_id UUID REFERENCES department(id),
    to_dept_id UUID REFERENCES department(id),
    applicant_id UUID REFERENCES sys_user(id),
    loan_start DATE,
    loan_end DATE,
    fee_mode VARCHAR(20),
    fee_time_unit VARCHAR(10),
    fee_unit_price DECIMAL(12,2),
    billing_start_at TIMESTAMP WITH TIME ZONE,
    billing_end_at TIMESTAMP WITH TIME ZONE,
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
COMMENT ON TABLE shared_device_loan IS '公用设备借调单';
COMMENT ON COLUMN shared_device_loan.loan_no IS '借调单号';
COMMENT ON COLUMN shared_device_loan.status IS '单据状态';

-- 9.7 公用设备归还单
CREATE TABLE shared_device_return (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE shared_device_return IS '公用设备归还单';

-- 9.8 公用设备借调收费
CREATE TABLE shared_device_fee (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    fee_no VARCHAR(30) UNIQUE NOT NULL,
    loan_id UUID REFERENCES shared_device_loan(id),
    fee_amount DECIMAL(12,2) NOT NULL,
    fee_date DATE NOT NULL,
    paid_status VARCHAR(20) DEFAULT 'unpaid',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE shared_device_fee IS '公用设备借调收费单';

-- 9.9 预防性维护（PM）模块
CREATE TABLE pm_type (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type_code VARCHAR(30) NOT NULL UNIQUE,
    type_name VARCHAR(100) NOT NULL,
    risk_level VARCHAR(20) DEFAULT 'medium',
    sort_order INTEGER DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE pm_type IS '预防性维护类型表';

CREATE TABLE pm_template (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE pm_template IS '预防性维护模板表';

CREATE TABLE pm_template_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE pm_template_item IS '预防性维护模板内容项';

CREATE TABLE pm_plan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE pm_plan IS '预防性维护计划表';

CREATE TABLE pm_execution (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    execution_no VARCHAR(30) UNIQUE NOT NULL,
    plan_id UUID REFERENCES pm_plan(id),
    template_id UUID REFERENCES pm_template(id),
    pm_type_id UUID REFERENCES pm_type(id),
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
COMMENT ON TABLE pm_execution IS '预防性维护执行单';

CREATE TABLE pm_execution_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE pm_execution_item IS '预防性维护执行明细';

CREATE TABLE pm_execution_result (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
COMMENT ON TABLE pm_execution_result IS '预防性维护执行结果';

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
    usage_hours DECIMAL(10,2),
    patient_count INTEGER DEFAULT 0,
    examination_count INTEGER DEFAULT 0,
    revenue DECIMAL(15,2) DEFAULT 0,
    data_source VARCHAR(20),
    source_record_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE device_usage_record IS '设备使用记录表（从HIS/PACS/LIS采集）';
COMMENT ON COLUMN device_usage_record.id IS '主键';
COMMENT ON COLUMN device_usage_record.device_id IS '关联设备';
COMMENT ON COLUMN device_usage_record.device_code IS '设备编码';
COMMENT ON COLUMN device_usage_record.device_name IS '设备名称';
COMMENT ON COLUMN device_usage_record.usage_date IS 'usage日期';
COMMENT ON COLUMN device_usage_record.usage_hours IS 'usage hours';
COMMENT ON COLUMN device_usage_record.patient_count IS 'patient数量';
COMMENT ON COLUMN device_usage_record.examination_count IS 'examination数量';
COMMENT ON COLUMN device_usage_record.revenue IS 'revenue';
COMMENT ON COLUMN device_usage_record.data_source IS 'data source';
COMMENT ON COLUMN device_usage_record.source_record_id IS '关联sourcerecord';
COMMENT ON COLUMN device_usage_record.created_at IS '创建时间';

-- 10.2 设备成本记录表
CREATE TABLE device_cost_record (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    cost_date DATE NOT NULL,
    cost_type VARCHAR(50) NOT NULL,
    cost_amount DECIMAL(15,2) NOT NULL,
    description TEXT,
    invoice_no VARCHAR(50),
    data_source VARCHAR(20) DEFAULT 'manual',
    source_record_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE device_cost_record IS '设备成本记录表';
COMMENT ON COLUMN device_cost_record.id IS '主键';
COMMENT ON COLUMN device_cost_record.device_id IS '关联设备';
COMMENT ON COLUMN device_cost_record.device_code IS '设备编码';
COMMENT ON COLUMN device_cost_record.device_name IS '设备名称';
COMMENT ON COLUMN device_cost_record.cost_date IS 'cost日期';
COMMENT ON COLUMN device_cost_record.cost_type IS 'cost type';
COMMENT ON COLUMN device_cost_record.cost_amount IS 'cost金额';
COMMENT ON COLUMN device_cost_record.description IS '描述';
COMMENT ON COLUMN device_cost_record.invoice_no IS '发票编号';
COMMENT ON COLUMN device_cost_record.data_source IS 'data source';
COMMENT ON COLUMN device_cost_record.source_record_id IS '关联sourcerecord';
COMMENT ON COLUMN device_cost_record.created_at IS '创建时间';

-- 10.3 设备效益分析汇总表（按月聚合）
CREATE TABLE device_benefit_summary (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    summary_year INTEGER NOT NULL,
    summary_month INTEGER NOT NULL,
    total_revenue DECIMAL(15,2) DEFAULT 0,
    total_cost DECIMAL(15,2) DEFAULT 0,
    net_profit DECIMAL(15,2) DEFAULT 0,
    profit_rate DECIMAL(10,4),
    usage_hours DECIMAL(10,2),
    patient_count INTEGER,
    utilization_rate DECIMAL(10,4),
    maintenance_cost DECIMAL(15,2),
    repair_cost DECIMAL(15,2),
    depreciation_cost DECIMAL(15,2),
    benefit_level VARCHAR(20),
    benefit_score DECIMAL(10,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id, summary_year, summary_month)
);
COMMENT ON TABLE device_benefit_summary IS '设备效益分析汇总表（按月聚合）';
COMMENT ON COLUMN device_benefit_summary.id IS '主键';
COMMENT ON COLUMN device_benefit_summary.device_id IS '关联设备';
COMMENT ON COLUMN device_benefit_summary.device_code IS '设备编码';
COMMENT ON COLUMN device_benefit_summary.device_name IS '设备名称';
COMMENT ON COLUMN device_benefit_summary.summary_year IS 'summary year';
COMMENT ON COLUMN device_benefit_summary.summary_month IS 'summary month';
COMMENT ON COLUMN device_benefit_summary.total_revenue IS 'total revenue';
COMMENT ON COLUMN device_benefit_summary.total_cost IS 'total cost';
COMMENT ON COLUMN device_benefit_summary.net_profit IS 'net profit';
COMMENT ON COLUMN device_benefit_summary.profit_rate IS 'profit rate';
COMMENT ON COLUMN device_benefit_summary.usage_hours IS 'usage hours';
COMMENT ON COLUMN device_benefit_summary.patient_count IS 'patient数量';
COMMENT ON COLUMN device_benefit_summary.utilization_rate IS 'utilization rate';
COMMENT ON COLUMN device_benefit_summary.maintenance_cost IS 'maintenance cost';
COMMENT ON COLUMN device_benefit_summary.repair_cost IS 'repair cost';
COMMENT ON COLUMN device_benefit_summary.depreciation_cost IS 'depreciation cost';
COMMENT ON COLUMN device_benefit_summary.benefit_level IS 'benefit level';
COMMENT ON COLUMN device_benefit_summary.benefit_score IS 'benefit score';
COMMENT ON COLUMN device_benefit_summary.created_at IS '创建时间';
COMMENT ON COLUMN device_benefit_summary.updated_at IS '更新时间';

-- 10.4 HIS收费对照表
CREATE TABLE benefit_mapping (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID NOT NULL REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    his_item_code VARCHAR(50),
    his_item_name VARCHAR(200),
    pacs_modality VARCHAR(50),
    charge_code VARCHAR(50),
    charge_name VARCHAR(200),
    unit_price DECIMAL(12,2) DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE benefit_mapping IS 'HIS收费项目与设备对照表';

-- ================================================================================
-- 10.5 电流监测模块
-- ================================================================================
CREATE TABLE power_base_station (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    station_code VARCHAR(50) NOT NULL UNIQUE,
    station_name VARCHAR(200) NOT NULL,
    campus_id UUID REFERENCES campus(id),
    location VARCHAR(200),
    ip_address VARCHAR(50),
    protocol_type VARCHAR(30) DEFAULT 'mqtt',
    status VARCHAR(20) DEFAULT 'online',
    is_active BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE power_base_station IS '电流监测基站表';

CREATE TABLE power_tag (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tag_code VARCHAR(50) NOT NULL UNIQUE,
    tag_name VARCHAR(200) NOT NULL,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    station_id UUID REFERENCES power_base_station(id),
    rated_power DECIMAL(10,2),
    install_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE power_tag IS '电流监测标签表';
COMMENT ON COLUMN power_tag.device_code IS '关联设备编码（冗余）';
COMMENT ON COLUMN power_tag.device_name IS '关联设备名称（冗余）';

CREATE TABLE power_device_status (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES medical_device(id),
    tag_id UUID REFERENCES power_tag(id) UNIQUE,
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    current_amp DECIMAL(10,3),
    voltage DECIMAL(10,2),
    power_watt DECIMAL(10,2),
    work_state VARCHAR(20) DEFAULT 'offline',
    collected_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE power_device_status IS '设备电流实时状态表';

CREATE TABLE power_monitor_record (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES medical_device(id),
    tag_id UUID REFERENCES power_tag(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    record_date DATE NOT NULL,
    run_hours DECIMAL(8,2) DEFAULT 0,
    idle_hours DECIMAL(8,2) DEFAULT 0,
    offline_hours DECIMAL(8,2) DEFAULT 0,
    avg_current DECIMAL(10,3),
    peak_current DECIMAL(10,3),
    energy_kwh DECIMAL(12,3) DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id, record_date)
);
COMMENT ON TABLE power_monitor_record IS '电流监测日记录表';

CREATE TABLE power_current_reading (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tag_id UUID REFERENCES power_tag(id),
    tag_code VARCHAR(50),
    station_id UUID REFERENCES power_base_station(id),
    station_code VARCHAR(50),
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    current_ma DECIMAL(12,3) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE power_current_reading IS '电流原始读数表';
COMMENT ON COLUMN power_current_reading.tag_code IS '标签编码（冗余）';
COMMENT ON COLUMN power_current_reading.station_code IS '基站编码（冗余）';
COMMENT ON COLUMN power_current_reading.current_ma IS '电流读数(mA)';
COMMENT ON COLUMN power_current_reading.read_at IS '读取时间';
COMMENT ON COLUMN power_current_reading.created_at IS '入库时间';

CREATE TABLE power_tag_bind_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tag_id UUID NOT NULL REFERENCES power_tag(id),
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    bound_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unbound_at TIMESTAMP WITH TIME ZONE,
    operator_id UUID,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE power_tag_bind_log IS '电流标签设备绑定历史';

-- ================================================================================
-- 11. 系统配置与字典表
-- ================================================================================
-- 11.1 系统参数表
CREATE TABLE sys_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    config_type VARCHAR(20),
    description TEXT,
    is_system BOOLEAN DEFAULT FALSE,
    category_code VARCHAR(20),
    category_name VARCHAR(100),
    item_code VARCHAR(20),
    item_name VARCHAR(200),
    value1 TEXT,
    value2 TEXT,
    value3 TEXT,
    value4 TEXT,
    value5 TEXT,
    value6 TEXT,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_config IS '系统参数表';
COMMENT ON COLUMN sys_config.id IS '主键';
COMMENT ON COLUMN sys_config.config_key IS 'config key';
COMMENT ON COLUMN sys_config.config_value IS 'config value';
COMMENT ON COLUMN sys_config.config_type IS 'config type';
COMMENT ON COLUMN sys_config.description IS '描述';
COMMENT ON COLUMN sys_config.is_system IS '是否system';
COMMENT ON COLUMN sys_config.created_at IS '创建时间';
COMMENT ON COLUMN sys_config.updated_at IS '更新时间';

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
COMMENT ON TABLE sys_dict IS '数据字典表';
COMMENT ON COLUMN sys_dict.id IS '主键';
COMMENT ON COLUMN sys_dict.dict_type IS 'dict type';
COMMENT ON COLUMN sys_dict.dict_code IS 'dict编码';
COMMENT ON COLUMN sys_dict.dict_label IS 'dict label';
COMMENT ON COLUMN sys_dict.dict_value IS 'dict value';
COMMENT ON COLUMN sys_dict.sort_order IS '排序号';
COMMENT ON COLUMN sys_dict.is_active IS '是否启用';
COMMENT ON COLUMN sys_dict.remark IS '备注';
COMMENT ON COLUMN sys_dict.created_at IS '创建时间';

-- 11.3 消息通知表
CREATE TABLE sys_notification (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(200) NOT NULL,
    content TEXT,
    notification_type VARCHAR(20),
    target_users UUID[],
    target_roles UUID[],
    target_depts UUID[],
    priority VARCHAR(20) DEFAULT 'normal',
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    related_entity_type VARCHAR(50),
    related_entity_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_notification IS 'sys notification';
COMMENT ON COLUMN sys_notification.id IS '主键';
COMMENT ON COLUMN sys_notification.title IS '标题';
COMMENT ON COLUMN sys_notification.content IS '内容';
COMMENT ON COLUMN sys_notification.notification_type IS '通知类型';
COMMENT ON COLUMN sys_notification.target_users IS '通知目标用户列表';
COMMENT ON COLUMN sys_notification.target_roles IS '通知目标角色列表';
COMMENT ON COLUMN sys_notification.target_depts IS '通知目标科室列表';
COMMENT ON COLUMN sys_notification.priority IS '优先级';
COMMENT ON COLUMN sys_notification.is_read IS '是否已读';
COMMENT ON COLUMN sys_notification.read_at IS '已读时间';
COMMENT ON COLUMN sys_notification.related_entity_type IS '关联业务类型';
COMMENT ON COLUMN sys_notification.related_entity_id IS '关联业务主键';
COMMENT ON COLUMN sys_notification.created_at IS '创建时间';

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

COMMENT ON VIEW v_device_full_info IS '设备完整信息视图';
COMMENT ON COLUMN v_device_full_info.id IS '设备主键';
COMMENT ON COLUMN v_device_full_info.device_code IS '设备编码';
COMMENT ON COLUMN v_device_full_info.device_name IS '设备名称';
COMMENT ON COLUMN v_device_full_info.brand IS '品牌';
COMMENT ON COLUMN v_device_full_info.model IS '型号';
COMMENT ON COLUMN v_device_full_info.serial_number IS '出厂序列号';
COMMENT ON COLUMN v_device_full_info.category_name IS '分类名称';
COMMENT ON COLUMN v_device_full_info.manufacturer_name IS '生产厂商';
COMMENT ON COLUMN v_device_full_info.supplier_name IS '供应商';
COMMENT ON COLUMN v_device_full_info.original_value IS '原值';
COMMENT ON COLUMN v_device_full_info.net_value IS '净值';
COMMENT ON COLUMN v_device_full_info.campus_name IS '院区名称';
COMMENT ON COLUMN v_device_full_info.building_name IS '建筑物名称';
COMMENT ON COLUMN v_device_full_info.dept_name IS '科室名称';
COMMENT ON COLUMN v_device_full_info.location_detail IS '位置详情';
COMMENT ON COLUMN v_device_full_info.enable_date IS '启用日期';
COMMENT ON COLUMN v_device_full_info.warranty_end_date IS '保修截止日期';
COMMENT ON COLUMN v_device_full_info.device_status IS '设备状态';
COMMENT ON COLUMN v_device_full_info.risk_level IS '风险等级';
COMMENT ON COLUMN v_device_full_info.is_life_support IS '是否生命支持设备';
COMMENT ON COLUMN v_device_full_info.is_emergency IS '是否应急设备';
COMMENT ON COLUMN v_device_full_info.created_at IS '创建时间';

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

COMMENT ON VIEW v_device_benefit IS '设备效益分析视图';
COMMENT ON COLUMN v_device_benefit.device_code IS '设备编码';
COMMENT ON COLUMN v_device_benefit.device_name IS '设备名称';
COMMENT ON COLUMN v_device_benefit.dept_id IS '所属科室';
COMMENT ON COLUMN v_device_benefit.dept_name IS '科室名称';
COMMENT ON COLUMN v_device_benefit.summary_year IS '汇总年度';
COMMENT ON COLUMN v_device_benefit.summary_month IS '汇总月份';
COMMENT ON COLUMN v_device_benefit.total_revenue IS '总收入';
COMMENT ON COLUMN v_device_benefit.total_cost IS '总成本';
COMMENT ON COLUMN v_device_benefit.net_profit IS '净利润';
COMMENT ON COLUMN v_device_benefit.profit_rate IS '利润率';
COMMENT ON COLUMN v_device_benefit.utilization_rate IS '使用率';
COMMENT ON COLUMN v_device_benefit.benefit_level IS '效益等级';

-- notification table for tenant schema
CREATE TABLE IF NOT EXISTS notification_message (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(200) NOT NULL,
    content TEXT,
    message_type VARCHAR(50) DEFAULT 'system',
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE notification_message IS '设备效益分析视图';
COMMENT ON COLUMN notification_message.id IS '主键';
COMMENT ON COLUMN notification_message.title IS '标题';
COMMENT ON COLUMN notification_message.content IS '内容';
COMMENT ON COLUMN notification_message.message_type IS '消息类型';
COMMENT ON COLUMN notification_message.is_read IS '是否已读';
COMMENT ON COLUMN notification_message.created_at IS '创建时间';

-- MEIS V2.0 tenant schema extensions
-- device outbound
CREATE TABLE IF NOT EXISTS device_outbound (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    outbound_no VARCHAR(30) UNIQUE NOT NULL,
    outbound_type VARCHAR(30) DEFAULT 'requisition',
    dept_id UUID REFERENCES department(id),
    receiver_id UUID REFERENCES sys_user(id),
    warehouse_id UUID REFERENCES warehouse(id),
    outbound_date DATE,
    purpose TEXT,
    is_urgent BOOLEAN DEFAULT FALSE,
    doc_status VARCHAR(20) DEFAULT 'draft',
    status VARCHAR(20) DEFAULT 'draft',
    approval_status VARCHAR(20) DEFAULT 'draft',
    operator_id UUID REFERENCES sys_user(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE device_outbound IS 'device outbound';
COMMENT ON COLUMN device_outbound.id IS '主键';
COMMENT ON COLUMN device_outbound.outbound_no IS '出库编号';
COMMENT ON COLUMN device_outbound.outbound_type IS 'outbound type';
COMMENT ON COLUMN device_outbound.dept_id IS '所属科室';
COMMENT ON COLUMN device_outbound.receiver_id IS '关联领用人';
COMMENT ON COLUMN device_outbound.outbound_date IS 'outbound日期';
COMMENT ON COLUMN device_outbound.purpose IS 'purpose';
COMMENT ON COLUMN device_outbound.is_urgent IS '是否urgent';
COMMENT ON COLUMN device_outbound.doc_status IS 'doc status';
COMMENT ON COLUMN device_outbound.operator_id IS '关联操作人';
COMMENT ON COLUMN device_outbound.created_at IS '创建时间';
COMMENT ON COLUMN device_outbound.updated_at IS '更新时间';

CREATE TABLE IF NOT EXISTS device_outbound_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    outbound_id UUID NOT NULL REFERENCES device_outbound(id) ON DELETE CASCADE,
    device_id UUID REFERENCES medical_device(id),
    device_code VARCHAR(50),
    device_name VARCHAR(200),
    quantity INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE device_outbound_item IS 'device outbound item';
COMMENT ON COLUMN device_outbound_item.id IS '主键';
COMMENT ON COLUMN device_outbound_item.outbound_id IS '关联出库';
COMMENT ON COLUMN device_outbound_item.device_id IS '关联设备';
COMMENT ON COLUMN device_outbound_item.device_code IS '设备编码';
COMMENT ON COLUMN device_outbound_item.device_name IS '设备名称';
COMMENT ON COLUMN device_outbound_item.quantity IS '数量';
COMMENT ON COLUMN device_outbound_item.created_at IS '创建时间';

-- approval workflow
CREATE TABLE IF NOT EXISTS sys_approval_flow (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_code VARCHAR(50) UNIQUE NOT NULL,
    flow_name VARCHAR(100) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_approval_flow IS 'sys approval flow';
COMMENT ON COLUMN sys_approval_flow.id IS '主键';
COMMENT ON COLUMN sys_approval_flow.flow_code IS '流程编码';
COMMENT ON COLUMN sys_approval_flow.flow_name IS '流程名称';
COMMENT ON COLUMN sys_approval_flow.business_type IS '业务类型标识';
COMMENT ON COLUMN sys_approval_flow.is_active IS '是否启用';
COMMENT ON COLUMN sys_approval_flow.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS sys_approval_node (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id UUID NOT NULL REFERENCES sys_approval_flow(id) ON DELETE CASCADE,
    node_order INTEGER NOT NULL,
    node_name VARCHAR(100) NOT NULL,
    approver_role VARCHAR(50),
    amount_threshold NUMERIC(18,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_approval_node IS 'sys approval node';
COMMENT ON COLUMN sys_approval_node.id IS '主键';
COMMENT ON COLUMN sys_approval_node.flow_id IS '关联flow';
COMMENT ON COLUMN sys_approval_node.node_order IS 'node order';
COMMENT ON COLUMN sys_approval_node.node_name IS 'node名称';
COMMENT ON COLUMN sys_approval_node.approver_role IS 'approver role';
COMMENT ON COLUMN sys_approval_node.amount_threshold IS 'amount threshold';
COMMENT ON COLUMN sys_approval_node.created_at IS '创建时间';

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
COMMENT ON TABLE sys_approval_instance IS 'sys approval instance';
COMMENT ON COLUMN sys_approval_instance.id IS '主键';
COMMENT ON COLUMN sys_approval_instance.flow_id IS '关联flow';
COMMENT ON COLUMN sys_approval_instance.business_type IS '业务类型标识';
COMMENT ON COLUMN sys_approval_instance.business_id IS '业务单据ID';
COMMENT ON COLUMN sys_approval_instance.business_no IS '业务单据编号';
COMMENT ON COLUMN sys_approval_instance.title IS '标题';
COMMENT ON COLUMN sys_approval_instance.applicant_id IS '关联申请人';
COMMENT ON COLUMN sys_approval_instance.status IS '状态';
COMMENT ON COLUMN sys_approval_instance.current_node_order IS 'current node order';
COMMENT ON COLUMN sys_approval_instance.created_at IS '创建时间';
COMMENT ON COLUMN sys_approval_instance.updated_at IS '更新时间';

CREATE TABLE IF NOT EXISTS sys_approval_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id UUID NOT NULL REFERENCES sys_approval_instance(id) ON DELETE CASCADE,
    node_order INTEGER NOT NULL,
    approver_id UUID REFERENCES sys_user(id),
    action VARCHAR(20) NOT NULL,
    comment TEXT,
    acted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_approval_record IS 'sys approval record';
COMMENT ON COLUMN sys_approval_record.id IS '主键';
COMMENT ON COLUMN sys_approval_record.instance_id IS '关联instance';
COMMENT ON COLUMN sys_approval_record.node_order IS 'node order';
COMMENT ON COLUMN sys_approval_record.approver_id IS '关联审批人';
COMMENT ON COLUMN sys_approval_record.action IS 'action';
COMMENT ON COLUMN sys_approval_record.comment IS 'comment';
COMMENT ON COLUMN sys_approval_record.acted_at IS 'acted时间';

-- inspection (mobile)
-- 5.0 巡检类型
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
COMMENT ON TABLE inspection_type IS '巡检类型表';

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
COMMENT ON TABLE inspection_template IS '巡检模板表';

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
COMMENT ON TABLE inspection_template_item IS '巡检模板内容项';

CREATE TABLE IF NOT EXISTS inspection_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_name VARCHAR(200) NOT NULL,
    plan_code VARCHAR(30),
    device_id UUID REFERENCES medical_device(id),
    template_id UUID REFERENCES inspection_template(id),
    inspection_type_id UUID REFERENCES inspection_type(id),
    inspection_type VARCHAR(50),
    dept_id UUID REFERENCES department(id),
    cycle_days INTEGER,
    plan_date DATE,
    next_due_date DATE,
    last_inspected_at DATE,
    start_date DATE,
    end_date DATE,
    frequency VARCHAR(30),
    assigned_inspector_id UUID REFERENCES sys_user(id),
    approval_status VARCHAR(20) DEFAULT 'draft',
    created_by UUID REFERENCES sys_user(id),
    approved_by UUID REFERENCES sys_user(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'pending',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE inspection_plan IS '巡检计划';
COMMENT ON COLUMN inspection_plan.id IS '主键';
COMMENT ON COLUMN inspection_plan.plan_name IS '计划名称';
COMMENT ON COLUMN inspection_plan.device_id IS '关联设备';
COMMENT ON COLUMN inspection_plan.inspection_type IS 'inspection type';
COMMENT ON COLUMN inspection_plan.plan_date IS 'plan日期';
COMMENT ON COLUMN inspection_plan.status IS '状态';
COMMENT ON COLUMN inspection_plan.created_at IS '创建时间';
COMMENT ON COLUMN inspection_plan.plan_code IS '订阅计划编码';
COMMENT ON COLUMN inspection_plan.dept_id IS '所属科室';
COMMENT ON COLUMN inspection_plan.start_date IS '开始日期';
COMMENT ON COLUMN inspection_plan.end_date IS '结束日期';
COMMENT ON COLUMN inspection_plan.frequency IS '执行频率';

CREATE TABLE IF NOT EXISTS inspection_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID REFERENCES inspection_plan(id),
    device_id UUID REFERENCES medical_device(id),
    inspector_id UUID REFERENCES sys_user(id),
    inspection_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    result VARCHAR(20),
    remark TEXT,
    status VARCHAR(20) DEFAULT 'completed',
    record_no VARCHAR(30),
    result_summary TEXT,
    inspect_date DATE
);
COMMENT ON TABLE inspection_record IS '巡检记录';
COMMENT ON COLUMN inspection_record.id IS '主键';
COMMENT ON COLUMN inspection_record.plan_id IS '采购计划';
COMMENT ON COLUMN inspection_record.device_id IS '关联设备';
COMMENT ON COLUMN inspection_record.inspector_id IS '关联巡检人';
COMMENT ON COLUMN inspection_record.inspection_date IS 'inspection日期';
COMMENT ON COLUMN inspection_record.result IS 'result';
COMMENT ON COLUMN inspection_record.remark IS '备注';
COMMENT ON COLUMN inspection_record.status IS '状态';
COMMENT ON COLUMN inspection_record.record_no IS '记录编号';
COMMENT ON COLUMN inspection_record.result_summary IS '结果摘要';
COMMENT ON COLUMN inspection_record.inspect_date IS '巡检日期';

CREATE TABLE IF NOT EXISTS inspection_record_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id UUID NOT NULL REFERENCES inspection_record(id) ON DELETE CASCADE,
    item_name VARCHAR(200),
    check_result VARCHAR(20),
    remark TEXT
);
COMMENT ON TABLE inspection_record_item IS 'inspection record item';
COMMENT ON COLUMN inspection_record_item.id IS '主键';
COMMENT ON COLUMN inspection_record_item.record_id IS '关联record';
COMMENT ON COLUMN inspection_record_item.item_name IS 'item名称';
COMMENT ON COLUMN inspection_record_item.check_result IS 'check result';
COMMENT ON COLUMN inspection_record_item.remark IS '备注';

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
COMMENT ON TABLE inspection_execution IS '巡检执行单';

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
COMMENT ON TABLE inspection_execution_item IS '巡检执行明细（按设备）';

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
COMMENT ON TABLE inspection_execution_result IS '巡检执行结果（按内容项）';

-- spare part transactions
CREATE TABLE IF NOT EXISTS spare_part_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    spare_part_id UUID REFERENCES spare_part(id),
    txn_type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(18,2),
    workorder_id UUID REFERENCES repair_workorder(id),
    operator_id UUID REFERENCES sys_user(id),
    ref_no VARCHAR(50),
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE spare_part_transaction IS 'spare part transaction';
COMMENT ON COLUMN spare_part_transaction.id IS '主键';
COMMENT ON COLUMN spare_part_transaction.spare_part_id IS '关联sparepart';
COMMENT ON COLUMN spare_part_transaction.txn_type IS 'txn type';
COMMENT ON COLUMN spare_part_transaction.quantity IS '数量';
COMMENT ON COLUMN spare_part_transaction.unit_price IS 'unit price';
COMMENT ON COLUMN spare_part_transaction.workorder_id IS '关联workorder';
COMMENT ON COLUMN spare_part_transaction.operator_id IS '关联操作人';
COMMENT ON COLUMN spare_part_transaction.created_at IS '创建时间';

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
COMMENT ON TABLE integration_sync_task IS 'integration sync task';
COMMENT ON COLUMN integration_sync_task.id IS '主键';
COMMENT ON COLUMN integration_sync_task.system_code IS 'system编码';
COMMENT ON COLUMN integration_sync_task.task_type IS 'task type';
COMMENT ON COLUMN integration_sync_task.status IS '状态';
COMMENT ON COLUMN integration_sync_task.payload IS 'payload';
COMMENT ON COLUMN integration_sync_task.result IS 'result';
COMMENT ON COLUMN integration_sync_task.created_at IS '创建时间';
COMMENT ON COLUMN integration_sync_task.finished_at IS 'finished时间';

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
COMMENT ON TABLE sys_notification IS 'sys notification';
COMMENT ON COLUMN sys_notification.id IS '主键';
COMMENT ON COLUMN sys_notification.title IS '标题';
COMMENT ON COLUMN sys_notification.content IS '内容';
COMMENT ON COLUMN sys_notification.notification_type IS '通知类型';
COMMENT ON COLUMN sys_notification.target_users IS '通知目标用户列表';
COMMENT ON COLUMN sys_notification.target_roles IS '通知目标角色列表';
COMMENT ON COLUMN sys_notification.target_depts IS '通知目标科室列表';
COMMENT ON COLUMN sys_notification.priority IS '优先级';
COMMENT ON COLUMN sys_notification.is_read IS '是否已读';
COMMENT ON COLUMN sys_notification.read_at IS '已读时间';
COMMENT ON COLUMN sys_notification.related_entity_type IS '关联业务类型';
COMMENT ON COLUMN sys_notification.related_entity_id IS '关联业务主键';
COMMENT ON COLUMN sys_notification.created_at IS '创建时间';

-- System RBAC: warehouse, user permissions snapshot, button permission dict
CREATE TABLE IF NOT EXISTS warehouse (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_code VARCHAR(50) UNIQUE NOT NULL,
    warehouse_name VARCHAR(100) NOT NULL,
    campus_id UUID REFERENCES campus(id),
    dept_id UUID REFERENCES department(id),
    warehouse_type VARCHAR(30) DEFAULT 'device',
    address VARCHAR(500),
    manager_id UUID REFERENCES sys_user(id),
    is_active BOOLEAN DEFAULT TRUE,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE warehouse IS '库房';
COMMENT ON COLUMN warehouse.id IS '主键';
COMMENT ON COLUMN warehouse.warehouse_code IS 'warehouse编码';
COMMENT ON COLUMN warehouse.warehouse_name IS 'warehouse名称';
COMMENT ON COLUMN warehouse.campus_id IS '所属院区';
COMMENT ON COLUMN warehouse.dept_id IS '所属科室';
COMMENT ON COLUMN warehouse.warehouse_type IS '库房类型';
COMMENT ON COLUMN warehouse.address IS '地址';
COMMENT ON COLUMN warehouse.manager_id IS '关联负责人';
COMMENT ON COLUMN warehouse.is_active IS '是否启用';
COMMENT ON COLUMN warehouse.sort_order IS '排序号';
COMMENT ON COLUMN warehouse.created_at IS '创建时间';
COMMENT ON COLUMN warehouse.updated_at IS '更新时间';

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approval_status VARCHAR(20) DEFAULT 'draft',
    business_chain_no VARCHAR(40)
);
COMMENT ON TABLE purchase_acceptance IS '安装验收';
COMMENT ON COLUMN purchase_acceptance.id IS '主键';
COMMENT ON COLUMN purchase_acceptance.acceptance_no IS 'acceptance编号';
COMMENT ON COLUMN purchase_acceptance.contract_id IS '采购合同';
COMMENT ON COLUMN purchase_acceptance.project_id IS '采购项目';
COMMENT ON COLUMN purchase_acceptance.supplier_id IS '供应商';
COMMENT ON COLUMN purchase_acceptance.acceptance_date IS '验收日期';
COMMENT ON COLUMN purchase_acceptance.acceptance_status IS '安装验收状态';
COMMENT ON COLUMN purchase_acceptance.quality_check_passed IS 'quality check passed';
COMMENT ON COLUMN purchase_acceptance.quality_checker_id IS '关联qualitychecker';
COMMENT ON COLUMN purchase_acceptance.quality_check_date IS 'qualitycheck日期';
COMMENT ON COLUMN purchase_acceptance.quality_check_report_url IS 'qualitycheckreport附件地址';
COMMENT ON COLUMN purchase_acceptance.installation_completed IS 'installation completed';
COMMENT ON COLUMN purchase_acceptance.installer_id IS '关联安装人';
COMMENT ON COLUMN purchase_acceptance.installation_date IS 'installation日期';
COMMENT ON COLUMN purchase_acceptance.installation_report_url IS 'installationreport附件地址';
COMMENT ON COLUMN purchase_acceptance.clinical_checker_id IS '关联clinicalchecker';
COMMENT ON COLUMN purchase_acceptance.argument_summary IS 'argument summary';
COMMENT ON COLUMN purchase_acceptance.report_url IS '报告附件URL';
COMMENT ON COLUMN purchase_acceptance.remark IS '备注';
COMMENT ON COLUMN purchase_acceptance.entry_id IS '关联入库';
COMMENT ON COLUMN purchase_acceptance.created_at IS '创建时间';
COMMENT ON COLUMN purchase_acceptance.updated_at IS '更新时间';
COMMENT ON COLUMN purchase_acceptance.approval_status IS '审批状态';
COMMENT ON COLUMN purchase_acceptance.business_chain_no IS '采购业务链编号（计划→入库追溯）';

-- MEIS V11: 采购101112阶段 — 验收专用表单、招标结构化、数据权限报表
-- 验收清单项
CREATE TABLE IF NOT EXISTS purchase_acceptance_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    acceptance_id UUID NOT NULL REFERENCES purchase_acceptance(id) ON DELETE CASCADE,
    item_name VARCHAR(200) NOT NULL,
    check_standard VARCHAR(500),
    check_result VARCHAR(20) DEFAULT 'pending',
    is_passed BOOLEAN,
    checker_id UUID REFERENCES sys_user(id),
    remark TEXT,
    sort_order INTEGER DEFAULT 0
);
COMMENT ON TABLE purchase_acceptance_item IS 'purchase acceptance item';
COMMENT ON COLUMN purchase_acceptance_item.id IS '主键';
COMMENT ON COLUMN purchase_acceptance_item.acceptance_id IS '安装验收单';
COMMENT ON COLUMN purchase_acceptance_item.item_name IS 'item名称';
COMMENT ON COLUMN purchase_acceptance_item.check_standard IS 'check standard';
COMMENT ON COLUMN purchase_acceptance_item.check_result IS 'check result';
COMMENT ON COLUMN purchase_acceptance_item.is_passed IS '是否passed';
COMMENT ON COLUMN purchase_acceptance_item.checker_id IS '盘点人';
COMMENT ON COLUMN purchase_acceptance_item.remark IS '备注';
COMMENT ON COLUMN purchase_acceptance_item.sort_order IS '排序号';

-- 验收小组成员
CREATE TABLE IF NOT EXISTS purchase_acceptance_member (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    acceptance_id UUID NOT NULL REFERENCES purchase_acceptance(id) ON DELETE CASCADE,
    member_role VARCHAR(30) NOT NULL,
    user_id UUID REFERENCES sys_user(id),
    member_name VARCHAR(100),
    signed_at TIMESTAMP WITH TIME ZONE,
    signature_url VARCHAR(500),
    remark TEXT
);
COMMENT ON TABLE purchase_acceptance_member IS 'purchase acceptance member';
COMMENT ON COLUMN purchase_acceptance_member.id IS '主键';
COMMENT ON COLUMN purchase_acceptance_member.acceptance_id IS '安装验收单';
COMMENT ON COLUMN purchase_acceptance_member.member_role IS 'member role';
COMMENT ON COLUMN purchase_acceptance_member.user_id IS '关联用户';
COMMENT ON COLUMN purchase_acceptance_member.member_name IS 'member名称';
COMMENT ON COLUMN purchase_acceptance_member.signed_at IS 'signed时间';
COMMENT ON COLUMN purchase_acceptance_member.signature_url IS 'signature附件地址';
COMMENT ON COLUMN purchase_acceptance_member.remark IS '备注';

-- 投标人（结构化）
CREATE TABLE IF NOT EXISTS purchase_bidder (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES purchase_project(id) ON DELETE CASCADE,
    bidder_name VARCHAR(200) NOT NULL,
    bid_amount DECIMAL(15,2),
    contact_person VARCHAR(100),
    contact_phone VARCHAR(50),
    is_winner BOOLEAN DEFAULT false,
    bid_doc_url VARCHAR(500),
    remark TEXT,
    sort_order INTEGER DEFAULT 0
);
COMMENT ON TABLE purchase_bidder IS 'purchase bidder';
COMMENT ON COLUMN purchase_bidder.id IS '主键';
COMMENT ON COLUMN purchase_bidder.project_id IS '采购项目';
COMMENT ON COLUMN purchase_bidder.bidder_name IS '投标人名称';
COMMENT ON COLUMN purchase_bidder.bid_amount IS 'bid金额';
COMMENT ON COLUMN purchase_bidder.contact_person IS 'contact person';
COMMENT ON COLUMN purchase_bidder.contact_phone IS '联系电话';
COMMENT ON COLUMN purchase_bidder.is_winner IS '是否winner';
COMMENT ON COLUMN purchase_bidder.bid_doc_url IS 'biddoc附件地址';
COMMENT ON COLUMN purchase_bidder.remark IS '备注';
COMMENT ON COLUMN purchase_bidder.sort_order IS '排序号';

-- 质疑投诉记录
CREATE TABLE IF NOT EXISTS purchase_complaint (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES purchase_project(id) ON DELETE CASCADE,
    complaint_date DATE,
    complaint_type VARCHAR(30) DEFAULT 'query',
    complainant VARCHAR(200),
    content TEXT,
    resolution TEXT,
    resolved_at DATE,
    status VARCHAR(20) DEFAULT 'open',
    attachment_url VARCHAR(500)
);
COMMENT ON TABLE purchase_complaint IS 'purchase complaint';
COMMENT ON COLUMN purchase_complaint.id IS '主键';
COMMENT ON COLUMN purchase_complaint.project_id IS '采购项目';
COMMENT ON COLUMN purchase_complaint.complaint_date IS 'complaint日期';
COMMENT ON COLUMN purchase_complaint.complaint_type IS 'complaint type';
COMMENT ON COLUMN purchase_complaint.complainant IS 'complainant';
COMMENT ON COLUMN purchase_complaint.content IS '内容';
COMMENT ON COLUMN purchase_complaint.resolution IS 'resolution';
COMMENT ON COLUMN purchase_complaint.resolved_at IS 'resolved时间';
COMMENT ON COLUMN purchase_complaint.status IS '状态';
COMMENT ON COLUMN purchase_complaint.attachment_url IS 'attachment附件地址';

-- 招标过程事件（时间轴）
CREATE TABLE IF NOT EXISTS purchase_project_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES purchase_project(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL,
    event_date DATE,
    event_title VARCHAR(200),
    event_desc TEXT,
    attachment_url VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE purchase_project_event IS 'purchase project event';
COMMENT ON COLUMN purchase_project_event.id IS '主键';
COMMENT ON COLUMN purchase_project_event.project_id IS '采购项目';
COMMENT ON COLUMN purchase_project_event.event_type IS 'event type';
COMMENT ON COLUMN purchase_project_event.event_date IS 'event日期';
COMMENT ON COLUMN purchase_project_event.event_title IS 'event title';
COMMENT ON COLUMN purchase_project_event.event_desc IS 'event desc';
COMMENT ON COLUMN purchase_project_event.attachment_url IS 'attachment附件地址';
COMMENT ON COLUMN purchase_project_event.created_at IS '创建时间';

-- MEIS V12: 采购131415阶段 — 看板预警快照去重
CREATE TABLE IF NOT EXISTS purchase_alert_snapshot (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_key VARCHAR(80) NOT NULL UNIQUE,
    alert_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT,
    level VARCHAR(20) DEFAULT 'warning',
    ref_code VARCHAR(60),
    notified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);
COMMENT ON TABLE purchase_alert_snapshot IS 'purchase alert snapshot';
COMMENT ON COLUMN purchase_alert_snapshot.id IS '主键';
COMMENT ON COLUMN purchase_alert_snapshot.alert_key IS 'alert key';
COMMENT ON COLUMN purchase_alert_snapshot.alert_type IS 'alert type';
COMMENT ON COLUMN purchase_alert_snapshot.title IS '标题';
COMMENT ON COLUMN purchase_alert_snapshot.message IS 'message';
COMMENT ON COLUMN purchase_alert_snapshot.level IS '层级';
COMMENT ON COLUMN purchase_alert_snapshot.ref_code IS 'ref编码';
COMMENT ON COLUMN purchase_alert_snapshot.notified_at IS 'notified时间';
COMMENT ON COLUMN purchase_alert_snapshot.resolved_at IS 'resolved时间';

-- 租户导入模板字段配置（按 business_type + profile_code 扩展）
CREATE TABLE IF NOT EXISTS import_template_field (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_type VARCHAR(50) NOT NULL,
    profile_code VARCHAR(50) NOT NULL DEFAULT 'default',
    field_key VARCHAR(100) NOT NULL,
    field_label VARCHAR(200) NOT NULL,
    field_type VARCHAR(20) NOT NULL DEFAULT 'string',
    target_column VARCHAR(100),
    required BOOLEAN DEFAULT FALSE,
    sort_order INTEGER DEFAULT 100,
    is_extension BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (business_type, profile_code, field_key)
);
COMMENT ON TABLE import_template_field IS '导入模板字段配置';
COMMENT ON COLUMN import_template_field.id IS '主键';
COMMENT ON COLUMN import_template_field.business_type IS '业务类型标识';
COMMENT ON COLUMN import_template_field.profile_code IS '导入方案编码';
COMMENT ON COLUMN import_template_field.field_key IS '字段键';
COMMENT ON COLUMN import_template_field.field_label IS '字段显示名';
COMMENT ON COLUMN import_template_field.field_type IS '字段数据类型';
COMMENT ON COLUMN import_template_field.target_column IS '映射物理列名';
COMMENT ON COLUMN import_template_field.required IS '是否必填';
COMMENT ON COLUMN import_template_field.sort_order IS '排序号';
COMMENT ON COLUMN import_template_field.is_extension IS '是否扩展字段';
COMMENT ON COLUMN import_template_field.is_active IS '是否启用';
COMMENT ON COLUMN import_template_field.remark IS '备注';
COMMENT ON COLUMN import_template_field.created_at IS '创建时间';
COMMENT ON COLUMN import_template_field.updated_at IS '更新时间';

-- 租户业务导入方案绑定（可选，未配置则使用租户编码作为 profile_code 查找扩展列）
CREATE TABLE IF NOT EXISTS import_profile_binding (
    business_type VARCHAR(50) PRIMARY KEY,
    profile_code VARCHAR(50) NOT NULL DEFAULT 'default',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE import_profile_binding IS '导入方案绑定';
COMMENT ON COLUMN import_profile_binding.business_type IS '业务类型标识';
COMMENT ON COLUMN import_profile_binding.profile_code IS '导入方案编码';
COMMENT ON COLUMN import_profile_binding.updated_at IS '更新时间';

-- 模块8：库房管理 — 设备退货
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
COMMENT ON TABLE device_return IS '设备退货单';
COMMENT ON COLUMN device_return.id IS '主键';
COMMENT ON COLUMN device_return.return_no IS '退货单号';
COMMENT ON COLUMN device_return.outbound_id IS '关联出库单';
COMMENT ON COLUMN device_return.warehouse_id IS '退回库房';
COMMENT ON COLUMN device_return.dept_id IS '退货科室';
COMMENT ON COLUMN device_return.returner_id IS '退货人';
COMMENT ON COLUMN device_return.return_date IS '退货日期';
COMMENT ON COLUMN device_return.return_type IS '退货类型';
COMMENT ON COLUMN device_return.reason IS '退货原因';
COMMENT ON COLUMN device_return.doc_status IS '单据状态';
COMMENT ON COLUMN device_return.status IS '退货状态';
COMMENT ON COLUMN device_return.approval_status IS '审批状态';
COMMENT ON COLUMN device_return.operator_id IS '经办人';
COMMENT ON COLUMN device_return.remark IS '备注';

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
COMMENT ON TABLE device_return_item IS '设备退货明细';
COMMENT ON COLUMN device_return_item.id IS '主键';
COMMENT ON COLUMN device_return_item.return_id IS '所属退货单';
COMMENT ON COLUMN device_return_item.device_id IS '关联设备';
COMMENT ON COLUMN device_return_item.device_code IS '设备编码';
COMMENT ON COLUMN device_return_item.device_name IS '设备名称';
COMMENT ON COLUMN device_return_item.quantity IS '数量';
COMMENT ON COLUMN device_return_item.condition_note IS '设备状况说明';
