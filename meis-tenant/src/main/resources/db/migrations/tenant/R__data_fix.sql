-- =============================================================================
-- 租户 schema 数据更正 / 字典与种子（可重复迁移 R__）
-- =============================================================================
-- 槽位：R__data_fix.sql（按字母序：columns_audit → columns_biz → data_fix）
-- 约定：
--   1. 本文件仅 INSERT / UPDATE / DELETE / 以数据为主的 DO 块
--   2. 结构性 ALTER 归 R__columns_biz.sql；审计七列归 R__columns_audit.sql
--   3. 不要 CREATE TABLE / CREATE INDEX；不要 COMMENT ON
-- =============================================================================

-- ---------- 数据修正与字典（非 DDL，可重复） ----------
-- 审批状态 draft 展示为「未提交」
UPDATE sys_dict SET dict_label = '未提交'
WHERE dict_type = 'approval_status' AND dict_code = 'draft' AND dict_label IS DISTINCT FROM '未提交';

-- 设备合同简化审批状态（PUR-UI-27）
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('contract_approval_status', 'draft', '未审批', 'draft', 1),
('contract_approval_status', 'pending', '未审批', 'pending', 2),
('contract_approval_status', 'rejected', '未审批', 'rejected', 3),
('contract_approval_status', 'unapproved', '未审批', 'unapproved', 4),
('contract_approval_status', 'approved', '已审批', 'approved', 5)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
UPDATE sys_dict SET dict_label = '未审批'
WHERE dict_type = 'contract_approval_status' AND dict_code IN ('draft', 'pending', 'rejected', 'unapproved')
  AND dict_label IS DISTINCT FROM '未审批';
UPDATE sys_dict SET dict_label = '已审批'
WHERE dict_type = 'contract_approval_status' AND dict_code = 'approved'
  AND dict_label IS DISTINCT FROM '已审批';

-- 安装验收列表审批状态文案（PUR-UI-29）
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('acceptance_review_status', 'draft', '未审核', 'draft', 1),
('acceptance_review_status', 'pending', '未审核', 'pending', 2),
('acceptance_review_status', 'rejected', '未审核', 'rejected', 3),
('acceptance_review_status', 'approved', '已审核', 'approved', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
UPDATE sys_dict SET dict_label = '未审核'
WHERE dict_type = 'acceptance_review_status' AND dict_code IN ('draft', 'pending', 'rejected')
  AND dict_label IS DISTINCT FROM '未审核';
UPDATE sys_dict SET dict_label = '已审核'
WHERE dict_type = 'acceptance_review_status' AND dict_code = 'approved'
  AND dict_label IS DISTINCT FROM '已审核';

-- 安装验收状态：passed 展示为「已经验收」（PUR-UI-29）
UPDATE sys_dict SET dict_label = '已经验收'
WHERE dict_type = 'acceptance_status' AND dict_code = 'passed'
  AND dict_label IS DISTINCT FROM '已经验收';
UPDATE sys_dict SET dict_label = '待验收'
WHERE dict_type = 'acceptance_status' AND dict_code = 'pending'
  AND dict_label IS DISTINCT FROM '待验收';

-- 已通过采购计划若缺审核人，回填终审记录
UPDATE purchase_plan p
SET approved_by = r.approver_id,
    approved_at = COALESCE(p.approved_at, r.acted_at, NOW())
FROM (
    SELECT DISTINCT ON (i.business_id)
           i.business_id, rec.approver_id, rec.acted_at
    FROM sys_approval_instance i
    JOIN sys_approval_record rec ON rec.instance_id = i.id AND rec.action = 'approve'
    WHERE i.business_type = 'purchase_plan' AND i.status = 'approved'
    ORDER BY i.business_id, rec.acted_at DESC NULLS LAST
) r
WHERE p.id = r.business_id
  AND p.approval_status = 'approved'
  AND p.approved_by IS NULL
  AND r.approver_id IS NOT NULL;

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
('wo_status', 'draft', '未提交', 'draft', 0),
('wo_status', 'reported', '报修中', 'reported', 1),
('wo_status', 'dispatching', '派单中', 'dispatching', 2),
('wo_status', 'pending_accept', '待接单', 'pending_accept', 3),
('wo_status', 'accepted', '已接单', 'accepted', 4),
('wo_status', 'repairing', '维修中', 'repairing', 5),
('wo_status', 'pending_verify', '已维修待验收', 'pending_verify', 6),
('wo_status', 'verify_rejected', '拒绝验收', 'verify_rejected', 7),
('wo_status', 'verified', '已验收', 'verified', 8),
('wo_status', 'closed', '已关闭', 'closed', 9),
('wo_status', 'cancelled', '已取消', 'cancelled', 10),
('wo_status', 'suspended', '已挂起', 'suspended', 11);
DELETE FROM sys_dict WHERE dict_type = 'repair_sub_status';
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('repair_sub_status', 'internal', '院内维修', 'internal', 1),
('repair_sub_status', 'external', '院外维修', 'external', 2),
('repair_sub_status', 'waiting_parts', '等待配件', 'waiting_parts', 3),
('repair_sub_status', 'waiting_approval', '待审批', 'waiting_approval', 4),
('repair_sub_status', 'on_site', '已到场', 'on_site', 5),
('repair_sub_status', 'diagnosing', '诊断中', 'diagnosing', 6),
('repair_sub_status', 'testing', '调试中', 'testing', 7),
('repair_sub_status', 'verified', '已验收', 'verified', 8);
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('warehouse_type', 'device', '设备库', 'device', 1),
('warehouse_type', 'spare', '备件库', 'spare', 2),
('warehouse_type', 'consumable', '耗材库', 'consumable', 3),
('unit_type', 'quantity', '数量', 'quantity', 1),
('unit_type', 'weight', '重量', 'weight', 2),
('unit_type', 'volume', '体积', 'volume', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('depreciation_status', 'not_started', '未开始', 'not_started', 1),
('depreciation_status', 'depreciating', '折旧中', 'depreciating', 2),
('depreciation_status', 'completed', '已提足', 'completed', 3),
('depreciation_status', 'suspended', '暂停折旧', 'suspended', 4)
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
UPDATE medical_device SET extension_data = '{}'::jsonb WHERE extension_data IS NULL;
INSERT INTO maintenance_level (level_code, level_name, sort_order, description) VALUES
('L1', '日常保养', 1, '每日或每周例行保养'),
('L2', '一级保养', 2, '月度基础保养'),
('L3', '二级保养', 3, '季度深度保养'),
('L4', '三级保养', 4, '年度全面保养')
ON CONFLICT (level_code) DO NOTHING;
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
-- 计量检定类型（法规属性 / 实施时机 / 执行地点 / 分级管理）
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('metrology_classification_group', 'regulatory', '法规监管属性', 'regulatory', 1),
('metrology_classification_group', 'timing', '实施时机', 'timing', 2),
('metrology_classification_group', 'location', '执行地点', 'location', 3),
('metrology_classification_group', 'grade', '分级管理', 'grade', 4),
('metrology_classification_group', 'device_scope', '适用设备范围', 'device_scope', 5),
('metrology_regulatory_attr', 'mandatory', '强制检定', 'mandatory', 1),
('metrology_regulatory_attr', 'voluntary', '非强制检定', 'voluntary', 2),
('metrology_traceability_mode', 'verification', '检定', 'verification', 1),
('metrology_traceability_mode', 'calibration', '校准', 'calibration', 2),
('metrology_timing_kind', 'first_only', '首次检定（失准报废）', 'first_only', 1),
('metrology_timing_kind', 'periodic', '周期检定', 'periodic', 2),
('metrology_timing_kind', 'after_repair', '修理后检定', 'after_repair', 3),
('metrology_timing_kind', 'arbitration', '临时/仲裁检定', 'arbitration', 4),
('metrology_timing_kind', 'interim', '期间核查', 'interim', 5),
('metrology_location_kind', 'lab', '送检检定', 'lab', 1),
('metrology_location_kind', 'onsite', '现场上门检定', 'onsite', 2),
('metrology_location_kind', 'both', '送检或现场', 'both', 3),
('metrology_management_grade', 'A', 'A级（强检类）', 'A', 1),
('metrology_management_grade', 'B', 'B级（重要非强检）', 'B', 2),
('metrology_management_grade', 'C', 'C级（一般辅助）', 'C', 3),
('metrology_certificate_kind', 'verification_cert', '检定证书', 'verification_cert', 1),
('metrology_certificate_kind', 'calibration_cert', '校准证书', 'calibration_cert', 2),
('metrology_certificate_kind', 'none', '无法定证书', 'none', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
-- 计量检定类型种子：显式限定当前迁移 schema，避免 public.metrology_type 影子表导致 parent_id 外键失败
INSERT INTO "${flyway:defaultSchema}".metrology_type (type_code, type_name, classification_group, regulatory_attr, traceability_mode, certificate_kind, sort_order, legal_basis, executor_scope, cycle_rule, description) VALUES
('MANDATORY', '强制检定（法定强制管理）', 'regulatory', 'mandatory', 'verification', 'verification_cert', 1,
 '《计量法》及《实施强制管理的计量器具目录》', '仅限法定计量技术机构', '按检定规程固定周期，不可自行缩短或延长',
 '用于医疗卫生且标注 V/P+V 的设备，必须按期送法定计量机构检定，超期/不合格严禁临床使用。'),
('VOLUNTARY', '非强制检定（自主溯源）', 'regulatory', 'voluntary', 'calibration', 'calibration_cert', 2,
 '医院自主管理', '法定机构或具备资质第三方校准实验室', '医院结合风险与使用频次自行制定',
 '未列入强检目录但诊疗质控需保证量值准确的设备，可检定或校准，医院自行判定是否可用。')
ON CONFLICT (type_code) DO NOTHING;
INSERT INTO "${flyway:defaultSchema}".metrology_type (type_code, type_name, parent_id, classification_group, regulatory_attr, traceability_mode, timing_kind, certificate_kind, sort_order, cycle_rule, description)
SELECT 'MAND_FIRST_ONCE', '首次检定（失准报废/到期轮换）', id, 'regulatory', 'mandatory', 'verification', 'first_only', 'verification_cert', 1,
 '仅出厂/入库做一次检定，使用中不周期复检，失准直接报废',
 '典型：玻璃水银体温计。'
FROM "${flyway:defaultSchema}".metrology_type WHERE type_code = 'MANDATORY'
ON CONFLICT (type_code) DO NOTHING;
INSERT INTO "${flyway:defaultSchema}".metrology_type (type_code, type_name, parent_id, classification_group, regulatory_attr, traceability_mode, timing_kind, certificate_kind, sort_order, cycle_rule, description)
SELECT 'MAND_PERIODIC', '周期强制检定', id, 'regulatory', 'mandatory', 'verification', 'periodic', 'verification_cert', 2,
 '按检定规程每年/每半年送检，周期不可更改',
 '绝大多数医用强检设备。出具检定证书（合格/不合格结论），带法定计量印记。'
FROM "${flyway:defaultSchema}".metrology_type WHERE type_code = 'MANDATORY'
ON CONFLICT (type_code) DO NOTHING;
INSERT INTO "${flyway:defaultSchema}".metrology_type (type_code, type_name, parent_id, classification_group, regulatory_attr, traceability_mode, timing_kind, sort_order, description)
SELECT v.code, v.name, p.id, 'device_scope', 'mandatory', 'verification', 'periodic', v.ord, v.descr
FROM "${flyway:defaultSchema}".metrology_type p
CROSS JOIN (VALUES
 ('MAND_SCOPE_VITAL', '生命体征类', 1, '电子血压计、水银血压计、电子体温计、多参数监护仪、心电图机、脑电图机'),
 ('MAND_SCOPE_ENT', '眼科耳鼻喉类', 2, '眼压计、纯音听力计、验光仪、验光镜片箱、焦度计'),
 ('MAND_SCOPE_RAD', '放射/核医学类', 3, 'DR/CT/乳腺钼靶、医用诊断X射线机、放射治疗电离室剂量计、医用活度计')
) AS v(code, name, ord, descr)
WHERE p.type_code = 'MAND_PERIODIC'
ON CONFLICT (type_code) DO NOTHING;
INSERT INTO "${flyway:defaultSchema}".metrology_type (type_code, type_name, parent_id, classification_group, regulatory_attr, traceability_mode, sort_order, description)
SELECT 'VOL_SCOPE_COMMON', '常见非强检设备', id, 'device_scope', 'voluntary', 'calibration', 1,
 '呼吸机、麻醉机、注射泵/输液泵、除颤仪、高频电刀、B超/MRI、骨密度仪、肺功能仪、医用激光源、负压压力表、生化分析仪、理疗设备等。'
FROM "${flyway:defaultSchema}".metrology_type WHERE type_code = 'VOLUNTARY'
ON CONFLICT (type_code) DO NOTHING;
INSERT INTO "${flyway:defaultSchema}".metrology_type (type_code, type_name, classification_group, timing_kind, traceability_mode, sort_order, description) VALUES
('TIME_FIRST', '首次检定', 'timing', 'first_only', 'verification', 1, '新设备入库、维修后、更换核心传感器后首次计量，合格后方可投入临床。'),
('TIME_PERIODIC', '周期检定（定期检定）', 'timing', 'periodic', 'verification', 2, '强检设备法定周期、非强检设备医院规定周期，到期统一送检/上门检。'),
('TIME_AFTER_REPAIR', '修理后检定', 'timing', 'after_repair', 'verification', 3, '大修、更换探头/传感器、搬迁、故障维修后必须重新计量，合格再使用。'),
('TIME_ARBITRATION', '临时检定（仲裁检定）', 'timing', 'arbitration', 'verification', 4, '医疗纠纷、数据争议、监管抽查异议时申请法定机构仲裁计量，具备法律效力。'),
('TIME_INTERIM', '期间核查', 'timing', 'interim', 'calibration', 5, '两次周期检定之间医院内部简易比对与稳定性核查，仅内部质控，无法定证书。')
ON CONFLICT (type_code) DO NOTHING;
INSERT INTO "${flyway:defaultSchema}".metrology_type (type_code, type_name, classification_group, location_kind, sort_order, description) VALUES
('LOC_LAB', '送检检定', 'location', 'lab', 1, '小型设备（血压计、体温计、验光镜片等）送至计量实验室检测。'),
('LOC_ONSITE', '现场上门检定', 'location', 'onsite', 2, '大型固定设备（CT、DR、监护仪、直线加速器等）计量人员到院内现场检测。')
ON CONFLICT (type_code) DO NOTHING;
INSERT INTO "${flyway:defaultSchema}".metrology_type (type_code, type_name, classification_group, management_grade, regulatory_attr, traceability_mode, sort_order, cycle_rule, description) VALUES
('GRADE_A', 'A级（强检类）', 'grade', 'A', 'mandatory', 'verification', 1, '法定周期，专人台账、证书存档',
 '全部强制检定设备，专人台账、法定周期、证书存档。'),
('GRADE_B', 'B级（重要非强检）', 'grade', 'B', 'voluntary', 'calibration', 2, '建议每年校准',
 '生命支持、急救、影像设备（呼吸机、除颤仪、彩超等），每年校准。'),
('GRADE_C', 'C级（一般辅助设备）', 'grade', 'C', 'voluntary', 'calibration', 3, '2~3年校准 + 内部期间核查',
 '理疗、常规压力表、小型治疗设备等。')
ON CONFLICT (type_code) DO NOTHING;
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
('device_return_default', '设备退库审批', 'device_return')
ON CONFLICT (flow_code) DO UPDATE SET flow_name = EXCLUDED.flow_name;
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '装备部审核', 'equipment_head', 0 FROM sys_approval_flow f WHERE f.flow_code = 'device_return_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

-- WH-UI-01：供应商退货状态 + 审批流
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('goods_return_status', 'draft', '草稿', 'draft', 1),
('goods_return_status', 'pending', '待审批', 'pending', 2),
('goods_return_status', 'approved', '已审批', 'approved', 3),
('goods_return_status', 'returned', '已退货', 'returned', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('device_status', 'returned', '已退货', 'returned', 90)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('device_goods_return_default', '设备退货审批', 'device_goods_return')
ON CONFLICT (flow_code) DO NOTHING;
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '装备部审核', 'equipment_head', 0 FROM sys_approval_flow f WHERE f.flow_code = 'device_goods_return_default'
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
UPDATE shared_device_loan SET
  fee_mode = COALESCE(fee_mode, 'time'),
  fee_time_unit = COALESCE(fee_time_unit, 'day'),
  fee_unit_price = COALESCE(fee_unit_price, fee_standard, 0)
WHERE fee_unit_price IS NULL OR fee_mode IS NULL;
-- 存量 shared_device 计费合并至台账后删表（附录 N/O）
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'shared_device') THEN
    UPDATE medical_device d SET
      is_shared_device = TRUE,
      shared_fee_mode = COALESCE(d.shared_fee_mode, 'time'),
      shared_fee_time_unit = COALESCE(d.shared_fee_time_unit, 'day'),
      shared_fee_unit_price = COALESCE(d.shared_fee_unit_price, s.fee_standard, 0)
    FROM shared_device s
    WHERE s.device_id = d.id AND COALESCE(s.is_deleted, 0) = 0;
  END IF;
END $$;
DROP TABLE IF EXISTS shared_device CASCADE;
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
('paid_status', 'waived', '已减免', 'waived', 3),
('shared_fee_mode', 'per_use', '按次收费', 'per_use', 1),
('shared_fee_mode', 'time', '计时收费', 'time', 2),
('shared_fee_time_unit', 'month', '月', 'month', 1),
('shared_fee_time_unit', 'day', '天', 'day', 2),
('shared_fee_time_unit', 'hour', '小时', 'hour', 3),
('shared_loan_display_status', 'in_stock', '在库', 'in_stock', 1),
('shared_loan_display_status', 'loan_pending', '借调申请中', 'loan_pending', 2),
('shared_loan_display_status', 'on_loan', '已借用', 'on_loan', 3),
('shared_loan_display_status', 'return_pending', '归还申请中', 'return_pending', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('shared_loan_default', '公用设备借调审批', 'shared_device_loan'),
('shared_return_default', '公用设备归还审批', 'shared_device_return')
ON CONFLICT (flow_code) DO NOTHING;
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
-- ---------- 附录 S/T：报修草稿状态 + 实体变更记录 ----------
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('wo_status', 'draft', '未提交', 'draft', 0)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
UPDATE sys_user u
SET is_repair_engineer = TRUE
FROM engineer e
WHERE e.user_id = u.id
  AND COALESCE(u.is_repair_engineer, FALSE) = FALSE;
DO $rep03_backfill_assigned_user$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'repair_workorder'
          AND column_name = 'assigned_engineer_id'
    ) THEN
        UPDATE repair_workorder w
        SET assigned_user_id = e.user_id
        FROM engineer e
        WHERE w.assigned_engineer_id = e.id
          AND w.assigned_user_id IS NULL;

        UPDATE repair_workorder w
        SET assigned_user_id = w.assigned_engineer_id
        WHERE w.assigned_user_id IS NULL
          AND w.assigned_engineer_id IS NOT NULL
          AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = w.assigned_engineer_id);
    END IF;
END $rep03_backfill_assigned_user$;
UPDATE repair_workorder_process p
SET user_id = e.user_id
FROM engineer e
WHERE p.user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = p.user_id);
UPDATE repair_workorder_process p
SET from_user_id = e.user_id
FROM engineer e
WHERE p.from_user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = p.from_user_id);
UPDATE repair_workorder_process p
SET to_user_id = e.user_id
FROM engineer e
WHERE p.to_user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = p.to_user_id);
UPDATE repair_workorder_event ev
SET user_id = e.user_id
FROM engineer e
WHERE ev.user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = ev.user_id);
UPDATE repair_workorder_event ev
SET from_user_id = e.user_id
FROM engineer e
WHERE ev.from_user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = ev.from_user_id);
UPDATE repair_workorder_event ev
SET to_user_id = e.user_id
FROM engineer e
WHERE ev.to_user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = ev.to_user_id);
INSERT INTO repair_process_type (type_code, type_name, sort_order, can_add_parts, can_engineer_add, engineer_add_rule)
SELECT v.type_code, v.type_name, v.sort_order, v.can_add_parts, v.can_engineer_add, v.engineer_add_rule
FROM (VALUES
    ('internal', '院内维修中', 1, true, true, NULL),
    ('external', '院外维修中', 2, true, true, NULL),
    ('waiting_parts', '等待配件中', 3, true, true, NULL),
    ('verify_rejected', '拒绝验收', 4, false, false, 'system_only'),
    ('pending_verify', '已维修待验收', 5, false, true, 'verify_rejected_only'),
    ('verified', '已验收', 6, false, false, 'system_only')
) AS v(type_code, type_name, sort_order, can_add_parts, can_engineer_add, engineer_add_rule)
WHERE NOT EXISTS (
    SELECT 1 FROM repair_process_type t WHERE t.type_code = v.type_code
);

-- ---------- 附录 W：维修子表 device_* 历史回填（可重复） ----------
UPDATE repair_workorder_event e
SET device_id = w.device_id, device_code = w.device_code, device_name = w.device_name
FROM repair_workorder w
WHERE e.workorder_id = w.id AND e.device_id IS NULL AND w.device_id IS NOT NULL;

UPDATE repair_workorder_process p
SET device_id = w.device_id, device_code = w.device_code, device_name = w.device_name
FROM repair_workorder w
WHERE p.workorder_id = w.id AND p.device_id IS NULL AND w.device_id IS NOT NULL;

UPDATE repair_workorder_segment s
SET device_id = w.device_id, device_code = w.device_code, device_name = w.device_name
FROM repair_workorder w
WHERE s.workorder_id = w.id AND s.device_id IS NULL AND w.device_id IS NOT NULL;

UPDATE repair_workorder_segment_part sp
SET device_id = w.device_id, device_code = w.device_code, device_name = w.device_name
FROM repair_workorder_segment s
JOIN repair_workorder w ON w.id = s.workorder_id
WHERE sp.segment_id = s.id AND sp.device_id IS NULL AND w.device_id IS NOT NULL;

UPDATE spare_part_usage u
SET device_id = w.device_id, device_code = w.device_code, device_name = w.device_name
FROM repair_workorder w
WHERE u.workorder_id = w.id AND u.device_id IS NULL AND w.device_id IS NOT NULL;

UPDATE spare_part_transaction t
SET device_id = w.device_id, device_code = w.device_code, device_name = w.device_name
FROM repair_workorder w
WHERE t.workorder_id = w.id AND t.device_id IS NULL AND w.device_id IS NOT NULL;

-- =============================================================================
-- 附录 W.5：审计 *_by_name 与维修责任人姓名回填
-- =============================================================================
DO $backfill_audit_by_names$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT t.table_name, c.column_name AS by_col, c.column_name || '_name' AS name_col
        FROM information_schema.tables t
        JOIN information_schema.columns c
          ON c.table_schema = t.table_schema AND c.table_name = t.table_name
        JOIN information_schema.columns n
          ON n.table_schema = t.table_schema AND n.table_name = t.table_name
         AND n.column_name = c.column_name || '_name'
        WHERE t.table_schema = current_schema()
          AND t.table_type = 'BASE TABLE'
          AND t.table_name NOT LIKE 'flyway_%'
          AND c.column_name IN ('created_by', 'updated_by', 'deleted_by')
    LOOP
        EXECUTE format(
            'UPDATE %I t SET %I = COALESCE(NULLIF(TRIM(u.real_name), ''''), u.username) '
            || 'FROM sys_user u WHERE t.%I = u.id AND (t.%I IS NULL OR TRIM(t.%I) = '''')',
            r.table_name, r.name_col, r.by_col, r.name_col, r.name_col
        );
    END LOOP;
END $backfill_audit_by_names$;

UPDATE repair_workorder t
SET reporter_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.reporter_id = u.id AND (t.reporter_name IS NULL OR TRIM(t.reporter_name) = '');

UPDATE repair_workorder t
SET assigned_user_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.assigned_user_id = u.id AND (t.assigned_user_name IS NULL OR TRIM(t.assigned_user_name) = '');

UPDATE repair_workorder_event t
SET operator_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.operator_id = u.id AND (t.operator_name IS NULL OR TRIM(t.operator_name) = '');

UPDATE repair_workorder_event t
SET user_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.user_id = u.id AND (t.user_name IS NULL OR TRIM(t.user_name) = '');

UPDATE repair_workorder_event t
SET from_user_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.from_user_id = u.id AND (t.from_user_name IS NULL OR TRIM(t.from_user_name) = '');

UPDATE repair_workorder_event t
SET to_user_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.to_user_id = u.id AND (t.to_user_name IS NULL OR TRIM(t.to_user_name) = '');

UPDATE repair_workorder_process t
SET operator_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.operator_id = u.id AND (t.operator_name IS NULL OR TRIM(t.operator_name) = '');

UPDATE repair_workorder_process t
SET user_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.user_id = u.id AND (t.user_name IS NULL OR TRIM(t.user_name) = '');

UPDATE repair_workorder_process t
SET from_user_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.from_user_id = u.id AND (t.from_user_name IS NULL OR TRIM(t.from_user_name) = '');

UPDATE repair_workorder_process t
SET to_user_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.to_user_id = u.id AND (t.to_user_name IS NULL OR TRIM(t.to_user_name) = '');

UPDATE repair_workorder_segment t
SET user_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.user_id = u.id AND (t.user_name IS NULL OR TRIM(t.user_name) = '');

UPDATE repair_workorder_segment t
SET confirmed_by_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.confirmed_by = u.id AND (t.confirmed_by_name IS NULL OR TRIM(t.confirmed_by_name) = '');

UPDATE repair_workorder_segment_user t
SET user_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.user_id = u.id AND (t.user_name IS NULL OR TRIM(t.user_name) = '');

UPDATE spare_part_usage t
SET operator_name = COALESCE(NULLIF(TRIM(u.real_name), ''), u.username)
FROM sys_user u
WHERE t.operator_id = u.id AND (t.operator_name IS NULL OR TRIM(t.operator_name) = '');

-- AST-UI-02：device_unit 文本按名称/编码匹配 unit_dict → unit_id；匹配不上留空
UPDATE medical_device d
SET unit_id = u.id,
    updated_at = NOW()
FROM unit_dict u
WHERE d.unit_id IS NULL
  AND NULLIF(TRIM(d.device_unit), '') IS NOT NULL
  AND COALESCE(u.is_deleted, 0) = 0
  AND (
    LOWER(TRIM(u.unit_name)) = LOWER(TRIM(d.device_unit))
    OR LOWER(TRIM(u.unit_code)) = LOWER(TRIM(d.device_unit))
  );
