export type StatusTagType = '' | 'success' | 'warning' | 'danger' | 'info'

const STATUS_PROP_PATTERN = /status|state|urgency|priority|phase|stage/i
const AMOUNT_PROP_PATTERN = /amount|budget|price|total|cost|fee|quantity|num|count|value/i

export function isStatusField(prop: string, dictType?: string) {
  if (dictType && /status|urgency|priority|state/i.test(dictType)) return true
  if (prop === 'is_active' || prop === 'is_clinical') return true
  return STATUS_PROP_PATTERN.test(prop)
}

export function isAmountField(prop: string, type?: string) {
  if (type === 'number' && AMOUNT_PROP_PATTERN.test(prop)) return true
  return /amount|budget|price|total|cost|fee/i.test(prop)
}

export function isNumericField(prop: string, type?: string) {
  if (type === 'number') return true
  return /sort_order|year|month|day|count|quantity|num/i.test(prop)
}

export function isBooleanField(prop: string, value: unknown) {
  if (prop.startsWith('is_')) return true
  return typeof value === 'boolean'
}

export function formatCellNumber(value: unknown, asAmount = false) {
  if (value === null || value === undefined || value === '') return '-'
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value)
  if (asAmount) {
    return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }
  return n.toLocaleString('zh-CN')
}

export function statusTagType(value: unknown): StatusTagType {
  if (value === true || value === 'true' || value === 1 || value === '1') return 'success'
  if (value === false || value === 'false' || value === 0 || value === '0') return 'info'

  const s = String(value ?? '').toLowerCase()
  if (['active', 'enabled', 'approved', 'completed', 'done', 'normal', '在用', '启用', '正常', '已通过', '已完成'].some((k) => s.includes(k))) {
    return 'success'
  }
  if (['pending', 'processing', 'draft', 'waiting', '待', '审批中', '处理中', '进行中'].some((k) => s.includes(k))) {
    return 'warning'
  }
  if (['disabled', 'rejected', 'cancelled', 'failed', 'closed', 'scrap', '停用', '驳回', '取消', '报废', '关闭'].some((k) => s.includes(k))) {
    return 'danger'
  }
  return 'info'
}

export function formatStatusLabel(value: unknown, prop?: string) {
  if (prop === 'is_active') {
    if (value === true || value === 'true' || value === 1 || value === '1') return '启用'
    if (value === false || value === 'false' || value === 0 || value === '0') return '停用'
  }
  if (prop === 'is_clinical') {
    if (value === true || value === 'true' || value === 1 || value === '1') return '临床'
    if (value === false || value === 'false' || value === 0 || value === '0') return '非临床'
  }
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

export function columnAlign(prop: string, type?: string) {
  if (isAmountField(prop, type) || isNumericField(prop, type)) return 'right'
  return undefined
}
