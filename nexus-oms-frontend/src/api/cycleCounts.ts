import client from './client'
import { ApiResponse, CycleCount, PaginationParams } from '../types'

export async function getCycleCounts(params?: PaginationParams & { status?: string }): Promise<ApiResponse<{ content: CycleCount[]; totalElements: number; totalPages: number; number: number }>> {
  try {
    const { data } = await client.get('/cycle-counts', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get cycle counts'
    return { success: false, error: msg } as any
  }
}

export async function getCycleCount(id: string): Promise<ApiResponse<CycleCount>> {
  try {
    const { data } = await client.get(`/cycle-counts/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get cycle count'
    return { success: false, error: msg } as any
  }
}

export async function createCycleCount(count: Partial<CycleCount>): Promise<ApiResponse<CycleCount>> {
  try {
    const { data } = await client.post('/cycle-counts', count)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create cycle count'
    return { success: false, error: msg } as any
  }
}

export async function performCount(id: string, countedQty: number, countedBy?: string): Promise<ApiResponse<CycleCount>> {
  try {
    const { data } = await client.post(`/cycle-counts/${id}/count`, null, { params: { countedQty, countedBy } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to perform count'
    return { success: false, error: msg } as any
  }
}
