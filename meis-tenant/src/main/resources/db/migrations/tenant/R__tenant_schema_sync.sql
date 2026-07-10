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
