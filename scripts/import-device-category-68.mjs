/**
 * 将 data/seed/medical_device_category_68.csv 导入租户 medical_device_category
 * 用法: node scripts/import-device-category-68.mjs [schema...]
 * 默认: tenant_demo tenant_hospdemo03 tenant_test
 */
import fs from 'fs'
import { spawnSync } from 'child_process'

const csvPath = 'e:/sbworkspace/data/seed/medical_device_category_68.csv'
const pgBin = 'D:/Program Files/PostgreSQL/18/bin/psql.exe'
const env = {
  ...process.env,
  PGPASSWORD: 'med123456',
  PGCLIENTENCODING: 'UTF8'
}

const schemas = process.argv.slice(2)
const targets = schemas.length ? schemas : ['tenant_demo', 'tenant_hospdemo03', 'tenant_test']

function parseCsv(text) {
  const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter(Boolean)
  const rows = []
  for (let i = 1; i < lines.length; i++) {
    // category_code,"name",sort
    const m = lines[i].match(/^(\d{4,8}),"(.*)",(\d+)$/)
    if (!m) {
      // name without quotes / escaped quotes
      const m2 = lines[i].match(/^(\d{4,8}),"((?:[^"]|"")*)",(\d+)$/)
      if (!m2) {
        console.warn('skip line', i + 1, lines[i].slice(0, 80))
        continue
      }
      rows.push({ code: m2[1], name: m2[2].replace(/""/g, '"'), sort: Number(m2[3]) })
      continue
    }
    rows.push({ code: m[1], name: m[2].replace(/""/g, '"'), sort: Number(m[3]) })
  }
  return rows
}

function parentOf(code) {
  return code.length > 4 ? code.substring(0, code.length - 2) : null
}

function levelOf(code) {
  return code.length / 2 - 1
}

function fullPath(code, map) {
  const parts = []
  for (let len = 4; len <= code.length; len += 2) {
    const n = map.get(code.substring(0, len))
    if (n) parts.push(n)
  }
  return parts.join('/')
}

function esc(s) {
  if (s == null) return 'NULL'
  return "'" + String(s).replace(/'/g, "''") + "'"
}

const raw = fs.readFileSync(csvPath, 'utf8')
const rows = parseCsv(raw)
console.log('parsed', rows.length)
rows.sort((a, b) => a.code.length - b.code.length || a.code.localeCompare(b.code))
const nameMap = new Map(rows.map((r) => [r.code, r.name]))

const valueSql = rows
  .map((r) => {
    const parent = parentOf(r.code)
    const level = levelOf(r.code)
    const path = fullPath(r.code, nameMap)
    return `(gen_random_uuid(), ${esc(r.code)}, ${esc(r.name)}, ${parent ? esc(parent) : 'NULL'}, ${level}, ${esc(path)}, ${r.sort}, TRUE, 0, NOW(), NOW())`
  })
  .join(',\n')

function runSql(schema, sql) {
  const tmp = `e:/sbworkspace/data/seed/_import_${schema}.sql`
  fs.writeFileSync(tmp, sql, 'utf8')
  const r = spawnSync(
    pgBin,
    ['-h', '43.138.177.53', '-p', '5432', '-U', 'med', '-d', 'meis', '-v', 'ON_ERROR_STOP=1', '-f', tmp],
    { env, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }
  )
  if (r.status !== 0) {
    console.error(schema, 'FAILED status=', r.status)
    console.error('stderr:', r.stderr)
    console.error('stdout:', r.stdout)
    console.error('error:', r.error)
    process.exit(r.status || 1)
  }
  const out = (r.stdout || '') + (r.stderr || '')
  console.log(schema, out.trim().split('\n').slice(-5).join(' | '))
}

for (const schema of targets) {
  console.log('---', schema)
  runSql(
    schema,
    `SET search_path TO ${schema};
ALTER TABLE medical_device_category ALTER COLUMN category_code TYPE VARCHAR(16);
ALTER TABLE medical_device_category ALTER COLUMN parent_code TYPE VARCHAR(16);
INSERT INTO medical_device_category
  (id, category_code, category_name, parent_code, level, full_path, sort_order, is_active, is_deleted, created_at, updated_at)
VALUES
${valueSql}
ON CONFLICT (category_code) DO UPDATE SET
  category_name = EXCLUDED.category_name,
  parent_code = EXCLUDED.parent_code,
  level = EXCLUDED.level,
  full_path = EXCLUDED.full_path,
  sort_order = EXCLUDED.sort_order,
  is_active = TRUE,
  is_deleted = 0,
  deleted_at = NULL,
  deleted_by = NULL,
  updated_at = NOW();
SELECT count(*) AS total, count(*) FILTER (WHERE parent_code IS NULL) AS roots
FROM medical_device_category WHERE COALESCE(is_deleted,0)=0;`
  )
}
console.log('done')
