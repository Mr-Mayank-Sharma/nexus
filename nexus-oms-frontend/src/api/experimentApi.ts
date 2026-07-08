import client from './client'
import { ApiResponse, AiExperiment } from '../types'

export async function getExperiments(modelId?: string, status?: string, page = 0, size = 20): Promise<ApiResponse<{ content: AiExperiment[], totalElements: number }>> {
  try {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (modelId) params.set('modelId', modelId)
    if (status) params.set('status', status)
    const { data } = await client.get(`/ai/experiments?${params}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get experiments'
    return { success: false, error: msg } as any
  }
}

export async function getExperiment(id: string): Promise<ApiResponse<AiExperiment>> {
  try {
    const { data } = await client.get(`/ai/experiments/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get experiment'
    return { success: false, error: msg } as any
  }
}

export async function createExperiment(experiment: Partial<AiExperiment>): Promise<ApiResponse<AiExperiment>> {
  try {
    const { data } = await client.post('/ai/experiments', experiment)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create experiment'
    return { success: false, error: msg } as any
  }
}

export async function updateExperiment(id: string, updates: Partial<AiExperiment>): Promise<ApiResponse<AiExperiment>> {
  try {
    const { data } = await client.put(`/ai/experiments/${id}`, updates)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update experiment'
    return { success: false, error: msg } as any
  }
}

export async function startExperiment(id: string): Promise<ApiResponse<AiExperiment>> {
  try {
    const { data } = await client.post(`/ai/experiments/${id}/start`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to start experiment'
    return { success: false, error: msg } as any
  }
}

export async function completeExperiment(id: string, winnerVersionId: string): Promise<ApiResponse<AiExperiment>> {
  try {
    const { data } = await client.post(`/ai/experiments/${id}/complete`, { winnerVersionId })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to complete experiment'
    return { success: false, error: msg } as any
  }
}

export async function rollbackExperiment(id: string): Promise<ApiResponse<AiExperiment>> {
  try {
    const { data } = await client.post(`/ai/experiments/${id}/rollback`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to rollback experiment'
    return { success: false, error: msg } as any
  }
}

export async function failExperiment(id: string, error: string): Promise<ApiResponse<AiExperiment>> {
  try {
    const { data } = await client.post(`/ai/experiments/${id}/fail`, { error })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to fail experiment'
    return { success: false, error: msg } as any
  }
}
