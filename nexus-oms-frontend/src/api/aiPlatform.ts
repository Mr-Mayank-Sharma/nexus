import client from './client'
import { ApiResponse } from '../types'

export interface AiModel {
  id: string
  tenantId?: string
  name: string
  displayName?: string
  description?: string
  modelType: string
  category: 'GLOBAL' | 'TENANT' | 'HYBRID'
  baseModelId?: string
  status: string
  currentVersion?: string
  inputSchema?: string
  outputSchema?: string
  config?: string
  tags?: string
  isActive?: boolean
  createdAt: string
  updatedAt: string
}

export interface AiModelVersion {
  id: string
  modelId: string
  version: string
  modelFileUrl?: string
  accuracy?: number
  precision?: number
  recall?: number
  f1Score?: number
  latencyMs?: number
  status: string
  createdAt: string
}

export interface AiTrainingJob {
  id: string
  modelId: string
  name?: string
  version?: string
  status: string
  jobType: string
  accuracy?: number
  precision?: number
  recall?: number
  f1Score?: number
  loss?: number
  epochs?: number
  datasetSize?: number
  durationSeconds?: number
  errorMessage?: string
  startedAt?: string
  completedAt?: string
  createdAt: string
}

export interface AiFeatureDefinition {
  id: string
  name: string
  displayName?: string
  description?: string
  featureGroup: string
  dataType: string
  entityType?: string
  isActive?: boolean
}

export async function getModels(page = 0, size = 20, category?: string): Promise<ApiResponse<{ content: AiModel[], totalElements: number }>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (category) params.set('category', category)
  const { data } = await client.get(`/api/ai/models?${params}`)
  return data
}

export async function getModel(id: string): Promise<ApiResponse<AiModel>> {
  const { data } = await client.get(`/api/ai/models/${id}`)
  return data
}

export async function getModelVersions(modelId: string): Promise<ApiResponse<AiModelVersion[]>> {
  const { data } = await client.get(`/api/ai/models/${modelId}/versions`)
  return data
}

export async function getModelSummary(): Promise<ApiResponse<Record<string, number>>> {
  const { data } = await client.get('/api/ai/models/summary')
  return data
}

export async function predict(modelType: string, input: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.post(`/api/ai/predict/${modelType}`, input)
  return data
}

export async function getTrainingJobs(modelId?: string, status?: string, page = 0): Promise<ApiResponse<{ content: AiTrainingJob[], totalElements: number }>> {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (modelId) params.set('modelId', modelId)
  if (status) params.set('status', status)
  const { data } = await client.get(`/api/ai/training/jobs?${params}`)
  return data
}

export async function getMonitoringDashboard(): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.get('/api/ai/monitoring/dashboard')
  return data
}

export async function getTenantDashboard(): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.get('/api/ai/analytics/dashboard')
  return data
}

export async function getFeatureGroups(): Promise<ApiResponse<Array<{ group: string; count: number }>>> {
  const { data } = await client.get('/api/ai/features/groups')
  return data
}

export async function startTrainingJob(jobId: string): Promise<ApiResponse<AiTrainingJob>> {
  const { data } = await client.post(`/api/ai/training/jobs/${jobId}/start`)
  return data
}

export async function deployModel(modelId: string, versionId: string, environment = 'PRODUCTION'): Promise<ApiResponse<unknown>> {
  const { data } = await client.post(`/api/ai/models/${modelId}/deploy/${versionId}?environment=${environment}`)
  return data
}

export async function rollbackModel(modelId: string, versionId: string): Promise<ApiResponse<null>> {
  const { data } = await client.post(`/api/ai/models/${modelId}/rollback/${versionId}`)
  return data
}
