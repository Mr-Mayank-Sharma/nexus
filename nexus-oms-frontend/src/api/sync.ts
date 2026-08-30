import client from './client'
import type { ApiResponse } from '../types'

// ── T-13: Bidirectional Sync & Conflict Handling ─────────────────────────────

export interface SyncConflict {
  id: string
  tenantId: string
  entityType: string
  entityId: string
  direction: string // INBOUND | OUTBOUND
  sourceSystem: string
  inboundVersion?: number
  localVersion?: number
  conflictingFields?: string // JSON string of conflicting field values
  resolution?: string // PENDING | LOCAL_WINS | INBOUND_WINS | MERGED | MANUAL
  status: string // OPEN | RESOLVED
  createdAt?: string
  resolvedAt?: string
  resolvedBy?: string
}

export interface SyncFieldMapping {
  id: string
  tenantId: string
  entityType: string
  fieldName: string
  direction: string // INBOUND | OUTBOUND
  winner: string // LOCAL | REMOTE
  isActive: boolean
  createdAt?: string
}

export interface DirectionSummary {
  direction: string // INBOUND | OUTBOUND
  pending: number
  queued: number
  completed: number
  failed: number
}

export interface SyncReconciliationReport {
  directions: DirectionSummary[]
  openConflicts: number
  resolvedConflicts: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export async function getSyncConflicts(params?: {
  status?: string
  page?: number
  size?: number
}): Promise<ApiResponse<Page<SyncConflict>>> {
  try {
    const { data } = await client.get('/sync/conflicts', { params })
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to get sync conflicts' } as any
  }
}

export async function getSyncConflict(id: string): Promise<ApiResponse<SyncConflict>> {
  try {
    const { data } = await client.get(`/sync/conflicts/${id}`)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to get conflict' } as any
  }
}

export async function resolveSyncConflict(
  id: string,
  resolution: {
    resolution: string // LOCAL_WINS | INBOUND_WINS | MERGED | MANUAL
    mergedFields?: Record<string, any>
  },
): Promise<ApiResponse<SyncConflict>> {
  try {
    const { data } = await client.post(`/sync/conflicts/${id}/resolve`, resolution)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to resolve conflict' } as any
  }
}

export async function getSyncReconciliation(): Promise<ApiResponse<SyncReconciliationReport>> {
  try {
    const { data } = await client.get('/sync/reconciliation')
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to get reconciliation' } as any
  }
}

export async function getSyncFieldMappings(): Promise<ApiResponse<SyncFieldMapping[]>> {
  try {
    const { data } = await client.get('/sync/field-mappings')
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to get field mappings' } as any
  }
}

export async function upsertSyncFieldMapping(mapping: {
  entityType: string
  fieldName: string
  direction: string
  winner: string
  isActive?: boolean
}): Promise<ApiResponse<SyncFieldMapping>> {
  try {
    const { data } = await client.post('/sync/field-mappings', mapping)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to save field mapping' } as any
  }
}

export async function triggerSyncRecovery(): Promise<ApiResponse<number>> {
  try {
    const { data } = await client.post('/sync/recovery')
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to trigger recovery' } as any
  }
}
