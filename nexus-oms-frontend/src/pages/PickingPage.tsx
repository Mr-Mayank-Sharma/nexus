import { useState, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ClipboardList, Plus, UserCheck, Play, CheckCircle, XCircle, Loader2,
  Search, Package, Clock, Printer, ChevronDown,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseToolbar from '../components/enterprise/EnterpriseToolbar'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import PermissionGate from '../components/rbac/PermissionGate'
import { useToast } from '../hooks/useToast'
import { useAuth } from '../context/AuthContext'
import * as pickingApi from '../api/picking'
import * as ordersApi from '../api/orders'
import * as warehouseApi from '../api/warehouse'
import client from '../api/client'
import type { Picklist, PicklistItem, Order, WarehouseStaff } from '../types'

const STATUS_ORDER = ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']

const statusIcon: Record<string, React.ReactNode> = {
  OPEN: <span className="w-6 h-6 rounded-full bg-[var(--nexus-warning-100)] flex items-center justify-center"><Clock className="w-3.5 h-3.5 text-[var(--nexus-warning-600)]" /></span>,
  IN_PROGRESS: <span className="w-6 h-6 rounded-full bg-[var(--nexus-primary-100)] flex items-center justify-center"><Play className="w-3.5 h-3.5 text-[var(--nexus-primary-600)]" /></span>,
  COMPLETED: <span className="w-6 h-6 rounded-full bg-[var(--nexus-success-100)] flex items-center justify-center"><CheckCircle className="w-3.5 h-3.5 text-[var(--nexus-success-600)]" /></span>,
  CANCELLED: <span className="w-6 h-6 rounded-full bg-[var(--nexus-error-50)] flex items-center justify-center"><XCircle className="w-3.5 h-3.5 text-[var(--nexus-error-600)]" /></span>,
}

export default function PickingPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState('all')
  const [searchTerm, setSearchTerm] = useState('')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [expandedPicklist, setExpandedPicklist] = useState<string | null>(null)
  const [createForm, setCreateForm] = useState({ name: '', waveType: 'SINGLE_ORDER', priority: 'NORMAL' })
  const [selectedOrders, setSelectedOrders] = useState<Array<{ id: string; display: string }>>([])
  const [assignTarget, setAssignTarget] = useState<{ picklistId: string } | null>(null)
  const [pickTarget, setPickTarget] = useState<{ itemId: string } | null>(null)
  const [orderSearchIdx, setOrderSearchIdx] = useState<number | null>(null)
  const [orderSearch, setOrderSearch] = useState('')
  const orderRefs = useRef<Map<number, HTMLDivElement>>(new Map())

  const { data: openOrders = [] } = useQuery({
    queryKey: ['open-orders'],
    queryFn: async () => {
      const res = await ordersApi.getOrders({ size: '5000' })
      const d = res.data
      const list = (Array.isArray(d) ? d : (d as any)?.content ?? []) as Order[]
      return list.filter(o => ['PENDING', 'CONFIRMED', 'ALLOCATED'].includes(o.status))
    },
  })

  const filteredOrders = openOrders.filter(o =>
    !orderSearch || o.orderNumber?.toLowerCase().includes(orderSearch.toLowerCase()) ||
    o.customerName?.toLowerCase().includes(orderSearch.toLowerCase()) ||
    o.id?.toLowerCase().includes(orderSearch.toLowerCase())
  )

  const { data: firstWarehouse } = useQuery({
    queryKey: ['first-warehouse'],
    queryFn: async () => {
      const res = await warehouseApi.getWarehouses(0, 1)
      const d = res.data as any
      const list = d?.content ?? []
      return list[0] as { id: string } | undefined
    },
  })

  const { user } = useAuth()
  const username = user?.username || ''

  const { data: staffList = [] } = useQuery({
    queryKey: ['warehouse-staff', firstWarehouse?.id],
    queryFn: async () => {
      if (!firstWarehouse?.id) return []
      const res = await warehouseApi.getStaff(firstWarehouse.id)
      return (res.data ?? []) as WarehouseStaff[]
    },
    enabled: !!firstWarehouse?.id,
  })

  const { data: currentStaffId } = useQuery({
    queryKey: ['current-staff-id', username],
    queryFn: async () => {
      if (!username) return null
      const res = await client.get('/picking/user-staff', { params: { username } })
      const d = res.data as any
      if (d?.success && d?.data?.staffId) return d.data.staffId as string
      return null
    },
    enabled: !!username,
  })

  const [staffSearch, setStaffSearch] = useState('')
  const [staffSearchOpen, setStaffSearchOpen] = useState<'assign' | 'pick' | null>(null)
  const staffRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      let inside = false
      orderRefs.current.forEach(ref => { if (ref.contains(e.target as Node)) inside = true })
      if (!inside) setOrderSearchIdx(null)
      if (staffRef.current && !staffRef.current.contains(e.target as Node))
        setStaffSearchOpen(null)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const isMultiOrder = createForm.waveType === 'BATCH' || createForm.waveType === 'WAVE'

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

  const pickerWorkload = picklists.reduce((acc, pl) => {
    if (pl.assigneeId && ['OPEN', 'IN_PROGRESS'].includes(pl.status))
      acc[pl.assigneeId] = (acc[pl.assigneeId] || 0) + 1
    return acc
  }, {} as Record<string, number>)

  const filteredPickers = staffList
    .filter(s => (s as any).role === 'PICKER')
    .filter(s => {
      const ss: any = s
      const fn = ss.firstName || ''
      const ln = ss.lastName || ''
      const code = ss.employeeCode || ''
      const name = ss.name || `${fn} ${ln}`.trim()
      return !staffSearch ||
        name.toLowerCase().includes(staffSearch.toLowerCase()) ||
        code.toLowerCase().includes(staffSearch.toLowerCase())
    })

  const pickerNameMap = staffList.reduce((acc, s) => {
    const ss: any = s
    acc[s.id] = ss.name || `${ss.firstName || ''} ${ss.lastName || ''}`.trim()
    return acc
  }, {} as Record<string, string>)

  const filtered = picklists.filter(p => {
    if (activeTab === 'mine' && currentStaffId && p.assigneeId !== currentStaffId) return false
    if (activeTab !== 'all' && activeTab !== 'mine' && p.status !== activeTab) return false
    if (searchTerm && !p.name.toLowerCase().includes(searchTerm.toLowerCase())) return false
    return true
  }).sort((a, b) => STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status))

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
    mutationFn: async () => {
      const orderIds = selectedOrders.map(o => o.id)
      const res = await pickingApi.createPicklist({
        name: createForm.name,
        waveType: createForm.waveType as any,
        priority: createForm.priority as any,
        orderIds: JSON.stringify(orderIds),
      })
      const pl = res.data as any
      if (pl?.id) {
        for (const oid of orderIds) {
          try {
            await client.post('/picking/seed-items', { picklistId: pl.id, orderId: oid })
          } catch { /* seed best-effort */ }
        }
      }
      return res
    },
    onSuccess: () => {
      invalidate(); setShowCreateModal(false)
      setCreateForm({ name: '', waveType: 'SINGLE_ORDER', priority: 'NORMAL' })
      setSelectedOrders([])
      addToast({ type: 'success', title: 'Picklist created' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to create picklist' }),
  })

  const assignMutation = useMutation({
    mutationFn: ({ id, staffId }: { id: string; staffId: string }) =>
      pickingApi.assignPicker(id, staffId),
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

  const startPickingMutation = useMutation({
    mutationFn: (picklistId: string) => client.post(`/picking/lists/${picklistId}/start`),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Picking started' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to start picking' }),
  })

  function handlePrintPicklist(pl: Picklist) {
    const items = itemsMap[pl.id] || []
    const win = window.open('', '_blank')
    if (!win) return
    win.document.write(`<!DOCTYPE html><html><head><title>Picklist - ${pl.name}</title>
      <style>body{font-family:monospace;padding:20px;max-width:500px;margin:auto}
      h1{font-size:18px;border-bottom:2px solid #000;padding-bottom:6px}
      .meta{font-size:11px;color:#666;margin:8px 0}
      table{width:100%;border-collapse:collapse;margin:12px 0}
      th{background:#f0f0f0;text-align:left;padding:6px 8px;border-bottom:2px solid #ddd;font-size:11px}
      td{padding:6px 8px;border-bottom:1px solid #eee;font-size:12px}
      .no-print{text-align:center;margin-bottom:12px}
      </style></head><body>
      <button class="no-print" onclick="window.print()" style="padding:8px 24px;cursor:pointer;border:1px solid #ccc;border-radius:4px;background:#fff;font-size:14px">Print Picklist</button>
      <h1>${pl.name}</h1>
      <div class="meta">Status: ${pl.status} | Wave: ${pl.waveType} | Priority: ${pl.priority} | ${pl.totalItems} items</div>
      ${pl.assigneeId ? `<div class="meta">Picker: ${pl.assigneeId}</div>` : ''}
      <table><tr><th>SKU</th><th>Product</th><th>Qty</th><th>Location</th></tr>
      ${items.map((i: any) => `<tr><td>${i.sku || ''}</td><td>${i.productName || ''}</td><td>${i.quantity || 0}</td><td>${i.fromLocation || i.fromBinId || '-'}</td></tr>`).join('')}
      </table>
      ${pl.notes ? `<p style="font-size:11px;color:#666">Notes: ${pl.notes}</p>` : ''}
      </body></html>`)
    win.document.close()
  }

  const tabs = [
    { id: 'all', label: 'All Picklists', count: picklists.length },
    { id: 'mine', label: 'My Picklists', count: currentStaffId ? picklists.filter(p => p.assigneeId === currentStaffId).length : 0 },
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
        onSearch={setSearchTerm}
        autocomplete={{
          fetchSuggestions: async (q) => {
            if (!q) return picklists.slice(0, 10)
            const term = q.toLowerCase()
            return picklists.filter(p => p.name?.toLowerCase().includes(term) || p.id?.toLowerCase().includes(term)).slice(0, 10)
          },
          onSelect: (item: any) => setSearchTerm(item.name || item.id),
          getOptionLabel: (item: any) => `${item.name || item.id} (${item.status})`,
          getOptionValue: (item: any) => item.id,
          minChars: 1,
        }}
        actions={[
          { label: 'New Picklist', icon: <Plus className="w-4 h-4" />, onClick: () => setShowCreateModal(true), variant: 'primary', permission: { resource: 'warehouse', action: 'create' } },
        ]}
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
                              {pl.assigneeId ? (
                                <>
                                  <div className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-[var(--nexus-primary-50)] border border-[var(--nexus-primary-200)]">
                                    <div className="w-5 h-5 rounded-full bg-[var(--nexus-primary-200)] flex items-center justify-center text-[9px] font-bold text-[var(--nexus-primary-700)] shrink-0">
                                      {(pickerNameMap[pl.assigneeId] || '?').split(' ').map((n: string) => n[0]).join('').slice(0, 2).toUpperCase()}
                                    </div>
                                    <span className="text-xs font-medium text-[var(--nexus-primary-700)] truncate max-w-[90px]">{pickerNameMap[pl.assigneeId] || pl.assigneeId.slice(0, 8)}</span>
                                  </div>
                                  <PermissionGate resource="warehouse" action="edit">
                                    <button className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-md bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)] hover:bg-[var(--nexus-success-100)] border border-[var(--nexus-success-200)] transition-colors" title="Start Picking"
                                      onClick={e => { e.stopPropagation(); startPickingMutation.mutate(pl.id); }}>
                                      <Play className="w-3.5 h-3.5" /> Start
                                    </button>
                                  </PermissionGate>
                                  <PermissionGate resource="warehouse" action="edit">
                                    <button className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-md bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)] hover:bg-[var(--nexus-warning-100)] border border-[var(--nexus-warning-200)] transition-colors" title="Change Picker"
                                      onClick={e => { e.stopPropagation(); setAssignTarget({ picklistId: pl.id }); }}>
                                      <UserCheck className="w-3.5 h-3.5" /> Change
                                    </button>
                                  </PermissionGate>
                                </>
                              ) : (
                                <PermissionGate resource="warehouse" action="edit">
                                  <button className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-md bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)] hover:bg-[var(--nexus-primary-100)] border border-[var(--nexus-primary-200)] transition-colors" title="Assign Picker"
                                    onClick={e => { e.stopPropagation(); setAssignTarget({ picklistId: pl.id }); }}>
                                    <UserCheck className="w-3.5 h-3.5" /> Assign
                                  </button>
                                </PermissionGate>
                              )}
                              <PermissionGate resource="warehouse" action="read">
                                <button className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-md bg-[var(--surface-sunken)] text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] border border-[var(--border-default)] transition-colors" title="Print Picklist"
                                  onClick={e => { e.stopPropagation(); handlePrintPicklist(pl); }}>
                                  <Printer className="w-3.5 h-3.5" /> Print
                                </button>
                              </PermissionGate>
                              <PermissionGate resource="warehouse" action="delete">
                                <button className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-md bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)] hover:bg-[var(--nexus-error-50)] border border-[var(--nexus-error-200)] transition-colors" title="Cancel Picklist"
                                  onClick={e => { e.stopPropagation(); cancelMutation.mutate(pl.id); }}>
                                  <XCircle className="w-3.5 h-3.5" /> Cancel
                                </button>
                              </PermissionGate>
                            </>
                          )}
                          {pl.status === 'IN_PROGRESS' && (
                            pl.pickedItems >= pl.totalItems && pl.totalItems > 0 ? (
                              <PermissionGate resource="warehouse" action="edit">
                                <button className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-md border bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)] border-[var(--nexus-success-200)] hover:bg-[var(--nexus-success-100)] cursor-pointer transition-colors" title="Complete Picklist"
                                  onClick={e => { e.stopPropagation(); completeMutation.mutate(pl.id); }}>
                                  <CheckCircle className="w-3.5 h-3.5" /> Complete
                                </button>
                              </PermissionGate>
                            ) : (
                              <button className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-md border bg-[var(--surface-sunken)] text-[var(--text-tertiary)] border-[var(--border-default)] cursor-not-allowed transition-colors" title="Pick all items first" disabled>
                                <CheckCircle className="w-3.5 h-3.5" /> Complete
                              </button>
                            )
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
                                    <th className="text-left py-1">Location</th>
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
                                      <td className="py-1.5 font-mono text-xs">
                                        {item.fromLocation ? (
                                          <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)] font-semibold">{item.fromLocation}</span>
                                        ) : <span className="text-[var(--text-tertiary)]">—</span>}
                                      </td>
                                      <td className="py-1.5 font-mono">{item.sku}</td>
                                      <td className="py-1.5 text-[var(--text-secondary)]">{item.productName || '-'}</td>
                                      <td className="py-1.5 text-center">{item.quantity}</td>
                                      <td className="py-1.5 text-center">{item.pickedQuantity}</td>
                                      <td className="py-1.5"><EnterpriseStatusBadge status={item.status === 'PICKED' ? 'success' : 'pending'} label={item.status} size="sm" /></td>
                                      <td className="py-1.5 text-right">
                                        {item.status === 'PENDING' && pl.startedAt && (
                                          <PermissionGate resource="warehouse" action="edit">
                                            <button className="inline-flex items-center gap-1 px-2 py-1 text-[11px] font-medium rounded-md bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)] hover:bg-[var(--nexus-primary-100)] border border-[var(--nexus-primary-200)] transition-colors"
                                              onClick={() => { setPickTarget({ itemId: item.id }); }}>
                                              <Play className="w-3 h-3" /> Pick Now
                                            </button>
                                          </PermissionGate>
                                        )}
                                        {item.status === 'PENDING' && !pl.startedAt && (
                                          <span className="text-[10px] text-[var(--text-tertiary)]">Start first</span>
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

      {assignTarget && (() => {
        const targetPl = picklists.find(p => p.id === assignTarget.picklistId)
        const isChange = !!targetPl?.assigneeId
        return (
        <div className="enterprise-modal-overlay" onClick={() => setAssignTarget(null)}>
          <div className="enterprise-card p-6 w-full max-w-sm mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">{isChange ? 'Change Picker' : 'Assign Picker'}</h2>
            <div ref={staffRef} className="relative">
              <input className="enterprise-input w-full" value={staffSearch}
                onChange={e => { setStaffSearch(e.target.value); setStaffSearchOpen('assign'); }}
                onFocus={() => setStaffSearchOpen('assign')}
                placeholder="Search picker by name or code..." autoFocus />
              {staffSearchOpen === 'assign' && (
                <div className="absolute z-50 mt-1 w-full bg-[var(--surface-base)] border border-[var(--border-default)] rounded-lg shadow-lg max-h-60 overflow-y-auto">
                  {filteredPickers.length === 0 ? (
                    <p className="p-3 text-sm text-[var(--text-tertiary)]">No pickers found</p>
                  ) : filteredPickers.slice(0, 30).map(s => {
                    const ss: any = s
                    const name = ss.name || `${ss.firstName || ''} ${ss.lastName || ''}`.trim()
                    const workload = pickerWorkload[s.id] || 0
                    const shift = ss.shift || '-'
                    return (
                      <PermissionGate resource="warehouse" action="edit">
                        <button key={s.id} type="button"
                          className="w-full text-left px-3 py-2.5 text-sm hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] border-b border-[var(--border-subtle)] last:border-0 flex items-center gap-3"
                          onClick={() => {
                            assignMutation.mutate({ id: assignTarget.picklistId, staffId: s.id })
                            setAssignTarget(null)
                            setStaffSearch('')
                            setStaffSearchOpen(null)
                          }}>
                        <div className="w-8 h-8 rounded-full bg-[var(--nexus-primary-100)] flex items-center justify-center text-[var(--nexus-primary-700)] text-xs font-bold shrink-0">
                          {name.split(' ').map((n: string) => n[0]).join('').slice(0, 2).toUpperCase()}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="font-medium text-[var(--text-primary)] truncate">{name}</div>
                          <div className="text-[10px] text-[var(--text-tertiary)]">{ss.employeeCode || ''}</div>
                        </div>
                        <div className="text-right shrink-0">
                          <div className={`text-xs font-semibold ${workload > 0 ? 'text-[var(--nexus-warning-600)]' : 'text-[var(--nexus-success-600)]'}`}>
                            {workload > 0 ? `${workload} active` : 'Free'}
                          </div>
                          <div className="text-[10px] text-[var(--text-tertiary)]">{shift}</div>
                        </div>
                      </button>
                      </PermissionGate>
                    )
                  })}
                </div>
              )}
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button className="enterprise-btn-secondary" onClick={() => { setAssignTarget(null); setStaffSearch(''); setStaffSearchOpen(null); }}>Cancel</button>
            </div>
          </div>
        </div>
      )})()}
      
      {pickTarget && (
        <div className="enterprise-modal-overlay" onClick={() => setPickTarget(null)}>
          <div className="enterprise-card p-6 w-full max-w-sm mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Pick Item</h2>
            <p className="text-sm text-[var(--text-secondary)] mb-3">Select the picker who picked this item:</p>
            <div ref={staffRef} className="relative">
              <input className="enterprise-input w-full" value={staffSearch}
                onChange={e => { setStaffSearch(e.target.value); setStaffSearchOpen('pick'); }}
                onFocus={() => setStaffSearchOpen('pick')}
                placeholder="Search picker by name or code..." autoFocus />
              {staffSearchOpen === 'pick' && (
                <div className="absolute z-50 mt-1 w-full bg-[var(--surface-base)] border border-[var(--border-default)] rounded-lg shadow-lg max-h-60 overflow-y-auto">
                  {filteredPickers.length === 0 ? (
                    <p className="p-3 text-sm text-[var(--text-tertiary)]">No pickers found</p>
                  ) : filteredPickers.slice(0, 30).map(s => {
                    const ss: any = s
                    const name = ss.name || `${ss.firstName || ''} ${ss.lastName || ''}`.trim()
                    const workload = pickerWorkload[s.id] || 0
                    const shift = ss.shift || '-'
                    return (
                      <PermissionGate resource="warehouse" action="edit">
                        <button key={s.id} type="button"
                          className="w-full text-left px-3 py-2.5 text-sm hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] border-b border-[var(--border-subtle)] last:border-0 flex items-center gap-3"
                          onClick={() => {
                            pickItemMutation.mutate({ itemId: pickTarget.itemId, staffId: s.id })
                            setPickTarget(null)
                            setStaffSearch('')
                            setStaffSearchOpen(null)
                          }}>
                        <div className="w-8 h-8 rounded-full bg-[var(--nexus-primary-100)] flex items-center justify-center text-[var(--nexus-primary-700)] text-xs font-bold shrink-0">
                          {name.split(' ').map((n: string) => n[0]).join('').slice(0, 2).toUpperCase()}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="font-medium text-[var(--text-primary)] truncate">{name}</div>
                          <div className="text-[10px] text-[var(--text-tertiary)]">{ss.employeeCode || ''}</div>
                        </div>
                        <div className="text-right shrink-0">
                          <div className={`text-xs font-semibold ${workload > 0 ? 'text-[var(--nexus-warning-600)]' : 'text-[var(--nexus-success-600)]'}`}>
                            {workload > 0 ? `${workload} active` : 'Free'}
                          </div>
                          <div className="text-[10px] text-[var(--text-tertiary)]">{shift}</div>
                        </div>
                      </button>
                      </PermissionGate>
                    )
                  })}
                </div>
              )}
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button className="enterprise-btn-secondary" onClick={() => { setPickTarget(null); setStaffSearch(''); setStaffSearchOpen(null); }}>Cancel</button>
            </div>
          </div>
        </div>
      )}

      {showCreateModal && (
        <div className="enterprise-modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="enterprise-card p-6 w-full max-w-lg mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Create Picklist</h2>
            <div className="space-y-4">
              <div>
                <label className="enterprise-label">Name</label>
                <input className="enterprise-input w-full" value={createForm.name} onChange={e => setCreateForm(p => ({ ...p, name: e.target.value }))} placeholder="e.g. Wave #1 - Zone A" />
              </div>
              <div>
                <label className="enterprise-label">Wave Type</label>
                <select className="enterprise-input w-full" value={createForm.waveType} onChange={e => {
                  setCreateForm(p => ({ ...p, waveType: e.target.value }))
                  setSelectedOrders([])
                }}>
                  <option value="SINGLE_ORDER">Single Order</option>
                  <option value="BATCH">Batch</option>
                  <option value="WAVE">Wave</option>
                  <option value="ZONE">Zone</option>
                </select>
              </div>
              {createForm.waveType === 'ZONE' ? (
                <div>
                  <label className="enterprise-label">Zone</label>
                  <input className="enterprise-input w-full" value={createForm.name} disabled placeholder="Zone is set via picklist name" />
                  <p className="text-xs text-[var(--text-tertiary)] mt-1">Zone-based picking uses the warehouse zone from the picklist name</p>
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <label className="enterprise-label">Orders ({selectedOrders.length})</label>
                    {isMultiOrder && (
                      <button type="button" className="text-xs text-[var(--color-primary)] hover:underline"
                        onClick={() => setSelectedOrders(p => [...p, { id: '', display: '' }])}>
                        + Add Order
                      </button>
                    )}
                  </div>
                  {(isMultiOrder ? selectedOrders : selectedOrders.length === 0 ? [{ id: '', display: '' }] : selectedOrders).map((sel, idx) => (
                    <div key={idx} ref={el => { if (el) orderRefs.current.set(idx, el); else orderRefs.current.delete(idx); }} className="relative">
                      <div className="flex gap-1">
                        <div className="relative flex-1">
                          <input className="enterprise-input w-full pr-8 text-sm"
                            value={orderSearchIdx === idx ? orderSearch : sel.display || orderSearch}
                            onChange={e => { setOrderSearch(e.target.value); setOrderSearchIdx(idx); }}
                            onFocus={() => { setOrderSearchIdx(idx); setOrderSearch(''); }}
                            placeholder={idx === 0 ? 'Search by order # or customer...' : `Order ${idx + 1}`} />
                          <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
                        </div>
                        {isMultiOrder && selectedOrders.length > 1 && (
                          <button type="button" className="text-[var(--nexus-error-500)] hover:text-[var(--nexus-error-700)] px-1"
                            onClick={() => setSelectedOrders(p => p.filter((_, i) => i !== idx))}>
                            <XCircle className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                      {orderSearchIdx === idx && (
                        <div className="absolute z-50 mt-1 w-full bg-[var(--surface-base)] border border-[var(--border-default)] rounded-lg shadow-lg max-h-48 overflow-y-auto">
                          {filteredOrders.length === 0 ? (
                            <p className="p-3 text-sm text-[var(--text-tertiary)]">No open orders found</p>
                          ) : filteredOrders.slice(0, 50).map(o => (
                            <button key={o.id} type="button" className="w-full text-left px-3 py-2 text-sm hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] border-b border-[var(--border-subtle)] last:border-0 flex items-center gap-2"
                              onClick={() => {
                                const display = `${o.orderNumber || o.id.slice(0, 8)} — ${o.customerName || '?'} [${o.status}]`
                                if (isMultiOrder) {
                                  const next = [...selectedOrders]
                                  next[idx] = { id: o.id, display }
                                  setSelectedOrders(next)
                                } else {
                                  setSelectedOrders([{ id: o.id, display }])
                                }
                                setOrderSearchIdx(null)
                                setOrderSearch('')
                              }}>
                              <span className="w-6 h-6 rounded-full bg-[var(--color-primary)]/10 text-[var(--color-primary)] flex items-center justify-center text-[10px] font-bold">{o.channel?.charAt(0) || '?'}</span>
                              <div className="flex-1 min-w-0">
                                <div className="font-medium truncate">{o.orderNumber || o.id.slice(0, 8)}</div>
                                <div className="text-[10px] text-[var(--text-tertiary)] truncate">{o.customerName || '?'}</div>
                              </div>
                              <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-[var(--color-primary)]/10 text-[var(--color-primary)] whitespace-nowrap">{o.status}</span>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                  {selectedOrders.length === 0 && !isMultiOrder && (
                    <p className="text-xs text-[var(--text-tertiary)]">Select an order above</p>
                  )}
                </div>
              )}
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
              <PermissionGate resource="warehouse" action="create">
                <button className="enterprise-btn-primary" onClick={() => createMutation.mutate()}
                  disabled={!createForm.name || selectedOrders.length === 0 || selectedOrders.some(s => !s.id) || createMutation.isPending}>
                  {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                  Create
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
