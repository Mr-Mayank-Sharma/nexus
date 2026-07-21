import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ShoppingCart, Plus, Package, Truck, CheckCircle, XCircle, Clock, Search, X,
  MapPin, User, Mail, Phone,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import endlessAisleApi from '../api/endlessAisle'
import type { NxEndlessAisleOrder, EndlessAisleStats } from '../api/endlessAisle'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'

type EATab = 'orders' | 'stats'
type StatusFilter = 'ALL' | 'PENDING' | 'CONFIRMED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'

const STATUS_CONFIG: Record<string, { label: string; color: string; icon: any }> = {
  PENDING: { label: 'Pending', color: 'warning', icon: Clock },
  CONFIRMED: { label: 'Confirmed', color: 'info', icon: CheckCircle },
  PROCESSING: { label: 'Processing', color: 'info', icon: Package },
  SHIPPED: { label: 'Shipped', color: 'active', icon: Truck },
  DELIVERED: { label: 'Delivered', color: 'success', icon: CheckCircle },
  CANCELLED: { label: 'Cancelled', color: 'error', icon: XCircle },
}

const FULFILLMENT_TYPES = [
  { value: 'SHIP_TO_CUSTOMER', label: 'Ship to Customer' },
  { value: 'SHIP_TO_STORE', label: 'Ship to Store' },
]

export default function EndlessAislePage() {
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<EATab>('orders')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [searchTerm, setSearchTerm] = useState('')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [selectedOrder, setSelectedOrder] = useState<NxEndlessAisleOrder | null>(null)

  const [form, setForm] = useState<Partial<NxEndlessAisleOrder>>({
    storeId: '',
    customerName: '',
    customerEmail: '',
    customerPhone: '',
    productSku: '',
    productName: '',
    quantity: 1,
    unitPrice: 0,
    fulfillmentType: 'SHIP_TO_CUSTOMER',
    shipToAddress: '',
    notes: '',
  })

  // ─── Queries ────────────────────────────────────────────────────────

  const { data: orders = [], isLoading, refetch } = useQuery({
    queryKey: ['endless-aisle-orders'],
    queryFn: async () => {
      const res = await endlessAisleApi.getOrders()
      return res.data as NxEndlessAisleOrder[]
    },
  })

  const { data: stats, isLoading: loadingStats } = useQuery({
    queryKey: ['endless-aisle-stats'],
    queryFn: async () => {
      const res = await endlessAisleApi.getStats()
      return res.data as EndlessAisleStats
    },
    enabled: activeTab === 'stats',
  })

  // ─── Mutations ──────────────────────────────────────────────────────

  const createMutation = useMutation({
    mutationFn: (data: NxEndlessAisleOrder) => endlessAisleApi.createOrder(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['endless-aisle-orders'] })
      addToast({ type: 'success', title: 'Order created' })
      setShowCreateModal(false)
      resetForm()
    },
    onError: (err: any) => addToast({ type: 'error', title: 'Failed to create order', message: err.message }),
  })

  const updateStatusMutation = useMutation({
    mutationFn: ({ id, status, notes }: { id: string; status: string; notes?: string }) =>
      endlessAisleApi.updateStatus(id, status, notes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['endless-aisle-orders'] })
      addToast({ type: 'success', title: 'Status updated' })
    },
    onError: (err: any) => addToast({ type: 'error', title: 'Failed to update status', message: err.message }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => endlessAisleApi.deleteOrder(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['endless-aisle-orders'] })
      addToast({ type: 'success', title: 'Order deleted' })
    },
    onError: (err: any) => addToast({ type: 'error', title: 'Failed to delete order', message: err.message }),
  })

  // ─── Helpers ────────────────────────────────────────────────────────

  const resetForm = () => {
    setForm({
      storeId: '',
      customerName: '',
      customerEmail: '',
      customerPhone: '',
      productSku: '',
      productName: '',
      quantity: 1,
      unitPrice: 0,
      fulfillmentType: 'SHIP_TO_CUSTOMER',
      shipToAddress: '',
      notes: '',
    })
  }

  const handleSubmit = () => {
    if (!form.storeId || !form.productSku || !form.productName || !form.quantity || !form.unitPrice) {
      addToast({ type: 'error', title: 'Please fill required fields' })
      return
    }
    createMutation.mutate(form as NxEndlessAisleOrder)
  }

  const getNextStatus = (current: string): string | null => {
    const transitions: Record<string, string> = {
      PENDING: 'CONFIRMED',
      CONFIRMED: 'PROCESSING',
      PROCESSING: 'SHIPPED',
      SHIPPED: 'DELIVERED',
    }
    return transitions[current] || null
  }

  const filteredOrders = orders.filter(o => {
    if (statusFilter !== 'ALL' && o.status !== statusFilter) return false
    if (searchTerm) {
      const term = searchTerm.toLowerCase()
      return (
        o.productName.toLowerCase().includes(term) ||
        o.productSku.toLowerCase().includes(term) ||
        (o.customerName && o.customerName.toLowerCase().includes(term)) ||
        (o.customerEmail && o.customerEmail.toLowerCase().includes(term))
      )
    }
    return true
  })

  // ─── KPI Data ──────────────────────────────────────────────────────

  const totalOrders = orders.length
  const pendingOrders = orders.filter(o => o.status === 'PENDING').length
  const shippedOrders = orders.filter(o => o.status === 'SHIPPED').length
  const totalRevenue = orders.filter(o => o.status !== 'CANCELLED').reduce((sum, o) => sum + (o.totalAmount || 0), 0)

  const tabs = [
    { key: 'orders', label: 'Orders', count: totalOrders },
    { key: 'stats', label: 'Analytics' },
  ] as const

  // ─── Render ────────────────────────────────────────────────────────

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Endless Aisle</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Fulfill out-of-stock items from warehouse or other stores
          </p>
        </div>
        <button
          onClick={() => { resetForm(); setShowCreateModal(true) }}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus className="h-4 w-4" /> New Order
        </button>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <EnterpriseKPICard title="Total Orders" value={totalOrders} icon={ShoppingCart} trend={{ value: 0, isPositive: true }} />
        <EnterpriseKPICard title="Pending" value={pendingOrders} icon={Clock} trend={{ value: 0, isPositive: true }} />
        <EnterpriseKPICard title="Shipped" value={shippedOrders} icon={Truck} trend={{ value: 0, isPositive: true }} />
        <EnterpriseKPICard title="Total Revenue" value={`$${totalRevenue.toLocaleString()}`} icon={Package} trend={{ value: 0, isPositive: true }} />
      </div>

      {/* Tabs */}
      <EnterpriseTabs tabs={[...tabs]} activeTab={activeTab} onChange={(t) => setActiveTab(t as EATab)} />

      {/* Orders Tab */}
      {activeTab === 'orders' && (
        <>
          {/* Status Filter */}
          <div className="flex gap-2 flex-wrap">
            {(['ALL', 'PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'] as StatusFilter[]).map(s => (
              <button
                key={s}
                onClick={() => setStatusFilter(s)}
                className={clsx(
                  'px-3 py-1.5 rounded-lg text-sm font-medium transition-colors',
                  statusFilter === s
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
                )}
              >
                {s === 'ALL' ? 'All' : STATUS_CONFIG[s]?.label || s}
              </button>
            ))}
          </div>

          {/* Search */}
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search orders..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {/* Orders Table */}
          <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
            {isLoading ? (
              <div className="p-8 text-center text-gray-500">Loading orders...</div>
            ) : filteredOrders.length === 0 ? (
              <div className="p-8 text-center text-gray-500">
                {searchTerm || statusFilter !== 'ALL' ? 'No orders match your filters' : 'No endless aisle orders yet'}
              </div>
            ) : (
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-200 dark:border-gray-700">
                    <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Order</th>
                    <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Customer</th>
                    <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Product</th>
                    <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Fulfillment</th>
                    <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Amount</th>
                    <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Status</th>
                    <th className="text-right px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredOrders.map((order) => {
                    const config = STATUS_CONFIG[order.status || 'PENDING']
                    const Icon = config?.icon || Clock
                    const nextStatus = getNextStatus(order.status || 'PENDING')
                    return (
                      <tr key={order.id} className="border-b border-gray-100 dark:border-gray-700/50 hover:bg-gray-50 dark:hover:bg-gray-700/50">
                        <td className="px-4 py-3">
                          <div className="text-sm font-medium text-gray-900 dark:text-white">
                            #{order.id?.slice(0, 8)}
                          </div>
                          <div className="text-xs text-gray-500">
                            {order.createdAt ? new Date(order.createdAt).toLocaleDateString() : '—'}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="text-sm text-gray-900 dark:text-white">{order.customerName || '—'}</div>
                          <div className="text-xs text-gray-500">{order.customerEmail || '—'}</div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="text-sm text-gray-900 dark:text-white">{order.productName}</div>
                          <div className="text-xs text-gray-500 font-mono">{order.productSku}</div>
                        </td>
                        <td className="px-4 py-3">
                          <span className={clsx(
                            'px-2 py-1 rounded text-xs font-medium',
                            order.fulfillmentType === 'SHIP_TO_CUSTOMER'
                              ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
                              : 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300'
                          )}>
                            {FULFILLMENT_TYPES.find(f => f.value === order.fulfillmentType)?.label || order.fulfillmentType}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <div className="text-sm font-medium text-gray-900 dark:text-white">
                            ${order.totalAmount?.toLocaleString() || '0'}
                          </div>
                          <div className="text-xs text-gray-500">
                            {order.quantity} × ${order.unitPrice}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <EnterpriseStatusBadge
                            status={config?.color as any || 'inactive'}
                            label={config?.label || order.status}
                          />
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center justify-end gap-2">
                            {nextStatus && (
                              <button
                                onClick={() => updateStatusMutation.mutate({ id: order.id!, status: nextStatus })}
                                className="px-2 py-1 bg-green-600 text-white text-xs rounded hover:bg-green-700"
                                title={`Move to ${nextStatus}`}
                              >
                                {STATUS_CONFIG[nextStatus]?.label || nextStatus}
                              </button>
                            )}
                            {order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && (
                              <button
                                onClick={() => {
                                  if (confirm('Cancel this order?')) {
                                    updateStatusMutation.mutate({ id: order.id!, status: 'CANCELLED' })
                                  }
                                }}
                                className="px-2 py-1 bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 text-xs rounded hover:bg-red-200 dark:hover:bg-red-900/50"
                              >
                                Cancel
                              </button>
                            )}
                            <button
                              onClick={() => setSelectedOrder(order)}
                              className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700"
                            >
                              <Package className="h-4 w-4 text-gray-500" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}

      {/* Stats Tab */}
      {activeTab === 'stats' && stats && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
            <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400">Orders by Status</h3>
            <div className="mt-4 space-y-3">
              {Object.entries(STATUS_CONFIG).map(([key, config]) => {
                const count = orders.filter(o => o.status === key).length
                return (
                  <div key={key} className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <config.icon className="h-4 w-4 text-gray-400" />
                      <span className="text-sm text-gray-700 dark:text-gray-300">{config.label}</span>
                    </div>
                    <span className="font-medium text-gray-900 dark:text-white">{count}</span>
                  </div>
                )
              })}
            </div>
          </div>

          <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
            <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400">Revenue Summary</h3>
            <div className="mt-4 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-700 dark:text-gray-300">Total Revenue</span>
                <span className="font-bold text-lg text-gray-900 dark:text-white">${stats.totalRevenue.toLocaleString()}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-700 dark:text-gray-300">Avg Order Value</span>
                <span className="font-medium text-gray-900 dark:text-white">
                  ${stats.totalOrders > 0 ? (stats.totalRevenue / stats.totalOrders).toFixed(2) : '0.00'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-700 dark:text-gray-300">Fulfillment Rate</span>
                <span className="font-medium text-gray-900 dark:text-white">
                  {stats.totalOrders > 0 ? ((stats.deliveredOrders / stats.totalOrders) * 100).toFixed(1) : 0}%
                </span>
              </div>
            </div>
          </div>

          <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
            <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400">Fulfillment Types</h3>
            <div className="mt-4 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-700 dark:text-gray-300">Ship to Customer</span>
                <span className="font-medium text-gray-900 dark:text-white">
                  {orders.filter(o => o.fulfillmentType === 'SHIP_TO_CUSTOMER').length}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-700 dark:text-gray-300">Ship to Store</span>
                <span className="font-medium text-gray-900 dark:text-white">
                  {orders.filter(o => o.fulfillmentType === 'SHIP_TO_STORE').length}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Create Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">New Endless Aisle Order</h2>
              <button onClick={() => setShowCreateModal(false)} className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
                <X className="h-5 w-5 text-gray-500" />
              </button>
            </div>

            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Store ID *</label>
                  <input type="text" value={form.storeId || ''} onChange={e => setForm({ ...form, storeId: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Fulfillment Type *</label>
                  <select value={form.fulfillmentType} onChange={e => setForm({ ...form, fulfillmentType: e.target.value as any })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                    {FULFILLMENT_TYPES.map(f => <option key={f.value} value={f.value}>{f.label}</option>)}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Product SKU *</label>
                  <input type="text" value={form.productSku || ''} onChange={e => setForm({ ...form, productSku: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white font-mono" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Product Name *</label>
                  <input type="text" value={form.productName || ''} onChange={e => setForm({ ...form, productName: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Quantity *</label>
                  <input type="number" min="1" value={form.quantity || ''} onChange={e => setForm({ ...form, quantity: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Unit Price ($) *</label>
                  <input type="number" step="0.01" min="0" value={form.unitPrice || ''} onChange={e => setForm({ ...form, unitPrice: parseFloat(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
              </div>

              {form.fulfillmentType === 'SHIP_TO_CUSTOMER' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Ship To Address</label>
                  <textarea value={form.shipToAddress || ''} onChange={e => setForm({ ...form, shipToAddress: e.target.value })} rows={2}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
              )}

              <div className="border-t border-gray-200 dark:border-gray-700 pt-4">
                <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">Customer Information</h3>
                <div className="grid grid-cols-3 gap-4">
                  <div>
                    <label className="block text-sm text-gray-600 dark:text-gray-400 mb-1">
                      <User className="inline h-3 w-3 mr-1" /> Name
                    </label>
                    <input type="text" value={form.customerName || ''} onChange={e => setForm({ ...form, customerName: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600 dark:text-gray-400 mb-1">
                      <Mail className="inline h-3 w-3 mr-1" /> Email
                    </label>
                    <input type="email" value={form.customerEmail || ''} onChange={e => setForm({ ...form, customerEmail: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600 dark:text-gray-400 mb-1">
                      <Phone className="inline h-3 w-3 mr-1" /> Phone
                    </label>
                    <input type="tel" value={form.customerPhone || ''} onChange={e => setForm({ ...form, customerPhone: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                  </div>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Notes</label>
                <textarea value={form.notes || ''} onChange={e => setForm({ ...form, notes: e.target.value })} rows={2}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
              </div>

              {form.unitPrice && form.quantity && (
                <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4">
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-600 dark:text-gray-400">Total Amount</span>
                    <span className="font-bold text-lg text-gray-900 dark:text-white">
                      ${(form.unitPrice * form.quantity).toFixed(2)}
                    </span>
                  </div>
                </div>
              )}
            </div>

            <div className="flex justify-end gap-3 p-6 border-t border-gray-200 dark:border-gray-700">
              <button onClick={() => setShowCreateModal(false)}
                className="px-4 py-2 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg">
                Cancel
              </button>
              <button onClick={handleSubmit}
                disabled={createMutation.isPending}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
                Create Order
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Order Detail Modal */}
      {selectedOrder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-2xl w-full max-w-lg">
            <div className="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Order Details</h2>
              <button onClick={() => setSelectedOrder(null)} className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
                <X className="h-5 w-5 text-gray-500" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <div className="text-sm text-gray-500">Order ID</div>
                  <div className="font-mono text-gray-900 dark:text-white">#{selectedOrder.id?.slice(0, 8)}</div>
                </div>
                <div>
                  <div className="text-sm text-gray-500">Status</div>
                  <EnterpriseStatusBadge
                    status={STATUS_CONFIG[selectedOrder.status || 'PENDING']?.color as any || 'inactive'}
                    label={STATUS_CONFIG[selectedOrder.status || 'PENDING']?.label || selectedOrder.status}
                  />
                </div>
              </div>
              <div>
                <div className="text-sm text-gray-500">Product</div>
                <div className="font-medium text-gray-900 dark:text-white">{selectedOrder.productName}</div>
                <div className="text-sm font-mono text-gray-500">{selectedOrder.productSku}</div>
              </div>
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <div className="text-sm text-gray-500">Quantity</div>
                  <div className="text-gray-900 dark:text-white">{selectedOrder.quantity}</div>
                </div>
                <div>
                  <div className="text-sm text-gray-500">Unit Price</div>
                  <div className="text-gray-900 dark:text-white">${selectedOrder.unitPrice}</div>
                </div>
                <div>
                  <div className="text-sm text-gray-500">Total</div>
                  <div className="font-bold text-gray-900 dark:text-white">${selectedOrder.totalAmount?.toFixed(2)}</div>
                </div>
              </div>
              {selectedOrder.customerName && (
                <div>
                  <div className="text-sm text-gray-500">Customer</div>
                  <div className="text-gray-900 dark:text-white">{selectedOrder.customerName}</div>
                  {selectedOrder.customerEmail && <div className="text-sm text-gray-500">{selectedOrder.customerEmail}</div>}
                </div>
              )}
              {selectedOrder.notes && (
                <div>
                  <div className="text-sm text-gray-500">Notes</div>
                  <div className="text-gray-700 dark:text-gray-300">{selectedOrder.notes}</div>
                </div>
              )}
            </div>
            <div className="flex justify-end p-6 border-t border-gray-200 dark:border-gray-700">
              <button onClick={() => setSelectedOrder(null)}
                className="px-4 py-2 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600">
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
