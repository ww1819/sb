import { resolveCodedLabel } from '@/i18n/resolveCodedLabel'

export type StatusTagType = '' | 'success' | 'warning' | 'danger' | 'info'

/** 仅匹配独立词段，避免 station_name 被 state 误伤（PLT-STATUS-CN-01 修订） */
const STATUS_PROP_PATTERN = /(^|_)(status|state|urgency|priority|phase|stage)(_|$)/i
const STATUS_DICT_PATTERN = /(^|_)(status|state|urgency|priority|phase|stage)(_|$)/i
const AMOUNT_PROP_PATTERN = /amount|budget|price|total|cost|fee|quantity|num|count|value/i

export function isStatusField(prop: string, dictType?: string) {
  if (dictType && STATUS_DICT_PATTERN.test(dictType)) return true
  if (prop === 'is_active' || prop === 'is_clinical') return true
  return STATUS_PROP_PATTERN.test(prop)
}

export function isAmountField(prop: string, type?: string) {
  if (type === 'number' && AMOUNT_PROP_PATTERN.test(prop)) return true
  return /amount|budget|price|total|cost|fee/i.test(prop)
}

export function isNumericField(prop: string, type?: string) {
  if (type === 'date' || type === 'datetime' || type === 'file') return false
  // 年度按普通文本展示，避免 toLocaleString 千分位（如 2,026）
  if (/(^|_)year$/i.test(prop)) return false
  if (type === 'number') return true
  // 用边界匹配，避免 fill_date 被 day、plan_year 以外误伤
  return /(^|_)(sort_order|month|day|count|quantity|num)(_|$)/i.test(prop)
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
  // 未提交/草稿：中性灰蓝，与「审批中」橙色区分（须先于泛匹配「待」）
  if (s === 'draft' || s.includes('未提交') || s === '草稿') return 'info'
  if (['active', 'enabled', 'approved', 'completed', 'done', 'normal', '在用', '启用', '正常', '已通过', '已完成'].some((k) => s.includes(k))) {
    return 'success'
  }
  if (['pending', 'processing', 'waiting', '审批中', '处理中', '进行中', '待'].some((k) => s.includes(k))) {
    return 'warning'
  }
  if (['disabled', 'rejected', 'cancelled', 'failed', 'closed', 'scrap', 'returned', '停用', '驳回', '取消', '报废', '关闭', '退货', '已退货'].some((k) => s.includes(k))) {
    return 'danger'
  }
  return 'info'
}

export function formatStatusLabel(value: unknown, prop?: string, dictType?: string) {
  if (prop === 'is_active') {
    if (value === true || value === 'true' || value === 1 || value === '1') return '启用'
    if (value === false || value === 'false' || value === 0 || value === '0') return '停用'
  }
  if (prop === 'is_clinical') {
    if (value === true || value === 'true' || value === 1 || value === '1') return '临床'
    if (value === false || value === 'false' || value === 0 || value === '0') return '非临床'
  }
  if (value === null || value === undefined || value === '') return '-'
  // 其余布尔 / is_*：统一是/否，避免列表直接显示 true/false
  if (prop?.startsWith('is_') || typeof value === 'boolean') {
    if (value === true || value === 'true' || value === 1 || value === '1') return '是'
    if (value === false || value === 'false' || value === 0 || value === '0') return '否'
  }
  // 仅状态/字典码走解析；属性类字段禁止套「未知(码)」
  if (dictType || (prop && isStatusField(prop, dictType))) {
    return resolveCodedLabel({ value, prop, dictType })
  }
  return String(value)
}

export function columnAlign(prop: string, type?: string) {
  if (isAmountField(prop, type) || isNumericField(prop, type)) return 'right'
  return undefined
}
