import client from './client'
import { ApiResponse, AiExperiment } from '../types'

export async function getExperiments(modelId?: string, status?: string, page = 0, size = 20): Promise<ApiResponse<{ content: AiExperiment[], totalElements: number }>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (modelId) params.set('modelId', modelId)
  if (status) params.set('status', status)
  const { data } = await client.get(`/ai/experiments?${params}`)
  return data
}

export async function getExperiment(id: string): Promise<ApiResponse<AiExperiment>> {
  const { data } = await client.get(`/ai/experiments/${id}`)
  return data
}

export async function createExperiment(experiment: Partial<AiExperiment>): Promise<ApiResponse<AiExperiment>> {
  const { data } = await client.post('/ai/experiments', experiment)
  return data
}

export async function updateExperiment(id: string, updates: Partial<AiExperiment>): Promise<ApiResponse<AiExperiment>> {
  const { data } = await client.put(`/ai/experiments/${id}`, updates)
  return data
}

export async function startExperiment(id: string): Promise<ApiResponse<AiExperiment>> {
  const { data } = await client.post(`/ai/experiments/${id}/start`)
  return data
}

export async function completeExperiment(id: string, winnerVersionId: string): Promise<ApiResponse<AiExperiment>> {
  const { data } = await client.post(`/ai/experiments/${id}/complete`, { winnerVersionId })
  return data
}

export async function rollbackExperiment(id: string): Promise<ApiResponse<AiExperiment>> {
  const { data } = await client.post(`/ai/experiments/${id}/rollback`)
  return data
}

export async function failExperiment(id: string, error: string): Promise<ApiResponse<AiExperiment>> {
  const { data } = await client.post(`/ai/experiments/${id}/fail`, { error })
  return data
}
