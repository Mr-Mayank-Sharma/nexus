import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Gauge, Plus, Sliders, Shield, Trash2, Edit, ToggleLeft, ToggleRight, RefreshCw,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import atpApi from '../api/atp'
import type { NxATPRule, NxATPSnapshot } from '../api/atp'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import type { Tab } from '../components/enterprise'

type AtpTab = 'rules' | 'snapshots'

export default function ATPRulesPage() {
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<AtpTab>('rules')
  const [searchTerm, setSearchTerm] = useState('')
  const [showCreateRule, setShowCreateRule] = useState(false)
  const [editingRule, setEditingRule] = useState<NxATPRule | null>(null)

  // Form state for create/edit
  const [formRule, setFormRule] = useState<Partial<NxATPRule>>({
    ruleName: '',
    ruleType: 'PERCENTAGE',
    priority: 10,
    enabled: true,
    formula: '',
    leadTimeDays: 0,
    safetyStock: 0,
    reorderPoint: 0,
    reorderQuantity: 0,
    description: '',
  })

  // ─── Queries ────────────────────────────────────────────────────────

  const { data: rules = [], isLoading: loadingRules, refetch: refetchRules } = useQuery({
    queryKey: ['atp-rules'],
    queryFn: async () => {
      const res = await atpApi.getRules()
      return res.data as NxATPRule[]
    },
  })

  const { data: snapshots = [], isLoading: loadingSnapshots, refetch: refetchSnapshots } = useQuery({
    queryKey: ['atp-snapshots'],
    queryFn: async () => {
      const res = await atpApi.getSnapshots()
      return res.data as NxATPSnapshot[]
    },
    enabled: activeTab === 'snapshots',
  })

  // ─── Mutations ──────────────────────────────────────────────────────

  const createMutation = useMutation({
    mutationFn: (data: NxATPRule) => atpApi.createRule(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['atp-rules'] })
      addToast({ type: 'success', title: 'Rule created' })
      setShowCreateRule(false)
      resetForm()
    },
    onError: (err: any) => addToast({ type: 'error', title: 'Failed to create rule', message: err.message }),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: NxATPRule }) => atpApi.updateRule(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['atp-rules'] })
      addToast({ type: 'success', title: 'Rule updated' })
      setEditingRule(null)
      resetForm()
    },
    onError: (err: any) => addToast({ type: 'error', title: 'Failed to update rule', message: err.message }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => atpApi.deleteRule(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['atp-rules'] })
      addToast({ type: 'success', title: 'Rule deleted' })
    },
    onError: (err: any) => addToast({ type: 'error', title: 'Failed to delete rule', message: err.message }),
  })

  const toggleMutation = useMutation({
    mutationFn: (rule: NxATPRule) => atpApi.updateRule(rule.id!, { ...rule, enabled: !rule.enabled }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['atp-rules'] })
      addToast({ type: 'success', title: 'Rule toggled' })
    },
    onError: (err: any) => addToast({ type: 'error', title: 'Failed to toggle rule', message: err.message }),
  })

  // ─── Helpers ────────────────────────────────────────────────────────

  function resetForm() {
    setFormRule({
      ruleName: '',
      ruleType: 'PERCENTAGE',
      priority: 10,
      enabled: true,
      formula: '',
      leadTimeDays: 0,
      safetyStock: 0,
      reorderPoint: 0,
      reorderQuantity: 0,
      description: '',
    })
  }

  function startEdit(rule: NxATPRule) {
    setFormRule({ ...rule })
    setEditingRule(rule)
    setShowCreateRule(true)
  }

  function handleSubmit() {
    if (!formRule.ruleName) {
      addToast({ type: 'error', title: 'Rule name is required' })
      return
    }
    if (editingRule?.id) {
      updateMutation.mutate({ id: editingRule.id, data: formRule as NxATPRule })
    } else {
      createMutation.mutate(formRule as NxATPRule)
    }
  }

  // ─── Filtering ──────────────────────────────────────────────────────

  const filteredRules = rules.filter(r =>
    !searchTerm || r.ruleName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
    r.description?.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const filteredSnapshots = snapshots.filter(s =>
    !searchTerm || s.sku?.toLowerCase().includes(searchTerm.toLowerCase()) ||
    s.nodeName?.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const activeRulesCount = rules.filter(r => r.enabled).length

  const tabs: Tab[] = [
    { id: 'rules', label: 'ATP Rules', badge: activeRulesCount },
    { id: 'snapshots', label: 'Snapshots', badge: snapshots.length },
  ]

  const formatDate = (d: string) => new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })

  const getRuleTypeBadge = (type: string) => {
    switch (type) {
      case 'PERCENTAGE': return 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)] dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]'
      case 'FIXED': return 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)] dark:bg-[var(--nexus-success-900)]/30 dark:text-[var(--nexus-success-400)]'
      case 'DYNAMIC': return 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-800)] dark:bg-[var(--nexus-ai-900)]/30 dark:text-[var(--nexus-ai-400)]'
      default: return 'bg-[var(--surface-muted)] text-[var(--text-primary)] bg-[var(--surface-sunken)]/30 dark:text-[var(--text-tertiary)]'
    }
  }

  // ─── Render ─────────────────────────────────────────────────────────

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Gauge className="w-7 h-7 text-[var(--nexus-primary-500)]" /> ATP Rules
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Available to Promise — stock calculation rules and snapshots</p>
        </div>
        <div className="flex items-center gap-2">
          <EnterpriseKPICard title="Active Rules" value={activeRulesCount} icon={<Gauge className="w-4 h-4" />} color="primary" trend={null} />
          <EnterpriseKPICard title="Snapshots" value={snapshots.length} icon={<Shield className="w-4 h-4" />} color="info" trend={null} />
        </div>
      </div>

      <EnterpriseTabs tabs={tabs} activeTab={activeTab} onChange={t => setActiveTab(t as AtpTab)} />

      <div className="flex items-center gap-3">
        <Autocomplete
          value={searchTerm}
          onChange={setSearchTerm}
          placeholder={activeTab === 'rules' ? 'Search rules...' : 'Search by SKU or node...'}
          minChars={0}
          className="flex-1 max-w-md"
        />
        <PermissionGate resource="inventory" action="create">
          <button
            onClick={() => { resetForm(); setEditingRule(null); setShowCreateRule(true) }}
            className="enterprise-btn-primary text-sm flex items-center gap-1.5 px-4 py-2.5"
          >
            <Plus className="w-4 h-4" /> Add Rule
          </button>
        </PermissionGate>
        <button
          onClick={() => activeTab === 'rules' ? refetchRules() : refetchSnapshots()}
          className="enterprise-btn-secondary px-3 py-2"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {/* ─── Create/Edit Rule Form ───────────────────────────────────── */}
      {showCreateRule && (
        <div className="enterprise-card p-6 space-y-4">
          <h3 className="text-lg font-semibold text-[var(--text-primary)]">
            {editingRule ? 'Edit ATP Rule' : 'Create ATP Rule'}
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Rule Name *</label>
              <input type="text" value={formRule.ruleName || ''} onChange={e => setFormRule(p => ({ ...p, ruleName: e.target.value }))}
                className="enterprise-input w-full" placeholder="e.g. Electronics ATP Rule" />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Rule Type</label>
              <select value={formRule.ruleType || 'PERCENTAGE'} onChange={e => setFormRule(p => ({ ...p, ruleType: e.target.value as any }))}
                className="enterprise-input w-full">
                <option value="PERCENTAGE">Percentage</option>
                <option value="FIXED">Fixed</option>
                <option value="DYNAMIC">Dynamic</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Priority</label>
              <input type="number" value={formRule.priority || 10} onChange={e => setFormRule(p => ({ ...p, priority: parseInt(e.target.value) || 10 }))}
                className="enterprise-input w-full" min={1} max={100} />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Safety Stock</label>
              <input type="number" value={formRule.safetyStock || 0} onChange={e => setFormRule(p => ({ ...p, safetyStock: parseInt(e.target.value) || 0 }))}
                className="enterprise-input w-full" min={0} />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Reorder Point</label>
              <input type="number" value={formRule.reorderPoint || 0} onChange={e => setFormRule(p => ({ ...p, reorderPoint: parseInt(e.target.value) || 0 }))}
                className="enterprise-input w-full" min={0} />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Lead Time (days)</label>
              <input type="number" value={formRule.leadTimeDays || 0} onChange={e => setFormRule(p => ({ ...p, leadTimeDays: parseInt(e.target.value) || 0 }))}
                className="enterprise-input w-full" min={0} />
            </div>
            <div className="md:col-span-2">
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Formula / Notes</label>
              <input type="text" value={formRule.description || ''} onChange={e => setFormRule(p => ({ ...p, description: e.target.value }))}
                className="enterprise-input w-full" placeholder="Description or formula" />
            </div>
            <div className="flex items-end gap-2">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={formRule.enabled ?? true} onChange={e => setFormRule(p => ({ ...p, enabled: e.target.checked }))}
                  className="rounded border-[var(--border-default)] text-[var(--text-brand)] focus:ring-[var(--nexus-primary-500)]" />
                <span className="text-sm text-[var(--text-secondary)]">Enabled</span>
              </label>
            </div>
          </div>
          <div className="flex items-center gap-2 pt-2">
            <button onClick={handleSubmit} disabled={createMutation.isPending || updateMutation.isPending}
              className="enterprise-btn-primary px-4 py-2 text-sm">
              {editingRule ? 'Update Rule' : 'Create Rule'}
            </button>
            <button onClick={() => { setShowCreateRule(false); setEditingRule(null); resetForm() }}
              className="enterprise-btn-secondary px-4 py-2 text-sm">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* ─── Rules Tab ───────────────────────────────────────────────── */}
      {activeTab === 'rules' && (
        <>
          {loadingRules ? (
            <div className="enterprise-card flex items-center justify-center p-12">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
            </div>
          ) : filteredRules.length === 0 ? (
            <div className="enterprise-card p-12 text-center">
              <Sliders className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
              <p className="font-medium text-[var(--text-secondary)]">No ATP rules found</p>
            </div>
          ) : (
            <div className="space-y-3">
              {filteredRules.map(rule => (
                <div key={rule.id} className="enterprise-card p-4 hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)]/30 transition-colors">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <Sliders className="w-5 h-5 text-[var(--nexus-primary-500)]" />
                      <div>
                        <p className="text-sm font-medium text-[var(--text-primary)]">{rule.ruleName}</p>
                        <p className="text-xs text-[var(--text-secondary)]">
                          <span className={clsx('inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium', getRuleTypeBadge(rule.ruleType))}>
                            {rule.ruleType}
                          </span>
                          {' · '}Priority {rule.priority}
                          {rule.description ? ` · ${rule.description}` : ''}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="text-right text-xs text-[var(--text-secondary)]">
                        <div>Safety: {rule.safetyStock || 0} · Reorder: {rule.reorderPoint || 0}</div>
                        <div>Lead: {rule.leadTimeDays || 0}d</div>
                      </div>
                      <PermissionGate resource="inventory" action="edit">
                        <button onClick={() => toggleMutation.mutate(rule)} className="p-1 hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] rounded transition-colors">
                          {rule.enabled ? <ToggleRight className="w-6 h-6 text-[var(--text-brand)]" /> : <ToggleLeft className="w-6 h-6 text-[var(--text-tertiary)]" />}
                        </button>
                      </PermissionGate>
                      <PermissionGate resource="inventory" action="edit">
                        <button onClick={() => startEdit(rule)} className="p-1 hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-primary-500)] transition-colors">
                          <Edit className="w-4 h-4" />
                        </button>
                      </PermissionGate>
                      <PermissionGate resource="inventory" action="delete">
                        <button onClick={() => rule.id && deleteMutation.mutate(rule.id)}
                          className="p-1 hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-error-500)] transition-colors">
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </PermissionGate>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {/* ─── Snapshots Tab ───────────────────────────────────────────── */}
      {activeTab === 'snapshots' && (
        <>
          {loadingSnapshots ? (
            <div className="enterprise-card flex items-center justify-center p-12">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
            </div>
          ) : filteredSnapshots.length === 0 ? (
            <div className="enterprise-card p-12 text-center">
              <Shield className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
              <p className="font-medium text-[var(--text-secondary)]">No ATP snapshots found</p>
            </div>
          ) : (
            <div className="enterprise-card overflow-hidden">
              <div className="overflow-x-auto">
                <table className="enterprise-table w-full">
                  <thead>
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Node</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">SKU</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Physical</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Reserved</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Allocated</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">In Transit</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">ATP</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Calculated</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                    {filteredSnapshots.map(snapshot => (
                      <tr key={snapshot.id} className="enterprise-table-row">
                        <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{snapshot.nodeName || snapshot.nodeId}</td>
                        <td className="px-4 py-3 text-xs font-mono text-[var(--text-secondary)]">{snapshot.sku}</td>
                        <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{snapshot.physicalStock}</td>
                        <td className="px-4 py-3 text-center text-sm text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]">{snapshot.reservedStock}</td>
                        <td className="px-4 py-3 text-center text-sm text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]">{snapshot.allocatedStock}</td>
                        <td className="px-4 py-3 text-center text-sm text-[var(--nexus-ai-600)] dark:text-[var(--nexus-ai-400)]">{snapshot.inTransitStock}</td>
                        <td className="px-4 py-3 text-center">
                          <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-bold',
                            snapshot.availableToPromise > 0 ? 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)] dark:bg-[var(--nexus-success-900)]/30 dark:text-[var(--nexus-success-400)]' : 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-400)]')}>
                            {snapshot.availableToPromise}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-center text-xs text-[var(--text-secondary)]">
                          {snapshot.lastCalculatedAt ? formatDate(snapshot.lastCalculatedAt) : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}