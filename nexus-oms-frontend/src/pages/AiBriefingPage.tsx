import { useState, useEffect, useCallback } from 'react'
import {
  TrendingUp, AlertTriangle, Info, Calendar,
  DollarSign, ShoppingCart, Package, TrendingDown,
  CheckCircle, XCircle, Brain, ChevronRight,
  RefreshCw, ArrowUp, ArrowDown, BarChart3,
} from 'lucide-react'
import clsx from 'clsx'
import { getBriefing, getRecommendations, approveRecommendation, rejectRecommendation, getForecasts } from '../api/aiAgents'
import PermissionGate from '../components/rbac/PermissionGate'
import type { AiBriefing, AiRecommendation, AiForecast } from '../api/aiAgents'

function formatNumber(n: number): string {
  return n.toLocaleString('en-IN')
}

function formatIndianCurrency(value: number): string {
  if (value >= 10000000) return `\u20B9${(value / 10000000).toFixed(2)} Cr`
  if (value >= 100000) return `\u20B9${(value / 100000).toFixed(1)} L`
  if (value >= 1000) return `\u20B9${(value / 1000).toFixed(0)}K`
  return `\u20B9${value}`
}

function formatCompact(n: number): string {
  if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return n.toString()
}

function getGreeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

const INSIGHT_ICONS: Record<string, React.ReactNode> = {
  'trending-up': <TrendingUp className="w-4 h-4" />,
  'alert-triangle': <AlertTriangle className="w-4 h-4" />,
  'info': <Info className="w-4 h-4" />,
}

const INSIGHT_BG: Record<string, string> = {
  positive: 'bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 border-[var(--nexus-success-200)] dark:border-[var(--nexus-success-800)] text-[var(--nexus-success-700)] dark:text-[var(--nexus-success-300)]',
  negative: 'bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 border-[var(--nexus-error-200)] dark:border-[var(--nexus-error-800)] text-[var(--nexus-error-700)] dark:text-[var(--nexus-error-300)]',
  neutral: 'bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/20 border-[var(--nexus-primary-200)] dark:border-[var(--nexus-primary-800)] text-[var(--nexus-primary-700)] dark:text-[var(--nexus-primary-300)]',
}

const INSIGHT_ICON_BG: Record<string, string> = {
  positive: 'bg-[var(--nexus-success-100)] dark:bg-[var(--nexus-success-900)]/30 text-[var(--nexus-success-600)] dark:text-[var(--nexus-success-400)]',
  negative: 'bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/30 text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]',
  neutral: 'bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)]/30 text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]',
}

const PRIORITY_STYLES: Record<string, string> = {
  high: 'bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 text-[var(--nexus-error-700)] dark:text-[var(--nexus-error-300)] border-[var(--nexus-error-200)] dark:border-[var(--nexus-error-800)]',
  medium: 'bg-[var(--nexus-warning-100)] dark:bg-[var(--nexus-warning-900)]/20 text-[var(--nexus-warning-700)] dark:text-[var(--nexus-warning-300)] border-[var(--nexus-warning-200)] dark:border-[var(--nexus-warning-800)]',
  low: 'bg-[var(--surface-muted)] text-[var(--text-secondary)] border-[var(--border-default)] border-[var(--border-default)]',
}

const SPARKLINE_DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

export default function AiBriefingPage() {
  const [briefing, setBriefing] = useState<AiBriefing | null>(null)
  const [recommendations, setRecommendations] = useState<AiRecommendation[]>([])
  const [forecasts, setForecastsData] = useState<AiForecast[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [handlingRecs, setHandlingRecs] = useState<Set<string>>(new Set())

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [briefingRes, recsRes, forecastsRes] = await Promise.all([
        getBriefing(),
        getRecommendations(),
        getForecasts(),
      ])
      setBriefing(briefingRes)
      setRecommendations(recsRes)
      setForecastsData(forecastsRes)
    } catch {
      setError('Failed to load briefing data')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchData() }, [fetchData])

  const handleApprove = async (id: string) => {
    setHandlingRecs(prev => new Set(prev).add(id))
    try {
      await approveRecommendation(id)
      setRecommendations(prev => prev.map(r => r.id === id ? { ...r, status: 'approved' } : r))
    } catch { /* ignore */ }
    finally {
      setHandlingRecs(prev => { const n = new Set(prev); n.delete(id); return n })
    }
  }

  const handleReject = async (id: string) => {
    setHandlingRecs(prev => new Set(prev).add(id))
    try {
      await rejectRecommendation(id)
      setRecommendations(prev => prev.map(r => r.id === id ? { ...r, status: 'rejected' } : r))
    } catch { /* ignore */ }
    finally {
      setHandlingRecs(prev => { const n = new Set(prev); n.delete(id); return n })
    }
  }

  const today = new Date()
  const dateStr = today.toLocaleDateString('en-IN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })
  const greeting = getGreeting()

  const overallConfidence = forecasts.length > 0
    ? Math.round(forecasts.reduce((acc, f) => acc + f.confidence, 0) / forecasts.length)
    : 93

  const revenueSparklineData = briefing
    ? [briefing.revenue.yesterday * 0.8, briefing.revenue.yesterday * 0.9, briefing.revenue.yesterday, briefing.revenue.today * 0.85, briefing.revenue.today * 0.95, briefing.revenue.today * 1.02, briefing.revenue.today]
    : [2.1, 2.4, 2.8, 2.5, 2.9, 3.1, 2.84]

  const ordersTrendData = briefing
    ? [briefing.orders.today * 0.85, briefing.orders.today * 0.9, briefing.orders.today * 0.95, briefing.orders.today * 0.88, briefing.orders.today * 0.97, briefing.orders.today * 1.03, briefing.orders.today]
    : [11200, 11800, 12400, 12100, 12700, 13200, 12438]

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="enterprise-page-header">
          <div>
            <div className="h-7 w-48 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded animate-pulse" />
            <div className="h-4 w-64 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded mt-2 animate-pulse" />
          </div>
        </div>
        <div className="bg-gradient-to-r from-gray-300 to-gray-400 dark:from-gray-700 dark:to-gray-600 rounded-xl p-6 animate-pulse">
          <div className="h-5 w-40 bg-white/30 rounded" />
          <div className="h-8 w-56 bg-white/30 rounded mt-2" />
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5 animate-pulse">
              <div className="h-4 w-20 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded" />
              <div className="h-8 w-28 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded mt-3" />
              <div className="h-3 w-16 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded mt-2" />
            </div>
          ))}
        </div>
        <div className="h-12 bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] animate-pulse" />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6 animate-pulse">
            <div className="h-5 w-40 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded" />
            <div className="h-3 w-56 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded mt-2" />
            {[1, 2, 3].map(i => (
              <div key={i} className="mt-4 h-16 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded-lg" />
            ))}
          </div>
          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6 animate-pulse">
            <div className="h-5 w-40 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded" />
            <div className="h-3 w-56 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded mt-2" />
            {[1, 2, 3].map(i => (
              <div key={i} className="mt-4 h-24 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded-lg" />
            ))}
          </div>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">
        <AlertTriangle className="w-12 h-12 mb-4" />
        <p className="text-lg font-medium text-[var(--text-secondary)]">{error}</p>
        <button onClick={fetchData} className="enterprise-btn enterprise-btn-primary mt-4">
          <RefreshCw className="w-4 h-4" /> Retry
        </button>
      </div>
    )
  }

  if (!briefing) return null

  return (
    <div className="space-y-6">
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5">
            <Brain className="w-7 h-7 text-[var(--nexus-primary-500)]" /> AI Briefing
          </h1>
          <p className="flex items-center gap-2 mt-1">
            <Calendar className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />
            {dateStr}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={fetchData} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm" disabled={loading}>
            <RefreshCw className={clsx('w-3.5 h-3.5', loading && 'animate-spin')} /> Refresh
          </button>
        </div>
      </div>

      <div className="bg-gradient-to-r from-primary-500 to-primary-700 rounded-xl p-6 text-white">
        <p className="text-lg font-medium opacity-90">{greeting}, Admin</p>
        <p className="text-3xl font-bold mt-1">Here's your daily briefing</p>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
          <div className="flex items-center gap-2 text-[var(--text-secondary)] text-xs font-medium uppercase tracking-wider">
            <DollarSign className="w-3.5 h-3.5" /> Revenue Today
          </div>
          <p className="text-2xl font-bold text-[var(--text-primary)] mt-2">{formatIndianCurrency(briefing.revenue.today)}</p>
          <div className="flex items-center gap-1 mt-1 text-xs">
            <ArrowUp className="w-3 h-3 text-[var(--nexus-success-500)]" />
            <span className="text-[var(--nexus-success-600)] dark:text-[var(--nexus-success-400)] font-medium">
              +{Math.round((briefing.revenue.today - briefing.revenue.yesterday) / briefing.revenue.yesterday * 100)}%
            </span>
            <span className="text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">vs yesterday</span>
          </div>
        </div>
        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
          <div className="flex items-center gap-2 text-[var(--text-secondary)] text-xs font-medium uppercase tracking-wider">
            <ShoppingCart className="w-3.5 h-3.5" /> Orders Today
          </div>
          <p className="text-2xl font-bold text-[var(--text-primary)] mt-2">{formatNumber(briefing.orders.today)}</p>
          <div className="flex items-center gap-1 mt-1 text-xs">
            <span className="text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)] font-medium">{formatNumber(briefing.orders.pending)} pending</span>
            {briefing.orders.late > 0 && (
              <>
                <span className="text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">·</span>
                <span className="text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)] font-medium">{briefing.orders.late} late</span>
              </>
            )}
          </div>
        </div>
        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
          <div className="flex items-center gap-2 text-[var(--text-secondary)] text-xs font-medium uppercase tracking-wider">
            <TrendingUp className="w-3.5 h-3.5" /> Profit Today
          </div>
          <p className="text-2xl font-bold text-[var(--text-primary)] mt-2">{formatIndianCurrency(briefing.profit.today)}</p>
          <div className="flex items-center gap-1 mt-1 text-xs">
            <span className="text-[var(--text-secondary)]">Margin</span>
            <span className="text-[var(--nexus-success-600)] dark:text-[var(--nexus-success-400)] font-medium">{(briefing.profit.margin * 100).toFixed(1)}%</span>
          </div>
        </div>
        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
          <div className="flex items-center gap-2 text-[var(--text-secondary)] text-xs font-medium uppercase tracking-wider">
            <Package className="w-3.5 h-3.5" /> Inventory Total
          </div>
          <p className="text-2xl font-bold text-[var(--text-primary)] mt-2">{formatCompact(briefing.inventory.total)}</p>
          <div className="flex items-center gap-1 mt-1 text-xs">
            <span className="text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)] font-medium">{briefing.inventory.lowStock} low stock</span>
            <span className="text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">·</span>
            <span className="text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)] font-medium">{briefing.inventory.deadStock} dead</span>
          </div>
        </div>
      </div>

      <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm font-medium text-[var(--text-secondary)]">Overall Briefing Confidence</span>
          <span className="text-sm font-bold text-[var(--text-brand)]">{overallConfidence}%</span>
        </div>
        <div className="w-full h-3 bg-[var(--surface-muted)] rounded-full overflow-hidden">
          <div
            className="h-full rounded-full bg-gradient-to-r from-primary-400 to-primary-600 transition-all duration-700"
            style={{ width: `${overallConfidence}%` }}
          />
        </div>
      </div>

      <div>
        <h3 className="text-sm font-semibold text-[var(--text-secondary)] uppercase tracking-wider mb-3">AI Insights</h3>
        <div className="flex gap-3 overflow-x-auto pb-2">
          {briefing.insights.map((insight, i) => (
            <div key={i} className={clsx('flex items-center gap-3 px-4 py-3 rounded-xl border min-w-[280px] flex-shrink-0', INSIGHT_BG[insight.type])}>
              <div className={clsx('w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0', INSIGHT_ICON_BG[insight.type])}>
                {INSIGHT_ICONS[insight.icon] || <Info className="w-4 h-4" />}
              </div>
              <p className="text-sm font-medium">{insight.text}</p>
            </div>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="space-y-6">
          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
            <div className="px-6 py-4 border-b border-[var(--border-default)]">
              <h3 className="font-semibold text-[var(--text-primary)] flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-[var(--nexus-error-500)]" /> Risks
              </h3>
            </div>
            <div className="p-5 space-y-3">
              {briefing.risks.map((risk, i) => (
                <div key={i} className="border-l-4 border-[var(--nexus-error-500)] pl-4 py-3 pr-4 rounded-r-lg bg-[var(--nexus-error-50)]/50 dark:bg-[var(--nexus-error-900)]/10">
                  <div className="flex items-center gap-2">
                    <span className={clsx(
                      'text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded',
                      risk.severity === 'high' && 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-300)]',
                      risk.severity === 'medium' && 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/30 dark:text-[var(--nexus-warning-300)]',
                      risk.severity === 'low' && 'bg-[var(--surface-muted)] text-[var(--text-secondary)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)]',
                    )}>
                      {risk.severity}
                    </span>
                  </div>
                  <p className="text-sm font-medium text-[var(--text-primary)] mt-1">{risk.title}</p>
                  <p className="text-xs text-[var(--text-secondary)] mt-0.5">{risk.description}</p>
                </div>
              ))}
            </div>
          </div>

          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
            <div className="px-6 py-4 border-b border-[var(--border-default)]">
              <h3 className="font-semibold text-[var(--text-primary)] flex items-center gap-2">
                <TrendingUp className="w-4 h-4 text-[var(--nexus-success-500)]" /> Opportunities
              </h3>
            </div>
            <div className="p-5 space-y-3">
              {briefing.opportunities.map((opp, i) => (
                <div key={i} className="border-l-4 border-[var(--nexus-success-500)] pl-4 py-3 pr-4 rounded-r-lg bg-[var(--nexus-success-50)]/50 dark:bg-[var(--nexus-success-900)]/10">
                  <p className="text-sm font-medium text-[var(--text-primary)]">{opp.title}</p>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="text-xs font-semibold text-[var(--nexus-success-600)] dark:text-[var(--nexus-success-400)] bg-[var(--nexus-success-100)] dark:bg-[var(--nexus-success-900)]/20 px-2 py-0.5 rounded-full">
                      {opp.potential}
                    </span>
                    <span className="text-xs text-[var(--text-secondary)]">{opp.action}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
          <div className="px-6 py-4 border-b border-[var(--border-default)]">
            <h3 className="font-semibold text-[var(--text-primary)] flex items-center gap-2">
              <Brain className="w-4 h-4 text-[var(--nexus-ai-500)]" /> AI Recommendations
            </h3>
          </div>
          <div className="p-5 space-y-4 max-h-[600px] overflow-y-auto">
            {recommendations.map(rec => (
              <div key={rec.id} className="border border-[var(--border-default)] rounded-xl p-4 hover:border-[var(--border-default)] dark:hover:border-[var(--border-default)] transition-colors">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                      <span className={clsx('text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded-full border', PRIORITY_STYLES[rec.impact])}>
                        {rec.impact}
                      </span>
                      {rec.status === 'approved' && (
                        <span className="text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded-full bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] dark:bg-[var(--nexus-success-900)]/30 dark:text-[var(--nexus-success-300)] border border-[var(--nexus-success-200)] dark:border-[var(--nexus-success-800)]">
                          Approved
                        </span>
                      )}
                      {rec.status === 'rejected' && (
                        <span className="text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded-full bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-300)] border border-[var(--nexus-error-200)] dark:border-[var(--nexus-error-800)]">
                          Rejected
                        </span>
                      )}
                    </div>
                    <p className="text-sm font-medium text-[var(--text-primary)]">{rec.title}</p>
                    <p className="text-xs text-[var(--text-secondary)] mt-1">{rec.description}</p>
                    <div className="flex items-center gap-2 mt-2">
                      <span className="text-xs font-medium text-[var(--text-secondary)]">Suggested: </span>
                      <span className="text-xs font-semibold text-[var(--text-brand)]">{rec.suggestedAction}</span>
                    </div>
                    <div className="mt-3">
                      <div className="flex justify-between text-xs text-[var(--text-secondary)] mb-1">
                        <span>Confidence</span>
                        <span>{rec.confidence}%</span>
                      </div>
                      <div className="w-full h-1.5 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                        <div
                          className={clsx('h-full rounded-full transition-all', rec.confidence >= 90 ? 'bg-[var(--nexus-success-50)]0' : rec.confidence >= 80 ? 'bg-[var(--nexus-warning-50)]0' : 'bg-[var(--nexus-error-50)]0')}
                          style={{ width: `${rec.confidence}%` }}
                        />
                      </div>
                    </div>
                  </div>
                </div>
                {rec.status === 'pending' && (
                  <div className="flex items-center gap-2 mt-3 pt-3 border-t border-[var(--border-subtle)]">
                    <PermissionGate resource="settings" action="edit">
                      <button
                        onClick={() => handleApprove(rec.id)}
                        disabled={handlingRecs.has(rec.id)}
                        className="flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 text-[var(--nexus-success-700)] dark:text-[var(--nexus-success-300)] border border-[var(--nexus-success-200)] dark:border-[var(--nexus-success-800)] hover:bg-[var(--nexus-success-100)] dark:hover:bg-[var(--nexus-success-900)]/30 transition-colors disabled:opacity-50"
                      >
                        <CheckCircle className="w-3.5 h-3.5" /> Approve
                      </button>
                    </PermissionGate>
                    <PermissionGate resource="settings" action="edit">
                      <button
                        onClick={() => handleReject(rec.id)}
                        disabled={handlingRecs.has(rec.id)}
                        className="flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 text-[var(--nexus-error-700)] dark:text-[var(--nexus-error-300)] border border-[var(--nexus-error-200)] dark:border-[var(--nexus-error-800)] hover:bg-[var(--nexus-error-50)] dark:hover:bg-[var(--nexus-error-900)]/30 transition-colors disabled:opacity-50"
                      >
                        <XCircle className="w-3.5 h-3.5" /> Reject
                      </button>
                    </PermissionGate>
                    {rec.reasoning.length > 0 && (
                      <div className="ml-auto flex items-center gap-1 text-xs text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">
                        <Brain className="w-3 h-3" /> {rec.reasoning.length} reasons
                      </div>
                    )}
                  </div>
                )}
                {rec.status === 'auto-approved' && (
                  <div className="mt-3 pt-3 border-t border-[var(--border-subtle)]">
                    <span className="text-xs text-[var(--text-secondary)] flex items-center gap-1">
                      <CheckCircle className="w-3 h-3 text-[var(--nexus-success-500)]" /> Auto-approved by {rec.agentName}
                    </span>
                  </div>
                )}
              </div>
            ))}
            {recommendations.length === 0 && (
              <p className="text-sm text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] text-center py-8">No recommendations available</p>
            )}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-semibold text-[var(--text-primary)]">Revenue Trend</h3>
              <p className="text-xs text-[var(--text-secondary)] mt-0.5">Daily revenue this week</p>
            </div>
            <DollarSign className="w-5 h-5 text-[var(--text-tertiary)]" />
          </div>
          <div className="h-32">
            <svg viewBox="0 0 280 100" className="w-full h-full" preserveAspectRatio="none">
              <defs>
                <linearGradient id="revenueGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.3" />
                  <stop offset="100%" stopColor="#3B82F6" stopOpacity="0" />
                </linearGradient>
              </defs>
              {revenueSparklineData.map((val, i) => {
                const barWidth = 28
                const gap = 8
                const x = i * (barWidth + gap) + 10
                const maxVal = Math.max(...revenueSparklineData)
                const barHeight = (val / maxVal) * 75
                return (
                  <rect key={i} x={x} y={85 - barHeight} width={barWidth} height={barHeight} rx={3} fill="url(#revenueGradient)" />
                )
              })}
              {revenueSparklineData.map((val, i) => {
                const barWidth = 28
                const gap = 8
                const x = i * (barWidth + gap) + 10
                const maxVal = Math.max(...revenueSparklineData)
                const barHeight = (val / maxVal) * 75
                return (
                  <rect key={`fg-${i}`} x={x} y={85 - barHeight} width={barWidth} height={barHeight} rx={3} fill="#3B82F6" opacity={0.6} />
                )
              })}
              {SPARKLINE_DAYS.map((day, i) => {
                const barWidth = 28
                const gap = 8
                const x = i * (barWidth + gap) + 10
                return (
                  <text key={i} x={x + barWidth / 2} y={98} textAnchor="middle" className="text-[8px]" fill="#9CA3AF">
                    {day}
                  </text>
                )
              })}
            </svg>
          </div>
        </div>

        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-semibold text-[var(--text-primary)]">Orders Trend</h3>
              <p className="text-xs text-[var(--text-secondary)] mt-0.5">Daily orders this week</p>
            </div>
            <ShoppingCart className="w-5 h-5 text-[var(--text-tertiary)]" />
          </div>
          <div className="h-32">
            <svg viewBox="0 0 280 100" className="w-full h-full" preserveAspectRatio="none">
              <defs>
                <linearGradient id="ordersGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#10B981" stopOpacity="0.3" />
                  <stop offset="100%" stopColor="#10B981" stopOpacity="0" />
                </linearGradient>
              </defs>
              {ordersTrendData.map((val, i) => {
                const barWidth = 28
                const gap = 8
                const x = i * (barWidth + gap) + 10
                const maxVal = Math.max(...ordersTrendData)
                const barHeight = (val / maxVal) * 75
                return (
                  <rect key={i} x={x} y={85 - barHeight} width={barWidth} height={barHeight} rx={3} fill="url(#ordersGradient)" />
                )
              })}
              {ordersTrendData.map((val, i) => {
                const barWidth = 28
                const gap = 8
                const x = i * (barWidth + gap) + 10
                const maxVal = Math.max(...ordersTrendData)
                const barHeight = (val / maxVal) * 75
                return (
                  <rect key={`fg-${i}`} x={x} y={85 - barHeight} width={barWidth} height={barHeight} rx={3} fill="#10B981" opacity={0.6} />
                )
              })}
              {SPARKLINE_DAYS.map((day, i) => {
                const barWidth = 28
                const gap = 8
                const x = i * (barWidth + gap) + 10
                return (
                  <text key={i} x={x + barWidth / 2} y={98} textAnchor="middle" className="text-[8px]" fill="#9CA3AF">
                    {day}
                  </text>
                )
              })}
            </svg>
          </div>
        </div>
      </div>

      <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6">
        <div className="flex items-center gap-2 mb-5">
          <BarChart3 className="w-5 h-5 text-[var(--nexus-primary-500)]" />
          <h3 className="font-semibold text-[var(--text-primary)]">Forecast Summary</h3>
        </div>
        {forecasts.length === 0 ? (
          <p className="text-sm text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] text-center py-8">No forecast data available</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {forecasts.map((forecast, i) => (
              <div key={i} className="p-4 rounded-lg bg-[var(--surface-muted)] border border-[var(--border-subtle)]">
                <p className="text-xs font-medium text-[var(--text-secondary)] uppercase tracking-wider">{forecast.metric}</p>
                <p className="text-xl font-bold text-[var(--text-primary)] mt-1">
                  {forecast.current}
                  <span className="text-sm font-normal text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] ml-1">{forecast.unit}</span>
                </p>
                <div className="flex items-end gap-0.5 mt-3 h-8">
                  {forecast.predicted.map((v, j) => {
                    const maxP = Math.max(...forecast.predicted)
                    const h = (v / maxP) * 100
                    const isUp = j > 0 && v >= forecast.predicted[j - 1]
                    return (
                      <div
                        key={j}
                        className={clsx('flex-1 rounded-t transition-all', isUp ? 'bg-[var(--nexus-success-400)]' : 'bg-[var(--nexus-error-400)]')}
                        style={{ height: `${Math.max(h, 8)}%` }}
                      />
                    )
                  })}
                </div>
                <div className="flex items-center justify-between mt-2">
                  <span className="text-[10px] text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">{forecast.period}</span>
                  <span className="text-[10px] font-medium text-[var(--text-brand)]">{forecast.confidence}% confidence</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
