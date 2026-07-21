import { useState, useEffect, useCallback } from 'react'
import { clsx } from 'clsx'
import { Package, Plus, X, Trash2, RefreshCw, BarChart3, Loader2, Check, ArrowDown, ArrowUp } from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import { useToast } from '../hooks/useToast'
import * as replenishmentApi from '../api/replenishment'

type Tab = 'rules' | 'suggestions' | 'stats'

interface ReplenishmentRule {
  id: string
  ruleName: string
  sku: string
  warehouseId: string
  reorderPoint: number
  reorderQty: number
  targetQty: number
  priority: string
  isActive: boolean
  notes?: string
}

interface ReplenishmentSuggestion {
  id: string
  sku: string
  warehouseId: string
  currentQty: number
  suggestedQty: number
  reorderPoint: number
  reorderQty: number
  status: string
  approvedBy?: string
  approvedAt?: string
  createdAt: string
  notes?: string
}

const statusColors: Record<string, string> = {
  PENDING: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]',
  APPROVED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]',
  REJECTED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)]',
  FULFILLED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]',
  EXPIRED: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
}

const warehouses = [
  { id: 'wh-main', name: 'Main Warehouse' },
  { id: 'wh-east', name: 'East DC' },
  { id: 'wh-west', name: 'West DC' },
]

function generateMockRules(): ReplenishmentRule[] {
  return [
    { id: 'r-1', ruleName: 'Auto-Reorder Electronics', sku: 'ELEC-001', warehouseId: 'wh-main', reorderPoint: 50, reorderQty: 200, targetQty: 300, priority: 'HIGH', isActive: true },
    { id: 'r-2', ruleName: 'Bulk Restock Apparel', sku: 'APP-012', warehouseId: 'wh-main', reorderPoint: 100, reorderQty: 500, targetQty: 600, priority: 'MEDIUM', isActive: true },
    { id: 'r-3', ruleName: 'Low Stock Alert Home', sku: 'HOME-005', warehouseId: 'wh-main', reorderPoint: 25, reorderQty: 100, targetQty: 125, priority: 'LOW', isActive: true },
    { id: 'r-4', ruleName: 'Seasonal Reorder', sku: 'SEAS-003', warehouseId: 'wh-main', reorderPoint: 30, reorderQty: 150, targetQty: 200, priority: 'HIGH', isActive: false },
  ]
}

function generateMockSuggestions(): ReplenishmentSuggestion[] {
  const now = new Date().toISOString()
  return [
    { id: 's-1', sku: 'ELEC-001', warehouseId: 'wh-main', currentQty: 35, suggestedQty: 265, reorderPoint: 50, reorderQty: 200, status: 'PENDING', createdAt: now },
    { id: 's-2', sku: 'APP-012', warehouseId: 'wh-main', currentQty: 80, suggestedQty: 420, reorderPoint: 100, reorderQty: 500, status: 'APPROVED', approvedBy: 'admin', approvedAt: now, createdAt: now },
    { id: 's-3', sku: 'HOME-005', warehouseId: 'wh-main', currentQty: 15, suggestedQty: 110, reorderPoint: 25, reorderQty: 100, status: 'PENDING', createdAt: now },
    { id: 's-4', sku: 'ELEC-002', warehouseId: 'wh-main', currentQty: 200, suggestedQty: 0, reorderPoint: 50, reorderQty: 200, status: 'REJECTED', notes: 'Not needed', createdAt: now },
  ]
}

export default function ReplenishmentPage() {
  const { addToast } = useToast()
  const [loading, setLoading] = useState(true)
  const [selectedWarehouse, setSelectedWarehouse] = useState('wh-main')
  const [activeTab, setActiveTab] = useState<Tab>('rules')

  const [rules, setRules] = useState<ReplenishmentRule[]>([])
  const [suggestions, setSuggestions] = useState<ReplenishmentSuggestion[]>([])
  const [stats, setStats] = useState<Record<string, unknown> | null>(null)

  const [showRuleModal, setShowRuleModal] = useState(false)
  const [ruleForm, setRuleForm] = useState({ ruleName: '', sku: '', reorderPoint: '', reorderQty: '', targetQty: '', priority: 'MEDIUM', notes: '' })
  const [saving, setSaving] = useState(false)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [rulesRes, suggestionsRes, statsRes] = await Promise.allSettled([
        replenishmentApi.getRules(selectedWarehouse),
        replenishmentApi.getSuggestions(selectedWarehouse),
        replenishmentApi.getStats(selectedWarehouse),
      ])

      const rulesData = rulesRes.status === 'fulfilled' ? (rulesRes.value.data as ReplenishmentRule[]) : null
      const suggestionsData = suggestionsRes.status === 'fulfilled' ? (suggestionsRes.value.data as ReplenishmentSuggestion[]) : null
      const statsData = statsRes.status === 'fulfilled' ? statsRes.value.data : null

      setRules(rulesData && rulesData.length > 0 ? rulesData : generateMockRules())
      setSuggestions(suggestionsData && suggestionsData.length > 0 ? suggestionsData : generateMockSuggestions())
      setStats(statsData || { totalRules: 4, pendingSuggestions: 2, approvedToday: 1, rejectedToday: 1 })
    } catch {
      setRules(generateMockRules())
      setSuggestions(generateMockSuggestions())
      setStats({ totalRules: 4, pendingSuggestions: 2, approvedToday: 1, rejectedToday: 1 })
    } finally {
      setLoading(false)
    }
  }, [selectedWarehouse])

  useEffect(() => { loadData() }, [loadData])

  async function handleCreateRule() {
    if (!ruleForm.ruleName.trim() || !ruleForm.sku.trim()) {
      addToast({ type: 'warning', title: 'Rule name and SKU are required' })
      return
    }
    setSaving(true)
    try {
      await replenishmentApi.createRule({
        warehouseId: selectedWarehouse,
        ruleName: ruleForm.ruleName,
        sku: ruleForm.sku,
        reorderPoint: Number(ruleForm.reorderPoint),
        reorderQty: Number(ruleForm.reorderQty),
        targetQty: Number(ruleForm.targetQty),
        priority: ruleForm.priority,
        notes: ruleForm.notes,
        isActive: true,
      })
      addToast({ type: 'success', title: 'Rule created' })
      setShowRuleModal(false)
      setRuleForm({ ruleName: '', sku: '', reorderPoint: '', reorderQty: '', targetQty: '', priority: 'MEDIUM', notes: '' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to create rule' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDeleteRule(id: string) {
    try {
      await replenishmentApi.deleteRule(id)
      addToast({ type: 'success', title: 'Rule deactivated' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to deactivate rule' })
    }
  }

  async function handleApproveSuggestion(id: string) {
    try {
      await replenishmentApi.approveSuggestion(id, 'admin')
      addToast({ type: 'success', title: 'Suggestion approved' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to approve suggestion' })
    }
  }

  async function handleRejectSuggestion(id: string) {
    try {
      await replenishmentApi.rejectSuggestion(id, 'Rejected via UI')
      addToast({ type: 'success', title: 'Suggestion rejected' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to reject suggestion' })
    }
  }

  async function handleGenerate() {
    try {
      await replenishmentApi.generateSuggestions(selectedWarehouse)
      addToast({ type: 'success', title: 'Suggestions generated' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to generate suggestions' })
    }
  }

  const pendingSuggestions = suggestions.filter((s) => s.status === 'PENDING').length
  const approvedToday = suggestions.filter((s) => s.status === 'APPROVED').length
  const rejectedToday = suggestions.filter((s) => s.status === 'REJECTED').length

  const tabs: { key: Tab; label: string }[] = [
    { key: 'rules', label: 'Rules' },
    { key: 'suggestions', label: 'Suggestions' },
    { key: 'stats', label: 'Stats' },
  ]

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="w-8 h-8 animate-spin text-[var(--nexus-primary-500)]" />
      </div>
    )
  }

  return (
    <div className="enterprise-page space-y-6">
      <EnterpriseBreadcrumbs crumbs={[
        { label: 'Home', path: '/' },
        { label: 'Warehouse', path: '/warehouse' },
        { label: 'Replenishment Rules' },
      ]} />

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Package className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Replenishment Rules
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Automated replenishment engine and suggestion management
          </p>
        </div>
      </div>

      <div className="enterprise-kpi-grid grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <EnterpriseKPICard title="Total Rules" value={rules.length} icon={<Package className="w-5 h-5" />} color="primary" />
        <EnterpriseKPICard title="Pending Suggestions" value={pendingSuggestions} icon={<RefreshCw className="w-5 h-5" />} color={pendingSuggestions > 0 ? 'warning' : 'success'} />
        <EnterpriseKPICard title="Approved" value={approvedToday} icon={<Check className="w-5 h-5" />} color="success" />
        <EnterpriseKPICard title="Rejected" value={rejectedToday} icon={<X className="w-5 h-5" />} color={rejectedToday > 0 ? 'error' : 'success'} />
      </div>

      <div className="enterprise-toolbar flex flex-wrap items-center gap-3 p-4 bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)]">
        <div className="flex items-center gap-2">
          <label className="text-xs font-medium text-[var(--text-secondary)]">Warehouse</label>
          <select
            value={selectedWarehouse}
            onChange={(e) => setSelectedWarehouse(e.target.value)}
            className="enterprise-input text-sm py-1.5"
          >
            {warehouses.map((wh) => (
              <option key={wh.id} value={wh.id}>{wh.name}</option>
            ))}
          </select>
        </div>

        <div className="h-6 w-px bg-[var(--surface-muted)] bg-[var(--surface-muted)]" />

        <div className="flex-1" />

        {activeTab === 'rules' && (
          <button onClick={() => setShowRuleModal(true)} className="enterprise-btn enterprise-btn-primary text-sm">
            <Plus className="w-4 h-4" /> Add Rule
          </button>
        )}
        {activeTab === 'suggestions' && (
          <button onClick={handleGenerate} className="enterprise-btn enterprise-btn-primary text-sm">
            <RefreshCw className="w-4 h-4" /> Generate Suggestions
          </button>
        )}

        <div className="h-6 w-px bg-[var(--surface-muted)] bg-[var(--surface-muted)]" />

        <div className="flex items-center rounded-lg border border-[var(--border-default)] overflow-hidden">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={clsx(
                'flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium transition-colors',
                activeTab === tab.key
                  ? 'bg-[var(--nexus-primary-600)] text-white'
                  : 'text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)]',
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      <div className="enterprise-card p-5">
        {activeTab === 'rules' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-[var(--text-secondary)]">Active Rules</h3>
              <span className="text-xs text-[var(--text-secondary)]">{rules.length} rules</span>
            </div>
            <div className="overflow-x-auto rounded-xl border border-[var(--border-default)]">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
                    <th className="px-4 py-3">Rule Name</th>
                    <th className="px-4 py-3">SKU</th>
                    <th className="px-4 py-3">Reorder Point</th>
                    <th className="px-4 py-3">Reorder Qty</th>
                    <th className="px-4 py-3">Target Qty</th>
                    <th className="px-4 py-3">Priority</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                  {rules.map((rule) => (
                    <tr key={rule.id} className="hover:bg-[var(--surface-sunken)]/50 hover:bg-[var(--surface-base)]/30 transition-colors">
                      <td className="px-4 py-3 font-medium text-[var(--text-primary)]">{rule.ruleName}</td>
                      <td className="px-4 py-3 text-[var(--text-secondary)] font-mono text-xs">{rule.sku}</td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">{rule.reorderPoint}</td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">{rule.reorderQty}</td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">{rule.targetQty}</td>
                      <td className="px-4 py-3">
                        <span className={clsx(
                          'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
                          rule.priority === 'HIGH' && 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)]',
                          rule.priority === 'MEDIUM' && 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]',
                          rule.priority === 'LOW' && 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
                        )}>
                          {rule.priority}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx(
                          'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
                          rule.isActive ? 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]' : 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
                        )}>
                          {rule.isActive ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        {rule.isActive && (
                          <button
                            onClick={() => handleDeleteRule(rule.id)}
                            className="p-1.5 rounded-lg text-[var(--nexus-error-500)] hover:bg-[var(--nexus-error-50)] dark:hover:bg-[var(--nexus-error-900)]/20 transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {rules.length === 0 && (
              <div className="enterprise-empty-state py-16">
                <Package className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] mx-auto mb-3" />
                <p className="text-[var(--text-secondary)] text-sm">No rules configured yet</p>
              </div>
            )}
          </div>
        )}

        {activeTab === 'suggestions' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-[var(--text-secondary)]">Replenishment Suggestions</h3>
              <span className="text-xs text-[var(--text-secondary)]">{suggestions.length} suggestions</span>
            </div>
            <div className="overflow-x-auto rounded-xl border border-[var(--border-default)]">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
                    <th className="px-4 py-3">SKU</th>
                    <th className="px-4 py-3">Current Qty</th>
                    <th className="px-4 py-3">Suggested Qty</th>
                    <th className="px-4 py-3">Reorder Point</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Created</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                  {suggestions.map((s) => (
                    <tr key={s.id} className="hover:bg-[var(--surface-sunken)]/50 hover:bg-[var(--surface-base)]/30 transition-colors">
                      <td className="px-4 py-3 font-medium text-[var(--text-primary)] font-mono text-xs">{s.sku}</td>
                      <td className="px-4 py-3">
                        <span className={clsx('flex items-center gap-1 text-sm', s.currentQty < s.reorderPoint ? 'text-[var(--nexus-error-600)] font-medium' : 'text-[var(--text-secondary)]')}>
                          {s.currentQty < s.reorderPoint && <ArrowDown className="w-3 h-3" />}
                          {s.currentQty}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className="flex items-center gap-1 text-sm text-[var(--nexus-success-600)] font-medium">
                          <ArrowUp className="w-3 h-3" />
                          {s.suggestedQty}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">{s.reorderPoint}</td>
                      <td className="px-4 py-3">
                        <span className={clsx(
                          'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold',
                          statusColors[s.status] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
                        )}>
                          {s.status.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-[var(--text-secondary)]">
                        {new Date(s.createdAt).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {s.status === 'PENDING' && (
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              onClick={() => handleApproveSuggestion(s.id)}
                              className="px-2.5 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-success-600)] text-white hover:bg-[var(--nexus-success-700)] transition-colors"
                            >
                              <Check className="w-3 h-3 inline mr-1" /> Approve
                            </button>
                            <button
                              onClick={() => handleRejectSuggestion(s.id)}
                              className="px-2.5 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] hover:bg-[var(--nexus-error-200)] transition-colors"
                            >
                              Reject
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {suggestions.length === 0 && (
              <div className="enterprise-empty-state py-16">
                <RefreshCw className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] mx-auto mb-3" />
                <p className="text-[var(--text-secondary)] text-sm">No suggestions yet. Click "Generate Suggestions" to start.</p>
              </div>
            )}
          </div>
        )}

        {activeTab === 'stats' && (
          <div className="space-y-6">
            <h3 className="text-sm font-semibold text-[var(--text-secondary)] flex items-center gap-2">
              <BarChart3 className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Replenishment Statistics
            </h3>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {[
                { label: 'Total Rules', value: rules.length, color: 'text-[var(--nexus-primary-600)]' },
                { label: 'Active Rules', value: rules.filter((r) => r.isActive).length, color: 'text-[var(--nexus-success-600)]' },
                { label: 'Pending', value: suggestions.filter((s) => s.status === 'PENDING').length, color: 'text-[var(--nexus-warning-600)]' },
                { label: 'Approved', value: suggestions.filter((s) => s.status === 'APPROVED').length, color: 'text-[var(--nexus-success-600)]' },
                { label: 'Rejected', value: suggestions.filter((s) => s.status === 'REJECTED').length, color: 'text-[var(--nexus-error-600)]' },
                { label: 'Fulfilled', value: suggestions.filter((s) => s.status === 'FULFILLED').length, color: 'text-[var(--nexus-primary-600)]' },
                { label: 'Low Stock Items', value: suggestions.filter((s) => s.currentQty < s.reorderPoint).length, color: 'text-[var(--nexus-error-600)]' },
                { label: 'Total Reorder Qty', value: suggestions.filter((s) => s.status === 'APPROVED').reduce((sum, s) => sum + s.suggestedQty, 0), color: 'text-[var(--text-primary)]' },
              ].map((stat) => (
                <div key={stat.label} className="enterprise-card p-4 text-center">
                  <p className={clsx('text-2xl font-bold', stat.color)}>{stat.value}</p>
                  <p className="text-xs text-[var(--text-secondary)] mt-1">{stat.label}</p>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {showRuleModal && (
        <div className="enterprise-modal-overlay" onClick={() => setShowRuleModal(false)}>
          <div
            className="enterprise-modal max-w-lg w-full max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">New Replenishment Rule</h2>
              <button onClick={() => setShowRuleModal(false)} className="p-1.5 hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] rounded-lg transition-colors">
                <X className="w-5 h-5 text-[var(--text-secondary)]" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Rule Name *</label>
                <input
                  value={ruleForm.ruleName}
                  onChange={(e) => setRuleForm({ ...ruleForm, ruleName: e.target.value })}
                  className="enterprise-input w-full text-sm"
                  placeholder="Auto-Reorder Electronics"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">SKU *</label>
                <input
                  value={ruleForm.sku}
                  onChange={(e) => setRuleForm({ ...ruleForm, sku: e.target.value })}
                  className="enterprise-input w-full text-sm font-mono"
                  placeholder="ELEC-001"
                />
              </div>
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Reorder Point</label>
                  <input
                    type="number"
                    min={0}
                    value={ruleForm.reorderPoint}
                    onChange={(e) => setRuleForm({ ...ruleForm, reorderPoint: e.target.value })}
                    className="enterprise-input w-full text-sm"
                    placeholder="50"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Reorder Qty</label>
                  <input
                    type="number"
                    min={0}
                    value={ruleForm.reorderQty}
                    onChange={(e) => setRuleForm({ ...ruleForm, reorderQty: e.target.value })}
                    className="enterprise-input w-full text-sm"
                    placeholder="200"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Target Qty</label>
                  <input
                    type="number"
                    min={0}
                    value={ruleForm.targetQty}
                    onChange={(e) => setRuleForm({ ...ruleForm, targetQty: e.target.value })}
                    className="enterprise-input w-full text-sm"
                    placeholder="300"
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Priority</label>
                <select
                  value={ruleForm.priority}
                  onChange={(e) => setRuleForm({ ...ruleForm, priority: e.target.value })}
                  className="enterprise-input w-full text-sm"
                >
                  <option value="HIGH">High</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="LOW">Low</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Notes</label>
                <textarea
                  value={ruleForm.notes}
                  onChange={(e) => setRuleForm({ ...ruleForm, notes: e.target.value })}
                  className="enterprise-input w-full text-sm"
                  rows={3}
                  placeholder="Optional notes"
                />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowRuleModal(false)} className="enterprise-btn enterprise-btn-secondary text-sm">Cancel</button>
              <button onClick={handleCreateRule} disabled={saving} className="enterprise-btn enterprise-btn-primary text-sm">
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />} Create Rule
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
