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
--   6. 【标准七列】created_at/updated_at/created_by/updated_by/is_deleted/deleted_at/deleted_by
--      由 R__audit_columns.sql（含 is_deleted）与 R__is_deleted_columns.sql 幂等保证；
--      业务补列写本文件，勿把标准七列散落在本文件各处。
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

-- 历史库 finance_category 误用 asset 列名 category_code/category_name，纠正为 finance_code/finance_name
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

-- 计量检定类型：纠正错误绑定到 public.metrology_type 的自引用外键（历史 search_path 串写）
ALTER TABLE "${flyway:defaultSchema}".metrology_type DROP CONSTRAINT IF EXISTS metrology_type_parent_id_fkey;
ALTER TABLE "${flyway:defaultSchema}".metrology_type
  ADD CONSTRAINT metrology_type_parent_id_fkey
  FOREIGN KEY (parent_id) REFERENCES "${flyway:defaultSchema}".metrology_type(id);

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

-- ---------- 公用设备借调模块（附录 N） ----------
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

-- ---------- 附录 P：资产标签打印记录 ----------
CREATE TABLE IF NOT EXISTS device_label_print_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID NOT NULL REFERENCES medical_device(id),
    device_code VARCHAR(20) NOT NULL,
    device_name VARCHAR(200),
    printed_by UUID,
    printed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    template_code VARCHAR(50) DEFAULT 'default',
    remark TEXT
);
