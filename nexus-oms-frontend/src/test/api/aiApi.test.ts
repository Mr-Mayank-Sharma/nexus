import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getModelInfo, predictCarrier, predictDemand, predictInventory } from '../../api/ai'

const mockGet = vi.fn()
const mockPost = vi.fn()

vi.mock('../../api/client', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
}))

describe('AI API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: { success: true } })
    mockPost.mockResolvedValue({ data: { success: true } })
  })

  describe('getModelInfo', () => {
    it('should GET /ai/models', async () => {
      const result = await getModelInfo()
      expect(mockGet).toHaveBeenCalledWith('/ai/models')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Network error'))
      const result = await getModelInfo()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Network error')
    })

    it('should use response data message on axios error', async () => {
      mockGet.mockRejectedValueOnce({ response: { data: { message: 'Server error' } } })
      const result = await getModelInfo()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Server error')
    })
  })

  describe('predictCarrier', () => {
    it('should POST /ai/predict/carrier with payload', async () => {
      const payload = { orderId: 'ORD-001', weight: 5 }
      const result = await predictCarrier(payload)
      expect(mockPost).toHaveBeenCalledWith('/ai/predict/carrier', payload)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Prediction failed'))
      const result = await predictCarrier({ orderId: 'ORD-001' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Prediction failed')
    })
  })

  describe('predictDemand', () => {
    it('should POST /ai/predict/demand with payload', async () => {
      const payload = { sku: 'SKU-001', days: 30 }
      const result = await predictDemand(payload)
      expect(mockPost).toHaveBeenCalledWith('/ai/predict/demand', payload)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Demand prediction failed'))
      const result = await predictDemand({ sku: 'SKU-001' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Demand prediction failed')
    })
  })

  describe('predictInventory', () => {
    it('should POST /ai/predict/inventory with payload', async () => {
      const payload = { warehouseId: 'WH-001', category: 'electronics' }
      const result = await predictInventory(payload)
      expect(mockPost).toHaveBeenCalledWith('/ai/predict/inventory', payload)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Inventory prediction failed'))
      const result = await predictInventory({ warehouseId: 'WH-001' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Inventory prediction failed')
    })
  })
})
