import client from './client'

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
    client.get<ParkedOrder[]>('/parked-orders'),

  getParkedOrder: (id: string) =>
    client.get<ParkedOrder>(`/parked-orders/${id}`),

  getParkedOrdersByReason: (reason: string) =>
    client.get<ParkedOrder[]>(`/parked-orders/reason/${reason}`),

  getParkedOrdersBySku: (sku: string) =>
    client.get<ParkedOrder[]>(`/parked-orders/sku/${sku}`),

  parkOrder: (data: ParkOrderRequest) =>
    client.post<ParkedOrder>('/parked-orders', data),

  releaseOrder: (id: string, reason?: string) =>
    client.post<ParkedOrder>(`/parked-orders/${id}/release`, null, { params: { reason } }),

  cancelOrder: (id: string, reason?: string) =>
    client.post<ParkedOrder>(`/parked-orders/${id}/cancel`, null, { params: { reason } }),

  updatePriority: (id: string, priority: number) =>
    client.put<ParkedOrder>(`/parked-orders/${id}/priority`, null, { params: { priority } }),

  updateNotes: (id: string, notes: string) =>
    client.put<ParkedOrder>(`/parked-orders/${id}/notes`, null, { params: { notes } }),
}

export default parkedOrdersApi
