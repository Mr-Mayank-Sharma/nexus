import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle, Search, Eye, RefreshCw, CheckCircle,
  MapPin, PauseCircle, User, Clock,
  Shield, ClipboardList, ArrowUpCircle,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as ordersApi from '../api/orders'
import { getExceptions, resolveException, escalateException } from '../api/orderRouting'
import type { FulfillmentException } from '../types'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import type { Tab } from '../components/enterprise'

type QueueTab = 'swap' | 'bad-address' | 'fraud' | 'hold'

interface QueueItem {
  id: string
  orderId: string
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
  type: string
  suggestedAction?: string
}

const queueTabs: Tab[] = [
  { id: 'swap', label: 'Substitution', icon: <RefreshCw className="w-4 h-4" /> },
  { id: 'bad-address', label: 'Bad Address', icon: <MapPin className="w-4 h-4" /> },
  { id: 'fraud', label: 'Fraud Review', icon: <Shield className="w-4 h-4" /> },
  { id: 'hold', label: 'On Hold', icon: <PauseCircle className="w-4 h-4" /> },
]

const tabTypes: Record<QueueTab, string[]> = {
  swap: ['INVENTORY_SHORTAGE'],
  'bad-address': ['SHIPPING_ADDRESS_ISSUE'],
  fraud: ['FRAUD_FLAG'],
  hold: ['PAYMENT_HOLD', 'CREDIT_HOLD', 'CUSTOMER_REQUEST'],
}

const activeStatuses = ['OPEN', 'IN_PROGRESS']

const asArray = (d: unknown): any[] => (Array.isArray(d) ? d : Array.isArray((d as any)?.content) ? (d as any).content : [])

function toPriority(severity: string): 'high' | 'medium' | 'low' {
  if (severity === 'CRITICAL' || severity === 'HIGH') return 'high'
  if (severity === 'MEDIUM') return 'medium'
  return 'low'
}

export default function TaskQueuesPage() {
  const navigate = useNavigate()
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<QueueTab>('swap')
  const [searchTerm, setSearchTerm] = useState('')
  const [priorityFilter, setPriorityFilter] = useState<'all' | 'high' | 'medium' | 'low'>('all')

  const { data: orders = [] } = useQuery({
    queryKey: ['task-queue-orders'],
    queryFn: async () => {
      const res = await ordersApi.getOrders({}).catch(() => null)
      return asArray(res?.data)
    },
  })

  const orderMap = useMemo(() => {
    const m = new Map<string, any>()
    for (const o of orders) m.set(o.id, o)
    return m
  }, [orders])

  const { data: rawExceptions = [], isLoading } = useQuery({
    queryKey: ['task-queue-exceptions'],
    queryFn: async () => {
      const res = await getExceptions({ page: 0, size: 100 }).catch(() => null)
      return asArray(res?.data?.content ?? res?.data)
    },
    refetchInterval: 60000,
  })

  const items: QueueItem[] = useMemo(() => {
    return rawExceptions
      .filter((e: FulfillmentException) => tabTypes[activeTab].includes(e.type) && activeStatuses.includes(e.status))
      .map((e: FulfillmentException) => {
        const order = orderMap.get(e.orderId)
        return {
          id: e.id,
          orderId: e.orderId,
          orderNumber: order?.orderNumber || e.orderId,
          customerName: order?.customerName || '—',
          customerEmail: order?.customerEmail || '—',
          orderDate: order?.createdAt || e.detectedAt,
          total: order?.total ?? 0,
          status: e.status,
          reason: e.title,
          details: e.description || e.suggestedAction || 'No additional details',
          priority: toPriority(e.severity),
          assignee: e.assignedTo,
          type: e.type,
          suggestedAction: e.suggestedAction,
        }
      })
  }, [rawExceptions, activeTab, orderMap])

  const resolveMutation = useMutation({
    mutationFn: async ({ id, suggestedAction }: { id: string; suggestedAction?: string }) => {
      return resolveException(id, {
        resolution: 'Reviewed and resolved by agent',
        resolutionStrategy: suggestedAction,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['task-queue-exceptions'] })
      addToast({ type: 'success', title: 'Exception resolved' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to resolve exception' }),
  })

  const escalateMutation = useMutation({
    mutationFn: async (id: string) => {
      return escalateException(id)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['task-queue-exceptions'] })
      addToast({ type: 'info', title: 'Exception escalated' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to escalate exception' }),
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
      default: return 'bg-[var(--surface-muted)] text-[var(--text-secondary)]'
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
            <button type="button" key={p} onClick={() => setPriorityFilter(p)}
              className={clsx('px-3 py-1.5 text-xs font-medium rounded-md capitalize',
                priorityFilter === p ? 'bg-[var(--surface-muted)] text-[var(--text-primary)] shadow-sm' : 'text-[var(--text-secondary)] hover:text-[var(--text-secondary)] dark:hover:text-[var(--text-tertiary)]')}>
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
                      <EnterpriseStatusBadge status={item.status === 'OPEN' ? 'warning' : item.status === 'IN_PROGRESS' ? 'info' : 'success'} label={item.status.replace('_', ' ')} />
                    </div>
                    <p className="text-xs text-[var(--text-secondary)] mb-1">
                      <User className="w-3 h-3 inline mr-1" />
                      {item.customerName}
                      {item.assignee ? <span className="ml-2 text-[var(--text-tertiary)]">· Assigned: {item.assignee}</span> : null}
                    </p>
                    <div className="bg-[var(--surface-sunken)]/50 rounded-lg p-2.5 mt-1.5">
                      <p className="text-xs font-medium text-[var(--text-secondary)] flex items-center gap-1">
                        <AlertTriangle className="w-3 h-3 text-[var(--nexus-warning-500)]" />
                        {item.reason}
                      </p>
                      <p className="text-xs text-[var(--text-secondary)] mt-0.5">{item.details}</p>
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2 shrink-0 mt-0.5">
                  <button type="button" onClick={() => navigate(`/orders/${item.orderId}`)} className="enterprise-btn-secondary text-xs px-2.5 py-1.5 flex items-center gap-1">
                    <Eye className="w-3.5 h-3.5" /> View
                  </button>
                  <PermissionGate resource="warehouse" action="edit">
                    <button type="button" onClick={() => escalateMutation.mutate(item.id)} disabled={escalateMutation.isPending} className="enterprise-btn-secondary text-xs px-2.5 py-1.5 flex items-center gap-1">
                      <ArrowUpCircle className="w-3.5 h-3.5" /> Escalate
                    </button>
                  </PermissionGate>
                  <PermissionGate resource="warehouse" action="edit">
                    <button type="button" onClick={() => resolveMutation.mutate({ id: item.id, suggestedAction: item.suggestedAction })} disabled={resolveMutation.isPending} className="enterprise-btn-primary text-xs px-2.5 py-1.5 flex items-center gap-1 bg-[var(--nexus-success-600)] hover:bg-[var(--nexus-success-700)]">
                      <CheckCircle className="w-3.5 h-3.5" /> Resolve
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
