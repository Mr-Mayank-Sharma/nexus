import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Store, Package, Users, ShoppingBag, TrendingUp, Clock, AlertTriangle,
  ShoppingCart, BarChart3, ArrowRight, Calendar, DollarSign, Eye,
} from 'lucide-react'
import clsx from 'clsx'
import * as ordersApi from '../api/orders'
import * as productsApi from '../api/products'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'

export default function StoreDashboardPage() {
  const navigate = useNavigate()

  const { data: stats } = useQuery({
    queryKey: ['store-stats'],
    queryFn: async () => {
      const [ordRes, prodRes] = await Promise.all([
        ordersApi.getOrders({}).catch(() => ({ data: { content: [] } })),
        productsApi.getProducts().catch(() => ({ data: [] })),
      ])
      return { orders: ordRes.data, products: prodRes.data }
    },
  })

  const kpis = [
    { title: "Today's Sales", value: '$12,847', icon: <DollarSign className="w-5 h-5" />, color: 'success' as const, trend: { value: 18, isUp: true } },
    { title: 'Orders Today', value: '47', icon: <ShoppingCart className="w-5 h-5" />, color: 'primary' as const, trend: { value: 12, isUp: true } },
    { title: 'BOPIS Ready', value: '8', icon: <ShoppingBag className="w-5 h-5" />, color: 'info' as const, trend: null },
    { title: 'Low Stock Items', value: '12', icon: <AlertTriangle className="w-5 h-5" />, color: 'warning' as const, trend: { value: 3, isUp: false } },
  ]

  const recentOrders = [
    { id: 'ORD-4821', customer: 'Alice M.', status: 'PICKUP_READY', items: 3, total: 124.99, time: '10 min ago' },
    { id: 'ORD-4820', customer: 'Bob K.', status: 'PROCESSING', items: 1, total: 49.99, time: '25 min ago' },
    { id: 'ORD-4819', customer: 'Carol S.', status: 'PICKUP_READY', items: 5, total: 289.50, time: '1 hr ago' },
    { id: 'ORD-4818', customer: 'Dave W.', status: 'CONFIRMED', items: 2, total: 79.98, time: '2 hr ago' },
    { id: 'ORD-4817', customer: 'Eve R.', status: 'DELIVERED', items: 4, total: 198.00, time: '3 hr ago' },
  ]

  const lowStock = [
    { name: 'Wireless Mouse', sku: 'WM-001', stock: 3, threshold: 10 },
    { name: 'USB-C Cable', sku: 'UC-100', stock: 5, threshold: 20 },
    { name: 'Laptop Stand', sku: 'LS-200', stock: 2, threshold: 8 },
    { name: 'HDMI Adapter', sku: 'HD-050', stock: 4, threshold: 15 },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] text-[var(--text-primary)] flex items-center gap-2.5">
            <Store className="w-7 h-7 text-[var(--nexus-success-500)]" />
            Store Dashboard
          </h1>
          <p className="text-sm text-[var(--text-secondary)] dark:text-[var(--text-tertiary)] mt-1">Downtown Store · Today's operations</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => navigate('/orders')} className="enterprise-btn-secondary text-sm px-4 py-2"><Package className="w-4 h-4" /> Orders</button>
          <button onClick={() => navigate('/bopis')} className="enterprise-btn-primary text-sm px-4 py-2"><ShoppingBag className="w-4 h-4" /> BOPIS</button>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-4">
        {kpis.map(k => (<EnterpriseKPICard key={k.title} title={k.title} value={k.value} icon={k.icon} color={k.color} trend={k.trend} />))}
      </div>

      <div className="grid grid-cols-3 gap-6">
        {/* Recent Orders */}
        <div className="col-span-2 enterprise-card p-5">
          <h3 className="text-sm font-semibold text-[var(--text-primary)] text-[var(--text-primary)] mb-4 flex items-center gap-2">
            <ShoppingCart className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Recent Store Orders
          </h3>
          <div className="divide-y divide-gray-100 dark:divide-gray-800">
            {recentOrders.map((o, i) => (
              <div key={i} className="flex items-center justify-between py-3">
                <div className="flex items-center gap-3">
                  <div className={clsx('w-9 h-9 rounded-lg flex items-center justify-center',
                    o.status === 'PICKUP_READY' ? 'bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 text-[var(--nexus-success-600)]' :
                    o.status === 'PROCESSING' ? 'bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/20 text-[var(--nexus-primary-600)]' :
                    'bg-[var(--surface-sunken)] bg-[var(--surface-base)] text-[var(--text-tertiary)]')}>
                    {o.status === 'PICKUP_READY' ? <ShoppingBag className="w-4 h-4" /> : <Package className="w-4 h-4" />}
                  </div>
                  <div>
                    <p className="text-sm font-medium text-[var(--text-primary)] text-[var(--text-primary)]">{o.id}</p>
                    <p className="text-xs text-[var(--text-secondary)]">{o.customer} · {o.items} items</p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-semibold text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">${o.total.toFixed(2)}</span>
                  <EnterpriseStatusBadge status={o.status === 'PICKUP_READY' ? 'success' : o.status === 'PROCESSING' ? 'info' : o.status === 'DELIVERED' ? 'success' : 'pending'} label={o.status.replace('_', ' ')} />
                  <button onClick={() => navigate(`/orders/${o.id}`)} className="p-1 hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] rounded"><Eye className="w-4 h-4 text-[var(--text-tertiary)]" /></button>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Low Stock + Quick Actions */}
        <div className="space-y-4">
          <div className="enterprise-card p-5">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] text-[var(--text-primary)] mb-3 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-[var(--nexus-warning-500)]" /> Low Stock Alerts
            </h3>
            <div className="space-y-2">
              {lowStock.map((item, i) => (
                <div key={i} className="flex items-center justify-between p-2 rounded-lg bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/10">
                  <div>
                    <p className="text-xs font-medium text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">{item.name}</p>
                    <p className="text-[10px] text-[var(--text-tertiary)]">{item.sku}</p>
                  </div>
                  <span className="text-xs font-semibold text-[var(--nexus-warning-600)]">{item.stock} / {item.threshold}</span>
                </div>
              ))}
            </div>
            <button onClick={() => navigate('/inventory')} className="w-full mt-3 enterprise-btn-secondary text-xs py-2">View Inventory</button>
          </div>

          <div className="enterprise-card p-5">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] text-[var(--text-primary)] mb-3">Quick Actions</h3>
            <div className="space-y-2">
              {[
                { label: 'BOPIS Orders', path: '/bopis', icon: <ShoppingBag className="w-4 h-4" /> },
                { label: 'Store Inventory', path: '/inventory', icon: <Package className="w-4 h-4" /> },
                { label: 'Customers', path: '/customers', icon: <Users className="w-4 h-4" /> },
                { label: 'Sales Report', path: '/analytics', icon: <BarChart3 className="w-4 h-4" /> },
              ].map((a, i) => (
                <button key={i} onClick={() => navigate(a.path)}
                  className="w-full flex items-center justify-between p-2.5 rounded-lg bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 hover:bg-[var(--surface-muted)] hover:bg-[var(--surface-base)] text-sm text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">
                  <span className="flex items-center gap-2">{a.icon} {a.label}</span>
                  <ArrowRight className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
