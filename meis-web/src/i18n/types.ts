/**
 * I18N 预留：当前仅 zh-CN；后续可扩展为多 locale 文案包入口。
 * 见约定包 §5.13 / PLT-I18N-01。
 */
export type AppLocale = 'zh-CN'

export const DEFAULT_LOCALE: AppLocale = 'zh-CN'

export type ResolveCodedLabelOptions = {
  /** 字典类型，如 device_status / wo_status */
  dictType?: string
  /** 字段名，用于推断 dictType 或布尔特例 */
  prop?: string
  value: unknown
  locale?: AppLocale
  /** 运行时字典命中的中文（优先） */
  fromDict?: string | null
}
