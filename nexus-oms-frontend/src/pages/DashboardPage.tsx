import { useState, useEffect, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  ShoppingCart, Clock, Truck, CheckCircle, DollarSign, Users,
  TrendingUp, AlertTriangle, Package, BarChart3, Activity, Plus, RefreshCw, Route, Brain,
  Warehouse, ArrowRight, Building2, Percent, XCircle, Shield, LayoutDashboard,
} from 'lucide-react'
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer,
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
import * as packingApi from '../api/packing'
import * as inventoryApi from '../api/inventory'
import promotionsApi from '../api/promotions'
import endlessAisleApi from '../api/endlessAisle'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import { useToast } from '../hooks/useToast'

interface AlertItem { id: string; message: string; severity: 'warning' | 'error' | 'info' }
interface FacilityData { id: string; name: string; code: string; city: string; status: string; capacityUtilization: number; totalBins: number; emptyBins: number; activeStaff: number }
interface PieChartData { name: string; value: number; color: string }

const PIE_COLORS = ['var(--nexus-primary-500)', 'var(--nexus-warning-500)', 'var(--nexus-success-500)', 'var(--nexus-error-500)', 'var(--nexus-ai-500)', '#EC4899', 'var(--nexus-primary-500)', 'var(--text-tertiary)']
const TOOLTIP_STYLE: React.CSSProperties = {
  backgroundColor: 'var(--chart-tooltip-bg)', border: '1px solid var(--chart-tooltip-border)', borderRadius: 'var(--radius-lg)', fontSize: 'var(--text-xs)', boxShadow: 'var(--elevation-3)',
}

const QUICK_ACTIONS = [
  { label: 'Create Order', icon: Plus, path: '/orders/new', desc: 'New sales order' },
  { label: 'View Inventory', icon: Package, path: '/inventory', desc: 'Stock levels' },
  { label: 'Check Analytics', icon: BarChart3, path: '/analytics', desc: 'Reports & insights' },
  { label: 'Open AI Assistant', icon: Activity, path: '/ai', desc: 'AI-powered help' },
  { label: 'AI Order Routing', icon: Route, path: '/order-routing', desc: 'Intelligent allocation' },
]

interface AiPrediction { predictedOrders: number; confidence: number; explanation: string }

export default function DashboardPage() {
  const navigate = useNavigate()
  const [rawKpis, setRawKpis] = useState<Record<string, any> | null>(null)
  const [velocityRate, setVelocityRate] = useState<number | null>(null)
  const [activities, setActivities] = useState<TimelineEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const { addToast } = useToast()
  const [aiDemand, setAiDemand] = useState<AiPrediction | null>(null)
  const [aiInventory, setAiInventory] = useState<AiPrediction | null>(null)
  const [aiShipping, setAiShipping] = useState<AiPrediction | null>(null)
  const [facilitySearch, setFacilitySearch] = useState('')
  const [selectedFacility, setSelectedFacility] = useState<string | null>(null)
  const [alerts, setAlerts] = useState<AlertItem[]>([])
  const [pieChartData, setPieChartData] = useState<PieChartData[]>([])
  const [facilities, setFacilities] = useState<FacilityData[]>([])
  const [taskQueue, setTaskQueue] = useState<any>(null)
  const [promotionStats, setPromotionStats] = useState<{ activeCount: number; totalUses: number; totalDiscount: number; topPromos: any[] }>({ activeCount: 0, totalUses: 0, totalDiscount: 0, topPromos: [] })
  const [endlessAisleStats, setEndlessAisleStats] = useState<{ pendingOrders: number; inTransit: number; delivered: number; cancelled: number; totalRevenue: number }>({ pendingOrders: 0, inTransit: 0, delivered: 0, cancelled: 0, totalRevenue: 0 })

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

  const { data: inventoryItems = [] } = useQuery({
    queryKey: ['dashboard-inventory-health'],
    queryFn: async () => {
      const res = await inventoryApi.getInventory()
      const d = res.data; return Array.isArray(d) ? d : (d?.content ?? [])
    },
    refetchInterval: 60000,
  })

  const inventoryHealth = useMemo(() => {
    let healthy = 0, low = 0, critical = 0, outOfStock = 0
    for (const item of inventoryItems as any[]) {
      const onHand = item.quantityOnHand ?? 0
      const allocated = item.quantityAllocated ?? 0
      const reserved = item.quantityReserved ?? 0
      const atp = item.atp ?? (onHand - allocated - reserved)
      const safetyStock = item.safetyStock ?? 0
      const reorderPoint = item.reorderPoint ?? 0
      if (onHand <= 0) outOfStock++
      else if (atp <= safetyStock) critical++
      else if (atp < reorderPoint) low++
      else healthy++
    }
    return { healthy, low, critical, outOfStock }
  }, [inventoryItems])

  // Fulfillment stage metrics
  const openPicklists = picklists.filter((p: any) => p.status === 'OPEN').length
  const inProgressPicklists = picklists.filter((p: any) => p.status === 'IN_PROGRESS').length
  const completedPicklists = picklists.filter((p: any) => p.status === 'COMPLETED').length
  const packedCount = shipments.filter((s: any) => s.status === 'PACKED').length
  const shippedCount = shipments.filter((s: any) => s.status === 'SHIPPED').length

  const totalOrders = openPicklists + inProgressPicklists + completedPicklists
  const fulfillmentStageMetrics = [
    { label: 'Assigned', value: openPicklists, percent: totalOrders > 0 ? Math.round(openPicklists / Math.max(totalOrders, 1) * 100) : 0, color: 'bg-[var(--nexus-primary-600)]' },
    { label: 'In Flight', value: inProgressPicklists, percent: totalOrders > 0 ? Math.round(inProgressPicklists / Math.max(totalOrders, 1) * 100) : 0, color: 'bg-[var(--nexus-warning-500)]' },
    { label: 'Packed & Shipped', value: completedPicklists, percent: totalOrders > 0 ? Math.round(completedPicklists / Math.max(totalOrders, 1) * 100) : 0, color: 'bg-[var(--nexus-success-600)]' },
  ]

  const filteredFacilities = facilities.filter(f =>
    !facilitySearch || f.name.toLowerCase().includes(facilitySearch.toLowerCase()) || f.code.toLowerCase().includes(facilitySearch.toLowerCase())
  ).slice(0, 10)

  const selectedFacilityData = selectedFacility ? facilities.find(f => f.id === selectedFacility) : null

  const fetchData = useCallback(async () => {
    setError(null)
    setLoading(true)
    try {
      // Fire ALL critical + non-critical calls in parallel
      const [
        kpiRes, velocityRes, alertsRes, statusDistRes, taskQueueRes, facilitiesRes,
        activityRes, promosRes, eaRes,
      ] = await Promise.allSettled([
        analyticsApi.getDashboardKpis(),
        analyticsApi.getOrderVelocity(24),
        analyticsApi.getAlerts(),
        analyticsApi.getOrderStatusDistribution(),
        analyticsApi.getTaskQueueSummary(),
        analyticsApi.getWarehousesSummary(),
        analyticsApi.getActivity(),
        promotionsApi.getPromotions(),
        endlessAisleApi.getEndlessAisleOrders(),
      ])

      // Critical data
      if (kpiRes.status === 'fulfilled') setRawKpis(kpiRes.value.data || {})
      if (velocityRes.status === 'fulfilled') {
        const vData = velocityRes.value.data
        setVelocityRate(typeof vData?.value === 'number' ? vData.value : null)
      } else setVelocityRate(null)
      if (alertsRes.status === 'fulfilled' && Array.isArray(alertsRes.value.data)) {
        setAlerts(alertsRes.value.data as AlertItem[])
      }
      if (statusDistRes.status === 'fulfilled' && Array.isArray(statusDistRes.value.data)) {
        const distData = statusDistRes.value.data as any[]
        setPieChartData(distData.map((item, i) => ({
          name: item.name, value: item.value, color: PIE_COLORS[i % PIE_COLORS.length]
        })))
      } else {
        setPieChartData([
          { name: 'Pending', value: 0, color: 'var(--nexus-primary-500)' },
          { name: 'Processing', value: 0, color: 'var(--nexus-warning-500)' },
          { name: 'Shipped', value: 0, color: 'var(--nexus-success-500)' },
          { name: 'Exceptions', value: 0, color: 'var(--nexus-error-500)' },
        ])
      }
      if (taskQueueRes.status === 'fulfilled') setTaskQueue(taskQueueRes.value.data)
      if (facilitiesRes.status === 'fulfilled' && Array.isArray(facilitiesRes.value.data)) {
        setFacilities(facilitiesRes.value.data as FacilityData[])
      }

      // Activity (non-critical)
      if (activityRes.status === 'fulfilled') {
        const aData = activityRes.value.data
        if (aData && Array.isArray(aData)) setActivities(aData as TimelineEvent[])
      }

      // Promotions & Endless Aisle (non-critical)
      if (promosRes.status === 'fulfilled') {
        const list = Array.isArray(promosRes.value.data) ? promosRes.value.data : (promosRes.value.data?.content ?? [])
        const active = list.filter((p: any) => p.active !== false)
        setPromotionStats({
          activeCount: active.length,
          totalUses: active.reduce((s: number, p: any) => s + (p.currentUses ?? 0), 0),
          totalDiscount: active.reduce((s: number, p: any) => s + ((p.currentUses ?? 0) * (p.discountValue ?? 0)), 0),
          topPromos: active.slice(0, 5),
        })
      }
      if (eaRes.status === 'fulfilled') {
        const eaList = Array.isArray(eaRes.value.data) ? eaRes.value.data : (eaRes.value.data?.content ?? [])
        setEndlessAisleStats({
          pendingOrders: eaList.filter((o: any) => o.status === 'PENDING').length,
          inTransit: eaList.filter((o: any) => o.status === 'IN_TRANSIT').length,
          delivered: eaList.filter((o: any) => o.status === 'DELIVERED').length,
          cancelled: eaList.filter((o: any) => o.status === 'CANCELLED').length,
          totalRevenue: eaList.reduce((s: number, o: any) => s + (o.orderTotal ?? 0), 0),
        })
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to load dashboard data' })
      setError('Failed to load dashboard data')
      setVelocityRate(null)
    } finally {
      setLoading(false)
    }

    // AI predictions — fire-and-forget, non-blocking; inputs driven by live metrics
    const observedKpis = kpiRes.status === 'fulfilled' ? (kpiRes.value.data ?? {}) : {}
    const observedOrders = Number(observedKpis.ordersToday ?? 0)
    Promise.allSettled([
      aiPlatformApi.predict('DEMAND_FORECAST', { historicalAverage: observedOrders }),
      aiPlatformApi.predict('INVENTORY_OPTIMIZER', { avgDailyDemand: Math.max(1, Math.round(observedOrders / 30)), leadTimeDays: 7 }),
      aiPlatformApi.predict('SHIPPING_PREDICTION', { avgDailyVolume: observedOrders }),
    ]).then(([demandRes, inventoryRes, shippingRes]) => {
      setAiDemand(demandRes.status === 'fulfilled' ? {
        predictedOrders: (demandRes.value.data?.predictedOrders as number) ?? 0,
        confidence: (demandRes.value.data?.confidence as number) ?? 0,
        explanation: (demandRes.value.data?.explanation as string) ?? 'Prediction unavailable for this model.',
      } : null)
      setAiInventory(inventoryRes.status === 'fulfilled' ? {
        predictedOrders: (inventoryRes.value.data?.predictedOrders as number) ?? 0,
        confidence: (inventoryRes.value.data?.confidence as number) ?? 0,
        explanation: (inventoryRes.value.data?.explanation as string) ?? 'Prediction unavailable for this model.',
      } : null)
      setAiShipping(shippingRes.status === 'fulfilled' ? {
        predictedOrders: (shippingRes.value.data?.predictedOrders as number) ?? 0,
        confidence: (shippingRes.value.data?.confidence as number) ?? 0,
        explanation: (shippingRes.value.data?.explanation as string) ?? 'Prediction unavailable for this model.',
      } : null)
    }).catch(() => {
      setAiDemand(null)
      setAiInventory(null)
      setAiShipping(null)
    })
  }, [])

  useEffect(() => { fetchData() }, [fetchData])

  // Auto-refresh every 60 seconds for real-time ops visibility
  useEffect(() => {
    const interval = setInterval(() => {
      if (!document.hidden) {
        fetchData()
      }
    }, 60_000)
    return () => clearInterval(interval)
  }, [fetchData])

  const summaryKpis = rawKpis as Record<string, any> | null
  const kpis = [
    { title: 'Orders Today', value: summaryKpis?.ordersToday ?? 0, icon: <ShoppingCart className="w-5 h-5" />, color: 'primary' as const, subtitle: 'Orders created today' },
    { title: 'On-Time Delivery', value: summaryKpis?.onTimeDelivery ?? '—', icon: <CheckCircle className="w-5 h-5" />, color: 'success' as const, subtitle: 'Share delivered on time' },
    { title: 'Active Exceptions', value: summaryKpis?.activeExceptions ?? 0, icon: <AlertTriangle className="w-5 h-5" />, color: 'warning' as const, subtitle: 'Open fulfillment exceptions' },
    { title: 'Avg Ship Time', value: summaryKpis?.avgShipTime ?? '—', icon: <Clock className="w-5 h-5" />, color: 'info' as const, subtitle: 'Average time to ship' },
    { title: 'Revenue Today', value: `$${(summaryKpis?.revenueToday ?? 0).toLocaleString()}`, icon: <DollarSign className="w-5 h-5" />, color: 'success' as const, subtitle: 'Sales value today' },
    { title: 'Active Pickers', value: summaryKpis?.activePickers ?? 0, icon: <Users className="w-5 h-5" />, color: 'ai' as const, subtitle: 'Pick users online' },
  ]

  return (
    <PermissionGate resource="analytics" action="view">
      <div className="space-y-6">
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5"><LayoutDashboard className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Dashboard</h1>
          <p>Real-time overview of your operations</p>
        </div>
        <div className="flex items-center gap-3">
          <button type="button" onClick={fetchData} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm" disabled={loading}>
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
        <div className="lg:col-span-3 bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-semibold text-[var(--text-primary)]">Fulfillment Progress</h3>
              <p className="text-xs text-[var(--text-secondary)] mt-0.5">Pipeline overview across all facilities</p>
            </div>
            <div className="flex items-center gap-3 text-sm">
              <span className="text-[var(--text-secondary)]">Total: {totalOrders} orders</span>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-6">
            {fulfillmentStageMetrics.map((m, i) => (
              <div key={m.label} className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-[var(--text-secondary)]">{m.label}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-lg font-bold text-[var(--text-primary)]">{m.value}</span>
                    <span className="text-xs text-[var(--text-tertiary)]">({m.percent}%)</span>
                  </div>
                </div>
                <div className="w-full h-2.5 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                  <div className={clsx('h-full rounded-full transition-all duration-500', m.color)} style={{ width: `${m.percent}%` }} />
                </div>
                <div className="flex items-center gap-1.5">
                  {i < 2 && <ArrowRight className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />}
                  {i === 0 && <span className="text-[10px] text-[var(--nexus-primary-600)] font-medium">Next: Picking</span>}
                  {i === 1 && <span className="text-[10px] text-[var(--nexus-warning-600)] font-medium">Next: Pack & Ship</span>}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Task Queue Cards */}
        <div className="space-y-3">
          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-4">
            <h4 className="text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider mb-3">Hold Tasks</h4>
            <div className="space-y-2">
              <button type="button" onClick={() => navigate('/orders?status=ON_HOLD')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-[var(--interactive-hover)] transition-colors">
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-lg bg-[var(--nexus-warning-50)] flex items-center justify-center text-[var(--nexus-warning-600)]"><AlertTriangle className="w-3.5 h-3.5" /></div>
                  <span className="text-sm text-[var(--text-secondary)]">Substitute Items</span>
                </div>
                <span className="text-xs font-semibold text-[var(--nexus-warning-600)] bg-[var(--nexus-warning-50)] px-2 py-0.5 rounded-full">{taskQueue?.holdTasks?.substituteItems ?? 0}</span>
              </button>
              <button type="button" onClick={() => navigate('/orders?status=BAD_ADDRESS')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-[var(--interactive-hover)] transition-colors">
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-lg bg-[var(--nexus-error-50)] flex items-center justify-center text-[var(--nexus-error-600)]"><XCircle className="w-3.5 h-3.5" /></div>
                  <span className="text-sm text-[var(--text-secondary)]">Bad Address</span>
                </div>
                <span className="text-xs font-semibold text-[var(--nexus-error-600)] bg-[var(--nexus-error-50)] px-2 py-0.5 rounded-full">{taskQueue?.holdTasks?.badAddress ?? 0}</span>
              </button>
              <button type="button" onClick={() => navigate('/orders?status=FRAUD')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-[var(--interactive-hover)] transition-colors">
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-lg bg-[var(--nexus-ai-50)] flex items-center justify-center text-[var(--nexus-ai-600)]"><Shield className="w-3.5 h-3.5" /></div>
                  <span className="text-sm text-[var(--text-secondary)]">Fraud Risk</span>
                </div>
                <span className="text-xs font-semibold text-[var(--nexus-ai-600)] bg-[var(--nexus-ai-50)] px-2 py-0.5 rounded-full">{taskQueue?.holdTasks?.fraudRisk ?? 0}</span>
              </button>
              <button type="button" onClick={() => navigate('/orders?status=ON_HOLD')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-[var(--interactive-hover)] transition-colors">
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-lg bg-[var(--nexus-primary-50)] flex items-center justify-center text-[var(--nexus-primary-600)]"><Clock className="w-3.5 h-3.5" /></div>
                  <span className="text-sm text-[var(--text-secondary)]">On Hold</span>
                </div>
                <span className="text-xs font-semibold text-[var(--nexus-primary-600)] bg-[var(--nexus-primary-50)] px-2 py-0.5 rounded-full">{taskQueue?.holdTasks?.onHold ?? 0}</span>
              </button>
            </div>
          </div>
          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-4">
            <h4 className="text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider mb-3">Unbrokered</h4>
            <div className="space-y-2">
              <button type="button" onClick={() => navigate('/order-routing')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-[var(--interactive-hover)] transition-colors">
                <span className="text-sm text-[var(--text-secondary)]">Brokering Queue</span>
                <span className="text-xs font-semibold text-[var(--text-secondary)] bg-[var(--surface-muted)] px-2 py-0.5 rounded-full">{taskQueue?.unbrokered?.brokeringQueue ?? 0}</span>
              </button>
              <button type="button" onClick={() => navigate('/orders?status=PENDING')} className="w-full flex items-center justify-between p-2.5 rounded-lg hover:bg-[var(--interactive-hover)] transition-colors">
                <span className="text-sm text-[var(--text-secondary)]">Unallocated</span>
                <span className="text-xs font-semibold text-[var(--text-secondary)] bg-[var(--surface-muted)] px-2 py-0.5 rounded-full">{taskQueue?.unbrokered?.unallocated ?? 0}</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Facilities Drill-Down Section */}
      <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
        <div className="px-6 py-4 border-b border-[var(--border-default)] flex items-center justify-between">
          <div>
            <h3 className="font-semibold text-[var(--text-primary)]">Facilities Performance</h3>
            <p className="text-xs text-[var(--text-secondary)] mt-0.5">Bin capacity utilization by facility</p>
          </div>
          <div className="flex items-center gap-3">
            <Autocomplete value={facilitySearch} onChange={setFacilitySearch} placeholder="Search facilities..." minChars={0} inputClassName="text-xs py-1.5" />
          </div>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-5 divide-x divide-[var(--border-default)]">
          {/* Facilities List */}
          <div className="lg:col-span-2 max-h-[320px] overflow-y-auto p-2">
            {filteredFacilities.length === 0 ? (
              <p className="text-sm text-[var(--text-tertiary)] p-4 text-center">No facilities found</p>
            ) : filteredFacilities.map(f => (
              <button type="button" key={f.id} onClick={() => setSelectedFacility(f.id)}
                className={clsx('w-full text-left p-3 rounded-lg transition-colors',
                  selectedFacility === f.id ? 'bg-[var(--interactive-selected)] border border-[var(--nexus-primary-200)]' : 'hover:bg-[var(--interactive-hover)] border border-transparent')}>
                <div className="flex items-center gap-3">
                  <Building2 className={clsx('w-4 h-4', selectedFacility === f.id ? 'text-[var(--text-brand)]' : 'text-[var(--text-tertiary)]')} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-[var(--text-primary)] truncate">{f.name}</p>
                    <p className="text-[10px] text-[var(--text-tertiary)]">{f.code}{f.city ? ` · ${f.city}` : ''}</p>
                  </div>
                  <span className="text-xs font-semibold text-[var(--text-secondary)]">{f.capacityUtilization ?? 0}%</span>
                </div>
                {/* Mini progress bar */}
                <div className="mt-2 w-full h-1 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                  <div className="h-full bg-[var(--nexus-primary-600)] rounded-full transition-all" style={{ width: `${Math.min(100, (f.capacityUtilization ?? 0))}%` }} />
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
                    <h4 className="text-lg font-semibold text-[var(--text-primary)]">{selectedFacilityData.name}</h4>
                    <p className="text-xs text-[var(--text-secondary)]">Code: {selectedFacilityData.code}</p>
                  </div>
                  <div className="flex items-center gap-4">
                    <div className="text-center">
                      <p className="text-2xl font-bold text-[var(--nexus-success-600)]">{selectedFacilityData.capacityUtilization ?? 0}%</p>
                      <p className="text-[10px] text-[var(--text-tertiary)]">Bin Utilization</p>
                    </div>
                  </div>
                </div>

                {/* Capacity + staff */}
                <div className="grid grid-cols-3 gap-4">
                  <div className="p-3 rounded-lg bg-[var(--surface-muted)]">
                    <p className="text-xs text-[var(--text-secondary)]">Total Bins</p>
                    <p className="text-lg font-bold text-[var(--text-primary)] mt-1">{selectedFacilityData.totalBins ?? 0}</p>
                  </div>
                  <div className="p-3 rounded-lg bg-[var(--surface-muted)]">
                    <p className="text-xs text-[var(--text-secondary)]">Empty Bins</p>
                    <p className="text-lg font-bold text-[var(--nexus-warning-600)] mt-1">{selectedFacilityData.emptyBins ?? 0}</p>
                  </div>
                  <div className="p-3 rounded-lg bg-[var(--surface-muted)]">
                    <p className="text-xs text-[var(--text-secondary)]">Active Staff</p>
                    <p className="text-lg font-bold text-[var(--nexus-primary-600)] mt-1">{selectedFacilityData.activeStaff ?? 0}</p>
                  </div>
                </div>

                {/* Capacity Progress Bar */}
                <div>
                  <p className="text-xs font-medium text-[var(--text-secondary)] mb-2">Capacity Utilization</p>
                  <div className="w-full h-6 rounded-lg bg-[var(--surface-muted)] overflow-hidden">
                    <div className={clsx(
                      'h-full rounded-lg flex items-center justify-center text-[10px] text-white font-medium transition-all',
                      (selectedFacilityData.capacityUtilization ?? 0) >= 90 ? 'bg-[var(--nexus-error-500)]' :
                      (selectedFacilityData.capacityUtilization ?? 0) >= 75 ? 'bg-[var(--nexus-warning-500)]' : 'bg-[var(--nexus-success-600)]'
                    )} style={{ width: `${Math.min(100, selectedFacilityData.capacityUtilization ?? 0)}%` }}>
                      {selectedFacilityData.capacityUtilization > 15 ? `${selectedFacilityData.capacityUtilization}%` : ''}
                    </div>
                  </div>
                </div>

                {/* Facility Status */}
                <div className="flex items-center justify-between p-3 rounded-lg bg-[var(--surface-muted)] border border-[var(--border-default)]">
                  <div className="flex items-center gap-2">
                    <Building2 className="w-4 h-4 text-[var(--nexus-primary-600)]" />
                    <span className="text-sm text-[var(--text-secondary)]">Facility Status</span>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-semibold text-[var(--text-primary)] capitalize">{(selectedFacilityData.status ?? 'unknown').toLowerCase()}</p>
                    <p className="text-[10px] text-[var(--text-tertiary)]">{(selectedFacilityData.totalBins ?? 0) - (selectedFacilityData.emptyBins ?? 0)} occupied / {selectedFacilityData.totalBins ?? 0} bins</p>
                  </div>
                </div>

                {/* Navigation buttons */}
                <div className="flex gap-2">
                  <button type="button" onClick={() => navigate(`/warehouse`)} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm">
                    <Building2 className="w-3.5 h-3.5" /> View Facility
                  </button>
                  <button type="button" onClick={() => navigate(`/orders?facility=${selectedFacilityData.code}`)} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm">
                    <Package className="w-3.5 h-3.5" /> View Orders
                  </button>
                </div>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center h-48 text-[var(--text-tertiary)]">
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
        <div className="lg:col-span-2 bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
          <div className="flex items-center justify-between px-6 py-4 border-b border-[var(--border-default)]">
            <div>
              <h3 className="font-semibold text-[var(--text-primary)]">Current Order Velocity</h3>
              <p className="text-xs text-[var(--text-secondary)] mt-0.5">Live orders-per-hour rate</p>
            </div>
          </div>
          <div className="p-6 flex items-center gap-6">
            <div className="text-center flex-shrink-0">
              <p className="text-5xl font-bold text-[var(--nexus-primary-600)] tabular-nums">
                {velocityRate === null ? '—' : velocityRate}
              </p>
              <p className="text-xs text-[var(--text-secondary)] mt-1">orders / hour</p>
            </div>
            <div className="flex-1">
              <p className="text-sm text-[var(--text-secondary)] leading-relaxed">
                {velocityRate === null
                  ? 'Velocity data is currently unavailable.'
                  : `The operation is processing ${velocityRate} orders per hour right now.`}
              </p>
            </div>
          </div>
        </div>

        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
          <div className="px-6 py-4 border-b border-[var(--border-default)]">
            <h3 className="font-semibold text-[var(--text-primary)]">Order Status</h3>
            <p className="text-xs text-[var(--text-secondary)] mt-0.5">Current distribution</p>
          </div>
          <div className="p-6">
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={pieChartData} cx="50%" cy="50%" innerRadius={50} outerRadius={80} paddingAngle={2} dataKey="value">
                  {pieChartData.map((entry, i) => <Cell key={i} fill={entry.color} />)}
                </Pie>
                <Tooltip contentStyle={TOOLTIP_STYLE} />
              </PieChart>
            </ResponsiveContainer>
            <div className="flex flex-wrap justify-center gap-4 mt-2">
              {pieChartData.map((entry, i) => (
                <div key={i} className="flex items-center gap-1.5">
                  <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: entry.color }} />
                  <span className="text-xs text-[var(--text-secondary)]">{entry.name}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Inventory Health + SLA Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Inventory Health */}
        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
          <div className="px-6 py-4 border-b border-[var(--border-default)]">
            <h3 className="font-semibold text-[var(--text-primary)]">Inventory Health</h3>
            <p className="text-xs text-[var(--text-secondary)] mt-0.5">Stock status across all SKUs</p>
          </div>
          <div className="p-6">
            <div className="flex items-center gap-6 mb-5">
              <div className="text-center">
                <p className="text-3xl font-bold text-[var(--nexus-success-600)]">{inventoryHealth.healthy}</p>
                <p className="text-xs text-[var(--text-tertiary)] mt-1">Healthy</p>
              </div>
              <div className="flex-1">
                <div className="flex h-3 rounded-full overflow-hidden gap-1">
                  <div className="bg-[var(--nexus-success-500)] rounded-l-full" style={{ width: `${inventoryHealth.healthy / (inventoryHealth.healthy + inventoryHealth.low + inventoryHealth.critical + inventoryHealth.outOfStock) * 100}%` }} />
                  <div className="bg-[var(--nexus-warning-500)]" style={{ width: `${inventoryHealth.low / (inventoryHealth.healthy + inventoryHealth.low + inventoryHealth.critical + inventoryHealth.outOfStock) * 100}%` }} />
                  <div className="bg-[var(--nexus-error-500)]" style={{ width: `${inventoryHealth.critical / (inventoryHealth.healthy + inventoryHealth.low + inventoryHealth.critical + inventoryHealth.outOfStock) * 100}%` }} />
                  <div className="bg-[var(--surface-muted)] rounded-r-full" style={{ width: `${inventoryHealth.outOfStock / (inventoryHealth.healthy + inventoryHealth.low + inventoryHealth.critical + inventoryHealth.outOfStock) * 100}%` }} />
                </div>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div className="p-3 rounded-lg bg-[var(--nexus-warning-50)] border border-[var(--nexus-warning-200)]">
                <p className="text-lg font-bold text-[var(--nexus-warning-600)]">{inventoryHealth.low}</p>
                <p className="text-[11px] font-medium text-[var(--nexus-warning-700)]">Low Stock</p>
              </div>
              <div className="p-3 rounded-lg bg-[var(--nexus-error-50)] border border-[var(--nexus-error-200)]">
                <p className="text-lg font-bold text-[var(--nexus-error-600)]">{inventoryHealth.critical}</p>
                <p className="text-[11px] font-medium text-[var(--nexus-error-700)]">Critical</p>
              </div>
              <div className="p-3 rounded-lg bg-[var(--surface-muted)] border border-[var(--border-default)]">
                <p className="text-lg font-bold text-[var(--text-secondary)]">{inventoryHealth.outOfStock}</p>
                <p className="text-[11px] font-medium text-[var(--text-tertiary)]">Out of Stock</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Activity + Alerts + Quick Actions */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

<div className="lg:col-span-2 bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
          <div className="px-6 py-4 border-b border-[var(--border-default)]">
            <h3 className="font-semibold text-[var(--text-primary)]">Activity Feed</h3>
          </div>
          <div className="p-5">
            <EnterpriseTimeline events={activities} />
          </div>
        </div>

        <div className="space-y-6">
          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
            <div className="px-5 py-4 border-b border-[var(--border-default)]">
              <h3 className="font-semibold text-[var(--text-primary)]">Alerts & Exceptions</h3>
            </div>
            <div className="p-5">
              {alerts.length === 0 ? (
                <div className="flex flex-col items-center py-8 text-[var(--text-tertiary)]">
                  <CheckCircle className="w-8 h-8 mb-2" />
                  <p className="text-sm">No active alerts</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {alerts.map(alert => (
                    <div key={alert.id} className="flex items-start gap-3 p-3 rounded-lg bg-[var(--surface-muted)]">
                      <div className={clsx('w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0',
                        alert.severity === 'warning' && 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-600)]',
                        alert.severity === 'error' && 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)]',
                        alert.severity === 'info' && 'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-600)]')}>
                        <AlertTriangle className="w-3.5 h-3.5" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm text-[var(--text-secondary)]">{alert.message}</p>
                        <p className="text-xs text-[var(--text-tertiary)] mt-0.5 capitalize">{alert.severity}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
            <div className="px-5 py-4 border-b border-[var(--border-default)]">
              <h3 className="font-semibold text-[var(--text-primary)]">Quick Actions</h3>
            </div>
            <div className="p-5">
              <div className="grid grid-cols-2 gap-3">
                {QUICK_ACTIONS.map(action => (
                  <button type="button" key={action.label} onClick={() => navigate(action.path)}
                    className="flex flex-col items-center gap-2 p-4 rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] hover:bg-[var(--interactive-hover)] hover:border-[var(--border-strong)] transition-all duration-150">
                    <div className="w-10 h-10 rounded-xl bg-[var(--nexus-primary-50)] flex items-center justify-center text-[var(--text-brand)]">
                      <action.icon className="w-5 h-5" />
                    </div>
                    <div className="text-center">
                      <p className="text-sm font-medium text-[var(--text-primary)]">{action.label}</p>
                      <p className="text-xs text-[var(--text-tertiary)] mt-0.5">{action.desc}</p>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Promotions & Endless Aisle */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
          <div className="px-6 py-4 border-b border-[var(--border-default)] flex items-center justify-between">
            <div>
              <h3 className="font-semibold text-[var(--text-primary)] flex items-center gap-2"><Percent className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Promotions Overview</h3>
              <p className="text-xs text-[var(--text-secondary)] mt-0.5">Active campaigns & redemption</p>
            </div>
            <button type="button" onClick={() => navigate('/promotions')} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm">
              View All
            </button>
          </div>
          <div className="p-6">
            <div className="grid grid-cols-3 gap-4 mb-5">
              <div className="p-3 rounded-lg bg-[var(--surface-muted)]">
                <p className="text-xs text-[var(--text-secondary)]">Active Promos</p>
                <p className="text-xl font-bold text-[var(--text-primary)] mt-1">{promotionStats.activeCount}</p>
              </div>
              <div className="p-3 rounded-lg bg-[var(--surface-muted)]">
                <p className="text-xs text-[var(--text-secondary)]">Total Redemptions</p>
                <p className="text-xl font-bold text-[var(--nexus-success-600)] mt-1">{promotionStats.totalUses.toLocaleString()}</p>
              </div>
              <div className="p-3 rounded-lg bg-[var(--surface-muted)]">
                <p className="text-xs text-[var(--text-secondary)]">Discount Given</p>
                <p className="text-xl font-bold text-[var(--nexus-warning-600)] mt-1">${promotionStats.totalDiscount.toLocaleString()}</p>
              </div>
            </div>
            {promotionStats.topPromos.length > 0 ? (
              <div className="space-y-2">
                <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Top Promotions</p>
                {promotionStats.topPromos.map((p: any) => (
                  <div key={p.id} className="flex items-center justify-between p-2.5 rounded-lg bg-[var(--surface-muted)]">
                    <div className="flex items-center gap-2.5">
                      <div className="w-7 h-7 rounded-lg bg-[var(--nexus-primary-50)] flex items-center justify-center">
                        <Percent className="w-3.5 h-3.5 text-[var(--nexus-primary-600)]" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-[var(--text-primary)]">{p.name}</p>
                        <p className="text-[10px] text-[var(--text-tertiary)]">{p.promotionType} · {p.couponCode || 'No code'}</p>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-sm font-semibold text-[var(--text-primary)]">{p.discountValue}{p.promotionType === 'PERCENTAGE' ? '%' : ''}</p>
                      <p className="text-[10px] text-[var(--text-tertiary)]">{p.currentUses ?? 0} uses</p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex flex-col items-center py-8 text-[var(--text-tertiary)]">
                <Percent className="w-8 h-8 mb-2" />
                <p className="text-sm">No active promotions</p>
                <p className="text-xs mt-1">Create a promotion to get started</p>
              </div>
            )}
          </div>
        </div>

        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
          <div className="px-6 py-4 border-b border-[var(--border-default)] flex items-center justify-between">
            <div>
              <h3 className="font-semibold text-[var(--text-primary)] flex items-center gap-2"><Warehouse className="w-4 h-4 text-[var(--nexus-ai-500)]" /> Endless Aisle</h3>
              <p className="text-xs text-[var(--text-secondary)] mt-0.5">Store-initiated out-of-stock orders</p>
            </div>
            <button type="button" onClick={() => navigate('/endless-aisle')} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm">
              View All
            </button>
          </div>
          <div className="p-6">
            <div className="grid grid-cols-2 gap-4 mb-5">
              <div className="p-3 rounded-lg bg-[var(--surface-muted)]">
                <p className="text-xs text-[var(--text-secondary)]">Pending</p>
                <p className="text-xl font-bold text-[var(--nexus-warning-600)] mt-1">{endlessAisleStats.pendingOrders}</p>
              </div>
              <div className="p-3 rounded-lg bg-[var(--surface-muted)]">
                <p className="text-xs text-[var(--text-secondary)]">In Transit</p>
                <p className="text-xl font-bold text-[var(--nexus-primary-600)] mt-1">{endlessAisleStats.inTransit}</p>
              </div>
              <div className="p-3 rounded-lg bg-[var(--surface-muted)]">
                <p className="text-xs text-[var(--text-secondary)]">Delivered</p>
                <p className="text-xl font-bold text-[var(--nexus-success-600)] mt-1">{endlessAisleStats.delivered}</p>
              </div>
              <div className="p-3 rounded-lg bg-[var(--surface-muted)]">
                <p className="text-xs text-[var(--text-secondary)]">Cancelled</p>
                <p className="text-xl font-bold text-[var(--nexus-error-600)] mt-1">{endlessAisleStats.cancelled}</p>
              </div>
            </div>
            <div className="p-4 rounded-lg bg-[var(--nexus-ai-50)] border border-[var(--nexus-ai-200)]">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <DollarSign className="w-4 h-4 text-[var(--nexus-ai-600)]" />
                  <span className="text-sm font-medium text-[var(--nexus-ai-900)]">Pipeline Revenue</span>
                </div>
                <span className="text-lg font-bold text-[var(--nexus-ai-700)]">${endlessAisleStats.totalRevenue.toLocaleString()}</span>
              </div>
              <p className="text-xs text-[var(--nexus-ai-700)] mt-1">Total value of all endless aisle orders in the system</p>
            </div>
          </div>
        </div>
      </div>

      {/* AI Insights */}
      <div className="mt-6">
        <h3 className="text-lg font-semibold mb-3 flex items-center gap-2">
          <Brain className="w-5 h-5 text-[var(--nexus-ai-600)]" /> AI Insights
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="bg-[var(--nexus-ai-50)] border border-[var(--nexus-ai-200)] rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <TrendingUp className="w-4 h-4 text-[var(--nexus-ai-600)]" />
              <h4 className="font-medium text-sm text-[var(--nexus-ai-900)]">Demand Forecast</h4>
            </div>
            <p className="text-2xl font-bold text-[var(--nexus-ai-700)]">{aiDemand?.predictedOrders ?? 'Unavailable'}</p>
            <div className="mt-2">
              <div className="flex justify-between text-xs text-[var(--nexus-ai-600)] mb-1">
                <span>Confidence</span>
                <span>{aiDemand ? `${Math.round((aiDemand.confidence ?? 0) * 100)}%` : '—'}</span>
              </div>
              <div className="w-full bg-[var(--nexus-ai-200)] rounded-full h-1.5">
                <div className="bg-[var(--nexus-ai-600)] h-1.5 rounded-full" style={{ width: `${Math.round((aiDemand?.confidence ?? 0) * 100)}%` }} />
              </div>
            </div>
            <p className="text-xs text-[var(--nexus-ai-700)] mt-2">{aiDemand?.explanation ?? 'Prediction unavailable.'}</p>
          </div>

          <div className="bg-[var(--nexus-ai-50)] border border-[var(--nexus-ai-200)] rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Package className="w-4 h-4 text-[var(--nexus-ai-600)]" />
              <h4 className="font-medium text-sm text-[var(--nexus-ai-900)]">Inventory Alert</h4>
            </div>
            <p className="text-2xl font-bold text-[var(--nexus-ai-700)]">{aiInventory?.predictedOrders ?? 'Unavailable'}{aiInventory ? ' items' : ''}</p>
            <div className="mt-2">
              <div className="flex justify-between text-xs text-[var(--nexus-ai-600)] mb-1">
                <span>Confidence</span>
                <span>{aiInventory ? `${Math.round((aiInventory.confidence ?? 0) * 100)}%` : '—'}</span>
              </div>
              <div className="w-full bg-[var(--nexus-ai-200)] rounded-full h-1.5">
                <div className="bg-[var(--nexus-ai-600)] h-1.5 rounded-full" style={{ width: `${Math.round((aiInventory?.confidence ?? 0) * 100)}%` }} />
              </div>
            </div>
            <p className="text-xs text-[var(--nexus-ai-700)] mt-2">{aiInventory?.explanation ?? 'Prediction unavailable.'}</p>
          </div>

          <div className="bg-[var(--nexus-ai-50)] border border-[var(--nexus-ai-200)] rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Truck className="w-4 h-4 text-[var(--nexus-ai-600)]" />
              <h4 className="font-medium text-sm text-[var(--nexus-ai-900)]">Shipping Prediction</h4>
            </div>
            <p className="text-2xl font-bold text-[var(--nexus-ai-700)]">{aiShipping?.predictedOrders ?? 'Unavailable'}</p>
            <div className="mt-2">
              <div className="flex justify-between text-xs text-[var(--nexus-ai-600)] mb-1">
                <span>Confidence</span>
                <span>{aiShipping ? `${Math.round((aiShipping.confidence ?? 0) * 100)}%` : '—'}</span>
              </div>
              <div className="w-full bg-[var(--nexus-ai-200)] rounded-full h-1.5">
                <div className="bg-[var(--nexus-ai-600)] h-1.5 rounded-full" style={{ width: `${Math.round((aiShipping?.confidence ?? 0) * 100)}%` }} />
              </div>
            </div>
            <p className="text-xs text-[var(--nexus-ai-700)] mt-2">{aiShipping?.explanation ?? 'Prediction unavailable.'}</p>
          </div>
        </div>
      </div>
    </div>
    </PermissionGate>
  )
}
