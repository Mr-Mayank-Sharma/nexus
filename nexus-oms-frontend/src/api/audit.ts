import client from './client'
import { ApiResponse } from '../types'

export interface AuditEntry {
  id: string
  tenantId: string
  flowId?: string
  messageId?: string
  entityType: string
  entityId: string
  action: string
  status: string
  requestPayload?: string
  responsePayload?: string
  sourceSystem?: string
  targetSystem?: string
  processingTimeMs?: number
  errorMessage?: string
  ipAddress?: string
  userAgent?: string
  createdBy?: string
  createdAt: string
}

export interface AuditPage {
  content: AuditEntry[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export async function getAuditLogs(page = 0, size = 25): Promise<ApiResponse<AuditPage>> {
  const { data } = await client.get('/integration-platform/audit', { params: { page, size } })
  return data
}

export async function getAuditByEntity(entityType: string, entityId: string, page = 0): Promise<ApiResponse<AuditPage>> {
  const { data } = await client.get('/integration-platform/audit/entity', { params: { entityType, entityId, page } })
  return data
}
