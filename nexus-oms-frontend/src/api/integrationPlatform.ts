import client from './client'
import type { ApiResponse, IntegrationImportJob, IntegrationExportJob, IntegrationDLQ, IntegrationEndpoint, IntegrationFlow, IntegrationFlowStep, IntegrationTransformMapping, IntegrationValidationRule, IntegrationCDCEvent, IntegrationAuditLog } from '../types'
export type { IntegrationImportJob, IntegrationExportJob, IntegrationDLQ, IntegrationEndpoint, IntegrationFlow, IntegrationFlowStep, IntegrationTransformMapping, IntegrationValidationRule, IntegrationCDCEvent, IntegrationAuditLog }

// ─────────────── Endpoints CRUD ───────────────

export async function getEndpointsPaginated(page = 0, size = 20): Promise<ApiResponse<{
  content: IntegrationEndpoint[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  try {
    const { data } = await client.get('/integration-platform/endpoints', { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get endpoints paginated'
    return { success: false, error: msg } as any
  }
}

export async function getEndpoint(id: string): Promise<ApiResponse<IntegrationEndpoint>> {
  try {
    const { data } = await client.get(`/integration-platform/endpoints/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get endpoint'
    return { success: false, error: msg } as any
  }
}

export async function createEndpoint(endpoint: Partial<IntegrationEndpoint>): Promise<ApiResponse<IntegrationEndpoint>> {
  try {
    const { data } = await client.post('/integration-platform/endpoints', endpoint)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create endpoint'
    return { success: false, error: msg } as any
  }
}

export async function updateEndpoint(id: string, endpoint: Partial<IntegrationEndpoint>): Promise<ApiResponse<IntegrationEndpoint>> {
  try {
    const { data } = await client.put(`/integration-platform/endpoints/${id}`, endpoint)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update endpoint'
    return { success: false, error: msg } as any
  }
}

export async function deleteEndpoint(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.delete(`/integration-platform/endpoints/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to delete endpoint'
    return { success: false, error: msg } as any
  }
}

export async function testEndpoint(id: string): Promise<ApiResponse<Record<string, unknown>>> {
  try {
    const { data } = await client.post(`/integration-platform/endpoints/${id}/test`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to test endpoint'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Flows CRUD ───────────────

export async function getFlows(page = 0, size = 20): Promise<ApiResponse<{
  content: IntegrationFlow[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  try {
    const { data } = await client.get('/integration-platform/flows', { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get flows'
    return { success: false, error: msg } as any
  }
}

export async function getFlow(id: string): Promise<ApiResponse<IntegrationFlow>> {
  try {
    const { data } = await client.get(`/integration-platform/flows/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get flow'
    return { success: false, error: msg } as any
  }
}

export async function createFlow(flow: Partial<IntegrationFlow>): Promise<ApiResponse<IntegrationFlow>> {
  try {
    const { data } = await client.post('/integration-platform/flows', flow)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create flow'
    return { success: false, error: msg } as any
  }
}

export async function updateFlow(id: string, flow: Partial<IntegrationFlow>): Promise<ApiResponse<IntegrationFlow>> {
  try {
    const { data } = await client.put(`/integration-platform/flows/${id}`, flow)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update flow'
    return { success: false, error: msg } as any
  }
}

export async function deleteFlow(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.delete(`/integration-platform/flows/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to delete flow'
    return { success: false, error: msg } as any
  }
}

export async function activateFlow(id: string): Promise<ApiResponse<IntegrationFlow>> {
  try {
    const { data } = await client.post(`/integration-platform/flows/${id}/activate`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to activate flow'
    return { success: false, error: msg } as any
  }
}

export async function pauseFlow(id: string): Promise<ApiResponse<IntegrationFlow>> {
  try {
    const { data } = await client.post(`/integration-platform/flows/${id}/pause`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to pause flow'
    return { success: false, error: msg } as any
  }
}

export async function getFlowSteps(flowId: string): Promise<ApiResponse<IntegrationFlowStep[]>> {
  try {
    const { data } = await client.get(`/integration-platform/flows/${flowId}/steps`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get flow steps'
    return { success: false, error: msg } as any
  }
}

export async function addFlowStep(flowId: string, step: Partial<IntegrationFlowStep>): Promise<ApiResponse<IntegrationFlowStep>> {
  try {
    const { data } = await client.post(`/integration-platform/flows/${flowId}/steps`, step)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to add flow step'
    return { success: false, error: msg } as any
  }
}

export async function updateFlowStep(id: string, step: Partial<IntegrationFlowStep>): Promise<ApiResponse<IntegrationFlowStep>> {
  try {
    const { data } = await client.put(`/integration-platform/flows/steps/${id}`, step)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update flow step'
    return { success: false, error: msg } as any
  }
}

export async function deleteFlowStep(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.delete(`/integration-platform/flows/steps/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to delete flow step'
    return { success: false, error: msg } as any
  }
}

export async function reorderFlowSteps(flowId: string, stepIds: Array<{ stepId: string }>): Promise<ApiResponse<IntegrationFlowStep[]>> {
  try {
    const { data } = await client.put(`/integration-platform/flows/${flowId}/steps/reorder`, stepIds)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to reorder flow steps'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Transform Mappings ───────────────

export async function getMappings(page = 0, size = 20): Promise<ApiResponse<{
  content: IntegrationTransformMapping[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  try {
    const { data } = await client.get('/integration-platform/mappings', { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get mappings'
    return { success: false, error: msg } as any
  }
}

export async function getMapping(id: string): Promise<ApiResponse<IntegrationTransformMapping>> {
  try {
    const { data } = await client.get(`/integration-platform/mappings/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get mapping'
    return { success: false, error: msg } as any
  }
}

export async function createMapping(mapping: Partial<IntegrationTransformMapping>): Promise<ApiResponse<IntegrationTransformMapping>> {
  try {
    const { data } = await client.post('/integration-platform/mappings', mapping)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create mapping'
    return { success: false, error: msg } as any
  }
}

export async function updateMapping(id: string, mapping: Partial<IntegrationTransformMapping>): Promise<ApiResponse<IntegrationTransformMapping>> {
  try {
    const { data } = await client.put(`/integration-platform/mappings/${id}`, mapping)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update mapping'
    return { success: false, error: msg } as any
  }
}

export async function transformMapping(id: string, payload: string, sourceFormat: string, targetFormat: string): Promise<ApiResponse<string>> {
  try {
    const { data } = await client.post(`/integration-platform/mappings/${id}/transform`, { payload, sourceFormat, targetFormat })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to transform mapping'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Validation Rules ───────────────

export async function getValidationRules(): Promise<ApiResponse<IntegrationValidationRule[]>> {
  try {
    const { data } = await client.get('/integration-platform/validation-rules')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get validation rules'
    return { success: false, error: msg } as any
  }
}

export async function getValidationRulesByEntity(entityType: string): Promise<ApiResponse<IntegrationValidationRule[]>> {
  try {
    const { data } = await client.get('/integration-platform/validation-rules/entity', { params: { entityType } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get validation rules by entity'
    return { success: false, error: msg } as any
  }
}

export async function createValidationRule(rule: Partial<IntegrationValidationRule>): Promise<ApiResponse<IntegrationValidationRule>> {
  try {
    const { data } = await client.post('/integration-platform/validation-rules', rule)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create validation rule'
    return { success: false, error: msg } as any
  }
}

export async function updateValidationRule(id: string, rule: Partial<IntegrationValidationRule>): Promise<ApiResponse<IntegrationValidationRule>> {
  try {
    const { data } = await client.put(`/integration-platform/validation-rules/${id}`, rule)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update validation rule'
    return { success: false, error: msg } as any
  }
}

export async function toggleValidationRule(id: string, active: boolean): Promise<ApiResponse<IntegrationValidationRule>> {
  try {
    const { data } = await client.put(`/integration-platform/validation-rules/${id}/toggle`, null, { params: { active } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to toggle validation rule'
    return { success: false, error: msg } as any
  }
}

export async function validatePayload(payload: string, entityType: string): Promise<ApiResponse<string[]>> {
  try {
    const { data } = await client.post('/integration-platform/validation-rules/validate', { payload, entityType })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to validate payload'
    return { success: false, error: msg } as any
  }
}

// ─────────────── CDC Events ───────────────

export async function getPendingCdcEvents(): Promise<ApiResponse<IntegrationCDCEvent[]>> {
  try {
    const { data } = await client.get('/integration-platform/cdc/pending')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get pending CDC events'
    return { success: false, error: msg } as any
  }
}

export async function markCdcEventProcessed(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.post(`/integration-platform/cdc/${id}/process`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to mark CDC event processed'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Audit Logs ───────────────

export async function getIntegrationAuditLogs(page = 0, size = 20): Promise<ApiResponse<{
  content: IntegrationAuditLog[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  try {
    const { data } = await client.get('/integration-platform/audit', { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get integration audit logs'
    return { success: false, error: msg } as any
  }
}

export async function getIntegrationAuditLogsByEntity(entityType: string, page = 0, size = 20): Promise<ApiResponse<{
  content: IntegrationAuditLog[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  try {
    const { data } = await client.get('/integration-platform/audit/entity', { params: { entityType, page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get integration audit logs by entity'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Import Jobs ───────────────

export async function getImportJobs(params?: {
  status?: string
  page?: number
  size?: number
}): Promise<ApiResponse<{
  content: IntegrationImportJob[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  try {
    const { data } = await client.get('/integration-platform/imports', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get import jobs'
    return { success: false, error: msg } as any
  }
}

export async function getImportJob(id: string): Promise<ApiResponse<IntegrationImportJob>> {
  try {
    const { data } = await client.get(`/integration-platform/imports/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get import job'
    return { success: false, error: msg } as any
  }
}

export async function createImportJob(job: Partial<IntegrationImportJob>): Promise<ApiResponse<IntegrationImportJob>> {
  try {
    const { data } = await client.post('/integration-platform/imports', job)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create import job'
    return { success: false, error: msg } as any
  }
}

export async function processImportJob(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.post(`/integration-platform/imports/${id}/process`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to process import job'
    return { success: false, error: msg } as any
  }
}

export async function retryImportJob(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.post(`/integration-platform/imports/${id}/retry`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to retry import job'
    return { success: false, error: msg } as any
  }
}

export async function cancelImportJob(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.post(`/integration-platform/imports/${id}/cancel`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to cancel import job'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Export Jobs ───────────────

export async function getExportJobs(params?: {
  status?: string
  page?: number
  size?: number
}): Promise<ApiResponse<{
  content: IntegrationExportJob[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  try {
    const { data } = await client.get('/integration-platform/exports', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get export jobs'
    return { success: false, error: msg } as any
  }
}

export async function getExportJob(id: string): Promise<ApiResponse<IntegrationExportJob>> {
  try {
    const { data } = await client.get(`/integration-platform/exports/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get export job'
    return { success: false, error: msg } as any
  }
}

export async function createExportJob(job: Partial<IntegrationExportJob>): Promise<ApiResponse<IntegrationExportJob>> {
  try {
    const { data } = await client.post('/integration-platform/exports', job)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create export job'
    return { success: false, error: msg } as any
  }
}

export async function processExportJob(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.post(`/integration-platform/exports/${id}/process`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to process export job'
    return { success: false, error: msg } as any
  }
}

export async function retryExportJob(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.post(`/integration-platform/exports/${id}/retry`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to retry export job'
    return { success: false, error: msg } as any
  }
}

export async function cancelExportJob(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.post(`/integration-platform/exports/${id}/cancel`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to cancel export job'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Dead Letter Queue ───────────────

export async function getDLQEntries(params?: {
  status?: string
  page?: number
  size?: number
}): Promise<ApiResponse<{
  content: IntegrationDLQ[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  try {
    const { data } = await client.get('/integration-platform/dlq', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get DLQ entries'
    return { success: false, error: msg } as any
  }
}

export async function replayDLQEntry(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.post(`/integration-platform/dlq/${id}/replay`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to replay DLQ entry'
    return { success: false, error: msg } as any
  }
}

export async function ignoreDLQEntry(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.post(`/integration-platform/dlq/${id}/ignore`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to ignore DLQ entry'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Dashboard ───────────────

export async function getDashboardStats(): Promise<ApiResponse<Record<string, unknown>>> {
  try {
    const { data } = await client.get('/integration-platform/dashboard')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get dashboard stats'
    return { success: false, error: msg } as any
  }
}

// ─────────────── Legacy getEndpoints (non-paginated) ───────────────

export async function getEndpoints(): Promise<ApiResponse<IntegrationEndpoint[]>> {
  try {
    const { data } = await client.get('/integration-platform/endpoints')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get endpoints'
    return { success: false, error: msg } as any
  }
}
