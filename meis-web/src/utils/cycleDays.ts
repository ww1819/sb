/** 周期类型 → 单位天数（与 OPS.15.1 / CycleDaysSupport 一致） */
const UNIT_DAYS: Record<string, number> = {
  day: 1,
  week: 7,
  month: 30,
  year: 365
}

/**
 * 由周期类型 + 周期值计算周期天数。
 * 类型或值无效时返回 null（调用方应清空 cycle_days）。
 */
export function calcCycleDays(cycleType: unknown, cycleValue: unknown): number | null {
  if (cycleType == null || cycleType === '') return null
  const unit = UNIT_DAYS[String(cycleType).trim().toLowerCase()]
  if (unit == null) return null
  const n = typeof cycleValue === 'number' ? cycleValue : Number(cycleValue)
  if (!Number.isFinite(n) || n <= 0) return null
  return Math.round(n * unit)
}

/** 表单模型上同步 cycle_days（有类型+值则写入，否则清空） */
export function syncCycleDays(model: Record<string, unknown>) {
  if (!('cycle_type' in model) && !('cycle_value' in model) && !('cycle_days' in model)) return
  const days = calcCycleDays(model.cycle_type, model.cycle_value)
  if (days == null) {
    if (model.cycle_days != null && model.cycle_days !== '') model.cycle_days = null
    return
  }
  if (model.cycle_days !== days) model.cycle_days = days
}
