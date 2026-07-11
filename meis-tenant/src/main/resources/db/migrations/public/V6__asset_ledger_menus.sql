-- 模块2：资产台账 — 菜单（导入 / 综合查询 / 资产管理）
UPDATE sys_menu SET menu_name = '资产台账' WHERE menu_code = 'mod_asset';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('asset_query', 'mod_asset', '资产综合查询', 'menu', '/asset/query', 1),
('asset_import', 'mod_asset', '资产导入', 'menu', '/asset/import', 2)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET menu_name = '资产管理', sort_order = 3 WHERE menu_code = 'asset_device';
UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'asset_entry';
UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'asset_outbound';
UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'asset_transfer';
UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'asset_inventory';
UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'asset_scrap';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('asset_query', 'asset_import')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('asset_query', 'asset_import')
ON CONFLICT DO NOTHING;
