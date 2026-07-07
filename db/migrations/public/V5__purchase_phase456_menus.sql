-- MEIS V9: 生产厂商菜单

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_manufacturer', 'mod_purchase', '生产厂商', 'menu', '/purchase/manufacturer', 7),
('purchase_dashboard', 'mod_purchase', '采购看板', 'menu', '/purchase/dashboard', 8)
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('purchase_manufacturer', 'purchase_dashboard')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', m.menu_code
FROM sys_menu m
WHERE m.menu_code IN ('purchase_manufacturer', 'purchase_dashboard')
ON CONFLICT DO NOTHING;
