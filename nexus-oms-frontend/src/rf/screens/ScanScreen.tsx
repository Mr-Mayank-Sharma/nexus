import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Search, PackageCheck, Package, PackageSearch, Truck, ClipboardCheck, PackagePlus } from 'lucide-react'
import { searchScan, ScanResult } from '../../api/scan'
import { RfCard, RfEmpty, ScreenHeader } from '../components'
import ScannerOverlay from '../ScannerOverlay'

type LookupType = 'picklist' | 'package' | 'shipment' | 'receipt' | 'count'

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
  const [results, setResults] = useState<ScanResult[]>([])
  const [searched, setSearched] = useState(false)
  const [loading, setLoading] = useState(false)

  const lookup = async (q: string) => {
    const needle = q.trim()
    if (!needle) return
    setQuery(needle)
    setLoading(true)
    const res = await searchScan(needle)
    setResults(res.success && Array.isArray(res.data) ? res.data : [])
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
              {TYPE_ICON[r.type as LookupType] ?? <Package className="w-6 h-6 text-[var(--text-tertiary)] shrink-0" />}
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
