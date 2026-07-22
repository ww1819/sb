-- 保养 / 巡检 / PM 模板演示数据（幂等：按 template_code 跳过已存在）
-- 用法：SET search_path TO tenant_demo; \i data/seed/ops_templates_demo.sql
-- 或：psql ... -v schema=tenant_demo -f ...

SET search_path TO tenant_demo;

-- 基础类型（若不存在）
INSERT INTO maintenance_level (id, level_code, level_name, sort_order, is_active)
SELECT gen_random_uuid(), v.code, v.name, v.ord, TRUE
FROM (VALUES ('L1','一级保养',1),('L2','二级保养',2)) AS v(code,name,ord)
WHERE NOT EXISTS (SELECT 1 FROM maintenance_level x WHERE x.level_code = v.code AND COALESCE(x.is_deleted,0)=0);

INSERT INTO inspection_type (id, type_code, type_name, sort_order, is_active)
SELECT gen_random_uuid(), v.code, v.name, v.ord, TRUE
FROM (VALUES ('ROUTINE','日常巡检',1),('SAFETY','安全巡检',2)) AS v(code,name,ord)
WHERE NOT EXISTS (SELECT 1 FROM inspection_type x WHERE x.type_code = v.code AND COALESCE(x.is_deleted,0)=0);

INSERT INTO pm_type (id, type_code, type_name, risk_level, sort_order, is_active)
SELECT gen_random_uuid(), v.code, v.name, v.risk, v.ord, TRUE
FROM (VALUES ('ANNUAL','年度PM','medium',1),('SEMI','半年度PM','high',2)) AS v(code,name,risk,ord)
WHERE NOT EXISTS (SELECT 1 FROM pm_type x WHERE x.type_code = v.code AND COALESCE(x.is_deleted,0)=0);

-- 保养模板
DO $$
DECLARE
  tid UUID;
  lid UUID;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM maintenance_template WHERE template_code='MT-DEMO-L1' AND COALESCE(is_deleted,0)=0) THEN
    SELECT id INTO lid FROM maintenance_level WHERE level_code='L1' AND COALESCE(is_deleted,0)=0 LIMIT 1;
    tid := gen_random_uuid();
    INSERT INTO maintenance_template (id, template_code, template_name, maintenance_level, maintenance_level_id, items, description, estimated_duration, is_active)
    VALUES (tid, 'MT-DEMO-L1', '通用设备一级保养模板', 'L1', lid, '[]'::jsonb, '演示：外观清洁、通电自检', 30, TRUE);
    INSERT INTO maintenance_template_item (id, template_id, item_code, item_name, item_content, standard_value, check_method, sort_order, is_required) VALUES
      (gen_random_uuid(), tid, 'MT1-01', '外观清洁', '擦拭机身表面，清除灰尘与污渍', '无可见污渍', '目视', 1, TRUE),
      (gen_random_uuid(), tid, 'MT1-02', '通电自检', '开机自检通过，无报警', '自检通过', '通电', 2, TRUE),
      (gen_random_uuid(), tid, 'MT1-03', '附件检查', '电源线、探头/附件完好', '完好', '目视+手检', 3, TRUE);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM maintenance_template WHERE template_code='MT-DEMO-L2' AND COALESCE(is_deleted,0)=0) THEN
    SELECT id INTO lid FROM maintenance_level WHERE level_code='L2' AND COALESCE(is_deleted,0)=0 LIMIT 1;
    tid := gen_random_uuid();
    INSERT INTO maintenance_template (id, template_code, template_name, maintenance_level, maintenance_level_id, items, description, estimated_duration, is_active)
    VALUES (tid, 'MT-DEMO-L2', '监护类设备二级保养模板', 'L2', lid, '[]'::jsonb, '演示：性能核对、电池保养', 60, TRUE);
    INSERT INTO maintenance_template_item (id, template_id, item_code, item_name, item_content, standard_value, check_method, sort_order, is_required) VALUES
      (gen_random_uuid(), tid, 'MT2-01', '性能核对', '按厂家手册核对主要参数', '符合说明书', '功能测试', 1, TRUE),
      (gen_random_uuid(), tid, 'MT2-02', '电池保养', '充放电循环，检查续航', '续航正常', '实测', 2, TRUE),
      (gen_random_uuid(), tid, 'MT2-03', '传感器校准确认', '确认传感器/模块工作正常', '正常', '比对', 3, FALSE);
  END IF;
END $$;

-- 巡检模板
DO $$
DECLARE
  tid UUID;
  typ UUID;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM inspection_template WHERE template_code='INS-DEMO-DAILY' AND COALESCE(is_deleted,0)=0) THEN
    SELECT id INTO typ FROM inspection_type WHERE type_code='ROUTINE' AND COALESCE(is_deleted,0)=0 LIMIT 1;
    tid := gen_random_uuid();
    INSERT INTO inspection_template (id, template_code, template_name, inspection_type_id, description, estimated_duration, is_active)
    VALUES (tid, 'INS-DEMO-DAILY', '病区日常巡检模板', typ, '演示：病区通用日常巡检', 15, TRUE);
    INSERT INTO inspection_template_item (id, template_id, item_code, item_name, item_content, standard_value, check_method, sort_order, is_required) VALUES
      (gen_random_uuid(), tid, 'INS1-01', '在位状态', '设备在位、标识清晰', '在位', '目视', 1, TRUE),
      (gen_random_uuid(), tid, 'INS1-02', '运行指示', '指示灯/屏幕显示正常', '正常', '目视', 2, TRUE),
      (gen_random_uuid(), tid, 'INS1-03', '环境安全', '周围无遮挡、无积水、通风正常', '安全', '目视', 3, TRUE);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM inspection_template WHERE template_code='INS-DEMO-SAFETY' AND COALESCE(is_deleted,0)=0) THEN
    SELECT id INTO typ FROM inspection_type WHERE type_code='SAFETY' AND COALESCE(is_deleted,0)=0 LIMIT 1;
    tid := gen_random_uuid();
    INSERT INTO inspection_template (id, template_code, template_name, inspection_type_id, description, estimated_duration, is_active)
    VALUES (tid, 'INS-DEMO-SAFETY', '生命支持设备安全巡检模板', typ, '演示：高风险设备安全巡检', 20, TRUE);
    INSERT INTO inspection_template_item (id, template_id, item_code, item_name, item_content, standard_value, check_method, sort_order, is_required) VALUES
      (gen_random_uuid(), tid, 'INS2-01', '报警功能', '测试报警可触发并可消音复位', '可报警', '功能测试', 1, TRUE),
      (gen_random_uuid(), tid, 'INS2-02', '气路/管路', '管路连接牢固无泄漏', '无泄漏', '目视+听诊', 2, TRUE),
      (gen_random_uuid(), tid, 'INS2-03', '应急电源', '市电断开后可切换应急供电', '可切换', '实测', 3, TRUE);
  END IF;
END $$;

-- PM 模板
DO $$
DECLARE
  tid UUID;
  typ UUID;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pm_template WHERE template_code='PM-DEMO-ANNUAL' AND COALESCE(is_deleted,0)=0) THEN
    SELECT id INTO typ FROM pm_type WHERE type_code='ANNUAL' AND COALESCE(is_deleted,0)=0 LIMIT 1;
    tid := gen_random_uuid();
    INSERT INTO pm_template (id, template_code, template_name, pm_type, pm_type_id, items, description, estimated_duration, is_active)
    VALUES (tid, 'PM-DEMO-ANNUAL', '厂家年度预防性维护模板', 'ANNUAL', typ, '[]'::jsonb, '演示：合同年度 PM', 120, TRUE);
    INSERT INTO pm_template_item (id, template_id, item_code, item_name, item_content, standard_value, check_method, sort_order, is_required) VALUES
      (gen_random_uuid(), tid, 'PM1-01', '电气安全', '漏电流、接地电阻检测', '符合 GB 9706', '仪器检测', 1, TRUE),
      (gen_random_uuid(), tid, 'PM1-02', '机械部件', '传动/升降/刹车机构润滑与紧固', '灵活可靠', '手检', 2, TRUE),
      (gen_random_uuid(), tid, 'PM1-03', '软件/固件版本', '记录当前版本并确认在保内', '已记录', '系统查询', 3, FALSE);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pm_template WHERE template_code='PM-DEMO-SEMI' AND COALESCE(is_deleted,0)=0) THEN
    SELECT id INTO typ FROM pm_type WHERE type_code='SEMI' AND COALESCE(is_deleted,0)=0 LIMIT 1;
    tid := gen_random_uuid();
    INSERT INTO pm_template (id, template_code, template_name, pm_type, pm_type_id, items, description, estimated_duration, is_active)
    VALUES (tid, 'PM-DEMO-SEMI', '半年度法规合规 PM 模板', 'SEMI', typ, '[]'::jsonb, '演示：半年度合规维护', 90, TRUE);
    INSERT INTO pm_template_item (id, template_id, item_code, item_name, item_content, standard_value, check_method, sort_order, is_required) VALUES
      (gen_random_uuid(), tid, 'PM2-01', '计量/校准状态', '确认计量有效期内或安排送检', '在有效期', '台账核对', 1, TRUE),
      (gen_random_uuid(), tid, 'PM2-02', '关键附件更换', '按厂家周期更换滤芯/密封件等', '已更换或无需', '按手册', 2, TRUE),
      (gen_random_uuid(), tid, 'PM2-03', '服务报告归档', '上传或归档 PM 服务报告', '已归档', '文件检查', 3, TRUE);
  END IF;
END $$;
