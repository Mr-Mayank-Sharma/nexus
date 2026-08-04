import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Package, DollarSign, Truck, AlertTriangle, Warehouse, Users,
  ShoppingCart, ArrowRight, RefreshCw, CheckCircle, Activity, Shield, XCircle, Clock,
} from 'lucide-react'
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts'
import clsx from 'clsx'
import {
  EnterpriseKPICard,
  EnterpriseStatusBadge,
  EnterpriseTimeline,
} from '../components/enterprise'
import type { TimelineEvent } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'
import * as analyticsApi from '../api/analytics'

const PIE_COLORS = [
  'var(--nexus-primary-500)', 'var(--nexus-warning-500)', 'var(--nexus-success-500)',
  'var(--nexus-error-500)', 'var(--nexus-ai-500)', '#EC4899', 'var(--nexus-info-500)',
  'var(--text-tertiary)',
]
const TOOLTIP_STYLE: React.CSSProperties = {
  backgroundColor: 'var(--chart-tooltip-bg)', border: '1px solid var(--chart-tooltip-border)',
  borderRadius: 'var(--radius-lg)', fontSize: 'var(--text-xs)', boxShadow: 'var(--elevation-3)',
}

export default function AnalyticsDashboardPage() {
  const queryClient = useQueryClient()

  const { data: kpis = {}, isLoading: kpisLoading } = useQuery({
    queryKey: ['analytics', 'kpis'],
    queryFn: analyticsApi.getDashboardKpis,
    select: (res) => res.data ?? {},
    refetchInterval: 60000,
  })

  const { data: statusDist = [] } = useQuery({
    queryKey: ['analytics', 'status-dist'],
    queryFn: analyticsApi.getOrderStatusDistribution,
    select: (res) => res.data ?? [],
    refetchInterval: 60000,
  })

  const { data: warehouses = [], isLoading: warehousesLoading } = useQuery({
    queryKey: ['analytics', 'warehouses'],
    queryFn: analyticsApi.getWarehousesSummary,
    select: (res) => res.data ?? [],
    refetchInterval: 60000,
  })

  const { data: taskQueue = null } = useQuery({
    queryKey: ['analytics', 'task-queue'],
    queryFn: analyticsApi.getTaskQueueSummary,
    select: (res) => res.data ?? null,
    refetchInterval: 60000,
  })

  const { data: activities = [] } = useQuery({
    queryKey: ['analytics', 'activity'],
    queryFn: analyticsApi.getActivity,
    select: (res) => res.data ?? [],
    refetchInterval: 60000,
  })

  const { data: alerts = [] } = useQuery({
    queryKey: ['analytics', 'alerts'],
    queryFn: analyticsApi.getAlerts,
    select: (res) => res.data ?? [],
    refetchInterval: 60000,
  })

  const pieChartData = statusDist.map((item: any, i: number) => ({
    name: item.name,
    value: item.value,
    color: PIE_COLORS[i % PIE_COLORS.length],
  }))

  const holdTasks = (taskQueue?.holdTasks ?? {}) as Record<string, number>

  const kpiCards = [
    { title: 'Orders Today', value: (kpis.ordersToday ?? 0).toLocaleString(), subtitle: 'Orders created today', icon: ShoppingCart, color: 'primary' as const },
    { title: 'Revenue Today', value: `$${(kpis.revenueToday ?? 0).toLocaleString()}`, subtitle: 'Sales value today', icon: DollarSign, color: 'success' as const },
    { title: 'On-Time Delivery', value: kpis.onTimeDelivery ?? '—', subtitle: 'Share delivered on time', icon: Truck, color: 'warning' as const },
    { title: 'Active Exceptions', value: (kpis.activeExceptions ?? 0).toLocaleString(), subtitle: 'Open fulfillment exceptions', icon: AlertTriangle, color: 'error' as const },
  ]

  const holdTaskCards = [
    { label: 'Substitute Items', key: 'substituteItems', color: 'text-[var(--nexus-warning-600)] bg-[var(--nexus-warning-50)]', icon: Package },
    { label: 'Bad Address', key: 'badAddress', color: 'text-[var(--nexus-error-600)] bg-[var(--nexus-error-50)]', icon: XCircle },
    { label: 'Fraud Risk', key: 'fraudRisk', color: 'text-[var(--nexus-ai-600)] bg-[var(--nexus-ai-50)]', icon: Shield },
    { label: 'On Hold', key: 'onHold', color: 'text-[var(--nexus-primary-600)] bg-[var(--nexus-primary-50)]', icon: Clock },
  ]

  return (
    <PermissionGate resource="analytics" action="view">
      <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Analytics Dashboard</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Live performance overview and key metrics
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            className="enterprise-btn enterprise-btn-secondary h-9 gap-2"
            onClick={() => queryClient.invalidateQueries({ queryKey: ['analytics'] })}
          >
            <RefreshCw className="w-4 h-4" />
            Refresh
          </button>
        </div>
      </div>

      {/* Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {kpiCards.map((kpi) => (
          <EnterpriseKPICard
            key={kpi.title}
            title={kpi.title}
            value={kpi.value}
            subtitle={kpi.subtitle}
            icon={kpi.icon}
            color={kpi.color}
            loading={kpisLoading}
          />
        ))}
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Order Status Distribution */}
        <div className="enterprise-card p-5">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-semibold text-[var(--text-primary)]">Order Status</h3>
              <p className="text-xs text-[var(--text-secondary)] mt-0.5">Current distribution</p>
            </div>
          </div>
          {pieChartData.length === 0 ? (
            <div className="flex flex-col items-center py-12 text-[var(--text-tertiary)]">
              <CheckCircle className="w-8 h-8 mb-2" />
              <p className="text-sm">No order data available</p>
            </div>
          ) : (
            <>
              <ResponsiveContainer width="100%" height={220}>
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
            </>
          )}
        </div>

        {/* Warehouse Performance */}
        <div className="enterprise-card">
          <div className="px-5 py-4 border-b border-[var(--border-subtle)]">
            <div className="flex items-center gap-2">
              <Warehouse className="w-4 h-4 text-[var(--nexus-primary-500)]" />
              <h3 className="font-semibold text-[var(--text-primary)]">Warehouse Utilization</h3>
            </div>
          </div>
          <div className="divide-y divide-[var(--border-subtle)]">
            {warehousesLoading ? (
              <div className="p-4 space-y-3">
                {Array.from({ length: 3 }).map((_, i) => <div key={i} className="enterprise-skeleton h-10 w-full" />)}
              </div>
            ) : warehouses.length === 0 ? (
              <div className="flex flex-col items-center py-10 text-[var(--text-tertiary)]">
                <Warehouse className="w-8 h-8 mb-2" />
                <p className="text-sm">No warehouses configured</p>
              </div>
            ) : warehouses.map((wh: any) => (
              <div key={wh.id} className="px-5 py-3 hover:bg-[var(--interactive-hover)] transition-colors">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-[var(--text-primary)]">{wh.name}</p>
                    <p className="text-xs text-[var(--text-tertiary)]">
                      {wh.code} · {wh.city ?? '—'}{wh.country ? `, ${wh.country}` : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <div className="text-right">
                      <p className="text-sm font-semibold text-[var(--text-primary)]">{wh.capacityUtilization ?? 0}%</p>
                      <p className="text-[10px] text-[var(--text-tertiary)]">{wh.totalBins ?? 0} bins</p>
                    </div>
                    <EnterpriseStatusBadge
                      status={(wh.status ?? 'UNKNOWN') === 'ACTIVE' ? 'ACTIVE' : (wh.status ?? 'UNKNOWN')}
                      size="sm"
                    />
                  </div>
                </div>
                <div className="mt-2 w-full h-1.5 bg-[var(--surface-sunken)] rounded-full overflow-hidden">
                  <div
                    className={clsx(
                      'h-full rounded-full transition-all duration-500',
                      (wh.capacityUtilization ?? 0) >= 90 ? 'bg-[var(--nexus-error-500)]' :
                      (wh.capacityUtilization ?? 0) >= 75 ? 'bg-[var(--nexus-warning-500)]' : 'bg-[var(--nexus-success-500)]'
                    )}
                    style={{ width: `${Math.min(100, wh.capacityUtilization ?? 0)}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Task Queue + Alerts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="enterprise-card p-5">
          <h3 className="font-semibold text-[var(--text-primary)] mb-4">Hold Tasks</h3>
          <div className="grid grid-cols-2 gap-3">
            {holdTaskCards.map((task) => (
              <div key={task.key} className="flex items-center gap-3 p-3 rounded-lg bg-[var(--surface-muted)]">
                <div className={clsx('w-8 h-8 rounded-lg flex items-center justify-center', task.color)}>
                  <task.icon className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <p className="text-lg font-bold text-[var(--text-primary)] leading-tight tabular-nums">
                    {holdTasks[task.key] ?? 0}
                  </p>
                  <p className="text-xs text-[var(--text-secondary)] truncate">{task.label}</p>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="enterprise-card p-5">
          <h3 className="font-semibold text-[var(--text-primary)] mb-4">Alerts & Exceptions</h3>
          {alerts.length === 0 ? (
            <div className="flex flex-col items-center py-10 text-[var(--text-tertiary)]">
              <CheckCircle className="w-8 h-8 mb-2" />
              <p className="text-sm">No active alerts</p>
            </div>
          ) : (
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {alerts.map((alert: any) => (
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

      {/* Activity Feed */}
      <div className="enterprise-card">
        <div className="px-5 py-4 border-b border-[var(--border-subtle)] flex items-center gap-2">
          <Activity className="w-4 h-4 text-[var(--nexus-primary-500)]" />
          <h3 className="font-semibold text-[var(--text-primary)]">Activity Feed</h3>
        </div>
        <div className="p-5">
          {activities.length === 0 ? (
            <div className="flex flex-col items-center py-10 text-[var(--text-tertiary)]">
              <Activity className="w-8 h-8 mb-2" />
              <p className="text-sm">No recent activity</p>
            </div>
          ) : (
            <EnterpriseTimeline events={activities as TimelineEvent[]} />
          )}
        </div>
      </div>
    </div>
    </PermissionGate>
  )
}
