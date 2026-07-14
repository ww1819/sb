-- 库存查询（资产管理）+ 仓库维护（系统管理）

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('asset_stock_query', 'mod_asset', '库存查询', 'menu', '/asset/stock', 3)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 1 WHERE menu_code = 'asset_device';
UPDATE sys_menu SET sort_order = 2, is_active = TRUE WHERE menu_code = 'asset_entry';
UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'asset_outbound';
UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'asset_transfer';
UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'asset_inventory';
UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'asset_scrap';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('system_warehouse', 'mod_system', '仓库维护', 'menu', '/system/warehouse', 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 1 WHERE menu_code = 'system_campus';
UPDATE sys_menu SET sort_order = 3 WHERE menu_code = 'system_dept';
UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'system_user';
UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'system_role';
UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'system_dict';
UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'system_log';
UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'system_approval';
UPDATE sys_menu SET sort_order = 9 WHERE menu_code = 'purchase_supplier';
UPDATE sys_menu SET sort_order = 10 WHERE menu_code = 'purchase_category';
UPDATE sys_menu SET sort_order = 11 WHERE menu_code = 'purchase_manufacturer';
UPDATE sys_menu SET sort_order = 12 WHERE menu_code = 'system_config';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship'), ('professional')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('asset_stock_query', 'system_warehouse')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('asset_stock_query', 'system_warehouse')
ON CONFLICT DO NOTHING;
