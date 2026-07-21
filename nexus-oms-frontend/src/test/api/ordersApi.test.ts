import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getOrders,
  getOrderById,
  createOrder,
  updateOrderStatus,
  confirmOrder,
  allocateOrder,
  shipOrder,
  cancelOrder,
  modifyOrder,
  splitOrder,
  mergeOrders,
} from '../../api/orders'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPut = vi.fn()
const mockPatch = vi.fn()

vi.mock('../../api/client', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
    patch: (...args: unknown[]) => mockPatch(...args),
  },
}))

describe('Orders API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: { success: true } })
    mockPost.mockResolvedValue({ data: { success: true } })
    mockPut.mockResolvedValue({ data: { success: true } })
    mockPatch.mockResolvedValue({ data: { success: true } })
  })

  describe('getOrders', () => {
    it('should GET /orders without params', async () => {
      const result = await getOrders()
      expect(mockGet).toHaveBeenCalledWith('/orders', { params: undefined })
      expect(result.success).toBe(true)
    })

    it('should GET /orders with params', async () => {
      const params = { status: 'CREATED', page: 0 }
      const result = await getOrders(params)
      expect(mockGet).toHaveBeenCalledWith('/orders', { params })
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Failed to get orders'))
      const result = await getOrders()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Failed to get orders')
    })
  })

  describe('getOrderById', () => {
    it('should GET /orders/:id', async () => {
      const result = await getOrderById('ORD-001')
      expect(mockGet).toHaveBeenCalledWith('/orders/ORD-001')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Order not found'))
      const result = await getOrderById('INVALID')
      expect(result.success).toBe(false)
      expect(result.error).toBe('Order not found')
    })
  })

  describe('createOrder', () => {
    it('should POST /orders with orderData', async () => {
      const orderData = { customerName: 'Test', items: [] }
      const result = await createOrder(orderData)
      expect(mockPost).toHaveBeenCalledWith('/orders', orderData)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Create failed'))
      const result = await createOrder({ customerName: 'Test' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Create failed')
    })
  })

  describe('updateOrderStatus', () => {
    it('should PUT /orders/:id with { status }', async () => {
      const result = await updateOrderStatus('ORD-001', 'CONFIRMED')
      expect(mockPut).toHaveBeenCalledWith('/orders/ORD-001', { status: 'CONFIRMED' })
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPut.mockRejectedValueOnce(new Error('Update failed'))
      const result = await updateOrderStatus('ORD-001', 'SHIPPED')
      expect(result.success).toBe(false)
      expect(result.error).toBe('Update failed')
    })
  })

  describe('confirmOrder', () => {
    it('should PATCH /orders/:id/confirm', async () => {
      const result = await confirmOrder('ORD-001')
      expect(mockPatch).toHaveBeenCalledWith('/orders/ORD-001/confirm')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPatch.mockRejectedValueOnce(new Error('Confirm failed'))
      const result = await confirmOrder('ORD-001')
      expect(result.success).toBe(false)
      expect(result.error).toBe('Confirm failed')
    })
  })

  describe('allocateOrder', () => {
    it('should PATCH /orders/:id/allocate', async () => {
      const result = await allocateOrder('ORD-001')
      expect(mockPatch).toHaveBeenCalledWith('/orders/ORD-001/allocate')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPatch.mockRejectedValueOnce(new Error('Allocate failed'))
      const result = await allocateOrder('ORD-001')
      expect(result.success).toBe(false)
      expect(result.error).toBe('Allocate failed')
    })
  })

  describe('shipOrder', () => {
    it('should PATCH /orders/:id/ship with carrierId and trackingNumber', async () => {
      const result = await shipOrder('ORD-001', 'FEDEX', 'TRACK-123')
      expect(mockPatch).toHaveBeenCalledWith('/orders/ORD-001/ship', {
        carrierId: 'FEDEX',
        trackingNumber: 'TRACK-123',
      })
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPatch.mockRejectedValueOnce(new Error('Ship failed'))
      const result = await shipOrder('ORD-001', 'UPS', 'TRACK-456')
      expect(result.success).toBe(false)
      expect(result.error).toBe('Ship failed')
    })
  })

  describe('cancelOrder', () => {
    it('should PATCH /orders/:id/cancel', async () => {
      const result = await cancelOrder('ORD-001')
      expect(mockPatch).toHaveBeenCalledWith('/orders/ORD-001/cancel')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPatch.mockRejectedValueOnce(new Error('Cancel failed'))
      const result = await cancelOrder('ORD-001')
      expect(result.success).toBe(false)
      expect(result.error).toBe('Cancel failed')
    })
  })

  describe('modifyOrder', () => {
    it('should PUT /orders/:id with payload', async () => {
      const payload = { shippingAddress: '123 Main St' }
      const result = await modifyOrder('ORD-001', payload)
      expect(mockPut).toHaveBeenCalledWith('/orders/ORD-001', payload)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPut.mockRejectedValueOnce(new Error('Modify failed'))
      const result = await modifyOrder('ORD-001', { shippingAddress: '456 Oak' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Modify failed')
    })
  })

  describe('splitOrder', () => {
    it('should POST /orders/:id/split with payload', async () => {
      const payload = { items: [{ productId: 'P-1', quantity: 5 }] }
      const result = await splitOrder('ORD-001', payload)
      expect(mockPost).toHaveBeenCalledWith('/orders/ORD-001/split', payload)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Split failed'))
      const result = await splitOrder('ORD-001', { items: [] })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Split failed')
    })
  })

  describe('mergeOrders', () => {
    it('should POST /orders/merge with payload', async () => {
      const payload = { orderIds: ['ORD-001', 'ORD-002'] }
      const result = await mergeOrders(payload)
      expect(mockPost).toHaveBeenCalledWith('/orders/merge', payload)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Merge failed'))
      const result = await mergeOrders({ orderIds: ['ORD-001'] })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Merge failed')
    })
  })
})
