import client from './client'

const WRAPPER_KEYS = ['success', 'data', 'message', 'error', 'errors', 'pagination']

/** Client normalizes every body to {success, data, message, ...}; unwrap back to the payload. */
async function unwrap<T>(promise: Promise<{ data: unknown }>): Promise<{ data: T }> {
  const res = await promise
  const body = res.data
  if (body && typeof body === 'object' && !Array.isArray(body) && 'data' in body) {
    const obj = body as Record<string, unknown>
    if (Object.keys(obj).every(k => WRAPPER_KEYS.includes(k))) return { data: obj.data as T }
  }
  return { data: body as T }
}

export interface NxATPRule {
  id?: string
  tenantId?: string
  ruleName: string
  ruleType: 'PERCENTAGE' | 'FIXED' | 'DYNAMIC'
  priority: number
  enabled: boolean
  nodeId?: string
  nodeName?: string
  sku?: string
  formula?: string
  leadTimeDays?: number
  safetyStock?: number
  reorderPoint?: number
  reorderQuantity?: number
  description?: string
  createdAt?: string
  updatedAt?: string
}

export interface NxATPSnapshot {
  id?: string
  tenantId?: string
  nodeId: string
  nodeName?: string
  sku: string
  physicalStock: number
  reservedStock: number
  allocatedStock: number
  availableToPromise: number
  inTransitStock: number
  lastCalculatedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface ATPCalculationResult {
  nodeId: string
  nodeName?: string
  sku: string
  physicalStock: number
  reservedStock: number
  allocatedStock: number
  availableToPromise: number
  inTransitStock: number
  safetyStock: number
  reorderPoint: number
  lastCalculatedAt: string
}

export interface NodeATPResult {
  nodeId: string
  nodeName: string
  distance?: number
  availableToPromise: number
  physicalStock: number
  reservedStock: number
  allocatedStock: number
}

export interface BulkATPResult {
  calculations: ATPCalculationResult[]
  summary: {
    totalATP: number
    totalPhysicalStock: number
    totalReserved: number
    totalAllocated: number
  }
}

export const atpApi = {
  // Rules
  getRules: () =>
    unwrap<NxATPRule[]>(client.get<NxATPRule[]>('/atp/rules')),

  getRule: (id: string) =>
    unwrap<NxATPRule>(client.get<NxATPRule>(`/atp/rules/${id}`)),

  createRule: (data: NxATPRule) =>
    unwrap<NxATPRule>(client.post<NxATPRule>('/atp/rules', data)),

  updateRule: (id: string, data: NxATPRule) =>
    unwrap<NxATPRule>(client.put<NxATPRule>(`/atp/rules/${id}`, data)),

  deleteRule: (id: string) =>
    unwrap<void>(client.delete(`/atp/rules/${id}`)),

  // Calculation
  calculateATP: (nodeId: string, sku: string) =>
    unwrap<ATPCalculationResult>(client.get<ATPCalculationResult>(`/atp/calculate/${nodeId}/${sku}`)),

  calculateBulkATP: (nodeId: string, skus: string[]) =>
    unwrap<BulkATPResult>(client.post<BulkATPResult>(`/atp/calculate/${nodeId}/bulk`, skus)),

  findNodesWithATP: (sku: string, requiredQuantity: number) =>
    unwrap<NodeATPResult[]>(client.get<NodeATPResult[]>('/atp/find-nodes', { params: { sku, requiredQuantity } })),

  // Reservations
  reserveStock: (nodeId: string, sku: string, quantity: number) =>
    unwrap<{ success: boolean }>(client.post<{ success: boolean }>('/atp/reserve', null, { params: { nodeId, sku, quantity } })),

  releaseReservation: (nodeId: string, sku: string, quantity: number) =>
    unwrap<void>(client.post('/atp/release', null, { params: { nodeId, sku, quantity } })),

  allocateStock: (nodeId: string, sku: string, quantity: number) =>
    unwrap<void>(client.post('/atp/allocate', null, { params: { nodeId, sku, quantity } })),

  // Snapshots
  getSnapshots: (nodeId?: string, sku?: string) =>
    unwrap<NxATPSnapshot[]>(client.get<NxATPSnapshot[]>('/atp/snapshots', { params: { nodeId, sku } })),

  updateSnapshot: (nodeId: string, sku: string, physicalStock: number) =>
    unwrap<NxATPSnapshot>(client.post<NxATPSnapshot>('/atp/snapshots', null, { params: { nodeId, sku, physicalStock } })),
}

export default atpApi