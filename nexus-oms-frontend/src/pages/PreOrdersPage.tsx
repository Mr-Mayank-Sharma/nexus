import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Calendar, Package, ShoppingCart, Clock, CheckCircle, AlertTriangle,
  Search, Eye, Archive, TrendingUp, Filter, Play, XCircle, RefreshCw,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import parkedOrdersApi from '../api/parkedOrders'
import type { ParkedOrder } from '../api/parkedOrders'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import type { Tab } from '../components/enterprise'

type PreOrderTab = 'parked' | 'released' | 'cancelled'

export default function PreOrdersPage() {
  const navigate = useNavigate()
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<PreOrderTab>('parked')
  const [searchTerm, setSearchTerm] = useState('')
  const [reasonFilter, setReasonFilter] = useState<'all' | 'PREORDER' | 'BACKORDER' | 'FRAUD_HOLD' | 'CREDIT_HOLD' | 'MANUAL_HOLD'>('all')

  const { data: parkedOrders = [], isLoading, refetch } = useQuery({
    queryKey: ['parked-orders'],
    queryFn: async () => {
      const res = await parkedOrdersApi.getParkedOrders()
      return res.data as ParkedOrder[]
    },
  })

  const releaseMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      parkedOrdersApi.releaseOrder(id, reason || 'Manual release by user'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['parked-orders'] })
      addToast({ type: 'success', title: 'Order released successfully' })
    },
    onError: (error: any) => {
      addToast({ type: 'error', title: 'Failed to release order', message: error.message })
    },
  })

  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      parkedOrdersApi.cancelOrder(id, reason || 'Cancelled by user'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['parked-orders'] })
      addToast({ type: 'success', title: 'Order cancelled successfully' })
    },
    onError: (error: any) => {
      addToast({ type: 'error', title: 'Failed to cancel order', message: error.message })
    },
  })

  const filteredOrders = parkedOrders.filter(order => {
    if (searchTerm) {
      const search = searchTerm.toLowerCase()
      if (!order.orderNumber?.toLowerCase().includes(search) &&
          !order.sku?.toLowerCase().includes(search) &&
          !order.customerEmail?.toLowerCase().includes(search)) {
        return false
      }
    }
    if (reasonFilter !== 'all' && order.reason !== reasonFilter) return false
    if (activeTab === 'parked' && order.status !== 'PARKED') return false
    if (activeTab === 'released' && order.status !== 'RELEASED') return false
    if (activeTab === 'cancelled' && order.status !== 'CANCELLED') return false
    return true
  })

  const parkedCount = parkedOrders.filter(o => o.status === 'PARKED').length
  const releasedCount = parkedOrders.filter(o => o.status === 'RELEASED').length
  const cancelledCount = parkedOrders.filter(o => o.status === 'CANCELLED').length

  const tabs: Tab[] = [
    { id: 'parked', label: 'Parked', badge: parkedCount },
    { id: 'released', label: 'Released', badge: releasedCount },
    { id: 'cancelled', label: 'Cancelled', badge: cancelledCount },
  ]

  const formatDate = (d: string) => new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })

  const getReasonColor = (reason: string) => {
    switch (reason) {
      case 'PREORDER': return 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)] dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]'
      case 'BACKORDER': return 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)] dark:bg-[var(--nexus-warning-900)]/30 dark:text-[var(--nexus-warning-400)]'
      case 'FRAUD_HOLD': return 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-400)]'
      case 'CREDIT_HOLD': return 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-800)] dark:bg-[var(--nexus-ai-900)]/30 dark:text-[var(--nexus-ai-400)]'
      case 'MANUAL_HOLD': return 'bg-[var(--surface-muted)] text-[var(--text-primary)] bg-[var(--surface-sunken)]/30 dark:text-[var(--text-tertiary)]'
      default: return 'bg-[var(--surface-muted)] text-[var(--text-primary)] bg-[var(--surface-sunken)]/30 dark:text-[var(--text-tertiary)]'
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] text-[var(--text-primary)] flex items-center gap-2.5">
            <Package className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Pre-Orders
          </h1>
          <p className="text-sm text-[var(--text-secondary)] dark:text-[var(--text-tertiary)] mt-1">Manage parked orders awaiting release</p>
        </div>
        <div className="flex items-center gap-2">
          <EnterpriseKPICard title="Parked" value={parkedCount} icon={<Clock className="w-4 h-4" />} color="warning" trend={null} />
          <EnterpriseKPICard title="Released" value={releasedCount} icon={<CheckCircle className="w-4 h-4" />} color="success" trend={null} />
        </div>
      </div>

      <EnterpriseTabs tabs={tabs} activeTab={activeTab} onChange={t => setActiveTab(t as PreOrderTab)} />

      <div className="flex items-center gap-3">
        <Autocomplete
          value={searchTerm}
          onChange={setSearchTerm}
          placeholder="Search by order number, SKU, or email..."
          minChars={0}
          className="flex-1 max-w-md"
        />
        <div className="flex gap-1 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded-lg p-0.5">
          {(['all', 'PREORDER', 'BACKORDER', 'FRAUD_HOLD', 'CREDIT_HOLD', 'MANUAL_HOLD'] as const).map(f => (
            <button
              key={f}
              onClick={() => setReasonFilter(f)}
              className={clsx('px-3 py-1.5 text-xs font-medium rounded-md transition-all capitalize',
                reasonFilter === f ? 'bg-[var(--surface-muted)] text-[var(--text-primary)] shadow-sm' : 'text-[var(--text-secondary)] dark:text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] dark:hover:text-[var(--text-tertiary)]')}
            >
              {f === 'all' ? 'All Reasons' : f.replace('_', ' ')}
            </button>
          ))}
        </div>
        <button onClick={() => refetch()} className="enterprise-btn-secondary px-3 py-2">
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {isLoading ? (
        <div className="enterprise-card flex items-center justify-center p-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      ) : filteredOrders.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <Package className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
          <p className="font-medium text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">No orders found</p>
        </div>
      ) : (
        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Reason</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">SKU</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Qty</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Priority</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Expected</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Parked At</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {filteredOrders.map(order => {
                  const isOverdue = order.expectedDate && new Date(order.expectedDate) < new Date()
                  return (
                    <tr key={order.id} className="enterprise-table-row">
                      <td className="px-4 py-3">
                        <div className="text-sm font-medium text-[var(--text-primary)] text-[var(--text-primary)]">{order.orderNumber}</div>
                        <div className="text-xs text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">{order.customerEmail || '—'}</div>
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', getReasonColor(order.reason))}>
                          {order.reason.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs font-mono text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">{order.sku || '—'}</td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">{order.quantity || '—'}</td>
                      <td className="px-4 py-3 text-center">
                        <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium',
                          order.priority && order.priority <= 3 ? 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]' :
                          order.priority && order.priority <= 6 ? 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]' :
                          'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]')}>
                          P{order.priority || 10}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center">
                        {order.expectedDate ? (
                          <span className={clsx('inline-flex items-center gap-1 text-xs font-medium', isOverdue ? 'text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]' : 'text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]')}>
                            <Calendar className="w-3 h-3" />
                            {formatDate(order.expectedDate)}
                            {isOverdue && <AlertTriangle className="w-3 h-3 text-[var(--nexus-error-500)]" />}
                          </span>
                        ) : <span className="text-xs text-[var(--text-tertiary)]">—</span>}
                      </td>
                      <td className="px-4 py-3 text-center text-xs text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">{formatDate(order.parkedAt)}</td>
                      <td className="px-4 py-3 text-center">
                        <EnterpriseStatusBadge
                          status={order.status === 'PARKED' ? 'pending' : order.status === 'RELEASED' ? 'success' : 'error'}
                          label={order.status}
                        />
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button className="enterprise-btn-secondary text-xs px-2 py-1" onClick={() => navigate(`/orders/${order.orderId}`)}>
                            <Eye className="w-3.5 h-3.5" />
                          </button>
                          {order.status === 'PARKED' && (
                            <>
                              <PermissionGate resource="orders" action="edit">
                                <button
                                  onClick={() => releaseMutation.mutate({ id: order.id })}
                                  disabled={releaseMutation.isPending}
                                  className="enterprise-btn-primary text-xs px-2 py-1"
                                >
                                  <Play className="w-3.5 h-3.5" />
                                </button>
                              </PermissionGate>
                              <PermissionGate resource="orders" action="delete">
                                <button
                                  onClick={() => cancelMutation.mutate({ id: order.id })}
                                  disabled={cancelMutation.isPending}
                                  className="enterprise-btn-danger text-xs px-2 py-1"
                                >
                                  <XCircle className="w-3.5 h-3.5" />
                                </button>
                              </PermissionGate>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}