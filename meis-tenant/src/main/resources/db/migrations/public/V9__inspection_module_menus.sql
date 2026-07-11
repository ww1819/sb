-- 模块5：巡检管理 — 菜单重组（参数/计划/执行/记录查询/设备管理）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('inspect_param', 'mod_ops', '巡检参数设置', 'menu', '/inspect/param', 12),
('inspect_plan', 'mod_ops', '巡检计划', 'menu', '/inspect/plan', 13),
('inspect_execution', 'mod_ops', '巡检执行', 'menu', '/inspect/execution', 14),
('inspect_query', 'mod_ops', '巡检记录查询', 'menu', '/inspect/query', 15),
('inspect_device', 'mod_ops', '巡检设备管理', 'menu', '/inspect/device', 16)
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
WHERE m.menu_code IN ('inspect_param', 'inspect_plan', 'inspect_execution', 'inspect_query', 'inspect_device')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('inspect_param', 'inspect_plan', 'inspect_execution', 'inspect_query', 'inspect_device')
ON CONFLICT DO NOTHING;
