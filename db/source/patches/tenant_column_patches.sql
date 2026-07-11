-- =============================================================================
-- 租户 schema 补列（可重复迁移 R__）—— 老租户缺列兜底
-- =============================================================================
-- 约定（务必遵守）：
--   1. 新建表 / 完整字段定义 → 只改 V1__tables.sql
--      （老租户更新时由 SchemaTableEnsuring 幂等执行 V1：没有的表会创建）
--   2. 本文件只做「已有表补列」：每条语句只 ADD 一个字段（ADD COLUMN IF NOT EXISTS）
--      禁止一条 ALTER 写多个列，避免老库漏列
--   3. 不要在本文件 CREATE TABLE（建表归 V1）
--   4. 不要在本文件 COMMENT ON（空注释由 SchemaCommentFiller 补，避免覆盖租户自定义）
--   5. 手工镜像：db/source/patches/tenant_column_patches.sql（与本文件保持同步）
-- =============================================================================

-- ---------- inventory_check ----------
ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS audit_status VARCHAR(20) DEFAULT 'pending';

-- ---------- repair_workorder（每列一条） ----------
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS repair_sub_status VARCHAR(30);
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS dispatch_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP WITH TIME ZONE;

-- ---------- 数据修正与字典（非 DDL，可重复） ----------
UPDATE inventory_check
SET audit_status = 'approved'
WHERE approved_by IS NOT NULL AND COALESCE(audit_status, 'pending') = 'pending';

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('audit_status', 'pending', '待审核', 'pending', 1),
('audit_status', 'approved', '已审核', 'approved', 2)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

UPDATE repair_workorder SET status = 'pending_accept' WHERE status = 'dispatched';
UPDATE repair_workorder SET status = 'repairing' WHERE status = 'in_progress';
UPDATE repair_workorder SET status = 'pending_verify' WHERE status = 'completed';
UPDATE repair_workorder SET status = 'verified'
WHERE status = 'accepted' AND verify_time IS NOT NULL;

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order)
SELECT 'device_status', 'pending_verify', '已维修待验收', 'pending_verify', 5
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
('wo_status', 'reported', '报修中', 'reported', 1),
('wo_status', 'dispatching', '派单中', 'dispatching', 2),
('wo_status', 'pending_accept', '待接单', 'pending_accept', 3),
('wo_status', 'accepted', '已接单', 'accepted', 4),
('wo_status', 'repairing', '维修中', 'repairing', 5),
('wo_status', 'pending_verify', '已维修待验收', 'pending_verify', 6),
('wo_status', 'verified', '已验收', 'verified', 7),
('wo_status', 'closed', '已关闭', 'closed', 8),
('wo_status', 'cancelled', '已取消', 'cancelled', 9),
('wo_status', 'suspended', '已挂起', 'suspended', 10);

DELETE FROM sys_dict WHERE dict_type = 'repair_sub_status';
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('repair_sub_status', 'internal', '院内维修', 'internal', 1),
('repair_sub_status', 'external', '院外维修', 'external', 2),
('repair_sub_status', 'waiting_parts', '等待配件', 'waiting_parts', 3),
('repair_sub_status', 'waiting_approval', '待审批', 'waiting_approval', 4),
('repair_sub_status', 'on_site', '已到场', 'on_site', 5),
('repair_sub_status', 'diagnosing', '诊断中', 'diagnosing', 6),
('repair_sub_status', 'testing', '调试中', 'testing', 7);

-- ---------- 基础字典模块：补列 ----------
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
('warehouse_type', 'device', '设备库', 'device', 1),
('warehouse_type', 'spare', '备件库', 'spare', 2),
('warehouse_type', 'consumable', '耗材库', 'consumable', 3),
('unit_type', 'quantity', '数量', 'quantity', 1),
('unit_type', 'weight', '重量', 'weight', 2),
('unit_type', 'volume', '体积', 'volume', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

INSERT INTO unit_dict (unit_code, unit_name, unit_type, sort_order) VALUES
('pcs', '个', 'quantity', 1),
('set', '套', 'quantity', 2),
('box', '盒', 'quantity', 3),
('piece', '件', 'quantity', 4),
('unit', '台', 'quantity', 5),
('kg', '千克', 'weight', 10),
('g', '克', 'weight', 11),
('l', '升', 'volume', 20),
('ml', '毫升', 'volume', 21)
ON CONFLICT (unit_code) DO NOTHING;

-- ---------- 维修管理模块：配件档案补列 ----------
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS model VARCHAR(100);
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS unit_id UUID;
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS manufacturer_id UUID;
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS ref_no VARCHAR(50);
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS remark TEXT;

-- ---------- 资产台账模块：设备档案补列 ----------
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

-- ---------- 库房管理模块：补列 ----------
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS from_warehouse_id UUID;
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS to_warehouse_id UUID;
ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS warehouse_id UUID;

-- ---------- 电流监测 / 设备台账：补列 ----------
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_shared_device BOOLEAN DEFAULT FALSE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_pm_device BOOLEAN DEFAULT FALSE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standby_current_max_ma DECIMAL(10,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standby_current_min_ma DECIMAL(10,2);
ALTER TABLE power_tag ADD COLUMN IF NOT EXISTS device_code VARCHAR(20);
ALTER TABLE power_tag ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);

-- 软删除与审计字段补列见 tenant/R__audit_columns.sql（非事务逐表 ALTER）
