import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getDashboardKpis,
  getOrderVelocity,
  getCarrierPerformance,
  getCostBreakdown,
  getLanePerformance,
  getReturnsAnalytics,
  getActivity,
  getAlerts,
  getOrderStatusDistribution,
  getTaskQueueSummary,
  getWarehousesSummary,
} from '../../api/analytics'

const mockGet = vi.fn()

vi.mock('../../api/client', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
  },
}))

describe('Analytics API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: { success: true } })
  })

  describe('getDashboardKpis', () => {
    it('should GET /analytics/dashboard', async () => {
      const result = await getDashboardKpis()
      expect(mockGet).toHaveBeenCalledWith('/analytics/dashboard')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Dashboard unavailable'))
      const result = await getDashboardKpis()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Dashboard unavailable')
    })
  })

  describe('getOrderVelocity', () => {
    it('should GET /analytics/orders/velocity with default hours=24', async () => {
      const result = await getOrderVelocity()
      expect(mockGet).toHaveBeenCalledWith('/analytics/orders/velocity', { params: { hours: 24 } })
      expect(result.success).toBe(true)
    })

    it('should GET /analytics/orders/velocity with custom hours', async () => {
      const result = await getOrderVelocity(48)
      expect(mockGet).toHaveBeenCalledWith('/analytics/orders/velocity', { params: { hours: 48 } })
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Stats unavailable'))
      const result = await getOrderVelocity()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Stats unavailable')
    })
  })

  describe('getCarrierPerformance', () => {
    it('should GET /analytics/carrier-performance', async () => {
      const result = await getCarrierPerformance()
      expect(mockGet).toHaveBeenCalledWith('/analytics/carrier-performance')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Carrier KPI failed'))
      const result = await getCarrierPerformance()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Carrier KPI failed')
    })
  })

  describe('getCostBreakdown', () => {
    it('should GET /analytics/cost-breakdown', async () => {
      const result = await getCostBreakdown()
      expect(mockGet).toHaveBeenCalledWith('/analytics/cost-breakdown')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Cost data unavailable'))
      const result = await getCostBreakdown()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Cost data unavailable')
    })
  })

  describe('getLanePerformance', () => {
    it('should GET /analytics/lanes', async () => {
      const result = await getLanePerformance()
      expect(mockGet).toHaveBeenCalledWith('/analytics/lanes')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Lane data unavailable'))
      const result = await getLanePerformance()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Lane data unavailable')
    })
  })

  describe('getReturnsAnalytics', () => {
    it('should GET /analytics/returns', async () => {
      const result = await getReturnsAnalytics()
      expect(mockGet).toHaveBeenCalledWith('/analytics/returns')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Returns analytics failed'))
      const result = await getReturnsAnalytics()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Returns analytics failed')
    })
  })

  describe('getActivity', () => {
    it('should GET /analytics/activity', async () => {
      const result = await getActivity()
      expect(mockGet).toHaveBeenCalledWith('/analytics/activity')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Activity feed unavailable'))
      const result = await getActivity()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Activity feed unavailable')
    })
  })

  describe('getAlerts', () => {
    it('should GET /analytics/alerts', async () => {
      const result = await getAlerts()
      expect(mockGet).toHaveBeenCalledWith('/analytics/alerts')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Alerts unavailable'))
      const result = await getAlerts()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Alerts unavailable')
    })
  })

  describe('getOrderStatusDistribution', () => {
    it('should GET /analytics/order-status-distribution', async () => {
      const result = await getOrderStatusDistribution()
      expect(mockGet).toHaveBeenCalledWith('/analytics/order-status-distribution')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Distribution data failed'))
      const result = await getOrderStatusDistribution()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Distribution data failed')
    })
  })

  describe('getTaskQueueSummary', () => {
    it('should GET /analytics/task-queue-summary', async () => {
      const result = await getTaskQueueSummary()
      expect(mockGet).toHaveBeenCalledWith('/analytics/task-queue-summary')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Task queue unavailable'))
      const result = await getTaskQueueSummary()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Task queue unavailable')
    })
  })

  describe('getWarehousesSummary', () => {
    it('should GET /warehouses/summary', async () => {
      const result = await getWarehousesSummary()
      expect(mockGet).toHaveBeenCalledWith('/warehouses/summary')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Warehouse summary failed'))
      const result = await getWarehousesSummary()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Warehouse summary failed')
    })
  })
})
