import client from './client'
import type { ApiResponse, OrderAllocation, FulfillmentException, AllocationResult } from '../types'

export async function allocateOrder(payload: {
  orderId: string
  strategy?: string
  dryRun?: boolean
}): Promise<ApiResponse<AllocationResult>> {
  const { data } = await client.post('/order-routing/allocate', payload)
  return data
}

export async function simulateAllocation(payload: {
  orderId: string
  strategy?: string
}): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.post('/order-routing/simulate', payload)
  return data
}

export async function reallocateOrder(
  orderId: string,
  strategy = 'HYBRID'
): Promise<ApiResponse<AllocationResult>> {
  const { data } = await client.post('/order-routing/reallocate', null, {
    params: { orderId, strategy },
  })
  return data
}

export async function getAllocations(
  orderId: string
): Promise<ApiResponse<OrderAllocation[]>> {
  const { data } = await client.get(`/order-routing/allocations/${orderId}`)
  return data
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
  const { data } = await client.get('/order-routing/exceptions', { params })
  return data
}

export async function getException(
  id: string
): Promise<ApiResponse<FulfillmentException>> {
  const { data } = await client.get(`/order-routing/exceptions/${id}`)
  return data
}

export async function resolveException(
  id: string,
  payload: { resolution: string; resolutionStrategy?: string }
): Promise<ApiResponse<FulfillmentException>> {
  const { data } = await client.post(
    `/order-routing/exceptions/${id}/resolve`,
    payload
  )
  return data
}

export async function escalateException(
  id: string
): Promise<ApiResponse<FulfillmentException>> {
  const { data } = await client.post(
    `/order-routing/exceptions/${id}/escalate`
  )
  return data
}

export async function getRoutingKPIs(): Promise<
  ApiResponse<Record<string, number>>
> {
  const { data } = await client.get('/order-routing/kpis')
  return data
}
