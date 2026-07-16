import { useState, useEffect, useCallback, useRef } from 'react'
import { clsx } from 'clsx'
import {
  Mail, Upload, RefreshCw, Loader2, Search, CheckCircle, XCircle,
  AlertTriangle, Clock, Eye, FileText, User, DollarSign, Package,
  ChevronDown, ChevronRight, Star,
} from 'lucide-react'
import * as emailParserApi from '../api/emailParser'
import type { EmailParsedOrder } from '../types'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import { useToast } from '../hooks/useToast'

const STATUS_BADGES: Record<string, string> = {
  NEW: 'enterprise-badge-neutral',
  PARSED: 'enterprise-badge-success',
  PENDING_REVIEW: 'enterprise-badge-warning',
  APPROVED: 'enterprise-badge-info',
  REJECTED: 'enterprise-badge-error',
  DUPLICATE: 'enterprise-badge-neutral',
  FAILED: 'enterprise-badge-error',
}

const CONFIDENCE_COLORS: Record<string, string> = {
  high: 'text-emerald-600 bg-emerald-50 dark:bg-emerald-900/20 dark:text-emerald-400',
  medium: 'text-amber-600 bg-amber-50 dark:bg-amber-900/20 dark:text-amber-400',
  low: 'text-red-600 bg-red-50 dark:bg-red-900/20 dark:text-red-400',
}

export default function EmailOrderParsingPage() {
  const [orders, setOrders] = useState<EmailParsedOrder[]>([])
  const [kpis, setKpis] = useState<Record<string, number> | null>(null)
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [parseOpen, setParseOpen] = useState(false)
  const [parseTab, setParseTab] = useState<'text' | 'csv'>('text')
  const [textForm, setTextForm] = useState({ subject: '', from: '', body: '' })
  const [csvFile, setCsvFile] = useState<File | null>(null)
  const [processing, setProcessing] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)
  const { addToast } = useToast()

  const fetchData = useCallback(async () => {
    try {
      const params: Record<string, any> = { page, size: 20 }
      if (statusFilter) params.status = statusFilter

      const [ordRes, kpiRes] = await Promise.all([
        emailParserApi.getParsedOrders(params),
        emailParserApi.getEmailParserKPIs(),
      ])
      setOrders(ordRes.data?.content || [])
      setTotalPages(ordRes.data?.totalPages || 0)
      setKpis(kpiRes.data as Record<string, number>)
    } catch {
      addToast({ type: 'error', title: 'Failed to load parsed orders' })
    } finally {
      setLoading(false)
    }
  }, [page, statusFilter, loading, addToast])

  useEffect(() => { fetchData() }, [fetchData])

  const handleParse = async () => {
    setProcessing(true)
    try {
      if (parseTab === 'csv' && csvFile) {
        await emailParserApi.parseCsvAttachment(csvFile, csvFile.name, csvFile.name.replace(/\.[^.]+$/, '') + '@imported.com')
      } else {
        await emailParserApi.parseEmailContent(textForm)
      }
      addToast({ type: 'success', title: 'Email content parsed successfully' })
      setParseOpen(false)
      setTextForm({ subject: '', from: '', body: '' })
      setCsvFile(null)
      fetchData()
    } catch {
      addToast({ type: 'error', title: 'Failed to parse content' })
    } finally {
      setProcessing(false)
    }
  }

  const handleApprove = async (id: string) => {
    try {
      await emailParserApi.approveParsedOrder(id)
      addToast({ type: 'success', title: 'Order approved and created' })
      fetchData()
    } catch { addToast({ type: 'error', title: 'Failed to approve' }) }
  }

  const handleReject = async (id: string) => {
    const reason = prompt('Reason for rejection:')
    if (!reason) return
    try {
      await emailParserApi.rejectParsedOrder(id, reason)
      addToast({ type: 'success', title: 'Order rejected' })
      fetchData()
    } catch { addToast({ type: 'error', title: 'Failed to reject' }) }
  }

  const toggleExpand = (id: string) => {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const getConfidenceClass = (score: number) => {
    if (score >= 0.8) return CONFIDENCE_COLORS.high
    if (score >= 0.5) return CONFIDENCE_COLORS.medium
    return CONFIDENCE_COLORS.low
  }

  const filteredOrders = orders.filter(o =>
    !search || o.emailSubject?.toLowerCase().includes(search.toLowerCase()) ||
    o.customerName?.toLowerCase().includes(search.toLowerCase()) ||
    o.customerEmail?.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="enterprise-page-header">
        <div className="flex items-center gap-4">
          <div>
            <h1 className="flex items-center gap-2.5"><Mail className="w-6 h-6 text-rose-600" />Email Order Parsing</h1>
            <p>Parse orders from email content, PDFs, and CSV attachments</p>
          </div>
        </div>
        <PermissionGate resource="integrations" action="create">
          <button
            onClick={() => setParseOpen(true)}
            className="enterprise-btn enterprise-btn-primary enterprise-btn-sm"
          >
            <Upload className="w-4 h-4" /> Parse Email
          </button>
        </PermissionGate>
      </div>

      {/* KPI Cards */}
      <div className="enterprise-kpi-grid">
        {[
          { label: 'Total Parsed', value: kpis ? (kpis.total || 0) : 0, icon: Mail, color: 'bg-rose-50 text-rose-600 dark:bg-rose-900/20 dark:text-rose-400' },
          { label: 'Pending Review', value: kpis?.pendingReview ?? 0, icon: AlertTriangle, color: 'bg-amber-50 text-amber-600 dark:bg-amber-900/20 dark:text-amber-400' },
          { label: 'Approved', value: kpis?.approved ?? 0, icon: CheckCircle, color: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-900/20 dark:text-emerald-400' },
          { label: 'Rejected', value: kpis?.rejected ?? 0, icon: XCircle, color: kpis?.rejected ? 'bg-red-50 text-red-600 dark:bg-red-900/20 dark:text-red-400' : 'bg-gray-50 text-gray-600' },
        ].map(kpi => (
          <div key={kpi.label} className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-sm text-gray-500">{kpi.label}</span>
              <div className={clsx('w-9 h-9 rounded-xl flex items-center justify-center', kpi.color)}>
                <kpi.icon className="w-4.5 h-4.5" />
              </div>
            </div>
            <div className="text-2xl font-bold text-gray-900 dark:text-gray-100">{kpi.value}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div className="enterprise-toolbar">
        <div className="enterprise-toolbar-left">
          <Autocomplete value={search} onChange={setSearch} placeholder="Search orders..." minChars={0} />
          <select className="enterprise-select w-40" value={statusFilter} onChange={e => { setStatusFilter(e.target.value); setPage(0) }}>
            <option value="">All Status</option>
            <option value="NEW">New</option>
            <option value="PARSED">Parsed</option>
            <option value="PENDING_REVIEW">Pending Review</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
            <option value="FAILED">Failed</option>
          </select>
        </div>
        <div className="enterprise-toolbar-right">
          <button onClick={() => fetchData()} className="enterprise-btn enterprise-btn-sm enterprise-btn-secondary">
            <RefreshCw className="w-4 h-4" /> Refresh
          </button>
        </div>
      </div>

      {/* Order List */}
      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <div key={i} className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
              <div className="enterprise-skeleton h-5 w-64 mb-3" />
              <div className="enterprise-skeleton h-4 w-48 mb-2" />
              <div className="enterprise-skeleton h-4 w-32" />
            </div>
          ))}
        </div>
      ) : filteredOrders.length === 0 ? (
        <div className="enterprise-empty-state py-16">
          <Mail className="w-12 h-12 text-gray-300" />
          <h3>No parsed orders</h3>
          <p>Parse an email or upload a CSV to get started</p>
        </div>
      ) : (
        <div className="space-y-2">
          {filteredOrders.map(order => (
            <div key={order.id} className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
              <button
                onClick={() => toggleExpand(order.id)}
                className="w-full flex items-center gap-4 p-4 hover:bg-gray-50 dark:hover:bg-gray-700/30 transition-colors text-left"
              >
                <div className={clsx('w-10 h-10 rounded-xl flex items-center justify-center shrink-0',
                  order.status === 'APPROVED' && 'bg-emerald-50 text-emerald-600',
                  order.status === 'REJECTED' && 'bg-red-50 text-red-600',
                  ['NEW', 'PARSED'].includes(order.status) && 'bg-blue-50 text-blue-600',
                  order.status === 'PENDING_REVIEW' && 'bg-amber-50 text-amber-600',
                )}>
                  <Mail className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2.5">
                    <span className={clsx('enterprise-badge', STATUS_BADGES[order.status])}>{order.status}</span>
                    <span className="font-medium text-gray-900 dark:text-gray-100 truncate">
                      {order.emailSubject || `Order from ${order.customerName || 'Unknown'}`}
                    </span>
                    <span className={clsx('inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md text-[10px] font-bold', getConfidenceClass(order.confidenceScore))}>
                      <Star className="w-2.5 h-2.5" />
                      {(order.confidenceScore * 100).toFixed(0)}%
                    </span>
                  </div>
                  <div className="flex items-center gap-4 mt-1 text-xs text-gray-500 flex-wrap">
                    {order.customerName && (
                      <span className="flex items-center gap-1"><User className="w-3 h-3" />{order.customerName}</span>
                    )}
                    {order.customerEmail && <span>{order.customerEmail}</span>}
                    {order.itemCount > 0 && (
                      <span className="flex items-center gap-1"><Package className="w-3 h-3" />{order.itemCount} item{order.itemCount > 1 ? 's' : ''}</span>
                    )}
                    {order.orderTotal > 0 && (
                      <span className="flex items-center gap-1"><DollarSign className="w-3 h-3" />${order.orderTotal.toFixed(2)}</span>
                    )}
                    <span>{new Date(order.createdAt).toLocaleString()}</span>
                  </div>
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  {order.status === 'PARSED' && (
                    <>
                      <PermissionGate resource="integrations" action="edit">
                        <button onClick={e => { e.stopPropagation(); handleApprove(order.id) }}
                          className="enterprise-btn enterprise-btn-xs bg-emerald-50 text-emerald-600 border border-emerald-200 hover:bg-emerald-100">
                          <CheckCircle className="w-3 h-3" /> Approve
                        </button>
                      </PermissionGate>
                      <PermissionGate resource="integrations" action="edit">
                        <button onClick={e => { e.stopPropagation(); handleReject(order.id) }}
                          className="enterprise-btn enterprise-btn-xs bg-red-50 text-red-600 border border-red-200 hover:bg-red-100">
                          <XCircle className="w-3 h-3" /> Reject
                        </button>
                      </PermissionGate>
                    </>
                  )}
                  {expanded.has(order.id) ? <ChevronDown className="w-4 h-4 text-gray-400" /> : <ChevronRight className="w-4 h-4 text-gray-400" />}
                </div>
              </button>

              {expanded.has(order.id) && order.parsedData && (
                <div className="px-4 pb-4 border-t border-gray-100 dark:border-gray-700">
                  <div className="mt-3 space-y-2">
                    <div className="grid grid-cols-2 gap-3">
                      {order.customerName && (
                        <div className="bg-gray-50 dark:bg-gray-700/30 rounded-xl p-3">
                          <p className="text-xs text-gray-400">Customer</p>
                          <p className="text-sm font-medium text-gray-900">{order.customerName}</p>
                          {order.customerEmail && <p className="text-xs text-gray-500">{order.customerEmail}</p>}
                        </div>
                      )}
                      <div className="bg-gray-50 dark:bg-gray-700/30 rounded-xl p-3">
                        <p className="text-xs text-gray-400">Source</p>
                        <p className="text-sm font-medium text-gray-900">{order.attachmentType || 'Email'}</p>
                        <p className="text-xs text-gray-500">{order.emailFrom || 'Unknown'}</p>
                      </div>
                    </div>
                    <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Parsed Data</h4>
                    <div className="bg-gray-50 dark:bg-gray-700/30 rounded-xl p-4">
                      <pre className="text-xs text-gray-700 dark:text-gray-300 overflow-x-auto max-h-60 whitespace-pre-wrap font-mono">
                        {JSON.stringify(order.parsedData, null, 2)}
                      </pre>
                    </div>
                    {order.orderId && (
                      <p className="text-xs text-primary-600">Order created: {order.orderId}</p>
                    )}
                    {order.rejectionReason && (
                      <div className="bg-red-50 dark:bg-red-900/10 rounded-xl p-3">
                        <p className="text-xs text-red-500">Rejection: {order.rejectionReason}</p>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))}

          {totalPages > 1 && (
            <div className="flex items-center justify-between pt-2">
              <p className="text-xs text-gray-400">Page {page + 1} of {totalPages}</p>
              <div className="flex items-center gap-1">
                <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                  className="enterprise-btn enterprise-btn-xs enterprise-btn-secondary disabled:opacity-30">Previous</button>
                <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}
                  className="enterprise-btn enterprise-btn-xs enterprise-btn-secondary disabled:opacity-30">Next</button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Parse Modal */}
      {parseOpen && (
        <div className="enterprise-modal-overlay" onClick={() => setParseOpen(false)}>
          <div className="enterprise-modal max-w-lg" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100">Parse Email Content</h2>
              <button onClick={() => setParseOpen(false)} className="p-1 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100">
                <XCircle className="w-5 h-5" />
              </button>
            </div>
            <div className="enterprise-modal-body space-y-5">
              <div className="flex gap-2">
                {(['text', 'csv'] as const).map(t => (
                  <button key={t} onClick={() => setParseTab(t)}
                    className={clsx('px-4 py-2 rounded-lg text-sm font-medium transition-colors',
                      parseTab === t ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-300' : 'text-gray-500 hover:text-gray-700 hover:bg-gray-100')}>
                    {t === 'text' ? 'Email Text' : 'CSV File'}
                  </button>
                ))}
              </div>

              {parseTab === 'text' ? (
                <>
                  <div className="enterprise-form-group">
                    <label>Subject</label>
                    <input type="text" className="enterprise-input" placeholder="Order Confirmation #12345"
                      value={textForm.subject} onChange={e => setTextForm(f => ({ ...f, subject: e.target.value }))} />
                  </div>
                  <div className="enterprise-form-group">
                    <label>From</label>
                    <input type="text" className="enterprise-input" placeholder="customer@example.com"
                      value={textForm.from} onChange={e => setTextForm(f => ({ ...f, from: e.target.value }))} />
                  </div>
                  <div className="enterprise-form-group">
                    <label>Email Body</label>
                    <textarea className="enterprise-textarea" rows={8}
                      placeholder="Paste email content with order details here..."
                      value={textForm.body} onChange={e => setTextForm(f => ({ ...f, body: e.target.value }))} />
                  </div>
                </>
              ) : (
                <div className="enterprise-form-group">
                  <label>CSV File</label>
                  <div onClick={() => fileRef.current?.click()}
                    className="border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-xl p-6 text-center cursor-pointer hover:border-primary-400 transition-colors">
                    {csvFile ? (
                      <div className="space-y-1">
                        <FileText className="w-8 h-8 mx-auto text-primary-500" />
                        <p className="text-sm font-medium text-gray-900">{csvFile.name}</p>
                        <p className="text-xs text-gray-400">{(csvFile.size / 1024).toFixed(1)} KB</p>
                      </div>
                    ) : (
                      <div className="space-y-1">
                        <Upload className="w-8 h-8 mx-auto text-gray-400" />
                        <p className="text-sm text-gray-500">Drop a CSV file or click to browse</p>
                        <p className="text-xs text-gray-400">.csv files with order data</p>
                      </div>
                    )}
                    <input ref={fileRef} type="file" accept=".csv" className="hidden"
                      onChange={e => setCsvFile(e.target.files?.[0] || null)} />
                  </div>
                </div>
              )}
            </div>
            <div className="enterprise-modal-footer">
              <button onClick={() => setParseOpen(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="integrations" action="create">
                <button onClick={handleParse} disabled={processing || (parseTab === 'text' ? !textForm.body : !csvFile)}
                  className="enterprise-btn enterprise-btn-primary disabled:opacity-50">
                  {processing && <Loader2 className="w-4 h-4 animate-spin" />}
                  {parseTab === 'text' ? 'Parse Email' : 'Parse CSV'}
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
