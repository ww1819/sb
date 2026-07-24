-- =============================================================================
-- MEIS public 菜单唯一维护脚本（PLT-MENU-01）
-- =============================================================================
-- 幂等：可重复执行。菜单变更只改本文件。
-- 禁止：R__data_fix / 租户脚本 / 临时 SQL 再散落 INSERT/UPDATE sys_menu。
-- 导出对齐（可选）：javac + java scripts/ExportMenus.java
-- =============================================================================

-- ========== 1. sys_menu ==========
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_dashboard', NULL, '工作台', 'module', '/dashboard', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_dict', NULL, '基础字典', 'module', NULL, NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_purchase', NULL, '采购管理', 'module', NULL, NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_warehouse', NULL, '库房管理', 'module', NULL, NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_asset', NULL, '资产管理', 'module', NULL, NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_special', NULL, '特种设备', 'module', NULL, NULL, 6, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_shared', NULL, '调配中心', 'module', NULL, NULL, 7, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_ops', NULL, '运维管理', 'module', NULL, NULL, 8, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_quality', NULL, '质控合规', 'module', NULL, NULL, 9, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_analytics', NULL, '数据决策', 'module', NULL, NULL, 10, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_screen', NULL, '设备大屏', 'module', NULL, NULL, 12, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_system', NULL, '系统管理', 'module', NULL, NULL, 13, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_platform', NULL, '平台管理', 'module', NULL, NULL, 99, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_benefit', 'mod_analytics', '效益分析', 'menu', '/analytics/benefit', NULL, 1, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_benefit_group', 'mod_analytics', '效益分析', 'group', NULL, NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_efficiency_view', 'analytics_efficiency', '效率分析', 'menu', '/analytics/efficiency', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_mapping', 'analytics_benefit_group', '效益分析对照', 'menu', '/analytics/mapping', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_reports', 'analytics_asset_report_group', '统计报表', 'menu', '/analytics/reports', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_change', 'mod_asset', '资产增减', 'group', NULL, NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_dept_inventory_apply', 'asset_dept_inventory', '科室盘点申请', 'menu', '/asset/dept-inventory-apply', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_device', 'asset_change', '资产登记', 'menu', '/asset/device', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_query', 'asset_query_group', '资产综合查询', 'menu', '/asset/query', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('dashboard', 'mod_dashboard', '工作台', 'menu', '/dashboard', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('inspect_param', 'ops_inspect', '巡检参数设置', 'menu', '/inspect/param', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('maintain_param', 'ops_maintain', '保养参数设置', 'menu', '/maintain/param', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mcontract_list', 'asset_maint_mgmt', '维保合同', 'menu', '/maintenance-contract/list', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('metrology_param', 'ops_metrology', '计量参数设置', 'menu', '/metrology/param', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('ops_repair', 'mod_ops', '维修管理', 'group', NULL, NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('platform_tenant', 'mod_platform', '租户列表', 'menu', '/tenant/list', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('pm_param', 'qc_pm_group', '预防性维护参数', 'menu', '/pm/param', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('power_station', 'mod_power', '基站维护', 'menu', '/power/station', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_apply', 'mod_purchase', '采购申请', 'menu', '/purchase/apply', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_plan', 'mod_purchase', '采购计划', 'menu', '/purchase/plan', NULL, 1, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('qc_adverse_group', 'mod_quality', '不良事件', 'group', NULL, NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('qc_risk', 'qc_adverse_group', '风险评估', 'menu', '/qc/risk', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('repair_apply', 'ops_repair', '报修申请', 'menu', '/repair/apply', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('repair_workorder', 'mod_ops', '维修工单', 'menu', '/repair/workorder', NULL, 1, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('screen_equipment', 'mod_screen', '设备运营大屏', 'menu', '/screen/equipment', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('shared_device', 'mod_shared', '公用设备管理', 'menu', '/shared/device', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('special_life', 'mod_special', '生命支持设备', 'menu', '/special/life', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('system_campus', 'mod_dict', '院区管理', 'menu', '/dict/campus', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('system_user', 'mod_system', '用户管理', 'menu', '/system/user', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('warehouse_scrap', 'asset_scrap_mgmt', '报废申请', 'menu', '/warehouse/scrap', NULL, 1, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('warehouse_setting', 'mod_warehouse', '库房维护', 'menu', '/warehouse/setting', NULL, 1, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_asset_usage', 'analytics_asset_report_group', '资产使用率统计', 'menu', '/analytics/asset-usage', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_charge_audit', 'analytics_efficiency', '收费项目审核', 'menu', '/analytics/charge-audit', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_efficiency', 'mod_analytics', '效率分析', 'group', NULL, NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_sync', 'analytics_benefit_group', '效益分析提取', 'menu', '/analytics/sync', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_dept_inventory_report', 'asset_dept_inventory', '设备盘点报表', 'menu', '/asset/dept-inventory-report', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_dynamic_stats', 'asset_query_group', '资产动态统计', 'menu', '/asset/dynamic-stats', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_import', 'asset_change', '资产导入', 'menu', '/asset/import', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_query_group', 'mod_asset', '资产查询', 'group', NULL, NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('dict_warehouse', 'mod_dict', '仓库维护', 'menu', '/dict/warehouse', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('inspect_plan', 'ops_inspect', '巡检计划', 'menu', '/inspect/plan', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('maintain_plan', 'ops_maintain', '保养计划', 'menu', '/maintain/plan', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mcontract_fulfillment', 'asset_maint_mgmt', '履约记录', 'menu', '/maintenance-contract/fulfillment', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('metrology_plan', 'ops_metrology', '计量计划', 'menu', '/metrology/plan', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('ops_maintain', 'mod_ops', '保养管理', 'group', NULL, NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('platform_tenant_menu', 'mod_platform', '租户菜单授权', 'menu', '/platform/tenant-menu', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('pm_plan', 'qc_pm_group', '预防性维护计划', 'menu', '/pm/plan', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('power_tag', 'mod_power', '标签维护', 'menu', '/power/tag', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_approval', 'mod_purchase', '采购审批', 'menu', '/purchase/approval', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('qc_adverse', 'mod_quality', '不良事件', 'menu', '/qc/adverse', NULL, 2, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('qc_adverse_report', 'qc_adverse_group', '不良事件上报', 'menu', '/qc/adverse/report', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('qc_pm_group', 'mod_quality', '预防性维护', 'group', NULL, NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('repair_handle', 'ops_repair', '维修处理', 'menu', '/repair/handle', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('screen_warehouse_twin', 'mod_screen', '数字孪生大屏', 'menu', '/screen/warehouse-twin', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('shared_loan', 'mod_shared', '借调申请', 'menu', '/shared/loan', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('special_emergency', 'mod_special', '应急设备库', 'menu', '/special/emergency', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('system_role', 'mod_system', '角色管理', 'menu', '/system/role', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('system_warehouse', 'mod_system', '仓库维护', 'menu', '/system/warehouse', NULL, 2, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('warehouse_entry', 'mod_warehouse', '设备入库', 'menu', '/warehouse/entry', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('warehouse_scrap_review', 'asset_scrap_mgmt', '报废审核', 'menu', '/warehouse/scrap-review', NULL, 2, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_asset_report_group', 'mod_analytics', '资产报表统计', 'group', NULL, NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_summary', 'analytics_benefit_group', '效益分析报表', 'menu', '/analytics/summary', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_value_structure', 'analytics_asset_report_group', '价值结构分析表', 'menu', '/analytics/value-structure', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_dept_inventory', 'mod_asset', '科室盘点', 'group', NULL, NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_scrap_mgmt', 'asset_query_group', '报废管理', 'group', NULL, NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('dict_dept', 'mod_dict', '科室维护', 'menu', '/dict/dept', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('inspect_execution', 'ops_inspect', '巡检执行', 'menu', '/inspect/execution', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('maintain_execution', 'ops_maintain', '保养执行', 'menu', '/maintain/execution', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('metrology_execution', 'ops_metrology', '计量执行', 'menu', '/metrology/execution', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('ops_inspect', 'mod_ops', '巡检管理', 'group', NULL, NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('platform_package', 'mod_platform', '套餐管理', 'menu', '/platform/package', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('pm_execution', 'qc_pm_group', '预防性维护执行', 'menu', '/pm/execution', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('power_status', 'mod_power', '设备运行状态', 'menu', '/power/status', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_project', 'mod_purchase', '设备采购计划表', 'menu', '/purchase/project', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('qc_adverse_query', 'qc_adverse_group', '不良事件查询', 'menu', '/qc/adverse/query', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('qc_metrology', 'mod_quality', '计量管理', 'menu', '/qc/metrology', NULL, 3, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('qc_performance', 'asset_maint_mgmt', '性能检测', 'menu', '/qc/performance', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('repair_spare', 'mod_ops', '备件管理', 'menu', '/repair/spare', NULL, 3, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('repair_spare_archive', 'ops_repair', '配件档案管理', 'menu', '/repair/spare-archive', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('shared_loan_approve', 'mod_shared', '借调审批', 'menu', '/shared/loan-approve', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('special_radiation', 'mod_special', '特种设备登记', 'menu', '/special/radiation', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('system_dept', 'mod_system', '科室管理', 'menu', '/system/dept', NULL, 3, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('system_dict', 'mod_system', '数据字典', 'menu', '/system/dict', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('warehouse_goods_return', 'mod_warehouse', '设备退货', 'menu', '/warehouse/goods-return', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('warehouse_scrap_query', 'asset_scrap_mgmt', '报废查询', 'menu', '/warehouse/scrap-query', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('warehouse_transfer', 'asset_change', '库房调拨', 'menu', '/warehouse/transfer', NULL, 3, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_cost', 'analytics_benefit_group', '效益分析上报', 'menu', '/analytics/cost', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_depr_due', 'analytics_asset_report_group', '折旧到期汇总', 'menu', '/analytics/depr-due', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_entry', 'mod_asset', '设备入库', 'menu', '/asset/entry', NULL, 4, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_maint_mgmt', 'mod_asset', '维保管理', 'group', NULL, NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_stock_query', 'mod_warehouse', '库存查询', 'menu', '/asset/stock', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('inspect_query', 'ops_inspect', '巡检记录查询', 'menu', '/inspect/query', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('maintain_query', 'ops_maintain', '保养记录查询', 'menu', '/maintain/query', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('maintain_template', 'mod_ops', '保养模板', 'menu', '/maintain/template', NULL, 4, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('metrology_query', 'ops_metrology', '计量记录查询', 'menu', '/metrology/query', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('ops_metrology', 'mod_ops', '计量管理', 'group', NULL, NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('pm_query', 'qc_pm_group', '预防性维护记录', 'menu', '/pm/query', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('power_stats', 'mod_power', '设备运行统计', 'menu', '/power/stats', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_bidding', 'mod_purchase', '招标管理', 'menu', '/purchase/bidding', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_supplier', 'mod_dict', '供应商管理', 'menu', '/dict/supplier', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('repair_verify', 'ops_repair', '维修验收', 'menu', '/repair/verify', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('shared_return', 'mod_shared', '归还申请', 'menu', '/shared/return', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('special_leased', 'mod_special', '租赁设备', 'menu', '/special/leased', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('system_log', 'mod_system', '操作日志', 'menu', '/system/log', NULL, 4, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_benefit_query', 'analytics_benefit_group', '效益分析查询', 'menu', '/analytics/benefit-query', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_depr_stats', 'analytics_asset_report_group', '资产折旧统计', 'menu', '/analytics/depr-stats', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_outbound', 'mod_asset', '设备出库', 'menu', '/asset/outbound', NULL, 5, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('inspect_device', 'ops_inspect', '巡检设备管理', 'menu', '/inspect/device', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('maintain_device', 'ops_maintain', '保养设备管理', 'menu', '/maintain/device', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('mod_power', 'mod_ops', '电流监测', 'group', NULL, NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('pm_device', 'qc_pm_group', 'PM设备管理', 'menu', '/pm/device', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('power_record', 'mod_power', '监测记录', 'menu', '/power/record', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_contract', 'mod_purchase', '设备合同管理', 'menu', '/purchase/contract', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_manufacturer', 'mod_dict', '生产厂商', 'menu', '/dict/manufacturer', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('repair_fault', 'ops_repair', '故障库', 'menu', '/repair/fault', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('shared_return_approve', 'mod_shared', '归还审批', 'menu', '/shared/return-approve', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('special_alerts', 'mod_special', '证照到期提醒', 'menu', '/special/alerts', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('system_approval', 'mod_system', '审批配置', 'menu', '/system/approval', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('warehouse_outbound', 'mod_warehouse', '设备出库', 'menu', '/warehouse/outbound', NULL, 5, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_depr_ratio', 'analytics_asset_report_group', '折旧明细比例', 'menu', '/analytics/depr-ratio', NULL, 6, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_device', 'analytics_benefit_group', '单机效益分析', 'menu', '/analytics/device', NULL, 6, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_transfer', 'mod_asset', '资产流转', 'menu', '/asset/transfer', NULL, 6, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('maintain_record', 'mod_ops', '保养记录', 'menu', '/maintain/record', NULL, 6, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_acceptance', 'mod_purchase', '安装验收', 'menu', '/purchase/acceptance', NULL, 6, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_category', 'mod_dict', '设备分类', 'menu', '/dict/category', NULL, 6, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('repair_process_type', 'ops_repair', '维修进程类型', 'menu', '/repair/process-type', NULL, 6, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('shared_fee', 'mod_shared', '借调收费', 'menu', '/shared/fee', NULL, 6, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('system_config', 'mod_system', '系统配置', 'menu', '/system/config', NULL, 6, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('warehouse_return', 'mod_warehouse', '设备退库', 'menu', '/warehouse/return', NULL, 6, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_depr_detail', 'analytics_asset_report_group', '折旧详情', 'menu', '/analytics/depr-detail', NULL, 7, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_inventory', 'mod_asset', '资产盘点', 'menu', '/asset/inventory', NULL, 7, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('dict_asset_category', 'mod_dict', '资产分类', 'menu', '/dict/asset-category', NULL, 7, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('repair_engineer', 'ops_repair', '维修工程师管理', 'menu', '/repair/engineer', NULL, 7, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('shared_record', 'mod_shared', '借调记录查询', 'menu', '/shared/record', NULL, 7, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('warehouse_inventory', 'mod_warehouse', '库存盘点', 'menu', '/warehouse/inventory', NULL, 7, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_asset_change', 'analytics_asset_report_group', '资产增减统计', 'menu', '/analytics/asset-change', NULL, 8, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('asset_scrap', 'mod_asset', '设备报废', 'menu', '/asset/scrap', NULL, 8, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('dict_finance_category', 'mod_dict', '财务分类', 'menu', '/dict/finance-category', NULL, 8, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_dashboard', 'mod_purchase', '采购看板', 'menu', '/purchase/dashboard', NULL, 8, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_asset_occupy', 'analytics_asset_report_group', '资产占用统计', 'menu', '/analytics/asset-occupy', NULL, 9, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('dict_unit', 'mod_dict', '单位维护', 'menu', '/dict/unit', NULL, 9, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_trace', 'mod_purchase', '业务追溯', 'menu', '/purchase/trace', NULL, 9, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('analytics_asset_transfer', 'analytics_asset_report_group', '资产异动统计', 'menu', '/analytics/asset-transfer', NULL, 10, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('purchase_report', 'mod_purchase', '预算执行', 'menu', '/purchase/report', NULL, 10, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('system_camera_debug', 'mod_system', '高拍仪调试', 'menu', '/system/camera-debug', NULL, 20, TRUE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES ('metrology_device', 'mod_ops', '计量设备管理', 'menu', '/metrology/device', NULL, 21, FALSE)
ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;

-- ========== 2. sys_package_menu ==========
INSERT INTO sys_package_menu (package_code, menu_code) VALUES
('flagship','analytics_asset_change'),
('flagship','analytics_asset_occupy'),
('flagship','analytics_asset_report_group'),
('flagship','analytics_asset_transfer'),
('flagship','analytics_asset_usage'),
('flagship','analytics_benefit'),
('flagship','analytics_benefit_group'),
('flagship','analytics_benefit_query'),
('flagship','analytics_charge_audit'),
('flagship','analytics_cost'),
('flagship','analytics_depr_detail'),
('flagship','analytics_depr_due'),
('flagship','analytics_depr_ratio'),
('flagship','analytics_depr_stats'),
('flagship','analytics_device'),
('flagship','analytics_efficiency'),
('flagship','analytics_efficiency_view'),
('flagship','analytics_mapping'),
('flagship','analytics_reports'),
('flagship','analytics_summary'),
('flagship','analytics_sync'),
('flagship','analytics_value_structure'),
('flagship','asset_change'),
('flagship','asset_dept_inventory'),
('flagship','asset_dept_inventory_apply'),
('flagship','asset_dept_inventory_report'),
('flagship','asset_device'),
('flagship','asset_dynamic_stats'),
('flagship','asset_entry'),
('flagship','asset_import'),
('flagship','asset_inventory'),
('flagship','asset_maint_mgmt'),
('flagship','asset_outbound'),
('flagship','asset_query'),
('flagship','asset_query_group'),
('flagship','asset_scrap'),
('flagship','asset_scrap_mgmt'),
('flagship','asset_stock_query'),
('flagship','asset_transfer'),
('flagship','dashboard'),
('flagship','dict_asset_category'),
('flagship','dict_dept'),
('flagship','dict_finance_category'),
('flagship','dict_unit'),
('flagship','dict_warehouse'),
('flagship','inspect_device'),
('flagship','inspect_execution'),
('flagship','inspect_param'),
('flagship','inspect_plan'),
('flagship','inspect_query'),
('flagship','maintain_device'),
('flagship','maintain_execution'),
('flagship','maintain_param'),
('flagship','maintain_plan'),
('flagship','maintain_query'),
('flagship','maintain_record'),
('flagship','maintain_template'),
('flagship','mcontract_fulfillment'),
('flagship','mcontract_list'),
('flagship','metrology_execution'),
('flagship','metrology_param'),
('flagship','metrology_plan'),
('flagship','metrology_query'),
('flagship','mod_analytics'),
('flagship','mod_asset'),
('flagship','mod_dashboard'),
('flagship','mod_dict'),
('flagship','mod_ops'),
('flagship','mod_power'),
('flagship','mod_purchase'),
('flagship','mod_quality'),
('flagship','mod_screen'),
('flagship','mod_shared'),
('flagship','mod_special'),
('flagship','mod_system'),
('flagship','mod_warehouse'),
('flagship','ops_inspect'),
('flagship','ops_maintain'),
('flagship','ops_metrology'),
('flagship','ops_repair')
ON CONFLICT DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code) VALUES
('flagship','pm_device'),
('flagship','pm_execution'),
('flagship','pm_param'),
('flagship','pm_plan'),
('flagship','pm_query'),
('flagship','power_record'),
('flagship','power_station'),
('flagship','power_stats'),
('flagship','power_status'),
('flagship','power_tag'),
('flagship','purchase_acceptance'),
('flagship','purchase_apply'),
('flagship','purchase_approval'),
('flagship','purchase_bidding'),
('flagship','purchase_category'),
('flagship','purchase_contract'),
('flagship','purchase_dashboard'),
('flagship','purchase_manufacturer'),
('flagship','purchase_plan'),
('flagship','purchase_project'),
('flagship','purchase_report'),
('flagship','purchase_supplier'),
('flagship','purchase_trace'),
('flagship','qc_adverse'),
('flagship','qc_adverse_group'),
('flagship','qc_adverse_query'),
('flagship','qc_adverse_report'),
('flagship','qc_metrology'),
('flagship','qc_performance'),
('flagship','qc_pm_group'),
('flagship','qc_risk'),
('flagship','repair_apply'),
('flagship','repair_engineer'),
('flagship','repair_fault'),
('flagship','repair_handle'),
('flagship','repair_process_type'),
('flagship','repair_spare'),
('flagship','repair_spare_archive'),
('flagship','repair_verify'),
('flagship','repair_workorder'),
('flagship','screen_equipment'),
('flagship','screen_warehouse_twin'),
('flagship','shared_device'),
('flagship','shared_fee'),
('flagship','shared_loan'),
('flagship','shared_loan_approve'),
('flagship','shared_record'),
('flagship','shared_return'),
('flagship','shared_return_approve'),
('flagship','special_alerts'),
('flagship','special_emergency'),
('flagship','special_leased'),
('flagship','special_life'),
('flagship','special_radiation'),
('flagship','system_approval'),
('flagship','system_camera_debug'),
('flagship','system_campus'),
('flagship','system_config'),
('flagship','system_dept'),
('flagship','system_dict'),
('flagship','system_log'),
('flagship','system_role'),
('flagship','system_user'),
('flagship','system_warehouse'),
('flagship','warehouse_entry'),
('flagship','warehouse_goods_return'),
('flagship','warehouse_inventory'),
('flagship','warehouse_outbound'),
('flagship','warehouse_return'),
('flagship','warehouse_scrap'),
('flagship','warehouse_scrap_query'),
('flagship','warehouse_scrap_review'),
('flagship','warehouse_setting'),
('flagship','warehouse_transfer'),
('professional','analytics_cost'),
('professional','analytics_device'),
('professional','analytics_mapping'),
('professional','analytics_summary'),
('professional','analytics_sync'),
('professional','asset_stock_query')
ON CONFLICT DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code) VALUES
('professional','inspect_device'),
('professional','maintain_device'),
('professional','mod_analytics'),
('professional','mod_power'),
('professional','mod_screen'),
('professional','pm_device'),
('professional','power_record'),
('professional','power_station'),
('professional','power_stats'),
('professional','power_status'),
('professional','power_tag'),
('professional','screen_equipment'),
('professional','screen_warehouse_twin'),
('professional','system_camera_debug'),
('professional','system_campus'),
('professional','system_config'),
('professional','system_warehouse'),
('standard','analytics_asset_change'),
('standard','analytics_asset_occupy'),
('standard','analytics_asset_report_group'),
('standard','analytics_asset_transfer'),
('standard','analytics_asset_usage'),
('standard','analytics_benefit'),
('standard','analytics_benefit_group'),
('standard','analytics_benefit_query'),
('standard','analytics_charge_audit'),
('standard','analytics_cost'),
('standard','analytics_depr_detail'),
('standard','analytics_depr_due'),
('standard','analytics_depr_ratio'),
('standard','analytics_depr_stats'),
('standard','analytics_device'),
('standard','analytics_efficiency'),
('standard','analytics_efficiency_view'),
('standard','analytics_mapping'),
('standard','analytics_reports'),
('standard','analytics_summary'),
('standard','analytics_sync'),
('standard','analytics_value_structure'),
('standard','asset_change'),
('standard','asset_dept_inventory'),
('standard','asset_dept_inventory_apply'),
('standard','asset_dept_inventory_report'),
('standard','asset_device'),
('standard','asset_dynamic_stats'),
('standard','asset_entry'),
('standard','asset_import'),
('standard','asset_inventory'),
('standard','asset_maint_mgmt'),
('standard','asset_outbound'),
('standard','asset_query'),
('standard','asset_query_group'),
('standard','asset_scrap'),
('standard','asset_scrap_mgmt'),
('standard','asset_stock_query'),
('standard','asset_transfer'),
('standard','dashboard'),
('standard','dict_asset_category'),
('standard','dict_dept'),
('standard','dict_finance_category'),
('standard','dict_unit'),
('standard','dict_warehouse'),
('standard','inspect_device'),
('standard','inspect_execution'),
('standard','inspect_param'),
('standard','inspect_plan'),
('standard','inspect_query'),
('standard','maintain_device'),
('standard','maintain_execution'),
('standard','maintain_param'),
('standard','maintain_plan'),
('standard','maintain_query'),
('standard','maintain_record'),
('standard','maintain_template'),
('standard','mcontract_fulfillment'),
('standard','mcontract_list'),
('standard','metrology_execution'),
('standard','metrology_param'),
('standard','metrology_plan'),
('standard','metrology_query')
ON CONFLICT DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code) VALUES
('standard','mod_analytics'),
('standard','mod_asset'),
('standard','mod_dashboard'),
('standard','mod_dict'),
('standard','mod_ops'),
('standard','mod_power'),
('standard','mod_purchase'),
('standard','mod_quality'),
('standard','mod_screen'),
('standard','mod_shared'),
('standard','mod_special'),
('standard','mod_system'),
('standard','mod_warehouse'),
('standard','ops_inspect'),
('standard','ops_maintain'),
('standard','ops_metrology'),
('standard','ops_repair'),
('standard','pm_device'),
('standard','pm_execution'),
('standard','pm_param'),
('standard','pm_plan'),
('standard','pm_query'),
('standard','power_record'),
('standard','power_station'),
('standard','power_stats'),
('standard','power_status'),
('standard','power_tag'),
('standard','purchase_acceptance'),
('standard','purchase_apply'),
('standard','purchase_approval'),
('standard','purchase_bidding'),
('standard','purchase_category'),
('standard','purchase_contract'),
('standard','purchase_dashboard'),
('standard','purchase_manufacturer'),
('standard','purchase_plan'),
('standard','purchase_project'),
('standard','purchase_report'),
('standard','purchase_supplier'),
('standard','purchase_trace'),
('standard','qc_adverse'),
('standard','qc_adverse_group'),
('standard','qc_adverse_query'),
('standard','qc_adverse_report'),
('standard','qc_metrology'),
('standard','qc_performance'),
('standard','qc_pm_group'),
('standard','qc_risk'),
('standard','repair_apply'),
('standard','repair_engineer'),
('standard','repair_fault'),
('standard','repair_handle'),
('standard','repair_process_type'),
('standard','repair_spare'),
('standard','repair_spare_archive'),
('standard','repair_verify'),
('standard','repair_workorder'),
('standard','screen_equipment'),
('standard','screen_warehouse_twin'),
('standard','shared_device'),
('standard','shared_fee'),
('standard','shared_loan'),
('standard','shared_loan_approve'),
('standard','shared_record'),
('standard','shared_return'),
('standard','shared_return_approve'),
('standard','special_alerts'),
('standard','special_emergency'),
('standard','special_leased'),
('standard','special_life'),
('standard','special_radiation'),
('standard','system_approval'),
('standard','system_camera_debug'),
('standard','system_campus'),
('standard','system_config'),
('standard','system_dept'),
('standard','system_dict'),
('standard','system_log'),
('standard','system_role'),
('standard','system_user')
ON CONFLICT DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code) VALUES
('standard','system_warehouse'),
('standard','warehouse_entry'),
('standard','warehouse_goods_return'),
('standard','warehouse_inventory'),
('standard','warehouse_outbound'),
('standard','warehouse_return'),
('standard','warehouse_scrap'),
('standard','warehouse_scrap_query'),
('standard','warehouse_scrap_review'),
('standard','warehouse_setting'),
('standard','warehouse_transfer')
ON CONFLICT DO NOTHING;

-- ========== 3. sys_tenant_menu：活跃租户挂接其套餐菜单 ==========
INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, pm.menu_code
FROM sys_tenant t
JOIN sys_package_menu pm ON pm.package_code = COALESCE(t.package_code, 'standard')
WHERE t.status = 'active'
ON CONFLICT DO NOTHING;
