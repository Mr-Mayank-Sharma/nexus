import client from './client'

export interface BrokeringQueueEntry {
  id: string
  tenantId: string
  orderId: string
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
  status: 'WAITING' | 'PROCESSING' | 'ALLOCATED' | 'FAILED' | 'EXPIRED'
  attempts: number
  maxAttempts: number
  lastAttemptAt?: string
  nextRunAt?: string
  allocatedNodeId?: string
  failureReason?: string
  enteredAt: string
  exitedAt?: string
  createdAt: string
  updatedAt: string
}

export interface BrokeringRun {
  id: string
  tenantId: string
  runType: 'SCHEDULED' | 'MANUAL' | 'PRIORITY'
  startedAt: string
  completedAt?: string
  ordersProcessed: number
  ordersAllocated: number
  ordersFailed: number
  executionTimeMs?: number
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  triggeredBy?: string
  createdAt: string
}

export interface BrokeringStats {
  waiting: number
  processing: number
  allocated: number
  failed: number
  expired: number
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

export const brokeringApi = {
  enqueueOrder: (orderId: string, priority: string = 'NORMAL') =>
    unwrap<BrokeringQueueEntry>(client.post<BrokeringQueueEntry>(`/brokering/enqueue?orderId=${orderId}&priority=${priority}`)),

  processBrokeringQueue: () =>
    unwrap<BrokeringRun>(client.post<BrokeringRun>('/brokering/process')),

  processPriorityQueue: () =>
    unwrap<BrokeringRun>(client.post<BrokeringRun>('/brokering/process/priority')),

  manualBrokeringRun: (orderIds: string[]) =>
    unwrap<BrokeringRun>(client.post<BrokeringRun>('/brokering/process/manual', orderIds)),

  getQueue: (status?: string) =>
    unwrap<BrokeringQueueEntry[]>(client.get<BrokeringQueueEntry[]>('/brokering/queue', { params: { status } })),

  getQueueStats: () =>
    unwrap<BrokeringStats>(client.get<BrokeringStats>('/brokering/queue/stats')),

  removeFromQueue: (id: string) =>
    client.delete(`/brokering/queue/${id}`),

  getRunHistory: () =>
    unwrap<BrokeringRun[]>(client.get<BrokeringRun[]>('/brokering/runs')),

  getRun: (id: string) =>
    unwrap<BrokeringRun>(client.get<BrokeringRun>(`/brokering/runs/${id}`)),

  expireStaleOrders: () =>
    unwrap<BrokeringQueueEntry[]>(client.post<BrokeringQueueEntry[]>('/brokering/expire-stale')),
}

export default brokeringApi
