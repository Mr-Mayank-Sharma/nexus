import type { PosRow, PosStatus } from './posApi'

export const STATUS_LABEL: Record<PosStatus, string> = {
  MATCH: 'Matches',
  DRIFT: 'Differs',
  STALE: 'Report is old',
  NO_POS_DATA: 'POS has not reported',
  NO_NEXUS_ROW: 'Not stocked in Nexus',
}

export const STATUS_HELP: Record<PosStatus, string> = {
  MATCH: 'The till and Nexus agree.',
  DRIFT: 'The till counts a different quantity. Reconcile to set Nexus to the till count.',
  STALE: 'The last report is older than this source allows. Wait for a fresh one before correcting.',
  NO_POS_DATA: 'Nexus holds stock the POS has never reported.',
  NO_NEXUS_ROW: 'The POS reports a SKU Nexus holds no stock row for at this store. Add the product first.',
}

/** The rows a reconcile would act on: fresh differences only. */
export const reconcilable = (rows: PosRow[]) => rows.filter(r => r.status === 'DRIFT')

/** Largest absolute difference first, so the worst mismatches lead the table. */
export function byImpact(rows: PosRow[]): PosRow[] {
  return [...rows].sort((a, b) => Math.abs(b.delta ?? 0) - Math.abs(a.delta ?? 0) || a.sku.localeCompare(b.sku))
}

export function filterRows(rows: PosRow[], status: PosStatus | 'ALL', search: string): PosRow[] {
  const q = search.trim().toLowerCase()
  return rows.filter(r => (status === 'ALL' || r.status === status) && (!q || r.sku.toLowerCase().includes(q)))
}

/** "+3", "-2", "0"; blank when there is nothing to compare. */
export const formatDelta = (d: number | null) => (d == null ? '' : d > 0 ? `+${d}` : String(d))

/** Parsed as UTC when the server sends a timestamp without an offset. */
export const whenText = (iso: string | null) => (iso ? new Date(iso.endsWith('Z') ? iso : iso + 'Z').toLocaleString() : 'never')
