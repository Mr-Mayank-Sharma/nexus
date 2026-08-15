import { useState, useEffect, useCallback } from 'react'
import {
  Globe, Package, Truck, ShoppingCart, Wallet, Receipt, RefreshCw,
  ArrowRight, Building2,
} from 'lucide-react'
import clsx from 'clsx'
import { useNavigate } from 'react-router-dom'
import { useToast } from '../hooks/useToast'
import { getClientOverview, type ClientPortalOverview } from '../api/billing'
import { getCustomers } from '../api/customers'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'

const asArray = (d: unknown): any[] => (Array.isArray(d) ? d : Array.isArray((d as any)?.content) ? (d as any).content : [])

const fmtMoney = (v: number | null | undefined) => (v ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const statusTone = (s: string) =>
  ['SHIPPED', 'DELIVERED', 'COMPLETED'].includes(s) ? 'success'
    : ['PENDING', 'APPROVED'].includes(s) ? 'warning'
    : ['PICKING', 'PACKED', 'READY', 'ALLOCATED', 'RELEASED'].includes(s) ? 'info'
    : 'neutral'

export default function ClientPortalPage() {
  const navigate = useNavigate()
  const { addToast } = useToast()
  const [customers, setCustomers] = useState<any[]>([])
  const [clientId, setClientId] = useState('')
  const [overview, setOverview] = useState<ClientPortalOverview | null>(null)
  const [loading, setLoading] = useState(false)

  const loadCustomers = useCallback(async () => {
    try {
      const res = await getCustomers()
      const list = asArray(res.data)
      setCustomers(list)
      if (list.length > 0 && !clientId) setClientId(list[0].id)
    } catch {
      setCustomers([])
    }
  }, [clientId])

  useEffect(() => {
    loadCustomers()
  }, [loadCustomers])

  useEffect(() => {
    if (!clientId) return
    setLoading(true)
    getClientOverview(clientId)
      .then(res => {
        if (res.data) setOverview(res.data)
      })
      .catch(() => addToast({ type: 'error', title: 'Failed to load client overview' }))
      .finally(() => setLoading(false))
  }, [clientId, addToast])

  const byStatus = overview?.ordersByStatus || {}

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Globe className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Client Portal
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Self-service 3PL client dashboard — live order, fulfillment and billing status</p>
        </div>
        <div className="flex items-center gap-2">
          <select value={clientId} onChange={e => setClientId(e.target.value)} className="enterprise-input max-w-xs">
            <option value="">Select client...</option>
            {customers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <button type="button" onClick={() => clientId && getClientOverview(clientId).then(r => setOverview(r.data)).catch(() => addToast({ type: 'error', title: 'Refresh failed' }))} className="enterprise-btn-secondary text-sm px-3 py-2 flex items-center gap-1.5">
            <RefreshCw className={clsx('w-4 h-4', loading && 'animate-spin')} /> Refresh
          </button>
        </div>
      </div>

      {!clientId ? (
        <div className="enterprise-card p-12 text-center">
          <Building2 className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">Select a client to view their portal</p>
        </div>
      ) : loading || !overview ? (
        <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" /></div>
      ) : (
        <>
          <div className="bg-gradient-to-r from-[var(--nexus-primary-600)] via-[var(--nexus-primary-700)] to-indigo-800 dark:from-gray-900 dark:via-gray-800 dark:to-gray-900 rounded-2xl p-6 text-white">
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-2xl bg-white/15 backdrop-blur-sm flex items-center justify-center border border-white/20">
                <Building2 className="w-6 h-6 text-white" />
              </div>
              <div>
                <h2 className="text-xl font-bold">{overview.client.name}</h2>
                <p className="text-sm text-white/70">{overview.client.email}</p>
              </div>
              <div className="ml-auto hidden sm:block text-right">
                <p className="text-xs text-white/60">Last 30 days</p>
                <p className="text-2xl font-bold">{overview.ordersLast30d} <span className="text-sm font-normal text-white/70">orders</span></p>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
            <EnterpriseKPICard title="Open Orders" value={overview.openOrders} icon={<Package className="w-4 h-4" />} color="warning" />
            <EnterpriseKPICard title="Fulfilled" value={overview.fulfilledOrders} icon={<Truck className="w-4 h-4" />} color="success" />
            <EnterpriseKPICard title="Units Ordered" value={overview.unitsOrdered} icon={<ShoppingCart className="w-4 h-4" />} color="info" />
            <EnterpriseKPICard title="Open Invoices" value={overview.outstandingInvoices} icon={<Receipt className="w-4 h-4" />} color={overview.outstandingInvoices > 0 ? 'error' : 'success'} />
            <EnterpriseKPICard title="Outstanding" value={fmtMoney(overview.outstandingBalance)} icon={<Wallet className="w-4 h-4" />} color={overview.outstandingBalance > 0 ? 'error' : 'success'} />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            <div className="enterprise-card p-5">
              <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-3">Orders by Status</h3>
              {Object.keys(byStatus).length === 0 ? (
                <p className="text-xs text-[var(--text-tertiary)]">No orders in the period</p>
              ) : (
                <div className="space-y-2">
                  {Object.entries(byStatus).sort((a, b) => b[1] - a[1]).map(([status, count]) => (
                    <div key={status} className="flex items-center justify-between">
                      <span className="text-xs text-[var(--text-secondary)]">{status.replace('_', ' ')}</span>
                      <div className="flex items-center gap-2">
                        <div className="w-24 bg-[var(--surface-muted)] rounded-full h-1.5 overflow-hidden">
                          <div
                            className={clsx('h-full rounded-full',
                              ['SHIPPED', 'DELIVERED', 'COMPLETED'].includes(status) ? 'bg-[var(--nexus-success-500)]'
                                : 'bg-[var(--nexus-primary-500)]')}
                            style={{ width: `${(count / (overview.ordersLast30d || 1)) * 100}%` }}
                          />
                        </div>
                        <span className="text-xs font-semibold text-[var(--text-primary)]">{count}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="enterprise-card p-5 lg:col-span-2">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-[var(--text-secondary)]">Recent Orders</h3>
                <button type="button" onClick={() => navigate('/orders')} className="text-xs text-[var(--nexus-primary-600)] flex items-center gap-1 hover:underline">
                  All orders <ArrowRight className="w-3 h-3" />
                </button>
              </div>
              {overview.recentOrders.length === 0 ? (
                <p className="text-xs text-[var(--text-tertiary)]">No recent orders</p>
              ) : (
                <div className="space-y-2">
                  {overview.recentOrders.map(o => (
                    <button type="button" key={o.id} onClick={() => navigate(`/orders/${o.id}`)} className="w-full flex items-center justify-between bg-[var(--surface-sunken)]/50 rounded-lg px-3 py-2 hover:bg-[var(--surface-sunken)] transition-colors text-left">
                      <div>
                        <p className="text-sm font-medium text-[var(--text-primary)]">{o.orderNumber}</p>
                        <p className="text-[10px] text-[var(--text-tertiary)]">{o.createdAt ? new Date(o.createdAt).toLocaleDateString() : ''}</p>
                      </div>
                      <div className="text-right">
                        <p className="text-sm font-semibold text-[var(--text-primary)]">{fmtMoney(o.totalAmount)}</p>
                        <EnterpriseStatusBadge status={statusTone(o.status) as any} label={(o.status || 'UNKNOWN').replace('_', ' ')} />
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
