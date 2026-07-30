/** Web 端日期/时间统一格式（PLT-DT-01 / 约定包 §5.12） */

export const DATE_FMT = 'YYYY-MM-DD'
export const DATETIME_FMT = 'YYYY-MM-DD HH:mm:ss'

/** 列表/详情展示：日期 → yyyy-MM-dd；空为 — */
export function formatDisplayDate(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const s = String(value).trim()
  const m = s.match(/^(\d{4}-\d{2}-\d{2})/)
  if (m) return m[1]
  const d = new Date(s)
  if (!Number.isNaN(d.getTime())) {
    const y = d.getFullYear()
    const mo = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${mo}-${day}`
  }
  return s
}

/** 列表/详情展示：时间 → yyyy-MM-dd HH:mm:ss（24h）；空为 — */
export function formatDisplayDateTime(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const s = String(value).trim()
  // 已是目标格式
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(s)) return s.slice(0, 19)
  // ISO：2026-07-30T14:32:01.123Z / +08:00
  const iso = s.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})/)
  if (iso) return `${iso[1]} ${iso[2]}`
  const d = new Date(s)
  if (!Number.isNaN(d.getTime())) {
    const y = d.getFullYear()
    const mo = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const mi = String(d.getMinutes()).padStart(2, '0')
    const sec = String(d.getSeconds()).padStart(2, '0')
    return `${y}-${mo}-${day} ${h}:${mi}:${sec}`
  }
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
