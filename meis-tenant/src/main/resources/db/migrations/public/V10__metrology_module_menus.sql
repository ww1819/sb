-- 模块6：计量管理 — 菜单重组

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('metrology_param', 'mod_ops', '计量参数设置', 'menu', '/metrology/param', 17),
('metrology_plan', 'mod_ops', '计量计划', 'menu', '/metrology/plan', 18),
('metrology_execution', 'mod_ops', '计量执行', 'menu', '/metrology/execution', 19),
('metrology_query', 'mod_ops', '计量记录查询', 'menu', '/metrology/query', 20),
('metrology_device', 'mod_ops', '计量设备管理', 'menu', '/metrology/device', 21)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'qc_metrology';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('metrology_param', 'metrology_plan', 'metrology_execution', 'metrology_query', 'metrology_device')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('metrology_param', 'metrology_plan', 'metrology_execution', 'metrology_query', 'metrology_device')
ON CONFLICT DO NOTHING;
