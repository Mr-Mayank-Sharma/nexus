import { useState, useEffect, useCallback } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend,
} from 'recharts'
import { BarChart3, Download, TrendingUp, TrendingDown, RefreshCw, AlertTriangle, Clock } from 'lucide-react'
import { EnterpriseKPICard, EnterpriseBreadcrumbs } from '../components/enterprise'
import { useToast } from '../hooks/useToast'
import * as analyticsApi from '../api/analytics'
import PermissionGate from '../components/rbac/PermissionGate'

const COLORS = ['var(--nexus-primary-600)', 'var(--nexus-success-600)', 'var(--nexus-warning-600)', 'var(--nexus-error-500)', 'var(--nexus-ai-500)', 'var(--nexus-info-500)']

const COST_LABELS: Record<string, string> = {
  shipping: 'Shipping',
  labor: 'Labor',
  packaging: 'Packaging',
  warehouse: 'Warehouse',
  returns: 'Returns',
}

interface CostItem { name: string; value: number }
interface LaneItem { lane: string; volume: number; otd: number }
interface ReturnReason { reason?: string; count?: number }
interface CarrierRow {
  carrier: string
  shipments: number
  otdRate: number | null
  avgDeliveryDays: number | null
}

function sumCost(data: CostItem[]): string {
  if (data.length === 0) return '—'
  return `$${data.reduce((s, d) => s + d.value, 0).toLocaleString()}`
}

function avgOtd(data: LaneItem[]): string {
  if (data.length === 0) return '—'
  return `${(data.reduce((s, d) => s + d.otd, 0) / data.length).toFixed(1)}%`
}

export default function AnalyticsPage() {
  const [costBreakdown, setCostBreakdown] = useState<CostItem[]>([])
  const [laneData, setLaneData] = useState<LaneItem[]>([])
  const [returnsSummary, setReturnsSummary] = useState<{ totalReturns?: number; returnRate?: number; avgRefundAmount?: number; topReasons?: ReturnReason[] } | null>(null)
  const [carrierData, setCarrierData] = useState<CarrierRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<string | null>(null)
  const { addToast } = useToast()

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const results = await Promise.allSettled([
        analyticsApi.getCostBreakdown(),
        analyticsApi.getLanePerformance(),
        analyticsApi.getReturnsAnalytics(),
        analyticsApi.getCarrierPerformance(),
      ])

      const [costRes, laneRes, returnsRes, carrierRes] = results

      if (costRes.status === 'fulfilled' && costRes.value.success && costRes.value.data) {
        const d = costRes.value.data
        if (Array.isArray(d)) {
          setCostBreakdown(d.map((item: any) => ({ name: item.name ?? item.category ?? 'Other', value: Number(item.value ?? item.amount ?? 0) || 0 })))
        } else {
          setCostBreakdown(Object.entries(d).map(([k, v]) => ({ name: COST_LABELS[k] || k, value: Number(v) || 0 })))
        }
      }

      if (laneRes.status === 'fulfilled' && laneRes.value.success && laneRes.value.data) {
        const d = laneRes.value.data
        const lanes = Array.isArray(d) ? d : d.lanes
        if (Array.isArray(lanes)) {
          setLaneData(lanes.map((l: any) => ({
            lane: l.origin && l.destination ? `${l.origin} → ${l.destination}` : (l.lane || '—'),
            volume: Number(l.volume) || 0,
            otd: Math.round((Number(l.onTime) || 0) * 1000) / 10,
          })))
        }
      }

      if (returnsRes.status === 'fulfilled' && returnsRes.value.success && returnsRes.value.data) {
        setReturnsSummary(returnsRes.value.data)
      }

      if (carrierRes.status === 'fulfilled' && carrierRes.value.success && carrierRes.value.data) {
        const d = carrierRes.value.data
        if (Array.isArray(d)) {
          setCarrierData(d.map((c: any) => ({
            carrier: c.carrier || '—',
            shipments: Number(c.shipments) || 0,
            otdRate: c.otdRate != null ? Math.round(Number(c.otdRate) * 1000) / 10 : (c.onTimeRate != null ? Math.round(Number(c.onTimeRate) * 1000) / 10 : null),
            avgDeliveryDays: c.avgDeliveryDays != null ? Number(c.avgDeliveryDays) : null,
          })))
        } else if (d.totalShipments != null || d.onTimeRate != null) {
          setCarrierData([{
            carrier: 'All Carriers',
            shipments: Number(d.totalShipments) || 0,
            otdRate: d.onTimeRate != null ? Math.round(Number(d.onTimeRate) * 1000) / 10 : null,
            avgDeliveryDays: d.avgDeliveryDays != null ? Number(d.avgDeliveryDays) : null,
          }])
        }
      }

      setLastUpdated(new Date().toLocaleString())
    } catch {
      setError('Failed to load analytics data')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const chartTooltipStyle: React.CSSProperties = {
    borderRadius: 'var(--radius-lg)',
    border: '1px solid var(--chart-tooltip-border)',
    backgroundColor: 'var(--chart-tooltip-bg)',
    fontSize: 'var(--text-xs)',
    boxShadow: 'var(--elevation-3)',
  }

  const topReasons = returnsSummary?.topReasons ?? []
  const returnRatePct = returnsSummary?.returnRate != null ? (Number(returnsSummary.returnRate) * 100).toFixed(1) : null

  if (error) {
    return (
      <div className="space-y-6">
        <EnterpriseBreadcrumbs crumbs={[{ label: 'Home', path: '/' }, { label: 'Analytics' }]} />
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><BarChart3 className="w-5 h-5" />Analytics</h1>
            <p className="text-sm text-[var(--text-secondary)] mt-1">Performance metrics and insights</p>
          </div>
        </div>
        <div className="card p-12 text-center">
          <AlertTriangle className="w-14 h-14 mx-auto text-[var(--nexus-error-500)] mb-4" />
          <h2 className="text-xl font-semibold text-[var(--text-primary)] mb-2">Failed to load analytics</h2>
          <p className="text-sm text-[var(--text-secondary)] mb-6 max-w-md mx-auto">{error}. Check your connection and try again.</p>
          <button type="button" onClick={fetchData} className="enterprise-btn enterprise-btn-primary inline-flex items-center gap-2">
            <RefreshCw className="w-4 h-4" />
            Retry
          </button>
        </div>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="space-y-6">
        <EnterpriseBreadcrumbs crumbs={[{ label: 'Home', path: '/' }, { label: 'Analytics' }]} />
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><BarChart3 className="w-5 h-5" />Analytics</h1>
            <p className="text-sm text-[var(--text-secondary)] mt-1">Performance metrics and insights</p>
          </div>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <EnterpriseKPICard key={i} title="" value="" loading color="primary" />
          ))}
        </div>
        <div className="card">
          <div className="card-header"><div className="enterprise-skeleton h-5 w-40" /></div>
          <div className="card-body">
            <div className="enterprise-skeleton" style={{ height: 200 }} />
          </div>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="card">
              <div className="card-header"><div className="enterprise-skeleton h-5 w-32" /></div>
              <div className="card-body">
                <div className="enterprise-skeleton" style={{ height: 300 }} />
              </div>
            </div>
          ))}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="card">
              <div className="card-header"><div className="enterprise-skeleton h-5 w-36" /></div>
              <div className="card-body">
                <div className="enterprise-skeleton" style={{ height: 240 }} />
              </div>
            </div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Home', path: '/' }, { label: 'Analytics' }]} />

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><BarChart3 className="w-5 h-5" />Analytics</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Performance metrics and insights</p>
        </div>
        <div className="flex items-center gap-3">
          {lastUpdated && (
            <span className="text-xs text-[var(--text-tertiary)] flex items-center gap-1">
              <Clock className="w-3.5 h-3.5" />
              Last updated: {lastUpdated}
            </span>
          )}
          <button type="button" className="enterprise-btn enterprise-btn-secondary text-sm" onClick={() => fetchData()}>
            <RefreshCw className="w-4 h-4" /> Refresh
          </button>
          <PermissionGate resource="reports" action="create">
            <button type="button" className="enterprise-btn enterprise-btn-primary text-sm" onClick={() => addToast({ type: 'info', title: 'Export feature coming soon' })}><Download className="w-4 h-4" /> Export Executive Summary</button>
          </PermissionGate>
        </div>
      </div>

      {/* KPI Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Total Fulfillment Cost"
          value={sumCost(costBreakdown)}
          icon={<TrendingDown className="w-5 h-5" />}
          color="primary"
          trend={costBreakdown.length > 0 ? 'neutral' : undefined}
        />
        <EnterpriseKPICard
          title="Avg On-Time Delivery"
          value={avgOtd(laneData)}
          icon={<TrendingUp className="w-5 h-5" />}
          color="success"
          trend={laneData.length > 0 ? 'neutral' : undefined}
        />
        <EnterpriseKPICard
          title="Return Rate"
          value={returnRatePct != null ? `${returnRatePct}%` : '—'}
          icon={<TrendingDown className="w-5 h-5" />}
          color="warning"
          trend={returnRatePct != null ? 'neutral' : undefined}
        />
        <EnterpriseKPICard
          title="Total Shipments"
          value={carrierData.reduce((s, c) => s + (c.shipments || 0), 0).toLocaleString()}
          icon={<TrendingUp className="w-5 h-5" />}
          color="info"
          trend={carrierData.length > 0 ? 'neutral' : undefined}
        />
      </div>

      {/* Carrier Scorecard */}
      <div className="card">
        <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]">Carrier Scorecard</h3></div>
        {carrierData.length === 0 ? (
          <div className="card-body">
            <p className="py-10 text-center text-sm text-[var(--text-tertiary)]">No carrier data yet</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Carrier</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Shipments</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">OTD %</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Avg Delivery (days)</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Score</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--surface-sunken)]">
                {carrierData.map((c) => (
                  <tr key={c.carrier} className="hover:bg-[var(--surface-sunken)]">
                    <td className="px-6 py-3 text-sm font-medium text-[var(--text-primary)]">{c.carrier}</td>
                    <td className="px-6 py-3 text-sm text-[var(--text-secondary)] text-right">{c.shipments.toLocaleString()}</td>
                    <td className="px-6 py-3 text-sm text-right">
                      {c.otdRate == null ? (
                        <span className="text-[var(--text-tertiary)]">—</span>
                      ) : (
                        <span className="flex items-center justify-end gap-1">
                          {c.otdRate >= 95 ? <TrendingUp className="w-3.5 h-3.5 text-[var(--nexus-success-500)]" /> : <TrendingDown className="w-3.5 h-3.5 text-[var(--nexus-error-500)]" />}
                          {c.otdRate}%
                        </span>
                      )}
                    </td>
                    <td className="px-6 py-3 text-sm text-[var(--text-secondary)] text-right">{c.avgDeliveryDays != null ? c.avgDeliveryDays : '—'}</td>
                    <td className="px-6 py-3 text-right">
                      {c.otdRate == null ? (
                        <span className="text-[var(--text-tertiary)]">—</span>
                      ) : (
                        <span className={`badge ${c.otdRate >= 95 ? 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]' : c.otdRate >= 92 ? 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]' : 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)]'}`}>
                          {c.otdRate >= 95 ? 'Excellent' : c.otdRate >= 92 ? 'Average' : 'Poor'}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Charts Row 1: Cost Breakdown + Lane Analysis */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="card">
          <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]">Cost Breakdown</h3></div>
          <div className="card-body">
            {costBreakdown.length === 0 ? (
              <p className="py-16 text-center text-sm text-[var(--text-tertiary)]">No cost data yet</p>
            ) : (
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={costBreakdown} layout="vertical" margin={{ left: 100 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                    <XAxis type="number" tick={{ fontSize: 11, fill: 'var(--chart-axis)' }} tickLine={false} axisLine={false} />
                    <YAxis dataKey="name" type="category" tick={{ fontSize: 11, fill: 'var(--chart-axis)' }} tickLine={false} axisLine={false} width={130} />
                    <Tooltip formatter={(value: number) => [`$${value.toLocaleString()}`, 'Cost']} contentStyle={chartTooltipStyle} />
                    <Bar dataKey="value" fill="var(--nexus-primary-700)" radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </div>
        <div className="card">
          <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]">Lane Analysis</h3></div>
          <div className="card-body">
            {laneData.length === 0 ? (
              <p className="py-16 text-center text-sm text-[var(--text-tertiary)]">No lane data yet</p>
            ) : (
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={laneData} margin={{ left: 10 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                    <XAxis dataKey="lane" tick={{ fontSize: 11, fill: 'var(--chart-axis)' }} tickLine={false} axisLine={false} />
                    <YAxis yAxisId="left" tick={{ fontSize: 11, fill: 'var(--chart-axis)' }} tickLine={false} axisLine={false} />
                    <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 11, fill: 'var(--chart-axis)' }} tickLine={false} axisLine={false} />
                    <Tooltip contentStyle={chartTooltipStyle} />
                    <Legend wrapperStyle={{ fontSize: '12px' }} />
                    <Bar yAxisId="left" dataKey="volume" fill="var(--nexus-primary-600)" radius={[4, 4, 0, 0]} name="Volume" />
                    <Bar yAxisId="right" dataKey="otd" fill="var(--nexus-success-600)" radius={[4, 4, 0, 0]} name="OTD %" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Charts Row 2: Returns Analytics + Cost Distribution */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="card">
          <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]">Returns Analytics</h3></div>
          <div className="card-body">
            {!returnsSummary ? (
              <p className="py-16 text-center text-sm text-[var(--text-tertiary)]">No returns data yet</p>
            ) : (
              <>
                <div className="grid grid-cols-3 gap-3 mb-5">
                  <div className="bg-[var(--surface-sunken)]/50 rounded-lg p-3 text-center">
                    <p className="text-xs text-[var(--text-secondary)]">Total Returns</p>
                    <p className="text-lg font-bold text-[var(--text-primary)]">{returnsSummary.totalReturns ?? '—'}</p>
                  </div>
                  <div className="bg-[var(--surface-sunken)]/50 rounded-lg p-3 text-center">
                    <p className="text-xs text-[var(--text-secondary)]">Return Rate</p>
                    <p className="text-lg font-bold text-[var(--text-primary)]">{returnRatePct != null ? `${returnRatePct}%` : '—'}</p>
                  </div>
                  <div className="bg-[var(--surface-sunken)]/50 rounded-lg p-3 text-center">
                    <p className="text-xs text-[var(--text-secondary)]">Avg Refund</p>
                    <p className="text-lg font-bold text-[var(--text-primary)]">{returnsSummary.avgRefundAmount != null ? `$${Number(returnsSummary.avgRefundAmount).toFixed(2)}` : '—'}</p>
                  </div>
                </div>
                {topReasons.length === 0 ? (
                  <p className="py-10 text-center text-sm text-[var(--text-tertiary)]">No return reason breakdown yet</p>
                ) : (
                  <div className="h-56">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={topReasons} margin={{ left: 10 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                        <XAxis dataKey="reason" tick={{ fontSize: 11, fill: 'var(--chart-axis)' }} tickLine={false} axisLine={false} />
                        <YAxis tick={{ fontSize: 11, fill: 'var(--chart-axis)' }} tickLine={false} axisLine={false} allowDecimals={false} />
                        <Tooltip contentStyle={chartTooltipStyle} formatter={(value: number) => [value, 'Returns']} />
                        <Bar dataKey="count" fill="var(--nexus-warning-600)" radius={[4, 4, 0, 0]} name="Returns" />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
        <div className="card">
          <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]">Cost Distribution</h3></div>
          <div className="card-body">
            {costBreakdown.length === 0 ? (
              <p className="py-16 text-center text-sm text-[var(--text-tertiary)]">No cost data yet</p>
            ) : (
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={costBreakdown} cx="50%" cy="50%" innerRadius={60} outerRadius={100} paddingAngle={3} dataKey="value">
                      {costBreakdown.map((_, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip formatter={(value: number) => [`$${value.toLocaleString()}`, 'Cost']} />
                    <Legend wrapperStyle={{ fontSize: '11px' }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
