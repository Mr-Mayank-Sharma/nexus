import client from './client'
import { ApiResponse, AiPlatformModel, AiModelVersion, AiTrainingJob, AiFeatureDefinition, AiDeployment, AiInferenceLog, AiRuleFallback } from '../types'

export type AiModel = AiPlatformModel
export type { AiModelVersion, AiTrainingJob, AiFeatureDefinition, AiDeployment, AiInferenceLog, AiRuleFallback }

// ─────────────── Model Registry ───────────────

export async function getModels(page = 0, size = 20, category?: string, status?: string): Promise<ApiResponse<{ content: AiModel[], totalElements: number }>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (category) params.set('category', category)
  if (status) params.set('status', status)
  const { data } = await client.get(`/ai/models?${params}`)
  return data
}

export async function getModel(id: string): Promise<ApiResponse<AiModel>> {
  const { data } = await client.get(`/ai/models/${id}`)
  return data
}

export async function createModel(model: Partial<AiModel>): Promise<ApiResponse<AiModel>> {
  const { data } = await client.post('/ai/models', model)
  return data
}

export async function updateModel(id: string, updates: Partial<AiModel>): Promise<ApiResponse<AiModel>> {
  const { data } = await client.put(`/ai/models/${id}`, updates)
  return data
}

export async function getModelVersions(modelId: string): Promise<ApiResponse<AiModelVersion[]>> {
  const { data } = await client.get(`/ai/models/${modelId}/versions`)
  return data
}

export async function createModelVersion(modelId: string, version: Partial<AiModelVersion>): Promise<ApiResponse<AiModelVersion>> {
  const { data } = await client.post(`/ai/models/${modelId}/versions`, version)
  return data
}

export async function deployModel(modelId: string, versionId: string, environment = 'PRODUCTION'): Promise<ApiResponse<AiDeployment>> {
  const { data } = await client.post(`/ai/models/${modelId}/deploy/${versionId}?environment=${environment}`)
  return data
}

export async function rollbackModel(modelId: string, versionId: string): Promise<ApiResponse<null>> {
  const { data } = await client.post(`/ai/models/${modelId}/rollback/${versionId}`)
  return data
}

export async function getModelSummary(): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.get('/ai/models/summary')
  return data
}

// ─────────────── Prediction / Inference ───────────────

export async function predict(modelType: string, input: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.post(`/ai/predict/${modelType}`, input)
  return data
}

export async function directPredict(modelId: string, versionId: string, input: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.post(`/ai/predict/direct/${modelId}/${versionId}`, input)
  return data
}

// ─────────────── Feature Store ───────────────

export async function getFeatures(featureGroup?: string, page = 0, size = 20): Promise<ApiResponse<{ content: AiFeatureDefinition[], totalElements: number }>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (featureGroup) params.set('featureGroup', featureGroup)
  const { data } = await client.get(`/ai/features?${params}`)
  return data
}

export async function createFeature(feature: Partial<AiFeatureDefinition>): Promise<ApiResponse<AiFeatureDefinition>> {
  const { data } = await client.post('/ai/features', feature)
  return data
}

export async function getFeatureGroups(): Promise<ApiResponse<Array<{ group: string; count: number }>>> {
  const { data } = await client.get('/ai/features/groups')
  return data
}

// ─────────────── Training Pipeline ───────────────

export async function getTrainingJobs(modelId?: string, status?: string, page = 0): Promise<ApiResponse<{ content: AiTrainingJob[], totalElements: number }>> {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (modelId) params.set('modelId', modelId)
  if (status) params.set('status', status)
  const { data } = await client.get(`/ai/training/jobs?${params}`)
  return data
}

export async function getTrainingJob(jobId: string): Promise<ApiResponse<AiTrainingJob>> {
  const { data } = await client.get(`/ai/training/jobs/${jobId}`)
  return data
}

export async function createTrainingJob(modelId: string, config: Record<string, unknown>): Promise<ApiResponse<AiTrainingJob>> {
  const { data } = await client.post(`/ai/training/jobs?modelId=${modelId}`, config)
  return data
}

export async function startTrainingJob(jobId: string): Promise<ApiResponse<AiTrainingJob>> {
  const { data } = await client.post(`/ai/training/jobs/${jobId}/start`)
  return data
}

export async function completeTrainingJob(jobId: string, results: Record<string, unknown>): Promise<ApiResponse<AiTrainingJob>> {
  const { data } = await client.post(`/ai/training/jobs/${jobId}/complete`, results)
  return data
}

export async function failTrainingJob(jobId: string, error: string): Promise<ApiResponse<AiTrainingJob>> {
  const { data } = await client.post(`/ai/training/jobs/${jobId}/fail`, { error })
  return data
}

// ─────────────── Monitoring ───────────────

export async function getModelHealth(modelId: string): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.get(`/ai/monitoring/models/${modelId}`)
  return data
}

export async function getMonitoringDashboard(): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.get('/ai/monitoring/dashboard')
  return data
}

// ─────────────── Analytics ───────────────

export async function getTenantDashboard(): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.get('/ai/analytics/dashboard')
  return data
}

// ─────────────── Inference Logs ───────────────

export async function getInferenceLogs(modelId: string, page = 0, size = 20): Promise<ApiResponse<{ content: AiInferenceLog[], totalElements: number }>> {
  const { data } = await client.get(`/ai/models/${modelId}/inference-logs`, { params: { page, size } })
  return data
}

// ─────────────── Rule Fallbacks ───────────────

export async function getModelFallbacks(modelId: string): Promise<ApiResponse<AiRuleFallback[]>> {
  const { data } = await client.get(`/ai/fallbacks/${modelId}`)
  return data
}
