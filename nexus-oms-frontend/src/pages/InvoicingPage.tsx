import { useState, useEffect } from 'react'
import { Receipt, CreditCard, FileText, Plus, Search, X, Check, ArrowUpDown, DollarSign, AlertTriangle, Loader2 } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import * as invoicingApi from '../api/invoicing'
import { Invoice, InvoiceItem, Payment, CreditMemo } from '../api/invoicing'
import StatusBadge from '../components/common/StatusBadge'

type Tab = 'invoices' | 'payments' | 'credit-memos'

const invoiceStatusStyles: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-700 border-gray-200',
  SENT: 'bg-blue-50 text-blue-700 border-blue-200',
  PARTIALLY_PAID: 'bg-yellow-50 text-yellow-700 border-yellow-200',
  PAID: 'bg-green-50 text-green-700 border-green-200',
  OVERDUE: 'bg-red-50 text-red-700 border-red-200',
  CANCELLED: 'bg-red-50 text-red-700 border-red-200',
  REFUNDED: 'bg-purple-50 text-purple-700 border-purple-200',
}

const paymentStatusStyles: Record<string, string> = {
  PENDING: 'bg-yellow-50 text-yellow-700 border-yellow-200',
  COMPLETED: 'bg-green-50 text-green-700 border-green-200',
  FAILED: 'bg-red-50 text-red-700 border-red-200',
  REFUNDED: 'bg-purple-50 text-purple-700 border-purple-200',
}

const creditMemoStatusStyles: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-700 border-gray-200',
  ISSUED: 'bg-blue-50 text-blue-700 border-blue-200',
  APPLIED: 'bg-green-50 text-green-700 border-green-200',
  VOID: 'bg-red-50 text-red-700 border-red-200',
}

function StatusBadgeLocal({ status, styles }: { status: string; styles: Record<string, string> }) {
  const s = styles[status] || styles.DRAFT || 'bg-gray-100 text-gray-700 border-gray-200'
  return (
    <span className={`inline-flex items-center rounded-full border font-medium px-2.5 py-0.5 text-xs ${s}`}>
      {status.replace(/_/g, ' ')}
    </span>
  )
}

function StatCard({ label, value, prefix = '$', className = '' }: { label: string; value: string | number; prefix?: string; className?: string }) {
  return (
    <div className={`card p-4 flex flex-col ${className}`}>
      <span className="text-xs text-gray-500 font-medium uppercase tracking-wider">{label}</span>
      <span className="text-2xl font-bold mt-1">{prefix}{typeof value === 'number' ? value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : value}</span>
    </div>
  )
}

function Badge({ label, count, className = '' }: { label: string; count: number; className?: string }) {
  return (
    <div className={`card p-3 flex items-center justify-between ${className}`}>
      <span className="text-sm text-gray-600">{label}</span>
      <span className="text-lg font-bold">{count}</span>
    </div>
  )
}

export default function InvoicingPage() {
  const { addToast } = useToast()
  const [tab, setTab] = useState<Tab>('invoices')

  // Summary
  const [summary, setSummary] = useState<Record<string, any>>({})
  const [loadingSummary, setLoadingSummary] = useState(true)

  // Invoices
  const [invoices, setInvoices] = useState<Invoice[]>([])
  const [loadingInvoices, setLoadingInvoices] = useState(true)
  const [invoiceSearch, setInvoiceSearch] = useState('')
  const [invoicePage, setInvoicePage] = useState(1)
  const [invoiceTotalPages, setInvoiceTotalPages] = useState(1)
  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null)
  const [invoicePayments, setInvoicePayments] = useState<Payment[]>([])

  // Payments
  const [payments, setPayments] = useState<Payment[]>([])
  const [loadingPayments, setLoadingPayments] = useState(true)
  const [paymentPage, setPaymentPage] = useState(1)
  const [paymentTotalPages, setPaymentTotalPages] = useState(1)
  const [paymentSearch, setPaymentSearch] = useState('')

  // Credit Memos
  const [creditMemos, setCreditMemos] = useState<CreditMemo[]>([])
  const [loadingMemos, setLoadingMemos] = useState(true)
  const [memoPage, setMemoPage] = useState(1)
  const [memoTotalPages, setMemoTotalPages] = useState(1)
  const [memoSearch, setMemoSearch] = useState('')

  // Modals
  const [showCreateInvoice, setShowCreateInvoice] = useState(false)
  const [showRecordPayment, setShowRecordPayment] = useState(false)
  const [showCreateMemo, setShowCreateMemo] = useState(false)
  const [showRefund, setShowRefund] = useState<Payment | null>(null)
  const [saving, setSaving] = useState(false)

  // Create Invoice form
  const emptyItemForm = { sku: '', productName: '', quantity: 1, unitPrice: 0 }
  const [invForm, setInvForm] = useState({
    invoiceType: 'STANDARD',
    customerName: '',
    customerEmail: '',
    orderId: '',
    dueDate: '',
    paymentTerms: 'NET_30',
    notes: '',
    items: [{ ...emptyItemForm }],
  })

  // Record Payment form
  const [paymentForm, setPaymentForm] = useState({
    invoiceId: '',
    amount: 0,
    method: 'CREDIT_CARD' as Payment['method'],
    reference: '',
    notes: '',
  })

  // Refund form
  const [refundForm, setRefundForm] = useState({ amount: 0, reason: '' })

  // Credit Memo form
  const [memoForm, setMemoForm] = useState({
    memoType: 'REFUND' as 'REFUND' | 'CREDIT',
    invoiceId: '',
    invoiceNumber: '',
    reason: '',
    amount: 0,
    notes: '',
  })

  const pageSize = 15

  useEffect(() => { fetchSummary() }, [])
  useEffect(() => { fetchInvoices() }, [invoicePage])
  useEffect(() => { fetchPayments() }, [paymentPage])
  useEffect(() => { fetchCreditMemos() }, [memoPage])

  async function fetchSummary() {
    try {
      setLoadingSummary(true)
      const res = await invoicingApi.getInvoiceSummary()
      setSummary(res.data || {})
    } catch {
      addToast({ type: 'error', title: 'Failed to load invoice summary' })
    } finally { setLoadingSummary(false) }
  }

  async function fetchInvoices() {
    try {
      setLoadingInvoices(true)
      const res = await invoicingApi.getInvoices(invoicePage - 1, pageSize)
      const d = res.data
      setInvoices(d.content || [])
      setInvoiceTotalPages(d.totalPages || 1)
    } catch {
      addToast({ type: 'error', title: 'Failed to load invoices' })
    } finally { setLoadingInvoices(false) }
  }

  async function fetchPayments() {
    try {
      setLoadingPayments(true)
      const res = await invoicingApi.getPayments(paymentPage - 1, pageSize)
      const d = res.data
      setPayments(d.content || [])
      setPaymentTotalPages(d.totalPages || 1)
    } catch {
      addToast({ type: 'error', title: 'Failed to load payments' })
    } finally { setLoadingPayments(false) }
  }

  async function fetchCreditMemos() {
    try {
      setLoadingMemos(true)
      const res = await invoicingApi.getCreditMemos(memoPage - 1, pageSize)
      const d = res.data
      setCreditMemos(d.content || [])
      setMemoTotalPages(d.totalPages || 1)
    } catch {
      addToast({ type: 'error', title: 'Failed to load credit memos' })
    } finally { setLoadingMemos(false) }
  }

  async function fetchInvoicePayments(invoiceId: string) {
    try {
      const res = await invoicingApi.getPaymentsByInvoice(invoiceId)
      setInvoicePayments(res.data || [])
    } catch {
      setInvoicePayments([])
    }
  }

  function openInvoiceDetail(invoice: Invoice) {
    setSelectedInvoice(invoice)
    fetchInvoicePayments(invoice.id)
  }

  function closeInvoiceDetail() {
    setSelectedInvoice(null)
    setInvoicePayments([])
  }

  function addItemRow() {
    setInvForm({ ...invForm, items: [...invForm.items, { ...emptyItemForm }] })
  }

  function removeItemRow(idx: number) {
    if (invForm.items.length <= 1) return
    setInvForm({ ...invForm, items: invForm.items.filter((_, i) => i !== idx) })
  }

  function updateItem(idx: number, field: string, value: string | number) {
    const items = invForm.items.map((item, i) => {
      if (i !== idx) return item
      const updated = { ...item, [field]: value }
      if (field === 'quantity' || field === 'unitPrice') {
        updated.unitPrice = field === 'unitPrice' ? Number(value) : item.unitPrice
        updated.quantity = field === 'quantity' ? Number(value) : item.quantity
      }
      return updated
    })
    setInvForm({ ...invForm, items })
  }

  function getItemTotal(item: typeof invForm.items[0]) {
    return (item.quantity || 0) * (item.unitPrice || 0)
  }

  function getInvoiceFormTotal() {
    return invForm.items.reduce((sum, item) => sum + (item.quantity || 0) * (item.unitPrice || 0), 0)
  }

  async function handleCreateInvoice() {
    if (!invForm.customerName.trim()) {
      addToast({ type: 'warning', title: 'Customer name is required' })
      return
    }
    setSaving(true)
    try {
      const payload = {
        invoiceType: invForm.invoiceType,
        customerName: invForm.customerName,
        customerEmail: invForm.customerEmail,
        orderId: invForm.orderId || undefined,
        dueDate: invForm.dueDate || undefined,
        paymentTerms: invForm.paymentTerms,
        notes: invForm.notes,
        items: invForm.items.map(item => ({
          sku: item.sku,
          productName: item.productName,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
        })),
      }
      await invoicingApi.createInvoice(payload)
      addToast({ type: 'success', title: 'Invoice created' })
      setShowCreateInvoice(false)
      resetInvForm()
      await fetchInvoices()
      await fetchSummary()
    } catch {
      addToast({ type: 'error', title: 'Failed to create invoice' })
    } finally { setSaving(false) }
  }

  function resetInvForm() {
    setInvForm({
      invoiceType: 'STANDARD',
      customerName: '',
      customerEmail: '',
      orderId: '',
      dueDate: '',
      paymentTerms: 'NET_30',
      notes: '',
      items: [{ ...emptyItemForm }],
    })
  }

  async function handleStatusChange(invoiceId: string, status: string) {
    try {
      await invoicingApi.updateInvoiceStatus(invoiceId, status)
      addToast({ type: 'success', title: 'Status updated' })
      await fetchInvoices()
      await fetchSummary()
      if (selectedInvoice?.id === invoiceId) {
        const updated = invoices.find(i => i.id === invoiceId)
        if (updated) setSelectedInvoice(updated)
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to update status' })
    }
  }

  function openRecordPayment(invoice?: Invoice) {
    setPaymentForm({
      invoiceId: invoice?.id || '',
      amount: invoice?.amountDue || 0,
      method: 'CREDIT_CARD',
      reference: '',
      notes: '',
    })
    setShowRecordPayment(true)
  }

  async function handleRecordPayment() {
    if (!paymentForm.invoiceId) {
      addToast({ type: 'warning', title: 'Invoice is required' })
      return
    }
    if (paymentForm.amount <= 0) {
      addToast({ type: 'warning', title: 'Amount must be greater than 0' })
      return
    }
    setSaving(true)
    try {
      await invoicingApi.recordPayment(paymentForm.invoiceId, {
        amount: paymentForm.amount,
        method: paymentForm.method,
        reference: paymentForm.reference,
        notes: paymentForm.notes,
      })
      addToast({ type: 'success', title: 'Payment recorded' })
      setShowRecordPayment(false)
      await fetchInvoices()
      await fetchPayments()
      await fetchSummary()
      if (selectedInvoice) {
        await fetchInvoicePayments(selectedInvoice.id)
        const updated = invoices.find(i => i.id === selectedInvoice.id)
        if (updated) setSelectedInvoice(updated)
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to record payment' })
    } finally { setSaving(false) }
  }

  function openRefund(payment: Payment) {
    setRefundForm({ amount: payment.amount, reason: '' })
    setShowRefund(payment)
  }

  async function handleRefund() {
    if (!showRefund) return
    if (refundForm.amount <= 0) {
      addToast({ type: 'warning', title: 'Amount must be greater than 0' })
      return
    }
    setSaving(true)
    try {
      await invoicingApi.processRefund(showRefund.id, { amount: refundForm.amount, reason: refundForm.reason })
      addToast({ type: 'success', title: 'Refund processed' })
      setShowRefund(null)
      await fetchPayments()
      await fetchInvoices()
      await fetchSummary()
    } catch {
      addToast({ type: 'error', title: 'Failed to process refund' })
    } finally { setSaving(false) }
  }

  async function handleCreateMemo() {
    if (!memoForm.invoiceId.trim()) {
      addToast({ type: 'warning', title: 'Invoice is required' })
      return
    }
    if (!memoForm.reason.trim()) {
      addToast({ type: 'warning', title: 'Reason is required' })
      return
    }
    if (memoForm.amount <= 0) {
      addToast({ type: 'warning', title: 'Amount must be greater than 0' })
      return
    }
    setSaving(true)
    try {
      await invoicingApi.createCreditMemo({
        memoType: memoForm.memoType,
        invoiceId: memoForm.invoiceId,
        reason: memoForm.reason,
        totalAmount: memoForm.amount,
        notes: memoForm.notes,
      })
      addToast({ type: 'success', title: 'Credit memo created' })
      setShowCreateMemo(false)
      setMemoForm({ memoType: 'REFUND', invoiceId: '', invoiceNumber: '', reason: '', amount: 0, notes: '' })
      await fetchCreditMemos()
      await fetchSummary()
    } catch {
      addToast({ type: 'error', title: 'Failed to create credit memo' })
    } finally { setSaving(false) }
  }

  const filteredInvoices = invoices.filter(inv => {
    if (!invoiceSearch) return true
    const q = invoiceSearch.toLowerCase()
    return inv.invoiceNumber?.toLowerCase().includes(q) || inv.customerName?.toLowerCase().includes(q) || inv.orderNumber?.toLowerCase().includes(q)
  })

  const filteredPayments = payments.filter(p => {
    if (!paymentSearch) return true
    const q = paymentSearch.toLowerCase()
    return p.id.toLowerCase().includes(q) || p.invoiceNumber?.toLowerCase().includes(q) || p.reference?.toLowerCase().includes(q)
  })

  const filteredMemos = creditMemos.filter(m => {
    if (!memoSearch) return true
    const q = memoSearch.toLowerCase()
    return m.creditMemoNumber?.toLowerCase().includes(q) || m.invoiceNumber?.toLowerCase().includes(q) || m.reason?.toLowerCase().includes(q)
  })

  // ── Render ──

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Invoicing</h1>
          <p className="text-sm text-gray-500 mt-1">Manage invoices, payments, and credit memos</p>
        </div>
      </div>

      {/* Summary Bar */}
      <div className="space-y-4">
        {loadingSummary ? (
          <div className="flex items-center gap-2 text-sm text-gray-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading summary...</div>
        ) : (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              <StatCard label="Total Outstanding" value={summary.totalOutstanding ?? 0} />
              <StatCard label="Total Overdue" value={summary.totalOverdue ?? 0} className="text-red-600" />
              <StatCard label="Paid This Month" value={summary.paidThisMonth ?? 0} className="text-green-600" />
            </div>
            <div className="flex flex-wrap gap-3">
              <Badge label="Pending Invoices" count={summary.pendingCount ?? 0} className="flex-1 min-w-[140px]" />
              <Badge label="Paid Invoices" count={summary.paidCount ?? 0} className="flex-1 min-w-[140px]" />
              <Badge label="Overdue Invoices" count={summary.overdueCount ?? 0} className="flex-1 min-w-[140px] text-red-600" />
            </div>
          </>
        )}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        {[
          { key: 'invoices' as Tab, label: 'Invoices', icon: Receipt },
          { key: 'payments' as Tab, label: 'Payments', icon: CreditCard },
          { key: 'credit-memos' as Tab, label: 'Credit Memos', icon: FileText },
        ].map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => { setTab(key); setSelectedInvoice(null) }}
            className={`flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
              tab === key
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            <Icon className="w-4 h-4" />
            {label}
          </button>
        ))}
      </div>

      {/* ── INVOICES TAB ── */}
      {tab === 'invoices' && (
        <div className="space-y-4">
          {/* Toolbar */}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input value={invoiceSearch} onChange={e => setInvoiceSearch(e.target.value)} className="input pl-9 w-64 text-sm" placeholder="Search invoices..." />
              </div>
            </div>
            <button onClick={() => setShowCreateInvoice(true)} className="btn-primary text-sm">
              <Plus className="w-4 h-4" /> Create Invoice
            </button>
          </div>

          {/* Invoice Detail View */}
          {selectedInvoice ? (
            <div className="card p-6 space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-semibold text-gray-900">Invoice {selectedInvoice.invoiceNumber}</h3>
                  <p className="text-sm text-gray-500">{selectedInvoice.customerName} &middot; {selectedInvoice.customerEmail}</p>
                </div>
                <div className="flex items-center gap-2">
                  <StatusBadgeLocal status={selectedInvoice.status} styles={invoiceStatusStyles} />
                  <button onClick={closeInvoiceDetail} className="p-1.5 hover:bg-gray-100 rounded text-gray-500"><X className="w-4 h-4" /></button>
                </div>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
                <div><span className="text-gray-500">Total</span><p className="font-semibold">${selectedInvoice.totalAmount.toFixed(2)}</p></div>
                <div><span className="text-gray-500">Paid</span><p className="font-semibold">${selectedInvoice.amountPaid.toFixed(2)}</p></div>
                <div><span className="text-gray-500">Balance</span><p className="font-semibold">${selectedInvoice.amountDue.toFixed(2)}</p></div>
                <div><span className="text-gray-500">Due</span><p className="font-semibold">{selectedInvoice.dueDate ? new Date(selectedInvoice.dueDate).toLocaleDateString() : '-'}</p></div>
              </div>

              {/* Items */}
              <div>
                <h4 className="text-sm font-semibold text-gray-700 mb-2">Items</h4>
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 text-left text-xs text-gray-500 uppercase">
                      <th className="pb-2 font-medium">SKU</th>
                      <th className="pb-2 font-medium">Description</th>
                      <th className="pb-2 font-medium text-right">Qty</th>
                      <th className="pb-2 font-medium text-right">Unit Price</th>
                      <th className="pb-2 font-medium text-right">Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selectedInvoice.items?.map((item) => (
                      <tr key={item.id} className="border-b border-gray-50">
                        <td className="py-2 text-gray-900">{item.sku}</td>
                        <td className="py-2 text-gray-600">{item.productName}</td>
                        <td className="py-2 text-right">{item.quantity}</td>
                        <td className="py-2 text-right">${item.unitPrice.toFixed(2)}</td>
                        <td className="py-2 text-right font-medium">${item.totalPrice.toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Payments on this invoice */}
              <div>
                <h4 className="text-sm font-semibold text-gray-700 mb-2">Payments</h4>
                {invoicePayments.length === 0 ? (
                  <p className="text-xs text-gray-400">No payments recorded</p>
                ) : (
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-100 text-left text-xs text-gray-500 uppercase">
                        <th className="pb-2 font-medium">Transaction</th>
                        <th className="pb-2 font-medium">Method</th>
                        <th className="pb-2 font-medium text-right">Amount</th>
                        <th className="pb-2 font-medium">Status</th>
                        <th className="pb-2 font-medium">Date</th>
                      </tr>
                    </thead>
                    <tbody>
                      {invoicePayments.map((p) => (
                        <tr key={p.id} className="border-b border-gray-50">
                          <td className="py-2 text-gray-900">{p.transactionId || p.id.slice(0, 8)}</td>
                          <td className="py-2 text-gray-600">{p.method.replace(/_/g, ' ')}</td>
                          <td className="py-2 text-right">${p.amount.toFixed(2)}</td>
                          <td className="py-2"><StatusBadgeLocal status={p.status} styles={paymentStatusStyles} /></td>
                          <td className="py-2 text-gray-500">{p.processedAt ? new Date(p.processedAt).toLocaleDateString() : '-'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              {/* Actions */}
              <div className="flex items-center gap-3 pt-2">
                <button onClick={() => openRecordPayment(selectedInvoice)} className="btn-primary text-sm">
                  <DollarSign className="w-4 h-4" /> Record Payment
                </button>
                <div className="flex items-center gap-2">
                  <label className="text-xs text-gray-500">Status:</label>
                  <select
                    value={selectedInvoice.status}
                    onChange={e => handleStatusChange(selectedInvoice.id, e.target.value)}
                    className="input text-sm py-1.5"
                  >
                    <option value="DRAFT">DRAFT</option>
                    <option value="SENT">SENT</option>
                    <option value="PARTIALLY_PAID">PARTIALLY PAID</option>
                    <option value="PAID">PAID</option>
                    <option value="OVERDUE">OVERDUE</option>
                    <option value="CANCELLED">CANCELLED</option>
                  </select>
                </div>
              </div>
            </div>
          ) : null}

          {/* Invoices Table */}
          <div className="card overflow-hidden">
            {loadingInvoices ? (
              <div className="flex items-center justify-center h-40">
                <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
              </div>
            ) : filteredInvoices.length === 0 ? (
              <div className="text-center py-12">
                <Receipt className="w-10 h-10 text-gray-300 mx-auto mb-2" />
                <p className="text-sm text-gray-500">No invoices found</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 text-left text-xs text-gray-500 uppercase">
                      <th className="px-4 py-3 font-medium">Invoice #</th>
                      <th className="px-4 py-3 font-medium">Type</th>
                      <th className="px-4 py-3 font-medium">Customer/Supplier</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                      <th className="px-4 py-3 font-medium text-right">Total</th>
                      <th className="px-4 py-3 font-medium text-right">Paid</th>
                      <th className="px-4 py-3 font-medium text-right">Balance</th>
                      <th className="px-4 py-3 font-medium">Due Date</th>
                      <th className="px-4 py-3 font-medium text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredInvoices.map((inv) => (
                      <tr
                        key={inv.id}
                        className="border-b border-gray-50 hover:bg-gray-50 cursor-pointer"
                        onClick={() => openInvoiceDetail(inv)}
                      >
                        <td className="px-4 py-3 font-medium text-primary-600">{inv.invoiceNumber || inv.id.slice(0, 8)}</td>
                        <td className="px-4 py-3 text-gray-600">{inv.orderNumber ? 'Sales' : 'Standard'}</td>
                        <td className="px-4 py-3 text-gray-900">{inv.customerName || inv.supplierName || '-'}</td>
                        <td className="px-4 py-3"><StatusBadgeLocal status={inv.status} styles={invoiceStatusStyles} /></td>
                        <td className="px-4 py-3 text-right font-medium">${inv.totalAmount.toFixed(2)}</td>
                        <td className="px-4 py-3 text-right">${inv.amountPaid.toFixed(2)}</td>
                        <td className="px-4 py-3 text-right font-semibold">${inv.amountDue.toFixed(2)}</td>
                        <td className="px-4 py-3 text-gray-500">{inv.dueDate ? new Date(inv.dueDate).toLocaleDateString() : '-'}</td>
                        <td className="px-4 py-3 text-right">
                          <div className="flex items-center justify-end gap-1" onClick={e => e.stopPropagation()}>
                            <button onClick={() => openRecordPayment(inv)} className="p-1.5 hover:bg-gray-100 rounded text-gray-500 hover:text-primary-600" title="Record Payment">
                              <DollarSign className="w-4 h-4" />
                            </button>
                            <select
                              value={inv.status}
                              onChange={e => handleStatusChange(inv.id, e.target.value)}
                              className="text-xs border border-gray-200 rounded px-1.5 py-1 bg-white text-gray-600"
                              onClick={e => e.stopPropagation()}
                            >
                              <option value="DRAFT">DRAFT</option>
                              <option value="SENT">SENT</option>
                              <option value="PARTIALLY_PAID">PARTIALLY PAID</option>
                              <option value="PAID">PAID</option>
                              <option value="OVERDUE">OVERDUE</option>
                              <option value="CANCELLED">CANCELLED</option>
                            </select>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {invoiceTotalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
                <span className="text-xs text-gray-500">Page {invoicePage} of {invoiceTotalPages}</span>
                <div className="flex gap-1">
                  <button disabled={invoicePage <= 1} onClick={() => setInvoicePage(p => p - 1)} className="px-3 py-1 text-xs border border-gray-200 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
                  <button disabled={invoicePage >= invoiceTotalPages} onClick={() => setInvoicePage(p => p + 1)} className="px-3 py-1 text-xs border border-gray-200 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── PAYMENTS TAB ── */}
      {tab === 'payments' && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input value={paymentSearch} onChange={e => setPaymentSearch(e.target.value)} className="input pl-9 w-64 text-sm" placeholder="Search payments..." />
            </div>
            <button onClick={() => openRecordPayment()} className="btn-primary text-sm">
              <Plus className="w-4 h-4" /> Record Payment
            </button>
          </div>

          <div className="card overflow-hidden">
            {loadingPayments ? (
              <div className="flex items-center justify-center h-40">
                <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
              </div>
            ) : filteredPayments.length === 0 ? (
              <div className="text-center py-12">
                <CreditCard className="w-10 h-10 text-gray-300 mx-auto mb-2" />
                <p className="text-sm text-gray-500">No payments found</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 text-left text-xs text-gray-500 uppercase">
                      <th className="px-4 py-3 font-medium">Payment #</th>
                      <th className="px-4 py-3 font-medium">Invoice</th>
                      <th className="px-4 py-3 font-medium text-right">Amount</th>
                      <th className="px-4 py-3 font-medium">Method</th>
                      <th className="px-4 py-3 font-medium">Reference</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                      <th className="px-4 py-3 font-medium">Date</th>
                      <th className="px-4 py-3 font-medium text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredPayments.map((p) => (
                      <tr key={p.id} className="border-b border-gray-50 hover:bg-gray-50">
                        <td className="px-4 py-3 font-medium text-primary-600">{p.transactionId || p.id.slice(0, 8)}</td>
                        <td className="px-4 py-3 text-gray-600">{p.invoiceNumber || p.invoiceId.slice(0, 8)}</td>
                        <td className="px-4 py-3 text-right font-medium">${p.amount.toFixed(2)}</td>
                        <td className="px-4 py-3 text-gray-600">{p.method.replace(/_/g, ' ')}</td>
                        <td className="px-4 py-3 text-gray-500">{p.reference || '-'}</td>
                        <td className="px-4 py-3"><StatusBadgeLocal status={p.status} styles={paymentStatusStyles} /></td>
                        <td className="px-4 py-3 text-gray-500">{p.processedAt ? new Date(p.processedAt).toLocaleDateString() : new Date(p.createdAt).toLocaleDateString()}</td>
                        <td className="px-4 py-3 text-right">
                          {p.status === 'COMPLETED' && (
                            <button onClick={() => openRefund(p)} className="text-xs text-red-600 hover:text-red-800 font-medium">Refund</button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {paymentTotalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
                <span className="text-xs text-gray-500">Page {paymentPage} of {paymentTotalPages}</span>
                <div className="flex gap-1">
                  <button disabled={paymentPage <= 1} onClick={() => setPaymentPage(p => p - 1)} className="px-3 py-1 text-xs border border-gray-200 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
                  <button disabled={paymentPage >= paymentTotalPages} onClick={() => setPaymentPage(p => p + 1)} className="px-3 py-1 text-xs border border-gray-200 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── CREDIT MEMOS TAB ── */}
      {tab === 'credit-memos' && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input value={memoSearch} onChange={e => setMemoSearch(e.target.value)} className="input pl-9 w-64 text-sm" placeholder="Search credit memos..." />
            </div>
            <button onClick={() => setShowCreateMemo(true)} className="btn-primary text-sm">
              <Plus className="w-4 h-4" /> Create Credit Memo
            </button>
          </div>

          <div className="card overflow-hidden">
            {loadingMemos ? (
              <div className="flex items-center justify-center h-40">
                <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
              </div>
            ) : filteredMemos.length === 0 ? (
              <div className="text-center py-12">
                <FileText className="w-10 h-10 text-gray-300 mx-auto mb-2" />
                <p className="text-sm text-gray-500">No credit memos found</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 text-left text-xs text-gray-500 uppercase">
                      <th className="px-4 py-3 font-medium">Memo #</th>
                      <th className="px-4 py-3 font-medium">Type</th>
                      <th className="px-4 py-3 font-medium">Invoice</th>
                      <th className="px-4 py-3 font-medium">Reason</th>
                      <th className="px-4 py-3 font-medium text-right">Amount</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                      <th className="px-4 py-3 font-medium">Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredMemos.map((m) => (
                      <tr key={m.id} className="border-b border-gray-50 hover:bg-gray-50">
                        <td className="px-4 py-3 font-medium text-primary-600">{m.creditMemoNumber || m.id.slice(0, 8)}</td>
                        <td className="px-4 py-3 text-gray-600">
                          {m.creditMemoNumber?.startsWith('CM-REFUND') ? 'Refund' : m.creditMemoNumber?.startsWith('CM-CREDIT') ? 'Credit' : m.totalAmount < 0 ? 'Credit' : 'Refund'}
                        </td>
                        <td className="px-4 py-3 text-gray-600">{m.invoiceNumber || m.invoiceId.slice(0, 8)}</td>
                        <td className="px-4 py-3 text-gray-600 max-w-[200px] truncate">{m.reason}</td>
                        <td className="px-4 py-3 text-right font-medium">${Math.abs(m.totalAmount).toFixed(2)}</td>
                        <td className="px-4 py-3"><StatusBadgeLocal status={m.status} styles={creditMemoStatusStyles} /></td>
                        <td className="px-4 py-3 text-gray-500">{m.issuedDate ? new Date(m.issuedDate).toLocaleDateString() : new Date(m.createdAt).toLocaleDateString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {memoTotalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
                <span className="text-xs text-gray-500">Page {memoPage} of {memoTotalPages}</span>
                <div className="flex gap-1">
                  <button disabled={memoPage <= 1} onClick={() => setMemoPage(p => p - 1)} className="px-3 py-1 text-xs border border-gray-200 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
                  <button disabled={memoPage >= memoTotalPages} onClick={() => setMemoPage(p => p + 1)} className="px-3 py-1 text-xs border border-gray-200 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── CREATE INVOICE MODAL ── */}
      {showCreateInvoice && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Create Invoice</h2>
              <button onClick={() => { setShowCreateInvoice(false); resetInvForm() }} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Invoice Type</label>
                  <select value={invForm.invoiceType} onChange={e => setInvForm({ ...invForm, invoiceType: e.target.value })} className="input w-full">
                    <option value="STANDARD">Standard</option>
                    <option value="PURCHASE">Purchase</option>
                    <option value="PROFORMA">Proforma</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Order ID (optional)</label>
                  <input value={invForm.orderId} onChange={e => setInvForm({ ...invForm, orderId: e.target.value })} className="input w-full" placeholder="order-123" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Customer Name</label>
                  <input value={invForm.customerName} onChange={e => setInvForm({ ...invForm, customerName: e.target.value })} className="input w-full" placeholder="John Doe" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Customer Email</label>
                  <input value={invForm.customerEmail} onChange={e => setInvForm({ ...invForm, customerEmail: e.target.value })} className="input w-full" placeholder="john@example.com" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Due Date</label>
                  <input type="date" value={invForm.dueDate} onChange={e => setInvForm({ ...invForm, dueDate: e.target.value })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Payment Terms</label>
                  <select value={invForm.paymentTerms} onChange={e => setInvForm({ ...invForm, paymentTerms: e.target.value })} className="input w-full">
                    <option value="NET_15">Net 15</option>
                    <option value="NET_30">Net 30</option>
                    <option value="NET_45">Net 45</option>
                    <option value="NET_60">Net 60</option>
                    <option value="DUE_ON_RECEIPT">Due on Receipt</option>
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                <textarea value={invForm.notes} onChange={e => setInvForm({ ...invForm, notes: e.target.value })} className="input w-full" rows={2} placeholder="Optional notes" />
              </div>

              {/* Invoice Items */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-sm font-medium text-gray-700">Invoice Items</label>
                  <button onClick={addItemRow} className="text-xs text-primary-600 hover:text-primary-800 font-medium">+ Add Item</button>
                </div>
                <div className="space-y-2">
                  {invForm.items.map((item, idx) => (
                    <div key={idx} className="flex items-start gap-2 p-2 bg-gray-50 rounded-lg">
                      <div className="flex-1 min-w-0">
                        <input value={item.sku} onChange={e => updateItem(idx, 'sku', e.target.value)} className="input w-full text-sm mb-1" placeholder="SKU" />
                      </div>
                      <div className="flex-[2] min-w-0">
                        <input value={item.productName} onChange={e => updateItem(idx, 'productName', e.target.value)} className="input w-full text-sm mb-1" placeholder="Description" />
                      </div>
                      <div className="w-16">
                        <input type="number" min={1} value={item.quantity} onChange={e => updateItem(idx, 'quantity', e.target.value)} className="input w-full text-sm mb-1 text-center" />
                      </div>
                      <div className="w-24">
                        <input type="number" min={0} step="0.01" value={item.unitPrice} onChange={e => updateItem(idx, 'unitPrice', e.target.value)} className="input w-full text-sm mb-1 text-right" />
                      </div>
                      <div className="w-20 pt-1 text-sm font-medium text-right">${getItemTotal(item).toFixed(2)}</div>
                      <button onClick={() => removeItemRow(idx)} className="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-red-500 mt-0.5" disabled={invForm.items.length <= 1}><X className="w-4 h-4" /></button>
                    </div>
                  ))}
                </div>
                <div className="text-right mt-2 text-sm font-semibold text-gray-900">
                  Total: ${getInvoiceFormTotal().toFixed(2)}
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => { setShowCreateInvoice(false); resetInvForm() }} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleCreateInvoice} disabled={saving} className="btn-primary text-sm">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                Create Invoice
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── RECORD PAYMENT MODAL ── */}
      {showRecordPayment && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Record Payment</h2>
              <button onClick={() => setShowRecordPayment(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Invoice</label>
                <select value={paymentForm.invoiceId} onChange={e => {
                  const inv = invoices.find(i => i.id === e.target.value)
                  setPaymentForm({ ...paymentForm, invoiceId: e.target.value, amount: inv?.amountDue || 0 })
                }} className="input w-full">
                  <option value="">Select invoice...</option>
                  {invoices.filter(i => i.status !== 'PAID' && i.status !== 'CANCELLED').map(inv => (
                    <option key={inv.id} value={inv.id}>{inv.invoiceNumber} - {inv.customerName} (${inv.amountDue.toFixed(2)})</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Amount</label>
                <input type="number" min={0} step="0.01" value={paymentForm.amount} onChange={e => setPaymentForm({ ...paymentForm, amount: Number(e.target.value) })} className="input w-full" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Payment Method</label>
                <select value={paymentForm.method} onChange={e => setPaymentForm({ ...paymentForm, method: e.target.value as Payment['method'] })} className="input w-full">
                  <option value="CREDIT_CARD">Credit Card</option>
                  <option value="DEBIT_CARD">Debit Card</option>
                  <option value="BANK_TRANSFER">Bank Transfer</option>
                  <option value="CHECK">Check</option>
                  <option value="CASH">Cash</option>
                  <option value="WIRE_TRANSFER">Wire Transfer</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Reference (optional)</label>
                <input value={paymentForm.reference} onChange={e => setPaymentForm({ ...paymentForm, reference: e.target.value })} className="input w-full" placeholder="Check #, Transaction ID..." />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                <textarea value={paymentForm.notes} onChange={e => setPaymentForm({ ...paymentForm, notes: e.target.value })} className="input w-full" rows={2} placeholder="Optional notes" />
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowRecordPayment(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleRecordPayment} disabled={saving} className="btn-primary text-sm">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                Record Payment
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── CREATE CREDIT MEMO MODAL ── */}
      {showCreateMemo && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Create Credit Memo</h2>
              <button onClick={() => setShowCreateMemo(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Memo Type</label>
                <select value={memoForm.memoType} onChange={e => setMemoForm({ ...memoForm, memoType: e.target.value as 'REFUND' | 'CREDIT' })} className="input w-full">
                  <option value="REFUND">Refund</option>
                  <option value="CREDIT">Credit</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Invoice</label>
                <select value={memoForm.invoiceId} onChange={e => {
                  const inv = invoices.find(i => i.id === e.target.value)
                  setMemoForm({ ...memoForm, invoiceId: e.target.value, invoiceNumber: inv?.invoiceNumber || '' })
                }} className="input w-full">
                  <option value="">Select invoice...</option>
                  {invoices.map(inv => (
                    <option key={inv.id} value={inv.id}>{inv.invoiceNumber} - {inv.customerName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Reason</label>
                <textarea value={memoForm.reason} onChange={e => setMemoForm({ ...memoForm, reason: e.target.value })} className="input w-full" rows={2} placeholder="Reason for credit/refund..." />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Amount</label>
                <input type="number" min={0} step="0.01" value={memoForm.amount} onChange={e => setMemoForm({ ...memoForm, amount: Number(e.target.value) })} className="input w-full" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                <textarea value={memoForm.notes} onChange={e => setMemoForm({ ...memoForm, notes: e.target.value })} className="input w-full" rows={2} placeholder="Optional notes" />
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowCreateMemo(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleCreateMemo} disabled={saving} className="btn-primary text-sm">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                Create Memo
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── REFUND MODAL ── */}
      {showRefund && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Process Refund</h2>
              <button onClick={() => setShowRefund(null)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Payment</label>
                <p className="text-sm text-gray-900">{showRefund.transactionId || showRefund.id.slice(0, 8)} &mdash; ${showRefund.amount.toFixed(2)}</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Refund Amount</label>
                <input type="number" min={0} step="0.01" max={showRefund.amount} value={refundForm.amount} onChange={e => setRefundForm({ ...refundForm, amount: Number(e.target.value) })} className="input w-full" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Reason</label>
                <textarea value={refundForm.reason} onChange={e => setRefundForm({ ...refundForm, reason: e.target.value })} className="input w-full" rows={2} placeholder="Reason for refund..." />
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowRefund(null)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleRefund} disabled={saving} className="btn-primary text-sm bg-red-600 hover:bg-red-700">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                Process Refund
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
