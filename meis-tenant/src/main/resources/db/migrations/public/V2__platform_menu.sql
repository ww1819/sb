-- Platform menu catalog and tenant authorization (MEIS V2.0)
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS credit_code VARCHAR(50);
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS package_code VARCHAR(50) DEFAULT 'standard';

CREATE TABLE IF NOT EXISTS sys_menu (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    menu_code VARCHAR(100) UNIQUE NOT NULL,
    parent_code VARCHAR(100),
    menu_name VARCHAR(100) NOT NULL,
    menu_type VARCHAR(20) NOT NULL DEFAULT 'menu',
    path VARCHAR(200),
    icon VARCHAR(50),
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_tenant_menu (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    menu_code VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, menu_code)
);

CREATE TABLE IF NOT EXISTS sys_package (
    package_code VARCHAR(50) PRIMARY KEY,
    package_name VARCHAR(100) NOT NULL,
    max_users INTEGER DEFAULT 500,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS sys_package_menu (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    package_code VARCHAR(50) NOT NULL REFERENCES sys_package(package_code) ON DELETE CASCADE,
    menu_code VARCHAR(100) NOT NULL,
    UNIQUE(package_code, menu_code)
);

CREATE INDEX IF NOT EXISTS idx_tenant_menu_tenant ON sys_tenant_menu(tenant_id);

-- packages
INSERT INTO sys_package (package_code, package_name, max_users, description) VALUES
('basic', '基础版', 50, '采购+资产基础'),
('standard', '标准版', 200, '全业务模块'),
('professional', '专业版', 500, '含效益分析'),
('flagship', '旗舰版', 2000, '全模块+集成')
ON CONFLICT (package_code) DO NOTHING;

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

-- standard package = all tenant menus
INSERT INTO sys_package_menu (package_code, menu_code)
SELECT 'standard', menu_code FROM sys_menu
WHERE menu_type IN ('module','menu')
  AND menu_code NOT LIKE 'platform_%' AND menu_code <> 'mod_platform'
ON CONFLICT DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT 'flagship', menu_code FROM sys_menu
WHERE menu_type IN ('module','menu')
  AND menu_code NOT LIKE 'platform_%' AND menu_code <> 'mod_platform'
ON CONFLICT DO NOTHING;

-- demo tenant: grant standard package menus
INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', pm.menu_code
FROM sys_package_menu pm WHERE pm.package_code = 'standard'
ON CONFLICT DO NOTHING;

UPDATE sys_tenant SET package_code = 'standard' WHERE tenant_code = 'demo';

-- platform admin seed
INSERT INTO platform_user (username, password_hash, real_name)
SELECT 'platform', '$2a$10$RFmAL1Ss2C.mxB.aT.Z9s.leTG8dX52WWn86CVk/grwDupZ92hcwO', '平台管理员'
WHERE NOT EXISTS (SELECT 1 FROM platform_user WHERE username = 'platform');
