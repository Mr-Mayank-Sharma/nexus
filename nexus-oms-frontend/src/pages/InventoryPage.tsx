import { useState, useMemo, useEffect, useCallback } from 'react'
import { Download, Upload, Plus, AlertTriangle, Package, Layers, CircleDollarSign, Archive, X } from 'lucide-react'
import DataTable, { Column } from '../components/common/DataTable'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseToolbar from '../components/enterprise/EnterpriseToolbar'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import { Inventory } from '../types'
import * as inventoryApi from '../api/inventory'
import PermissionGate from '../components/rbac/PermissionGate'

interface RawInventory {
  sku?: string
  id?: string
  productName?: string
  category?: string
  nodeId?: string
  nodeName?: string
  quantityOnHand?: number
  quantityAllocated?: number
  quantityReserved?: number
  quantityInTransit?: number
  quantityDamaged?: number
  safetyStock?: number
  reorderPoint?: number
  unitCost?: number
  lastCountedAt?: string
  updatedAt?: string
}

export default function InventoryPage() {
  const [inventory, setInventory] = useState<Inventory[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [nodeFilter, setNodeFilter] = useState('ALL')
  const [lowStock, setLowStock] = useState(false)
  const [sortBy, setSortBy] = useState('sku')
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')
  const [page, setPage] = useState(1)
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [showAdjustModal, setShowAdjustModal] = useState(false)
  const [adjustSku, setAdjustSku] = useState('')
  const [adjustQty, setAdjustQty] = useState(0)
  const [adjustReason, setAdjustReason] = useState('')
  const [adjustSubmitting, setAdjustSubmitting] = useState(false)
  const pageSize = 15

  useEffect(() => {
    async function fetchInventory() {
      try {
        setLoading(true)
        const res = await inventoryApi.getInventory()
        const data = res.data
        if (Array.isArray(data)) {
          setInventory(data.map((item: RawInventory) => ({
            sku: item.sku || item.id || '',
            productName: item.productName || item.sku || '',
            category: item.category || '',
            nodeId: item.nodeId || '',
            nodeName: item.nodeName || '',
            quantityOnHand: item.quantityOnHand || 0,
            quantityAllocated: item.quantityAllocated || 0,
            quantityReserved: item.quantityReserved || 0,
            quantityInTransit: item.quantityInTransit || 0,
            quantityDamaged: item.quantityDamaged || 0,
            atp: item.atp || (item.quantityOnHand || 0) - (item.quantityAllocated || 0) - (item.quantityReserved || 0),
            safetyStock: item.safetyStock || 50,
            reorderPoint: item.reorderPoint || 25,
            unitCost: item.unitCost || 0,
            totalValue: item.totalValue || ((item.quantityOnHand || 0) * (item.unitCost || 0)),
            lastCountedAt: item.lastCountedAt || new Date().toISOString(),
            updatedAt: item.updatedAt || new Date().toISOString(),
          })))
        } else {
          setInventory([])
        }
      } catch {
        setInventory([])
      } finally {
        setLoading(false)
      }
    }
    fetchInventory()
  }, [])

  const filtered = useMemo(() => {
    let result = [...inventory]
    if (search) {
      const q = search.toLowerCase()
      result = result.filter((i) => i.sku.toLowerCase().includes(q) || i.productName.toLowerCase().includes(q))
    }
    if (nodeFilter !== 'ALL') result = result.filter((i) => i.nodeId === nodeFilter)
    if (lowStock) result = result.filter((i) => i.atp < i.reorderPoint)
    result.sort((a, b) => {
      const aVal = String(a[sortBy as keyof Inventory] ?? '')
      const bVal = String(b[sortBy as keyof Inventory] ?? '')
      return sortOrder === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal)
    })
    return result
  }, [inventory, search, nodeFilter, lowStock, sortBy, sortOrder])

  const totalPages = Math.ceil(filtered.length / pageSize)
  const paged = filtered.slice((page - 1) * pageSize, page * pageSize)

  const totalOnHand = inventory.reduce((s, i) => s + i.quantityOnHand, 0)
  const totalAllocated = inventory.reduce((s, i) => s + i.quantityAllocated, 0)
  const totalValue = inventory.reduce((s, i) => s + i.totalValue, 0)
  const lowStockCount = inventory.filter((i) => i.atp < i.reorderPoint).length

  const columns: Column<Inventory>[] = [
    { key: 'sku', header: 'SKU', sortable: true, render: (i) => <span className="font-medium text-[var(--text-primary)]">{i.sku}</span> },
    { key: 'productName', header: 'Product', sortable: true, render: (i) => <span className="text-[var(--text-secondary)]">{i.productName}</span> },
    { key: 'nodeName', header: 'Node', sortable: true, render: (i) => <span className="text-[var(--text-tertiary)] text-xs">{i.nodeName}</span> },
    { key: 'quantityOnHand', header: 'On Hand', sortable: true, render: (i) => <span className="text-[var(--text-primary)]">{i.quantityOnHand.toLocaleString()}</span> },
    { key: 'quantityAllocated', header: 'Allocated', sortable: true, render: (i) => <span className="text-[var(--text-tertiary)]">{i.quantityAllocated.toLocaleString()}</span> },
    { key: 'quantityReserved', header: 'Reserved', sortable: true, render: (i) => <span className="text-[var(--text-tertiary)]">{i.quantityReserved.toLocaleString()}</span> },
    {
      key: 'atp', header: 'ATP', sortable: true,
      render: (i) => {
        const status = i.atp >= i.safetyStock ? 'success' : i.atp >= i.reorderPoint ? 'warning' : 'error'
        return (
          <div className="flex items-center gap-2">
            <EnterpriseStatusBadge status={status} size="sm" />
            <span className="font-medium text-[var(--text-primary)]">{i.atp.toLocaleString()}</span>
          </div>
        )
      },
    },
    {
      key: 'totalValue', header: 'Value', sortable: true,
      render: (i) => <span className="text-[var(--text-primary)]">${i.totalValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>,
    },
  ]

  const nodes = Array.from(new Set(inventory.map((i) => i.nodeId)))

  const handleAdjustSubmit = useCallback(async () => {
    if (!adjustSku.trim()) return
    setAdjustSubmitting(true)
    try {
      await inventoryApi.adjustInventory(adjustSku.trim(), adjustQty, adjustReason)
      setShowAdjustModal(false)
      const res = await inventoryApi.getInventory()
      const data = res.data
      if (Array.isArray(data)) {
        setInventory(data.map((item: RawInventory) => ({
          sku: item.sku || item.id || '',
          productName: item.productName || item.sku || '',
          category: item.category || '',
          nodeId: item.nodeId || '',
          nodeName: item.nodeName || '',
          quantityOnHand: item.quantityOnHand || 0,
          quantityAllocated: item.quantityAllocated || 0,
          quantityReserved: item.quantityReserved || 0,
          quantityInTransit: item.quantityInTransit || 0,
          quantityDamaged: item.quantityDamaged || 0,
          atp: item.atp || (item.quantityOnHand || 0) - (item.quantityAllocated || 0) - (item.quantityReserved || 0),
          safetyStock: item.safetyStock || 50,
          reorderPoint: item.reorderPoint || 25,
          unitCost: item.unitCost || 0,
          totalValue: item.totalValue || ((item.quantityOnHand || 0) * (item.unitCost || 0)),
          lastCountedAt: item.lastCountedAt || new Date().toISOString(),
          updatedAt: item.updatedAt || new Date().toISOString(),
        })))
      }
    } catch {
      // error handled by api layer
    } finally {
      setAdjustSubmitting(false)
    }
  }, [adjustSku, adjustQty, adjustReason])

  return (
    <div className="space-y-4">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Inventory' }]} />

      <EnterpriseToolbar
        title="Inventory"
        subtitle={`${inventory.length} SKUs across ${nodes.length} nodes`}
        searchValue={search}
        onSearch={(v) => { setSearch(v); setPage(1) }}
        searchPlaceholder="Search by SKU or product name..."
        autocomplete={{
          fetchSuggestions: async (q) => {
            if (!q) return inventory.slice(0, 10)
            const term = q.toLowerCase()
            return inventory.filter(i => i.sku?.toLowerCase().includes(term) || i.productName?.toLowerCase().includes(term)).slice(0, 10)
          },
          onSelect: (item: Inventory) => setSearch(item.sku),
          getOptionLabel: (item: Inventory) => `${item.sku} — ${item.productName || ''} (Qty: ${item.quantityOnHand ?? 0})`,
          getOptionValue: (item: Inventory) => item.sku,
          minChars: 1,
        }}
        filters={
          <>
            <select className="enterprise-input w-44" value={nodeFilter} onChange={(e) => { setNodeFilter(e.target.value); setPage(1) }}>
              <option value="ALL">All Nodes</option>
              {nodes.map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
            <label className="flex items-center gap-2 text-sm text-[var(--text-secondary)] cursor-pointer whitespace-nowrap">
              <input type="checkbox" className="rounded border-[var(--border-default)] text-[var(--nexus-primary-600)] focus:ring-[var(--nexus-primary-500)]"
                checked={lowStock} onChange={(e) => { setLowStock(e.target.checked); setPage(1) }} />
              <AlertTriangle className="w-4 h-4 text-[var(--nexus-warning-600)]" />
              Low stock only
            </label>
          </>
        }
        actions={[
          { label: 'Export', icon: <Download className="w-4 h-4" />, onClick: () => {
            const headers = ['SKU', 'Product', 'Node', 'On Hand', 'Allocated', 'Reserved', 'ATP', 'Safety Stock', 'Reorder Point', 'Unit Cost', 'Total Value']
            const rows = filtered.map(i => [i.sku, i.productName, i.nodeName, String(i.quantityOnHand), String(i.quantityAllocated), String(i.quantityReserved), String(i.atp), String(i.safetyStock), String(i.reorderPoint), String(i.unitCost), String(i.totalValue)])
            const csv = [headers, ...rows].map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
            const blob = new Blob([csv], { type: 'text/csv' })
            const url = URL.createObjectURL(blob)
            const a = document.createElement('a'); a.href = url; a.download = `inventory-${new Date().toISOString().split('T')[0]}.csv`; a.click()
            URL.revokeObjectURL(url)
          }, variant: 'secondary' },
          { label: 'Import', icon: <Upload className="w-4 h-4" />, onClick: () => { window.location.href = '/import-export' }, variant: 'ghost' },
          { label: 'Adjust Inventory', icon: <Plus className="w-4 h-4" />, onClick: () => { setAdjustSku(''); setAdjustQty(0); setAdjustReason(''); setShowAdjustModal(true); }, variant: 'primary', permission: { resource: 'inventory', action: 'edit' } },
        ]}
      />

      <div className="enterprise-kpi-grid">
        <EnterpriseKPICard title="Total SKUs" value={inventory.length} icon={<Package className="w-5 h-5" />} color="primary" />
        <EnterpriseKPICard title="Units On Hand" value={totalOnHand.toLocaleString()} icon={<Layers className="w-5 h-5" />} color="success" />
        <EnterpriseKPICard title="Units Allocated" value={totalAllocated.toLocaleString()} icon={<CircleDollarSign className="w-5 h-5" />} color="warning" />
        <EnterpriseKPICard title="Low Stock Items" value={lowStockCount} subtitle={lowStockCount > 0 ? 'Needs reorder' : 'All healthy'} icon={<Archive className="w-5 h-5" />} color={lowStockCount > 0 ? 'error' : 'success'} />
      </div>

      <div className="enterprise-card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center p-12">
            <div className="enterprise-spinner enterprise-spinner-lg" />
          </div>
        ) : (
          <DataTable columns={columns} data={paged} keyExtractor={(i) => `${i.sku}-${i.nodeId}`} sortBy={sortBy} sortOrder={sortOrder} onSort={(key) => { if (sortBy === key) setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc'); else { setSortBy(key); setSortOrder('asc') } }} selectedIds={selectedIds} onSelectionChange={setSelectedIds} pagination={{ page, totalPages, total: filtered.length, onPageChange: setPage }} emptyMessage="No inventory items found" />
        )}
      </div>

      {showAdjustModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setShowAdjustModal(false)}>
          <div className="enterprise-card p-6 w-full max-w-md space-y-4" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Adjust Inventory</h2>
              <button type="button" onClick={() => setShowAdjustModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded-lg transition-colors">
                <X className="w-5 h-5 text-[var(--text-secondary)]" />
              </button>
            </div>

            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">SKU</label>
                <input
                  type="text"
                  list="sku-suggestions"
                  className="enterprise-input w-full"
                  placeholder="Search or enter SKU..."
                  value={adjustSku}
                  onChange={(e) => setAdjustSku(e.target.value)}
                />
                <datalist id="sku-suggestions">
                  {inventory.map((i) => (
                    <option key={i.sku} value={i.sku}>{i.productName} (On Hand: {i.quantityOnHand})</option>
                  ))}
                </datalist>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Quantity Change</label>
                <input
                  type="number"
                  className="enterprise-input w-full"
                  placeholder="Positive to add, negative to reduce"
                  value={adjustQty}
                  onChange={(e) => setAdjustQty(parseInt(e.target.value) || 0)}
                />
                <p className="text-xs text-[var(--text-tertiary)] mt-1">Use positive values to add stock, negative to remove.</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Reason</label>
                <input
                  type="text"
                  className="enterprise-input w-full"
                  placeholder="e.g. Cycle count correction, Damaged goods"
                  value={adjustReason}
                  onChange={(e) => setAdjustReason(e.target.value)}
                />
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <button type="button" onClick={() => setShowAdjustModal(false)} className="enterprise-btn enterprise-btn-secondary">
                Cancel
              </button>
              <button
                onClick={handleAdjustSubmit}
                disabled={adjustSubmitting || !adjustSku.trim()}
                className="enterprise-btn enterprise-btn-primary"
              >
                {adjustSubmitting ? 'Adjusting...' : 'Apply Adjustment'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
