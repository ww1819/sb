-- 导入模板扩展：设备台账扩展字段 + 租户可配置导入列

-- 设备台账客户扩展数据（未建模字段存入 JSONB）
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS extension_data JSONB NOT NULL DEFAULT '{}'::jsonb;

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

CREATE INDEX IF NOT EXISTS idx_import_template_field_biz_profile
    ON import_template_field(business_type, profile_code);

-- 租户业务导入方案绑定（可选，未配置则使用租户编码作为 profile_code 查找扩展列）
CREATE TABLE IF NOT EXISTS import_profile_binding (
    business_type VARCHAR(50) PRIMARY KEY,
    profile_code VARCHAR(50) NOT NULL DEFAULT 'default',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE import_template_field IS '导入模板字段配置：标准字段在代码中定义，本表用于客户扩展列';
COMMENT ON COLUMN import_template_field.is_extension IS 'true 时写入 extension_data；false 且 target_column 有值时写入对应列';
COMMENT ON TABLE import_profile_binding IS '租户级导入方案绑定，如某医院设备台账使用 profile_code=hospital_a';
