-- 模块13：效益分析 — 菜单（挂数据决策 mod_analytics）

UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'analytics_benefit';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('analytics_mapping', 'mod_analytics', '对照管理', 'menu', '/analytics/mapping', 1),
('analytics_sync', 'mod_analytics', '数据抓取', 'menu', '/analytics/sync', 2),
('analytics_summary', 'mod_analytics', '效益分析汇总', 'menu', '/analytics/summary', 3),
('analytics_cost', 'mod_analytics', '成本上报', 'menu', '/analytics/cost', 4),
('analytics_device', 'mod_analytics', '单机效益分析', 'menu', '/analytics/device', 5)
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
WHERE m.menu_code IN ('analytics_mapping', 'analytics_sync', 'analytics_summary', 'analytics_cost', 'analytics_device')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('analytics_mapping', 'analytics_sync', 'analytics_summary', 'analytics_cost', 'analytics_device')
ON CONFLICT DO NOTHING;
