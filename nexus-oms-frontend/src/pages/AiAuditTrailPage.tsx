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
  fraud_check: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] border-[var(--nexus-error-200)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-300)]',
  routing: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] border-[var(--nexus-primary-200)] dark:bg-[var(--nexus-primary-900)]/20 dark:text-[var(--nexus-primary-300)]',
  carrier_select: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)] border-[var(--nexus-ai-200)] dark:bg-[var(--nexus-ai-900)]/20 dark:text-[var(--nexus-ai-300)]',
  inventory_alloc: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] border-[var(--nexus-success-200)] dark:bg-[var(--nexus-success-900)]/20 dark:text-[var(--nexus-success-300)]',
  packaging: 'bg-orange-100 text-orange-700 border-orange-200 dark:bg-orange-900/20 dark:text-orange-300',
  picking: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] border-[var(--nexus-success-200)] dark:bg-[var(--nexus-success-900)]/20 dark:text-[var(--nexus-success-300)]',
}

function confidenceColor(value: number) {
  if (value >= 90) return 'bg-[var(--nexus-success-50)]0'
  if (value >= 70) return 'bg-[var(--nexus-warning-50)]0'
  return 'bg-[var(--nexus-error-50)]0'
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
            <Shield className="w-7 h-7 text-[var(--nexus-primary-500)]" /> AI Audit Trail
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
          <p className="text-2xl font-bold text-[var(--nexus-success-600)]">{stats.autoApproved}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Overridden</p>
          <p className="text-2xl font-bold text-[var(--nexus-warning-600)]">{stats.overridden}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Fallback Used</p>
          <p className="text-2xl font-bold text-[var(--nexus-error-600)]">{stats.fallback}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Avg Confidence</p>
          <p className="text-2xl font-bold text-[var(--nexus-primary-600)]">{stats.avgConf}%</p>
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
          <label className="flex items-center gap-1.5 text-sm text-[var(--text-secondary)] cursor-pointer">
            <input
              type="checkbox"
              checked={showOnlyOverridden}
              onChange={e => { setShowOnlyOverridden(e.target.checked); setShowOnlyApproved(false) }}
              className="rounded border-[var(--border-default)]"
            />
            Overridden only
          </label>
          <label className="flex items-center gap-1.5 text-sm text-[var(--text-secondary)] cursor-pointer">
            <input
              type="checkbox"
              checked={showOnlyApproved}
              onChange={e => { setShowOnlyApproved(e.target.checked); setShowOnlyOverridden(false) }}
              className="rounded border-[var(--border-default)]"
            />
            Auto-approved only
          </label>
          {Object.entries(typeCounts).map(([type, count]) => (
            <span key={type} className={clsx(
              'text-xs px-2 py-0.5 rounded-full font-medium border',
              DECISION_TYPE_STYLES[type] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)]'
            )}>
              {type.replace(/_/g, ' ')}: {count}
            </span>
          ))}
        </div>
      </div>

      <div className="enterprise-card overflow-hidden">
        {isLoading ? (
          <div className="p-12 text-center">
            <div className="w-8 h-8 border-2 border-[var(--nexus-primary-500)] border-t-transparent rounded-full animate-spin mx-auto mb-3" />
            <p className="text-sm text-[var(--text-tertiary)]">Loading audit trail...</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center">
            <Shield className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
            <p className="font-medium text-[var(--text-secondary)]">No decisions match your filters</p>
            <p className="text-sm text-[var(--text-tertiary)] mt-1">Try adjusting search or filter criteria</p>
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
                  const badgeColor = DECISION_TYPE_STYLES[dec.decisionType] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)]'
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
                          <span className={`text-xs font-medium ${dec.confidence >= 90 ? 'text-[var(--nexus-success-600)]' : dec.confidence >= 70 ? 'text-[var(--nexus-warning-600)]' : 'text-[var(--nexus-error-600)]'}`}>
                            {Math.round(dec.confidence)}%
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        {dec.wasOverridden ? (
                          <span className="inline-flex items-center gap-1 text-xs text-[var(--nexus-warning-700)] bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20 px-2 py-0.5 rounded-full border border-[var(--nexus-warning-200)] dark:border-[var(--nexus-warning-800)]">
                            <AlertTriangle className="w-3 h-3" /> Overridden
                          </span>
                        ) : dec.fallbackUsed ? (
                          <span className="inline-flex items-center gap-1 text-xs text-orange-700 bg-orange-50 dark:bg-orange-900/20 px-2 py-0.5 rounded-full border border-orange-200 dark:border-orange-800">
                            <AlertTriangle className="w-3 h-3" /> Fallback
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-xs text-[var(--nexus-success-700)] bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 px-2 py-0.5 rounded-full border border-[var(--nexus-success-200)] dark:border-[var(--nexus-success-800)]">
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
                          className="p-1.5 rounded-lg hover:bg-[var(--surface-muted)] hover:bg-[var(--surface-base)] text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] dark:hover:text-[var(--text-tertiary)] transition-colors"
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
        <div className="enterprise-modal-overlay" onClick={() => setSelectedDecision(null)}>
          <div className="enterprise-card p-6 w-full max-w-lg mx-4" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-[var(--text-primary)] flex items-center gap-2">
                <Brain className="w-5 h-5 text-[var(--nexus-primary-500)]" /> AI Decision Detail
              </h2>
              <button onClick={() => setSelectedDecision(null)} className="text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]">✕</button>
            </div>
            <div className="space-y-3 text-sm">
              <div className="grid grid-cols-2 gap-3">
                <div className="p-3 rounded-lg bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50">
                  <p className="text-[10px] font-medium text-[var(--text-tertiary)] uppercase">Order</p>
                  <p className="font-mono font-medium text-[var(--text-primary)]">{selectedDecision.orderNumber || '—'}</p>
                </div>
                <div className="p-3 rounded-lg bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50">
                  <p className="text-[10px] font-medium text-[var(--text-tertiary)] uppercase">Agent</p>
                  <p className="font-medium text-[var(--text-primary)]">{selectedDecision.agentName}</p>
                </div>
                <div className="p-3 rounded-lg bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50">
                  <p className="text-[10px] font-medium text-[var(--text-tertiary)] uppercase">Decision Type</p>
                  <p className="capitalize font-medium text-[var(--text-primary)]">{selectedDecision.decisionType.replace(/_/g, ' ')}</p>
                </div>
                <div className="p-3 rounded-lg bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50">
                  <p className="text-[10px] font-medium text-[var(--text-tertiary)] uppercase">Confidence</p>
                  <p className="font-semibold">{Math.round(selectedDecision.confidence)}%</p>
                </div>
                <div className="p-3 rounded-lg bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50">
                  <p className="text-[10px] font-medium text-[var(--text-tertiary)] uppercase">Model</p>
                  <p className="text-[var(--text-primary)]">{selectedDecision.modelVersion}</p>
                </div>
                <div className="p-3 rounded-lg bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50">
                  <p className="text-[10px] font-medium text-[var(--text-tertiary)] uppercase">Processing</p>
                  <p className="text-[var(--text-primary)]">{formatTime(selectedDecision.processingTimeMs)}</p>
                </div>
              </div>
              <div className="p-3 rounded-lg bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50">
                <p className="text-[10px] font-medium text-[var(--text-tertiary)] uppercase mb-1">Output Decision</p>
                <p className="font-medium text-[var(--text-primary)]">{selectedDecision.output?.decision || '—'}</p>
              </div>
              {selectedDecision.wasOverridden && (
                <div className="p-3 rounded-lg bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20 border border-[var(--nexus-warning-200)] dark:border-[var(--nexus-warning-800)]">
                  <p className="text-[10px] font-medium text-[var(--nexus-warning-600)] uppercase mb-1">Override</p>
                  <p className="text-sm text-[var(--nexus-warning-700)] dark:text-[var(--nexus-warning-300)]">
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
              <div className="text-[10px] text-[var(--text-tertiary)] text-right">
                {new Date(selectedDecision.createdAt).toLocaleString()}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
