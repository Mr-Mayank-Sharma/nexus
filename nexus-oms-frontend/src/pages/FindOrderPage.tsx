import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, Package, ArrowRight, Loader2, AlertCircle, Clock, CheckCircle, Truck, XCircle } from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import { getOrderById } from '../api/orders'
import { Order } from '../types'
import clsx from 'clsx'

const STATUS_STYLES: Record<string, { bg: string; text: string; icon: typeof Package }> = {
  PENDING: { bg: 'bg-amber-100 dark:bg-amber-900/30', text: 'text-amber-700 dark:text-amber-400', icon: Clock },
  CONFIRMED: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-400', icon: CheckCircle },
  ALLOCATED: { bg: 'bg-indigo-100 dark:bg-indigo-900/30', text: 'text-indigo-700 dark:text-indigo-400', icon: Package },
  SHIPPED: { bg: 'bg-purple-100 dark:bg-purple-900/30', text: 'text-purple-700 dark:text-purple-400', icon: Truck },
  DELIVERED: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-400', icon: CheckCircle },
  EXCEPTION: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-400', icon: AlertCircle },
  CANCELLED: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-600 dark:text-gray-400', icon: XCircle },
}

function StatusBadge({ status }: { status: string }) {
  const style = STATUS_STYLES[status] || STATUS_STYLES.PENDING
  const Icon = style.icon
  return (
    <span className={clsx('inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium', style.bg, style.text)}>
      <Icon className="w-3.5 h-3.5" />
      {status}
    </span>
  )
}

export default function FindOrderPage() {
  const navigate = useNavigate()
  const [orderId, setOrderId] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<Order | null>(null)
  const [recentSearches, setRecentSearches] = useState<string[]>([])

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    const id = orderId.trim()
    if (!id) return

    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const response = await getOrderById(id)
      if (response.success && response.data) {
        setResult(response.data)
        setRecentSearches(prev => {
          const next = [id, ...prev.filter(s => s !== id)].slice(0, 8)
          return next
        })
      } else {
        setError(response.error || `Order "${id}" not found`)
      }
    } catch {
      setError('Failed to fetch order. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs
        items={[
          { label: 'Home', path: '/' },
          { label: 'Orders', path: '/orders' },
          { label: 'Find Order' },
        ]}
      />

      {/* Search Card */}
      <div className="bg-[var(--surface-base)] border border-[var(--border-default)] rounded-xl p-6 shadow-sm">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-xl bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)/30] flex items-center justify-center">
            <Search className="w-5 h-5 text-[var(--nexus-primary-600)]" />
          </div>
          <div>
            <h1 className="text-lg font-semibold text-[var(--text-primary)]">Find Order</h1>
            <p className="text-sm text-[var(--text-secondary)]">Search by order ID to view details</p>
          </div>
        </div>

        <form onSubmit={handleSearch} className="flex gap-3">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
            <input
              type="text"
              value={orderId}
              onChange={e => setOrderId(e.target.value)}
              placeholder="Enter order ID (e.g. ORD-001, 12345)"
              className="w-full h-11 pl-10 pr-4 rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] text-[var(--text-primary)] placeholder-[var(--text-tertiary)] focus:outline-none focus:ring-2 focus:ring-[var(--nexus-primary-500)] focus:border-transparent transition-all text-sm"
              autoFocus
            />
          </div>
          <button
            type="submit"
            disabled={!orderId.trim() || loading}
            className="enterprise-btn enterprise-btn-primary h-11 px-6"
          >
            {loading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <>
                <Search className="w-4 h-4" />
                Search
              </>
            )}
          </button>
        </form>

        {/* Recent Searches */}
        {recentSearches.length > 0 && (
          <div className="mt-4 flex flex-wrap gap-2">
            <span className="text-xs text-[var(--text-tertiary)]">Recent:</span>
            {recentSearches.map(id => (
              <button
                key={id}
                onClick={() => { setOrderId(id); }}
                className="text-xs px-2.5 py-1 rounded-md bg-[var(--surface-sunken)] text-[var(--text-secondary)] hover:bg-[var(--interactive-hover)] transition-colors"
              >
                {id}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Error */}
      {error && (
        <div className="flex items-center gap-3 p-4 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <AlertCircle className="w-5 h-5 text-red-500 shrink-0" />
          <p className="text-sm text-red-700 dark:text-red-400">{error}</p>
        </div>
      )}

      {/* Result */}
      {result && (
        <div className="bg-[var(--surface-base)] border border-[var(--border-default)] rounded-xl overflow-hidden shadow-sm">
          <div className="p-6 border-b border-[var(--border-subtle)]">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)/30] flex items-center justify-center">
                  <Package className="w-6 h-6 text-[var(--nexus-primary-600)]" />
                </div>
                <div>
                  <h2 className="text-xl font-bold text-[var(--text-primary)]">
                    {result.orderNumber || result.id}
                  </h2>
                  <p className="text-sm text-[var(--text-secondary)]">
                    {result.channel || 'N/A'} channel &middot; {result.createdAt ? new Date(result.createdAt).toLocaleDateString() : 'N/A'}
                  </p>
                </div>
              </div>
              <StatusBadge status={result.status || 'PENDING'} />
            </div>
          </div>

          <div className="p-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            <InfoGroup label="Customer" value={result.customerName || 'N/A'} sub={result.customerEmail || ''} />
            <InfoGroup label="Shipping Address" value={
              result.shippingAddress
                ? `${result.shippingAddress.street || ''}, ${result.shippingAddress.city || ''} ${result.shippingAddress.state || ''} ${result.shippingAddress.zip || ''}`
                : 'N/A'
            } />
            <InfoGroup label="Total" value={result.totalAmount != null ? `$${result.totalAmount.toFixed(2)}` : 'N/A'} />
            <InfoGroup label="Items" value={result.items?.length ? `${result.items.length} item(s)` : 'N/A'} />
            <InfoGroup label="Tracking" value={result.trackingNumber || 'Not yet shipped'} />
            <InfoGroup label="Priority" value={result.priority || 'Standard'} />
          </div>

          {/* Items Table */}
          {result.items && result.items.length > 0 && (
            <div className="px-6 pb-6">
              <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-3">Order Items</h3>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-[var(--border-subtle)]">
                      <th className="text-left py-2 text-[var(--text-tertiary)] font-medium">SKU</th>
                      <th className="text-left py-2 text-[var(--text-tertiary)] font-medium">Product</th>
                      <th className="text-right py-2 text-[var(--text-tertiary)] font-medium">Qty</th>
                      <th className="text-right py-2 text-[var(--text-tertiary)] font-medium">Price</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.items.map((item, idx) => (
                      <tr key={idx} className="border-b border-[var(--border-subtle)] last:border-0">
                        <td className="py-2.5 font-mono text-xs text-[var(--text-secondary)]">{item.sku || '—'}</td>
                        <td className="py-2.5 text-[var(--text-primary)]">{item.productName || '—'}</td>
                        <td className="py-2.5 text-right text-[var(--text-secondary)]">{item.quantity ?? '—'}</td>
                        <td className="py-2.5 text-right text-[var(--text-primary)]">
                          {item.unitPrice != null ? `$${item.unitPrice.toFixed(2)}` : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Actions */}
          <div className="px-6 pb-6 flex gap-3">
            <button
              onClick={() => navigate(`/orders/${result.id}`)}
              className="enterprise-btn enterprise-btn-primary"
            >
              View Full Details
              <ArrowRight className="w-4 h-4" />
            </button>
            <button
              onClick={() => navigate('/orders')}
              className="enterprise-btn enterprise-btn-secondary"
            >
              Back to Orders
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function InfoGroup({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-[var(--text-tertiary)] uppercase tracking-wider mb-1">{label}</dt>
      <dd className="text-sm font-medium text-[var(--text-primary)]">{value}</dd>
      {sub && <dd className="text-xs text-[var(--text-secondary)] mt-0.5">{sub}</dd>}
    </div>
  )
}
