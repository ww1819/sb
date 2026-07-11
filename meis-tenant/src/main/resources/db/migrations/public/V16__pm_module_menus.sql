-- 模块12：预防性维护 — 菜单（挂质控合规 mod_quality）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('pm_param', 'mod_quality', '预防性维护参数', 'menu', '/pm/param', 10),
('pm_plan', 'mod_quality', '预防性维护计划', 'menu', '/pm/plan', 11),
('pm_execution', 'mod_quality', '预防性维护执行', 'menu', '/pm/execution', 12),
('pm_query', 'mod_quality', '预防性维护记录', 'menu', '/pm/query', 13),
('pm_device', 'mod_quality', '预防性维护设备', 'menu', '/pm/device', 14)
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
WHERE m.menu_code IN ('pm_param', 'pm_plan', 'pm_execution', 'pm_query', 'pm_device')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('pm_param', 'pm_plan', 'pm_execution', 'pm_query', 'pm_device')
ON CONFLICT DO NOTHING;
