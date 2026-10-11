import type { Address, ApiOrder, Order } from '../types'

const EMPTY_ADDRESS: Address = { street: '', city: '', state: '', zip: '', country: '' }

/** Accepts an object ({street|line1, city, ...}), a "street, city, ST 12345, country" string, or nothing. */
export function parseAddress(a: unknown): Address {
  if (!a) return { ...EMPTY_ADDRESS }
  if (typeof a === 'string') {
    const parts = a.split(',').map(s => s.trim())
    const region = parts[2]?.split(' ') ?? []
    return { street: parts[0] || '', city: parts[1] || '', state: region[0] || '', zip: region[1] || parts[2] || '', country: parts[3] || '' }
  }
  const o = a as Record<string, any>
  return { street: o.street || o.line1 || '', city: o.city || '', state: o.state || '', zip: o.zip || o.pincode || '', country: o.country || '' }
}

/**
 * Turns an order as the API sends it (carrierId, promisedDelivery, shipTo, shippedAt, taxAmount, ...)
 * into the model the screens read (carrier, promisedDeliveryDate, shippingAddress, shippedDate, tax, ...).
 * Without this the Orders list showed blank Carrier, Destination and Promised Delivery for every order.
 * Safe to apply twice.
 */
export function normalizeOrder(raw: ApiOrder | Order | null | undefined): Order {
  const r = (raw ?? {}) as Record<string, any>
  const id: string = r.id ?? ''
  return {
    ...r,
    id,
    orderNumber: r.orderNumber || r.externalId || r.channelOrderId || (id ? `ORD-${id.slice(0, 8).toUpperCase()}` : ''),
    customerName: r.customerName || (r.customerId ? `Customer ${String(r.customerId).slice(0, 8)}` : 'Guest Customer'),
    customerEmail: r.customerEmail || '',
    channel: r.channel || 'MANUAL',
    status: r.status || 'PENDING',
    items: (r.items || []).map((i: any) => ({
      id: i.id, sku: i.sku || '', productName: i.productName || i.sku || '', imageUrl: i.imageUrl || '',
      quantity: i.quantity || 0, unitPrice: i.unitPrice || 0, totalPrice: i.totalPrice || (i.unitPrice || 0) * (i.quantity || 0),
    })),
    total: r.total || 0,
    subtotal: r.subtotal || 0,
    shippingCost: r.shippingCost || 0,
    tax: r.taxAmount ?? r.tax ?? 0,
    currency: r.currency || 'USD',
    shippingAddress: parseAddress(r.shippingAddress ?? r.shipTo),
    billingAddress: parseAddress(r.billingAddress),
    fulfillmentType: r.fulfillmentType || 'STANDARD',
    allocationNodeId: r.allocatedNode ?? r.allocationNodeId ?? '',
    carrier: r.carrierId ?? r.carrier ?? '',
    trackingNumber: r.trackingNumber || '',
    promisedDeliveryDate: r.promisedDelivery ?? r.promisedDeliveryDate ?? '',
    estimatedShipDate: r.shipBy ?? r.estimatedShipDate ?? r.shippedAt ?? '',
    shippedDate: r.shippedAt ?? r.shippedDate ?? '',
    deliveredDate: r.deliveredAt ?? r.deliveredDate ?? '',
    createdAt: r.createdAt || new Date().toISOString(),
    updatedAt: r.updatedAt || new Date().toISOString(),
    priority: r.priority || 'MEDIUM',
    hasException: r.hasException ?? r.subStatus === 'EXCEPTION',
    exceptionReason: r.exceptionReason,
    tags: r.tags || [],
    notes: r.notes || '',
  } as Order
}

/** Normalises a list response whether the API sent an array or a Spring page ({ content, ... }). */
export function normalizeOrderList<T>(data: T): T {
  if (Array.isArray(data)) return data.map(o => normalizeOrder(o)) as unknown as T
  if (data && typeof data === 'object' && Array.isArray((data as any).content)) {
    return { ...(data as object), content: (data as any).content.map((o: any) => normalizeOrder(o)) } as T
  }
  return data
}
