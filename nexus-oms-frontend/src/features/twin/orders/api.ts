import client from '../../../api/client'

/** Mirrors OrderNetworkTwinService records (GET /twin/orders?hours=). */
export interface NetworkKpis {
  orders: number
  revenue: number
  routedPct: number | null
  splitRatePct: number | null
  backorders: number
  avgEstimatedCostPerOrder: number | null
  avgPromiseConfidencePct: number | null
  openExceptions: number
  queueDepth: number
}

export interface NetworkChannel { id: string; name: string; orders: number; revenue: number }

export interface NetworkNode {
  id: string
  code: string | null
  name: string | null
  type: string | null
  city: string | null
  state: string | null
  status: string | null
  fillPct: number | null
  allocations: number
  unitsAllocated: number
  availableUnits: number
  skuCount: number
  avgEstimatedCost: number | null
}

export interface NetworkRegion { id: string; orders: number }
export interface NetworkFlow { from: string; to: string; kind: 'CHANNEL_TO_NODE' | 'NODE_TO_REGION'; orders: number }
export interface StageCount { status: string; orders: number }

export interface AllocationView {
  nodeId: string | null
  nodeName: string | null
  status: string | null
  quantityAllocated: number | null
  quantityRequested: number | null
  strategy: string | null
  ruleName: string | null
  estimatedCost: number | null
  estimatedDistanceKm: number | null
  confidence: number | null
  promiseDate: string | null
}

export interface OrderView {
  id: string
  reference: string
  channel: string
  status: string | null
  total: number | null
  currency: string | null
  region: string
  city: string | null
  createdAt: string | null
  shippedAt: string | null
  deliveredAt: string | null
  allocations: AllocationView[]
}

export interface QueueItem { id: string; orderId: string; priority: string | null; status: string | null; attempts: number | null; failureReason: string | null; enteredAt: string | null }
export interface ExceptionItem { id: string; orderId: string; type: string | null; severity: string | null; status: string | null; title: string | null; suggestedAction: string | null; detectedAt: string | null }

export interface NetworkSnapshot {
  windowHours: number
  since: string
  kpis: NetworkKpis
  channels: NetworkChannel[]
  nodes: NetworkNode[]
  regions: NetworkRegion[]
  flows: NetworkFlow[]
  lifecycle: StageCount[]
  recentOrders: OrderView[]
  queue: QueueItem[]
  exceptions: ExceptionItem[]
  generatedAt: string
}

export async function fetchNetworkSnapshot(hours: number): Promise<NetworkSnapshot> {
  const { data } = await client.get('/twin/orders', { params: { hours } })
  if (data && data.success === false) throw new Error(data.message || 'Request failed')
  return data.data as NetworkSnapshot
}
