import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  fetchDashboard,
  fetchOrders,
  fetchOrderById,
  createOrder,
  updateOrder,
  transitionOrder,
  fetchOrderStats,
  fetchProducts,
  fetchCustomers,
  fetchInventory,
  fetchEnhancedInventory,
  fetchReceiving,
  fetchCycleCounts,
  adjustInventory,
  fetchWarehouses,
  fetchWarehouseZones,
  fetchWavePlans,
  createWavePlan,
  updateWavePlan,
  fetchEmployees,
  fetchShifts,
  assignTask,
  fetchPickLists,
  createPickList,
  updatePickList,
  fetchPackingQueues,
  completePacking,
  fetchCarriers,
  fetchCarrierRates,
  generateLabel,
  generateBulkLabels,
  fetchLabels,
  fetchManifests,
  createManifest,
  updateManifest,
  fetchReturns,
  createReturn,
  updateReturn,
  fetchReturnAnalytics,
  fetchPayments,
  fetchInvoices,
  createInvoice,
  updateInvoice,
  fetchReconciliation,
  fetchDashboardWidgets,
  generateReport,
  fetchScheduledReports,
  createScheduledReport,
  fetchReportTemplates,
  fetchTaskQueues,
  updateTaskQueue,
  fetchNotifications,
  fetchSettings,
  saveSettings,
} from '../../api/newBackend'

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

describe('New Backend API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: { success: true } })
    mockPost.mockResolvedValue({ data: { success: true } })
    mockPut.mockResolvedValue({ data: { success: true } })
    mockPatch.mockResolvedValue({ data: { success: true } })
  })

  // ─── Dashboard ───────────────────────────────────────────────
  describe('fetchDashboard', () => {
    it('should GET /dashboard', async () => {
      const result = await fetchDashboard()
      expect(mockGet).toHaveBeenCalledWith('/dashboard')
      expect(result).toEqual({ success: true })
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchDashboard()).toBeNull()
    })
  })

  // ─── Orders ──────────────────────────────────────────────────
  describe('fetchOrders', () => {
    it('should GET /orders without params', async () => {
      await fetchOrders()
      expect(mockGet).toHaveBeenCalledWith('/orders', { params: undefined })
    })

    it('should GET /orders with params', async () => {
      await fetchOrders({ status: 'CREATED' })
      expect(mockGet).toHaveBeenCalledWith('/orders', { params: { status: 'CREATED' } })
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchOrders()).toBeNull()
    })
  })

  describe('fetchOrderById', () => {
    it('should GET /orders/:id', async () => {
      await fetchOrderById('ORD-001')
      expect(mockGet).toHaveBeenCalledWith('/orders/ORD-001')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchOrderById('X')).toBeNull()
    })
  })

  describe('createOrder', () => {
    it('should POST /orders', async () => {
      const data = { customerName: 'Test' }
      await createOrder(data)
      expect(mockPost).toHaveBeenCalledWith('/orders', data)
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await createOrder({})).toBeNull()
    })
  })

  describe('updateOrder', () => {
    it('should PUT /orders/:id', async () => {
      const body = { status: 'CONFIRMED' }
      await updateOrder('ORD-001', body)
      expect(mockPut).toHaveBeenCalledWith('/orders/ORD-001', body)
    })

    it('should return null on error', async () => {
      mockPut.mockRejectedValueOnce(new Error('fail'))
      expect(await updateOrder('X', {})).toBeNull()
    })
  })

  describe('transitionOrder', () => {
    it('should PATCH /orders/:id/:action', async () => {
      await transitionOrder('ORD-001', 'confirm')
      expect(mockPatch).toHaveBeenCalledWith('/orders/ORD-001/confirm')
    })

    it('should return null on error', async () => {
      mockPatch.mockRejectedValueOnce(new Error('fail'))
      expect(await transitionOrder('X', 'cancel')).toBeNull()
    })
  })

  describe('fetchOrderStats', () => {
    it('should GET /orders/stats', async () => {
      await fetchOrderStats()
      expect(mockGet).toHaveBeenCalledWith('/orders/stats')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchOrderStats()).toBeNull()
    })
  })

  // ─── Products & Customers ────────────────────────────────────
  describe('fetchProducts', () => {
    it('should GET /products without search', async () => {
      await fetchProducts()
      expect(mockGet).toHaveBeenCalledWith('/products', { params: { search: undefined } })
    })

    it('should GET /products with search', async () => {
      await fetchProducts('widget')
      expect(mockGet).toHaveBeenCalledWith('/products', { params: { search: 'widget' } })
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchProducts()).toBeNull()
    })
  })

  describe('fetchCustomers', () => {
    it('should GET /customers without search', async () => {
      await fetchCustomers()
      expect(mockGet).toHaveBeenCalledWith('/customers', { params: { search: undefined } })
    })

    it('should GET /customers with search', async () => {
      await fetchCustomers('acme')
      expect(mockGet).toHaveBeenCalledWith('/customers', { params: { search: 'acme' } })
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchCustomers()).toBeNull()
    })
  })

  // ─── Inventory ───────────────────────────────────────────────
  describe('fetchInventory', () => {
    it('should GET /inventory when no sku', async () => {
      await fetchInventory()
      expect(mockGet).toHaveBeenCalledWith('/inventory')
    })

    it('should GET /inventory/:sku when sku provided', async () => {
      await fetchInventory('SKU-001')
      expect(mockGet).toHaveBeenCalledWith('/inventory/SKU-001')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchInventory()).toBeNull()
    })
  })

  describe('fetchEnhancedInventory', () => {
    it('should GET /inventory/enhanced', async () => {
      await fetchEnhancedInventory()
      expect(mockGet).toHaveBeenCalledWith('/inventory/enhanced')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchEnhancedInventory()).toBeNull()
    })
  })

  describe('fetchReceiving', () => {
    it('should GET /inventory/receiving', async () => {
      await fetchReceiving()
      expect(mockGet).toHaveBeenCalledWith('/inventory/receiving')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchReceiving()).toBeNull()
    })
  })

  describe('fetchCycleCounts', () => {
    it('should GET /inventory/cycle-counts', async () => {
      await fetchCycleCounts()
      expect(mockGet).toHaveBeenCalledWith('/inventory/cycle-counts')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchCycleCounts()).toBeNull()
    })
  })

  describe('adjustInventory', () => {
    it('should POST /inventory/adjust with all params', async () => {
      await adjustInventory('SKU-001', 'WH-001', 10, 'Restock')
      expect(mockPost).toHaveBeenCalledWith('/inventory/adjust', {
        sku: 'SKU-001',
        warehouseId: 'WH-001',
        qty: 10,
        reason: 'Restock',
      })
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await adjustInventory('S', 'W', 0, 'r')).toBeNull()
    })
  })

  // ─── Warehouse ───────────────────────────────────────────────
  describe('fetchWarehouses', () => {
    it('should GET /warehouse', async () => {
      await fetchWarehouses()
      expect(mockGet).toHaveBeenCalledWith('/warehouse')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchWarehouses()).toBeNull()
    })
  })

  describe('fetchWarehouseZones', () => {
    it('should GET /warehouse/zones', async () => {
      await fetchWarehouseZones()
      expect(mockGet).toHaveBeenCalledWith('/warehouse/zones')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchWarehouseZones()).toBeNull()
    })
  })

  // ─── Wave Planning ──────────────────────────────────────────
  describe('fetchWavePlans', () => {
    it('should GET /wave-plans', async () => {
      await fetchWavePlans()
      expect(mockGet).toHaveBeenCalledWith('/wave-plans')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchWavePlans()).toBeNull()
    })
  })

  describe('createWavePlan', () => {
    it('should POST /wave-plans', async () => {
      const data = { name: 'Wave-1', items: [] }
      await createWavePlan(data)
      expect(mockPost).toHaveBeenCalledWith('/wave-plans', data)
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await createWavePlan({})).toBeNull()
    })
  })

  describe('updateWavePlan', () => {
    it('should PATCH /wave-plans/:id', async () => {
      const body = { status: 'ACTIVE' }
      await updateWavePlan('WP-001', body)
      expect(mockPatch).toHaveBeenCalledWith('/wave-plans/WP-001', body)
    })

    it('should return null on error', async () => {
      mockPatch.mockRejectedValueOnce(new Error('fail'))
      expect(await updateWavePlan('X', {})).toBeNull()
    })
  })

  // ─── Labor Management ───────────────────────────────────────
  describe('fetchEmployees', () => {
    it('should GET /labor', async () => {
      await fetchEmployees()
      expect(mockGet).toHaveBeenCalledWith('/labor')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchEmployees()).toBeNull()
    })
  })

  describe('fetchShifts', () => {
    it('should GET /labor/shifts without date', async () => {
      await fetchShifts()
      expect(mockGet).toHaveBeenCalledWith('/labor/shifts', { params: { date: undefined } })
    })

    it('should GET /labor/shifts with date', async () => {
      await fetchShifts('2026-07-21')
      expect(mockGet).toHaveBeenCalledWith('/labor/shifts', { params: { date: '2026-07-21' } })
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchShifts()).toBeNull()
    })
  })

  describe('assignTask', () => {
    it('should POST /labor/tasks', async () => {
      const data = { employeeId: 'E-1', taskType: 'PICK' }
      await assignTask(data)
      expect(mockPost).toHaveBeenCalledWith('/labor/tasks', data)
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await assignTask({})).toBeNull()
    })
  })

  // ─── Picking ────────────────────────────────────────────────
  describe('fetchPickLists', () => {
    it('should GET /picking/lists', async () => {
      await fetchPickLists()
      expect(mockGet).toHaveBeenCalledWith('/picking/lists')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchPickLists()).toBeNull()
    })
  })

  describe('createPickList', () => {
    it('should POST /picking/lists', async () => {
      const data = { orderId: 'ORD-1', items: [] }
      await createPickList(data)
      expect(mockPost).toHaveBeenCalledWith('/picking/lists', data)
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await createPickList({})).toBeNull()
    })
  })

  describe('updatePickList', () => {
    it('should PATCH /picking/lists/:id', async () => {
      const body = { status: 'COMPLETED' }
      await updatePickList('PL-001', body)
      expect(mockPatch).toHaveBeenCalledWith('/picking/lists/PL-001', body)
    })

    it('should return null on error', async () => {
      mockPatch.mockRejectedValueOnce(new Error('fail'))
      expect(await updatePickList('X', {})).toBeNull()
    })
  })

  // ─── Packing ────────────────────────────────────────────────
  describe('fetchPackingQueues', () => {
    it('should GET /packing/queues', async () => {
      await fetchPackingQueues()
      expect(mockGet).toHaveBeenCalledWith('/packing/queues')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchPackingQueues()).toBeNull()
    })
  })

  describe('completePacking', () => {
    it('should POST /packing/complete', async () => {
      await completePacking()
      expect(mockPost).toHaveBeenCalledWith('/packing/complete')
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await completePacking()).toBeNull()
    })
  })

  // ─── Carriers ───────────────────────────────────────────────
  describe('fetchCarriers', () => {
    it('should GET /carriers', async () => {
      await fetchCarriers()
      expect(mockGet).toHaveBeenCalledWith('/carriers')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchCarriers()).toBeNull()
    })
  })

  describe('fetchCarrierRates', () => {
    it('should GET /carriers/rates', async () => {
      await fetchCarrierRates()
      expect(mockGet).toHaveBeenCalledWith('/carriers/rates')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchCarrierRates()).toBeNull()
    })
  })

  // ─── Labels & Manifests ────────────────────────────────────
  describe('generateLabel', () => {
    it('should POST /labels/generate', async () => {
      const data = { orderId: 'ORD-1', carrier: 'UPS' }
      await generateLabel(data)
      expect(mockPost).toHaveBeenCalledWith('/labels/generate', data)
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await generateLabel({})).toBeNull()
    })
  })

  describe('generateBulkLabels', () => {
    it('should POST /labels/generate with bulk flag', async () => {
      await generateBulkLabels(5)
      expect(mockPost).toHaveBeenCalledWith('/labels/generate', { bulk: true, count: 5 })
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await generateBulkLabels(1)).toBeNull()
    })
  })

  describe('fetchLabels', () => {
    it('should GET /labels', async () => {
      await fetchLabels()
      expect(mockGet).toHaveBeenCalledWith('/labels')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchLabels()).toBeNull()
    })
  })

  describe('fetchManifests', () => {
    it('should GET /manifests', async () => {
      await fetchManifests()
      expect(mockGet).toHaveBeenCalledWith('/manifests')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchManifests()).toBeNull()
    })
  })

  describe('createManifest', () => {
    it('should POST /manifests', async () => {
      const data = { carrier: 'FEDEX', shipments: [] }
      await createManifest(data)
      expect(mockPost).toHaveBeenCalledWith('/manifests', data)
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await createManifest({})).toBeNull()
    })
  })

  describe('updateManifest', () => {
    it('should PATCH /manifests/:id', async () => {
      const body = { status: 'SENT' }
      await updateManifest('MAN-001', body)
      expect(mockPatch).toHaveBeenCalledWith('/manifests/MAN-001', body)
    })

    it('should return null on error', async () => {
      mockPatch.mockRejectedValueOnce(new Error('fail'))
      expect(await updateManifest('X', {})).toBeNull()
    })
  })

  // ─── Returns ────────────────────────────────────────────────
  describe('fetchReturns', () => {
    it('should GET /returns', async () => {
      await fetchReturns()
      expect(mockGet).toHaveBeenCalledWith('/returns')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchReturns()).toBeNull()
    })
  })

  describe('createReturn', () => {
    it('should POST /returns', async () => {
      const data = { orderId: 'ORD-1', reason: 'Damaged' }
      await createReturn(data)
      expect(mockPost).toHaveBeenCalledWith('/returns', data)
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await createReturn({})).toBeNull()
    })
  })

  describe('updateReturn', () => {
    it('should PATCH /returns/:id', async () => {
      const body = { status: 'REFUNDED' }
      await updateReturn('RET-001', body)
      expect(mockPatch).toHaveBeenCalledWith('/returns/RET-001', body)
    })

    it('should return null on error', async () => {
      mockPatch.mockRejectedValueOnce(new Error('fail'))
      expect(await updateReturn('X', {})).toBeNull()
    })
  })

  describe('fetchReturnAnalytics', () => {
    it('should GET /returns/analytics', async () => {
      await fetchReturnAnalytics()
      expect(mockGet).toHaveBeenCalledWith('/returns/analytics')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchReturnAnalytics()).toBeNull()
    })
  })

  // ─── Payments & Invoices ────────────────────────────────────
  describe('fetchPayments', () => {
    it('should GET /payments without status', async () => {
      await fetchPayments()
      expect(mockGet).toHaveBeenCalledWith('/payments', { params: { status: undefined } })
    })

    it('should GET /payments with status', async () => {
      await fetchPayments('PENDING')
      expect(mockGet).toHaveBeenCalledWith('/payments', { params: { status: 'PENDING' } })
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchPayments()).toBeNull()
    })
  })

  describe('fetchInvoices', () => {
    it('should GET /invoices without status', async () => {
      await fetchInvoices()
      expect(mockGet).toHaveBeenCalledWith('/invoices', { params: { status: undefined } })
    })

    it('should GET /invoices with status', async () => {
      await fetchInvoices('PAID')
      expect(mockGet).toHaveBeenCalledWith('/invoices', { params: { status: 'PAID' } })
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchInvoices()).toBeNull()
    })
  })

  describe('createInvoice', () => {
    it('should POST /invoices', async () => {
      const data = { orderId: 'ORD-1', amount: 99.99 }
      await createInvoice(data)
      expect(mockPost).toHaveBeenCalledWith('/invoices', data)
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await createInvoice({})).toBeNull()
    })
  })

  describe('updateInvoice', () => {
    it('should PATCH /invoices/:id', async () => {
      const body = { status: 'PAID' }
      await updateInvoice('INV-001', body)
      expect(mockPatch).toHaveBeenCalledWith('/invoices/INV-001', body)
    })

    it('should return null on error', async () => {
      mockPatch.mockRejectedValueOnce(new Error('fail'))
      expect(await updateInvoice('X', {})).toBeNull()
    })
  })

  describe('fetchReconciliation', () => {
    it('should GET /reconciliation', async () => {
      await fetchReconciliation()
      expect(mockGet).toHaveBeenCalledWith('/reconciliation')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchReconciliation()).toBeNull()
    })
  })

  // ─── Reports ────────────────────────────────────────────────
  describe('fetchDashboardWidgets', () => {
    it('should GET /reports/dashboard', async () => {
      await fetchDashboardWidgets()
      expect(mockGet).toHaveBeenCalledWith('/reports/dashboard')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchDashboardWidgets()).toBeNull()
    })
  })

  describe('generateReport', () => {
    it('should POST /reports/generate with templateId and format', async () => {
      await generateReport('TPL-001', 'pdf')
      expect(mockPost).toHaveBeenCalledWith('/reports/generate', { templateId: 'TPL-001', format: 'pdf' })
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await generateReport('X', 'csv')).toBeNull()
    })
  })

  describe('fetchScheduledReports', () => {
    it('should GET /reports/scheduled', async () => {
      await fetchScheduledReports()
      expect(mockGet).toHaveBeenCalledWith('/reports/scheduled')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchScheduledReports()).toBeNull()
    })
  })

  describe('createScheduledReport', () => {
    it('should POST /reports/scheduled', async () => {
      const data = { name: 'Daily Sales', cron: '0 8 * * *' }
      await createScheduledReport(data)
      expect(mockPost).toHaveBeenCalledWith('/reports/scheduled', data)
    })

    it('should return null on error', async () => {
      mockPost.mockRejectedValueOnce(new Error('fail'))
      expect(await createScheduledReport({})).toBeNull()
    })
  })

  describe('fetchReportTemplates', () => {
    it('should GET /reports', async () => {
      await fetchReportTemplates()
      expect(mockGet).toHaveBeenCalledWith('/reports')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchReportTemplates()).toBeNull()
    })
  })

  // ─── Task Queues ────────────────────────────────────────────
  describe('fetchTaskQueues', () => {
    it('should GET /task-queues', async () => {
      await fetchTaskQueues()
      expect(mockGet).toHaveBeenCalledWith('/task-queues')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchTaskQueues()).toBeNull()
    })
  })

  describe('updateTaskQueue', () => {
    it('should PATCH /task-queues/:id', async () => {
      const body = { priority: 'HIGH' }
      await updateTaskQueue('TQ-001', body)
      expect(mockPatch).toHaveBeenCalledWith('/task-queues/TQ-001', body)
    })

    it('should return null on error', async () => {
      mockPatch.mockRejectedValueOnce(new Error('fail'))
      expect(await updateTaskQueue('X', {})).toBeNull()
    })
  })

  // ─── Notifications ──────────────────────────────────────────
  describe('fetchNotifications', () => {
    it('should GET /notifications', async () => {
      await fetchNotifications()
      expect(mockGet).toHaveBeenCalledWith('/notifications')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchNotifications()).toBeNull()
    })
  })

  // ─── Settings ───────────────────────────────────────────────
  describe('fetchSettings', () => {
    it('should GET /settings', async () => {
      await fetchSettings()
      expect(mockGet).toHaveBeenCalledWith('/settings')
    })

    it('should return null on error', async () => {
      mockGet.mockRejectedValueOnce(new Error('fail'))
      expect(await fetchSettings()).toBeNull()
    })
  })

  describe('saveSettings', () => {
    it('should PUT /settings', async () => {
      const body = { theme: 'dark', language: 'en' }
      await saveSettings(body)
      expect(mockPut).toHaveBeenCalledWith('/settings', body)
    })

    it('should return null on error', async () => {
      mockPut.mockRejectedValueOnce(new Error('fail'))
      expect(await saveSettings({})).toBeNull()
    })
  })
})
