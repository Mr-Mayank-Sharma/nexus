import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  XCircle, AlertTriangle, CheckCircle, Clock, Search, Eye, Filter,
  Package, Shield, FileText,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import { rejectionsApi, OrderRejection, RejectionReason, RejectionStats } from '../api/rejections'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'
import type { Tab } from '../components/enterprise'

type RejectionTab = 'pending' | 'processed' | 'reasons' | 'all'

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]',
  PROCESSED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  CANCELLED: 'bg-[var(--surface-muted)] text-[var(--text-primary)]',
}

const INVENTORY_ACTION_COLORS: Record<string, string> = {
  RESTOCK: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
  DAMAGE_WRITE_OFF: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
  RETURN_TO_VENDOR: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-800)]',
  QUARANTINE: 'bg-orange-100 text-orange-800',
}

const CATEGORY_COLORS: Record<string, string> = {
  QUALITY: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
  DAMAGED: 'bg-orange-100 text-orange-800',
  WRONG_ITEM: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-800)]',
  CUSTOMER: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
  INVENTORY: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]',
  OTHER: 'bg-[var(--surface-muted)] text-[var(--text-primary)]',
}

export default function RejectionsPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<RejectionTab>('pending')
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedRejection, setSelectedRejection] = useState<OrderRejection | null>(null)

  const tabs: Tab[] = [
    { key: 'pending', label: 'Pending', icon: Clock },
    { key: 'processed', label: 'Processed', icon: CheckCircle },
    { key: 'reasons', label: 'Reasons', icon: Shield },
    { key: 'all', label: 'All', icon: Package },
  ]

  const { data: pendingRejections = [], isLoading: loadingPending } = useQuery({
    queryKey: ['rejections-pending'],
    queryFn: async () => {
      const res = await rejectionsApi.getPendingRejections()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'pending',
  })

  const { data: allRejections = [], isLoading: loadingAll } = useQuery({
    queryKey: ['rejections-all'],
    queryFn: async () => {
      const res = await rejectionsApi.getAllRejections()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'all' || activeTab === 'processed',
  })

  const { data: reasons = [], isLoading: loadingReasons } = useQuery({
    queryKey: ['rejection-reasons'],
    queryFn: async () => {
      const res = await rejectionsApi.getReasons()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'reasons',
  })

  const { data: stats } = useQuery({
    queryKey: ['rejection-stats'],
    queryFn: async () => {
      const res = await rejectionsApi.getRejectionStats()
      return res.data as RejectionStats
    },
  })

  const processMutation = useMutation({
    mutationFn: (id: string) => rejectionsApi.processRejection(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rejections-pending'] })
      queryClient.invalidateQueries({ queryKey: ['rejections-all'] })
      queryClient.invalidateQueries({ queryKey: ['rejection-stats'] })
      addToast({ type: 'success', title: 'Rejection processed' })
      setSelectedRejection(null)
    },
    onError: () => addToast({ type: 'error', title: 'Failed to process rejection' }),
  })

  const displayRejections = activeTab === 'processed'
    ? allRejections.filter(r => r.status === 'PROCESSED')
    : activeTab === 'all'
    ? allRejections
    : pendingRejections

  const filteredRejections = displayRejections.filter(r =>
    r.orderNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
    r.sku.toLowerCase().includes(searchTerm.toLowerCase()) ||
    r.rejectionCode.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const filteredReasons = reasons.filter(r =>
    !searchTerm || r.label.toLowerCase().includes(searchTerm.toLowerCase()) || r.code.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const formatDate = (d?: string) => d ? new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '—'

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Rejection Handling</h1>
          <p className="text-[var(--text-secondary)] mt-1">Manage item rejections and inventory adjustments</p>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Total Rejections"
          value={stats?.totalRejections || 0}
          icon={XCircle}
          color="red"
        />
        <EnterpriseKPICard
          title="Pending"
          value={stats?.pending || 0}
          icon={Clock}
          color="yellow"
        />
        <EnterpriseKPICard
          title="Processed"
          value={stats?.processed || 0}
          icon={CheckCircle}
          color="green"
        />
        <EnterpriseKPICard
          title="Cancelled"
          value={stats?.cancelled || 0}
          icon={AlertTriangle}
          color="gray"
        />
      </div>

      <EnterpriseTabs
        tabs={tabs}
        activeTab={activeTab}
        onTabChange={(key) => setActiveTab(key as RejectionTab)}
      />

      <div className="bg-[var(--surface-base)] rounded-lg shadow">
        <div className="p-4 border-b">
          <div className="flex items-center gap-4">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
              <input
                type="text"
                placeholder={activeTab === 'reasons' ? 'Search reasons...' : 'Search rejections...'}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-[var(--nexus-primary-500)]"
              />
            </div>
          </div>
        </div>

        {activeTab === 'reasons' ? (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Code</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Label</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Category</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Inventory Impact</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Photo Required</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Active</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingReasons ? (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td></tr>
                ) : filteredReasons.length === 0 ? (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">No reasons found</td></tr>
                ) : (
                  filteredReasons.map((reason) => (
                    <tr key={reason.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3 text-sm font-mono text-[var(--text-primary)]">{reason.code}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-primary)]">{reason.label}</td>
                      <td className="px-4 py-3">
                        <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', CATEGORY_COLORS[reason.category] || 'bg-[var(--surface-muted)] text-[var(--text-primary)]')}>
                          {reason.category}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', INVENTORY_ACTION_COLORS[reason.inventoryImpact] || 'bg-[var(--surface-muted)] text-[var(--text-primary)]')}>
                          {reason.inventoryImpact.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">
                        {reason.requiresPhoto ? '✓' : '—'}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', reason.active ? 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]' : 'bg-[var(--surface-muted)] text-[var(--text-primary)]')}>
                          {reason.active ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Order #</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">SKU</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Qty</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Reason</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Inventory Action</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Date</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingPending || loadingAll ? (
                  <tr><td colSpan={8} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td></tr>
                ) : filteredRejections.length === 0 ? (
                  <tr><td colSpan={8} className="px-4 py-8 text-center text-[var(--text-secondary)]">No rejections found</td></tr>
                ) : (
                  filteredRejections.map((rejection) => (
                    <tr key={rejection.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3 text-sm font-medium text-[var(--nexus-primary-600)]">{rejection.orderNumber}</td>
                      <td className="px-4 py-3 text-sm font-mono text-[var(--text-secondary)]">{rejection.sku}</td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-primary)]">{rejection.quantity}</td>
                      <td className="px-4 py-3">
                        <span className="px-2 py-1 text-xs rounded-full font-medium bg-[var(--surface-muted)] text-[var(--text-primary)]">
                          {rejection.rejectionCode}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', INVENTORY_ACTION_COLORS[rejection.inventoryAction] || 'bg-[var(--surface-muted)] text-[var(--text-primary)]')}>
                          {rejection.inventoryAction.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', STATUS_COLORS[rejection.status] || 'bg-[var(--surface-muted)] text-[var(--text-primary)]')}>
                          {rejection.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{formatDate(rejection.createdAt)}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => setSelectedRejection(rejection)}
                            className="p-1 text-[var(--text-tertiary)] hover:text-[var(--nexus-primary-600)]"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                          {rejection.status === 'PENDING' && (
                            <PermissionGate permission="rejections.process">
                              <button
                                onClick={() => processMutation.mutate(rejection.id)}
                                className="px-2 py-1 text-xs bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] rounded hover:bg-[var(--nexus-success-200)]"
                              >
                                Process
                              </button>
                            </PermissionGate>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selectedRejection && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-2xl">
            <div className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-xl font-bold">Rejection Details</h2>
                <button onClick={() => setSelectedRejection(null)} className="text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]">
                  <XCircle className="w-6 h-6" />
                </button>
              </div>
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Order Number</p>
                    <p className="font-medium">{selectedRejection.orderNumber}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">SKU</p>
                    <p className="font-medium font-mono">{selectedRejection.sku}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Quantity</p>
                    <p className="font-medium">{selectedRejection.quantity}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Rejected By</p>
                    <p className="font-medium">{selectedRejection.rejectedBy}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Reason</p>
                    <span className="px-2 py-1 text-xs rounded-full font-medium bg-[var(--surface-muted)] text-[var(--text-primary)]">
                      {selectedRejection.rejectionCode}
                    </span>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Status</p>
                    <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', STATUS_COLORS[selectedRejection.status])}>
                      {selectedRejection.status}
                    </span>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Inventory Action</p>
                    <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', INVENTORY_ACTION_COLORS[selectedRejection.inventoryAction])}>
                      {selectedRejection.inventoryAction.replace('_', ' ')}
                    </span>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Inventory Adjusted</p>
                    <p className="font-medium">{selectedRejection.inventoryAdjusted ? 'Yes' : 'No'}</p>
                  </div>
                </div>
                {selectedRejection.notes && (
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Notes</p>
                    <p className="text-[var(--text-secondary)]">{selectedRejection.notes}</p>
                  </div>
                )}
                {selectedRejection.photoPath && (
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Photo</p>
                    <p className="text-[var(--nexus-primary-600)] text-sm">{selectedRejection.photoPath}</p>
                  </div>
                )}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Created</p>
                    <p className="text-sm">{formatDate(selectedRejection.createdAt)}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Processed At</p>
                    <p className="text-sm">{formatDate(selectedRejection.processedAt)}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
