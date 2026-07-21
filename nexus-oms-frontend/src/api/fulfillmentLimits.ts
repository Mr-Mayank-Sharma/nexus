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

export const fulfillmentLimitsApi = {
  getLimits: () =>
    client.get<FulfillmentLimit[]>('/fulfillment-limits'),

  getLimit: (nodeId: string) =>
    client.get<FulfillmentLimit>(`/fulfillment-limits/${nodeId}`),

  createLimit: (limit: Partial<FulfillmentLimit>) =>
    client.post<FulfillmentLimit>('/fulfillment-limits', limit),

  updateLimit: (id: string, limit: Partial<FulfillmentLimit>) =>
    client.put<FulfillmentLimit>(`/fulfillment-limits/${id}`, limit),

  checkCapacity: (nodeId: string) =>
    client.get<CapacityCheck>(`/fulfillment-limits/${nodeId}/capacity`),

  toggleFulfillment: (nodeId: string, enabled: boolean) =>
    client.put<FulfillmentLimit>(`/fulfillment-limits/${nodeId}/toggle`, null, {
      params: { enabled },
    }),

  getCapacityAlerts: () =>
    client.get<CapacityLog[]>('/fulfillment-limits/alerts'),

  getCapacityHistory: (nodeId: string, start?: string, end?: string) =>
    client.get<CapacityLog[]>(`/fulfillment-limits/history/${nodeId}`, {
      params: { start, end },
    }),

  resetDailyCounts: () =>
    client.post<void>('/fulfillment-limits/reset'),
}

export default fulfillmentLimitsApi
