import client from './client'
import { ApiResponse, AiPlatformModel, AiModelVersion, AiTrainingJob, AiFeatureDefinition, AiDeployment, AiInferenceLog, AiRuleFallback } from '../types'

export type AiModel = AiPlatformModel
export type { AiModelVersion, AiTrainingJob, AiFeatureDefinition, AiDeployment, AiInferenceLog, AiRuleFallback }

// ─────────────── Model Registry ───────────────

export async function getModels(page = 0, size = 20, category?: string, status?: string): Promise<ApiResponse<{ content: AiModel[], totalElements: number }>> {
  try {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (category) params.set('category', category)
    if (status) params.set('status', status)
    const { data } = await client.get(`/ai/models?${params}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get models'
    return { success: false, error: msg } as any
  }
}

export async function getModel(id: string): Promise<ApiResponse<AiModel>> {
  try {
    const { data } = await client.get(`/ai/models/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get model'
    return { success: false, error: msg } as any
  }
}

export async function createModel(model: Partial<AiModel>): Promise<ApiResponse<AiModel>> {
  try {
    const { data } = await client.post('/ai/models', model)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create model'
    return { success: false, error: msg } as any
  }
}

export async function updateModel(id: string, updates: Partial<AiModel>): Promise<ApiResponse<AiModel>> {
  try {
    const { data } = await client.put(`/ai/models/${id}`, updates)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update model'
    return { success: false, error: msg } as any
  }
}

export async function getModelVersions(modelId: string): Promise<ApiResponse<AiModelVersion[]>> {
  try {
    const { data } = await client.get(`/ai/models/${modelId}/versions`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get model versions'
    return { success: false, error: msg } as any
  }
}

export async function createModelVersion(modelId: string, version: Partial<AiModelVersion>): Promise<ApiResponse<AiModelVersion>> {
  try {
    const { data } = await client.post(`/ai/models/${modelId}/versions`, version)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create model version'
    return { success: false, error: msg } as any
  }
}

export async function deployModel(modelId: string, versionId: string, environment = 'PRODUCTION'): Promise<ApiResponse<AiDeployment>> {
  try {
    const { data } = await client.post(`/ai/models/${modelId}/deploy/${versionId}?environment=${environment}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to deploy model'
    return { success: false, error: msg } as any
  }
}

export async function rollbackModel(modelId: string, versionId: string): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.post(`/ai/models/${modelId}/rollback/${versionId}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to rollback model'
    return { success: false, error: msg } as any
  }
}

export async function getModelSummary(): Promise<ApiResponse<Record<string, unknown>>> {
  try {
    const { data } = await client.get('/ai/models/summary')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get model summary'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Prediction / Inference ───────────────

export async function predict(modelType: string, input: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
  try {
    const { data } = await client.post(`/ai/predict/${modelType}`, input)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to predict'
    return { success: false, error: msg } as any
  }
}

export async function directPredict(modelId: string, versionId: string, input: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>>> {
  try {
    const { data } = await client.post(`/ai/predict/direct/${modelId}/${versionId}`, input)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to direct predict'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Feature Store ───────────────

export async function getFeatures(featureGroup?: string, page = 0, size = 20): Promise<ApiResponse<{ content: AiFeatureDefinition[], totalElements: number }>> {
  try {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (featureGroup) params.set('featureGroup', featureGroup)
    const { data } = await client.get(`/ai/features?${params}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get features'
    return { success: false, error: msg } as any
  }
}

export async function createFeature(feature: Partial<AiFeatureDefinition>): Promise<ApiResponse<AiFeatureDefinition>> {
  try {
    const { data } = await client.post('/ai/features', feature)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create feature'
    return { success: false, error: msg } as any
  }
}

export async function getFeatureGroups(): Promise<ApiResponse<Array<{ group: string; count: number }>>> {
  try {
    const { data } = await client.get('/ai/features/groups')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get feature groups'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Training Pipeline ───────────────

export async function getTrainingJobs(modelId?: string, status?: string, page = 0): Promise<ApiResponse<{ content: AiTrainingJob[], totalElements: number }>> {
  try {
    const params = new URLSearchParams({ page: String(page), size: '20' })
    if (modelId) params.set('modelId', modelId)
    if (status) params.set('status', status)
    const { data } = await client.get(`/ai/training/jobs?${params}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get training jobs'
    return { success: false, error: msg } as any
  }
}

export async function getTrainingJob(jobId: string): Promise<ApiResponse<AiTrainingJob>> {
  try {
    const { data } = await client.get(`/ai/training/jobs/${jobId}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get training job'
    return { success: false, error: msg } as any
  }
}

export async function createTrainingJob(modelId: string, config: Record<string, unknown>): Promise<ApiResponse<AiTrainingJob>> {
  try {
    const { data } = await client.post(`/ai/training/jobs?modelId=${modelId}`, config)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create training job'
    return { success: false, error: msg } as any
  }
}

export async function startTrainingJob(jobId: string): Promise<ApiResponse<AiTrainingJob>> {
  try {
    const { data } = await client.post(`/ai/training/jobs/${jobId}/start`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to start training job'
    return { success: false, error: msg } as any
  }
}

export async function completeTrainingJob(jobId: string, results: Record<string, unknown>): Promise<ApiResponse<AiTrainingJob>> {
  try {
    const { data } = await client.post(`/ai/training/jobs/${jobId}/complete`, results)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to complete training job'
    return { success: false, error: msg } as any
  }
}

export async function failTrainingJob(jobId: string, error: string): Promise<ApiResponse<AiTrainingJob>> {
  try {
    const { data } = await client.post(`/ai/training/jobs/${jobId}/fail`, { error })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to fail training job'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Monitoring ───────────────

export async function getModelHealth(modelId: string): Promise<ApiResponse<Record<string, unknown>>> {
  try {
    const { data } = await client.get(`/ai/monitoring/models/${modelId}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get model health'
    return { success: false, error: msg } as any
  }
}

export async function getMonitoringDashboard(): Promise<ApiResponse<Record<string, unknown>>> {
  try {
    const { data } = await client.get('/ai/monitoring/dashboard')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get monitoring dashboard'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Analytics ───────────────

export async function getTenantDashboard(): Promise<ApiResponse<Record<string, unknown>>> {
  try {
    const { data } = await client.get('/ai/analytics/dashboard')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get tenant dashboard'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Inference Logs ───────────────

export async function getInferenceLogs(modelId: string, page = 0, size = 20): Promise<ApiResponse<{ content: AiInferenceLog[], totalElements: number }>> {
  try {
    const { data } = await client.get(`/ai/models/${modelId}/inference-logs`, { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get inference logs'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Rule Fallbacks ───────────────

export async function getModelFallbacks(modelId: string): Promise<ApiResponse<AiRuleFallback[]>> {
  try {
    const { data } = await client.get(`/ai/fallbacks/${modelId}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get model fallbacks'
    return { success: false, error: msg } as any
  }
}
