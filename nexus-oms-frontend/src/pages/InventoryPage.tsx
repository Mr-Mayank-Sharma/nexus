import { useState, useMemo, useEffect } from 'react'
import { Search, Filter, Download, Plus, AlertTriangle } from 'lucide-react'
import DataTable, { Column } from '../components/common/DataTable'
import { Inventory } from '../types'
import * as inventoryApi from '../api/inventory'

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

  function atpStatus(item: Inventory) {
    if (item.atp >= item.safetyStock) return 'green'
    if (item.atp >= item.reorderPoint) return 'yellow'
    return 'red'
  }

  const columns: Column<Inventory>[] = [
    { key: 'sku', header: 'SKU', sortable: true, render: (i) => <span className="font-medium text-gray-900">{i.sku}</span> },
    { key: 'productName', header: 'Product', sortable: true, render: (i) => <span className="text-gray-700">{i.productName}</span> },
    { key: 'nodeName', header: 'Node', sortable: true, render: (i) => <span className="text-gray-500 text-xs">{i.nodeName}</span> },
    { key: 'quantityOnHand', header: 'On Hand', sortable: true, render: (i) => <span className="text-gray-700">{i.quantityOnHand.toLocaleString()}</span> },
    { key: 'quantityAllocated', header: 'Allocated', sortable: true, render: (i) => <span className="text-gray-500">{i.quantityAllocated.toLocaleString()}</span> },
    { key: 'quantityReserved', header: 'Reserved', sortable: true, render: (i) => <span className="text-gray-500">{i.quantityReserved.toLocaleString()}</span> },
    {
      key: 'atp', header: 'ATP', sortable: true,
      render: (i) => {
        const status = atpStatus(i)
        return (
          <div className="flex items-center gap-2">
            <div className={`w-2 h-2 rounded-full ${status === 'green' ? 'bg-green-500' : status === 'yellow' ? 'bg-yellow-500' : 'bg-red-500'}`} />
            <span className={`font-medium ${status === 'red' ? 'text-red-600' : 'text-gray-700'}`}>{i.atp.toLocaleString()}</span>
          </div>
        )
      },
    },
    {
      key: 'totalValue', header: 'Value', sortable: true,
      render: (i) => <span className="text-gray-700">${i.totalValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>,
    },
  ]

  const nodes = Array.from(new Set(inventory.map((i) => i.nodeId)))

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Inventory</h1>
          <p className="text-sm text-gray-500 mt-1">{inventory.length} SKUs across {nodes.length} nodes</p>
        </div>
        <div className="flex items-center gap-2">
          <button className="btn-secondary text-sm"><Download className="w-4 h-4" /> Export</button>
          <button className="btn-primary text-sm"><Plus className="w-4 h-4" /> Adjust Inventory</button>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input type="text" placeholder="Search by SKU or product name..." className="input pl-10" value={search} onChange={(e) => { setSearch(e.target.value); setPage(1) }} />
        </div>
        <select className="input w-44" value={nodeFilter} onChange={(e) => { setNodeFilter(e.target.value); setPage(1) }}>
          <option value="ALL">All Nodes</option>
          {nodes.map((n) => <option key={n} value={n}>{n}</option>)}
        </select>
        <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
          <input type="checkbox" className="rounded border-gray-300 text-primary-600 focus:ring-primary-500" checked={lowStock} onChange={(e) => { setLowStock(e.target.checked); setPage(1) }} />
          <AlertTriangle className="w-4 h-4 text-yellow-500" />
          Low stock only
        </label>
      </div>

      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center p-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
          </div>
        ) : (
          <DataTable columns={columns} data={paged} keyExtractor={(i) => `${i.sku}-${i.nodeId}`} sortBy={sortBy} sortOrder={sortOrder} onSort={(key) => { if (sortBy === key) setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc'); else { setSortBy(key); setSortOrder('asc') } }} selectedIds={selectedIds} onSelectionChange={setSelectedIds} pagination={{ page, totalPages, total: filtered.length, onPageChange: setPage }} emptyMessage="No inventory items found" />
        )}
      </div>
    </div>
  )
}
