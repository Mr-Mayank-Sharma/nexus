import client from './client'
import type { ApiResponse, Return, ReturnItem, ReturnStatus } from '../types'

export async function getReturns(params?: { status?: ReturnStatus | string; search?: string }): Promise<ApiResponse<Return[]>> {
  try {
    const { data } = await client.get('/returns', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get returns'
    return { success: false, error: msg } as any
  }
}

export async function getReturn(id: string): Promise<ApiResponse<Return>> {
  try {
    const { data } = await client.get(`/returns/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get return'
    return { success: false, error: msg } as any
  }
}

export async function getReturnItems(id: string): Promise<ApiResponse<ReturnItem[]>> {
  try {
    const { data } = await client.get(`/returns/${id}/items`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get return items'
    return { success: false, error: msg } as any
  }
}

export async function createReturn(params: {
  orderId: string
  customerId?: string
  reason: string
  returnChannel?: string
  rmaType?: string
  items?: ReturnItem[]
}): Promise<ApiResponse<Return>> {
  try {
    const { data } = await client.post('/returns', params)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create return'
    return { success: false, error: msg } as any
  }
}

export async function approveReturn(id: string, approvedBy?: string): Promise<ApiResponse<Return>> {
  try {
    const { data } = await client.post(`/returns/${id}/approve`, null, { params: { approvedBy } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to approve return'
    return { success: false, error: msg } as any
  }
}

export async function receiveReturn(id: string, receivedBy?: string): Promise<ApiResponse<Return>> {
  try {
    const { data } = await client.post(`/returns/${id}/receive`, null, { params: { receivedBy } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to receive return'
    return { success: false, error: msg } as any
  }
}

export async function inspectReturn(id: string, items: ReturnItem[], inspectedBy?: string): Promise<ApiResponse<Return>> {
  try {
    const { data } = await client.post(`/returns/${id}/inspect`, { items, inspectedBy })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to inspect return'
    return { success: false, error: msg } as any
  }
}

export async function processRefund(id: string, refundAmount: number, refundReference?: string): Promise<ApiResponse<Return>> {
  try {
    const { data } = await client.post(`/returns/${id}/refund`, { refundAmount, refundReference })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to process refund'
    return { success: false, error: msg } as any
  }
}

export async function rejectReturn(id: string, reason: string): Promise<ApiResponse<Return>> {
  try {
    const { data } = await client.post(`/returns/${id}/reject`, { reason })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to reject return'
    return { success: false, error: msg } as any
  }
}

export async function updateReturnStatus(id: string, status: ReturnStatus | string): Promise<ApiResponse<Return>> {
  try {
    const { data } = await client.put(`/returns/${id}/status`, { status })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update return status'
    return { success: false, error: msg } as any
  }
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
  try {
    const { data } = await client.get('/returns/kpis')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get return KPIs'
    return { success: false, error: msg } as any
  }
}

export async function getReturnReasons(): Promise<ApiResponse<Array<{ reason: string; count: number }>>> {
  try {
    const { data } = await client.get('/returns/reasons')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get return reasons'
    return { success: false, error: msg } as any
  }
}
