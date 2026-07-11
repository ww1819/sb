-- MEIS 模块1：基础字典 — 独立菜单模块
-- 将供应商/厂商/68码从采购模块归并到基础字典，并补全科室/仓库/资产分类/财务分类/单位

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_dict', NULL, '基础字典', 'module', NULL, 2)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 3 WHERE menu_code = 'mod_purchase';
UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'mod_asset';
UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'mod_ops';
UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'mod_quality';
UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'mod_analytics';
UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'mod_system';

UPDATE sys_menu SET parent_code = 'mod_dict', path = '/dict/supplier', menu_name = '供应商维护', sort_order = 1
WHERE menu_code = 'purchase_supplier';
UPDATE sys_menu SET parent_code = 'mod_dict', path = '/dict/manufacturer', menu_name = '生产厂家维护', sort_order = 2
WHERE menu_code = 'purchase_manufacturer';
UPDATE sys_menu SET parent_code = 'mod_dict', path = '/dict/category', menu_name = '设备68档案', sort_order = 3
WHERE menu_code = 'purchase_category';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('dict_asset_category', 'mod_dict', '资产分类', 'menu', '/dict/asset-category', 4),
('dict_finance_category', 'mod_dict', '财务分类', 'menu', '/dict/finance-category', 5),
('dict_dept', 'mod_dict', '科室维护', 'menu', '/dict/dept', 6),
('dict_warehouse', 'mod_dict', '仓库维护', 'menu', '/dict/warehouse', 7),
('dict_unit', 'mod_dict', '单位维护', 'menu', '/dict/unit', 8)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET is_active = FALSE WHERE menu_code = 'system_dept';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_dict',
    'purchase_supplier', 'purchase_manufacturer', 'purchase_category',
    'dict_asset_category', 'dict_finance_category', 'dict_dept', 'dict_warehouse', 'dict_unit'
)
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_dict',
    'purchase_supplier', 'purchase_manufacturer', 'purchase_category',
    'dict_asset_category', 'dict_finance_category', 'dict_dept', 'dict_warehouse', 'dict_unit'
)
ON CONFLICT DO NOTHING;
