import client from './client'
import type { ApiResponse, Return, ReturnItem, ReturnStatus } from '../types'

export async function getReturns(params?: { status?: ReturnStatus | string; search?: string }): Promise<ApiResponse<Return[]>> {
  const { data } = await client.get('/returns', { params })
  return data
}

export async function getReturn(id: string): Promise<ApiResponse<Return>> {
  const { data } = await client.get(`/returns/${id}`)
  return data
}

export async function getReturnItems(id: string): Promise<ApiResponse<ReturnItem[]>> {
  const { data } = await client.get(`/returns/${id}/items`)
  return data
}

export async function createReturn(params: {
  orderId: string
  customerId?: string
  reason: string
  returnChannel?: string
  rmaType?: string
  items?: ReturnItem[]
}): Promise<ApiResponse<Return>> {
  const { data } = await client.post('/returns', params)
  return data
}

export async function approveReturn(id: string, approvedBy?: string): Promise<ApiResponse<Return>> {
  const { data } = await client.post(`/returns/${id}/approve`, null, { params: { approvedBy } })
  return data
}

export async function receiveReturn(id: string, receivedBy?: string): Promise<ApiResponse<Return>> {
  const { data } = await client.post(`/returns/${id}/receive`, null, { params: { receivedBy } })
  return data
}

export async function inspectReturn(id: string, items: ReturnItem[], inspectedBy?: string): Promise<ApiResponse<Return>> {
  const { data } = await client.post(`/returns/${id}/inspect`, { items, inspectedBy })
  return data
}

export async function processRefund(id: string, refundAmount: number, refundReference?: string): Promise<ApiResponse<Return>> {
  const { data } = await client.post(`/returns/${id}/refund`, { refundAmount, refundReference })
  return data
}

export async function rejectReturn(id: string, reason: string): Promise<ApiResponse<Return>> {
  const { data } = await client.post(`/returns/${id}/reject`, { reason })
  return data
}

export async function updateReturnStatus(id: string, status: ReturnStatus | string): Promise<ApiResponse<Return>> {
  const { data } = await client.put(`/returns/${id}/status`, { status })
  return data
}

export async function getReturnKPIs(): Promise<ApiResponse<{
  total: number
  pending: number
  approved: number
  received: number
  inspected: number
  refunded: number
  rejected: number
}>> {
  const { data } = await client.get('/returns/kpis')
  return data
}

export async function getReturnReasons(): Promise<ApiResponse<Array<{ reason: string; count: number }>>> {
  const { data } = await client.get('/returns/reasons')
  return data
}
