import client from './client'

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
    client.get<NxATPRule[]>('/atp/rules'),

  getRule: (id: string) =>
    client.get<NxATPRule>(`/atp/rules/${id}`),

  createRule: (data: NxATPRule) =>
    client.post<NxATPRule>('/atp/rules', data),

  updateRule: (id: string, data: NxATPRule) =>
    client.put<NxATPRule>(`/atp/rules/${id}`, data),

  deleteRule: (id: string) =>
    client.delete(`/atp/rules/${id}`),

  // Calculation
  calculateATP: (nodeId: string, sku: string) =>
    client.get<ATPCalculationResult>(`/atp/calculate/${nodeId}/${sku}`),

  calculateBulkATP: (nodeId: string, skus: string[]) =>
    client.post<BulkATPResult>(`/atp/calculate/${nodeId}/bulk`, skus),

  findNodesWithATP: (sku: string, requiredQuantity: number) =>
    client.get<NodeATPResult[]>('/atp/find-nodes', { params: { sku, requiredQuantity } }),

  // Reservations
  reserveStock: (nodeId: string, sku: string, quantity: number) =>
    client.post<{ success: boolean }>('/atp/reserve', null, { params: { nodeId, sku, quantity } }),

  releaseReservation: (nodeId: string, sku: string, quantity: number) =>
    client.post('/atp/release', null, { params: { nodeId, sku, quantity } }),

  allocateStock: (nodeId: string, sku: string, quantity: number) =>
    client.post('/atp/allocate', null, { params: { nodeId, sku, quantity } }),

  // Snapshots
  getSnapshots: (nodeId?: string, sku?: string) =>
    client.get<NxATPSnapshot[]>('/atp/snapshots', { params: { nodeId, sku } }),

  updateSnapshot: (nodeId: string, sku: string, physicalStock: number) =>
    client.post<NxATPSnapshot>('/atp/snapshots', null, { params: { nodeId, sku, physicalStock } }),
}

export default atpApi
