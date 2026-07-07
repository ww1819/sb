-- MEIS V10: 采购789阶段 — 统一追溯链、医院合规字段、版本号

-- 统一业务链编号（计划→项目→合同→验收→入库）
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS business_chain_no VARCHAR(40);
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS business_chain_no VARCHAR(40);
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS business_chain_no VARCHAR(40);
ALTER TABLE purchase_acceptance ADD COLUMN IF NOT EXISTS business_chain_no VARCHAR(40);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS business_chain_no VARCHAR(40);

-- 计划扩展（医院合规）
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS is_large_equipment BOOLEAN DEFAULT false;
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS large_equipment_class VARCHAR(20);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS benefit_analysis_url VARCHAR(500);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS dept_argument_url VARCHAR(500);

-- 计划明细扩展
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS unit VARCHAR(20);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS brand_intent VARCHAR(100);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS is_metrology BOOLEAN DEFAULT false;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS udi_code VARCHAR(100);

-- 项目扩展
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS bid_agency VARCHAR(200);
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS notice_date DATE;
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS control_price DECIMAL(15,2);

-- 合同扩展
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS contract_type VARCHAR(30) DEFAULT 'purchase';
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS performance_bond DECIMAL(15,2);
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS registration_cert_url VARCHAR(500);

-- 乐观锁版本号
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;

-- 字典
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('large_equipment_class', 'class_a', '甲类', 'class_a', 1),
('large_equipment_class', 'class_b', '乙类', 'class_b', 2),
('contract_type', 'purchase', '采购合同', 'purchase', 1),
('contract_type', 'maintenance', '维保合同', 'maintenance', 2),
('contract_type', 'service', '服务合同', 'service', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- 回填已有数据业务链号
UPDATE purchase_plan SET business_chain_no = plan_code WHERE business_chain_no IS NULL;
UPDATE purchase_project pj SET business_chain_no = pl.business_chain_no
FROM purchase_plan pl WHERE pj.plan_id = pl.id AND pj.business_chain_no IS NULL;
UPDATE purchase_contract pc SET business_chain_no = pj.business_chain_no
FROM purchase_project pj WHERE pc.project_id = pj.id AND pc.business_chain_no IS NULL;
UPDATE purchase_acceptance pa SET business_chain_no = pc.business_chain_no
FROM purchase_contract pc WHERE pa.contract_id = pc.id AND pa.business_chain_no IS NULL;
UPDATE device_entry de SET business_chain_no = pc.business_chain_no
FROM purchase_contract pc WHERE de.contract_id = pc.id AND de.business_chain_no IS NULL;

UPDATE sys_role SET permissions = '{"menus":["mod_purchase","purchase_plan","purchase_project","purchase_contract","purchase_supplier","purchase_category","purchase_acceptance","purchase_manufacturer","purchase_dashboard","purchase_trace"],"buttons":[],"dataScope":"dept"}'::jsonb
WHERE role_code = 'purchase_staff';
