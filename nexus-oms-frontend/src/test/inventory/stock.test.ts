import { describe, expect, it } from 'vitest'
import { atpForSku, available, lowStock, outOfStock, stockValue, summariseLocations, type StockRow } from '../../features/inventory/stock'

let seq = 0
const row = (p: Partial<StockRow>): StockRow => ({
  id: `r${++seq}`, sku: 'A', nodeId: 'n1', quantityOnHand: 0, quantityAllocated: 0, quantityReserved: 0, quantityInTransit: 0,
  quantityOnOrder: 0, safetyStock: 0, reorderPoint: 0, reorderQty: 0, ...p,
})

describe('available', () => {
  it('is on hand minus allocated, reserved and safety stock, floored at zero', () => {
    expect(available(row({ quantityOnHand: 100, quantityAllocated: 10, quantityReserved: 5, safetyStock: 20 }))).toBe(65)
    expect(available(row({ quantityOnHand: 5, quantityReserved: 20 }))).toBe(0)
    expect(available(row({ quantityOnHand: null, quantityAllocated: null, quantityReserved: null, safetyStock: null }))).toBe(0)
  })
})

describe('summariseLocations', () => {
  const locations = [
    { id: 'n1', name: 'Main DC', type: 'WAREHOUSE', totalCapacitySqm: 1000, usedCapacitySqm: 250 },
    { id: 'n2', name: 'Empty store', type: 'STORE' },
  ]

  it('totals each location, including ones with no stock, and reports real capacity', () => {
    const out = summariseLocations([row({ nodeId: 'n1', sku: 'A', quantityOnHand: 50, quantityAllocated: 5 }), row({ nodeId: 'n1', sku: 'B', quantityOnHand: 10 })], locations)
    const main = out.find(l => l.id === 'n1')!
    expect(main).toMatchObject({ units: 60, available: 55, committed: 5, skus: 2, capacityPct: 25 })
    expect(out.find(l => l.id === 'n2')).toMatchObject({ units: 0, skus: 0, capacityPct: null })
  })

  it('does not hide stock held at an unknown or missing location', () => {
    const out = summariseLocations([row({ nodeId: null, quantityOnHand: 7 }), row({ nodeId: 'ghost-1234-5678', quantityOnHand: 3 })], [])
    expect(out.map(l => l.units).sort()).toEqual([3, 7])
    expect(out.some(l => l.name === 'Unassigned stock')).toBe(true)
  })
})

describe('alerts', () => {
  it('flags rows at or below a defined reorder point that still have stock, most urgent first', () => {
    const rows = [
      row({ sku: 'OK', quantityOnHand: 500, reorderPoint: 100 }),
      row({ sku: 'LOW', quantityOnHand: 90, reorderPoint: 100 }),
      row({ sku: 'LOWER', quantityOnHand: 10, reorderPoint: 100 }),
      row({ sku: 'NOPOINT', quantityOnHand: 1, reorderPoint: 0 }),
      row({ sku: 'GONE', quantityOnHand: 0, reorderPoint: 100 }),
    ]
    expect(lowStock(rows).map(r => r.sku)).toEqual(['LOWER', 'LOW'])
  })

  it('lists a SKU as out of stock only when no location can promise any', () => {
    const rows = [row({ sku: 'X', nodeId: 'n1', quantityOnHand: 0 }), row({ sku: 'X', nodeId: 'n2', quantityOnHand: 5 }), row({ sku: 'Y', quantityOnHand: 2, quantityReserved: 2 })]
    expect(outOfStock(rows)).toEqual(['Y'])
  })
})

describe('stockValue', () => {
  it('values at cost and counts SKUs without a cost instead of treating them as free', () => {
    const rows = [row({ sku: 'A', quantityOnHand: 10 }), row({ sku: 'B', quantityOnHand: 4 }), row({ sku: 'C', quantityOnHand: 0 })]
    const cost = new Map<string, number | undefined>([['A', 2.5], ['C', undefined]])
    expect(stockValue(rows, cost)).toEqual({ value: 25, unpricedSkus: 1 })
  })
})

describe('atpForSku', () => {
  it('splits one SKU by location with committed, incoming and on-order units', () => {
    const rows = [
      row({ sku: 'A', nodeId: 'n1', quantityOnHand: 30, quantityAllocated: 5, quantityInTransit: 4 }),
      row({ sku: 'A', nodeId: 'n1', quantityOnHand: 10, quantityOnOrder: 6 }),
      row({ sku: 'A', nodeId: 'n2', quantityOnHand: 8 }),
      row({ sku: 'B', nodeId: 'n1', quantityOnHand: 99 }),
    ]
    const lines = atpForSku(rows, 'A')
    expect(lines).toHaveLength(2)
    expect(lines.find(l => l.nodeId === 'n1')).toEqual({ nodeId: 'n1', available: 35, committed: 5, incoming: 4, onOrder: 6 })
  })
})
