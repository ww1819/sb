-- 租户 schema 补充字段汇总（V5–V13，仅 ALTER ADD COLUMN，幂等）
-- 不含 INSERT/CREATE TABLE；完整迁移请用 Flyway db/migrations/tenant

-- ========== V5 ==========
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_scrap ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);

-- ========== V6 ==========
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS permissions JSONB;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS permission_mode VARCHAR(20) DEFAULT 'synced';

-- ========== V7 ==========
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS bid_sections TEXT;
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS bid_evaluation TEXT;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS acceptance_status VARCHAR(20) DEFAULT 'pending';
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS invoice_summary TEXT;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS plan_code VARCHAR(30);
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS last_maintained_at DATE;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS dept_id UUID REFERENCES department(id);
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS plan_code VARCHAR(30);
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS plan_name VARCHAR(200);
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS dept_id UUID REFERENCES department(id);
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS start_date DATE;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS end_date DATE;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS frequency VARCHAR(30);
ALTER TABLE inspection_record ADD COLUMN IF NOT EXISTS record_no VARCHAR(30);
ALTER TABLE inspection_record ADD COLUMN IF NOT EXISTS result_summary TEXT;
ALTER TABLE inspection_record ADD COLUMN IF NOT EXISTS inspect_date DATE;

-- ========== V8 ==========
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS plan_type VARCHAR(20) DEFAULT 'annual';
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS fund_source VARCHAR(30);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS use_dept_id UUID REFERENCES department(id);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS is_imported BOOLEAN DEFAULT false;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS registration_no VARCHAR(100);
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS argument_report_url VARCHAR(500);
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS budget_amount DECIMAL(15,2);
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS delivery_deadline DATE;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS acceptance_report_url VARCHAR(500);
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS supplier_id UUID REFERENCES supplier(id);

-- ========== V9 ==========
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS paid_amount DECIMAL(15,2) DEFAULT 0;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS payment_progress DECIMAL(5,2) DEFAULT 0;
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS finance_auditor_id UUID REFERENCES sys_user(id);
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS finance_audit_date DATE;
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS invoice_type VARCHAR(30);
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(15,2);
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS voucher_no VARCHAR(50);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS acceptance_id UUID;
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS project_id UUID REFERENCES purchase_project(id);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS plan_id UUID REFERENCES purchase_plan(id);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS trace_no VARCHAR(60);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS contract_id UUID REFERENCES purchase_contract(id);
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE purchase_acceptance ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';

-- ========== V10 ==========
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS business_chain_no VARCHAR(40);
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS business_chain_no VARCHAR(40);
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS business_chain_no VARCHAR(40);
ALTER TABLE purchase_acceptance ADD COLUMN IF NOT EXISTS business_chain_no VARCHAR(40);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS business_chain_no VARCHAR(40);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS is_large_equipment BOOLEAN DEFAULT false;
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS large_equipment_class VARCHAR(20);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS benefit_analysis_url VARCHAR(500);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS dept_argument_url VARCHAR(500);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS unit VARCHAR(20);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS brand_intent VARCHAR(100);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS is_metrology BOOLEAN DEFAULT false;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS udi_code VARCHAR(100);
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS bid_agency VARCHAR(200);
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS notice_date DATE;
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS control_price DECIMAL(15,2);
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS contract_type VARCHAR(30) DEFAULT 'purchase';
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS performance_bond DECIMAL(15,2);
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS registration_cert_url VARCHAR(500);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;

-- ========== V13 拼音简码 ==========
ALTER TABLE department ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE manufacturer ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_department_pinyin_code ON department(pinyin_code);
CREATE INDEX IF NOT EXISTS idx_supplier_pinyin_code ON supplier(pinyin_code);
CREATE INDEX IF NOT EXISTS idx_manufacturer_pinyin_code ON manufacturer(pinyin_code);

-- ========== V14 导入模板扩展 ==========
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS extension_data JSONB NOT NULL DEFAULT '{}'::jsonb;

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

CREATE INDEX IF NOT EXISTS idx_import_template_field_biz_profile
    ON import_template_field(business_type, profile_code);

-- ========== V15 设备台账扩展字段 ==========
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS registration_no VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS production_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS service_life_years INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS calibration_period_days INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS last_calibration_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS next_calibration_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS service_expiry_date DATE;

CREATE INDEX IF NOT EXISTS idx_device_production_date ON medical_device(production_date);
CREATE INDEX IF NOT EXISTS idx_device_next_calibration ON medical_device(next_calibration_date);
CREATE INDEX IF NOT EXISTS idx_device_service_expiry ON medical_device(service_expiry_date);

CREATE TABLE IF NOT EXISTS import_profile_binding (
    business_type VARCHAR(50) PRIMARY KEY,
    profile_code VARCHAR(50) NOT NULL DEFAULT 'default',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
