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

export interface TransferOrderItem {
  id?: string
  transferOrderId?: string
  sku: string
  productName?: string
  quantityRequested: number
  quantityShipped?: number
  quantityReceived?: number
  unitCost?: number
  status?: string
}

export interface TransferOrder {
  id: string
  tenantId: string
  transferNumber: string
  transferType: 'WAREHOUSE_TO_STORE' | 'STORE_TO_STORE' | 'STORE_TO_WAREHOUSE'
  sourceNodeId: string
  destinationNodeId: string
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'IN_TRANSIT' | 'RECEIVED' | 'CANCELLED'
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
  requestedBy?: string
  approvedBy?: string
  expectedArrival?: string
  actualArrival?: string
  notes?: string
  createdAt: string
  updatedAt: string
}

export interface CreateTransferRequest {
  transferType: string
  sourceNodeId: string
  destinationNodeId: string
  priority?: string
  expectedArrival?: string
  notes?: string
  items?: TransferOrderItem[]
}

export interface TransferStats {
  totalTransfers: number
  draft: number
  pendingApproval: number
  approved: number
  inTransit: number
  received: number
  cancelled: number
  urgentTransfers: number
}

export const transfersApi = {
  createTransfer: (data: CreateTransferRequest) =>
    unwrap<TransferOrder>(client.post<TransferOrder>('/transfers', data)),

  getTransfers: (status?: string) =>
    unwrap<TransferOrder[]>(client.get<TransferOrder[]>('/transfers', { params: { status } })),

  getTransfer: (id: string) =>
    unwrap<TransferOrder>(client.get<TransferOrder>(`/transfers/${id}`)),

  getTransferItems: (id: string) =>
    unwrap<TransferOrderItem[]>(client.get<TransferOrderItem[]>(`/transfers/${id}/items`)),

  approveTransfer: (id: string) =>
    unwrap<TransferOrder>(client.put<TransferOrder>(`/transfers/${id}/approve`)),

  shipTransfer: (id: string, items?: TransferOrderItem[]) =>
    unwrap<TransferOrder>(client.put<TransferOrder>(`/transfers/${id}/ship`, items)),

  receiveTransfer: (id: string, items?: TransferOrderItem[]) =>
    unwrap<TransferOrder>(client.put<TransferOrder>(`/transfers/${id}/receive`, items)),

  cancelTransfer: (id: string) =>
    unwrap<TransferOrder>(client.put<TransferOrder>(`/transfers/${id}/cancel`)),

  getTransfersInTransit: () =>
    unwrap<TransferOrder[]>(client.get<TransferOrder[]>('/transfers/in-transit')),

  getTransfersByNode: (nodeId: string) =>
    unwrap<TransferOrder[]>(client.get<TransferOrder[]>(`/transfers/node/${nodeId}`)),

  getTransferStats: () =>
    unwrap<TransferStats>(client.get<TransferStats>('/transfers/stats')),
}

export default transfersApi