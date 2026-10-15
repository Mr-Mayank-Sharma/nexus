import { describe, expect, it } from 'vitest'
import { byImpact, filterRows, formatDelta, reconcilable } from './posRules'
import type { PosRow } from './posApi'

const row = (sku: string, status: PosRow['status'], delta: number | null): PosRow => ({
  sku, nodeId: 'n', nexusOnHand: 5, nexusAllocated: 0, posQuantity: delta == null ? null : 5 + delta, delta, posReportedAt: null, status,
})

describe('posRules', () => {
  const rows = [row('A', 'MATCH', 0), row('B', 'DRIFT', -4), row('C', 'DRIFT', 1), row('D', 'STALE', 9), row('E', 'NO_POS_DATA', null)]

  it('only fresh differences are reconcilable', () => {
    expect(reconcilable(rows).map(r => r.sku)).toEqual(['B', 'C'])
  })

  it('orders by the size of the difference, whichever way it points', () => {
    expect(byImpact(rows).map(r => r.sku)).toEqual(['D', 'B', 'C', 'A', 'E'])
  })

  it('filters by status and by SKU text', () => {
    expect(filterRows(rows, 'DRIFT', '').map(r => r.sku)).toEqual(['B', 'C'])
    expect(filterRows(rows, 'ALL', ' d ').map(r => r.sku)).toEqual(['D'])
    expect(filterRows(rows, 'ALL', '').length).toBe(5)
  })

  it('formats differences with a sign', () => {
    expect(formatDelta(3)).toBe('+3')
    expect(formatDelta(-2)).toBe('-2')
    expect(formatDelta(0)).toBe('0')
    expect(formatDelta(null)).toBe('')
  })
})
