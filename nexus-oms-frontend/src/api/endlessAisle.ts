import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1/endless-aisle',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('nexus_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export interface NxEndlessAisleOrder {
  id?: string
  tenantId?: string
  storeId: string
  customerId?: string
  customerName?: string
  customerEmail?: string
  customerPhone?: string
  productSku: string
  productName: string
  quantity: number
  unitPrice: number
  totalAmount?: number
  fulfillmentType: 'SHIP_TO_CUSTOMER' | 'SHIP_TO_STORE'
  shipToAddress?: string
  status?: string
  linkedOrderId?: string
  notes?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface EndlessAisleStats {
  totalOrders: number
  pendingOrders: number
  processingOrders: number
  shippedOrders: number
  deliveredOrders: number
  cancelledOrders: number
  totalRevenue: number
}

const endlessAisleApi = {
  // CRUD
  getOrders: () => api.get('/'),
  getOrder: (id: string) => api.get(`/${id}`),
  createOrder: (data: NxEndlessAisleOrder) => api.post('/', data),
  updateOrder: (id: string, data: Partial<NxEndlessAisleOrder>) => api.put(`/${id}`, data),
  deleteOrder: (id: string) => api.delete(`/${id}`),

  // Status
  updateStatus: (id: string, status: string, notes?: string) =>
    api.put(`/${id}/status`, null, { params: { status, notes } }),

  // Queries
  getOrdersByStatus: (status: string) => api.get(`/status/${status}`),
  getOrdersByStore: (storeId: string) => api.get(`/store/${storeId}`),
  getOrdersByCustomer: (customerId: string) => api.get(`/customer/${customerId}`),

  // Analytics
  getStats: () => api.get('/stats'),
}

export default endlessAisleApi
