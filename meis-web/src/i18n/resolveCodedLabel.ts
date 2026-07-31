import type { AppLocale, ResolveCodedLabelOptions } from './types'
import { DEFAULT_LOCALE } from './types'
import { inferDictType, lookupStatusCatalog } from './statusCatalog.zh-CN'

/** 未命中字典/目录时的统一回退（禁止裸码直出） */
export function unknownCodedLabel(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return `未知(${String(value)})`
}

/**
 * 统一业务码标签解析（I18N 预留入口）。
 * 优先级：运行时字典 → 静态 catalog → 未知(码)
 */
export function resolveCodedLabel(opts: ResolveCodedLabelOptions): string {
  const locale: AppLocale = opts.locale ?? DEFAULT_LOCALE
  const { value, prop, fromDict } = opts
  if (value === null || value === undefined || value === '') return '—'

  if (fromDict) return fromDict

  const dictType = inferDictType(prop, opts.dictType)
  const fromCatalog = lookupStatusCatalog(dictType, value, locale)
  if (fromCatalog) return fromCatalog

  // 已是中文则原样（后端偶发直接给中文标签）
  const s = String(value)
  if (/[\u4e00-\u9fff]/.test(s)) return s

  return unknownCodedLabel(value)
}
