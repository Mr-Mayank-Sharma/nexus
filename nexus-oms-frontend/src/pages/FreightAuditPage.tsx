import { useState, useEffect, useCallback, Fragment } from 'react'
import { clsx } from 'clsx'
import { Receipt, Plus, X, Loader2, Check, ChevronDown, ChevronUp, AlertTriangle, FileText, Search, Ban, DollarSign } from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import { useToast } from '../hooks/useToast'
import * as freightAuditApi from '../api/freightAudit'

type Tab = 'invoices' | 'audit-log' | 'stats'

interface InvoiceLine {
  lineNumber: number
  shipmentId: string
  trackingNumber: string
  serviceLevel: string
  weightKg: number
  billedAmount: number
  expectedAmount: number
  varianceAmount: number
}

interface FreightInvoice {
  id: string
  invoiceNumber: string
  carrierId: string
  warehouseId: string
  invoiceDate: string
  totalAmount: number
  auditStatus: string
  approvedBy?: string
  approvedAt?: string
  paidAt?: string
  disputeReason?: string
  notes?: string
  lines?: InvoiceLine[]
}

interface AuditLog {
  id: string
  invoiceId: string
  action: string
  performedBy: string
  performedAt: string
  details?: string
}

const auditStatusColors: Record<string, string> = {
  PENDING: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]',
  MATCHED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]',
  DISPUTED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)]',
  APPROVED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]',
  PAID: 'bg-emerald-100 text-emerald-700',
  DISCREPANCY: 'bg-orange-100 text-orange-700',
}

function generateMockInvoices(): FreightInvoice[] {
  const now = new Date().toISOString()
  return [
    {
      id: 'fi-1', invoiceNumber: 'INV-2026-001', carrierId: 'carrier-fedex', warehouseId: 'wh-main',
      invoiceDate: now, totalAmount: 12450.75, auditStatus: 'MATCHED',
      lines: [
        { lineNumber: 1, shipmentId: 'shp-001', trackingNumber: 'FX-99881', serviceLevel: 'Ground', weightKg: 24.5, billedAmount: 18.50, expectedAmount: 18.50, varianceAmount: 0 },
        { lineNumber: 2, shipmentId: 'shp-002', trackingNumber: 'FX-99882', serviceLevel: 'Express', weightKg: 8.2, billedAmount: 42.00, expectedAmount: 40.00, varianceAmount: 2.00 },
      ],
    },
    {
      id: 'fi-2', invoiceNumber: 'INV-2026-002', carrierId: 'carrier-ups', warehouseId: 'wh-main',
      invoiceDate: now, totalAmount: 8320.00, auditStatus: 'PENDING',
      lines: [
        { lineNumber: 1, shipmentId: 'shp-003', trackingNumber: 'UPS-55412', serviceLevel: 'Next Day Air', weightKg: 15.0, billedAmount: 65.00, expectedAmount: 58.50, varianceAmount: 6.50 },
        { lineNumber: 2, shipmentId: 'shp-004', trackingNumber: 'UPS-55413', serviceLevel: 'Ground', weightKg: 40.0, billedAmount: 28.00, expectedAmount: 28.00, varianceAmount: 0 },
      ],
    },
    {
      id: 'fi-3', invoiceNumber: 'INV-2026-003', carrierId: 'carrier-fedex', warehouseId: 'wh-main',
      invoiceDate: now, totalAmount: 4100.50, auditStatus: 'DISPUTED',
      disputeReason: 'Carrier charged incorrect zone',
      lines: [
        { lineNumber: 1, shipmentId: 'shp-005', trackingNumber: 'FX-77221', serviceLevel: 'Freight', weightKg: 120.0, billedAmount: 285.00, expectedAmount: 210.00, varianceAmount: 75.00 },
      ],
    },
    {
      id: 'fi-4', invoiceNumber: 'INV-2026-004', carrierId: 'carrier-dhl', warehouseId: 'wh-main',
      invoiceDate: now, totalAmount: 3200.00, auditStatus: 'PAID', approvedBy: 'admin', approvedAt: now, paidAt: now,
      lines: [
        { lineNumber: 1, shipmentId: 'shp-006', trackingNumber: 'DHL-11001', serviceLevel: 'International', weightKg: 55.0, billedAmount: 120.00, expectedAmount: 120.00, varianceAmount: 0 },
      ],
    },
  ]
}

function generateMockAuditLogs(): AuditLog[] {
  const now = new Date().toISOString()
  return [
    { id: 'al-1', invoiceId: 'fi-1', action: 'CREATED', performedBy: 'system', performedAt: now },
    { id: 'al-2', invoiceId: 'fi-1', action: 'AUDIT_MATCH', performedBy: 'system', performedAt: now, details: 'All lines matched within tolerance' },
    { id: 'al-3', invoiceId: 'fi-2', action: 'CREATED', performedBy: 'system', performedAt: now },
    { id: 'al-4', invoiceId: 'fi-3', action: 'CREATED', performedBy: 'system', performedAt: now },
    { id: 'al-5', invoiceId: 'fi-3', action: 'AUDIT_MATCH', performedBy: 'system', performedAt: now, details: 'Discrepancy detected: $75.00 variance on line 1' },
    { id: 'al-6', invoiceId: 'fi-3', action: 'DISPUTED', performedBy: 'admin', performedAt: now, details: 'Carrier charged incorrect zone rate' },
    { id: 'al-7', invoiceId: 'fi-4', action: 'CREATED', performedBy: 'system', performedAt: now },
    { id: 'al-8', invoiceId: 'fi-4', action: 'APPROVED', performedBy: 'admin', performedAt: now },
    { id: 'al-9', invoiceId: 'fi-4', action: 'PAID', performedBy: 'system', performedAt: now },
  ]
}

export default function FreightAuditPage() {
  const { addToast } = useToast()
  const [loading, setLoading] = useState(true)
  const [selectedWarehouse, setSelectedWarehouse] = useState('wh-main')
  const [activeTab, setActiveTab] = useState<Tab>('invoices')

  const [invoices, setInvoices] = useState<FreightInvoice[]>([])
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([])
  const [stats, setStats] = useState<Record<string, unknown> | null>(null)

  const [expandedInvoiceId, setExpandedInvoiceId] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [filterStatus, setFilterStatus] = useState('ALL')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [createForm, setCreateForm] = useState({ invoiceNumber: '', carrierId: '', totalAmount: '', notes: '' })
  const [saving, setSaving] = useState(false)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [invoicesRes, logsRes, statsRes] = await Promise.allSettled([
        freightAuditApi.getInvoices(selectedWarehouse),
        freightAuditApi.getAuditLog(selectedWarehouse),
        freightAuditApi.getStats(selectedWarehouse),
      ])

      const invoicesData = invoicesRes.status === 'fulfilled' ? (invoicesRes.value.data as FreightInvoice[]) : null
      const logsData = logsRes.status === 'fulfilled' ? (logsRes.value.data as AuditLog[]) : null
      const statsData = statsRes.status === 'fulfilled' ? statsRes.value.data : null

      setInvoices(invoicesData && invoicesData.length > 0 ? invoicesData : generateMockInvoices())
      setAuditLogs(logsData && logsData.length > 0 ? logsData : generateMockAuditLogs())
      setStats(statsData || {
        totalInvoices: 4, pendingAudit: 1, disputed: 1, matched: 1, paid: 1,
        totalBilled: 28071.25, totalVariance: 83.50, matchRate: 75,
      })
    } catch {
      setInvoices(generateMockInvoices())
      setAuditLogs(generateMockAuditLogs())
      setStats({ totalInvoices: 4, pendingAudit: 1, disputed: 1, matched: 1, paid: 1, totalBilled: 28071.25, totalVariance: 83.50, matchRate: 75 })
    } finally {
      setLoading(false)
    }
  }, [selectedWarehouse])

  useEffect(() => { loadData() }, [loadData])

  async function handleCreateInvoice() {
    if (!createForm.invoiceNumber.trim() || !createForm.carrierId.trim()) {
      addToast({ type: 'warning', title: 'Invoice number and carrier are required' })
      return
    }
    setSaving(true)
    try {
      await freightAuditApi.createInvoice({
        invoiceNumber: createForm.invoiceNumber,
        carrierId: createForm.carrierId,
        warehouseId: selectedWarehouse,
        totalAmount: Number(createForm.totalAmount),
        notes: createForm.notes,
      })
      addToast({ type: 'success', title: 'Invoice created' })
      setShowCreateModal(false)
      setCreateForm({ invoiceNumber: '', carrierId: '', totalAmount: '', notes: '' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to create invoice' })
    } finally {
      setSaving(false)
    }
  }

  async function handleApprove(invoiceId: string) {
    try {
      await freightAuditApi.approveInvoice(invoiceId, 'admin')
      addToast({ type: 'success', title: 'Invoice approved' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to approve invoice' })
    }
  }

  async function handleDispute(invoiceId: string) {
    const reason = window.prompt('Dispute reason:')
    if (!reason) return
    try {
      await freightAuditApi.disputeInvoice(invoiceId, reason)
      addToast({ type: 'success', title: 'Invoice disputed' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to dispute invoice' })
    }
  }

  async function handlePay(invoiceId: string) {
    try {
      await freightAuditApi.payInvoice(invoiceId)
      addToast({ type: 'success', title: 'Invoice marked as paid' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to mark as paid' })
    }
  }

  const filteredInvoices = invoices.filter((inv) => {
    const matchesSearch = !searchQuery || inv.invoiceNumber.toLowerCase().includes(searchQuery.toLowerCase()) || inv.carrierId.toLowerCase().includes(searchQuery.toLowerCase())
    const matchesStatus = filterStatus === 'ALL' || inv.auditStatus === filterStatus
    return matchesSearch && matchesStatus
  })

  const pendingCount = invoices.filter((i) => i.auditStatus === 'PENDING').length
  const disputedCount = invoices.filter((i) => i.auditStatus === 'DISPUTED').length
  const matchedCount = invoices.filter((i) => i.auditStatus === 'MATCHED').length
  const paidCount = invoices.filter((i) => i.auditStatus === 'PAID').length
  const totalBilled = invoices.reduce((sum, i) => sum + i.totalAmount, 0)
  const totalVariance = invoices.reduce((sum, i) =>
    sum + (i.lines?.reduce((ls, l) => ls + Math.abs(l.varianceAmount), 0) || 0), 0)

  const tabs: { key: Tab; label: string; count?: number }[] = [
    { key: 'invoices', label: 'Invoices', count: invoices.length },
    { key: 'audit-log', label: 'Audit Log', count: auditLogs.length },
    { key: 'stats', label: 'Stats' },
  ]

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="w-8 h-8 animate-spin text-[var(--nexus-primary-500)]" />
      </div>
    )
  }

  return (
    <div className="enterprise-page space-y-6">
      <EnterpriseBreadcrumbs crumbs={[
        { label: 'Home', path: '/' },
        { label: 'Warehouse', path: '/warehouse' },
        { label: 'Freight Audit & Payment' },
      ]} />

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Receipt className="w-7 h-7 text-[var(--nexus-ai-500)]" /> Freight Audit & Payment
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Invoice audit, discrepancy resolution, and payment tracking
          </p>
        </div>
      </div>

      <div className="enterprise-kpi-grid grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <EnterpriseKPICard title="Total Invoices" value={invoices.length} icon={<FileText className="w-5 h-5" />} color="primary" />
        <EnterpriseKPICard title="Pending Audit" value={pendingCount} icon={<AlertTriangle className="w-5 h-5" />} color={pendingCount > 0 ? 'warning' : 'success'} />
        <EnterpriseKPICard title="Disputed" value={disputedCount} icon={<Ban className="w-5 h-5" />} color={disputedCount > 0 ? 'error' : 'success'} />
        <EnterpriseKPICard title="Total Billed" value={`$${totalBilled.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`} icon={<DollarSign className="w-5 h-5" />} color="primary" />
      </div>

      <div className="enterprise-toolbar flex flex-wrap items-center gap-3 p-4 bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)]">
        <div className="flex items-center gap-2">
          <label className="text-xs font-medium text-[var(--text-secondary)]">Warehouse</label>
          <select
            value={selectedWarehouse}
            onChange={(e) => setSelectedWarehouse(e.target.value)}
            className="enterprise-input text-sm py-1.5"
          >
            <option value="wh-main">Main Warehouse</option>
            <option value="wh-east">East DC</option>
            <option value="wh-west">West DC</option>
          </select>
        </div>

        <div className="h-6 w-px bg-[var(--surface-muted)] bg-[var(--surface-muted)]" />

        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
            <input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="enterprise-input text-sm pl-8 w-48"
              placeholder="Search invoices..."
            />
          </div>
          <select
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value)}
            className="enterprise-input text-sm py-1.5"
          >
            <option value="ALL">All Status</option>
            <option value="PENDING">Pending</option>
            <option value="MATCHED">Matched</option>
            <option value="DISCREPANCY">Discrepancy</option>
            <option value="DISPUTED">Disputed</option>
            <option value="APPROVED">Approved</option>
            <option value="PAID">Paid</option>
          </select>
        </div>

        <div className="flex-1" />

        {activeTab === 'invoices' && (
          <button onClick={() => setShowCreateModal(true)} className="enterprise-btn enterprise-btn-primary text-sm">
            <Plus className="w-4 h-4" /> Add Invoice
          </button>
        )}

        <div className="h-6 w-px bg-[var(--surface-muted)] bg-[var(--surface-muted)]" />

        <div className="flex items-center rounded-lg border border-[var(--border-default)] overflow-hidden">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={clsx(
                'flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium transition-colors',
                activeTab === tab.key
                  ? 'bg-[var(--nexus-ai-600)] text-white'
                  : 'text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)]',
              )}
            >
              {tab.label}
              {tab.count !== undefined && (
                <span className={clsx(
                  'ml-0.5 px-1.5 py-0.5 rounded-full text-[10px] font-bold',
                  activeTab === tab.key ? 'bg-white/20' : 'bg-[var(--surface-muted)] bg-[var(--surface-muted)]',
                )}>{tab.count}</span>
              )}
            </button>
          ))}
        </div>
      </div>

      <div className="enterprise-card p-5">
        {activeTab === 'invoices' && (
          <div className="space-y-4">
            <div className="overflow-x-auto rounded-xl border border-[var(--border-default)]">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
                    <th className="px-4 py-3 w-8" />
                    <th className="px-4 py-3">Invoice #</th>
                    <th className="px-4 py-3">Carrier</th>
                    <th className="px-4 py-3">Date</th>
                    <th className="px-4 py-3 text-right">Total Amount</th>
                    <th className="px-4 py-3">Audit Status</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                  {filteredInvoices.map((inv) => {
                    const isExpanded = expandedInvoiceId === inv.id
                    const lineVariance = inv.lines?.reduce((sum, l) => sum + Math.abs(l.varianceAmount), 0) || 0
                    return (
                      <Fragment key={inv.id}>
                        <tr className="hover:bg-[var(--surface-sunken)]/50 hover:bg-[var(--surface-base)]/30 transition-colors">
                          <td className="px-4 py-3">
                            <button
                              onClick={() => setExpandedInvoiceId(isExpanded ? null : inv.id)}
                              className="p-1 rounded hover:bg-[var(--surface-muted)] hover:bg-[var(--interactive-hover)] transition-colors"
                            >
                              {isExpanded ? <ChevronUp className="w-4 h-4 text-[var(--text-secondary)]" /> : <ChevronDown className="w-4 h-4 text-[var(--text-secondary)]" />}
                            </button>
                          </td>
                          <td className="px-4 py-3 font-medium text-[var(--text-primary)] font-mono text-xs">{inv.invoiceNumber}</td>
                          <td className="px-4 py-3 text-[var(--text-secondary)] text-xs">{inv.carrierId}</td>
                          <td className="px-4 py-3 text-[var(--text-secondary)] text-xs">{new Date(inv.invoiceDate).toLocaleDateString()}</td>
                          <td className="px-4 py-3 text-right font-semibold text-[var(--text-primary)]">
                            ${inv.totalAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                          </td>
                          <td className="px-4 py-3">
                            <span className={clsx(
                              'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold',
                              auditStatusColors[inv.auditStatus] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
                            )}>
                              {inv.auditStatus.replace(/_/g, ' ')}
                            </span>
                            {lineVariance > 0 && (
                              <span className="ml-2 text-[10px] text-[var(--nexus-error-500)] font-medium">
                                (${lineVariance.toFixed(2)} variance)
                              </span>
                            )}
                          </td>
                          <td className="px-4 py-3 text-right">
                            <div className="flex items-center justify-end gap-1.5">
                              {inv.auditStatus === 'PENDING' && (
                                <>
                                  <button
                                    onClick={() => handleApprove(inv.id)}
                                    className="px-2 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-success-600)] text-white hover:bg-[var(--nexus-success-700)]"
                                  >
                                    <Check className="w-3 h-3 inline mr-0.5" /> Approve
                                  </button>
                                  <button
                                    onClick={() => handleDispute(inv.id)}
                                    className="px-2 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] hover:bg-[var(--nexus-error-200)]"
                                  >
                                    Dispute
                                  </button>
                                </>
                              )}
                              {inv.auditStatus === 'MATCHED' && (
                                <button
                                  onClick={() => handleApprove(inv.id)}
                                  className="px-2 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-success-600)] text-white hover:bg-[var(--nexus-success-700)]"
                                >
                                  <Check className="w-3 h-3 inline mr-0.5" /> Approve
                                </button>
                              )}
                              {inv.auditStatus === 'APPROVED' && (
                                <button
                                  onClick={() => handlePay(inv.id)}
                                  className="px-2 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-primary-600)] text-white hover:bg-[var(--nexus-primary-700)]"
                                >
                                  <DollarSign className="w-3 h-3 inline mr-0.5" /> Pay
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                        {isExpanded && inv.lines && inv.lines.length > 0 && (
                          <tr>
                            <td colSpan={7} className="px-4 py-3 bg-[var(--surface-sunken)]/80 bg-[var(--surface-base)]/40">
                              <table className="w-full text-xs">
                                <thead>
                                  <tr className="text-left text-[var(--text-secondary)] uppercase tracking-wider">
                                    <th className="pb-2 font-medium">Line</th>
                                    <th className="pb-2 font-medium">Tracking #</th>
                                    <th className="pb-2 font-medium">Service</th>
                                    <th className="pb-2 font-medium text-right">Weight</th>
                                    <th className="pb-2 font-medium text-right">Billed</th>
                                    <th className="pb-2 font-medium text-right">Expected</th>
                                    <th className="pb-2 font-medium text-right">Variance</th>
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-200/50 dark:divide-gray-700/30">
                                  {inv.lines.map((line) => (
                                    <tr key={line.lineNumber}>
                                      <td className="py-1.5 text-[var(--text-secondary)]">{line.lineNumber}</td>
                                      <td className="py-1.5 text-[var(--text-secondary)] font-mono">{line.trackingNumber}</td>
                                      <td className="py-1.5 text-[var(--text-secondary)]">{line.serviceLevel}</td>
                                      <td className="py-1.5 text-[var(--text-secondary)] text-right">{line.weightKg} kg</td>
                                      <td className="py-1.5 text-[var(--text-primary)] text-right font-medium">${line.billedAmount.toFixed(2)}</td>
                                      <td className="py-1.5 text-[var(--text-secondary)] text-right">${line.expectedAmount.toFixed(2)}</td>
                                      <td className={clsx(
                                        'py-1.5 text-right font-medium',
                                        line.varianceAmount > 0 ? 'text-[var(--nexus-error-600)]' : 'text-[var(--nexus-success-600)]',
                                      )}>
                                        {line.varianceAmount > 0 ? '+' : ''}{line.varianceAmount === 0 ? '—' : `$${line.varianceAmount.toFixed(2)}`}
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    )
                  })}
                </tbody>
              </table>
            </div>
            {filteredInvoices.length === 0 && (
              <div className="enterprise-empty-state py-16">
                <Receipt className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] mx-auto mb-3" />
                <p className="text-[var(--text-secondary)] text-sm">No invoices match your filters</p>
              </div>
            )}
          </div>
        )}

        {activeTab === 'audit-log' && (
          <div className="space-y-4">
            <h3 className="text-sm font-semibold text-[var(--text-secondary)]">Audit Trail</h3>
            <div className="space-y-2">
              {auditLogs.map((log) => (
                <div key={log.id} className="flex items-start gap-3 p-3 rounded-lg bg-[var(--surface-sunken)]/80 bg-[var(--surface-base)]/40 border border-[var(--border-subtle)]/50">
                  <div className={clsx(
                    'w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 text-xs font-bold',
                    log.action === 'CREATED' && 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-600)]',
                    log.action === 'AUDIT_MATCH' && 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-600)]',
                    log.action === 'APPROVED' && 'bg-emerald-100 text-emerald-600',
                    log.action === 'DISPUTED' && 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)]',
                    log.action === 'PAID' && 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-600)]',
                    !['CREATED', 'AUDIT_MATCH', 'APPROVED', 'DISPUTED', 'PAID'].includes(log.action) && 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
                  )}>
                    {log.action.charAt(0)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-semibold text-[var(--text-primary)]">{log.action.replace(/_/g, ' ')}</span>
                      <span className="text-[10px] text-[var(--text-tertiary)]">•</span>
                      <span className="text-[10px] text-[var(--text-secondary)]">{log.invoiceId}</span>
                      <span className="text-[10px] text-[var(--text-tertiary)]">•</span>
                      <span className="text-[10px] text-[var(--text-secondary)]">{log.performedBy}</span>
                    </div>
                    {log.details && (
                      <p className="text-xs text-[var(--text-secondary)] mt-0.5">{log.details}</p>
                    )}
                  </div>
                  <span className="text-[10px] text-[var(--text-tertiary)] whitespace-nowrap flex-shrink-0">
                    {new Date(log.performedAt).toLocaleString()}
                  </span>
                </div>
              ))}
            </div>
            {auditLogs.length === 0 && (
              <div className="enterprise-empty-state py-16">
                <FileText className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] mx-auto mb-3" />
                <p className="text-[var(--text-secondary)] text-sm">No audit log entries yet</p>
              </div>
            )}
          </div>
        )}

        {activeTab === 'stats' && (
          <div className="space-y-6">
            <h3 className="text-sm font-semibold text-[var(--text-secondary)] flex items-center gap-2">
              <DollarSign className="w-4 h-4 text-[var(--nexus-ai-500)]" /> Freight Audit Statistics
            </h3>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {[
                { label: 'Total Invoices', value: invoices.length, color: 'text-[var(--nexus-primary-600)]' },
                { label: 'Matched', value: matchedCount, color: 'text-[var(--nexus-success-600)]' },
                { label: 'Pending', value: pendingCount, color: 'text-[var(--nexus-warning-600)]' },
                { label: 'Disputed', value: disputedCount, color: 'text-[var(--nexus-error-600)]' },
                { label: 'Approved', value: invoices.filter((i) => i.auditStatus === 'APPROVED').length, color: 'text-[var(--nexus-primary-600)]' },
                { label: 'Paid', value: paidCount, color: 'text-emerald-600' },
                { label: 'Total Billed', value: `$${totalBilled.toLocaleString(undefined, { minimumFractionDigits: 2 })}`, color: 'text-[var(--text-primary)]' },
                { label: 'Total Variance', value: `$${totalVariance.toFixed(2)}`, color: totalVariance > 0 ? 'text-[var(--nexus-error-600)]' : 'text-[var(--nexus-success-600)]' },
              ].map((stat) => (
                <div key={stat.label} className="enterprise-card p-4 text-center">
                  <p className={clsx('text-2xl font-bold', stat.color)}>{stat.value}</p>
                  <p className="text-xs text-[var(--text-secondary)] mt-1">{stat.label}</p>
                </div>
              ))}
            </div>
            <div className="enterprise-card p-4 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 border border-[var(--nexus-ai-200)] dark:border-[var(--nexus-ai-700)]/30">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-[var(--nexus-ai-100)] dark:bg-[var(--nexus-ai-900)]/40 flex items-center justify-center">
                  <AlertTriangle className="w-5 h-5 text-[var(--nexus-ai-600)] dark:text-[var(--nexus-ai-400)]" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-[var(--nexus-ai-800)] dark:text-[var(--nexus-ai-200)]">Match Rate: {invoices.length > 0 ? Math.round((matchedCount / invoices.length) * 100) : 0}%</p>
                  <p className="text-xs text-[var(--nexus-ai-600)] dark:text-[var(--nexus-ai-400)]">
                    {matchedCount} of {invoices.length} invoices fully matched within tolerance
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {showCreateModal && (
        <div className="enterprise-modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div
            className="enterprise-modal max-w-lg w-full max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">New Freight Invoice</h2>
              <button onClick={() => setShowCreateModal(false)} className="p-1.5 hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] rounded-lg transition-colors">
                <X className="w-5 h-5 text-[var(--text-secondary)]" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Invoice Number *</label>
                <input
                  value={createForm.invoiceNumber}
                  onChange={(e) => setCreateForm({ ...createForm, invoiceNumber: e.target.value })}
                  className="enterprise-input w-full text-sm"
                  placeholder="INV-2026-005"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Carrier *</label>
                <select
                  value={createForm.carrierId}
                  onChange={(e) => setCreateForm({ ...createForm, carrierId: e.target.value })}
                  className="enterprise-input w-full text-sm"
                >
                  <option value="">Select carrier...</option>
                  <option value="carrier-fedex">FedEx</option>
                  <option value="carrier-ups">UPS</option>
                  <option value="carrier-dhl">DHL</option>
                  <option value="carrier-usps">USPS</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Total Amount</label>
                <input
                  type="number"
                  min={0}
                  step={0.01}
                  value={createForm.totalAmount}
                  onChange={(e) => setCreateForm({ ...createForm, totalAmount: e.target.value })}
                  className="enterprise-input w-full text-sm"
                  placeholder="0.00"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Notes</label>
                <textarea
                  value={createForm.notes}
                  onChange={(e) => setCreateForm({ ...createForm, notes: e.target.value })}
                  className="enterprise-input w-full text-sm"
                  rows={3}
                  placeholder="Optional notes"
                />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowCreateModal(false)} className="enterprise-btn enterprise-btn-secondary text-sm">Cancel</button>
              <button onClick={handleCreateInvoice} disabled={saving} className="enterprise-btn enterprise-btn-primary text-sm">
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />} Create Invoice
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

