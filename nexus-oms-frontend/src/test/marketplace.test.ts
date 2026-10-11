import { describe, expect, it } from 'vitest'
import { formatMoney, toRow, type ChannelOrder } from '../features/marketplace/channelOrders'

const base: ChannelOrder = {
  orderId: 'AMAZON-90003', id: 'f56cea88', status: 'ALLOCATED', subStatus: null, total: 132.14, currency: 'USD',
  customerEmail: 'a@b.co', trackingNumber: null, createdAt: '2026-10-08T11:12:00', items: [{ sku: 'A', qty: 3 }, { sku: 'B', qty: 1 }],
}

describe('toRow', () => {
  it('maps only what the API sends', () => {
    const r = toRow(base)
    expect(r).toMatchObject({ id: 'f56cea88', orderId: 'AMAZON-90003', status: 'ALLOCATED', customer: 'a@b.co', items: 2 })
    expect(r.amount).toContain('132.14')
    expect(r.placedAt).not.toBe('—')
  })

  it('never invents a value: no items is 0 (not 1), no date is a dash (not now), no customer is a dash', () => {
    const r = toRow({ ...base, items: [], createdAt: null, customerEmail: null, total: null })
    expect(r.items).toBe(0)
    expect(r.placedAt).toBe('—')
    expect(r.customer).toBe('—')
    expect(r.amount).toBe('—')
  })

  it('tolerates a missing items array and an unreadable date', () => {
    const r = toRow({ ...base, items: undefined as never, createdAt: 'not a date' })
    expect(r.items).toBe(0)
    expect(r.placedAt).toBe('—')
  })
})

describe('formatMoney', () => {
  it('falls back gracefully on an invalid currency code', () => {
    expect(formatMoney(5, 'ZZZZ!')).toContain('5.00')
    expect(formatMoney(undefined, 'USD')).toBe('—')
  })
})
