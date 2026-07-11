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

-- ---------- 维修管理模块：配件档案补列 ----------
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS model VARCHAR(100);
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS unit_id UUID;
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS manufacturer_id UUID;
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS ref_no VARCHAR(50);
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS remark TEXT;

-- ---------- 保养管理模块：参数/计划/执行补列 ----------
ALTER TABLE maintenance_template ADD COLUMN IF NOT EXISTS template_code VARCHAR(30);
ALTER TABLE maintenance_template ADD COLUMN IF NOT EXISTS maintenance_level_id UUID;
ALTER TABLE maintenance_template ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS cycle_days INTEGER;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE;

INSERT INTO maintenance_level (level_code, level_name, sort_order, description) VALUES
('L1', '日常保养', 1, '每日或每周例行保养'),
('L2', '一级保养', 2, '月度基础保养'),
('L3', '二级保养', 3, '季度深度保养'),
('L4', '三级保养', 4, '年度全面保养')
ON CONFLICT (level_code) DO NOTHING;

-- ---------- 巡检管理模块：参数/计划/执行补列 ----------
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS template_id UUID;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS inspection_type_id UUID;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS cycle_days INTEGER;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS next_due_date DATE;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS last_inspected_at DATE;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS assigned_inspector_id UUID;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS remark TEXT;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

INSERT INTO inspection_type (type_code, type_name, sort_order, description) VALUES
('ROUTINE', '日常巡检', 1, '每日或每周例行巡检'),
('SAFETY', '安全巡检', 2, '安全隐患排查'),
('DEPT', '科室巡检', 3, '科室责任区巡检'),
('SPECIAL', '专项巡检', 4, '专项检查巡检')
ON CONFLICT (type_code) DO NOTHING;

-- ---------- 计量管理模块：参数/计划/执行（新表由补丁创建，此处仅种子） ----------
INSERT INTO metrology_category (category_code, category_name, sort_order, description) VALUES
('FORCE', '力学计量', 1, '压力、力值等'),
('ELECTRIC', '电学计量', 2, '电压、电流、电阻等'),
('TEMP', '热学计量', 3, '温度、湿度等'),
('LENGTH', '长度计量', 4, '尺寸、量具等')
ON CONFLICT (category_code) DO NOTHING;

-- ---------- 不良事件模块：字典种子 ----------
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('adverse_event_type', 'malfunction', '设备故障', 'malfunction', 1),
('adverse_event_type', 'injury', '人员伤害', 'injury', 2),
('adverse_event_type', 'misuse', '使用不当', 'misuse', 3),
('adverse_event_type', 'quality', '质量问题', 'quality', 4),
('adverse_event_type', 'other', '其他', 'other', 5),
('adverse_severity', 'minor', '轻微', 'minor', 1),
('adverse_severity', 'moderate', '一般', 'moderate', 2),
('adverse_severity', 'serious', '严重', 'serious', 3),
('adverse_severity', 'critical', '重大', 'critical', 4),
('adverse_status', 'reported', '已上报', 'reported', 1),
('adverse_status', 'handling', '处理中', 'handling', 2),
('adverse_status', 'reviewed', '已审核', 'reviewed', 3),
('adverse_status', 'closed', '已结案', 'closed', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ---------- 库房管理模块：补列 ----------
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS from_warehouse_id UUID;
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS to_warehouse_id UUID;
ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS warehouse_id UUID;

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('return_type', 'unused', '未使用退回', 'unused', 1),
('return_type', 'quality', '质量问题', 'quality', 2),
('return_type', 'damage', '损坏退回', 'damage', 3),
('return_type', 'other', '其他', 'other', 4),
('return_status', 'draft', '草稿', 'draft', 1),
('return_status', 'returned', '已退库', 'returned', 2),
('transfer_type', 'warehouse', '库房间调拨', 'warehouse', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('device_return_default', '设备退货审批', 'device_return')
ON CONFLICT (flow_code) DO NOTHING;

INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '装备部审核', 'equipment_head', 0 FROM sys_approval_flow f WHERE f.flow_code = 'device_return_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

-- ---------- 特种设备模块：字典种子 ----------
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('special_type', 'radiation', '放射辐射类', 'radiation', 1),
('special_type', 'pressure', '压力容器类', 'pressure', 2),
('special_type', 'elevator', '电梯类', 'elevator', 3),
('special_type', 'other', '其他特种', 'other', 4),
('criticality_level', 'critical', '极高', 'critical', 1),
('criticality_level', 'high', '高', 'high', 2),
('criticality_level', 'medium', '中', 'medium', 3),
('standby_status', 'ready', '待用', 'ready', 1),
('standby_status', 'in_use', '使用中', 'in_use', 2),
('standby_status', 'maintenance', '维护中', 'maintenance', 3),
('lease_status', 'active', '租赁中', 'active', 1),
('lease_status', 'expired', '已到期', 'expired', 2),
('lease_status', 'returned', '已退租', 'returned', 3),
('allocation_status', 'pending', '待审批', 'pending', 1),
('allocation_status', 'approved', '已调配', 'approved', 2),
('allocation_status', 'returned', '已归还', 'returned', 3),
('urgency_level', 'normal', '一般', 'normal', 1),
('urgency_level', 'urgent', '紧急', 'urgent', 2),
('urgency_level', 'critical', '特急', 'critical', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ---------- 公用设备借调模块 ----------
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_shared_device BOOLEAN DEFAULT FALSE;

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('shared_availability', 'available', '可借', 'available', 1),
('shared_availability', 'on_loan', '借出中', 'on_loan', 2),
('shared_availability', 'maintenance', '维护中', 'maintenance', 3),
('loan_status', 'draft', '草稿', 'draft', 1),
('loan_status', 'pending', '待审批', 'pending', 2),
('loan_status', 'approved', '已审批', 'approved', 3),
('loan_status', 'on_loan', '借出中', 'on_loan', 4),
('loan_status', 'returned', '已归还', 'returned', 5),
('loan_status', 'rejected', '已驳回', 'rejected', 6),
('return_status', 'pending', '待审批', 'pending', 1),
('return_status', 'approved', '已审批', 'approved', 2),
('return_status', 'rejected', '已驳回', 'rejected', 3),
('paid_status', 'unpaid', '未收费', 'unpaid', 1),
('paid_status', 'paid', '已收费', 'paid', 2),
('paid_status', 'waived', '已减免', 'waived', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('shared_loan_default', '公用设备借调审批', 'shared_device_loan'),
('shared_return_default', '公用设备归还审批', 'shared_device_return')
ON CONFLICT (flow_code) DO NOTHING;

-- ---------- 预防性维护模块 ----------
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_pm_device BOOLEAN DEFAULT FALSE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standby_current_max_ma DECIMAL(10,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standby_current_min_ma DECIMAL(10,2);
ALTER TABLE power_tag ADD COLUMN IF NOT EXISTS device_code VARCHAR(20);
ALTER TABLE power_tag ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('pm_risk_level', 'low', '低', 'low', 1),
('pm_risk_level', 'medium', '中', 'medium', 2),
('pm_risk_level', 'high', '高', 'high', 3),
('pm_risk_level', 'critical', '极高', 'critical', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ---------- 效益分析模块（字典种子） ----------
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

-- ---------- 电流监测模块 ----------
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('power_protocol_type', 'mqtt', 'MQTT', 'mqtt', 1),
('power_protocol_type', 'modbus', 'Modbus', 'modbus', 2),
('power_protocol_type', 'http', 'HTTP', 'http', 3),
('power_station_status', 'online', '在线', 'online', 1),
('power_station_status', 'offline', '离线', 'offline', 2),
('power_station_status', 'maintenance', '维护中', 'maintenance', 3),
('power_work_state', 'running', '运行中', 'running', 1),
('power_work_state', 'idle', '待机', 'idle', 2),
('power_work_state', 'offline', '离线', 'offline', 3),
('power_work_state', 'alarm', '告警', 'alarm', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
