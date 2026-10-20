import client from './client'
import { ApiResponse, Order, OrderFilters } from '../types'
import { normalizeOrder, normalizeOrderList } from './normalizeOrder'

export interface OrderPage { content: Order[]; totalElements: number; totalPages: number }

/** One page of orders from the server, with its totals. */
export async function getOrdersPage(params: Record<string, string>): Promise<OrderPage> {
  const { data } = await client.get('/orders', { params })
  const d = normalizeOrderList(data?.data)
  if (Array.isArray(d)) return { content: d, totalElements: d.length, totalPages: 1 }
  return { content: d?.content ?? [], totalElements: Number(d?.totalElements ?? 0), totalPages: Number(d?.totalPages ?? 0) }
}

/** Orders per status plus ALL, from the server (no need to load every order to count them). */
export async function getStatusCounts(): Promise<Record<string, number>> {
  const { data } = await client.get('/orders/status-counts')
  return data?.data ?? {}
}

export async function getOrders(params?: OrderFilters): Promise<ApiResponse<Order[]>> {
  try {
    const { data } = await client.get('/orders', { params })
    return data?.success ? { ...data, data: normalizeOrderList(data.data) } : data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get orders'
    return { success: false, error: msg } as any
  }
}

export async function getOrderById(id: string): Promise<ApiResponse<Order>> {
  try {
    const { data } = await client.get(`/orders/${id}`)
    return data?.success && data.data ? { ...data, data: normalizeOrder(data.data) } : data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get order'
    return { success: false, error: msg } as any
  }
}

export async function createOrder(orderData: Record<string, any>): Promise<ApiResponse<Order>> {
  try {
    const { data } = await client.post('/orders', orderData)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create order'
    return { success: false, error: msg } as any
  }
}

export async function updateOrderStatus(id: string, status: string): Promise<ApiResponse<Order>> {
  try {
    const { data } = await client.put(`/orders/${id}`, { status })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update order status'
    return { success: false, error: msg } as any
  }
}

export async function confirmOrder(id: string): Promise<ApiResponse<Order>> {
  try {
    const { data } = await client.post(`/orders/${id}/confirm`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to confirm order'
    return { success: false, error: msg } as any
  }
}

export async function allocateOrder(id: string): Promise<ApiResponse<Order>> {
  try {
    const { data } = await client.post(`/orders/${id}/allocate`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to allocate order'
    return { success: false, error: msg } as any
  }
}

export async function shipOrder(id: string, carrierId: string, trackingNumber: string): Promise<ApiResponse<Order>> {
  try {
    const { data } = await client.post(`/orders/${id}/ship`, { carrierId, trackingNumber })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to ship order'
    return { success: false, error: msg } as any
  }
}

export async function cancelOrder(id: string): Promise<ApiResponse<Order>> {
  try {
    const { data } = await client.post(`/orders/${id}/cancel`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to cancel order'
    return { success: false, error: msg } as any
  }
}

export async function modifyOrder(id: string, payload: Record<string, any>): Promise<ApiResponse<Order>> {
  try {
    const { data } = await client.put(`/orders/${id}`, payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to modify order'
    return { success: false, error: msg } as any
  }
}

export async function splitOrder(id: string, payload: Record<string, any>): Promise<ApiResponse<Order[]>> {
  try {
    const { data } = await client.post(`/orders/${id}/split`, payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to split order'
    return { success: false, error: msg } as any
  }
}

export async function mergeOrders(payload: Record<string, any>): Promise<ApiResponse<Order>> {
  try {
    const { data } = await client.post('/orders/merge', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to merge orders'
    return { success: false, error: msg } as any
  }
}
