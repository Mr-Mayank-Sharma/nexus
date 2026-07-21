import { useEffect, useState } from 'react'
import { fetchPayments, fetchInvoices, fetchReconciliation, createInvoice, updateInvoice } from '../api/newBackend'
import {
  CreditCard, DollarSign, Receipt, TrendingUp, TrendingDown,
  Search, Download, Plus, Filter, ChevronDown, ChevronUp,
  CheckCircle, XCircle, AlertTriangle, Clock, ArrowUpRight, ArrowDownRight,
} from 'lucide-react'
import Autocomplete from '../components/common/Autocomplete'
import clsx from 'clsx'
import PermissionGate from '../components/rbac/PermissionGate'
import { useToast } from '../hooks/useToast'

interface Payment {
  id: string
  transactionId: string
  orderNumber: string
  customer: string
  amount: number
  currency: string
  method: string
  status: 'captured' | 'pending' | 'failed' | 'refunded' | 'voided'
  date: string
  fee: number
  netAmount: number
}

interface Invoice {
  id: string
  invoiceNumber: string
  orderNumber: string
  customer: string
  amount: number
  status: 'paid' | 'pending' | 'overdue' | 'cancelled'
  dueDate: string
  paidDate?: string
}



const STATUS_STYLES: Record<string, string> = {
  captured: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] dark:bg-[var(--nexus-success-900)]/20 dark:text-[var(--nexus-success-300)] border-[var(--nexus-success-200)] dark:border-[var(--nexus-success-800)]',
  pending: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/20 dark:text-[var(--nexus-warning-300)] border-[var(--nexus-warning-200)] dark:border-[var(--nexus-warning-800)]',
  failed: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-300)] border-[var(--nexus-error-200)] dark:border-[var(--nexus-error-800)]',
  refunded: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)] dark:bg-[var(--nexus-ai-900)]/20 dark:text-[var(--nexus-ai-300)] border-[var(--nexus-ai-200)] dark:border-[var(--nexus-ai-800)]',
  voided: 'bg-[var(--surface-muted)] text-[var(--text-secondary)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)] border-[var(--border-default)]',
}

export default function PaymentsPage() {
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<'payments' | 'invoices' | 'reconciliation'>('payments')
  const [search, setSearch] = useState('')
  const [showTransactionDetail, setShowTransactionDetail] = useState<Payment | null>(null)
  const [showInvoiceDetail, setShowInvoiceDetail] = useState<Invoice | null>(null)
  const [createInvoiceOpen, setCreateInvoiceOpen] = useState(false)
  const [invoiceForm, setInvoiceForm] = useState({ orderNumber: '', customerName: '', amount: '', dueDate: '' })
  const [payments, setPayments] = useState<any[]>([])
  const [invoices, setInvoices] = useState<any[]>([])
  const [reconciliation, setReconciliation] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      fetchPayments(),
      fetchInvoices(),
      fetchReconciliation(),
    ]).then(([payRes, invRes, recRes]) => {
      if (payRes?.payments) setPayments(payRes.payments)
      if (invRes?.invoices) setInvoices(invRes.invoices)
      if (recRes?.items) setReconciliation(recRes.items)
      setLoading(false)
    }).catch(() => setLoading(false))
  }, [])

  const totalCaptured = payments.filter(p => p.status === 'captured').reduce((s, p) => s + p.amount, 0)
  const totalPending = payments.filter(p => p.status === 'pending').reduce((s, p) => s + p.amount, 0)
  const totalRefunded = payments.filter(p => p.status === 'refunded').reduce((s, p) => s + p.amount, 0)
  const totalFees = payments.reduce((s, p) => s + p.fee, 0)
  const overdueInvoices = invoices.filter(i => i.status === 'overdue')
  const totalOverdue = overdueInvoices.reduce((s, i) => s + i.amount, 0)

  const filteredPayments = payments.filter(p =>
    !search.trim() || p.orderNumber.toLowerCase().includes(search.toLowerCase()) || p.customer.toLowerCase().includes(search.toLowerCase()) || p.transactionId.toLowerCase().includes(search.toLowerCase())
  )
  const filteredInvoices = invoices.filter(i =>
    !search.trim() || i.invoiceNumber.toLowerCase().includes(search.toLowerCase()) || i.customer.toLowerCase().includes(search.toLowerCase()) || i.orderNumber.toLowerCase().includes(search.toLowerCase())
  )

  async function handleCreateInvoice(data: any) {
    const res = await createInvoice(data)
    if (res?.invoice) {
      setInvoices(prev => [res.invoice, ...prev])
      addToast({ type: 'success', title: 'Invoice created' })
    }
  }

  async function handleUpdateInvoice(id: string, data: any) {
    const res = await updateInvoice(id, data)
    if (res?.invoice) {
      setInvoices(prev => prev.map(inv => inv.id === id ? res.invoice : inv))
      addToast({ type: 'success', title: 'Invoice updated' })
    }
  }

  return (
    <div className="space-y-6">
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5">
            <CreditCard className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Payments & Invoicing
          </h1>
          <p>Transaction management, invoicing & reconciliation</p>
        </div>
        <div className="flex items-center gap-3">
          <PermissionGate resource="payments" action="create">
            <button onClick={() => setCreateInvoiceOpen(true)} className="enterprise-btn enterprise-btn-primary enterprise-btn-sm">
              <Plus className="w-3.5 h-3.5" /> Create Invoice
            </button>
          </PermissionGate>
          <button className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm">
            <Download className="w-3.5 h-3.5" /> Export
          </button>
        </div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Captured</p>
          <p className="text-xl font-bold text-[var(--nexus-success-600)]">${totalCaptured.toLocaleString()}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Pending</p>
          <p className="text-xl font-bold text-[var(--nexus-warning-600)]">${totalPending.toLocaleString()}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Refunded</p>
          <p className="text-xl font-bold text-[var(--nexus-ai-600)]">${totalRefunded.toLocaleString()}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Fees</p>
          <p className="text-xl font-bold text-[var(--nexus-error-600)]">${totalFees.toLocaleString()}</p>
        </div>
        <div className="enterprise-card p-4">
          <p className="text-xs text-[var(--text-tertiary)]">Overdue</p>
          <p className="text-xl font-bold text-[var(--nexus-error-600)]">${totalOverdue.toLocaleString()}</p>
          <p className="text-[10px] text-[var(--nexus-error-500)]">{overdueInvoices.length} invoices</p>
        </div>
      </div>

      <div className="flex items-center gap-1 border-b border-[var(--border-subtle)]">
        {(['payments', 'invoices', 'reconciliation'] as const).map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)} className={clsx('px-4 py-2.5 text-sm font-medium border-b-2 transition-colors capitalize', activeTab === tab ? 'border-[var(--color-primary)] text-[var(--color-primary)]' : 'border-transparent text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]')}>
            {tab} {tab === 'payments' ? `(${payments.length})` : tab === 'invoices' ? `(${invoices.length})` : ''}
          </button>
        ))}
        <div className="ml-auto flex items-center gap-2">
          <Autocomplete value={search} onChange={setSearch} placeholder={activeTab === 'payments' ? 'Search payments...' : 'Search invoices...'} minChars={0} />
        </div>
      </div>

      {activeTab === 'payments' && (
        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Transaction</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Customer</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Method</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Amount</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Fee</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Net</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {filteredPayments.map(p => (
                  <tr key={p.id} className="enterprise-table-row cursor-pointer" onClick={() => setShowTransactionDetail(p)}>
                    <td className="px-4 py-3 text-sm font-mono text-[var(--color-primary)]">{p.transactionId}</td>
                    <td className="px-4 py-3 text-sm font-mono text-[var(--text-primary)]">{p.orderNumber}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-primary)]">{p.customer}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{p.method}</td>
                    <td className="px-4 py-3 text-right text-sm font-semibold text-[var(--text-primary)]">${p.amount.toFixed(2)}</td>
                    <td className="px-4 py-3 text-right text-sm text-[var(--nexus-error-500)]">${p.fee.toFixed(2)}</td>
                    <td className="px-4 py-3 text-right text-sm font-semibold text-[var(--nexus-success-600)]">${p.netAmount.toFixed(2)}</td>
                    <td className="px-4 py-3 text-center"><span className={clsx('inline-flex items-center px-2 py-0.5 text-[10px] font-medium rounded-full border', STATUS_STYLES[p.status])}>{p.status}</span></td>
                    <td className="px-4 py-3 text-center text-xs text-[var(--text-tertiary)]">{p.date}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'invoices' && (
        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Invoice</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Customer</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Amount</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Due Date</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Paid Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {filteredInvoices.map(inv => (
                  <tr key={inv.id} className="enterprise-table-row cursor-pointer" onClick={() => setShowInvoiceDetail(inv)}>
                    <td className="px-4 py-3 text-sm font-mono text-[var(--color-primary)]">{inv.invoiceNumber}</td>
                    <td className="px-4 py-3 text-sm font-mono text-[var(--text-primary)]">{inv.orderNumber}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-primary)]">{inv.customer}</td>
                    <td className="px-4 py-3 text-right text-sm font-semibold text-[var(--text-primary)]">${inv.amount.toFixed(2)}</td>
                    <td className="px-4 py-3 text-center"><span className={clsx('inline-flex items-center px-2 py-0.5 text-[10px] font-medium rounded-full border', inv.status === 'paid' ? 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]' : inv.status === 'pending' ? 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]' : inv.status === 'overdue' ? 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)]' : 'bg-[var(--surface-muted)] text-[var(--text-secondary)]')}>{inv.status}</span></td>
                    <td className={clsx('px-4 py-3 text-center text-xs', inv.status === 'overdue' ? 'text-[var(--nexus-error-600)] font-semibold' : 'text-[var(--text-tertiary)]')}>{inv.dueDate}</td>
                    <td className="px-4 py-3 text-center text-xs text-[var(--text-tertiary)]">{inv.paidDate || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'reconciliation' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="enterprise-card p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2"><TrendingUp className="w-4 h-4 text-[var(--nexus-success-500)]" /> Matched</h3>
              <p className="text-2xl font-bold text-[var(--nexus-success-600)]">142</p>
              <p className="text-xs text-[var(--text-tertiary)]">$45,230.00 matched</p>
            </div>
            <div className="enterprise-card p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2"><AlertTriangle className="w-4 h-4 text-[var(--nexus-warning-500)]" /> Unmatched</h3>
              <p className="text-2xl font-bold text-[var(--nexus-warning-600)]">8</p>
              <p className="text-xs text-[var(--text-tertiary)]">$1,247.50 unmatched</p>
            </div>
            <div className="enterprise-card p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2"><XCircle className="w-4 h-4 text-[var(--nexus-error-500)]" /> Discrepancies</h3>
              <p className="text-2xl font-bold text-[var(--nexus-error-600)]">3</p>
              <p className="text-xs text-[var(--text-tertiary)]">$342.80 discrepancy total</p>
            </div>
          </div>
          <div className="enterprise-card p-5">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3">Unmatched Transactions</h3>
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Source</th>
                  <th className="px-4 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Reference</th>
                  <th className="px-4 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Amount</th>
                  <th className="px-4 py-2 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {[{ src: 'Stripe', ref: 'ch_12345', amt: 249.99 }, { src: 'PayPal', ref: 'PAY-67890', amt: 199.99 }, { src: 'Bank Deposit', ref: 'BP-11223', amt: 1500.00 }].map((tx, i) => (
                  <tr key={i} className="enterprise-table-row">
                    <td className="px-4 py-2 text-sm text-[var(--text-primary)]">{tx.src}</td>
                    <td className="px-4 py-2 text-sm font-mono text-[var(--text-secondary)]">{tx.ref}</td>
                    <td className="px-4 py-2 text-right text-sm font-semibold text-[var(--text-primary)]">${tx.amt.toFixed(2)}</td>
                    <td className="px-4 py-2 text-center">
                      <PermissionGate resource="payments" action="edit">
                        <button onClick={() => addToast({ type: 'success', title: `Matched ${tx.ref}` })} className="text-xs px-3 py-1 bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)] rounded-lg border border-[var(--nexus-primary-200)] hover:bg-[var(--nexus-primary-100)]">Match</button>
                      </PermissionGate>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showTransactionDetail && (
        <div className="enterprise-modal-overlay" onClick={() => setShowTransactionDetail(null)}>
          <div className="enterprise-card p-6 w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Transaction Detail</h2>
              <button onClick={() => setShowTransactionDetail(null)} className="text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]">✕</button>
            </div>
            <div className="space-y-3 text-sm">
              {Object.entries({ 'Transaction ID': showTransactionDetail.transactionId, Order: showTransactionDetail.orderNumber, Customer: showTransactionDetail.customer, Method: showTransactionDetail.method, Amount: `$${showTransactionDetail.amount.toFixed(2)}`, Fee: `$${showTransactionDetail.fee.toFixed(2)}`, Net: `$${showTransactionDetail.netAmount.toFixed(2)}`, Status: showTransactionDetail.status, Date: showTransactionDetail.date }).map(([k, v]) => (
                <div key={k} className="flex justify-between py-1.5 border-b border-[var(--border-subtle)] last:border-0">
                  <span className="text-[var(--text-tertiary)]">{k}</span>
                  <span className="font-medium text-[var(--text-primary)] capitalize">{v}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {showInvoiceDetail && (
        <div className="enterprise-modal-overlay" onClick={() => setShowInvoiceDetail(null)}>
          <div className="enterprise-card p-6 w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Invoice Detail</h2>
              <button onClick={() => setShowInvoiceDetail(null)} className="text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]">✕</button>
            </div>
            <div className="space-y-3 text-sm">
              {Object.entries({ 'Invoice #': showInvoiceDetail.invoiceNumber, Order: showInvoiceDetail.orderNumber, Customer: showInvoiceDetail.customer, Amount: `$${showInvoiceDetail.amount.toFixed(2)}`, Status: showInvoiceDetail.status, 'Due Date': showInvoiceDetail.dueDate, 'Paid Date': showInvoiceDetail.paidDate || '—' }).map(([k, v]) => (
                <div key={k} className="flex justify-between py-1.5 border-b border-[var(--border-subtle)] last:border-0">
                  <span className="text-[var(--text-tertiary)]">{k}</span>
                  <span className="font-medium text-[var(--text-primary)] capitalize">{v}</span>
                </div>
              ))}
              <div className="flex gap-2 pt-3">
                <PermissionGate resource="payments" action="edit">
                  <button className="enterprise-btn-primary text-sm flex-1 py-2" onClick={() => handleUpdateInvoice(showInvoiceDetail.id, { status: 'paid' })}>Mark as Paid</button>
                </PermissionGate>
                <PermissionGate resource="payments" action="edit">
                  <button className="enterprise-btn-secondary text-sm flex-1 py-2">Send Reminder</button>
                </PermissionGate>
              </div>
            </div>
          </div>
        </div>
      )}

      {createInvoiceOpen && (
        <div className="enterprise-modal-overlay" onClick={() => setCreateInvoiceOpen(false)}>
          <div className="enterprise-card p-6 w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Create Invoice</h2>
            <div className="space-y-3">
              <div><label className="enterprise-label">Order Number</label><input value={invoiceForm.orderNumber} onChange={e => setInvoiceForm(f => ({ ...f, orderNumber: e.target.value }))} className="enterprise-input w-full" placeholder="ORD-20000" /></div>
              <div><label className="enterprise-label">Customer Name</label><input value={invoiceForm.customerName} onChange={e => setInvoiceForm(f => ({ ...f, customerName: e.target.value }))} className="enterprise-input w-full" placeholder="Acme Corp" /></div>
              <div><label className="enterprise-label">Amount</label><input value={invoiceForm.amount} onChange={e => setInvoiceForm(f => ({ ...f, amount: e.target.value }))} className="enterprise-input w-full" placeholder="0.00" type="number" /></div>
              <div><label className="enterprise-label">Due Date</label><input value={invoiceForm.dueDate} onChange={e => setInvoiceForm(f => ({ ...f, dueDate: e.target.value }))} className="enterprise-input w-full" type="date" /></div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button className="enterprise-btn-secondary" onClick={() => setCreateInvoiceOpen(false)}>Cancel</button>
              <PermissionGate resource="payments" action="create">
                <button className="enterprise-btn-primary" onClick={() => handleCreateInvoice(invoiceForm)} disabled={!invoiceForm.orderNumber || !invoiceForm.customerName || !invoiceForm.amount}>Create Invoice</button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
