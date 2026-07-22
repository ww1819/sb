-- =============================================================================
-- ?? schema ?????????? R__??? ???????
-- =============================================================================
-- ???R__columns_biz.sql??????columns_audit ? columns_biz ? data_fix?
-- ???
--   1. ??? / ???? ? ?? V1__tables.sql
--   2. ?????????????ALTER TABLE ... ADD COLUMN / FK / RENAME / DROP COLUMN
--   3. ?? CREATE TABLE / CREATE INDEX?? V1 / V2__indexes?
--   4. ????? R__columns_audit.sql?????? R__data_fix.sql
--   5. ?????? COMMENT ON
-- =============================================================================

ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS audit_status VARCHAR(20) DEFAULT 'pending';
-- ---------- repair_workorder?????? ----------
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS repair_sub_status VARCHAR(30);
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS dispatch_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP WITH TIME ZONE;
-- ---------- ????????? ----------
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
-- ?? 68 ?????? 8 ??? 68010101???? VARCHAR(6) ??
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
-- AST-UI-02?????? FK ? unit_dict??? spare_part??? device_unit ??? R__data_fix ??
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS unit_id UUID;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS manage_dept_id UUID;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS location_floor VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS room_number VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS card_code VARCHAR(50);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS use_dept_head VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS manage_dept_head VARCHAR(100);
-- ??? finance_category ?? asset ?? category_code/category_name???? finance_code/finance_name
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
-- ---------- ????????????? ----------
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
-- ---------- ????????????? ----------
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS model VARCHAR(100);
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS unit_id UUID;
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS manufacturer_id UUID;
ALTER TABLE spare_part ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS ref_no VARCHAR(50);
ALTER TABLE spare_part_transaction ADD COLUMN IF NOT EXISTS remark TEXT;
-- ---------- ?????????/??/???? ----------
ALTER TABLE maintenance_template ADD COLUMN IF NOT EXISTS template_code VARCHAR(30);
ALTER TABLE maintenance_template ADD COLUMN IF NOT EXISTS maintenance_level_id UUID;
ALTER TABLE maintenance_template ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS cycle_days INTEGER;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE;
-- ---------- ?????????/??/???? ----------
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
-- ?????????????? public.metrology_type ????????? search_path ???
ALTER TABLE "${flyway:defaultSchema}".metrology_type DROP CONSTRAINT IF EXISTS metrology_type_parent_id_fkey;
ALTER TABLE "${flyway:defaultSchema}".metrology_type
  ADD CONSTRAINT metrology_type_parent_id_fkey
  FOREIGN KEY (parent_id) REFERENCES "${flyway:defaultSchema}".metrology_type(id);
-- ---------- ????????? ----------
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS remark TEXT;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(100);
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS updated_by_name VARCHAR(100);
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS deleted_by_name VARCHAR(100);
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS approved_by_name VARCHAR(100);
ALTER TABLE device_outbound ADD COLUMN IF NOT EXISTS approved_at DATE;

ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS unit VARCHAR(50);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS unit_price DECIMAL(15,2);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS total_price DECIMAL(15,2);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS manufacturer_id UUID;
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS supplier_id UUID;
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS serial_number VARCHAR(100);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS category_id UUID;
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS category_name VARCHAR(200);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS asset_category_id UUID;
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS asset_category_name VARCHAR(200);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS finance_category_id UUID;
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS finance_category_name VARCHAR(200);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(100);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS updated_by_name VARCHAR(100);
ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS deleted_by_name VARCHAR(100);

-- ---------- ???????/?????WH-UI-22? ----------
ALTER TABLE device_return ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(100);
ALTER TABLE device_return ADD COLUMN IF NOT EXISTS updated_by_name VARCHAR(100);
ALTER TABLE device_return ADD COLUMN IF NOT EXISTS deleted_by_name VARCHAR(100);
ALTER TABLE device_return ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE device_return ADD COLUMN IF NOT EXISTS approved_by_name VARCHAR(100);
ALTER TABLE device_return ADD COLUMN IF NOT EXISTS approved_at DATE;
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS unit VARCHAR(50);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS unit_price DECIMAL(15,2);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS total_price DECIMAL(15,2);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS manufacturer_id UUID;
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS supplier_id UUID;
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS serial_number VARCHAR(100);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS category_id UUID;
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS category_name VARCHAR(200);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS asset_category_id UUID;
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS asset_category_name VARCHAR(200);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS finance_category_id UUID;
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS finance_category_name VARCHAR(200);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(100);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS updated_by_name VARCHAR(100);
ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS deleted_by_name VARCHAR(100);

ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS from_warehouse_id UUID;
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS to_warehouse_id UUID;
ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS warehouse_id UUID;
-- ---------- ?????? / ????????? AST.INV? ----------
ALTER TABLE inventory_check_item ADD COLUMN IF NOT EXISTS need_reprint_label BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE inventory_check_item ADD COLUMN IF NOT EXISTS label_printed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE inventory_check_item ADD COLUMN IF NOT EXISTS label_print_count INT NOT NULL DEFAULT 0;
ALTER TABLE inventory_check_item ADD COLUMN IF NOT EXISTS row_version INTEGER DEFAULT 1;
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS printed_by_name VARCHAR(100);
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS biz_type VARCHAR(50);
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS biz_id UUID;
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS biz_no VARCHAR(50);
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS biz_item_id UUID;
-- ---------- ??????????? N? ----------
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
-- ---------- ??????? ----------
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS is_pm_device BOOLEAN DEFAULT FALSE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standby_current_max_ma DECIMAL(10,2);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS standby_current_min_ma DECIMAL(10,2);
ALTER TABLE power_tag ADD COLUMN IF NOT EXISTS device_code VARCHAR(20);
ALTER TABLE power_tag ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
-- ---------- REP-03?????? sys_user + assigned_user_id ----------
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS is_repair_engineer BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS assigned_user_id UUID;
-- ---------- U.14???????? + ??????? ----------
ALTER TABLE repair_workorder_segment ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repair_workorder_segment ADD COLUMN IF NOT EXISTS confirmed_by UUID;
ALTER TABLE repair_workorder_segment_user ADD COLUMN IF NOT EXISTS work_content TEXT;
COMMENT ON COLUMN repair_workorder_segment.confirmed_at IS '???????';
COMMENT ON COLUMN repair_workorder_segment.confirmed_by IS '????';
COMMENT ON COLUMN repair_workorder_segment_user.work_content IS '???????????';
-- ---------- U.15 / ?? W???????????????? device_* ----------
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
-- ---------- ?? W.5???????????? ----------
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
-- ---------- ??????? + ??/?? + ?1~?6 ----------
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
-- ---------- purchase_plan??????????? PUR-UI-01? ----------
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
-- ---------- purchase_plan_item??????? PUR-UI-01? ----------
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
-- ---------- purchase_plan_item???? / ???? PUR-UI-09? ----------
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS order_no VARCHAR(20);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bidding_no VARCHAR(20);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS order_review_comment TEXT;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS order_reviewed_at TIMESTAMPTZ;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS order_reviewed_by UUID;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS order_reviewed_by_name VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uk_purchase_plan_item_bidding_no
    ON purchase_plan_item(bidding_no) WHERE bidding_no IS NOT NULL;
-- ---------- purchase_plan_item????????? PUR-UI-10? ----------
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
-- ---------- purchase_plan_item????? PUR-UI-21? ----------
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bidding_review_result VARCHAR(20);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bidding_review_comment VARCHAR(500);
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bidding_reviewed_at TIMESTAMPTZ;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bidding_reviewed_by UUID;
ALTER TABLE purchase_plan_item ADD COLUMN IF NOT EXISTS bidding_reviewed_by_name VARCHAR(100);

-- ---------- purchase_plan_item_bid_supplier?????? PUR-UI-15? ----------
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

-- ---------- purchase_contract_item??????? PUR-UI-17? ----------
CREATE TABLE IF NOT EXISTS purchase_contract_item (
    id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
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

-- ---------- purchase_contract ?????PUR-UI-19? ----------
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS fund_source VARCHAR(30);
-- ---------- contract_payment????? PUR-UI-23? ----------
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS payment_ratio DECIMAL(8,2);
ALTER TABLE contract_payment ADD COLUMN IF NOT EXISTS payment_condition VARCHAR(500);

-- ---------- purchase_acceptance_device??????? PUR-UI-24? ----------
CREATE TABLE IF NOT EXISTS purchase_acceptance_device (
    id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    acceptance_id UUID NOT NULL REFERENCES purchase_acceptance(id) ON DELETE CASCADE,
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
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS acceptance_id UUID;
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS quantity DECIMAL(15,2);
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS unit_price DECIMAL(15,2);
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS amount DECIMAL(15,2);
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS manufacturer_id UUID;
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS manufacturer_name VARCHAR(200);
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(100);
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS updated_by_name VARCHAR(100);
ALTER TABLE purchase_acceptance_device ADD COLUMN IF NOT EXISTS deleted_by_name VARCHAR(100);

-- ---------- purchase_acceptance_member????? PUR-UI-25? ----------
ALTER TABLE purchase_acceptance_member ADD COLUMN IF NOT EXISTS acceptance_content VARCHAR(500);
ALTER TABLE purchase_acceptance_member ADD COLUMN IF NOT EXISTS acceptance_result VARCHAR(100);

-- ---------- purchase_acceptance ???/?????PUR-UI-30? ----------
ALTER TABLE purchase_acceptance ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE purchase_acceptance ADD COLUMN IF NOT EXISTS approved_by_name VARCHAR(100);
ALTER TABLE purchase_acceptance ADD COLUMN IF NOT EXISTS approved_at DATE;
-- ???? timestamptz ????? DATE?????
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'purchase_acceptance' AND column_name = 'approved_at'
      AND data_type = 'timestamp with time zone'
  ) THEN
    ALTER TABLE purchase_acceptance
      ALTER COLUMN approved_at TYPE DATE USING (approved_at AT TIME ZONE 'Asia/Shanghai')::date;
  END IF;
END $$;

-- ---------- device_goods_return ??????WH-UI-01? ----------
CREATE TABLE IF NOT EXISTS device_goods_return (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_no VARCHAR(30) UNIQUE NOT NULL,
    warehouse_id UUID,
    supplier_id UUID,
    entry_id UUID,
    return_date DATE,
    reason TEXT,
    doc_status VARCHAR(20) DEFAULT 'draft',
    status VARCHAR(20) DEFAULT 'draft',
    approval_status VARCHAR(20) DEFAULT 'draft',
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    created_by_name VARCHAR(100),
    updated_by_name VARCHAR(100),
    deleted_by_name VARCHAR(100)
);
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS return_no VARCHAR(30);
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS supplier_id UUID;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS entry_id UUID;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS return_date DATE;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS doc_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'draft';
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS remark TEXT;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS deleted_by UUID;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(100);
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS updated_by_name VARCHAR(100);
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS deleted_by_name VARCHAR(100);
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS approved_by_name VARCHAR(100);
ALTER TABLE device_goods_return ADD COLUMN IF NOT EXISTS approved_at DATE;

CREATE TABLE IF NOT EXISTS device_goods_return_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id UUID NOT NULL,
    device_id UUID,
    device_code VARCHAR(50),
    device_name VARCHAR(200),
    specification VARCHAR(200),
    unit VARCHAR(50),
    quantity INTEGER DEFAULT 1,
    unit_price DECIMAL(15,2),
    total_price DECIMAL(15,2),
    manufacturer_id UUID,
    serial_number VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    created_by_name VARCHAR(100),
    updated_by_name VARCHAR(100),
    deleted_by_name VARCHAR(100)
);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS return_id UUID;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS device_id UUID;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS unit VARCHAR(50);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS quantity INTEGER DEFAULT 1;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS unit_price DECIMAL(15,2);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS total_price DECIMAL(15,2);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS manufacturer_id UUID;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS serial_number VARCHAR(100);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS category_id UUID;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS category_name VARCHAR(200);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS asset_category_id UUID;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS asset_category_name VARCHAR(200);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS finance_category_id UUID;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS finance_category_name VARCHAR(200);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS deleted_by UUID;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(100);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS updated_by_name VARCHAR(100);
ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS deleted_by_name VARCHAR(100);

-- ---------- device_entry ?????WH-UI-02? ----------
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS invoice_amount DECIMAL(15,2);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS invoice_no VARCHAR(50);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS approved_by_name VARCHAR(100);
ALTER TABLE device_entry ADD COLUMN IF NOT EXISTS approved_at DATE;
UPDATE device_entry SET approval_status = 'approved'
WHERE status = 'completed' AND COALESCE(approval_status, '') IS DISTINCT FROM 'approved';
UPDATE device_entry SET approval_status = 'draft'
WHERE approval_status IS NULL;

-- ---------- device_entry_item ???????WH-UI-02? ----------
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS unit VARCHAR(50);
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS dept_id UUID;
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS manufacturer_id UUID;
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS manufacturer_name VARCHAR(200);
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS factory_code VARCHAR(100);
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS financial_code VARCHAR(50);
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS depreciation_years INTEGER;
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS production_date DATE;
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS warranty_period VARCHAR(100);
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS purchase_method VARCHAR(50);
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS storage_location VARCHAR(200);
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS category_id UUID;
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS finance_category_id UUID;
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS asset_category_id UUID;
-- ??????????????
UPDATE device_entry_item SET specification = model
WHERE (specification IS NULL OR specification = '') AND model IS NOT NULL AND model <> '';

-- ---------- ?? OPS????? / ???? / ?????2026-07-21? ----------
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS plan_no VARCHAR(30);
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS template_name VARCHAR(200);
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS maintenance_level_id UUID;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS assigned_user_id UUID;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS assigned_user_name VARCHAR(100);
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS approved_by_name VARCHAR(100);
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS campus_id UUID;

ALTER TABLE pm_plan ADD COLUMN IF NOT EXISTS plan_no VARCHAR(30);
ALTER TABLE pm_plan ADD COLUMN IF NOT EXISTS template_name VARCHAR(200);
ALTER TABLE pm_plan ADD COLUMN IF NOT EXISTS assigned_user_id UUID;
ALTER TABLE pm_plan ADD COLUMN IF NOT EXISTS assigned_user_name VARCHAR(100);
ALTER TABLE pm_plan ADD COLUMN IF NOT EXISTS approved_by_name VARCHAR(100);
ALTER TABLE pm_plan ADD COLUMN IF NOT EXISTS campus_id UUID;

ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS plan_no VARCHAR(30);
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS template_name VARCHAR(200);

ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS plan_no VARCHAR(30);
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) DEFAULT 'from_plan';
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS template_name VARCHAR(200);
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS maintenance_level VARCHAR(20);
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS assigned_user_id UUID;
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS assigned_user_name VARCHAR(100);
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS executor_name VARCHAR(100);
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS submitter_id UUID;
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS submitter_name VARCHAR(100);
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS auditor_id UUID;
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS auditor_name VARCHAR(100);
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS audited_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE maintenance_execution ADD COLUMN IF NOT EXISTS audit_comment TEXT;

ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS plan_no VARCHAR(30);
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) DEFAULT 'from_plan';
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS template_name VARCHAR(200);
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS assigned_user_id UUID;
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS assigned_user_name VARCHAR(100);
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS executor_name VARCHAR(100);
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS submitter_id UUID;
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS submitter_name VARCHAR(100);
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS auditor_id UUID;
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS auditor_name VARCHAR(100);
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS audited_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pm_execution ADD COLUMN IF NOT EXISTS audit_comment TEXT;

ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS plan_no VARCHAR(30);
ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) DEFAULT 'from_plan';
ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS template_name VARCHAR(200);
ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS executor_name VARCHAR(100);
ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS submitter_id UUID;
ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS submitter_name VARCHAR(100);
ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS auditor_id UUID;
ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS auditor_name VARCHAR(100);
ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS audited_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE inspection_execution ADD COLUMN IF NOT EXISTS audit_comment TEXT;

ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS execution_no VARCHAR(30);
ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS plan_item_id UUID;
ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS executor_id UUID;
ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS executor_name VARCHAR(100);
ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS start_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS end_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS issues_found TEXT;
ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS photos JSONB;
ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS signature_url VARCHAR(500);
ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS row_version INTEGER DEFAULT 1;

ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS execution_no VARCHAR(30);
ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS plan_item_id UUID;
ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS executor_id UUID;
ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS executor_name VARCHAR(100);
ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS start_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS end_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS issues_found TEXT;
ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS photos JSONB;
ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS signature_url VARCHAR(500);
ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS row_version INTEGER DEFAULT 1;

ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS execution_no VARCHAR(30);
ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS plan_item_id UUID;
ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS executor_id UUID;
ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS executor_name VARCHAR(100);
ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS start_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS end_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS issues_found TEXT;
ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS photos JSONB;
ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS signature_url VARCHAR(500);
ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS row_version INTEGER DEFAULT 1;

ALTER TABLE maintenance_execution_result ADD COLUMN IF NOT EXISTS standard_value VARCHAR(200);
ALTER TABLE maintenance_execution_result ADD COLUMN IF NOT EXISTS check_method VARCHAR(200);
ALTER TABLE maintenance_execution_result ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;
ALTER TABLE maintenance_execution_result ADD COLUMN IF NOT EXISTS is_required BOOLEAN DEFAULT TRUE;
ALTER TABLE maintenance_execution_result ADD COLUMN IF NOT EXISTS photos JSONB;
ALTER TABLE maintenance_execution_result ADD COLUMN IF NOT EXISTS row_version INTEGER DEFAULT 1;

ALTER TABLE pm_execution_result ADD COLUMN IF NOT EXISTS standard_value VARCHAR(200);
ALTER TABLE pm_execution_result ADD COLUMN IF NOT EXISTS check_method VARCHAR(200);
ALTER TABLE pm_execution_result ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;
ALTER TABLE pm_execution_result ADD COLUMN IF NOT EXISTS is_required BOOLEAN DEFAULT TRUE;
ALTER TABLE pm_execution_result ADD COLUMN IF NOT EXISTS photos JSONB;
ALTER TABLE pm_execution_result ADD COLUMN IF NOT EXISTS row_version INTEGER DEFAULT 1;

ALTER TABLE inspection_execution_result ADD COLUMN IF NOT EXISTS standard_value VARCHAR(200);
ALTER TABLE inspection_execution_result ADD COLUMN IF NOT EXISTS check_method VARCHAR(200);
ALTER TABLE inspection_execution_result ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;
ALTER TABLE inspection_execution_result ADD COLUMN IF NOT EXISTS is_required BOOLEAN DEFAULT TRUE;
ALTER TABLE inspection_execution_result ADD COLUMN IF NOT EXISTS photos JSONB;
ALTER TABLE inspection_execution_result ADD COLUMN IF NOT EXISTS row_version INTEGER DEFAULT 1;

-- ---------- MP.3: WeChat mini-program openid (subscribe message) ----------
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS wx_openid VARCHAR(64);
COMMENT ON COLUMN sys_user.wx_openid IS 'WeChat mini-program openid for subscribe message';
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_wx_openid ON sys_user (wx_openid) WHERE wx_openid IS NOT NULL AND wx_openid <> '';

-- ---------- 附录 W.6 / BACKLOG-PLT-W03：明细业务单号与主数据冗余（2026-07-22） ----------
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS entry_no VARCHAR(30);
ALTER TABLE device_entry_item ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
COMMENT ON COLUMN device_entry_item.entry_no IS '入库单号快照（W.6）';
COMMENT ON COLUMN device_entry_item.device_code IS '设备编码快照（W.6；审核生成台账后回写）';

ALTER TABLE device_outbound_item ADD COLUMN IF NOT EXISTS outbound_no VARCHAR(30);
COMMENT ON COLUMN device_outbound_item.outbound_no IS '出库单号快照（W.6）';

ALTER TABLE device_return_item ADD COLUMN IF NOT EXISTS return_no VARCHAR(30);
COMMENT ON COLUMN device_return_item.return_no IS '退库单号快照（W.6）';

ALTER TABLE device_goods_return_item ADD COLUMN IF NOT EXISTS return_no VARCHAR(30);
COMMENT ON COLUMN device_goods_return_item.return_no IS '退货单号快照（W.6）';

ALTER TABLE inventory_check_item ADD COLUMN IF NOT EXISTS check_no VARCHAR(30);
COMMENT ON COLUMN inventory_check_item.check_no IS '盘点单号快照（W.6）';

ALTER TABLE repair_workorder_event ADD COLUMN IF NOT EXISTS wo_no VARCHAR(30);
ALTER TABLE repair_workorder_process ADD COLUMN IF NOT EXISTS wo_no VARCHAR(30);
ALTER TABLE repair_workorder_segment ADD COLUMN IF NOT EXISTS wo_no VARCHAR(30);
ALTER TABLE repair_workorder_segment_part ADD COLUMN IF NOT EXISTS wo_no VARCHAR(30);
ALTER TABLE spare_part_usage ADD COLUMN IF NOT EXISTS wo_no VARCHAR(30);
COMMENT ON COLUMN repair_workorder_event.wo_no IS '工单号快照（W.6）';
COMMENT ON COLUMN repair_workorder_process.wo_no IS '工单号快照（W.6）';
COMMENT ON COLUMN repair_workorder_segment.wo_no IS '工单号快照（W.6）';
COMMENT ON COLUMN repair_workorder_segment_part.wo_no IS '工单号快照（W.6）';
COMMENT ON COLUMN spare_part_usage.wo_no IS '工单号快照（W.6）';

ALTER TABLE metrology_execution_item ADD COLUMN IF NOT EXISTS execution_no VARCHAR(30);
COMMENT ON COLUMN metrology_execution_item.execution_no IS '计量执行单号快照（W.6）';

ALTER TABLE shared_device_fee ADD COLUMN IF NOT EXISTS loan_no VARCHAR(30);
ALTER TABLE shared_device_fee ADD COLUMN IF NOT EXISTS device_id UUID;
ALTER TABLE shared_device_fee ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
ALTER TABLE shared_device_fee ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
COMMENT ON COLUMN shared_device_fee.loan_no IS '借调单号快照（W.6）';
COMMENT ON COLUMN shared_device_fee.device_id IS '设备ID冗余（W.6 / AST-W01）';
COMMENT ON COLUMN shared_device_fee.device_code IS '设备编码快照';
COMMENT ON COLUMN shared_device_fee.device_name IS '设备名称快照';

ALTER TABLE maintenance_plan_item ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
ALTER TABLE maintenance_execution_item ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
ALTER TABLE inspection_plan_item ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
ALTER TABLE inspection_execution_item ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
ALTER TABLE pm_plan_item ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
ALTER TABLE pm_execution_item ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
ALTER TABLE metrology_execution_item ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);

-- ---------- 附录 W.6 / BACKLOG-PLT-W03 P2（2026-07-22） ----------
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS contract_code VARCHAR(50);
COMMENT ON COLUMN purchase_contract_item.contract_code IS '合同编号快照（W.6）';

ALTER TABLE purchase_acceptance_item ADD COLUMN IF NOT EXISTS acceptance_no VARCHAR(30);
COMMENT ON COLUMN purchase_acceptance_item.acceptance_no IS '验收单号快照（W.6）';

ALTER TABLE maintenance_execution_result ADD COLUMN IF NOT EXISTS execution_no VARCHAR(30);
ALTER TABLE pm_execution_result ADD COLUMN IF NOT EXISTS execution_no VARCHAR(30);
ALTER TABLE inspection_execution_result ADD COLUMN IF NOT EXISTS execution_no VARCHAR(30);
ALTER TABLE metrology_execution_result ADD COLUMN IF NOT EXISTS execution_no VARCHAR(30);
COMMENT ON COLUMN maintenance_execution_result.execution_no IS '执行单号快照（W.6）';
COMMENT ON COLUMN pm_execution_result.execution_no IS '执行单号快照（W.6）';
COMMENT ON COLUMN inspection_execution_result.execution_no IS '执行单号快照（W.6）';
COMMENT ON COLUMN metrology_execution_result.execution_no IS '执行单号快照（W.6）';

ALTER TABLE repair_workorder_segment_user ADD COLUMN IF NOT EXISTS wo_no VARCHAR(30);
COMMENT ON COLUMN repair_workorder_segment_user.wo_no IS '工单号快照（W.6）';

ALTER TABLE inspection_record ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
ALTER TABLE inspection_record ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
COMMENT ON COLUMN inspection_record.device_code IS '设备编码快照（W.6）';
COMMENT ON COLUMN inspection_record.device_name IS '设备名称快照（W.6）';
