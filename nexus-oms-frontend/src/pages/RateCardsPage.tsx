import { useState, useEffect, useCallback } from 'react'
import {
  Tag, Plus, Pencil, Trash2, Search, RefreshCw, DollarSign, CreditCard,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import {
  getRateCards, createRateCard, updateRateCard, deleteRateCard,
  type RateCard,
} from '../api/billing'
import { getCustomers } from '../api/customers'
import Autocomplete from '../components/common/Autocomplete'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'

interface RateCardForm {
  clientId: string
  currency: string
  perOrderFee: string
  perLineFee: string
  pickingFeePerLine: string
  storageFeePerUnit: string
  description: string
  isActive: boolean
}

const emptyForm: RateCardForm = {
  clientId: '',
  currency: 'USD',
  perOrderFee: '',
  perLineFee: '',
  pickingFeePerLine: '',
  storageFeePerUnit: '',
  description: '',
  isActive: true,
}

const asArray = (d: unknown): any[] => (Array.isArray(d) ? d : Array.isArray((d as any)?.content) ? (d as any).content : [])

export default function RateCardsPage() {
  const { addToast } = useToast()
  const [cards, setCards] = useState<RateCard[]>([])
  const [customers, setCustomers] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<RateCard | null>(null)
  const [form, setForm] = useState<RateCardForm>(emptyForm)
  const [saving, setSaving] = useState(false)

  const loadCustomers = useCallback(async () => {
    try {
      const res = await getCustomers()
      setCustomers(asArray(res.data))
    } catch {
      setCustomers([])
    }
  }, [])

  const fetchCards = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getRateCards()
      setCards(asArray(res.data))
    } catch {
      setCards([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchCards()
    loadCustomers()
  }, [fetchCards, loadCustomers])

  const filtered = cards.filter(c => {
    if (!search) return true
    const q = search.toLowerCase()
    return (c.clientName || '').toLowerCase().includes(q)
      || (c.currency || '').toLowerCase().includes(q)
      || (c.description || '').toLowerCase().includes(q)
  })

  function openCreate() {
    setEditing(null)
    setForm(emptyForm)
    setShowForm(true)
  }

  function openEdit(card: RateCard) {
    setEditing(card)
    setForm({
      clientId: card.clientId || '',
      currency: card.currency,
      perOrderFee: String(card.perOrderFee),
      perLineFee: String(card.perLineFee),
      pickingFeePerLine: String(card.pickingFeePerLine),
      storageFeePerUnit: String(card.storageFeePerUnit),
      description: card.description || '',
      isActive: card.isActive,
    })
    setShowForm(true)
  }

  const clientName = (id?: string) => customers.find(c => c.id === id)?.name || 'Default'

  async function handleSave() {
    if (form.perOrderFee === '' || form.perLineFee === '' || form.pickingFeePerLine === '' || form.storageFeePerUnit === '' || !form.currency.trim()) {
      addToast({ type: 'warning', title: 'Fill all fee fields and currency' })
      return
    }
    setSaving(true)
    const payload = {
      clientId: form.clientId || undefined,
      clientName: form.clientId ? clientName(form.clientId) : undefined,
      currency: form.currency.trim(),
      perOrderFee: parseFloat(form.perOrderFee),
      perLineFee: parseFloat(form.perLineFee),
      pickingFeePerLine: parseFloat(form.pickingFeePerLine),
      storageFeePerUnit: parseFloat(form.storageFeePerUnit),
      description: form.description.trim() || undefined,
      isActive: form.isActive,
    }
    try {
      if (editing) {
        await updateRateCard(editing.id, payload)
        addToast({ type: 'success', title: 'Rate card updated' })
      } else {
        await createRateCard(payload)
        addToast({ type: 'success', title: 'Rate card created' })
      }
      setShowForm(false)
      fetchCards()
    } catch (e: any) {
      addToast({ type: 'error', title: e?.response?.data?.message || 'Failed to save rate card' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteRateCard(id)
      setCards(prev => prev.filter(c => c.id !== id))
      addToast({ type: 'success', title: 'Rate card deleted' })
    } catch (e: any) {
      addToast({ type: 'error', title: e?.response?.data?.message || 'Failed to delete rate card' })
    }
  }

  const defaultCards = cards.filter(c => !c.clientId)
  const clientCards = cards.filter(c => c.clientId)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Tag className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Rate Cards
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">3PL pricing per client — drives billing statement generation</p>
        </div>
        <div className="flex items-center gap-2">
          <EnterpriseKPICard title="Default" value={defaultCards.length} icon={<Tag className="w-4 h-4" />} color="info" />
          <EnterpriseKPICard title="Client Cards" value={clientCards.length} icon={<CreditCard className="w-4 h-4" />} color="success" />
          <PermissionGate resource="finance" action="edit">
            <button type="button" onClick={openCreate} className="enterprise-btn-primary text-sm px-4 py-2 flex items-center gap-1.5 bg-[var(--nexus-primary-600)] hover:bg-[var(--nexus-primary-700)]">
              <Plus className="w-4 h-4" /> New Rate Card
            </button>
          </PermissionGate>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <Autocomplete value={search} onChange={setSearch} placeholder="Search by client, currency or description..." minChars={0} className="flex-1 max-w-md" />
        <button type="button" onClick={fetchCards} className="enterprise-btn-secondary text-sm px-3 py-2 flex items-center gap-1.5">
          <RefreshCw className={clsx('w-4 h-4', loading && 'animate-spin')} /> Refresh
        </button>
      </div>

      {loading ? (
        <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" /></div>
      ) : filtered.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <Search className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No rate cards found</p>
          <p className="text-xs text-[var(--text-tertiary)] mt-1">Create a rate card to start billing clients</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filtered.map(card => (
            <div key={card.id} className="enterprise-card p-5 hover:border-[var(--nexus-primary-300)] dark:hover:border-[var(--nexus-primary-600)] transition-all">
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-2.5">
                  <div className="w-9 h-9 rounded-xl bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/20 text-[var(--nexus-primary-600)] flex items-center justify-center">
                    <DollarSign className="w-5 h-5" />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-[var(--text-primary)]">{card.clientName || 'Default Rate Card'}</p>
                    <p className="text-[10px] text-[var(--text-tertiary)]">{card.currency} · {card.id.slice(0, 8)}</p>
                  </div>
                </div>
                <EnterpriseStatusBadge status={card.isActive ? 'success' : 'neutral'} label={card.isActive ? 'Active' : 'Inactive'} />
              </div>

              <div className="grid grid-cols-2 gap-2 mb-3">
                {[
                  { label: 'Per Order', value: card.perOrderFee },
                  { label: 'Per Line', value: card.perLineFee },
                  { label: 'Picking / Line', value: card.pickingFeePerLine },
                  { label: 'Storage / Unit', value: card.storageFeePerUnit },
                ].map(f => (
                  <div key={f.label} className="bg-[var(--surface-sunken)]/60 rounded-lg px-3 py-2">
                    <p className="text-[10px] text-[var(--text-tertiary)] uppercase">{f.label}</p>
                    <p className="text-sm font-bold text-[var(--text-primary)]">{card.currency} {f.value.toFixed(2)}</p>
                  </div>
                ))}
              </div>

              {card.description && (
                <p className="text-xs text-[var(--text-secondary)] mb-3">{card.description}</p>
              )}
              <p className="text-[10px] text-[var(--text-tertiary)] mb-3">Updated {new Date(card.updatedAt).toLocaleDateString()}</p>

              <PermissionGate resource="finance" action="edit">
                <div className="flex items-center gap-2">
                  <button type="button" onClick={() => openEdit(card)} className="enterprise-btn-secondary text-xs px-3 py-1.5 flex items-center gap-1">
                    <Pencil className="w-3.5 h-3.5" /> Edit
                  </button>
                  <button type="button" onClick={() => handleDelete(card.id)} className="enterprise-btn-secondary text-xs px-3 py-1.5 flex items-center gap-1 text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]">
                    <Trash2 className="w-3.5 h-3.5" /> Delete
                  </button>
                </div>
              </PermissionGate>
            </div>
          ))}
        </div>
      )}

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={() => setShowForm(false)}>
          <div className="bg-[var(--surface-base)] rounded-2xl border border-[var(--border-default)] shadow-2xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-bold text-[var(--text-primary)] mb-4">{editing ? 'Edit Rate Card' : 'New Rate Card'}</h2>

            <div className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Client (blank = default)</label>
                <select
                  value={form.clientId}
                  onChange={e => setForm(prev => ({ ...prev, clientId: e.target.value }))}
                  className="enterprise-input w-full"
                >
                  <option value="">Default rate card (all clients)</option>
                  {customers.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Currency</label>
                  <input
                    value={form.currency}
                    onChange={e => setForm(prev => ({ ...prev, currency: e.target.value }))}
                    placeholder="USD"
                    className="enterprise-input w-full"
                  />
                </div>
                <div className="flex items-end pb-1">
                  <label className="flex items-center gap-2 text-sm text-[var(--text-secondary)]">
                    <input type="checkbox" checked={form.isActive} onChange={e => setForm(prev => ({ ...prev, isActive: e.target.checked }))} className="accent-[var(--nexus-primary-600)]" />
                    Active
                  </label>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Per Order Fee</label>
                  <input type="number" step="0.01" min="0" value={form.perOrderFee} onChange={e => setForm(prev => ({ ...prev, perOrderFee: e.target.value }))} className="enterprise-input w-full" placeholder="0.00" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Per Line Fee</label>
                  <input type="number" step="0.01" min="0" value={form.perLineFee} onChange={e => setForm(prev => ({ ...prev, perLineFee: e.target.value }))} className="enterprise-input w-full" placeholder="0.00" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Picking Fee / Line</label>
                  <input type="number" step="0.01" min="0" value={form.pickingFeePerLine} onChange={e => setForm(prev => ({ ...prev, pickingFeePerLine: e.target.value }))} className="enterprise-input w-full" placeholder="0.00" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Storage Fee / Unit</label>
                  <input type="number" step="0.01" min="0" value={form.storageFeePerUnit} onChange={e => setForm(prev => ({ ...prev, storageFeePerUnit: e.target.value }))} className="enterprise-input w-full" placeholder="0.00" />
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Description</label>
                <input
                  value={form.description}
                  onChange={e => setForm(prev => ({ ...prev, description: e.target.value }))}
                  placeholder="Optional notes"
                  className="enterprise-input w-full"
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 mt-6">
              <button type="button" onClick={() => setShowForm(false)} className="enterprise-btn-secondary text-sm px-4 py-2">Cancel</button>
              <button type="button" onClick={handleSave} disabled={saving} className="enterprise-btn-primary text-sm px-4 py-2 bg-[var(--nexus-primary-600)] hover:bg-[var(--nexus-primary-700)]">
                {saving ? 'Saving...' : editing ? 'Save Changes' : 'Create Rate Card'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
