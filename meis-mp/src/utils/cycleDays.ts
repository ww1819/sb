const UNIT_DAYS: Record<string, number> = {
  day: 1,
  week: 7,
  month: 30,
  year: 365
}

export function calcCycleDays(cycleType: unknown, cycleValue: unknown): number | null {
  if (cycleType == null || cycleType === '') return null
  const unit = UNIT_DAYS[String(cycleType).trim().toLowerCase()]
  if (unit == null) return null
  const n = typeof cycleValue === 'number' ? cycleValue : Number(cycleValue)
  if (!Number.isFinite(n) || n <= 0) return null
  return Math.round(n * unit)
}

export function todayYmd(): string {
  const n = new Date()
  const y = n.getFullYear()
  const m = String(n.getMonth() + 1).padStart(2, '0')
  const d = String(n.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
