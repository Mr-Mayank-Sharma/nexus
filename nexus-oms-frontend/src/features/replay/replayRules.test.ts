import { describe, expect, it } from 'vitest'
import { change, changeText, comparisonCsv, differencesCsv, headline, pct } from './replayRules'
import type { ReplayMetrics, ReplayResult } from './replayApi'

const m = (o: Partial<ReplayMetrics>): ReplayMetrics => ({
  strategy: 'NEAREST_FIRST', label: 'Nearest', orders: 100, filled: 100, partial: 0, unfillable: 0, shipments: 120, splitOrders: 20,
  splitRate: 0.2, shipmentsPerOrder: 1.2, unfilledUnits: 0, expectedFailedUnits: 10, shippingCost: 780, reworkCost: 65, totalCost: 845, ...o,
})
const result = (over: Partial<ReplayResult> = {}): ReplayResult => ({
  strategies: [m({}), m({ strategy: 'FEWEST_SHIPMENTS', label: 'Fewest', shipments: 100, splitOrders: 0, totalCost: 700 }),
    m({ strategy: 'FAILURE_AWARE', label: 'Aware', shipments: 100, splitOrders: 0, expectedFailedUnits: 4, totalCost: 676 })],
  differences: [], differencesTotal: 0, usedDistance: true, usedFailureRates: true, assumptions: [], ...over,
})

describe('replay rules', () => {
  it('formats and compares without dividing by zero', () => {
    expect(pct(0.1591)).toBe('15.9%')
    expect(change(0, 5)).toBeNull()
    expect(changeText(0, 0)).toBe('same')
    expect(changeText(0, 5)).toBe('—')
    expect(changeText(120, 100)).toBe('−16.7%')
    expect(changeText(100, 120)).toBe('+20.0%')
    expect(changeText(100, 100)).toBe('same')
  })

  it('leads with what was measured, from the numbers given', () => {
    const h = headline(result())
    expect(h).toContain('from 120 to 100')
    expect(h).toContain('split orders from 20 to 0')
    expect(h).toContain('from 10 to 4 units')
    expect(h).toContain('$845')
  })

  it('does not claim an improvement that did not happen', () => {
    const flat = result({ strategies: [m({}), m({ strategy: 'FEWEST_SHIPMENTS', shipments: 120 }), m({ strategy: 'FAILURE_AWARE', shipments: 120, expectedFailedUnits: 10, totalCost: 845 })] })
    const h = headline(flat)
    expect(h).toContain('did not reduce shipments')
    expect(h).toContain('no difference')
    expect(h).not.toContain('would have cut')
  })

  it('says nothing about failure rates when none were supplied', () => {
    expect(headline(result({ usedFailureRates: false }))).not.toContain('fail picks')
  })

  it('writes CSV that a spreadsheet cannot run as a formula', () => {
    const csv = differencesCsv([{ orderId: '=cmd()', shipmentsBefore: 2, shipmentsAfter: 1, before: 'A, B', after: 'C' }])
    expect(csv).toContain("'=cmd()")
    expect(csv).toContain('"A, B"')
    expect(comparisonCsv(result().strategies).split('\r\n')[0]).toContain('expected_failed_units')
  })
})
