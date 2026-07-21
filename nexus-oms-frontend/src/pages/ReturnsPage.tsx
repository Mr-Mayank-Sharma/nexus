import { useState, useEffect, useMemo } from 'react'
import { clsx } from 'clsx'
import {
  RotateCcw, Plus, Search, X, Check, ChevronDown, ChevronRight,
  Eye, PackageCheck, DollarSign, ThumbsUp, RefreshCw, AlertTriangle, Loader2,
} from 'lucide-react'
import PermissionGate from '../components/rbac/PermissionGate'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseToolbar from '../components/enterprise/EnterpriseToolbar'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import * as returnsApi from '../api/returns'
import type { Return, ReturnItem } from '../types'
import { useToast } from '../hooks/useToast'

const STATUS_COLORS: Record<string, string> = {
  REQUESTED: 'text-[var(--nexus-warning-600)] bg-[var(--nexus-warning-50)] ring-amber-500/20',
  APPROVED: 'text-[var(--nexus-primary-600)] bg-[var(--nexus-primary-50)] ring-blue-500/20',
  RECEIVED: 'text-[var(--text-secondary)] bg-[var(--surface-sunken)] ring-gray-500/20',
  INSPECTED: 'text-violet-600 bg-violet-50 ring-violet-500/20',
  REFUNDED: 'text-emerald-600 bg-emerald-50 ring-emerald-500/20',
  REJECTED: 'text-[var(--nexus-error-600)] bg-[var(--nexus-error-50)] ring-red-500/20',
  CANCELLED: 'text-[var(--text-tertiary)] bg-[var(--surface-sunken)] ring-gray-500/20',
}

const STATUS_KPI_COLORS: Record<string, 'warning' | 'info' | 'neutral' | 'ai' | 'success' | 'error'> = {
  PENDING: 'warning',
  APPROVED: 'info',
  RECEIVED: 'info',
  INSPECTED: 'ai',
  REFUNDED: 'success',
  REJECTED: 'error',
}

const DISPOSITION_LABELS: Record<string, string> = {
  PENDING: 'Awaiting Decision',
  RESTOCK: 'Restock Inventory',
  REFURBISH: 'Refurbish',
  RETURN_TO_VENDOR: 'Return to Vendor',
  DISPOSE: 'Dispose',
  DONATE: 'Donate',
}

const CONDITION_OPTIONS = ['LIKE_NEW', 'GOOD', 'FAIR', 'DAMAGED', 'DEFECTIVE']
const DISPOSITION_OPTIONS = ['RESTOCK', 'REFURBISH', 'RETURN_TO_VENDOR', 'DISPOSE', 'DONATE']

const TABS = [
  { id: 'ALL', label: 'All Returns' },
  { id: 'REQUESTED', label: 'Requested' },
  { id: 'APPROVED', label: 'Approved' },
  { id: 'RECEIVED', label: 'Received' },
  { id: 'INSPECTED', label: 'Inspected' },
  { id: 'REFUNDED', label: 'Refunded' },
  { id: 'REJECTED', label: 'Rejected' },
]

export default function ReturnsPage() {
  const [returns, setReturns] = useState<Return[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState('ALL')
  const [search, setSearch] = useState('')
  const [kpis, setKpis] = useState({ total: 0, pending: 0, approved: 0, received: 0, inspected: 0, refunded: 0, rejected: 0 })
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [selectedReturn, setSelectedReturn] = useState<Return | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [inspectOpen, setInspectOpen] = useState(false)
  const [refundOpen, setRefundOpen] = useState(false)
  const [rejectOpen, setRejectOpen] = useState(false)
  const [processing, setProcessing] = useState<string | null>(null)
  const { addToast } = useToast()

  const [createForm, setCreateForm] = useState({ orderId: '', customerId: '', reason: '', returnChannel: 'MANUAL', rmaType: 'RETURN' })
  const [createItems, setCreateItems] = useState<ReturnItem[]>([{ sku: '', productName: '', quantity: 1 }])
  const [inspectItems, setInspectItems] = useState<ReturnItem[]>([])
  const [refundAmount, setRefundAmount] = useState(0)
  const [refundReference, setRefundReference] = useState('')
  const [rejectReason, setRejectReason] = useState('')

  const fetchData = async () => {
    try {
      setLoading(true)
      setError(null)
      const statusParam = activeTab === 'ALL' ? undefined : activeTab
      const [returnsRes, kpisRes] = await Promise.all([
        returnsApi.getReturns({ status: statusParam as any, search: search || undefined }),
        returnsApi.getReturnKPIs(),
      ])
      setReturns(returnsRes.data || [])
      if (kpisRes.data) setKpis(kpisRes.data as typeof kpis)
    } catch {
      addToast({ type: 'error', title: 'Failed to load returns' })
      setError('Failed to load returns')
      setReturns([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchData() }, [activeTab])
  useEffect(() => {
    const t = setTimeout(() => { if (search) fetchData() }, 400)
    return () => clearTimeout(t)
  }, [search])

  const filteredReturns = useMemo(() => {
    if (!search) return returns
    const q = search.toLowerCase()
    return returns.filter(r =>
      r.rmaNumber.toLowerCase().includes(q) ||
      r.customerName?.toLowerCase().includes(q) ||
      r.orderId?.toLowerCase().includes(q) ||
      r.reason?.toLowerCase().includes(q)
    )
  }, [returns, search])

  const handleApprove = async (id: string) => {
    setProcessing(id)
    try { await returnsApi.approveReturn(id); addToast({ type: 'success', title: 'Return approved' }); fetchData() }
    catch { addToast({ type: 'error', title: 'Failed to approve return' }) }
    finally { setProcessing(null) }
  }

  const handleReceive = async (id: string) => {
    setProcessing(id)
    try { await returnsApi.receiveReturn(id); addToast({ type: 'success', title: 'Return received' }); fetchData() }
    catch { addToast({ type: 'error', title: 'Failed to receive return' }) }
    finally { setProcessing(null) }
  }

  const openInspect = (ret: Return) => {
    setSelectedReturn(ret)
    setInspectItems(ret.items?.map(i => ({ ...i, id: i.id || '' })) || [])
    setInspectOpen(true)
  }

  const handleInspect = async () => {
    if (!selectedReturn) return
    setProcessing('inspect')
    try { await returnsApi.inspectReturn(selectedReturn.id, inspectItems); addToast({ type: 'success', title: 'Return inspected' }); setInspectOpen(false); fetchData() }
    catch { addToast({ type: 'error', title: 'Failed to inspect return' }) }
    finally { setProcessing(null) }
  }

  const openRefund = (ret: Return) => {
    setSelectedReturn(ret)
    setRefundAmount(ret.refundAmount || 0)
    setRefundReference('')
    setRefundOpen(true)
  }

  const handleRefund = async () => {
    if (!selectedReturn) return
    setProcessing('refund')
    try { await returnsApi.processRefund(selectedReturn.id, refundAmount, refundReference || `REF-${Date.now()}`); addToast({ type: 'success', title: 'Refund processed' }); setRefundOpen(false); fetchData() }
    catch { addToast({ type: 'error', title: 'Failed to process refund' }) }
    finally { setProcessing(null) }
  }

  const handleReject = async () => {
    if (!selectedReturn) return
    setProcessing('reject')
    try { await returnsApi.rejectReturn(selectedReturn.id, rejectReason || 'No reason provided'); addToast({ type: 'success', title: 'Return rejected' }); setRejectOpen(false); fetchData() }
    catch { addToast({ type: 'error', title: 'Failed to reject return' }) }
    finally { setProcessing(null) }
  }

  const handleCreate = async () => {
    setProcessing('create')
    try { await returnsApi.createReturn({ ...createForm, items: createItems.filter(i => i.sku) }); addToast({ type: 'success', title: 'Return created' }); setCreateOpen(false); setCreateForm({ orderId: '', customerId: '', reason: '', returnChannel: 'MANUAL', rmaType: 'RETURN' }); setCreateItems([{ sku: '', productName: '', quantity: 1 }]); fetchData() }
    catch { addToast({ type: 'error', title: 'Failed to create return' }) }
    finally { setProcessing(null) }
  }

  const handleDelete = async (id: string) => {
    if (!window.confirm('Cancel this return?')) return
    setProcessing(id)
    try { await returnsApi.updateReturnStatus(id, 'CANCELLED'); addToast({ type: 'success', title: 'Return cancelled' }); fetchData() }
    catch { addToast({ type: 'error', title: 'Failed to cancel return' }) }
    finally { setProcessing(null) }
  }

  const KPI_ITEMS = [
    { key: 'PENDING', label: 'Pending', value: kpis.pending, icon: RotateCcw },
    { key: 'APPROVED', label: 'Approved', value: kpis.approved, icon: ThumbsUp },
    { key: 'RECEIVED', label: 'Received', value: kpis.received, icon: PackageCheck },
    { key: 'INSPECTED', label: 'Inspected', value: kpis.inspected, icon: Eye },
    { key: 'REFUNDED', label: 'Refunded', value: kpis.refunded, icon: DollarSign },
    { key: 'REJECTED', label: 'Rejected', value: kpis.rejected, icon: X },
  ]

  return (
    <div className="space-y-4">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Returns' }]} />

      <EnterpriseToolbar
        title="Returns Management"
        subtitle={`${kpis.total} total returns`}
        searchValue={search}
        onSearch={(v) => setSearch(v)}
        searchPlaceholder="Search RMA, customer, order..."
        autocomplete={{
          fetchSuggestions: async (q) => {
            if (!q) return returns.slice(0, 10)
            const term = q.toLowerCase()
            return returns.filter(r => r.rmaNumber?.toLowerCase().includes(term) || r.customerId?.toLowerCase().includes(term) || r.orderId?.toLowerCase().includes(term)).slice(0, 10)
          },
          onSelect: (item: any) => setSearch(item.rmaNumber || item.id),
          getOptionLabel: (item: any) => `${item.rmaNumber || item.id} — ${item.customerId || ''}`,
          getOptionValue: (item: any) => item.id,
          minChars: 1,
        }}
        actions={[
          {
            label: 'Create Return', icon: <Plus className="w-4 h-4" />, onClick: () => {
              setCreateForm({ orderId: '', customerId: '', reason: '', returnChannel: 'MANUAL', rmaType: 'RETURN' })
              setCreateItems([{ sku: '', productName: '', quantity: 1 }])
              setCreateOpen(true)
            }, variant: 'primary', permission: { resource: 'returns', action: 'create' },
          },
          { label: '', icon: <RefreshCw className={clsx('w-4 h-4', loading && 'animate-spin')} />, onClick: fetchData, variant: 'secondary' },
        ]}
      />

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        {KPI_ITEMS.map(kpi => (
          <EnterpriseKPICard key={kpi.key} title={kpi.label} value={kpi.value} icon={<kpi.icon className="w-5 h-5" />} color={STATUS_KPI_COLORS[kpi.key]} />
        ))}
      </div>

      <EnterpriseTabs tabs={TABS} activeTab={activeTab} onChange={setActiveTab} variant="underline" />

      {/* Returns List */}
      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="enterprise-spinner enterprise-spinner-lg" />
        </div>
      ) : error ? (
        <div className="enterprise-empty-state">
          <AlertTriangle />
          <h3>Failed to load returns</h3>
          <p>{error}</p>
          <button onClick={fetchData} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm mt-4">Try again</button>
        </div>
      ) : filteredReturns.length === 0 ? (
        <div className="enterprise-empty-state">
          <RotateCcw />
          <h3>No returns found</h3>
          <p>Create a new return to get started</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filteredReturns.map(ret => (
            <div key={ret.id} className="enterprise-card overflow-hidden transition-shadow hover:shadow-sm p-5">
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-4 flex-1 min-w-0">
                  <div className={clsx(
                    'w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ring-1',
                    STATUS_COLORS[ret.status],
                  )}>
                    <RotateCcw className="w-5 h-5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-3 flex-wrap">
                      <span className="font-semibold text-[var(--text-primary)]">{ret.rmaNumber}</span>
                      <EnterpriseStatusBadge status={ret.status} />
                      {ret.refundStatus === 'COMPLETED' && <EnterpriseStatusBadge status="success" label="Refunded" />}
                    </div>
                    <div className="flex items-center gap-4 mt-1.5 text-sm text-[var(--text-tertiary)] flex-wrap">
                      <span>Customer: <span className="font-medium text-[var(--text-secondary)]">{ret.customerName}</span></span>
                      <span>Order: <span className="font-mono text-[var(--text-secondary)]">{ret.orderId?.slice(0, 8)}</span></span>
                      {ret.reason && <span>Reason: <span className="font-medium text-[var(--text-secondary)]">{ret.reason}</span></span>}
                      <span>{new Date(ret.createdAt).toLocaleDateString()}</span>
                    </div>
                    {ret.items && ret.items.length > 0 && (
                      <div className="flex items-center gap-2 mt-2">
                        {ret.items.map((item, i) => (
                          <span key={i} className="enterprise-tag">
                            {item.sku}{item.quantity > 1 ? ` x${item.quantity}` : ''}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-1.5 ml-4">
                  {ret.status === 'REQUESTED' && (
                    <PermissionGate resource="returns" action="edit">
                      <button onClick={() => handleApprove(ret.id)} disabled={processing === ret.id}
                        className="enterprise-btn enterprise-btn-sm bg-emerald-600 text-white hover:bg-emerald-700 border-none disabled:opacity-50">
                        {processing === ret.id ? <div className="enterprise-spinner" /> : <Check className="w-3 h-3" />}
                        Approve
                      </button>
                    </PermissionGate>
                  )}
                  {ret.status === 'APPROVED' && (
                    <PermissionGate resource="returns" action="edit">
                      <button onClick={() => handleReceive(ret.id)} disabled={processing === ret.id}
                        className="enterprise-btn enterprise-btn-sm bg-[var(--nexus-primary-600)] text-white hover:bg-[var(--nexus-primary-700)] border-none disabled:opacity-50">
                        {processing === ret.id ? <div className="enterprise-spinner" /> : <PackageCheck className="w-3 h-3" />}
                        Receive
                      </button>
                    </PermissionGate>
                  )}
                  {ret.status === 'RECEIVED' && (
                    <PermissionGate resource="returns" action="edit">
                      <button onClick={() => openInspect(ret)} disabled={processing === 'inspect'}
                        className="enterprise-btn enterprise-btn-sm bg-violet-600 text-white hover:bg-violet-700 border-none disabled:opacity-50">
                        <Eye className="w-3 h-3" /> Inspect
                      </button>
                    </PermissionGate>
                  )}
                  {ret.status === 'INSPECTED' && (
                    <PermissionGate resource="returns" action="edit">
                      <button onClick={() => openRefund(ret)} disabled={processing === 'refund'}
                        className="enterprise-btn enterprise-btn-sm bg-emerald-600 text-white hover:bg-emerald-700 border-none disabled:opacity-50">
                        <DollarSign className="w-3 h-3" /> Refund
                      </button>
                    </PermissionGate>
                  )}
                  {!['REFUNDED', 'REJECTED', 'CANCELLED'].includes(ret.status) && (
                    <PermissionGate resource="returns" action="delete">
                      <button onClick={() => { setSelectedReturn(ret); setRejectReason(''); setRejectOpen(true) }}
                        className="enterprise-btn enterprise-btn-sm enterprise-btn-ghost">
                        <X className="w-3 h-3" />
                      </button>
                    </PermissionGate>
                  )}
                  <button onClick={() => setExpandedId(expandedId === ret.id ? null : ret.id)}
                    className="w-7 h-7 flex items-center justify-center text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] rounded-lg hover:bg-[var(--bg-tertiary)]">
                    {expandedId === ret.id ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {expandedId === ret.id && (
                <div className="mt-4 pt-4 border-t border-[var(--border-color)]">
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-sm">
                    <div>
                      <h4 className="font-medium text-[var(--text-primary)] mb-3">Return Details</h4>
                      <dl className="space-y-2">
                        {[
                          ['RMA', ret.rmaNumber],
                          ['Type', ret.rmaType || 'RETURN'],
                          ['Channel', ret.returnChannel || 'MANUAL'],
                          ['Created', new Date(ret.createdAt).toLocaleString()],
                          ['Refund', `$${(ret.refundAmount || 0).toFixed(2)}`],
                        ].map(([k, v]) => (
                          <div key={k} className="flex justify-between py-1 px-3 bg-[var(--bg-tertiary)] rounded-lg">
                            <span className="text-[var(--text-tertiary)]">{k}</span>
                            <span className="font-medium text-[var(--text-primary)]">{v}</span>
                          </div>
                        ))}
                      </dl>
                    </div>
                    <div>
                      <h4 className="font-medium text-[var(--text-primary)] mb-3">Items ({ret.items?.length || 0})</h4>
                      <div className="space-y-2">
                        {(ret.items || []).map((item, i) => (
                          <div key={i} className="flex items-center justify-between text-xs bg-[var(--bg-tertiary)] rounded-lg px-3 py-2">
                            <div>
                              <span className="font-medium text-[var(--text-secondary)]">{item.sku}</span>
                              {item.productName && <span className="text-[var(--text-tertiary)] ml-2">{item.productName}</span>}
                            </div>
                            <div className="flex items-center gap-3">
                              <span className="text-[var(--text-tertiary)]">x{item.quantity}</span>
                              {item.condition && <span className={clsx('enterprise-tag', (item.condition === 'DAMAGED' || item.condition === 'DEFECTIVE') && '!text-[var(--nexus-error-600)] !bg-[var(--nexus-error-50)]')}>{item.condition}</span>}
                              {item.disposition && <span className="enterprise-tag">{DISPOSITION_LABELS[item.disposition] || item.disposition}</span>}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                    <div>
                      <h4 className="font-medium text-[var(--text-primary)] mb-3">Reason</h4>
                      <p className="text-sm text-[var(--text-secondary)] bg-[var(--bg-tertiary)] rounded-lg p-3">{ret.reason || 'No reason provided'}</p>
                      {ret.rejectedReason && (
                        <>
                          <h4 className="font-medium text-[var(--nexus-error-600)] mt-4 mb-2">Rejection Reason</h4>
                          <p className="text-sm text-[var(--nexus-error-600)] bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 rounded-lg p-3">{ret.rejectedReason}</p>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Create Return Modal */}
      {createOpen && (
        <div className="enterprise-modal-overlay" onClick={() => setCreateOpen(false)}>
          <div className="enterprise-modal max-w-lg" onClick={e => e.stopPropagation()}>
              <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-[var(--text-primary)]">Create Return</h2>
              <button onClick={() => setCreateOpen(false)} className="p-1 text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] rounded-lg hover:bg-[var(--bg-tertiary)]"><X className="w-5 h-5" /></button>
            </div>
            <div className="enterprise-modal-body space-y-5">
              <div className="enterprise-form-row">
                <div className="enterprise-form-group">
                  <label>Order ID</label>
                  <input type="text" className="enterprise-input" value={createForm.orderId} onChange={e => setCreateForm(f => ({ ...f, orderId: e.target.value }))} />
                </div>
                <div className="enterprise-form-group">
                  <label>Customer ID</label>
                  <input type="text" className="enterprise-input" value={createForm.customerId} onChange={e => setCreateForm(f => ({ ...f, customerId: e.target.value }))} />
                </div>
              </div>
              <div className="enterprise-form-group">
                <label>Reason</label>
                <textarea className="enterprise-textarea" rows={2} value={createForm.reason} onChange={e => setCreateForm(f => ({ ...f, reason: e.target.value }))} />
              </div>
              <div className="enterprise-form-row">
                <div className="enterprise-form-group">
                  <label>Channel</label>
                  <select className="enterprise-select" value={createForm.returnChannel} onChange={e => setCreateForm(f => ({ ...f, returnChannel: e.target.value }))}>
                    <option value="MANUAL">Manual</option>
                    <option value="PORTAL">Customer Portal</option>
                    <option value="EMAIL">Email</option>
                    <option value="EDI">EDI</option>
                    <option value="API">API</option>
                  </select>
                </div>
                <div className="enterprise-form-group">
                  <label>Type</label>
                  <select className="enterprise-select" value={createForm.rmaType} onChange={e => setCreateForm(f => ({ ...f, rmaType: e.target.value }))}>
                    <option value="RETURN">Return</option>
                    <option value="EXCHANGE">Exchange</option>
                    <option value="REPLACEMENT">Replacement</option>
                    <option value="CANCEL">Cancel</option>
                  </select>
                </div>
              </div>
              <div className="enterprise-form-group">
                <div className="flex items-center justify-between">
                  <label>Return Items</label>
                  <button onClick={() => setCreateItems(i => [...i, { sku: '', productName: '', quantity: 1 }])} className="text-xs text-[var(--text-brand)] hover:underline font-medium">+ Add Item</button>
                </div>
                <div className="space-y-2 mt-2">
                  {createItems.map((item, i) => (
                    <div key={i} className="flex items-center gap-2">
                      <input type="text" placeholder="SKU" className="enterprise-input flex-1" value={item.sku} onChange={e => { const items = [...createItems]; items[i] = { ...items[i], sku: e.target.value }; setCreateItems(items) }} />
                      <input type="text" placeholder="Product" className="enterprise-input flex-1" value={item.productName} onChange={e => { const items = [...createItems]; items[i] = { ...items[i], productName: e.target.value }; setCreateItems(items) }} />
                      <input type="number" min={1} className="enterprise-input w-16" value={item.quantity} onChange={e => { const items = [...createItems]; items[i] = { ...items[i], quantity: parseInt(e.target.value) || 1 }; setCreateItems(items) }} />
                      {createItems.length > 1 && (
                        <button onClick={() => setCreateItems(items => items.filter((_, idx) => idx !== i))} className="p-1.5 text-[var(--nexus-error-400)] hover:text-[var(--nexus-error-600)]"><X className="w-4 h-4" /></button>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button onClick={() => setCreateOpen(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="returns" action="create">
                <button onClick={handleCreate} disabled={processing === 'create' || !createForm.orderId} className="enterprise-btn enterprise-btn-primary disabled:opacity-50">
                  {processing === 'create' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                  Create Return
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {/* Inspect Modal */}
      {inspectOpen && selectedReturn && (
        <div className="enterprise-modal-overlay" onClick={() => setInspectOpen(false)}>
          <div className="enterprise-modal max-w-xl" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-[var(--text-primary)]">Inspect Return: {selectedReturn.rmaNumber}</h2>
              <button onClick={() => setInspectOpen(false)} className="p-1 text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] rounded-lg hover:bg-[var(--bg-tertiary)]"><X className="w-5 h-5" /></button>
            </div>
            <div className="enterprise-modal-body space-y-4 max-h-[60vh] overflow-y-auto">
              {inspectItems.map((item, i) => (
                <div key={item.id || i} className="border border-[var(--border-color)] rounded-xl p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="font-medium text-[var(--text-primary)]">{item.sku}</span>
                      {item.productName && <span className="text-sm text-[var(--text-tertiary)] ml-2">{item.productName}</span>}
                    </div>
                    <span className="text-sm text-[var(--text-tertiary)]">x{item.quantity}</span>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="enterprise-form-group">
                      <label>Condition</label>
                      <select className="enterprise-select" value={item.condition || 'UNKNOWN'} onChange={e => { const items = [...inspectItems]; items[i] = { ...items[i], condition: e.target.value }; setInspectItems(items) }}>
                        {CONDITION_OPTIONS.map(c => <option key={c} value={c}>{c.replace('_', ' ')}</option>)}
                      </select>
                    </div>
                    <div className="enterprise-form-group">
                      <label>Disposition</label>
                      <select className="enterprise-select" value={item.disposition || 'PENDING'} onChange={e => { const items = [...inspectItems]; items[i] = { ...items[i], disposition: e.target.value }; setInspectItems(items) }}>
                        {DISPOSITION_OPTIONS.map(d => <option key={d} value={d}>{DISPOSITION_LABELS[d]}</option>)}
                      </select>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="enterprise-form-group">
                      <label>Grade</label>
                      <input type="text" className="enterprise-input" placeholder="A, B, C..." value={item.grade || ''} onChange={e => { const items = [...inspectItems]; items[i] = { ...items[i], grade: e.target.value }; setInspectItems(items) }} />
                    </div>
                    <div className="enterprise-form-group">
                      <label>Refund Amount</label>
                      <input type="number" step="0.01" className="enterprise-input" value={item.refundAmount || 0} onChange={e => { const items = [...inspectItems]; items[i] = { ...items[i], refundAmount: parseFloat(e.target.value) || 0 }; setInspectItems(items) }} />
                    </div>
                  </div>
                  <div className="enterprise-form-group">
                    <label>Condition Notes</label>
                    <input type="text" className="enterprise-input" placeholder="Describe item condition..." value={item.conditionNotes || ''} onChange={e => { const items = [...inspectItems]; items[i] = { ...items[i], conditionNotes: e.target.value }; setInspectItems(items) }} />
                  </div>
                </div>
              ))}
            </div>
            <div className="enterprise-modal-footer">
              <button onClick={() => setInspectOpen(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="returns" action="edit">
                <button onClick={handleInspect} disabled={processing === 'inspect'} className="enterprise-btn bg-violet-600 text-white hover:bg-violet-700 border-none disabled:opacity-50">
                  {processing === 'inspect' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Eye className="w-4 h-4" />}
                  Complete Inspection
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {/* Refund Modal */}
      {refundOpen && selectedReturn && (
        <div className="enterprise-modal-overlay" onClick={() => setRefundOpen(false)}>
          <div className="enterprise-modal max-w-md" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-[var(--text-primary)]">Process Refund</h2>
              <button onClick={() => setRefundOpen(false)} className="p-1 text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] rounded-lg hover:bg-[var(--bg-tertiary)]"><X className="w-5 h-5" /></button>
            </div>
            <div className="enterprise-modal-body space-y-4">
              <div className="enterprise-form-group">
                <label>RMA</label>
                <p className="text-sm text-[var(--text-primary)] font-medium">{selectedReturn.rmaNumber}</p>
              </div>
              <div className="enterprise-form-group">
                <label>Refund Amount ($)</label>
                <input type="number" step="0.01" className="enterprise-input" value={refundAmount} onChange={e => setRefundAmount(parseFloat(e.target.value) || 0)} />
              </div>
              <div className="enterprise-form-group">
                <label>Reference (optional)</label>
                <input type="text" className="enterprise-input" placeholder="REF-..." value={refundReference} onChange={e => setRefundReference(e.target.value)} />
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button onClick={() => setRefundOpen(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="returns" action="edit">
                <button onClick={handleRefund} disabled={processing === 'refund' || refundAmount <= 0} className="enterprise-btn bg-emerald-600 text-white hover:bg-emerald-700 border-none disabled:opacity-50">
                  {processing === 'refund' ? <Loader2 className="w-4 h-4 animate-spin" /> : <DollarSign className="w-4 h-4" />}
                  Process Refund
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {/* Reject Modal */}
      {rejectOpen && selectedReturn && (
        <div className="enterprise-modal-overlay" onClick={() => setRejectOpen(false)}>
          <div className="enterprise-modal max-w-md" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-[var(--text-primary)]">Reject Return</h2>
              <button onClick={() => setRejectOpen(false)} className="p-1 text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] rounded-lg hover:bg-[var(--bg-tertiary)]"><X className="w-5 h-5" /></button>
            </div>
            <div className="enterprise-modal-body space-y-4">
              <p className="text-sm text-[var(--text-tertiary)]">Rejecting <strong className="text-[var(--text-primary)]">{selectedReturn.rmaNumber}</strong></p>
              <div className="enterprise-form-group">
                <label>Reason</label>
                <textarea className="enterprise-textarea" rows={3} placeholder="Why is this return being rejected?" value={rejectReason} onChange={e => setRejectReason(e.target.value)} />
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button onClick={() => setRejectOpen(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="returns" action="delete">
                <button onClick={handleReject} disabled={processing === 'reject'} className="enterprise-btn bg-[var(--nexus-error-600)] text-white hover:bg-[var(--nexus-error-700)] border-none disabled:opacity-50">
                  {processing === 'reject' ? <Loader2 className="w-4 h-4 animate-spin" /> : <X className="w-4 h-4" />}
                  Reject Return
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
