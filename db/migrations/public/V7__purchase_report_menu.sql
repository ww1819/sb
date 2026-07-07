-- MEIS V11: 采购预算执行报表菜单

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_report', 'mod_purchase', '预算执行', 'menu', '/purchase/report', 10)
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code = 'purchase_report'
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', 'purchase_report'
FROM sys_menu m WHERE m.menu_code = 'purchase_report'
ON CONFLICT DO NOTHING;
