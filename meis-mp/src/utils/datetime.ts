/** 小程序日期/时间统一格式（PLT-DT-01 / 约定包 §5.12） */

export const DATE_FMT = 'YYYY-MM-DD'
export const DATETIME_FMT = 'YYYY-MM-DD HH:mm:ss'

function pad2(n: number) {
  return n < 10 ? `0${n}` : String(n)
}

/** Date → yyyy-MM-dd */
export function formatDate(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** Date → yyyy-MM-dd HH:mm:ss */
export function formatDateTime(d: Date): string {
  return `${formatDate(d)} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

/** 列表/详情展示：日期 → yyyy-MM-dd；空为 — */
export function formatDisplayDate(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const s = String(value).trim()
  const m = s.match(/^(\d{4}-\d{2}-\d{2})/)
  if (m) return m[1]
  const d = new Date(s)
  if (!Number.isNaN(d.getTime())) return formatDate(d)
  return s
}

/** 列表/详情展示：时间 → yyyy-MM-dd HH:mm:ss（24h）；空为 — */
export function formatDisplayDateTime(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const s = String(value).trim()
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(s)) return s.slice(0, 19)
  const iso = s.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})/)
  if (iso) return `${iso[1]} ${iso[2]}`
  const d = new Date(s)
  if (!Number.isNaN(d.getTime())) return formatDateTime(d)
  return s
}

/** 仅日期入参规范化（查询/表单） */
export function toDateParam(value: unknown): string {
  const s = formatDisplayDate(value)
  return s === '—' ? '' : s
}

/** 日期时间入参规范化（查询/表单，空格分隔、无 T） */
export function toDateTimeParam(value: unknown): string {
  const s = formatDisplayDateTime(value)
  return s === '—' ? '' : s
}
