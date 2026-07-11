-- 模块7：采购管理 — 菜单对齐（申请/审批/合同/验收）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_apply', 'mod_purchase', '采购申请', 'menu', '/purchase/apply', 1),
('purchase_approval', 'mod_purchase', '采购审批', 'menu', '/purchase/approval', 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET menu_name = '设备合同管理', sort_order = 4 WHERE menu_code = 'purchase_contract';
UPDATE sys_menu SET menu_name = '安装验收', sort_order = 5 WHERE menu_code = 'purchase_acceptance';
UPDATE sys_menu SET menu_name = '采购项目', sort_order = 3 WHERE menu_code = 'purchase_project';
UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'purchase_plan';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('purchase_apply', 'purchase_approval')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('purchase_apply', 'purchase_approval')
ON CONFLICT DO NOTHING;
