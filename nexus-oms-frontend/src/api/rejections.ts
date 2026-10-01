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

export interface RejectionReason {
  id: string
  tenantId: string
  code: string
  label: string
  description?: string
  category: string // QUALITY, DAMAGED, WRONG_ITEM, CUSTOMER, INVENTORY, OTHER
  inventoryImpact: string // RESTOCK, DAMAGE_WRITE_OFF, RETURN_TO_VENDOR, QUARANTINE
  requiresPhoto: boolean
  requiresNotes: boolean
  active: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface OrderRejection {
  id: string
  tenantId: string
  orderId: string
  orderNumber: string
  orderItemId?: string
  sku: string
  rejectionReasonId: string
  rejectionCode: string
  quantity: number
  rejectedBy: string
  notes?: string
  photoPath?: string
  inventoryAction: string
  inventoryAdjusted: boolean
  status: string // PENDING, PROCESSED, CANCELLED
  processedAt?: string
  createdAt: string
  updatedAt: string
}

export interface RejectionStats {
  totalRejections: number
  pending: number
  processed: number
  cancelled: number
  topReasons: Array<{ code: string; count: number }>
}

export const rejectionsApi = {
  // Reasons
  getReasons: () =>
    unwrap<RejectionReason[]>(client.get<RejectionReason[]>('/rejections/reasons')),

  getReason: (id: string) =>
    unwrap<RejectionReason>(client.get<RejectionReason>(`/rejections/reasons/${id}`)),

  getReasonsByCategory: (category: string) =>
    unwrap<RejectionReason[]>(client.get<RejectionReason[]>(`/rejections/reasons/category/${category}`)),

  createReason: (reason: Partial<RejectionReason>) =>
    unwrap<RejectionReason>(client.post<RejectionReason>('/rejections/reasons', reason)),

  updateReason: (id: string, reason: Partial<RejectionReason>) =>
    unwrap<RejectionReason>(client.put<RejectionReason>(`/rejections/reasons/${id}`, reason)),

  deleteReason: (id: string) =>
    unwrap<void>(client.delete(`/rejections/reasons/${id}`)),

  // Rejections
  getAllRejections: () =>
    unwrap<OrderRejection[]>(client.get<OrderRejection[]>('/rejections')),

  getPendingRejections: () =>
    unwrap<OrderRejection[]>(client.get<OrderRejection[]>('/rejections/pending')),

  getRejectionsByOrder: (orderId: string) =>
    unwrap<OrderRejection[]>(client.get<OrderRejection[]>(`/rejections/order/${orderId}`)),

  rejectItem: (rejection: Partial<OrderRejection>) =>
    unwrap<OrderRejection>(client.post<OrderRejection>('/rejections', rejection)),

  processRejection: (id: string) =>
    unwrap<OrderRejection>(client.post<OrderRejection>(`/rejections/${id}/process`)),

  getRejectionStats: () =>
    unwrap<RejectionStats>(client.get<RejectionStats>('/rejections/stats')),
}

export default rejectionsApi