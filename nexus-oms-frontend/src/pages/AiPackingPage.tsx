import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Package, Box, Search, CheckCircle, AlertTriangle,
  Layers, Maximize, Minimize, Zap,
  ThumbsUp, Info, Clock,
} from 'lucide-react'
import clsx from 'clsx'
import { getPackagingPlan } from '../api/aiAgents'
import { getOrders } from '../api/orders'
import type { Order } from '../types'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import { useToast } from '../hooks/useToast'

interface SimpleOrderItem {
  sku: string
  name: string
  qty: number
}

interface SimpleOrder {
  id: string
  items: SimpleOrderItem[]
}

interface PackagingPlan {
  boxType?: string
  dimensions?: string
  weight?: number
  fillRate?: number
  materials?: string[]
  confidence?: number
}

const fromApiOrder = (o: Order): SimpleOrder => ({
  id: o.id || o.orderNumber || '',
  items: o.items.map((i: any) => ({
    sku: i.sku ?? i.productSku ?? '',
    name: i.name ?? i.productName ?? i.description ?? '',
    qty: i.quantity ?? i.qty ?? 1,
  })),
})

const BOX_ICONS: Record<string, React.ReactNode> = {
  large: <Maximize className="w-4 h-4" />,
  medium: <Box className="w-4 h-4" />,
  small: <Minimize className="w-4 h-4" />,
  custom: <Package className="w-4 h-4" />,
}

export default function AiPackingPage() {
  const [search, setSearch] = useState('')
  const [selectedOrder, setSelectedOrder] = useState<SimpleOrder | null>(null)
  const [plan, setPlan] = useState<PackagingPlan | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { addToast } = useToast()

  const { data: ordersData } = useQuery({
    queryKey: ['packing-orders'],
    queryFn: async () => {
      const res = await getOrders()
      return (res?.data ?? []).map(fromApiOrder)
    },
  })

  const orders = ordersData ?? []

  const orderSuggestions = useMemo(() => {
    if (!search.trim()) return []
    return orders.filter(o =>
      o.id.toLowerCase().includes(search.toLowerCase())
    )
  }, [search, orders])

  const selectOrder = (order: SimpleOrder) => {
    setSelectedOrder(order)
    setPlan(null)
    setError('')
  }

  const handleAnalyze = async () => {
    if (!selectedOrder) return
    setLoading(true)
    setError('')
    setPlan(null)
    try {
      const res = await getPackagingPlan(selectedOrder.id)
      if (res?.success && res.data) {
        setPlan(res.data as PackagingPlan)
      } else {
        setError('No AI packaging plan available for this order yet')
      }
    } catch {
      setError('Failed to generate packaging plan. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleAccept = () => {
    if (!selectedOrder) return
    addToast({ type: 'success', title: `Plan accepted for ${selectedOrder.id}` })
    setPlan(null)
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Package className="w-7 h-7 text-[var(--nexus-success-500)]" />
            AI Packing Intelligence
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Optimized packaging recommendations</p>
        </div>
        {plan && plan.confidence != null && (
          <div className="flex items-center gap-2 bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 text-[var(--nexus-success-700)] dark:text-[var(--nexus-success-300)] text-xs font-semibold px-3 py-1.5 rounded-full">
            <ShieldIcon />
            AI Confidence: {Math.round(plan.confidence * 100)}%
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-4 gap-6">
        {/* Left Panel */}
        <div className="xl:col-span-1 space-y-4">
          <div className="enterprise-card p-4">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
              <Search className="w-4 h-4 text-[var(--nexus-success-500)]" /> Order Scan / Select
            </h3>
            <Autocomplete
              value={search}
              onChange={setSearch}
              onSelect={(o: any) => { setSearch(o.id); selectOrder(orders.find(x => x.id === o.id) || o) }}
              placeholder="Search order number..."
              showSearchIcon={false}
              inputClassName="w-full px-3 py-2 text-sm border border-[var(--border-default)] rounded-lg bg-[var(--surface-base)] text-[var(--text-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--nexus-success-500)] focus:border-[var(--nexus-success-500)]"
              clearable={false}
            />
            {orderSuggestions.length > 0 && (
              <div className="mt-2 space-y-1 max-h-60 overflow-y-auto">
                {orderSuggestions.map(order => (
                  <button
                    key={order.id}
                    onClick={() => selectOrder(order)}
                    className={clsx(
                      'w-full text-left p-3 rounded-lg transition-colors text-sm',
                      selectedOrder?.id === order.id
                        ? 'bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 border border-[var(--nexus-success-200)] dark:border-[var(--nexus-success-800)]'
                        : 'bg-[var(--surface-sunken)]/50 hover:bg-[var(--surface-muted)] border border-transparent'
                    )}
                  >
                    <p className="font-medium text-[var(--text-primary)]">{order.id}</p>
                    <p className="text-xs text-[var(--text-secondary)] mt-0.5">{order.items.length} items</p>
                  </button>
                ))}
              </div>
            )}
            {!search.trim() && orders.length > 0 && (
              <div className="mt-2 space-y-1">
                {orders.slice(0, 5).map(order => (
                  <button
                    key={order.id}
                    onClick={() => selectOrder(order)}
                    className={clsx(
                      'w-full text-left p-3 rounded-lg transition-colors text-sm',
                      selectedOrder?.id === order.id
                        ? 'bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 border border-[var(--nexus-success-200)] dark:border-[var(--nexus-success-800)]'
                        : 'bg-[var(--surface-sunken)]/50 hover:bg-[var(--surface-muted)] border border-transparent'
                    )}
                  >
                    <p className="font-medium text-[var(--text-primary)]">{order.id}</p>
                    <p className="text-xs text-[var(--text-secondary)] mt-0.5">{order.items.length} items</p>
                  </button>
                ))}
              </div>
            )}
          </div>

          {selectedOrder && (
            <div className="enterprise-card p-4">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2">
                  <Layers className="w-4 h-4 text-[var(--nexus-success-500)]" /> Items
                </h3>
                <span className="text-xs text-[var(--text-tertiary)]">{selectedOrder.items.length} total</span>
              </div>
              <div className="space-y-2">
                {selectedOrder.items.map((item, i) => (
                  <div key={i} className="flex items-center justify-between p-2 rounded bg-[var(--surface-sunken)]/50">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-[var(--text-primary)] truncate">{item.name}</p>
                      <p className="text-xs text-[var(--text-secondary)]">{item.sku}</p>
                    </div>
                    <span className="text-xs font-semibold text-[var(--text-secondary)] ml-2">x{item.qty}</span>
                  </div>
                ))}
              </div>
              <PermissionGate resource="settings" action="create">
                <button
                  onClick={handleAnalyze}
                  disabled={loading}
                  className="enterprise-btn-primary w-full mt-4 bg-[var(--nexus-success-600)] hover:bg-[var(--nexus-success-700)] text-sm flex items-center justify-center gap-2"
                >
                  {loading ? (
                    <>
                      <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      Analyzing...
                    </>
                  ) : (
                    <>
                      <Zap className="w-4 h-4" /> Analyze
                    </>
                  )}
                </button>
              </PermissionGate>
            </div>
          )}

          {error && (
            <div className="enterprise-card p-4 border-[var(--nexus-error-200)] dark:border-[var(--nexus-error-800)]">
              <div className="flex items-center gap-2 text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)] text-sm">
                <AlertTriangle className="w-4 h-4" /> {error}
              </div>
            </div>
          )}

          {loading && !plan && (
            <div className="enterprise-card p-8">
              <div className="flex flex-col items-center gap-3 text-[var(--text-tertiary)]">
                <div className="w-8 h-8 border-3 border-[var(--nexus-success-500)] border-t-transparent rounded-full animate-spin" />
                <p className="text-sm">Generating optimal packaging plan...</p>
              </div>
            </div>
          )}
        </div>

        {/* Right Panel */}
        <div className="xl:col-span-2 space-y-4">
          {!plan && !loading && (
            <div className="enterprise-card p-12">
              <div className="flex flex-col items-center gap-3 text-[var(--text-tertiary)]">
                <Package className="w-12 h-12 text-[var(--text-tertiary)]" />
                <p className="text-sm font-medium text-[var(--text-secondary)]">Select an order and click "Analyze"</p>
                <p className="text-xs text-[var(--text-tertiary)]">AI will recommend the optimal packaging plan</p>
              </div>
            </div>
          )}

          {plan && (
            <>
              <div className="enterprise-card p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2">
                    <Box className="w-4 h-4 text-[var(--nexus-success-500)]" /> Recommended Box
                  </h3>
                  <span className="text-xs text-[var(--text-tertiary)]">{selectedOrder?.id}</span>
                </div>

                <div className="flex items-start gap-4 p-4 bg-[var(--surface-sunken)]/50 rounded-lg">
                  <div className="w-11 h-11 rounded-xl flex items-center justify-center bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 text-[var(--nexus-success-600)] shrink-0">
                    {BOX_ICONS[(plan.boxType || '').toLowerCase()] || <Box className="w-5 h-5" />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2 flex-wrap">
                      <p className="text-sm font-semibold text-[var(--text-primary)]">{plan.boxType || '—'}</p>
                      {plan.weight != null && (
                        <p className="text-sm font-semibold text-[var(--text-secondary)]">{plan.weight} kg</p>
                      )}
                    </div>
                    {plan.dimensions && (
                      <p className="text-xs text-[var(--text-secondary)] mt-0.5">{plan.dimensions}</p>
                    )}
                    {plan.fillRate != null && (
                      <div className="mt-3">
                        <div className="flex items-center justify-between text-xs text-[var(--text-secondary)] mb-1">
                          <span>Fill rate</span>
                          <span>{Math.round(plan.fillRate * 100)}%</span>
                        </div>
                        <div className="w-full bg-[var(--surface-muted)] rounded-full h-2">
                          <div className="bg-[var(--nexus-success-500)] h-2 rounded-full"
                               style={{ width: `${Math.min(Math.round(plan.fillRate * 100), 100)}%` }} />
                        </div>
                      </div>
                    )}
                    {plan.materials && plan.materials.length > 0 && (
                      <div className="mt-3">
                        <p className="text-xs text-[var(--text-secondary)] mb-1.5">Materials</p>
                        <div className="flex flex-wrap gap-1.5">
                          {plan.materials.map(m => (
                            <span key={m} className="text-xs px-2.5 py-1 rounded-full bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 text-[var(--nexus-success-700)] dark:text-[var(--nexus-success-300)] font-medium">
                              {m}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <PermissionGate resource="settings" action="edit">
                  <button type="button" onClick={handleAccept} className="enterprise-btn-primary bg-[var(--nexus-success-600)] hover:bg-[var(--nexus-success-700)] text-sm flex items-center gap-1.5 px-5 py-2.5">
                    <ThumbsUp className="w-4 h-4" /> Accept Plan
                  </button>
                </PermissionGate>
              </div>
            </>
          )}
        </div>

        {/* Right Sidebar - AI Insights */}
        <div className="xl:col-span-1 space-y-4">
          <div className="enterprise-card p-4">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
              <Zap className="w-4 h-4 text-[var(--nexus-success-500)]" /> Plan Summary
            </h3>
            {plan ? (
              <div className="space-y-3">
                {plan.boxType && (
                  <div className="p-3 rounded-lg bg-[var(--surface-sunken)]/50">
                    <p className="text-xs font-semibold text-[var(--text-secondary)]">Box type</p>
                    <p className="text-sm text-[var(--text-primary)] mt-0.5">{plan.boxType}</p>
                  </div>
                )}
                {plan.weight != null && (
                  <div className="p-3 rounded-lg bg-[var(--surface-sunken)]/50">
                    <p className="text-xs font-semibold text-[var(--text-secondary)]">Est. weight</p>
                    <p className="text-sm text-[var(--text-primary)] mt-0.5">{plan.weight} kg</p>
                  </div>
                )}
                {plan.fillRate != null && (
                  <div className="p-3 rounded-lg bg-[var(--surface-sunken)]/50">
                    <p className="text-xs font-semibold text-[var(--text-secondary)]">Fill rate</p>
                    <p className="text-sm text-[var(--text-primary)] mt-0.5">{Math.round(plan.fillRate * 100)}%</p>
                  </div>
                )}
                {plan.confidence != null && (
                  <div className="p-3 rounded-lg bg-[var(--surface-sunken)]/50">
                    <p className="text-xs font-semibold text-[var(--text-secondary)]">Confidence</p>
                    <p className="text-sm text-[var(--text-primary)] mt-0.5">{Math.round(plan.confidence * 100)}%</p>
                  </div>
                )}
              </div>
            ) : (
              <div className="text-center py-6 text-[var(--text-tertiary)]">
                <Info className="w-8 h-8 mx-auto mb-2" />
                <p className="text-xs">Plan summary will appear after analysis</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Bottom Section - Recent Packs */}
      <div className="enterprise-card p-5">
        <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
          <Clock className="w-4 h-4 text-[var(--text-tertiary)]" /> Recent AI-Packed Orders
        </h3>
        <div className="py-10 text-center text-sm text-[var(--text-tertiary)]">
          <CheckCircle className="w-8 h-8 mx-auto mb-2 text-[var(--text-tertiary)]" />
          No AI-packed orders recorded yet. Accepted plans will appear here.
        </div>
      </div>
    </div>
  )
}

function ShieldIcon() {
  return <span className="w-3.5 h-3.5 inline-flex items-center justify-center">✓</span>
}
