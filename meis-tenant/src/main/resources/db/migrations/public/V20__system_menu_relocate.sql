-- 供应商/设备分类/生产厂商归入系统管理，并新增系统配置菜单

UPDATE sys_menu SET parent_code = 'mod_system', menu_name = '供应商管理', path = '/system/supplier', sort_order = 8, is_active = TRUE
WHERE menu_code = 'purchase_supplier';
UPDATE sys_menu SET parent_code = 'mod_system', menu_name = '设备分类', path = '/system/category', sort_order = 9, is_active = TRUE
WHERE menu_code = 'purchase_category';
UPDATE sys_menu SET parent_code = 'mod_system', menu_name = '生产厂商', path = '/system/manufacturer', sort_order = 10, is_active = TRUE
WHERE menu_code = 'purchase_manufacturer';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('system_config', 'mod_system', '系统配置', 'menu', '/system/config', 11)
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
