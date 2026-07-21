import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Clock, Play, Pause, CheckCircle, XCircle, AlertTriangle, RefreshCw,
  Trash2, Eye, Zap, Timer,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import { brokeringApi, BrokeringQueueEntry, BrokeringRun, BrokeringStats } from '../api/brokering'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import type { Tab } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'

type BrokeringTab = 'queue' | 'runs' | 'stats'

const STATUS_COLORS: Record<string, string> = {
  WAITING: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]',
  PROCESSING: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
  ALLOCATED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  FAILED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
  EXPIRED: 'bg-[var(--surface-muted)] text-[var(--text-primary)]',
}

const PRIORITY_COLORS: Record<string, string> = {
  LOW: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
  NORMAL: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-600)]',
  HIGH: 'bg-orange-100 text-orange-600',
  URGENT: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)]',
}

export default function BrokeringQueuePage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<BrokeringTab>('queue')
  const [selectedRun, setSelectedRun] = useState<BrokeringRun | null>(null)

  const tabs: Tab[] = [
    { key: 'queue', label: 'Queue', icon: Clock },
    { key: 'runs', label: 'Run History', icon: Play },
    { key: 'stats', label: 'Statistics', icon: AlertTriangle },
  ]

  const { data: queue = [], isLoading: loadingQueue } = useQuery({
    queryKey: ['brokering-queue'],
    queryFn: async () => {
      const res = await brokeringApi.getQueue()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'queue',
  })

  const { data: stats } = useQuery({
    queryKey: ['brokering-stats'],
    queryFn: async () => {
      const res = await brokeringApi.getQueueStats()
      return res.data as BrokeringStats
    },
  })

  const { data: runs = [], isLoading: loadingRuns } = useQuery({
    queryKey: ['brokering-runs'],
    queryFn: async () => {
      const res = await brokeringApi.getRunHistory()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'runs',
  })

  const processMutation = useMutation({
    mutationFn: () => brokeringApi.processBrokeringQueue(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['brokering-queue'] })
      queryClient.invalidateQueries({ queryKey: ['brokering-stats'] })
      queryClient.invalidateQueries({ queryKey: ['brokering-runs'] })
      addToast({ type: 'success', title: 'Brokering run completed' })
    },
    onError: () => addToast({ type: 'error', title: 'Brokering run failed' }),
  })

  const processPriorityMutation = useMutation({
    mutationFn: () => brokeringApi.processPriorityQueue(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['brokering-queue'] })
      queryClient.invalidateQueries({ queryKey: ['brokering-stats'] })
      addToast({ type: 'success', title: 'Priority brokering run completed' })
    },
    onError: () => addToast({ type: 'error', title: 'Priority brokering failed' }),
  })

  const expireMutation = useMutation({
    mutationFn: () => brokeringApi.expireStaleOrders(),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['brokering-queue'] })
      addToast({ type: 'success', title: `Expired ${res.data.length} stale orders` })
    },
    onError: () => addToast({ type: 'error', title: 'Expire failed' }),
  })

  const removeMutation = useMutation({
    mutationFn: (id: string) => brokeringApi.removeFromQueue(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['brokering-queue'] })
      addToast({ type: 'success', title: 'Removed from queue' })
    },
    onError: () => addToast({ type: 'error', title: 'Remove failed' }),
  })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Brokering Queue</h1>
          <p className="text-[var(--text-secondary)] mt-1">Manage order brokering and allocation runs</p>
        </div>
        <div className="flex items-center gap-2">
          <PermissionGate permission="brokering.process">
            <button
              onClick={() => processMutation.mutate()}
              disabled={processMutation.isPending}
              className="flex items-center gap-2 px-4 py-2 bg-[var(--nexus-success-600)] text-white rounded-lg hover:bg-[var(--nexus-success-700)] disabled:opacity-50"
            >
              <Play className="w-4 h-4" />
              Process Queue
            </button>
          </PermissionGate>
          <PermissionGate permission="brokering.process">
            <button
              onClick={() => processPriorityMutation.mutate()}
              disabled={processPriorityMutation.isPending}
              className="flex items-center gap-2 px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 disabled:opacity-50"
            >
              <Zap className="w-4 h-4" />
              Priority Run
            </button>
          </PermissionGate>
          <PermissionGate permission="brokering.expire">
            <button
              onClick={() => expireMutation.mutate()}
              disabled={expireMutation.isPending}
              className="flex items-center gap-2 px-4 py-2 bg-[var(--nexus-error-600)] text-white rounded-lg hover:bg-[var(--nexus-error-700)] disabled:opacity-50"
            >
              <Trash2 className="w-4 h-4" />
              Expire Stale
            </button>
          </PermissionGate>
        </div>
      </div>

      <div className="grid grid-cols-5 gap-4">
        <EnterpriseKPICard
          title="Waiting"
          value={stats?.waiting || 0}
          icon={Clock}
          color="yellow"
        />
        <EnterpriseKPICard
          title="Processing"
          value={stats?.processing || 0}
          icon={RefreshCw}
          color="blue"
        />
        <EnterpriseKPICard
          title="Allocated"
          value={stats?.allocated || 0}
          icon={CheckCircle}
          color="green"
        />
        <EnterpriseKPICard
          title="Failed"
          value={stats?.failed || 0}
          icon={XCircle}
          color="red"
        />
        <EnterpriseKPICard
          title="Expired"
          value={stats?.expired || 0}
          icon={AlertTriangle}
          color="gray"
        />
      </div>

      <EnterpriseTabs
        tabs={tabs}
        activeTab={activeTab}
        onTabChange={(key) => setActiveTab(key as BrokeringTab)}
      />

      {activeTab === 'queue' && (
        <div className="bg-white rounded-lg shadow">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Order ID</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Priority</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Attempts</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Next Run</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingQueue ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td>
                  </tr>
                ) : queue.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">Queue is empty</td>
                  </tr>
                ) : (
                  queue.map((entry) => (
                    <tr key={entry.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3 font-mono text-sm">{entry.orderId}</td>
                      <td className="px-4 py-3">
                        <span className={clsx(
                          'px-2 py-1 text-xs rounded-full font-medium',
                          PRIORITY_COLORS[entry.priority]
                        )}>
                          {entry.priority}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx(
                          'px-2 py-1 text-xs rounded-full font-medium',
                          STATUS_COLORS[entry.status]
                        )}>
                          {entry.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                        {entry.attempts}/{entry.maxAttempts}
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                        {entry.nextRunAt
                          ? new Date(entry.nextRunAt).toLocaleString()
                          : '—'}
                      </td>
                      <td className="px-4 py-3">
                        <button
                          onClick={() => removeMutation.mutate(entry.id)}
                          className="p-1 text-[var(--text-tertiary)] hover:text-[var(--nexus-error-600)]"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'runs' && (
        <div className="bg-white rounded-lg shadow">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Run ID</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Type</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Processed</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Allocated</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Failed</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Duration</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingRuns ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td>
                  </tr>
                ) : runs.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-8 text-center text-[var(--text-secondary)]">No runs yet</td>
                  </tr>
                ) : (
                  runs.map((run) => (
                    <tr key={run.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3 font-mono text-sm">{run.id.substring(0, 8)}...</td>
                      <td className="px-4 py-3">
                        <span className={clsx(
                          'px-2 py-1 text-xs rounded-full font-medium',
                          run.runType === 'PRIORITY' ? 'bg-orange-100 text-orange-700' :
                          run.runType === 'MANUAL' ? 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]' :
                          'bg-[var(--surface-muted)] text-[var(--text-secondary)]'
                        )}>
                          {run.runType}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx(
                          'px-2 py-1 text-xs rounded-full font-medium',
                          run.status === 'COMPLETED' ? 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]' :
                          run.status === 'FAILED' ? 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)]' :
                          'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]'
                        )}>
                          {run.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{run.ordersProcessed}</td>
                      <td className="px-4 py-3 text-sm text-[var(--nexus-success-600)]">{run.ordersAllocated}</td>
                      <td className="px-4 py-3 text-sm text-[var(--nexus-error-600)]">{run.ordersFailed}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                        {run.executionTimeMs ? `${run.executionTimeMs}ms` : '—'}
                      </td>
                      <td className="px-4 py-3">
                        <button
                          onClick={() => setSelectedRun(run)}
                          className="p-1 text-[var(--text-tertiary)] hover:text-[var(--nexus-primary-600)]"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'stats' && stats && (
        <div className="grid grid-cols-2 gap-6">
          <div className="bg-white rounded-lg shadow p-6">
            <h3 className="text-lg font-semibold mb-4">Queue Distribution</h3>
            <div className="space-y-3">
              {Object.entries(stats).map(([key, value]) => (
                <div key={key} className="flex items-center justify-between">
                  <span className="text-[var(--text-secondary)] capitalize">{key}</span>
                  <span className="font-medium">{value as number}</span>
                </div>
              ))}
            </div>
          </div>
          <div className="bg-white rounded-lg shadow p-6">
            <h3 className="text-lg font-semibold mb-4">Processing Info</h3>
            <div className="space-y-3 text-sm">
              <p><strong>Scheduled:</strong> Every 5 minutes</p>
              <p><strong>Priority:</strong> Every 2 minutes</p>
              <p><strong>Expire stale:</strong> Daily at 1 AM</p>
              <p><strong>Max attempts:</strong> 3 per order</p>
            </div>
          </div>
        </div>
      )}

      {selectedRun && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-md">
            <div className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-xl font-bold">Brokering Run Details</h2>
                <button
                  onClick={() => setSelectedRun(null)}
                  className="text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]"
                >
                  <XCircle className="w-6 h-6" />
                </button>
              </div>
              <div className="space-y-3">
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Run ID:</span>
                  <span className="font-mono text-sm">{selectedRun.id.substring(0, 12)}...</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Type:</span>
                  <span>{selectedRun.runType}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Status:</span>
                  <span>{selectedRun.status}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Processed:</span>
                  <span>{selectedRun.ordersProcessed}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Allocated:</span>
                  <span className="text-[var(--nexus-success-600)]">{selectedRun.ordersAllocated}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Failed:</span>
                  <span className="text-[var(--nexus-error-600)]">{selectedRun.ordersFailed}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Duration:</span>
                  <span>{selectedRun.executionTimeMs}ms</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Started:</span>
                  <span>{new Date(selectedRun.startedAt).toLocaleString()}</span>
                </div>
                {selectedRun.completedAt && (
                  <div className="flex justify-between">
                    <span className="text-[var(--text-secondary)]">Completed:</span>
                    <span>{new Date(selectedRun.completedAt).toLocaleString()}</span>
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
