import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Truck, Package, Store, Search, CheckCircle, Clock, XCircle, Plus,
  ChevronRight, Eye, ArrowRight, Filter, AlertTriangle,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import { transfersApi, TransferOrder, TransferStats } from '../api/transfers'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import type { Tab } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'

type TransferTab = 'active' | 'in-transit' | 'received' | 'all'

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-[var(--surface-muted)] text-[var(--text-primary)]',
  PENDING_APPROVAL: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]',
  APPROVED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
  IN_TRANSIT: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-800)]',
  RECEIVED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  CANCELLED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
}

const PRIORITY_COLORS: Record<string, string> = {
  LOW: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
  NORMAL: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-600)]',
  HIGH: 'bg-orange-100 text-orange-600',
  URGENT: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)]',
}

const TRANSFER_TYPE_LABELS: Record<string, string> = {
  WAREHOUSE_TO_STORE: 'Warehouse → Store',
  STORE_TO_STORE: 'Store → Store',
  STORE_TO_WAREHOUSE: 'Store → Warehouse',
}

export default function TransfersPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<TransferTab>('active')
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedTransfer, setSelectedTransfer] = useState<TransferOrder | null>(null)

  const tabs: Tab[] = [
    { key: 'active', label: 'Active', icon: Clock },
    { key: 'in-transit', label: 'In Transit', icon: Truck },
    { key: 'received', label: 'Received', icon: CheckCircle },
    { key: 'all', label: 'All', icon: Package },
  ]

  const { data: transfers = [], isLoading: loadingTransfers } = useQuery({
    queryKey: ['transfers', activeTab],
    queryFn: async () => {
      let status: string | undefined
      if (activeTab === 'active') status = undefined
      else if (activeTab === 'in-transit') status = 'IN_TRANSIT'
      else if (activeTab === 'received') status = 'RECEIVED'

      const res = await transfersApi.getTransfers(status)
      const data = res.data

      if (activeTab === 'active') {
        return Array.isArray(data)
          ? data.filter(t => !['RECEIVED', 'CANCELLED'].includes(t.status))
          : []
      }
      return Array.isArray(data) ? data : []
    },
  })

  const { data: stats } = useQuery({
    queryKey: ['transfer-stats'],
    queryFn: async () => {
      const res = await transfersApi.getTransferStats()
      return res.data as TransferStats
    },
  })

  const approveMutation = useMutation({
    mutationFn: (id: string) => transfersApi.approveTransfer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transfers'] })
      queryClient.invalidateQueries({ queryKey: ['transfer-stats'] })
      addToast({ type: 'success', title: 'Transfer approved' })
    },
    onError: () => addToast({ type: 'error', title: 'Approval failed' }),
  })

  const shipMutation = useMutation({
    mutationFn: (id: string) => transfersApi.shipTransfer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transfers'] })
      queryClient.invalidateQueries({ queryKey: ['transfer-stats'] })
      addToast({ type: 'success', title: 'Transfer shipped' })
    },
    onError: () => addToast({ type: 'error', title: 'Ship failed' }),
  })

  const receiveMutation = useMutation({
    mutationFn: (id: string) => transfersApi.receiveTransfer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transfers'] })
      queryClient.invalidateQueries({ queryKey: ['transfer-stats'] })
      addToast({ type: 'success', title: 'Transfer received' })
    },
    onError: () => addToast({ type: 'error', title: 'Receive failed' }),
  })

  const cancelMutation = useMutation({
    mutationFn: (id: string) => transfersApi.cancelTransfer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transfers'] })
      queryClient.invalidateQueries({ queryKey: ['transfer-stats'] })
      addToast({ type: 'success', title: 'Transfer cancelled' })
    },
    onError: () => addToast({ type: 'error', title: 'Cancel failed' }),
  })

  const filteredTransfers = transfers.filter(t =>
    t.transferNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
    t.transferType.toLowerCase().includes(searchTerm.toLowerCase())
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Transfer Orders</h1>
          <p className="text-[var(--text-secondary)] mt-1">Manage inventory transfers between locations</p>
        </div>
        <PermissionGate permission="transfers.create">
          <button className="flex items-center gap-2 px-4 py-2 bg-[var(--nexus-primary-600)] text-white rounded-lg hover:bg-[var(--nexus-primary-700)]">
            <Plus className="w-4 h-4" />
            New Transfer
          </button>
        </PermissionGate>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Total Transfers"
          value={stats?.totalTransfers || 0}
          icon={Package}
          color="blue"
        />
        <EnterpriseKPICard
          title="Pending Approval"
          value={stats?.pendingApproval || 0}
          icon={Clock}
          color="yellow"
        />
        <EnterpriseKPICard
          title="In Transit"
          value={stats?.inTransit || 0}
          icon={Truck}
          color="purple"
        />
        <EnterpriseKPICard
          title="Urgent"
          value={stats?.urgentTransfers || 0}
          icon={AlertTriangle}
          color="red"
        />
      </div>

      <EnterpriseTabs
        tabs={tabs}
        activeTab={activeTab}
        onTabChange={(key) => setActiveTab(key as TransferTab)}
      />

      <div className="bg-[var(--surface-base)] rounded-lg shadow">
        <div className="p-4 border-b">
          <div className="flex items-center gap-4">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
              <input
                type="text"
                placeholder="Search transfers..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-[var(--nexus-primary-500)]"
              />
            </div>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-[var(--surface-sunken)]">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Transfer #</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Type</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Priority</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Status</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Expected</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {loadingTransfers ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td>
                </tr>
              ) : filteredTransfers.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">No transfers found</td>
                </tr>
              ) : (
                filteredTransfers.map((transfer) => (
                  <tr key={transfer.id} className="hover:bg-[var(--surface-sunken)]">
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setSelectedTransfer(transfer)}
                        className="text-[var(--nexus-primary-600)] hover:text-[var(--nexus-primary-800)] font-medium"
                      >
                        {transfer.transferNumber}
                      </button>
                    </td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                      {TRANSFER_TYPE_LABELS[transfer.transferType] || transfer.transferType}
                    </td>
                    <td className="px-4 py-3">
                      <span className={clsx(
                        'px-2 py-1 text-xs rounded-full font-medium',
                        PRIORITY_COLORS[transfer.priority]
                      )}>
                        {transfer.priority}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={clsx(
                        'px-2 py-1 text-xs rounded-full font-medium',
                        STATUS_COLORS[transfer.status]
                      )}>
                        {transfer.status.replace('_', ' ')}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                      {transfer.expectedArrival
                        ? new Date(transfer.expectedArrival).toLocaleDateString()
                        : '—'}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => setSelectedTransfer(transfer)}
                          className="p-1 text-[var(--text-tertiary)] hover:text-[var(--nexus-primary-600)]"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                        {transfer.status === 'PENDING_APPROVAL' && (
                          <PermissionGate permission="transfers.approve">
                            <button
                              onClick={() => approveMutation.mutate(transfer.id)}
                              className="px-2 py-1 text-xs bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] rounded hover:bg-[var(--nexus-success-200)]"
                            >
                              Approve
                            </button>
                          </PermissionGate>
                        )}
                        {transfer.status === 'APPROVED' && (
                          <PermissionGate permission="transfers.ship">
                            <button
                              onClick={() => shipMutation.mutate(transfer.id)}
                              className="px-2 py-1 text-xs bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)] rounded hover:bg-[var(--nexus-ai-200)]"
                            >
                              Ship
                            </button>
                          </PermissionGate>
                        )}
                        {transfer.status === 'IN_TRANSIT' && (
                          <PermissionGate permission="transfers.receive">
                            <button
                              onClick={() => receiveMutation.mutate(transfer.id)}
                              className="px-2 py-1 text-xs bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] rounded hover:bg-[var(--nexus-primary-200)]"
                            >
                              Receive
                            </button>
                          </PermissionGate>
                        )}
                        {!['RECEIVED', 'CANCELLED'].includes(transfer.status) && (
                          <PermissionGate permission="transfers.cancel">
                            <button
                              onClick={() => cancelMutation.mutate(transfer.id)}
                              className="px-2 py-1 text-xs bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] rounded hover:bg-[var(--nexus-error-200)]"
                            >
                              Cancel
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
      </div>

      {selectedTransfer && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-2xl">
            <div className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-xl font-bold">{selectedTransfer.transferNumber}</h2>
                <button
                  onClick={() => setSelectedTransfer(null)}
                  className="text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]"
                >
                  <XCircle className="w-6 h-6" />
                </button>
              </div>
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Type</p>
                    <p className="font-medium">{TRANSFER_TYPE_LABELS[selectedTransfer.transferType]}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Status</p>
                    <span className={clsx(
                      'px-2 py-1 text-xs rounded-full font-medium',
                      STATUS_COLORS[selectedTransfer.status]
                    )}>
                      {selectedTransfer.status.replace('_', ' ')}
                    </span>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Priority</p>
                    <span className={clsx(
                      'px-2 py-1 text-xs rounded-full font-medium',
                      PRIORITY_COLORS[selectedTransfer.priority]
                    )}>
                      {selectedTransfer.priority}
                    </span>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Expected Arrival</p>
                    <p className="font-medium">
                      {selectedTransfer.expectedArrival
                        ? new Date(selectedTransfer.expectedArrival).toLocaleDateString()
                        : '—'}
                    </p>
                  </div>
                </div>
                {selectedTransfer.notes && (
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Notes</p>
                    <p className="text-[var(--text-secondary)]">{selectedTransfer.notes}</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
