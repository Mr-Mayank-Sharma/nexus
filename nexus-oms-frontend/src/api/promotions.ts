import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1/promotions',
  headers: { 'Content-Type': 'application/json' },
})

// Add auth token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('nexus_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export interface NxPromotion {
  id?: string
  tenantId?: string
  name: string
  description?: string
  promotionType: 'PERCENTAGE' | 'FIXED_AMOUNT' | 'BOGO' | 'FREE_SHIPPING' | 'BUY_X_GET_Y'
  discountValue: number
  minOrderAmount?: number
  minQuantity?: number
  maxUsesTotal?: number
  maxUsesPerCustomer?: number
  currentUses?: number
  couponCode?: string
  startDate: string
  endDate: string
  applicableChannels?: string
  applicableProductIds?: string
  applicableCategoryIds?: string
  stackable: boolean
  priority: number
  active: boolean
}

export interface PromotionUsage {
  id: string
  promotionId: string
  orderId?: string
  customerId?: string
  couponCode?: string
  discountAmount: number
  orderTotal?: number
  usedAt: string
}

export interface PromotionStats {
  promotionId: string
  name: string
  totalUses: number
  totalDiscountGiven: number
  maxUsesTotal?: number
  remainingUses?: number
}

const promotionsApi = {
  // CRUD
  getPromotions: () => api.get('/'),
  getPromotion: (id: string) => api.get(`/${id}`),
  createPromotion: (data: NxPromotion) => api.post('/', data),
  updatePromotion: (id: string, data: Partial<NxPromotion>) => api.put(`/${id}`, data),
  deletePromotion: (id: string) => api.delete(`/${id}`),

  // Application
  validateCoupon: (couponCode: string, orderTotal: number, customerId?: string) =>
    api.post('/validate', null, { params: { couponCode, orderTotal, customerId } }),
  calculateDiscount: (couponCode: string, orderTotal: number, quantity: number = 1, customerId?: string) =>
    api.post('/calculate', null, { params: { couponCode, orderTotal, quantity, customerId } }),
  recordUsage: (id: string, discountAmount: number, orderTotal: number, orderId?: string, customerId?: string, couponCode?: string) =>
    api.post(`/${id}/record`, null, { params: { discountAmount, orderTotal, orderId, customerId, couponCode } }),

  // Analytics
  getPromotionStats: (id: string) => api.get(`/${id}/stats`),
  getActivePromotions: (orderTotal: number, quantity: number = 1, channel?: string) =>
    api.get('/active-for-order', { params: { orderTotal, quantity, channel } }),
}

export default promotionsApi
