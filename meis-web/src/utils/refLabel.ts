import type { RefSelectMeta } from '@/config/refSelectConfig'

/** 外键行展示：强业务主数据默认「编码 名称」（PLT-REF-CODE-01） */
export function formatRefRowLabel(
  row: Record<string, unknown>,
  meta: RefSelectMeta,
  hideCode = false
): string {
  const vk = meta.valueKey ?? 'id'
  const name = row[meta.labelKey]
  const code = meta.codeKey ? row[meta.codeKey] : null
  const withCode = !hideCode && meta.showCode !== false
  if (withCode && code != null && code !== '' && name != null && name !== '') {
    return `${code} ${name}`
  }
  if (name != null && name !== '') return String(name)
  if (code != null && code !== '') return String(code)
  return String(row[vk] ?? row.id ?? '')
}
