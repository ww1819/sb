const fs = require('fs')
const p = 'meis-tenant/src/main/resources/db/migrations/public/R__data_fix.sql'
let text = fs.readFileSync(p, 'utf8')
if (text.includes('ops_inspect')) {
  console.log('already has ops_inspect')
  process.exit(0)
}
const block = `
-- ---------- INS-UI-01 / MET-UI-01：运维下巡检管理、计量管理二级分组（与保养管理同级） ----------
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('ops_inspect', 'mod_ops', '巡检管理', 'group', NULL, 9),
('ops_metrology', 'mod_ops', '计量管理', 'group', NULL, 10)
ON CONFLICT (menu_code) DO UPDATE SET
    parent_code = EXCLUDED.parent_code,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE;

UPDATE sys_menu SET parent_code = 'ops_inspect', menu_name = '巡检参数设置', path = '/inspect/param',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'inspect_param';
UPDATE sys_menu SET parent_code = 'ops_inspect', menu_name = '巡检计划', path = '/inspect/plan',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'inspect_plan';
UPDATE sys_menu SET parent_code = 'ops_inspect', menu_name = '巡检执行', path = '/inspect/execution',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'inspect_execution';
UPDATE sys_menu SET parent_code = 'ops_inspect', menu_name = '巡检记录查询', path = '/inspect/query',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'inspect_query';

UPDATE sys_menu SET parent_code = 'ops_metrology', menu_name = '计量参数设置', path = '/metrology/param',
    sort_order = 1, is_active = TRUE
WHERE menu_code = 'metrology_param';
UPDATE sys_menu SET parent_code = 'ops_metrology', menu_name = '计量计划', path = '/metrology/plan',
    sort_order = 2, is_active = TRUE
WHERE menu_code = 'metrology_plan';
UPDATE sys_menu SET parent_code = 'ops_metrology', menu_name = '计量执行', path = '/metrology/execution',
    sort_order = 3, is_active = TRUE
WHERE menu_code = 'metrology_execution';
UPDATE sys_menu SET parent_code = 'ops_metrology', menu_name = '计量记录查询', path = '/metrology/query',
    sort_order = 4, is_active = TRUE
WHERE menu_code = 'metrology_query';

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, m.menu_code
FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('ops_inspect', 'ops_metrology')
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT t.id, m.menu_code
FROM sys_tenant t
CROSS JOIN sys_menu m
WHERE m.menu_code IN ('ops_inspect', 'ops_metrology')
ON CONFLICT DO NOTHING;
`
if (!text.endsWith('\n')) text += '\n'
fs.writeFileSync(p, text + block, 'utf8')
console.log('appended OK')
