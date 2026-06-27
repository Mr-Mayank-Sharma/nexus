import client from './client'
import { ApiResponse, Order, OrderFilters } from '../types'

export async function getOrders(params?: OrderFilters): Promise<ApiResponse<Order[]>> {
  const { data } = await client.get('/orders', { params })
  return data
}

export async function getOrderById(id: string): Promise<ApiResponse<Order>> {
  const { data } = await client.get(`/orders/${id}`)
  return data
}

export async function createOrder(orderData: Record<string, any>): Promise<ApiResponse<Order>> {
  const { data } = await client.post('/orders', orderData)
  return data
}

export async function updateOrderStatus(id: string, status: string): Promise<ApiResponse<Order>> {
  const { data } = await client.put(`/orders/${id}/status`, { status })
  return data
}

export async function confirmOrder(id: string): Promise<ApiResponse<Order>> {
  const { data } = await client.post(`/orders/${id}/confirm`)
  return data
}

export async function allocateOrder(id: string): Promise<ApiResponse<Order>> {
  const { data } = await client.post(`/orders/${id}/allocate`)
  return data
}

export async function shipOrder(id: string, carrierId: string, trackingNumber: string): Promise<ApiResponse<Order>> {
  const { data } = await client.post(`/orders/${id}/ship`, {}, { params: { carrierId, trackingNumber } })
  return data
}

export async function cancelOrder(id: string): Promise<ApiResponse<Order>> {
  const { data } = await client.post(`/orders/${id}/cancel`)
  return data
}

export async function modifyOrder(id: string, payload: Record<string, any>): Promise<ApiResponse<Order>> {
  const { data } = await client.put(`/orders/${id}`, payload)
  return data
}

export async function splitOrder(id: string, payload: Record<string, any>): Promise<ApiResponse<Order[]>> {
  const { data } = await client.post(`/orders/${id}/split`, payload)
  return data
}

export async function mergeOrders(payload: Record<string, any>): Promise<ApiResponse<Order>> {
  const { data } = await client.post('/orders/merge', payload)
  return data
}
