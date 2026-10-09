/** Display formatting shared by the twin pages. */

export function fmtTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

export function fmtNumber(n: number | null | undefined): string {
  return n === null || n === undefined ? '—' : n.toLocaleString()
}
