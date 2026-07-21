import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Gauge, AlertTriangle, CheckCircle, Clock, Search, Eye, Plus,
  ToggleLeft, ToggleRight, Edit, Activity, TrendingUp,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import {
  fulfillmentLimitsApi, FulfillmentLimit, CapacityLog, CapacityCheck,
} from '../api/fulfillmentLimits'
import { EnterpriseTabs, EnterpriseKPICard } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'
import type { Tab } from '../components/enterprise'

type LimitTab = 'limits' | 'alerts' | 'history'

const ACTION_COLORS: Record<string, string> = {
  ORDER_ASSIGNED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
  ORDER_REMOVED: 'bg-[var(--surface-muted)] text-[var(--text-primary)]',
  LIMIT_REACHED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
  LIMIT_RESET: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
}

export default function FulfillmentLimitsPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<LimitTab>('limits')
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedLimit, setSelectedLimit] = useState<FulfillmentLimit | null>(null)
  const [capacityData, setCapacityData] = useState<Record<string, CapacityCheck>>({})

  const tabs: Tab[] = [
    { key: 'limits', label: 'Limits', icon: Gauge },
    { key: 'alerts', label: 'Alerts', icon: AlertTriangle },
    { key: 'history', label: 'History', icon: Activity },
  ]

  const { data: limits = [], isLoading: loadingLimits } = useQuery({
    queryKey: ['fulfillment-limits'],
    queryFn: async () => {
      const res = await fulfillmentLimitsApi.getLimits()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'limits',
  })

  const { data: alerts = [], isLoading: loadingAlerts } = useQuery({
    queryKey: ['capacity-alerts'],
    queryFn: async () => {
      const res = await fulfillmentLimitsApi.getCapacityAlerts()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'alerts',
  })

  const { data: history = [], isLoading: loadingHistory } = useQuery({
    queryKey: ['capacity-history'],
    queryFn: async () => {
      const res = await fulfillmentLimitsApi.getCapacityAlerts()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'history',
  })

  const toggleMutation = useMutation({
    mutationFn: ({ nodeId, enabled }: { nodeId: string; enabled: boolean }) =>
      fulfillmentLimitsApi.toggleFulfillment(nodeId, enabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fulfillment-limits'] })
      addToast({ type: 'success', title: 'Fulfillment toggled' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to toggle fulfillment' }),
  })

  const resetMutation = useMutation({
    mutationFn: () => fulfillmentLimitsApi.resetDailyCounts(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fulfillment-limits'] })
      addToast({ type: 'success', title: 'Daily counts reset' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to reset counts' }),
  })

  const checkCapacity = async (nodeId: string) => {
    try {
      const res = await fulfillmentLimitsApi.checkCapacity(nodeId)
      setCapacityData(prev => ({ ...prev, [nodeId]: res.data }))
    } catch {
      // ignore
    }
  }

  const filteredLimits = limits.filter(l =>
    l.nodeId.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const filteredAlerts = alerts.filter(a =>
    a.nodeId.toLowerCase().includes(searchTerm.toLowerCase()) ||
    a.action.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const filteredHistory = history.filter(h =>
    h.nodeId.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const formatDate = (d?: string) => d ? new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'

  const getUtilizationColor = (current?: number, max?: number) => {
    if (!max || !current) return 'text-[var(--text-secondary)]'
    const pct = current / max
    if (pct >= 0.9) return 'text-[var(--nexus-error-600)]'
    if (pct >= 0.7) return 'text-[var(--nexus-warning-600)]'
    return 'text-[var(--nexus-success-600)]'
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Fulfillment Limits</h1>
          <p className="text-[var(--text-secondary)] mt-1">Manage capacity limits per fulfillment node</p>
        </div>
        <div className="flex items-center gap-2">
          <PermissionGate permission="fulfillment.edit">
            <button
              onClick={() => resetMutation.mutate()}
              className="flex items-center gap-2 px-4 py-2 bg-[var(--surface-muted)] text-[var(--text-secondary)] rounded-lg hover:bg-[var(--surface-muted)]"
            >
              Reset Daily Counts
            </button>
          </PermissionGate>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Total Nodes"
          value={limits.length}
          icon={Gauge}
          color="blue"
        />
        <EnterpriseKPICard
          title="Enabled"
          value={limits.filter(l => l.fulfillmentEnabled).length}
          icon={CheckCircle}
          color="green"
        />
        <EnterpriseKPICard
          title="At Capacity"
          value={limits.filter(l => {
            if (!l.maxOrdersPerDay) return false
            return l.currentOrdersToday >= l.maxOrdersPerDay * 0.9
          }).length}
          icon={AlertTriangle}
          color="red"
        />
        <EnterpriseKPICard
          title="Capacity Alerts"
          value={alerts.length}
          icon={Activity}
          color="orange"
        />
      </div>

      <EnterpriseTabs
        tabs={tabs}
        activeTab={activeTab}
        onTabChange={(key) => setActiveTab(key as LimitTab)}
      />

      <div className="bg-white rounded-lg shadow">
        <div className="p-4 border-b">
          <div className="flex items-center gap-4">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
              <input
                type="text"
                placeholder="Search by node ID..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-[var(--nexus-primary-500)]"
              />
            </div>
          </div>
        </div>

        {activeTab === 'limits' && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Node ID</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Orders Today</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Max/Day</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Orders Week</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Max/Week</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Items Today</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Enabled</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Capacity</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingLimits ? (
                  <tr><td colSpan={9} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td></tr>
                ) : filteredLimits.length === 0 ? (
                  <tr><td colSpan={9} className="px-4 py-8 text-center text-[var(--text-secondary)]">No limits configured</td></tr>
                ) : (
                  filteredLimits.map((limit) => {
                    const cap = capacityData[limit.nodeId]
                    const dayPct = limit.maxOrdersPerDay ? (limit.currentOrdersToday / limit.maxOrdersPerDay * 100) : 0
                    return (
                      <tr key={limit.id} className="hover:bg-[var(--surface-sunken)]">
                        <td className="px-4 py-3 text-sm font-mono text-[var(--text-primary)]">{limit.nodeId.slice(0, 8)}...</td>
                        <td className={clsx('px-4 py-3 text-center text-sm font-medium', getUtilizationColor(limit.currentOrdersToday, limit.maxOrdersPerDay))}>
                          {limit.currentOrdersToday}
                        </td>
                        <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{limit.maxOrdersPerDay || '∞'}</td>
                        <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{limit.currentOrdersThisWeek}</td>
                        <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{limit.maxOrdersPerWeek || '∞'}</td>
                        <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{limit.currentItemsToday}</td>
                        <td className="px-4 py-3 text-center">
                          <PermissionGate permission="fulfillment.edit">
                            <button onClick={() => toggleMutation.mutate({ nodeId: limit.nodeId, enabled: !limit.fulfillmentEnabled })}>
                              {limit.fulfillmentEnabled ? <ToggleRight className="w-6 h-6 text-[var(--nexus-success-600)] mx-auto" /> : <ToggleLeft className="w-6 h-6 text-[var(--text-tertiary)] mx-auto" />}
                            </button>
                          </PermissionGate>
                        </td>
                        <td className="px-4 py-3 text-center">
                          {limit.maxOrdersPerDay ? (
                            <div className="w-full bg-[var(--surface-muted)] rounded-full h-2 mx-auto" style={{ maxWidth: 80 }}>
                              <div
                                className={clsx('h-2 rounded-full', dayPct >= 90 ? 'bg-[var(--nexus-error-50)]0' : dayPct >= 70 ? 'bg-[var(--nexus-warning-50)]0' : 'bg-[var(--nexus-success-50)]0')}
                                style={{ width: `${Math.min(dayPct, 100)}%` }}
                              />
                            </div>
                          ) : (
                            <span className="text-xs text-[var(--text-tertiary)]">No limit</span>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          <button
                            onClick={() => { setSelectedLimit(limit); checkCapacity(limit.nodeId) }}
                            className="p-1 text-[var(--text-tertiary)] hover:text-[var(--nexus-primary-600)]"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === 'alerts' && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Node ID</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Action</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Before</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">After</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Capacity %</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingAlerts ? (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td></tr>
                ) : filteredAlerts.length === 0 ? (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">No alerts</td></tr>
                ) : (
                  filteredAlerts.map((alert) => (
                    <tr key={alert.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3 text-sm font-mono text-[var(--text-primary)]">{alert.nodeId.slice(0, 8)}...</td>
                      <td className="px-4 py-3">
                        <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', ACTION_COLORS[alert.action] || 'bg-[var(--surface-muted)] text-[var(--text-primary)]')}>
                          {alert.action.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{alert.ordersBefore ?? '—'}</td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{alert.ordersAfter ?? '—'}</td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">
                        {alert.capacityPercentage != null ? `${alert.capacityPercentage.toFixed(0)}%` : '—'}
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{formatDate(alert.createdAt)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === 'history' && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Node ID</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Action</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Orders Before</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Orders After</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Capacity</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Timestamp</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingHistory ? (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td></tr>
                ) : filteredHistory.length === 0 ? (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">No history</td></tr>
                ) : (
                  filteredHistory.map((entry) => (
                    <tr key={entry.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3 text-sm font-mono text-[var(--text-primary)]">{entry.nodeId.slice(0, 8)}...</td>
                      <td className="px-4 py-3">
                        <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', ACTION_COLORS[entry.action] || 'bg-[var(--surface-muted)] text-[var(--text-primary)]')}>
                          {entry.action.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{entry.ordersBefore ?? '—'}</td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{entry.ordersAfter ?? '—'}</td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">
                        {entry.capacityPercentage != null ? `${entry.capacityPercentage.toFixed(0)}%` : '—'}
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{formatDate(entry.createdAt)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selectedLimit && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-2xl">
            <div className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-xl font-bold">Fulfillment Limit Details</h2>
                <button onClick={() => setSelectedLimit(null)} className="text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]">
                  <span className="sr-only">Close</span>×
                </button>
              </div>
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Node ID</p>
                    <p className="font-medium font-mono">{selectedLimit.nodeId}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Enabled</p>
                    <p className="font-medium">{selectedLimit.fulfillmentEnabled ? 'Yes' : 'No'}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Orders Today</p>
                    <p className="font-medium">{selectedLimit.currentOrdersToday} / {selectedLimit.maxOrdersPerDay || '∞'}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Orders This Week</p>
                    <p className="font-medium">{selectedLimit.currentOrdersThisWeek} / {selectedLimit.maxOrdersPerWeek || '∞'}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Items Today</p>
                    <p className="font-medium">{selectedLimit.currentItemsToday} / {selectedLimit.maxItemsPerDay || '∞'}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Alert Threshold</p>
                    <p className="font-medium">{(selectedLimit.alertThreshold * 100).toFixed(0)}%</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Last Reset</p>
                    <p className="font-medium">{formatDate(selectedLimit.lastResetAt)}</p>
                  </div>
                </div>
                {capacityData[selectedLimit.nodeId] && (
                  <div className="mt-4 p-4 bg-[var(--surface-sunken)] rounded-lg">
                    <h3 className="text-sm font-medium text-[var(--text-secondary)] mb-2">Live Capacity Check</h3>
                    <div className="grid grid-cols-3 gap-4 text-center">
                      <div>
                        <p className="text-lg font-bold">{capacityData[selectedLimit.nodeId].dayUtilization.toFixed(0)}%</p>
                        <p className="text-xs text-[var(--text-secondary)]">Day Utilization</p>
                      </div>
                      <div>
                        <p className="text-lg font-bold">{capacityData[selectedLimit.nodeId].weekUtilization.toFixed(0)}%</p>
                        <p className="text-xs text-[var(--text-secondary)]">Week Utilization</p>
                      </div>
                      <div>
                        <p className={clsx('text-lg font-bold', capacityData[selectedLimit.nodeId].atCapacity ? 'text-[var(--nexus-error-600)]' : 'text-[var(--nexus-success-600)]')}>
                          {capacityData[selectedLimit.nodeId].atCapacity ? 'At Capacity' : 'Available'}
                        </p>
                        <p className="text-xs text-[var(--text-secondary)]">Status</p>
                      </div>
                    </div>
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
