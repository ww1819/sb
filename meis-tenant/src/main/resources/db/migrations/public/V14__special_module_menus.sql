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
