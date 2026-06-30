import { useState, useEffect } from 'react'
import { Plus, GripVertical, Pencil, Trash2, ToggleLeft, ToggleRight, ArrowUp, ArrowDown, X, Loader2 } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import { RoutingRule } from '../types'
import * as routingRulesApi from '../api/routingRules'
import StatusBadge from '../components/common/StatusBadge'

export default function RoutingRulesPage() {
  const [rules, setRules] = useState<RoutingRule[]>([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [editingRule, setEditingRule] = useState<RoutingRule | null>(null)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState({ name: '', description: '', ruleType: 'NEAREST_AVAILABLE', conditions: '', actions: '', priority: 1, isActive: true })
  const { addToast } = useToast()

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
          <h1 className="text-2xl font-bold text-gray-900">Routing Rules</h1>
          <p className="text-sm text-gray-500 mt-1">Configure order routing logic for fulfillment allocation</p>
        </div>
        <button onClick={openCreate} className="btn-primary text-sm">
          <Plus className="w-4 h-4" /> New Rule
        </button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
        </div>
      ) : rules.length === 0 ? (
        <div className="text-center py-16 card">
          <p className="text-gray-500 text-sm">No routing rules configured. Create your first rule to control order allocation.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {rules.map((rule, index) => (
            <div key={rule.id} className={`card flex items-center gap-4 p-4 ${!rule.isActive ? 'opacity-60' : ''}`}>
              <div className="flex flex-col gap-0.5 text-gray-400">
                <button onClick={() => handleMoveUp(index)} className="hover:text-gray-600 disabled:opacity-30" disabled={index === 0}>
                  <ArrowUp className="w-3.5 h-3.5" />
                </button>
                <button onClick={() => handleMoveDown(index)} className="hover:text-gray-600 disabled:opacity-30" disabled={index === rules.length - 1}>
                  <ArrowDown className="w-3.5 h-3.5" />
                </button>
              </div>
              <GripVertical className="w-4 h-4 text-gray-300 flex-shrink-0" />
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-gray-900">{rule.name}</span>
                  <StatusBadge status={rule.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                </div>
                {rule.description && <p className="text-xs text-gray-500 mt-0.5">{rule.description}</p>}
                <div className="flex items-center gap-3 mt-1">
                  <span className="text-xs bg-gray-100 px-2 py-0.5 rounded text-gray-600">{rule.ruleType.replace(/_/g, ' ')}</span>
                  <span className="text-xs text-gray-400">Priority {rule.priority}</span>
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button onClick={() => handleToggleActive(rule)} className="p-1.5 hover:bg-gray-100 rounded text-gray-500 hover:text-gray-700">
                  {rule.isActive ? <ToggleRight className="w-4 h-4 text-green-500" /> : <ToggleLeft className="w-4 h-4" />}
                </button>
                <button onClick={() => openEdit(rule)} className="p-1.5 hover:bg-gray-100 rounded text-gray-500 hover:text-gray-700">
                  <Pencil className="w-4 h-4" />
                </button>
                <button onClick={() => handleDelete(rule.id)} className="p-1.5 hover:bg-red-50 rounded text-gray-500 hover:text-red-600">
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">{editingRule ? 'Edit Rule' : 'New Routing Rule'}</h2>
              <button onClick={() => setShowModal(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Rule Name</label>
                <input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} className="input w-full" placeholder="e.g. Nearest Warehouse First" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} className="input w-full" rows={2} placeholder="Describe when this rule applies" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Rule Type</label>
                <select value={form.ruleType} onChange={e => setForm({ ...form, ruleType: e.target.value })} className="input w-full">
                  <option value="NEAREST_AVAILABLE">Nearest Available</option>
                  <option value="LOWEST_COST">Lowest Cost</option>
                  <option value="BEST_CARRIER">Best Carrier</option>
                  <option value="CAPACITY_BASED">Capacity Based</option>
                  <option value="CUSTOMER_ZONE">Customer Zone</option>
                  <option value="INVENTORY_THRESHOLD">Inventory Threshold</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Conditions (JSON)</label>
                <textarea value={form.conditions} onChange={e => setForm({ ...form, conditions: e.target.value })} className="input w-full font-mono text-xs" rows={3} placeholder='{"maxDistance": 100, "carrier": "FEDEX"}' />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Actions (JSON)</label>
                <textarea value={form.actions} onChange={e => setForm({ ...form, actions: e.target.value })} className="input w-full font-mono text-xs" rows={3} placeholder='{"allocateTo": "NEAREST_NODE", "priority": "HIGH"}' />
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleSave} disabled={saving} className="btn-primary text-sm">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                {editingRule ? 'Update' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
