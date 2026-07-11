-- 模块8：库房管理 — 独立菜单模块

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('mod_warehouse', NULL, '库房管理', 'module', NULL, 5)
ON CONFLICT (menu_code) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'mod_ops';
UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'mod_quality';
UPDATE sys_menu SET sort_order = 8 WHERE menu_code = 'mod_analytics';
UPDATE sys_menu SET sort_order = 9 WHERE menu_code = 'mod_system';

INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('warehouse_setting', 'mod_warehouse', '库房维护', 'menu', '/warehouse/setting', 1),
('warehouse_entry', 'mod_warehouse', '设备入库', 'menu', '/warehouse/entry', 2),
('warehouse_outbound', 'mod_warehouse', '设备出库', 'menu', '/warehouse/outbound', 3),
('warehouse_return', 'mod_warehouse', '设备退货', 'menu', '/warehouse/return', 4),
('warehouse_transfer', 'mod_warehouse', '库房调拨', 'menu', '/warehouse/transfer', 5),
('warehouse_inventory', 'mod_warehouse', '库存盘点', 'menu', '/warehouse/inventory', 6),
('warehouse_scrap', 'mod_warehouse', '设备报废', 'menu', '/warehouse/scrap', 7)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET is_active = FALSE WHERE menu_code IN (
    'asset_entry', 'asset_outbound', 'asset_transfer', 'asset_inventory', 'asset_scrap'
);

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_warehouse',
    'warehouse_setting', 'warehouse_entry', 'warehouse_outbound', 'warehouse_return',
    'warehouse_transfer', 'warehouse_inventory', 'warehouse_scrap'
)
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN (
    'mod_warehouse',
    'warehouse_setting', 'warehouse_entry', 'warehouse_outbound', 'warehouse_return',
    'warehouse_transfer', 'warehouse_inventory', 'warehouse_scrap'
)
ON CONFLICT DO NOTHING;
