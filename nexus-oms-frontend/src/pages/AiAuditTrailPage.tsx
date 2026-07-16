import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Shield, AlertTriangle, CheckCircle, Clock, Search,
  Filter, Brain, RefreshCw, Download, Eye,
} from 'lucide-react'
import clsx from 'clsx'
import { getDecisions } from '../api/aiAgents'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import type { AiDecision } from '../api/aiAgents'

const DECISION_TYPE_STYLES: Record<string, string> = {
  fraud_check: 'bg-red-100 text-red-700 border-red-200 dark:bg-red-900/20 dark:text-red-300',
  routing: 'bg-blue-100 text-blue-700 border-blue-200 dark:bg-blue-900/20 dark:text-blue-300',
  carrier_select: 'bg-purple-100 text-purple-700 border-purple-200 dark:bg-purple-900/20 dark:text-purple-300',
  inventory_alloc: 'bg-green-100 text-green-700 border-green-200 dark:bg-green-900/20 dark:text-green-300',
  packaging: 'bg-orange-100 text-orange-700 border-orange-200 dark:bg-orange-900/20 dark:text-orange-300',
  picking: 'bg-teal-100 text-teal-700 border-teal-200 dark:bg-teal-900/20 dark:text-teal-300',
}

function confidenceColor(value: number) {
  if (value >= 90) return 'bg-green-500'
  if (value >= 70) return 'bg-amber-500'
  return 'bg-red-500'
}

function formatTime(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

export default function AiAuditTrailPage() {
  const [filterType, setFilterType] = useState<string>('all')
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedDecision, setSelectedDecision] = useState<AiDecision | null>(null)
  const [showOnlyOverridden, setShowOnlyOverridden] = useState(false)
  const [showOnlyApproved, setShowOnlyApproved] = useState(false)

  const { data: decisions = [], isLoading, refetch } = useQuery({
    queryKey: ['ai-decisions-audit', 100],
    queryFn: () => getDecisions(100),
    refetchInterval: 30000,
  })

  const filtered = useMemo(() => {
    let result = [...decisions]
    if (filterType !== 'all') {
      result = result.filter(d => d.decisionType === filterType)
    }
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase()
      result = result.filter(d =>
        d.orderNumber?.toLowerCase().includes(q) ||
        d.agentName.toLowerCase().includes(q) ||
        d.decisionType.toLowerCase().includes(q)
      )
    }
    if (showOnlyOverridden) result = result.filter(d => d.wasOverridden)
    if (showOnlyApproved) result = result.filter(d => !d.wasOverridden)
    return result
  }, [decisions, filterType, searchQuery, showOnlyOverridden, showOnlyApproved])

  const stats = useMemo(() => {
    const total = decisions.length
    const overridden = decisions.filter(d => d.wasOverridden).length
    const autoApproved = total - overridden
    const fallback = decisions.filter(d => d.fallbackUsed).length
    const avgConf = decisions.length > 0
      ? Math.round(decisions.reduce((s, d) => s + d.confidence, 0) / decisions.length)
      : 0
    return { total, overridden, autoApproved, fallback, avgConf }
  }, [decisions])

  const typeCounts = useMemo(() => {
    const counts: Record<string, number> = {}
    for (const d of decisions) {
      counts[d.decisionType] = (counts[d.decisionType] || 0) + 1
    }
    return counts
  }, [decisions])

  return (
    <div className="space-y-6">
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5">
            <Shield className="w-7 h-7 text-primary-500" /> AI Audit Trail
          </h1>
          <p>Complete record of all AI decisions, overrides & approvals</p>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={() => refetch()} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm">
            <RefreshCw className="w-3.5 h-3.5" /> Refresh
          </button>
          <button className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm">
            <Download className="w-3.5 h-3.5" /> Export
          </button>
        </div>
      </div>

      <div className="grid grid-cols-5 gap-4">
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Total Decisions</p>
          <p className="text-2xl font-bold text-[var(--text-primary)]">{stats.total}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Auto-Approved</p>
          <p className="text-2xl font-bold text-green-600">{stats.autoApproved}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Overridden</p>
          <p className="text-2xl font-bold text-amber-600">{stats.overridden}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Fallback Used</p>
          <p className="text-2xl font-bold text-red-600">{stats.fallback}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Avg Confidence</p>
          <p className="text-2xl font-bold text-blue-600">{stats.avgConf}%</p>
        </div>
      </div>

      <div className="enterprise-card p-5">
        <div className="flex items-center gap-4 flex-wrap">
          <Autocomplete value={searchQuery} onChange={setSearchQuery} placeholder="Search by order, agent, type..." minChars={0} className="flex-1 min-w-[200px] max-w-xs" />
          <select
            value={filterType}
            onChange={e => setFilterType(e.target.value)}
            className="enterprise-input text-sm py-2"
          >
            <option value="all">All Types</option>
            <option value="fraud_check">Fraud Check</option>
            <option value="routing">Routing</option>
            <option value="carrier_select">Carrier Select</option>
            <option value="inventory_alloc">Inventory Alloc</option>
            <option value="packaging">Packaging</option>
            <option value="picking">Picking</option>
          </select>
          <label className="flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-400 cursor-pointer">
            <input
              type="checkbox"
              checked={showOnlyOverridden}
              onChange={e => { setShowOnlyOverridden(e.target.checked); setShowOnlyApproved(false) }}
              className="rounded border-gray-300"
            />
            Overridden only
          </label>
          <label className="flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-400 cursor-pointer">
            <input
              type="checkbox"
              checked={showOnlyApproved}
              onChange={e => { setShowOnlyApproved(e.target.checked); setShowOnlyOverridden(false) }}
              className="rounded border-gray-300"
            />
            Auto-approved only
          </label>
          {Object.entries(typeCounts).map(([type, count]) => (
            <span key={type} className={clsx(
              'text-xs px-2 py-0.5 rounded-full font-medium border',
              DECISION_TYPE_STYLES[type] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
            )}>
              {type.replace(/_/g, ' ')}: {count}
            </span>
          ))}
        </div>
      </div>

      <div className="enterprise-card overflow-hidden">
        {isLoading ? (
          <div className="p-12 text-center">
            <div className="w-8 h-8 border-2 border-primary-500 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
            <p className="text-sm text-gray-400">Loading audit trail...</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center">
            <Shield className="w-12 h-12 mx-auto mb-3 text-gray-300" />
            <p className="font-medium text-gray-500">No decisions match your filters</p>
            <p className="text-sm text-gray-400 mt-1">Try adjusting search or filter criteria</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Timestamp</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order #</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Type</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Agent</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Decision</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Confidence</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Time</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {filtered.map(dec => {
                  const badgeColor = DECISION_TYPE_STYLES[dec.decisionType] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
                  return (
                    <tr key={dec.id} className="enterprise-table-row">
                      <td className="px-4 py-3 text-xs text-[var(--text-tertiary)] whitespace-nowrap">
                        {new Date(dec.createdAt).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 text-sm font-mono font-medium text-[var(--color-primary)]">
                        {dec.orderNumber || '—'}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border capitalize ${badgeColor}`}>
                          {dec.decisionType.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{dec.agentName}</td>
                      <td className="px-4 py-3 text-xs text-[var(--text-tertiary)] max-w-[120px] truncate">
                        {dec.output?.decision || '—'}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-16 h-2 bg-[var(--bg-tertiary)] rounded-full overflow-hidden">
                            <div className={`h-full rounded-full ${confidenceColor(dec.confidence)}`} style={{ width: `${Math.round(dec.confidence)}%` }} />
                          </div>
                          <span className={`text-xs font-medium ${dec.confidence >= 90 ? 'text-green-600' : dec.confidence >= 70 ? 'text-amber-600' : 'text-red-600'}`}>
                            {Math.round(dec.confidence)}%
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        {dec.wasOverridden ? (
                          <span className="inline-flex items-center gap-1 text-xs text-amber-700 bg-amber-50 dark:bg-amber-900/20 px-2 py-0.5 rounded-full border border-amber-200 dark:border-amber-800">
                            <AlertTriangle className="w-3 h-3" /> Overridden
                          </span>
                        ) : dec.fallbackUsed ? (
                          <span className="inline-flex items-center gap-1 text-xs text-orange-700 bg-orange-50 dark:bg-orange-900/20 px-2 py-0.5 rounded-full border border-orange-200 dark:border-orange-800">
                            <AlertTriangle className="w-3 h-3" /> Fallback
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-xs text-green-700 bg-green-50 dark:bg-green-900/20 px-2 py-0.5 rounded-full border border-green-200 dark:border-green-800">
                            <CheckCircle className="w-3 h-3" /> Auto
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right text-xs text-[var(--text-tertiary)]">
                        {formatTime(dec.processingTimeMs)}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <button
                          onClick={() => setSelectedDecision(dec)}
                          className="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Detail Modal */}
      {selectedDecision && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={() => setSelectedDecision(null)}>
          <div className="enterprise-card p-6 w-full max-w-lg mx-4" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-[var(--text-primary)] flex items-center gap-2">
                <Brain className="w-5 h-5 text-primary-500" /> AI Decision Detail
              </h2>
              <button onClick={() => setSelectedDecision(null)} className="text-gray-400 hover:text-gray-600">✕</button>
            </div>
            <div className="space-y-3 text-sm">
              <div className="grid grid-cols-2 gap-3">
                <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                  <p className="text-[10px] font-medium text-gray-400 uppercase">Order</p>
                  <p className="font-mono font-medium text-[var(--text-primary)]">{selectedDecision.orderNumber || '—'}</p>
                </div>
                <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                  <p className="text-[10px] font-medium text-gray-400 uppercase">Agent</p>
                  <p className="font-medium text-[var(--text-primary)]">{selectedDecision.agentName}</p>
                </div>
                <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                  <p className="text-[10px] font-medium text-gray-400 uppercase">Decision Type</p>
                  <p className="capitalize font-medium text-[var(--text-primary)]">{selectedDecision.decisionType.replace(/_/g, ' ')}</p>
                </div>
                <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                  <p className="text-[10px] font-medium text-gray-400 uppercase">Confidence</p>
                  <p className="font-semibold">{Math.round(selectedDecision.confidence)}%</p>
                </div>
                <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                  <p className="text-[10px] font-medium text-gray-400 uppercase">Model</p>
                  <p className="text-[var(--text-primary)]">{selectedDecision.modelVersion}</p>
                </div>
                <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                  <p className="text-[10px] font-medium text-gray-400 uppercase">Processing</p>
                  <p className="text-[var(--text-primary)]">{formatTime(selectedDecision.processingTimeMs)}</p>
                </div>
              </div>
              <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                <p className="text-[10px] font-medium text-gray-400 uppercase mb-1">Output Decision</p>
                <p className="font-medium text-[var(--text-primary)]">{selectedDecision.output?.decision || '—'}</p>
              </div>
              {selectedDecision.wasOverridden && (
                <div className="p-3 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800">
                  <p className="text-[10px] font-medium text-amber-600 uppercase mb-1">Override</p>
                  <p className="text-sm text-amber-700 dark:text-amber-300">
                    Overridden by {selectedDecision.overriddenBy || 'Unknown'}
                  </p>
                </div>
              )}
              {selectedDecision.fallbackUsed && (
                <div className="p-3 rounded-lg bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800">
                  <p className="text-[10px] font-medium text-orange-600 uppercase mb-1">⚠ Fallback Rules Used</p>
                  <p className="text-sm text-orange-700 dark:text-orange-300">
                    AI confidence was below threshold; rule-based fallback was applied
                  </p>
                </div>
              )}
              <div className="text-[10px] text-gray-400 text-right">
                {new Date(selectedDecision.createdAt).toLocaleString()}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
