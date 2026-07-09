-- MEIS consolidated Flyway migration (auto-generated, do not split into per-feature files)
-- Categories: V1 tables | V2 extensions | V3 seed data

-- [V1__platform.sql]
-- demo tenant metadata (schema created by meis-tenant service)
INSERT INTO sys_tenant (id, tenant_code, tenant_name, schema_name, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'demo', '演示医院', 'tenant_demo', 'active')
ON CONFLICT (tenant_code) DO NOTHING;

-- [V2__platform_menu.sql]
-- packages
INSERT INTO sys_package (package_code, package_name, max_users, description) VALUES
('basic', '基础版', 50, '采购+资产基础'),
('standard', '标准版', 200, '全业务模块'),
('professional', '专业版', 500, '含效益分析'),
('flagship', '旗舰版', 2000, '全模块+集成')
ON CONFLICT (package_code) DO NOTHING;

-- [V2__platform_menu.sql]
-- menu catalog (modules + menus + key buttons)
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_dashboard', NULL, '工作台', 'module', '/dashboard', 1),
('dashboard', 'mod_dashboard', '工作台', 'menu', '/dashboard', 1),
('mod_purchase', NULL, '采购管理', 'module', NULL, 2),
('purchase_plan', 'mod_purchase', '采购计划', 'menu', '/purchase/plan', 1),
('purchase_project', 'mod_purchase', '采购项目', 'menu', '/purchase/project', 2),
('purchase_contract', 'mod_purchase', '采购合同', 'menu', '/purchase/contract', 3),
('mod_asset', NULL, '资产管理', 'module', NULL, 3),
('asset_device', 'mod_asset', '设备台账', 'menu', '/asset/device', 1),
('asset_entry', 'mod_asset', '设备入库', 'menu', '/asset/entry', 2),
('asset_outbound', 'mod_asset', '设备出库', 'menu', '/asset/outbound', 3),
('asset_transfer', 'mod_asset', '资产流转', 'menu', '/asset/transfer', 4),
('asset_inventory', 'mod_asset', '资产盘点', 'menu', '/asset/inventory', 5),
('asset_scrap', 'mod_asset', '设备报废', 'menu', '/asset/scrap', 6),
('mod_ops', NULL, '运维保障', 'module', NULL, 4),
('repair_workorder', 'mod_ops', '维修工单', 'menu', '/repair/workorder', 1),
('repair_engineer', 'mod_ops', '工程师', 'menu', '/repair/engineer', 2),
('repair_spare', 'mod_ops', '备件管理', 'menu', '/repair/spare', 3),
('maintain_template', 'mod_ops', '保养模板', 'menu', '/maintain/template', 4),
('maintain_plan', 'mod_ops', '保养计划', 'menu', '/maintain/plan', 5),
('maintain_record', 'mod_ops', '保养记录', 'menu', '/maintain/record', 6),
('mod_quality', NULL, '质控合规', 'module', NULL, 5),
('qc_risk', 'mod_quality', '风险评估', 'menu', '/qc/risk', 1),
('qc_adverse', 'mod_quality', '不良事件', 'menu', '/qc/adverse', 2),
('qc_metrology', 'mod_quality', '计量管理', 'menu', '/qc/metrology', 3),
('qc_performance', 'mod_quality', '性能检测', 'menu', '/qc/performance', 4),
('mcontract_list', 'mod_quality', '维保合同', 'menu', '/maintenance-contract/list', 5),
('mcontract_fulfillment', 'mod_quality', '履约记录', 'menu', '/maintenance-contract/fulfillment', 6),
('special_life', 'mod_quality', '生命支持', 'menu', '/special/life', 7),
('special_emergency', 'mod_quality', '应急设备', 'menu', '/special/emergency', 8),
('special_leased', 'mod_quality', '租赁设备', 'menu', '/special/leased', 9),
('mod_analytics', NULL, '数据决策', 'module', NULL, 6),
('analytics_benefit', 'mod_analytics', '效益分析', 'menu', '/analytics/benefit', 1),
('analytics_reports', 'mod_analytics', '统计报表', 'menu', '/analytics/reports', 2),
('mod_system', NULL, '系统管理', 'module', NULL, 7),
('system_campus', 'mod_system', '院区管理', 'menu', '/system/campus', 1),
('system_dept', 'mod_system', '科室管理', 'menu', '/system/dept', 2),
('system_user', 'mod_system', '用户管理', 'menu', '/system/user', 3),
('system_role', 'mod_system', '角色管理', 'menu', '/system/role', 4),
('system_dict', 'mod_system', '数据字典', 'menu', '/system/dict', 5),
('system_log', 'mod_system', '操作日志', 'menu', '/system/log', 6),
('system_approval', 'mod_system', '审批配置', 'menu', '/system/approval', 7)
ON CONFLICT (menu_code) DO NOTHING;

-- [V2__platform_menu.sql]
-- standard package = all tenant menus
INSERT INTO sys_package_menu (package_code, menu_code)
SELECT 'standard', menu_code FROM sys_menu
WHERE menu_type IN ('module','menu')
  AND menu_code NOT LIKE 'platform_%' AND menu_code <> 'mod_platform'
ON CONFLICT DO NOTHING;

-- [V2__platform_menu.sql]
INSERT INTO sys_package_menu (package_code, menu_code)
SELECT 'flagship', menu_code FROM sys_menu
WHERE menu_type IN ('module','menu')
  AND menu_code NOT LIKE 'platform_%' AND menu_code <> 'mod_platform'
ON CONFLICT DO NOTHING;

-- [V2__platform_menu.sql]
-- demo tenant: grant standard package menus
INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', pm.menu_code
FROM sys_package_menu pm WHERE pm.package_code = 'standard'
ON CONFLICT DO NOTHING;

-- [V2__platform_menu.sql]
UPDATE sys_tenant SET package_code = 'standard' WHERE tenant_code = 'demo';

-- [V2__platform_menu.sql]
-- platform admin seed
INSERT INTO platform_user (username, password_hash, real_name)
SELECT 'platform', '$2a$10$RFmAL1Ss2C.mxB.aT.Z9s.leTG8dX52WWn86CVk/grwDupZ92hcwO', '平台管理员'
WHERE NOT EXISTS (SELECT 1 FROM platform_user WHERE username = 'platform');

-- [V3__platform_admin_menu.sql]
-- Platform admin menus (not in sys_tenant_menu)
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_platform', NULL, '平台管理', 'module', NULL, 99),
('platform_tenant', 'mod_platform', '租户列表', 'menu', '/tenant/list', 1),
('platform_tenant_menu', 'mod_platform', '租户菜单授权', 'menu', '/platform/tenant-menu', 2),
('platform_package', 'mod_platform', '套餐管理', 'menu', '/platform/package', 3)
ON CONFLICT (menu_code) DO NOTHING;

-- [V4__purchase_menus.sql]
-- MEIS V8: 采购模块菜单补全（供应商、设备分类、安装验收）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_supplier', 'mod_purchase', '供应商管理', 'menu', '/purchase/supplier', 4),
('purchase_category', 'mod_purchase', '设备分类', 'menu', '/purchase/category', 5),
('purchase_acceptance', 'mod_purchase', '安装验收', 'menu', '/purchase/acceptance', 6)
ON CONFLICT (menu_code) DO NOTHING;

-- [V4__purchase_menus.sql]
INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('purchase_supplier', 'purchase_category', 'purchase_acceptance')
ON CONFLICT DO NOTHING;

-- [V4__purchase_menus.sql]
INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', m.menu_code
FROM sys_menu m
WHERE m.menu_code IN ('purchase_supplier', 'purchase_category', 'purchase_acceptance')
ON CONFLICT DO NOTHING;

-- [V5__purchase_phase456_menus.sql]
-- MEIS V9: 生产厂商菜单

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_manufacturer', 'mod_purchase', '生产厂商', 'menu', '/purchase/manufacturer', 7),
('purchase_dashboard', 'mod_purchase', '采购看板', 'menu', '/purchase/dashboard', 8)
ON CONFLICT (menu_code) DO NOTHING;

-- [V5__purchase_phase456_menus.sql]
INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('purchase_manufacturer', 'purchase_dashboard')
ON CONFLICT DO NOTHING;

-- [V5__purchase_phase456_menus.sql]
INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', m.menu_code
FROM sys_menu m
WHERE m.menu_code IN ('purchase_manufacturer', 'purchase_dashboard')
ON CONFLICT DO NOTHING;

-- [V6__purchase_trace_menu.sql]
-- MEIS V10: 采购业务追溯菜单

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_trace', 'mod_purchase', '业务追溯', 'menu', '/purchase/trace', 9)
ON CONFLICT (menu_code) DO NOTHING;

-- [V6__purchase_trace_menu.sql]
INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code = 'purchase_trace'
ON CONFLICT DO NOTHING;

-- [V6__purchase_trace_menu.sql]
INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', 'purchase_trace'
FROM sys_menu m WHERE m.menu_code = 'purchase_trace'
ON CONFLICT DO NOTHING;

-- [V7__purchase_report_menu.sql]
-- MEIS V11: 采购预算执行报表菜单

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_report', 'mod_purchase', '预算执行', 'menu', '/purchase/report', 10)
ON CONFLICT (menu_code) DO NOTHING;

-- [V7__purchase_report_menu.sql]
INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code = 'purchase_report'
ON CONFLICT DO NOTHING;

-- [V7__purchase_report_menu.sql]
INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', 'purchase_report'
FROM sys_menu m WHERE m.menu_code = 'purchase_report'
ON CONFLICT DO NOTHING;
