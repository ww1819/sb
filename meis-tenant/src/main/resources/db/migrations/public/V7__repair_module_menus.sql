-- 模块3：维修管理 — 菜单拆分（报修/处理/配件档案/验收）

UPDATE sys_menu SET menu_name = '运维管理' WHERE menu_code = 'mod_ops';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('repair_apply', 'mod_ops', '报修申请', 'menu', '/repair/apply', 1),
('repair_handle', 'mod_ops', '维修处理', 'menu', '/repair/handle', 2),
('repair_spare_archive', 'mod_ops', '配件档案管理', 'menu', '/repair/spare-archive', 3),
('repair_verify', 'mod_ops', '维修验收', 'menu', '/repair/verify', 4),
('repair_fault', 'mod_ops', '故障库', 'menu', '/repair/fault', 5),
('repair_engineer', 'mod_ops', '工程师', 'menu', '/repair/engineer', 6)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET is_active = FALSE WHERE menu_code IN ('repair_workorder', 'repair_spare');

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('repair_apply', 'repair_handle', 'repair_spare_archive', 'repair_verify', 'repair_fault')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('repair_apply', 'repair_handle', 'repair_spare_archive', 'repair_verify', 'repair_fault')
ON CONFLICT DO NOTHING;
