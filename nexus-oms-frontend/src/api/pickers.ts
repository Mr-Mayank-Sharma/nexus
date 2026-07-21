import client from './client'

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
    client.get<Picker[]>(`/pickers/${nodeId}`),

  getAvailablePickers: (nodeId: string) =>
    client.get<Picker[]>(`/pickers/${nodeId}/available`),

  getPicker: (id: string) =>
    client.get<Picker>(`/pickers/detail/${id}`),

  createPicker: (picker: Partial<Picker>) =>
    client.post<Picker>('/pickers', picker),

  updatePicker: (id: string, picker: Partial<Picker>) =>
    client.put<Picker>(`/pickers/${id}`, picker),

  updateStatus: (id: string, status: string) =>
    client.put<Picker>(`/pickers/${id}/status`, null, { params: { status } }),

  assignNextAvailable: (nodeId: string, pickupOrderId: string, orderNumber: string) =>
    client.post<PickerAssignment>('/pickers/assign/next', null, {
      params: { nodeId, pickupOrderId, orderNumber },
    }),

  assignPicker: (pickerId: string, pickupOrderId: string, orderNumber: string, nodeId: string) =>
    client.post<PickerAssignment>(`/pickers/assign/${pickerId}`, null, {
      params: { pickupOrderId, orderNumber, nodeId },
    }),

  startAssignment: (id: string) =>
    client.post<PickerAssignment>(`/pickers/assignments/${id}/start`),

  completeAssignment: (id: string) =>
    client.post<PickerAssignment>(`/pickers/assignments/${id}/complete`),

  getActiveAssignments: (nodeId: string) =>
    client.get<PickerAssignment[]>(`/pickers/assignments/active/${nodeId}`),

  getPickerAssignments: (pickerId: string) =>
    client.get<PickerAssignment[]>(`/pickers/assignments/picker/${pickerId}`),

  getPickerStats: (nodeId: string) =>
    client.get<PickerStats>(`/pickers/stats/${nodeId}`),
}

export default pickersApi
