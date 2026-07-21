import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Truck, Plus, Search, CheckCircle, XCircle, Loader2,
  Package, Clock, AlertTriangle, MapPin, ExternalLink,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseToolbar from '../components/enterprise/EnterpriseToolbar'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import PermissionGate from '../components/rbac/PermissionGate'
import { useToast } from '../hooks/useToast'
import * as shippingApi from '../api/shipping'
import type { Shipment } from '../types'

const STATUS_ORDER = ['PENDING', 'LABELED', 'SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'EXCEPTION', 'VOIDED']

export default function ShippingPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState('all')
  const [searchTerm, setSearchTerm] = useState('')
  const [expandedShipment, setExpandedShipment] = useState<string | null>(null)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [createForm, setCreateForm] = useState({ orderId: '', carrierId: '', trackingNumber: '' })

  const { data: kpis = { pending: 0, shipped: 0, inTransit: 0, delivered: 0, exceptions: 0, total: 0 } } = useQuery({
    queryKey: ['shipping-kpis'],
    queryFn: async () => {
      const res = await shippingApi.getShippingKPIs()
      return res.data as Record<string, number>
    },
    refetchInterval: 15000,
  })

  const { data: shipments = [], isLoading } = useQuery({
    queryKey: ['shipments'],
    queryFn: async () => {
      const res = await shippingApi.getShipments()
      const d = res.data; return Array.isArray(d) ? d : (d?.content ?? []) as Shipment[]
    },
  })

  const filtered = shipments.filter(s => {
    if (activeTab !== 'all' && s.status !== activeTab.toUpperCase().replace(/-/g, '_')) return false
    if (searchTerm) {
      const term = searchTerm.toLowerCase()
      return s.trackingNumber?.toLowerCase().includes(term) ||
        s.carrier?.toLowerCase().includes(term) ||
        s.orderNumber?.toLowerCase().includes(term) ||
        s.id.toLowerCase().includes(term)
    }
    return true
  }).sort((a, b) => STATUS_ORDER.indexOf(a.status as any) - STATUS_ORDER.indexOf(b.status as any))

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['shipments'] })
    queryClient.invalidateQueries({ queryKey: ['shipping-kpis'] })
  }

  const createMutation = useMutation({
    mutationFn: () => shippingApi.createShipment(createForm),
    onSuccess: () => { invalidate(); setShowCreateModal(false); addToast({ type: 'success', title: 'Shipment created' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to create shipment' }),
  })

  const markShippedMutation = useMutation({
    mutationFn: (id: string) => shippingApi.markShipped(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Shipment marked shipped' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to update shipment' }),
  })

  const markDeliveredMutation = useMutation({
    mutationFn: (id: string) => shippingApi.markDelivered(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Shipment delivered' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to update shipment' }),
  })

  const voidMutation = useMutation({
    mutationFn: (id: string) => shippingApi.voidShipment(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Shipment voided' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to void shipment' }),
  })

  const tabs = [
    { id: 'all', label: 'All', count: kpis.total },
    { id: 'PENDING', label: 'Pending', count: kpis.pending },
    { id: 'SHIPPED', label: 'Shipped', count: kpis.shipped },
    { id: 'IN_TRANSIT', label: 'In Transit', count: kpis.inTransit },
    { id: 'DELIVERED', label: 'Delivered', count: kpis.delivered },
    { id: 'EXCEPTION', label: 'Exceptions', count: kpis.exceptions },
  ]

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Logistics' }, { label: 'Shipping' }]} />

      <EnterpriseToolbar
        title="Shipping"
        searchPlaceholder="Search by tracking, carrier, order..."
        searchValue={searchTerm}
        onSearch={setSearchTerm}
        autocomplete={{
          fetchSuggestions: async (q) => {
            if (!q) return shipments.slice(0, 10)
            const term = q.toLowerCase()
            return shipments.filter(s => s.trackingNumber?.toLowerCase().includes(term) || s.carrier?.toLowerCase().includes(term) || s.orderId?.toLowerCase().includes(term)).slice(0, 10)
          },
          onSelect: (item: any) => setSearchTerm(item.trackingNumber || item.id),
          getOptionLabel: (item: any) => `${item.trackingNumber || item.id} — ${item.carrier || ''}`,
          getOptionValue: (item: any) => item.id,
          minChars: 1,
        }}
        actions={[
          { label: 'New Shipment', icon: <Plus className="w-4 h-4" />, onClick: () => setShowCreateModal(true), variant: 'primary', permission: { resource: 'logistics', action: 'create' } },
        ]}
      />

      <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
        <EnterpriseKPICard title="Pending" value={kpis.pending} icon={<Clock />} color="warning" />
        <EnterpriseKPICard title="Shipped" value={kpis.shipped} icon={<Truck />} color="primary" />
        <EnterpriseKPICard title="In Transit" value={kpis.inTransit} icon={<Package />} color="info" />
        <EnterpriseKPICard title="Delivered" value={kpis.delivered} icon={<CheckCircle />} color="success" />
        <EnterpriseKPICard title="Exceptions" value={kpis.exceptions} icon={<AlertTriangle />} color="error" />
      </div>

      <EnterpriseTabs tabs={tabs.map(t => ({ id: t.id, label: t.label, badge: t.count }))} activeTab={activeTab} onChange={setActiveTab} />

      {isLoading ? (
        <div className="space-y-3">
          {[1,2,3].map(i => <div key={i} className="enterprise-skeleton h-16 w-full" />)}
        </div>
      ) : filtered.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <Truck className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No shipments found</p>
          <p className="text-sm text-[var(--text-tertiary)] mt-1">Create a new shipment to get started</p>
        </div>
      ) : (
        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Carrier</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Tracking</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Ship Date</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {filtered.map(s => (
                  <>
                    <tr key={s.id} className="enterprise-table-row cursor-pointer" onClick={() => setExpandedShipment(expandedShipment === s.id ? null : s.id)}>
                      <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{s.orderNumber || s.orderId?.slice(0, 8)}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{s.carrier || '-'}</td>
                      <td className="px-4 py-3 text-sm font-mono text-[var(--color-primary)]">{s.trackingNumber || '-'}</td>
                      <td className="px-4 py-3">
                        <EnterpriseStatusBadge
                          status={s.status === 'PENDING' ? 'pending' : s.status === 'LABELED' ? 'primary' : s.status === 'SHIPPED' ? 'info' : s.status === 'IN_TRANSIT' ? 'warning' : s.status === 'OUT_FOR_DELIVERY' ? 'warning' : s.status === 'DELIVERED' ? 'success' : s.status === 'EXCEPTION' ? 'error' : 'neutral'}
                          label={s.status}
                        />
                      </td>
                      <td className="px-4 py-3 text-right text-sm text-[var(--text-secondary)]">
                        {s.shipDate ? new Date(s.shipDate).toLocaleDateString() : '-'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1">
                          {s.status === 'PENDING' && (
                            <PermissionGate resource="logistics" action="edit">
                              <button className="enterprise-btn-ghost p-1.5 text-[var(--nexus-primary-500)]" title="Mark Shipped"
                                onClick={e => { e.stopPropagation(); markShippedMutation.mutate(s.id); }}>
                                <Truck className="w-4 h-4" />
                              </button>
                            </PermissionGate>
                          )}
                          {(s.status === 'SHIPPED' || s.status === 'IN_TRANSIT') && (
                            <PermissionGate resource="logistics" action="edit">
                              <button className="enterprise-btn-ghost p-1.5 text-[var(--nexus-success-500)]" title="Mark Delivered"
                                onClick={e => { e.stopPropagation(); markDeliveredMutation.mutate(s.id); }}>
                                <CheckCircle className="w-4 h-4" />
                              </button>
                            </PermissionGate>
                          )}
                          {s.trackingNumber && (
                            <button className="enterprise-btn-ghost p-1.5 text-[var(--text-tertiary)]" title="Track"
                              onClick={e => { e.stopPropagation(); window.open(`https://track.example.com/${s.trackingNumber}`, '_blank'); }}>
                              <ExternalLink className="w-4 h-4" />
                            </button>
                          )}
                          {s.status !== 'DELIVERED' && s.status !== 'VOIDED' && (
                            <PermissionGate resource="logistics" action="delete">
                              <button className="enterprise-btn-ghost p-1.5 text-[var(--nexus-error-500)]" title="Void"
                                onClick={e => { e.stopPropagation(); if (confirm('Void this shipment?')) voidMutation.mutate(s.id); }}>
                                <XCircle className="w-4 h-4" />
                              </button>
                            </PermissionGate>
                          )}
                        </div>
                      </td>
                    </tr>
                    {expandedShipment === s.id && (
                      <tr key={`${s.id}-detail`}>
                        <td colSpan={6} className="px-4 py-3 bg-[var(--bg-tertiary)]/30">
                          <div className="pl-4 border-l-2 border-[var(--color-primary)]/30 space-y-3">
                            <div className="grid grid-cols-3 gap-4 text-sm">
                              <div>
                                <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase">Service</p>
                                <p className="text-[var(--text-primary)]">{s.service || '-'}</p>
                              </div>
                              <div>
                                <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase">Est. Delivery</p>
                                <p className="text-[var(--text-primary)]">{s.estimatedDelivery ? new Date(s.estimatedDelivery).toLocaleDateString() : '-'}</p>
                              </div>
                              <div>
                                <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase">Shipping Cost</p>
                                <p className="text-[var(--text-primary)]">${s.shippingCost?.toFixed(2) || '-'}</p>
                              </div>
                            </div>

                            {s.events && s.events.length > 0 && (
                              <div>
                                <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase mb-2">Tracking Events</p>
                                <div className="space-y-1">
                                  {s.events.map((evt, i) => (
                                    <div key={i} className="flex items-center gap-2 text-xs">
                                      <MapPin className="w-3 h-3 text-[var(--text-tertiary)]" />
                                      <span className="text-[var(--text-secondary)]">{new Date(evt.timestamp).toLocaleString()}</span>
                                      <span className="text-[var(--text-primary)]">{evt.status}</span>
                                      <span className="text-[var(--text-tertiary)]">- {evt.location}</span>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}

                            {s.labels && s.labels.length > 0 && (
                              <div>
                                <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase mb-2">Labels</p>
                                <div className="flex gap-2">
                                  {s.labels.map((lbl, i) => (
                                    <a key={i} href={lbl.url} target="_blank" rel="noopener noreferrer"
                                      className="enterprise-btn-secondary text-xs">
                                      <ExternalLink className="w-3 h-3" /> {lbl.format} Label
                                    </a>
                                  ))}
                                </div>
                              </div>
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
        <div className="enterprise-modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="enterprise-card p-6 w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Create Shipment</h2>
            <div className="space-y-4">
              <div>
                <label className="enterprise-label">Order ID</label>
                <input className="enterprise-input w-full" value={createForm.orderId} onChange={e => setCreateForm(p => ({ ...p, orderId: e.target.value }))} placeholder="Order UUID" />
              </div>
              <div>
                <label className="enterprise-label">Carrier ID</label>
                <input className="enterprise-input w-full" value={createForm.carrierId} onChange={e => setCreateForm(p => ({ ...p, carrierId: e.target.value }))} placeholder="Carrier UUID" />
              </div>
              <div>
                <label className="enterprise-label">Tracking Number</label>
                <input className="enterprise-input w-full" value={createForm.trackingNumber} onChange={e => setCreateForm(p => ({ ...p, trackingNumber: e.target.value }))} placeholder="Auto-generated if empty" />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button className="enterprise-btn-secondary" onClick={() => setShowCreateModal(false)}>Cancel</button>
              <PermissionGate resource="logistics" action="create">
                <button className="enterprise-btn-primary" onClick={() => createMutation.mutate()} disabled={!createForm.orderId || createMutation.isPending}>
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
