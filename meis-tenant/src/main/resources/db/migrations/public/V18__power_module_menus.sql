-- 模块14：电流监测 — 独立菜单模块

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_power', NULL, '电流监测', 'module', NULL, 11)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

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
