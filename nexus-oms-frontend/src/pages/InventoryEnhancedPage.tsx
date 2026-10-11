import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Ban, Building2, DollarSign, Layers, Package, Plus, Search, Store, Warehouse } from 'lucide-react'
import clsx from 'clsx'
import client from '../api/client'
import { getWarehouses } from '../api/warehouse'
import { getProducts } from '../api/products'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import PermissionGate from '../components/rbac/PermissionGate'
import NewTransferDialog from '../features/transfers/NewTransferDialog'
import {
  atpForSku, available, lowStock, outOfStock, stockValue, summariseLocations, type StockRow,
} from '../features/inventory/stock'

const field = 'w-full rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)]'

async function fetchStock(): Promise<StockRow[]> {
  const { data } = await client.get('/inventory')
  if (data?.success === false) throw new Error(data.message || 'Could not load inventory')
  const rows = Array.isArray(data?.data) ? data.data : (data?.data?.content ?? [])
  return rows as StockRow[]
}

function capacityColor(pct: number): string {
  if (pct >= 80) return 'bg-[var(--nexus-error-500)]'
  if (pct >= 60) return 'bg-[var(--nexus-warning-500)]'
  return 'bg-[var(--nexus-success-500)]'
}

const money = (n: number) => new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(n)

export default function InventoryEnhancedPage() {
  const stockQ = useQuery({ queryKey: ['inventory-all'], queryFn: fetchStock })
  const warehousesQ = useQuery({ queryKey: ['warehouses-all'], queryFn: async () => (await getWarehouses(0, 200)).data?.content ?? [] })
  const productsQ = useQuery({ queryKey: ['products-all'], queryFn: async () => (await getProducts()).data ?? [] })

  const [search, setSearch] = useState('')
  const [tab, setTab] = useState<'low' | 'out'>('low')
  const [showTransfer, setShowTransfer] = useState(false)

  const rows = useMemo(() => stockQ.data ?? [], [stockQ.data])
  const locations = useMemo(() => warehousesQ.data ?? [], [warehousesQ.data])
  const products = useMemo(() => productsQ.data ?? [], [productsQ.data])

  const nameBySku = useMemo(() => new Map(products.map(p => [p.sku, p.productName])), [products])
  const costBySku = useMemo(() => new Map<string, number | undefined>(products.map(p => [p.sku, p.costPrice])), [products])
  const locationName = (id: string | null) => locations.find(l => l.id === id)?.name ?? (id ? `Location ${id.slice(0, 8)}` : 'Unassigned')

  const summary = useMemo(() => summariseLocations(rows, locations), [rows, locations])
  const totals = useMemo(() => ({
    units: rows.reduce((s, r) => s + (r.quantityOnHand ?? 0), 0),
    available: rows.reduce((s, r) => s + available(r), 0),
    skus: new Set(rows.map(r => r.sku)).size,
  }), [rows])
  const low = useMemo(() => lowStock(rows), [rows])
  const out = useMemo(() => outOfStock(rows), [rows])
  const value = useMemo(() => stockValue(rows, costBySku), [rows, costBySku])

  const matches = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return []
    const skus = [...new Set(rows.map(r => r.sku))]
    return skus.filter(s => s.toLowerCase().includes(q) || (nameBySku.get(s) ?? '').toLowerCase().includes(q)).slice(0, 8)
  }, [search, rows, nameBySku])

  if (stockQ.isLoading) {
    return <div className="space-y-4"><div className="h-24 animate-pulse rounded-xl bg-[var(--surface-sunken)]" /><div className="h-64 animate-pulse rounded-xl bg-[var(--surface-sunken)]" /></div>
  }
  if (stockQ.isError) {
    return <p role="alert" className="text-sm text-[var(--nexus-error-600)]">Couldn't load inventory: {(stockQ.error as Error).message}</p>
  }

  return (
    <PermissionGate resource="inventory" action="view">
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="flex items-center gap-2.5 text-2xl font-bold text-[var(--text-primary)]">
              <Warehouse className="h-7 w-7 text-[var(--nexus-warning-500)]" /> Multi-Node Inventory
            </h1>
            <p className="mt-1 text-sm text-[var(--text-secondary)]">Stock across every location, from your inventory records</p>
          </div>
          <PermissionGate resource="inventory" action="create">
            <button type="button" onClick={() => setShowTransfer(true)}
              className="inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-primary-600)] px-3.5 py-2 text-sm font-semibold text-white hover:bg-[var(--nexus-primary-700)]">
              <Plus className="h-4 w-4" /> New transfer
            </button>
          </PermissionGate>
        </div>

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
          <EnterpriseKPICard title="Units on hand" value={totals.units.toLocaleString()} subtitle={`${totals.skus} SKUs`} icon={<Layers className="h-5 w-5" />} color="primary" />
          <EnterpriseKPICard title="Available to promise" value={totals.available.toLocaleString()} subtitle="After allocations and safety stock" icon={<Package className="h-5 w-5" />} color="success" />
          <EnterpriseKPICard title="Stock value" value={value.value > 0 || value.unpricedSkus === 0 ? money(value.value) : '—'}
            subtitle={value.unpricedSkus ? `${value.unpricedSkus} SKU${value.unpricedSkus === 1 ? '' : 's'} have no cost price` : 'At cost price'} icon={<DollarSign className="h-5 w-5" />} color="info" />
          <EnterpriseKPICard title="Low stock" value={low.length} subtitle="At or below reorder point" icon={<AlertTriangle className="h-5 w-5" />} color="warning" />
          <EnterpriseKPICard title="Out of stock" value={out.length} subtitle="Nothing left to promise" icon={<Ban className="h-5 w-5" />} color="error" />
        </div>

        <section className="rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5">
          <h2 className="mb-1 flex items-center gap-2 text-base font-semibold text-[var(--text-primary)]"><Building2 className="h-4 w-4 text-[var(--nexus-warning-500)]" /> Locations</h2>
          <p className="mb-4 text-xs text-[var(--text-secondary)]">Capacity is floor space used, where the location records it.</p>
          {summary.length === 0 ? (
            <p className="text-sm text-[var(--text-tertiary)]">No locations or stock yet. Add a warehouse, then receive stock into it.</p>
          ) : (
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
              {summary.map(l => {
                const Icon = /store/i.test(l.type) ? Store : Building2
                return (
                  <div key={l.id} className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-4">
                    <div className="mb-3 flex items-center gap-3">
                      <div className="grid h-9 w-9 place-items-center rounded-lg bg-[var(--nexus-warning-50)]"><Icon className="h-4 w-4 text-[var(--nexus-warning-600)]" /></div>
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-[var(--text-primary)]">{l.name}</p>
                        <p className="text-[11px] text-[var(--text-tertiary)]">{l.type || 'Location'} · {l.skus} SKU{l.skus === 1 ? '' : 's'}</p>
                      </div>
                    </div>
                    <dl className="grid grid-cols-3 gap-2 text-center">
                      <div><dt className="text-[10px] uppercase text-[var(--text-tertiary)]">On hand</dt><dd className="text-sm font-semibold tabular-nums">{l.units.toLocaleString()}</dd></div>
                      <div><dt className="text-[10px] uppercase text-[var(--text-tertiary)]">Available</dt><dd className="text-sm font-semibold tabular-nums">{l.available.toLocaleString()}</dd></div>
                      <div><dt className="text-[10px] uppercase text-[var(--text-tertiary)]">Committed</dt><dd className="text-sm font-semibold tabular-nums">{l.committed.toLocaleString()}</dd></div>
                    </dl>
                    {l.capacityPct !== null && (
                      <div className="mt-3">
                        <div className="mb-1 flex justify-between text-[11px] text-[var(--text-secondary)]"><span>Capacity used</span><span>{l.capacityPct}%</span></div>
                        <div className="h-1.5 overflow-hidden rounded-full bg-[var(--surface-sunken)]"><div className={clsx('h-full rounded-full', capacityColor(l.capacityPct))} style={{ width: `${l.capacityPct}%` }} /></div>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </section>

        <section className="rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5">
          <h2 className="mb-3 text-base font-semibold text-[var(--text-primary)]">Availability by SKU</h2>
          <div className="relative max-w-md">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--text-tertiary)]" />
            <input aria-label="Search SKU or product" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search a SKU or product name…" className={clsx(field, 'pl-9')} />
          </div>
          {search.trim() && matches.length === 0 && <p className="mt-3 text-sm text-[var(--text-tertiary)]">No SKU matches "{search.trim()}".</p>}
          <div className="mt-4 space-y-4">
            {matches.map(sku => {
              const lines = atpForSku(rows, sku)
              return (
                <div key={sku} className="overflow-x-auto rounded-xl border border-[var(--border-default)]">
                  <div className="border-b border-[var(--border-subtle)] px-4 py-2.5">
                    <span className="font-mono text-xs">{sku}</span> <span className="text-sm text-[var(--text-secondary)]">{nameBySku.get(sku) ?? ''}</span>
                  </div>
                  <table className="w-full min-w-[480px] text-sm">
                    <thead><tr className="text-left text-xs uppercase tracking-wider text-[var(--text-tertiary)]">
                      <th className="px-4 py-2 font-medium">Location</th><th className="px-4 py-2 text-right font-medium">Available</th>
                      <th className="px-4 py-2 text-right font-medium">Committed</th><th className="px-4 py-2 text-right font-medium">Incoming</th><th className="px-4 py-2 text-right font-medium">On order</th>
                    </tr></thead>
                    <tbody>{lines.map(l => (
                      <tr key={l.nodeId ?? 'unassigned'} className="border-t border-[var(--border-subtle)]">
                        <td className="px-4 py-2">{locationName(l.nodeId)}</td>
                        <td className="px-4 py-2 text-right font-semibold tabular-nums">{l.available}</td>
                        <td className="px-4 py-2 text-right tabular-nums">{l.committed}</td>
                        <td className="px-4 py-2 text-right tabular-nums">{l.incoming}</td>
                        <td className="px-4 py-2 text-right tabular-nums">{l.onOrder}</td>
                      </tr>))}
                    </tbody>
                  </table>
                </div>
              )
            })}
          </div>
        </section>

        <section className="rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5">
          <div className="mb-3 flex items-center gap-2" role="tablist" aria-label="Stock alerts">
            {([['low', `Low stock (${low.length})`], ['out', `Out of stock (${out.length})`]] as const).map(([key, label]) => (
              <button key={key} type="button" role="tab" aria-selected={tab === key} onClick={() => setTab(key)}
                className={clsx('rounded-lg px-3 py-1.5 text-sm font-medium', tab === key ? 'bg-[var(--nexus-primary-600)] text-white' : 'text-[var(--text-secondary)] hover:bg-[var(--interactive-hover)]')}>{label}</button>
            ))}
          </div>
          {tab === 'low' ? (
            low.length === 0 ? <p className="text-sm text-[var(--text-tertiary)]">Nothing is at or below its reorder point.</p> : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px] text-sm">
                  <thead><tr className="text-left text-xs uppercase tracking-wider text-[var(--text-tertiary)]">
                    <th className="py-2 pr-3 font-medium">SKU</th><th className="py-2 pr-3 font-medium">Location</th>
                    <th className="py-2 pr-3 text-right font-medium">Available</th><th className="py-2 pr-3 text-right font-medium">Reorder point</th><th className="py-2 text-right font-medium">Reorder qty</th>
                  </tr></thead>
                  <tbody>{low.map(r => (
                    <tr key={r.id} className="border-t border-[var(--border-subtle)]">
                      <td className="py-2 pr-3"><span className="font-mono text-xs">{r.sku}</span> <span className="text-[var(--text-secondary)]">{nameBySku.get(r.sku) ?? ''}</span></td>
                      <td className="py-2 pr-3">{locationName(r.nodeId)}</td>
                      <td className="py-2 pr-3 text-right font-semibold tabular-nums text-[var(--nexus-warning-700)]">{available(r)}</td>
                      <td className="py-2 pr-3 text-right tabular-nums">{r.reorderPoint}</td>
                      <td className="py-2 text-right tabular-nums">{r.reorderQty || '—'}</td>
                    </tr>))}
                  </tbody>
                </table>
              </div>
            )
          ) : (
            out.length === 0 ? <p className="text-sm text-[var(--text-tertiary)]">Every SKU has something left to promise.</p> : (
              <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {out.map(sku => <li key={sku} className="rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm"><span className="font-mono text-xs">{sku}</span> <span className="text-[var(--text-secondary)]">{nameBySku.get(sku) ?? ''}</span></li>)}
              </ul>
            )
          )}
        </section>
      </div>
      {showTransfer && <NewTransferDialog onClose={() => setShowTransfer(false)} />}
    </PermissionGate>
  )
}
