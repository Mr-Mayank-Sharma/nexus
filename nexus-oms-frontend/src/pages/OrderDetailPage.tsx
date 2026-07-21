import { useParams, useNavigate } from 'react-router-dom'
import { useState, useEffect, useMemo } from 'react'
import {
  ArrowLeft, Clock, MapPin, Package, User, CreditCard, Truck, CheckCircle, XCircle,
  AlertTriangle, FileText, MessageSquare, Printer, Loader2, Receipt,
  Edit3, Split, Merge, Brain, Download, ExternalLink, Shield,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTimeline from '../components/enterprise/EnterpriseTimeline'
import { OrderTimelineEvent, Order } from '../types'
import { useToast } from '../hooks/useToast'
import * as ordersApi from '../api/orders'
import * as aiPlatformApi from '../api/aiPlatform'
import * as aiOrdersApi from '../api/aiOrders'
import type { AiSuggestion, AiActionHistory } from '../api/aiOrders'
import PermissionGate from '../components/rbac/PermissionGate'
import Autocomplete from '../components/common/Autocomplete'

interface Payment {
  id: string
  method: string
  amount: number
  status: string
  date: string
  reference: string
}

interface Document {
  id: string
  name: string
  type: string
  size: string
  uploadedAt: string
  url: string
}

export default function OrderDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  const [showModifyModal, setShowModifyModal] = useState(false)
  const [showSplitModal, setShowSplitModal] = useState(false)
  const [showMergeModal, setShowMergeModal] = useState(false)
  const { addToast } = useToast()

  const [aiPrediction, setAiPrediction] = useState<{ deliveryDate?: string; confidence?: number; anomaly?: boolean } | null>(null)
  const [aiSuggestions, setAiSuggestions] = useState<AiSuggestion[]>([])
  const [aiHistory, setAiHistory] = useState<AiActionHistory[]>([])
  const [aiExecuting, setAiExecuting] = useState<string | null>(null)
  const [payments, setPayments] = useState<Payment[]>([])
  const [documents, setDocuments] = useState<Document[]>([])

  useEffect(() => { if (id) fetchOrder() }, [id])

  async function fetchOrder() {
    try {
      setLoading(true)
      const res = await ordersApi.getOrderById(id!)
      const orderNum = res.data.orderNumber || res.data.externalId || res.data.channelOrderId || `ORD-${(res.data.id || id!).slice(0, 8).toUpperCase()}`
      const rawAddr = res.data.shippingAddress || res.data.shipTo
      const parseAddress = (a: any): { street: string; city: string; state: string; zip: string; country: string } => {
        if (!a) return { street: '', city: '', state: '', zip: '', country: '' }
        if (typeof a === 'string') {
          const parts = a.split(',').map((s: string) => s.trim())
          return { street: parts[0] || '', city: parts[1] || '', state: parts[2]?.split(' ')[0] || '', zip: parts[2]?.split(' ')[1] || parts[2] || '', country: parts[3] || '' }
        }
        return { street: a.street || a.line1 || '', city: a.city || '', state: a.state || '', zip: a.zip || a.pincode || '', country: a.country || '' }
      }
      setOrder({
        id: res.data.id || id!,
        orderNumber: orderNum,
        customerName: res.data.customerName || (res.data.customerId ? `Customer ${res.data.customerId.slice(0, 8)}` : 'Guest Customer'),
        customerEmail: res.data.customerEmail || '',
        channel: res.data.channel || 'MANUAL',
        status: res.data.status || 'PENDING',
        items: (res.data.items || []).map((i: any) => ({
          id: i.id, sku: i.sku || '', productName: i.productName || i.sku || '',
          quantity: i.quantity || 0, unitPrice: i.unitPrice || 0, totalPrice: (i.totalPrice || i.unitPrice * i.quantity || 0),
        })),
        total: res.data.total || 0, subtotal: res.data.subtotal || 0,
        shippingCost: res.data.shippingCost || 0, tax: res.data.taxAmount || res.data.tax || 0, currency: res.data.currency || 'USD',
        shippingAddress: parseAddress(rawAddr),
        billingAddress: res.data.billingAddress ? parseAddress(res.data.billingAddress) : { street: '', city: '', state: '', zip: '', country: '' },
        fulfillmentType: res.data.fulfillmentType || 'STANDARD',
        allocationNodeId: res.data.allocatedNode || res.data.allocationNodeId || '', carrier: res.data.carrierId || '',
        trackingNumber: res.data.trackingNumber || '', promisedDeliveryDate: res.data.promisedDelivery || '',
        estimatedShipDate: res.data.shipBy || res.data.estimatedShipDate || res.data.shippedAt || '',
        shippedDate: res.data.shippedAt || '', deliveredDate: res.data.deliveredAt || '',
        createdAt: res.data.createdAt || new Date().toISOString(),
        updatedAt: res.data.updatedAt || new Date().toISOString(), priority: 'MEDIUM',
        hasException: res.data.subStatus === 'EXCEPTION', tags: [], notes: res.data.notes || '',
      })

      setPayments(res.data.paymentStatus ? [
        { id: 'pmt1', method: res.data.paymentReference ? `Ref: ${res.data.paymentReference}` : 'Standard', amount: res.data.total || 0, status: res.data.paymentStatus === 'PAID' ? 'CAPTURED' : res.data.paymentStatus || 'PENDING', date: res.data.createdAt || new Date().toISOString(), reference: res.data.paymentReference || 'N/A' },
      ] : [])
      setDocuments([])

      // Try AI prediction
      try {
        const aiRes = await aiPlatformApi.predict('delivery_estimate', { orderId: id, items: res.data.items || [] })
        if (aiRes.data) {
          setAiPrediction({
            deliveryDate: aiRes.data.predictedDate || aiRes.data.estimatedDelivery,
            confidence: aiRes.data.confidence,
            anomaly: aiRes.data.isAnomaly,
          })
        }
      } catch { /* AI prediction not available in this build */ }

      // Fetch AI suggestions & history (silent fail — not available in this build)
      try {
        const [sugRes, histRes] = await Promise.all([
          aiOrdersApi.getAiSuggestions(id!),
          aiOrdersApi.getAiHistory(id!),
        ])
        setAiSuggestions(sugRes.data || [])
        setAiHistory(histRes.data || [])
      } catch { setAiSuggestions([]); setAiHistory([]) }
    } catch (err) {
      addToast({ type: 'error', title: 'Failed to load order' })
      console.error('Failed to fetch order:', err)
      setOrder(null)
    } finally {
      setLoading(false)
    }
  }

  async function handleAction(action: string) {
    if (!id) return
    setActionLoading(action)
    try {
      if (action === 'confirm') await ordersApi.confirmOrder(id)
      else if (action === 'allocate') await ordersApi.allocateOrder(id)
      else if (action === 'ship') await ordersApi.shipOrder(id, 'auto', 'TN-' + Date.now())
      else if (action === 'cancel') await ordersApi.cancelOrder(id)
      addToast({ type: 'success', title: `Order ${action}ed successfully` })
      await fetchOrder()
    } catch {
      addToast({ type: 'error', title: `Failed to ${action} order` })
    } finally {
      setActionLoading(null)
    }
  }

  function handlePrintLabel() {
    if (!order) return
    const win = window.open('', '_blank')
    if (!win) return
    const items = (typeof order.items === 'string' ? JSON.parse(order.items) : order.items || []) as Array<{sku?:string;productName?:string;quantity?:number}>
    win.document.write(`<!DOCTYPE html><html><head><title>Label - ${order.orderNumber || order.id}</title>
      <style>body{font-family:monospace;padding:20px;max-width:400px;margin:auto}
      h1{font-size:16px;border-bottom:2px solid #000;padding-bottom:8px}
      table{width:100%;border-collapse:collapse;margin:12px 0}
      th,td{text-align:left;padding:4px 8px;border-bottom:1px solid #ddd;font-size:12px}
      .addr{font-size:13px;line-height:1.5;margin:8px 0;padding:8px;border:1px solid #ccc;border-radius:4px}
      .track{font-size:18px;font-weight:bold;margin:12px 0;padding:8px;background:#f5f5f5;text-align:center;border-radius:4px}
      .no-print{display:none}@media print{body{padding:10px}.no-print{display:block;text-align:center;margin-bottom:10px;font-size:10px;color:#999}}
      </style></head><body>
      <button class="no-print" onclick="window.print()" style="padding:8px 16px;cursor:pointer;border:1px solid #ccc;border-radius:4px;background:#fff">Print This Label</button>
      <h1>${order.orderNumber || 'Order'}</h1>
      <div class="track">${order.trackingNumber || 'No Tracking'}</div>
      <div class="addr"><strong>Ship To:</strong><br/>${order.customerName || ''}<br/>${order.shippingAddress?.street || ''}<br/>${order.shippingAddress?.city || ''}, ${order.shippingAddress?.state || ''} ${order.shippingAddress?.zip || ''}</div>
      <table><tr><th>SKU</th><th>Product</th><th>Qty</th></tr>
      ${items.map(i => `<tr><td>${i.sku || ''}</td><td>${i.productName || ''}</td><td>${i.quantity || 0}</td></tr>`).join('')}
      </table>
      ${order.trackingNumber ? `<p style="font-size:11px;color:#666">Carrier: ${order.carrier || 'Auto'} &nbsp;|&nbsp; Tracking: ${order.trackingNumber}</p>` : ''}
      </body></html>`)
    win.document.close()
    addToast({ type: 'success', title: 'Label opened in new tab' })
  }

  function handlePrintInvoice() {
    if (!order) return
    const win = window.open('', '_blank')
    if (!win) return
    const items = (typeof order.items === 'string' ? JSON.parse(order.items) : order.items || []) as Array<{sku?:string;productName?:string;quantity?:number;unitPrice?:number}>
    const subtotal = items.reduce((s, i) => s + (i.unitPrice || 0) * (i.quantity || 0), 0)
    win.document.write(`<!DOCTYPE html><html><head><title>Invoice - ${order.orderNumber || order.id}</title>
      <style>body{font-family:monospace;padding:30px;max-width:700px;margin:auto}
      h1{font-size:20px;border-bottom:2px solid #000;padding-bottom:8px}
      .header{display:flex;justify-content:space-between;margin:12px 0 20px;font-size:12px;color:#555}
      table{width:100%;border-collapse:collapse;margin:16px 0}
      th{background:#f5f5f5;text-align:left;padding:8px;border-bottom:2px solid #ddd;font-size:12px}
      td{padding:8px;border-bottom:1px solid #eee;font-size:12px}
      .total{text-align:right;font-size:14px;font-weight:bold;margin-top:12px;padding-top:8px;border-top:2px solid #000}
      .addr{font-size:12px;line-height:1.6;margin:12px 0;padding:12px;border:1px solid #ddd;border-radius:4px}
      .no-print{text-align:center;margin-bottom:16px}
      </style></head><body>
      <button class="no-print" onclick="window.print()" style="padding:8px 24px;cursor:pointer;border:1px solid #ccc;border-radius:4px;background:#fff;font-size:14px">Print Invoice</button>
      <h1>INVOICE</h1>
      <div class="header"><span><strong>Order:</strong> ${order.orderNumber || order.id}</span><span><strong>Date:</strong> ${new Date(order.createdAt).toLocaleDateString()}</span></div>
      <div class="header"><span><strong>Channel:</strong> ${order.channel || 'N/A'}</span><span><strong>Status:</strong> ${order.status}</span></div>
      <div class="addr"><strong>Bill To / Ship To:</strong><br/>${order.customerName || 'N/A'}<br/>${order.customerEmail || ''}<br/>${order.shippingAddress?.street || ''}<br/>${order.shippingAddress?.city || ''}, ${order.shippingAddress?.state || ''} ${order.shippingAddress?.zip || ''}</div>
      <table><tr><th>SKU</th><th>Product</th><th>Qty</th><th>Price</th><th>Total</th></tr>
      ${items.map(i => `<tr><td>${i.sku || ''}</td><td>${i.productName || ''}</td><td>${i.quantity || 0}</td><td>$${(i.unitPrice || 0).toFixed(2)}</td><td>$${((i.unitPrice || 0) * (i.quantity || 0)).toFixed(2)}</td></tr>`).join('')}
      </table>
      <div class="total">Total: $${subtotal.toFixed(2)}</div>
      <p style="font-size:10px;color:#999;margin-top:24px;text-align:center">Thank you for your business</p>
      </body></html>`)
    win.document.close()
    addToast({ type: 'success', title: 'Invoice opened in new tab' })
  }

  async function handleAiAction(actionType: string) {
    if (!id) return
    setAiExecuting(actionType)
    try {
      const res = await aiOrdersApi.executeAiAction(id, { actionType })
      if (res.data?.status === 'SUCCESS') {
        addToast({ type: 'success', title: `AI action completed: ${res.data.label}` })
      } else {
        addToast({ type: 'error', title: `AI action failed: ${res.data?.details || 'Unknown error'}` })
      }
      await fetchOrder()
    } catch {
      addToast({ type: 'error', title: 'Failed to execute AI action' })
    } finally {
      setAiExecuting(null)
    }
  }

  const [modifyForm, setModifyForm] = useState({ street: '', city: '', state: '', zip: '', country: '' })
  const [modifyItems, setModifyItems] = useState<{ sku: string; productName: string; quantity: number; unitPrice: number }[]>([])

  function openModify() {
    if (!order) return
    setModifyForm({ street: order.shippingAddress.street, city: order.shippingAddress.city, state: order.shippingAddress.state, zip: order.shippingAddress.zip, country: order.shippingAddress.country })
    setModifyItems(order.items.map(i => ({ sku: i.sku, productName: i.productName, quantity: i.quantity, unitPrice: i.unitPrice })))
    setShowModifyModal(true)
  }

  async function handleModify() {
    if (!id) return; setActionLoading('modify')
    try {
      await ordersApi.modifyOrder(id, { shippingAddress: { line1: modifyForm.street, city: modifyForm.city, state: modifyForm.state, pincode: modifyForm.zip, country: modifyForm.country }, items: modifyItems })
      addToast({ type: 'success', title: 'Order modified' }); setShowModifyModal(false); await fetchOrder()
    } catch { addToast({ type: 'error', title: 'Failed to modify order' }) } finally { setActionLoading(null) }
  }

  function addModifyRow() { setModifyItems([...modifyItems, { sku: '', productName: '', quantity: 1, unitPrice: 0 }]) }
  function updateModifyItem(index: number, field: string, value: any) { const items = [...modifyItems]; (items[index] as any)[field] = value; setModifyItems(items) }
  function removeModifyItem(index: number) { setModifyItems(modifyItems.filter((_, i) => i !== index)) }

  const [splitGroups, setSplitGroups] = useState<{ itemIds: string[]; priority: number }[]>([])

  function openSplit() {
    if (!order) return; setSplitGroups([{ itemIds: order.items.map(i => i.id || i.sku), priority: 1 }]); setShowSplitModal(true)
  }

  function toggleSplitItem(groupIdx: number, itemId: string) {
    const groups = [...splitGroups]; const idx = groups[groupIdx].itemIds.indexOf(itemId)
    idx >= 0 ? groups[groupIdx].itemIds.splice(idx, 1) : groups[groupIdx].itemIds.push(itemId); setSplitGroups(groups)
  }

  function addSplitGroup() { setSplitGroups([...splitGroups, { itemIds: [], priority: splitGroups.length + 1 }]) }
  function removeSplitGroup(idx: number) { const movedItems = splitGroups[idx].itemIds; const groups = splitGroups.filter((_, i) => i !== idx); if (groups.length > 0) groups[0].itemIds.push(...movedItems); setSplitGroups(groups) }

  async function handleSplit() {
    if (!id) return; const nonEmpty = splitGroups.filter(g => g.itemIds.length > 0)
    if (nonEmpty.length < 2) { addToast({ type: 'warning', title: 'Create at least 2 groups with items to split' }); return }
    setActionLoading('split')
    try {
      if (new Set(nonEmpty.flatMap(g => g.itemIds)).size < (order?.items.length || 0)) { addToast({ type: 'warning', title: 'All items must be assigned' }); setActionLoading(null); return }
      await ordersApi.splitOrder(id, { groups: nonEmpty }); addToast({ type: 'success', title: 'Order split' }); setShowSplitModal(false); await fetchOrder()
    } catch { addToast({ type: 'error', title: 'Failed to split' }) } finally { setActionLoading(null) }
  }

  const [mergeOrderIds, setMergeOrderIds] = useState<string[]>([''])
  const [mergeSearchTerm, setMergeSearchTerm] = useState('')
  const [mergeResults, setMergeResults] = useState<any[]>([])
  const [searchingMerge, setSearchingMerge] = useState(false)

  function openMerge() { setMergeOrderIds(['']); setMergeSearchTerm(''); setMergeResults([]); setShowMergeModal(true) }

  async function searchOrders() {
    if (!mergeSearchTerm.trim()) return; setSearchingMerge(true)
    try { const res = await ordersApi.getOrders({ search: mergeSearchTerm }); setMergeResults(res.data || []) } catch { addToast({ type: 'error', title: 'Search failed' }) } finally { setSearchingMerge(false) }
  }

  function addMergeId() { setMergeOrderIds([...mergeOrderIds, '']) }
  function updateMergeId(idx: number, val: string) { const ids = [...mergeOrderIds]; ids[idx] = val; setMergeOrderIds(ids) }
  function removeMergeId(idx: number) { setMergeOrderIds(mergeOrderIds.filter((_, i) => i !== idx)) }

  async function handleMerge() {
    if (!id) return; const validIds = mergeOrderIds.filter(Boolean)
    if (validIds.length < 2) { addToast({ type: 'warning', title: 'Add at least 2 order IDs' }); return }
    setActionLoading('merge')
    try { await ordersApi.mergeOrders({ orderIds: [...validIds, id], targetOrderId: id }); addToast({ type: 'success', title: 'Orders merged' }); setShowMergeModal(false); await fetchOrder() }
    catch { addToast({ type: 'error', title: 'Merge failed' }) } finally { setActionLoading(null) }
  }

  const timelineEvents: OrderTimelineEvent[] = useMemo(() => {
    if (!order) return []
    const events: OrderTimelineEvent[] = [
      { id: 'e1', type: 'CREATED', title: 'Order Created', description: `Placed via ${order.channel}`, timestamp: order.createdAt, user: 'System' },
    ]
    if (order.status !== 'PENDING') events.push({ id: 'e2', type: 'CONFIRMED', title: 'Order Confirmed', description: 'Payment verified', timestamp: order.updatedAt, user: 'System' })
    if (['ALLOCATED', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED'].includes(order.status)) events.push({ id: 'e3', type: 'ALLOCATED', title: 'Inventory Allocated', description: order.allocationNodeId ? `Allocated to ${order.allocationNodeId}` : 'Allocation complete', timestamp: order.updatedAt, user: 'Auto-Allocator' })
    if (['SHIPPED', 'IN_TRANSIT', 'DELIVERED'].includes(order.status)) events.push({ id: 'e4', type: 'SHIPPED', title: 'Shipped', description: `Carrier: ${order.carrier} · ${order.trackingNumber}`, timestamp: order.updatedAt, user: 'System' })
    if (order.status === 'DELIVERED') events.push({ id: 'e5', type: 'DELIVERED', title: 'Delivered', description: 'Package delivered', timestamp: order.updatedAt, user: 'System' })
    if (order.status === 'CANCELLED') events.push({ id: 'e6', type: 'CANCELLED', title: 'Cancelled', description: 'Order was cancelled', timestamp: order.updatedAt, user: 'System' })
    aiHistory.forEach(h => events.push({
      id: `ai-${h.id}`,
      type: 'AI_ACTION',
      title: h.label,
      description: `${h.details} (${h.actor})`,
      timestamp: h.timestamp,
      user: h.actor,
    }))
    return events
  }, [order, aiHistory])

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--color-primary)]" /></div>

  if (!order) return (
    <div className="space-y-6">
      <button onClick={() => navigate('/orders')} className="enterprise-btn enterprise-btn-ghost text-sm"><ArrowLeft className="w-4 h-4" /> Back to Orders</button>
      <div className="text-center py-12">
        <AlertTriangle className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
        <p className="text-[var(--text-tertiary)]">Order not found</p>
      </div>
    </div>
  )

  const statusActions: { label: string; icon: React.ReactNode; onClick: () => void; variant: 'primary' | 'danger'; disabled: boolean }[] = []
  if (order.status === 'PENDING') statusActions.push({ label: 'Confirm Order', icon: actionLoading === 'confirm' ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle className="w-4 h-4" />, onClick: () => handleAction('confirm'), variant: 'primary', disabled: actionLoading !== null })
  if (order.status === 'CONFIRMED') statusActions.push({ label: 'Allocate Inventory', icon: actionLoading === 'allocate' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Package className="w-4 h-4" />, onClick: () => handleAction('allocate'), variant: 'primary', disabled: actionLoading !== null })
  if (order.status === 'ALLOCATED') statusActions.push({ label: 'Ship Order', icon: actionLoading === 'ship' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Truck className="w-4 h-4" />, onClick: () => handleAction('ship'), variant: 'primary', disabled: actionLoading !== null })
  if (order.status !== 'DELIVERED' && order.status !== 'CANCELLED') statusActions.push({ label: 'Cancel Order', icon: actionLoading === 'cancel' ? <Loader2 className="w-4 h-4 animate-spin" /> : <XCircle className="w-4 h-4" />, onClick: () => handleAction('cancel'), variant: 'danger', disabled: actionLoading !== null })

  const isModifiable = !['SHIPPED', 'DELIVERED', 'CANCELLED', 'ALLOCATED'].includes(order.status)

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Orders', path: '/orders' }, { label: order.orderNumber }]} />

      <div className="flex items-start justify-between flex-wrap gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2"><Package className="w-6 h-6" />{order.orderNumber}</h1>
            <EnterpriseStatusBadge status={order.status} label={order.status} />
            {order.hasException && <EnterpriseStatusBadge status="error" label="Exception" />}
          </div>
          <p className="text-sm text-[var(--text-tertiary)] mt-1">
            Created {new Date(order.createdAt).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' })}
            {' · '}{order.channel} · {order.fulfillmentType}
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          {isModifiable && (<PermissionGate resource="orders" action="edit"><button onClick={openModify} className="enterprise-btn enterprise-btn-secondary text-sm" disabled={actionLoading !== null}><Edit3 className="w-4 h-4" /> Modify</button></PermissionGate>)}
          {isModifiable && (<PermissionGate resource="orders" action="edit"><button onClick={openSplit} className="enterprise-btn enterprise-btn-secondary text-sm" disabled={actionLoading !== null}><Split className="w-4 h-4" /> Split</button></PermissionGate>)}
          {order.status === 'PENDING' && (<PermissionGate resource="orders" action="edit"><button onClick={openMerge} className="enterprise-btn enterprise-btn-secondary text-sm" disabled={actionLoading !== null}><Merge className="w-4 h-4" /> Merge</button></PermissionGate>)}
          <button onClick={handlePrintLabel} className="enterprise-btn enterprise-btn-secondary text-sm"><Printer className="w-4 h-4" /> Print Label</button>
          <button onClick={handlePrintInvoice} className="enterprise-btn enterprise-btn-secondary text-sm"><Receipt className="w-4 h-4" /> Invoice</button>
          {statusActions.map(a => (
            <PermissionGate key={a.label} resource="orders" action="edit">
              <button onClick={a.onClick} disabled={a.disabled}
                className={a.variant === 'primary' ? 'enterprise-btn enterprise-btn-primary text-sm' : 'enterprise-btn enterprise-btn-danger text-sm'}>
                {a.icon}{a.label}
              </button>
            </PermissionGate>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Content */}
        <div className="lg:col-span-2 space-y-6">
          {/* Order Items */}
          <div className="enterprise-card">
            <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]">Order Items ({order.items.length})</h3></div>
            <div className="overflow-x-auto">
              <table className="enterprise-table w-full">
                <thead>
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Product</th>
                    <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">SKU</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Qty</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Price</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border-subtle)]">
                  {order.items.map((item) => (
                    <tr key={item.sku} className="enterprise-table-row">
                      <td className="px-6 py-3 text-sm text-[var(--text-primary)]">{item.productName}</td>
                      <td className="px-6 py-3 text-sm text-[var(--text-tertiary)] font-mono">{item.sku}</td>
                      <td className="px-6 py-3 text-sm text-[var(--text-secondary)] text-right">{item.quantity}</td>
                      <td className="px-6 py-3 text-sm text-[var(--text-secondary)] text-right">{order.currency} {item.unitPrice.toFixed(2)}</td>
                      <td className="px-6 py-3 text-sm font-medium text-[var(--text-primary)] text-right">{order.currency} {item.totalPrice.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-[var(--border-subtle)]"><td colSpan={4} className="px-6 py-3 text-sm text-[var(--text-secondary)] text-right">Subtotal</td><td className="px-6 py-3 text-sm font-medium text-[var(--text-primary)] text-right">{order.currency} {order.subtotal.toFixed(2)}</td></tr>
                  <tr><td colSpan={4} className="px-6 py-3 text-sm text-[var(--text-secondary)] text-right">Shipping</td><td className="px-6 py-3 text-sm text-[var(--text-primary)] text-right">{order.currency} {order.shippingCost.toFixed(2)}</td></tr>
                  <tr><td colSpan={4} className="px-6 py-3 text-sm text-[var(--text-secondary)] text-right">Tax</td><td className="px-6 py-3 text-sm text-[var(--text-primary)] text-right">{order.currency} {order.tax.toFixed(2)}</td></tr>
                  <tr className="border-t-2 border-[var(--border-subtle)]"><td colSpan={4} className="px-6 py-3 text-sm font-semibold text-[var(--text-primary)] text-right">Total</td><td className="px-6 py-3 text-sm font-bold text-[var(--text-primary)] text-right">{order.currency} {order.total.toFixed(2)}</td></tr>
                </tfoot>
              </table>
            </div>
          </div>

          {/* Timeline */}
          <div className="enterprise-card">
            <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]">Order Timeline</h3></div>
            <div className="p-6">
              <EnterpriseTimeline
                events={timelineEvents.map(e => ({
                  id: e.id, title: e.title,
                  description: e.description,
                  timestamp: e.timestamp,
                  status: e.type === 'DELIVERED' || e.type === 'CONFIRMED' ? 'completed' : e.type === 'SHIPPED' ? 'current' : e.type === 'AI_ACTION' ? 'completed' : e.type === 'CANCELLED' || e.type === 'EXCEPTION' ? 'error' : 'pending',
                }))}
              />
            </div>
          </div>

          {/* AI Insights */}
          {aiPrediction && (
            <div className="enterprise-card border border-[var(--color-primary)]/20">
              <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Brain className="w-4 h-4 text-[var(--color-primary)]" />AI Insights</h3></div>
              <div className="p-6">
                <div className="grid grid-cols-3 gap-4">
                  <div>
                    <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase">Predicted Delivery</p>
                    <p className="text-sm font-medium text-[var(--text-primary)] mt-1">{aiPrediction.deliveryDate ? new Date(aiPrediction.deliveryDate).toLocaleDateString() : 'N/A'}</p>
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase">Confidence</p>
                    <div className="flex items-center gap-2 mt-1">
                      <div className="w-16 h-2 bg-[var(--bg-tertiary)] rounded-full overflow-hidden">
                        <div className="h-full rounded-full bg-[var(--color-primary)]" style={{ width: `${(aiPrediction.confidence || 0) * 100}%` }} />
                      </div>
                      <span className="text-sm font-medium text-[var(--text-primary)]">{Math.round((aiPrediction.confidence || 0) * 100)}%</span>
                    </div>
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase">Anomaly Detection</p>
                    <p className="mt-1">{aiPrediction.anomaly
                      ? <EnterpriseStatusBadge status="error" label="Anomaly Detected" />
                      : <EnterpriseStatusBadge status="success" label="Normal" />}</p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* AI Suggestions */}
          {aiSuggestions.filter(s => s.confidence > 0.3).length > 0 && (
            <div className="enterprise-card border border-[var(--color-ai-border)]">
              <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Brain className="w-4 h-4 text-[var(--color-ai)]" />AI Suggested Actions</h3></div>
              <div className="p-4 space-y-3">
                {aiSuggestions.filter(s => s.confidence > 0.3).map(s => (
                  <div key={s.actionType} className="enterprise-insight">
                    <Brain className="w-5 h-5 shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-2">
                        <h4 className="text-sm font-medium">{s.label}</h4>
                        <span className="text-xs font-medium text-[var(--color-ai)] whitespace-nowrap">{Math.round(s.confidence * 100)}% confidence</span>
                      </div>
                      <p className="text-xs mt-0.5">{s.description}</p>
                      <div className="w-full h-1.5 bg-[var(--bg-tertiary)] rounded-full mt-2 overflow-hidden">
                        <div className="h-full rounded-full bg-[var(--color-ai)]" style={{ width: `${s.confidence * 100}%` }} />
                      </div>
                    </div>
                    <div className="flex gap-1.5 shrink-0">
                      <PermissionGate resource="orders" action="edit">
                        <button onClick={() => handleAiAction(s.actionType)} disabled={aiExecuting !== null}
                          className="enterprise-btn enterprise-btn-ai enterprise-btn-sm">
                          {aiExecuting === s.actionType ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <CheckCircle className="w-3.5 h-3.5" />}
                          Apply
                        </button>
                      </PermissionGate>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Documents */}
          <div className="enterprise-card">
            <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><FileText className="w-4 h-4" />Documents</h3></div>
            <div className="p-6">
              {documents.length === 0 ? (
                <div className="flex items-center gap-2 text-sm text-[var(--text-tertiary)]"><FileText className="w-4 h-4" /> No documents attached</div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {documents.map(doc => (
                    <div key={doc.id} className="flex items-center justify-between p-3 rounded-lg bg-[var(--bg-tertiary)]">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded bg-[var(--color-primary)]/10 flex items-center justify-center">
                          <FileText className="w-4 h-4 text-[var(--color-primary)]" />
                        </div>
                        <div>
                          <p className="text-sm font-medium text-[var(--text-primary)]">{doc.name}</p>
                          <p className="text-xs text-[var(--text-tertiary)]">{doc.type} · {doc.size}</p>
                        </div>
                      </div>
                      <div className="flex gap-1">
                        <button onClick={() => addToast({ type: 'info', title: 'Download not yet implemented' })} className="enterprise-btn enterprise-btn-icon enterprise-btn-ghost" title="Download"><Download className="w-3.5 h-3.5" /></button>
                        <button onClick={() => addToast({ type: 'info', title: 'View not yet implemented' })} className="enterprise-btn enterprise-btn-icon enterprise-btn-ghost" title="View"><ExternalLink className="w-3.5 h-3.5" /></button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Right Sidebar */}
        <div className="space-y-6">
          {/* Customer */}
          <div className="enterprise-card">
            <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]"><User className="w-4 h-4 inline mr-1" />Customer</h3></div>
            <div className="p-5 space-y-3">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-[var(--color-primary)]/10 flex items-center justify-center text-[var(--color-primary)] font-semibold">
                  {order.customerName.charAt(0).toUpperCase()}
                </div>
                <div>
                  <p className="text-sm font-medium text-[var(--text-primary)]">{order.customerName}</p>
                  <p className="text-xs text-[var(--text-tertiary)]">{order.customerEmail}</p>
                </div>
              </div>
              <hr className="border-[var(--border-subtle)]" />
              <div className="flex items-start gap-2">
                <MapPin className="w-4 h-4 text-[var(--text-tertiary)] mt-0.5" />
                <div>
                  <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase">Shipping</p>
                  {order.shippingAddress.street || order.shippingAddress.city ? (
                    <>
                      <p className="text-sm text-[var(--text-primary)]">{order.shippingAddress.street}</p>
                      <p className="text-sm text-[var(--text-primary)]">{order.shippingAddress.city}{order.shippingAddress.state ? `, ${order.shippingAddress.state}` : ''}{order.shippingAddress.zip ? ` ${order.shippingAddress.zip}` : ''}</p>
                    </>
                  ) : (
                    <p className="text-sm text-[var(--text-tertiary)]">No address provided</p>
                  )}
                </div>
              </div>
              {order.billingAddress.street || order.billingAddress.city ? (
                <div className="flex items-start gap-2">
                  <MapPin className="w-4 h-4 text-[var(--text-tertiary)] mt-0.5" />
                  <div>
                    <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase">Billing</p>
                    <p className="text-sm text-[var(--text-primary)]">{order.billingAddress.street}</p>
                    <p className="text-sm text-[var(--text-primary)]">{order.billingAddress.city}{order.billingAddress.state ? `, ${order.billingAddress.state}` : ''}{order.billingAddress.zip ? ` ${order.billingAddress.zip}` : ''}</p>
                  </div>
                </div>
              ) : null}
            </div>
          </div>

          {/* Fulfillment */}
          <div className="enterprise-card">
            <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]"><Package className="w-4 h-4 inline mr-1" />Fulfillment</h3></div>
            <div className="p-5 space-y-3">
              <div className="flex items-center justify-between"><span className="text-sm text-[var(--text-secondary)]">Type</span><span className="text-sm font-medium text-[var(--text-primary)]">{order.fulfillmentType}</span></div>
              {order.allocationNodeId ? <div className="flex items-center justify-between"><span className="text-sm text-[var(--text-secondary)]">Allocated Node</span><span className="text-sm font-medium text-[var(--text-primary)]">{order.allocationNodeId}</span></div> : null}
              {order.carrier ? <div className="flex items-center justify-between"><span className="text-sm text-[var(--text-secondary)]">Carrier</span><span className="text-sm font-medium text-[var(--text-primary)]">{order.carrier}</span></div> : null}
              {order.trackingNumber ? <div className="flex items-center justify-between"><span className="text-sm text-[var(--text-secondary)]">Tracking</span><span className="text-sm font-mono text-[var(--color-primary)]">{order.trackingNumber}</span></div> : null}
              {order.promisedDeliveryDate ? <div className="flex items-center justify-between"><span className="text-sm text-[var(--text-secondary)]">Promised Delivery</span><span className="text-sm font-medium text-[var(--text-primary)]">{new Date(order.promisedDeliveryDate).toLocaleDateString()}</span></div> : null}
              {order.shippedDate ? <div className="flex items-center justify-between"><span className="text-sm text-[var(--text-secondary)]">Shipped</span><span className="text-sm font-medium text-[var(--text-primary)]">{new Date(order.shippedDate).toLocaleDateString()}</span></div> : null}
              {order.deliveredDate ? <div className="flex items-center justify-between"><span className="text-sm text-[var(--text-secondary)]">Delivered</span><span className="text-sm font-medium text-[var(--text-primary)]">{new Date(order.deliveredDate).toLocaleDateString()}</span></div> : null}
            </div>
          </div>

          {/* Payments */}
          <div className="enterprise-card">
            <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]"><CreditCard className="w-4 h-4 inline mr-1" />Payments</h3></div>
            <div className="p-5 space-y-3">
              {payments.length === 0 ? (
                <div className="flex items-center gap-2 text-sm text-[var(--text-tertiary)]"><CreditCard className="w-4 h-4" /> No payments recorded</div>
              ) : payments.map(p => (
                <div key={p.id} className="flex items-start justify-between">
                  <div>
                    <p className="text-sm font-medium text-[var(--text-primary)]">{order.currency} {p.amount.toFixed(2)}</p>
                    <p className="text-xs text-[var(--text-tertiary)]">{p.method}</p>
                    <p className="text-xs text-[var(--text-tertiary)]">Ref: {p.reference}</p>
                  </div>
                  <EnterpriseStatusBadge status={p.status === 'CAPTURED' ? 'success' : p.status === 'PENDING' ? 'pending' : 'error'} label={p.status} size="sm" />
                </div>
              ))}
            </div>
          </div>

          {/* Audit Trail */}
          <div className="enterprise-card">
            <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]"><Shield className="w-4 h-4 inline mr-1" />Activity Log</h3></div>
            <div className="p-5 space-y-2 max-h-48 overflow-y-auto">
              {timelineEvents.map(e => (
                <div key={e.id} className="flex items-start gap-2 text-xs">
                  <Clock className="w-3 h-3 text-[var(--text-tertiary)] mt-0.5" />
                  <div>
                    <p className="text-[var(--text-secondary)]">{e.title} — {e.description}</p>
                    <p className="text-[var(--text-tertiary)]">{new Date(e.timestamp).toLocaleString()} {e.user ? `by ${e.user}` : ''}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* MODIFY MODAL */}
      {showModifyModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-card p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Modify Order</h2>
              <button onClick={() => setShowModifyModal(false)} className="enterprise-btn enterprise-btn-icon enterprise-btn-ghost" aria-label="Close modify dialog"><XCircle className="w-5 h-5" /></button>
            </div>
            <div className="space-y-6">
              <div>
                <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3">Shipping Address</h3>
                <div className="grid grid-cols-2 gap-3">
                  <div className="col-span-2"><label className="enterprise-label">Street</label><input value={modifyForm.street} onChange={e => setModifyForm({ ...modifyForm, street: e.target.value })} className="enterprise-input w-full" /></div>
                  <div><label className="enterprise-label">City</label><input value={modifyForm.city} onChange={e => setModifyForm({ ...modifyForm, city: e.target.value })} className="enterprise-input w-full" /></div>
                  <div><label className="enterprise-label">State</label><input value={modifyForm.state} onChange={e => setModifyForm({ ...modifyForm, state: e.target.value })} className="enterprise-input w-full" /></div>
                  <div><label className="enterprise-label">ZIP</label><input value={modifyForm.zip} onChange={e => setModifyForm({ ...modifyForm, zip: e.target.value })} className="enterprise-input w-full" /></div>
                  <div><label className="enterprise-label">Country</label><input value={modifyForm.country} onChange={e => setModifyForm({ ...modifyForm, country: e.target.value })} className="enterprise-input w-full" /></div>
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between mb-3"><h3 className="text-sm font-semibold text-[var(--text-primary)]">Items</h3><button onClick={addModifyRow} className="enterprise-btn enterprise-btn-sm enterprise-btn-secondary">+ Add Item</button></div>
                <div className="space-y-2">
                  {modifyItems.map((item, idx) => (
                    <div key={idx} className="flex items-center gap-2 bg-[var(--bg-tertiary)] p-2 rounded-lg">
                      <input value={item.sku} onChange={e => updateModifyItem(idx, 'sku', e.target.value)} className="enterprise-input flex-[2] text-xs" placeholder="SKU" />
                      <input value={item.productName} onChange={e => updateModifyItem(idx, 'productName', e.target.value)} className="enterprise-input flex-[2] text-xs" placeholder="Product" />
                      <input type="number" value={item.quantity} onChange={e => updateModifyItem(idx, 'quantity', parseInt(e.target.value) || 1)} className="enterprise-input w-16 text-xs" min={1} />
                      <input type="number" value={item.unitPrice} onChange={e => updateModifyItem(idx, 'unitPrice', parseFloat(e.target.value) || 0)} className="enterprise-input w-20 text-xs" min={0} step={0.01} />
                      <button onClick={() => removeModifyItem(idx)} className="enterprise-btn enterprise-btn-icon enterprise-btn-ghost hover:text-[var(--nexus-error-500)]" aria-label="Remove item"><XCircle className="w-4 h-4" /></button>
                    </div>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6 pt-6 border-t border-[var(--border-subtle)]">
              <button onClick={() => setShowModifyModal(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="orders" action="edit">
                <button onClick={handleModify} disabled={actionLoading === 'modify'} className="enterprise-btn enterprise-btn-primary">
                  {actionLoading === 'modify' ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle className="w-4 h-4" />}
                  Save Changes
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {/* SPLIT MODAL */}
      {showSplitModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-card p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Split Order</h2>
              <button onClick={() => setShowSplitModal(false)} className="enterprise-btn enterprise-btn-icon enterprise-btn-ghost" aria-label="Close split dialog"><XCircle className="w-5 h-5" /></button>
            </div>
            <p className="text-sm text-[var(--text-secondary)] mb-4">Assign each item to a shipment group. Each group becomes a new order.</p>
            {order && <div className="flex flex-wrap gap-2 mb-4">
              {order.items.map(item => (
                <span key={item.id || item.sku} className="px-2 py-1 rounded text-xs bg-[var(--bg-tertiary)] text-[var(--text-secondary)]">
                  {item.productName} ({item.sku}) x{item.quantity}
                </span>
              ))}
            </div>}
            <div className="space-y-4">
              {splitGroups.map((group, gIdx) => (
                <div key={gIdx} className="border border-[var(--border-subtle)] rounded-lg p-4">
                  <div className="flex items-center justify-between mb-2">
                    <h4 className="text-sm font-medium text-[var(--text-primary)]">Group {gIdx + 1} ({group.itemIds.length} items)</h4>
                    {splitGroups.length > 1 && <button onClick={() => removeSplitGroup(gIdx)} className="enterprise-btn enterprise-btn-sm enterprise-btn-ghost text-[var(--nexus-error-500)]">Remove</button>}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {order?.items.map(item => {
                      const selected = group.itemIds.includes(item.id || item.sku)
                      const inOtherGroup = splitGroups.some((g, i) => i !== gIdx && g.itemIds.includes(item.id || item.sku))
                      return (
                        <button key={item.id || item.sku} onClick={() => { if (!inOtherGroup) toggleSplitItem(gIdx, item.id || item.sku) }}
                          className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                            selected ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]' :
                            inOtherGroup ? 'border-[var(--border-subtle)] text-[var(--text-tertiary)] cursor-not-allowed' :
                            'border-[var(--border-subtle)] text-[var(--text-secondary)] hover:border-[var(--text-tertiary)]'
                          }`}>
                          {item.productName}
                        </button>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>
            <button onClick={addSplitGroup} className="enterprise-btn enterprise-btn-secondary text-sm mt-4">+ Add Group</button>
            <div className="flex justify-end gap-3 mt-6 pt-6 border-t border-[var(--border-subtle)]">
              <button onClick={() => setShowSplitModal(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="orders" action="edit">
                <button onClick={handleSplit} disabled={actionLoading === 'split'} className="enterprise-btn enterprise-btn-primary">
                  {actionLoading === 'split' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Split className="w-4 h-4" />}
                  Split Order
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {/* MERGE MODAL */}
      {showMergeModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-card p-6 w-full max-w-lg">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Merge Orders</h2>
              <button onClick={() => setShowMergeModal(false)} className="enterprise-btn enterprise-btn-icon enterprise-btn-ghost" aria-label="Close merge dialog"><XCircle className="w-5 h-5" /></button>
            </div>
            <p className="text-sm text-[var(--text-secondary)] mb-4">Current: <strong>{order?.orderNumber}</strong></p>
            <div className="space-y-2">
              {mergeOrderIds.map((oid, idx) => (
                <div key={idx} className="flex items-center gap-2">
                  <input value={oid} onChange={e => updateMergeId(idx, e.target.value)} className="enterprise-input flex-1 text-sm font-mono" placeholder="Order UUID" />
                  {mergeOrderIds.length > 1 && <button onClick={() => removeMergeId(idx)} className="enterprise-btn enterprise-btn-icon enterprise-btn-ghost"><XCircle className="w-4 h-4" /></button>}
                </div>
              ))}
            </div>
            <button onClick={addMergeId} className="enterprise-btn enterprise-btn-sm enterprise-btn-secondary mt-2">+ Add Another</button>
            <hr className="border-[var(--border-subtle)] my-4" />
            <div>
              <label className="enterprise-label">Search Orders</label>
              <div className="flex gap-2 mt-1">
                <Autocomplete
                  value={mergeSearchTerm}
                  onChange={setMergeSearchTerm}
                  onSelect={(o: any) => { setMergeSearchTerm(o.orderNumber || o.id); searchOrders() }}
                  placeholder="Search by order # or customer..."
                  showSearchIcon={false}
                  inputClassName="enterprise-input flex-1 text-sm"
                  clearable={false}
                  minChars={2}
                />
                <button onClick={searchOrders} disabled={searchingMerge} className="enterprise-btn enterprise-btn-secondary text-sm">{searchingMerge ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Search'}</button>
              </div>
              {mergeResults.length > 0 && (
                <div className="mt-2 max-h-40 overflow-y-auto space-y-1">
                  {mergeResults.filter((o: any) => o.id !== id).map((o: any) => (
                    <button key={o.id} onClick={() => { const idx = mergeOrderIds.findIndex(v => !v); if (idx >= 0) updateMergeId(idx, o.id) }}
                      className="w-full text-left p-2 rounded-lg hover:bg-[var(--bg-tertiary)] text-sm flex justify-between">
                      <span className="font-medium">{o.orderNumber || o.id}</span>
                      <span className="text-[var(--text-tertiary)]">{o.customerName} - {o.status}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
            <div className="flex justify-end gap-3 mt-6 pt-6 border-t border-[var(--border-subtle)]">
              <button onClick={() => setShowMergeModal(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="orders" action="edit">
                <button onClick={handleMerge} disabled={actionLoading === 'merge'} className="enterprise-btn enterprise-btn-primary">
                  {actionLoading === 'merge' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Merge className="w-4 h-4" />}
                  Merge Orders
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
