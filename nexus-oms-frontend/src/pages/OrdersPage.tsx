import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Filter, Search, Download, Printer, XCircle, Ship, RotateCcw, Loader2, Plus, X,
} from 'lucide-react'
import DataTable, { Column } from '../components/common/DataTable'
import StatusBadge from '../components/common/StatusBadge'
import { Order, ApiResponse } from '../types'
import { useToast } from '../hooks/useToast'
import * as ordersApi from '../api/orders'

export default function OrdersPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
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
    queryKey: ['orders', statusFilter, search],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (statusFilter !== 'ALL') params.status = statusFilter
      if (search) params.search = search
      const res: ApiResponse<Order[]> = await ordersApi.getOrders(params)
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

  const columns: Column<Order>[] = [
    { key: 'orderNumber', header: 'Order ID', sortable: true, render: (o) => <span className="font-medium text-primary-600">{o.orderNumber || o.id}</span> },
    { key: 'channel', header: 'Channel', sortable: true, render: (o) => <span className="text-xs font-medium text-gray-500 uppercase">{o.channel}</span> },
    { key: 'customerName', header: 'Customer', sortable: true },
    { key: 'status', header: 'Status', sortable: true, render: (o) => <StatusBadge status={o.status} /> },
    { key: 'items', header: 'Items', render: (o) => <span className="text-gray-500">{o.items?.reduce((s, i) => s + i.quantity, 0) || 0} units</span> },
    { key: 'shippingAddress', header: 'Destination', render: (o) => o.shippingAddress ? <span className="text-gray-500">{o.shippingAddress.city}, {o.shippingAddress.state}</span> : <span className="text-gray-300">—</span> },
    { key: 'carrier', header: 'Carrier', render: (o) => o.carrier ? <span className="text-gray-500">{o.carrier}</span> : <span className="text-gray-300">—</span> },
    { key: 'promisedDeliveryDate', header: 'Promised Delivery', sortable: true, render: (o) => o.promisedDeliveryDate ? <span className="text-gray-500 text-xs">{new Date(o.promisedDeliveryDate).toLocaleDateString()}</span> : <span className="text-gray-300">—</span> },
    { key: 'hasException', header: '', render: (o) => o.hasException ? <span className="badge bg-red-50 text-red-600">!</span> : null },
  ]

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Orders</h1>
          <p className="text-sm text-gray-500 mt-1">{orders.length} total orders</p>
        </div>
        <div className="flex items-center gap-2">
          <button className="btn-secondary text-sm"><Filter className="w-4 h-4" /> Filters</button>
          <button className="btn-secondary text-sm"><Download className="w-4 h-4" /> Export</button>
          <button className="btn-primary text-sm" onClick={() => setShowCreate(true)}><Plus className="w-4 h-4" /> New Order</button>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input type="text" placeholder="Search orders..." className="input pl-10" value={search} onChange={(e) => { setSearch(e.target.value); setPage(1) }} />
        </div>
        <select className="input w-40" value={channelFilter} onChange={(e) => { setChannelFilter(e.target.value); setPage(1) }}>
          <option value="ALL">All Channels</option>
          <option value="SHOPIFY">Shopify</option>
          <option value="AMAZON">Amazon</option>
          <option value="WOOCOMMERCE">WooCommerce</option>
          <option value="MANUAL">Manual</option>
          <option value="API">API</option>
        </select>
        <select className="input w-40" value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(1) }}>
          <option value="ALL">All Statuses</option>
          <option value="PENDING">Pending</option>
          <option value="CONFIRMED">Confirmed</option>
          <option value="ALLOCATED">Allocated</option>
          <option value="SHIPPED">Shipped</option>
          <option value="DELIVERED">Delivered</option>
          <option value="EXCEPTION">Exception</option>
        </select>
      </div>

      {selectedIds.length > 0 && (
        <div className="flex items-center gap-2 px-4 py-2 bg-primary-50 rounded-lg border border-primary-200">
          <span className="text-sm text-primary-700 font-medium">{selectedIds.length} selected</span>
          <div className="w-px h-4 bg-primary-200" />
          <button className="btn-ghost text-xs text-primary-600"><Ship className="w-3.5 h-3.5" /> Book Shipment</button>
          <button className="btn-ghost text-xs text-primary-600"><Printer className="w-3.5 h-3.5" /> Print Labels</button>
          <button className="btn-ghost text-xs text-primary-600"><RotateCcw className="w-3.5 h-3.5" /> Reassign</button>
          <button className="btn-ghost text-xs text-red-600" onClick={() => { selectedIds.forEach(id => cancelMutation.mutate(id)) }}>
            {cancelMutation.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <XCircle className="w-3.5 h-3.5" />}
            Cancel
          </button>
        </div>
      )}

      <div className="card overflow-hidden">
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
          <div className="bg-white rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between px-6 py-4 border-b">
              <h2 className="text-lg font-semibold">Create Order</h2>
              <button onClick={() => setShowCreate(false)}><X className="w-5 h-5 text-gray-400 hover:text-gray-600" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="col-span-2">
                  <label className="label">Customer Name</label>
                  <input className="input" value={createForm.customerName} onChange={e => setCreateForm(f => ({ ...f, customerName: e.target.value }))} />
                </div>
                <div className="col-span-2">
                  <label className="label">Customer Email</label>
                  <input className="input" type="email" value={createForm.customerEmail} onChange={e => setCreateForm(f => ({ ...f, customerEmail: e.target.value }))} />
                </div>
                <div className="col-span-2">
                  <label className="label">Street</label>
                  <input className="input" value={createForm.street} onChange={e => setCreateForm(f => ({ ...f, street: e.target.value }))} />
                </div>
                <div>
                  <label className="label">City</label>
                  <input className="input" value={createForm.city} onChange={e => setCreateForm(f => ({ ...f, city: e.target.value }))} />
                </div>
                <div>
                  <label className="label">State</label>
                  <input className="input" value={createForm.state} onChange={e => setCreateForm(f => ({ ...f, state: e.target.value }))} />
                </div>
                <div>
                  <label className="label">ZIP</label>
                  <input className="input" value={createForm.zip} onChange={e => setCreateForm(f => ({ ...f, zip: e.target.value }))} />
                </div>
                <div>
                  <label className="label">Country</label>
                  <input className="input" value={createForm.country} onChange={e => setCreateForm(f => ({ ...f, country: e.target.value }))} />
                </div>
                <div>
                  <label className="label">Channel</label>
                  <select className="input" value={createForm.channel} onChange={e => setCreateForm(f => ({ ...f, channel: e.target.value }))}>
                    <option value="MANUAL">Manual</option>
                    <option value="SHOPIFY">Shopify</option>
                    <option value="AMAZON">Amazon</option>
                    <option value="WOOCOMMERCE">WooCommerce</option>
                    <option value="API">API</option>
                  </select>
                </div>
              </div>
              <hr className="border-gray-200" />
              <p className="text-sm font-medium text-gray-700">Item</p>
              <div className="grid grid-cols-2 gap-4">
                <div className="col-span-2">
                  <label className="label">Product Name</label>
                  <input className="input" value={createForm.productName} onChange={e => setCreateForm(f => ({ ...f, productName: e.target.value }))} />
                </div>
                <div>
                  <label className="label">SKU</label>
                  <input className="input" value={createForm.sku} onChange={e => setCreateForm(f => ({ ...f, sku: e.target.value }))} />
                </div>
                <div>
                  <label className="label">Qty</label>
                  <input className="input" type="number" min={1} value={createForm.quantity} onChange={e => setCreateForm(f => ({ ...f, quantity: Number(e.target.value) }))} />
                </div>
                <div>
                  <label className="label">Unit Price</label>
                  <input className="input" type="number" min={0} step={0.01} value={createForm.unitPrice} onChange={e => setCreateForm(f => ({ ...f, unitPrice: Number(e.target.value) }))} />
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-3 px-6 py-4 border-t bg-gray-50">
              <button className="btn-secondary text-sm" onClick={() => setShowCreate(false)}>Cancel</button>
              <button className="btn-primary text-sm" onClick={() => createMutation.mutate()} disabled={createMutation.isPending}>
                {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                {createMutation.isPending ? 'Creating...' : 'Create Order'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
