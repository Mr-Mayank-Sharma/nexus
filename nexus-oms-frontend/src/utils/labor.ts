/** Labour metrics derived from the productivity data the API returns. No data means null, never a made-up figure. */

export interface LogLike { loggedAt: string; vsStandardPct: number }
export interface PerformanceLike { taskType: string; actualRate: number; standardRate: number }
export interface TaskRateLike { taskType: string; avgItemsPerHour: number }

export const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const

/** Mean efficiency (percent of standard) for Monday..Sunday; null where nothing was logged that weekday. */
export function weekdayEfficiency(logs: LogLike[]): (number | null)[] {
  const sums = Array.from({ length: 7 }, () => ({ total: 0, n: 0 }))
  for (const log of logs) {
    const t = Date.parse(log.loggedAt.endsWith('Z') || /[+-]\d\d:?\d\d$/.test(log.loggedAt) ? log.loggedAt : log.loggedAt + 'Z')
    if (Number.isNaN(t) || typeof log.vsStandardPct !== 'number' || Number.isNaN(log.vsStandardPct)) continue
    const mondayFirst = (new Date(t).getUTCDay() + 6) % 7
    sums[mondayFirst].total += log.vsStandardPct
    sums[mondayFirst].n++
  }
  return sums.map(s => (s.n ? Math.round(s.total / s.n) : null))
}

/** Mean of actual/standard for one task type, as a percentage; null with no usable rows. */
export function taskEfficiency(rows: PerformanceLike[], taskType: string): number | null {
  const ratios = rows.filter(r => r.taskType === taskType && r.standardRate > 0).map(r => (r.actualRate / r.standardRate) * 100)
  return ratios.length ? Math.round(ratios.reduce((a, b) => a + b, 0) / ratios.length) : null
}

export function taskRate(rows: TaskRateLike[], taskType: string): number | null {
  const row = rows.find(r => r.taskType === taskType)
  return row && row.avgItemsPerHour > 0 ? Math.round(row.avgItemsPerHour) : null
}

/** Share of staff who are active; null with no staff (avoids NaN%). */
export function utilization(active: number, total: number): number | null {
  return total > 0 ? Math.round((active / total) * 100) : null
}

export const percent = (n: number | null) => (n === null ? '—' : `${n}%`)
