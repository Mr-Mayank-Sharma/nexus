import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Search, PackageCheck, Package, PackageSearch, Truck, ClipboardCheck, PackagePlus } from 'lucide-react'
import * as pickingApi from '../../api/picking'
import * as packingApi from '../../api/packing'
import * as shippingApi from '../../api/shipping'
import * as receiptsApi from '../../api/inventoryReceipts'
import * as cycleCountsApi from '../../api/cycleCounts'
import { RfCard, RfEmpty, ScreenHeader } from '../components'
import ScannerOverlay from '../ScannerOverlay'

type LookupType = 'picklist' | 'package' | 'shipment' | 'receipt' | 'count'

interface LookupResult {
  type: LookupType
  id: string
  label: string
  sub: string
  href: string
}

const asArray = (data: any): any[] => (Array.isArray(data) ? data : (data?.content ?? []))

function actionableShipment(s: any) {  return !['SHIPPED', 'DELIVERED', 'COMPLETED'].includes(String(s.status ?? '').toUpperCase())
}

function actionableReceipt(r: any) {
  return !r.receivedAt && !['RECEIVED', 'COMPLETED'].includes(String(r.status ?? '').toUpperCase())
}

function actionableCount(c: any) {
  return !c.countedAt && !['COUNTED', 'COMPLETED'].includes(String(c.status ?? '').toUpperCase())
}

const TYPE_ICON: Record<LookupType, JSX.Element> = {
  picklist: <PackageCheck className="w-6 h-6 text-[var(--nexus-primary-600)] shrink-0" />,
  package: <Package className="w-6 h-6 text-[var(--nexus-warning-600)] shrink-0" />,
  shipment: <Truck className="w-6 h-6 text-[var(--nexus-info-600)] shrink-0" />,
  receipt: <PackagePlus className="w-6 h-6 text-[var(--nexus-success-600)] shrink-0" />,
  count: <ClipboardCheck className="w-6 h-6 text-[var(--nexus-info-600)] shrink-0" />,
}

export default function ScanScreen() {
  const [scanOpen, setScanOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<LookupResult[]>([])
  const [searched, setSearched] = useState(false)
  const [loading, setLoading] = useState(false)

  const lookup = async (q: string) => {
    const needle = q.trim()
    if (!needle) return
    setQuery(needle)
    setLoading(true)
    const out: LookupResult[] = []
    const low = needle.toLowerCase()

    const [plRes, pkgRes, shipRes, recRes, cntRes] = await Promise.all([
      pickingApi.getPicklists(),
      packingApi.getPackages(),
      shippingApi.getShipments(),
      receiptsApi.getReceipts({}),
      cycleCountsApi.getCycleCounts({}),
    ])

      for (const pl of asArray(plRes.data)) {
        if (String(pl.id ?? '').toLowerCase().includes(low) || String(pl.name ?? '').toLowerCase().includes(low)) {
          out.push({ type: 'picklist', id: pl.id, label: pl.name, sub: `${pl.pickedItems}/${pl.totalItems} picked · ${pl.status}`, href: `/rf/pick?id=${pl.id}` })
        }
      }
      for (const p of asArray(pkgRes.data)) {
        if (String(p.id ?? '').toLowerCase().includes(low) || String(p.orderId ?? '').toLowerCase().includes(low)) {
          out.push({ type: 'package', id: p.id, label: `Package #${p.id?.slice(0, 8)}`, sub: `Order ${p.orderId?.slice(0, 8) ?? '—'} · ${p.status}`, href: `/rf/pack?id=${p.id}` })
        }
      }
      for (const s of asArray(shipRes.data)) {
        if (!actionableShipment(s)) continue
        if (String(s.id ?? '').toLowerCase().includes(low) || String(s.orderId ?? '').toLowerCase().includes(low) || String(s.trackingNumber ?? '').toLowerCase().includes(low)) {
          out.push({ type: 'shipment', id: s.id, label: `Shipment #${s.id?.slice(0, 8)}`, sub: `Order ${s.orderId?.slice(0, 8) ?? '—'} · ${s.status}`, href: '/rf/ship' })
        }
      }
      for (const r of asArray(recRes.data)) {
        if (!actionableReceipt(r)) continue
        if (String(r.id ?? '').toLowerCase().includes(low) || String(r.sku ?? '').toLowerCase().includes(low) || String(r.referenceNumber ?? '').toLowerCase().includes(low)) {
          out.push({ type: 'receipt', id: r.id, label: r.productName || r.sku, sub: `SKU ${r.sku} · ${r.receiptType ?? 'RECEIPT'}`, href: '/rf/receive' })
        }
      }
      for (const c of asArray(cntRes.data)) {
        if (!actionableCount(c)) continue
        if (String(c.id ?? '').toLowerCase().includes(low) || String(c.sku ?? '').toLowerCase().includes(low)) {
          out.push({ type: 'count', id: c.id, label: c.productName || c.sku, sub: `SKU ${c.sku} · expected ${c.expectedQty}`, href: '/rf/count' })
        }
      }

    setResults(out)
    setSearched(true)
    setLoading(false)
  }

  const handleScan = (code: string) => {
    setScanOpen(false)
    void lookup(code)
  }

  return (
    <div className="px-4 pt-4 pb-24">
      <ScreenHeader title="Scan" subtitle="Find a picklist, package, shipment, receipt, or count by barcode" />

      <button
        type="button"
        onClick={() => setScanOpen(true)}
        className="w-full rounded-2xl bg-black border-2 border-emerald-500/50 p-6 flex flex-col items-center gap-3 text-emerald-400 active:scale-[0.98] transition-transform touch-manipulation"
      >
        <PackageSearch className="w-10 h-10" />
        <span className="text-base font-semibold">Tap to scan barcode</span>
        <span className="text-xs text-emerald-500/60">or type below</span>
      </button>

      <div className="mt-4 flex items-center gap-2">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') void lookup(query)
          }}
          placeholder="Picklist, package, shipment, SKU…"
          className="flex-1 rounded-xl bg-[var(--surface-sunken)] text-[var(--text-primary)] px-4 py-3 text-base placeholder:text-[var(--text-tertiary)] focus:outline-none focus:ring-2 focus:ring-[var(--nexus-primary-600)]"
        />
        <button
          type="button"
          onClick={() => void lookup(query)}
          className="shrink-0 px-4 py-3 rounded-xl bg-[var(--nexus-primary-600)] text-white active:scale-95 transition-transform"
          aria-label="Search"
        >
          <Search className="w-5 h-5" />
        </button>
      </div>

      <div className="mt-4 space-y-3">
        {loading && <div className="py-8 text-center text-[var(--text-tertiary)]">Searching…</div>}
        {!loading && searched && results.length === 0 && (
          <RfEmpty icon={<PackageSearch className="w-10 h-10" />} text="No matching picklists or packages" />
        )}
        {!loading && results.map((r) => (
          <Link key={`${r.type}-${r.id}`} to={r.href}>
            <RfCard className="p-4 flex items-center gap-3 active:scale-[0.99] transition-transform">
              {TYPE_ICON[r.type]}
              <div className="min-w-0">
                <p className="text-sm font-semibold text-[var(--text-primary)] truncate">{r.label}</p>
                <p className="text-xs text-[var(--text-secondary)] truncate">{r.sub}</p>
              </div>
            </RfCard>
          </Link>
        ))}
      </div>

      <ScannerOverlay
        open={scanOpen}
        title="Scan barcode"
        placeholder="Scan or type barcode…"
        onScan={handleScan}
        onClose={() => setScanOpen(false)}
      />
    </div>
  )
}
