import client from './client'
import { ApiResponse, AuditEntry, AuditPage } from '../types'
export type { AuditEntry, AuditPage }

export async function getAuditLogs(page = 0, size = 25): Promise<ApiResponse<AuditPage>> {
  try {
    const { data } = await client.get('/integration-platform/audit', { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get audit logs'
    return { success: false, error: msg } as any
  }
}

export async function getAuditByEntity(entityType: string, entityId: string, page = 0): Promise<ApiResponse<AuditPage>> {
  try {
    const { data } = await client.get('/integration-platform/audit/entity', { params: { entityType, entityId, page } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get audit by entity'
    return { success: false, error: msg } as any
  }
}
