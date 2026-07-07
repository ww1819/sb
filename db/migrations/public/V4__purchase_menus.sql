-- MEIS V8: 采购模块菜单补全（供应商、设备分类、安装验收）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_supplier', 'mod_purchase', '供应商管理', 'menu', '/purchase/supplier', 4),
('purchase_category', 'mod_purchase', '设备分类', 'menu', '/purchase/category', 5),
('purchase_acceptance', 'mod_purchase', '安装验收', 'menu', '/purchase/acceptance', 6)
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('purchase_supplier', 'purchase_category', 'purchase_acceptance')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', m.menu_code
FROM sys_menu m
WHERE m.menu_code IN ('purchase_supplier', 'purchase_category', 'purchase_acceptance')
ON CONFLICT DO NOTHING;
