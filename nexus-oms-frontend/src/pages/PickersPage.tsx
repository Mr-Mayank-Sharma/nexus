import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Users, UserCheck, Clock, Coffee, WifiOff, Search, Eye, Plus,
  ChevronRight, ArrowRight, Filter, BarChart3,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import { pickersApi, Picker, PickerAssignment, PickerStats } from '../api/pickers'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'
import type { Tab } from '../components/enterprise'

type PickerTab = 'pickers' | 'assignments' | 'stats'

const STATUS_COLORS: Record<string, string> = {
  AVAILABLE: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  PICKING: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
  ON_BREAK: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]',
  OFFLINE: 'bg-[var(--surface-muted)] text-[var(--text-primary)]',
}

const ASSIGNMENT_STATUS_COLORS: Record<string, string> = {
  ASSIGNED: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]',
  IN_PROGRESS: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
  COMPLETED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  CANCELLED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
}

// Default node ID — in production this would come from context
const DEFAULT_NODE_ID = '00000000-0000-0000-0000-000000000001'

export default function PickersPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<PickerTab>('pickers')
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedPicker, setSelectedPicker] = useState<Picker | null>(null)
  const [nodeId] = useState(DEFAULT_NODE_ID)

  const tabs: Tab[] = [
    { key: 'pickers', label: 'Pickers', icon: Users },
    { key: 'assignments', label: 'Assignments', icon: ArrowRight },
    { key: 'stats', label: 'Stats', icon: BarChart3 },
  ]

  const { data: pickers = [], isLoading: loadingPickers } = useQuery({
    queryKey: ['pickers', nodeId],
    queryFn: async () => {
      const res = await pickersApi.getPickers(nodeId)
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'pickers',
  })

  const { data: assignments = [], isLoading: loadingAssignments } = useQuery({
    queryKey: ['picker-assignments', nodeId],
    queryFn: async () => {
      const res = await pickersApi.getActiveAssignments(nodeId)
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'assignments',
  })

  const { data: stats } = useQuery({
    queryKey: ['picker-stats', nodeId],
    queryFn: async () => {
      const res = await pickersApi.getPickerStats(nodeId)
      return res.data as PickerStats
    },
    enabled: activeTab === 'stats',
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      pickersApi.updateStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pickers'] })
      queryClient.invalidateQueries({ queryKey: ['picker-stats'] })
      addToast({ type: 'success', title: 'Picker status updated' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to update status' }),
  })

  const completeMutation = useMutation({
    mutationFn: (id: string) => pickersApi.completeAssignment(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['picker-assignments'] })
      queryClient.invalidateQueries({ queryKey: ['picker-stats'] })
      addToast({ type: 'success', title: 'Assignment completed' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to complete assignment' }),
  })

  const filteredPickers = pickers.filter(p =>
    p.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (p.employeeId && p.employeeId.toLowerCase().includes(searchTerm.toLowerCase()))
  )

  const filteredAssignments = assignments.filter(a =>
    a.orderNumber.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const formatDate = (d?: string) => d ? new Date(d).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }) : '—'

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Picker Management</h1>
          <p className="text-[var(--text-secondary)] mt-1">Assign and track picker workload</p>
        </div>
        <PermissionGate permission="pickers.create">
          <button className="flex items-center gap-2 px-4 py-2 bg-[var(--nexus-primary-600)] text-white rounded-lg hover:bg-[var(--nexus-primary-700)]">
            <Plus className="w-4 h-4" />
            Add Picker
          </button>
        </PermissionGate>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Total Pickers"
          value={stats?.totalPickers || pickers.length}
          icon={Users}
          color="blue"
        />
        <EnterpriseKPICard
          title="Available"
          value={stats?.available || pickers.filter(p => p.status === 'AVAILABLE').length}
          icon={UserCheck}
          color="green"
        />
        <EnterpriseKPICard
          title="Picking"
          value={stats?.picking || pickers.filter(p => p.status === 'PICKING').length}
          icon={Clock}
          color="purple"
        />
        <EnterpriseKPICard
          title="Active Assignments"
          value={stats?.activeAssignments || assignments.filter(a => a.status !== 'COMPLETED' && a.status !== 'CANCELLED').length}
          icon={ArrowRight}
          color="orange"
        />
      </div>

      <EnterpriseTabs
        tabs={tabs}
        activeTab={activeTab}
        onTabChange={(key) => setActiveTab(key as PickerTab)}
      />

      <div className="bg-[var(--surface-base)] rounded-lg shadow">
        <div className="p-4 border-b">
          <div className="flex items-center gap-4">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
              <input
                type="text"
                placeholder={activeTab === 'pickers' ? 'Search pickers...' : 'Search assignments...'}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-[var(--nexus-primary-500)]"
              />
            </div>
          </div>
        </div>

        {activeTab === 'pickers' && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Name</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Employee ID</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Orders Today</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Items Picked</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Max Concurrent</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Last Active</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingPickers ? (
                  <tr><td colSpan={8} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td></tr>
                ) : filteredPickers.length === 0 ? (
                  <tr><td colSpan={8} className="px-4 py-8 text-center text-[var(--text-secondary)]">No pickers found</td></tr>
                ) : (
                  filteredPickers.map((picker) => (
                    <tr key={picker.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3">
                        <button
                          onClick={() => setSelectedPicker(picker)}
                          className="text-[var(--nexus-primary-600)] hover:text-[var(--nexus-primary-800)] font-medium"
                        >
                          {picker.name}
                        </button>
                      </td>
                      <td className="px-4 py-3 text-sm font-mono text-[var(--text-secondary)]">{picker.employeeId || '—'}</td>
                      <td className="px-4 py-3 text-center">
                        <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', STATUS_COLORS[picker.status])}>
                          {picker.status.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-primary)]">{picker.ordersCompletedToday}</td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-primary)]">{picker.itemsPickedToday}</td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{picker.maxConcurrentOrders}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{formatDate(picker.lastActiveAt)}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          {picker.status === 'AVAILABLE' && (
                            <button
                              onClick={() => statusMutation.mutate({ id: picker.id, status: 'ON_BREAK' })}
                              className="px-2 py-1 text-xs bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] rounded hover:bg-[var(--nexus-warning-200)]"
                            >
                              Break
                            </button>
                          )}
                          {picker.status === 'ON_BREAK' && (
                            <button
                              onClick={() => statusMutation.mutate({ id: picker.id, status: 'AVAILABLE' })}
                              className="px-2 py-1 text-xs bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] rounded hover:bg-[var(--nexus-success-200)]"
                            >
                              Back
                            </button>
                          )}
                          {picker.status !== 'OFFLINE' && (
                            <button
                              onClick={() => statusMutation.mutate({ id: picker.id, status: 'OFFLINE' })}
                              className="px-2 py-1 text-xs bg-[var(--surface-muted)] text-[var(--text-secondary)] rounded hover:bg-[var(--surface-muted)]"
                            >
                              Offline
                            </button>
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

        {activeTab === 'assignments' && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Order #</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Picker</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Priority</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Assigned</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Started</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingAssignments ? (
                  <tr><td colSpan={7} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td></tr>
                ) : filteredAssignments.length === 0 ? (
                  <tr><td colSpan={7} className="px-4 py-8 text-center text-[var(--text-secondary)]">No active assignments</td></tr>
                ) : (
                  filteredAssignments.map((assignment) => {
                    const picker = pickers.find(p => p.id === assignment.pickerId)
                    return (
                      <tr key={assignment.id} className="hover:bg-[var(--surface-sunken)]">
                        <td className="px-4 py-3 text-sm font-medium text-[var(--nexus-primary-600)]">{assignment.orderNumber}</td>
                        <td className="px-4 py-3 text-sm text-[var(--text-primary)]">{picker?.name || assignment.pickerId.slice(0, 8)}</td>
                        <td className="px-4 py-3 text-center">
                          <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', ASSIGNMENT_STATUS_COLORS[assignment.status])}>
                            {assignment.status.replace('_', ' ')}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{assignment.priority}</td>
                        <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{formatDate(assignment.assignedAt)}</td>
                        <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{formatDate(assignment.startedAt)}</td>
                        <td className="px-4 py-3">
                          {assignment.status === 'IN_PROGRESS' && (
                            <button
                              onClick={() => completeMutation.mutate(assignment.id)}
                              className="px-2 py-1 text-xs bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] rounded hover:bg-[var(--nexus-success-200)]"
                            >
                              Complete
                            </button>
                          )}
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === 'stats' && stats && (
          <div className="p-6">
            <div className="grid grid-cols-3 gap-6">
              <div className="text-center">
                <p className="text-3xl font-bold text-[var(--nexus-primary-600)]">{stats.totalPickers}</p>
                <p className="text-sm text-[var(--text-secondary)] mt-1">Total Pickers</p>
              </div>
              <div className="text-center">
                <p className="text-3xl font-bold text-[var(--nexus-success-600)]">{stats.completedToday}</p>
                <p className="text-sm text-[var(--text-secondary)] mt-1">Completed Today</p>
              </div>
              <div className="text-center">
                <p className="text-3xl font-bold text-[var(--nexus-ai-600)]">{stats.activeAssignments}</p>
                <p className="text-sm text-[var(--text-secondary)] mt-1">Active Assignments</p>
              </div>
            </div>
          </div>
        )}
      </div>

      {selectedPicker && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-2xl">
            <div className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-xl font-bold">{selectedPicker.name}</h2>
                <button onClick={() => setSelectedPicker(null)} className="text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]">
                  <span className="sr-only">Close</span>×
                </button>
              </div>
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Employee ID</p>
                    <p className="font-medium font-mono">{selectedPicker.employeeId || '—'}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Status</p>
                    <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', STATUS_COLORS[selectedPicker.status])}>
                      {selectedPicker.status}
                    </span>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Orders Completed Today</p>
                    <p className="font-medium">{selectedPicker.ordersCompletedToday}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Items Picked Today</p>
                    <p className="font-medium">{selectedPicker.itemsPickedToday}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Max Concurrent Orders</p>
                    <p className="font-medium">{selectedPicker.maxConcurrentOrders}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Shift</p>
                    <p className="font-medium">
                      {selectedPicker.shiftStart ? formatDate(selectedPicker.shiftStart) : '—'} – {selectedPicker.shiftEnd ? formatDate(selectedPicker.shiftEnd) : '—'}
                    </p>
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
