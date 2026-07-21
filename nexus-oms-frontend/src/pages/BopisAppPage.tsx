import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Package, CheckCircle, Truck, User, Clock, AlertTriangle, QrCode,
  ArrowRight, ArrowDown, Camera, FileText, XCircle,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import { useAuth } from '../context/AuthContext'
import { pickupApi, PickupOrder, PickupOrderItem } from '../api/pickup'

const STATUS_FLOW = [
  { key: 'PENDING', label: 'Pending', color: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]' },
  { key: 'PICKING', label: 'Picking', color: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]' },
  { key: 'PICKED', label: 'Picked', color: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]' },
  { key: 'PACKED', label: 'Packed', color: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)]' },
  { key: 'READY_FOR_HANDOFF', label: 'Ready', color: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]' },
  { key: 'POD_COLLECTED', label: 'Collected', color: 'bg-emerald-100 text-emerald-700' },
]

export default function BopisAppPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const { user } = useAuth()

  const [selectedOrder, setSelectedOrder] = useState<PickupOrder | null>(null)
  const [pickupCodeInput, setPickupCodeInput] = useState('')
  const [searchMode, setSearchMode] = useState(false)
  const [showPodModal, setShowPodModal] = useState(false)
  const [podForm, setPodForm] = useState({
    collectedByName: '',
    collectedByIdDoc: '',
    collectorSignature: '',
    collectionNotes: '',
  })

  const nodeId = user?.nodeId || ''

  const { data: pendingOrders = [] } = useQuery({
    queryKey: ['pickup-pending', nodeId],
    queryFn: async () => {
      const res = await pickupApi.getPendingPickups(nodeId)
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: !!nodeId,
    refetchInterval: 30000,
  })

  const { data: readyOrders = [] } = useQuery({
    queryKey: ['pickup-ready'],
    queryFn: async () => {
      const res = await pickupApi.getReadyForHandoff()
      return Array.isArray(res.data) ? res.data : []
    },
    refetchInterval: 30000,
  })

  const { data: statusCounts } = useQuery({
    queryKey: ['pickup-status', nodeId],
    queryFn: async () => {
      const res = await pickupApi.getStatusCounts(nodeId)
      return res.data
    },
    enabled: !!nodeId,
  })

  const { data: pickupItems = [] } = useQuery({
    queryKey: ['pickup-items', selectedOrder?.id],
    queryFn: async () => {
      const res = await pickupApi.getPickupItems(selectedOrder!.id)
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: !!selectedOrder,
  })

  const lookupMutation = useMutation({
    mutationFn: (code: string) => pickupApi.getByPickupCode(code),
    onSuccess: (res) => {
      setSelectedOrder(res.data)
      setSearchMode(false)
      setPickupCodeInput('')
    },
    onError: () => addToast({ type: 'error', title: 'Order not found' }),
  })

  const assignMutation = useMutation({
    mutationFn: (orderId: string) =>
      pickupApi.assignPicker(orderId, user?.id || '', user?.name || 'Associate'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pickup-pending'] })
      addToast({ type: 'success', title: 'Picking started' })
    },
  })

  const startPickingMutation = useMutation({
    mutationFn: (orderId: string) => pickupApi.startPicking(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pickup-pending'] })
      addToast({ type: 'success', title: 'Now picking items' })
    },
  })

  const pickItemMutation = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: string; quantity: number }) =>
      pickupApi.pickItem(itemId, quantity),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pickup-items'] })
    },
  })

  const completePickingMutation = useMutation({
    mutationFn: (orderId: string) => pickupApi.completePicking(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pickup-pending'] })
      queryClient.invalidateQueries({ queryKey: ['pickup-items'] })
      addToast({ type: 'success', title: 'All items picked!' })
    },
  })

  const packMutation = useMutation({
    mutationFn: (orderId: string) => pickupApi.packOrder(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pickup-pending'] })
      addToast({ type: 'success', title: 'Order packed' })
    },
  })

  const readyMutation = useMutation({
    mutationFn: (orderId: string) => pickupApi.markReadyForHandoff(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pickup-pending'] })
      queryClient.invalidateQueries({ queryKey: ['pickup-ready'] })
      addToast({ type: 'success', title: 'Ready for customer pickup' })
    },
  })

  const handoffMutation = useMutation({
    mutationFn: (orderId: string) => pickupApi.handoffOrder(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pickup-ready'] })
      addToast({ type: 'success', title: 'Order handed off' })
    },
  })

  const collectMutation = useMutation({
    mutationFn: ({ orderId, pod }: { orderId: string; pod: typeof podForm }) =>
      pickupApi.collectOrder(orderId, pod),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pickup-ready'] })
      setShowPodModal(false)
      setSelectedOrder(null)
      addToast({ type: 'success', title: 'Order collected with POD' })
    },
  })

  const handleLookup = () => {
    if (pickupCodeInput.trim()) {
      lookupMutation.mutate(pickupCodeInput.trim())
    }
  }

  const allPicked = pickupItems.length > 0 && pickupItems.every(i => i.pickedQuantity > 0)
  const allItemsPicked = pickupItems.length > 0 && pickupItems.every(i => i.status === 'PICKED' || i.status === 'SUBSTITUTED')

  return (
    <div className="min-h-screen bg-[var(--surface-sunken)]">
      <div className="bg-[var(--surface-base)] shadow-sm border-b px-4 py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Package className="w-6 h-6 text-[var(--nexus-success-600)]" />
            <h1 className="text-lg font-bold text-[var(--text-primary)]">Store Pickup</h1>
          </div>
          <button
            onClick={() => setSearchMode(!searchMode)}
            className="flex items-center gap-2 px-3 py-2 bg-[var(--surface-muted)] rounded-lg hover:bg-[var(--surface-muted)]"
          >
            <QrCode className="w-5 h-5" />
            <span className="text-sm">Scan</span>
          </button>
        </div>

        {searchMode && (
          <div className="mt-3 flex gap-2">
            <input
              type="text"
              value={pickupCodeInput}
              onChange={(e) => setPickupCodeInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleLookup()}
              placeholder="Enter or scan pickup code..."
              className="flex-1 px-3 py-2 border rounded-lg"
              autoFocus
            />
            <button
              onClick={handleLookup}
              disabled={lookupMutation.isPending}
              className="px-4 py-2 bg-[var(--nexus-success-600)] text-white rounded-lg"
            >
              Look Up
            </button>
          </div>
        )}
      </div>

      <div className="p-4 space-y-4">
        {statusCounts && (
          <div className="grid grid-cols-5 gap-2">
            {[
              { label: 'Total', value: statusCounts.total, color: 'text-[var(--text-primary)]' },
              { label: 'Pending', value: statusCounts.pending, color: 'text-[var(--nexus-warning-600)]' },
              { label: 'Picking', value: statusCounts.picking, color: 'text-[var(--nexus-primary-600)]' },
              { label: 'Ready', value: statusCounts.ready, color: 'text-[var(--nexus-success-600)]' },
              { label: 'Done', value: statusCounts.collected, color: 'text-emerald-600' },
            ].map((s) => (
              <div key={s.label} className="bg-[var(--surface-base)] rounded-lg p-3 text-center shadow-sm">
                <div className={clsx('text-2xl font-bold', s.color)}>{s.value}</div>
                <div className="text-xs text-[var(--text-secondary)]">{s.label}</div>
              </div>
            ))}
          </div>
        )}

        {selectedOrder ? (
          <SelectedOrderView
            order={selectedOrder}
            items={pickupItems}
            onBack={() => setSelectedOrder(null)}
            onStartPicking={() => startPickingMutation.mutate(selectedOrder.id)}
            onPickItem={(itemId, qty) => pickItemMutation.mutate({ itemId, quantity: qty })}
            onCompletePicking={() => completePickingMutation.mutate(selectedOrder.id)}
            onPack={() => packMutation.mutate(selectedOrder.id)}
            onReady={() => readyMutation.mutate(selectedOrder.id)}
            onHandoff={() => handoffMutation.mutate(selectedOrder.id)}
            onCollect={() => setShowPodModal(true)}
            allPicked={allPicked}
            allItemsPicked={allItemsPicked}
          />
        ) : (
          <div className="space-y-4">
            {readyOrders.length > 0 && (
              <div>
                <h2 className="text-sm font-semibold text-[var(--text-secondary)] mb-2 flex items-center gap-2">
                  <CheckCircle className="w-4 h-4 text-[var(--nexus-success-600)]" />
                  Ready for Handoff ({readyOrders.length})
                </h2>
                <div className="space-y-2">
                  {readyOrders.map((order) => (
                    <OrderCard
                      key={order.id}
                      order={order}
                      onSelect={() => setSelectedOrder(order)}
                      variant="ready"
                    />
                  ))}
                </div>
              </div>
            )}

            <div>
              <h2 className="text-sm font-semibold text-[var(--text-secondary)] mb-2 flex items-center gap-2">
                <Clock className="w-4 h-4 text-[var(--nexus-warning-600)]" />
                Pending Orders ({pendingOrders.length})
              </h2>
              {pendingOrders.length === 0 ? (
                <div className="bg-[var(--surface-base)] rounded-lg p-8 text-center text-[var(--text-secondary)] shadow-sm">
                  <Package className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
                  <p>No pending pickup orders</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {pendingOrders.map((order) => (
                    <OrderCard
                      key={order.id}
                      order={order}
                      onSelect={() => setSelectedOrder(order)}
                      variant="pending"
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {showPodModal && selectedOrder && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-md">
            <div className="p-4 border-b flex items-center justify-between">
              <h2 className="font-bold text-lg">Proof of Delivery</h2>
              <button onClick={() => setShowPodModal(false)}>
                <XCircle className="w-6 h-6 text-[var(--text-tertiary)]" />
              </button>
            </div>
            <div className="p-4 space-y-3">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Customer Name *</label>
                <input
                  type="text"
                  value={podForm.collectedByName}
                  onChange={(e) => setPodForm({ ...podForm, collectedByName: e.target.value })}
                  className="w-full px-3 py-2 border rounded-lg"
                  placeholder="Name of person collecting"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">ID Document</label>
                <input
                  type="text"
                  value={podForm.collectedByIdDoc}
                  onChange={(e) => setPodForm({ ...podForm, collectedByIdDoc: e.target.value })}
                  className="w-full px-3 py-2 border rounded-lg"
                  placeholder="Driver license / ID number"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Signature *</label>
                <div className="border-2 border-dashed rounded-lg p-4 text-center text-[var(--text-tertiary)]">
                  <FileText className="w-8 h-8 mx-auto mb-2" />
                  <p className="text-sm">Tap to capture signature</p>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Notes</label>
                <textarea
                  value={podForm.collectionNotes}
                  onChange={(e) => setPodForm({ ...podForm, collectionNotes: e.target.value })}
                  className="w-full px-3 py-2 border rounded-lg"
                  rows={2}
                  placeholder="Any notes about the handoff..."
                />
              </div>
            </div>
            <div className="p-4 border-t flex gap-2">
              <button
                onClick={() => setShowPodModal(false)}
                className="flex-1 px-4 py-2 border rounded-lg"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  if (!podForm.collectedByName || !podForm.collectorSignature) {
                    addToast({ type: 'error', title: 'Name and signature are required' })
                    return
                  }
                  collectMutation.mutate({ orderId: selectedOrder.id, pod: podForm })
                }}
                disabled={!podForm.collectedByName}
                className="flex-1 px-4 py-2 bg-[var(--nexus-success-600)] text-white rounded-lg disabled:opacity-50"
              >
                Confirm Collection
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function OrderCard({ order, onSelect, variant }: {
  order: PickupOrder
  onSelect: () => void
  variant: 'pending' | 'ready'
}) {
  return (
    <button
      onClick={onSelect}
      className={clsx(
        'w-full text-left bg-[var(--surface-base)] rounded-lg p-4 shadow-sm border-l-4 hover:shadow-md transition-shadow',
        variant === 'ready' ? 'border-[var(--nexus-success-500)]' : 'border-[var(--nexus-warning-500)]'
      )}
    >
      <div className="flex items-center justify-between">
        <div>
          <div className="font-mono text-sm font-bold">{order.orderNumber}</div>
          <div className="text-xs text-[var(--text-secondary)] mt-1">
            {order.customerName || 'Customer'} • {order.pickupType}
          </div>
          {order.pickupCode && (
            <div className="text-xs text-[var(--text-tertiary)] mt-1">Code: {order.pickupCode}</div>
          )}
        </div>
        <div className="text-right">
          {order.pickerName && (
            <div className="text-xs text-[var(--text-secondary)]">{order.pickerName}</div>
          )}
          {order.estimatedReadyAt && (
            <div className="text-xs text-[var(--text-tertiary)] mt-1">
              ETA: {new Date(order.estimatedReadyAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </div>
          )}
          <ArrowRight className="w-5 h-5 text-[var(--text-tertiary)] mt-1 ml-auto" />
        </div>
      </div>
    </button>
  )
}

function SelectedOrderView({ order, items, onBack, onStartPicking, onPickItem, onCompletePicking, onPack, onReady, onHandoff, onCollect, allPicked, allItemsPicked }: {
  order: PickupOrder
  items: PickupOrderItem[]
  onBack: () => void
  onStartPicking: () => void
  onPickItem: (itemId: string, qty: number) => void
  onCompletePicking: () => void
  onPack: () => void
  onReady: () => void
  onHandoff: () => void
  onCollect: () => void
  allPicked: boolean
  allItemsPicked: boolean
}) {
  const statusIndex = STATUS_FLOW.findIndex(s => s.key === order.status)

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="text-sm text-[var(--nexus-primary-600)] flex items-center gap-1">
        ← Back to list
      </button>

      <div className="bg-[var(--surface-base)] rounded-lg shadow-sm p-4">
        <div className="flex items-center justify-between mb-3">
          <div>
            <div className="font-bold text-lg">{order.orderNumber}</div>
            <div className="text-sm text-[var(--text-secondary)]">{order.customerName}</div>
          </div>
          <span className={clsx(
            'px-3 py-1 rounded-full text-sm font-medium',
            STATUS_FLOW[statusIndex]?.color || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]'
          )}>
            {STATUS_FLOW[statusIndex]?.label || order.status}
          </span>
        </div>

        <div className="flex gap-1 mb-3">
          {STATUS_FLOW.map((s, i) => (
            <div key={s.key} className={clsx(
              'flex-1 h-1.5 rounded',
              i <= statusIndex ? 'bg-[var(--nexus-success-50)]0' : 'bg-[var(--surface-muted)]'
            )} />
          ))}
        </div>

        <div className="grid grid-cols-2 gap-2 text-sm">
          <div><span className="text-[var(--text-secondary)]">Type:</span> {order.pickupType}</div>
          <div><span className="text-[var(--text-secondary)]">Code:</span> {order.pickupCode || '—'}</div>
          {order.pickerName && (
            <div><span className="text-[var(--text-secondary)]">Picker:</span> {order.pickerName}</div>
          )}
        </div>
      </div>

      {items.length > 0 && (
        <div className="bg-[var(--surface-base)] rounded-lg shadow-sm p-4">
          <h3 className="font-semibold mb-3">Items ({items.length})</h3>
          <div className="space-y-2">
            {items.map((item) => (
              <div key={item.id} className="flex items-center justify-between p-2 bg-[var(--surface-sunken)] rounded">
                <div>
                  <div className="font-mono text-sm">{item.sku}</div>
                  <div className="text-xs text-[var(--text-secondary)]">{item.productName}</div>
                  {item.location && (
                    <div className="text-xs text-[var(--nexus-primary-600)]">📍 {item.location}</div>
                  )}
                </div>
                <div className="text-right">
                  <div className="text-sm">
                    <span className={clsx(
                      'font-medium',
                      item.status === 'PICKED' ? 'text-[var(--nexus-success-600)]' :
                      item.status === 'SHORT' ? 'text-[var(--nexus-error-600)]' :
                      'text-[var(--text-secondary)]'
                    )}>
                      {item.pickedQuantity}
                    </span>
                    <span className="text-[var(--text-tertiary)]"> / {item.quantity}</span>
                  </div>
                  {item.status === 'PENDING' && order.status === 'PICKING' && (
                    <div className="flex gap-1 mt-1">
                      <button
                        onClick={() => onPickItem(item.id, item.quantity)}
                        className="px-2 py-1 text-xs bg-[var(--nexus-success-600)] text-white rounded"
                      >
                        Pick All
                      </button>
                      <button
                        onClick={() => {
                          const qty = prompt(`Picked quantity (max ${item.quantity}):`)
                          if (qty) onPickItem(item.id, parseInt(qty))
                        }}
                        className="px-2 py-1 text-xs bg-[var(--surface-muted)] rounded"
                      >
                        Partial
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="bg-[var(--surface-base)] rounded-lg shadow-sm p-4">
        <h3 className="font-semibold mb-3">Actions</h3>
        <div className="space-y-2">
          {order.status === 'PENDING' && (
            <button
              onClick={onStartPicking}
              className="w-full py-3 bg-[var(--nexus-primary-600)] text-white rounded-lg font-medium flex items-center justify-center gap-2"
            >
              <Package className="w-5 h-5" /> Start Picking
            </button>
          )}

          {order.status === 'PICKING' && allItemsPicked && (
            <button
              onClick={onCompletePicking}
              className="w-full py-3 bg-[var(--nexus-primary-600)] text-white rounded-lg font-medium flex items-center justify-center gap-2"
            >
              <CheckCircle className="w-5 h-5" /> Complete Picking
            </button>
          )}

          {order.status === 'PICKED' && (
            <button
              onClick={onPack}
              className="w-full py-3 bg-[var(--nexus-ai-600)] text-white rounded-lg font-medium flex items-center justify-center gap-2"
            >
              <Package className="w-5 h-5" /> Pack Order
            </button>
          )}

          {order.status === 'PACKED' && (
            <button
              onClick={onReady}
              className="w-full py-3 bg-[var(--nexus-success-600)] text-white rounded-lg font-medium flex items-center justify-center gap-2"
            >
              <CheckCircle className="w-5 h-5" /> Mark Ready for Pickup
            </button>
          )}

          {order.status === 'READY_FOR_HANDOFF' && (
            <>
              <button
                onClick={onHandoff}
                className="w-full py-3 bg-emerald-600 text-white rounded-lg font-medium flex items-center justify-center gap-2"
              >
                <Truck className="w-5 h-5" /> Hand Off (No POD)
              </button>
              <button
                onClick={onCollect}
                className="w-full py-3 bg-[var(--nexus-success-600)] text-white rounded-lg font-medium flex items-center justify-center gap-2"
              >
                <FileText className="w-5 h-5" /> Collect with POD
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
