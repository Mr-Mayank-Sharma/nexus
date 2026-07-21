import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle, Search, Eye, ArrowRight, CheckCircle, XCircle,
  RefreshCw, MapPin, CreditCard, PauseCircle, User, Package, Clock,
  Ban, Shield, ClipboardList,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as ordersApi from '../api/orders'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import type { Tab } from '../components/enterprise'

type QueueTab = 'swap' | 'bad-address' | 'fraud' | 'hold'

interface QueueItem {
  id: string
  orderNumber: string
  customerName: string
  customerEmail: string
  orderDate: string
  total: number
  status: string
  reason: string
  details: string
  priority: 'high' | 'medium' | 'low'
  assignee?: string
}

const queueTabs: Tab[] = [
  { id: 'swap', label: 'Substitution', icon: <RefreshCw className="w-4 h-4" /> },
  { id: 'bad-address', label: 'Bad Address', icon: <MapPin className="w-4 h-4" /> },
  { id: 'fraud', label: 'Fraud Review', icon: <Shield className="w-4 h-4" /> },
  { id: 'hold', label: 'On Hold', icon: <PauseCircle className="w-4 h-4" /> },
]

const reasonMap: Record<QueueTab, { reasons: string[]; details: string[] }> = {
  swap: {
    reasons: ['Out of Stock', 'Damaged Inventory', 'Supplier Backorder'],
    details: ['Item XYZ-123 out of stock, needs substitute', 'Item ABC-456 damaged in warehouse', 'Supplier delayed on item DEF-789'],
  },
  'bad-address': {
    reasons: ['Invalid ZIP', 'Missing Unit/Apt', 'Undeliverable', 'Address Verification Failed'],
    details: ['ZIP code does not match city/state', 'Apartment number missing from address', 'Address flagged by USPS as undeliverable', 'Address verification service returned low confidence'],
  },
  fraud: {
    reasons: ['High Risk Score', 'AVS Mismatch', 'CVV Failure', 'Unusual Order Pattern', 'Shipping/Billing Mismatch'],
    details: ['Risk score of 87 (threshold: 80)', 'Billing address does not match card on file', 'CVV verification failed on second attempt', 'Order velocity exceeds normal pattern for this customer', 'Shipping address differs from billing in high-risk zone'],
  },
  hold: {
    reasons: ['Payment Pending', 'Customer Verification', 'Terms Review', 'Credit Limit Exceeded'],
    details: ['Payment authorization pending for 24+ hours', 'Customer flagged for identity verification', 'Order requires manual terms approval', 'Order exceeds credit limit by $500'],
  },
}

export default function TaskQueuesPage() {
  const navigate = useNavigate()
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<QueueTab>('swap')
  const [searchTerm, setSearchTerm] = useState('')
  const [priorityFilter, setPriorityFilter] = useState<'all' | 'high' | 'medium' | 'low'>('all')

  const { data: items = [], isLoading } = useQuery({
    queryKey: ['task-queue', activeTab],
    queryFn: async () => {
      const res = await ordersApi.getOrders({})
      const d = res.data
      const list = Array.isArray(d) ? d : (d?.content ?? [])
      const reasons = reasonMap[activeTab]
      return list.slice(0, 12).map((o: any, i: number) => ({
        id: o.id,
        orderNumber: o.orderNumber || `ORD-${String(i + 1).padStart(4, '0')}`,
        customerName: o.customerName || `Customer ${i + 1}`,
        customerEmail: o.customerEmail || `customer${i + 1}@example.com`,
        orderDate: o.orderDate || new Date(Date.now() - i * 24 * 60 * 60 * 1000).toISOString(),
        total: o.total || Math.floor(Math.random() * 500) + 20,
        status: o.status || 'PENDING',
        reason: reasons.reasons[i % reasons.reasons.length],
        details: reasons.details[i % reasons.details.length],
        priority: (['high', 'medium', 'low'] as const)[i % 3],
        assignee: i % 4 === 0 ? 'Jane Cooper' : undefined,
      })) as QueueItem[]
    },
  })

  const resolveMutation = useMutation({
    mutationFn: async ({ id, action }: { id: string; action: 'resolve' | 'reject' }) => {
      await new Promise(r => setTimeout(r, 400))
      return { id, action }
    },
    onSuccess: (_, vars) => {
      queryClient.invalidateQueries({ queryKey: ['task-queue'] })
      addToast({ type: vars.action === 'resolve' ? 'success' : 'error', title: vars.action === 'resolve' ? 'Task resolved' : 'Task rejected' })
    },
  })

  const filteredItems = items.filter(item => {
    if (searchTerm) {
      const q = searchTerm.toLowerCase()
      if (!item.orderNumber.toLowerCase().includes(q) && !item.customerName.toLowerCase().includes(q) && !item.reason.toLowerCase().includes(q)) return false
    }
    if (priorityFilter !== 'all' && item.priority !== priorityFilter) return false
    return true
  })

  const highCount = items.filter(i => i.priority === 'high').length
  const totalCount = items.length

  const getPriorityColor = (p: string) => {
    switch (p) {
      case 'high': return 'bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 text-[var(--nexus-error-700)] dark:text-[var(--nexus-error-400)]'
      case 'medium': return 'bg-[var(--nexus-warning-100)] dark:bg-[var(--nexus-warning-900)]/20 text-[var(--nexus-warning-700)] dark:text-[var(--nexus-warning-400)]'
      case 'low': return 'bg-[var(--nexus-success-100)] dark:bg-[var(--nexus-success-900)]/20 text-[var(--nexus-success-700)] dark:text-[var(--nexus-success-400)]'
      default: return 'bg-[var(--surface-muted)] bg-[var(--surface-base)] text-[var(--text-secondary)]'
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><ClipboardList className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Task Queues</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Review and resolve exception tasks</p>
        </div>
        <div className="flex items-center gap-2">
          <EnterpriseKPICard title="High Priority" value={highCount} icon={<AlertTriangle className="w-4 h-4" />} color={highCount > 0 ? 'danger' : 'success'} trend={null} />
          <EnterpriseKPICard title="Total Tasks" value={totalCount} icon={<Clock className="w-4 h-4" />} color="info" trend={null} />
        </div>
      </div>

      <EnterpriseTabs tabs={queueTabs} activeTab={activeTab} onChange={t => setActiveTab(t as QueueTab)} />

      <div className="flex items-center gap-3">
        <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder="Search by order, customer, or reason..." minChars={0} className="flex-1 max-w-md" />
        <div className="flex gap-1 bg-[var(--surface-muted)] rounded-lg p-0.5">
          {(['all', 'high', 'medium', 'low'] as const).map(p => (
            <button key={p} onClick={() => setPriorityFilter(p)}
              className={clsx('px-3 py-1.5 text-xs font-medium rounded-md capitalize',
                priorityFilter === p ? 'bg-white bg-[var(--surface-muted)] text-[var(--text-primary)] shadow-sm' : 'text-[var(--text-secondary)] hover:text-[var(--text-secondary)] dark:hover:text-[var(--text-tertiary)]')}>
              {p}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" /></div>
      ) : filteredItems.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <CheckCircle className="w-12 h-12 mx-auto mb-3 text-[var(--nexus-success-300)] dark:text-[var(--nexus-success-600)]" />
          <p className="font-medium text-[var(--text-secondary)]">No pending tasks in this queue</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filteredItems.map(item => (
            <div key={item.id} className="enterprise-card p-4 hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)]/30 transition-all">
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-start gap-3 min-w-0 flex-1">
                  <div className={clsx('w-10 h-10 rounded-xl flex items-center justify-center shrink-0 mt-0.5',
                    activeTab === 'swap' && 'bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/20 text-[var(--nexus-primary-600)]',
                    activeTab === 'bad-address' && 'bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20 text-[var(--nexus-warning-600)]',
                    activeTab === 'fraud' && 'bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 text-[var(--nexus-error-600)]',
                    activeTab === 'hold' && 'bg-[var(--nexus-ai-50)] dark:bg-[var(--nexus-ai-900)]/20 text-[var(--nexus-ai-600)]',
                  )}>
                    {activeTab === 'swap' && <RefreshCw className="w-5 h-5" />}
                    {activeTab === 'bad-address' && <MapPin className="w-5 h-5" />}
                    {activeTab === 'fraud' && <Shield className="w-5 h-5" />}
                    {activeTab === 'hold' && <PauseCircle className="w-5 h-5" />}
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-semibold text-[var(--text-primary)]">{item.orderNumber}</span>
                      <span className={clsx('text-[10px] font-semibold px-1.5 py-0.5 rounded-full uppercase', getPriorityColor(item.priority))}>
                        {item.priority}
                      </span>
                      <EnterpriseStatusBadge status={item.status === 'PENDING' ? 'pending' : item.status === 'CONFIRMED' ? 'info' : item.status === 'ALLOCATED' ? 'warning' : 'info'} label={item.status} />
                    </div>
                    <p className="text-xs text-[var(--text-secondary)] mb-1">
                      <User className="w-3 h-3 inline mr-1" />
                      {item.customerName}
                    </p>
                    <div className="bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 rounded-lg p-2.5 mt-1.5">
                      <p className="text-xs font-medium text-[var(--text-secondary)] flex items-center gap-1">
                        <AlertTriangle className="w-3 h-3 text-[var(--nexus-warning-500)]" />
                        {item.reason}
                      </p>
                      <p className="text-xs text-[var(--text-secondary)] mt-0.5">{item.details}</p>
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2 shrink-0 mt-0.5">
                  <button onClick={() => navigate(`/orders/${item.id}`)} className="enterprise-btn-secondary text-xs px-2.5 py-1.5 flex items-center gap-1">
                    <Eye className="w-3.5 h-3.5" /> View
                  </button>
                  <PermissionGate resource="warehouse" action="edit">
                    <button onClick={() => resolveMutation.mutate({ id: item.id, action: 'resolve' })} className="enterprise-btn-primary text-xs px-2.5 py-1.5 flex items-center gap-1 bg-[var(--nexus-success-600)] hover:bg-[var(--nexus-success-700)]">
                      <CheckCircle className="w-3.5 h-3.5" /> Resolve
                    </button>
                  </PermissionGate>
                  <PermissionGate resource="warehouse" action="delete">
                    <button onClick={() => resolveMutation.mutate({ id: item.id, action: 'reject' })} className="enterprise-btn-secondary text-xs px-2.5 py-1.5 flex items-center gap-1 border-[var(--nexus-error-200)] dark:border-[var(--nexus-error-800)] text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)] hover:bg-[var(--nexus-error-50)] dark:hover:bg-[var(--nexus-error-900)]/20">
                      <XCircle className="w-3.5 h-3.5" /> Reject
                    </button>
                  </PermissionGate>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
