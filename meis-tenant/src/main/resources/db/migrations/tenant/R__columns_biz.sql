-- =============================================================================
-- 租户 schema 业务补列（可重复迁移 R__）—— 老租户缺列兜底
-- =============================================================================
-- 槽位：R__columns_biz.sql（按字母序：columns_audit → columns_biz → data_fix）
-- 约定：
--   1. 新建表 / 完整字段 → 只改 V1__tables.sql
--   2. 本文件只做业务结构性变更：ALTER TABLE ... ADD COLUMN / FK / RENAME / DROP COLUMN
--   3. 禁止 CREATE TABLE / CREATE INDEX（归 V1 / V2__indexes）
--   4. 标准七列归 R__columns_audit.sql；数据更正归 R__data_fix.sql
--   5. 不要在本文件 COMMENT ON
-- =============================================================================

ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS audit_status VARCHAR(20) DEFAULT 'pending';
-- ---------- repair_workorder（每列一条） ----------
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS repair_sub_status VARCHAR(30);
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS dispatch_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP WITH TIME ZONE;
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
-- 设备 68 码三级编码为 8 位（如 68010101），历史 VARCHAR(6) 不够
ALTER TABLE medical_device_category ALTER COLUMN category_code TYPE VARCHAR(16);
ALTER TABLE medical_device_category ALTER COLUMN parent_code TYPE VARCHAR(16);
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
-- AST-UI-02：台账单位改 FK → unit_dict（对齐 spare_part）；旧 device_unit 文本由 R__data_fix 迁移
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS unit_id UUID;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS manage_dept_id UUID;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS location_floor VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS room_number VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS card_code VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS use_dept_head VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS manage_dept_head VARCHAR(100);
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
-- 计量检定类型：纠正错误绑定到 public.metrology_type 的自引用外键（历史 search_path 串写）
ALTER TABLE "${flyway:defaultSchema}".metrology_type DROP CONSTRAINT IF EXISTS metrology_type_parent_id_fkey;
ALTER TABLE "${flyway:defaultSchema}".metrology_type
  ADD CONSTRAINT metrology_type_parent_id_fkey
  FOREIGN KEY (parent_id) REFERENCES "${flyway:defaultSchema}".metrology_type(id);
-- ---------- 库房管理模块：补列 ----------
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS from_warehouse_id UUID;
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS to_warehouse_id UUID;
ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS warehouse_id UUID;
-- ---------- 盘点补打标签 / 统一打印流水（附录 AST.INV） ----------
ALTER TABLE inventory_check_item ADD COLUMN IF NOT EXISTS need_reprint_label BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE inventory_check_item ADD COLUMN IF NOT EXISTS label_printed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE inventory_check_item ADD COLUMN IF NOT EXISTS label_print_count INT NOT NULL DEFAULT 0;
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS printed_by_name VARCHAR(100);
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS biz_type VARCHAR(50);
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS biz_id UUID;
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS biz_no VARCHAR(50);
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS biz_item_id UUID;
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
-- ---------- 预防性维护模块 ----------
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_pm_device BOOLEAN DEFAULT FALSE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standby_current_max_ma DECIMAL(10,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standby_current_min_ma DECIMAL(10,2);
ALTER TABLE power_tag ADD COLUMN IF NOT EXISTS device_code VARCHAR(20);
ALTER TABLE power_tag ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
-- ---------- REP-03：维修工程师 sys_user + assigned_user_id ----------
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS is_repair_engineer BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS assigned_user_id UUID;
-- ---------- U.14：进程段确认固化 + 工程师工作内容 ----------
ALTER TABLE repair_workorder_segment ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repair_workorder_segment ADD COLUMN IF NOT EXISTS confirmed_by UUID;
ALTER TABLE repair_workorder_segment_user ADD COLUMN IF NOT EXISTS work_content TEXT;
COMMENT ON COLUMN repair_workorder_segment.confirmed_at IS '段确认固化时间';
COMMENT ON COLUMN repair_workorder_segment.confirmed_by IS '段确认人';
COMMENT ON COLUMN repair_workorder_segment_user.work_content IS '工程师工作内容（选填）';
-- ---------- U.15 / 附录 W：配件供应商、拼音简码、业务冗余 device_* ----------
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE repair_workorder_segment_part ADD COLUMN IF NOT EXISTS supplier_id UUID;
ALTER TABLE repair_workorder_segment_part ADD COLUMN IF NOT EXISTS device_id UUID;
ALTER TABLE repair_workorder_segment_part ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
ALTER TABLE repair_workorder_segment_part ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
ALTER TABLE repair_workorder_segment ADD COLUMN IF NOT EXISTS device_id UUID;
ALTER TABLE repair_workorder_segment ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
ALTER TABLE repair_workorder_segment ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
ALTER TABLE repair_workorder_event ADD COLUMN IF NOT EXISTS device_id UUID;
ALTER TABLE repair_workorder_event ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
ALTER TABLE repair_workorder_event ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
ALTER TABLE repair_workorder_process ADD COLUMN IF NOT EXISTS device_id UUID;
ALTER TABLE repair_workorder_process ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
ALTER TABLE repair_workorder_process ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
ALTER TABLE spare_part_usage ADD COLUMN IF NOT EXISTS device_id UUID;
ALTER TABLE spare_part_usage ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
ALTER TABLE spare_part_usage ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS device_id UUID;
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
-- ---------- 附录 W.5：维修业务责任人姓名快照 ----------
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS reporter_name VARCHAR(100);
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS assigned_user_name VARCHAR(100);
ALTER TABLE repair_workorder_event ADD COLUMN IF NOT EXISTS operator_name VARCHAR(100);
ALTER TABLE repair_workorder_event ADD COLUMN IF NOT EXISTS user_name VARCHAR(100);
ALTER TABLE repair_workorder_event ADD COLUMN IF NOT EXISTS from_user_name VARCHAR(100);
ALTER TABLE repair_workorder_event ADD COLUMN IF NOT EXISTS to_user_name VARCHAR(100);
ALTER TABLE repair_workorder_process ADD COLUMN IF NOT EXISTS operator_name VARCHAR(100);
ALTER TABLE repair_workorder_process ADD COLUMN IF NOT EXISTS user_name VARCHAR(100);
ALTER TABLE repair_workorder_process ADD COLUMN IF NOT EXISTS from_user_name VARCHAR(100);
ALTER TABLE repair_workorder_process ADD COLUMN IF NOT EXISTS to_user_name VARCHAR(100);
ALTER TABLE repair_workorder_segment ADD COLUMN IF NOT EXISTS user_name VARCHAR(100);
ALTER TABLE repair_workorder_segment ADD COLUMN IF NOT EXISTS confirmed_by_name VARCHAR(100);
ALTER TABLE repair_workorder_segment_user ADD COLUMN IF NOT EXISTS user_name VARCHAR(100);
ALTER TABLE repair_workorder_segment_user ADD COLUMN IF NOT EXISTS labor_cost DECIMAL(10,2);
ALTER TABLE spare_part_usage ADD COLUMN IF NOT EXISTS operator_name VARCHAR(100);
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
DO $rep03_drop_wo_eng$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'repair_workorder' AND column_name = 'assigned_engineer_id'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'repair_workorder' AND column_name = 'assigned_user_id'
    ) THEN
        ALTER TABLE repair_workorder DROP CONSTRAINT IF EXISTS repair_workorder_assigned_engineer_id_fkey;
        ALTER TABLE repair_workorder DROP COLUMN assigned_engineer_id;
    END IF;
END $rep03_drop_wo_eng$;
DO $rep03_rename_process$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'user_id')
    THEN
        ALTER TABLE repair_workorder_process RENAME COLUMN engineer_id TO user_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'from_engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'from_user_id')
    THEN
        ALTER TABLE repair_workorder_process RENAME COLUMN from_engineer_id TO from_user_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'to_engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'to_user_id')
    THEN
        ALTER TABLE repair_workorder_process RENAME COLUMN to_engineer_id TO to_user_id;
    END IF;
END $rep03_rename_process$;
DO $rep03_rename_event$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'user_id')
    THEN
        ALTER TABLE repair_workorder_event RENAME COLUMN engineer_id TO user_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'from_engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'from_user_id')
    THEN
        ALTER TABLE repair_workorder_event RENAME COLUMN from_engineer_id TO from_user_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'to_engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'to_user_id')
    THEN
        ALTER TABLE repair_workorder_event RENAME COLUMN to_engineer_id TO to_user_id;
    END IF;
END $rep03_rename_event$;
-- ---------- purchase_plan（采购申请基本信息扩展 PUR-UI-01） ----------
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS campus_id UUID;
DO $purchase_plan_campus_fk$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'purchase_plan_campus_id_fkey'
    ) THEN
        ALTER TABLE purchase_plan
            ADD CONSTRAINT purchase_plan_campus_id_fkey
            FOREIGN KEY (campus_id) REFERENCES campus(id);
    END IF;
END $purchase_plan_campus_fk$;
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS unit VARCHAR(20);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS model VARCHAR(100);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS fill_date DATE;
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS existing_device_status VARCHAR(50);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS existing_device_usage_freq VARCHAR(50);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS reference_manufacturer VARCHAR(200);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS specification TEXT;
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS quantity INTEGER;
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS similar_device_count INTEGER;
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS demand_level VARCHAR(30);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS product_attribute_req TEXT;
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS other_condition_confirm TEXT;
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS unit_budget_price DECIMAL(15,2);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS category_id UUID;
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS demand_nature VARCHAR(30);
ALTER TABLE purchase_plan ADD COLUMN IF NOT EXISTS prefer_import BOOLEAN DEFAULT false;
DO $purchase_plan_category_fk$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'purchase_plan'
          AND constraint_name = 'purchase_plan_category_id_fkey'
    ) THEN
        ALTER TABLE purchase_plan
            ADD CONSTRAINT purchase_plan_category_id_fkey
            FOREIGN KEY (category_id) REFERENCES medical_device_category(id);
    END IF;
END $purchase_plan_category_fk$;
-- ---------- purchase_plan_item（明细扩展字段 PUR-UI-01） ----------
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS similar_device_count INTEGER;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS demand_level VARCHAR(30);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS product_attribute_req TEXT;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS fund_source VARCHAR(30);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS demand_nature VARCHAR(30);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS existing_device_status VARCHAR(50);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS existing_device_usage_freq VARCHAR(50);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS other_condition_confirm TEXT;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS is_large_equipment BOOLEAN DEFAULT false;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS large_equipment_class VARCHAR(20);
-- ---------- purchase_plan_item（订单号 / 订单审核 PUR-UI-09） ----------
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS order_no VARCHAR(20);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS order_review_comment TEXT;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS order_reviewed_at TIMESTAMPTZ;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS order_reviewed_by UUID;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS order_reviewed_by_name VARCHAR(100);
-- ---------- purchase_plan_item（询价议价会议记录 PUR-UI-10） ----------
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_meeting_location VARCHAR(100);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_meeting_time DATE;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_participant_depts VARCHAR(200);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_dept_opinion TEXT;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_meeting_content TEXT;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_meeting_conclusion TEXT;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_record_url VARCHAR(500);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_review_result VARCHAR(20);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_review_comment VARCHAR(500);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_reviewed_at TIMESTAMPTZ;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_reviewed_by UUID;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_reviewed_by_name VARCHAR(100);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_at TIMESTAMPTZ;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_by UUID;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bargain_by_name VARCHAR(100);

-- ---------- purchase_plan_item_bid_supplier（招标供应商 PUR-UI-15） ----------
CREATE TABLE IF NOT EXISTS purchase_plan_item_bid_supplier (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_item_id UUID NOT NULL REFERENCES purchase_plan_item(id),
    supplier_id UUID REFERENCES supplier(id),
    supplier_name VARCHAR(200) NOT NULL,
    contact_person VARCHAR(100),
    contact_phone VARCHAR(50),
    brand VARCHAR(100),
    specification VARCHAR(200),
    final_amount DECIMAL(15,2),
    warranty_period VARCHAR(100),
    preferential_terms TEXT,
    bid_doc_url VARCHAR(500),
    is_winner BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    created_by_name VARCHAR(100),
    updated_by UUID,
    updated_by_name VARCHAR(100),
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    deleted_by_name VARCHAR(100)
);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS plan_item_id UUID;
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(200);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS contact_person VARCHAR(100);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(50);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS final_amount DECIMAL(15,2);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS warranty_period VARCHAR(100);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS preferential_terms TEXT;
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(100);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS updated_by_name VARCHAR(100);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS deleted_by_name VARCHAR(100);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS supplier_id UUID;
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS bid_doc_url VARCHAR(500);
ALTER TABLE purchase_plan_item_bid_supplier ADD COLUMN IF NOT EXISTS is_winner BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------- purchase_contract_item（合同设备明细 PUR-UI-17） ----------
CREATE TABLE IF NOT EXISTS purchase_contract_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID NOT NULL REFERENCES purchase_contract(id),
    device_name VARCHAR(200) NOT NULL,
    specification VARCHAR(200),
    brand VARCHAR(100),
    quantity DECIMAL(15,2),
    unit_price DECIMAL(15,2),
    amount DECIMAL(15,2),
    manufacturer_id UUID REFERENCES manufacturer(id),
    manufacturer_name VARCHAR(200),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    created_by_name VARCHAR(100),
    updated_by UUID,
    updated_by_name VARCHAR(100),
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    deleted_by_name VARCHAR(100)
);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS contract_id UUID;
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS quantity DECIMAL(15,2);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS unit_price DECIMAL(15,2);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS amount DECIMAL(15,2);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS manufacturer_id UUID;
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS manufacturer_name VARCHAR(200);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(100);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS updated_by_name VARCHAR(100);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS deleted_by_name VARCHAR(100);

-- ---------- purchase_contract 资金来源（PUR-UI-19） ----------
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS fund_source VARCHAR(30);
