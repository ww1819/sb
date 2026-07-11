-- 模块4：保养管理 — 菜单重组（参数/计划/执行/记录查询/设备管理）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('maintain_param', 'mod_ops', '保养参数设置', 'menu', '/maintain/param', 7),
('maintain_execution', 'mod_ops', '保养执行', 'menu', '/maintain/execution', 9),
('maintain_query', 'mod_ops', '保养记录查询', 'menu', '/maintain/query', 10),
('maintain_device', 'mod_ops', '保养设备管理', 'menu', '/maintain/device', 11)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET menu_name = '保养计划', path = '/maintain/plan', sort_order = 8, is_active = TRUE
WHERE menu_code = 'maintain_plan';
UPDATE sys_menu SET is_active = FALSE WHERE menu_code IN ('maintain_template', 'maintain_record');

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('maintain_param', 'maintain_plan', 'maintain_execution', 'maintain_query', 'maintain_device')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('maintain_param', 'maintain_plan', 'maintain_execution', 'maintain_query', 'maintain_device')
ON CONFLICT DO NOTHING;
