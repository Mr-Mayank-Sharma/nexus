import { useState, useEffect, useCallback } from 'react'
import {
  Receipt, Plus, Eye, Search, RefreshCw, FileCheck2, BadgeCheck, Wallet,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import {
  getStatements, getStatement, generateStatement, updateStatementStatus,
  type BillingStatement, type BillingStatementLine,
} from '../api/billing'
import { getCustomers } from '../api/customers'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'

const asArray = (d: unknown): any[] => (Array.isArray(d) ? d : Array.isArray((d as any)?.content) ? (d as any).content : [])

const fmtMoney = (v: number | null | undefined) => (v ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export default function BillingStatementsPage() {
  const { addToast } = useToast()
  const [statements, setStatements] = useState<BillingStatement[]>([])
  const [customers, setCustomers] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [clientFilter, setClientFilter] = useState('')
  const [showGenerate, setShowGenerate] = useState(false)
  const [showDetail, setShowDetail] = useState<{ statement: BillingStatement; lines: BillingStatementLine[] } | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [genForm, setGenForm] = useState({
    clientId: '',
    periodStart: new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1).toISOString().slice(0, 10),
    periodEnd: new Date(new Date().getFullYear(), new Date().getMonth(), 0).toISOString().slice(0, 10),
  })

  const loadCustomers = useCallback(async () => {
    try {
      const res = await getCustomers()
      setCustomers(asArray(res.data))
    } catch {
      setCustomers([])
    }
  }, [])

  const fetchStatements = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getStatements(clientFilter || undefined)
      setStatements(asArray(res.data))
    } catch {
      setStatements([])
    } finally {
      setLoading(false)
    }
  }, [clientFilter])

  useEffect(() => {
    fetchStatements()
    loadCustomers()
  }, [fetchStatements, loadCustomers])

  const clientName = (id: string) => customers.find(c => c.id === id)?.name || id.slice(0, 8)

  async function handleGenerate() {
    if (!genForm.clientId) {
      addToast({ type: 'warning', title: 'Select a client' })
      return
    }
    setSaving(true)
    try {
      const res: any = await generateStatement(genForm.clientId, genForm.periodStart, genForm.periodEnd)
      if (res.success === false) throw new Error(res.message || 'Generation failed')
      addToast({ type: 'success', title: 'Statement generated from real order data' })
      setShowGenerate(false)
      fetchStatements()
    } catch (e: any) {
      addToast({ type: 'error', title: e?.response?.data?.message || e?.message || 'Failed to generate statement' })
    } finally {
      setSaving(false)
    }
  }

  async function handleView(id: string) {
    setDetailLoading(true)
    setShowDetail(null)
    try {
      const res: any = await getStatement(id)
      setShowDetail({ statement: res.data.statement, lines: res.data.lines || [] })
    } catch (e: any) {
      addToast({ type: 'error', title: e?.response?.data?.message || 'Failed to load statement' })
    } finally {
      setDetailLoading(false)
    }
  }

  async function handleStatus(id: string, status: string) {
    try {
      await updateStatementStatus(id, status)
      setStatements(prev => prev.map(s => s.id === id ? { ...s, status: status as BillingStatement['status'] } : s))
      if (showDetail?.statement.id === id) {
        setShowDetail(prev => prev ? { ...prev, statement: { ...prev.statement, status: status as BillingStatement['status'] } } : prev)
      }
      addToast({ type: 'success', title: `Statement marked ${status}` })
    } catch (e: any) {
      addToast({ type: 'error', title: e?.response?.data?.message || 'Failed to update status' })
    }
  }

  const statusBadge = (s: string) =>
    s === 'PAID' ? 'success' : s === 'ISSUED' ? 'info' : 'warning'

  const totals = {
    issued: statements.filter(s => s.status === 'ISSUED').reduce((a, s) => a + (s.total || 0), 0),
    paid: statements.filter(s => s.status === 'PAID').reduce((a, s) => a + (s.total || 0), 0),
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Receipt className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Billing Statements
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Multi-client 3PL billing — statements computed from real orders, items and pick activity</p>
        </div>
        <div className="flex items-center gap-2">
          <EnterpriseKPICard title="Outstanding" value={fmtMoney(totals.issued)} icon={<Wallet className="w-4 h-4" />} color={totals.issued > 0 ? 'error' : 'success'} />
          <EnterpriseKPICard title="Collected" value={fmtMoney(totals.paid)} icon={<BadgeCheck className="w-4 h-4" />} color="success" />
          <PermissionGate resource="finance" action="edit">
            <button type="button" onClick={() => setShowGenerate(true)} className="enterprise-btn-primary text-sm px-4 py-2 flex items-center gap-1.5 bg-[var(--nexus-primary-600)] hover:bg-[var(--nexus-primary-700)]">
              <Plus className="w-4 h-4" /> Generate Statement
            </button>
          </PermissionGate>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <select
          value={clientFilter}
          onChange={e => setClientFilter(e.target.value)}
          className="enterprise-input max-w-xs"
        >
          <option value="">All clients</option>
          {customers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <button type="button" onClick={fetchStatements} className="enterprise-btn-secondary text-sm px-3 py-2 flex items-center gap-1.5">
          <RefreshCw className={clsx('w-4 h-4', loading && 'animate-spin')} /> Refresh
        </button>
      </div>

      {loading ? (
        <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" /></div>
      ) : statements.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <Search className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No statements yet</p>
          <p className="text-xs text-[var(--text-tertiary)] mt-1">Generate a statement for a client to bill them for real fulfillment activity</p>
        </div>
      ) : (
        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-[var(--text-tertiary)] uppercase border-b border-[var(--border-default)]">
                  <th className="px-4 py-3">Client</th>
                  <th className="px-4 py-3">Period</th>
                  <th className="px-4 py-3 text-right">Orders</th>
                  <th className="px-4 py-3 text-right">Lines</th>
                  <th className="px-4 py-3 text-right">Units</th>
                  <th className="px-4 py-3 text-right">Total</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {statements.map(s => (
                  <tr key={s.id} className="border-b border-[var(--border-default)]/60 hover:bg-[var(--surface-sunken)]/40 transition-colors">
                    <td className="px-4 py-3">
                      <p className="font-medium text-[var(--text-primary)]">{s.clientName || clientName(s.clientId)}</p>
                      <p className="text-[10px] text-[var(--text-tertiary)]">{s.currency} · {s.id.slice(0, 8)}</p>
                    </td>
                    <td className="px-4 py-3 text-[var(--text-secondary)]">{s.periodStart} → {s.periodEnd}</td>
                    <td className="px-4 py-3 text-right text-[var(--text-primary)] font-medium">{s.orderCount}</td>
                    <td className="px-4 py-3 text-right text-[var(--text-primary)] font-medium">{s.lineCount}</td>
                    <td className="px-4 py-3 text-right text-[var(--text-primary)] font-medium">{s.unitsHandled}</td>
                    <td className="px-4 py-3 text-right font-bold text-[var(--text-primary)]">{s.currency} {fmtMoney(s.total)}</td>
                    <td className="px-4 py-3"><EnterpriseStatusBadge status={statusBadge(s.status) as any} label={s.status} /></td>
                    <td className="px-4 py-3 text-right">
                      <button type="button" onClick={() => handleView(s.id)} className="enterprise-btn-secondary text-xs px-2.5 py-1.5 flex items-center gap-1 ml-auto">
                        <Eye className="w-3.5 h-3.5" /> View
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showGenerate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={() => setShowGenerate(false)}>
          <div className="bg-[var(--surface-base)] rounded-2xl border border-[var(--border-default)] shadow-2xl w-full max-w-md p-6" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-bold text-[var(--text-primary)] mb-4">Generate Billing Statement</h2>
            <p className="text-xs text-[var(--text-secondary)] mb-4">
              Amounts are computed from the client's rate card against real orders, order lines and picked lines in the period.
            </p>
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Client</label>
                <select value={genForm.clientId} onChange={e => setGenForm(prev => ({ ...prev, clientId: e.target.value }))} className="enterprise-input w-full">
                  <option value="">Select client...</option>
                  {customers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Period Start</label>
                  <input type="date" value={genForm.periodStart} onChange={e => setGenForm(prev => ({ ...prev, periodStart: e.target.value }))} className="enterprise-input w-full" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Period End</label>
                  <input type="date" value={genForm.periodEnd} onChange={e => setGenForm(prev => ({ ...prev, periodEnd: e.target.value }))} className="enterprise-input w-full" />
                </div>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 mt-6">
              <button type="button" onClick={() => setShowGenerate(false)} className="enterprise-btn-secondary text-sm px-4 py-2">Cancel</button>
              <button type="button" onClick={handleGenerate} disabled={saving} className="enterprise-btn-primary text-sm px-4 py-2 bg-[var(--nexus-primary-600)] hover:bg-[var(--nexus-primary-700)]">
                {saving ? 'Generating...' : 'Generate Statement'}
              </button>
            </div>
          </div>
        </div>
      )}

      {showDetail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={() => setShowDetail(null)}>
          <div className="bg-[var(--surface-base)] rounded-2xl border border-[var(--border-default)] shadow-2xl w-full max-w-2xl p-6 max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
            <div className="flex items-start justify-between mb-4">
              <div>
                <h2 className="text-lg font-bold text-[var(--text-primary)]">{showDetail.statement.clientName || clientName(showDetail.statement.clientId)}</h2>
                <p className="text-xs text-[var(--text-secondary)]">
                  {showDetail.statement.periodStart} → {showDetail.statement.periodEnd} · {showDetail.statement.currency} · {showDetail.statement.id.slice(0, 8)}
                </p>
              </div>
              <EnterpriseStatusBadge status={statusBadge(showDetail.statement.status) as any} label={showDetail.statement.status} />
            </div>

            <div className="grid grid-cols-4 gap-2 mb-4">
              {[
                { label: 'Orders', value: showDetail.statement.orderCount },
                { label: 'Order Lines', value: showDetail.statement.lineCount },
                { label: 'Picked Lines', value: showDetail.statement.pickedLines },
                { label: 'Units Handled', value: showDetail.statement.unitsHandled },
              ].map(f => (
                <div key={f.label} className="bg-[var(--surface-sunken)]/60 rounded-lg px-3 py-2 text-center">
                  <p className="text-lg font-bold text-[var(--text-primary)]">{f.value}</p>
                  <p className="text-[10px] text-[var(--text-tertiary)] uppercase">{f.label}</p>
                </div>
              ))}
            </div>

            <div className="space-y-2 mb-4">
              {showDetail.lines.length === 0 ? (
                <p className="text-sm text-[var(--text-tertiary)] text-center py-4">No line items</p>
              ) : showDetail.lines.map(line => (
                <div key={line.id} className="flex items-center justify-between bg-[var(--surface-sunken)]/50 rounded-lg px-3 py-2">
                  <div>
                    <p className="text-sm font-medium text-[var(--text-primary)]">{line.rateType}</p>
                    <p className="text-[10px] text-[var(--text-tertiary)]">{line.description}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-semibold text-[var(--text-primary)]">{showDetail.statement.currency} {fmtMoney(line.amount)}</p>
                    <p className="text-[10px] text-[var(--text-tertiary)]">{line.quantity} × {fmtMoney(line.unitPrice)}</p>
                  </div>
                </div>
              ))}
            </div>

            <div className="flex items-center justify-between bg-[var(--surface-sunken)] rounded-xl px-4 py-3 mb-4">
              <p className="text-sm font-semibold text-[var(--text-secondary)]">Total</p>
              <p className="text-xl font-bold text-[var(--text-primary)]">{showDetail.statement.currency} {fmtMoney(showDetail.statement.total)}</p>
            </div>

            {showDetail.statement.notes && (
              <p className="text-xs text-[var(--text-tertiary)] mb-4">{showDetail.statement.notes}</p>
            )}

            <div className="flex items-center justify-end gap-2">
              {showDetail.statement.status === 'DRAFT' && (
                <PermissionGate resource="finance" action="edit">
                  <button type="button" onClick={() => handleStatus(showDetail.statement.id, 'ISSUED')} className="enterprise-btn-primary text-sm px-4 py-2 bg-[var(--nexus-primary-600)] hover:bg-[var(--nexus-primary-700)] flex items-center gap-1.5">
                    <FileCheck2 className="w-4 h-4" /> Issue Statement
                  </button>
                </PermissionGate>
              )}
              {showDetail.statement.status === 'ISSUED' && (
                <PermissionGate resource="finance" action="edit">
                  <button type="button" onClick={() => handleStatus(showDetail.statement.id, 'PAID')} className="enterprise-btn-primary text-sm px-4 py-2 bg-[var(--nexus-success-600)] hover:bg-[var(--nexus-success-700)] flex items-center gap-1.5">
                    <BadgeCheck className="w-4 h-4" /> Mark Paid
                  </button>
                </PermissionGate>
              )}
              <button type="button" onClick={() => setShowDetail(null)} className="enterprise-btn-secondary text-sm px-4 py-2">Close</button>
            </div>
          </div>
        </div>
      )}

      {detailLoading && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      )}
    </div>
  )
}
