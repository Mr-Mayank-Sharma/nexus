import client from '../../api/client'

export type PosStatus = 'MATCH' | 'DRIFT' | 'STALE' | 'NO_POS_DATA' | 'NO_NEXUS_ROW'
export type ReconcileOutcome = 'PLANNED' | 'APPLIED' | 'BLOCKED' | 'SKIPPED' | 'UNCHANGED'

export interface PosSource {
  id: string
  tenantId?: string
  nodeId: string
  name: string
  vendor: string
  staleAfterMinutes: number
  enabled: boolean
  lastSnapshotAt: string | null
  createdAt: string
}

export interface PosRow {
  sku: string
  nodeId: string
  nexusOnHand: number | null
  nexusAllocated: number | null
  posQuantity: number | null
  delta: number | null
  posReportedAt: string | null
  status: PosStatus
}

export interface ReconcileLine { sku: string; outcome: ReconcileOutcome; from: number | null; to: number | null; reason: string }
export interface ReconcileResult { applied: boolean; lines: ReconcileLine[] }
export interface CreatedSource { source: PosSource; secret: string; note: string }

export async function getSources(): Promise<PosSource[]> {
  return (await client.get('/pos/sources')).data?.data ?? []
}

export async function createSource(nodeId: string, name: string, vendor: string, staleAfterMinutes?: number): Promise<CreatedSource> {
  return (await client.post('/pos/sources', { nodeId, name, vendor, staleAfterMinutes })).data.data
}

export async function rotateKey(id: string): Promise<CreatedSource> {
  return (await client.post(`/pos/sources/${id}/rotate-key`)).data.data
}

export async function getAggregate(nodeId: string): Promise<{ summary: Record<string, number>; rows: PosRow[] }> {
  const d = (await client.get('/pos/inventory/aggregate', { params: { nodeId } })).data?.data
  return { summary: d?.summary ?? {}, rows: d?.rows ?? [] }
}

export async function reconcile(nodeId: string, apply: boolean, skus?: string[]): Promise<ReconcileResult> {
  return (await client.post('/pos/inventory/reconcile', { nodeId, apply, skus })).data.data
}
