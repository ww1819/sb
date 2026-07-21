-- =============================================================================
-- public schema 数据更正 / 菜单目录（可重复迁移 R__）
-- =============================================================================
-- 槽位：R__data_fix.sql
-- 约定：
--   1. 建表 / 索引 → 只改 V1__tables.sql、V2__indexes.sql
--   2. 本文件：菜单目录幂等同步（INSERT ON CONFLICT / UPDATE）与数据更正
--   3. 已有表加列 → 单独一行 ALTER TABLE ... ADD COLUMN IF NOT EXISTS
--   4. 不要在本文件 COMMENT ON（注释在 V1/V4）；禁止再新建 V5+/V20+
-- =============================================================================

-- ---------- 菜单：基础模块（工作台 / 采购 / 资产 / 运维 / 质控 / 决策 / 系统） ----------
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_dashboard', NULL, '工作台', 'module', '/dashboard', 1),
('dashboard', 'mod_dashboard', '工作台', 'menu', '/dashboard', 1),
('mod_purchase', NULL, '采购管理', 'module', NULL, 3),
('purchase_plan', 'mod_purchase', '采购计划', 'menu', '/purchase/plan', 1),
('purchase_project', 'mod_purchase', '设备采购计划表', 'menu', '/purchase/project', 2),
('purchase_contract', 'mod_purchase', '采购合同', 'menu', '/purchase/contract', 3),
('mod_asset', NULL, '资产管理', 'module', NULL, 4),
('asset_device', 'mod_asset', '资产登记', 'menu', '/asset/device', 1),
('asset_entry', 'mod_asset', '设备入库', 'menu', '/asset/entry', 2),
('asset_outbound', 'mod_asset', '设备出库', 'menu', '/asset/outbound', 3),
('asset_transfer', 'mod_asset', '资产流转', 'menu', '/asset/transfer', 4),
('asset_inventory', 'mod_asset', '资产盘点', 'menu', '/asset/inventory', 5),
('asset_scrap', 'mod_asset', '设备报废', 'menu', '/asset/scrap', 6),
('mod_ops', NULL, '运维管理', 'module', NULL, 8),
('repair_workorder', 'mod_ops', '维修工单', 'menu', '/repair/workorder', 1),
('repair_engineer', 'mod_ops', '维修工程师管理', 'menu', '/repair/engineer', 2),
('repair_spare', 'mod_ops', '备件管理', 'menu', '/repair/spare', 3),
('maintain_template', 'mod_ops', '保养模板', 'menu', '/maintain/template', 4),
('maintain_plan', 'mod_ops', '保养计划', 'menu', '/maintain/plan', 5),
('maintain_record', 'mod_ops', '保养记录', 'menu', '/maintain/record', 6),
('mod_quality', NULL, '质控合规', 'module', NULL, 9),
('qc_risk', 'mod_quality', '风险评估', 'menu', '/qc/risk', 1),
('qc_adverse', 'mod_quality', '不良事件', 'menu', '/qc/adverse', 2),
('qc_metrology', 'mod_quality', '计量管理', 'menu', '/qc/metrology', 3),
('qc_performance', 'mod_quality', '性能检测', 'menu', '/qc/performance', 4),
('mcontract_list', 'mod_quality', '维保合同', 'menu', '/maintenance-contract/list', 5),
('mcontract_fulfillment', 'mod_quality', '履约记录', 'menu', '/maintenance-contract/fulfillment', 6),
('special_life', 'mod_quality', '生命支持', 'menu', '/special/life', 7),
('special_emergency', 'mod_quality', '应急设备', 'menu', '/special/emergency', 8),
('special_leased', 'mod_quality', '租赁设备', 'menu', '/special/leased', 9),
('mod_analytics', NULL, '数据决策', 'module', NULL, 10),
('analytics_benefit', 'mod_analytics', '效益分析', 'menu', '/analytics/benefit', 1),
('analytics_reports', 'mod_analytics', '统计报表', 'menu', '/analytics/reports', 2),
('mod_system', NULL, '系统管理', 'module', NULL, 13),
('system_campus', 'mod_system', '院区管理', 'menu', '/system/campus', 1),
('system_dept', 'mod_system', '科室管理', 'menu', '/system/dept', 2),
('system_user', 'mod_system', '用户管理', 'menu', '/system/user', 3),
('system_role', 'mod_system', '角色管理', 'menu', '/system/role', 4),
('system_dict', 'mod_system', '数据字典', 'menu', '/system/dict', 5),
('system_log', 'mod_system', '操作日志', 'menu', '/system/log', 6),
('system_approval', 'mod_system', '审批配置', 'menu', '/system/approval', 7)
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_supplier', 'mod_purchase', '供应商管理', 'menu', '/purchase/supplier', 4),
('purchase_category', 'mod_purchase', '设备分类', 'menu', '/purchase/category', 5),
('purchase_acceptance', 'mod_purchase', '安装验收', 'menu', '/purchase/acceptance', 6),
('purchase_manufacturer', 'mod_purchase', '生产厂商', 'menu', '/purchase/manufacturer', 7),
('purchase_dashboard', 'mod_purchase', '采购看板', 'menu', '/purchase/dashboard', 8),
('purchase_trace', 'mod_purchase', '业务追溯', 'menu', '/purchase/trace', 9),
('purchase_report', 'mod_purchase', '预算执行', 'menu', '/purchase/report', 10)
ON CONFLICT (menu_code) DO NOTHING;

-- ---------- from V5__dict_module_menus.sql ----------
-- MEIS 模块1：基础字典 — 独立菜单模块
-- 将供应商/厂商/68码从采购模块归并到基础字典，并补全科室/仓库/资产分类/财务分类/单位

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_dict', NULL, '基础字典', 'module', NULL, 2)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 3 WHERE menu_code = 'mod_purchase';
UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'mod_asset';
UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'mod_ops';
UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'mod_quality';
UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'mod_analytics';
UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'mod_system';

UPDATE sys_menu SET parent_code = 'mod_dict', path = '/dict/supplier', menu_name = '供应商维护', sort_order = 1
WHERE menu_code = 'purchase_supplier';
UPDATE sys_menu SET parent_code = 'mod_dict', path = '/dict/manufacturer', menu_name = '生产厂家维护', sort_order = 2
WHERE menu_code = 'purchase_manufacturer';
UPDATE sys_menu SET parent_code = 'mod_dict', path = '/dict/category', menu_name = '设备68档案', sort_order = 3
WHERE menu_code = 'purchase_category';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('dict_asset_category', 'mod_dict', '资产分类', 'menu', '/dict/asset-category', 4),
('dict_finance_category', 'mod_dict', '财务分类', 'menu', '/dict/finance-category', 5),
('dict_dept', 'mod_dict', '科室维护', 'menu', '/dict/dept', 6),
('dict_warehouse', 'mod_dict', '仓库维护', 'menu', '/dict/warehouse', 7),
('dict_unit', 'mod_dict', '单位维护', 'menu', '/dict/unit', 8)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'system_dept';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_dict',
    'purchase_supplier', 'purchase_manufacturer', 'purchase_category',
    'dict_asset_category', 'dict_finance_category', 'dict_dept', 'dict_warehouse', 'dict_unit'
)
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_dict',
    'purchase_supplier', 'purchase_manufacturer', 'purchase_category',
    'dict_asset_category', 'dict_finance_category', 'dict_dept', 'dict_warehouse', 'dict_unit'
)
ON CONFLICT DO NOTHING;

-- ---------- from V6__asset_ledger_menus.sql ----------
-- 模块2：资产管理 — 菜单（登记 / 综合查询 / 导入）；终态分组见文末 AST-UI-05
UPDATE sys_menu SET menu_name = '资产管理' WHERE menu_code = 'mod_asset';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('asset_query', 'mod_asset', '资产综合查询', 'menu', '/asset/query', 2),
('asset_import', 'mod_asset', '资产导入', 'menu', '/asset/import', 3)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET menu_name = '资产登记', sort_order = 1 WHERE menu_code = 'asset_device';
UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'asset_entry';
UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'asset_outbound';
UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'asset_transfer';
UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'asset_inventory';
UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'asset_scrap';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('asset_query', 'asset_import')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('asset_query', 'asset_import')
ON CONFLICT DO NOTHING;

-- ---------- from V7__repair_module_menus.sql ----------
-- 模块3：维修管理 — 菜单拆分；终态挂在 ops_repair 分组下见文末 REP-UI-01

UPDATE sys_menu SET menu_name = '运维管理' WHERE menu_code = 'mod_ops';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('repair_apply', 'mod_ops', '报修申请', 'menu', '/repair/apply', 1),
('repair_handle', 'mod_ops', '维修处理', 'menu', '/repair/handle', 2),
('repair_spare_archive', 'mod_ops', '配件档案管理', 'menu', '/repair/spare-archive', 3),
('repair_verify', 'mod_ops', '维修验收', 'menu', '/repair/verify', 4),
('repair_fault', 'mod_ops', '故障库', 'menu', '/repair/fault', 5),
('repair_process_type', 'mod_ops', '维修进程类型', 'menu', '/repair/process-type', 6),
('repair_engineer', 'mod_ops', '维修工程师管理', 'menu', '/repair/engineer', 7)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    is_active = TRUE;
-- 注意：parent_code / sort_order 由文末 REP-UI-01 块定为 ops_repair 下 1~7，此处勿覆盖 parent_code

UPDATE sys_menu SET is_active = FALSE WHERE menu_code IN ('repair_workorder', 'repair_spare');

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('repair_apply', 'repair_handle', 'repair_spare_archive', 'repair_verify', 'repair_fault')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('repair_apply', 'repair_handle', 'repair_spare_archive', 'repair_verify', 'repair_fault')
ON CONFLICT DO NOTHING;

-- ---------- from V8__maintain_module_menus.sql ----------
-- 模块4：保养管理 — 菜单重组（参数/计划/执行/记录查询）；终态挂在 ops_maintain 分组下见文末 MT-UI-01

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('maintain_param', 'mod_ops', '保养参数设置', 'menu', '/maintain/param', 7),
('maintain_execution', 'mod_ops', '保养执行', 'menu', '/maintain/execution', 9),
('maintain_query', 'mod_ops', '保养记录查询', 'menu', '/maintain/query', 10)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    is_active = TRUE;
-- 注意：parent_code / sort_order 由文末 MT-UI-01 块定为 ops_maintain 下 1~4，此处勿覆盖 parent_code

-- 注意：V8 历史块勿覆盖 maintain_plan 的 parent；终态 sort 见文末
UPDATE sys_menu SET menu_name = '保养计划', path = '/maintain/plan', is_active = TRUE
WHERE menu_code = 'maintain_plan';
UPDATE sys_menu SET is_active = FALSE WHERE menu_code IN ('maintain_template', 'maintain_record');

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('maintain_param', 'maintain_plan', 'maintain_execution', 'maintain_query')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('maintain_param', 'maintain_plan', 'maintain_execution', 'maintain_query')
ON CONFLICT DO NOTHING;

-- ---------- from V9__inspection_module_menus.sql ----------
-- 模块5：巡检管理 — 菜单重组；终态挂在 ops_inspect 分组下见文末 INS-UI-01

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('inspect_param', 'mod_ops', '巡检参数设置', 'menu', '/inspect/param', 12),
('inspect_plan', 'mod_ops', '巡检计划', 'menu', '/inspect/plan', 13),
('inspect_execution', 'mod_ops', '巡检执行', 'menu', '/inspect/execution', 14),
('inspect_query', 'mod_ops', '巡检记录查询', 'menu', '/inspect/query', 15)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    is_active = TRUE;
-- 注意：parent_code / sort_order 由文末 INS-UI-01 块定为 ops_inspect 下 1~4

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('inspect_param', 'inspect_plan', 'inspect_execution', 'inspect_query')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('inspect_param', 'inspect_plan', 'inspect_execution', 'inspect_query')
ON CONFLICT DO NOTHING;

-- ---------- from V10__metrology_module_menus.sql ----------
-- 模块6：计量管理 — 菜单重组；终态挂在 ops_metrology 分组下见文末 MET-UI-01

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('metrology_param', 'mod_ops', '计量参数设置', 'menu', '/metrology/param', 17),
('metrology_plan', 'mod_ops', '计量计划', 'menu', '/metrology/plan', 18),
('metrology_execution', 'mod_ops', '计量执行', 'menu', '/metrology/execution', 19),
('metrology_query', 'mod_ops', '计量记录查询', 'menu', '/metrology/query', 20)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    is_active = TRUE;
-- 注意：parent_code / sort_order 由文末 MET-UI-01 块定为 ops_metrology 下 1~4

UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'qc_metrology';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('metrology_param', 'metrology_plan', 'metrology_execution', 'metrology_query')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('metrology_param', 'metrology_plan', 'metrology_execution', 'metrology_query')
ON CONFLICT DO NOTHING;

-- ---------- from V11__purchase_module_menus.sql ----------
-- 模块7：采购管理 — 菜单对齐（申请/审批/合同/验收）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_apply', 'mod_purchase', '采购申请', 'menu', '/purchase/apply', 1),
('purchase_approval', 'mod_purchase', '采购审批', 'menu', '/purchase/approval', 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_bidding', 'mod_purchase', '招标管理', 'menu', '/purchase/bidding', 4)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET menu_name = '设备合同管理', sort_order = 5 WHERE menu_code = 'purchase_contract';
UPDATE sys_menu SET menu_name = '安装验收', sort_order = 6 WHERE menu_code = 'purchase_acceptance';
UPDATE sys_menu SET menu_name = '设备采购计划表', sort_order = 3 WHERE menu_code = 'purchase_project';
UPDATE sys_menu SET menu_name = '招标管理', sort_order = 4 WHERE menu_code = 'purchase_bidding';
UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'purchase_plan';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('purchase_apply', 'purchase_approval')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('purchase_apply', 'purchase_approval')
ON CONFLICT DO NOTHING;

-- ---------- from V12__warehouse_module_menus.sql ----------
-- 模块8：库房管理 — 独立菜单模块

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_warehouse', NULL, '库房管理', 'module', NULL, 4)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'mod_asset';
UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'mod_ops';
UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'mod_quality';
UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'mod_analytics';
UPDATE sys_menu SET sort_order = 9 WHERE menu_code = 'mod_system';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('warehouse_setting', 'mod_warehouse', '库房维护', 'menu', '/warehouse/setting', 1),
('warehouse_entry', 'mod_warehouse', '设备入库', 'menu', '/warehouse/entry', 2),
('warehouse_outbound', 'mod_warehouse', '设备出库', 'menu', '/warehouse/outbound', 3),
('warehouse_return', 'mod_warehouse', '设备退货', 'menu', '/warehouse/return', 4),
('warehouse_transfer', 'mod_warehouse', '库房调拨', 'menu', '/warehouse/transfer', 5),
('warehouse_inventory', 'mod_warehouse', '库存盘点', 'menu', '/warehouse/inventory', 6),
('warehouse_scrap', 'mod_warehouse', '设备报废', 'menu', '/warehouse/scrap', 7)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

-- 与库房管理重复的资产侧出入库/调拨/盘点/报废：停用菜单（入口保留在 mod_warehouse）
UPDATE sys_menu SET is_active = FALSE WHERE menu_code IN (
    'asset_entry', 'asset_outbound', 'asset_transfer', 'asset_inventory', 'asset_scrap'
);
-- 仓库主数据入口统一在「基础字典 → 仓库维护」(dict_warehouse)，停用库房侧重复菜单
UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'warehouse_setting';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_warehouse',
    'warehouse_entry', 'warehouse_goods_return', 'asset_stock_query', 'warehouse_outbound', 'warehouse_return',
    'warehouse_transfer', 'warehouse_inventory', 'warehouse_scrap'
)
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_warehouse',
    'warehouse_entry', 'warehouse_goods_return', 'asset_stock_query', 'warehouse_outbound', 'warehouse_return',
    'warehouse_transfer', 'warehouse_inventory', 'warehouse_scrap'
)
ON CONFLICT DO NOTHING;

-- ---------- from V13__adverse_module_menus.sql ----------
-- 模块9：不良事件 — 菜单对齐（上报/查询）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('qc_adverse_report', 'mod_quality', '不良事件上报', 'menu', '/qc/adverse/report', 2),
('qc_adverse_query', 'mod_quality', '不良事件查询', 'menu', '/qc/adverse/query', 3)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 1 WHERE menu_code = 'qc_risk';
UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'qc_performance';
UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'mcontract_list';
UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'mcontract_fulfillment';
UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'special_life';
UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'special_emergency';
UPDATE sys_menu SET sort_order = 9 WHERE menu_code = 'special_leased';
UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'qc_adverse';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('qc_adverse_report', 'qc_adverse_query')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('qc_adverse_report', 'qc_adverse_query')
ON CONFLICT DO NOTHING;

-- ---------- from V14__special_module_menus.sql ----------
-- 模块10：特种设备 — 独立菜单模块

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_special', NULL, '特种设备', 'module', NULL, 6)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'mod_ops';
UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'mod_quality';
UPDATE sys_menu SET sort_order = 9 WHERE menu_code = 'mod_analytics';
UPDATE sys_menu SET sort_order = 10 WHERE menu_code = 'mod_system';

UPDATE sys_menu SET parent_code = 'mod_special', menu_name = '生命支持设备', path = '/special/life', sort_order = 1
WHERE menu_code = 'special_life';
UPDATE sys_menu SET parent_code = 'mod_special', menu_name = '应急设备库', path = '/special/emergency', sort_order = 2
WHERE menu_code = 'special_emergency';
UPDATE sys_menu SET parent_code = 'mod_special', menu_name = '租赁设备', path = '/special/leased', sort_order = 4
WHERE menu_code = 'special_leased';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('special_radiation', 'mod_special', '特种设备登记', 'menu', '/special/radiation', 3),
('special_alerts', 'mod_special', '证照到期提醒', 'menu', '/special/alerts', 5)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('mod_special', 'special_radiation', 'special_alerts')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('mod_special', 'special_radiation', 'special_alerts')
ON CONFLICT DO NOTHING;

-- ---------- from V15__shared_module_menus.sql ----------
-- 模块11：调配中心（原「公用设备借调」/「借调中心」）— 独立菜单模块

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_shared', NULL, '调配中心', 'module', NULL, 7)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'mod_ops';
UPDATE sys_menu SET sort_order = 9 WHERE menu_code = 'mod_quality';
UPDATE sys_menu SET sort_order = 10 WHERE menu_code = 'mod_analytics';
UPDATE sys_menu SET sort_order = 11 WHERE menu_code = 'mod_system';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('shared_device', 'mod_shared', '公用设备管理', 'menu', '/shared/device', 1),
('shared_loan', 'mod_shared', '借调申请', 'menu', '/shared/loan', 2),
('shared_loan_approve', 'mod_shared', '借调审批', 'menu', '/shared/loan-approve', 3),
('shared_return', 'mod_shared', '归还申请', 'menu', '/shared/return', 4),
('shared_return_approve', 'mod_shared', '归还审批', 'menu', '/shared/return-approve', 5),
('shared_fee', 'mod_shared', '借调收费', 'menu', '/shared/fee', 6),
('shared_record', 'mod_shared', '借调记录查询', 'menu', '/shared/record', 7)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_shared', 'shared_device', 'shared_loan', 'shared_loan_approve',
    'shared_return', 'shared_return_approve', 'shared_fee', 'shared_record'
)
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_shared', 'shared_device', 'shared_loan', 'shared_loan_approve',
    'shared_return', 'shared_return_approve', 'shared_fee', 'shared_record'
)
ON CONFLICT DO NOTHING;

-- ---------- from V16__pm_module_menus.sql ----------
-- 模块12：预防性维护 — 菜单（挂质控合规 mod_quality）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('pm_param', 'mod_quality', '预防性维护参数', 'menu', '/pm/param', 10),
('pm_plan', 'mod_quality', '预防性维护计划', 'menu', '/pm/plan', 11),
('pm_execution', 'mod_quality', '预防性维护执行', 'menu', '/pm/execution', 12),
('pm_query', 'mod_quality', '预防性维护记录', 'menu', '/pm/query', 13)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('pm_param', 'pm_plan', 'pm_execution', 'pm_query')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('pm_param', 'pm_plan', 'pm_execution', 'pm_query')
ON CONFLICT DO NOTHING;

-- ---------- from V17__benefit_module_menus.sql ----------
-- 模块13：效益分析 — 菜单（挂数据决策 mod_analytics）

UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'analytics_benefit';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('analytics_mapping', 'mod_analytics', '对照管理', 'menu', '/analytics/mapping', 1),
('analytics_sync', 'mod_analytics', '数据抓取', 'menu', '/analytics/sync', 2),
('analytics_summary', 'mod_analytics', '效益分析汇总', 'menu', '/analytics/summary', 3),
('analytics_cost', 'mod_analytics', '成本上报', 'menu', '/analytics/cost', 4),
('analytics_device', 'mod_analytics', '单机效益分析', 'menu', '/analytics/device', 5)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship'), ('professional')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('analytics_mapping', 'analytics_sync', 'analytics_summary', 'analytics_cost', 'analytics_device')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('analytics_mapping', 'analytics_sync', 'analytics_summary', 'analytics_cost', 'analytics_device')
ON CONFLICT DO NOTHING;

-- ---------- from V18__power_module_menus.sql ----------
-- 模块14：电流监测 — 历史独立模块；终态改为运维下二级分组见文末 PWR-UI-01

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_power', NULL, '电流监测', 'module', NULL, 11)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    is_active = TRUE;
-- 注意：parent_code / menu_type / sort_order 由文末 PWR-UI-01 定为 mod_ops 下 group

UPDATE sys_menu SET sort_order = 12 WHERE menu_code = 'mod_system';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('power_station', 'mod_power', '基站维护', 'menu', '/power/station', 1),
('power_tag', 'mod_power', '标签维护', 'menu', '/power/tag', 2),
('power_status', 'mod_power', '设备运行状态', 'menu', '/power/status', 3),
('power_stats', 'mod_power', '设备运行统计', 'menu', '/power/stats', 4),
('power_record', 'mod_power', '监测记录', 'menu', '/power/record', 5)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship'), ('professional')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('mod_power', 'power_station', 'power_tag', 'power_status', 'power_stats', 'power_record')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('mod_power', 'power_station', 'power_tag', 'power_status', 'power_stats', 'power_record')
ON CONFLICT DO NOTHING;

-- ---------- from V19__screen_module_menus.sql ----------
-- 模块15：设备大屏 — 独立菜单模块

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_screen', NULL, '设备大屏', 'module', NULL, 12)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 13 WHERE menu_code = 'mod_system';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('screen_equipment', 'mod_screen', '设备运营大屏', 'menu', '/screen/equipment', 1)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('screen_warehouse_twin', 'mod_screen', '数字孪生大屏', 'menu', '/screen/warehouse-twin', 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship'), ('professional')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('mod_screen', 'screen_equipment', 'screen_warehouse_twin')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('mod_screen', 'screen_equipment', 'screen_warehouse_twin')
ON CONFLICT DO NOTHING;

-- ---------- 套餐 / 租户菜单授权（标准版、旗舰版、专业版） ----------

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT 'standard', menu_code FROM sys_menu
WHERE menu_type IN ('module','menu','group')
  AND menu_code NOT LIKE 'platform_%' AND menu_code <> 'mod_platform'
  AND is_active = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT 'flagship', menu_code FROM sys_menu
WHERE menu_type IN ('module','menu','group')
  AND menu_code NOT LIKE 'platform_%' AND menu_code <> 'mod_platform'
  AND is_active = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT 'professional', menu_code FROM sys_menu
WHERE menu_type IN ('module','menu')
  AND menu_code NOT LIKE 'platform_%' AND menu_code <> 'mod_platform'
  AND is_active = TRUE
  AND menu_code IN (
    'mod_analytics', 'analytics_mapping', 'analytics_sync', 'analytics_summary',
    'analytics_cost', 'analytics_device', 'mod_screen', 'screen_equipment', 'screen_warehouse_twin',
    'mod_power', 'power_station', 'power_tag', 'power_status', 'power_stats', 'power_record'
  )
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, pm.menu_code
FROM sys_tenant t
JOIN sys_package_menu pm ON pm.package_code = COALESCE(t.package_code, 'standard')
WHERE t.status = 'active'
  AND pm.menu_code NOT LIKE 'platform_%' AND pm.menu_code <> 'mod_platform'
ON CONFLICT DO NOTHING;

-- ---------- 附录 O：停用独立「设备管理」子菜单（设备主数据统一在资产台账） ----------
UPDATE sys_menu SET is_active = FALSE
WHERE menu_code IN ('maintain_device', 'inspect_device', 'metrology_device', 'pm_device');

DELETE FROM sys_package_menu
WHERE menu_code IN ('maintain_device', 'inspect_device', 'metrology_device', 'pm_device');

DELETE FROM sys_tenant_menu
WHERE menu_code IN ('maintain_device', 'inspect_device', 'metrology_device', 'pm_device');

-- ---------- 系统管理：仅保留账号/权限/配置类；主数据归基础字典 ----------
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('system_config', 'mod_system', '系统配置', 'menu', '/system/config', 6)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship'), ('professional')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code = 'system_config'
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code = 'system_config'
ON CONFLICT DO NOTHING;

-- 院区/供应商/设备分类/生产厂商 → 基础字典（与 dict_* 主数据并列）
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '院区管理', path = '/dict/campus', sort_order = 1, is_active = TRUE
WHERE menu_code = 'system_campus';
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '仓库维护', path = '/dict/warehouse', sort_order = 2, is_active = TRUE
WHERE menu_code = 'dict_warehouse';
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '科室维护', path = '/dict/dept', sort_order = 3, is_active = TRUE
WHERE menu_code = 'dict_dept';
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '供应商管理', path = '/dict/supplier', sort_order = 4, is_active = TRUE
WHERE menu_code = 'purchase_supplier';
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '生产厂商', path = '/dict/manufacturer', sort_order = 5, is_active = TRUE
WHERE menu_code = 'purchase_manufacturer';
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '设备分类', path = '/dict/category', sort_order = 6, is_active = TRUE
WHERE menu_code = 'purchase_category';
UPDATE sys_menu SET sort_order = 7, is_active = TRUE WHERE menu_code = 'dict_asset_category';
UPDATE sys_menu SET sort_order = 8, is_active = TRUE WHERE menu_code = 'dict_finance_category';
UPDATE sys_menu SET sort_order = 9, is_active = TRUE WHERE menu_code = 'dict_unit';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship'), ('professional')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code = 'system_campus'
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code = 'system_campus'
ON CONFLICT DO NOTHING;

-- ---------- 库存查询：迁至库房管理，挂在设备入库（备货入库）之后 ----------
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('asset_stock_query', 'mod_warehouse', '库存查询', 'menu', '/asset/stock', 3)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

-- AST-UI-03→05：资产管理二级分组终态（资产增减 / 资产查询）
UPDATE sys_menu SET menu_name = '资产管理', is_active = TRUE WHERE menu_code = 'mod_asset';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('asset_change', 'mod_asset', '资产增减', 'group', NULL, 1),
('asset_query_group', 'mod_asset', '资产查询', 'group', NULL, 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET parent_code = 'asset_change', menu_name = '资产登记', path = '/asset/device',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'asset_device';
UPDATE sys_menu SET parent_code = 'asset_change', menu_name = '资产导入', path = '/asset/import',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'asset_import';
UPDATE sys_menu SET parent_code = 'asset_change', menu_name = '库房调拨', path = '/warehouse/transfer',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'warehouse_transfer';
UPDATE sys_menu SET parent_code = 'asset_query_group', menu_name = '资产综合查询', path = '/asset/query',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'asset_query';
-- 设备入库/出库等与库房管理重复：保持停用（调拨在 asset_change；报废见 AST-UI-07）
UPDATE sys_menu SET parent_code = 'mod_asset', sort_order = 4, is_active = FALSE WHERE menu_code = 'asset_entry';
UPDATE sys_menu SET parent_code = 'mod_asset', sort_order = 5, is_active = FALSE WHERE menu_code = 'asset_outbound';
UPDATE sys_menu SET parent_code = 'mod_asset', sort_order = 6, is_active = FALSE WHERE menu_code = 'asset_transfer';
UPDATE sys_menu SET parent_code = 'mod_asset', sort_order = 7, is_active = FALSE WHERE menu_code = 'asset_inventory';
UPDATE sys_menu SET parent_code = 'mod_asset', sort_order = 8, is_active = FALSE WHERE menu_code = 'asset_scrap';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('asset_change', 'asset_query_group')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('asset_change', 'asset_query_group')
ON CONFLICT DO NOTHING;

-- AST-UI-04 / SHR-UI-01→02：调配中心更名；库存查询迁库房（入库后）
UPDATE sys_menu SET menu_name = '调配中心' WHERE menu_code = 'mod_shared';
UPDATE sys_menu SET parent_code = 'mod_warehouse', menu_name = '库存查询', path = '/asset/stock',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'asset_stock_query';
UPDATE sys_menu SET sort_order = 2, is_active = TRUE WHERE menu_code = 'warehouse_entry';
UPDATE sys_menu SET sort_order = 5, is_active = TRUE WHERE menu_code = 'warehouse_outbound';
UPDATE sys_menu SET menu_name = '设备退库', sort_order = 6, is_active = TRUE WHERE menu_code = 'warehouse_return';
UPDATE sys_menu SET sort_order = 7, is_active = TRUE WHERE menu_code = 'warehouse_inventory';
-- warehouse_transfer / warehouse_scrap 终态挂资产增减，见 AST-UI-05/06（勿在此写回 mod_warehouse）
-- 系统管理侧「仓库维护」：保留菜单定义但不启用（与基础字典 /dict/warehouse 重复）
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order, is_active) VALUES
('system_warehouse', 'mod_system', '仓库维护', 'menu', '/system/warehouse', 2, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = FALSE;

-- 系统管理：用户/角色/字典值/日志/审批/配置
UPDATE sys_menu SET sort_order = 1 WHERE menu_code = 'system_user';
UPDATE sys_menu SET sort_order = 2 WHERE menu_code = 'system_role';
UPDATE sys_menu SET sort_order = 3 WHERE menu_code = 'system_dict';
UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'system_log';
UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'system_approval';
UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'system_config';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship'), ('professional')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('asset_stock_query')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('asset_stock_query')
ON CONFLICT DO NOTHING;

-- 仓库主数据：仅「基础字典 → 仓库维护」生效
UPDATE sys_menu SET is_active = TRUE WHERE menu_code = 'dict_warehouse';
UPDATE sys_menu SET is_active = FALSE WHERE menu_code IN ('warehouse_setting', 'system_warehouse');

-- 再次确保四项主数据挂在基础字典，并按定稿顺序排列
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '院区管理', path = '/dict/campus', sort_order = 1, is_active = TRUE
WHERE menu_code = 'system_campus';
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '仓库维护', path = '/dict/warehouse', sort_order = 2, is_active = TRUE
WHERE menu_code = 'dict_warehouse';
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '科室维护', path = '/dict/dept', sort_order = 3, is_active = TRUE
WHERE menu_code = 'dict_dept';
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '供应商管理', path = '/dict/supplier', sort_order = 4, is_active = TRUE
WHERE menu_code = 'purchase_supplier';
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '生产厂商', path = '/dict/manufacturer', sort_order = 5, is_active = TRUE
WHERE menu_code = 'purchase_manufacturer';
UPDATE sys_menu SET parent_code = 'mod_dict', menu_name = '设备分类', path = '/dict/category', sort_order = 6, is_active = TRUE
WHERE menu_code = 'purchase_category';
UPDATE sys_menu SET sort_order = 7, is_active = TRUE WHERE menu_code = 'dict_asset_category';
UPDATE sys_menu SET sort_order = 8, is_active = TRUE WHERE menu_code = 'dict_finance_category';
UPDATE sys_menu SET sort_order = 9, is_active = TRUE WHERE menu_code = 'dict_unit';


-- ---------- SHR-UI-02 / MT-UI-01：调配中心更名；运维→保养管理二级分组 ----------
UPDATE sys_menu SET menu_name = '调配中心' WHERE menu_code = 'mod_shared';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('ops_maintain', 'mod_ops', '保养管理', 'group', NULL, 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET parent_code = 'ops_maintain', menu_name = '保养参数设置', path = '/maintain/param',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'maintain_param';
UPDATE sys_menu SET parent_code = 'ops_maintain', menu_name = '保养计划', path = '/maintain/plan',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'maintain_plan';
UPDATE sys_menu SET parent_code = 'ops_maintain', menu_name = '保养执行', path = '/maintain/execution',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'maintain_execution';
UPDATE sys_menu SET parent_code = 'ops_maintain', menu_name = '保养记录查询', path = '/maintain/query',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'maintain_query';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code = 'ops_maintain'
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code = 'ops_maintain'
ON CONFLICT DO NOTHING;

-- ---------- INS-UI-01 / MET-UI-01：巡检挂保养下（四级）；计量管理仍为运维二级 ----------
-- 终态：运维 → 保养管理 → 巡检管理 → 巡检*；运维 → 计量管理 → 计量*
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('ops_inspect', 'ops_maintain', '巡检管理', 'group', NULL, 5),
('ops_metrology', 'mod_ops', '计量管理', 'group', NULL, 3)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET parent_code = 'ops_inspect', menu_name = '巡检参数设置', path = '/inspect/param',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'inspect_param';
UPDATE sys_menu SET parent_code = 'ops_inspect', menu_name = '巡检计划', path = '/inspect/plan',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'inspect_plan';
UPDATE sys_menu SET parent_code = 'ops_inspect', menu_name = '巡检执行', path = '/inspect/execution',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'inspect_execution';
UPDATE sys_menu SET parent_code = 'ops_inspect', menu_name = '巡检记录查询', path = '/inspect/query',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'inspect_query';

UPDATE sys_menu SET parent_code = 'ops_metrology', menu_name = '计量参数设置', path = '/metrology/param',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'metrology_param';
UPDATE sys_menu SET parent_code = 'ops_metrology', menu_name = '计量计划', path = '/metrology/plan',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'metrology_plan';
UPDATE sys_menu SET parent_code = 'ops_metrology', menu_name = '计量执行', path = '/metrology/execution',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'metrology_execution';
UPDATE sys_menu SET parent_code = 'ops_metrology', menu_name = '计量记录查询', path = '/metrology/query',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'metrology_query';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('ops_inspect', 'ops_metrology')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('ops_inspect', 'ops_metrology')
ON CONFLICT DO NOTHING;

-- ---------- REP-UI-01 / PWR-UI-01：运维下维修管理置顶；电流监测迁入运维（计量后） ----------
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('ops_repair', 'mod_ops', '维修管理', 'group', NULL, 1)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET parent_code = 'ops_repair', menu_name = '报修申请', path = '/repair/apply',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'repair_apply';
UPDATE sys_menu SET parent_code = 'ops_repair', menu_name = '维修处理', path = '/repair/handle',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'repair_handle';
UPDATE sys_menu SET parent_code = 'ops_repair', menu_name = '配件档案管理', path = '/repair/spare-archive',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'repair_spare_archive';
UPDATE sys_menu SET parent_code = 'ops_repair', menu_name = '维修验收', path = '/repair/verify',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'repair_verify';
UPDATE sys_menu SET parent_code = 'ops_repair', menu_name = '故障库', path = '/repair/fault',
    sort_order = 5, is_active = TRUE
WHERE menu_code = 'repair_fault';
UPDATE sys_menu SET parent_code = 'ops_repair', menu_name = '维修进程类型', path = '/repair/process-type',
    sort_order = 6, is_active = TRUE
WHERE menu_code = 'repair_process_type';
UPDATE sys_menu SET parent_code = 'ops_repair', menu_name = '维修工程师管理', path = '/repair/engineer',
    sort_order = 7, is_active = TRUE
WHERE menu_code = 'repair_engineer';

-- 电流监测：由一级模块改为运维下二级分组（仍用 menu_code=mod_power，子菜单不变）
UPDATE sys_menu SET parent_code = 'mod_ops', menu_name = '电流监测', menu_type = 'group',
    path = NULL, sort_order = 4, is_active = TRUE
WHERE menu_code = 'mod_power';

-- 运维二级分组顺序：维修 → 保养（含巡检） → 计量 → 电流监测
UPDATE sys_menu SET sort_order = 1, is_active = TRUE WHERE menu_code = 'ops_repair';
UPDATE sys_menu SET sort_order = 2, is_active = TRUE WHERE menu_code = 'ops_maintain';
UPDATE sys_menu SET parent_code = 'ops_maintain', sort_order = 5, is_active = TRUE WHERE menu_code = 'ops_inspect';
UPDATE sys_menu SET sort_order = 3, is_active = TRUE WHERE menu_code = 'ops_metrology';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code = 'ops_repair'
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code = 'ops_repair'
ON CONFLICT DO NOTHING;

-- ---------- NAV-UI-01：一级模块顺序 — 库房管理在资产管理之上 ----------
UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'mod_warehouse';
UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'mod_asset';

-- ---------- WH-UI-01 / AST-UI-06：库房子菜单 — 入库→退货→库存查询→出库→退库→盘点 ----------
-- 调拨/报废已迁至资产管理→资产增减（见下文 AST-UI-06 终态再确认）
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('warehouse_goods_return', 'mod_warehouse', '设备退货', 'menu', '/warehouse/goods-return', 3)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 2, is_active = TRUE WHERE menu_code = 'warehouse_entry';
UPDATE sys_menu SET menu_name = '设备退货', path = '/warehouse/goods-return', sort_order = 3, is_active = TRUE
WHERE menu_code = 'warehouse_goods_return';
UPDATE sys_menu SET parent_code = 'mod_warehouse', menu_name = '库存查询', path = '/asset/stock',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'asset_stock_query';
UPDATE sys_menu SET sort_order = 5, is_active = TRUE WHERE menu_code = 'warehouse_outbound';
UPDATE sys_menu SET menu_name = '设备退库', path = '/warehouse/return', sort_order = 6, is_active = TRUE
WHERE menu_code = 'warehouse_return';
UPDATE sys_menu SET parent_code = 'mod_warehouse', menu_name = '库存盘点', path = '/warehouse/inventory',
    sort_order = 7, is_active = TRUE
WHERE menu_code = 'warehouse_inventory';

-- ---------- AST-UI-06：库房调拨迁入资产管理→资产增减（报废见 AST-UI-07） ----------
UPDATE sys_menu SET parent_code = 'asset_change', menu_name = '库房调拨', path = '/warehouse/transfer',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'warehouse_transfer';

-- ---------- AST-UI-07：报废管理挂资产查询下；科室盘点二级分组 ----------
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('asset_scrap_mgmt', 'asset_query_group', '报废管理', 'group', NULL, 3),
('asset_dept_inventory', 'mod_asset', '科室盘点', 'group', NULL, 3),
('asset_dept_inventory_apply', 'asset_dept_inventory', '科室盘点申请', 'menu', '/asset/dept-inventory-apply', 1),
('asset_dept_inventory_report', 'asset_dept_inventory', '设备盘点报表', 'menu', '/asset/dept-inventory-report', 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET parent_code = 'asset_scrap_mgmt', menu_name = '报废申请', path = '/warehouse/scrap',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'warehouse_scrap';
UPDATE sys_menu SET parent_code = 'asset_query_group', menu_name = '资产综合查询', path = '/asset/query',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'asset_query';
UPDATE sys_menu SET sort_order = 1, is_active = TRUE WHERE menu_code = 'asset_change';
UPDATE sys_menu SET sort_order = 2, is_active = TRUE WHERE menu_code = 'asset_query_group';
UPDATE sys_menu SET sort_order = 3, is_active = TRUE WHERE menu_code = 'asset_dept_inventory';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'asset_scrap_mgmt', 'asset_dept_inventory',
    'asset_dept_inventory_apply', 'asset_dept_inventory_report',
    'warehouse_goods_return'
)
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'asset_scrap_mgmt', 'asset_dept_inventory',
    'asset_dept_inventory_apply', 'asset_dept_inventory_report',
    'warehouse_goods_return'
)
ON CONFLICT DO NOTHING;

-- ---------- AST-UI-08：资产查询下增加「资产动态统计」 ----------
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('asset_dynamic_stats', 'asset_query_group', '资产动态统计', 'menu', '/asset/dynamic-stats', 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET parent_code = 'asset_query_group', menu_name = '资产综合查询', path = '/asset/query',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'asset_query';
UPDATE sys_menu SET parent_code = 'asset_query_group', sort_order = 3, is_active = TRUE
WHERE menu_code = 'asset_scrap_mgmt';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code = 'asset_dynamic_stats'
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code = 'asset_dynamic_stats'
ON CONFLICT DO NOTHING;

-- ---------- QC-UI-02 / AST-UI-09 / ANA-UI-01：质控不良事件分组、资产维保管理、PM 分组、数据决策效益/效率 ----------
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('qc_adverse_group', 'mod_quality', '不良事件', 'group', NULL, 2),
('qc_pm_group', 'mod_quality', '预防性维护', 'group', NULL, 3),
('asset_maint_mgmt', 'mod_asset', '维保管理', 'group', NULL, 4),
('analytics_benefit_group', 'mod_analytics', '效益分析', 'group', NULL, 1),
('analytics_efficiency', 'mod_analytics', '效率分析', 'menu', '/analytics/efficiency', 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

-- 质控：风险评估 / 不良事件上报 / 查询 → 不良事件分组
UPDATE sys_menu SET parent_code = 'qc_adverse_group', menu_name = '风险评估', path = '/qc/risk',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'qc_risk';
UPDATE sys_menu SET parent_code = 'qc_adverse_group', menu_name = '不良事件上报', path = '/qc/adverse/report',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'qc_adverse_report';
UPDATE sys_menu SET parent_code = 'qc_adverse_group', menu_name = '不良事件查询', path = '/qc/adverse/query',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'qc_adverse_query';
UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'qc_adverse';

-- 质控：预防性维护计划/执行/记录 → 预防性维护分组（参数一并收纳，避免散落）
UPDATE sys_menu SET parent_code = 'qc_pm_group', menu_name = '预防性维护参数', path = '/pm/param',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'pm_param';
UPDATE sys_menu SET parent_code = 'qc_pm_group', menu_name = '预防性维护计划', path = '/pm/plan',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'pm_plan';
UPDATE sys_menu SET parent_code = 'qc_pm_group', menu_name = '预防性维护执行', path = '/pm/execution',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'pm_execution';
UPDATE sys_menu SET parent_code = 'qc_pm_group', menu_name = '预防性维护记录', path = '/pm/query',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'pm_query';

-- 质控剩余直挂项排序
UPDATE sys_menu SET sort_order = 1, is_active = TRUE WHERE menu_code = 'qc_adverse_group';
UPDATE sys_menu SET sort_order = 2, is_active = TRUE WHERE menu_code = 'qc_pm_group';

-- 资产管理：维保合同 / 履约记录 / 性能检测 → 维保管理
UPDATE sys_menu SET parent_code = 'asset_maint_mgmt', menu_name = '维保合同', path = '/maintenance-contract/list',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'mcontract_list';
UPDATE sys_menu SET parent_code = 'asset_maint_mgmt', menu_name = '履约记录', path = '/maintenance-contract/fulfillment',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'mcontract_fulfillment';
UPDATE sys_menu SET parent_code = 'asset_maint_mgmt', menu_name = '性能检测', path = '/qc/performance',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'qc_performance';
UPDATE sys_menu SET sort_order = 4, is_active = TRUE WHERE menu_code = 'asset_maint_mgmt';

-- 数据决策：既有效益相关页收入「效益分析」；新增「效率分析」占位
UPDATE sys_menu SET parent_code = 'analytics_benefit_group', menu_name = '对照管理', path = '/analytics/mapping',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'analytics_mapping';
UPDATE sys_menu SET parent_code = 'analytics_benefit_group', menu_name = '数据抓取', path = '/analytics/sync',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'analytics_sync';
UPDATE sys_menu SET parent_code = 'analytics_benefit_group', menu_name = '效益分析汇总', path = '/analytics/summary',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'analytics_summary';
UPDATE sys_menu SET parent_code = 'analytics_benefit_group', menu_name = '成本上报', path = '/analytics/cost',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'analytics_cost';
UPDATE sys_menu SET parent_code = 'analytics_benefit_group', menu_name = '单机效益分析', path = '/analytics/device',
    sort_order = 5, is_active = TRUE
WHERE menu_code = 'analytics_device';
UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'analytics_benefit';
UPDATE sys_menu SET parent_code = 'mod_analytics', menu_name = '统计报表', path = '/analytics/reports',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'analytics_reports';
UPDATE sys_menu SET sort_order = 1, is_active = TRUE WHERE menu_code = 'analytics_benefit_group';
UPDATE sys_menu SET sort_order = 2, is_active = TRUE WHERE menu_code = 'analytics_efficiency';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'qc_adverse_group', 'qc_pm_group', 'asset_maint_mgmt',
    'analytics_benefit_group', 'analytics_efficiency'
)
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'qc_adverse_group', 'qc_pm_group', 'asset_maint_mgmt',
    'analytics_benefit_group', 'analytics_efficiency'
)
ON CONFLICT DO NOTHING;

-- ---------- ANA-UI-02：效益分析子菜单更名/增查询；效率分析改为分组 ----------
UPDATE sys_menu SET parent_code = 'analytics_benefit_group', menu_name = '效益分析对照', path = '/analytics/mapping',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'analytics_mapping';
UPDATE sys_menu SET parent_code = 'analytics_benefit_group', menu_name = '效益分析提取', path = '/analytics/sync',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'analytics_sync';
UPDATE sys_menu SET parent_code = 'analytics_benefit_group', menu_name = '效益分析报表', path = '/analytics/summary',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'analytics_summary';
UPDATE sys_menu SET parent_code = 'analytics_benefit_group', menu_name = '效益分析上报', path = '/analytics/cost',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'analytics_cost';
UPDATE sys_menu SET parent_code = 'analytics_benefit_group', menu_name = '单机效益分析', path = '/analytics/device',
    sort_order = 6, is_active = TRUE
WHERE menu_code = 'analytics_device';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('analytics_benefit_query', 'analytics_benefit_group', '效益分析查询', 'menu', '/analytics/benefit-query', 5),
('analytics_efficiency_view', 'analytics_efficiency', '效率分析', 'menu', '/analytics/efficiency', 1),
('analytics_charge_audit', 'analytics_efficiency', '收费项目审核', 'menu', '/analytics/charge-audit', 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

-- 效率分析：由叶子菜单改为分组
UPDATE sys_menu SET parent_code = 'mod_analytics', menu_name = '效率分析', menu_type = 'group', path = NULL,
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'analytics_efficiency';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'analytics_benefit_query', 'analytics_efficiency_view', 'analytics_charge_audit'
)
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'analytics_benefit_query', 'analytics_efficiency_view', 'analytics_charge_audit'
)
ON CONFLICT DO NOTHING;

-- ---------- AST-UI-10：报废管理 — 报废申请/审核/查询 ----------
UPDATE sys_menu SET parent_code = 'asset_scrap_mgmt', menu_name = '报废申请', path = '/warehouse/scrap',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'warehouse_scrap';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('warehouse_scrap_review', 'asset_scrap_mgmt', '报废审核', 'menu', '/warehouse/scrap-review', 2),
('warehouse_scrap_query', 'asset_scrap_mgmt', '报废查询', 'menu', '/warehouse/scrap-query', 3)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('warehouse_scrap_review', 'warehouse_scrap_query')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('warehouse_scrap_review', 'warehouse_scrap_query')
ON CONFLICT DO NOTHING;
