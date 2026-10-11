/** Stock arithmetic for the multi-location inventory screen. Mirrors what the order allocator can promise. */

export interface StockRow {
  id: string
  sku: string
  nodeId: string | null
  quantityOnHand: number | null
  quantityAllocated: number | null
  quantityReserved: number | null
  quantityInTransit: number | null
  quantityOnOrder: number | null
  safetyStock: number | null
  reorderPoint: number | null
  reorderQty: number | null
}

export interface LocationLike {
  id: string
  name: string
  type?: string | null
  totalCapacitySqm?: number | null
  usedCapacitySqm?: number | null
}

const n = (v: number | null | undefined) => v ?? 0

/** What can be promised from this row: on hand − allocated − reserved − safety stock, never below zero. */
export function available(row: StockRow): number {
  return Math.max(0, n(row.quantityOnHand) - n(row.quantityAllocated) - n(row.quantityReserved) - n(row.safetyStock))
}

export interface LocationSummary {
  id: string
  name: string
  type: string
  units: number
  available: number
  committed: number
  skus: number
  /** Percent of floor space used, or null when the location has no capacity recorded. */
  capacityPct: number | null
}

export function summariseLocations(rows: StockRow[], locations: LocationLike[]): LocationSummary[] {
  const byNode = new Map<string, StockRow[]>()
  for (const r of rows) {
    const key = r.nodeId ?? 'unassigned'
    byNode.set(key, [...(byNode.get(key) ?? []), r])
  }
  const known = new Map(locations.map(l => [l.id, l]))
  const ids = new Set<string>([...locations.map(l => l.id), ...byNode.keys()])
  return [...ids].map(id => {
    const rs = byNode.get(id) ?? []
    const loc = known.get(id)
    const total = n(loc?.totalCapacitySqm)
    return {
      id,
      name: loc?.name ?? (id === 'unassigned' ? 'Unassigned stock' : `Unknown location ${id.slice(0, 8)}`),
      type: loc?.type ?? '',
      units: rs.reduce((s, r) => s + n(r.quantityOnHand), 0),
      available: rs.reduce((s, r) => s + available(r), 0),
      committed: rs.reduce((s, r) => s + n(r.quantityAllocated) + n(r.quantityReserved), 0),
      skus: new Set(rs.map(r => r.sku)).size,
      capacityPct: total > 0 ? Math.min(100, Math.round((n(loc?.usedCapacitySqm) / total) * 100)) : null,
    }
  }).sort((a, b) => b.units - a.units || a.name.localeCompare(b.name))
}

/** Rows at or below their reorder point (only rows that define one) that still have some stock. */
export function lowStock(rows: StockRow[]): StockRow[] {
  return rows.filter(r => n(r.reorderPoint) > 0 && available(r) <= n(r.reorderPoint) && available(r) > 0)
    .sort((a, b) => available(a) / n(a.reorderPoint) - available(b) / n(b.reorderPoint))
}

/** SKUs with nothing left to promise across every location. */
export function outOfStock(rows: StockRow[]): string[] {
  const total = new Map<string, number>()
  for (const r of rows) total.set(r.sku, (total.get(r.sku) ?? 0) + available(r))
  return [...total.entries()].filter(([, qty]) => qty === 0).map(([sku]) => sku).sort()
}

/** Stock value at cost; SKUs with no cost price are counted separately instead of valued at zero silently. */
export function stockValue(rows: StockRow[], costBySku: Map<string, number | undefined>): { value: number; unpricedSkus: number } {
  let value = 0
  const unpriced = new Set<string>()
  for (const r of rows) {
    const cost = costBySku.get(r.sku)
    if (cost == null) { if (n(r.quantityOnHand) > 0) unpriced.add(r.sku); continue }
    value += n(r.quantityOnHand) * cost
  }
  return { value, unpricedSkus: unpriced.size }
}

export interface AtpLine { nodeId: string | null; available: number; committed: number; incoming: number; onOrder: number }

/** Per-location promise for one SKU: what is free, what is spoken for, and what is on its way. */
export function atpForSku(rows: StockRow[], sku: string): AtpLine[] {
  const byNode = new Map<string, AtpLine>()
  for (const r of rows.filter(x => x.sku === sku)) {
    const key = r.nodeId ?? 'unassigned'
    const line = byNode.get(key) ?? { nodeId: r.nodeId, available: 0, committed: 0, incoming: 0, onOrder: 0 }
    line.available += available(r)
    line.committed += n(r.quantityAllocated) + n(r.quantityReserved)
    line.incoming += n(r.quantityInTransit)
    line.onOrder += n(r.quantityOnOrder)
    byNode.set(key, line)
  }
  return [...byNode.values()]
}
