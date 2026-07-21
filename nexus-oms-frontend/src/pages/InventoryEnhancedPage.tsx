import { useState, useMemo, useEffect } from 'react'
import {
  Warehouse, Package, DollarSign, AlertTriangle, Layers, Archive,
  Building2, Store, ArrowRight, Search, X, Truck, Plus,
  RefreshCw, TrendingUp, Sparkles, Clock, Hash, Ban,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import Autocomplete from '../components/common/Autocomplete'
import { fetchEnhancedInventory, adjustInventory } from '../api/newBackend'

interface WarehouseNode {
  id: string
  name: string
  type: 'DC' | 'Warehouse' | 'Store'
  capacity: number
  totalUnits: number
  skuCount: number
  lastUpdated: string
}

interface ProductATP {
  sku: string
  productName: string
  nodes: { nodeName: string; available: number; reserved: number; incoming: number; onOrder: number }[]
}

interface Transfer {
  id: string
  from: string
  to: string
  sku: string
  productName: string
  qty: number
  status: 'In Transit' | 'Completed' | 'Pending'
  date: string
}

interface StockAlert {
  sku: string
  productName: string
  currentStock: number
  reorderPoint: number
  maxStock: number
  node: string
}

interface ReplenishmentSuggestion {
  id: string
  message: string
  type: 'reorder' | 'overstock'
  priority: 'high' | 'medium' | 'low'
}

const mockProducts: ProductATP[] = [
  { sku: 'SKU-001', productName: 'Wireless Mouse', nodes: [
    { nodeName: 'Main DC', available: 120, reserved: 30, incoming: 50, onOrder: 0 },
    { nodeName: 'Warehouse B', available: 45, reserved: 10, incoming: 20, onOrder: 0 },
    { nodeName: 'Warehouse C', available: 8, reserved: 2, incoming: 0, onOrder: 25 },
    { nodeName: 'Store #5', available: 3, reserved: 1, incoming: 0, onOrder: 0 },
    { nodeName: 'Store #3', available: 0, reserved: 0, incoming: 0, onOrder: 0 },
  ]},
  { sku: 'SKU-042', productName: 'Mechanical Keyboard', nodes: [
    { nodeName: 'Main DC', available: 250, reserved: 60, incoming: 100, onOrder: 0 },
    { nodeName: 'Warehouse B', available: 88, reserved: 22, incoming: 0, onOrder: 50 },
    { nodeName: 'Warehouse C', available: 34, reserved: 8, incoming: 12, onOrder: 0 },
    { nodeName: 'Store #5', available: 15, reserved: 3, incoming: 0, onOrder: 0 },
    { nodeName: 'Store #3', available: 7, reserved: 2, incoming: 5, onOrder: 0 },
  ]},
  { sku: 'SKU-107', productName: 'USB-C Hub', nodes: [
    { nodeName: 'Main DC', available: 600, reserved: 120, incoming: 200, onOrder: 0 },
    { nodeName: 'Warehouse B', available: 210, reserved: 45, incoming: 80, onOrder: 0 },
    { nodeName: 'Warehouse C', available: 95, reserved: 20, incoming: 30, onOrder: 100 },
    { nodeName: 'Store #5', available: 22, reserved: 5, incoming: 10, onOrder: 0 },
    { nodeName: 'Store #3', available: 11, reserved: 3, incoming: 0, onOrder: 0 },
  ]},
  { sku: 'SKU-203', productName: '27" Monitor', nodes: [
    { nodeName: 'Main DC', available: 89, reserved: 35, incoming: 25, onOrder: 0 },
    { nodeName: 'Warehouse B', available: 12, reserved: 8, incoming: 0, onOrder: 40 },
    { nodeName: 'Warehouse C', available: 4, reserved: 1, incoming: 0, onOrder: 0 },
    { nodeName: 'Store #5', available: 1, reserved: 0, incoming: 0, onOrder: 0 },
    { nodeName: 'Store #3', available: 0, reserved: 0, incoming: 0, onOrder: 0 },
  ]},
  { sku: 'SKU-311', productName: 'Webcam HD', nodes: [
    { nodeName: 'Main DC', available: 340, reserved: 80, incoming: 120, onOrder: 0 },
    { nodeName: 'Warehouse B', available: 145, reserved: 30, incoming: 0, onOrder: 60 },
    { nodeName: 'Warehouse C', available: 72, reserved: 15, incoming: 25, onOrder: 0 },
    { nodeName: 'Store #5', available: 28, reserved: 5, incoming: 0, onOrder: 0 },
    { nodeName: 'Store #3', available: 9, reserved: 2, incoming: 0, onOrder: 0 },
  ]},
  { sku: 'SKU-089', productName: 'Noise Cancelling Headphones', nodes: [
    { nodeName: 'Main DC', available: 23, reserved: 10, incoming: 0, onOrder: 0 },
    { nodeName: 'Warehouse B', available: 5, reserved: 2, incoming: 0, onOrder: 0 },
    { nodeName: 'Warehouse C', available: 0, reserved: 0, incoming: 0, onOrder: 0 },
    { nodeName: 'Store #5', available: 0, reserved: 0, incoming: 0, onOrder: 0 },
    { nodeName: 'Store #3', available: 0, reserved: 0, incoming: 0, onOrder: 0 },
  ]},
  { sku: 'SKU-455', productName: 'Bluetooth Speaker', nodes: [
    { nodeName: 'Main DC', available: 800, reserved: 150, incoming: 300, onOrder: 0 },
    { nodeName: 'Warehouse B', available: 350, reserved: 70, incoming: 100, onOrder: 200 },
    { nodeName: 'Warehouse C', available: 180, reserved: 40, incoming: 60, onOrder: 0 },
    { nodeName: 'Store #5', available: 55, reserved: 10, incoming: 20, onOrder: 0 },
    { nodeName: 'Store #3', available: 42, reserved: 8, incoming: 15, onOrder: 0 },
  ]},
]

const mockTransfers: Transfer[] = [
  { id: 'TR-001', from: 'Main DC', to: 'Store #5', sku: 'SKU-001', productName: 'Wireless Mouse', qty: 50, status: 'In Transit', date: '2026-06-28' },
  { id: 'TR-002', from: 'Warehouse B', to: 'Main DC', sku: 'SKU-042', productName: 'Mechanical Keyboard', qty: 100, status: 'Completed', date: '2026-06-27' },
  { id: 'TR-003', from: 'Main DC', to: 'Store #3', sku: 'SKU-107', productName: 'USB-C Hub', qty: 30, status: 'In Transit', date: '2026-06-29' },
  { id: 'TR-004', from: 'Warehouse C', to: 'Warehouse B', sku: 'SKU-203', productName: '27" Monitor', qty: 20, status: 'Pending', date: '2026-07-01' },
  { id: 'TR-005', from: 'Main DC', to: 'Warehouse C', sku: 'SKU-311', productName: 'Webcam HD', qty: 75, status: 'Completed', date: '2026-06-25' },
  { id: 'TR-006', from: 'Warehouse B', to: 'Store #5', sku: 'SKU-089', productName: 'Noise Cancelling Headphones', qty: 10, status: 'Pending', date: '2026-07-02' },
]

const lowStockAlerts: StockAlert[] = [
  { sku: 'SKU-203', productName: '27" Monitor', currentStock: 4, reorderPoint: 25, maxStock: 200, node: 'Warehouse C' },
  { sku: 'SKU-089', productName: 'Noise Cancelling Headphones', currentStock: 5, reorderPoint: 50, maxStock: 300, node: 'Warehouse B' },
  { sku: 'SKU-001', productName: 'Wireless Mouse', currentStock: 3, reorderPoint: 20, maxStock: 150, node: 'Store #5' },
  { sku: 'SKU-042', productName: 'Mechanical Keyboard', currentStock: 7, reorderPoint: 15, maxStock: 120, node: 'Store #3' },
  { sku: 'SKU-311', productName: 'Webcam HD', currentStock: 9, reorderPoint: 20, maxStock: 180, node: 'Store #3' },
  { sku: 'SKU-107', productName: 'USB-C Hub', currentStock: 11, reorderPoint: 30, maxStock: 250, node: 'Store #3' },
]

const overstockAlerts: StockAlert[] = [
  { sku: 'SKU-455', productName: 'Bluetooth Speaker', currentStock: 800, reorderPoint: 100, maxStock: 400, node: 'Main DC' },
  { sku: 'SKU-107', productName: 'USB-C Hub', currentStock: 600, reorderPoint: 80, maxStock: 350, node: 'Main DC' },
  { sku: 'SKU-311', productName: 'Webcam HD', currentStock: 340, reorderPoint: 60, maxStock: 200, node: 'Main DC' },
  { sku: 'SKU-455', productName: 'Bluetooth Speaker', currentStock: 350, reorderPoint: 80, maxStock: 250, node: 'Warehouse B' },
  { sku: 'SKU-001', productName: 'Wireless Mouse', currentStock: 120, reorderPoint: 30, maxStock: 100, node: 'Main DC' },
]

const deadStockAlerts: StockAlert[] = [
  { sku: 'SKU-089', productName: 'Noise Cancelling Headphones', currentStock: 23, reorderPoint: 50, maxStock: 300, node: 'Main DC' },
  { sku: 'SKU-203', productName: '27" Monitor', currentStock: 12, reorderPoint: 25, maxStock: 200, node: 'Warehouse B' },
  { sku: 'SKU-089', productName: 'Noise Cancelling Headphones', currentStock: 5, reorderPoint: 50, maxStock: 300, node: 'Warehouse B' },
]

const replenishmentSuggestions: ReplenishmentSuggestion[] = [
  { id: 'RS-001', message: 'Wireless Mouse needs 50 units in Store #5 (current: 3, reorder point: 20)', type: 'reorder', priority: 'high' },
  { id: 'RS-002', message: 'Noise Cancelling Headphones needs 45 units in Warehouse B (current: 5, reorder point: 50)', type: 'reorder', priority: 'high' },
  { id: 'RS-003', message: '27" Monitor needs 21 units in Warehouse C (current: 4, reorder point: 25)', type: 'reorder', priority: 'medium' },
  { id: 'RS-004', message: 'Mechanical Keyboard needs 8 units in Store #3 (current: 7, reorder point: 15)', type: 'reorder', priority: 'medium' },
  { id: 'RS-005', message: 'Bluetooth Speaker overstocked in Main DC (current: 800, max: 400)', type: 'overstock', priority: 'high' },
  { id: 'RS-006', message: 'USB-C Hub overstocked in Main DC (current: 600, max: 350)', type: 'overstock', priority: 'medium' },
  { id: 'RS-007', message: 'Webcam HD overstocked in Main DC (current: 340, max: 200)', type: 'overstock', priority: 'low' },
]

const nodeIconMap: Record<string, typeof Building2> = {
  DC: Building2,
  Warehouse: Warehouse,
  Store: Store,
}

function capacityColor(cap: number): string {
  if (cap >= 80) return 'bg-[var(--nexus-error-50)]0'
  if (cap >= 60) return 'bg-[var(--nexus-warning-50)]0'
  if (cap >= 35) return 'bg-emerald-500'
  return 'bg-[var(--nexus-primary-50)]0'
}

function atpColor(atp: number): string {
  if (atp > 50) return 'text-emerald-600 bg-emerald-50 dark:text-emerald-400 dark:bg-emerald-900/20'
  if (atp >= 10) return 'text-[var(--nexus-warning-600)] bg-[var(--nexus-warning-50)] dark:text-[var(--nexus-warning-400)] dark:bg-[var(--nexus-warning-900)]/20'
  return 'text-[var(--nexus-error-600)] bg-[var(--nexus-error-50)] dark:text-[var(--nexus-error-400)] dark:bg-[var(--nexus-error-900)]/20'
}

function atpBgBar(atp: number): string {
  if (atp > 50) return 'bg-emerald-500'
  if (atp >= 10) return 'bg-[var(--nexus-warning-50)]0'
  return 'bg-[var(--nexus-error-50)]0'
}

export default function InventoryEnhancedPage() {
  const [warehouseData, setWarehouseData] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchEnhancedInventory().then(res => {
      if (res?.warehouses) setWarehouseData(res.warehouses)
      setLoading(false)
    }).catch(() => setLoading(false))
  }, [])

  const { addToast } = useToast()

  const [atpSearch, setAtpSearch] = useState('')
  const [selectedSku, setSelectedSku] = useState<string | null>(null)
  const [showTransferModal, setShowTransferModal] = useState(false)
  const [alertTab, setAlertTab] = useState<'low' | 'overstock' | 'dead'>('low')
  const [transferForm, setTransferForm] = useState({ from: '', to: '', sku: '', qty: 0 })

  const filteredProducts = useMemo(() => {
    if (!atpSearch.trim()) return []
    const q = atpSearch.toLowerCase()
    return mockProducts.filter(p => p.sku.toLowerCase().includes(q) || p.productName.toLowerCase().includes(q))
  }, [atpSearch])

  const selectedProduct = mockProducts.find(p => p.sku === selectedSku) || null

  const totalUnits = warehouseData.reduce((s, w) => s + w.totalUnits, 0)
  const totalValue = 12400000
  const lowStockCount = 23
  const overstockCount = 156
  const deadStockCount = 34

  function handleSelectProduct(sku: string) {
    setSelectedSku(sku)
    setAtpSearch('')
  }

  function handleCreateTransfer() {
    addToast({ type: 'success', title: 'Transfer Created', message: `${transferForm.sku} transfer from ${transferForm.from} to ${transferForm.to}` })
    setShowTransferModal(false)
    setTransferForm({ from: '', to: '', sku: '', qty: 0 })
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Warehouse className="w-7 h-7 text-[var(--nexus-warning-500)]" />
            Multi-Node Inventory
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Real-time inventory across all fulfillment nodes</p>
        </div>
      </div>

      {/* Global Inventory Summary KPI Row */}
      <div className="grid grid-cols-5 gap-4">
        <EnterpriseKPICard title="Total Units" value={totalUnits.toLocaleString()} icon={<Layers className="w-5 h-5" />} color="primary" />
        <EnterpriseKPICard title="Total Value" value={'$12.4M'} icon={<DollarSign className="w-5 h-5" />} color="success" />
        <EnterpriseKPICard title="Low Stock Items" value={lowStockCount} subtitle="Needs reorder" icon={<AlertTriangle className="w-5 h-5" />} color="error" />
        <EnterpriseKPICard title="Overstock Items" value={overstockCount} subtitle="Excess inventory" icon={<Archive className="w-5 h-5" />} color="warning" />
        <EnterpriseKPICard title="Dead Stock" value={deadStockCount} subtitle="No movement" icon={<Ban className="w-5 h-5" />} color="info" />
      </div>

      {/* Warehouse Breakdown Grid */}
      <div className="enterprise-card p-5">
        <div className="flex items-center justify-between mb-5">
          <div>
            <h3 className="text-base font-semibold text-[var(--text-primary)] flex items-center gap-2">
              <Building2 className="w-4 h-4 text-[var(--nexus-warning-500)]" />
              Warehouse Breakdown
            </h3>
            <p className="text-xs text-[var(--text-secondary)] mt-0.5">Capacity and inventory across all nodes</p>
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
          {warehouseData.map(w => {
            const Icon = nodeIconMap[w.type] || Building2
            return (
              <div key={w.id} className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-4 hover:shadow-md transition-shadow">
                <div className="flex items-center gap-3 mb-3">
                  <div className="w-9 h-9 rounded-lg bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20 flex items-center justify-center">
                    <Icon className="w-4 h-4 text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-[var(--text-primary)] truncate">{w.name}</p>
                    <p className="text-[10px] text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">{w.id} · {w.type}</p>
                  </div>
                </div>
                <div className="space-y-2">
                  <div>
                    <div className="flex items-center justify-between text-xs mb-1">
                      <span className="text-[var(--text-secondary)]">Capacity</span>
                      <span className="font-semibold text-[var(--text-secondary)]">{w.capacity}%</span>
                    </div>
                    <div className="w-full bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded-full h-2">
                      <div className={clsx(capacityColor(w.capacity), 'h-2 rounded-full transition-all')} style={{ width: `${w.capacity}%` }} />
                    </div>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-[var(--text-secondary)]">Total Units</span>
                    <span className="font-medium text-[var(--text-primary)]">{w.totalUnits.toLocaleString()}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-[var(--text-secondary)]">SKUs</span>
                    <span className="font-medium text-[var(--text-primary)]">{w.skuCount.toLocaleString()}</span>
                  </div>
                  <div className="flex items-center justify-between text-[10px] text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] pt-1 border-t border-[var(--border-subtle)]">
                    <Clock className="w-3 h-3" />
                    <span>{w.lastUpdated}</span>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* ATP Simulator */}
      <div className="enterprise-card p-5">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-semibold text-[var(--text-primary)] flex items-center gap-2">
              <RefreshCw className="w-4 h-4 text-emerald-500" />
              ATP Simulator
            </h3>
            <p className="text-xs text-[var(--text-secondary)] mt-0.5">Available-to-Promise across all fulfillment nodes</p>
          </div>
        </div>
        <div className="relative mb-4">
          <Autocomplete value={atpSearch} onChange={setAtpSearch} placeholder="Search product by SKU or name..." minChars={0} />
          {filteredProducts.length > 0 && atpSearch && (
            <div className="absolute z-10 mt-1 w-full bg-[var(--surface-base)] border border-[var(--border-default)] rounded-xl shadow-lg overflow-hidden">
              {filteredProducts.map(p => (
                <button
                  key={p.sku}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-left hover:bg-[var(--surface-sunken)] dark:hover:bg-[var(--surface-muted)] transition-colors border-b border-[var(--border-subtle)] last:border-b-0"
                  onClick={() => handleSelectProduct(p.sku)}
                >
                  <Hash className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />
                  <span className="font-medium text-[var(--text-primary)]">{p.sku}</span>
                  <span className="text-[var(--text-secondary)]">— {p.productName}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        {selectedProduct && (
          <div className="overflow-x-auto">
            <div className="mb-3 flex items-center gap-2 text-sm">
              <span className="font-semibold text-[var(--text-primary)]">{selectedProduct.sku}</span>
              <span className="text-[var(--text-secondary)]">— {selectedProduct.productName}</span>
            </div>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--border-default)]">
                  <th className="text-left py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Node</th>
                  <th className="text-right py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Available</th>
                  <th className="text-right py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Reserved</th>
                  <th className="text-right py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Incoming</th>
                  <th className="text-right py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">On Order</th>
                  <th className="text-right py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">ATP</th>
                </tr>
              </thead>
              <tbody>
                {selectedProduct.nodes.map(n => {
                  const atp = n.available - n.reserved + n.incoming
                  return (
                    <tr key={n.nodeName} className="border-b border-[var(--border-subtle)]/50 hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)]/50 transition-colors">
                      <td className="py-2.5 px-3 text-[var(--text-primary)] font-medium">{n.nodeName}</td>
                      <td className="py-2.5 px-3 text-right text-[var(--text-secondary)]">{n.available.toLocaleString()}</td>
                      <td className="py-2.5 px-3 text-right text-[var(--text-secondary)]">{n.reserved.toLocaleString()}</td>
                      <td className="py-2.5 px-3 text-right text-emerald-600 dark:text-emerald-400 font-medium">+{n.incoming.toLocaleString()}</td>
                      <td className="py-2.5 px-3 text-right text-[var(--text-secondary)]">{n.onOrder.toLocaleString()}</td>
                      <td className="py-2.5 px-3 text-right">
                        <span className={clsx('inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-bold', atpColor(atp))}>
                          <span className={clsx('w-1.5 h-1.5 rounded-full', atpBgBar(atp))} />
                          {atp.toLocaleString()}
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {!selectedProduct && (
          <div className="text-center py-8 text-sm text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">
            <Search className="w-8 h-8 mx-auto mb-2 opacity-50" />
            Search for a product to view ATP across all nodes
          </div>
        )}
      </div>

      {/* Inventory Transfers */}
      <div className="enterprise-card p-5">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-semibold text-[var(--text-primary)] flex items-center gap-2">
              <Truck className="w-4 h-4 text-[var(--nexus-primary-500)]" />
              Inventory Transfers
            </h3>
            <p className="text-xs text-[var(--text-secondary)] mt-0.5">Stock movement between nodes</p>
          </div>
          <button
            className="enterprise-btn-primary text-sm flex items-center gap-1.5 px-4 py-2"
            onClick={() => setShowTransferModal(true)}
          >
            <Plus className="w-4 h-4" />
            Create Transfer
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[var(--border-default)]">
                <th className="text-left py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">From → To</th>
                <th className="text-left py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">SKU</th>
                <th className="text-right py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Qty</th>
                <th className="text-center py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Status</th>
                <th className="text-right py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Date</th>
              </tr>
            </thead>
            <tbody>
              {mockTransfers.map(t => (
                <tr key={t.id} className="border-b border-[var(--border-subtle)]/50 hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)]/50 transition-colors">
                  <td className="py-2.5 px-3">
                    <div className="flex items-center gap-1.5 text-sm text-[var(--text-primary)]">
                      <span className="font-medium">{t.from}</span>
                      <ArrowRight className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />
                      <span className="font-medium">{t.to}</span>
                    </div>
                  </td>
                  <td className="py-2.5 px-3">
                    <div className="text-[var(--text-primary)] font-medium">{t.sku}</div>
                    <div className="text-xs text-[var(--text-secondary)]">{t.productName}</div>
                  </td>
                  <td className="py-2.5 px-3 text-right text-[var(--text-primary)] font-medium">{t.qty.toLocaleString()}</td>
                  <td className="py-2.5 px-3 text-center">
                    <EnterpriseStatusBadge status={t.status.toLowerCase().replace(/\s+/g, '_')} label={t.status} />
                  </td>
                  <td className="py-2.5 px-3 text-right text-[var(--text-secondary)] text-xs">{t.date}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Stock Alerts */}
      <div className="enterprise-card p-5">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-semibold text-[var(--text-primary)] flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-[var(--nexus-error-500)]" />
              Stock Alerts
            </h3>
            <p className="text-xs text-[var(--text-secondary)] mt-0.5">Inventory items requiring attention</p>
          </div>
        </div>
        <div className="flex gap-1 bg-[var(--surface-muted)] rounded-lg p-0.5 w-fit mb-4">
          {(['low', 'overstock', 'dead'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setAlertTab(tab)}
              className={clsx(
                'px-3 py-1.5 text-xs font-medium rounded-md capitalize transition-all',
                alertTab === tab
                  ? 'bg-[var(--surface-muted)] text-[var(--text-primary)] shadow-sm'
                  : 'text-[var(--text-secondary)] hover:text-[var(--text-secondary)] dark:hover:text-[var(--text-tertiary)]'
              )}
            >
              {tab === 'low' ? 'Low Stock' : tab === 'overstock' ? 'Overstock' : 'Dead Stock'}
              <span className={clsx(
                'ml-1.5 text-[10px] px-1.5 py-0.5 rounded-full',
                alertTab === tab
                  ? 'bg-[var(--surface-muted)] dark:bg-[var(--surface-muted)] text-[var(--text-secondary)] text-[var(--text-primary)]'
                  : 'bg-[var(--surface-muted)] bg-[var(--surface-muted)] text-[var(--text-secondary)]'
              )}>
                {tab === 'low' ? lowStockAlerts.length : tab === 'overstock' ? overstockAlerts.length : deadStockAlerts.length}
              </span>
            </button>
          ))}
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[var(--border-default)]">
                <th className="text-left py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">SKU</th>
                <th className="text-left py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Product</th>
                <th className="text-right py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Current Stock</th>
                <th className="text-right py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Reorder Point</th>
                <th className="text-center py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Status</th>
                <th className="text-left py-2.5 px-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Node</th>
              </tr>
            </thead>
            <tbody>
              {(alertTab === 'low' ? lowStockAlerts : alertTab === 'overstock' ? overstockAlerts : deadStockAlerts).map((a, i) => {
                const isLow = a.currentStock < a.reorderPoint
                const isOver = a.currentStock > a.maxStock
                return (
                  <tr key={`${a.sku}-${i}`} className="border-b border-[var(--border-subtle)]/50 hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)]/50 transition-colors">
                    <td className="py-2.5 px-3 text-[var(--text-primary)] font-medium">{a.sku}</td>
                    <td className="py-2.5 px-3 text-[var(--text-secondary)]">{a.productName}</td>
                    <td className="py-2.5 px-3 text-right font-semibold">
                      <span className={clsx(isLow && 'text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]', isOver && 'text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]', !isLow && !isOver && 'text-[var(--text-primary)]')}>
                        {a.currentStock.toLocaleString()}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-right text-[var(--text-secondary)]">{a.reorderPoint.toLocaleString()}</td>
                    <td className="py-2.5 px-3 text-center">
                      {isLow ? (
                        <EnterpriseStatusBadge status="error" label="Low Stock" />
                      ) : isOver ? (
                        <EnterpriseStatusBadge status="warning" label="Overstock" />
                      ) : (
                        <EnterpriseStatusBadge status="neutral" label="Dead Stock" />
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-[var(--text-secondary)]">{a.node}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Replenishment Suggestions */}
      <div className="enterprise-card p-5">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-semibold text-[var(--text-primary)] flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-violet-500" />
              AI Replenishment Suggestions
            </h3>
            <p className="text-xs text-[var(--text-secondary)] mt-0.5">Intelligent recommendations based on stock levels and demand</p>
          </div>
        </div>
        <div className="space-y-2">
          {replenishmentSuggestions.map(s => (
            <div
              key={s.id}
              className={clsx(
                'flex items-start gap-3 p-3 rounded-xl border transition-colors',
                s.type === 'reorder'
                  ? 'bg-[var(--nexus-error-50)]/50 dark:bg-[var(--nexus-error-900)]/10 border-[var(--nexus-error-100)] dark:border-[var(--nexus-error-900)]/30'
                  : 'bg-[var(--nexus-warning-50)]/50 dark:bg-[var(--nexus-warning-900)]/10 border-[var(--nexus-warning-100)] dark:border-[var(--nexus-warning-900)]/30'
              )}
            >
              <div className={clsx(
                'w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5',
                s.type === 'reorder' ? 'bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/30' : 'bg-[var(--nexus-warning-100)] dark:bg-[var(--nexus-warning-900)]/30'
              )}>
                {s.type === 'reorder' ? (
                  <TrendingUp className="w-3.5 h-3.5 text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]" />
                ) : (
                  <Archive className="w-3.5 h-3.5 text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]" />
                )}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm text-[var(--text-primary)]">{s.message}</p>
                <div className="flex items-center gap-2 mt-1">
                  <span className={clsx(
                    'text-[10px] font-medium px-1.5 py-0.5 rounded-full',
                    s.priority === 'high' && 'bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/30 text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]',
                    s.priority === 'medium' && 'bg-[var(--nexus-warning-100)] dark:bg-[var(--nexus-warning-900)]/30 text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]',
                    s.priority === 'low' && 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
                  )}>
                    {s.priority} priority
                  </span>
                  <span className="text-[10px] text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">
                    {s.type === 'reorder' ? 'Reorder needed' : 'Overstock detected'}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Create Transfer Modal */}
      {showTransferModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-md">
            <div className="flex items-center justify-between px-6 py-4 border-b border-[var(--border-default)]">
              <h3 className="text-base font-semibold text-[var(--text-primary)] flex items-center gap-2">
                <Truck className="w-4 h-4 text-[var(--nexus-primary-500)]" />
                Create Transfer
              </h3>
              <button onClick={() => setShowTransferModal(false)} className="text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] dark:hover:text-[var(--text-tertiary)]">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="px-6 py-5 space-y-4">
              <div>
                <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">From Node</label>
                <select
                  className="enterprise-input w-full"
                  value={transferForm.from}
                  onChange={e => setTransferForm(f => ({ ...f, from: e.target.value }))}
                >
                  <option value="">Select source node</option>
                  {warehouseData.map(w => <option key={w.id} value={w.name}>{w.name}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">To Node</label>
                <select
                  className="enterprise-input w-full"
                  value={transferForm.to}
                  onChange={e => setTransferForm(f => ({ ...f, to: e.target.value }))}
                >
                  <option value="">Select destination node</option>
                  {warehouseData.map(w => <option key={w.id} value={w.name}>{w.name}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">SKU</label>
                <input
                  className="enterprise-input w-full"
                  placeholder="e.g. SKU-001"
                  value={transferForm.sku}
                  onChange={e => setTransferForm(f => ({ ...f, sku: e.target.value }))}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Quantity</label>
                <input
                  type="number"
                  className="enterprise-input w-full"
                  placeholder="0"
                  min={0}
                  value={transferForm.qty || ''}
                  onChange={e => setTransferForm(f => ({ ...f, qty: parseInt(e.target.value) || 0 }))}
                />
              </div>
            </div>
            <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-[var(--border-default)] bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50">
              <button className="enterprise-btn-secondary text-sm px-4 py-2" onClick={() => setShowTransferModal(false)}>Cancel</button>
              <button
                className="enterprise-btn-primary text-sm px-4 py-2"
                disabled={!transferForm.from || !transferForm.to || !transferForm.sku || transferForm.qty <= 0}
                onClick={handleCreateTransfer}
              >
                <Plus className="w-4 h-4" />
                Create Transfer
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
