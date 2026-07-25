import { useState, useEffect } from 'react'
import {
  BarChart3, TrendingUp, TrendingDown, Package, DollarSign, Truck, Warehouse,
  Users, ShoppingCart, ArrowUpRight, ArrowDownRight, Calendar, Filter,
  RefreshCw, Download, Eye,
} from 'lucide-react'
import { clsx } from 'clsx'
import { useAuth } from '../context/AuthContext'
import PermissionGate from '../components/rbac/PermissionGate'

interface MetricCard {
  title: string
  value: string
  change: number
  changeLabel: string
  icon: React.ReactNode
  color: string
  bgColor: string
}

interface ChartData {
  label: string
  value: number
  color?: string
}

export default function AnalyticsDashboardPage() {
  const { user } = useAuth()
  const [timeRange, setTimeRange] = useState<'today' | '7d' | '30d' | '90d'>('30d')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const timer = setTimeout(() => setLoading(false), 800)
    return () => clearTimeout(timer)
  }, [])

  const metrics: MetricCard[] = [
    {
      title: 'Total Orders',
      value: '12,847',
      change: 12.5,
      changeLabel: 'vs last period',
      icon: <Package className="w-5 h-5" />,
      color: 'text-blue-600',
      bgColor: 'bg-blue-50 dark:bg-blue-950/30',
    },
    {
      title: 'Revenue',
      value: '$2.4M',
      change: 8.3,
      changeLabel: 'vs last period',
      icon: <DollarSign className="w-5 h-5" />,
      color: 'text-emerald-600',
      bgColor: 'bg-emerald-50 dark:bg-emerald-950/30',
    },
    {
      title: 'Fulfillment Rate',
      value: '94.2%',
      change: 2.1,
      changeLabel: 'vs last period',
      icon: <Truck className="w-5 h-5" />,
      color: 'text-purple-600',
      bgColor: 'bg-purple-50 dark:bg-purple-950/30',
    },
    {
      title: 'Inventory Value',
      value: '$8.7M',
      change: -1.2,
      changeLabel: 'vs last period',
      icon: <Warehouse className="w-5 h-5" />,
      color: 'text-amber-600',
      bgColor: 'bg-amber-50 dark:bg-amber-950/30',
    },
  ]

  const ordersOverTime: ChartData[] = [
    { label: 'Jan', value: 8200 },
    { label: 'Feb', value: 9100 },
    { label: 'Mar', value: 8800 },
    { label: 'Apr', value: 10200 },
    { label: 'May', value: 11500 },
    { label: 'Jun', value: 12100 },
    { label: 'Jul', value: 12847 },
  ]

  const revenueByCategory: ChartData[] = [
    { label: 'Electronics', value: 890000, color: 'bg-blue-500' },
    { label: 'Apparel', value: 620000, color: 'bg-purple-500' },
    { label: 'Home & Garden', value: 410000, color: 'bg-emerald-500' },
    { label: 'Sports', value: 280000, color: 'bg-amber-500' },
    { label: 'Other', value: 200000, color: 'bg-gray-400' },
  ]

  const topProducts = [
    { name: 'Wireless Headphones Pro', sku: 'WHP-001', orders: 1247, revenue: '$186,450' },
    { name: 'Smart Watch Series X', sku: 'SWX-002', orders: 983, revenue: '$294,900' },
    { name: 'USB-C Hub 7-in-1', sku: 'UCH-003', orders: 876, revenue: '$43,800' },
    { name: 'Ergonomic Keyboard', sku: 'EKB-004', orders: 654, revenue: '$78,480' },
    { name: '4K Webcam Ultra', sku: 'WC4-005', orders: 543, revenue: '$108,600' },
  ]

  const warehousePerformance = [
    { warehouse: 'East Coast DC', efficiency: 96, throughput: '2,340/day', status: 'optimal' },
    { warehouse: 'West Coast DC', efficiency: 89, throughput: '1,890/day', status: 'good' },
    { warehouse: 'Central Hub', efficiency: 78, throughput: '1,200/day', status: 'attention' },
    { warehouse: 'Southeast FC', efficiency: 92, throughput: '1,650/day', status: 'optimal' },
  ]

  const maxValue = Math.max(...ordersOverTime.map(d => d.value))
  const maxRevenue = Math.max(...revenueByCategory.map(d => d.value))

  return (
    <PermissionGate resource="analytics" action="view">
      <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Analytics Dashboard</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Performance overview and key metrics
          </p>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={timeRange}
            onChange={(e) => setTimeRange(e.target.value as any)}
            className="enterprise-select h-9 text-sm"
          >
            <option value="today">Today</option>
            <option value="7d">Last 7 days</option>
            <option value="30d">Last 30 days</option>
            <option value="90d">Last 90 days</option>
          </select>
          <button className="enterprise-btn enterprise-btn-ghost h-9 gap-2">
            <RefreshCw className="w-4 h-4" />
            Refresh
          </button>
          <button className="enterprise-btn enterprise-btn-primary h-9 gap-2">
            <Download className="w-4 h-4" />
            Export
          </button>
        </div>
      </div>

      {/* Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {metrics.map((metric) => (
          <div
            key={metric.title}
            className="enterprise-card p-4 hover:shadow-md transition-shadow"
          >
            <div className="flex items-start justify-between">
              <div className={clsx('p-2 rounded-lg', metric.bgColor)}>
                <span className={metric.color}>{metric.icon}</span>
              </div>
              <span className={clsx(
                'flex items-center gap-1 text-sm font-medium',
                metric.change >= 0 ? 'text-emerald-600' : 'text-red-600'
              )}>
                {metric.change >= 0 ? (
                  <ArrowUpRight className="w-4 h-4" />
                ) : (
                  <ArrowDownRight className="w-4 h-4" />
                )}
                {Math.abs(metric.change)}%
              </span>
            </div>
            <div className="mt-3">
              <p className="text-2xl font-bold text-[var(--text-primary)]">{metric.value}</p>
              <p className="text-sm text-[var(--text-secondary)] mt-1">{metric.title}</p>
            </div>
            <p className="text-xs text-[var(--text-tertiary)] mt-2">{metric.changeLabel}</p>
          </div>
        ))}
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Orders Over Time */}
        <div className="enterprise-card p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-semibold text-[var(--text-primary)]">Orders Over Time</h3>
            <span className="text-sm text-[var(--text-secondary)]">Monthly</span>
          </div>
          <div className="flex items-end gap-2 h-48">
            {ordersOverTime.map((item) => (
              <div key={item.label} className="flex-1 flex flex-col items-center gap-1">
                <div
                  className="w-full bg-gradient-to-t from-blue-600 to-blue-400 rounded-t-sm transition-all duration-500 hover:from-blue-700 hover:to-blue-500"
                  style={{ height: `${(item.value / maxValue) * 100}%` }}
                />
                <span className="text-xs text-[var(--text-tertiary)]">{item.label}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Revenue by Category */}
        <div className="enterprise-card p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-semibold text-[var(--text-primary)]">Revenue by Category</h3>
            <span className="text-sm text-[var(--text-secondary)]">Top 5</span>
          </div>
          <div className="space-y-3">
            {revenueByCategory.map((item) => (
              <div key={item.label}>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm text-[var(--text-primary)]">{item.label}</span>
                  <span className="text-sm font-medium text-[var(--text-secondary)]">
                    ${(item.value / 1000).toFixed(0)}K
                  </span>
                </div>
                <div className="h-2 bg-[var(--surface-sunken)] rounded-full overflow-hidden">
                  <div
                    className={clsx('h-full rounded-full transition-all duration-500', item.color)}
                    style={{ width: `${(item.value / maxRevenue) * 100}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Tables Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Top Products */}
        <div className="enterprise-card">
          <div className="px-5 py-4 border-b border-[var(--border-subtle)]">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-[var(--text-primary)]">Top Products</h3>
              <button className="text-sm text-[var(--nexus-primary-600)] hover:underline">
                View All
              </button>
            </div>
          </div>
          <div className="divide-y divide-[var(--border-subtle)]">
            {topProducts.map((product, idx) => (
              <div key={product.sku} className="px-5 py-3 hover:bg-[var(--interactive-hover)] transition-colors">
                <div className="flex items-center gap-3">
                  <span className="text-sm font-medium text-[var(--text-tertiary)] w-6">
                    #{idx + 1}
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-[var(--text-primary)] truncate">
                      {product.name}
                    </p>
                    <p className="text-xs text-[var(--text-tertiary)]">{product.sku}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-medium text-[var(--text-primary)]">{product.revenue}</p>
                    <p className="text-xs text-[var(--text-tertiary)]">{product.orders} orders</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Warehouse Performance */}
        <div className="enterprise-card">
          <div className="px-5 py-4 border-b border-[var(--border-subtle)]">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-[var(--text-primary)]">Warehouse Performance</h3>
              <button className="text-sm text-[var(--nexus-primary-600)] hover:underline">
                View All
              </button>
            </div>
          </div>
          <div className="divide-y divide-[var(--border-subtle)]">
            {warehousePerformance.map((wh) => (
              <div key={wh.warehouse} className="px-5 py-3 hover:bg-[var(--interactive-hover)] transition-colors">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-[var(--text-primary)]">{wh.warehouse}</p>
                    <p className="text-xs text-[var(--text-tertiary)]">{wh.throughput}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <div className="w-24">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-xs text-[var(--text-tertiary)]">{wh.efficiency}%</span>
                      </div>
                      <div className="h-1.5 bg-[var(--surface-sunken)] rounded-full overflow-hidden">
                        <div
                          className={clsx(
                            'h-full rounded-full transition-all duration-500',
                            wh.efficiency >= 90 ? 'bg-emerald-500' :
                            wh.efficiency >= 80 ? 'bg-amber-500' : 'bg-red-500'
                          )}
                          style={{ width: `${wh.efficiency}%` }}
                        />
                      </div>
                    </div>
                    <span className={clsx(
                      'px-2 py-0.5 rounded-full text-xs font-medium',
                      wh.status === 'optimal' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400' :
                      wh.status === 'good' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' :
                      'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
                    )}>
                      {wh.status}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
    </PermissionGate>
  )
}