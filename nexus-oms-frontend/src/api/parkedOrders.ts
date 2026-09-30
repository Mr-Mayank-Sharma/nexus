import client from './client'

const WRAPPER_KEYS = ['success', 'data', 'message', 'error', 'errors', 'pagination']

/** Client normalizes every body to {success, data, message, ...}; unwrap back to the payload. */
async function unwrap<T>(promise: Promise<{ data: unknown }>): Promise<{ data: T }> {
  const res = await promise
  const body = res.data
  if (body && typeof body === 'object' && !Array.isArray(body) && 'data' in body) {
    const obj = body as Record<string, unknown>
    if (Object.keys(obj).every(k => WRAPPER_KEYS.includes(k))) return { data: obj.data as T }
  }
  return { data: body as T }
}

export interface ParkedOrder {
  id: string
  tenantId: string
  orderId: string
  orderNumber: string
  reason: 'PREORDER' | 'BACKORDER' | 'FRAUD_HOLD' | 'CREDIT_HOLD' | 'MANUAL_HOLD'
  priority: number
  sku?: string
  productName?: string
  quantity?: number
  customerEmail?: string
  expectedDate?: string
  notes?: string
  status: 'PARKED' | 'RELEASED' | 'CANCELLED' | 'CONVERTED'
  parkedAt: string
  releasedAt?: string
  createdAt: string
  updatedAt: string
}

export interface ParkOrderRequest {
  orderId: string
  orderNumber: string
  reason: string
  priority?: number
  sku?: string
  productName?: string
  quantity?: number
  customerEmail?: string
  expectedDate?: string
  notes?: string
}

export const parkedOrdersApi = {
  getParkedOrders: () =>
    unwrap<ParkedOrder[]>(client.get<ParkedOrder[]>('/parked-orders')),

  getParkedOrder: (id: string) =>
    unwrap<ParkedOrder>(client.get<ParkedOrder>(`/parked-orders/${id}`)),

  getParkedOrdersByReason: (reason: string) =>
    unwrap<ParkedOrder[]>(client.get<ParkedOrder[]>(`/parked-orders/reason/${reason}`)),

  getParkedOrdersBySku: (sku: string) =>
    unwrap<ParkedOrder[]>(client.get<ParkedOrder[]>(`/parked-orders/sku/${sku}`)),

  parkOrder: (data: ParkOrderRequest) =>
    unwrap<ParkedOrder>(client.post<ParkedOrder>('/parked-orders', data)),

  releaseOrder: (id: string, reason?: string) =>
    unwrap<ParkedOrder>(client.post<ParkedOrder>(`/parked-orders/${id}/release`, null, { params: { reason } })),

  cancelOrder: (id: string, reason?: string) =>
    unwrap<ParkedOrder>(client.post<ParkedOrder>(`/parked-orders/${id}/cancel`, null, { params: { reason } })),

  updatePriority: (id: string, priority: number) =>
    unwrap<ParkedOrder>(client.put<ParkedOrder>(`/parked-orders/${id}/priority`, null, { params: { priority } })),

  updateNotes: (id: string, notes: string) =>
    unwrap<ParkedOrder>(client.put<ParkedOrder>(`/parked-orders/${id}/notes`, null, { params: { notes } })),
}

export default parkedOrdersApi