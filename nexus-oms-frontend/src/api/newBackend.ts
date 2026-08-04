import client from './client'
import type { ApiResponse } from '../types'

export async function fetchDashboard(): Promise<any> {
  try { const { data } = await client.get('/dashboard'); return data } catch { return null }
}

export async function fetchOrders(params?: Record<string, any>): Promise<any> {
  try { const { data } = await client.get('/orders', { params }); return data } catch { return null }
}

export async function fetchOrderById(id: string): Promise<any> {
  try { const { data } = await client.get(`/orders/${id}`); return data } catch { return null }
}

export async function createOrder(orderData: Record<string, any>): Promise<any> {
  try { const { data } = await client.post('/orders', orderData); return data } catch { return null }
}

export async function updateOrder(id: string, body: Record<string, any>): Promise<any> {
  try { const { data } = await client.put(`/orders/${id}`, body); return data } catch { return null }
}

export async function transitionOrder(id: string, action: string): Promise<any> {
  try { const { data } = await client.patch(`/orders/${id}/${action}`); return data } catch { return null }
}

export async function fetchOrderStats(): Promise<any> {
  try { const { data } = await client.get('/orders/stats'); return data } catch { return null }
}

export async function fetchProducts(search?: string): Promise<any> {
  try { const { data } = await client.get('/products', { params: { search } }); return data } catch { return null }
}

export async function fetchCustomers(search?: string): Promise<any> {
  try { const { data } = await client.get('/customers', { params: { search } }); return data } catch { return null }
}

export async function fetchInventory(sku?: string): Promise<any> {
  try { const { data } = await client.get(sku ? `/inventory/${sku}` : '/inventory'); return data } catch { return null }
}

export async function fetchEnhancedInventory(): Promise<any> {
  try { const { data } = await client.get('/inventory/enhanced'); return data } catch { return null }
}

export async function fetchReceiving(): Promise<any> {
  try { const { data } = await client.get('/inventory-receipts'); return data } catch { return null }
}

export async function fetchCycleCounts(): Promise<any> {
  try { const { data } = await client.get('/cycle-counts'); return data } catch { return null }
}

export async function adjustInventory(sku: string, warehouseId: string, qty: number, reason: string): Promise<any> {
  try { const { data } = await client.post('/inventory/adjust', { sku, warehouseId, qty, reason }); return data } catch { return null }
}

export async function fetchWarehouses(): Promise<any> {
  try { const { data } = await client.get('/warehouses'); return data } catch { return null }
}

export async function fetchWarehouseZones(): Promise<any> {
  try { const { data } = await client.get('/warehouses/zones'); return data } catch { return null }
}

export async function fetchWavePlans(): Promise<any> {
  try { const { data } = await client.get('/waves'); return data } catch { return null }
}

export async function fetchWaveStats(): Promise<any> {
  try { const { data } = await client.get('/waves/stats'); return data } catch { return null }
}

export async function createWavePlan(waveData: Record<string, any>): Promise<any> {
  try { const { data } = await client.post('/waves', waveData); return data } catch { return null }
}

export async function updateWavePlan(id: string, body: Record<string, any>): Promise<any> {
  try { const { data } = await client.put(`/waves/${id}`, body); return data } catch { return null }
}

export async function planWave(id: string): Promise<any> {
  try { const { data } = await client.post(`/waves/${id}/plan`); return data } catch { return null }
}

export async function releaseWave(id: string): Promise<any> {
  try { const { data } = await client.post(`/waves/${id}/release`); return data } catch { return null }
}

export async function completeWave(id: string): Promise<any> {
  try { const { data } = await client.post(`/waves/${id}/complete`); return data } catch { return null }
}

export async function cancelWave(id: string): Promise<any> {
  try { const { data } = await client.post(`/waves/${id}/cancel`); return data } catch { return null }
}

export async function optimizeWave(id: string): Promise<any> {
  try { const { data } = await client.post(`/waves/${id}/optimize`); return data } catch { return null }
}

export async function fetchEmployees(): Promise<any> {
  try { const { data } = await client.get('/labor'); return data } catch { return null }
}

export async function fetchShifts(date?: string): Promise<any> {
  try { const { data } = await client.get('/labor/shifts', { params: { date } }); return data } catch { return null }
}

export async function assignTask(taskData: Record<string, any>): Promise<any> {
  try { const { data } = await client.post('/labor/tasks', taskData); return data } catch { return null }
}

export async function fetchPickLists(): Promise<any> {
  try { const { data } = await client.get('/picking/lists'); return data } catch { return null }
}

export async function createPickList(listData: Record<string, any>): Promise<any> {
  try { const { data } = await client.post('/picking/lists', listData); return data } catch { return null }
}

export async function updatePickList(id: string, body: Record<string, any>): Promise<any> {
  try { const { data } = await client.patch(`/picking/lists/${id}`, body); return data } catch { return null }
}

export async function fetchPackingQueues(): Promise<any> {
  try { const { data } = await client.get('/packing/queues'); return data } catch { return null }
}

export async function completePacking(): Promise<any> {
  try { const { data } = await client.post('/packing/complete'); return data } catch { return null }
}

export async function fetchCarriers(): Promise<any> {
  try { const { data } = await client.get('/carriers'); return data } catch { return null }
}

export async function fetchCarrierRates(): Promise<any> {
  try { const { data } = await client.get('/carriers/kpis'); return data } catch { return null }
}

export async function generateLabel(labelData: Record<string, any>): Promise<any> {
  try { const { data } = await client.post('/labels/generate', labelData); return data } catch { return null }
}

export async function generateBulkLabels(count: number): Promise<any> {
  try { const { data } = await client.post('/labels/generate', { bulk: true, count }); return data } catch { return null }
}

export async function fetchLabels(): Promise<any> {
  try { const { data } = await client.get('/labels'); return data } catch { return null }
}

export async function fetchManifests(): Promise<any> {
  try { const { data } = await client.get('/manifests'); return data } catch { return null }
}

export async function createManifest(manifestData: Record<string, any>): Promise<any> {
  try { const { data } = await client.post('/manifests', manifestData); return data } catch { return null }
}

export async function updateManifest(id: string, body: Record<string, any>): Promise<any> {
  try { const { data } = await client.patch(`/manifests/${id}`, body); return data } catch { return null }
}

export async function fetchReturns(): Promise<any> {
  try { const { data } = await client.get('/returns'); return data } catch { return null }
}

export async function createReturn(returnData: Record<string, any>): Promise<any> {
  try { const { data } = await client.post('/returns', returnData); return data } catch { return null }
}

export async function updateReturn(id: string, body: Record<string, any>): Promise<any> {
  try { const { data } = await client.patch(`/returns/${id}`, body); return data } catch { return null }
}

export async function fetchReturnAnalytics(): Promise<any> {
  try { const { data } = await client.get('/analytics/returns'); return data } catch { return null }
}

export async function fetchPayments(status?: string): Promise<any> {
  try { const { data } = await client.get('/invoicing/payments', { params: { status } }); return data } catch { return null }
}

export async function fetchInvoices(status?: string): Promise<any> {
  try { const { data } = await client.get('/invoicing/invoices', { params: { status } }); return data } catch { return null }
}

export async function createInvoice(invoiceData: Record<string, any>): Promise<any> {
  try { const { data } = await client.post('/invoicing/invoices', invoiceData); return data } catch { return null }
}

export async function updateInvoice(id: string, body: Record<string, any>): Promise<any> {
  try { const { data } = await client.put(`/invoicing/invoices/${id}/status`, body); return data } catch { return null }
}

export async function fetchReconciliation(): Promise<any> {
  try { const { data } = await client.get('/reconciliation'); return data } catch { return null }
}

export async function fetchDashboardWidgets(): Promise<any> {
  try { const { data } = await client.get('/reports/dashboard'); return data } catch { return null }
}

export async function generateReport(templateId: string, format: string): Promise<any> {
  try { const { data } = await client.post('/reports/generate', { templateId, format }); return data } catch { return null }
}

export async function fetchScheduledReports(): Promise<any> {
  try { const { data } = await client.get('/reports/scheduled'); return data } catch { return null }
}

export async function createScheduledReport(reportData: Record<string, any>): Promise<any> {
  try { const { data } = await client.post('/reports/scheduled', reportData); return data } catch { return null }
}

export async function fetchReportTemplates(): Promise<any> {
  try { const { data } = await client.get('/reports'); return data } catch { return null }
}

export async function fetchTaskQueues(): Promise<any> {
  try { const { data } = await client.get('/task-queues'); return data } catch { return null }
}

export async function updateTaskQueue(id: string, body: Record<string, any>): Promise<any> {
  try { const { data } = await client.patch(`/task-queues/${id}`, body); return data } catch { return null }
}

export async function fetchNotifications(): Promise<any> {
  try { const { data } = await client.get('/notifications'); return data } catch { return null }
}

export async function fetchSettings(): Promise<any> {
  try { const { data } = await client.get('/settings'); return data } catch { return null }
}

export async function saveSettings(body: Record<string, any>): Promise<any> {
  try { const { data } = await client.put('/settings', body); return data } catch { return null }
}
