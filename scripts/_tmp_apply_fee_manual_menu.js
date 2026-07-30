const { Client } = require('pg')

const c = new Client({
  host: '43.138.177.53',
  port: 5432,
  user: 'med',
  password: 'med123456',
  database: 'meis'
})

;(async () => {
  await c.connect()
  console.log('connected remote')

  await c.query(`
    INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active)
    VALUES ('analytics_fee_manual', 'analytics_benefit_group', '费用手工登记', 'menu', '/analytics/fee-manual', NULL, 1, TRUE)
    ON CONFLICT (menu_code) DO UPDATE SET
      parent_code=EXCLUDED.parent_code,
      menu_name=EXCLUDED.menu_name,
      menu_type=EXCLUDED.menu_type,
      path=EXCLUDED.path,
      icon=EXCLUDED.icon,
      sort_order=EXCLUDED.sort_order,
      is_active=EXCLUDED.is_active
  `)

  await c.query(`UPDATE sys_menu SET sort_order = 2 WHERE menu_code = 'analytics_mapping'`)
  await c.query(`UPDATE sys_menu SET sort_order = 3 WHERE menu_code = 'analytics_sync'`)
  await c.query(`UPDATE sys_menu SET sort_order = 4 WHERE menu_code = 'analytics_summary'`)
  await c.query(`UPDATE sys_menu SET sort_order = 5 WHERE menu_code = 'analytics_cost'`)
  await c.query(`UPDATE sys_menu SET sort_order = 6 WHERE menu_code = 'analytics_benefit_query'`)
  await c.query(`UPDATE sys_menu SET sort_order = 7 WHERE menu_code = 'analytics_device'`)

  await c.query(`
    INSERT INTO sys_package_menu (package_code, menu_code) VALUES
      ('flagship','analytics_fee_manual'),
      ('professional','analytics_fee_manual'),
      ('standard','analytics_fee_manual')
    ON CONFLICT DO NOTHING
  `)

  const tenantIns = await c.query(`
    INSERT INTO sys_tenant_menu (tenant_id, menu_code)
    SELECT t.id, 'analytics_fee_manual'
    FROM sys_tenant t
    JOIN sys_package_menu pm
      ON pm.package_code = COALESCE(t.package_code, 'standard')
     AND pm.menu_code = 'analytics_fee_manual'
    WHERE t.status = 'active'
    ON CONFLICT DO NOTHING
  `)

  // 角色菜单（租户 schema）
  const schemas = await c.query(`
    SELECT nspname FROM pg_namespace
    WHERE nspname LIKE 't_%' OR nspname LIKE 'tenant_%'
    ORDER BY 1
  `)
  console.log('tenant schemas sample:', schemas.rows.slice(0, 10))

  for (const row of schemas.rows) {
    const schema = row.nspname
    try {
      const exists = await c.query(
        `SELECT to_regclass($1) AS reg`,
        [`${schema}.sys_role_menu`]
      )
      if (!exists.rows[0].reg) continue
      const r = await c.query(`
        INSERT INTO ${schema}.sys_role_menu (role_id, menu_code)
        SELECT r.id, 'analytics_fee_manual'
        FROM ${schema}.sys_role r
        WHERE COALESCE(r.deleted, false) = false
          AND (
            r.role_code IN ('admin', 'tenant_admin', 'system_admin', 'ADMIN')
            OR r.role_name ILIKE '%管理%'
            OR r.permission_mode = 'all'
          )
        ON CONFLICT DO NOTHING
      `)
      if (r.rowCount) console.log('role_menu', schema, r.rowCount)
    } catch (e) {
      console.log('role_menu skip', schema, e.message)
    }
  }

  const menus = await c.query(`
    SELECT menu_code, menu_name, sort_order
    FROM public.sys_menu
    WHERE parent_code = 'analytics_benefit_group'
    ORDER BY sort_order
  `)
  const tenantCnt = await c.query(`
    SELECT COUNT(*)::int AS cnt
    FROM public.sys_tenant_menu
    WHERE menu_code = 'analytics_fee_manual'
  `)

  console.log('benefit menus:', menus.rows)
  console.log('tenant_menu inserted:', tenantIns.rowCount, 'total:', tenantCnt.rows[0].cnt)
  await c.end()
})().catch((e) => {
  console.error(e)
  process.exit(1)
})
