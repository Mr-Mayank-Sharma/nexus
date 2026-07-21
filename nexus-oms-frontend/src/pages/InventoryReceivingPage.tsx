import { useState, useEffect } from 'react'
import { Plus, PackageCheck, Search, X, Loader2, Filter, Download } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import { InventoryReceipt, Node } from '../types'
import * as receiptsApi from '../api/inventoryReceipts'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import StatusBadge from '../components/common/StatusBadge'

export default function InventoryReceivingPage() {
  const [receipts, setReceipts] = useState<InventoryReceipt[]>([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [saving, setSaving] = useState(false)
  const [statusFilter, setStatusFilter] = useState('')
  const [search, setSearch] = useState('')
  const [form, setForm] = useState({ nodeId: '', receiptType: 'PO', referenceNumber: '', sku: '', productName: '', quantity: 1, unitCost: 0, lotNumber: '', expiryDate: '' })
  const { addToast } = useToast()

  useEffect(() => { fetchReceipts() }, [statusFilter])

  async function fetchReceipts() {
    try {
      setLoading(true)
      const res = await receiptsApi.getReceipts({ status: statusFilter || undefined })
      setReceipts(Array.isArray(res.data) ? res.data : res.data?.content ?? [])
    } catch { addToast({ type: 'error', title: 'Failed to load receipts' })
    } finally { setLoading(false) }
  }

  function openCreate() {
    setForm({ nodeId: '', receiptType: 'PO', referenceNumber: '', sku: '', productName: '', quantity: 1, unitCost: 0, lotNumber: '', expiryDate: '' })
    setShowModal(true)
  }

  async function handleCreate() {
    if (!form.sku.trim() || !form.nodeId) { addToast({ type: 'warning', title: 'SKU and Node are required' }); return }
    setSaving(true)
    try {
      await receiptsApi.createReceipt(form)
      addToast({ type: 'success', title: 'Receipt created' })
      setShowModal(false)
      await fetchReceipts()
    } catch { addToast({ type: 'error', title: 'Failed to create receipt' })
    } finally { setSaving(false) }
  }

  async function handleReceive(id: string) {
    try {
      await receiptsApi.receiveInventory(id)
      addToast({ type: 'success', title: 'Inventory received' })
      await fetchReceipts()
    } catch { addToast({ type: 'error', title: 'Failed to receive inventory' }) }
  }

  const filtered = receipts.filter(r => !search || r.sku.toLowerCase().includes(search.toLowerCase()) || (r.productName || '').toLowerCase().includes(search.toLowerCase()))

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><PackageCheck className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Inventory Receiving</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Receive purchase orders, transfer orders, and returns into inventory</p>
        </div>
        <PermissionGate resource="inventory" action="create">
          <button onClick={openCreate} className="btn-primary text-sm">
            <Plus className="w-4 h-4" /> New Receipt
          </button>
        </PermissionGate>
      </div>

      <div className="flex items-center gap-3 flex-wrap">
        <Autocomplete value={search} onChange={setSearch} placeholder="Search by SKU or product..." minChars={0} className="flex-1 max-w-xs" />
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} className="input w-auto">
          <option value="">All Statuses</option>
          <option value="PENDING">Pending</option>
          <option value="RECEIVED">Received</option>
        </select>
        <button className="btn-secondary text-sm"><Download className="w-4 h-4" /> Export</button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 card">
          <PackageCheck className="w-12 h-12 text-[var(--text-tertiary)] mx-auto mb-3" />
          <p className="text-[var(--text-secondary)] text-sm">No receipts found. Create a new receipt to receive inventory.</p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">SKU</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Product</th>
                  <th className="px-6 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Qty</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Type</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Reference</th>
                  <th className="px-6 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filtered.map(receipt => (
                  <tr key={receipt.id} className="hover:bg-[var(--surface-sunken)]">
                    <td className="px-6 py-3 text-sm font-medium text-[var(--text-primary)]">{receipt.sku}</td>
                    <td className="px-6 py-3 text-sm text-[var(--text-secondary)]">{receipt.productName || '-'}</td>
                    <td className="px-6 py-3 text-sm text-[var(--text-secondary)] text-center">{receipt.quantity}</td>
                    <td className="px-6 py-3 text-sm text-[var(--text-secondary)]">
                      <span className="bg-[var(--surface-muted)] px-2 py-0.5 rounded text-xs font-medium">{receipt.receiptType}</span>
                    </td>
                    <td className="px-6 py-3 text-sm text-[var(--text-secondary)]">{receipt.referenceNumber || '-'}</td>
                    <td className="px-6 py-3 text-center"><StatusBadge status={receipt.status} size="sm" /></td>
                    <td className="px-6 py-3 text-right">
                      {receipt.status === 'PENDING' ? (
                        <PermissionGate resource="inventory" action="edit">
                          <button onClick={() => handleReceive(receipt.id)} className="btn-primary text-xs py-1.5">
                            <PackageCheck className="w-3.5 h-3.5" /> Receive
                          </button>
                        </PermissionGate>
                      ) : (
                        <span className="text-xs text-[var(--text-tertiary)]">Received by {receipt.receivedBy || 'system'}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-lg">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">New Inventory Receipt</h2>
              <button onClick={() => setShowModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Receipt Type</label>
                  <select value={form.receiptType} onChange={e => setForm({ ...form, receiptType: e.target.value })} className="input w-full">
                    <option value="PO">Purchase Order</option>
                    <option value="TRANSFER">Transfer Order</option>
                    <option value="RETURN">Return</option>
                    <option value="ADJUSTMENT">Adjustment</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Node ID</label>
                  <input value={form.nodeId} onChange={e => setForm({ ...form, nodeId: e.target.value })} className="input w-full" placeholder="Warehouse node UUID" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">SKU</label>
                  <input value={form.sku} onChange={e => setForm({ ...form, sku: e.target.value })} className="input w-full" placeholder="e.g. PROD-001" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Product Name</label>
                  <input value={form.productName} onChange={e => setForm({ ...form, productName: e.target.value })} className="input w-full" placeholder="Product name" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Quantity</label>
                  <input type="number" value={form.quantity} onChange={e => setForm({ ...form, quantity: parseInt(e.target.value) || 1 })} className="input w-full" min={1} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Unit Cost</label>
                  <input type="number" value={form.unitCost} onChange={e => setForm({ ...form, unitCost: parseFloat(e.target.value) || 0 })} className="input w-full" min={0} step={0.01} />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Reference Number</label>
                <input value={form.referenceNumber} onChange={e => setForm({ ...form, referenceNumber: e.target.value })} className="input w-full" placeholder="PO-12345" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Lot Number</label>
                  <input value={form.lotNumber} onChange={e => setForm({ ...form, lotNumber: e.target.value })} className="input w-full" placeholder="LOT-001" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Expiry Date</label>
                  <input type="date" value={form.expiryDate} onChange={e => setForm({ ...form, expiryDate: e.target.value })} className="input w-full" />
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="inventory" action="create">
                <button onClick={handleCreate} disabled={saving} className="btn-primary text-sm">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <PackageCheck className="w-4 h-4" />}
                  Create Receipt
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
