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
  for (const schema of ['tenant_demo', 'tenant_hospdemo03', 'tenant_test']) {
    const tables = await c.query(
      `SELECT table_name FROM information_schema.tables WHERE table_schema=$1 AND table_name LIKE '%role%' OR (table_schema=$1 AND table_name LIKE '%perm%') OR (table_schema=$1 AND table_name LIKE '%menu%') ORDER BY 1`,
      [schema]
    )
    console.log(schema, tables.rows.map((r) => r.table_name))

    // check user preferences / role json for menus
    try {
      const cols = await c.query(
        `SELECT column_name FROM information_schema.columns WHERE table_schema=$1 AND table_name='sys_role' ORDER BY 1`,
        [schema]
      )
      console.log(schema, 'sys_role cols', cols.rows.map((r) => r.column_name))
      const roles = await c.query(`SELECT id, role_code, role_name, menu_codes, permissions FROM ${schema}.sys_role LIMIT 5`)
      console.log(schema, 'roles sample', roles.rows)
    } catch (e) {
      console.log(schema, 'role inspect', e.message)
    }
  }
  await c.end()
})().catch((e) => {
  console.error(e)
  process.exit(1)
})
