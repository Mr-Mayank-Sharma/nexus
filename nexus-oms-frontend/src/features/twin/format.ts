/** Display formatting shared by the twin pages. */

export function fmtTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

export function fmtNumber(n: number | null | undefined): string {
  return n === null || n === undefined ? '—' : n.toLocaleString()
}

/** Colour and label for a status chip on the twin pages. */
export function statusTone(status: string | null | undefined): { label: string; tone: 'green' | 'blue' | 'amber' | 'red' | 'slate' } {
  const s = (status ?? '').toUpperCase()
  const label = s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') : 'Unknown'
  if (['AVAILABLE', 'IN_STOCK', 'DELIVERED', 'COMPLETED', 'OPERATIONAL', 'ACTIVE', 'IDLE'].includes(s)) return { label, tone: 'green' }
  if (['IN_PROGRESS', 'LOADING', 'UNLOADING', 'DOCKED', 'OCCUPIED', 'IN_USE', 'CHECKED_IN', 'SHIPPED'].includes(s)) return { label, tone: 'blue' }
  if (['SCHEDULED', 'CONFIRMED', 'IN_TRANSIT', 'EN_ROUTE', 'LOW_STOCK', 'MAINTENANCE', 'CHARGING', 'AT_GATE'].includes(s)) return { label, tone: 'amber' }
  if (['OUT_OF_STOCK', 'CANCELLED', 'NO_SHOW', 'FAILED', 'OFFLINE', 'DOWN', 'BLOCKED'].includes(s)) return { label, tone: 'red' }
  return { label, tone: 'slate' }
}
