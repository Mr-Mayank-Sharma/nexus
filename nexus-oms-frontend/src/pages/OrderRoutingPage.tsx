import { useState, useEffect, useCallback, useRef } from 'react'
import { clsx } from 'clsx'
import {
  Route, AlertTriangle, CheckCircle2, XCircle, Loader2, Search, RefreshCw,
  TrendingUp, Clock, DollarSign, Zap, Shield, ChevronRight, ExternalLink,
  ArrowUpRight, AlertCircle, Info, Flag, Brain,
} from 'lucide-react'
import * as orderRoutingApi from '../api/orderRouting'
import type { FulfillmentException } from '../types'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'
import PermissionGate from '../components/rbac/PermissionGate'

const EXCEPTION_TYPE_ICONS: Record<string, typeof AlertTriangle> = {
  INVENTORY_SHORTAGE: AlertTriangle,
  CARRIER_FAILURE: XCircle,
  CAPACITY_EXCEEDED: AlertCircle,
  WORKER_ABSENT: AlertCircle,
  WEATHER_DELAY: Info,
  SHIPPING_ADDRESS_ISSUE: Flag,
  PAYMENT_HOLD: AlertTriangle,
  CREDIT_HOLD: AlertTriangle,
  FRAUD_FLAG: AlertTriangle,
  CUSTOMER_REQUEST: Info,
  SYSTEM_ERROR: XCircle,
  OTHER: AlertTriangle,
}

const EXCEPTION_SEVERITY_CLASSES: Record<string, string> = {
  LOW: 'enterprise-badge-info',
  MEDIUM: 'enterprise-badge-warning',
  HIGH: 'enterprise-badge-error',
  CRITICAL: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-300)] ring-1 ring-red-200 dark:ring-red-800',
}

const EXCEPTION_STATUS_CLASSES: Record<string, string> = {
  OPEN: 'enterprise-badge-error',
  IN_PROGRESS: 'enterprise-badge-warning',
  RESOLVED: 'enterprise-badge-success',
  ESCALATED: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)] dark:bg-[var(--nexus-ai-900)]/30 dark:text-[var(--nexus-ai-300)]',
  CLOSED: 'enterprise-badge-neutral',
}

const STRATEGY_BADGES: Record<string, string> = {
  RULE_BASED: 'enterprise-badge-info',
  AI_OPTIMIZED: 'enterprise-badge-ai',
  HYBRID: 'enterprise-badge-success',
  MANUAL: 'enterprise-badge-neutral',
}

export default function OrderRoutingPage() {
  const [kpis, setKpis] = useState<Record<string, number> | null>(null)
  const [exceptions, setExceptions] = useState<FulfillmentException[]>([])
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState<'exceptions' | 'allocations'>('exceptions')
  const [exceptionFilter, setExceptionFilter] = useState<string>('OPEN')
  const [search, setSearch] = useState('')
  const [selectedException, setSelectedException] = useState<FulfillmentException | null>(null)
  const [resolveOpen, setResolveOpen] = useState(false)
  const [resolveForm, setResolveForm] = useState({ resolution: '', strategy: 'REALLOCATE' })
  const [allocations, setAllocations] = useState<any[]>([])
  const [processing, setProcessing] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const mountedRef = useRef(false)
  const { addToast } = useToast()

  const fetchData = useCallback(async () => {
    try {
      const [kpiRes, excRes, allocRes] = await Promise.all([
        orderRoutingApi.getRoutingKPIs(),
        orderRoutingApi.getExceptions({
          status: exceptionFilter === 'ALL' ? undefined : exceptionFilter,
          size: 50,
        }),
        orderRoutingApi.getAllocations(''),
      ])
      setKpis(kpiRes.data as Record<string, number>)
      setExceptions(excRes.data?.content || [])
      setAllocations(allocRes.data?.content || [])
    } catch {
      if (mountedRef.current) addToast({ type: 'error', title: 'Failed to load routing data' })
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [exceptionFilter, addToast])

  useEffect(() => {
    mountedRef.current = true
    fetchData()
    return () => { mountedRef.current = false }
  }, [fetchData])

  const handleRefresh = () => {
    setRefreshing(true)
    fetchData()
  }

  const handleResolve = async () => {
    if (!selectedException || !resolveForm.resolution) return
    setProcessing(true)
    try {
      await orderRoutingApi.resolveException(selectedException.id, {
        resolution: resolveForm.resolution,
        resolutionStrategy: resolveForm.strategy,
      })
      addToast({ type: 'success', title: 'Exception resolved' })
      setResolveOpen(false)
      setSelectedException(null)
      setResolveForm({ resolution: '', strategy: 'REALLOCATE' })
      fetchData()
    } catch {
      addToast({ type: 'error', title: 'Failed to resolve exception' })
    } finally {
      setProcessing(false)
    }
  }

  const handleEscalate = async (id: string) => {
    try {
      await orderRoutingApi.escalateException(id)
      addToast({ type: 'success', title: 'Exception escalated' })
      fetchData()
    } catch {
      addToast({ type: 'error', title: 'Failed to escalate exception' })
    }
  }

  const totalExceptions = kpis ? (kpis.openExceptions || 0) + (kpis.criticalExceptions || 0) : 0

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="enterprise-page-header">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-2xl bg-violet-50 dark:bg-violet-900/20 flex items-center justify-center">
            <Route className="w-6 h-6 text-violet-600" />
          </div>
          <div>
            <h1>AI Order Routing</h1>
            <p>Intelligent order allocation with multi-node optimization and exception resolution</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm"
          >
            <RefreshCw className={clsx('w-4 h-4', refreshing && 'animate-spin')} />
            Refresh
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="enterprise-kpi-grid">
        {[
          { label: 'Allocations Today', value: kpis?.totalAllocationsToday ?? 0, icon: TrendingUp, color: 'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-600)] dark:bg-[var(--nexus-primary-900)]/20 dark:text-[var(--nexus-primary-400)]', trend: kpis?.totalAllocationsToday ? `${kpis.totalAllocationsToday} total` : 'No data' },
          { label: 'Active Allocations', value: kpis?.activeAllocations ?? 0, icon: Zap, color: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-900/20 dark:text-emerald-400', trend: 'Currently allocated' },
          { label: 'Open Exceptions', value: kpis?.openExceptions ?? 0, icon: AlertTriangle, color: 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-600)] dark:bg-[var(--nexus-warning-900)]/20 dark:text-[var(--nexus-warning-400)]', trend: 'Requiring attention' },
          { label: 'Critical Exceptions', value: kpis?.criticalExceptions ?? 0, icon: XCircle, color: kpis?.criticalExceptions ? 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-400)]' : 'bg-[var(--surface-sunken)] text-[var(--text-secondary)] bg-[var(--surface-base)] dark:text-[var(--text-tertiary)]', trend: kpis?.criticalExceptions ? 'Requires immediate action' : 'All clear' },
        ].map(kpi => (
          <div key={kpi.label} className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-sm text-[var(--text-secondary)]">{kpi.label}</span>
              <div className={clsx('w-9 h-9 rounded-xl flex items-center justify-center', kpi.color)}>
                <kpi.icon className="w-4.5 h-4.5" />
              </div>
            </div>
            <div className="text-2xl font-bold text-[var(--text-primary)]">
              {kpi.value}
            </div>
            <div className="mt-1.5 pt-2 border-t border-[var(--border-subtle)] text-xs text-[var(--text-secondary)]">
              {kpi.trend}
            </div>
          </div>
        ))}
      </div>

      {/* Tabs */}
      <div className="enterprise-tabs">
        <button
          onClick={() => setTab('exceptions')}
          className={clsx('enterprise-tab', tab === 'exceptions' && 'enterprise-tab-active')}
        >
          <AlertTriangle className="w-4 h-4" /> Exceptions
          {totalExceptions > 0 && (
            <span className="ml-1.5 px-1.5 py-0.5 text-[10px] font-bold bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-300)] rounded-full">
              {totalExceptions}
            </span>
          )}
        </button>
        <button
          onClick={() => setTab('allocations')}
          className={clsx('enterprise-tab', tab === 'allocations' && 'enterprise-tab-active')}
        >
          <Route className="w-4 h-4" /> Allocation History
        </button>
      </div>

      {/* Exceptions View */}
      {tab === 'exceptions' && (
        <div className="space-y-3">
          <div className="enterprise-toolbar">
            <div className="enterprise-toolbar-left">
              <Autocomplete value={search} onChange={setSearch} placeholder="Search exceptions..." minChars={0} />
              <select
                className="enterprise-select w-40"
                value={exceptionFilter}
                onChange={e => setExceptionFilter(e.target.value)}
              >
                <option value="ALL">All Status</option>
                <option value="OPEN">Open</option>
                <option value="IN_PROGRESS">In Progress</option>
                <option value="RESOLVED">Resolved</option>
                <option value="ESCALATED">Escalated</option>
                <option value="CLOSED">Closed</option>
              </select>
            </div>
          </div>

          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map(i => (
                <div key={i} className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
                  <div className="enterprise-skeleton h-5 w-48 mb-3" />
                  <div className="enterprise-skeleton h-4 w-96 mb-2" />
                  <div className="enterprise-skeleton h-4 w-64" />
                </div>
              ))}
            </div>
          ) : exceptions.length === 0 ? (
            <div className="enterprise-empty-state py-16">
              <CheckCircle2 className="w-12 h-12 text-emerald-400" />
              <h3>No exceptions found</h3>
              <p>All orders are routing smoothly</p>
            </div>
          ) : (
            exceptions
              .filter(e => !search || e.title.toLowerCase().includes(search.toLowerCase()) || e.type.toLowerCase().includes(search.toLowerCase()))
              .map(exception => {
                const Icon = EXCEPTION_TYPE_ICONS[exception.type] || AlertTriangle
                return (
                  <div key={exception.id} className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5 transition-shadow hover:shadow-sm">
                    <div className="flex items-start gap-4">
                      <div className={clsx('w-10 h-10 rounded-xl flex items-center justify-center shrink-0 mt-0.5',
                        exception.severity === 'CRITICAL' && 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-400)]',
                        exception.severity === 'HIGH' && 'bg-orange-50 text-orange-600 dark:bg-orange-900/20 dark:text-orange-400',
                        exception.severity === 'MEDIUM' && 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-600)] dark:bg-[var(--nexus-warning-900)]/20 dark:text-[var(--nexus-warning-400)]',
                        exception.severity === 'LOW' && 'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-600)] dark:bg-[var(--nexus-primary-900)]/20 dark:text-[var(--nexus-primary-400)]',
                      )}>
                        <Icon className="w-5 h-5" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-start justify-between">
                          <div>
                            <div className="flex items-center gap-2.5">
                              <h4 className="font-semibold text-[var(--text-primary)]">{exception.title}</h4>
                              <span className={clsx('enterprise-badge', EXCEPTION_SEVERITY_CLASSES[exception.severity])}>
                                {exception.severity}
                              </span>
                              <span className={clsx('enterprise-badge', EXCEPTION_STATUS_CLASSES[exception.status])}>
                                {exception.status.replace('_', ' ')}
                              </span>
                            </div>
                            {exception.description && (
                              <p className="text-sm text-[var(--text-secondary)] mt-1">{exception.description}</p>
                            )}
                          </div>
                          <div className="flex items-center gap-1.5 ml-4 shrink-0">
                            <span className={clsx(
                              'inline-flex items-center gap-1 text-[10px] font-medium px-2 py-0.5 rounded-md',
                              exception.autoResolvable
                                ? 'text-violet-600 dark:text-violet-400 bg-violet-50 dark:bg-violet-900/20'
                                : 'text-[var(--text-secondary)] bg-[var(--surface-sunken)] bg-[var(--surface-base)]'
                            )}>
                              <Brain className="w-3 h-3" />
                              {exception.autoResolvable ? 'Auto-resolve available' : 'Needs manual review'}
                            </span>
                            <PermissionGate resource="orders" action="edit">
                              <button
                                onClick={() => { setSelectedException(exception); setResolveOpen(true) }}
                                className="enterprise-btn enterprise-btn-xs enterprise-btn-secondary"
                                disabled={exception.status === 'RESOLVED' || exception.status === 'CLOSED'}
                              >
                                <CheckCircle2 className="w-3 h-3" /> Resolve
                              </button>
                            </PermissionGate>
                            <PermissionGate resource="orders" action="edit">
                              <button
                                onClick={() => handleEscalate(exception.id)}
                                className="enterprise-btn enterprise-btn-xs bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)] border border-[var(--nexus-error-200)] hover:bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-400)] dark:border-[var(--nexus-error-800)]"
                                disabled={exception.status === 'RESOLVED' || exception.status === 'CLOSED' || exception.status === 'ESCALATED'}
                              >
                                <ArrowUpRight className="w-3 h-3" /> Escalate
                              </button>
                            </PermissionGate>
                          </div>
                        </div>
                        <div className="flex items-center gap-4 mt-3 text-xs text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] flex-wrap">
                          <span className="font-mono">#{exception.orderId?.slice(0, 8)}</span>
                          <span>{new Date(exception.detectedAt).toLocaleString()}</span>
                          {exception.resolutionStrategy && (
                            <span className="font-medium text-[var(--text-secondary)] dark:text-[var(--text-tertiary)]">
                              Suggested: {exception.resolutionStrategy.replace('_', ' ')}
                            </span>
                          )}
                          {exception.suggestedAction && (
                            <span className="text-violet-600 dark:text-violet-400">{exception.suggestedAction}</span>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                )
              })
          )}
        </div>
      )}

      {/* Allocations View */}
      {tab === 'allocations' && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-semibold text-[var(--text-primary)]">Allocation History</h3>
          </div>
          {allocations.length === 0 ? (
            <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-8">
              <div className="enterprise-empty-state">
                <Route className="w-12 h-12 text-[var(--nexus-primary-400)]" />
                <h3>No allocations yet</h3>
                <p>Order allocations will appear here once orders are routed</p>
              </div>
            </div>
          ) : (
            <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-[var(--border-default)] bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
                    <th className="px-4 py-3">Order</th>
                    <th className="px-4 py-3">Source</th>
                    <th className="px-4 py-3">Destination</th>
                    <th className="px-4 py-3">Strategy</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                  {(allocations as any[]).map((a: any, i: number) => (
                    <tr key={a.id || i} className="hover:bg-[var(--surface-sunken)] dark:hover:bg-[var(--surface-muted)]/30">
                      <td className="px-4 py-3 font-medium text-[var(--text-primary)]">{a.orderId || a.orderNumber || '-'}</td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">{a.sourceNode || a.source || '-'}</td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">{a.destinationNode || a.destination || '-'}</td>
                      <td className="px-4 py-3">
                        <span className={clsx('enterprise-badge', STRATEGY_BADGES[a.strategy] || 'enterprise-badge-neutral')}>{a.strategy || '-'}</span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx('enterprise-badge', a.status === 'ACTIVE' ? 'enterprise-badge-success' : 'enterprise-badge-neutral')}>{a.status || '-'}</span>
                      </td>
                      <td className="px-4 py-3 text-[var(--text-secondary)] text-xs">{a.createdAt ? new Date(a.createdAt).toLocaleDateString() : '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Resolve Modal */}
      {resolveOpen && selectedException && (
        <div className="enterprise-modal-overlay" onClick={() => setResolveOpen(false)}>
          <div className="enterprise-modal max-w-md" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-[var(--text-primary)]">Resolve Exception</h2>
              <button onClick={() => setResolveOpen(false)} className="p-1 text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] rounded-lg hover:bg-[var(--surface-muted)]">
                <XCircle className="w-5 h-5" />
              </button>
            </div>
            <div className="enterprise-modal-body space-y-5">
              <div className="bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/10 rounded-xl p-4 space-y-1.5">
                <p className="font-medium text-[var(--text-primary)] text-sm">{selectedException.title}</p>
                <p className="text-xs text-[var(--text-secondary)]">
                  {selectedException.severity} severity &middot; {selectedException.type.replace('_', ' ')}
                </p>
              </div>
              <div className="enterprise-form-group">
                <label>Resolution Strategy</label>
                <select
                  className="enterprise-select"
                  value={resolveForm.strategy}
                  onChange={e => setResolveForm(f => ({ ...f, strategy: e.target.value }))}
                >
                  <option value="REALLOCATE">Reallocate to another node</option>
                  <option value="OVERRIDE">Override exception</option>
                  <option value="CONTACT_CUSTOMER">Contact customer</option>
                  <option value="ESCALATE_MANAGER">Escalate to manager</option>
                  <option value="SPLIT_ORDER">Split order across nodes</option>
                  <option value="BACKORDER">Place on backorder</option>
                  <option value="SUBSTITUTE">Substitute product</option>
                  <option value="CANCEL_ORDER">Cancel order</option>
                </select>
              </div>
              <div className="enterprise-form-group">
                <label>Resolution Notes</label>
                <textarea
                  className="enterprise-textarea"
                  rows={3}
                  placeholder="Describe how this exception is being resolved..."
                  value={resolveForm.resolution}
                  onChange={e => setResolveForm(f => ({ ...f, resolution: e.target.value }))}
                />
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button onClick={() => setResolveOpen(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="orders" action="edit">
                <button
                  onClick={handleResolve}
                  disabled={processing || !resolveForm.resolution}
                  className="enterprise-btn enterprise-btn-primary disabled:opacity-50"
                >
                  {processing && <Loader2 className="w-4 h-4 animate-spin" />}
                  Resolve Exception
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
