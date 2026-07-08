import { useState, useMemo } from 'react'
import {
  Package, Box, Search, CheckCircle, AlertTriangle, Clock,
  Layers, Maximize, Minimize, RotateCcw, Shield,
  ThumbsUp, Edit3, X, ChevronDown, ChevronUp,
  TrendingUp, TrendingDown, Info, Zap,
} from 'lucide-react'
import clsx from 'clsx'
import { getPackagingPlan } from '../api/aiAgents'
import type { AiPackagingPlan, AiBox } from '../api/aiAgents'
import Autocomplete from '../components/common/Autocomplete'

interface MockOrder {
  id: string
  items: { sku: string; name: string; qty: number }[]
}

const MOCK_ORDERS: MockOrder[] = [
  {
    id: 'ORD-10230',
    items: [
      { sku: 'LAP-001', name: 'Laptop Pro 15"', qty: 2 },
      { sku: 'KB-001', name: 'Wireless Keyboard', qty: 1 },
      { sku: 'MOU-001', name: 'Wireless Mouse', qty: 3 },
    ],
  },
  {
    id: 'ORD-10231',
    items: [
      { sku: 'MON-001', name: '27" Monitor', qty: 2 },
      { sku: 'LAP-002', name: 'Laptop Air 13"', qty: 1 },
      { sku: 'TAB-001', name: 'Tablet 11"', qty: 1 },
      { sku: 'KB-002', name: 'Mechanical Keyboard', qty: 1 },
      { sku: 'MOU-002', name: 'Ergonomic Mouse', qty: 2 },
    ],
  },
  {
    id: 'ORD-10232',
    items: [
      { sku: 'HD-001', name: 'External SSD 1TB', qty: 1 },
      { sku: 'USB-001', name: 'USB-C Hub', qty: 1 },
    ],
  },
]

const RECENT_PACKS = [
  { order: 'ORD-10228', date: '10 min ago', boxes: 2, weight: '8.2 kg', status: 'accepted' as const },
  { order: 'ORD-10229', date: '22 min ago', boxes: 1, weight: '3.5 kg', status: 'accepted' as const },
  { order: 'ORD-10225', date: '1h ago', boxes: 3, weight: '14.1 kg', status: 'overridden' as const },
  { order: 'ORD-10220', date: '2h ago', boxes: 2, weight: '6.8 kg', status: 'accepted' as const },
  { order: 'ORD-10218', date: '3h ago', boxes: 1, weight: '1.2 kg', status: 'overridden' as const },
]

const BOX_ICONS: Record<string, React.ReactNode> = {
  large: <Maximize className="w-4 h-4" />,
  medium: <Box className="w-4 h-4" />,
  small: <Minimize className="w-4 h-4" />,
  custom: <Package className="w-4 h-4" />,
}

const BOX_COLORS: Record<string, string> = {
  large: 'text-purple-500 bg-purple-50 dark:bg-purple-900/20',
  medium: 'text-blue-500 bg-blue-50 dark:bg-blue-900/20',
  small: 'text-emerald-500 bg-emerald-50 dark:bg-emerald-900/20',
  custom: 'text-amber-500 bg-amber-50 dark:bg-amber-900/20',
}

const INSIGHTS = [
  { icon: <TrendingUp className="w-4 h-4 text-emerald-500" />, title: 'Material Savings', text: 'Switching to custom foam inserts could save \$1.28 per package' },
  { icon: <AlertTriangle className="w-4 h-4 text-amber-500" />, title: 'Weight Distribution', text: 'Box #2 exceeds 10 kg — consider splitting heavy items across 2 medium boxes' },
  { icon: <Info className="w-4 h-4 text-blue-500" />, title: 'Optimization Tip', text: 'Consolidate small items into a single box to reduce void fill usage' },
  { icon: <Zap className="w-4 h-4 text-violet-500" />, title: 'Speed Improvement', text: 'Estimated pack time can be reduced by 1.5 min with pre-cut materials' },
  { icon: <Shield className="w-4 h-4 text-rose-500" />, title: 'Fragile Alert', text: 'All fragile items placed in correctly labeled boxes' },
]

export default function AiPackingPage() {
  const [search, setSearch] = useState('')
  const [selectedOrder, setSelectedOrder] = useState<MockOrder | null>(null)
  const [plan, setPlan] = useState<AiPackagingPlan | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [overrideMode, setOverrideMode] = useState(false)
  const [overrideReason, setOverrideReason] = useState('')
  const [adjustedBoxes, setAdjustedBoxes] = useState<AiBox[] | null>(null)
  const [expandedBox, setExpandedBox] = useState<string | null>(null)

  const orderSuggestions = useMemo(() => {
    if (!search.trim()) return []
    return MOCK_ORDERS.filter(o =>
      o.id.toLowerCase().includes(search.toLowerCase())
    )
  }, [search])

  const selectOrder = (order: MockOrder) => {
    setSelectedOrder(order)
    setPlan(null)
    setError('')
    setOverrideMode(false)
    setOverrideReason('')
    setAdjustedBoxes(null)
  }

  const handleAnalyze = async () => {
    if (!selectedOrder) return
    setLoading(true)
    setError('')
    setPlan(null)
    try {
      const result = await getPackagingPlan(selectedOrder.id)
      setPlan(result)
      setAdjustedBoxes(null)
    } catch {
      setError('Failed to generate packaging plan. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleAccept = () => {
    setOverrideMode(false)
    setOverrideReason('')
    setAdjustedBoxes(null)
  }

  const handleOverride = () => {
    setOverrideMode(true)
    if (plan) setAdjustedBoxes(plan.boxes.map(b => ({ ...b })))
  }

  const handleConfirmOverride = () => {
    setOverrideMode(false)
  }

  const updateBoxType = (boxId: string, type: AiBox['type']) => {
    if (!adjustedBoxes) return
    setAdjustedBoxes(prev =>
      prev?.map(b => (b.id === boxId ? { ...b, type } : b)) ?? null
    )
  }

  const toggleMaterial = (boxId: string, material: string) => {
    if (!adjustedBoxes) return
    setAdjustedBoxes(prev =>
      prev?.map(b =>
        b.id === boxId
          ? {
              ...b,
              materials: b.materials.includes(material)
                ? b.materials.filter(m => m !== material)
                : [...b.materials, material],
            }
          : b
      ) ?? null
    )
  }

  const allMaterials = useMemo(() => {
    if (!plan) return []
    return [...new Set(plan.boxes.flatMap(b => b.materials))]
  }, [plan])

  const displayBoxes = adjustedBoxes ?? plan?.boxes ?? []

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2.5">
            <Package className="w-7 h-7 text-emerald-500" />
            AI Packing Intelligence
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Optimized packaging recommendations</p>
        </div>
        {plan && (
          <div className="flex items-center gap-2 bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300 text-xs font-semibold px-3 py-1.5 rounded-full">
            <Shield className="w-3.5 h-3.5" />
            AI Confidence: 96.7%
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-4 gap-6">
        {/* Left Panel */}
        <div className="xl:col-span-1 space-y-4">
          <div className="enterprise-card p-4">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3 flex items-center gap-2">
              <Search className="w-4 h-4 text-emerald-500" /> Order Scan / Select
            </h3>
            <Autocomplete
              value={search}
              onChange={setSearch}
              onSelect={(o: any) => { setSearch(o.id); setOrderSuggestions([]) }}
              placeholder="Search order number..."
              showSearchIcon={false}
              inputClassName="w-full px-3 py-2 text-sm border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
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
                        ? 'bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800'
                        : 'bg-gray-50 dark:bg-gray-800/50 hover:bg-gray-100 dark:hover:bg-gray-800 border border-transparent'
                    )}
                  >
                    <p className="font-medium text-gray-900 dark:text-gray-100">{order.id}</p>
                    <p className="text-xs text-gray-500 mt-0.5">{order.items.length} items</p>
                  </button>
                ))}
              </div>
            )}
            {!search.trim() && (
              <div className="mt-2 space-y-1">
                {MOCK_ORDERS.map(order => (
                  <button
                    key={order.id}
                    onClick={() => selectOrder(order)}
                    className={clsx(
                      'w-full text-left p-3 rounded-lg transition-colors text-sm',
                      selectedOrder?.id === order.id
                        ? 'bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800'
                        : 'bg-gray-50 dark:bg-gray-800/50 hover:bg-gray-100 dark:hover:bg-gray-800 border border-transparent'
                    )}
                  >
                    <p className="font-medium text-gray-900 dark:text-gray-100">{order.id}</p>
                    <p className="text-xs text-gray-500 mt-0.5">{order.items.length} items</p>
                  </button>
                ))}
              </div>
            )}
          </div>

          {selectedOrder && (
            <div className="enterprise-card p-4">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                  <Layers className="w-4 h-4 text-emerald-500" /> Items
                </h3>
                <span className="text-xs text-gray-400">{selectedOrder.items.length} total</span>
              </div>
              <div className="space-y-2">
                {selectedOrder.items.map((item, i) => (
                  <div key={i} className="flex items-center justify-between p-2 rounded bg-gray-50 dark:bg-gray-800/50">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{item.name}</p>
                      <p className="text-xs text-gray-500">{item.sku}</p>
                    </div>
                    <span className="text-xs font-semibold text-gray-700 dark:text-gray-300 ml-2">x{item.qty}</span>
                  </div>
                ))}
              </div>
              <button
                onClick={handleAnalyze}
                disabled={loading}
                className="enterprise-btn-primary w-full mt-4 bg-emerald-600 hover:bg-emerald-700 text-sm flex items-center justify-center gap-2"
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
            </div>
          )}

          {error && (
            <div className="enterprise-card p-4 border-red-200 dark:border-red-800">
              <div className="flex items-center gap-2 text-red-600 dark:text-red-400 text-sm">
                <AlertTriangle className="w-4 h-4" /> {error}
              </div>
            </div>
          )}

          {loading && !plan && (
            <div className="enterprise-card p-8">
              <div className="flex flex-col items-center gap-3 text-gray-400">
                <div className="w-8 h-8 border-3 border-emerald-500 border-t-transparent rounded-full animate-spin" />
                <p className="text-sm">Generating optimal packaging plan...</p>
                <p className="text-xs text-gray-400">AI is analyzing item dimensions, weights, and fragility</p>
              </div>
            </div>
          )}
        </div>

        {/* Right Panel */}
        <div className="xl:col-span-2 space-y-4">
          {!plan && !loading && (
            <div className="enterprise-card p-12">
              <div className="flex flex-col items-center gap-3 text-gray-400">
                <Package className="w-12 h-12 text-gray-300" />
                <p className="text-sm font-medium text-gray-500">Select an order and click "Analyze"</p>
                <p className="text-xs text-gray-400">AI will recommend the optimal packaging plan</p>
              </div>
            </div>
          )}

          {plan && (
            <>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                    <Box className="w-4 h-4 text-emerald-500" /> Box Plan
                  </h3>
                  {overrideMode && (
                    <span className="text-xs text-amber-600 bg-amber-50 dark:bg-amber-900/20 px-2 py-0.5 rounded-full font-medium">
                      Override Mode
                    </span>
                  )}
                </div>

                {displayBoxes.map((box, idx) => (
                  <div key={box.id} className="enterprise-card p-4 border border-gray-200 dark:border-gray-700">
                    <div className="flex items-start justify-between">
                      <div className="flex items-center gap-3">
                        <div className={clsx('w-9 h-9 rounded-xl flex items-center justify-center', BOX_COLORS[box.type])}>
                          {BOX_ICONS[box.type]}
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <p className="text-sm font-semibold text-gray-900 dark:text-gray-100 capitalize">
                              {overrideMode ? (
                                <select
                                  value={box.type}
                                  onChange={e => updateBoxType(box.id, e.target.value as AiBox['type'])}
                                  className="text-sm font-semibold border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800 px-1 py-0.5"
                                >
                                  <option value="small">Small</option>
                                  <option value="medium">Medium</option>
                                  <option value="large">Large</option>
                                  <option value="custom">Custom</option>
                                </select>
                              ) : (
                                <span className="capitalize">{box.type}</span>
                              )}
                              {' '}Box #{idx + 1}
                            </p>
                          </div>
                          <p className="text-xs text-gray-500">
                            {box.dimensions.w} × {box.dimensions.d} × {box.dimensions.h} cm
                          </p>
                        </div>
                      </div>
                      <button
                        onClick={() => setExpandedBox(expandedBox === box.id ? null : box.id)}
                        className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                      >
                        {expandedBox === box.id ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                      </button>
                    </div>

                    {expandedBox === box.id && (
                      <div className="mt-3 pt-3 border-t border-gray-100 dark:border-gray-700 space-y-3">
                        <div>
                          <p className="text-xs font-medium text-gray-500 mb-1.5">Items</p>
                          <div className="space-y-1">
                            {box.items.map((item, i) => (
                              <div key={i} className="flex items-center justify-between text-sm">
                                <span className="text-gray-700 dark:text-gray-300">{item.name}</span>
                                <span className="text-gray-500 text-xs">SKU: {item.sku} × {item.qty}</span>
                              </div>
                            ))}
                          </div>
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                          <div>
                            <p className="text-xs font-medium text-gray-500 mb-1">Weight</p>
                            <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">{box.weight} kg</p>
                          </div>
                          <div>
                            <p className="text-xs font-medium text-gray-500 mb-1">Volume</p>
                            <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">{(box.volume / 1000000).toFixed(4)} m³</p>
                          </div>
                        </div>

                        <div>
                          <p className="text-xs font-medium text-gray-500 mb-1.5">Materials</p>
                          <div className="flex flex-wrap gap-1.5">
                            {overrideMode
                              ? allMaterials.map(material => (
                                  <button
                                    key={material}
                                    onClick={() => toggleMaterial(box.id, material)}
                                    className={clsx(
                                      'text-xs px-2.5 py-1 rounded-full font-medium transition-colors',
                                      box.materials.includes(material)
                                        ? 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800'
                                        : 'bg-gray-50 dark:bg-gray-800 text-gray-500 border border-gray-200 dark:border-gray-700 hover:border-gray-300'
                                    )}
                                  >
                                    {box.materials.includes(material) ? '✓ ' : '+ '}{material}
                                  </button>
                                ))
                              : box.materials.map(m => (
                                  <span key={m} className="text-xs px-2.5 py-1 rounded-full bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300 font-medium">
                                    {m}
                                  </span>
                                ))}
                          </div>
                        </div>

                        {box.labels.length > 0 && (
                          <div>
                            <p className="text-xs font-medium text-gray-500 mb-1.5">Labels</p>
                            <div className="flex flex-wrap gap-1.5">
                              {box.labels.map(label => (
                                <span
                                  key={label}
                                  className={clsx(
                                    'text-xs px-2.5 py-1 rounded-full font-medium',
                                    label === 'Fragile'
                                      ? 'bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400'
                                      : label === 'This Side Up'
                                      ? 'bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400'
                                      : 'bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400'
                                  )}
                                >
                                  {label}
                                </span>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>

              {/* Summary Row */}
              <div className="enterprise-card p-4 bg-gray-50 dark:bg-gray-800/50">
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                  <div>
                    <p className="text-xs text-gray-500">Total Boxes</p>
                    <p className="text-lg font-bold text-gray-900 dark:text-gray-100">{plan.boxes.length}</p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500">Total Weight</p>
                    <p className="text-lg font-bold text-gray-900 dark:text-gray-100">{plan.totalWeight} kg</p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500">Total Volume</p>
                    <p className="text-lg font-bold text-gray-900 dark:text-gray-100">{(plan.totalVolume / 1000000).toFixed(2)} m³</p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500">Est. Pack Time</p>
                    <p className="text-lg font-bold text-gray-900 dark:text-gray-100 flex items-center gap-1">
                      <Clock className="w-4 h-4 text-gray-400" /> {plan.estimatedTimeMinutes} min
                    </p>
                  </div>
                </div>
              </div>

              {/* Actions */}
              {!overrideMode && (
                <div className="flex items-center gap-3">
                  <button onClick={handleAccept} className="enterprise-btn-primary bg-emerald-600 hover:bg-emerald-700 text-sm flex items-center gap-1.5 px-5 py-2.5">
                    <ThumbsUp className="w-4 h-4" /> Accept Plan
                  </button>
                  <button className="enterprise-btn-secondary text-sm flex items-center gap-1.5 px-5 py-2.5">
                    <Edit3 className="w-4 h-4" /> Adjust Boxes
                  </button>
                  <button onClick={handleOverride} className="enterprise-btn-secondary text-sm flex items-center gap-1.5 px-5 py-2.5 border-amber-200 dark:border-amber-800 text-amber-700 dark:text-amber-300 hover:bg-amber-50 dark:hover:bg-amber-900/10">
                    <RotateCcw className="w-4 h-4" /> Override
                  </button>
                </div>
              )}

              {overrideMode && (
                <div className="enterprise-card p-4 space-y-3 border-amber-200 dark:border-amber-800">
                  <h4 className="text-sm font-semibold text-amber-700 dark:text-amber-300 flex items-center gap-2">
                    <AlertTriangle className="w-4 h-4" /> Override Reason
                  </h4>
                  <textarea
                    value={overrideReason}
                    onChange={e => setOverrideReason(e.target.value)}
                    placeholder="Describe why you are overriding the AI recommendation..."
                    className="w-full px-3 py-2 text-sm border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-amber-500 resize-none"
                    rows={3}
                  />
                  <div className="flex items-center gap-3">
                    <button
                      onClick={handleConfirmOverride}
                      disabled={!overrideReason.trim()}
                      className="enterprise-btn-primary bg-amber-600 hover:bg-amber-700 text-sm flex items-center gap-1.5 px-5 py-2.5 disabled:opacity-50"
                    >
                      <RotateCcw className="w-4 h-4" /> Confirm Override
                    </button>
                    <button
                      onClick={() => {
                        setOverrideMode(false)
                        setAdjustedBoxes(null)
                        setOverrideReason('')
                      }}
                      className="enterprise-btn-secondary text-sm flex items-center gap-1.5 px-5 py-2.5"
                    >
                      <X className="w-4 h-4" /> Cancel
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* Right Sidebar - AI Insights */}
        <div className="xl:col-span-1 space-y-4">
          <div className="enterprise-card p-4">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3 flex items-center gap-2">
              <Zap className="w-4 h-4 text-emerald-500" /> AI Insights
            </h3>
            {plan ? (
              <div className="space-y-3">
                {INSIGHTS.map((insight, i) => (
                  <div key={i} className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                    <div className="flex items-start gap-2.5">
                      <div className="mt-0.5">{insight.icon}</div>
                      <div>
                        <p className="text-xs font-semibold text-gray-700 dark:text-gray-300">{insight.title}</p>
                        <p className="text-xs text-gray-500 mt-0.5">{insight.text}</p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-6 text-gray-400">
                <Info className="w-8 h-8 mx-auto mb-2" />
                <p className="text-xs">Insights will appear after analysis</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Bottom Section - Recent Packs */}
      <div className="enterprise-card p-5">
        <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center gap-2">
          <Clock className="w-4 h-4 text-gray-400" /> Recent AI-Packed Orders
        </h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 dark:border-gray-700">
                <th className="text-left py-2.5 px-3 text-xs font-medium text-gray-500">Order</th>
                <th className="text-left py-2.5 px-3 text-xs font-medium text-gray-500">Time</th>
                <th className="text-left py-2.5 px-3 text-xs font-medium text-gray-500">Boxes</th>
                <th className="text-left py-2.5 px-3 text-xs font-medium text-gray-500">Weight</th>
                <th className="text-left py-2.5 px-3 text-xs font-medium text-gray-500">Status</th>
              </tr>
            </thead>
            <tbody>
              {RECENT_PACKS.map((pack, i) => (
                <tr key={i} className="border-b border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td className="py-2.5 px-3 font-medium text-gray-900 dark:text-gray-100">{pack.order}</td>
                  <td className="py-2.5 px-3 text-gray-500">{pack.date}</td>
                  <td className="py-2.5 px-3 text-gray-700 dark:text-gray-300">{pack.boxes}</td>
                  <td className="py-2.5 px-3 text-gray-700 dark:text-gray-300">{pack.weight}</td>
                  <td className="py-2.5 px-3">
                    <span
                      className={clsx(
                        'inline-flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-full',
                        pack.status === 'accepted'
                          ? 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300'
                          : 'bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-300'
                      )}
                    >
                      {pack.status === 'accepted' ? (
                        <CheckCircle className="w-3 h-3" />
                      ) : (
                        <RotateCcw className="w-3 h-3" />
                      )}
                      {pack.status === 'accepted' ? 'Accepted' : 'Overridden'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
