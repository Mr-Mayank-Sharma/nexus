import { useState, useEffect } from 'react'
import { Plus, GripVertical, Pencil, Trash2, ToggleLeft, ToggleRight, ArrowUp, ArrowDown, X, Loader2, Route } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import { RoutingRule } from '../types'
import * as routingRulesApi from '../api/routingRules'
import PermissionGate from '../components/rbac/PermissionGate'
import StatusBadge from '../components/common/StatusBadge'
import Autocomplete from '../components/common/Autocomplete'

export default function RoutingRulesPage() {
  const [rules, setRules] = useState<RoutingRule[]>([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [editingRule, setEditingRule] = useState<RoutingRule | null>(null)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState({ name: '', description: '', ruleType: 'NEAREST_AVAILABLE', conditions: '', actions: '', priority: 1, isActive: true })
  const { addToast } = useToast()

  const ruleTypeOptions = [
    { value: 'NEAREST_AVAILABLE', label: 'Nearest Available' },
    { value: 'LOWEST_COST', label: 'Lowest Cost' },
    { value: 'BEST_CARRIER', label: 'Best Carrier' },
    { value: 'CAPACITY_BASED', label: 'Capacity Based' },
    { value: 'CUSTOMER_ZONE', label: 'Customer Zone' },
    { value: 'INVENTORY_THRESHOLD', label: 'Inventory Threshold' },
  ]

  useEffect(() => { fetchRules() }, [])

  async function fetchRules() {
    try {
      setLoading(true)
      const res = await routingRulesApi.getRules()
      setRules(Array.isArray(res.data) ? res.data : res.data?.content ?? [])
    } catch { addToast({ type: 'error', title: 'Failed to load routing rules' })
    } finally { setLoading(false) }
  }

  function openCreate() {
    setEditingRule(null)
    setForm({ name: '', description: '', ruleType: 'NEAREST_AVAILABLE', conditions: '', actions: '', priority: rules.length + 1, isActive: true })
    setShowModal(true)
  }

  function openEdit(rule: RoutingRule) {
    setEditingRule(rule)
    setForm({ name: rule.name, description: rule.description || '', ruleType: rule.ruleType, conditions: rule.conditions || '', actions: rule.actions || '', priority: rule.priority, isActive: rule.isActive })
    setShowModal(true)
  }

  async function handleSave() {
    if (!form.name.trim()) { addToast({ type: 'warning', title: 'Rule name is required' }); return }
    setSaving(true)
    try {
      if (editingRule) {
        await routingRulesApi.updateRule(editingRule.id, form)
        addToast({ type: 'success', title: 'Rule updated' })
      } else {
        await routingRulesApi.createRule(form)
        addToast({ type: 'success', title: 'Rule created' })
      }
      setShowModal(false)
      await fetchRules()
    } catch { addToast({ type: 'error', title: 'Failed to save rule' })
    } finally { setSaving(false) }
  }

  async function handleDelete(id: string) {
    try {
      await routingRulesApi.deleteRule(id)
      addToast({ type: 'success', title: 'Rule deleted' })
      await fetchRules()
    } catch { addToast({ type: 'error', title: 'Failed to delete rule' }) }
  }

  async function handleToggleActive(rule: RoutingRule) {
    try {
      await routingRulesApi.updateRule(rule.id, { ...rule, isActive: !rule.isActive, conditions: rule.conditions || '', actions: rule.actions || '' })
      await fetchRules()
    } catch { addToast({ type: 'error', title: 'Failed to toggle rule' }) }
  }

  async function handleMoveUp(index: number) {
    if (index === 0) return
    const newRules = [...rules]
    const [removed] = newRules.splice(index, 1)
    newRules.splice(index - 1, 0, removed)
    setRules(newRules)
    try {
      await routingRulesApi.reorderRules(newRules.map(r => r.id))
    } catch { addToast({ type: 'error', title: 'Failed to reorder' }); await fetchRules() }
  }

  async function handleMoveDown(index: number) {
    if (index === rules.length - 1) return
    const newRules = [...rules]
    const [removed] = newRules.splice(index, 1)
    newRules.splice(index + 1, 0, removed)
    setRules(newRules)
    try {
      await routingRulesApi.reorderRules(newRules.map(r => r.id))
    } catch { addToast({ type: 'error', title: 'Failed to reorder' }); await fetchRules() }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><Route className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Routing Rules</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Configure order routing logic for fulfillment allocation</p>
        </div>
        <PermissionGate resource="routing" action="create">
          <button onClick={openCreate} className="btn-primary text-sm">
            <Plus className="w-4 h-4" /> New Rule
          </button>
        </PermissionGate>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      ) : rules.length === 0 ? (
        <div className="text-center py-16 card">
          <Route className="w-12 h-12 text-[var(--text-tertiary)] mx-auto mb-3" />
          <p className="text-[var(--text-secondary)] text-sm">No routing rules configured. Create your first rule to control order allocation.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {rules.map((rule, index) => (
            <div key={rule.id} className={`card flex items-center gap-4 p-4 ${!rule.isActive ? 'opacity-60' : ''}`}>
              <div className="flex flex-col gap-0.5 text-[var(--text-tertiary)]">
                <button onClick={() => handleMoveUp(index)} className="hover:text-[var(--text-secondary)] disabled:opacity-30" disabled={index === 0}>
                  <ArrowUp className="w-3.5 h-3.5" />
                </button>
                <button onClick={() => handleMoveDown(index)} className="hover:text-[var(--text-secondary)] disabled:opacity-30" disabled={index === rules.length - 1}>
                  <ArrowDown className="w-3.5 h-3.5" />
                </button>
              </div>
              <GripVertical className="w-4 h-4 text-[var(--text-tertiary)] flex-shrink-0" />
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-[var(--text-primary)]">{rule.name}</span>
                  <StatusBadge status={rule.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                </div>
                {rule.description && <p className="text-xs text-[var(--text-secondary)] mt-0.5">{rule.description}</p>}
                <div className="flex items-center gap-3 mt-1">
                  <span className="text-xs bg-[var(--surface-muted)] px-2 py-0.5 rounded text-[var(--text-secondary)]">{rule.ruleType.replace(/_/g, ' ')}</span>
                  <span className="text-xs text-[var(--text-tertiary)]">Priority {rule.priority}</span>
                </div>
              </div>
              <div className="flex items-center gap-1">
                <PermissionGate resource="routing" action="edit">
                  <button onClick={() => handleToggleActive(rule)} className="p-1.5 hover:bg-[var(--surface-muted)] rounded text-[var(--text-secondary)] hover:text-[var(--text-secondary)]">
                    {rule.isActive ? <ToggleRight className="w-4 h-4 text-[var(--nexus-success-500)]" /> : <ToggleLeft className="w-4 h-4" />}
                  </button>
                </PermissionGate>
                <PermissionGate resource="routing" action="edit">
                  <button onClick={() => openEdit(rule)} className="p-1.5 hover:bg-[var(--surface-muted)] rounded text-[var(--text-secondary)] hover:text-[var(--text-secondary)]">
                    <Pencil className="w-4 h-4" />
                  </button>
                </PermissionGate>
                <PermissionGate resource="routing" action="delete">
                  <button onClick={() => handleDelete(rule.id)} className="p-1.5 hover:bg-[var(--nexus-error-50)] rounded text-[var(--text-secondary)] hover:text-[var(--nexus-error-600)]">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </PermissionGate>
              </div>
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-lg">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">{editingRule ? 'Edit Rule' : 'New Routing Rule'}</h2>
              <button onClick={() => setShowModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Rule Name</label>
                <Autocomplete value={form.name} onChange={(value) => setForm({ ...form, name: value })} inputClassName="input w-full" placeholder="e.g. Nearest Warehouse First" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Description</label>
                <Autocomplete value={form.description} onChange={(value) => setForm({ ...form, description: value })} inputClassName="input w-full" placeholder="Describe when this rule applies" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Rule Type</label>
                <Autocomplete
                  value={form.ruleType}
                  onChange={(value) => setForm({ ...form, ruleType: value })}
                  suggestions={ruleTypeOptions}
                  getOptionLabel={o => o.label}
                  getOptionValue={o => o.value}
                  inputClassName="input w-full"
                  minChars={0}
                  showSearchIcon={false}
                  clearable={false}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Conditions (JSON)</label>
                <Autocomplete value={form.conditions} onChange={(value) => setForm({ ...form, conditions: value })} inputClassName="input w-full font-mono text-xs" placeholder='{"maxDistance": 100, "carrier": "FEDEX"}' minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Actions (JSON)</label>
                <Autocomplete value={form.actions} onChange={(value) => setForm({ ...form, actions: value })} inputClassName="input w-full font-mono text-xs" placeholder='{"allocateTo": "NEAREST_NODE", "priority": "HIGH"}' minChars={0} showSearchIcon={false} clearable={false} />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="routing" action={editingRule ? 'edit' : 'create'}>
                <button onClick={handleSave} disabled={saving} className="btn-primary text-sm">
                  {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                  {editingRule ? 'Update' : 'Create'}
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
