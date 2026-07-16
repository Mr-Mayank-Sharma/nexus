import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Filter, Search, Download, Printer, XCircle, Ship, RotateCcw, Loader2, Plus, X,
  ShoppingCart, Clock, CheckCircle, Truck, AlertTriangle,
} from 'lucide-react'
import DataTable, { Column } from '../components/common/DataTable'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseToolbar from '../components/enterprise/EnterpriseToolbar'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import { Order, ApiResponse } from '../types'
import { PermissionGate } from '../components/rbac'
import { useToast } from '../hooks/useToast'
import * as ordersApi from '../api/orders'

const STATUS_ORDER = ['PENDING', 'CONFIRMED', 'ALLOCATED', 'SHIPPED', 'DELIVERED', 'EXCEPTION', 'CANCELLED']

export default function OrdersPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [search, setSearch] = useState('')
  const [activeTab, setActiveTab] = useState('ALL')
  const [channelFilter, setChannelFilter] = useState<string>('ALL')
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [sortBy, setSortBy] = useState<string>('createdAt')
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc')
  const [page, setPage] = useState(1)
  const [showCreate, setShowCreate] = useState(false)
  const [createForm, setCreateForm] = useState({
    customerName: '', customerEmail: '',
    street: '', city: '', state: '', zip: '', country: 'US',
    channel: 'MANUAL', sku: '', productName: '', quantity: 1, unitPrice: 0,
  })
  const pageSize = 15

  const createMutation = useMutation({
    mutationFn: () => ordersApi.createOrder({
      customerName: createForm.customerName,
      customerEmail: createForm.customerEmail,
      shippingAddress: {
        street: createForm.street, city: createForm.city,
        state: createForm.state, zip: createForm.zip, country: createForm.country,
      },
      channel: createForm.channel,
      items: [{
        sku: createForm.sku, productName: createForm.productName,
        quantity: createForm.quantity, unitPrice: createForm.unitPrice,
      }],
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      addToast({ type: 'success', title: 'Order created' })
      setShowCreate(false)
    },
    onError: () => addToast({ type: 'error', title: 'Failed to create order' }),
  })

  const { data: orders = [], isLoading } = useQuery({
    queryKey: ['orders', activeTab, search],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (activeTab !== 'ALL') params.status = activeTab
      if (search) params.search = search
      params.size = '5000'
      const res: ApiResponse<Order[]> = await ordersApi.getOrders(params as any)
      const d = res.data
      if (Array.isArray(d)) return d
      if (d && typeof d === 'object' && 'content' in d) return (d as { content: Order[] }).content
      return []
    },
  })

  const filtered = useMemo(() => {
    let result = [...orders]
    if (channelFilter !== 'ALL') result = result.filter((o) => o.channel === channelFilter)
    result.sort((a, b) => {
      const aVal = String(a[sortBy as keyof Order] ?? '')
      const bVal = String(b[sortBy as keyof Order] ?? '')
      return sortOrder === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal)
    })
    return result
  }, [orders, channelFilter, sortBy, sortOrder])

  const totalPages = Math.ceil(filtered.length / pageSize)
  const paged = filtered.slice((page - 1) * pageSize, page * pageSize)

  function handleSort(key: string) {
    if (sortBy === key) setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')
    else { setSortBy(key); setSortOrder('asc') }
  }

  const cancelMutation = useMutation({
    mutationFn: (id: string) => ordersApi.cancelOrder(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      addToast({ type: 'success', title: 'Order cancelled' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to cancel order' }),
  })

  const statusCounts = useMemo(() => {
    const counts: Record<string, number> = { ALL: orders.length }
    STATUS_ORDER.forEach(s => counts[s] = orders.filter(o => o.status === s).length)
    return counts
  }, [orders])

  const tabs = [
    { id: 'ALL', label: 'All Orders', icon: <ShoppingCart className="w-4 h-4" />, badge: statusCounts.ALL },
    ...STATUS_ORDER.filter(s => s !== 'CANCELLED').map(s => ({
      id: s,
      label: s.charAt(0) + s.slice(1).toLowerCase(),
      icon: s === 'PENDING' ? <Clock className="w-4 h-4" /> : s === 'SHIPPED' || s === 'DELIVERED' ? <Truck className="w-4 h-4" /> : s === 'EXCEPTION' ? <AlertTriangle className="w-4 h-4" /> : <CheckCircle className="w-4 h-4" />,
      badge: statusCounts[s],
    })),
  ]

  const columns: Column<Order>[] = [
    { key: 'orderNumber', header: 'Order ID', sortable: true, render: (o) => <span className="font-medium text-primary-600">{o.orderNumber || o.id}</span> },
    { key: 'channel', header: 'Channel', sortable: true, render: (o) => <span className="text-xs font-medium text-gray-500 uppercase">{o.channel}</span> },
    { key: 'customerName', header: 'Customer', sortable: true },
    { key: 'status', header: 'Status', sortable: true, render: (o) => <EnterpriseStatusBadge status={o.status.toLowerCase()} /> },
    { key: 'items', header: 'Items', render: (o) => <span className="text-gray-500">{o.items?.reduce((s, i) => s + i.quantity, 0) || 0} units</span> },
    { key: 'shippingAddress', header: 'Destination', render: (o) => o.shippingAddress ? <span className="text-gray-500">{o.shippingAddress.city}, {o.shippingAddress.state}</span> : <span className="text-gray-300">—</span> },
    { key: 'carrier', header: 'Carrier', render: (o) => o.carrier ? <span className="text-gray-500">{o.carrier}</span> : <span className="text-gray-300">—</span> },
    { key: 'promisedDeliveryDate', header: 'Promised Delivery', sortable: true, render: (o) => o.promisedDeliveryDate ? <span className="text-gray-500 text-xs">{new Date(o.promisedDeliveryDate).toLocaleDateString()}</span> : <span className="text-gray-300">—</span> },
    { key: 'hasException', header: '', render: (o) => o.hasException ? <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-red-100 text-red-600 text-xs font-bold">!</span> : null },
  ]

  return (
    <div className="space-y-4">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Orders' }]} />

      <div className="grid grid-cols-4 gap-4">
        <EnterpriseKPICard title="Total Orders" value={orders.length} icon={<ShoppingCart className="w-5 h-5" />} color="primary" />
        <EnterpriseKPICard title="Pending" value={statusCounts.PENDING || 0} icon={<Clock className="w-5 h-5" />} color="amber" />
        <EnterpriseKPICard title="Shipped" value={statusCounts.SHIPPED || 0} icon={<Truck className="w-5 h-5" />} color="blue" />
        <EnterpriseKPICard title="Exceptions" value={statusCounts.EXCEPTION || 0} icon={<AlertTriangle className="w-5 h-5" />} color="red" />
      </div>

      <EnterpriseTabs tabs={tabs} activeTab={activeTab} onChange={(id) => { setActiveTab(id); setPage(1) }} />

      <EnterpriseToolbar
        searchValue={search}
        onSearch={(v) => { setSearch(v); setPage(1) }}
        searchPlaceholder="Search orders..."
        autocomplete={{
          fetchSuggestions: async (q) => {
            const res: ApiResponse<Order[]> = await ordersApi.getOrders({ search: q, size: '10' })
            return Array.isArray(res.data) ? res.data : []
          },
          onSelect: (item: Order) => navigate(`/orders/${item.id}`),
          getOptionLabel: (item: Order) => `${item.orderNumber || item.id} — ${item.customerName || ''}`,
          getOptionValue: (item: Order) => item.id,
          minChars: 2,
        }}
        filters={
          <select className="enterprise-input w-40" value={channelFilter} onChange={(e) => { setChannelFilter(e.target.value); setPage(1) }}>
            <option value="ALL">All Channels</option>
            <option value="SHOPIFY">Shopify</option>
            <option value="AMAZON">Amazon</option>
            <option value="WOOCOMMERCE">WooCommerce</option>
            <option value="MANUAL">Manual</option>
            <option value="API">API</option>
          </select>
        }
        actions={[
          { id: 'filters', label: 'Filters', icon: 'Filter', onClick: () => {} },
          { id: 'export', label: 'Export', icon: 'Download', onClick: () => {} },
          { id: 'new-order', label: 'New Order', icon: 'Plus', onClick: () => setShowCreate(true), primary: true, permission: { resource: 'orders', action: 'create' } },
        ]}
      />

      {selectedIds.length > 0 && (
        <div className="flex items-center gap-2 px-4 py-2 bg-primary-50 rounded-lg border border-primary-200">
          <span className="text-sm text-primary-700 font-medium">{selectedIds.length} selected</span>
          <div className="w-px h-4 bg-primary-200" />
          <button className="enterprise-btn enterprise-btn-ghost text-xs text-primary-600" onClick={() => {
            const ready = selectedIds.filter(id => orders.find(o => o.id === id)?.status === 'ALLOCATED')
            ready.forEach(id => ordersApi.shipOrder(id, 'auto', 'TN-BATCH-' + Date.now()))
            addToast({ type: 'success', title: `Shipment booked for ${ready.length} orders` })
            setTimeout(() => queryClient.invalidateQueries({ queryKey: ['orders'] }), 1000)
          }}><Ship className="w-3.5 h-3.5" /> Book Shipment</button>
          <button className="enterprise-btn enterprise-btn-ghost text-xs text-primary-600" onClick={() => {
            const tnList = selectedIds.map(id => {
              const o = orders.find(o2 => o2.id === id)
              return `${o?.orderNumber || id}: ${o?.trackingNumber || 'N/A'}`
            }).join('\n')
            const win = window.open('', '_blank')
            if (win) {
              win.document.write(`<html><head><title>Labels</title><style>body{font-family:monospace;padding:20px}pre{margin:0 0 20px;border:1px dashed #ccc;padding:10px}</style></head><body>${tnList.map(t => `<pre>${t}</pre>`).join('')}</body></html>`)
              win.document.close()
            }
            addToast({ type: 'success', title: `${selectedIds.length} label(s) opened` })
          }}><Printer className="w-3.5 h-3.5" /> Print Labels</button>
          <button className="enterprise-btn enterprise-btn-ghost text-xs text-primary-600" onClick={() => {
            selectedIds.forEach(id => ordersApi.allocateOrder(id))
            addToast({ type: 'success', title: `Reallocating ${selectedIds.length} orders` })
            setTimeout(() => queryClient.invalidateQueries({ queryKey: ['orders'] }), 1000)
          }}><RotateCcw className="w-3.5 h-3.5" /> Reassign</button>
          <PermissionGate resource="orders" action="delete">
            <button className="enterprise-btn enterprise-btn-ghost text-xs text-red-600" onClick={() => { selectedIds.forEach(id => cancelMutation.mutate(id)) }}>
              {cancelMutation.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <XCircle className="w-3.5 h-3.5" />}
              Cancel
            </button>
          </PermissionGate>
        </div>
      )}

      <div className="enterprise-card overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center p-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
          </div>
        ) : (
          <DataTable
            columns={columns}
            data={paged}
            keyExtractor={(o) => o.id}
            sortBy={sortBy}
            sortOrder={sortOrder}
            onSort={handleSort}
            selectedIds={selectedIds}
            onSelectionChange={setSelectedIds}
            onRowClick={(o) => navigate(`/orders/${o.id}`)}
            pagination={{ page, totalPages, total: filtered.length, onPageChange: setPage }}
          />
        )}
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setShowCreate(false)}>
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Create Order</h2>
              <button onClick={() => setShowCreate(false)} className="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"><X className="w-5 h-5 text-gray-400" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="col-span-2">
                  <label className="text-xs font-medium text-gray-500 mb-1 block">Customer Name</label>
                  <input className="enterprise-input w-full" value={createForm.customerName} onChange={e => setCreateForm(f => ({ ...f, customerName: e.target.value }))} />
                </div>
                <div className="col-span-2">
                  <label className="text-xs font-medium text-gray-500 mb-1 block">Customer Email</label>
                  <input className="enterprise-input w-full" type="email" value={createForm.customerEmail} onChange={e => setCreateForm(f => ({ ...f, customerEmail: e.target.value }))} />
                </div>
                <div className="col-span-2">
                  <label className="text-xs font-medium text-gray-500 mb-1 block">Street</label>
                  <input className="enterprise-input w-full" value={createForm.street} onChange={e => setCreateForm(f => ({ ...f, street: e.target.value }))} />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-500 mb-1 block">City</label>
                  <input className="enterprise-input w-full" value={createForm.city} onChange={e => setCreateForm(f => ({ ...f, city: e.target.value }))} />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-500 mb-1 block">State</label>
                  <input className="enterprise-input w-full" value={createForm.state} onChange={e => setCreateForm(f => ({ ...f, state: e.target.value }))} />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-500 mb-1 block">ZIP</label>
                  <input className="enterprise-input w-full" value={createForm.zip} onChange={e => setCreateForm(f => ({ ...f, zip: e.target.value }))} />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-500 mb-1 block">Country</label>
                  <input className="enterprise-input w-full" value={createForm.country} onChange={e => setCreateForm(f => ({ ...f, country: e.target.value }))} />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-500 mb-1 block">Channel</label>
                  <select className="enterprise-input w-full" value={createForm.channel} onChange={e => setCreateForm(f => ({ ...f, channel: e.target.value }))}>
                    <option value="MANUAL">Manual</option>
                    <option value="SHOPIFY">Shopify</option>
                    <option value="AMAZON">Amazon</option>
                    <option value="WOOCOMMERCE">WooCommerce</option>
                    <option value="API">API</option>
                  </select>
                </div>
              </div>
              <hr className="border-gray-200 dark:border-gray-700" />
              <p className="text-sm font-medium text-gray-700 dark:text-gray-300">Item</p>
              <div className="grid grid-cols-2 gap-4">
                <div className="col-span-2">
                  <label className="text-xs font-medium text-gray-500 mb-1 block">Product Name</label>
                  <input className="enterprise-input w-full" value={createForm.productName} onChange={e => setCreateForm(f => ({ ...f, productName: e.target.value }))} />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-500 mb-1 block">SKU</label>
                  <input className="enterprise-input w-full" value={createForm.sku} onChange={e => setCreateForm(f => ({ ...f, sku: e.target.value }))} />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-500 mb-1 block">Qty</label>
                  <input className="enterprise-input w-full" type="number" min={1} value={createForm.quantity} onChange={e => setCreateForm(f => ({ ...f, quantity: Number(e.target.value) }))} />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-500 mb-1 block">Unit Price</label>
                  <input className="enterprise-input w-full" type="number" min={0} step={0.01} value={createForm.unitPrice} onChange={e => setCreateForm(f => ({ ...f, unitPrice: Number(e.target.value) }))} />
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
              <button className="enterprise-btn enterprise-btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
              <PermissionGate resource="orders" action="create">
                <button className="enterprise-btn enterprise-btn-primary" onClick={() => createMutation.mutate()} disabled={createMutation.isPending}>
                  {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                  {createMutation.isPending ? 'Creating...' : 'Create Order'}
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
