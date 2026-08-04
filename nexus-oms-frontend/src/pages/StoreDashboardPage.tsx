import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Store, Package, Users, ShoppingBag, Clock, AlertTriangle,
  ShoppingCart, BarChart3, ArrowRight, DollarSign, Eye,
} from 'lucide-react'
import clsx from 'clsx'
import * as ordersApi from '../api/orders'
import * as inventoryApi from '../api/inventory'
import { pickupApi } from '../api/pickup'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'

function asArray<T = any>(d: any): T[] {
  if (Array.isArray(d)) return d
  if (d && Array.isArray(d.data)) return d.data
  if (d && Array.isArray(d.content)) return d.content
  if (d && typeof d === 'object') {
    const vals = Object.values(d)
    if (vals.length > 0 && typeof vals[0] === 'object') return vals as T[]
  }
  return []
}

function timeAgo(iso?: string): string {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins} min ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs} hr ago`
  return `${Math.floor(hrs / 24)} d ago`
}

function orderBadge(status: string): { status: string; label: string } {
  switch (status) {
    case 'DELIVERED': return { status: 'success', label: 'Delivered' }
    case 'SHIPPED':
    case 'IN_TRANSIT': return { status: 'info', label: status.replace('_', ' ') }
    case 'PENDING':
    case 'CONFIRMED':
    case 'ALLOCATED': return { status: 'pending', label: status }
    case 'EXCEPTION': return { status: 'error', label: 'Exception' }
    case 'CANCELLED': return { status: 'neutral', label: 'Cancelled' }
    default: return { status: 'neutral', label: status.replace('_', ' ') }
  }
}

export default function StoreDashboardPage() {
  const navigate = useNavigate()

  const { data: stats, isLoading } = useQuery({
    queryKey: ['store-stats'],
    queryFn: async () => {
      const [ordRes, invRes, pickupRes] = await Promise.all([
        ordersApi.getOrders({}).catch(() => null),
        inventoryApi.getInventory().catch(() => null),
        pickupApi.getReadyForHandoff().catch(() => null),
      ])
      return {
        orders: asArray(ordRes?.data),
        inventory: asArray(invRes?.data),
        readyPickups: asArray(pickupRes?.data),
      }
    },
    refetchInterval: 60000,
  })

  const orders = stats?.orders ?? []
  const inventory = stats?.inventory ?? []
  const readyPickups = stats?.readyPickups ?? []

  const todayStr = new Date().toISOString().split('T')[0]
  const todayOrders = useMemo(
    () => orders.filter(o => (o.createdAt || '').slice(0, 10) === todayStr),
    [orders, todayStr],
  )

  const salesToday = useMemo(
    () => todayOrders.reduce((sum, o) => sum + (o.total || 0), 0),
    [todayOrders],
  )

  const lowStockItems = useMemo(
    () => inventory.filter(item => (item.quantityOnHand || 0) <= 0 || item.atp <= item.safetyStock),
    [inventory],
  )

  const kpis = [
    { title: "Today's Sales", value: `$${salesToday.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, icon: <DollarSign className="w-5 h-5" />, color: 'success' as const },
    { title: 'Orders Today', value: String(todayOrders.length), icon: <ShoppingCart className="w-5 h-5" />, color: 'primary' as const },
    { title: 'BOPIS Ready', value: String(readyPickups.length), icon: <ShoppingBag className="w-5 h-5" />, color: 'info' as const },
    { title: 'Low Stock Items', value: String(lowStockItems.length), icon: <AlertTriangle className="w-5 h-5" />, color: 'warning' as const },
  ]

  const recentOrders = useMemo(
    () => [...orders]
      .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
      .slice(0, 5)
      .map(o => ({
        id: o.id,
        orderNumber: o.orderNumber || o.id,
        customerName: o.customerName || '—',
        items: (o.items || []).length,
        total: o.total || 0,
        status: o.status,
        createdAt: o.createdAt,
      })),
    [orders],
  )

  const lowStock = useMemo(
    () => lowStockItems.slice(0, 5).map(item => ({
      name: item.productName || item.sku,
      sku: item.sku,
      stock: item.quantityOnHand,
      threshold: Math.max(item.safetyStock || item.reorderPoint || 0, 0),
    })),
    [lowStockItems],
  )

  return (
    <PermissionGate resource="inventory" action="view">
      <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Store className="w-7 h-7 text-[var(--nexus-success-500)]" />
            Store Dashboard
          </h1>
          <p className="text-sm text-[var(--text-secondary)] dark:text-[var(--text-tertiary)] mt-1">Downtown Store · Today's operations</p>
        </div>
        <div className="flex items-center gap-2">
          <button type="button" onClick={() => navigate('/orders')} className="enterprise-btn-secondary text-sm px-4 py-2"><Package className="w-4 h-4" /> Orders</button>
          <button type="button" onClick={() => navigate('/bopis')} className="enterprise-btn-primary text-sm px-4 py-2"><ShoppingBag className="w-4 h-4" /> BOPIS</button>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-4">
        {kpis.map(k => (<EnterpriseKPICard key={k.title} title={k.title} value={k.value} icon={k.icon} color={k.color} loading={isLoading} />))}
      </div>

      <div className="grid grid-cols-3 gap-6">
        {/* Recent Orders */}
        <div className="col-span-2 enterprise-card p-5">
          <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
            <ShoppingCart className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Recent Store Orders
          </h3>
          <div className="divide-y divide-[var(--surface-sunken)] dark:divide-gray-800">
            {recentOrders.length === 0 ? (
              <p className="py-8 text-center text-sm text-[var(--text-tertiary)]">
                {isLoading ? 'Loading orders…' : 'No store orders yet'}
              </p>
            ) : recentOrders.map(o => {
              const badge = orderBadge(o.status)
              return (
                <div key={o.id} className="flex items-center justify-between py-3">
                  <div className="flex items-center gap-3">
                    <div className={clsx('w-9 h-9 rounded-lg flex items-center justify-center',
                      o.status === 'PICKUP_READY' ? 'bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 text-[var(--nexus-success-600)]' :
                      'bg-[var(--surface-sunken)] text-[var(--text-tertiary)]')}>
                      <Package className="w-4 h-4" />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-[var(--text-primary)]">{o.orderNumber}</p>
                      <p className="text-xs text-[var(--text-secondary)]">{o.customerName} · {o.items} items · {timeAgo(o.createdAt)}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-sm font-semibold text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">${o.total.toFixed(2)}</span>
                    <EnterpriseStatusBadge status={badge.status} label={badge.label} />
                    <button type="button" onClick={() => navigate(`/orders/${o.id}`)} className="p-1 hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] rounded"><Eye className="w-4 h-4 text-[var(--text-tertiary)]" /></button>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        {/* Low Stock + Quick Actions */}
        <div className="space-y-4">
          <div className="enterprise-card p-5">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-[var(--nexus-warning-500)]" /> Low Stock Alerts
            </h3>
            <div className="space-y-2">
              {lowStock.length === 0 ? (
                <p className="py-3 text-center text-xs text-[var(--text-tertiary)]">
                  {isLoading ? 'Loading inventory…' : 'No low stock alerts'}
                </p>
              ) : lowStock.map((item, i) => (
                <div key={`${item.sku}-${i}`} className="flex items-center justify-between p-2 rounded-lg bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/10">
                  <div>
                    <p className="text-xs font-medium text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">{item.name}</p>
                    <p className="text-[10px] text-[var(--text-tertiary)]">{item.sku}</p>
                  </div>
                  <span className="text-xs font-semibold text-[var(--nexus-warning-600)]">{item.stock} / {item.threshold}</span>
                </div>
              ))}
            </div>
            <button type="button" onClick={() => navigate('/inventory')} className="w-full mt-3 enterprise-btn-secondary text-xs py-2">View Inventory</button>
          </div>

          <div className="enterprise-card p-5">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
              <Clock className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Quick Actions
            </h3>
            <div className="space-y-2">
              {[
                { label: 'BOPIS Orders', path: '/bopis', icon: <ShoppingBag className="w-4 h-4" /> },
                { label: 'Store Inventory', path: '/inventory', icon: <Package className="w-4 h-4" /> },
                { label: 'Customers', path: '/customers', icon: <Users className="w-4 h-4" /> },
                { label: 'Sales Report', path: '/analytics', icon: <BarChart3 className="w-4 h-4" /> },
              ].map((a, i) => (
                <button type="button" key={i} onClick={() => navigate(a.path)}
                  className="w-full flex items-center justify-between p-2.5 rounded-lg bg-[var(--surface-sunken)]/50 hover:bg-[var(--surface-muted)] text-sm text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">
                  <span className="flex items-center gap-2">{a.icon} {a.label}</span>
                  <ArrowRight className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
    </PermissionGate>
  )
}
