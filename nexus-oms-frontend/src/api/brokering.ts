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

export const brokeringApi = {
  enqueueOrder: (orderId: string, priority: string = 'NORMAL') =>
    client.post<BrokeringQueueEntry>(`/brokering/enqueue?orderId=${orderId}&priority=${priority}`),

  processBrokeringQueue: () =>
    client.post<BrokeringRun>('/brokering/process'),

  processPriorityQueue: () =>
    client.post<BrokeringRun>('/brokering/process/priority'),

  manualBrokeringRun: (orderIds: string[]) =>
    client.post<BrokeringRun>('/brokering/process/manual', orderIds),

  getQueue: (status?: string) =>
    client.get<BrokeringQueueEntry[]>('/brokering/queue', { params: { status } }),

  getQueueStats: () =>
    client.get<BrokeringStats>('/brokering/queue/stats'),

  removeFromQueue: (id: string) =>
    client.delete(`/brokering/queue/${id}`),

  getRunHistory: () =>
    client.get<BrokeringRun[]>('/brokering/runs'),

  getRun: (id: string) =>
    client.get<BrokeringRun>(`/brokering/runs/${id}`),

  expireStaleOrders: () =>
    client.post<BrokeringQueueEntry[]>('/brokering/expire-stale'),
}

export default brokeringApi
