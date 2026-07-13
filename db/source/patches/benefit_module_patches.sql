-- 模块13：效益分析 — 租户补丁（幂等）

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS benefit_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

CREATE INDEX IF NOT EXISTS idx_benefit_mapping_device ON benefit_mapping(device_id);
CREATE INDEX IF NOT EXISTS idx_benefit_mapping_charge ON benefit_mapping(charge_code);

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('benefit_level', 'excellent', '优秀', 'excellent', 1),
('benefit_level', 'good', '良好', 'good', 2),
('benefit_level', 'normal', '一般', 'normal', 3),
('benefit_level', 'poor', '较差', 'poor', 4),
('cost_type', 'repair', '维修费', 'repair', 1),
('cost_type', 'maintain', '保养费', 'maintain', 2),
('cost_type', 'power', '电费', 'power', 3),
('cost_type', 'depreciation', '折旧', 'depreciation', 4),
('cost_type', 'consumable', '耗材', 'consumable', 5),
('cost_type', 'other', '其他', 'other', 6),
('benefit_data_source', 'manual', '手工录入', 'manual', 1),
('benefit_data_source', 'HIS', 'HIS', 'HIS', 2),
('benefit_data_source', 'PACS', 'PACS', 'PACS', 3),
('benefit_data_source', 'LIS', 'LIS', 'LIS', 4),
('benefit_data_source', 'HRP', 'HRP', 'HRP', 5)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
