import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  GitBranch, Brain, Loader2, CheckCircle, XCircle, AlertTriangle,
  Clock, RefreshCw,
} from 'lucide-react'
import { getAgents, getDecisions, approveRecommendation, rejectRecommendation } from '../api/aiAgents'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import type { AiAgent, AiDecision } from '../api/aiAgents'

interface QueueOrder {
  id: string
  orderNumber: string
  customer: string
  items: number
  value: number
  slaRemaining: string
  aiDecision: string
  confidence: number
  agentName: string
  agentId: string
}

interface OverrideState {
  orderId: string
  currentDecision: string
  currentConfidence: number
  agentName: string
}

const MOCK_QUEUE: QueueOrder[] = [
  { id: 'q-1', orderNumber: 'ORD-10234', customer: 'Acme Corp', items: 3, value: 1247.50, slaRemaining: '4h 12m', aiDecision: 'Route to WH #2', confidence: 97, agentName: 'Order Routing AI', agentId: 'agent-routing' },
  { id: 'q-2', orderNumber: 'ORD-10235', customer: 'GlobalTech Inc', items: 1, value: 89.99, slaRemaining: '6h 30m', aiDecision: 'Route to Store #5', confidence: 94, agentName: 'Order Routing AI', agentId: 'agent-routing' },
  { id: 'q-3', orderNumber: 'ORD-10236', customer: 'Sarah Johnson', items: 5, value: 2340.00, slaRemaining: '2h 05m', aiDecision: 'Flag for Review', confidence: 62, agentName: 'Fraud Detection AI', agentId: 'agent-fraud' },
  { id: 'q-4', orderNumber: 'ORD-10237', customer: 'BestBuy Wholesale', items: 12, value: 5678.00, slaRemaining: '8h 00m', aiDecision: 'Route to WH #1', confidence: 91, agentName: 'Order Routing AI', agentId: 'agent-routing' },
  { id: 'q-5', orderNumber: 'ORD-10238', customer: 'John Davis', items: 2, value: 345.50, slaRemaining: '1h 45m', aiDecision: 'Route to Store #3', confidence: 88, agentName: 'Warehouse Selection AI', agentId: 'agent-warehouse' },
  { id: 'q-6', orderNumber: 'ORD-10239', customer: 'TechSupply Co', items: 8, value: 4230.00, slaRemaining: '3h 20m', aiDecision: 'Route to WH #2', confidence: 95, agentName: 'Order Routing AI', agentId: 'agent-routing' },
  { id: 'q-7', orderNumber: 'ORD-10240', customer: 'Maria Garcia', items: 1, value: 29.99, slaRemaining: '5h 00m', aiDecision: 'Auto-Route to WH #1', confidence: 98, agentName: 'Order Routing AI', agentId: 'agent-routing' },
  { id: 'q-8', orderNumber: 'ORD-10241', customer: 'OfficeDepot Inc', items: 15, value: 8920.00, slaRemaining: '12h 00m', aiDecision: 'Route to WH #3', confidence: 87, agentName: 'Inventory Allocation AI', agentId: 'agent-inventory' },
]

const DECISION_TYPE_STYLES: Record<string, string> = {
  fraud_check: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] border-[var(--nexus-error-200)]',
  routing: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] border-[var(--nexus-primary-200)]',
  carrier_select: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)] border-[var(--nexus-ai-200)]',
  inventory_alloc: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] border-[var(--nexus-success-200)]',
  packaging: 'bg-orange-100 text-orange-700 border-orange-200',
  picking: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] border-[var(--nexus-success-200)]',
}

function statusColor(status: AiAgent['status']) {
  switch (status) {
    case 'active': return 'bg-[var(--nexus-success-50)]0'
    case 'training': return 'bg-[var(--nexus-warning-50)]0'
    case 'idle': return 'bg-[var(--nexus-warning-400)]'
    case 'error': return 'bg-[var(--nexus-error-50)]0'
  }
}

function confidenceColor(value: number) {
  if (value >= 90) return 'bg-[var(--nexus-success-50)]0'
  if (value >= 70) return 'bg-[var(--nexus-warning-50)]0'
  return 'bg-[var(--nexus-error-50)]0'
}

function confidenceTextColor(value: number) {
  if (value >= 90) return 'text-[var(--nexus-success-600)]'
  if (value >= 70) return 'text-[var(--nexus-warning-600)]'
  return 'text-[var(--nexus-error-600)]'
}

function formatTime(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

export default function AiOrderRoutingPage() {
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<'queue' | 'log'>('queue')
  const [expandedAgent, setExpandedAgent] = useState<string | null>(null)
  const [overrideState, setOverrideState] = useState<OverrideState | null>(null)
  const [overrideReason, setOverrideReason] = useState('')
  const [overrideDecision, setOverrideDecision] = useState('')

  const { data: agents = [], isLoading: agentsLoading } = useQuery({
    queryKey: ['ai-agents'],
    queryFn: getAgents,
    refetchInterval: 30000,
  })

  const { data: decisions = [], isLoading: decisionsLoading } = useQuery({
    queryKey: ['ai-decisions', 50],
    queryFn: () => getDecisions(50),
    refetchInterval: 15000,
  })

  const approveMutation = useMutation({
    mutationFn: approveRecommendation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ai-recommendations'] }),
  })

  const rejectMutation = useMutation({
    mutationFn: rejectRecommendation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ai-recommendations'] }),
  })

  function handleOverrideConfirm() {
    if (!overrideReason.trim()) return
    setOverrideState(null)
    setOverrideReason('')
    setOverrideDecision('')
  }

  const activeAgents = agents.filter(a => a.status === 'active').length
  const trainingAgents = agents.filter(a => a.status === 'training' || a.status === 'idle').length
  const errorAgents = agents.filter(a => a.status === 'error').length

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5">
            <GitBranch className="w-7 h-7 text-[var(--nexus-primary-500)]" />
            Order Routing & Brokering
          </h1>
          <p>AI-powered fulfillment decisions</p>
        </div>
        <div className="flex items-center gap-3">
          <PermissionGate resource="settings" action="edit">
            <button
              onClick={() => {
                queryClient.invalidateQueries({ queryKey: ['ai-agents'] })
                queryClient.invalidateQueries({ queryKey: ['ai-decisions'] })
              }}
              className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm"
            >
              <RefreshCw className="w-3.5 h-3.5" /> Refresh
            </button>
          </PermissionGate>
        </div>
      </div>

      {/* Auto-routing KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)] mb-1">Auto-approved Today</p>
          <p className="text-2xl font-bold text-[var(--nexus-success-600)]">1,247</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)] mb-1">Flagged for Review</p>
          <p className="text-2xl font-bold text-[var(--nexus-warning-600)]">34</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)] mb-1">Override Rate</p>
          <p className="text-2xl font-bold text-[var(--nexus-ai-600)]">2.7%</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)] mb-1">Avg Confidence</p>
          <p className="text-2xl font-bold text-[var(--nexus-primary-600)]">95.3%</p>
        </div>
      </div>

      {/* AI Agent Status */}
      <div className="enterprise-card p-5">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Brain className="w-5 h-5 text-[var(--nexus-ai-600)]" />
            <h2 className="font-semibold text-[var(--text-primary)]">AI Agent Status</h2>
          </div>
          <div className="flex items-center gap-3 text-xs text-[var(--text-secondary)]">
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-[var(--nexus-success-50)]0" /> {activeAgents} Active
            </span>
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-[var(--nexus-warning-50)]0" /> {trainingAgents} Idle/Training
            </span>
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-[var(--nexus-error-50)]0" /> {errorAgents} Error
            </span>
          </div>
        </div>
        {agentsLoading ? (
          <div className="flex gap-3 overflow-x-auto pb-2">
            {[1, 2, 3, 4, 5].map(i => (
              <div key={i} className="enterprise-skeleton h-24 w-48 shrink-0 rounded-lg" />
            ))}
          </div>
        ) : (
          <div className="flex gap-3 overflow-x-auto pb-2">
            {agents.map(agent => (
              <button
                key={agent.id}
                onClick={() => setExpandedAgent(expandedAgent === agent.id ? null : agent.id)}
                className="shrink-0 w-56 p-3 rounded-lg border border-[var(--border-subtle)] hover:border-[var(--color-primary)]/30 hover:bg-[var(--bg-tertiary)]/30 transition-all text-left"
              >
                <div className="flex items-center gap-2 mb-1.5">
                  <span className={`w-2.5 h-2.5 rounded-full ${statusColor(agent.status)}`} />
                  <span className="text-sm font-medium text-[var(--text-primary)] truncate">{agent.name}</span>
                </div>
                <p className="text-[10px] text-[var(--text-tertiary)] mb-2 line-clamp-1">{agent.description}</p>
                <div className="flex items-center justify-between text-[10px]">
                  <span className="text-[var(--text-secondary)]">
                    Acc: <strong>{agent.accuracy}%</strong>
                  </span>
                  <span className="text-[var(--text-secondary)]">
                    {agent.decisions24h} decisions
                  </span>
                </div>
                {expandedAgent === agent.id && (
                  <div className="mt-2 pt-2 border-t border-[var(--border-subtle)] text-[10px] space-y-1">
                    <p className="text-[var(--text-tertiary)]">Model: <span className="text-[var(--text-secondary)]">{agent.model}</span></p>
                    <p className="text-[var(--text-tertiary)]">Last run: <span className="text-[var(--text-secondary)]">{agent.lastRun}</span></p>
                    <p className="text-[var(--text-tertiary)]">Status: <span className="capitalize text-[var(--text-secondary)]">{agent.status}</span></p>
                  </div>
                )}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-1 border-b border-[var(--border-subtle)]">
        <button
          onClick={() => setActiveTab('queue')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'queue'
              ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
              : 'border-transparent text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]'
          }`}
        >
          Pending Brokering Queue
        </button>
        <button
          onClick={() => setActiveTab('log')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'log'
              ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
              : 'border-transparent text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]'
          }`}
        >
          Routing Decision Log
        </button>
      </div>

      {/* Queue Tab */}
      {activeTab === 'queue' && (
        <div className="enterprise-card overflow-hidden">
          {MOCK_QUEUE.length === 0 ? (
            <div className="p-12 text-center">
              <CheckCircle className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
              <p className="font-medium text-[var(--text-secondary)]">No pending orders</p>
              <p className="text-sm text-[var(--text-tertiary)] mt-1">All orders have been processed</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="enterprise-table w-full">
                <thead>
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order #</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Customer</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Items</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Value</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">SLA</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">AI Decision</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Confidence</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border-subtle)]">
                  {MOCK_QUEUE.map(order => (
                    <tr key={order.id} className="enterprise-table-row">
                      <td className="px-4 py-3 text-sm font-mono font-medium text-[var(--color-primary)]">{order.orderNumber}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-primary)]">{order.customer}</td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{order.items}</td>
                      <td className="px-4 py-3 text-right text-sm font-medium text-[var(--text-primary)]">
                        ${order.value.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className="inline-flex items-center gap-1 text-xs font-medium text-[var(--nexus-warning-600)] bg-[var(--nexus-warning-50)] px-2 py-0.5 rounded-full">
                          <Clock className="w-3 h-3" />
                          {order.slaRemaining}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <Autocomplete
                          value={order.aiDecision}
                          onChange={() => {}}
                          minChars={0}
                          suggestions={['Route to WH #1', 'Route to WH #2', 'Route to WH #3', 'Route to Store #5', 'Route to Store #3', 'Flag for Review', 'Auto-Route to WH #1']}
                          inputClassName="enterprise-input text-xs py-1 px-2 w-full max-w-[140px]"
                        />
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-16 h-2 bg-[var(--bg-tertiary)] rounded-full overflow-hidden">
                            <div
                              className={`h-full rounded-full ${confidenceColor(order.confidence)}`}
                              style={{ width: `${order.confidence}%` }}
                            />
                          </div>
                          <span className={`text-xs font-medium ${confidenceTextColor(order.confidence)}`}>
                            {order.confidence}%
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-center gap-1">
                          <PermissionGate resource="settings" action="edit">
                            <button
                              className="inline-flex items-center gap-1 px-2 py-1.5 text-xs font-medium rounded-md bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)] hover:bg-[var(--nexus-success-100)] border border-[var(--nexus-success-200)] transition-colors"
                              title="Approve"
                              onClick={() => approveMutation.mutate(order.id)}
                            >
                              <CheckCircle className="w-3.5 h-3.5" /> Approve
                            </button>
                          </PermissionGate>
                          <PermissionGate resource="settings" action="edit">
                            <button
                              className="inline-flex items-center gap-1 px-2 py-1.5 text-xs font-medium rounded-md bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)] hover:bg-[var(--nexus-warning-100)] border border-[var(--nexus-warning-200)] transition-colors"
                              title="Override"
                              onClick={() => {
                                setOverrideState({
                                  orderId: order.id,
                                  currentDecision: order.aiDecision,
                                  currentConfidence: order.confidence,
                                  agentName: order.agentName,
                                })
                                setOverrideDecision(order.aiDecision)
                                setOverrideReason('')
                              }}
                            >
                              <AlertTriangle className="w-3.5 h-3.5" /> Override
                            </button>
                          </PermissionGate>
                          <PermissionGate resource="settings" action="delete">
                            <button
                              className="inline-flex items-center gap-1 px-2 py-1.5 text-xs font-medium rounded-md bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)] hover:bg-[var(--nexus-error-50)] border border-[var(--nexus-error-200)] transition-colors"
                              title="Reject"
                              onClick={() => rejectMutation.mutate(order.id)}
                            >
                              <XCircle className="w-3.5 h-3.5" /> Reject
                            </button>
                          </PermissionGate>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Decision Log Tab */}
      {activeTab === 'log' && (
        <div className="enterprise-card overflow-hidden">
          {decisionsLoading ? (
            <div className="p-12 text-center">
              <Loader2 className="w-8 h-8 animate-spin mx-auto mb-3 text-[var(--text-tertiary)]" />
              <p className="text-sm text-[var(--text-secondary)]">Loading decisions...</p>
            </div>
          ) : decisions.length === 0 ? (
            <div className="p-12 text-center">
              <Brain className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
              <p className="font-medium text-[var(--text-secondary)]">No decisions recorded</p>
              <p className="text-sm text-[var(--text-tertiary)] mt-1">Decisions will appear here as the AI processes orders</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="enterprise-table w-full">
                <thead>
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Timestamp</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order #</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Decision Type</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Agent</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Confidence</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Override</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Processing</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border-subtle)]">
                  {decisions.map(dec => {
                    const decisionTypeLabel = dec.decisionType.replace(/_/g, ' ')
                    const badgeColor = DECISION_TYPE_STYLES[dec.decisionType] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)] border-[var(--border-default)]'
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
                            {decisionTypeLabel}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{dec.agentName}</td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <div className="w-16 h-2 bg-[var(--bg-tertiary)] rounded-full overflow-hidden">
                              <div
                                className={`h-full rounded-full ${confidenceColor(dec.confidence)}`}
                                style={{ width: `${Math.round(dec.confidence)}%` }}
                              />
                            </div>
                            <span className={`text-xs font-medium ${confidenceTextColor(dec.confidence)}`}>
                              {Math.round(dec.confidence)}%
                            </span>
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          {dec.wasOverridden ? (
                            <span className="inline-flex items-center gap-1 text-xs text-[var(--nexus-warning-700)] bg-[var(--nexus-warning-50)] px-2 py-0.5 rounded-full">
                              <AlertTriangle className="w-3 h-3" />
                              {dec.overriddenBy ? `Overridden by ${dec.overriddenBy}` : 'Overridden'}
                            </span>
                          ) : (
                            <span className="text-xs text-[var(--nexus-success-600)]">Auto-approved</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right text-xs text-[var(--text-tertiary)]">
                          {formatTime(dec.processingTimeMs)}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Override Modal */}
      {overrideState && (
        <div className="enterprise-modal-overlay" onClick={() => setOverrideState(null)}>
          <div className="enterprise-card p-6 w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Override AI Decision</h2>

            <div className="space-y-4">
              <div>
                <label className="enterprise-label">Current AI Recommendation</label>
                <div className="p-3 rounded-lg bg-[var(--nexus-warning-50)] border border-[var(--nexus-warning-200)]">
                  <div className="flex items-center gap-2 mb-1">
                    <Brain className="w-4 h-4 text-[var(--nexus-warning-600)]" />
                    <span className="text-sm font-medium text-[var(--nexus-warning-800)]">{overrideState.agentName}</span>
                  </div>
                  <p className="text-sm text-[var(--nexus-warning-700)]">{overrideState.currentDecision}</p>
                  <div className="flex items-center gap-2 mt-1">
                    <div className="w-20 h-1.5 bg-[var(--nexus-warning-200)] rounded-full overflow-hidden">
                      <div
                        className="h-full bg-[var(--nexus-warning-50)]0 rounded-full"
                        style={{ width: `${overrideState.currentConfidence}%` }}
                      />
                    </div>
                    <span className="text-xs text-[var(--nexus-warning-600)]">{overrideState.currentConfidence}% confidence</span>
                  </div>
                </div>
              </div>

              <div>
                <label className="enterprise-label">Override Reason <span className="text-[var(--nexus-error-500)]">*</span></label>
                <Autocomplete
                  value={overrideReason}
                  onChange={setOverrideReason}
                  placeholder="Explain why this decision is being overridden..."
                  minChars={0}
                  inputClassName="enterprise-input w-full min-h-[80px] resize-none"
                />
              </div>

              <div>
                <label className="enterprise-label">New Decision</label>
                <Autocomplete
                  value={overrideDecision}
                  onChange={setOverrideDecision}
                  minChars={0}
                  suggestions={['Route to WH #1', 'Route to WH #2', 'Route to WH #3', 'Route to Store #5', 'Route to Store #3', 'Flag for Review', 'Auto-Route to WH #1', 'Cancel Order']}
                  inputClassName="enterprise-input w-full"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 mt-6">
              <button className="enterprise-btn-secondary" onClick={() => setOverrideState(null)}>
                Cancel
              </button>
              <PermissionGate resource="settings" action="edit">
                <button
                  className="enterprise-btn-primary"
                  disabled={!overrideReason.trim()}
                  onClick={handleOverrideConfirm}
                >
                  <AlertTriangle className="w-4 h-4" /> Confirm Override
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
