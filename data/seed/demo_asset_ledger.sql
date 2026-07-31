-- =============================================================================
-- AST-DEMO-01：资产登记列表 + 查看态各 Sheet 样例数据
-- 用法（必须在租户 schema 下执行，禁止 public）：
--   SET search_path TO tenant_demo;   -- ★ 先切租户，再执行本文件
--   \i data/seed/demo_asset_ledger.sql
-- 或在 pgAdmin「查询工具」里先单独执行 SET search_path，再粘贴本脚本。
--
-- 【若提示 schema "public" 缺少列】
--   说明当前连的是 public，不是 tenant_demo。业务表在租户 schema，public 没有这些补列。
-- 【若提示 schema "tenant_demo" 缺少列】
--   说明该租户未跑完 R__：重启 meis-tenant 后再执行。
--
-- 种子命名空间（仅用于圈定删除范围，界面文案不含「演示/测试」）：
--   设备 SD2408**** / 旧版 DEMO-AST-***；配件 SDSP**** / DEMO-SP-*；
--   业务单号以 SD 开头或历史 DEMO-* 前缀
-- 不可 SQL 填充：设备档案、设备图片（前端内存态，无落库表）
-- =============================================================================

-- #############################################################################
-- 0) 预检：本 schema 是否已具备种子所需关键列（缺则说明未跑 R__）
-- #############################################################################
DO $$
DECLARE
  v_schema TEXT := current_schema();
  v_miss TEXT := '';
  r RECORD;
BEGIN
  IF v_schema IS NULL OR v_schema IN ('public', 'pg_catalog', 'information_schema') THEN
    RAISE EXCEPTION
      '当前 search_path 落在 schema "%"，业务种子必须在租户 schema 执行。请先执行：SET search_path TO tenant_demo; 然后再跑本脚本。',
      COALESCE(v_schema, '(null)');
  END IF;

  FOR r IN
    SELECT * FROM (VALUES
      ('shared_device_fee', 'loan_no'),
      ('shared_device_fee', 'device_id'),
      ('shared_device_fee', 'device_code'),
      ('shared_device_fee', 'device_name'),
      ('shared_device_fee', 'is_deleted'),
      ('medical_device', 'is_deleted'),
      ('medical_device', 'location_floor'),
      ('medical_device', 'room_number'),
      ('medical_device', 'use_dept_head'),
      ('metrology_execution_item', 'execution_no'),
      ('metrology_execution_item', 'is_deleted'),
      ('maintenance_execution_item', 'is_deleted'),
      ('inspection_execution_item', 'is_deleted'),
      ('pm_execution_item', 'is_deleted'),
      ('repair_workorder_segment_part', 'wo_no'),
      ('repair_workorder_segment_part', 'is_deleted')
    ) AS t(tbl, col)
  LOOP
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = v_schema
        AND table_name = r.tbl AND column_name = r.col
    ) THEN
      v_miss := v_miss || E'\n  - ' || r.tbl || '.' || r.col;
    END IF;
  END LOOP;

  IF v_miss <> '' THEN
    -- PG 的 RAISE 占位符是 %（不是 %s）；多写 s 会拼出 is_deleteds 这类乱码
    RAISE EXCEPTION
      'schema "%" 缺少种子所需列：%\n请先重启 meis-tenant 执行 R__columns_biz / R__columns_audit，再重跑本脚本。',
      v_schema, v_miss;
  END IF;
END
$$;

-- #############################################################################
-- 1) 物理删除（子表优先；兼容旧 DEMO-* 与当前 SD* 命名）
-- #############################################################################

-- 电流读数 / 绑定 / 状态 / 标签
DELETE FROM power_current_reading
 WHERE tag_code IN ('SDCT0001', 'DEMO-TAG-AST-001')
    OR device_code LIKE 'SD2408%'
    OR device_code LIKE 'DEMO-AST-%';

DELETE FROM power_tag_bind_log
 WHERE tag_id IN (SELECT id FROM power_tag WHERE tag_code IN ('SDCT0001', 'DEMO-TAG-AST-001'))
    OR device_code LIKE 'SD2408%'
    OR device_code LIKE 'DEMO-AST-%';

DELETE FROM power_device_status
 WHERE tag_id IN (SELECT id FROM power_tag WHERE tag_code IN ('SDCT0001', 'DEMO-TAG-AST-001'))
    OR device_code LIKE 'SD2408%'
    OR device_code LIKE 'DEMO-AST-%';

DELETE FROM power_monitor_record
 WHERE tag_id IN (SELECT id FROM power_tag WHERE tag_code IN ('SDCT0001', 'DEMO-TAG-AST-001'))
    OR device_code LIKE 'SD2408%'
    OR device_code LIKE 'DEMO-AST-%';

DELETE FROM power_tag WHERE tag_code IN ('SDCT0001', 'DEMO-TAG-AST-001');

-- 不良事件
DELETE FROM adverse_event
 WHERE event_no IN ('SDAE0001', 'DEMO-AE-001')
    OR device_code LIKE 'SD2408%'
    OR device_code LIKE 'DEMO-AST-%';

-- 盘点（明细随头 CASCADE，仍显式清）
DELETE FROM inventory_check_item
 WHERE check_id IN (SELECT id FROM inventory_check WHERE check_no IN ('SDPD2025A01', 'DEMO-INV-001'))
    OR device_code LIKE 'SD2408%'
    OR device_code LIKE 'DEMO-AST-%';
DELETE FROM inventory_check WHERE check_no IN ('SDPD2025A01', 'DEMO-INV-001');

-- 借调费用 / 归还 / 借调
-- 说明：shared_device_fee 的 loan_no/device_* 为 R__ 增量列；删除仅用 fee_no + loan_id，兼容未迁库租户
DELETE FROM shared_device_fee
 WHERE fee_no IN ('SDSF0001', 'DEMO-FEE-001')
    OR loan_id IN (
      SELECT id FROM shared_device_loan
      WHERE loan_no IN ('SDJD20250701', 'DEMO-LOAN-001')
         OR device_code LIKE 'SD2408%'
         OR device_code LIKE 'DEMO-AST-%'
    );
DELETE FROM shared_device_return
 WHERE loan_id IN (SELECT id FROM shared_device_loan WHERE loan_no IN ('SDJD20250701', 'DEMO-LOAN-001'));
DELETE FROM shared_device_loan
 WHERE loan_no IN ('SDJD20250701', 'DEMO-LOAN-001')
    OR device_code LIKE 'SD2408%'
    OR device_code LIKE 'DEMO-AST-%';

-- 计量执行 / 计划
DELETE FROM metrology_execution_result
 WHERE execution_item_id IN (
   SELECT ei.id FROM metrology_execution_item ei
   JOIN metrology_execution e ON e.id = ei.execution_id
   WHERE e.execution_no IN ('SDJLZX0001', 'DEMO-EXE-ME-001')
      OR ei.device_code LIKE 'SD2408%' OR ei.device_code LIKE 'DEMO-AST-%'
 );
DELETE FROM metrology_execution_item
 WHERE execution_id IN (SELECT id FROM metrology_execution WHERE execution_no IN ('SDJLZX0001', 'DEMO-EXE-ME-001'))
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%';
DELETE FROM metrology_execution WHERE execution_no IN ('SDJLZX0001', 'DEMO-EXE-ME-001');
DELETE FROM metrology_plan WHERE plan_code IN ('SDJLJH0001', 'DEMO-PLAN-ME-001');

-- PM 执行 / 计划
DELETE FROM pm_execution_result
 WHERE execution_item_id IN (
   SELECT ei.id FROM pm_execution_item ei
   JOIN pm_execution e ON e.id = ei.execution_id
   WHERE e.execution_no IN ('SDPMZX0001', 'DEMO-EXE-PM-001')
      OR ei.device_code LIKE 'SD2408%' OR ei.device_code LIKE 'DEMO-AST-%'
 );
DELETE FROM pm_execution_item
 WHERE execution_id IN (SELECT id FROM pm_execution WHERE execution_no IN ('SDPMZX0001', 'DEMO-EXE-PM-001'))
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%';
DELETE FROM pm_execution WHERE execution_no IN ('SDPMZX0001', 'DEMO-EXE-PM-001');
DELETE FROM pm_plan_item
 WHERE plan_id IN (SELECT id FROM pm_plan WHERE plan_no IN ('SDPMJH0001', 'DEMO-PLAN-PM-001'))
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%';
DELETE FROM pm_plan WHERE plan_no IN ('SDPMJH0001', 'DEMO-PLAN-PM-001');
DELETE FROM ops_plan_include_request
 WHERE module = 'pm' AND (plan_no IN ('SDPMJH0001', 'DEMO-PLAN-PM-001')
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%');

-- 巡检执行 / 计划
DELETE FROM inspection_execution_result
 WHERE execution_item_id IN (
   SELECT ei.id FROM inspection_execution_item ei
   JOIN inspection_execution e ON e.id = ei.execution_id
   WHERE e.execution_no IN ('SDXJZX0001', 'DEMO-EXE-IN-001')
      OR ei.device_code LIKE 'SD2408%' OR ei.device_code LIKE 'DEMO-AST-%'
 );
DELETE FROM inspection_execution_item
 WHERE execution_id IN (SELECT id FROM inspection_execution WHERE execution_no IN ('SDXJZX0001', 'DEMO-EXE-IN-001'))
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%';
DELETE FROM inspection_execution WHERE execution_no IN ('SDXJZX0001', 'DEMO-EXE-IN-001');
DELETE FROM inspection_plan_item
 WHERE plan_id IN (SELECT id FROM inspection_plan WHERE plan_no IN ('SDXJJH0001', 'DEMO-PLAN-IN-001'))
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%';
DELETE FROM inspection_plan WHERE plan_no IN ('SDXJJH0001', 'DEMO-PLAN-IN-001');
DELETE FROM ops_plan_include_request
 WHERE module = 'inspect' AND (plan_no IN ('SDXJJH0001', 'DEMO-PLAN-IN-001')
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%');

-- 保养执行 / 计划 / 旧 record
DELETE FROM maintenance_execution_result
 WHERE execution_item_id IN (
   SELECT ei.id FROM maintenance_execution_item ei
   JOIN maintenance_execution e ON e.id = ei.execution_id
   WHERE e.execution_no IN ('SDBYZX0001', 'DEMO-EXE-MT-001')
      OR ei.device_code LIKE 'SD2408%' OR ei.device_code LIKE 'DEMO-AST-%'
 );
DELETE FROM maintenance_execution_item
 WHERE execution_id IN (SELECT id FROM maintenance_execution WHERE execution_no IN ('SDBYZX0001', 'DEMO-EXE-MT-001'))
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%';
DELETE FROM maintenance_execution WHERE execution_no IN ('SDBYZX0001', 'DEMO-EXE-MT-001');
DELETE FROM maintenance_record
 WHERE device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%'
    OR plan_id IN (SELECT id FROM maintenance_plan WHERE plan_no IN ('SDBYJH0001', 'DEMO-PLAN-MT-001'));
DELETE FROM maintenance_plan_item
 WHERE plan_id IN (SELECT id FROM maintenance_plan WHERE plan_no IN ('SDBYJH0001', 'DEMO-PLAN-MT-001'))
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%';
DELETE FROM maintenance_plan WHERE plan_no IN ('SDBYJH0001', 'DEMO-PLAN-MT-001');
DELETE FROM ops_plan_include_request
 WHERE module = 'maintain' AND (plan_no IN ('SDBYJH0001', 'DEMO-PLAN-MT-001')
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%');

-- 标签打印
DELETE FROM device_label_print_log
 WHERE device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%'
    OR remark IN ('DEMO-LABEL-AST-001', 'DEMO-LABEL-CARD-001');

-- 维保主从（先明细后头；头仅删本脚本种子）
DELETE FROM device_warranty_device
 WHERE device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%'
    OR warranty_id IN (SELECT id FROM device_warranty WHERE remark IN ('SDWRN0001', 'DEMO-WRN-AST-001'));
DELETE FROM device_warranty WHERE remark IN ('SDWRN0001', 'DEMO-WRN-AST-001');

-- 维修工单（段/配件 CASCADE）
DELETE FROM repair_workorder
 WHERE wo_no IN ('SDWX2025071801', 'DEMO-WO-AST-001')
    OR device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%';

-- 配件（先清段配件引用，再删种子配件）
DELETE FROM repair_workorder_segment_part
 WHERE spare_part_id IN (
   SELECT id FROM spare_part
   WHERE part_code IN ('SDSP0001', 'SDSP0002', 'SDSP0003', 'DEMO-SP-001', 'DEMO-SP-002', 'DEMO-SP-003')
 );
DELETE FROM spare_part
 WHERE part_code IN ('SDSP0001', 'SDSP0002', 'SDSP0003', 'DEMO-SP-001', 'DEMO-SP-002', 'DEMO-SP-003');

-- 设备台账（最后删）
DELETE FROM medical_device
 WHERE device_code LIKE 'SD2408%' OR device_code LIKE 'DEMO-AST-%';

-- #############################################################################
-- 2) 插入：60 台台账
-- #############################################################################

DO $$
DECLARE
  v_campus UUID;
  v_dept UUID;
  v_wh UUID;
  v_i INT;
  v_code TEXT;
  v_names TEXT[] := ARRAY[
    '多参数监护仪','注射泵','呼吸机','除颤监护仪','心电图机',
    '彩色超声诊断系统','血液透析机','麻醉工作站','手术无影灯','电动手术床',
    '全自动洗消机','脉动真空灭菌器','婴儿培养箱','医用雾化器','制氧机',
    '血糖分析仪','红外额温计','电动吸引器','折叠轮椅','电动病床'
  ];
  v_brands TEXT[] := ARRAY['迈瑞','飞利浦','GE','西门子','鱼跃','理邦','新华医疗','德尔格'];
  v_models TEXT[] := ARRAY[
    'uMEC12','IntelliVue MX450','B105','SC7000','BeneHeart R12',
    'Resona 7','Dialog+','Fabius plus','HyLED 9','HyBase 6100',
    'WD290','MOST-T-100','YP-970','NB-150','8F-5',
    'EA-18','YHW-2','7A-23D','KL-FY','A3'
  ];
  v_specs TEXT[] := ARRAY[
    '12.1寸触控屏','双通道注射','有创通气','双相波200J','12导联',
    '凸阵+线阵','单泵','七管','LED冷光源','对开腿板',
    '双舱','容积100L','伺服控制','超声雾化','5L/min',
    '末梢血','非接触','负压≥0.09MPa','铝合金架','三功能'
  ];
  v_locs TEXT[] := ARRAY[
    '重症医学科监护一区','急诊抢救室','手术室3号间','心内科病区东侧','儿科病房二区'
  ];
  v_heads TEXT[] := ARRAY['王磊','李敏','张华','陈静','赵强','刘洋','周倩','孙伟'];
BEGIN
  SELECT id INTO v_campus FROM campus WHERE campus_code = 'A' AND COALESCE(is_deleted,0)=0 LIMIT 1;
  SELECT id INTO v_dept FROM department WHERE dept_code = '001' AND COALESCE(is_deleted,0)=0 LIMIT 1;
  SELECT id INTO v_wh FROM warehouse WHERE COALESCE(is_deleted,0)=0 ORDER BY created_at NULLS LAST LIMIT 1;

  FOR v_i IN 1..60 LOOP
    v_code := 'SD2408' || lpad(v_i::text, 4, '0');
    INSERT INTO medical_device (
      device_code, device_name, brand, model, specification, serial_number,
      campus_id, dept_id, warehouse_id, location_detail, location_floor, room_number,
      use_dept_head, device_status, risk_level,
      original_value, net_value, purchase_date, enable_date, acceptance_date, production_date,
      registration_no, is_active, is_deleted
    ) VALUES (
      v_code,
      v_names[((v_i - 1) % array_length(v_names, 1)) + 1],
      v_brands[((v_i - 1) % array_length(v_brands, 1)) + 1],
      v_models[((v_i - 1) % array_length(v_models, 1)) + 1],
      v_specs[((v_i - 1) % array_length(v_specs, 1)) + 1],
      'SN' || to_char(CURRENT_DATE, 'YY') || lpad(v_i::text, 6, '0'),
      v_campus, v_dept, v_wh,
      v_locs[((v_i - 1) % array_length(v_locs, 1)) + 1],
      ((v_i % 8) + 1)::text || 'F',
      lpad(((v_i % 20) + 101)::text, 3, '0'),
      v_heads[((v_i - 1) % array_length(v_heads, 1)) + 1],
      CASE WHEN v_i % 7 = 0 THEN 'in_use' ELSE 'normal' END,
      CASE WHEN v_i % 5 = 0 THEN 'high' WHEN v_i % 3 = 0 THEN 'medium' ELSE 'low' END,
      (18600 + v_i * 1280)::numeric,
      (12400 + v_i * 860)::numeric,
      (CURRENT_DATE - (v_i * 20 + 100)),
      (CURRENT_DATE - (v_i * 15 + 60)),
      (CURRENT_DATE - (v_i * 15 + 70)),
      (CURRENT_DATE - (v_i * 40 + 400)),
      '国械注准20193' || lpad((700 + v_i)::text, 5, '0'),
      TRUE, 0
    );
  END LOOP;
END
$$;

-- #############################################################################
-- 3) 配件
-- #############################################################################

INSERT INTO spare_part (part_code, part_name, specification, model, unit_price, stock_quantity, is_active)
VALUES
  ('SDSP0001', '电源模块', '12V/5A 医疗级', 'PSU-MR12', 320.00, 18, TRUE),
  ('SDSP0002', '血氧探头延长线', '3m 屏蔽线', 'SPO2-C3', 180.00, 35, TRUE),
  ('SDSP0003', '密封圈套件', '标准口径', 'SEAL-STD', 45.00, 80, TRUE);

-- #############################################################################
-- 4) 样板机 SD24080001：维修 + 各 Sheet
-- #############################################################################

DO $$
DECLARE
  v_dev UUID;
  v_code TEXT := 'SD24080001';
  v_name TEXT;
  v_dept UUID;
  v_dept2 UUID;
  v_campus UUID;
  v_user UUID;
  v_user_name TEXT := '系统管理员';
  v_supplier UUID;
  v_supplier_name TEXT := '深圳市迈瑞生物医疗电子股份有限公司';
  v_wid UUID;
  v_plan UUID;
  v_exec UUID;
  v_loan UUID;
  v_check UUID;
  v_tag UUID;
  v_station UUID;
  v_wo UUID;
  v_seg UUID;
  v_ptype UUID;
  v_part1 UUID;
  v_part2 UUID;
  v_loc TEXT;
BEGIN
  SELECT id, device_name, dept_id, campus_id, location_detail
    INTO v_dev, v_name, v_dept, v_campus, v_loc
  FROM medical_device
  WHERE device_code = v_code AND COALESCE(is_deleted, 0) = 0
  LIMIT 1;
  IF v_dev IS NULL THEN
    RAISE EXCEPTION 'seed device % missing after insert', v_code;
  END IF;

  SELECT id INTO v_user FROM sys_user WHERE username = 'admin' AND COALESCE(is_deleted, 0) = 0 LIMIT 1;
  SELECT COALESCE(real_name, username) INTO v_user_name FROM sys_user WHERE id = v_user;
  IF v_user_name IS NULL OR btrim(v_user_name) = '' THEN v_user_name := '系统管理员'; END IF;

  SELECT id INTO v_dept2
  FROM department
  WHERE COALESCE(is_deleted, 0) = 0 AND (v_dept IS NULL OR id <> v_dept)
  ORDER BY created_at NULLS LAST
  LIMIT 1;
  IF v_dept2 IS NULL THEN v_dept2 := v_dept; END IF;

  SELECT id, supplier_name INTO v_supplier, v_supplier_name
  FROM supplier WHERE COALESCE(is_deleted, 0) = 0 ORDER BY created_at NULLS LAST LIMIT 1;
  IF v_supplier_name IS NULL OR btrim(v_supplier_name) = '' THEN
    v_supplier_name := '深圳市迈瑞生物医疗电子股份有限公司';
  END IF;

  SELECT id INTO v_station FROM power_base_station WHERE COALESCE(is_deleted, 0) = 0 LIMIT 1;
  SELECT id INTO v_part1 FROM spare_part WHERE part_code = 'SDSP0001' LIMIT 1;
  SELECT id INTO v_part2 FROM spare_part WHERE part_code = 'SDSP0002' LIMIT 1;

  SELECT id INTO v_ptype FROM repair_process_type
  WHERE can_add_parts = TRUE AND COALESCE(is_deleted, 0) = 0
  ORDER BY sort_order NULLS LAST LIMIT 1;
  IF v_ptype IS NULL THEN
    SELECT id INTO v_ptype FROM repair_process_type
    WHERE COALESCE(is_deleted, 0) = 0 ORDER BY sort_order NULLS LAST LIMIT 1;
  END IF;

  UPDATE medical_device
  SET is_shared_device = TRUE,
      shared_fee_mode = 'time',
      shared_fee_time_unit = 'day',
      shared_fee_unit_price = 50,
      label_printed = TRUE,
      updated_at = NOW()
  WHERE id = v_dev;

  -- 维修工单 + 换件
  IF v_ptype IS NOT NULL THEN
    v_wo := gen_random_uuid();
    INSERT INTO repair_workorder (
      id, wo_no, device_id, device_code, device_name, report_time, fault_description,
      urgency_level, status, reporter_id
    ) VALUES (
      v_wo, 'SDWX2025071801', v_dev, v_code, v_name, NOW() - INTERVAL '10 days',
      '开机后屏幕间歇性黑屏，伴电源指示灯闪烁，重启可暂时恢复', 'normal', 'closed', v_user
    );

    v_seg := gen_random_uuid();
    INSERT INTO repair_workorder_segment (
      id, workorder_id, process_type_id, user_id, started_at, ended_at,
      device_id, device_code, device_name, remark, is_deleted
    ) VALUES (
      v_seg, v_wo, v_ptype, v_user, NOW() - INTERVAL '9 days', NOW() - INTERVAL '8 days',
      v_dev, v_code, v_name, '更换电源模块及血氧探头延长线', 0
    );

    IF v_part1 IS NOT NULL THEN
      INSERT INTO repair_workorder_segment_part (
        segment_id, spare_part_id, quantity, unit_price, total_price,
        device_id, device_code, device_name, wo_no, remark, is_deleted
      ) VALUES (
        v_seg, v_part1, 1, 320.00, 320.00, v_dev, v_code, v_name, 'SDWX2025071801', '更换电源模块', 0
      );
    END IF;
    IF v_part2 IS NOT NULL THEN
      INSERT INTO repair_workorder_segment_part (
        segment_id, spare_part_id, quantity, unit_price, total_price,
        device_id, device_code, device_name, wo_no, remark, is_deleted
      ) VALUES (
        v_seg, v_part2, 2, 180.00, 360.00, v_dev, v_code, v_name, 'SDWX2025071801', '更换血氧探头延长线', 0
      );
    END IF;
  END IF;

  -- 维保
  v_wid := gen_random_uuid();
  INSERT INTO device_warranty (
    id, supplier_id, supplier_name, start_date, end_date, total_amount,
    coverage_content, remark, created_by, created_by_name, is_deleted
  ) VALUES (
    v_wid, v_supplier, v_supplier_name,
    CURRENT_DATE - 90, CURRENT_DATE + 275, 12800.00,
    '整机保养、4小时响应、常用易损件更换（不含探头）',
    'SDWRN0001', v_user, v_user_name, 0
  );
  INSERT INTO device_warranty_device (
    warranty_id, device_id, device_code, device_name, unit_price,
    remark, created_by, created_by_name, is_deleted
  ) VALUES (
    v_wid, v_dev, v_code, v_name, 12800.00,
    '年度维保包覆盖', v_user, v_user_name, 0
  );

  -- 标签打印
  INSERT INTO device_label_print_log (
    device_id, device_code, device_name, printed_by, printed_by_name,
    printed_at, template_code, biz_type, remark, is_deleted
  ) VALUES
    (v_dev, v_code, v_name, v_user, v_user_name, NOW() - INTERVAL '3 days', 'default', 'device', '入库补打资产标签', 0),
    (v_dev, v_code, v_name, v_user, v_user_name, NOW() - INTERVAL '1 day', 'asset_card', 'device', '打印资产卡片', 0);

  -- 保养计划 + 执行
  v_plan := gen_random_uuid();
  INSERT INTO maintenance_plan (
    id, plan_name, plan_no, plan_code, device_id, dept_id, campus_id,
    maintenance_level, cycle_type, cycle_value, cycle_days,
    next_due_date, last_maintained_at, status, approval_status,
    assigned_user_id, assigned_user_name, created_by, created_by_name, remark
  ) VALUES (
    v_plan, '多参数监护仪一级保养', 'SDBYJH0001', 'SDBYJH0001',
    v_dev, v_dept, v_campus, 'level1', 'month', 3, 90,
    CURRENT_DATE + 30, CURRENT_DATE - 60, 'active', 'approved',
    v_user, v_user_name, v_user, v_user_name, 'ICU监护设备季度保养'
  );
  INSERT INTO maintenance_plan_item (
    plan_id, plan_no, device_id, device_code, device_name, dept_id,
    last_done_date, next_due_date, assigned_user_id, assigned_user_name,
    item_status, remark, created_by, created_by_name, is_deleted
  ) VALUES (
    v_plan, 'SDBYJH0001', v_dev, v_code, v_name, v_dept,
    CURRENT_DATE - 60, CURRENT_DATE + 30, v_user, v_user_name,
    'active', NULL, v_user, v_user_name, 0
  );

  v_exec := gen_random_uuid();
  INSERT INTO maintenance_execution (
    id, execution_no, plan_id, plan_no, source_type, maintenance_level,
    planned_date, assigned_user_id, assigned_user_name, executor_id, executor_name,
    execute_start_time, execute_end_time, status, created_by, created_by_name, remark
  ) VALUES (
    v_exec, 'SDBYZX0001', v_plan, 'SDBYJH0001', 'from_plan', 'level1',
    CURRENT_DATE - 60, v_user, v_user_name, v_user, v_user_name,
    NOW() - INTERVAL '60 days', NOW() - INTERVAL '60 days' + INTERVAL '2 hours',
    'completed', v_user, v_user_name, '外观清洁、报警自检、参数校准核对'
  );
  INSERT INTO maintenance_execution_item (
    execution_id, execution_no, device_id, device_code, device_name, dept_id, plan_id,
    executor_id, executor_name, start_time, end_time, status, overall_result,
    remark, is_deleted
  ) VALUES (
    v_exec, 'SDBYZX0001', v_dev, v_code, v_name, v_dept, v_plan,
    v_user, v_user_name, NOW() - INTERVAL '60 days', NOW() - INTERVAL '60 days' + INTERVAL '2 hours',
    'completed', 'pass', '保养合格', 0
  );

  -- 巡检计划 + 执行
  v_plan := gen_random_uuid();
  INSERT INTO inspection_plan (
    id, plan_name, plan_no, plan_code, device_id, dept_id,
    cycle_type, cycle_value, cycle_days, next_due_date, last_inspected_at,
    status, approval_status, assigned_inspector_id, assigned_inspector_name,
    created_by, created_by_name, remark
  ) VALUES (
    v_plan, 'ICU监护仪日常巡检', 'SDXJJH0001', 'SDXJJH0001',
    v_dev, v_dept, 'week', 1, 7, CURRENT_DATE + 3, CURRENT_DATE - 7,
    'active', 'approved', v_user, v_user_name, v_user, v_user_name, '重点监护设备周巡'
  );
  INSERT INTO inspection_plan_item (
    plan_id, plan_no, device_id, device_code, device_name, dept_id,
    last_done_date, next_due_date, assigned_user_id, assigned_user_name,
    item_status, remark, created_by, created_by_name, is_deleted
  ) VALUES (
    v_plan, 'SDXJJH0001', v_dev, v_code, v_name, v_dept,
    CURRENT_DATE - 7, CURRENT_DATE + 3, v_user, v_user_name,
    'active', NULL, v_user, v_user_name, 0
  );

  v_exec := gen_random_uuid();
  INSERT INTO inspection_execution (
    id, execution_no, plan_id, plan_no, source_type, planned_date,
    assigned_inspector_id, executor_id, executor_name,
    execute_start_time, execute_end_time, status, created_by, created_by_name, remark
  ) VALUES (
    v_exec, 'SDXJZX0001', v_plan, 'SDXJJH0001', 'from_plan', CURRENT_DATE - 7,
    v_user, v_user, v_user_name,
    NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days' + INTERVAL '30 minutes',
    'completed', v_user, v_user_name, '在位、通电、报警功能正常'
  );
  INSERT INTO inspection_execution_item (
    execution_id, execution_no, device_id, device_code, device_name, dept_id, plan_id,
    executor_id, executor_name, start_time, end_time, status, overall_result,
    remark, is_deleted
  ) VALUES (
    v_exec, 'SDXJZX0001', v_dev, v_code, v_name, v_dept, v_plan,
    v_user, v_user_name, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days' + INTERVAL '30 minutes',
    'completed', 'pass', '巡检正常', 0
  );

  -- 计量计划 + 执行
  INSERT INTO metrology_plan (
    plan_code, plan_name, device_id, cycle_days, next_due_date, last_calibrated_at,
    assigned_inspector_id, approval_status, created_by, status, remark
  ) VALUES (
    'SDJLJH0001', '多参数监护仪年度强检', v_dev, 365,
    CURRENT_DATE + 200, CURRENT_DATE - 165,
    v_user, 'approved', v_user, 'active', '心电/无创血压/血氧通道'
  ) RETURNING id INTO v_plan;

  v_exec := gen_random_uuid();
  INSERT INTO metrology_execution (
    id, execution_no, plan_id, planned_date, assigned_inspector_id, executor_id,
    execute_start_time, execute_end_time, status, created_by, remark
  ) VALUES (
    v_exec, 'SDJLZX0001', v_plan, CURRENT_DATE - 165, v_user, v_user,
    NOW() - INTERVAL '165 days', NOW() - INTERVAL '165 days' + INTERVAL '3 hours',
    'completed', v_user, '送检并回装'
  );
  INSERT INTO metrology_execution_item (
    execution_id, execution_no, device_id, device_code, device_name, dept_id, plan_id,
    certificate_no, cost, status, overall_result, remark, is_deleted
  ) VALUES (
    v_exec, 'SDJLZX0001', v_dev, v_code, v_name, v_dept, v_plan,
    'JL' || to_char(CURRENT_DATE - 165, 'YYYYMMDD') || '018', 800.00,
    'completed', 'qualified', '检定合格', 0
  );

  -- PM 计划 + 执行
  v_plan := gen_random_uuid();
  INSERT INTO pm_plan (
    id, plan_name, plan_no, plan_code, device_id, dept_id, campus_id,
    cycle_type, cycle_value, cycle_days, next_due_date, last_maintained_at,
    status, approval_status, assigned_user_id, assigned_user_name,
    created_by, created_by_name, remark
  ) VALUES (
    v_plan, '多参数监护仪预防性维护', 'SDPMJH0001', 'SDPMJH0001',
    v_dev, v_dept, v_campus, 'month', 6, 180, CURRENT_DATE + 90, CURRENT_DATE - 90,
    'active', 'approved', v_user, v_user_name, v_user, v_user_name, '半年度深度维护'
  );
  INSERT INTO pm_plan_item (
    plan_id, plan_no, device_id, device_code, device_name, dept_id,
    last_done_date, next_due_date, assigned_user_id, assigned_user_name,
    item_status, remark, created_by, created_by_name, is_deleted
  ) VALUES (
    v_plan, 'SDPMJH0001', v_dev, v_code, v_name, v_dept,
    CURRENT_DATE - 90, CURRENT_DATE + 90, v_user, v_user_name,
    'active', NULL, v_user, v_user_name, 0
  );

  v_exec := gen_random_uuid();
  INSERT INTO pm_execution (
    id, execution_no, plan_id, plan_no, source_type, planned_date,
    assigned_user_id, assigned_user_name, executor_id, executor_name,
    execute_start_time, execute_end_time, status, created_by, created_by_name, remark
  ) VALUES (
    v_exec, 'SDPMZX0001', v_plan, 'SDPMJH0001', 'from_plan', CURRENT_DATE - 90,
    v_user, v_user_name, v_user, v_user_name,
    NOW() - INTERVAL '90 days', NOW() - INTERVAL '90 days' + INTERVAL '4 hours',
    'completed', v_user, v_user_name, '主板固件检查、风扇除尘、接地电阻测量'
  );
  INSERT INTO pm_execution_item (
    execution_id, execution_no, device_id, device_code, device_name, dept_id, plan_id,
    executor_id, executor_name, start_time, end_time, status, overall_result,
    remark, is_deleted
  ) VALUES (
    v_exec, 'SDPMZX0001', v_dev, v_code, v_name, v_dept, v_plan,
    v_user, v_user_name, NOW() - INTERVAL '90 days', NOW() - INTERVAL '90 days' + INTERVAL '4 hours',
    'completed', 'pass', 'PM完成', 0
  );

  -- 借调 + 费用
  v_loan := gen_random_uuid();
  INSERT INTO shared_device_loan (
    id, loan_no, device_id, device_code, device_name,
    from_dept_id, to_dept_id, applicant_id,
    loan_start, loan_end, fee_mode, fee_time_unit, fee_unit_price,
    billing_start_at, billing_end_at, reason, status, approval_status,
    approved_by, approved_by_name, approved_at, loan_time, return_time, remark
  ) VALUES (
    v_loan, 'SDJD20250701', v_dev, v_code, v_name,
    v_dept, v_dept2, v_user,
    CURRENT_DATE - 20, CURRENT_DATE - 5, 'time', 'day', 50.00,
    (CURRENT_DATE - 20)::timestamptz, (CURRENT_DATE - 5)::timestamptz,
    '急诊科床位紧张，临时借用监护仪', 'returned', 'approved',
    v_user, v_user_name, NOW() - INTERVAL '21 days',
    NOW() - INTERVAL '20 days', NOW() - INTERVAL '5 days', '已归还，设备外观完好'
  );

  -- 费用冗余列（W.6）可能尚未迁入：先补列再插入，避免脚本中断
  ALTER TABLE shared_device_fee ADD COLUMN IF NOT EXISTS loan_no VARCHAR(30);
  ALTER TABLE shared_device_fee ADD COLUMN IF NOT EXISTS device_id UUID;
  ALTER TABLE shared_device_fee ADD COLUMN IF NOT EXISTS device_code VARCHAR(50);
  ALTER TABLE shared_device_fee ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);
  ALTER TABLE shared_device_fee ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;

  INSERT INTO shared_device_fee (
    fee_no, loan_id, loan_no, device_id, device_code, device_name,
    fee_amount, fee_date, paid_status, remark, is_deleted
  ) VALUES (
    'SDSF0001', v_loan, 'SDJD20250701', v_dev, v_code, v_name,
    750.00, CURRENT_DATE - 4, 'paid', '借调15天×50元/天', 0
  );

  -- 盘点
  v_check := gen_random_uuid();
  INSERT INTO inventory_check (
    id, check_no, check_name, check_year, check_type, campus_id, dept_id,
    start_date, end_date, actual_start_at, actual_end_at, checker_id,
    total_count, checked_count, matched_count, status, audit_status,
    created_by, approved_by, approved_by_name, approved_at, remark
  ) VALUES (
    v_check, 'SDPD2025A01',
    to_char(CURRENT_DATE, 'YYYY') || '年度固定资产盘点（设备科）',
    EXTRACT(YEAR FROM CURRENT_DATE)::int, 'annual',
    v_campus, v_dept, CURRENT_DATE - 40, CURRENT_DATE - 35,
    NOW() - INTERVAL '40 days', NOW() - INTERVAL '35 days', v_user,
    1, 1, 1, 'completed', 'approved',
    v_user, v_user, v_user_name, NOW() - INTERVAL '34 days', '账实相符'
  );
  INSERT INTO inventory_check_item (
    check_id, device_id, device_code, device_name,
    expected_location, actual_location, is_found, is_matched,
    condition_status, check_date, checker_id, remark
  ) VALUES (
    v_check, v_dev, v_code, v_name,
    COALESCE(v_loc, '重症医学科监护一区'), COALESCE(v_loc, '重症医学科监护一区'), TRUE, TRUE,
    'normal', NOW() - INTERVAL '38 days', v_user, '账实相符'
  );

  -- 不良事件
  INSERT INTO adverse_event (
    event_no, device_id, device_code, device_name,
    reporter_id, reporter_name, report_time, event_type, severity_level,
    event_description, cause_analysis, impact_description,
    handler_id, handler_name, handle_measures, handle_time,
    status, remark
  ) VALUES (
    'SDAE0001', v_dev, v_code, v_name,
    v_user, v_user_name, NOW() - INTERVAL '45 days', 'device_failure', 'minor',
    '夜间监护过程中主机黑屏约30秒后自行恢复，未影响患者生命体征采集连续性',
    '电源模块接触不良导致供电瞬断',
    '短暂中断显示，未造成诊疗延误',
    v_user, v_user_name, '更换电源模块，连续观察7天无复发后关闭事件', NOW() - INTERVAL '40 days',
    'closed', '已闭环'
  );

  -- 电流标签 + 绑定 + 今日读数
  v_tag := gen_random_uuid();
  INSERT INTO power_tag (
    id, tag_code, tag_name, device_id, device_code, device_name,
    station_id, rated_power, install_date, is_active, remark
  ) VALUES (
    v_tag, 'SDCT0001', 'ICU一区监护仪电流标签', v_dev, v_code, v_name,
    v_station, 120.00, CURRENT_DATE - 100, TRUE, '床旁插座回路'
  );

  INSERT INTO power_tag_bind_log (
    tag_id, device_id, device_code, device_name, bound_at, operator_id, remark
  ) VALUES (
    v_tag, v_dev, v_code, v_name, NOW() - INTERVAL '100 days', v_user, '新装绑定'
  );

  INSERT INTO power_current_reading (
    tag_id, tag_code, station_id, station_code, device_id, device_code, current_ma, read_at
  )
  SELECT
    v_tag, 'SDCT0001', v_station,
    (SELECT station_code FROM power_base_station WHERE id = v_station),
    v_dev, v_code, v.ma, (CURRENT_DATE + v.t)
  FROM (VALUES
    (85.5::numeric, TIME '08:15'),
    (120.0::numeric, TIME '10:40'),
    (95.2::numeric, TIME '14:20'),
    (72.0::numeric, TIME '16:05')
  ) AS v(ma, t);
END
$$;
