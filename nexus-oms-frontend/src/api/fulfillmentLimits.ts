import client from './client'

export interface FulfillmentLimit {
  id: string
  tenantId: string
  nodeId: string
  maxOrdersPerDay?: number
  maxOrdersPerWeek?: number
  maxItemsPerDay?: number
  currentOrdersToday: number
  currentOrdersThisWeek: number
  currentItemsToday: number
  fulfillmentEnabled: boolean
  alertThreshold: number
  lastResetAt?: string
  createdAt: string
  updatedAt: string
}

export interface CapacityLog {
  id: string
  tenantId: string
  nodeId: string
  orderId?: string
  action: string // ORDER_ASSIGNED, ORDER_REMOVED, LIMIT_REACHED, LIMIT_RESET
  ordersBefore?: number
  ordersAfter?: number
  capacityPercentage?: number
  createdAt: string
}

export interface CapacityCheck {
  nodeId: string
  enabled: boolean
  ordersToday: number
  maxOrdersPerDay?: number
  ordersThisWeek: number
  maxOrdersPerWeek?: number
  itemsToday: number
  maxItemsPerDay?: number
  dayUtilization: number
  weekUtilization: number
  atCapacity: boolean
}

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

export const fulfillmentLimitsApi = {
  getLimits: () =>
    unwrap<FulfillmentLimit[]>(client.get<FulfillmentLimit[]>('/fulfillment-limits')),

  getLimit: (nodeId: string) =>
    unwrap<FulfillmentLimit>(client.get<FulfillmentLimit>(`/fulfillment-limits/${nodeId}`)),

  createLimit: (limit: Partial<FulfillmentLimit>) =>
    unwrap<FulfillmentLimit>(client.post<FulfillmentLimit>('/fulfillment-limits', limit)),

  updateLimit: (id: string, limit: Partial<FulfillmentLimit>) =>
    unwrap<FulfillmentLimit>(client.put<FulfillmentLimit>(`/fulfillment-limits/${id}`, limit)),

  checkCapacity: (nodeId: string) =>
    unwrap<CapacityCheck>(client.get<CapacityCheck>(`/fulfillment-limits/${nodeId}/capacity`)),

  toggleFulfillment: (nodeId: string, enabled: boolean) =>
    unwrap<FulfillmentLimit>(client.put<FulfillmentLimit>(`/fulfillment-limits/${nodeId}/toggle`, null, {
      params: { enabled },
    })),

  getCapacityAlerts: () =>
    unwrap<CapacityLog[]>(client.get<CapacityLog[]>('/fulfillment-limits/alerts')),

  getCapacityHistory: (nodeId: string, start?: string, end?: string) =>
    unwrap<CapacityLog[]>(client.get<CapacityLog[]>(`/fulfillment-limits/history/${nodeId}`, {
      params: { start, end },
    })),

  resetDailyCounts: () =>
    unwrap<void>(client.post<void>('/fulfillment-limits/reset')),
}

export default fulfillmentLimitsApi
