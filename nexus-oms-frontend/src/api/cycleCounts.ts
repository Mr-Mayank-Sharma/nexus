import client from './client'
import { ApiResponse, CycleCount, PaginationParams } from '../types'

export async function getCycleCounts(params?: PaginationParams & { status?: string }): Promise<ApiResponse<CycleCount[]>> {
  const { data } = await client.get('/cycle-counts', { params })
  return data
}

export async function getCycleCount(id: string): Promise<ApiResponse<CycleCount>> {
  const { data } = await client.get(`/cycle-counts/${id}`)
  return data
}

export async function createCycleCount(count: Partial<CycleCount>): Promise<ApiResponse<CycleCount>> {
  const { data } = await client.post('/cycle-counts', count)
  return data
}

export async function performCount(id: string, countedQty: number, countedBy?: string): Promise<ApiResponse<CycleCount>> {
  const { data } = await client.post(`/cycle-counts/${id}/count`, null, { params: { countedQty, countedBy } })
  return data
}
