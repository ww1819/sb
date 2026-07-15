-- =============================================================================
-- 租户 schema 业务补列手工镜像（历史；权威见 R__columns_biz.sql）
-- 【DEPRECATED】勿在此新增语句。新补列请写 Flyway 槽位。
-- 标准七列（created_at/updated_at/created_by/updated_by/is_deleted/deleted_at/deleted_by）
-- 由 R__columns_audit.sql 维护，勿在此文件重复散落。详见 docs 附录 G.0。
-- =============================================================================

ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS audit_status VARCHAR(20) DEFAULT 'pending';

-- ---------- repair_workorder锛堟瘡鍒椾竴鏉★級 ----------
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS repair_sub_status VARCHAR(30);
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS dispatch_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP WITH TIME ZONE;

-- ---------- 鏁版嵁淇涓庡瓧鍏革紙闈?DDL锛屽彲閲嶅锛?----------
UPDATE inventory_check
SET audit_status = 'approved'
WHERE approved_by IS NOT NULL AND COALESCE(audit_status, 'pending') = 'pending';

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('audit_status', 'pending', '寰呭鏍?, 'pending', 1),
('audit_status', 'approved', '宸插鏍?, 'approved', 2)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

UPDATE repair_workorder SET status = 'pending_accept' WHERE status = 'dispatched';
UPDATE repair_workorder SET status = 'repairing' WHERE status = 'in_progress';
UPDATE repair_workorder SET status = 'pending_verify' WHERE status = 'completed';
UPDATE repair_workorder SET status = 'verified'
WHERE status = 'accepted' AND verify_time IS NOT NULL;

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order)
SELECT 'device_status', 'pending_verify', '宸茬淮淇緟楠屾敹', 'pending_verify', 5
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict WHERE dict_type = 'device_status' AND dict_code = 'pending_verify'
);

UPDATE medical_device d
SET device_status = 'pending_verify', updated_at = NOW()
FROM repair_workorder w
WHERE w.device_id = d.id
  AND w.status = 'pending_verify'
  AND COALESCE(d.device_status, '') = 'maintenance';

DELETE FROM sys_dict WHERE dict_type = 'wo_status';
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('wo_status', 'reported', '鎶ヤ慨涓?, 'reported', 1),
('wo_status', 'dispatching', '娲惧崟涓?, 'dispatching', 2),
('wo_status', 'pending_accept', '寰呮帴鍗?, 'pending_accept', 3),
('wo_status', 'accepted', '宸叉帴鍗?, 'accepted', 4),
('wo_status', 'repairing', '缁翠慨涓?, 'repairing', 5),
('wo_status', 'pending_verify', '宸茬淮淇緟楠屾敹', 'pending_verify', 6),
('wo_status', 'verified', '宸查獙鏀?, 'verified', 7),
('wo_status', 'closed', '宸插叧闂?, 'closed', 8),
('wo_status', 'cancelled', '宸插彇娑?, 'cancelled', 9),
('wo_status', 'suspended', '宸叉寕璧?, 'suspended', 10);

DELETE FROM sys_dict WHERE dict_type = 'repair_sub_status';
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('repair_sub_status', 'internal', '闄㈠唴缁翠慨', 'internal', 1),
('repair_sub_status', 'external', '闄㈠缁翠慨', 'external', 2),
('repair_sub_status', 'waiting_parts', '绛夊緟閰嶄欢', 'waiting_parts', 3),
('repair_sub_status', 'waiting_approval', '寰呭鎵?, 'waiting_approval', 4),
('repair_sub_status', 'on_site', '宸插埌鍦?, 'on_site', 5),
('repair_sub_status', 'diagnosing', '璇婃柇涓?, 'diagnosing', 6),
('repair_sub_status', 'testing', '璋冭瘯涓?, 'testing', 7);

-- ---------- 鍩虹瀛楀吀妯″潡锛氳ˉ鍒?----------
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE manufacturer ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE department ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE warehouse ADD COLUMN IF NOT EXISTS warehouse_type VARCHAR(30) DEFAULT 'device';
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS asset_category_id UUID;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS finance_category_id UUID;
ALTER TABLE asset_category ADD COLUMN IF NOT EXISTS depreciation_years INTEGER;
ALTER TABLE asset_category ADD COLUMN IF NOT EXISTS residual_rate DECIMAL(5,2);
ALTER TABLE finance_category ADD COLUMN IF NOT EXISTS account_subject VARCHAR(50);
ALTER TABLE finance_category ADD COLUMN IF NOT EXISTS fund_source VARCHAR(50);

DO $finance_cat_fix$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'finance_category' AND column_name = 'category_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'finance_category' AND column_name = 'finance_code'
    ) THEN
        ALTER TABLE finance_category RENAME COLUMN category_code TO finance_code;
        ALTER TABLE finance_category RENAME COLUMN category_name TO finance_name;
    END IF;
END $finance_cat_fix$;

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('warehouse_type', 'device', '璁惧搴?, 'device', 1),
('warehouse_type', 'spare', '澶囦欢搴?, 'spare', 2),
('warehouse_type', 'consumable', '鑰楁潗搴?, 'consumable', 3),
('unit_type', 'quantity', '鏁伴噺', 'quantity', 1),
('unit_type', 'weight', '閲嶉噺', 'weight', 2),
('unit_type', 'volume', '浣撶Н', 'volume', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

INSERT INTO unit_dict (unit_code, unit_name, unit_type, sort_order) VALUES
('pcs', '涓?, 'quantity', 1),
('set', '濂?, 'quantity', 2),
('box', '鐩?, 'quantity', 3),
('piece', '浠?, 'quantity', 4),
('unit', '鍙?, 'quantity', 5),
('kg', '鍗冨厠', 'weight', 10),
('g', '鍏?, 'weight', 11),
('l', '鍗?, 'volume', 20),
('ml', '姣崌', 'volume', 21)
ON CONFLICT (unit_code) DO NOTHING;

-- ---------- 缁翠慨绠＄悊妯″潡锛氶厤浠舵。妗堣ˉ鍒?----------
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS model VARCHAR(100);
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS unit_id UUID;
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS manufacturer_id UUID;
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS ref_no VARCHAR(50);
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS remark TEXT;

-- ---------- 璧勪骇鍙拌处妯″潡锛氳澶囨。妗堣ˉ鍒?----------
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS registration_no VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS production_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS service_life_years INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS calibration_period_days INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS last_calibration_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS next_calibration_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS service_expiry_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS extension_data JSONB DEFAULT '{}'::jsonb;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_metrology BOOLEAN DEFAULT FALSE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_maintain_device BOOLEAN DEFAULT FALSE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_inspection_device BOOLEAN DEFAULT FALSE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);

UPDATE medical_device SET extension_data = '{}'::jsonb WHERE extension_data IS NULL;

-- ---------- 搴撴埧绠＄悊妯″潡锛氳ˉ鍒?----------
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS from_warehouse_id UUID;
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS to_warehouse_id UUID;
ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS warehouse_id UUID;

-- ---------- 鐢垫祦鐩戞祴 / 璁惧鍙拌处锛氳ˉ鍒?----------
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_shared_device BOOLEAN DEFAULT FALSE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS metrology_type_code VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS shared_fee_mode VARCHAR(20);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS shared_fee_time_unit VARCHAR(10);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS shared_fee_unit_price DECIMAL(12,2);
ALTER TABLE shared_device_loan ADD COLUMN IF NOT EXISTS fee_mode VARCHAR(20);
ALTER TABLE shared_device_loan ADD COLUMN IF NOT EXISTS fee_time_unit VARCHAR(10);
ALTER TABLE shared_device_loan ADD COLUMN IF NOT EXISTS fee_unit_price DECIMAL(12,2);
ALTER TABLE shared_device_loan ADD COLUMN IF NOT EXISTS billing_start_at TIMESTAMPTZ;
ALTER TABLE shared_device_loan ADD COLUMN IF NOT EXISTS billing_end_at TIMESTAMPTZ;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_pm_device BOOLEAN DEFAULT FALSE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standby_current_max_ma DECIMAL(10,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standby_current_min_ma DECIMAL(10,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS residual_rate DECIMAL(5,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS accrued_disposal_cost DECIMAL(15,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS depreciation_start_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS depreciated_months INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS estimated_useful_life_months INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS monthly_depreciation_rate DECIMAL(8,4);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS depreciation_status VARCHAR(20);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS contract_name VARCHAR(200);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS contract_sign_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS contract_price DECIMAL(15,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS contract_submit_time TIMESTAMPTZ;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS bid_win_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS supply_notice_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS delivery_deadline DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS first_acceptance_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS second_acceptance_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS warranty_expiry_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS maintenance_company VARCHAR(200);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS maintenance_phone VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS supplier_phone VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS supplier_contact VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS maintenance_engineer VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS material_category_code VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS material_group VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS asset_class_code VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS asset_class_name VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS kingdee_asset_code VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS invoice_no VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS invoice_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS expense_item_code VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS expense_item_name VARCHAR(200);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS fund_source VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS lease_fee_per_use DECIMAL(12,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS lease_fee_per_day DECIMAL(12,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS supplier_uscc VARCHAR(30);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS maintenance_uscc VARCHAR(30);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS manufacturer_uscc VARCHAR(30);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS electronic_tag_barcode VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS boot_current_min_ma DECIMAL(10,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS boot_current_max_ma DECIMAL(10,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS risk_assessment VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS inventory_category VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS after_sales_engineer VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS after_sales_engineer_phone VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS warranty_start_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS warranty_service_end_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS warranty_period_years INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS warranty_type VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS has_network_function BOOLEAN;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS same_batch_purchase_count INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standard_function_count INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS purchase_expected_benefit VARCHAR(200);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS rated_workload VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS device_unit VARCHAR(30);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS unit_id UUID;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS manage_dept_id UUID;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS location_floor VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS room_number VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS card_code VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS use_dept_head VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS manage_dept_head VARCHAR(100);
ALTER TABLE power_tag ADD COLUMN IF NOT EXISTS device_code VARCHAR(20);
ALTER TABLE power_tag ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);

-- ---------- 系统配置：分类 + 编号/名称 + 值1~值6 ----------
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS category_code VARCHAR(20);
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS category_name VARCHAR(100);
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS item_code VARCHAR(20);
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS item_name VARCHAR(200);
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS value1 TEXT;
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS value2 TEXT;
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS value3 TEXT;
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS value4 TEXT;
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS value5 TEXT;
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS value6 TEXT;
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;

-- 软删除与审计字段补列见 tenant/R__columns_audit.sql（非业务逐表 ALTER）

