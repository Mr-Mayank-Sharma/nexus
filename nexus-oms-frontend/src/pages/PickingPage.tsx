import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ClipboardList, Plus, UserCheck, Play, CheckCircle, XCircle, Loader2,
  Search, Package, Clock, AlertTriangle,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseToolbar from '../components/enterprise/EnterpriseToolbar'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import { useToast } from '../hooks/useToast'
import * as pickingApi from '../api/picking'
import type { Picklist, PicklistItem } from '../types'

const STATUS_ORDER = ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']

const statusIcon: Record<string, React.ReactNode> = {
  OPEN: <Clock className="w-4 h-4" />,
  IN_PROGRESS: <Play className="w-4 h-4" />,
  COMPLETED: <CheckCircle className="w-4 h-4" />,
  CANCELLED: <XCircle className="w-4 h-4" />,
}

export default function PickingPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState('all')
  const [searchTerm, setSearchTerm] = useState('')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [expandedPicklist, setExpandedPicklist] = useState<string | null>(null)
  const [createForm, setCreateForm] = useState({ name: '', waveType: 'SINGLE_ORDER', priority: 'NORMAL' })

  const { data: kpis = { activePicklists: 0, completedToday: 0, pendingItems: 0, pickedItems: 0 } } = useQuery({
    queryKey: ['picking-kpis'],
    queryFn: async () => {
      const res = await pickingApi.getPickingKPIs()
      return res.data as Record<string, number>
    },
    refetchInterval: 15000,
  })

  const { data: picklists = [], isLoading } = useQuery({
    queryKey: ['picklists', activeTab],
    queryFn: async () => {
      const status = activeTab === 'all' ? undefined : activeTab.toUpperCase()
      const res = await pickingApi.getPicklists(status)
      const d = res.data; return Array.isArray(d) ? d : (d?.content ?? []) as Picklist[]
    },
  })

  const filtered = picklists.filter(p =>
    !searchTerm || p.name.toLowerCase().includes(searchTerm.toLowerCase())
  ).sort((a, b) => STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status))

  const { data: itemsMap = {} as Record<string, PicklistItem[]> } = useQuery({
    queryKey: ['picklist-items', expandedPicklist],
    queryFn: async () => {
      if (!expandedPicklist) return {}
      const res = await pickingApi.getPicklistItems(expandedPicklist)
      const d = res.data; return { [expandedPicklist]: Array.isArray(d) ? d : (d?.content ?? []) as PicklistItem[] }
    },
    enabled: !!expandedPicklist,
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['picklists'] })
    queryClient.invalidateQueries({ queryKey: ['picking-kpis'] })
  }

  const createMutation = useMutation({
    mutationFn: () => pickingApi.createPicklist(createForm),
    onSuccess: () => { invalidate(); setShowCreateModal(false); addToast({ type: 'success', title: 'Picklist created' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to create picklist' }),
  })

  const assignMutation = useMutation({
    mutationFn: ({ id, staffId }: { id: string; staffId: string }) => pickingApi.assignPicker(id, staffId),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Picker assigned' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to assign picker' }),
  })

  const completeMutation = useMutation({
    mutationFn: (id: string) => pickingApi.completePicklist(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Picklist completed' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to complete picklist' }),
  })

  const cancelMutation = useMutation({
    mutationFn: (id: string) => pickingApi.cancelPicklist(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Picklist cancelled' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to cancel picklist' }),
  })

  const pickItemMutation = useMutation({
    mutationFn: ({ itemId, staffId }: { itemId: string; staffId: string }) => pickingApi.pickItem(itemId, staffId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['picklist-items'] })
      invalidate()
      addToast({ type: 'success', title: 'Item picked' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to pick item' }),
  })

  const tabs = [
    { id: 'all', label: 'All Picklists', count: picklists.length },
    { id: 'OPEN', label: 'Open', count: picklists.filter(p => p.status === 'OPEN').length },
    { id: 'IN_PROGRESS', label: 'In Progress', count: picklists.filter(p => p.status === 'IN_PROGRESS').length },
    { id: 'COMPLETED', label: 'Completed', count: picklists.filter(p => p.status === 'COMPLETED').length },
  ]

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Warehouse' }, { label: 'Picking' }]} />

      <EnterpriseToolbar
        title="Picking"
        searchPlaceholder="Search picklists..."
        searchValue={searchTerm}
        onSearchChange={setSearchTerm}
        actions={
          <button className="enterprise-btn-primary" onClick={() => setShowCreateModal(true)}>
            <Plus className="w-4 h-4" /> New Picklist
          </button>
        }
      />

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <EnterpriseKPICard title="Active Picklists" value={kpis.activePicklists} icon={<ClipboardList />} color="primary" />
        <EnterpriseKPICard title="Completed Today" value={kpis.completedToday} icon={<CheckCircle />} color="success" />
        <EnterpriseKPICard title="Pending Items" value={kpis.pendingItems} icon={<Clock />} color="warning" />
        <EnterpriseKPICard title="Items Picked" value={kpis.pickedItems} icon={<Package />} color="info" />
      </div>

      <EnterpriseTabs tabs={tabs.map(t => ({ id: t.id, label: t.label, badge: t.count }))} activeTab={activeTab} onChange={setActiveTab} />

      {isLoading ? (
        <div className="space-y-3">
          {[1,2,3].map(i => <div key={i} className="enterprise-skeleton h-16 w-full" />)}
        </div>
      ) : filtered.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <ClipboardList className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No picklists found</p>
          <p className="text-sm text-[var(--text-tertiary)] mt-1">Create a new picklist to get started</p>
        </div>
      ) : (
        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Name</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Wave Type</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Priority</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Progress</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {filtered.map(pl => (
                  <>
                    <tr key={pl.id} className="enterprise-table-row cursor-pointer" onClick={() => setExpandedPicklist(expandedPicklist === pl.id ? null : pl.id)}>
                      <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{pl.name}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{pl.waveType}</td>
                      <td className="px-4 py-3">
                        <EnterpriseStatusBadge status={pl.priority === 'URGENT' ? 'error' : pl.priority === 'HIGH' ? 'warning' : 'neutral'} label={pl.priority} />
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5">
                          {statusIcon[pl.status]}
                          <EnterpriseStatusBadge status={pl.status === 'OPEN' ? 'pending' : pl.status === 'IN_PROGRESS' ? 'warning' : pl.status === 'COMPLETED' ? 'success' : 'error'} label={pl.status} />
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-center gap-2">
                          <div className="w-24 h-2 bg-[var(--bg-tertiary)] rounded-full overflow-hidden">
                            <div className="h-full bg-[var(--color-primary)] rounded-full transition-all"
                              style={{ width: `${pl.totalItems > 0 ? Math.round(pl.pickedItems / pl.totalItems * 100) : 0}%` }} />
                          </div>
                          <span className="text-xs text-[var(--text-tertiary)]">{pl.pickedItems}/{pl.totalItems}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1">
                          {pl.status === 'OPEN' && (
                            <>
                              <button className="enterprise-btn-ghost p-1.5" title="Assign Picker"
                                onClick={e => { e.stopPropagation(); const staffId = prompt('Staff ID:'); if (staffId) assignMutation.mutate({ id: pl.id, staffId }); }}>
                                <UserCheck className="w-4 h-4" />
                              </button>
                              <button className="enterprise-btn-ghost p-1.5 text-red-500" title="Cancel"
                                onClick={e => { e.stopPropagation(); cancelMutation.mutate(pl.id); }}>
                                <XCircle className="w-4 h-4" />
                              </button>
                            </>
                          )}
                          {pl.status === 'IN_PROGRESS' && (
                            <button className="enterprise-btn-ghost p-1.5 text-green-500" title="Complete"
                              onClick={e => { e.stopPropagation(); completeMutation.mutate(pl.id); }}>
                              <CheckCircle className="w-4 h-4" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                    {expandedPicklist === pl.id && (
                      <tr key={`${pl.id}-items`}>
                        <td colSpan={6} className="px-4 py-3 bg-[var(--bg-tertiary)]/30">
                          <div className="pl-4 border-l-2 border-[var(--color-primary)]/30 space-y-1">
                            <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase mb-2">Picklist Items</p>
                            {itemsMap[pl.id]?.length ? (
                              <table className="w-full text-xs">
                                <thead>
                                  <tr className="text-[var(--text-tertiary)]">
                                    <th className="text-left py-1">SKU</th>
                                    <th className="text-left py-1">Product</th>
                                    <th className="text-center py-1">Qty</th>
                                    <th className="text-center py-1">Picked</th>
                                    <th className="text-left py-1">Status</th>
                                    <th className="text-right py-1">Action</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {itemsMap[pl.id].map(item => (
                                    <tr key={item.id} className="border-t border-[var(--border-subtle)]/50">
                                      <td className="py-1.5 font-mono">{item.sku}</td>
                                      <td className="py-1.5 text-[var(--text-secondary)]">{item.productName || '-'}</td>
                                      <td className="py-1.5 text-center">{item.quantity}</td>
                                      <td className="py-1.5 text-center">{item.pickedQuantity}</td>
                                      <td className="py-1.5"><EnterpriseStatusBadge status={item.status === 'PICKED' ? 'success' : 'pending'} label={item.status} size="sm" /></td>
                                      <td className="py-1.5 text-right">
                                        {item.status === 'PENDING' && (
                                          <button className="enterprise-btn-primary text-[10px] px-2 py-1"
                                            onClick={() => { const staffId = prompt('Staff ID:'); if (staffId) pickItemMutation.mutate({ itemId: item.id, staffId }); }}>
                                            Pick Now
                                          </button>
                                        )}
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            ) : (
                              <p className="text-xs text-[var(--text-tertiary)] py-2">No items in this picklist</p>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={() => setShowCreateModal(false)}>
          <div className="enterprise-card p-6 w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Create Picklist</h2>
            <div className="space-y-4">
              <div>
                <label className="enterprise-label">Name</label>
                <input className="enterprise-input w-full" value={createForm.name} onChange={e => setCreateForm(p => ({ ...p, name: e.target.value }))} placeholder="e.g. Wave #1 - Zone A" />
              </div>
              <div>
                <label className="enterprise-label">Wave Type</label>
                <select className="enterprise-input w-full" value={createForm.waveType} onChange={e => setCreateForm(p => ({ ...p, waveType: e.target.value }))}>
                  <option value="SINGLE_ORDER">Single Order</option>
                  <option value="BATCH">Batch</option>
                  <option value="WAVE">Wave</option>
                  <option value="ZONE">Zone</option>
                </select>
              </div>
              <div>
                <label className="enterprise-label">Priority</label>
                <select className="enterprise-input w-full" value={createForm.priority} onChange={e => setCreateForm(p => ({ ...p, priority: e.target.value }))}>
                  <option value="LOW">Low</option>
                  <option value="NORMAL">Normal</option>
                  <option value="HIGH">High</option>
                  <option value="URGENT">Urgent</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button className="enterprise-btn-secondary" onClick={() => setShowCreateModal(false)}>Cancel</button>
              <button className="enterprise-btn-primary" onClick={() => createMutation.mutate()} disabled={!createForm.name || createMutation.isPending}>
                {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                Create
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
