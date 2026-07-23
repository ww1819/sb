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

/** 解析计划周期天数（优先 cycle_days，否则 type×value，默认 30） */
export function resolvePlanCycleDays(plan: Record<string, unknown> | null | undefined): number {
  if (!plan) return 30
  const raw = plan.cycle_days
  if (raw != null && raw !== '') {
    const n = typeof raw === 'number' ? raw : Number(raw)
    if (Number.isFinite(n) && n > 0) return Math.round(n)
  }
  return calcCycleDays(plan.cycle_type, plan.cycle_value) ?? 30
}

/**
 * 明细下次到期：基准日（上次完成或今天）+ 周期天数，返回 YYYY-MM-DD。
 */
export function calcItemNextDueDate(
  plan: Record<string, unknown> | null | undefined,
  lastDoneDate?: unknown
): string {
  const days = resolvePlanCycleDays(plan)
  let base: Date
  if (lastDoneDate != null && String(lastDoneDate).trim()) {
    const s = String(lastDoneDate).trim().slice(0, 10)
    base = new Date(s + 'T00:00:00')
  } else {
    base = new Date()
    base.setHours(0, 0, 0, 0)
  }
  if (Number.isNaN(base.getTime())) {
    base = new Date()
    base.setHours(0, 0, 0, 0)
  }
  base.setDate(base.getDate() + days)
  const y = base.getFullYear()
  const m = String(base.getMonth() + 1).padStart(2, '0')
  const d = String(base.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
