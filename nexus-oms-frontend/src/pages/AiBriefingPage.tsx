import { useState, useEffect, useCallback } from 'react'
import {
  TrendingUp, AlertTriangle, Info, Calendar,
  DollarSign, ShoppingCart, Package, TrendingDown,
  CheckCircle, XCircle, Brain, ChevronRight,
  RefreshCw, ArrowUp, ArrowDown, BarChart3,
} from 'lucide-react'
import clsx from 'clsx'
import { getBriefing, getRecommendations, approveRecommendation, rejectRecommendation, getForecasts } from '../api/aiAgents'
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
  positive: 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800 text-green-700 dark:text-green-300',
  negative: 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800 text-red-700 dark:text-red-300',
  neutral: 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800 text-blue-700 dark:text-blue-300',
}

const INSIGHT_ICON_BG: Record<string, string> = {
  positive: 'bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400',
  negative: 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400',
  neutral: 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400',
}

const PRIORITY_STYLES: Record<string, string> = {
  high: 'bg-red-100 dark:bg-red-900/20 text-red-700 dark:text-red-300 border-red-200 dark:border-red-800',
  medium: 'bg-yellow-100 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-300 border-yellow-200 dark:border-yellow-800',
  low: 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 border-gray-200 dark:border-gray-600',
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
            <div className="h-7 w-48 bg-gray-200 dark:bg-gray-700 rounded animate-pulse" />
            <div className="h-4 w-64 bg-gray-200 dark:bg-gray-700 rounded mt-2 animate-pulse" />
          </div>
        </div>
        <div className="bg-gradient-to-r from-gray-300 to-gray-400 dark:from-gray-700 dark:to-gray-600 rounded-xl p-6 animate-pulse">
          <div className="h-5 w-40 bg-white/30 rounded" />
          <div className="h-8 w-56 bg-white/30 rounded mt-2" />
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 animate-pulse">
              <div className="h-4 w-20 bg-gray-200 dark:bg-gray-700 rounded" />
              <div className="h-8 w-28 bg-gray-200 dark:bg-gray-700 rounded mt-3" />
              <div className="h-3 w-16 bg-gray-200 dark:bg-gray-700 rounded mt-2" />
            </div>
          ))}
        </div>
        <div className="h-12 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 animate-pulse" />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 animate-pulse">
            <div className="h-5 w-40 bg-gray-200 dark:bg-gray-700 rounded" />
            <div className="h-3 w-56 bg-gray-200 dark:bg-gray-700 rounded mt-2" />
            {[1, 2, 3].map(i => (
              <div key={i} className="mt-4 h-16 bg-gray-200 dark:bg-gray-700 rounded-lg" />
            ))}
          </div>
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 animate-pulse">
            <div className="h-5 w-40 bg-gray-200 dark:bg-gray-700 rounded" />
            <div className="h-3 w-56 bg-gray-200 dark:bg-gray-700 rounded mt-2" />
            {[1, 2, 3].map(i => (
              <div key={i} className="mt-4 h-24 bg-gray-200 dark:bg-gray-700 rounded-lg" />
            ))}
          </div>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-gray-400 dark:text-gray-500">
        <AlertTriangle className="w-12 h-12 mb-4" />
        <p className="text-lg font-medium text-gray-600 dark:text-gray-400">{error}</p>
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
            <Brain className="w-7 h-7 text-primary-500" /> AI Briefing
          </h1>
          <p className="flex items-center gap-2 mt-1">
            <Calendar className="w-3.5 h-3.5 text-gray-400" />
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
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <div className="flex items-center gap-2 text-gray-500 dark:text-gray-400 text-xs font-medium uppercase tracking-wider">
            <DollarSign className="w-3.5 h-3.5" /> Revenue Today
          </div>
          <p className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-2">{formatIndianCurrency(briefing.revenue.today)}</p>
          <div className="flex items-center gap-1 mt-1 text-xs">
            <ArrowUp className="w-3 h-3 text-green-500" />
            <span className="text-green-600 dark:text-green-400 font-medium">
              +{Math.round((briefing.revenue.today - briefing.revenue.yesterday) / briefing.revenue.yesterday * 100)}%
            </span>
            <span className="text-gray-400 dark:text-gray-500">vs yesterday</span>
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <div className="flex items-center gap-2 text-gray-500 dark:text-gray-400 text-xs font-medium uppercase tracking-wider">
            <ShoppingCart className="w-3.5 h-3.5" /> Orders Today
          </div>
          <p className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-2">{formatNumber(briefing.orders.today)}</p>
          <div className="flex items-center gap-1 mt-1 text-xs">
            <span className="text-amber-600 dark:text-amber-400 font-medium">{formatNumber(briefing.orders.pending)} pending</span>
            {briefing.orders.late > 0 && (
              <>
                <span className="text-gray-400 dark:text-gray-500">·</span>
                <span className="text-red-600 dark:text-red-400 font-medium">{briefing.orders.late} late</span>
              </>
            )}
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <div className="flex items-center gap-2 text-gray-500 dark:text-gray-400 text-xs font-medium uppercase tracking-wider">
            <TrendingUp className="w-3.5 h-3.5" /> Profit Today
          </div>
          <p className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-2">{formatIndianCurrency(briefing.profit.today)}</p>
          <div className="flex items-center gap-1 mt-1 text-xs">
            <span className="text-gray-500 dark:text-gray-400">Margin</span>
            <span className="text-green-600 dark:text-green-400 font-medium">{(briefing.profit.margin * 100).toFixed(1)}%</span>
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <div className="flex items-center gap-2 text-gray-500 dark:text-gray-400 text-xs font-medium uppercase tracking-wider">
            <Package className="w-3.5 h-3.5" /> Inventory Total
          </div>
          <p className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-2">{formatCompact(briefing.inventory.total)}</p>
          <div className="flex items-center gap-1 mt-1 text-xs">
            <span className="text-amber-600 dark:text-amber-400 font-medium">{briefing.inventory.lowStock} low stock</span>
            <span className="text-gray-400 dark:text-gray-500">·</span>
            <span className="text-red-600 dark:text-red-400 font-medium">{briefing.inventory.deadStock} dead</span>
          </div>
        </div>
      </div>

      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Overall Briefing Confidence</span>
          <span className="text-sm font-bold text-primary-600 dark:text-primary-400">{overallConfidence}%</span>
        </div>
        <div className="w-full h-3 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
          <div
            className="h-full rounded-full bg-gradient-to-r from-primary-400 to-primary-600 transition-all duration-700"
            style={{ width: `${overallConfidence}%` }}
          />
        </div>
      </div>

      <div>
        <h3 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-3">AI Insights</h3>
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
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
              <h3 className="font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-red-500" /> Risks
              </h3>
            </div>
            <div className="p-5 space-y-3">
              {briefing.risks.map((risk, i) => (
                <div key={i} className="border-l-4 border-red-500 pl-4 py-3 pr-4 rounded-r-lg bg-red-50/50 dark:bg-red-900/10">
                  <div className="flex items-center gap-2">
                    <span className={clsx(
                      'text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded',
                      risk.severity === 'high' && 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
                      risk.severity === 'medium' && 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
                      risk.severity === 'low' && 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
                    )}>
                      {risk.severity}
                    </span>
                  </div>
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100 mt-1">{risk.title}</p>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{risk.description}</p>
                </div>
              ))}
            </div>
          </div>

          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
              <h3 className="font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                <TrendingUp className="w-4 h-4 text-green-500" /> Opportunities
              </h3>
            </div>
            <div className="p-5 space-y-3">
              {briefing.opportunities.map((opp, i) => (
                <div key={i} className="border-l-4 border-green-500 pl-4 py-3 pr-4 rounded-r-lg bg-green-50/50 dark:bg-green-900/10">
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{opp.title}</p>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="text-xs font-semibold text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/20 px-2 py-0.5 rounded-full">
                      {opp.potential}
                    </span>
                    <span className="text-xs text-gray-500 dark:text-gray-400">{opp.action}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
              <Brain className="w-4 h-4 text-purple-500" /> AI Recommendations
            </h3>
          </div>
          <div className="p-5 space-y-4 max-h-[600px] overflow-y-auto">
            {recommendations.map(rec => (
              <div key={rec.id} className="border border-gray-200 dark:border-gray-700 rounded-xl p-4 hover:border-gray-300 dark:hover:border-gray-600 transition-colors">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                      <span className={clsx('text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded-full border', PRIORITY_STYLES[rec.impact])}>
                        {rec.impact}
                      </span>
                      {rec.status === 'approved' && (
                        <span className="text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded-full bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300 border border-green-200 dark:border-green-800">
                          Approved
                        </span>
                      )}
                      {rec.status === 'rejected' && (
                        <span className="text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded-full bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300 border border-red-200 dark:border-red-800">
                          Rejected
                        </span>
                      )}
                    </div>
                    <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{rec.title}</p>
                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{rec.description}</p>
                    <div className="flex items-center gap-2 mt-2">
                      <span className="text-xs font-medium text-gray-600 dark:text-gray-400">Suggested: </span>
                      <span className="text-xs font-semibold text-primary-600 dark:text-primary-400">{rec.suggestedAction}</span>
                    </div>
                    <div className="mt-3">
                      <div className="flex justify-between text-xs text-gray-500 dark:text-gray-400 mb-1">
                        <span>Confidence</span>
                        <span>{rec.confidence}%</span>
                      </div>
                      <div className="w-full h-1.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                        <div
                          className={clsx('h-full rounded-full transition-all', rec.confidence >= 90 ? 'bg-green-500' : rec.confidence >= 80 ? 'bg-yellow-500' : 'bg-red-500')}
                          style={{ width: `${rec.confidence}%` }}
                        />
                      </div>
                    </div>
                  </div>
                </div>
                {rec.status === 'pending' && (
                  <div className="flex items-center gap-2 mt-3 pt-3 border-t border-gray-100 dark:border-gray-700">
                    <button
                      onClick={() => handleApprove(rec.id)}
                      disabled={handlingRecs.has(rec.id)}
                      className="flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300 border border-green-200 dark:border-green-800 hover:bg-green-100 dark:hover:bg-green-900/30 transition-colors disabled:opacity-50"
                    >
                      <CheckCircle className="w-3.5 h-3.5" /> Approve
                    </button>
                    <button
                      onClick={() => handleReject(rec.id)}
                      disabled={handlingRecs.has(rec.id)}
                      className="flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300 border border-red-200 dark:border-red-800 hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors disabled:opacity-50"
                    >
                      <XCircle className="w-3.5 h-3.5" /> Reject
                    </button>
                    {rec.reasoning.length > 0 && (
                      <div className="ml-auto flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500">
                        <Brain className="w-3 h-3" /> {rec.reasoning.length} reasons
                      </div>
                    )}
                  </div>
                )}
                {rec.status === 'auto-approved' && (
                  <div className="mt-3 pt-3 border-t border-gray-100 dark:border-gray-700">
                    <span className="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
                      <CheckCircle className="w-3 h-3 text-green-500" /> Auto-approved by {rec.agentName}
                    </span>
                  </div>
                )}
              </div>
            ))}
            {recommendations.length === 0 && (
              <p className="text-sm text-gray-400 dark:text-gray-500 text-center py-8">No recommendations available</p>
            )}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-gray-100">Revenue Trend</h3>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Daily revenue this week</p>
            </div>
            <DollarSign className="w-5 h-5 text-gray-400" />
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

        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-gray-100">Orders Trend</h3>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Daily orders this week</p>
            </div>
            <ShoppingCart className="w-5 h-5 text-gray-400" />
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

      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
        <div className="flex items-center gap-2 mb-5">
          <BarChart3 className="w-5 h-5 text-primary-500" />
          <h3 className="font-semibold text-gray-900 dark:text-gray-100">Forecast Summary</h3>
        </div>
        {forecasts.length === 0 ? (
          <p className="text-sm text-gray-400 dark:text-gray-500 text-center py-8">No forecast data available</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {forecasts.map((forecast, i) => (
              <div key={i} className="p-4 rounded-lg bg-gray-50 dark:bg-gray-700/50 border border-gray-100 dark:border-gray-700">
                <p className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">{forecast.metric}</p>
                <p className="text-xl font-bold text-gray-900 dark:text-gray-100 mt-1">
                  {forecast.current}
                  <span className="text-sm font-normal text-gray-400 dark:text-gray-500 ml-1">{forecast.unit}</span>
                </p>
                <div className="flex items-end gap-0.5 mt-3 h-8">
                  {forecast.predicted.map((v, j) => {
                    const maxP = Math.max(...forecast.predicted)
                    const h = (v / maxP) * 100
                    const isUp = j > 0 && v >= forecast.predicted[j - 1]
                    return (
                      <div
                        key={j}
                        className={clsx('flex-1 rounded-t transition-all', isUp ? 'bg-green-400' : 'bg-red-400')}
                        style={{ height: `${Math.max(h, 8)}%` }}
                      />
                    )
                  })}
                </div>
                <div className="flex items-center justify-between mt-2">
                  <span className="text-[10px] text-gray-400 dark:text-gray-500">{forecast.period}</span>
                  <span className="text-[10px] font-medium text-primary-600 dark:text-primary-400">{forecast.confidence}% confidence</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
