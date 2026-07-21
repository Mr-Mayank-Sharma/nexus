import client from './client'

export interface PickupOrder {
  id: string
  tenantId: string
  orderId: string
  orderNumber: string
  nodeId: string
  pickerId?: string
  pickerName?: string
  status: 'PENDING' | 'PICKING' | 'PICKED' | 'PACKED' | 'READY_FOR_HANDOFF' | 'HANDED_OFF' | 'POD_COLLECTED' | 'CANCELLED'
  pickupType: 'BOPIS' | 'BORIS'
  customerName?: string
  customerEmail?: string
  customerPhone?: string
  pickupCode?: string
  estimatedReadyAt?: string
  pickedAt?: string
  packedAt?: string
  readyAt?: string
  handedOffAt?: string
  collectedAt?: string
  createdAt: string
  updatedAt: string
}

export interface PickupOrderItem {
  id: string
  pickupOrderId: string
  sku: string
  productName?: string
  quantity: number
  pickedQuantity: number
  location?: string
  status: 'PENDING' | 'PICKED' | 'SUBSTITUTED' | 'SHORT'
  substitutedSku?: string
  notes?: string
  createdAt: string
}

export interface ProofOfDelivery {
  id: string
  tenantId: string
  pickupOrderId: string
  orderNumber: string
  collectedByName: string
  collectedByIdDoc?: string
  collectorSignature: string
  associateName: string
  associateSignature?: string
  collectionNotes?: string
  itemsHandedOver: number
  photoPath?: string
  collectedAt: string
  createdAt: string
}

export interface PickupStatusCounts {
  total: number
  pending: number
  picking: number
  ready: number
  collected: number
}

export const pickupApi = {
  getPendingPickups: (nodeId: string) =>
    client.get<PickupOrder[]>(`/pickup/pending/${nodeId}`),

  getPickupOrder: (id: string) =>
    client.get<PickupOrder>(`/pickup/${id}`),

  getByPickupCode: (pickupCode: string) =>
    client.get<PickupOrder>(`/pickup/code/${pickupCode}`),

  getPickupsByPicker: (pickerId: string) =>
    client.get<PickupOrder[]>(`/pickup/picker/${pickerId}`),

  getReadyForHandoff: () =>
    client.get<PickupOrder[]>('/pickup/ready'),

  getPickupItems: (pickupOrderId: string) =>
    client.get<PickupOrderItem[]>(`/pickup/${pickupOrderId}/items`),

  assignPicker: (pickupOrderId: string, pickerId: string, pickerName: string) =>
    client.post<PickupOrder>(`/pickup/${pickupOrderId}/assign?pickerId=${pickerId}&pickerName=${encodeURIComponent(pickerName)}`),

  startPicking: (pickupOrderId: string) =>
    client.post<PickupOrder>(`/pickup/${pickupOrderId}/start`),

  pickItem: (itemId: string, quantity: number, notes?: string) =>
    client.post<PickupOrderItem>(`/pickup/items/${itemId}/pick?quantity=${quantity}${notes ? `&notes=${encodeURIComponent(notes)}` : ''}`),

  substituteItem: (itemId: string, substitutedSku: string, quantity: number) =>
    client.post<PickupOrderItem>(`/pickup/items/${itemId}/substitute?substitutedSku=${substitutedSku}&quantity=${quantity}`),

  completePicking: (pickupOrderId: string) =>
    client.post<PickupOrder>(`/pickup/${pickupOrderId}/complete-picking`),

  packOrder: (pickupOrderId: string) =>
    client.post<PickupOrder>(`/pickup/${pickupOrderId}/pack`),

  markReadyForHandoff: (pickupOrderId: string) =>
    client.post<PickupOrder>(`/pickup/${pickupOrderId}/ready`),

  handoffOrder: (pickupOrderId: string) =>
    client.post<PickupOrder>(`/pickup/${pickupOrderId}/handoff`),

  collectOrder: (pickupOrderId: string, pod: Partial<ProofOfDelivery>) =>
    client.post<ProofOfDelivery>(`/pickup/${pickupOrderId}/collect`, pod),

  getPOD: (pickupOrderId: string) =>
    client.get<ProofOfDelivery>(`/pickup/${pickupOrderId}/pod`),

  getStatusCounts: (nodeId: string) =>
    client.get<PickupStatusCounts>(`/pickup/status/${nodeId}`),
}

export default pickupApi
