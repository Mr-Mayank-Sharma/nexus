import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getInventory, getInventoryBySku, getAtp, adjustInventory } from '../../api/inventory'

const mockGet = vi.fn()
const mockPost = vi.fn()

vi.mock('../../api/client', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
}))

describe('Inventory API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: { success: true } })
    mockPost.mockResolvedValue({ data: { success: true } })
  })

  describe('getInventory', () => {
    it('should GET /inventory without params', async () => {
      const result = await getInventory()
      expect(mockGet).toHaveBeenCalledWith('/inventory', { params: undefined })
      expect(result.success).toBe(true)
    })

    it('should GET /inventory with params', async () => {
      const params = { warehouseId: 'WH-001', status: 'IN_STOCK' }
      const result = await getInventory(params)
      expect(mockGet).toHaveBeenCalledWith('/inventory', { params })
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Network error'))
      const result = await getInventory()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Network error')
    })
  })

  describe('getInventoryBySku', () => {
    it('should GET /inventory/:sku', async () => {
      const result = await getInventoryBySku('SKU-001')
      expect(mockGet).toHaveBeenCalledWith('/inventory/SKU-001')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('SKU not found'))
      const result = await getInventoryBySku('INVALID')
      expect(result.success).toBe(false)
      expect(result.error).toBe('SKU not found')
    })
  })

  describe('getAtp', () => {
    it('should GET /inventory with sku param', async () => {
      const result = await getAtp('SKU-001')
      expect(mockGet).toHaveBeenCalledWith('/inventory', { params: { sku: 'SKU-001' } })
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('ATP lookup failed'))
      const result = await getAtp('SKU-001')
      expect(result.success).toBe(false)
      expect(result.error).toBe('ATP lookup failed')
    })
  })

  describe('adjustInventory', () => {
    it('should POST /inventory/adjust with sku, quantity, reason', async () => {
      const result = await adjustInventory('SKU-001', 10, 'Restock')
      expect(mockPost).toHaveBeenCalledWith('/inventory/adjust', {
        sku: 'SKU-001',
        quantity: 10,
        reason: 'Restock',
      })
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Adjustment failed'))
      const result = await adjustInventory('SKU-001', -5, 'Damage')
      expect(result.success).toBe(false)
      expect(result.error).toBe('Adjustment failed')
    })
  })
})
