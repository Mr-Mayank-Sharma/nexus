import { useState, useEffect } from 'react'
import { Plus, Search, X, Loader2, ClipboardCheck, CheckCircle, AlertTriangle } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import { CycleCount } from '../types'
import * as cycleCountsApi from '../api/cycleCounts'
import StatusBadge from '../components/common/StatusBadge'

export default function CycleCountPage() {
  const [counts, setCounts] = useState<CycleCount[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showCountModal, setShowCountModal] = useState<CycleCount | null>(null)
  const [saving, setSaving] = useState(false)
  const [countValue, setCountValue] = useState(0)
  const [statusFilter, setStatusFilter] = useState('')
  const [search, setSearch] = useState('')
  const [form, setForm] = useState({ nodeId: '', sku: '', productName: '', expectedQty: 1, notes: '' })
  const { addToast } = useToast()

  useEffect(() => { fetchCounts() }, [statusFilter])

  async function fetchCounts() {
    try {
      setLoading(true)
      const res = await cycleCountsApi.getCycleCounts({ status: statusFilter || undefined })
      setCounts(res.data)
    } catch { addToast({ type: 'error', title: 'Failed to load cycle counts' })
    } finally { setLoading(false) }
  }

  function openCreate() {
    setForm({ nodeId: '', sku: '', productName: '', expectedQty: 1, notes: '' })
    setShowCreateModal(true)
  }

  async function handleCreate() {
    if (!form.sku.trim() || !form.nodeId) { addToast({ type: 'warning', title: 'SKU and Node are required' }); return }
    setSaving(true)
    try {
      await cycleCountsApi.createCycleCount(form)
      addToast({ type: 'success', title: 'Cycle count created' })
      setShowCreateModal(false)
      await fetchCounts()
    } catch { addToast({ type: 'error', title: 'Failed to create cycle count' })
    } finally { setSaving(false) }
  }

  async function handlePerformCount() {
    if (!showCountModal) return
    setSaving(true)
    try {
      await cycleCountsApi.performCount(showCountModal.id, countValue)
      addToast({ type: 'success', title: 'Count recorded' })
      setShowCountModal(null)
      await fetchCounts()
    } catch { addToast({ type: 'error', title: 'Failed to record count' })
    } finally { setSaving(false) }
  }

  const filtered = counts.filter(c => !search || c.sku.toLowerCase().includes(search.toLowerCase()) || (c.productName || '').toLowerCase().includes(search.toLowerCase()))

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Cycle Counting</h1>
          <p className="text-sm text-gray-500 mt-1">Regular inventory reconciliation to ensure stock accuracy</p>
        </div>
        <button onClick={openCreate} className="btn-primary text-sm">
          <Plus className="w-4 h-4" /> New Count
        </button>
      </div>

      <div className="flex items-center gap-3 flex-wrap">
        <div className="relative flex-1 max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input value={search} onChange={e => setSearch(e.target.value)} className="input pl-9 w-full" placeholder="Search by SKU or product..." />
        </div>
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} className="input w-auto">
          <option value="">All Statuses</option>
          <option value="PENDING">Pending</option>
          <option value="MATCH">Match</option>
          <option value="MISMATCH">Mismatch</option>
        </select>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 card">
          <ClipboardCheck className="w-12 h-12 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 text-sm">No cycle counts found. Create a new count to reconcile inventory.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map(count => {
            const isPending = count.status === 'PENDING'
            const isMatch = count.status === 'MATCH'
            return (
              <div key={count.id} className={`card p-5 ${!isPending ? (isMatch ? 'ring-1 ring-green-200' : 'ring-1 ring-red-200') : ''}`}>
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <h3 className="text-sm font-semibold text-gray-900">{count.sku}</h3>
                    {count.productName && <p className="text-xs text-gray-500">{count.productName}</p>}
                  </div>
                  <StatusBadge status={count.status} size="sm" />
                </div>
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between"><span className="text-gray-500">Expected</span><span className="font-medium">{count.expectedQty}</span></div>
                  {count.countedQty !== undefined && count.countedQty !== null && (
                    <div className="flex justify-between">
                      <span className="text-gray-500">Counted</span>
                      <span className={`font-medium ${count.countedQty !== count.expectedQty ? 'text-red-600' : 'text-green-600'}`}>{count.countedQty}</span>
                    </div>
                  )}
                  {count.notes && <p className="text-xs text-gray-400 mt-2">{count.notes}</p>}
                </div>
                {count.countedBy && (
                  <p className="text-xs text-gray-400 mt-3">Counted by {count.countedBy} {count.countedAt ? `on ${new Date(count.countedAt).toLocaleDateString()}` : ''}</p>
                )}
                {isPending && (
                  <button onClick={() => { setShowCountModal(count); setCountValue(count.expectedQty) }} className="btn-primary text-xs w-full mt-4">
                    <ClipboardCheck className="w-3.5 h-3.5" /> Record Count
                  </button>
                )}
                {!isPending && (
                  <div className="flex items-center gap-1.5 mt-3 text-xs">
                    {isMatch ? <CheckCircle className="w-3.5 h-3.5 text-green-500" /> : <AlertTriangle className="w-3.5 h-3.5 text-red-500" />}
                    <span className={isMatch ? 'text-green-600' : 'text-red-600'}>
                      {isMatch ? 'Count matches system' : `Variance: ${count.countedQty! - count.expectedQty > 0 ? '+' : ''}${count.countedQty! - count.expectedQty}`}
                    </span>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">New Cycle Count</h2>
              <button onClick={() => setShowCreateModal(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Node ID</label>
                  <input value={form.nodeId} onChange={e => setForm({ ...form, nodeId: e.target.value })} className="input w-full" placeholder="Warehouse node UUID" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">SKU</label>
                  <input value={form.sku} onChange={e => setForm({ ...form, sku: e.target.value })} className="input w-full" placeholder="e.g. PROD-001" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Product Name</label>
                <input value={form.productName} onChange={e => setForm({ ...form, productName: e.target.value })} className="input w-full" placeholder="Product name" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Expected Quantity</label>
                <input type="number" value={form.expectedQty} onChange={e => setForm({ ...form, expectedQty: parseInt(e.target.value) || 1 })} className="input w-full" min={1} />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                <textarea value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} className="input w-full" rows={2} placeholder="Optional notes" />
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowCreateModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleCreate} disabled={saving} className="btn-primary text-sm">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      {showCountModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Record Count: {showCountModal.sku}</h2>
              <button onClick={() => setShowCountModal(null)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <p className="text-sm text-gray-500">Expected quantity: <strong>{showCountModal.expectedQty}</strong></p>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Actual Count</label>
                <input type="number" value={countValue} onChange={e => setCountValue(parseInt(e.target.value) || 0)} className="input w-full text-lg" min={0} autoFocus />
              </div>
              {countValue !== showCountModal.expectedQty && (
                <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 text-sm text-yellow-700 flex items-start gap-2">
                  <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                  <span>Count differs from expected by {Math.abs(countValue - showCountModal.expectedQty)} units</span>
                </div>
              )}
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowCountModal(null)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handlePerformCount} disabled={saving} className="btn-primary text-sm">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                Submit Count
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
