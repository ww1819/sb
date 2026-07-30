const { Client } = require('pg')

async function tryHost(host) {
  const c = new Client({
    host,
    port: 5432,
    user: 'med',
    password: 'med123456',
    database: 'meis',
    connectionTimeoutMillis: 5000
  })
  await c.connect()
  console.log('OK', host)
  const r = await c.query(`
    SELECT menu_code, parent_code, menu_name, sort_order, is_active
    FROM public.sys_menu
    WHERE parent_code = 'analytics_benefit_group' OR menu_code = 'analytics_benefit_group'
    ORDER BY sort_order NULLS FIRST, menu_code
  `)
  console.log(host, 'rows', r.rows.length)
  console.log(r.rows)
  await c.end()
  return r.rows.length
}

;(async () => {
  for (const h of ['localhost', '127.0.0.1', '43.138.177.53']) {
    try {
      await tryHost(h)
    } catch (e) {
      console.error(h, e.message)
    }
  }
})()
