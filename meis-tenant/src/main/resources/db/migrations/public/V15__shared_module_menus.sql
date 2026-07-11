-- 模块11：公用设备借调 — 独立菜单模块

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_shared', NULL, '公用设备借调', 'module', NULL, 7)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'mod_ops';
UPDATE sys_menu SET sort_order = 9 WHERE menu_code = 'mod_quality';
UPDATE sys_menu SET sort_order = 10 WHERE menu_code = 'mod_analytics';
UPDATE sys_menu SET sort_order = 11 WHERE menu_code = 'mod_system';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('shared_device', 'mod_shared', '公用设备管理', 'menu', '/shared/device', 1),
('shared_loan', 'mod_shared', '借调申请', 'menu', '/shared/loan', 2),
('shared_loan_approve', 'mod_shared', '借调审批', 'menu', '/shared/loan-approve', 3),
('shared_return', 'mod_shared', '归还申请', 'menu', '/shared/return', 4),
('shared_return_approve', 'mod_shared', '归还审批', 'menu', '/shared/return-approve', 5),
('shared_fee', 'mod_shared', '借调收费', 'menu', '/shared/fee', 6),
('shared_record', 'mod_shared', '借调记录查询', 'menu', '/shared/record', 7)
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
WHERE m.menu_code IN (
    'mod_shared', 'shared_device', 'shared_loan', 'shared_loan_approve',
    'shared_return', 'shared_return_approve', 'shared_fee', 'shared_record'
)
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_shared', 'shared_device', 'shared_loan', 'shared_loan_approve',
    'shared_return', 'shared_return_approve', 'shared_fee', 'shared_record'
)
ON CONFLICT DO NOTHING;
