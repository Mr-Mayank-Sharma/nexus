import { useState, useEffect, useCallback, useRef } from 'react'
import { clsx } from 'clsx'
import {
  FileText, Upload, RefreshCw, Loader2, Search, ChevronDown, ChevronRight,
  CheckCircle, XCircle, AlertTriangle, Clock, Eye, Download, Trash2, Plus,
} from 'lucide-react'
import * as ediApi from '../api/edi'
import type { EdiDocument } from '../types'
import { useToast } from '../hooks/useToast'

const DOC_TYPE_BADGES: Record<string, string> = {
  '850': 'enterprise-badge-info',
  '856': 'enterprise-badge-warning',
  '810': 'enterprise-badge-ai',
}

const STATUS_BADGES: Record<string, string> = {
  PENDING: 'enterprise-badge-neutral',
  PARSED: 'enterprise-badge-success',
  VALIDATED: 'enterprise-badge-info',
  FAILED: 'enterprise-badge-error',
  ERROR: 'enterprise-badge-error',
}

const DOC_TYPE_LABELS: Record<string, string> = {
  '850': '850 - Purchase Order',
  '856': '856 - Ship Notice / ASN',
  '810': '810 - Invoice',
}

export default function EdiAutomationPage() {
  const [documents, setDocuments] = useState<EdiDocument[]>([])
  const [kpis, setKpis] = useState<Record<string, number> | null>(null)
  const [loading, setLoading] = useState(true)
  const [docTypeFilter, setDocTypeFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [uploadOpen, setUploadOpen] = useState(false)
  const [uploadType, setUploadType] = useState('850')
  const [uploadContent, setUploadContent] = useState('')
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [processing, setProcessing] = useState(false)
  const [selectedDoc, setSelectedDoc] = useState<EdiDocument | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const { addToast } = useToast()

  const fetchData = useCallback(async () => {
    try {
      const params: Record<string, any> = { page, size: 20 }
      if (docTypeFilter) params.docType = docTypeFilter
      if (statusFilter) params.status = statusFilter

      const [docRes, kpiRes] = await Promise.all([
        ediApi.getEdiDocuments(params),
        ediApi.getEdiKPIs(),
      ])
      setDocuments(docRes.data?.content || [])
      setTotalPages(docRes.data?.totalPages || 0)
      setKpis(kpiRes.data as Record<string, number>)
    } catch {
      if (!loading) addToast({ type: 'error', title: 'Failed to load EDI documents' })
    } finally {
      setLoading(false)
    }
  }, [page, docTypeFilter, statusFilter, loading, addToast])

  useEffect(() => { fetchData() }, [fetchData])

  const handleUpload = async () => {
    if (!uploadContent && !uploadFile) return
    setProcessing(true)
    try {
      if (uploadFile) {
        await ediApi.uploadEdiDocument(uploadFile, uploadType)
      } else {
        await ediApi.parseEdiContent(uploadContent, uploadType)
      }
      addToast({ type: 'success', title: 'EDI document processed' })
      setUploadOpen(false)
      setUploadContent('')
      setUploadFile(null)
      fetchData()
    } catch {
      addToast({ type: 'error', title: 'Failed to process EDI document' })
    } finally {
      setProcessing(false)
    }
  }

  const handleReprocess = async (id: string) => {
    try {
      await ediApi.reprocessEdiDocument(id)
      addToast({ type: 'success', title: 'Document reprocessed' })
      fetchData()
    } catch {
      addToast({ type: 'error', title: 'Failed to reprocess' })
    }
  }

  const toggleExpand = (id: string) => {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const filteredDocs = documents.filter(d =>
    !search || d.filename?.toLowerCase().includes(search.toLowerCase()) ||
    d.partnerName?.toLowerCase().includes(search.toLowerCase()) ||
    d.controlNumber?.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="enterprise-page-header">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-2xl bg-cyan-50 dark:bg-cyan-900/20 flex items-center justify-center">
            <FileText className="w-6 h-6 text-cyan-600" />
          </div>
          <div>
            <h1>EDI Automation</h1>
            <p>Process 850 Purchase Orders, 856 ASNs, and 810 Invoices</p>
          </div>
        </div>
        <button
          onClick={() => setUploadOpen(true)}
          className="enterprise-btn enterprise-btn-primary enterprise-btn-sm"
        >
          <Upload className="w-4 h-4" /> Upload EDI
        </button>
      </div>

      {/* KPI Cards */}
      <div className="enterprise-kpi-grid">
        {[
          { label: 'Total Documents', value: kpis ? (kpis.totalDocuments || 0) + (kpis.failed || 0) : 0, icon: FileText, color: 'bg-blue-50 text-blue-600' },
          { label: 'Pending', value: kpis?.pending ?? 0, icon: Clock, color: 'bg-gray-50 text-gray-600' },
          { label: 'Parsed / Validated', value: (kpis?.parsed ?? 0) + (kpis?.validated ?? 0), icon: CheckCircle, color: 'bg-emerald-50 text-emerald-600' },
          { label: 'Failed', value: kpis?.failed ?? 0, icon: XCircle, color: kpis?.failed ? 'bg-red-50 text-red-600' : 'bg-gray-50 text-gray-600' },
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
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search documents..."
              className="enterprise-input pl-9 w-64"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </div>
          <select className="enterprise-select w-40" value={docTypeFilter} onChange={e => { setDocTypeFilter(e.target.value); setPage(0) }}>
            <option value="">All Types</option>
            <option value="850">850 - Purchase Order</option>
            <option value="856">856 - Ship Notice</option>
            <option value="810">810 - Invoice</option>
          </select>
          <select className="enterprise-select w-40" value={statusFilter} onChange={e => { setStatusFilter(e.target.value); setPage(0) }}>
            <option value="">All Status</option>
            <option value="PENDING">Pending</option>
            <option value="PARSED">Parsed</option>
            <option value="VALIDATED">Validated</option>
            <option value="FAILED">Failed</option>
          </select>
        </div>
        <div className="enterprise-toolbar-right">
          <button onClick={() => fetchData()} className="enterprise-btn enterprise-btn-sm enterprise-btn-secondary">
            <RefreshCw className="w-4 h-4" /> Refresh
          </button>
        </div>
      </div>

      {/* Document List */}
      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <div key={i} className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
              <div className="enterprise-skeleton h-5 w-64 mb-3" />
              <div className="enterprise-skeleton h-4 w-48" />
            </div>
          ))}
        </div>
      ) : filteredDocs.length === 0 ? (
        <div className="enterprise-empty-state py-16">
          <FileText className="w-12 h-12 text-gray-300" />
          <h3>No EDI documents</h3>
          <p>Upload an 850, 856, or 810 file to get started</p>
        </div>
      ) : (
        <div className="space-y-2">
          {filteredDocs.map(doc => (
            <div key={doc.id} className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
              <button
                onClick={() => toggleExpand(doc.id)}
                className="w-full flex items-center gap-4 p-4 hover:bg-gray-50 dark:hover:bg-gray-700/30 transition-colors text-left"
              >
                <div className={clsx('w-10 h-10 rounded-xl flex items-center justify-center shrink-0',
                  doc.docType === '850' && 'bg-blue-50 text-blue-600',
                  doc.docType === '856' && 'bg-amber-50 text-amber-600',
                  doc.docType === '810' && 'bg-violet-50 text-violet-600')}>
                  <FileText className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2.5">
                    <span className={clsx('enterprise-badge', DOC_TYPE_BADGES[doc.docType])}>
                      {doc.docType}
                    </span>
                    <span className="font-medium text-gray-900 dark:text-gray-100 truncate">
                      {doc.filename || `EDI ${doc.docType}`}
                    </span>
                    <span className={clsx('enterprise-badge', STATUS_BADGES[doc.parsedStatus])}>
                      {doc.parsedStatus}
                    </span>
                    {doc.controlNumber && (
                      <span className="text-xs text-gray-400 font-mono">#{doc.controlNumber}</span>
                    )}
                  </div>
                  <div className="flex items-center gap-4 mt-1 text-xs text-gray-500 dark:text-gray-400">
                    {doc.partnerName && <span>{doc.partnerName}</span>}
                    {doc.partnerId && <span className="font-mono">{doc.partnerId}</span>}
                    <span>{new Date(doc.createdAt).toLocaleString()}</span>
                    {doc.orderId && <span className="font-mono">Order: {doc.orderId.slice(0, 8)}</span>}
                    {doc.errorMessage && <span className="text-red-500">{doc.errorMessage}</span>}
                  </div>
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    onClick={e => { e.stopPropagation(); setSelectedDoc(doc) }}
                    className="enterprise-btn enterprise-btn-xs enterprise-btn-ghost"
                  >
                    <Eye className="w-3.5 h-3.5" />
                  </button>
                  {doc.parsedStatus === 'FAILED' && (
                    <button
                      onClick={e => { e.stopPropagation(); handleReprocess(doc.id) }}
                      className="enterprise-btn enterprise-btn-xs enterprise-btn-secondary"
                    >
                      <RefreshCw className="w-3 h-3" /> Retry
                    </button>
                  )}
                  {expanded.has(doc.id) ? <ChevronDown className="w-4 h-4 text-gray-400" /> : <ChevronRight className="w-4 h-4 text-gray-400" />}
                </div>
              </button>

              {/* Expanded parsed data */}
              {expanded.has(doc.id) && doc.parsedData && (
                <div className="px-4 pb-4 border-t border-gray-100 dark:border-gray-700">
                  <div className="mt-3 space-y-2">
                    <h4 className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">Parsed Data</h4>
                    <div className="bg-gray-50 dark:bg-gray-700/30 rounded-xl p-4">
                      <pre className="text-xs text-gray-700 dark:text-gray-300 overflow-x-auto max-h-80 whitespace-pre-wrap font-mono">
                        {JSON.stringify(doc.parsedData, null, 2)}
                      </pre>
                    </div>
                    {doc.validationErrors && doc.validationErrors.length > 0 && (
                      <div className="bg-red-50 dark:bg-red-900/10 rounded-xl p-3">
                        <p className="text-xs font-semibold text-red-600 dark:text-red-400 mb-1">Validation Errors</p>
                        <ul className="text-xs text-red-500 space-y-0.5">
                          {doc.validationErrors.map((err, i) => <li key={i}>{err}</li>)}
                        </ul>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between pt-2">
              <p className="text-xs text-gray-400">
                Page {page + 1} of {totalPages}
              </p>
              <div className="flex items-center gap-1">
                <button
                  disabled={page === 0}
                  onClick={() => setPage(p => p - 1)}
                  className="enterprise-btn enterprise-btn-xs enterprise-btn-secondary disabled:opacity-30"
                >
                  Previous
                </button>
                <button
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage(p => p + 1)}
                  className="enterprise-btn enterprise-btn-xs enterprise-btn-secondary disabled:opacity-30"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Upload Modal */}
      {uploadOpen && (
        <div className="enterprise-modal-overlay" onClick={() => setUploadOpen(false)}>
          <div className="enterprise-modal max-w-lg" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100">Upload EDI Document</h2>
              <button onClick={() => setUploadOpen(false)} className="p-1 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100">
                <XCircle className="w-5 h-5" />
              </button>
            </div>
            <div className="enterprise-modal-body space-y-5">
              <div className="enterprise-form-group">
                <label>Document Type</label>
                <select className="enterprise-select" value={uploadType} onChange={e => setUploadType(e.target.value)}>
                  <option value="850">850 - Purchase Order</option>
                  <option value="856">856 - Ship Notice / ASN</option>
                  <option value="810">810 - Invoice</option>
                </select>
              </div>
              <div className="enterprise-form-group">
                <label>Upload File (optional)</label>
                <div
                  onClick={() => fileRef.current?.click()}
                  className="border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-xl p-6 text-center cursor-pointer hover:border-primary-400 dark:hover:border-primary-500 transition-colors"
                >
                  {uploadFile ? (
                    <div className="space-y-1">
                      <FileText className="w-8 h-8 mx-auto text-primary-500" />
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{uploadFile.name}</p>
                      <p className="text-xs text-gray-400">{(uploadFile.size / 1024).toFixed(1)} KB</p>
                    </div>
                  ) : (
                    <div className="space-y-1">
                      <Upload className="w-8 h-8 mx-auto text-gray-400" />
                      <p className="text-sm text-gray-500">Drop a file or click to browse</p>
                      <p className="text-xs text-gray-400">.edi, .txt, or .x12 files</p>
                    </div>
                  )}
                  <input
                    ref={fileRef}
                    type="file"
                    accept=".edi,.txt,.x12,.850,.856,.810"
                    className="hidden"
                    onChange={e => setUploadFile(e.target.files?.[0] || null)}
                  />
                </div>
              </div>
              <div className="enterprise-form-group">
                <label>Paste Content (alternative)</label>
                <textarea
                  className="enterprise-textarea"
                  rows={6}
                  placeholder="ISA*00*          *00*         ... paste X12 EDI content here"
                  value={uploadContent}
                  onChange={e => setUploadContent(e.target.value)}
                />
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button onClick={() => setUploadOpen(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <button
                onClick={handleUpload}
                disabled={processing || (!uploadContent && !uploadFile)}
                className="enterprise-btn enterprise-btn-primary disabled:opacity-50"
              >
                {processing && <Loader2 className="w-4 h-4 animate-spin" />}
                Process EDI Document
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Document Detail Modal */}
      {selectedDoc && (
        <div className="enterprise-modal-overlay" onClick={() => setSelectedDoc(null)}>
          <div className="enterprise-modal max-w-2xl" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100">EDI Document Details</h2>
              <button onClick={() => setSelectedDoc(null)} className="p-1 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100">
                <XCircle className="w-5 h-5" />
              </button>
            </div>
            <div className="enterprise-modal-body space-y-5 max-h-[60vh] overflow-y-auto">
              <div className="grid grid-cols-2 gap-4">
                {[
                  { label: 'Type', value: `${selectedDoc.docType} - ${DOC_TYPE_LABELS[selectedDoc.docType]?.split(' - ')[1] || ''}` },
                  { label: 'Status', value: selectedDoc.parsedStatus },
                  { label: 'Filename', value: selectedDoc.filename || 'N/A' },
                  { label: 'Partner', value: selectedDoc.partnerName || 'N/A' },
                  { label: 'Control Number', value: selectedDoc.controlNumber || 'N/A' },
                  { label: 'Interchange Control', value: selectedDoc.interchangeControlNumber || 'N/A' },
                  { label: 'Processed At', value: selectedDoc.processedAt ? new Date(selectedDoc.processedAt).toLocaleString() : 'N/A' },
                  { label: 'Order ID', value: selectedDoc.orderId ? selectedDoc.orderId.slice(0, 8) : 'N/A' },
                ].map(({ label, value }) => (
                  <div key={label} className="bg-gray-50 dark:bg-gray-700/30 rounded-xl p-3">
                    <p className="text-xs text-gray-400 mb-0.5">{label}</p>
                    <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{value}</p>
                  </div>
                ))}
              </div>

              {selectedDoc.validationErrors && selectedDoc.validationErrors.length > 0 && (
                <div>
                  <h4 className="text-xs font-semibold text-red-600 dark:text-red-400 uppercase tracking-wider mb-2">Validation Errors</h4>
                  <div className="bg-red-50 dark:bg-red-900/10 rounded-xl p-3 space-y-1">
                    {selectedDoc.validationErrors.map((err, i) => (
                      <p key={i} className="text-xs text-red-500">{err}</p>
                    ))}
                  </div>
                </div>
              )}

              {selectedDoc.parsedData && (
                <div>
                  <h4 className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-2">Parsed Data</h4>
                  <div className="bg-gray-50 dark:bg-gray-700/30 rounded-xl p-4">
                    <pre className="text-xs text-gray-700 dark:text-gray-300 overflow-x-auto whitespace-pre-wrap font-mono max-h-60">
                      {JSON.stringify(selectedDoc.parsedData, null, 2)}
                    </pre>
                  </div>
                </div>
              )}

              {selectedDoc.errorMessage && (
                <div>
                  <h4 className="text-xs font-semibold text-red-600 dark:text-red-400 uppercase tracking-wider mb-2">Error</h4>
                  <div className="bg-red-50 dark:bg-red-900/10 rounded-xl p-3">
                    <p className="text-xs text-red-500">{selectedDoc.errorMessage}</p>
                  </div>
                </div>
              )}
            </div>
            <div className="enterprise-modal-footer">
              {selectedDoc.parsedStatus === 'FAILED' && (
                <button onClick={() => { handleReprocess(selectedDoc.id); setSelectedDoc(null) }} className="enterprise-btn enterprise-btn-secondary">
                  <RefreshCw className="w-4 h-4" /> Retry Processing
                </button>
              )}
              <button onClick={() => setSelectedDoc(null)} className="enterprise-btn enterprise-btn-primary">Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
