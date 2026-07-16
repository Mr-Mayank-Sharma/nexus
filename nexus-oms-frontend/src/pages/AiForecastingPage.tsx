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
          trend ? 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-300' : 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-300'
        )}>
          {trend ? <ArrowUp className="w-3 h-3" /> : <ArrowDown className="w-3 h-3" />}
          {trend ? 'Up' : 'Down'}
        </div>
      </div>
      <div className="flex items-end gap-2">
        <MiniSparkline data={forecast.predicted} color={trend ? '#10B981' : '#EF4444'} />
        <div className="text-right">
          <p className="text-[10px] text-[var(--text-tertiary)]">{forecast.period}</p>
          <p className="text-xs font-medium text-primary-600">{forecast.confidence}% confidence</p>
        </div>
      </div>
      <div className="flex items-end gap-0.5 h-12 mt-2">
        {forecast.predicted.map((v, i) => {
          const h = (v / maxP) * 100
          const isUp = i > 0 && v >= forecast.predicted[i - 1]
          return (
            <div key={i} className="flex-1 flex flex-col items-center gap-0.5 group relative">
              <div
                className={clsx('w-full rounded-t transition-all', isUp ? 'bg-green-400' : 'bg-red-400')}
                style={{ height: `${Math.max(h, 6)}%` }}
              />
              <span className="text-[8px] text-gray-400">D{i + 1}</span>
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
          risk.riskScore >= 70 ? 'bg-red-100 text-red-700 dark:bg-red-900/20 dark:text-red-300' :
          risk.riskScore >= 40 ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/20 dark:text-amber-300' :
          'bg-green-100 text-green-700 dark:bg-green-900/20 dark:text-green-300'
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
          risk.trend === 'improving' ? 'text-green-600 bg-green-50 dark:bg-green-900/20' :
          risk.trend === 'declining' ? 'text-red-600 bg-red-50 dark:bg-red-900/20' :
          'text-gray-500 bg-gray-100 dark:bg-gray-700'
        )}>
          {risk.trend}
        </span>
        <p className="text-[10px] text-primary-600 font-medium">{risk.recommendation}</p>
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
            <BarChart3 className="w-7 h-7 text-primary-500" /> AI Forecasting
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
            <span className="text-[10px] bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400 px-1.5 py-0.5 rounded-full">{tab.count}</span>
          </button>
        ))}
      </div>

      {activeTab === 'demand' && (
        <>
          {fcLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {[1, 2, 3, 4].map(i => <div key={i} className="enterprise-card p-5 animate-pulse"><div className="h-24 bg-gray-200 dark:bg-gray-700 rounded" /></div>)}
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {forecasts.map((fc, i) => <ForecastCard key={i} forecast={fc} />)}
            </div>
          )}
          <div className="enterprise-card p-5">
            <div className="flex items-center gap-2 mb-4">
              <Brain className="w-5 h-5 text-primary-500" />
              <h3 className="font-semibold text-[var(--text-primary)]">AI Insights</h3>
            </div>
            <div className="space-y-3 text-sm">
              <div className="flex items-start gap-3 p-3 rounded-lg bg-blue-50 dark:bg-blue-900/10 border border-blue-100 dark:border-blue-900/20">
                <TrendingUp className="w-5 h-5 text-blue-500 mt-0.5 shrink-0" />
                <div>
                  <p className="font-medium text-blue-800 dark:text-blue-200">Order volume expected to increase 22% next week</p>
                  <p className="text-xs text-blue-600 dark:text-blue-400 mt-0.5">Peak predicted on Thursday with ~15,200 orders. Ensure adequate staffing.</p>
                </div>
              </div>
              <div className="flex items-start gap-3 p-3 rounded-lg bg-amber-50 dark:bg-amber-900/10 border border-amber-100 dark:border-amber-900/20">
                <AlertTriangle className="w-5 h-5 text-amber-500 mt-0.5 shrink-0" />
                <div>
                  <p className="font-medium text-amber-800 dark:text-amber-200">Warehouse capacity at 83%, projected to hit 91%</p>
                  <p className="text-xs text-amber-600 dark:text-amber-400 mt-0.5">Consider opening secondary warehouse or extending receiving hours.</p>
                </div>
              </div>
              <div className="flex items-start gap-3 p-3 rounded-lg bg-green-50 dark:bg-green-900/10 border border-green-100 dark:border-green-900/20">
                <TrendingUp className="w-5 h-5 text-green-500 mt-0.5 shrink-0" />
                <div>
                  <p className="font-medium text-green-800 dark:text-green-200">Carrier SLA predicted to recover to 97.5% by Sunday</p>
                  <p className="text-xs text-green-600 dark:text-green-400 mt-0.5">FedEx performance remains strongest at 98.3% in North Zone.</p>
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
              {[1, 2, 3, 4].map(i => <div key={i} className="enterprise-card p-5 animate-pulse"><div className="h-20 bg-gray-200 dark:bg-gray-700 rounded" /></div>)}
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
              {[1, 2, 3].map(i => <div key={i} className="enterprise-card p-5 animate-pulse"><div className="h-16 bg-gray-200 dark:bg-gray-700 rounded" /></div>)}
            </div>
          ) : recommendations.length === 0 ? (
            <div className="enterprise-card p-12 text-center">
              <Brain className="w-12 h-12 mx-auto mb-3 text-gray-300" />
              <p className="font-medium text-gray-500">No recommendations</p>
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
                          rec.impact === 'high' ? 'bg-red-100 text-red-700 dark:bg-red-900/20 dark:text-red-300' :
                          rec.impact === 'medium' ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/20 dark:text-amber-300' :
                          'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
                        )}>{rec.impact}</span>
                        <span className="text-xs text-[var(--text-tertiary)]">{rec.agentName}</span>
                      </div>
                      <h4 className="text-sm font-semibold text-[var(--text-primary)]">{rec.title}</h4>
                      <p className="text-xs text-[var(--text-tertiary)] mt-1">{rec.description}</p>
                      <p className="text-xs font-medium text-primary-600 mt-1">→ {rec.suggestedAction}</p>
                    </div>
                    <div className="text-right shrink-0 ml-4">
                      <div className="flex items-center gap-1 justify-end mb-1">
                        <span className="text-xs font-medium">{rec.confidence}%</span>
                      </div>
                      <div className="w-20 h-1.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden ml-auto">
                        <div className={clsx('h-full rounded-full', rec.confidence >= 90 ? 'bg-green-500' : rec.confidence >= 80 ? 'bg-amber-500' : 'bg-red-500')} style={{ width: `${rec.confidence}%` }} />
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
