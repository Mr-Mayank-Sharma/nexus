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
  const { data } = await client.get('/integration-platform/endpoints', { params: { page, size } })
  return data
}

export async function getEndpoint(id: string): Promise<ApiResponse<IntegrationEndpoint>> {
  const { data } = await client.get(`/integration-platform/endpoints/${id}`)
  return data
}

export async function createEndpoint(endpoint: Partial<IntegrationEndpoint>): Promise<ApiResponse<IntegrationEndpoint>> {
  const { data } = await client.post('/integration-platform/endpoints', endpoint)
  return data
}

export async function updateEndpoint(id: string, endpoint: Partial<IntegrationEndpoint>): Promise<ApiResponse<IntegrationEndpoint>> {
  const { data } = await client.put(`/integration-platform/endpoints/${id}`, endpoint)
  return data
}

export async function deleteEndpoint(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.delete(`/integration-platform/endpoints/${id}`)
  return data
}

export async function testEndpoint(id: string): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.post(`/integration-platform/endpoints/${id}/test`)
  return data
}

// ─────────────── Flows CRUD ───────────────

export async function getFlows(page = 0, size = 20): Promise<ApiResponse<{
  content: IntegrationFlow[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  const { data } = await client.get('/integration-platform/flows', { params: { page, size } })
  return data
}

export async function getFlow(id: string): Promise<ApiResponse<IntegrationFlow>> {
  const { data } = await client.get(`/integration-platform/flows/${id}`)
  return data
}

export async function createFlow(flow: Partial<IntegrationFlow>): Promise<ApiResponse<IntegrationFlow>> {
  const { data } = await client.post('/integration-platform/flows', flow)
  return data
}

export async function updateFlow(id: string, flow: Partial<IntegrationFlow>): Promise<ApiResponse<IntegrationFlow>> {
  const { data } = await client.put(`/integration-platform/flows/${id}`, flow)
  return data
}

export async function deleteFlow(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.delete(`/integration-platform/flows/${id}`)
  return data
}

export async function activateFlow(id: string): Promise<ApiResponse<IntegrationFlow>> {
  const { data } = await client.post(`/integration-platform/flows/${id}/activate`)
  return data
}

export async function pauseFlow(id: string): Promise<ApiResponse<IntegrationFlow>> {
  const { data } = await client.post(`/integration-platform/flows/${id}/pause`)
  return data
}

export async function getFlowSteps(flowId: string): Promise<ApiResponse<IntegrationFlowStep[]>> {
  const { data } = await client.get(`/integration-platform/flows/${flowId}/steps`)
  return data
}

export async function addFlowStep(flowId: string, step: Partial<IntegrationFlowStep>): Promise<ApiResponse<IntegrationFlowStep>> {
  const { data } = await client.post(`/integration-platform/flows/${flowId}/steps`, step)
  return data
}

export async function updateFlowStep(id: string, step: Partial<IntegrationFlowStep>): Promise<ApiResponse<IntegrationFlowStep>> {
  const { data } = await client.put(`/integration-platform/flows/steps/${id}`, step)
  return data
}

export async function deleteFlowStep(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.delete(`/integration-platform/flows/steps/${id}`)
  return data
}

export async function reorderFlowSteps(flowId: string, stepIds: Array<{ stepId: string }>): Promise<ApiResponse<IntegrationFlowStep[]>> {
  const { data } = await client.put(`/integration-platform/flows/${flowId}/steps/reorder`, stepIds)
  return data
}

// ─────────────── Transform Mappings ───────────────

export async function getMappings(page = 0, size = 20): Promise<ApiResponse<{
  content: IntegrationTransformMapping[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  const { data } = await client.get('/integration-platform/mappings', { params: { page, size } })
  return data
}

export async function getMapping(id: string): Promise<ApiResponse<IntegrationTransformMapping>> {
  const { data } = await client.get(`/integration-platform/mappings/${id}`)
  return data
}

export async function createMapping(mapping: Partial<IntegrationTransformMapping>): Promise<ApiResponse<IntegrationTransformMapping>> {
  const { data } = await client.post('/integration-platform/mappings', mapping)
  return data
}

export async function updateMapping(id: string, mapping: Partial<IntegrationTransformMapping>): Promise<ApiResponse<IntegrationTransformMapping>> {
  const { data } = await client.put(`/integration-platform/mappings/${id}`, mapping)
  return data
}

export async function transformMapping(id: string, payload: string, sourceFormat: string, targetFormat: string): Promise<ApiResponse<string>> {
  const { data } = await client.post(`/integration-platform/mappings/${id}/transform`, { payload, sourceFormat, targetFormat })
  return data
}

// ─────────────── Validation Rules ───────────────

export async function getValidationRules(): Promise<ApiResponse<IntegrationValidationRule[]>> {
  const { data } = await client.get('/integration-platform/validation-rules')
  return data
}

export async function getValidationRulesByEntity(entityType: string): Promise<ApiResponse<IntegrationValidationRule[]>> {
  const { data } = await client.get('/integration-platform/validation-rules/entity', { params: { entityType } })
  return data
}

export async function createValidationRule(rule: Partial<IntegrationValidationRule>): Promise<ApiResponse<IntegrationValidationRule>> {
  const { data } = await client.post('/integration-platform/validation-rules', rule)
  return data
}

export async function updateValidationRule(id: string, rule: Partial<IntegrationValidationRule>): Promise<ApiResponse<IntegrationValidationRule>> {
  const { data } = await client.put(`/integration-platform/validation-rules/${id}`, rule)
  return data
}

export async function toggleValidationRule(id: string, active: boolean): Promise<ApiResponse<IntegrationValidationRule>> {
  const { data } = await client.put(`/integration-platform/validation-rules/${id}/toggle`, null, { params: { active } })
  return data
}

export async function validatePayload(payload: string, entityType: string): Promise<ApiResponse<string[]>> {
  const { data } = await client.post('/integration-platform/validation-rules/validate', { payload, entityType })
  return data
}

// ─────────────── CDC Events ───────────────

export async function getPendingCdcEvents(): Promise<ApiResponse<IntegrationCDCEvent[]>> {
  const { data } = await client.get('/integration-platform/cdc/pending')
  return data
}

export async function markCdcEventProcessed(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.post(`/integration-platform/cdc/${id}/process`)
  return data
}

// ─────────────── Audit Logs ───────────────

export async function getIntegrationAuditLogs(page = 0, size = 20): Promise<ApiResponse<{
  content: IntegrationAuditLog[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  const { data } = await client.get('/integration-platform/audit', { params: { page, size } })
  return data
}

export async function getIntegrationAuditLogsByEntity(entityType: string, page = 0, size = 20): Promise<ApiResponse<{
  content: IntegrationAuditLog[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  const { data } = await client.get('/integration-platform/audit/entity', { params: { entityType, page, size } })
  return data
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
  const { data } = await client.get('/integration-platform/imports', { params })
  return data
}

export async function getImportJob(id: string): Promise<ApiResponse<IntegrationImportJob>> {
  const { data } = await client.get(`/integration-platform/imports/${id}`)
  return data
}

export async function createImportJob(job: Partial<IntegrationImportJob>): Promise<ApiResponse<IntegrationImportJob>> {
  const { data } = await client.post('/integration-platform/imports', job)
  return data
}

export async function processImportJob(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.post(`/integration-platform/imports/${id}/process`)
  return data
}

export async function retryImportJob(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.post(`/integration-platform/imports/${id}/retry`)
  return data
}

export async function cancelImportJob(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.post(`/integration-platform/imports/${id}/cancel`)
  return data
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
  const { data } = await client.get('/integration-platform/exports', { params })
  return data
}

export async function getExportJob(id: string): Promise<ApiResponse<IntegrationExportJob>> {
  const { data } = await client.get(`/integration-platform/exports/${id}`)
  return data
}

export async function createExportJob(job: Partial<IntegrationExportJob>): Promise<ApiResponse<IntegrationExportJob>> {
  const { data } = await client.post('/integration-platform/exports', job)
  return data
}

export async function processExportJob(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.post(`/integration-platform/exports/${id}/process`)
  return data
}

export async function retryExportJob(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.post(`/integration-platform/exports/${id}/retry`)
  return data
}

export async function cancelExportJob(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.post(`/integration-platform/exports/${id}/cancel`)
  return data
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
  const { data } = await client.get('/integration-platform/dlq', { params })
  return data
}

export async function replayDLQEntry(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.post(`/integration-platform/dlq/${id}/replay`)
  return data
}

export async function ignoreDLQEntry(id: string): Promise<ApiResponse<void>> {
  const { data } = await client.post(`/integration-platform/dlq/${id}/ignore`)
  return data
}

// ─────────────── Dashboard ───────────────

export async function getDashboardStats(): Promise<ApiResponse<Record<string, unknown>>> {
  const { data } = await client.get('/integration-platform/dashboard')
  return data
}

// ─────────────── Legacy getEndpoints (non-paginated) ───────────────

export async function getEndpoints(): Promise<ApiResponse<IntegrationEndpoint[]>> {
  const { data } = await client.get('/integration-platform/endpoints')
  return data
}
