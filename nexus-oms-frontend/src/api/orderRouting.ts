import client from './client'
import type { ApiResponse, OrderAllocation, FulfillmentException, AllocationResult } from '../types'

export async function allocateOrder(payload: {
  orderId: string
  strategy?: string
  dryRun?: boolean
}): Promise<ApiResponse<AllocationResult>> {
  try {
    const { data } = await client.post('/order-routing/allocate', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to allocate order'
    return { success: false, error: msg } as any
  }
}

export async function simulateAllocation(payload: {
  orderId: string
  strategy?: string
}): Promise<ApiResponse<Record<string, unknown>>> {
  try {
    const { data } = await client.post('/order-routing/simulate', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to simulate allocation'
    return { success: false, error: msg } as any
  }
}

export async function reallocateOrder(
  orderId: string,
  strategy = 'HYBRID'
): Promise<ApiResponse<AllocationResult>> {
  try {
    const { data } = await client.post('/order-routing/reallocate', null, {
      params: { orderId, strategy },
    })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to reallocate order'
    return { success: false, error: msg } as any
  }
}

export async function getAllocations(
  orderId: string
): Promise<ApiResponse<OrderAllocation[]>> {
  try {
    const { data } = await client.get(`/order-routing/allocations/${orderId}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get allocations'
    return { success: false, error: msg } as any
  }
}

export async function getExceptions(params?: {
  status?: string
  severity?: string
  page?: number
  size?: number
}): Promise<ApiResponse<{
  content: FulfillmentException[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  try {
    const { data } = await client.get('/order-routing/exceptions', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get exceptions'
    return { success: false, error: msg } as any
  }
}

export async function getException(
  id: string
): Promise<ApiResponse<FulfillmentException>> {
  try {
    const { data } = await client.get(`/order-routing/exceptions/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get exception'
    return { success: false, error: msg } as any
  }
}

export async function resolveException(
  id: string,
  payload: { resolution: string; resolutionStrategy?: string }
): Promise<ApiResponse<FulfillmentException>> {
  try {
    const { data } = await client.post(
      `/order-routing/exceptions/${id}/resolve`,
      payload
    )
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to resolve exception'
    return { success: false, error: msg } as any
  }
}

export async function escalateException(
  id: string
): Promise<ApiResponse<FulfillmentException>> {
  try {
    const { data } = await client.post(
      `/order-routing/exceptions/${id}/escalate`
    )
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to escalate exception'
    return { success: false, error: msg } as any
  }
}

export async function getRoutingKPIs(): Promise<
  ApiResponse<Record<string, number>>
> {
  try {
    const { data } = await client.get('/order-routing/kpis')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get routing KPIs'
    return { success: false, error: msg } as any
  }
}
