import { describe, expect, it } from 'vitest'
import { policyProblems, summary, toInputValue, untilText } from './releaseRules'

describe('release rules', () => {
  const now = new Date('2026-10-20T10:00:00Z')
  it('says how long until release', () => {
    expect(untilText('2026-10-20T09:00:00', now)).toBe('due now')
    expect(untilText('2026-10-20T10:20:00', now)).toBe('in 20 min')
    expect(untilText('2026-10-21T10:00:00', now)).toBe('in 24 hours')
    expect(untilText('2026-11-01T10:00:00Z', now)).toBe('in 12 days')
  })
  it('checks the windows', () => {
    expect(policyProblems({ b2bDays: 3, b2cDays: 1 })).toEqual([])
    expect(policyProblems({ b2bDays: -1, b2cDays: 1.5 })).toHaveLength(2)
    expect(policyProblems({ b2bDays: 181, b2cDays: 0 })[0]).toMatch(/^B2B/)
  })
  it('summarises the holds', () => {
    const h = (t: 'B2B' | 'B2C', r: string) => ({ id: r, orderNumber: null, channel: null, orderType: t, requestedShipDate: r, releaseAt: r, status: 'PENDING', customerEmail: null, total: null, currency: null })
    expect(summary([h('B2B', '2026-12-01T00:00'), h('B2C', '2026-11-01T00:00'), h('B2B', '2027-01-01T00:00')])).toEqual({ b2b: 2, b2c: 1, nextRelease: '2026-11-01T00:00' })
    expect(summary([])).toEqual({ b2b: 0, b2c: 0, nextRelease: null })
  })
  it('formats for a datetime input', () => {
    expect(toInputValue('2027-01-15T09:30:00')).toBe('2027-01-15T09:30')
    expect(toInputValue(null)).toBe('')
  })
})
