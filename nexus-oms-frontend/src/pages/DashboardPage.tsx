import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ShoppingCart, Clock, Truck, CheckCircle, DollarSign, Users,
  TrendingUp, TrendingDown, AlertTriangle, Package, BarChart3, Activity, Plus, RefreshCw, Route,
} from 'lucide-react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  LineChart, Line, PieChart, Pie, Cell, Legend,
} from 'recharts'
import clsx from 'clsx'
import {
  EnterpriseKPICard,
  EnterpriseTimeline,
} from '../components/enterprise'
import type { TimelineEvent } from '../components/enterprise'
import * as analyticsApi from '../api/analytics'

interface VelocityPoint {
  hour: string
  orders: number
  fulfilled: number
}

interface AlertItem {
  id: string
  message: string
  severity: 'warning' | 'error' | 'info'
}

const PIE_COLORS = ['#2563EB', '#D97706', '#059669', '#DC2626']
const CHART_AXIS_STYLE = { fontSize: 11, fill: '#9CA3AF' }
const TOOLTIP_STYLE: React.CSSProperties = {
  backgroundColor: '#FFFFFF',
  border: '1px solid #E5E7EB',
  borderRadius: '8px',
  fontSize: '12px',
  boxShadow: '0 4px 6px -1px rgba(0,0,0,0.06)',
}

const PIE_CHART_DATA = [
  { name: 'Pending', value: 28, color: '#2563EB' },
  { name: 'Processing', value: 35, color: '#D97706' },
  { name: 'Shipped', value: 22, color: '#059669' },
  { name: 'Exceptions', value: 15, color: '#DC2626' },
]

const MOCK_EVENTS: TimelineEvent[] = [
  { id: 'e1', title: 'Order ORD-7852 shipped via FedEx', description: 'Tracking #FX-78412', timestamp: '2 min ago', status: 'completed' },
  { id: 'e2', title: 'Exception resolved for ORD-7842', timestamp: '5 min ago', status: 'completed' },
  { id: 'e3', title: 'Inventory adjusted: SKU WH-1002 (+50)', description: 'Bin A-12 restocked', timestamp: '8 min ago', status: 'current' },
  { id: 'e4', title: 'New order ORD-7853 received', description: 'Channel: Shopify', timestamp: '11 min ago', status: 'current' },
  { id: 'e5', title: 'Carrier UPS rate update applied', timestamp: '15 min ago', status: 'pending' },
  { id: 'e6', title: 'Batch fulfillment wave #W-342 completed', description: '42 orders fulfilled', timestamp: '20 min ago', status: 'completed' },
  { id: 'e7', title: 'RMA RMA-0089 approved for return', timestamp: '25 min ago', status: 'completed' },
  { id: 'e8', title: 'Low stock alert: SKU-PRO-X1', description: 'Only 12 remaining', timestamp: '30 min ago', status: 'error' },
]

const ALERTS: AlertItem[] = [
  { id: 'a1', message: 'Low stock: SKU-PRO-X1 (12 remaining)', severity: 'warning' },
  { id: 'a2', message: 'Carrier UPS service delay in Northeast region', severity: 'warning' },
  { id: 'a3', message: 'Payment pending: Order #10002458', severity: 'error' },
  { id: 'a4', message: 'Batch fulfillment #W-345 completed successfully', severity: 'info' },
]

const QUICK_ACTIONS = [
  { label: 'Create Order', icon: Plus, path: '/orders/create', desc: 'New sales order' },
  { label: 'View Inventory', icon: Package, path: '/inventory', desc: 'Stock levels' },
  { label: 'Check Analytics', icon: BarChart3, path: '/analytics', desc: 'Reports & insights' },
  { label: 'Open AI Assistant', icon: Activity, path: '/ai', desc: 'AI-powered help' },
  { label: 'AI Order Routing', icon: Route, path: '/order-routing', desc: 'Intelligent allocation' },
]

function generateMockVelocity(): VelocityPoint[] {
  return Array.from({ length: 24 }, (_, i) => ({
    hour: `${i.toString().padStart(2, '0')}:00`,
    orders: Math.floor(Math.random() * 80) + 10,
    fulfilled: Math.floor(Math.random() * 60) + 5,
  }))
}

export default function DashboardPage() {
  const navigate = useNavigate()

  const [rawKpis, setRawKpis] = useState<Record<string, any> | null>(null)
  const [velocity, setVelocity] = useState<VelocityPoint[]>([])
  const [chartTab, setChartTab] = useState<'24h' | '7d' | '30d'>('24h')
  const [activities] = useState<TimelineEvent[]>(MOCK_EVENTS)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [kpiRes, velocityRes] = await Promise.allSettled([
        analyticsApi.getDashboardKpis(),
        analyticsApi.getOrderVelocity(24),
      ])
      if (kpiRes.status === 'fulfilled') {
        setRawKpis(kpiRes.value.data || {})
      }
      if (velocityRes.status === 'fulfilled') {
        const vData = velocityRes.value.data
        if (Array.isArray(vData)) {
          setVelocity(vData as VelocityPoint[])
        } else if (vData?.velocity && Array.isArray(vData.velocity)) {
          setVelocity(vData.velocity)
        } else {
          setVelocity(generateMockVelocity())
        }
      } else {
        setVelocity(generateMockVelocity())
      }
    } catch {
      setError('Failed to load dashboard data')
      setVelocity(generateMockVelocity())
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchData() }, [fetchData])

  const summaryKpis = rawKpis as Record<string, any> | null
  const kpis = [
    { title: 'Orders Today', value: summaryKpis?.ordersToday ?? 1_284, icon: <ShoppingCart className="w-5 h-5" />, color: 'primary' as const, trend: 'up' as const, trendValue: '+12.5%', subtitle: 'vs yesterday' },
    { title: 'On-Time Delivery', value: summaryKpis?.onTimeDelivery ?? '97.2%', icon: <CheckCircle className="w-5 h-5" />, color: 'success' as const, trend: 'up' as const, trendValue: '+2.1%' },
    { title: 'Active Exceptions', value: summaryKpis?.activeExceptions ?? 23, icon: <AlertTriangle className="w-5 h-5" />, color: 'warning' as const, trend: 'down' as const, trendValue: '-8.3%' },
    { title: 'Avg Ship Time', value: summaryKpis?.avgShipTime ?? '4.2h', icon: <Clock className="w-5 h-5" />, color: 'info' as const, trend: 'down' as const, trendValue: '-12%' },
    { title: 'Revenue Today', value: `$${(summaryKpis?.revenueToday ?? 127_450).toLocaleString()}`, icon: <DollarSign className="w-5 h-5" />, color: 'success' as const, trend: 'up' as const, trendValue: '+8.7%' },
    { title: 'Active Pickers', value: summaryKpis?.activePickers ?? 18, icon: <Users className="w-5 h-5" />, color: 'ai' as const, trend: 'neutral' as const, trendValue: 'Stable' },
  ]

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="enterprise-page-header">
        <div>
          <h1>Dashboard</h1>
          <p>Real-time overview of your operations</p>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={fetchData} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm" disabled={loading}>
            <RefreshCw className={clsx('w-3.5 h-3.5', loading && 'animate-spin')} />
            Refresh
          </button>
        </div>
      </div>

      {/* KPI Grid */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        {kpis.map(kpi => (
          <EnterpriseKPICard
            key={kpi.title}
            title={kpi.title}
            value={loading ? '...' : kpi.value}
            icon={kpi.icon}
            color={loading ? 'primary' : kpi.color}
            trend={kpi.trend}
            trendValue={kpi.trendValue}
            subtitle={kpi.subtitle}
            loading={loading}
          />
        ))}
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Order Velocity */}
        <div className="lg:col-span-2 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-gray-100">Order Velocity</h3>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Orders and fulfillments over time</p>
            </div>
            <div className="flex gap-1 bg-gray-100 dark:bg-gray-700 rounded-lg p-0.5">
              {(['24h', '7d', '30d'] as const).map(tab => (
                <button
                  key={tab}
                  onClick={() => setChartTab(tab)}
                  className={clsx(
                    'px-3 py-1.5 text-xs font-medium rounded-md transition-all',
                    chartTab === tab
                      ? 'bg-white dark:bg-gray-600 text-gray-900 dark:text-gray-100 shadow-sm'
                      : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                  )}
                >
                  {tab === '24h' ? '24 Hours' : tab === '7d' ? '7 Days' : '30 Days'}
                </button>
              ))}
            </div>
          </div>
          <div className="p-6">
            <ResponsiveContainer width="100%" height={280}>
              <LineChart data={velocity} margin={{ top: 5, right: 20, left: -10, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
                <XAxis dataKey="hour" tick={CHART_AXIS_STYLE} />
                <YAxis tick={CHART_AXIS_STYLE} />
                <Tooltip contentStyle={TOOLTIP_STYLE} />
                <Line type="monotone" dataKey="orders" stroke="#2563EB" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
                <Line type="monotone" dataKey="fulfilled" stroke="#059669" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Order Status Distribution */}
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="font-semibold text-gray-900 dark:text-gray-100">Order Status</h3>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Current distribution</p>
          </div>
          <div className="p-6">
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={PIE_CHART_DATA} cx="50%" cy="50%" innerRadius={50} outerRadius={80} paddingAngle={2} dataKey="value">
                  {PIE_CHART_DATA.map((entry, i) => (
                    <Cell key={i} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip contentStyle={TOOLTIP_STYLE} />
              </PieChart>
            </ResponsiveContainer>
            <div className="flex flex-wrap justify-center gap-4 mt-2">
              {PIE_CHART_DATA.map((entry, i) => (
                <div key={i} className="flex items-center gap-1.5">
                  <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: entry.color }} />
                  <span className="text-xs text-gray-500 dark:text-gray-400">{entry.name}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Row: Activity + Alerts + Quick Actions */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Activity Timeline */}
        <div className="lg:col-span-2 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="font-semibold text-gray-900 dark:text-gray-100">Activity Feed</h3>
          </div>
          <div className="p-5">
            <EnterpriseTimeline events={activities} />
          </div>
        </div>

        {/* Right Column: Alerts + Quick Actions */}
        <div className="space-y-6">
          {/* Alerts */}
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-200 dark:border-gray-700">
              <h3 className="font-semibold text-gray-900 dark:text-gray-100">Alerts & Exceptions</h3>
            </div>
            <div className="p-5">
              {ALERTS.length === 0 ? (
                <div className="flex flex-col items-center py-8 text-gray-400 dark:text-gray-500">
                  <CheckCircle className="w-8 h-8 mb-2" />
                  <p className="text-sm">No active alerts</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {ALERTS.map(alert => (
                    <div key={alert.id} className="flex items-start gap-3 p-3 rounded-lg bg-gray-50 dark:bg-gray-700/50">
                      <div className={clsx(
                        'w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0',
                        alert.severity === 'warning' && 'bg-amber-50 text-amber-600 dark:bg-amber-900/20 dark:text-amber-400',
                        alert.severity === 'error' && 'bg-red-50 text-red-600 dark:bg-red-900/20 dark:text-red-400',
                        alert.severity === 'info' && 'bg-blue-50 text-blue-600 dark:bg-blue-900/20 dark:text-blue-400',
                      )}>
                        {alert.severity === 'error' ? (
                          <AlertTriangle className="w-3.5 h-3.5" />
                        ) : (
                          <AlertTriangle className="w-3.5 h-3.5" />
                        )}
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm text-gray-700 dark:text-gray-300">{alert.message}</p>
                        <p className="text-xs text-gray-400 dark:text-gray-500 mt-0.5 capitalize">{alert.severity}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Quick Actions */}
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-200 dark:border-gray-700">
              <h3 className="font-semibold text-gray-900 dark:text-gray-100">Quick Actions</h3>
            </div>
            <div className="p-5">
              <div className="grid grid-cols-2 gap-3">
                {QUICK_ACTIONS.map(action => (
                  <button
                    key={action.label}
                    onClick={() => navigate(action.path)}
                    className="flex flex-col items-center gap-2 p-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 hover:border-gray-300 dark:hover:border-gray-600 transition-all duration-150"
                  >
                    <div className="w-10 h-10 rounded-xl bg-primary-50 dark:bg-primary-900/20 flex items-center justify-center text-primary-600 dark:text-primary-400">
                      <action.icon className="w-5 h-5" />
                    </div>
                    <div className="text-center">
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{action.label}</p>
                      <p className="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{action.desc}</p>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
