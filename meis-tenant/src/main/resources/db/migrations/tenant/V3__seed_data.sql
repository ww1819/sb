-- MEIS consolidated Flyway migration (auto-generated, do not split into per-feature files)
-- Categories: V1 tables | V2 extensions | V3 seed data

-- [V2__seed_demo.sql]
-- Demo hospital seed (tenant_demo schema)
INSERT INTO campus (campus_code, campus_name) VALUES ('A', '主院区') ON CONFLICT DO NOTHING;

-- [V2__seed_demo.sql]
INSERT INTO department (dept_code, dept_name, is_clinical) VALUES ('001', '设备科', false) ON CONFLICT DO NOTHING;

-- [V2__seed_demo.sql]
-- admin / admin123 (BCrypt)
INSERT INTO sys_user (username, password_hash, real_name, is_active)
SELECT 'admin', '$2a$10$CedZfmrp1GW/UsPu/jkLfOBO9GpJUMESw/pu4VsxCK9cR6gY9N0/C', '系统管理员', true
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

-- [V2__seed_demo.sql]
INSERT INTO sys_role (role_code, role_name, permissions)
SELECT 'admin', '管理员', '["*"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'admin');

-- [V4__v2_extensions.sql]
-- dict seeds V2.0
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('device_status', 'normal', '正常', 'normal', 1),
('device_status', 'in_use', '在用', 'in_use', 2),
('device_status', 'maintenance', '维修中', 'maintenance', 3),
('device_status', 'scrap', '已报废', 'scrap', 4),
('risk_level', 'high', '高风险', 'high', 1),
('risk_level', 'medium', '中风险', 'medium', 2),
('risk_level', 'low', '低风险', 'low', 3),
('urgency', 'urgent', '紧急', 'urgent', 1),
('urgency', 'high', '高', 'high', 2),
('urgency', 'normal', '普通', 'normal', 3),
('urgency', 'low', '低', 'low', 4),
('purchase_method', 'public_bidding', '公开招标', 'public_bidding', 1),
('purchase_method', 'inquiry', '询价采购', 'inquiry', 2),
('approval_status', 'draft', '草稿', 'draft', 1),
('approval_status', 'pending', '审批中', 'pending', 2),
('approval_status', 'approved', '已通过', 'approved', 3),
('approval_status', 'rejected', '已驳回', 'rejected', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- [V4__v2_extensions.sql]
-- role templates (tenant admin configures permissions within platform grant)
INSERT INTO sys_role (role_code, role_name, permissions) VALUES
('tenant_admin', '租户管理员', '{"menus":["*"],"buttons":["*"],"dataScope":"all"}'::jsonb),
('hospital_leader', '院级领导', '{"menus":[],"buttons":[],"dataScope":"all"}'::jsonb),
('equipment_head', '装备部负责人', '{"menus":[],"buttons":[],"dataScope":"all"}'::jsonb),
('purchase_staff', '采购科人员', '{"menus":["mod_purchase","purchase_plan","purchase_project","purchase_contract"],"buttons":[],"dataScope":"dept"}'::jsonb),
('warehouse_keeper', '库管员', '{"menus":["mod_asset","asset_entry","asset_outbound","asset_inventory"],"buttons":[],"dataScope":"dept"}'::jsonb),
('engineer', '维修工程师', '{"menus":["repair_workorder","maintain_plan","maintain_record"],"buttons":[],"dataScope":"self"}'::jsonb),
('dept_admin', '科室设备管理员', '{"menus":["asset_device","repair_workorder","asset_transfer"],"buttons":[],"dataScope":"dept"}'::jsonb),
('clinical_user', '临床使用人员', '{"menus":["repair_workorder"],"buttons":[],"dataScope":"self"}'::jsonb)
ON CONFLICT (role_code) DO NOTHING;

-- [V4__v2_extensions.sql]
-- default purchase approval flow
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('purchase_plan_default', '采购计划审批', 'purchase_plan')
ON CONFLICT (flow_code) DO NOTHING;

-- [V4__v2_extensions.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '科室主任', 'dept_admin', 0 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_plan_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

-- [V4__v2_extensions.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 2, '装备部审核', 'equipment_head', 10000 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_plan_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 2);

-- [V4__v2_extensions.sql]
-- assign admin role to demo admin user
UPDATE sys_user SET role_ids = ARRAY(SELECT id FROM sys_role WHERE role_code IN ('admin', 'tenant_admin') LIMIT 1)
WHERE username = 'admin' AND (role_ids IS NULL OR role_ids = '{}');

-- [V4__v2_extensions.sql]
UPDATE sys_role SET permissions = '{"menus":["*"],"buttons":["*"],"dataScope":"all"}'::jsonb
WHERE role_code = 'admin' AND permissions::text = '["*"]';

-- [V5__v2_deepening.sql]
UPDATE device_scrap SET approval_status = status WHERE approval_status IS NULL;

-- [V5__v2_deepening.sql]
UPDATE asset_transfer SET approval_status = status WHERE approval_status IS NULL;

-- [V5__v2_deepening.sql]
-- migrate notification_message -> sys_notification (V1 schema uses notification_type)
INSERT INTO sys_notification (title, content, notification_type, is_read, created_at)
SELECT title, content, COALESCE(message_type, 'system'), COALESCE(is_read, false), created_at
FROM notification_message nm
WHERE NOT EXISTS (
    SELECT 1 FROM sys_notification sn WHERE sn.title = nm.title AND sn.created_at = nm.created_at
);

-- [V5__v2_deepening.sql]
-- default approval flows
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('purchase_contract_default', '采购合同审批', 'purchase_contract'),
('asset_transfer_default', '资产流转审批', 'asset_transfer'),
('device_scrap_default', '设备报废审批', 'device_scrap'),
('device_outbound_default', '设备出库审批', 'device_outbound')
ON CONFLICT (flow_code) DO NOTHING;

-- [V5__v2_deepening.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '科室主任', 'dept_admin', 0 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_contract_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

-- [V5__v2_deepening.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 2, '装备部审核', 'equipment_head', 50000 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_contract_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 2);

-- [V5__v2_deepening.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '装备部审核', 'equipment_head', 0 FROM sys_approval_flow f WHERE f.flow_code IN ('asset_transfer_default','device_scrap_default','device_outbound_default')
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

-- [V5__v2_deepening.sql]
-- demo purchase plan seed
INSERT INTO purchase_plan (plan_code, plan_year, total_budget, justification, approval_status)
SELECT 'PP2026-001', 2026, 500000.00, '年度设备采购计划演示', 'draft'
WHERE NOT EXISTS (SELECT 1 FROM purchase_plan WHERE plan_code = 'PP2026-001');

-- [V6__system_rbac.sql]
-- Default warehouse for demo campus
INSERT INTO warehouse (warehouse_code, warehouse_name, campus_id, address)
SELECT 'WH01', '中心库房', c.id, '主院区设备科库房'
FROM campus c
WHERE c.campus_code = 'A'
  AND NOT EXISTS (SELECT 1 FROM warehouse w WHERE w.warehouse_code = 'WH01');

-- [V6__system_rbac.sql]
INSERT INTO warehouse (warehouse_code, warehouse_name, campus_id, address)
SELECT 'WH02', '备件库房', c.id, '主院区备件库'
FROM campus c
WHERE c.campus_code = 'A'
  AND NOT EXISTS (SELECT 1 FROM warehouse w WHERE w.warehouse_code = 'WH02');

-- [V6__system_rbac.sql]
-- Button permission dict entries
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_code, v.dict_label, v.dict_value, v.sort_order
FROM (VALUES
    ('button_perm', 'add', '新增', 'add', 1),
    ('button_perm', 'edit', '编辑', 'edit', 2),
    ('button_perm', 'delete', '删除', 'delete', 3),
    ('button_perm', 'export', '导出', 'export', 4),
    ('button_perm', 'import', '导入', 'import', 5),
    ('button_perm', 'approve', '审批', 'approve', 6),
    ('button_perm', 'print', '打印', 'print', 7)
) AS v(dict_type, dict_code, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_code = v.dict_code
);

-- [V6__system_rbac.sql]
-- Copy admin role permissions to admin user
UPDATE sys_user u
SET permissions = r.permissions,
    permission_mode = 'synced',
    updated_at = NOW()
FROM sys_role r
WHERE u.username = 'admin'
  AND r.role_code = 'admin'
  AND u.role_ids IS NOT NULL
  AND r.id = ANY(u.role_ids)
  AND u.permissions IS NULL;

-- [V6__system_rbac.sql]
-- Users with role but no permissions: copy from first role
UPDATE sys_user u
SET permissions = r.permissions,
    permission_mode = 'synced',
    updated_at = NOW()
FROM sys_role r
WHERE u.permissions IS NULL
  AND u.role_ids IS NOT NULL
  AND cardinality(u.role_ids) > 0
  AND r.id = u.role_ids[1];

-- [V6__system_rbac.sql]
-- Enforce single role: keep first only
UPDATE sys_user
SET role_ids = ARRAY[role_ids[1]]
WHERE role_ids IS NOT NULL AND cardinality(role_ids) > 1;

-- [V7__core_business_enhance.sql]
-- 业务字典种子
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('project_status', 'draft', '草稿', 'draft', 1),
('project_status', 'bidding', '招标中', 'bidding', 2),
('project_status', 'awarded', '已定标', 'awarded', 3),
('project_status', 'closed', '已关闭', 'closed', 4),
('contract_status', 'active', '生效', 'active', 1),
('contract_status', 'completed', '已完成', 'completed', 2),
('contract_status', 'terminated', '已终止', 'terminated', 3),
('acceptance_status', 'pending', '待验收', 'pending', 1),
('acceptance_status', 'passed', '验收通过', 'passed', 2),
('acceptance_status', 'failed', '验收不通过', 'failed', 3),
('payment_stage', 'advance', '预付款', 'advance', 1),
('payment_stage', 'delivery', '到货款', 'delivery', 2),
('payment_stage', 'acceptance', '验收款', 'acceptance', 3),
('payment_stage', 'warranty', '质保金', 'warranty', 4),
('payment_status', 'pending', '待付款', 'pending', 1),
('payment_status', 'paid', '已付款', 'paid', 2),
('entry_type', 'purchase', '采购入库', 'purchase', 1),
('entry_type', 'donation', '捐赠入库', 'donation', 2),
('entry_type', 'transfer_in', '调拨入库', 'transfer_in', 3),
('entry_status', 'draft', '草稿', 'draft', 1),
('entry_status', 'pending', '待验收', 'pending', 2),
('entry_status', 'completed', '已完成', 'completed', 3),
('outbound_status', 'draft', '草稿', 'draft', 1),
('outbound_status', 'issued', '已发放', 'issued', 2),
('transfer_type', 'dept', '科室内流转', 'dept', 1),
('transfer_type', 'campus', '院区间调拨', 'campus', 2),
('transfer_type', 'external', '院外调出', 'external', 3),
('transfer_status', 'pending', '待审批', 'pending', 1),
('transfer_status', 'approved', '已批准', 'approved', 2),
('transfer_status', 'completed', '已完成', 'completed', 3),
('check_type', 'annual', '年度盘点', 'annual', 1),
('check_type', 'spot', '抽盘', 'spot', 2),
('check_type', 'dept', '科室盘点', 'dept', 3),
('check_status', 'planning', '计划中', 'planning', 1),
('check_status', 'in_progress', '盘点中', 'in_progress', 2),
('check_status', 'completed', '已完成', 'completed', 3),
('condition_status', 'good', '良好', 'good', 1),
('condition_status', 'fair', '一般', 'fair', 2),
('condition_status', 'poor', '较差', 'poor', 3),
('scrap_type', 'obsolete', '技术淘汰', 'obsolete', 1),
('scrap_type', 'damaged', '损坏报废', 'damaged', 2),
('scrap_status', 'draft', '草稿', 'draft', 1),
('scrap_status', 'pending', '审批中', 'pending', 2),
('scrap_status', 'approved', '已批准', 'approved', 3),
('scrap_status', 'disposed', '已处置', 'disposed', 4),
('disposal_method', 'auction', '拍卖', 'auction', 1),
('disposal_method', 'recycle', '回收', 'recycle', 2),
('disposal_method', 'destroy', '销毁', 'destroy', 3),
('inspection_type', 'daily', '日常巡检', 'daily', 1),
('inspection_type', 'special', '专项巡检', 'special', 2),
('inspection_frequency', 'daily', '每日', 'daily', 1),
('inspection_frequency', 'weekly', '每周', 'weekly', 2),
('inspection_frequency', 'monthly', '每月', 'monthly', 3),
('plan_status', 'active', '进行中', 'active', 1),
('plan_status', 'completed', '已完成', 'completed', 2),
('inspection_status', 'pending', '待巡检', 'pending', 1),
('inspection_status', 'completed', '已完成', 'completed', 2),
('report_method', 'web', '网页报修', 'web', 1),
('report_method', 'phone', '电话报修', 'phone', 2),
('report_method', 'app', 'APP报修', 'app', 3),
('wo_status', 'reported', '已报修', 'reported', 1),
('wo_status', 'dispatched', '已派工', 'dispatched', 2),
('wo_status', 'in_progress', '维修中', 'in_progress', 3),
('wo_status', 'completed', '待验收', 'completed', 4),
('wo_status', 'accepted', '已验收', 'accepted', 5),
('wo_status', 'closed', '已关闭', 'closed', 6),
('verify_result', 'pass', '通过', 'pass', 1),
('verify_result', 'fail', '不通过', 'fail', 2),
('maintenance_level', 'daily', '日常保养', 'daily', 1),
('maintenance_level', 'level1', '一级保养', 'level1', 2),
('maintenance_level', 'level2', '二级保养', 'level2', 3),
('cycle_type', 'day', '按天', 'day', 1),
('cycle_type', 'week', '按周', 'week', 2),
('cycle_type', 'month', '按月', 'month', 3),
('cycle_type', 'year', '按年', 'year', 4),
('maintain_plan_status', 'active', '激活', 'active', 1),
('maintain_plan_status', 'paused', '暂停', 'paused', 2),
('maintain_plan_status', 'completed', '完成', 'completed', 3),
('maintain_result', 'pass', '合格', 'pass', 1),
('maintain_result', 'fail', '不合格', 'fail', 2),
('maintain_record_status', 'draft', '草稿', 'draft', 1),
('maintain_record_status', 'submitted', '已提交', 'submitted', 2)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- [V8__purchase_enhance.sql]
-- 字典
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('plan_type', 'annual', '年度计划', 'annual', 1),
('plan_type', 'supplement', '增补计划', 'supplement', 2),
('plan_type', 'emergency', '应急采购', 'emergency', 3),
('fund_source', 'fiscal', '财政资金', 'fiscal', 1),
('fund_source', 'self', '自筹资金', 'self', 2),
('fund_source', 'research', '科研经费', 'research', 3),
('fund_source', 'donation', '捐赠', 'donation', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- [V8__purchase_enhance.sql]
-- 付款审批流
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('contract_payment_default', '合同付款审批', 'contract_payment')
ON CONFLICT (flow_code) DO NOTHING;

-- [V8__purchase_enhance.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '科室主任', 'dept_admin', 0 FROM sys_approval_flow f WHERE f.flow_code = 'contract_payment_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

-- [V8__purchase_enhance.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 2, '装备部审核', 'equipment_head', 10000 FROM sys_approval_flow f WHERE f.flow_code = 'contract_payment_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 2);

-- [V8__purchase_enhance.sql]
-- 采购科角色补菜单
UPDATE sys_role SET permissions = '{"menus":["mod_purchase","purchase_plan","purchase_project","purchase_contract","purchase_supplier","purchase_category","purchase_acceptance"],"buttons":[],"dataScope":"dept"}'::jsonb
WHERE role_code = 'purchase_staff';

-- [V9__purchase_phase456.sql]
-- 采购方式字典补全
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('purchase_method', 'competitive_negotiation', '竞争性谈判', 'competitive_negotiation', 3),
('purchase_method', 'single_source', '单一来源', 'single_source', 4),
('purchase_method', 'framework', '框架协议', 'framework', 5),
('invoice_type', 'special', '增值税专用发票', 'special', 1),
('invoice_type', 'normal', '增值税普通发票', 'normal', 2),
('invoice_type', 'electronic', '电子发票', 'electronic', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- [V9__purchase_phase456.sql]
-- 采购项目审批流
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('purchase_project_default', '采购项目审批', 'purchase_project')
ON CONFLICT (flow_code) DO NOTHING;

-- [V9__purchase_phase456.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '科室主任', 'dept_admin', 0 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_project_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

-- [V9__purchase_phase456.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 2, '装备部审核', 'equipment_head', 50000 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_project_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 2);

-- [V9__purchase_phase456.sql]
-- 安装验收审批流
INSERT INTO sys_approval_flow (flow_code, flow_name, business_type) VALUES
('purchase_acceptance_default', '安装验收审批', 'purchase_acceptance')
ON CONFLICT (flow_code) DO NOTHING;

-- [V9__purchase_phase456.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 1, '设备科验收', 'equipment_head', 0 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_acceptance_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 1);

-- [V9__purchase_phase456.sql]
INSERT INTO sys_approval_node (flow_id, node_order, node_name, approver_role, amount_threshold)
SELECT f.id, 2, '临床科室确认', 'dept_admin', 0 FROM sys_approval_flow f WHERE f.flow_code = 'purchase_acceptance_default'
AND NOT EXISTS (SELECT 1 FROM sys_approval_node n WHERE n.flow_id = f.id AND n.node_order = 2);

-- [V9__purchase_phase456.sql]
-- 采购科角色补看板菜单
UPDATE sys_role SET permissions = '{"menus":["mod_purchase","purchase_plan","purchase_project","purchase_contract","purchase_supplier","purchase_category","purchase_acceptance","purchase_manufacturer","purchase_dashboard"],"buttons":[],"dataScope":"dept"}'::jsonb
WHERE role_code = 'purchase_staff';

-- [V10__purchase_phase789.sql]
-- 字典
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('large_equipment_class', 'class_a', '甲类', 'class_a', 1),
('large_equipment_class', 'class_b', '乙类', 'class_b', 2),
('contract_type', 'purchase', '采购合同', 'purchase', 1),
('contract_type', 'maintenance', '维保合同', 'maintenance', 2),
('contract_type', 'service', '服务合同', 'service', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- [V10__purchase_phase789.sql]
-- 回填已有数据业务链号
UPDATE purchase_plan SET business_chain_no = plan_code WHERE business_chain_no IS NULL;

-- [V10__purchase_phase789.sql]
UPDATE purchase_project pj SET business_chain_no = pl.business_chain_no
FROM purchase_plan pl WHERE pj.plan_id = pl.id AND pj.business_chain_no IS NULL;

-- [V10__purchase_phase789.sql]
UPDATE purchase_contract pc SET business_chain_no = pj.business_chain_no
FROM purchase_project pj WHERE pc.project_id = pj.id AND pc.business_chain_no IS NULL;

-- [V10__purchase_phase789.sql]
UPDATE purchase_acceptance pa SET business_chain_no = pc.business_chain_no
FROM purchase_contract pc WHERE pa.contract_id = pc.id AND pa.business_chain_no IS NULL;

-- [V10__purchase_phase789.sql]
UPDATE device_entry de SET business_chain_no = pc.business_chain_no
FROM purchase_contract pc WHERE de.contract_id = pc.id AND de.business_chain_no IS NULL;

-- [V10__purchase_phase789.sql]
UPDATE sys_role SET permissions = '{"menus":["mod_purchase","purchase_plan","purchase_project","purchase_contract","purchase_supplier","purchase_category","purchase_acceptance","purchase_manufacturer","purchase_dashboard","purchase_trace"],"buttons":[],"dataScope":"dept"}'::jsonb
WHERE role_code = 'purchase_staff';

-- [V11__purchase_phase101112.sql]
-- 字典
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('acceptance_check_result', 'pending', '待检', 'pending', 1),
('acceptance_check_result', 'passed', '合格', 'passed', 2),
('acceptance_check_result', 'failed', '不合格', 'failed', 3),
('acceptance_member_role', 'quality', '质控', 'quality', 1),
('acceptance_member_role', 'engineering', '工程', 'engineering', 2),
('acceptance_member_role', 'clinical', '临床', 'clinical', 3),
('acceptance_member_role', 'equipment', '设备科', 'equipment', 4),
('complaint_type', 'query', '质疑', 'query', 1),
('complaint_type', 'complaint', '投诉', 'complaint', 2),
('complaint_status', 'open', '处理中', 'open', 1),
('complaint_status', 'resolved', '已办结', 'resolved', 2),
('project_event_type', 'notice', '发布公告', 'notice', 1),
('project_event_type', 'bid_open', '开标', 'bid_open', 2),
('project_event_type', 'evaluation', '评标', 'evaluation', 3),
('project_event_type', 'award', '定标', 'award', 4),
('project_event_type', 'contract', '签约', 'contract', 5)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- [V11__purchase_phase101112.sql]
-- 采购预算报表菜单
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_report', 'mod_purchase', '预算执行', 'menu', '/purchase/report', 10)
ON CONFLICT (menu_code) DO NOTHING;

-- [V11__purchase_phase101112.sql]
INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, 'purchase_report' FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
ON CONFLICT DO NOTHING;

-- [V11__purchase_phase101112.sql]
INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', 'purchase_report'
FROM sys_menu WHERE menu_code = 'purchase_report'
ON CONFLICT DO NOTHING;

-- [V11__purchase_phase101112.sql]
UPDATE sys_role SET permissions = '{"menus":["mod_purchase","purchase_plan","purchase_project","purchase_contract","purchase_supplier","purchase_category","purchase_acceptance","purchase_manufacturer","purchase_dashboard","purchase_trace","purchase_report"],"buttons":[],"dataScope":"dept"}'::jsonb
WHERE role_code = 'purchase_staff';
