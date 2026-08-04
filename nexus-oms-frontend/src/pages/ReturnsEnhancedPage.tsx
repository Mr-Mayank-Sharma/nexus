import { useState, useMemo } from 'react'
import { clsx } from 'clsx'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  RotateCcw, Plus, X, Check, ChevronDown, ChevronRight,
  Eye, PackageCheck, DollarSign, ThumbsUp, AlertTriangle, Loader2,
  ClipboardList, ShieldCheck, BarChart3, Settings, TrendingUp,
  TrendingDown, Box, Recycle, Heart, Wrench, Trash2, Sparkles, Filter,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import { useAuth } from '../context/AuthContext'
import {
  getReturns, getReturnKPIs, createReturn, approveReturn, rejectReturn, inspectReturn,
} from '../api/returns'
import { fetchReturnAnalytics } from '../api/newBackend'

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
  orderId: string
  orderNumber: string
  customer: string
  reason: string
  status: string
  date: string
  value: number
  items: RmaItem[]
}

const STATUS_STEPS = ['REQUESTED', 'APPROVED', 'RECEIVED', 'INSPECTED', 'REFUNDED']

const STATUS_LABELS: Record<string, string> = {
  REQUESTED: 'Pending Approval',
  APPROVED: 'Authorized',
  RECEIVED: 'Received',
  INSPECTED: 'Inspected',
  REFUNDED: 'Refunded',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
}

const STATUS_BADGE: Record<string, string> = {
  REQUESTED: 'pending_approval',
  APPROVED: 'approved',
  RECEIVED: 'received',
  INSPECTED: 'inspected',
  REFUNDED: 'completed',
  REJECTED: 'rejected',
  CANCELLED: 'rejected',
}

const STATUS_COLORS: Record<string, string> = {
  REQUESTED: 'text-[var(--nexus-warning-600)] bg-[var(--nexus-warning-50)] ring-[var(--nexus-warning-500)]/20',
  APPROVED: 'text-[var(--nexus-primary-600)] bg-[var(--nexus-primary-50)] ring-[var(--nexus-primary-500)]/20',
  RECEIVED: 'text-[var(--text-secondary)] bg-[var(--surface-sunken)] ring-[var(--border-default)]/20',
  INSPECTED: 'text-[var(--nexus-info-600)] bg-[var(--nexus-info-50)] ring-[var(--nexus-info-500)]/20',
  REFUNDED: 'text-[var(--nexus-success-600)] bg-[var(--nexus-success-50)] ring-[var(--nexus-success-500)]/20',
  REJECTED: 'text-[var(--nexus-error-600)] bg-[var(--nexus-error-50)] ring-[var(--nexus-error-500)]/20',
  CANCELLED: 'text-[var(--text-tertiary)] bg-[var(--surface-sunken)] ring-[var(--border-default)]/20',
}

const CONDITION_OPTIONS: ItemCondition[] = ['EXCELLENT', 'GOOD', 'FAIR', 'POOR', 'DAMAGED']
const DISPOSITION_OPTIONS: Disposition[] = ['RESTOCK', 'REFURBISH', 'DONATE', 'RECYCLE', 'SCRAP']
const GRADE_OPTIONS: Grade[] = ['A', 'B', 'C', 'D', 'F']

const TABS = [
  { id: 'RMA_QUEUE', label: 'RMA Queue', icon: <ClipboardList className="w-4 h-4" /> },
  { id: 'INSPECTION', label: 'Inspection', icon: <Eye className="w-4 h-4" /> },
  { id: 'DISPOSITION', label: 'Disposition', icon: <ShieldCheck className="w-4 h-4" /> },
  { id: 'ANALYTICS', label: 'Analytics', icon: <BarChart3 className="w-4 h-4" /> },
]

const DISPOSITION_META: { key: Disposition; label: string; icon: typeof Box; color: string }[] = [
  { key: 'RESTOCK', label: 'Restock', icon: PackageCheck, color: 'text-[var(--nexus-success-600)] bg-[var(--nexus-success-50)] ring-[var(--nexus-success-500)]/20' },
  { key: 'REFURBISH', label: 'Refurbish', icon: Wrench, color: 'text-[var(--nexus-primary-600)] bg-[var(--nexus-primary-50)] ring-[var(--nexus-primary-500)]/20' },
  { key: 'DONATE', label: 'Donate', icon: Heart, color: 'text-[var(--nexus-primary-600)] bg-[var(--nexus-primary-50)] ring-[var(--nexus-primary-500)]/20' },
  { key: 'RECYCLE', label: 'Recycle', icon: Recycle, color: 'text-[var(--nexus-info-600)] bg-[var(--nexus-info-50)] ring-[var(--nexus-info-500)]/20' },
  { key: 'SCRAP', label: 'Scrap', icon: Trash2, color: 'text-[var(--text-secondary)] bg-[var(--surface-sunken)] ring-[var(--border-default)]/20' },
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

function suggestDisposition(condition: ItemCondition, grade: Grade): Disposition {
  if (condition === 'EXCELLENT' && (grade === 'A' || grade === 'B')) return 'RESTOCK'
  if (condition === 'GOOD' && grade === 'A') return 'RESTOCK'
  if (condition === 'GOOD') return 'REFURBISH'
  if (condition === 'FAIR') return 'REFURBISH'
  if (condition === 'POOR') return 'RECYCLE'
  return 'SCRAP'
}

function mapReturn(raw: any): RmaRecord {
  const items: RmaItem[] = Array.isArray(raw.items)
    ? raw.items.map((it: any, i: number) => ({
        id: it.id || `${raw.id}-item-${i}`,
        sku: it.sku || '—',
        productName: it.productName || '',
        quantity: it.quantity ?? 1,
        condition: it.condition || '',
        grade: it.grade || '',
        disposition: it.disposition || '',
        conditionNotes: it.conditionNotes || '',
        unitPrice: Number(it.unitPrice) || 0,
      }))
    : []
  const itemValue = items.reduce((s, it) => s + it.unitPrice * it.quantity, 0)
  return {
    id: raw.id,
    rmaNumber: raw.rmaNumber || `RMA-${String(raw.id).slice(0, 8).toUpperCase()}`,
    orderId: raw.orderId || '',
    orderNumber: raw.orderNumber || '—',
    customer: raw.customerName || '—',
    reason: raw.reason || '',
    status: raw.status || 'REQUESTED',
    date: raw.createdAt || new Date().toISOString(),
    value: raw.refundAmount != null ? Number(raw.refundAmount) : itemValue,
    items,
  }
}

function SparklineChart({ data, height = 32 }: { data: number[]; height?: number }) {
  if (!data.length) return null
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
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const { addToast } = useToast()

  const [activeTab, setActiveTab] = useState('RMA_QUEUE')
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [reasonFilter, setReasonFilter] = useState('ALL')
  const [expandedRma, setExpandedRma] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [confirmAction, setConfirmAction] = useState<{ type: 'approve' | 'reject'; rma: RmaRecord } | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [inspectionRma, setInspectionRma] = useState<RmaRecord | null>(null)
  const [inspectForm, setInspectForm] = useState<{ condition: ItemCondition; grade: Grade; disposition: Disposition; notes: string }>({
    condition: 'GOOD', grade: 'B', disposition: 'RESTOCK', notes: '',
  })
  const [createForm, setCreateForm] = useState<{
    orderId: string
    customerId: string
    reason: string
    items: { sku: string; productName: string; quantity: number; unitPrice: number }[]
  }>({ orderId: '', customerId: '', reason: '', items: [{ sku: '', productName: '', quantity: 1, unitPrice: 0 }] })

  const actorId = user?.id

  const { data: rawReturns = [], isLoading } = useQuery({
    queryKey: ['returns'],
    queryFn: async () => {
      const res = await getReturns()
      return Array.isArray(res?.data) ? res.data : []
    },
    refetchInterval: 60_000,
  })

  const { data: rawKpis = {} } = useQuery({
    queryKey: ['returns-kpis'],
    queryFn: async () => {
      const res = await getReturnKPIs()
      return (res?.data as Record<string, number>) ?? {}
    },
    refetchInterval: 60_000,
  })

  const { data: analytics = {} } = useQuery({
    queryKey: ['return-analytics'],
    queryFn: async () => {
      const res = await fetchReturnAnalytics()
      return (res?.data ?? res) || {}
    },
    refetchInterval: 60_000,
  })

  const rmas = useMemo<RmaRecord[]>(() => rawReturns.map(mapReturn), [rawReturns])

  const invalidateQueries = () => {
    queryClient.invalidateQueries({ queryKey: ['returns'] })
    queryClient.invalidateQueries({ queryKey: ['returns-kpis'] })
    queryClient.invalidateQueries({ queryKey: ['return-analytics'] })
  }

  const approveMutation = useMutation({
    mutationFn: (id: string) => approveReturn(id, actorId),
    onSuccess: (res) => {
      if (res?.success) {
        addToast({ type: 'success', title: 'RMA approved' })
        invalidateQueries()
      } else {
        addToast({ type: 'error', title: res?.error || 'Failed to approve return' })
      }
    },
  })

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => rejectReturn(id, reason),
    onSuccess: (res) => {
      if (res?.success) {
        addToast({ type: 'success', title: 'RMA rejected' })
        invalidateQueries()
      } else {
        addToast({ type: 'error', title: res?.error || 'Failed to reject return' })
      }
    },
  })

  const inspectMutation = useMutation({
    mutationFn: ({ id, items }: { id: string; items: RmaItem[] }) =>
      inspectReturn(id, items.map(it => ({
        sku: it.sku, productName: it.productName, quantity: it.quantity, unitPrice: it.unitPrice,
        condition: it.condition, grade: it.grade, disposition: it.disposition, conditionNotes: it.conditionNotes,
      })), actorId),
    onSuccess: (res) => {
      if (res?.success) {
        addToast({ type: 'success', title: 'Return inspected' })
        setInspectionRma(null)
        invalidateQueries()
      } else {
        addToast({ type: 'error', title: res?.error || 'Failed to inspect return' })
      }
    },
  })

  const createMutation = useMutation({
    mutationFn: () => createReturn({
      orderId: createForm.orderId,
      customerId: createForm.customerId,
      reason: createForm.reason,
      returnChannel: 'MANUAL',
      rmaType: 'RETURN',
      items: createForm.items.filter(it => it.sku),
    }),
    onSuccess: (res) => {
      if (res?.success) {
        addToast({ type: 'success', title: 'RMA created' })
        setCreateOpen(false)
        setCreateForm({ orderId: '', customerId: '', reason: '', items: [{ sku: '', productName: '', quantity: 1, unitPrice: 0 }] })
        invalidateQueries()
      } else {
        addToast({ type: 'error', title: res?.error || 'Failed to create return' })
      }
    },
  })

  const filteredRmas = useMemo(() => {
    let list = rmas
    if (search) {
      const q = search.toLowerCase()
      list = list.filter(r =>
        r.rmaNumber.toLowerCase().includes(q) ||
        r.customer.toLowerCase().includes(q) ||
        r.orderNumber.toLowerCase().includes(q) ||
        r.orderId.toLowerCase().includes(q) ||
        r.reason.toLowerCase().includes(q),
      )
    }
    if (statusFilter !== 'ALL') list = list.filter(r => r.status === statusFilter)
    if (reasonFilter !== 'ALL') list = list.filter(r => r.reason === reasonFilter)
    return list
  }, [rmas, search, statusFilter, reasonFilter])

  const receivedRmas = useMemo(() => rmas.filter(r => r.status === 'RECEIVED'), [rmas])

  const reasonOptions = useMemo(() =>
    Array.from(new Set(rmas.map(r => r.reason).filter(Boolean))),
  [rmas])

  const reasonBreakdown = useMemo(() => {
    const counts = new Map<string, number>()
    for (const r of rmas) if (r.reason) counts.set(r.reason, (counts.get(r.reason) || 0) + 1)
    return Array.from(counts.entries())
      .map(([reason, count]) => ({ reason, count }))
      .sort((a, b) => b.count - a.count)
  }, [rmas])

  const monthlyTrend = useMemo(() => {
    const counts = new Map<string, { key: string; month: string; returns: number }>()
    for (const r of rmas) {
      const d = new Date(r.date)
      if (isNaN(d.getTime())) continue
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
      const cur = counts.get(key) || { key, month: d.toLocaleString('en-US', { month: 'short' }), returns: 0 }
      cur.returns += 1
      counts.set(key, cur)
    }
    return Array.from(counts.values()).sort((a, b) => a.key.localeCompare(b.key))
  }, [rmas])

  const topProducts = useMemo(() => {
    const agg = new Map<string, { sku: string; name: string; returns: number; revenue: number }>()
    for (const r of rmas) {
      for (const it of r.items) {
        if (!it.sku || it.sku === '—') continue
        const cur = agg.get(it.sku) || { sku: it.sku, name: it.productName || it.sku, returns: 0, revenue: 0 }
        cur.returns += it.quantity || 1
        cur.revenue += it.unitPrice * (it.quantity || 1)
        agg.set(it.sku, cur)
      }
    }
    return Array.from(agg.values()).sort((a, b) => b.returns - a.returns).slice(0, 5)
  }, [rmas])

  const recoveryByDisposition = useMemo(() => {
    const agg = new Map<string, { items: number; value: number }>()
    for (const r of rmas) {
      const dispo = (r.items.find(it => it.disposition)?.disposition || '') as Disposition | ''
      if (!dispo) continue
      const cur = agg.get(dispo) || { items: 0, value: 0 }
      cur.items += r.items.reduce((s, it) => s + (it.quantity || 1), 0)
      cur.value += r.value || 0
      agg.set(dispo, cur)
    }
    return Array.from(agg.entries()).map(([key, v]) => ({ key: key as Disposition, items: v.items, value: v.value }))
  }, [rmas])

  const dispositionCounts = useMemo(() => {
    const counts = new Map<string, number>()
    for (const r of rmas) {
      const dispo = r.items.find(it => it.disposition)?.disposition
      if (dispo) counts.set(dispo, (counts.get(dispo) || 0) + 1)
    }
    return counts
  }, [rmas])

  const totalRecovery = useMemo(() => recoveryByDisposition.reduce((s, d) => s + d.value, 0), [recoveryByDisposition])

  const openInspection = (rma: RmaRecord) => {
    setInspectionRma(rma)
    setInspectForm({ condition: 'GOOD', grade: 'B', disposition: 'RESTOCK', notes: '' })
  }

  const handleInspectSubmit = () => {
    if (!inspectionRma) return
    const { condition, grade, disposition, notes } = inspectForm
    inspectMutation.mutate({
      id: inspectionRma.id,
      items: inspectionRma.items.map(item => ({ ...item, condition, grade, disposition, conditionNotes: notes })),
    })
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

  const renderRmaQueueTab = () => (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-2 bg-[var(--surface-base)] border border-[var(--border-default)] rounded-lg px-3 py-2">
          <Filter className="w-4 h-4 text-[var(--text-tertiary)]" />
          <select className="text-sm bg-transparent border-none outline-none text-[var(--text-secondary)]" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="ALL">All Statuses</option>
            {STATUS_STEPS.concat(['REJECTED', 'CANCELLED']).map(s => (
              <option key={s} value={s}>{STATUS_LABELS[s] || s.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </div>
        <div className="flex items-center gap-2 bg-[var(--surface-base)] border border-[var(--border-default)] rounded-lg px-3 py-2">
          <Filter className="w-4 h-4 text-[var(--text-tertiary)]" />
          <select className="text-sm bg-transparent border-none outline-none text-[var(--text-secondary)]" value={reasonFilter} onChange={e => setReasonFilter(e.target.value)}>
            <option value="ALL">All Reasons</option>
            {reasonOptions.map(rt => <option key={rt} value={rt}>{rt}</option>)}
          </select>
        </div>
        <div className="ml-auto">
          <PermissionGate resource="orders" action="create">
            <button type="button" onClick={() => setCreateOpen(true)} className="enterprise-btn enterprise-btn-primary flex items-center gap-1.5">
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
              <tr className="border-b border-[var(--border-default)] bg-[var(--surface-sunken)]/50">
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
            <tbody className="divide-y divide-[var(--surface-sunken)] dark:divide-gray-700/50">
              {isLoading ? (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center">
                    <Loader2 className="w-6 h-6 animate-spin mx-auto text-[var(--text-tertiary)]" />
                  </td>
                </tr>
              ) : filteredRmas.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-[var(--text-tertiary)]">
                    {rmas.length === 0 ? 'No returns found' : 'No RMAs match the current filters'}
                  </td>
                </tr>
              ) : (
                filteredRmas.map(rma => (
                  <tr key={rma.id} className={clsx('group hover:bg-[var(--surface-sunken)] transition-colors', expandedRma === rma.id && 'bg-[var(--surface-sunken)]/30')}>
                    <td className="px-4 py-3">
                      <button type="button" onClick={() => setExpandedRma(expandedRma === rma.id ? null : rma.id)} className="flex items-center gap-1.5 font-mono text-sm font-medium text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)] hover:underline">
                        {expandedRma === rma.id ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
                        {rma.rmaNumber}
                      </button>
                    </td>
                    <td className="px-4 py-3 font-mono text-sm text-[var(--text-secondary)]">{rma.orderNumber}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{rma.customer}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)] max-w-[180px] truncate">{rma.reason || '—'}</td>
                    <td className="px-4 py-3">
                      <EnterpriseStatusBadge status={STATUS_BADGE[rma.status] || 'neutral'} label={STATUS_LABELS[rma.status] || rma.status.replace(/_/g, ' ')} />
                    </td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{new Date(rma.date).toLocaleDateString()}</td>
                    <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)] text-right">{rma.value > 0 ? `$${rma.value.toFixed(2)}` : '—'}</td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {rma.status === 'REQUESTED' && (
                          <>
                            <PermissionGate resource="orders" action="edit">
                              <button type="button" onClick={() => setConfirmAction({ type: 'approve', rma })}
                                className="w-7 h-7 flex items-center justify-center rounded-lg bg-[var(--nexus-success-50)] text-[var(--nexus-success-600)] hover:bg-[var(--nexus-success-100)] dark:bg-[var(--nexus-success-900)]/20 dark:text-[var(--nexus-success-400)] dark:hover:bg-[var(--nexus-success-900)]/30 transition-colors"
                                title="Approve"><Check className="w-4 h-4" /></button>
                            </PermissionGate>
                            <PermissionGate resource="orders" action="edit">
                              <button type="button" onClick={() => setConfirmAction({ type: 'reject', rma })}
                                className="w-7 h-7 flex items-center justify-center rounded-lg bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)] hover:bg-[var(--nexus-error-100)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-400)] dark:hover:bg-[var(--nexus-error-900)]/30 transition-colors"
                                title="Reject"><X className="w-4 h-4" /></button>
                            </PermissionGate>
                          </>
                        )}
                        {rma.status === 'APPROVED' && (
                          <span className="text-xs text-[var(--text-tertiary)] italic">Awaiting receipt</span>
                        )}
                        {rma.status === 'RECEIVED' && (
                          <PermissionGate resource="orders" action="edit">
                            <button type="button" onClick={() => openInspection(rma)}
                              className="enterprise-btn enterprise-btn-sm bg-[var(--nexus-primary-600)] text-white hover:bg-[var(--nexus-primary-700)] border-none">
                              <Eye className="w-3 h-3" /> Inspect
                            </button>
                          </PermissionGate>
                        )}
                        {(rma.status === 'INSPECTED' || rma.status === 'REFUNDED') && (
                          <span className="text-xs text-[var(--nexus-success-600)] font-medium">Done</span>
                        )}
                        {(rma.status === 'REJECTED' || rma.status === 'CANCELLED') && (
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
                    ['Date Created', new Date(rma.date).toLocaleDateString()],
                    ['Total Value', rma.value > 0 ? `$${rma.value.toFixed(2)}` : '—'],
                  ].map(([k, v]) => (
                    <div key={k} className="flex justify-between py-1.5 px-3 bg-[var(--surface-sunken)]/50 rounded-lg">
                      <span className="text-[var(--text-secondary)]">{k}</span>
                      <span className="font-medium text-[var(--text-primary)]">{v}</span>
                    </div>
                  ))}
                </dl>
              </div>
              <div>
                <h4 className="font-semibold text-[var(--text-primary)] mb-3">Items ({rma.items.length})</h4>
                <div className="space-y-2">
                  {rma.items.length === 0 && <p className="text-sm text-[var(--text-tertiary)]">No line items recorded</p>}
                  {rma.items.map((item, i) => (
                    <div key={item.id || i} className="bg-[var(--surface-sunken)]/50 rounded-lg p-3 space-y-1.5">
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
                  {STATUS_STEPS.map((s, i) => {
                    const statusIdx = STATUS_STEPS.indexOf(rma.status)
                    const done = statusIdx >= 0 && i <= statusIdx
                    const current = statusIdx === i
                    return (
                      <div key={s} className="flex items-center gap-3">
                        <div className={clsx('w-2.5 h-2.5 rounded-full ring-2 shrink-0', done ? 'bg-[var(--nexus-success-500)] ring-[var(--nexus-success-200)] dark:ring-[var(--nexus-success-800)]' : 'bg-[var(--surface-muted)] ring-[var(--surface-sunken)] dark:ring-[var(--border-default)]')} />
                        <span className={clsx('text-xs', current ? 'font-semibold text-[var(--text-primary)]' : done ? 'text-[var(--text-secondary)]' : 'text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]')}>
                          {STATUS_LABELS[s] || s.replace(/_/g, ' ')}
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
              <button type="button" onClick={() => setInspectionRma(null)} className="text-sm text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)] hover:underline flex items-center gap-1 mb-2">
                <ChevronDown className="w-4 h-4 rotate-90" /> Back to queue
              </button>
              <h3 className="text-lg font-bold text-[var(--text-primary)]">Inspect: {inspectionRma.rmaNumber}</h3>
              <p className="text-sm text-[var(--text-secondary)]">{inspectionRma.customer} &middot; {inspectionRma.orderNumber}</p>
            </div>
          </div>

          <div className="enterprise-card p-5">
            <div className="space-y-5">
              {/* Item Info */}
              <div className="bg-[var(--surface-sunken)]/50 rounded-xl p-4">
                {inspectionRma.items.length === 0 ? (
                  <p className="text-sm text-[var(--text-tertiary)]">No line items recorded for this return</p>
                ) : (
                  <>
                    <div className="flex items-center justify-between mb-2">
                      <span className="font-mono font-semibold text-[var(--text-primary)]">{inspectionRma.items[0].sku}</span>
                      <span className="text-sm text-[var(--text-secondary)]">x{inspectionRma.items[0].quantity}</span>
                    </div>
                    <p className="text-sm text-[var(--text-secondary)]">{inspectionRma.items[0].productName}</p>
                  </>
                )}
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
                      <button type="button" key={g} onClick={() => handleGradeChange(g)}
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
              <div className="bg-gradient-to-r from-[var(--nexus-primary-50)] to-[var(--nexus-info-50)] dark:from-[var(--nexus-primary-900)]/20 dark:to-[var(--nexus-primary-900)]/20 rounded-xl p-4 border border-[var(--nexus-primary-200)] dark:border-[var(--nexus-primary-800)]">
                <div className="flex items-center gap-2 mb-2">
                  <Sparkles className="w-4 h-4 text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]" />
                  <span className="text-sm font-semibold text-[var(--nexus-primary-700)] dark:text-[var(--nexus-primary-300)]">AI Disposition Suggestion</span>
                </div>
                <div className="flex items-center gap-3">
                  {DISPOSITION_OPTIONS.map(d => (
                    <button type="button" key={d} onClick={() => setInspectForm(f => ({ ...f, disposition: d }))}
                      className={clsx('px-3 py-1.5 rounded-lg text-xs font-medium border transition-all',
                        inspectForm.disposition === d
                          ? 'bg-[var(--nexus-primary-600)] text-white border-[var(--nexus-primary-600)]'
                          : 'bg-[var(--surface-base)] text-[var(--text-secondary)] border-[var(--border-default)] hover:border-[var(--nexus-primary-300)]'
                      )}>
                      {d}
                    </button>
                  ))}
                </div>
                <p className="text-xs text-[var(--nexus-primary-500)] dark:text-[var(--nexus-primary-400)] mt-2">
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
                <button type="button" onClick={() => setInspectionRma(null)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
                <PermissionGate resource="orders" action="edit">
                  <button type="button" onClick={handleInspectSubmit} disabled={inspectMutation.isPending || inspectionRma.items.length === 0}
                    className="enterprise-btn bg-[var(--nexus-primary-600)] text-white hover:bg-[var(--nexus-primary-700)] border-none flex items-center gap-1.5 disabled:opacity-50">
                    {inspectMutation.isPending
                      ? <Loader2 className="w-4 h-4 animate-spin" />
                      : <Eye className="w-4 h-4" />} Inspect & Submit
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
                      <p className="text-sm text-[var(--text-secondary)] mt-0.5">{rma.customer} &middot; {rma.orderNumber} &middot; {rma.items[0]?.productName || '—'}</p>
                      <p className="text-xs text-[var(--text-tertiary)] mt-0.5">Reason: {rma.reason}</p>
                    </div>
                  </div>
                  <PermissionGate resource="orders" action="edit">
                    <button type="button" onClick={() => openInspection(rma)}
                      className="enterprise-btn enterprise-btn-sm bg-[var(--nexus-primary-600)] text-white hover:bg-[var(--nexus-primary-700)] border-none shrink-0 ml-4">
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

  const renderDispositionTab = () => {
    const totalItems = rmas.length
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
          {DISPOSITION_META.map(cat => {
            const count = dispositionCounts.get(cat.key) || 0
            const value = recoveryByDisposition.find(d => d.key === cat.key)?.value || 0
            return (
              <div key={cat.key} className="enterprise-card p-5 hover:shadow-md transition-shadow">
                <div className={clsx('w-12 h-12 rounded-xl flex items-center justify-center ring-1 mb-4', cat.color)}>
                  <cat.icon className="w-6 h-6" />
                </div>
                <h3 className="text-lg font-bold text-[var(--text-primary)]">{count}</h3>
                <p className="text-sm text-[var(--text-secondary)]">{cat.label}</p>
                {value > 0 && <p className="text-xs text-[var(--text-tertiary)] mt-1">${value.toLocaleString()} recovery value</p>}
                <p className="text-xs text-[var(--text-tertiary)] mt-1">assigned via inspection</p>
              </div>
            )
          })}
        </div>

        <div className="enterprise-card p-5">
          <h3 className="font-semibold text-[var(--text-primary)] mb-4">Disposition Summary</h3>
          <div className="space-y-3">
            {DISPOSITION_META.map(cat => {
              const count = dispositionCounts.get(cat.key) || 0
              const pct = totalItems > 0 ? Math.round((count / totalItems) * 100) : 0
              return (
                <div key={cat.key} className="flex items-center gap-4">
                  <div className={clsx('w-2 h-2 rounded-full shrink-0', cat.key === 'RESTOCK' ? 'bg-[var(--nexus-success-500)]' : cat.key === 'REFURBISH' ? 'bg-[var(--nexus-primary-500)]' : cat.key === 'DONATE' ? 'bg-[var(--nexus-primary-500)]' : cat.key === 'RECYCLE' ? 'bg-[var(--nexus-info-500)]' : 'bg-[var(--surface-muted)]')} />
                  <span className="text-sm text-[var(--text-secondary)] w-20">{cat.label}</span>
                  <div className="flex-1 h-6 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                    <div className={clsx('h-full rounded-full transition-all', cat.key === 'RESTOCK' ? 'bg-[var(--nexus-success-500)]' : cat.key === 'REFURBISH' ? 'bg-[var(--nexus-primary-500)]' : cat.key === 'DONATE' ? 'bg-[var(--nexus-primary-500)]' : cat.key === 'RECYCLE' ? 'bg-[var(--nexus-info-500)]' : 'bg-[var(--surface-muted)]')}
                      style={{ width: `${pct}%` }} />
                  </div>
                  <span className="text-sm font-medium text-[var(--text-secondary)] w-12 text-right">{pct}%</span>
                  <span className="text-sm text-[var(--text-secondary)] w-20 text-right">{count} returns</span>
                </div>
              )
            })}
          </div>
          {totalItems === 0 && (
            <p className="text-sm text-[var(--text-tertiary)] mt-4">No returns have been dispositioned yet. Inspect received returns to assign dispositions.</p>
          )}
        </div>

        {/* Refund Rules Section */}
        <div className="enterprise-card p-5">
          <div className="flex items-center gap-2 mb-4">
            <Settings className="w-5 h-5 text-[var(--text-secondary)]" />
            <h3 className="font-semibold text-[var(--text-primary)]">Refund Rules Configuration</h3>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 rounded-xl p-4 border border-[var(--nexus-success-200)] dark:border-[var(--nexus-success-800)]">
              <div className="flex items-center gap-2 mb-2">
                <Check className="w-4 h-4 text-[var(--nexus-success-600)] dark:text-[var(--nexus-success-400)]" />
                <h4 className="font-medium text-[var(--nexus-success-800)] dark:text-[var(--nexus-success-200)] text-sm">Automatic Refund</h4>
              </div>
              <p className="text-xs text-[var(--nexus-success-600)] dark:text-[var(--nexus-success-400)]">Triggered on RMA approval + item receipt</p>
              <div className="mt-3 flex items-center justify-between text-sm">
                <span className="text-[var(--nexus-success-700)] dark:text-[var(--nexus-success-300)]">Status</span>
                <span className="font-semibold text-[var(--nexus-success-800)] dark:text-[var(--nexus-success-200)] bg-[var(--nexus-success-100)] dark:bg-[var(--nexus-success-900)]/40 px-2 py-0.5 rounded text-xs">Active</span>
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
  }

  const renderAnalyticsTab = () => (
    <div className="space-y-6">
      {/* Return Reasons Breakdown */}
      <div className="enterprise-card p-5">
        <h3 className="font-semibold text-[var(--text-primary)] mb-4">Return Reasons Breakdown</h3>
        {reasonBreakdown.length === 0 ? (
          <p className="text-sm text-[var(--text-tertiary)]">No return reasons recorded yet.</p>
        ) : (
          <div className="space-y-3">
            {reasonBreakdown.map(item => (
              <HorizontalBar key={item.reason} label={item.reason} value={item.count} max={reasonBreakdown[0].count}
                color="bg-[var(--nexus-primary-500)]" />
            ))}
          </div>
        )}
      </div>

      {/* Return Trend */}
      <div className="enterprise-card p-5">
        <h3 className="font-semibold text-[var(--text-primary)] mb-4">Monthly Return Trend</h3>
        {monthlyTrend.length === 0 ? (
          <p className="text-sm text-[var(--text-tertiary)]">No return data to chart yet.</p>
        ) : (
          <>
            <div className="overflow-x-auto">
              <div className="flex items-end gap-2 min-w-[600px] h-40">
                {monthlyTrend.map(m => {
                  const maxReturns = Math.max(...monthlyTrend.map(x => x.returns))
                  const h = maxReturns > 0 ? (m.returns / maxReturns) * 100 : 0
                  return (
                    <div key={m.key} className="flex-1 flex flex-col items-center gap-1">
                      <span className="text-[10px] text-[var(--text-tertiary)]">{m.returns}</span>
                      <div className="w-full bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)]/30 rounded-t relative" style={{ height: `${h}%` }}>
                        <div className="absolute inset-x-0 bottom-0 bg-[var(--nexus-primary-500)] dark:bg-[var(--nexus-primary-400)] rounded-t transition-all"
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
                <span>Total Returns: <strong className="text-[var(--text-primary)]">{analytics.totalReturns != null ? analytics.totalReturns : rmas.length}</strong></span>
                {analytics.avgRefundAmount != null && (
                  <span>Avg Refund: <strong className="text-[var(--text-primary)]">${Number(analytics.avgRefundAmount).toFixed(2)}</strong></span>
                )}
                {analytics.returnRate != null && (
                  <span>Return Rate: <strong className="text-[var(--text-primary)]">{(Number(analytics.returnRate) * 100).toFixed(1)}%</strong></span>
                )}
              </div>
              <SparklineChart data={monthlyTrend.map(m => m.returns)} />
            </div>
          </>
        )}
      </div>

      {/* Top Returned Products & Recovery Value */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Top Returned Products */}
        <div className="enterprise-card p-5">
          <h3 className="font-semibold text-[var(--text-primary)] mb-4">Top Returned Products</h3>
          {topProducts.length === 0 ? (
            <p className="text-sm text-[var(--text-tertiary)]">No returned products recorded yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-[var(--border-subtle)]">
                    <th className="text-left pb-2 font-semibold text-xs text-[var(--text-secondary)] uppercase tracking-wider">Product</th>
                    <th className="text-right pb-2 font-semibold text-xs text-[var(--text-secondary)] uppercase tracking-wider">Units</th>
                    <th className="text-right pb-2 font-semibold text-xs text-[var(--text-secondary)] uppercase tracking-wider">Est. Value</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--surface-sunken)] dark:divide-gray-700/50">
                  {topProducts.map(p => (
                    <tr key={p.sku} className="text-sm">
                      <td className="py-2.5">
                        <div className="flex flex-col">
                          <span className="font-medium text-[var(--text-primary)]">{p.name}</span>
                          <span className="text-xs text-[var(--text-tertiary)] font-mono">{p.sku}</span>
                        </div>
                      </td>
                      <td className="py-2.5 text-right font-medium text-[var(--text-primary)]">{p.returns}</td>
                      <td className="py-2.5 text-right font-medium text-[var(--text-primary)]">${p.revenue.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Recovery Value by Disposition */}
        <div className="enterprise-card p-5">
          <h3 className="font-semibold text-[var(--text-primary)] mb-4">Recovery Value by Disposition</h3>
          {recoveryByDisposition.length === 0 ? (
            <p className="text-sm text-[var(--text-tertiary)]">No dispositioned returns to chart yet.</p>
          ) : (
            <>
              <div className="space-y-4">
                {recoveryByDisposition.map(d => {
                  const totalItems = recoveryByDisposition.reduce((s, x) => s + x.items, 0)
                  const pct = totalItems > 0 ? Math.round((d.items / totalItems) * 100) : 0
                  return (
                    <div key={d.key}>
                      <div className="flex items-center justify-between text-sm mb-1">
                        <span className="text-[var(--text-secondary)] font-medium">{DISPOSITION_LABELS[d.key] || d.key}</span>
                        <div className="flex items-center gap-3">
                          <span className="text-[var(--text-secondary)]">{d.items} items</span>
                          {d.value > 0 && <span className="font-semibold text-[var(--text-primary)]">${d.value.toLocaleString()}</span>}
                          <span className={clsx('text-xs font-medium', pct > 0 ? 'text-[var(--nexus-primary-600)]' : 'text-[var(--text-tertiary)]')}>{pct}%</span>
                        </div>
                      </div>
                      <div className="h-2 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                        <div className={clsx('h-full rounded-full', d.key === 'RESTOCK' ? 'bg-[var(--nexus-success-500)]' : d.key === 'REFURBISH' ? 'bg-[var(--nexus-primary-500)]' : d.key === 'RECYCLE' ? 'bg-[var(--nexus-info-500)]' : 'bg-[var(--surface-muted)]')}
                          style={{ width: `${pct}%` }} />
                      </div>
                    </div>
                  )
                })}
              </div>
              <div className="mt-4 pt-3 border-t border-[var(--border-subtle)] flex items-center justify-between text-sm">
                <span className="text-[var(--text-secondary)]">Total Recovery Value</span>
                <span className="font-bold text-lg text-[var(--text-primary)]">${totalRecovery.toLocaleString()}</span>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Return Rate by Channel */}
      <div className="enterprise-card p-5">
        <h3 className="font-semibold text-[var(--text-primary)] mb-4">Return Rate by Channel</h3>
        <div className="enterprise-empty-state">
          <AlertTriangle className="w-10 h-10" />
          <h3>Channel-level return rates unavailable</h3>
          <p>Returns are not tracked by sales channel yet. Once channel data is attached to returns, per-channel rates will appear here.</p>
        </div>
      </div>
    </div>
  )

  return (
    <div className="space-y-4">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Returns' }, { label: 'Command Center' }]} />

      {/* Header */}
      <div className="flex items-center gap-4 py-2">
        <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-[var(--nexus-primary-500)] to-[var(--nexus-primary-600)] flex items-center justify-center ring-4 ring-[var(--nexus-primary-100)] dark:ring-[var(--nexus-primary-900)]/30">
          <RotateCcw className="w-6 h-6 text-white" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Returns Command Center</h1>
          <p className="text-sm text-[var(--text-secondary)]">RMA lifecycle, disposition automation & return analytics</p>
        </div>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        <EnterpriseKPICard title="Return Rate" value={analytics.returnRate != null ? `${(Number(analytics.returnRate) * 100).toFixed(1)}%` : '—'} subtitle="from analytics" icon={<TrendingUp className="w-5 h-5" />} color="warning" />
        <EnterpriseKPICard title="Pending RMA" value={String(rawKpis.pending ?? 0)} icon={<ClipboardList className="w-5 h-5" />} color="warning" />
        <EnterpriseKPICard title="Approved" value={String(rawKpis.approved ?? 0)} icon={<ThumbsUp className="w-5 h-5" />} color="primary" />
        <EnterpriseKPICard title="Received" value={String(rawKpis.received ?? 0)} icon={<PackageCheck className="w-5 h-5" />} color="info" />
        <EnterpriseKPICard title="Refunded" value={String(rawKpis.refunded ?? 0)} icon={<DollarSign className="w-5 h-5" />} color="success" />
        <EnterpriseKPICard title="Rejected" value={String(rawKpis.rejected ?? 0)} icon={<X className="w-5 h-5" />} color="error" />
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
              <button type="button" onClick={() => setCreateOpen(false)} className="p-1 text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] rounded-lg hover:bg-[var(--surface-muted)]"><X className="w-5 h-5" /></button>
            </div>
            <div className="enterprise-modal-body space-y-4">
              <div className="enterprise-form-group">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Order ID (UUID)</label>
                <input type="text" className="enterprise-input" placeholder="Order UUID" value={createForm.orderId} onChange={e => setCreateForm(f => ({ ...f, orderId: e.target.value }))} />
              </div>
              <div className="enterprise-form-group">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Customer ID (UUID)</label>
                <input type="text" className="enterprise-input" placeholder="Customer UUID" value={createForm.customerId} onChange={e => setCreateForm(f => ({ ...f, customerId: e.target.value }))} />
              </div>
              <div className="enterprise-form-group">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Reason</label>
                <textarea className="enterprise-textarea" rows={2} placeholder="Describe the return reason..." value={createForm.reason} onChange={e => setCreateForm(f => ({ ...f, reason: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Items</label>
                {createForm.items.map((item, i) => (
                  <div key={i} className="flex gap-2">
                    <input type="text" className="enterprise-input" placeholder="SKU" value={item.sku} onChange={e => {
                      const items = createForm.items.map((it, idx) => idx === i ? { ...it, sku: e.target.value } : it)
                      setCreateForm(f => ({ ...f, items }))
                    }} />
                    <input type="number" min="1" className="enterprise-input w-20" placeholder="Qty" value={item.quantity || ''} onChange={e => {
                      const items = createForm.items.map((it, idx) => idx === i ? { ...it, quantity: parseInt(e.target.value) || 0 } : it)
                      setCreateForm(f => ({ ...f, items }))
                    }} />
                    <input type="number" min="0" step="0.01" className="enterprise-input w-24" placeholder="Price" value={item.unitPrice || ''} onChange={e => {
                      const items = createForm.items.map((it, idx) => idx === i ? { ...it, unitPrice: parseFloat(e.target.value) || 0 } : it)
                      setCreateForm(f => ({ ...f, items }))
                    }} />
                  </div>
                ))}
                <button type="button" onClick={() => setCreateForm(f => ({ ...f, items: [...f.items, { sku: '', productName: '', quantity: 1, unitPrice: 0 }] }))}
                  className="text-sm text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)] hover:underline">
                  + Add item
                </button>
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button type="button" onClick={() => setCreateOpen(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="orders" action="create">
                <button type="button" onClick={() => createMutation.mutate()} disabled={!createForm.orderId || !createForm.customerId || !createForm.reason || !createForm.items.some(it => it.sku) || createMutation.isPending}
                  className="enterprise-btn enterprise-btn-primary disabled:opacity-50">
                  {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />} Create RMA
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
              <button type="button" onClick={() => setConfirmAction(null)} className="p-1 text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] rounded-lg hover:bg-[var(--surface-muted)]"><X className="w-5 h-5" /></button>
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
              <button type="button" onClick={() => setConfirmAction(null)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              {confirmAction.type === 'approve' ? (
                <PermissionGate resource="orders" action="edit">
                  <button type="button" onClick={() => { approveMutation.mutate(confirmAction.rma.id); setConfirmAction(null) }}
                    className="enterprise-btn bg-[var(--nexus-success-600)] text-white hover:bg-[var(--nexus-success-700)] border-none">
                    <Check className="w-4 h-4" /> Approve
                  </button>
                </PermissionGate>
              ) : (
                <PermissionGate resource="orders" action="edit">
                  <button type="button" onClick={() => { rejectMutation.mutate({ id: confirmAction.rma.id, reason: rejectReason || 'No reason provided' }); setConfirmAction(null) }}
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
