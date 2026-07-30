const { Client } = require('pg')
const c = new Client({
  host: 'localhost',
  port: 5432,
  user: 'med',
  password: 'med123456',
  database: 'meis'
})

;(async () => {
  await c.connect()
  const all = await c.query(`
    SELECT menu_code, parent_code, menu_name, path, sort_order, is_active
    FROM public.sys_menu
    WHERE menu_code LIKE 'analytics_%' OR menu_name LIKE '%效益%'
    ORDER BY parent_code NULLS FIRST, sort_order, menu_code
  `)
  console.log(JSON.stringify(all.rows, null, 2))

  const miss = await c.query(`
    SELECT menu_code FROM public.sys_menu
    WHERE menu_code IN (
      'analytics_mapping','analytics_sync','analytics_summary',
      'analytics_cost','analytics_benefit_query','analytics_device','analytics_fee_manual'
    )
  `)
  console.log('present:', miss.rows.map((r) => r.menu_code))

  const tm = await c.query(`
    SELECT t.code, t.package_code, COUNT(*) FILTER (WHERE tm.menu_code LIKE 'analytics_%') AS analytics_cnt
    FROM sys_tenant t
    LEFT JOIN sys_tenant_menu tm ON tm.tenant_id = t.id
    WHERE t.status = 'active'
    GROUP BY t.code, t.package_code
  `)
  console.log('tenants:', tm.rows)

  await c.end()
})().catch((e) => {
  console.error(e)
  process.exit(1)
})
