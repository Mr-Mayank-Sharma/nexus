import client from './client'

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
    client.get<RejectionReason[]>('/rejections/reasons'),

  getReason: (id: string) =>
    client.get<RejectionReason>(`/rejections/reasons/${id}`),

  getReasonsByCategory: (category: string) =>
    client.get<RejectionReason[]>(`/rejections/reasons/category/${category}`),

  createReason: (reason: Partial<RejectionReason>) =>
    client.post<RejectionReason>('/rejections/reasons', reason),

  updateReason: (id: string, reason: Partial<RejectionReason>) =>
    client.put<RejectionReason>(`/rejections/reasons/${id}`, reason),

  deleteReason: (id: string) =>
    client.delete(`/rejections/reasons/${id}`),

  // Rejections
  getAllRejections: () =>
    client.get<OrderRejection[]>('/rejections'),

  getPendingRejections: () =>
    client.get<OrderRejection[]>('/rejections/pending'),

  getRejectionsByOrder: (orderId: string) =>
    client.get<OrderRejection[]>(`/rejections/order/${orderId}`),

  rejectItem: (rejection: Partial<OrderRejection>) =>
    client.post<OrderRejection>('/rejections', rejection),

  processRejection: (id: string) =>
    client.post<OrderRejection>(`/rejections/${id}/process`),

  getRejectionStats: () =>
    client.get<RejectionStats>('/rejections/stats'),
}

export default rejectionsApi
