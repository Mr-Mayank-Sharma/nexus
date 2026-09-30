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

export interface Picker {
  id: string
  tenantId: string
  nodeId: string
  userId: string
  name: string
  employeeId?: string
  status: string // AVAILABLE, PICKING, ON_BREAK, OFFLINE
  currentOrderId?: string
  maxConcurrentOrders: number
  ordersCompletedToday: number
  itemsPickedToday: number
  lastActiveAt?: string
  shiftStart?: string
  shiftEnd?: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface PickerAssignment {
  id: string
  tenantId: string
  pickerId: string
  pickupOrderId: string
  orderNumber: string
  nodeId: string
  status: string // ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED
  assignedAt: string
  startedAt?: string
  completedAt?: string
  priority: number
  createdAt: string
  updatedAt: string
}

export interface PickerStats {
  totalPickers: number
  available: number
  picking: number
  onBreak: number
  offline: number
  activeAssignments: number
  completedToday: number
}

export const pickersApi = {
  getPickers: (nodeId: string) =>
    unwrap<Picker[]>(client.get<Picker[]>(`/pickers/${nodeId}`)),

  getAvailablePickers: (nodeId: string) =>
    unwrap<Picker[]>(client.get<Picker[]>(`/pickers/${nodeId}/available`)),

  getPicker: (id: string) =>
    unwrap<Picker>(client.get<Picker>(`/pickers/detail/${id}`)),

  createPicker: (picker: Partial<Picker>) =>
    unwrap<Picker>(client.post<Picker>('/pickers', picker)),

  updatePicker: (id: string, picker: Partial<Picker>) =>
    unwrap<Picker>(client.put<Picker>(`/pickers/${id}`, picker)),

  updateStatus: (id: string, status: string) =>
    unwrap<Picker>(client.put<Picker>(`/pickers/${id}/status`, null, { params: { status } })),

  assignNextAvailable: (nodeId: string, pickupOrderId: string, orderNumber: string) =>
    unwrap<PickerAssignment>(client.post<PickerAssignment>('/pickers/assign/next', null, {
      params: { nodeId, pickupOrderId, orderNumber },
    })),

  assignPicker: (pickerId: string, pickupOrderId: string, orderNumber: string, nodeId: string) =>
    unwrap<PickerAssignment>(client.post<PickerAssignment>(`/pickers/assign/${pickerId}`, null, {
      params: { pickupOrderId, orderNumber, nodeId },
    })),

  startAssignment: (id: string) =>
    unwrap<PickerAssignment>(client.post<PickerAssignment>(`/pickers/assignments/${id}/start`)),

  completeAssignment: (id: string) =>
    unwrap<PickerAssignment>(client.post<PickerAssignment>(`/pickers/assignments/${id}/complete`)),

  getActiveAssignments: (nodeId: string) =>
    unwrap<PickerAssignment[]>(client.get<PickerAssignment[]>(`/pickers/assignments/active/${nodeId}`)),

  getPickerAssignments: (pickerId: string) =>
    unwrap<PickerAssignment[]>(client.get<PickerAssignment[]>(`/pickers/assignments/picker/${pickerId}`)),

  getPickerStats: (nodeId: string) =>
    unwrap<PickerStats>(client.get<PickerStats>(`/pickers/stats/${nodeId}`)),
}

export default pickersApi