import client from './client'
import type { ApiResponse } from '../types'

// ── T-08: RFID / Serialized Inventory ────────────────────────────────────────

export interface RfidScanSession {
  id: string
  tenantId: string
  locationId?: string
  sessionType: string
  mode?: string
  startedBy?: string
  startedAt: string
  completedAt?: string
  status: string
  seenEpcs?: string
  rejectedEpcs?: string
}

export interface RfidScanResult {
  totalReads: number
  accepted: number
  duplicates: number
  rejected: number
  rejectedEpcs: string[]
}

export interface CycleCountResult {
  expected: number
  found: number
  missing: number
  newEpcs: number
  damaged: number
  onHold: number
  missingEpcs: string[]
  newEpcsList: string[]
}

export interface SerializedInventory {
  id: string
  tenantId: string
  locationId?: string
  epc: string
  sku?: string
  status: string
  mode?: string
  receivedAt?: string
  lastSeenAt?: string
  createdAt: string
  updatedAt: string
}

export interface RetagResult {
  oldEpc: string
  newEpc: string
  sku?: string
}

export interface DecodedEpc {
  epc: string
  sku?: string
  serial?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export async function openRfidSession(params: {
  locationId: string
  sessionType: string
  mode?: string
}): Promise<ApiResponse<RfidScanSession>> {
  try {
    const { data } = await client.post('/rfid/sessions', params)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to open RFID session' } as any
  }
}

export async function ingestEpcs(sessionId: string, epcs: string[]): Promise<ApiResponse<RfidScanResult>> {
  try {
    // Backend expects a raw JSON array body (List<String>)
    const { data } = await client.post(`/rfid/sessions/${sessionId}/epcs`, epcs)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to ingest EPCs' } as any
  }
}

export async function completeRfidSession(sessionId: string): Promise<ApiResponse<RfidScanSession>> {
  try {
    const { data } = await client.post(`/rfid/sessions/${sessionId}/complete`)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to complete session' } as any
  }
}

export async function receiveRfid(
  sessionId: string,
  locationId: string,
  epcs: string[],
): Promise<ApiResponse<number>> {
  try {
    const { data } = await client.post(`/rfid/sessions/${sessionId}/receive`, epcs, { params: { locationId } })
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to receive RFID' } as any
  }
}

export async function runRfidCycleCount(
  sessionId: string,
  locationId: string,
): Promise<ApiResponse<CycleCountResult>> {
  try {
    const { data } = await client.post(`/rfid/sessions/${sessionId}/cycle-count`, null, { params: { locationId } })
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to run cycle count' } as any
  }
}

export async function getRfidInventory(params?: {
  locationId?: string
  status?: string
  page?: number
  size?: number
}): Promise<ApiResponse<Page<SerializedInventory>>> {
  try {
    const { data } = await client.get('/rfid/inventory', { params })
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to get RFID inventory' } as any
  }
}

export async function retagRfid(params: {
  oldEpc: string
  newEpc: string
  locationId: string
}): Promise<ApiResponse<RetagResult>> {
  try {
    const { data } = await client.post('/rfid/retag', null, { params })
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to retag' } as any
  }
}

export async function decodeEpc(epc: string): Promise<ApiResponse<DecodedEpc>> {
  try {
    const { data } = await client.get(`/rfid/decode/${epc}`)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to decode EPC' } as any
  }
}
