import { describe, expect, it } from 'vitest'
import { normalizeOrder, normalizeOrderList, parseAddress } from '../../api/normalizeOrder'

/** The shape GET /orders actually returns (verified against the running API). */
const api = {
  id: '6e273f1d-2f99-41c9-9d0b-80c409accbff', channelOrderId: 'SHP-78441', externalId: null, customerId: null, customerName: 'Ada Lovelace',
  channel: 'SHOPIFY', status: 'SHIPPED', subStatus: 'EXCEPTION', items: [{ sku: 'SKU-1', productName: 'Widget', quantity: 2, unitPrice: 5 }],
  total: 12, subtotal: 10, shippingCost: 1, taxAmount: 1, currency: 'USD', shipTo: { street: '1 Main St', city: 'Hoboken', state: 'NJ', zip: '07030', country: 'US' },
  fulfillmentType: 'STANDARD', allocatedNode: 'node-1', carrierId: 'UPS', trackingNumber: '1Z', promisedDelivery: '2026-10-20', shippedAt: '2026-10-12T10:00:00Z',
  deliveredAt: null, paymentStatus: 'PAID', paymentReference: 'ch_1', createdAt: '2026-10-08T11:12:00Z', updatedAt: '2026-10-09T00:00:00Z',
}

describe('normalizeOrder', () => {
  it('maps the API field names onto the ones the screens read', () => {
    const o = normalizeOrder(api as any)
    expect(o.carrier).toBe('UPS')
    expect(o.promisedDeliveryDate).toBe('2026-10-20')
    expect(o.shippedDate).toBe('2026-10-12T10:00:00Z')
    expect(o.tax).toBe(1)
    expect(o.allocationNodeId).toBe('node-1')
    expect(o.shippingAddress).toEqual({ street: '1 Main St', city: 'Hoboken', state: 'NJ', zip: '07030', country: 'US' })
  })

  it('derives the order number and exception flag, and passes payment fields through', () => {
    const o = normalizeOrder(api as any)
    expect(o.orderNumber).toBe('SHP-78441')
    expect(o.hasException).toBe(true)
    expect(o.paymentStatus).toBe('PAID')
    expect(o.items[0].totalPrice).toBe(10)
  })

  it('copes with a sparse order: no crash, safe blanks', () => {
    const o = normalizeOrder({ id: 'abcdef12-0000' } as any)
    expect(o.orderNumber).toBe('ORD-ABCDEF12')
    expect(o.customerName).toBe('Guest Customer')
    expect(o.shippingAddress.street).toBe('')
    expect(o.items).toEqual([])
    expect(normalizeOrder(null).id).toBe('')
  })

  it('is idempotent', () => {
    const once = normalizeOrder(api as any)
    expect(normalizeOrder(once)).toEqual(once)
  })

  it('normalises arrays and Spring pages alike', () => {
    expect(normalizeOrderList([api as any])[0].carrier).toBe('UPS')
    const page = normalizeOrderList({ content: [api as any], totalElements: 1 } as any) as any
    expect(page.totalElements).toBe(1)
    expect(page.content[0].carrier).toBe('UPS')
  })
})

describe('parseAddress', () => {
  it('reads objects, strings and nothing', () => {
    expect(parseAddress('1 Main St, Hoboken, NJ 07030, US')).toEqual({ street: '1 Main St', city: 'Hoboken', state: 'NJ', zip: '07030', country: 'US' })
    expect(parseAddress({ line1: '9 Elm', city: 'X' }).street).toBe('9 Elm')
    expect(parseAddress(undefined).city).toBe('')
  })
})
