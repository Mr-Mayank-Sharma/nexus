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
    unwrap<PickupOrder[]>(client.get<PickupOrder[]>(`/pickup/pending/${nodeId}`)),

  getPickupOrder: (id: string) =>
    unwrap<PickupOrder>(client.get<PickupOrder>(`/pickup/${id}`)),

  getByPickupCode: (pickupCode: string) =>
    unwrap<PickupOrder>(client.get<PickupOrder>(`/pickup/code/${pickupCode}`)),

  getPickupsByPicker: (pickerId: string) =>
    unwrap<PickupOrder[]>(client.get<PickupOrder[]>(`/pickup/picker/${pickerId}`)),

  getReadyForHandoff: () =>
    unwrap<PickupOrder[]>(client.get<PickupOrder[]>('/pickup/ready')),

  getPickupItems: (pickupOrderId: string) =>
    unwrap<PickupOrderItem[]>(client.get<PickupOrderItem[]>(`/pickup/${pickupOrderId}/items`)),

  assignPicker: (pickupOrderId: string, pickerId: string, pickerName: string) =>
    unwrap<PickupOrder>(client.post<PickupOrder>(`/pickup/${pickupOrderId}/assign?pickerId=${pickerId}&pickerName=${encodeURIComponent(pickerName)}`)),

  startPicking: (pickupOrderId: string) =>
    unwrap<PickupOrder>(client.post<PickupOrder>(`/pickup/${pickupOrderId}/start`)),

  pickItem: (itemId: string, quantity: number, notes?: string) =>
    unwrap<PickupOrderItem>(client.post<PickupOrderItem>(`/pickup/items/${itemId}/pick?quantity=${quantity}${notes ? `&notes=${encodeURIComponent(notes)}` : ''}`)),

  substituteItem: (itemId: string, substitutedSku: string, quantity: number) =>
    unwrap<PickupOrderItem>(client.post<PickupOrderItem>(`/pickup/items/${itemId}/substitute?substitutedSku=${substitutedSku}&quantity=${quantity}`)),

  completePicking: (pickupOrderId: string) =>
    unwrap<PickupOrder>(client.post<PickupOrder>(`/pickup/${pickupOrderId}/complete-picking`)),

  packOrder: (pickupOrderId: string) =>
    unwrap<PickupOrder>(client.post<PickupOrder>(`/pickup/${pickupOrderId}/pack`)),

  markReadyForHandoff: (pickupOrderId: string) =>
    unwrap<PickupOrder>(client.post<PickupOrder>(`/pickup/${pickupOrderId}/ready`)),

  handoffOrder: (pickupOrderId: string) =>
    unwrap<PickupOrder>(client.post<PickupOrder>(`/pickup/${pickupOrderId}/handoff`)),

  collectOrder: (pickupOrderId: string, pod: Partial<ProofOfDelivery>) =>
    unwrap<ProofOfDelivery>(client.post<ProofOfDelivery>(`/pickup/${pickupOrderId}/collect`, pod)),

  getPOD: (pickupOrderId: string) =>
    unwrap<ProofOfDelivery>(client.get<ProofOfDelivery>(`/pickup/${pickupOrderId}/pod`)),

  getStatusCounts: (nodeId: string) =>
    unwrap<PickupStatusCounts>(client.get<PickupStatusCounts>(`/pickup/status/${nodeId}`)),
}

export default pickupApi