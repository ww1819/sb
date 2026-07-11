-- 模块9：不良事件 — 菜单对齐（上报/查询）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('qc_adverse_report', 'mod_quality', '不良事件上报', 'menu', '/qc/adverse/report', 2),
('qc_adverse_query', 'mod_quality', '不良事件查询', 'menu', '/qc/adverse/query', 3)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 1 WHERE menu_code = 'qc_risk';
UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'qc_performance';
UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'mcontract_list';
UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'mcontract_fulfillment';
UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'special_life';
UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'special_emergency';
UPDATE sys_menu SET sort_order = 9 WHERE menu_code = 'special_leased';
UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'qc_adverse';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('qc_adverse_report', 'qc_adverse_query')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('qc_adverse_report', 'qc_adverse_query')
ON CONFLICT DO NOTHING;
