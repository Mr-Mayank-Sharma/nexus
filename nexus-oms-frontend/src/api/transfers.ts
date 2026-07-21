import client from './client'

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
    client.post<TransferOrder>('/transfers', data),

  getTransfers: (status?: string) =>
    client.get<TransferOrder[]>('/transfers', { params: { status } }),

  getTransfer: (id: string) =>
    client.get<TransferOrder>(`/transfers/${id}`),

  getTransferItems: (id: string) =>
    client.get<TransferOrderItem[]>(`/transfers/${id}/items`),

  approveTransfer: (id: string) =>
    client.put<TransferOrder>(`/transfers/${id}/approve`),

  shipTransfer: (id: string, items?: TransferOrderItem[]) =>
    client.put<TransferOrder>(`/transfers/${id}/ship`, items),

  receiveTransfer: (id: string, items?: TransferOrderItem[]) =>
    client.put<TransferOrder>(`/transfers/${id}/receive`, items),

  cancelTransfer: (id: string) =>
    client.put<TransferOrder>(`/transfers/${id}/cancel`),

  getTransfersInTransit: () =>
    client.get<TransferOrder[]>('/transfers/in-transit'),

  getTransfersByNode: (nodeId: string) =>
    client.get<TransferOrder[]>(`/transfers/node/${nodeId}`),

  getTransferStats: () =>
    client.get<TransferStats>('/transfers/stats'),
}

export default transfersApi
