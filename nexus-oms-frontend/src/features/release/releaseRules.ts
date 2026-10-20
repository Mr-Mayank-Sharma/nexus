import type { HeldOrder, ReleasePolicy } from './releaseApi'

const asDate = (s: string) => new Date(s + (/[zZ]|[+-]\d\d:?\d\d$/.test(s) ? '' : 'Z'))

/** "in 3 days", "in 5 hours", "due now". */
export function untilText(iso: string, now = new Date()): string {
  const ms = asDate(iso).getTime() - now.getTime()
  if (ms <= 0) return 'due now'
  const h = ms / 3_600_000
  if (h < 1) return `in ${Math.max(1, Math.round(ms / 60_000))} min`
  if (h < 48) return `in ${Math.round(h)} hours`
  return `in ${Math.round(h / 24)} days`
}

export function policyProblems(p: ReleasePolicy): string[] {
  const out: string[] = []
  for (const [k, v] of [['B2B', p.b2bDays], ['B2C', p.b2cDays]] as const) {
    if (!Number.isInteger(v) || v < 0 || v > 180) out.push(`${k} window must be a whole number of days from 0 to 180`)
  }
  return out
}

/** Units held back from reservation, summed per order type, for the header. */
export function summary(held: HeldOrder[]): { b2b: number; b2c: number; nextRelease: string | null } {
  const b2b = held.filter(h => h.orderType === 'B2B').length
  return { b2b, b2c: held.length - b2b, nextRelease: held.length ? held.map(h => h.releaseAt).sort()[0] : null }
}

/** The value a datetime-local input wants from an ISO date-time. */
export function toInputValue(iso: string | null): string {
  return iso ? iso.slice(0, 16) : ''
}
