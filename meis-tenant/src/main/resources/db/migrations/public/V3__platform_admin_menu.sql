-- Platform admin menus (not in sys_tenant_menu)
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_platform', NULL, '平台管理', 'module', NULL, 99),
('platform_tenant', 'mod_platform', '租户列表', 'menu', '/tenant/list', 1),
('platform_tenant_menu', 'mod_platform', '租户菜单授权', 'menu', '/platform/tenant-menu', 2),
('platform_package', 'mod_platform', '套餐管理', 'menu', '/platform/package', 3)
ON CONFLICT (menu_code) DO NOTHING;
