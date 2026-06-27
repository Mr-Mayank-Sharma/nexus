import client from './client'
import type { ApiResponse } from '../types'

export interface IntegrationImportJob {
  id: string
  tenantId: string
  flowId?: string
  jobName: string
  sourceType: string
  targetType: string
  fileName?: string
  fileSize?: number
  recordCount: number
  successCount: number
  errorCount: number
  status: string
  errorSummary?: string
  processingTimeMs?: number
  startedAt?: string
  completedAt?: string
  createdAt: string
  updatedAt: string
}

export interface IntegrationExportJob {
  id: string
  tenantId: string
  flowId?: string
  jobName: string
  exportType: string
  format: string
  status: string
  recordCount: number
  fileSize?: number
  fileUrl?: string
  errorSummary?: string
  processingTimeMs?: number
  startedAt?: string
  completedAt?: string
  createdAt: string
  updatedAt: string
}

export interface IntegrationDLQ {
  id: string
  messageId: string
  flowId?: string
  flowName?: string
  errorCategory: string
  errorMessage: string
  errorDetail?: string
  retryCount: number
  lastRetryAt?: string
  status: string
  payload?: string
  createdAt: string
}

export interface IntegrationEndpoint {
  id: string
  name: string
  type: string
  status: string
  lastTestedAt?: string
  errorMessage?: string
}

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

export async function getEndpoints(): Promise<ApiResponse<IntegrationEndpoint[]>> {
  const { data } = await client.get('/integration-platform/endpoints')
  return data
}

export async function getDashboardStats(): Promise<ApiResponse<Record<string, any>>> {
  const { data } = await client.get('/integration-platform/dashboard')
  return data
}
