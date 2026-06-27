import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Box, Plus, Play, CheckCircle, XCircle, Loader2, Search,
  Package, Printer, Truck, Ruler, Weight,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseToolbar from '../components/enterprise/EnterpriseToolbar'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import { useToast } from '../hooks/useToast'
import * as packingApi from '../api/packing'
import type { NxPackage } from '../types'

const STATUS_ORDER = ['PENDING_PACK', 'PACKING', 'PACKED', 'LABELED', 'SHIPPED', 'VOIDED']

export default function PackingPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState('all')
  const [searchTerm, setSearchTerm] = useState('')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showLabelModal, setShowLabelModal] = useState<{ id: string; orderId: string } | null>(null)
  const [createForm, setCreateForm] = useState({ orderId: '', packageType: 'BOX' as const })
  const [labelForm, setLabelForm] = useState({ carrierId: '', carrierName: '', serviceLevel: '', trackingNumber: '', labelUrl: '' })

  const { data: kpis = { pendingPack: 0, packing: 0, packed: 0, shipped: 0 } } = useQuery({
    queryKey: ['packing-kpis'],
    queryFn: async () => {
      const res = await packingApi.getPackingKPIs()
      return res.data as Record<string, number>
    },
    refetchInterval: 15000,
  })

  const { data: packages = [], isLoading } = useQuery({
    queryKey: ['packages', activeTab],
    queryFn: async () => {
      const status = activeTab === 'all' ? undefined : activeTab.toUpperCase().replace(/-/g, '_')
      const res = await packingApi.getPackages(status)
      return res.data as NxPackage[]
    },
  })

  const filtered = packages.filter(p =>
    !searchTerm || p.orderId?.toLowerCase().includes(searchTerm.toLowerCase()) || p.boxName?.toLowerCase().includes(searchTerm.toLowerCase())
  ).sort((a, b) => STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status))

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['packages'] })
    queryClient.invalidateQueries({ queryKey: ['packing-kpis'] })
  }

  const createMutation = useMutation({
    mutationFn: () => packingApi.createPackage(createForm),
    onSuccess: () => { invalidate(); setShowCreateModal(false); addToast({ type: 'success', title: 'Package created' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to create package' }),
  })

  const startMutation = useMutation({
    mutationFn: (id: string) => packingApi.startPacking(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Packing started' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to start packing' }),
  })

  const completeMutation = useMutation({
    mutationFn: (id: string) => packingApi.completePacking(id, 'current-user'),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Packing completed' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to complete packing' }),
  })

  const labelMutation = useMutation({
    mutationFn: () => {
      if (!showLabelModal) throw new Error('No modal state')
      return packingApi.generateLabel(showLabelModal.id, labelForm.carrierId, labelForm.carrierName, labelForm.serviceLevel, labelForm.trackingNumber, labelForm.labelUrl)
    },
    onSuccess: () => { invalidate(); setShowLabelModal(null); addToast({ type: 'success', title: 'Label generated' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to generate label' }),
  })

  const shipMutation = useMutation({
    mutationFn: (id: string) => packingApi.shipPackage(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Package shipped' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to ship package' }),
  })

  const tabs = [
    { id: 'all', label: 'All Packages', count: packages.length },
    { id: 'PENDING_PACK', label: 'To Pack', count: packages.filter(p => p.status === 'PENDING_PACK').length },
    { id: 'PACKING', label: 'In Progress', count: packages.filter(p => p.status === 'PACKING').length },
    { id: 'PACKED', label: 'Packed', count: packages.filter(p => p.status === 'PACKED').length },
    { id: 'LABELED', label: 'Labeled', count: packages.filter(p => p.status === 'LABELED').length },
    { id: 'SHIPPED', label: 'Shipped', count: packages.filter(p => p.status === 'SHIPPED').length },
  ]

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Warehouse' }, { label: 'Packing' }]} />

      <EnterpriseToolbar
        title="Packing"
        searchPlaceholder="Search by order or box..."
        searchValue={searchTerm}
        onSearchChange={setSearchTerm}
        actions={
          <button className="enterprise-btn-primary" onClick={() => setShowCreateModal(true)}>
            <Plus className="w-4 h-4" /> New Package
          </button>
        }
      />

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <EnterpriseKPICard title="To Pack" value={kpis.pendingPack} icon={<Package />} color="warning" />
        <EnterpriseKPICard title="In Progress" value={kpis.packing} icon={<Play />} color="primary" />
        <EnterpriseKPICard title="Packed" value={kpis.packed} icon={<Box />} color="info" />
        <EnterpriseKPICard title="Shipped" value={kpis.shipped} icon={<Truck />} color="success" />
      </div>

      <EnterpriseTabs tabs={tabs.map(t => ({ id: t.id, label: t.label, badge: t.count }))} activeTab={activeTab} onChange={setActiveTab} />

      {isLoading ? (
        <div className="space-y-3">
          {[1,2,3].map(i => <div key={i} className="enterprise-skeleton h-16 w-full" />)}
        </div>
      ) : filtered.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <Box className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No packages found</p>
          <p className="text-sm text-[var(--text-tertiary)] mt-1">Create a new package to start packing</p>
        </div>
      ) : (
        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Box</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Items</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Carrier</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Tracking</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {filtered.map(pkg => (
                  <tr key={pkg.id} className="enterprise-table-row">
                    <td className="px-4 py-3 text-sm font-mono text-[var(--text-primary)]">{pkg.orderId?.slice(0, 8)}...</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{pkg.boxName || pkg.packageType}</td>
                    <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{pkg.itemCount}</td>
                    <td className="px-4 py-3">
                      <EnterpriseStatusBadge
                        status={pkg.status === 'PENDING_PACK' ? 'pending' : pkg.status === 'PACKING' ? 'warning' : pkg.status === 'PACKED' ? 'info' : pkg.status === 'LABELED' ? 'primary' : pkg.status === 'SHIPPED' ? 'success' : 'error'}
                        label={pkg.status}
                      />
                    </td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{pkg.carrierName || '-'}</td>
                    <td className="px-4 py-3 text-sm font-mono text-[var(--text-secondary)]">{pkg.trackingNumber || '-'}</td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {(pkg.status === 'PENDING_PACK' || pkg.status === 'PACKING') && (
                          <>
                            {pkg.status === 'PENDING_PACK' && (
                              <button className="enterprise-btn-ghost p-1.5 text-blue-500" title="Start Packing" onClick={() => startMutation.mutate(pkg.id)}>
                                <Play className="w-4 h-4" />
                              </button>
                            )}
                            <button className="enterprise-btn-ghost p-1.5 text-green-500" title="Complete Packing" onClick={() => completeMutation.mutate(pkg.id)}>
                              <CheckCircle className="w-4 h-4" />
                            </button>
                            <button className="enterprise-btn-ghost p-1.5 text-red-500" title="Void" onClick={() => { if (confirm('Void this package?')) packingApi.voidPackage(pkg.id).then(invalidate); }}>
                              <XCircle className="w-4 h-4" />
                            </button>
                          </>
                        )}
                        {pkg.status === 'PACKED' && (
                          <button className="enterprise-btn-primary text-xs" onClick={() => setShowLabelModal({ id: pkg.id, orderId: pkg.orderId })}>
                            <Printer className="w-3 h-3" /> Generate Label
                          </button>
                        )}
                        {pkg.status === 'LABELED' && (
                          <button className="enterprise-btn-ghost p-1.5 text-green-500" title="Ship" onClick={() => shipMutation.mutate(pkg.id)}>
                            <Truck className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={() => setShowCreateModal(false)}>
          <div className="enterprise-card p-6 w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Create Package</h2>
            <div className="space-y-4">
              <div>
                <label className="enterprise-label">Order ID</label>
                <input className="enterprise-input w-full" value={createForm.orderId} onChange={e => setCreateForm(p => ({ ...p, orderId: e.target.value }))} placeholder="Order UUID" />
              </div>
              <div>
                <label className="enterprise-label">Package Type</label>
                <select className="enterprise-input w-full" value={createForm.packageType} onChange={e => setCreateForm(p => ({ ...p, packageType: e.target.value }))}>
                  <option value="BOX">Box</option>
                  <option value="PALLET">Pallet</option>
                  <option value="CRATE">Crate</option>
                  <option value="ENVELOPE">Envelope</option>
                  <option value="TUBE">Tube</option>
                  <option value="BAG">Bag</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button className="enterprise-btn-secondary" onClick={() => setShowCreateModal(false)}>Cancel</button>
              <button className="enterprise-btn-primary" onClick={() => createMutation.mutate()} disabled={!createForm.orderId || createMutation.isPending}>
                {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      {showLabelModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={() => setShowLabelModal(null)}>
          <div className="enterprise-card p-6 w-full max-w-lg mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Generate Shipping Label</h2>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="enterprise-label">Carrier ID</label>
                  <input className="enterprise-input w-full" value={labelForm.carrierId} onChange={e => setLabelForm(p => ({ ...p, carrierId: e.target.value }))} placeholder="carrier-uuid" />
                </div>
                <div>
                  <label className="enterprise-label">Carrier Name</label>
                  <input className="enterprise-input w-full" value={labelForm.carrierName} onChange={e => setLabelForm(p => ({ ...p, carrierName: e.target.value }))} placeholder="FedEx, UPS..." />
                </div>
              </div>
              <div>
                <label className="enterprise-label">Service Level</label>
                <input className="enterprise-input w-full" value={labelForm.serviceLevel} onChange={e => setLabelForm(p => ({ ...p, serviceLevel: e.target.value }))} placeholder="GROUND, 2DAY, OVERNIGHT..." />
              </div>
              <div>
                <label className="enterprise-label">Tracking Number</label>
                <input className="enterprise-input w-full" value={labelForm.trackingNumber} onChange={e => setLabelForm(p => ({ ...p, trackingNumber: e.target.value }))} placeholder="Auto-generated if empty" />
              </div>
              <div>
                <label className="enterprise-label">Label URL (optional)</label>
                <input className="enterprise-input w-full" value={labelForm.labelUrl} onChange={e => setLabelForm(p => ({ ...p, labelUrl: e.target.value }))} placeholder="https://..." />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button className="enterprise-btn-secondary" onClick={() => setShowLabelModal(null)}>Cancel</button>
              <button className="enterprise-btn-primary" onClick={() => labelMutation.mutate()} disabled={!labelForm.carrierId || !labelForm.carrierName || labelMutation.isPending}>
                {labelMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                Generate Label
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
