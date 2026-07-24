-- MEIS public 种子数据（一次性）：租户、套餐、平台账号
-- 菜单目录唯一维护点：R__menus.sql（PLT-MENU-01；可重复、幂等）
-- 下方仅保留平台管理最小种子；完整业务菜单由 R__menus 同步

INSERT INTO sys_tenant (id, tenant_code, tenant_name, schema_name, status, package_code)
VALUES ('00000000-0000-0000-0000-000000000001', 'demo', '演示医院', 'tenant_demo', 'active', 'standard')
ON CONFLICT (tenant_code) DO NOTHING;

INSERT INTO sys_package (package_code, package_name, max_users, description) VALUES
('basic', '基础版', 50, '采购+资产基础'),
('standard', '标准版', 200, '全业务模块'),
('professional', '专业版', 500, '含效益分析'),
('flagship', '旗舰版', 2000, '全模块+集成')
ON CONFLICT (package_code) DO NOTHING;

INSERT INTO platform_user (username, password_hash, real_name)
SELECT 'platform', '$2a$10$RFmAL1Ss2C.mxB.aT.Z9s.leTG8dX52WWn86CVk/grwDupZ92hcwO', '平台管理员'
WHERE NOT EXISTS (SELECT 1 FROM platform_user WHERE username = 'platform');

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_platform', NULL, '平台管理', 'module', NULL, 99),
('platform_tenant', 'mod_platform', '租户列表', 'menu', '/tenant/list', 1),
('platform_tenant_menu', 'mod_platform', '租户菜单授权', 'menu', '/platform/tenant-menu', 2),
('platform_package', 'mod_platform', '套餐管理', 'menu', '/platform/package', 3)
ON CONFLICT (menu_code) DO NOTHING;

UPDATE sys_tenant SET package_code = 'standard' WHERE tenant_code = 'demo';
