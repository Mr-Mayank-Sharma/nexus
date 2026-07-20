import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getSlottingAssignments,
  getSlottingAssignment,
  getSlottingRules,
  createSlottingRule,
  updateSlottingRule,
  toggleSlottingRule,
  analyzeSlotting,
  optimizeSlotting,
  reassignSku,
  getVelocityAnalysis,
  getSpaceUtilization,
  getSlottingAuditLog,
} from '../api/slotting'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPut = vi.fn()

vi.mock('../api/client', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
  },
}))

describe('Slotting API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: { success: true } })
    mockPost.mockResolvedValue({ data: { success: true } })
    mockPut.mockResolvedValue({ data: { success: true } })
  })

  describe('getSlottingAssignments', () => {
    it('should GET /slotting/assignments with warehouseId', async () => {
      await getSlottingAssignments('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/slotting/assignments?warehouseId=wh-1')
    })
  })

  describe('getSlottingAssignment', () => {
    it('should GET /slotting/assignments/:id', async () => {
      await getSlottingAssignment('assign-1')
      expect(mockGet).toHaveBeenCalledWith('/slotting/assignments/assign-1')
    })
  })

  describe('getSlottingRules', () => {
    it('should GET /slotting/rules with warehouseId', async () => {
      await getSlottingRules('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/slotting/rules?warehouseId=wh-1')
    })
  })

  describe('createSlottingRule', () => {
    it('should POST /slotting/rules with data', async () => {
      const data = { name: 'velocity-based', priority: 1 }
      await createSlottingRule(data)
      expect(mockPost).toHaveBeenCalledWith('/slotting/rules', data)
    })
  })

  describe('updateSlottingRule', () => {
    it('should PUT /slotting/rules/:id with data', async () => {
      const data = { priority: 2 }
      await updateSlottingRule('rule-1', data)
      expect(mockPut).toHaveBeenCalledWith('/slotting/rules/rule-1', data)
    })
  })

  describe('toggleSlottingRule', () => {
    it('should PUT /slotting/rules/:id/toggle with isActive', async () => {
      await toggleSlottingRule('rule-1', true)
      expect(mockPut).toHaveBeenCalledWith('/slotting/rules/rule-1/toggle?isActive=true')
    })

    it('should pass isActive=false', async () => {
      await toggleSlottingRule('rule-1', false)
      expect(mockPut).toHaveBeenCalledWith('/slotting/rules/rule-1/toggle?isActive=false')
    })
  })

  describe('analyzeSlotting', () => {
    it('should GET /slotting/analyze with warehouseId', async () => {
      await analyzeSlotting('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/slotting/analyze?warehouseId=wh-1')
    })
  })

  describe('optimizeSlotting', () => {
    it('should POST /slotting/optimize with warehouseId', async () => {
      await optimizeSlotting('wh-1')
      expect(mockPost).toHaveBeenCalledWith('/slotting/optimize?warehouseId=wh-1')
    })
  })

  describe('reassignSku', () => {
    it('should POST /slotting/reassign with data', async () => {
      const data = { sku: 'SKU-001', fromLocation: 'A-01', toLocation: 'B-02' }
      await reassignSku(data)
      expect(mockPost).toHaveBeenCalledWith('/slotting/reassign', data)
    })
  })

  describe('getVelocityAnalysis', () => {
    it('should GET /slotting/velocity with warehouseId', async () => {
      await getVelocityAnalysis('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/slotting/velocity?warehouseId=wh-1')
    })
  })

  describe('getSpaceUtilization', () => {
    it('should GET /slotting/space with warehouseId', async () => {
      await getSpaceUtilization('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/slotting/space?warehouseId=wh-1')
    })
  })

  describe('getSlottingAuditLog', () => {
    it('should GET /slotting/audit with warehouseId only', async () => {
      await getSlottingAuditLog('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/slotting/audit?warehouseId=wh-1')
    })

    it('should include from and to params when provided', async () => {
      await getSlottingAuditLog('wh-1', '2026-01-01', '2026-01-31')
      expect(mockGet).toHaveBeenCalledWith(
        '/slotting/audit?warehouseId=wh-1&from=2026-01-01&to=2026-01-31',
      )
    })
  })
})
