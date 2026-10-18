import client from '../../api/client'

export type StrategyKey = 'NEAREST_FIRST' | 'FEWEST_SHIPMENTS' | 'FAILURE_AWARE'

export interface ReplayMetrics {
  strategy: StrategyKey
  label: string
  orders: number
  filled: number
  partial: number
  unfillable: number
  shipments: number
  splitOrders: number
  splitRate: number
  shipmentsPerOrder: number
  unfilledUnits: number
  expectedFailedUnits: number
  shippingCost: number
  reworkCost: number
  totalCost: number
}
export interface ReplayDifference { orderId: string; shipmentsBefore: number; shipmentsAfter: number; before: string; after: string }
export interface ReplayResult {
  strategies: ReplayMetrics[]
  differences: ReplayDifference[]
  differencesTotal: number
  usedDistance: boolean
  usedFailureRates: boolean
  assumptions: string[]
}
export interface ReplayResponse { orders: number; orderLines: number; skus: number; locations: number; result: ReplayResult }
export interface ReplayCosts { perShipment: number; perKm: number; perFailedUnit: number | null }
export interface SampleFiles { orders: string; stock: string; nodes: string }

export async function getSample(): Promise<SampleFiles> {
  return (await client.get('/order-routing/replay/sample')).data.data
}

export async function runReplay(orders: File, stock: File, nodes: File | null, costs: ReplayCosts): Promise<ReplayResponse> {
  const body = new FormData()
  body.append('orders', orders)
  body.append('stock', stock)
  if (nodes) body.append('nodes', nodes)
  body.append('costPerShipment', String(costs.perShipment))
  body.append('costPerKm', String(costs.perKm))
  if (costs.perFailedUnit !== null) body.append('costPerFailedUnit', String(costs.perFailedUnit))
  return (await client.post('/order-routing/replay', body, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })).data.data
}
