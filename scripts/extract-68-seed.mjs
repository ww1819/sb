import fs from 'fs'

const path =
  'C:/Users/Administrator/.cursor/projects/e-sbworkspace/agent-transcripts/d1eef281-696a-47ef-bc5e-5ff20b67ad19/d1eef281-696a-47ef-bc5e-5ff20b67ad19.jsonl'
const lines = fs.readFileSync(path, 'utf8').split(/\n/)
let found = null
for (let i = lines.length - 1; i >= 0; i--) {
  if (!lines[i]) continue
  if (lines[i].includes('标准分类代码') && lines[i].includes('68010101')) {
    found = lines[i]
    break
  }
}
if (!found) {
  console.error('not found')
  process.exit(1)
}
const obj = JSON.parse(found)
let text = ''
const dig = (o) => {
  if (!o) return
  if (typeof o === 'string') {
    if (o.includes('标准分类代码')) text = o
    return
  }
  if (Array.isArray(o)) return o.forEach(dig)
  if (typeof o === 'object') Object.values(o).forEach(dig)
}
dig(obj)

const out = []
for (const line of text.split(/\r?\n/)) {
  const m = line.match(/^(\d+)\t(\d{4,8})\t(.+)$/)
  if (m) out.push({ code: m[2], name: m[3].trim(), sort: Number(m[1]) })
}
console.log('rows', out.length)

const esc = (s) => '"' + s.replace(/"/g, '""') + '"'
const csv = [
  'category_code,category_name,sort_order',
  ...out.map((r) => `${r.code},${esc(r.name)},${r.sort}`)
].join('\n')
const csvPath = 'e:/sbworkspace/data/seed/medical_device_category_68.csv'
fs.writeFileSync(csvPath, csv, 'utf8')
console.log('wrote', csvPath)
