import { useState, useMemo, useEffect } from 'react'
import { clsx } from 'clsx'
import {
  RotateCcw, Plus, Search, X, Check, ChevronDown, ChevronRight,
  Eye, PackageCheck, DollarSign, ThumbsUp, RefreshCw, AlertTriangle, Loader2,
  ClipboardList, ShieldCheck, BarChart3, Settings, Truck, TrendingUp,
  TrendingDown, ArrowUpRight, ArrowDownRight, Box, Recycle, Heart,
  Wrench, Trash2, Sparkles, Filter, Calendar,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'
import { fetchReturns, fetchReturnAnalytics, createReturn, updateReturn } from '../api/newBackend'
import PermissionGate from '../components/rbac/PermissionGate'

type RmaStatus = 'PENDING_APPROVAL' | 'AUTHORIZED' | 'IN_TRANSIT' | 'RECEIVED' | 'INSPECTED' | 'COMPLETED' | 'REJECTED'
type ItemCondition = 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR' | 'DAMAGED'
type Disposition = 'RESTOCK' | 'REFURBISH' | 'DONATE' | 'RECYCLE' | 'SCRAP'
type Grade = 'A' | 'B' | 'C' | 'D' | 'F'

interface RmaItem {
  id: string
  sku: string
  productName: string
  quantity: number
  condition: ItemCondition | ''
  grade: Grade | ''
  disposition: Disposition | ''
  conditionNotes: string
  unitPrice: number
}

interface RmaRecord {
  id: string
  rmaNumber: string
  orderNumber: string
  customer: string
  reason: string
  reasonType: string
  status: RmaStatus
  date: string
  value: number
  items: RmaItem[]
}

interface DispoCategory {
  key: Disposition
  label: string
  icon: typeof Box
  count: number
  value: number
  color: string
}

const RMA_STATUS_COLORS: Record<RmaStatus, string> = {
  PENDING_APPROVAL: 'text-[var(--nexus-warning-600)] bg-[var(--nexus-warning-50)] ring-amber-500/20',
  AUTHORIZED: 'text-[var(--nexus-primary-600)] bg-[var(--nexus-primary-50)] ring-blue-500/20',
  IN_TRANSIT: 'text-[var(--nexus-info-600)] bg-[var(--nexus-info-50)] ring-cyan-500/20',
  RECEIVED: 'text-[var(--text-secondary)] bg-[var(--surface-sunken)] ring-gray-500/20',
  INSPECTED: 'text-violet-600 bg-violet-50 ring-violet-500/20',
  COMPLETED: 'text-emerald-600 bg-emerald-50 ring-emerald-500/20',
  REJECTED: 'text-[var(--nexus-error-600)] bg-[var(--nexus-error-50)] ring-red-500/20',
}

const RMA_STATUS_BADGE: Record<RmaStatus, string> = {
  PENDING_APPROVAL: 'pending_approval',
  AUTHORIZED: 'approved',
  IN_TRANSIT: 'info',
  RECEIVED: 'received',
  INSPECTED: 'inspected',
  COMPLETED: 'completed',
  REJECTED: 'rejected',
}

const CONDITION_OPTIONS: ItemCondition[] = ['EXCELLENT', 'GOOD', 'FAIR', 'POOR', 'DAMAGED']
const DISPOSITION_OPTIONS: Disposition[] = ['RESTOCK', 'REFURBISH', 'DONATE', 'RECYCLE', 'SCRAP']
const GRADE_OPTIONS: Grade[] = ['A', 'B', 'C', 'D', 'F']
const REASON_TYPES = ['Defective', 'Damaged in Transit', 'Wrong Item', 'Not Needed', 'Quality Issue', 'Other']

const TABS = [
  { id: 'RMA_QUEUE', label: 'RMA Queue', icon: <ClipboardList className="w-4 h-4" /> },
  { id: 'INSPECTION', label: 'Inspection', icon: <Eye className="w-4 h-4" /> },
  { id: 'DISPOSITION', label: 'Disposition', icon: <ShieldCheck className="w-4 h-4" /> },
  { id: 'ANALYTICS', label: 'Analytics', icon: <BarChart3 className="w-4 h-4" /> },
]

const DISPOSITION_CATEGORIES: DispoCategory[] = [
  { key: 'RESTOCK', label: 'Restock', icon: PackageCheck, count: 234, value: 12450, color: 'text-emerald-600 bg-emerald-50 ring-emerald-500/20' },
  { key: 'REFURBISH', label: 'Refurbish', icon: Wrench, count: 67, value: 3210, color: 'text-[var(--nexus-primary-600)] bg-[var(--nexus-primary-50)] ring-blue-500/20' },
  { key: 'DONATE', label: 'Donate', icon: Heart, count: 12, value: 0, color: 'text-pink-600 bg-pink-50 ring-pink-500/20' },
  { key: 'RECYCLE', label: 'Recycle', icon: Recycle, count: 8, value: 0, color: 'text-[var(--nexus-info-600)] bg-[var(--nexus-info-50)] ring-cyan-500/20' },
  { key: 'SCRAP', label: 'Scrap', icon: Trash2, count: 3, value: 0, color: 'text-[var(--text-secondary)] bg-[var(--surface-sunken)] ring-gray-500/20' },
]

const DISPOSITION_LABELS: Record<Disposition, string> = {
  RESTOCK: 'Restock Inventory',
  REFURBISH: 'Refurbish',
  DONATE: 'Donate',
  RECYCLE: 'Recycle',
  SCRAP: 'Scrap',
}

const CONDITION_DISPOSITION_MAP: Record<ItemCondition, Disposition> = {
  EXCELLENT: 'RESTOCK',
  GOOD: 'RESTOCK',
  FAIR: 'REFURBISH',
  POOR: 'RECYCLE',
  DAMAGED: 'SCRAP',
}

const GRADE_ORDER: Record<Grade, number> = { A: 5, B: 4, C: 3, D: 2, F: 1 }

function generateMockRmas(): RmaRecord[] {
  const statuses: RmaStatus[] = ['PENDING_APPROVAL', 'AUTHORIZED', 'IN_TRANSIT', 'RECEIVED', 'INSPECTED', 'COMPLETED', 'REJECTED']
  const reasons = ['Defective product', 'Wrong size', 'Damaged in transit', 'Not as described', 'Changed mind', 'Quality issue']
  const reasonTypes = ['Defective', 'Damaged in Transit', 'Wrong Item', 'Not Needed', 'Quality Issue', 'Other']
  const customers = ['Alice Johnson', 'Bob Smith', 'Carol Davis', 'David Wilson', 'Eva Martinez', 'Frank Brown', 'Grace Lee', 'Henry Taylor']
  const products = [
    { sku: 'TEE-BLK-001', name: 'Classic Black Tee', price: 29.99 },
    { sku: 'TEE-WHT-002', name: 'Essential White Tee', price: 27.99 },
    { sku: 'HOD-NAV-003', name: 'Navy Zip Hoodie', price: 69.99 },
    { sku: 'CAP-RED-004', name: 'Red Baseball Cap', price: 24.99 },
    { sku: 'JOG-GRY-005', name: 'Grey Joggers', price: 49.99 },
    { sku: 'JKT-BLK-006', name: 'Black Bomber Jacket', price: 129.99 },
  ]

  return Array.from({ length: 25 }, (_, i) => {
    const status = statuses[i % statuses.length]
    const cust = customers[i % customers.length]
    const prod = products[i % products.length]
    const reasonIdx = i % reasons.length
    return {
      id: `rma-${1000 + i}`,
      rmaNumber: `RMA-2024-${String(1000 + i).slice(-4)}`,
      orderNumber: `ORD-${String(5000 + i).slice(-4)}`,
      customer: cust,
      reason: reasons[reasonIdx],
      reasonType: reasonTypes[reasonIdx],
      status,
      date: new Date(2024, 6, 10 + i).toISOString().slice(0, 10),
      value: prod.price * (1 + (i % 3)),
      items: [
        {
          id: `item-${i}`,
          sku: prod.sku,
          productName: prod.name,
          quantity: 1 + (i % 3),
          condition: '',
          grade: '',
          disposition: '',
          conditionNotes: '',
          unitPrice: prod.price,
        },
      ],
    }
  })
}

const MOCK_RMAS = generateMockRmas()

const MONTHLY_RETURN_TREND = [
  { month: 'Jan', returns: 42, totalOrders: 2100 },
  { month: 'Feb', returns: 38, totalOrders: 1950 },
  { month: 'Mar', returns: 55, totalOrders: 2400 },
  { month: 'Apr', returns: 48, totalOrders: 2300 },
  { month: 'May', returns: 62, totalOrders: 2600 },
  { month: 'Jun', returns: 58, totalOrders: 2500 },
  { month: 'Jul', returns: 71, totalOrders: 2800 },
  { month: 'Aug', returns: 65, totalOrders: 2700 },
  { month: 'Sep', returns: 52, totalOrders: 2550 },
  { month: 'Oct', returns: 47, totalOrders: 2400 },
  { month: 'Nov', returns: 82, totalOrders: 3200 },
  { month: 'Dec', returns: 95, totalOrders: 3600 },
]

const RETURN_REASONS_BREAKDOWN = [
  { reason: 'Defective', count: 145, percentage: 28 },
  { reason: 'Wrong Item', count: 98, percentage: 19 },
  { reason: 'Not Needed', count: 87, percentage: 17 },
  { reason: 'Damaged Transit', count: 76, percentage: 15 },
  { reason: 'Quality Issue', count: 64, percentage: 12 },
  { reason: 'Other', count: 47, percentage: 9 },
]

const TOP_RETURNED_PRODUCTS = [
  { sku: 'TEE-BLK-001', name: 'Classic Black Tee', returns: 42, returnRate: 3.8, revenueLost: 1259.58 },
  { sku: 'HOD-NAV-003', name: 'Navy Zip Hoodie', returns: 31, returnRate: 4.2, revenueLost: 2169.69 },
  { sku: 'JOG-GRY-005', name: 'Grey Joggers', returns: 27, returnRate: 3.5, revenueLost: 1349.73 },
  { sku: 'JKT-BLK-006', name: 'Black Bomber Jacket', returns: 18, returnRate: 5.1, revenueLost: 2339.82 },
  { sku: 'CAP-RED-004', name: 'Red Baseball Cap', returns: 15, returnRate: 2.9, revenueLost: 374.85 },
]

const RECOVERY_BY_DISPOSITION = [
  { disposition: 'Restock', items: 234, recoveryValue: 12450, recoveryRate: 85 },
  { disposition: 'Refurbish', items: 67, recoveryValue: 3210, recoveryRate: 55 },
  { disposition: 'Donate', items: 12, recoveryValue: 0, recoveryRate: 0 },
  { disposition: 'Recycle', items: 8, recoveryValue: 160, recoveryRate: 10 },
  { disposition: 'Scrap', items: 3, recoveryValue: 0, recoveryRate: 0 },
]

const RETURN_RATE_BY_CHANNEL = [
  { channel: 'Online Store', orders: 12500, returns: 320, rate: 2.56 },
  { channel: 'Marketplace', orders: 8400, returns: 210, rate: 2.50 },
  { channel: 'Mobile App', orders: 5600, returns: 98, rate: 1.75 },
  { channel: 'Wholesale', orders: 3200, returns: 24, rate: 0.75 },
  { channel: 'In-Store', orders: 9800, returns: 147, rate: 1.50 },
]

function suggestDisposition(condition: ItemCondition, grade: Grade): Disposition {
  if (condition === 'EXCELLENT' && (grade === 'A' || grade === 'B')) return 'RESTOCK'
  if (condition === 'GOOD' && grade === 'A') return 'RESTOCK'
  if (condition === 'GOOD') return 'REFURBISH'
  if (condition === 'FAIR') return 'REFURBISH'
  if (condition === 'POOR') return 'RECYCLE'
  return 'SCRAP'
}

function SparklineChart({ data, height = 32 }: { data: number[]; height?: number }) {
  const max = Math.max(...data)
  const min = Math.min(...data)
  const range = max - min || 1
  const width = 120
  const points = data.map((v, i) => `${(i / (data.length - 1)) * width},${height - ((v - min) / range) * (height - 4) - 2}`).join(' ')
  return (
    <svg width={width} height={height} className="overflow-visible">
      <polyline fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
        points={points} className="text-[var(--nexus-primary-500)]" />
      <circle cx={parseFloat(points.split(' ').pop()!.split(',')[0])} cy={parseFloat(points.split(' ').pop()!.split(',')[1])}
        r="3" className="fill-blue-500" />
    </svg>
  )
}

function HorizontalBar({ label, value, max, color }: { label: string; value: number; max: number; color: string }) {
  const pct = max > 0 ? (value / max) * 100 : 0
  return (
    <div className="flex items-center gap-3">
      <span className="text-sm text-[var(--text-secondary)] w-28 shrink-0 truncate">{label}</span>
      <div className="flex-1 h-5 bg-[var(--surface-muted)] rounded-full overflow-hidden">
        <div className={clsx('h-full rounded-full transition-all', color)} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-sm font-medium text-[var(--text-primary)] w-12 text-right">{value}</span>
    </div>
  )
}

export default function ReturnsEnhancedPage() {
  const [activeTab, setActiveTab] = useState('RMA_QUEUE')
  const [search, setSearch] = useState('')
  const { addToast } = useToast()

  const [returns, setReturns] = useState<any[]>([])
  const [analytics, setAnalytics] = useState<any>(null)

  useEffect(() => {
    Promise.all([fetchReturns(), fetchReturnAnalytics()]).then(([r, a]) => {
      if (r?.returns) setReturns(r.returns)
      if (a?.analytics) setAnalytics(a.analytics)
    }).catch(() => {})
  }, [])

  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [reasonFilter, setReasonFilter] = useState<string>('ALL')

  const [expandedRma, setExpandedRma] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [confirmAction, setConfirmAction] = useState<{ type: 'approve' | 'reject'; rma: RmaRecord } | null>(null)
  const [processing, setProcessing] = useState<string | null>(null)

  const [createForm, setCreateForm] = useState({ customer: '', orderNumber: '', reason: '', reasonType: 'Defective', value: 0 })
  const [rejectReason, setRejectReason] = useState('')

  const [rmas, setRmas] = useState<RmaRecord[]>([])
  const [inspectionRma, setInspectionRma] = useState<RmaRecord | null>(null)

  const [inspectForm, setInspectForm] = useState<{ condition: ItemCondition; grade: Grade; disposition: Disposition; notes: string }>({
    condition: 'GOOD', grade: 'B', disposition: 'RESTOCK', notes: '',
  })

  const filteredRmas = useMemo(() => {
    let list = rmas
    if (search) {
      const q = search.toLowerCase()
      list = list.filter(r => r.rmaNumber.toLowerCase().includes(q) || r.customer.toLowerCase().includes(q) || r.orderNumber.toLowerCase().includes(q) || r.reason.toLowerCase().includes(q))
    }
    if (statusFilter !== 'ALL') list = list.filter(r => r.status === statusFilter)
    if (reasonFilter !== 'ALL') list = list.filter(r => r.reasonType === reasonFilter)
    return list
  }, [rmas, search, statusFilter, reasonFilter])

  const pendingInspection = useMemo(() => rmas.filter(r => r.status === 'RECEIVED'), [rmas])

  const receivedRmas = pendingInspection

  async function handleCreateReturn(data: any) {
    const res = await createReturn(data)
    if (res?.return) {
      setReturns(prev => [res.return, ...prev])
      addToast({ type: 'success', title: 'RMA created' })
    }
  }

  async function handleUpdateReturn(id: string, updates: any) {
    const res = await updateReturn(id, updates)
    if (res?.return) {
      setReturns(prev => prev.map(r => r.id === id ? res.return : r))
      addToast({ type: 'success', title: 'Return updated' })
    }
  }

  const handleApproveRma = (rma: RmaRecord) => {
    handleUpdateReturn(rma.id, { status: 'AUTHORIZED' })
    setConfirmAction(null)
  }

  const handleRejectRma = (rma: RmaRecord) => {
    handleUpdateReturn(rma.id, { status: 'REJECTED', reason: rejectReason })
    setConfirmAction(null)
  }

  const openInspection = (rma: RmaRecord) => {
    setInspectionRma(rma)
    const item = rma.items[0]
    setInspectForm({ condition: 'GOOD', grade: 'B', disposition: 'RESTOCK', notes: '' })
  }

  const handleInspectSubmit = () => {
    if (!inspectionRma) return
    const { condition, grade, disposition, notes } = inspectForm
    handleUpdateReturn(inspectionRma.id, { status: 'INSPECTED', items: inspectionRma.items.map(item => ({ ...item, condition, grade, disposition, conditionNotes: notes })) })
    setInspectionRma(null)
  }

  const handleConditionChange = (condition: ItemCondition) => {
    const suggested = CONDITION_DISPOSITION_MAP[condition]
    const grade: Grade = condition === 'EXCELLENT' ? 'A' : condition === 'GOOD' ? 'B' : condition === 'FAIR' ? 'C' : condition === 'POOR' ? 'D' : 'F'
    setInspectForm({ condition, grade, disposition: suggested, notes: inspectForm.notes })
  }

  const handleGradeChange = (grade: Grade) => {
    const suggested = suggestDisposition(inspectForm.condition || 'GOOD', grade)
    setInspectForm({ ...inspectForm, grade, disposition: suggested })
  }

  const handleProcessDisposition = (disposition: Disposition) => {
    const cat = DISPOSITION_CATEGORIES.find(c => c.key === disposition)
    addToast({ type: 'success', title: `Processing ${cat?.count} items for ${cat?.label}` })
  }

  const renderRmaQueueTab = () => (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-2 bg-[var(--surface-base)] border border-[var(--border-default)] rounded-lg px-3 py-2">
          <Filter className="w-4 h-4 text-[var(--text-tertiary)]" />
          <select className="text-sm bg-transparent border-none outline-none text-[var(--text-secondary)]" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="ALL">All Statuses</option>
            <option value="PENDING_APPROVAL">Pending Approval</option>
            <option value="AUTHORIZED">Authorized</option>
            <option value="IN_TRANSIT">In Transit</option>
            <option value="RECEIVED">Received</option>
            <option value="INSPECTED">Inspected</option>
            <option value="COMPLETED">Completed</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </div>
        <div className="flex items-center gap-2 bg-[var(--surface-base)] border border-[var(--border-default)] rounded-lg px-3 py-2">
          <Filter className="w-4 h-4 text-[var(--text-tertiary)]" />
          <select className="text-sm bg-transparent border-none outline-none text-[var(--text-secondary)]" value={reasonFilter} onChange={e => setReasonFilter(e.target.value)}>
            <option value="ALL">All Reasons</option>
            {REASON_TYPES.map(rt => <option key={rt} value={rt}>{rt}</option>)}
          </select>
        </div>
        <div className="flex items-center gap-2 bg-[var(--surface-base)] border border-[var(--border-default)] rounded-lg px-3 py-2">
          <Calendar className="w-4 h-4 text-[var(--text-tertiary)]" />
          <span className="text-sm text-[var(--text-secondary)]">Last 30 days</span>
        </div>
        <div className="ml-auto">
          <PermissionGate resource="orders" action="create">
            <button onClick={() => setCreateOpen(true)} className="enterprise-btn enterprise-btn-primary flex items-center gap-1.5">
              <Plus className="w-4 h-4" /> Create RMA
            </button>
          </PermissionGate>
        </div>
      </div>

      {/* RMA Table */}
      <div className="enterprise-card overflow-hidden p-0">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[var(--border-default)] bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50">
                <th className="text-left px-4 py-3 font-semibold text-[var(--text-secondary)] text-xs uppercase tracking-wider">RMA#</th>
                <th className="text-left px-4 py-3 font-semibold text-[var(--text-secondary)] text-xs uppercase tracking-wider">Order#</th>
                <th className="text-left px-4 py-3 font-semibold text-[var(--text-secondary)] text-xs uppercase tracking-wider">Customer</th>
                <th className="text-left px-4 py-3 font-semibold text-[var(--text-secondary)] text-xs uppercase tracking-wider">Reason</th>
                <th className="text-left px-4 py-3 font-semibold text-[var(--text-secondary)] text-xs uppercase tracking-wider">Status</th>
                <th className="text-left px-4 py-3 font-semibold text-[var(--text-secondary)] text-xs uppercase tracking-wider">Date</th>
                <th className="text-right px-4 py-3 font-semibold text-[var(--text-secondary)] text-xs uppercase tracking-wider">Value</th>
                <th className="text-right px-4 py-3 font-semibold text-[var(--text-secondary)] text-xs uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
              {filteredRmas.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-[var(--text-tertiary)]">No RMAs match the current filters</td>
                </tr>
              ) : (
                filteredRmas.map(rma => (
                  <tr key={rma.id} className={clsx('group hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)]/30 transition-colors', expandedRma === rma.id && 'bg-[var(--surface-sunken)] bg-[var(--surface-base)]/30')}>
                    <td className="px-4 py-3">
                      <button onClick={() => setExpandedRma(expandedRma === rma.id ? null : rma.id)} className="flex items-center gap-1.5 font-mono text-sm font-medium text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)] hover:underline">
                        {expandedRma === rma.id ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
                        {rma.rmaNumber}
                      </button>
                    </td>
                    <td className="px-4 py-3 font-mono text-sm text-[var(--text-secondary)]">{rma.orderNumber}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{rma.customer}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)] max-w-[180px] truncate">{rma.reason}</td>
                    <td className="px-4 py-3">
                      <EnterpriseStatusBadge status={RMA_STATUS_BADGE[rma.status]} label={rma.status.replace(/_/g, ' ')} />
                    </td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{new Date(rma.date).toLocaleDateString()}</td>
                    <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)] text-right">${rma.value.toFixed(2)}</td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {rma.status === 'PENDING_APPROVAL' && (
                          <>
                            <PermissionGate resource="orders" action="edit">
                              <button onClick={() => setConfirmAction({ type: 'approve', rma })}
                                className="w-7 h-7 flex items-center justify-center rounded-lg bg-emerald-50 text-emerald-600 hover:bg-emerald-100 dark:bg-emerald-900/20 dark:text-emerald-400 dark:hover:bg-emerald-900/30 transition-colors"
                                title="Approve"><Check className="w-4 h-4" /></button>
                            </PermissionGate>
                            <PermissionGate resource="orders" action="edit">
                              <button onClick={() => setConfirmAction({ type: 'reject', rma })}
                                className="w-7 h-7 flex items-center justify-center rounded-lg bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)] hover:bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-400)] dark:hover:bg-[var(--nexus-error-900)]/30 transition-colors"
                                title="Reject"><X className="w-4 h-4" /></button>
                            </PermissionGate>
                          </>
                        )}
                        {rma.status === 'AUTHORIZED' && (
                          <span className="text-xs text-[var(--text-tertiary)] italic">Awaiting receipt</span>
                        )}
                        {rma.status === 'IN_TRANSIT' && (
                          <span className="text-xs text-[var(--nexus-info-500)] italic"><Truck className="w-3.5 h-3.5 inline mr-1" />In transit</span>
                        )}
                        {rma.status === 'RECEIVED' && (
                          <PermissionGate resource="orders" action="edit">
                            <button onClick={() => openInspection(rma)}
                              className="enterprise-btn enterprise-btn-sm bg-violet-600 text-white hover:bg-violet-700 border-none">
                              <Eye className="w-3 h-3" /> Inspect
                            </button>
                          </PermissionGate>
                        )}
                        {rma.status === 'INSPECTED' && (
                          <PermissionGate resource="orders" action="edit">
                            <button onClick={() => addToast({ type: 'info', title: `Processing completion for ${rma.rmaNumber}` })}
                              className="enterprise-btn enterprise-btn-sm bg-emerald-600 text-white hover:bg-emerald-700 border-none">
                              <DollarSign className="w-3 h-3" /> Complete
                            </button>
                          </PermissionGate>
                        )}
                        {rma.status === 'COMPLETED' && (
                          <span className="text-xs text-emerald-600 font-medium">Done</span>
                        )}
                        {rma.status === 'REJECTED' && (
                          <span className="text-xs text-[var(--nexus-error-400)]">Rejected</span>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Expanded RMA details */}
      {expandedRma && (() => {
        const rma = rmas.find(r => r.id === expandedRma)
        if (!rma) return null
        return (
          <div className="enterprise-card p-5">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-sm">
              <div>
                <h4 className="font-semibold text-[var(--text-primary)] mb-3">RMA Details</h4>
                <dl className="space-y-2">
                  {[
                    ['RMA Number', rma.rmaNumber],
                    ['Order Number', rma.orderNumber],
                    ['Customer', rma.customer],
                    ['Reason', rma.reason],
                    ['Reason Type', rma.reasonType],
                    ['Date Created', new Date(rma.date).toLocaleDateString()],
                    ['Total Value', `$${rma.value.toFixed(2)}`],
                  ].map(([k, v]) => (
                    <div key={k} className="flex justify-between py-1.5 px-3 bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 rounded-lg">
                      <span className="text-[var(--text-secondary)]">{k}</span>
                      <span className="font-medium text-[var(--text-primary)]">{v}</span>
                    </div>
                  ))}
                </dl>
              </div>
              <div>
                <h4 className="font-semibold text-[var(--text-primary)] mb-3">Items ({rma.items.length})</h4>
                <div className="space-y-2">
                  {rma.items.map((item, i) => (
                    <div key={item.id || i} className="bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 rounded-lg p-3 space-y-1.5">
                      <div className="flex items-center justify-between">
                        <span className="font-mono text-sm font-medium text-[var(--text-secondary)]">{item.sku}</span>
                        <span className="text-xs text-[var(--text-tertiary)]">x{item.quantity}</span>
                      </div>
                      {item.productName && <p className="text-xs text-[var(--text-secondary)]">{item.productName}</p>}
                      {item.condition && <span className="enterprise-tag">{item.condition}</span>}
                      {item.disposition && <span className="enterprise-tag ml-1">{DISPOSITION_LABELS[item.disposition] || item.disposition}</span>}
                      {item.conditionNotes && <p className="text-xs text-[var(--text-tertiary)] mt-1">{item.conditionNotes}</p>}
                    </div>
                  ))}
                </div>
              </div>
              <div>
                <h4 className="font-semibold text-[var(--text-primary)] mb-3">Status Timeline</h4>
                <div className="space-y-3">
                  {['PENDING_APPROVAL', 'AUTHORIZED', 'IN_TRANSIT', 'RECEIVED', 'INSPECTED', 'COMPLETED'].map((s, i) => {
                    const statusIdx = ['PENDING_APPROVAL', 'AUTHORIZED', 'IN_TRANSIT', 'RECEIVED', 'INSPECTED', 'COMPLETED'].indexOf(rma.status)
                    const done = i <= statusIdx
                    const current = i === statusIdx
                    return (
                      <div key={s} className="flex items-center gap-3">
                        <div className={clsx('w-2.5 h-2.5 rounded-full ring-2 shrink-0', done ? 'bg-emerald-500 ring-emerald-200 dark:ring-emerald-800' : 'bg-[var(--surface-muted)] bg-[var(--surface-muted)] ring-gray-100 dark:ring-gray-700')} />
                        <span className={clsx('text-xs', current ? 'font-semibold text-[var(--text-primary)]' : done ? 'text-[var(--text-secondary)]' : 'text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]')}>
                          {s.replace(/_/g, ' ')}
                        </span>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          </div>
        )
      })()}
    </div>
  )

  const renderInspectionTab = () => (
    <div className="space-y-4">
      {inspectionRma ? (
        <div>
          <div className="flex items-center justify-between mb-4">
            <div>
              <button onClick={() => setInspectionRma(null)} className="text-sm text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)] hover:underline flex items-center gap-1 mb-2">
                <ChevronDown className="w-4 h-4 rotate-90" /> Back to queue
              </button>
              <h3 className="text-lg font-bold text-[var(--text-primary)]">Inspect: {inspectionRma.rmaNumber}</h3>
              <p className="text-sm text-[var(--text-secondary)]">{inspectionRma.customer} &middot; {inspectionRma.orderNumber}</p>
            </div>
          </div>

          <div className="enterprise-card p-5">
            <div className="space-y-5">
              {/* Item Info */}
              <div className="bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 rounded-xl p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-mono font-semibold text-[var(--text-primary)]">{inspectionRma.items[0].sku}</span>
                  <span className="text-sm text-[var(--text-secondary)]">x{inspectionRma.items[0].quantity}</span>
                </div>
                <p className="text-sm text-[var(--text-secondary)]">{inspectionRma.items[0].productName}</p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Condition */}
                <div className="enterprise-form-group">
                  <label className="flex items-center gap-1.5 text-sm font-medium text-[var(--text-secondary)]">
                    <Box className="w-4 h-4" /> Item Condition
                  </label>
                  <select className="enterprise-select mt-1" value={inspectForm.condition}
                    onChange={e => handleConditionChange(e.target.value as ItemCondition)}>
                    {CONDITION_OPTIONS.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>

                {/* Grade */}
                <div className="enterprise-form-group">
                  <label className="flex items-center gap-1.5 text-sm font-medium text-[var(--text-secondary)]">
                    <ShieldCheck className="w-4 h-4" /> Grade
                  </label>
                  <div className="flex gap-2 mt-1">
                    {GRADE_OPTIONS.map(g => (
                      <button key={g} onClick={() => handleGradeChange(g)}
                        className={clsx('w-10 h-10 rounded-lg text-sm font-bold border transition-all',
                          inspectForm.grade === g
                            ? 'bg-[var(--nexus-primary-600)] text-white border-[var(--nexus-primary-600)]'
                            : 'bg-[var(--surface-base)] text-[var(--text-secondary)] border-[var(--border-default)] hover:border-[var(--nexus-primary-300)]'
                        )}>
                        {g}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* AI Disposition Suggestion */}
              <div className="bg-gradient-to-r from-violet-50 to-blue-50 dark:from-violet-900/20 dark:to-blue-900/20 rounded-xl p-4 border border-violet-200 dark:border-violet-800">
                <div className="flex items-center gap-2 mb-2">
                  <Sparkles className="w-4 h-4 text-violet-600 dark:text-violet-400" />
                  <span className="text-sm font-semibold text-violet-700 dark:text-violet-300">AI Disposition Suggestion</span>
                </div>
                <div className="flex items-center gap-3">
                  {DISPOSITION_OPTIONS.map(d => (
                    <button key={d} onClick={() => setInspectForm(f => ({ ...f, disposition: d }))}
                      className={clsx('px-3 py-1.5 rounded-lg text-xs font-medium border transition-all',
                        inspectForm.disposition === d
                          ? 'bg-violet-600 text-white border-violet-600'
                          : 'bg-[var(--surface-base)] text-[var(--text-secondary)] border-[var(--border-default)] hover:border-violet-300'
                      )}>
                      {d}
                    </button>
                  ))}
                </div>
                <p className="text-xs text-violet-500 dark:text-violet-400 mt-2">
                  Based on condition ({inspectForm.condition}) and grade ({inspectForm.grade}), we recommend: <strong>{inspectForm.disposition}</strong>
                </p>
              </div>

              {/* Condition Notes */}
              <div className="enterprise-form-group">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Condition Notes</label>
                <textarea className="enterprise-textarea mt-1" rows={3}
                  placeholder="Describe the item's condition, defects, or damage in detail..."
                  value={inspectForm.notes} onChange={e => setInspectForm(f => ({ ...f, notes: e.target.value }))} />
              </div>

              <div className="flex items-center justify-end gap-3 pt-2 border-t border-[var(--border-subtle)]">
                <button onClick={() => setInspectionRma(null)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
                <PermissionGate resource="orders" action="edit">
                  <button onClick={handleInspectSubmit}
                    className="enterprise-btn bg-violet-600 text-white hover:bg-violet-700 border-none flex items-center gap-1.5">
                    <Eye className="w-4 h-4" /> Inspect & Submit
                  </button>
                </PermissionGate>
              </div>
            </div>
          </div>
        </div>
      ) : (
        <>
          <div className="flex items-center justify-between">
            <p className="text-sm text-[var(--text-secondary)]">{receivedRmas.length} returns awaiting inspection</p>
          </div>

          {receivedRmas.length === 0 ? (
            <div className="enterprise-empty-state">
              <Eye className="w-12 h-12" />
              <h3>No items to inspect</h3>
              <p>All received returns have been inspected</p>
            </div>
          ) : (
            <div className="space-y-3">
              {receivedRmas.map(rma => (
                <div key={rma.id} className="enterprise-card p-4 flex items-center justify-between hover:shadow-sm transition-shadow">
                  <div className="flex items-center gap-4 min-w-0">
                    <div className="w-10 h-10 rounded-xl bg-[var(--surface-muted)] flex items-center justify-center shrink-0">
                      <PackageCheck className="w-5 h-5 text-[var(--text-secondary)]" />
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-[var(--text-primary)]">{rma.rmaNumber}</span>
                        <EnterpriseStatusBadge status="received" />
                      </div>
                      <p className="text-sm text-[var(--text-secondary)] mt-0.5">{rma.customer} &middot; {rma.orderNumber} &middot; {rma.items[0].productName}</p>
                      <p className="text-xs text-[var(--text-tertiary)] mt-0.5">Reason: {rma.reason}</p>
                    </div>
                  </div>
                  <PermissionGate resource="orders" action="edit">
                    <button onClick={() => openInspection(rma)}
                      className="enterprise-btn enterprise-btn-sm bg-violet-600 text-white hover:bg-violet-700 border-none shrink-0 ml-4">
                      <Eye className="w-3.5 h-3.5" /> Inspect
                    </button>
                  </PermissionGate>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )

  const renderDispositionTab = () => (
    <div className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
        {DISPOSITION_CATEGORIES.map(cat => (
          <div key={cat.key} className="enterprise-card p-5 hover:shadow-md transition-shadow">
            <div className={clsx('w-12 h-12 rounded-xl flex items-center justify-center ring-1 mb-4', cat.color)}>
              <cat.icon className="w-6 h-6" />
            </div>
            <h3 className="text-lg font-bold text-[var(--text-primary)]">{cat.count}</h3>
            <p className="text-sm text-[var(--text-secondary)]">{cat.label}</p>
            {cat.value > 0 && <p className="text-xs text-[var(--text-tertiary)] mt-1">${cat.value.toLocaleString()} recovery value</p>}
            <PermissionGate resource="orders" action="edit">
              <button onClick={() => handleProcessDisposition(cat.key)}
                className="mt-4 w-full enterprise-btn enterprise-btn-sm border border-[var(--border-default)] text-[var(--text-secondary)] hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)]">
                Process Disposition
              </button>
            </PermissionGate>
          </div>
        ))}
      </div>

      <div className="enterprise-card p-5">
        <h3 className="font-semibold text-[var(--text-primary)] mb-4">Disposition Summary</h3>
        <div className="space-y-3">
          {DISPOSITION_CATEGORIES.map(cat => {
            const total = DISPOSITION_CATEGORIES.reduce((s, c) => s + c.count, 0)
            const pct = total > 0 ? Math.round((cat.count / total) * 100) : 0
            return (
              <div key={cat.key} className="flex items-center gap-4">
                <div className={clsx('w-2 h-2 rounded-full shrink-0', cat.key === 'RESTOCK' ? 'bg-emerald-500' : cat.key === 'REFURBISH' ? 'bg-[var(--nexus-primary-50)]0' : cat.key === 'DONATE' ? 'bg-pink-500' : cat.key === 'RECYCLE' ? 'bg-[var(--nexus-info-50)]0' : 'bg-[var(--surface-muted)]')} />
                <span className="text-sm text-[var(--text-secondary)] w-20">{cat.label}</span>
                <div className="flex-1 h-6 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                  <div className={clsx('h-full rounded-full transition-all', cat.key === 'RESTOCK' ? 'bg-emerald-500' : cat.key === 'REFURBISH' ? 'bg-[var(--nexus-primary-50)]0' : cat.key === 'DONATE' ? 'bg-pink-500' : cat.key === 'RECYCLE' ? 'bg-[var(--nexus-info-50)]0' : 'bg-[var(--surface-muted)]')}
                    style={{ width: `${pct}%` }} />
                </div>
                <span className="text-sm font-medium text-[var(--text-secondary)] w-12 text-right">{pct}%</span>
                <span className="text-sm text-[var(--text-secondary)] w-20 text-right">{cat.count} items</span>
              </div>
            )
          })}
        </div>
      </div>

      {/* Refund Rules Section */}
      <div className="enterprise-card p-5">
        <div className="flex items-center gap-2 mb-4">
          <Settings className="w-5 h-5 text-[var(--text-secondary)]" />
          <h3 className="font-semibold text-[var(--text-primary)]">Refund Rules Configuration</h3>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-emerald-50 dark:bg-emerald-900/20 rounded-xl p-4 border border-emerald-200 dark:border-emerald-800">
            <div className="flex items-center gap-2 mb-2">
              <Check className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <h4 className="font-medium text-emerald-800 dark:text-emerald-200 text-sm">Automatic Refund</h4>
            </div>
            <p className="text-xs text-emerald-600 dark:text-emerald-400">Triggered on RMA approval + item receipt</p>
            <div className="mt-3 flex items-center justify-between text-sm">
              <span className="text-emerald-700 dark:text-emerald-300">Status</span>
              <span className="font-semibold text-emerald-800 dark:text-emerald-200 bg-emerald-100 dark:bg-emerald-900/40 px-2 py-0.5 rounded text-xs">Active</span>
            </div>
          </div>

          <div className="bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20 rounded-xl p-4 border border-[var(--nexus-warning-200)] dark:border-[var(--nexus-warning-800)]">
            <div className="flex items-center gap-2 mb-2">
              <TrendingDown className="w-4 h-4 text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]" />
              <h4 className="font-medium text-[var(--nexus-warning-800)] dark:text-[var(--nexus-warning-200)] text-sm">Partial Refund Rules</h4>
            </div>
            <p className="text-xs text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]">Grade-based: A=100%, B=85%, C=70%, D=50%, F=25%</p>
            <div className="mt-2 flex gap-1.5 flex-wrap">
              {[['A', '100%'], ['B', '85%'], ['C', '70%'], ['D', '50%'], ['F', '25%']].map(([g, p]) => (
                <span key={g} className="text-xs bg-[var(--nexus-warning-100)] dark:bg-[var(--nexus-warning-900)]/40 text-[var(--nexus-warning-700)] dark:text-[var(--nexus-warning-300)] px-1.5 py-0.5 rounded font-medium">{g}: {p}</span>
              ))}
            </div>
          </div>

          <div className="bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/20 rounded-xl p-4 border border-[var(--nexus-primary-200)] dark:border-[var(--nexus-primary-800)]">
            <div className="flex items-center gap-2 mb-2">
              <DollarSign className="w-4 h-4 text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]" />
              <h4 className="font-medium text-[var(--nexus-primary-800)] dark:text-[var(--nexus-primary-200)] text-sm">Restocking Fee</h4>
            </div>
            <p className="text-xs text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]">15% fee for non-defective returns within 30 days</p>
            <div className="mt-3 flex items-center justify-between text-sm">
              <span className="text-[var(--nexus-primary-700)] dark:text-[var(--nexus-primary-300)]">Fee Rate</span>
              <span className="font-semibold text-[var(--nexus-primary-800)] dark:text-[var(--nexus-primary-200)] bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)]/40 px-2 py-0.5 rounded text-xs">15%</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )

  const renderAnalyticsTab = () => (
    <div className="space-y-6">
      {/* Return Reasons Breakdown */}
      <div className="enterprise-card p-5">
        <h3 className="font-semibold text-[var(--text-primary)] mb-4">Return Reasons Breakdown</h3>
        <div className="space-y-3">
          {RETURN_REASONS_BREAKDOWN.map(item => (
            <HorizontalBar key={item.reason} label={item.reason} value={item.count} max={RETURN_REASONS_BREAKDOWN[0].count}
              color="bg-[var(--nexus-primary-50)]0" />
          ))}
        </div>
      </div>

      {/* Return Trend */}
      <div className="enterprise-card p-5">
        <h3 className="font-semibold text-[var(--text-primary)] mb-4">Monthly Return Trend</h3>
        <div className="overflow-x-auto">
          <div className="flex items-end gap-2 min-w-[600px] h-40">
            {MONTHLY_RETURN_TREND.map(m => {
              const maxReturns = Math.max(...MONTHLY_RETURN_TREND.map(x => x.returns))
              const h = maxReturns > 0 ? (m.returns / maxReturns) * 100 : 0
              const rate = (m.returns / m.totalOrders) * 100
              return (
                <div key={m.month} className="flex-1 flex flex-col items-center gap-1">
                  <span className="text-[10px] text-[var(--text-tertiary)]">{rate.toFixed(1)}%</span>
                  <div className="w-full bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)]/30 rounded-t relative" style={{ height: `${h}%` }}>
                    <div className="absolute inset-x-0 bottom-0 bg-[var(--nexus-primary-50)]0 dark:bg-[var(--nexus-primary-400)] rounded-t transition-all"
                      style={{ height: `${h}%` }} />
                  </div>
                  <span className="text-[10px] text-[var(--text-secondary)] mt-1">{m.month}</span>
                </div>
              )
            })}
          </div>
        </div>
        <div className="flex items-center justify-between mt-4 pt-3 border-t border-[var(--border-subtle)]">
          <div className="flex items-center gap-4 text-xs text-[var(--text-secondary)]">
            <span className="flex items-center gap-1"><TrendingUp className="w-3 h-3 text-emerald-500" /> Trend: +12.3% YoY</span>
            <span>Avg Return Rate: <strong className="text-[var(--text-secondary)]">2.4%</strong></span>
          </div>
          <SparklineChart data={MONTHLY_RETURN_TREND.map(m => m.returns)} />
        </div>
      </div>

      {/* Top Returned Products & Recovery Value */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Top Returned Products */}
        <div className="enterprise-card p-5">
          <h3 className="font-semibold text-[var(--text-primary)] mb-4">Top Returned Products</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--border-subtle)]">
                  <th className="text-left pb-2 font-semibold text-xs text-[var(--text-secondary)] uppercase tracking-wider">Product</th>
                  <th className="text-right pb-2 font-semibold text-xs text-[var(--text-secondary)] uppercase tracking-wider">Returns</th>
                  <th className="text-right pb-2 font-semibold text-xs text-[var(--text-secondary)] uppercase tracking-wider">Rate</th>
                  <th className="text-right pb-2 font-semibold text-xs text-[var(--text-secondary)] uppercase tracking-wider">Lost Revenue</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                {TOP_RETURNED_PRODUCTS.map(p => (
                  <tr key={p.sku} className="text-sm">
                    <td className="py-2.5">
                      <div className="flex flex-col">
                        <span className="font-medium text-[var(--text-primary)]">{p.name}</span>
                        <span className="text-xs text-[var(--text-tertiary)] font-mono">{p.sku}</span>
                      </div>
                    </td>
                    <td className="py-2.5 text-right font-medium text-[var(--text-primary)]">{p.returns}</td>
                    <td className="py-2.5 text-right">
                      <span className="text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)] font-medium">{p.returnRate}%</span>
                    </td>
                    <td className="py-2.5 text-right font-medium text-[var(--text-primary)]">${p.revenueLost.toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Recovery Value by Disposition */}
        <div className="enterprise-card p-5">
          <h3 className="font-semibold text-[var(--text-primary)] mb-4">Recovery Value by Disposition</h3>
          <div className="space-y-4">
            {RECOVERY_BY_DISPOSITION.map(d => (
              <div key={d.disposition}>
                <div className="flex items-center justify-between text-sm mb-1">
                  <span className="text-[var(--text-secondary)] font-medium">{d.disposition}</span>
                  <div className="flex items-center gap-3">
                    <span className="text-[var(--text-secondary)]">{d.items} items</span>
                    {d.recoveryValue > 0 && <span className="font-semibold text-[var(--text-primary)]">${d.recoveryValue.toLocaleString()}</span>}
                    <span className={clsx('text-xs font-medium', d.recoveryRate >= 50 ? 'text-emerald-600' : 'text-[var(--text-tertiary)]')}>{d.recoveryRate}%</span>
                  </div>
                </div>
                <div className="h-2 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                  <div className={clsx('h-full rounded-full', d.disposition === 'Restock' ? 'bg-emerald-500' : d.disposition === 'Refurbish' ? 'bg-[var(--nexus-primary-50)]0' : d.disposition === 'Donate' ? 'bg-pink-500' : d.disposition === 'Recycle' ? 'bg-[var(--nexus-info-50)]0' : 'bg-[var(--surface-muted)]')}
                    style={{ width: `${d.recoveryRate}%` }} />
                </div>
              </div>
            ))}
          </div>
          <div className="mt-4 pt-3 border-t border-[var(--border-subtle)] flex items-center justify-between text-sm">
            <span className="text-[var(--text-secondary)]">Total Recovery Value</span>
            <span className="font-bold text-lg text-[var(--text-primary)]">$15,820</span>
          </div>
        </div>
      </div>

      {/* Return Rate by Channel */}
      <div className="enterprise-card p-5">
        <h3 className="font-semibold text-[var(--text-primary)] mb-4">Return Rate by Channel</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          {RETURN_RATE_BY_CHANNEL.map(ch => {
            const avg = RETURN_RATE_BY_CHANNEL.reduce((s, c) => s + c.rate, 0) / RETURN_RATE_BY_CHANNEL.length
            const aboveAvg = ch.rate > avg
            return (
              <div key={ch.channel} className="bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 rounded-xl p-4 text-center">
                <p className="text-sm text-[var(--text-secondary)] mb-1">{ch.channel}</p>
                <p className={clsx('text-2xl font-bold', aboveAvg ? 'text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]' : 'text-emerald-600 dark:text-emerald-400')}>
                  {ch.rate}%
                </p>
                <div className="flex items-center justify-center gap-1 mt-1">
                  {aboveAvg
                    ? <ArrowUpRight className="w-3 h-3 text-[var(--nexus-error-500)]" />
                    : <ArrowDownRight className="w-3 h-3 text-emerald-500" />}
                  <span className={clsx('text-xs', aboveAvg ? 'text-[var(--nexus-error-500)]' : 'text-emerald-500')}>
                    {aboveAvg ? '+' : ''}{(ch.rate - avg).toFixed(2)}% vs avg
                  </span>
                </div>
                <p className="text-xs text-[var(--text-tertiary)] mt-1">{ch.returns} returns / {ch.orders.toLocaleString()} orders</p>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )

  return (
    <div className="space-y-4">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Returns' }, { label: 'Command Center' }]} />

      {/* Header */}
      <div className="flex items-center gap-4 py-2">
        <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-violet-500 to-blue-600 flex items-center justify-center ring-4 ring-violet-100 dark:ring-violet-900/30">
          <RotateCcw className="w-6 h-6 text-white" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Returns Command Center</h1>
          <p className="text-sm text-[var(--text-secondary)]">RMA lifecycle, disposition automation & return analytics</p>
        </div>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        <EnterpriseKPICard title="Return Rate" value="2.1%" subtitle="vs 1.8% target" icon={<TrendingUp className="w-5 h-5" />} color="warning" trend="up" trendValue="+0.3%" />
        <EnterpriseKPICard title="Pending RMA" value="23" icon={<ClipboardList className="w-5 h-5" />} color="warning" />
        <EnterpriseKPICard title="In Transit" value="12" icon={<Truck className="w-5 h-5" />} color="info" />
        <EnterpriseKPICard title="Inspected Today" value="47" icon={<Eye className="w-5 h-5" />} color="ai" />
        <EnterpriseKPICard title="Refund Amount Today" value="$3,240" icon={<DollarSign className="w-5 h-5" />} color="success" />
        <EnterpriseKPICard title="Recovery Rate" value="68%" subtitle="of return value" icon={<RefreshCw className="w-5 h-5" />} color="primary" trend="up" trendValue="+5%" />
      </div>

      {/* Tabs */}
      <EnterpriseTabs tabs={TABS} activeTab={activeTab} onChange={setActiveTab} variant="pills" />

      {/* Search Bar */}
      {activeTab === 'RMA_QUEUE' && (
        <Autocomplete value={search} onChange={setSearch} placeholder="Search RMA, customer, order..." minChars={0} className="max-w-md" />
      )}

      {/* Tab Content */}
      {activeTab === 'RMA_QUEUE' && renderRmaQueueTab()}
      {activeTab === 'INSPECTION' && renderInspectionTab()}
      {activeTab === 'DISPOSITION' && renderDispositionTab()}
      {activeTab === 'ANALYTICS' && renderAnalyticsTab()}

      {/* Create RMA Modal */}
      {createOpen && (
        <div className="enterprise-modal-overlay" onClick={() => setCreateOpen(false)}>
          <div className="enterprise-modal max-w-md" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-[var(--text-primary)]">Create RMA</h2>
              <button onClick={() => setCreateOpen(false)} className="p-1 text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] dark:hover:text-[var(--text-tertiary)] rounded-lg hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)]"><X className="w-5 h-5" /></button>
            </div>
            <div className="enterprise-modal-body space-y-4">
              <div className="enterprise-form-group">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Customer</label>
                <input type="text" className="enterprise-input" placeholder="Customer name" value={createForm.customer} onChange={e => setCreateForm(f => ({ ...f, customer: e.target.value }))} />
              </div>
              <div className="enterprise-form-group">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Order Number</label>
                <input type="text" className="enterprise-input" placeholder="ORD-XXXX" value={createForm.orderNumber} onChange={e => setCreateForm(f => ({ ...f, orderNumber: e.target.value }))} />
              </div>
              <div className="enterprise-form-group">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Reason Type</label>
                <select className="enterprise-select" value={createForm.reasonType} onChange={e => setCreateForm(f => ({ ...f, reasonType: e.target.value }))}>
                  {REASON_TYPES.map(rt => <option key={rt} value={rt}>{rt}</option>)}
                </select>
              </div>
              <div className="enterprise-form-group">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Reason Details</label>
                <textarea className="enterprise-textarea" rows={2} placeholder="Describe the return reason..." value={createForm.reason} onChange={e => setCreateForm(f => ({ ...f, reason: e.target.value }))} />
              </div>
              <div className="enterprise-form-group">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Estimated Value ($)</label>
                <input type="number" step="0.01" className="enterprise-input" placeholder="0.00" value={createForm.value || ''} onChange={e => setCreateForm(f => ({ ...f, value: parseFloat(e.target.value) || 0 }))} />
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button onClick={() => setCreateOpen(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="orders" action="create">
                <button onClick={async () => { await handleCreateReturn(createForm); setCreateOpen(false); setCreateForm({ customer: '', orderNumber: '', reason: '', reasonType: 'Defective', value: 0 }) }} disabled={!createForm.customer && !createForm.orderNumber}
                  className="enterprise-btn enterprise-btn-primary disabled:opacity-50">
                  <Plus className="w-4 h-4" /> Create RMA
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {/* Confirm Action Modal */}
      {confirmAction && (
        <div className="enterprise-modal-overlay" onClick={() => setConfirmAction(null)}>
          <div className="enterprise-modal max-w-sm" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-[var(--text-primary)]">
                {confirmAction.type === 'approve' ? 'Approve RMA' : 'Reject RMA'}
              </h2>
              <button onClick={() => setConfirmAction(null)} className="p-1 text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] dark:hover:text-[var(--text-tertiary)] rounded-lg hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)]"><X className="w-5 h-5" /></button>
            </div>
            <div className="enterprise-modal-body space-y-4">
              <p className="text-sm text-[var(--text-secondary)]">
                {confirmAction.type === 'approve'
                  ? `Are you sure you want to approve ${confirmAction.rma.rmaNumber}?`
                  : `Are you sure you want to reject ${confirmAction.rma.rmaNumber}?`}
              </p>
              {confirmAction.type === 'reject' && (
                <div className="enterprise-form-group">
                  <label className="text-sm font-medium text-[var(--text-secondary)]">Rejection Reason</label>
                  <textarea className="enterprise-textarea" rows={3} placeholder="Why is this RMA being rejected?" value={rejectReason} onChange={e => setRejectReason(e.target.value)} />
                </div>
              )}
            </div>
            <div className="enterprise-modal-footer">
              <button onClick={() => setConfirmAction(null)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              {confirmAction.type === 'approve' ? (
                <PermissionGate resource="orders" action="edit">
                  <button onClick={() => handleApproveRma(confirmAction.rma)}
                    className="enterprise-btn bg-emerald-600 text-white hover:bg-emerald-700 border-none">
                    <Check className="w-4 h-4" /> Approve
                  </button>
                </PermissionGate>
              ) : (
                <PermissionGate resource="orders" action="edit">
                  <button onClick={() => handleRejectRma(confirmAction.rma)}
                    className="enterprise-btn bg-[var(--nexus-error-600)] text-white hover:bg-[var(--nexus-error-700)] border-none">
                    <X className="w-4 h-4" /> Reject
                  </button>
                </PermissionGate>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
