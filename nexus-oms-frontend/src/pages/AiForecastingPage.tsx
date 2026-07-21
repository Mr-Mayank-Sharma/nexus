import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  BarChart3, TrendingUp, TrendingDown, AlertTriangle,
  Brain, RefreshCw, Search, Calendar, ArrowUp, ArrowDown,
} from 'lucide-react'
import clsx from 'clsx'
import { getForecasts, getSupplierRisks, getRecommendations } from '../api/aiAgents'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import type { AiForecast, AiSupplierRisk, AiRecommendation } from '../api/aiAgents'

function MiniSparkline({ data, color }: { data: number[]; color: string }) {
  const max = Math.max(...data)
  const min = Math.min(...data)
  const range = max - min || 1
  const w = 120
  const h = 36
  const points = data.map((v, i) => {
    const x = (i / (data.length - 1)) * w
    const y = h - ((v - min) / range) * (h - 4) - 2
    return `${x},${y}`
  }).join(' ')
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} className="shrink-0">
      <polyline fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" points={points} />
    </svg>
  )
}

function ForecastCard({ forecast }: { forecast: AiForecast }) {
  const maxP = Math.max(...forecast.predicted)
  const trend = forecast.predicted[forecast.predicted.length - 1] >= forecast.predicted[0]
  return (
    <div className="enterprise-card p-5">
      <div className="flex items-center justify-between mb-3">
        <div>
          <p className="text-xs font-medium text-[var(--text-tertiary)] uppercase tracking-wider">{forecast.metric}</p>
          <p className="text-2xl font-bold text-[var(--text-primary)] mt-0.5">
            {typeof forecast.current === 'number' ? forecast.current.toLocaleString() : forecast.current}
            <span className="text-sm font-normal text-[var(--text-tertiary)] ml-1">{forecast.unit}</span>
          </p>
        </div>
        <div className={clsx(
          'flex items-center gap-1 text-xs font-medium px-2 py-1 rounded-full',
          trend ? 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)] dark:bg-[var(--nexus-success-900)]/20 dark:text-[var(--nexus-success-300)]' : 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-300)]'
        )}>
          {trend ? <ArrowUp className="w-3 h-3" /> : <ArrowDown className="w-3 h-3" />}
          {trend ? 'Up' : 'Down'}
        </div>
      </div>
      <div className="flex items-end gap-2">
        <MiniSparkline data={forecast.predicted} color={trend ? '#10B981' : '#EF4444'} />
        <div className="text-right">
          <p className="text-[10px] text-[var(--text-tertiary)]">{forecast.period}</p>
          <p className="text-xs font-medium text-[var(--text-brand)]">{forecast.confidence}% confidence</p>
        </div>
      </div>
      <div className="flex items-end gap-0.5 h-12 mt-2">
        {forecast.predicted.map((v, i) => {
          const h = (v / maxP) * 100
          const isUp = i > 0 && v >= forecast.predicted[i - 1]
          return (
            <div key={i} className="flex-1 flex flex-col items-center gap-0.5 group relative">
              <div
                className={clsx('w-full rounded-t transition-all', isUp ? 'bg-[var(--nexus-success-400)]' : 'bg-[var(--nexus-error-400)]')}
                style={{ height: `${Math.max(h, 6)}%` }}
              />
              <span className="text-[8px] text-[var(--text-tertiary)]">D{i + 1}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function SupplierRiskCard({ risk }: { risk: AiSupplierRisk }) {
  return (
    <div className={clsx(
      'enterprise-card p-4 border-l-4',
      risk.riskScore >= 70 ? 'border-l-red-500' : risk.riskScore >= 40 ? 'border-l-amber-500' : 'border-l-green-500'
    )}>
      <div className="flex items-center justify-between mb-2">
        <h4 className="text-sm font-semibold text-[var(--text-primary)]">{risk.supplierName}</h4>
        <span className={clsx(
          'text-xs font-bold px-2 py-0.5 rounded-full',
          risk.riskScore >= 70 ? 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-300)]' :
          risk.riskScore >= 40 ? 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/20 dark:text-[var(--nexus-warning-300)]' :
          'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] dark:bg-[var(--nexus-success-900)]/20 dark:text-[var(--nexus-success-300)]'
        )}>
          Risk: {risk.riskScore}
        </span>
      </div>
      <div className="grid grid-cols-3 gap-2 text-xs text-[var(--text-tertiary)] mb-2">
        <div><span className="text-[var(--text-secondary)]">Delay: </span>{(risk.delayProbability * 100).toFixed(0)}%</div>
        <div><span className="text-[var(--text-secondary)]">Quality: </span>{risk.qualityScore}%</div>
        <div><span className="text-[var(--text-secondary)]">On-Time: </span>{risk.onTimeRate}%</div>
      </div>
      <div className="flex items-center justify-between">
        <span className={clsx(
          'text-[10px] font-medium px-1.5 py-0.5 rounded',
          risk.trend === 'improving' ? 'text-[var(--nexus-success-600)] bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20' :
          risk.trend === 'declining' ? 'text-[var(--nexus-error-600)] bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20' :
          'text-[var(--text-secondary)] bg-[var(--surface-muted)]'
        )}>
          {risk.trend}
        </span>
        <p className="text-[10px] text-[var(--text-brand)] font-medium">{risk.recommendation}</p>
      </div>
    </div>
  )
}

export default function AiForecastingPage() {
  const [activeTab, setActiveTab] = useState<'demand' | 'supplier' | 'recommendations'>('demand')
  const [searchTerm, setSearchTerm] = useState('')

  const { data: forecasts = [], isLoading: fcLoading } = useQuery({
    queryKey: ['ai-forecasts'],
    queryFn: getForecasts,
    refetchInterval: 60000,
  })

  const { data: suppliers = [], isLoading: supLoading } = useQuery({
    queryKey: ['ai-suppliers'],
    queryFn: getSupplierRisks,
    refetchInterval: 60000,
  })

  const { data: recommendations = [], isLoading: recLoading } = useQuery({
    queryKey: ['ai-recs-forecast'],
    queryFn: () => getRecommendations(),
    refetchInterval: 60000,
  })

  const filteredSuppliers = useMemo(() => {
    if (!searchTerm.trim()) return suppliers
    const q = searchTerm.toLowerCase()
    return suppliers.filter(s => s.supplierName.toLowerCase().includes(q))
  }, [suppliers, searchTerm])

  const tabs = [
    { id: 'demand' as const, label: 'Demand & Orders', count: forecasts.length },
    { id: 'supplier' as const, label: 'Supplier Risk', count: suppliers.length },
    { id: 'recommendations' as const, label: 'AI Recommendations', count: recommendations.length },
  ]

  return (
    <div className="space-y-6">
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5">
            <BarChart3 className="w-7 h-7 text-[var(--nexus-primary-500)]" /> AI Forecasting
          </h1>
          <p>Demand prediction, supplier risk analysis & AI recommendations</p>
        </div>
      </div>

      <div className="flex items-center gap-1 border-b border-[var(--border-subtle)]">
        {tabs.map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={clsx(
              'px-4 py-2.5 text-sm font-medium border-b-2 transition-colors flex items-center gap-2',
              activeTab === tab.id
                ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
                : 'border-transparent text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]'
            )}
          >
            {tab.label}
            <span className="text-[10px] bg-[var(--surface-muted)] bg-[var(--surface-base)] text-[var(--text-secondary)] px-1.5 py-0.5 rounded-full">{tab.count}</span>
          </button>
        ))}
      </div>

      {activeTab === 'demand' && (
        <>
          {fcLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {[1, 2, 3, 4].map(i => <div key={i} className="enterprise-card p-5 animate-pulse"><div className="h-24 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded" /></div>)}
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {forecasts.map((fc, i) => <ForecastCard key={i} forecast={fc} />)}
            </div>
          )}
          <div className="enterprise-card p-5">
            <div className="flex items-center gap-2 mb-4">
              <Brain className="w-5 h-5 text-[var(--nexus-primary-500)]" />
              <h3 className="font-semibold text-[var(--text-primary)]">AI Insights</h3>
            </div>
            <div className="space-y-3 text-sm">
              <div className="flex items-start gap-3 p-3 rounded-lg bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/10 border border-[var(--nexus-primary-100)] dark:border-[var(--nexus-primary-900)]/20">
                <TrendingUp className="w-5 h-5 text-[var(--nexus-primary-500)] mt-0.5 shrink-0" />
                <div>
                  <p className="font-medium text-[var(--nexus-primary-800)] dark:text-[var(--nexus-primary-200)]">Order volume expected to increase 22% next week</p>
                  <p className="text-xs text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)] mt-0.5">Peak predicted on Thursday with ~15,200 orders. Ensure adequate staffing.</p>
                </div>
              </div>
              <div className="flex items-start gap-3 p-3 rounded-lg bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/10 border border-[var(--nexus-warning-100)] dark:border-[var(--nexus-warning-900)]/20">
                <AlertTriangle className="w-5 h-5 text-[var(--nexus-warning-500)] mt-0.5 shrink-0" />
                <div>
                  <p className="font-medium text-[var(--nexus-warning-800)] dark:text-[var(--nexus-warning-200)]">Warehouse capacity at 83%, projected to hit 91%</p>
                  <p className="text-xs text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)] mt-0.5">Consider opening secondary warehouse or extending receiving hours.</p>
                </div>
              </div>
              <div className="flex items-start gap-3 p-3 rounded-lg bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/10 border border-[var(--nexus-success-100)] dark:border-[var(--nexus-success-900)]/20">
                <TrendingUp className="w-5 h-5 text-[var(--nexus-success-500)] mt-0.5 shrink-0" />
                <div>
                  <p className="font-medium text-[var(--nexus-success-800)] dark:text-[var(--nexus-success-200)]">Carrier SLA predicted to recover to 97.5% by Sunday</p>
                  <p className="text-xs text-[var(--nexus-success-600)] dark:text-[var(--nexus-success-400)] mt-0.5">FedEx performance remains strongest at 98.3% in North Zone.</p>
                </div>
              </div>
            </div>
          </div>
        </>
      )}

      {activeTab === 'supplier' && (
        <>
          <div className="flex items-center gap-4">
            <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder="Search suppliers..." minChars={0} className="flex-1 max-w-xs" />
          </div>
          {supLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {[1, 2, 3, 4].map(i => <div key={i} className="enterprise-card p-5 animate-pulse"><div className="h-20 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded" /></div>)}
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {filteredSuppliers.map((risk, i) => <SupplierRiskCard key={i} risk={risk} />)}
            </div>
          )}
        </>
      )}

      {activeTab === 'recommendations' && (
        <>
          {recLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map(i => <div key={i} className="enterprise-card p-5 animate-pulse"><div className="h-16 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded" /></div>)}
            </div>
          ) : recommendations.length === 0 ? (
            <div className="enterprise-card p-12 text-center">
              <Brain className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
              <p className="font-medium text-[var(--text-secondary)]">No recommendations</p>
            </div>
          ) : (
            <div className="space-y-3">
              {recommendations.map(rec => (
                <div key={rec.id} className="enterprise-card p-5 border-l-4 border-l-primary-500">
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span className={clsx(
                          'text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded-full',
                          rec.impact === 'high' ? 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-300)]' :
                          rec.impact === 'medium' ? 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/20 dark:text-[var(--nexus-warning-300)]' :
                          'bg-[var(--surface-muted)] text-[var(--text-secondary)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)]'
                        )}>{rec.impact}</span>
                        <span className="text-xs text-[var(--text-tertiary)]">{rec.agentName}</span>
                      </div>
                      <h4 className="text-sm font-semibold text-[var(--text-primary)]">{rec.title}</h4>
                      <p className="text-xs text-[var(--text-tertiary)] mt-1">{rec.description}</p>
                      <p className="text-xs font-medium text-[var(--text-brand)] mt-1">→ {rec.suggestedAction}</p>
                    </div>
                    <div className="text-right shrink-0 ml-4">
                      <div className="flex items-center gap-1 justify-end mb-1">
                        <span className="text-xs font-medium">{rec.confidence}%</span>
                      </div>
                      <div className="w-20 h-1.5 bg-[var(--surface-muted)] rounded-full overflow-hidden ml-auto">
                        <div className={clsx('h-full rounded-full', rec.confidence >= 90 ? 'bg-[var(--nexus-success-50)]0' : rec.confidence >= 80 ? 'bg-[var(--nexus-warning-50)]0' : 'bg-[var(--nexus-error-50)]0')} style={{ width: `${rec.confidence}%` }} />
                      </div>
                      {rec.reasoning.length > 0 && (
                        <div className="mt-2 text-[10px] text-[var(--text-tertiary)]">
                          {rec.reasoning.map((r, i) => <p key={i}>• {r}</p>)}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
