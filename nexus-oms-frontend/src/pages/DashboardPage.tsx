import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  ShoppingCart, Clock, Truck, CheckCircle, DollarSign, Users,
  TrendingUp, TrendingDown, AlertTriangle, Package, BarChart3, Activity, Plus, RefreshCw, Route, Brain,
  Warehouse, ClipboardList, ArrowRight, Search, Building2, Percent, Play, XCircle, Shield, LayoutDashboard,
} from 'lucide-react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  LineChart, Line, PieChart, Pie, Cell, Legend,
} from 'recharts'
import clsx from 'clsx'
import {
  EnterpriseKPICard,
  EnterpriseTimeline,
  EnterpriseStatusBadge,
} from '../components/enterprise'
import type { TimelineEvent } from '../components/enterprise'
import * as analyticsApi from '../api/analytics'
import * as aiPlatformApi from '../api/aiPlatform'
import * as pickingApi from '../api/picking'
import * as ordersApi from '../api/orders'
import * as packingApi from '../api/packing'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'

interface VelocityPoint { hour: string; orders: number; fulfilled: number }
interface AlertItem { id: string; message: string; severity: 'warning' | 'error' | 'info' }

const PIE_COLORS = ['#2563EB', '#D97706', '#059669', '#DC2626']
const CHART_AXIS_STYLE = { fontSize: 11, fill: '#9CA3AF' }
const TOOLTIP_STYLE: React.CSSProperties = {
  backgroundColor: '#FFFFFF', border: '1px solid #E5E7EB', borderRadius: '8px', fontSize: '12px', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.06)',
}

const PIE_CHART_DATA = [
  { name: 'Pending', value: 28, color: '#2563EB' },
  { name: 'Processing', value: 35, color: '#D97706' },
  { name: 'Shipped', value: 22, color: '#059669' },
  { name: 'Exceptions', value: 15, color: '#DC2626' },
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

interface AiPrediction { predictedOrders: number; confidence: number; explanation: string }

export default function DashboardPage() {
  const navigate = useNavigate()
  const [rawKpis, setRawKpis] = useState<Record<string, any> | null>(null)
  const [velocity, setVelocity] = useState<VelocityPoint[]>([])
  const [chartTab, setChartTab] = useState<'24h' | '7d' | '30d'>('24h')
  const [activities, setActivities] = useState<TimelineEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const { addToast } = useToast()
  const [aiDemand, setAiDemand] = useState<AiPrediction | null>(null)
  const [aiInventory, setAiInventory] = useState<AiPrediction | null>(null)
  const [aiShipping, setAiShipping] = useState<AiPrediction | null>(null)
  const [facilitySearch, setFacilitySearch] = useState('')
  const [selectedFacility, setSelectedFacility] = useState<string | null>(null)
  const [facilityDimension, setFacilityDimension] = useState<'volume' | 'velocity' | 'partial'>('volume')

  // Fetch funnel data
  const { data: picklists = [] } = useQuery({
    queryKey: ['dashboard-picklists'],
    queryFn: async () => {
      const res = await pickingApi.getPicklists()
      const d = res.data; return Array.isArray(d) ? d : (d?.content ?? [])
    },
    refetchInterval: 30000,
  })

  const { data: shipments = [] } = useQuery({
    queryKey: ['dashboard-shipments-stats'],
    queryFn: async () => {
      const res = await packingApi.getPackages()
      const d = res.data; return Array.isArray(d) ? d : (d?.content ?? [])
    },
    refetchInterval: 30000,
  })

  // Fulfillment stage metrics
  const openPicklists = picklists.filter((p: any) => p.status === 'OPEN').length
  const inProgressPicklists = picklists.filter((p: any) => p.status === 'IN_PROGRESS').length
  const completedPicklists = picklists.filter((p: any) => p.status === 'COMPLETED').length
  const packedCount = shipments.filter((s: any) => s.status === 'PACKED').length
  const shippedCount = shipments.filter((s: any) => s.status === 'SHIPPED').length

  const totalOrders = openPicklists + inProgressPicklists + completedPicklists
  const fulfillmentStageMetrics = [
    { label: 'Assigned', value: openPicklists, percent: totalOrders > 0 ? Math.round(openPicklists / Math.max(totalOrders, 1) * 100) : 0, color: 'bg-blue-500' },
    { label: 'In Flight', value: inProgressPicklists, percent: totalOrders > 0 ? Math.round(inProgressPicklists / Math.max(totalOrders, 1) * 100) : 0, color: 'bg-amber-500' },
    { label: 'Packed & Shipped', value: completedPicklists, percent: totalOrders > 0 ? Math.round(completedPicklists / Math.max(totalOrders, 1) * 100) : 0, color: 'bg-green-500' },
  ]

  // Mock facilities data
  const facilities = [
    { id: 'fac-1', name: 'Main DC - Chicago', code: 'CHI-01', volume: 342, velocity: 28, partial: 12, fillRate: 94, pending: 18, oldestAssigned: '2h 15m' },
    { id: 'fac-2', name: 'East Coast Warehouse', code: 'NYC-01', volume: 256, velocity: 22, partial: 8, fillRate: 91, pending: 12, oldestAssigned: '1h 45m' },
    { id: 'fac-3', name: 'West Coast DC', code: 'LAX-01', volume: 198, velocity: 18, partial: 6, fillRate: 96, pending: 9, oldestAssigned: '3h 20m' },
    { id: 'fac-4', name: 'South Regional WH', code: 'ATL-01', volume: 145, velocity: 15, partial: 4, fillRate: 93, pending: 7, oldestAssigned: '45m' },
    { id: 'fac-5', name: 'Central Fulfillment', code: 'DFW-01', volume: 178, velocity: 20, partial: 5, fillRate: 95, pending: 11, oldestAssigned: '1h 10m' },
  ]

  const filteredFacilities = facilities.filter(f =>
    !facilitySearch || f.name.toLowerCase().includes(facilitySearch.toLowerCase()) || f.code.toLowerCase().includes(facilitySearch.toLowerCase())
  ).slice(0, 10)

  const selectedFacilityData = selectedFacility ? facilities.find(f => f.id === selectedFacility) : null

  const dimensionKey = facilityDimension === 'volume' ? 'volume' : facilityDimension === 'velocity' ? 'velocity' : 'partial'
  const maxMetric = Math.max(...filteredFacilities.map(f => f[dimensionKey as keyof typeof f] as number), 1)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [kpiRes, velocityRes] = await Promise.allSettled([
        analyticsApi.getDashboardKpis(),
        analyticsApi.getOrderVelocity(24),
      ])
      if (kpiRes.status === 'fulfilled') setRawKpis(kpiRes.value.data || {})
      if (velocityRes.status === 'fulfilled') {
        const vData = velocityRes.value.data
        if (Array.isArray(vData)) setVelocity(vData as VelocityPoint[])
        else if (vData?.velocity && Array.isArray(vData.velocity)) setVelocity(vData.velocity)
        else setVelocity(generateMockVelocity())
      } else setVelocity(generateMockVelocity())
    } catch {
      addToast({ type: 'error', title: 'Failed to load dashboard data' })
      setError('Failed to load dashboard data')
      setVelocity(generateMockVelocity())
    }
    try {
      const activityRes = await analyticsApi.getActivity()
      if (activityRes?.data && Array.isArray(activityRes.data)) setActivities(activityRes.data as TimelineEvent[])
    } catch { /* non-critical */ }
    finally { setLoading(false) }
    try {
      const [demandRes, inventoryRes, shippingRes] = await Promise.allSettled([
        aiPlatformApi.predict('DEMAND_FORECAST', { historicalAverage: 150 }),
        aiPlatformApi.predict('INVENTORY_OPTIMIZER', { avgDailyDemand: 10, leadTimeDays: 7 }),
        aiPlatformApi.predict('SHIPPING_PREDICTION', { avgDailyVolume: 200 }),
      ])
      setAiDemand(demandRes.status === 'fulfilled' ? {
        predictedOrders: (demandRes.value.data?.predictedOrders as number) ?? 182,
        confidence: (demandRes.value.data?.confidence as number) ?? 0.87,
        explanation: (demandRes.value.data?.explanation as string) ?? 'Based on historical trends and seasonality',
      } : { predictedOrders: 182, confidence: 0.87, explanation: 'Based on historical trends and seasonality' })
      setAiInventory(inventoryRes.status === 'fulfilled' ? {
        predictedOrders: (inventoryRes.value.data?.predictedOrders as number) ?? 12,
        confidence: (inventoryRes.value.data?.confidence as number) ?? 0.76,
        explanation: (inventoryRes.value.data?.explanation as string) ?? 'SKU-PRO-X1, SKU-BASIC-2K running low',
      } : { predictedOrders: 12, confidence: 0.76, explanation: 'SKU-PRO-X1, SKU-BASIC-2K running low' })
      setAiShipping(shippingRes.status === 'fulfilled' ? {
        predictedOrders: (shippingRes.value.data?.predictedOrders as number) ?? 245,
        confidence: (shippingRes.value.data?.confidence as number) ?? 0.91,
        explanation: (shippingRes.value.data?.explanation as string) ?? 'Expected 245 packages dispatched by 8 PM',
      } : { predictedOrders: 245, confidence: 0.91, explanation: 'Expected 245 packages dispatched by 8 PM' })
    } catch {
      setAiDemand({ predictedOrders: 182, confidence: 0.87, explanation: 'Based on historical trends and seasonality' })
      setAiInventory({ predictedOrders: 12, confidence: 0.76, explanation: 'SKU-PRO-X1, SKU-BASIC-2K running low' })
      setAiShipping({ predictedOrders: 245, confidence: 0.91, explanation: 'Expected 245 packages dispatched by 8 PM' })
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
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5"><LayoutDashboard className="w-7 h-7 text-primary-500" /> Dashboard</h1>
          <p>Real-time overview of your operations</p>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={fetchData} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm" disabled={loading}>
            <RefreshCw className={clsx('w-3.5 h-3.5', loading && 'animate-spin')} /> Refresh
          </button>
        </div>
      </div>

      {/* KPI Grid */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        {kpis.map(kpi => (
          <EnterpriseKPICard key={kpi.title} title={kpi.title} value={loading ? '...' : kpi.value}
            icon={kpi.icon} color={loading ? 'primary' : kpi.color} trend={kpi.trend}
            trendValue={kpi.trendValue} subtitle={kpi.subtitle} loading={loading} />
        ))}
      </div>

      {/* Fulfillment Progress Section */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        <div className="lg:col-span-3 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-gray-100">Fulfillment Progress</h3>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Pipeline overview across all facilities</p>
            </div>
            <div className="flex items-center gap-3 text-sm">
              <span className="text-gray-500 dark:text-gray-400">Total: {totalOrders} orders</span>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-6">
            {fulfillmentStageMetrics.map((m, i) => (
              <div key={m.label} className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-700 dark:text-gray-300">{m.label}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-lg font-bold text-gray-900 dark:text-gray-100">{m.value}</span>
                    <span className="text-xs text-gray-400 dark:text-gray-500">({m.percent}%)</span>
                  </div>
                </div>
                <div className="w-full h-2.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                  <div className={clsx('h-full rounded-full transition-all duration-500', m.color)} style={{ width: `${m.percent}%` }} />
                </div>
                <div className="flex items-center gap-1.5">
                  {i < 2 && <ArrowRight className="w-3.5 h-3.5 text-gray-300 dark:text-gray-600" />}
                  {i === 0 && <span className="text-[10px] text-blue-600 dark:text-blue-400 font-medium">Next: Picking</span>}
                  {i === 1 && <span className="text-[10px] text-amber-600 dark:text-amber-400 font-medium">Next: Pack & Ship</span>}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Task Queue Cards */}
        <div className="space-y-3">
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
            <h4 className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-3">Hold Tasks</h4>
            <div className="space-y-2">
              <button onClick={() => navigate('/orders?status=ON_HOLD')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-lg bg-amber-50 dark:bg-amber-900/20 flex items-center justify-center text-amber-600 dark:text-amber-400"><AlertTriangle className="w-3.5 h-3.5" /></div>
                  <span className="text-sm text-gray-700 dark:text-gray-300">Substitute Items</span>
                </div>
                <span className="text-xs font-semibold text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/20 px-2 py-0.5 rounded-full">3</span>
              </button>
              <button onClick={() => navigate('/orders?status=BAD_ADDRESS')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-lg bg-red-50 dark:bg-red-900/20 flex items-center justify-center text-red-600 dark:text-red-400"><XCircle className="w-3.5 h-3.5" /></div>
                  <span className="text-sm text-gray-700 dark:text-gray-300">Bad Address</span>
                </div>
                <span className="text-xs font-semibold text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 px-2 py-0.5 rounded-full">7</span>
              </button>
              <button onClick={() => navigate('/orders?status=FRAUD')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-lg bg-purple-50 dark:bg-purple-900/20 flex items-center justify-center text-purple-600 dark:text-purple-400"><Shield className="w-3.5 h-3.5" /></div>
                  <span className="text-sm text-gray-700 dark:text-gray-300">Fraud Risk</span>
                </div>
                <span className="text-xs font-semibold text-purple-600 dark:text-purple-400 bg-purple-50 dark:bg-purple-900/20 px-2 py-0.5 rounded-full">2</span>
              </button>
              <button onClick={() => navigate('/orders?status=ON_HOLD')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-lg bg-blue-50 dark:bg-blue-900/20 flex items-center justify-center text-blue-600 dark:text-blue-400"><Clock className="w-3.5 h-3.5" /></div>
                  <span className="text-sm text-gray-700 dark:text-gray-300">On Hold</span>
                </div>
                <span className="text-xs font-semibold text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20 px-2 py-0.5 rounded-full">5</span>
              </button>
            </div>
          </div>
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
            <h4 className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-3">Unbrokered</h4>
            <div className="space-y-2">
              <button onClick={() => navigate('/order-routing')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                <span className="text-sm text-gray-700 dark:text-gray-300">Brokering Queue</span>
                <span className="text-xs font-semibold text-gray-500 bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded-full">12</span>
              </button>
              <button onClick={() => navigate('/orders?status=PENDING')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                <span className="text-sm text-gray-700 dark:text-gray-300">Unallocated</span>
                <span className="text-xs font-semibold text-gray-500 bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded-full">8</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Facilities Drill-Down Section */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
          <div>
            <h3 className="font-semibold text-gray-900 dark:text-gray-100">Facilities Performance</h3>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Select a facility to view detailed metrics</p>
          </div>
          <div className="flex items-center gap-3">
            <Autocomplete value={facilitySearch} onChange={setFacilitySearch} placeholder="Search facilities..." minChars={0} inputClassName="text-xs py-1.5" />
            <div className="flex gap-1 bg-gray-100 dark:bg-gray-700 rounded-lg p-0.5">
              {(['volume', 'velocity', 'partial'] as const).map(d => (
                <button key={d} onClick={() => setFacilityDimension(d)}
                  className={clsx('px-3 py-1 text-xs font-medium rounded-md transition-all capitalize',
                    facilityDimension === d ? 'bg-white dark:bg-gray-600 text-gray-900 dark:text-gray-100 shadow-sm' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300')}>
                  {d === 'volume' ? 'Order Volume' : d === 'velocity' ? 'Velocity' : 'Partial Fulfillments'}
                </button>
              ))}
            </div>
          </div>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-5 divide-x divide-gray-200 dark:divide-gray-700">
          {/* Facilities List */}
          <div className="lg:col-span-2 max-h-[320px] overflow-y-auto p-2">
            {filteredFacilities.length === 0 ? (
              <p className="text-sm text-gray-400 dark:text-gray-500 p-4 text-center">No facilities found</p>
            ) : filteredFacilities.map(f => (
              <button key={f.id} onClick={() => setSelectedFacility(f.id)}
                className={clsx('w-full text-left p-3 rounded-lg transition-colors',
                  selectedFacility === f.id ? 'bg-primary-50 dark:bg-primary-900/20 border border-primary-200 dark:border-primary-800' : 'hover:bg-gray-50 dark:hover:bg-gray-700/50 border border-transparent')}>
                <div className="flex items-center gap-3">
                  <Building2 className={clsx('w-4 h-4', selectedFacility === f.id ? 'text-primary-600 dark:text-primary-400' : 'text-gray-400 dark:text-gray-500')} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{f.name}</p>
                    <p className="text-[10px] text-gray-400 dark:text-gray-500">{f.code}</p>
                  </div>
                  <span className="text-xs font-semibold text-gray-600 dark:text-gray-400">{f[dimensionKey as keyof typeof f]}</span>
                </div>
                {/* Mini progress bar */}
                <div className="mt-2 w-full h-1 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                  <div className="h-full bg-primary-500 rounded-full transition-all" style={{ width: `${(f[dimensionKey as keyof typeof f] as number) / maxMetric * 100}%` }} />
                </div>
              </button>
            ))}
          </div>

          {/* Selected Facility Details */}
          <div className="lg:col-span-3 p-6">
            {selectedFacilityData ? (
              <div className="space-y-5">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="text-lg font-semibold text-gray-900 dark:text-gray-100">{selectedFacilityData.name}</h4>
                    <p className="text-xs text-gray-500 dark:text-gray-400">Code: {selectedFacilityData.code}</p>
                  </div>
                  <div className="flex items-center gap-4">
                    <div className="text-center">
                      <p className="text-2xl font-bold text-green-600 dark:text-green-400">{selectedFacilityData.fillRate}%</p>
                      <p className="text-[10px] text-gray-400 dark:text-gray-500">Fill Rate</p>
                    </div>
                  </div>
                </div>

                {/* Fill rate + pending */}
                <div className="grid grid-cols-3 gap-4">
                  <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-700/50">
                    <p className="text-xs text-gray-500 dark:text-gray-400">Allocated</p>
                    <p className="text-lg font-bold text-gray-900 dark:text-gray-100 mt-1">{selectedFacilityData.volume}</p>
                  </div>
                  <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-700/50">
                    <p className="text-xs text-gray-500 dark:text-gray-400">Packed</p>
                    <p className="text-lg font-bold text-amber-600 dark:text-amber-400 mt-1">{Math.round(selectedFacilityData.volume * 0.7)}</p>
                  </div>
                  <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-700/50">
                    <p className="text-xs text-gray-500 dark:text-gray-400">Rejected</p>
                    <p className="text-lg font-bold text-red-600 dark:text-red-400 mt-1">{selectedFacilityData.partial}</p>
                  </div>
                </div>

                {/* Fulfillment Progress Bar */}
                <div>
                  <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">Fulfillment Progress</p>
                  <div className="flex h-6 rounded-lg overflow-hidden">
                    <div className="bg-green-500 text-white text-[10px] flex items-center justify-center font-medium" style={{ width: `${selectedFacilityData.fillRate}%` }}>
                      {selectedFacilityData.fillRate > 15 ? `Packed ${selectedFacilityData.fillRate}%` : ''}
                    </div>
                    <div className="bg-amber-400 text-white text-[10px] flex items-center justify-center font-medium" style={{ width: `${Math.max(5, 100 - selectedFacilityData.fillRate - 5)}%` }}>
                      {100 - selectedFacilityData.fillRate > 10 ? `Allocated ${100 - selectedFacilityData.fillRate}%` : ''}
                    </div>
                    <div className="bg-red-400 text-white text-[10px] flex items-center justify-center font-medium" style={{ width: '5%' }}>
                      {selectedFacilityData.partial > 0 ? `${selectedFacilityData.partial}` : ''}
                    </div>
                  </div>
                </div>

                {/* Pending Orders */}
                <div className="flex items-center justify-between p-3 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800">
                  <div className="flex items-center gap-2">
                    <Clock className="w-4 h-4 text-amber-600 dark:text-amber-400" />
                    <span className="text-sm text-amber-700 dark:text-amber-300">Orders Pending Fulfillment</span>
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-bold text-amber-700 dark:text-amber-300">{selectedFacilityData.pending}</p>
                    <p className="text-[10px] text-amber-600 dark:text-amber-400">Oldest: {selectedFacilityData.oldestAssigned}</p>
                  </div>
                </div>

                {/* Navigation buttons */}
                <div className="flex gap-2">
                  <button onClick={() => navigate(`/warehouse`)} className="enterprise-btn-secondary text-xs px-3 py-1.5">
                    <Building2 className="w-3.5 h-3.5" /> View Facility
                  </button>
                  <button onClick={() => navigate(`/orders?facility=${selectedFacilityData.code}`)} className="enterprise-btn-secondary text-xs px-3 py-1.5">
                    <Package className="w-3.5 h-3.5" /> View Orders
                  </button>
                </div>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center h-48 text-gray-400 dark:text-gray-500">
                <Building2 className="w-10 h-10 mb-3" />
                <p className="text-sm font-medium">Select a facility</p>
                <p className="text-xs mt-1">Choose a facility from the list to view details</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-gray-100">Order Velocity</h3>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Orders and fulfillments over time</p>
            </div>
            <div className="flex gap-1 bg-gray-100 dark:bg-gray-700 rounded-lg p-0.5">
              {(['24h', '7d', '30d'] as const).map(tab => (
                <button key={tab} onClick={() => setChartTab(tab)}
                  className={clsx('px-3 py-1.5 text-xs font-medium rounded-md transition-all',
                    chartTab === tab ? 'bg-white dark:bg-gray-600 text-gray-900 dark:text-gray-100 shadow-sm' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300')}>
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

        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="font-semibold text-gray-900 dark:text-gray-100">Order Status</h3>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Current distribution</p>
          </div>
          <div className="p-6">
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={PIE_CHART_DATA} cx="50%" cy="50%" innerRadius={50} outerRadius={80} paddingAngle={2} dataKey="value">
                  {PIE_CHART_DATA.map((entry, i) => <Cell key={i} fill={entry.color} />)}
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
        <div className="lg:col-span-2 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="font-semibold text-gray-900 dark:text-gray-100">Activity Feed</h3>
          </div>
          <div className="p-5">
            <EnterpriseTimeline events={activities} />
          </div>
        </div>

        <div className="space-y-6">
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
                      <div className={clsx('w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0',
                        alert.severity === 'warning' && 'bg-amber-50 text-amber-600 dark:bg-amber-900/20 dark:text-amber-400',
                        alert.severity === 'error' && 'bg-red-50 text-red-600 dark:bg-red-900/20 dark:text-red-400',
                        alert.severity === 'info' && 'bg-blue-50 text-blue-600 dark:bg-blue-900/20 dark:text-blue-400')}>
                        <AlertTriangle className="w-3.5 h-3.5" />
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

          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-200 dark:border-gray-700">
              <h3 className="font-semibold text-gray-900 dark:text-gray-100">Quick Actions</h3>
            </div>
            <div className="p-5">
              <div className="grid grid-cols-2 gap-3">
                {QUICK_ACTIONS.map(action => (
                  <button key={action.label} onClick={() => navigate(action.path)}
                    className="flex flex-col items-center gap-2 p-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 hover:border-gray-300 dark:hover:border-gray-600 transition-all duration-150">
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

      {/* AI Insights */}
      <div className="mt-6">
        <h3 className="text-lg font-semibold mb-3 flex items-center gap-2">
          <Brain className="w-5 h-5 text-purple-600" /> AI Insights
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="bg-purple-50 border border-purple-200 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <TrendingUp className="w-4 h-4 text-purple-600" />
              <h4 className="font-medium text-sm text-purple-900">Demand Forecast</h4>
            </div>
            <p className="text-2xl font-bold text-purple-700">{aiDemand?.predictedOrders ?? '...'}</p>
            <div className="mt-2">
              <div className="flex justify-between text-xs text-purple-600 mb-1">
                <span>Confidence</span>
                <span>{Math.round((aiDemand?.confidence ?? 0) * 100)}%</span>
              </div>
              <div className="w-full bg-purple-200 rounded-full h-1.5">
                <div className="bg-purple-600 h-1.5 rounded-full" style={{ width: `${Math.round((aiDemand?.confidence ?? 0) * 100)}%` }} />
              </div>
            </div>
            <p className="text-xs text-purple-700 mt-2">{aiDemand?.explanation ?? 'Loading...'}</p>
          </div>

          <div className="bg-purple-50 border border-purple-200 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Package className="w-4 h-4 text-purple-600" />
              <h4 className="font-medium text-sm text-purple-900">Inventory Alert</h4>
            </div>
            <p className="text-2xl font-bold text-purple-700">{aiInventory?.predictedOrders ?? '...'} items</p>
            <div className="mt-2">
              <div className="flex justify-between text-xs text-purple-600 mb-1">
                <span>Confidence</span>
                <span>{Math.round((aiInventory?.confidence ?? 0) * 100)}%</span>
              </div>
              <div className="w-full bg-purple-200 rounded-full h-1.5">
                <div className="bg-purple-600 h-1.5 rounded-full" style={{ width: `${Math.round((aiInventory?.confidence ?? 0) * 100)}%` }} />
              </div>
            </div>
            <p className="text-xs text-purple-700 mt-2">{aiInventory?.explanation ?? 'Loading...'}</p>
          </div>

          <div className="bg-purple-50 border border-purple-200 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Truck className="w-4 h-4 text-purple-600" />
              <h4 className="font-medium text-sm text-purple-900">Shipping Prediction</h4>
            </div>
            <p className="text-2xl font-bold text-purple-700">{aiShipping?.predictedOrders ?? '...'}</p>
            <div className="mt-2">
              <div className="flex justify-between text-xs text-purple-600 mb-1">
                <span>Confidence</span>
                <span>{Math.round((aiShipping?.confidence ?? 0) * 100)}%</span>
              </div>
              <div className="w-full bg-purple-200 rounded-full h-1.5">
                <div className="bg-purple-600 h-1.5 rounded-full" style={{ width: `${Math.round((aiShipping?.confidence ?? 0) * 100)}%` }} />
              </div>
            </div>
            <p className="text-xs text-purple-700 mt-2">{aiShipping?.explanation ?? 'Loading...'}</p>
          </div>
        </div>
      </div>
    </div>
  )
}
