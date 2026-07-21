import { useState, useEffect } from 'react'
import { clsx } from 'clsx'
import {
  Store, Package, RotateCcw, User, Loader2, ExternalLink,
  Truck, Clock, XCircle, Download, Plus, Eye,
  MapPin,
} from 'lucide-react'
import * as customersApi from '../api/customers'
import * as returnsApi from '../api/returns'
import * as ordersApi from '../api/orders'
import type { Customer, Return, Order } from '../types'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'
import PermissionGate from '../components/rbac/PermissionGate'

const STATUS_BADGES: Record<string, string> = {
  PENDING: 'enterprise-badge-warning',
  PROCESSING: 'enterprise-badge-info',
  SHIPPED: 'enterprise-badge-ai',
  DELIVERED: 'enterprise-badge-success',
  CANCELLED: 'enterprise-badge-neutral',
}

const RETURN_STATUS_BADGES: Record<string, string> = {
  REQUESTED: 'enterprise-badge-warning',
  APPROVED: 'enterprise-badge-info',
  RECEIVED: 'enterprise-badge-neutral',
  INSPECTED: 'enterprise-badge-ai',
  REFUNDED: 'enterprise-badge-success',
  REJECTED: 'enterprise-badge-error',
}

interface PortalOrder {
  id: string
  orderNumber: string
  status: string
  items: number
  total: number
  date: string
  tracking: string | null
  carrier: string | null
  eta: string | null
}

export default function B2BPortalPage() {
  const [customers, setCustomers] = useState<Customer[]>([])
  const [selectedCustomerId, setSelectedCustomerId] = useState<string | null>(null)
  const [returns, setReturns] = useState<Return[]>([])
  const [portalOrders, setPortalOrders] = useState<PortalOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [ordersLoading, setOrdersLoading] = useState(false)
  const [tab, setTab] = useState<'orders' | 'returns' | 'account'>('orders')
  const [returnRequestOpen, setReturnRequestOpen] = useState(false)
  const [returnForm, setReturnForm] = useState({ orderId: '', reason: '', items: '' })
  const [processing, setProcessing] = useState(false)
  const { addToast } = useToast()

  useEffect(() => {
    async function fetchCustomers() {
      try {
        const res = await customersApi.getCustomers()
        setCustomers(Array.isArray(res.data) ? res.data : [])
        if (Array.isArray(res.data) && res.data.length > 0) {
          setSelectedCustomerId(res.data[0].id)
        }
      } catch {
        addToast({ type: 'error', title: 'Failed to load customers' })
        setCustomers([])
      } finally {
        setLoading(false)
      }
    }
    fetchCustomers()
  }, [])

  useEffect(() => {
    if (!selectedCustomerId) return
    async function fetchReturns() {
      try {
        const res = await returnsApi.getReturns()
        const allReturns = res.data || []
        const customer = customers.find(c => c.id === selectedCustomerId)
        setReturns(allReturns.filter(r => r.customerId === selectedCustomerId || r.customerName === customer?.name))
      } catch {
        addToast({ type: 'error', title: 'Failed to load returns' })
        setReturns([])
      }
    }
    fetchReturns()
  }, [selectedCustomerId, customers])

  useEffect(() => {
    if (!selectedCustomerId) return
    const customer = customers.find(c => c.id === selectedCustomerId)
    if (!customer) return
    async function fetchOrders() {
      setOrdersLoading(true)
      try {
        const res = await ordersApi.getOrders({ search: customer.name })
        const orders: Order[] = Array.isArray(res.data) ? res.data : (res.data as any)?.content ?? []
        setPortalOrders(orders.map(o => ({
          id: o.id,
          orderNumber: o.orderNumber,
          status: o.status,
          items: o.items?.length ?? 0,
          total: o.total ?? 0,
          date: o.createdAt,
          tracking: o.trackingNumber ?? null,
          carrier: o.carrier ?? null,
          eta: o.promisedDeliveryDate ?? null,
        })))
      } catch {
        setPortalOrders([])
      } finally {
        setOrdersLoading(false)
      }
    }
    fetchOrders()
  }, [selectedCustomerId, customers])

  const selectedCustomer = customers.find(c => c.id === selectedCustomerId)

  const handleReturnRequest = async () => {
    if (!selectedCustomerId || !returnForm.orderId || !returnForm.reason) return
    setProcessing(true)
    try {
      await returnsApi.createReturn({
        orderId: returnForm.orderId,
        customerId: selectedCustomerId,
        reason: returnForm.reason,
        returnChannel: 'PORTAL',
        rmaType: 'RETURN',
        items: returnForm.items.split(',').map(s => s.trim()).filter(Boolean).map(sku => ({ sku, productName: '', quantity: 1 })),
      })
      addToast({ type: 'success', title: 'Return request submitted' })
      setReturnRequestOpen(false)
      setReturnForm({ orderId: '', reason: '', items: '' })
    } catch {
      addToast({ type: 'error', title: 'Failed to submit return request' })
    } finally {
      setProcessing(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="w-8 h-8 animate-spin text-[var(--text-brand)]" />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="enterprise-page-header">
        <div className="flex items-center gap-4">
          <div>
            <h1 className="flex items-center gap-2.5"><Store className="w-6 h-6 text-[var(--text-brand)]" />B2B Customer Portal</h1>
            <p>Self-service order management and returns</p>
          </div>
        </div>
        <Autocomplete
          value={selectedCustomerId || ''}
          onChange={val => setSelectedCustomerId(val || null)}
          minChars={0}
          suggestions={customers.map(c => c.id)}
          className="w-64"
        />
      </div>

      {/* Customer Profile Card */}
      {selectedCustomer && (
        <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6">
          <div className="flex items-start gap-5">
            <div className="enterprise-avatar w-14 h-14 text-xl">
              {selectedCustomer.name?.charAt(0) || 'C'}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-start justify-between">
                <div>
                  <h2 className="text-xl font-bold text-[var(--text-primary)]">{selectedCustomer.name}</h2>
                  <p className="text-sm text-[var(--text-secondary)]">{selectedCustomer.email}</p>
                </div>
                <div className="flex items-center gap-2">
                  <span className="enterprise-badge enterprise-badge-success">Active</span>
                  <span className="enterprise-badge enterprise-badge-info">B2B Tier 2</span>
                </div>
              </div>
              <div className="grid grid-cols-3 gap-6 mt-4">
                {[
                  { label: 'Total Orders', value: '12' },
                  { label: 'Open Returns', value: returns.filter(r => !['REFUNDED', 'REJECTED', 'CANCELLED'].includes(r.status)).length },
                  { label: 'Member Since', value: 'Jan 2026' },
                ].map(stat => (
                  <div key={stat.label} className="text-sm">
                    <span className="text-[var(--text-secondary)]">{stat.label}: </span>
                    <span className="font-semibold text-[var(--text-primary)]">{stat.value}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tabs */}
      <div className="enterprise-tabs">
        {([
          { key: 'orders' as const, label: 'Order History', icon: Package },
          { key: 'returns' as const, label: 'Returns', icon: RotateCcw },
          { key: 'account' as const, label: 'Account', icon: User },
        ]).map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={clsx('enterprise-tab', tab === t.key && 'enterprise-tab-active')}
          >
            <t.icon className="w-4 h-4" /> {t.label}
          </button>
        ))}
      </div>

      {/* Orders Tab */}
      {tab === 'orders' && (
        <div className="space-y-3">
          <h3 className="text-lg font-semibold text-[var(--text-primary)]">Recent Orders</h3>
          {ordersLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="w-6 h-6 animate-spin text-[var(--text-brand)]" />
            </div>
          ) : portalOrders.length === 0 ? (
            <div className="enterprise-empty-state py-12">
              <Package className="w-8 h-8 mx-auto text-[var(--text-tertiary)]" />
              <h3>No orders found</h3>
              <p>This customer has no orders yet</p>
            </div>
          ) : portalOrders.map(order => (
            <div key={order.id} className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5 transition-shadow hover:shadow-sm">
              <div className="flex items-start justify-between">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-3">
                    <span className="font-semibold text-[var(--text-primary)]">{order.orderNumber}</span>
                    <span className={clsx('enterprise-badge', STATUS_BADGES[order.status])}>{order.status}</span>
                  </div>
                  <div className="flex items-center gap-5 mt-2 text-sm text-[var(--text-secondary)] flex-wrap">
                    <span>{new Date(order.date).toLocaleDateString()}</span>
                    <span>{order.items} item{order.items > 1 ? 's' : ''}</span>
                    <span className="font-semibold text-[var(--text-primary)]">${order.total.toFixed(2)}</span>
                    {order.tracking && (
                      <span className="inline-flex items-center gap-1.5 text-[var(--text-brand)]">
                        <Truck className="w-3.5 h-3.5" />
                        {order.carrier}: <span className="font-mono text-xs">{order.tracking}</span>
                      </span>
                    )}
                    {order.eta && (
                      <span className="inline-flex items-center gap-1.5">
                        <Clock className="w-3.5 h-3.5 text-[var(--nexus-warning-500)]" />
                        Est. {new Date(order.eta).toLocaleDateString()}
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-2 ml-4">
                  <button
                    onClick={() => window.open(`/orders/${order.id}`, '_blank')}
                    className="enterprise-btn enterprise-btn-sm enterprise-btn-secondary"
                  >
                    <Eye className="w-3.5 h-3.5" /> View
                  </button>
                  {order.tracking && (
                    <button
                      onClick={() => window.open(`https://www.google.com/search?q=${encodeURIComponent(order.tracking!)}`, '_blank')}
                      className="enterprise-btn enterprise-btn-sm bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)] border border-[var(--nexus-primary-200)] dark:bg-[var(--nexus-primary-900)]/20 dark:text-[var(--nexus-primary-300)] dark:border-[var(--nexus-primary-800)] hover:bg-[var(--nexus-primary-100)]"
                    >
                      <ExternalLink className="w-3.5 h-3.5" /> Track
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Returns Tab */}
      {tab === 'returns' && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-semibold text-[var(--text-primary)]">My Returns</h3>
            <PermissionGate resource="integrations" action="create">
              <button
                onClick={() => setReturnRequestOpen(true)}
                className="enterprise-btn enterprise-btn-primary enterprise-btn-sm"
              >
                <Plus className="w-4 h-4" /> Request Return
              </button>
            </PermissionGate>
          </div>
          {returns.length === 0 ? (
            <div className="enterprise-empty-state py-16">
              <RotateCcw />
              <h3>No returns yet</h3>
              <p>Request a return for any recent order</p>
            </div>
          ) : (
            returns.map(ret => (
              <div key={ret.id} className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
                <div className="flex items-start justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-3">
                      <span className="font-semibold text-[var(--text-primary)]">{ret.rmaNumber}</span>
                      <span className={clsx('enterprise-badge', RETURN_STATUS_BADGES[ret.status] || 'enterprise-badge-neutral')}>{ret.status}</span>
                    </div>
                    <div className="flex items-center gap-4 mt-1.5 text-sm text-[var(--text-secondary)] flex-wrap">
                      <span>Order: <span className="font-mono text-[var(--text-secondary)]">{ret.orderId?.slice(0, 8)}</span></span>
                      <span>Reason: <span className="font-medium text-[var(--text-secondary)]">{ret.reason}</span></span>
                      {ret.refundAmount > 0 && <span className="font-medium text-emerald-600 dark:text-emerald-400">${ret.refundAmount.toFixed(2)} refunded</span>}
                      <span>{new Date(ret.createdAt).toLocaleDateString()}</span>
                    </div>
                    {ret.items && ret.items.length > 0 && (
                      <div className="flex items-center gap-2 mt-2">
                        {ret.items.map((item, i) => (
                          <span key={i} className="enterprise-tag">{item.sku}{item.quantity > 1 ? ` x${item.quantity}` : ''}</span>
                        ))}
                      </div>
                    )}
                  </div>
                  <div className="flex items-center gap-2 ml-4">
                    {ret.returnLabelUrl && (
                      <button
                        onClick={() => window.open(ret.returnLabelUrl!, '_blank')}
                        className="enterprise-btn enterprise-btn-sm enterprise-btn-ghost text-[var(--text-brand)]"
                      >
                        <Download className="w-3.5 h-3.5" /> Label
                      </button>
                    )}
                    <span className={clsx('text-xs font-medium px-2.5 py-1 rounded-lg',
                      ret.status === 'REQUESTED' && 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-600)] dark:bg-[var(--nexus-warning-900)]/20 dark:text-[var(--nexus-warning-400)]',
                      ret.status === 'REFUNDED' && 'bg-emerald-50 text-emerald-600 dark:bg-emerald-900/20 dark:text-emerald-400',
                      ret.status === 'REJECTED' && 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)] dark:bg-[var(--nexus-error-900)]/20 dark:text-[var(--nexus-error-400)]',
                      (ret.status === 'APPROVED' || ret.status === 'RECEIVED' || ret.status === 'INSPECTED') && 'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-600)] dark:bg-[var(--nexus-primary-900)]/20 dark:text-[var(--nexus-primary-400)]',
                    )}>
                      {ret.status === 'REQUESTED' && 'Awaiting approval'}
                      {ret.status === 'REFUNDED' && 'Completed'}
                      {ret.status === 'REJECTED' && 'Rejected'}
                      {['APPROVED', 'RECEIVED', 'INSPECTED'].includes(ret.status) && 'Processing'}
                    </span>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* Account Tab */}
      {tab === 'account' && selectedCustomer && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6">
            <h3 className="text-lg font-semibold text-[var(--text-primary)] mb-5">Profile</h3>
            <dl className="space-y-0.5">
              {[
                { label: 'Name', value: selectedCustomer.name },
                { label: 'Email', value: selectedCustomer.email },
                { label: 'Account ID', value: selectedCustomer.id?.slice(0, 8) },
                { label: 'Tier', value: 'B2B Tier 2 - Wholesale' },
                { label: 'Terms', value: 'Net 30' },
                { label: 'Credit Limit', value: '$25,000.00' },
                { label: 'Tax ID', value: 'XX-XXXXXXX' },
              ].map(({ label, value }) => (
                <div key={label} className="flex justify-between py-2.5 px-3 -mx-3 rounded-lg hover:bg-[var(--surface-sunken)] dark:hover:bg-[var(--surface-muted)]/30">
                  <dt className="text-sm text-[var(--text-secondary)]">{label}</dt>
                  <dd className="text-sm font-medium text-[var(--text-primary)]">{value}</dd>
                </div>
              ))}
            </dl>
          </div>
          <div className="space-y-6">
            <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6">
              <h3 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Shipping Addresses</h3>
              <div className="bg-[var(--surface-muted)] rounded-xl p-4">
                <div className="flex items-start gap-3">
                  <MapPin className="w-4 h-4 text-[var(--text-tertiary)] mt-0.5" />
                  <div>
                    <p className="font-medium text-[var(--text-primary)]">{selectedCustomer.name}</p>
                    <p className="text-sm text-[var(--text-secondary)] mt-1">
                      123 Commerce Street<br />Suite 400<br />San Francisco, CA 94105<br />United States
                    </p>
                  </div>
                </div>
              </div>
            </div>
            <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6">
              <h3 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Pricing</h3>
              <div className="space-y-3 bg-[var(--surface-muted)] rounded-xl p-4">
                {[
                  { label: 'Price List', value: 'Wholesale 2026' },
                  { label: 'Discount', value: '15% off MSRP', valueClass: 'text-emerald-600 dark:text-emerald-400' },
                  { label: 'Shipping', value: 'Free over $500' },
                ].map(({ label, value, valueClass }) => (
                  <div key={label} className="flex justify-between">
                    <span className="text-sm text-[var(--text-secondary)]">{label}</span>
                    <span className={clsx('text-sm font-medium text-[var(--text-primary)]', valueClass)}>{value}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Return Request Modal */}
      {returnRequestOpen && (
        <div className="enterprise-modal-overlay" onClick={() => setReturnRequestOpen(false)}>
          <div className="enterprise-modal max-w-md" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-lg font-bold text-[var(--text-primary)]">Request Return</h2>
              <button onClick={() => setReturnRequestOpen(false)} className="p-1 text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] rounded-lg hover:bg-[var(--surface-muted)]"><XCircle className="w-5 h-5" /></button>
            </div>
            <div className="enterprise-modal-body space-y-5">
              <div className="enterprise-form-group">
                <label>Order Number</label>
                <Autocomplete value={returnForm.orderId} onChange={val => setReturnForm(f => ({ ...f, orderId: val }))} suggestions={portalOrders.map(o => o.id)} minChars={0} />
              </div>
              <div className="enterprise-form-group">
                <label>Items (SKUs, comma separated)</label>
                <Autocomplete value={returnForm.items} onChange={val => setReturnForm(f => ({ ...f, items: val }))} placeholder="SKU-001, SKU-002" minChars={0} inputClassName="enterprise-input" />
              </div>
              <div className="enterprise-form-group">
                <label>Reason for Return</label>
                <Autocomplete value={returnForm.reason} onChange={val => setReturnForm(f => ({ ...f, reason: val }))} suggestions={['DEFECTIVE', 'WRONG_ITEM', 'DAMAGED', 'NOT_AS_DESC', 'CHANGED_MIND', 'DUPLICATE', 'OTHER']} minChars={0} />
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button onClick={() => setReturnRequestOpen(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="integrations" action="create">
                <button onClick={handleReturnRequest} disabled={processing || !returnForm.orderId || !returnForm.reason} className="enterprise-btn enterprise-btn-primary disabled:opacity-50">
                  {processing ? <Loader2 className="w-4 h-4 animate-spin" /> : <RotateCcw className="w-4 h-4" />}
                  Submit Request
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
