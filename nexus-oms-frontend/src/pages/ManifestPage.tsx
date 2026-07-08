import { useState, useMemo, useEffect } from 'react'
import {
  ClipboardList, Search, Plus, X, Loader2, CheckCircle, AlertCircle,
  Download, Eye, FileText, Truck, DollarSign, Package, PenLine,
  ChevronRight, Calendar, Send, Ban,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import clsx from 'clsx'
import Autocomplete from '../components/common/Autocomplete'
import { fetchManifests, createManifest, updateManifest, fetchCarriers } from '../api/newBackend'

interface ManifestShipment {
  id: string
  orderId: string
  tracking: string
  weight: number
  cost: number
  service: string
  destination: string
}

interface Manifest {
  id: string
  carrier: string
  date: string
  shipments: ManifestShipment[]
  totalWeight: number
  totalCost: number
  status: 'Draft' | 'Closed' | 'Submitted'
  bolNumber?: string
}

const CARRIERS = ['FedEx', 'UPS', 'DHL', 'USPS'] as const

const STATUS_STYLES: Record<string, string> = {
  Draft: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  Closed: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  Submitted: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
}

function generateManifestId(idx: number) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
  let suffix = ''
  for (let i = 0; i < 5; i++) suffix += chars[Math.floor(Math.random() * chars.length)]
  return `MFT-${String(idx).padStart(4, '0')}-${suffix}`
}

function generateShipmentForManifest(orderIdx: number): ManifestShipment {
  const carriers = ['FedEx', 'UPS', 'USPS', 'DHL']
  const services = ['Ground', '2Day', 'Overnight', 'International']
  const destinations = ['New York, NY', 'Los Angeles, CA', 'Chicago, IL', 'Houston, TX', 'Phoenix, AZ', 'Dallas, TX']
  return {
    id: `SHP-${String(Math.random()).slice(2, 10)}`,
    orderId: `ORD-${String(10230 + orderIdx).padStart(5, '0')}`,
    tracking: `TRK${String(Math.random()).slice(2, 13)}`,
    weight: +(Math.random() * 25 + 0.5).toFixed(1),
    cost: +(Math.random() * 30 + 4).toFixed(2),
    service: services[orderIdx % services.length],
    destination: destinations[orderIdx % destinations.length],
  }
}

const MOCK_MANIFESTS: Manifest[] = [
  {
    id: generateManifestId(1), carrier: 'FedEx', date: '2026-06-30',
    shipments: Array.from({ length: 52 }, (_, i) => generateShipmentForManifest(i)),
    status: 'Submitted', bolNumber: 'BOL-FX-88291',
  },
  {
    id: generateManifestId(2), carrier: 'UPS', date: '2026-06-30',
    shipments: Array.from({ length: 48 }, (_, i) => generateShipmentForManifest(i + 52)),
    status: 'Closed', bolNumber: 'BOL-UPS-45123',
  },
  {
    id: generateManifestId(3), carrier: 'DHL', date: '2026-06-29',
    shipments: Array.from({ length: 47 }, (_, i) => generateShipmentForManifest(i + 100)),
    status: 'Draft',
  },
]

const TOTALS = {
  totalManifests: 3,
  totalShipments: 147,
  totalCost: 1247.50,
  avgCost: 8.49,
}

export default function ManifestPage() {
  const { addToast } = useToast()
  const [searchManifest, setSearchManifest] = useState('')

  const [createCarrier, setCreateCarrier] = useState('FedEx')
  const [dateFrom, setDateFrom] = useState('2026-06-28')
  const [dateTo, setDateTo] = useState('2026-06-30')
  const [fetchedShipments, setFetchedShipments] = useState<ManifestShipment[]>([])
  const [selectedShipments, setSelectedShipments] = useState<string[]>([])
  const [isFetching, setIsFetching] = useState(false)
  const [fetched, setFetched] = useState(false)

  const [manifests, setManifests] = useState<any[]>([])
  const [carrierList, setCarrierList] = useState<any[]>([])

  useEffect(() => {
    Promise.all([fetchManifests(), fetchCarriers()]).then(([m, c]) => {
      if (m?.manifests) setManifests(m.manifests)
      if (c?.carriers) setCarrierList(c.carriers)
    }).catch(() => {})
  }, [])
  const [selectedManifest, setSelectedManifest] = useState<Manifest | null>(null)
  const [showDetail, setShowDetail] = useState(false)

  const [signature, setSignature] = useState('')
  const [scanInput, setScanInput] = useState('')

  const filteredManifests = useMemo(() => {
    if (!searchManifest) return manifests
    const q = searchManifest.toLowerCase()
    return manifests.filter(m =>
      m.id.toLowerCase().includes(q) ||
      m.carrier.toLowerCase().includes(q) ||
      (m.bolNumber || '').toLowerCase().includes(q)
    )
  }, [manifests, searchManifest])

  function handleFetchShipments() {
    if (!dateFrom || !dateTo) {
      addToast({ type: 'error', title: 'Select a date range' })
      return
    }
    setIsFetching(true)
    setTimeout(() => {
      const count = Math.floor(Math.random() * 25 + 10)
      const shipments = Array.from({ length: count }, (_, i) => generateShipmentForManifest(i))
      setFetchedShipments(shipments)
      setSelectedShipments(shipments.map(s => s.id))
      setIsFetching(false)
      setFetched(true)
      addToast({ type: 'success', title: `${count} shipments fetched` })
    }, 1000)
  }

  function toggleShipment(id: string) {
    setSelectedShipments(prev =>
      prev.includes(id) ? prev.filter(s => s !== id) : [...prev, id]
    )
  }

  async function handleCreateManifest(data: any) {
    const res = await createManifest(data)
    if (res?.manifest) {
      setManifests(prev => [res.manifest, ...prev])
      addToast({ type: 'success', title: 'Manifest created' })
    }
  }

  function handleViewDetail(manifest: Manifest) {
    setSelectedManifest(manifest)
    setShowDetail(true)
  }

  function handleDownload(manifest: Manifest) {
    addToast({ type: 'success', title: `Downloading manifest ${manifest.id}` })
  }

  async function handleUpdateManifest(id: string, updates: any) {
    const res = await updateManifest(id, updates)
    if (res?.manifest) {
      setManifests(prev => prev.map(m => m.id === id ? res.manifest : m))
      addToast({ type: 'success', title: 'Manifest updated' })
    }
  }

  function handleVoid(manifest: Manifest) {
    setManifests(prev => prev.filter(m => m.id !== manifest.id))
    addToast({ type: 'success', title: `Manifest ${manifest.id} voided` })
  }

  async function handleCloseManifest() {
    if (!selectedManifest) return
    await handleUpdateManifest(selectedManifest.id, { status: 'Closed' })
    setSelectedManifest(prev => prev ? { ...prev, status: 'Closed' } : null)
    setShowDetail(false)
  }

  async function handleSubmitFromDetail() {
    if (!selectedManifest) return
    await handleUpdateManifest(selectedManifest.id, { status: 'Submitted' })
    setShowDetail(false)
  }

  function handleBOLScan() {
    if (!scanInput.trim()) {
      addToast({ type: 'error', title: 'Scan or enter BOL number' })
      return
    }
    addToast({ type: 'success', title: `BOL ${scanInput} recorded` })
    setScanInput('')
  }

  function handleSign() {
    if (!signature.trim()) {
      addToast({ type: 'error', title: 'Please provide a signature' })
      return
    }
    addToast({ type: 'success', title: 'BOL signed and closed out' })
    setSignature('')
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <ClipboardList className="w-5 h-5" />Shipping Manifests
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-0.5">End-of-day carrier manifests</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-4">
        <div className="enterprise-card p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-blue-500 flex items-center justify-center text-white shrink-0">
            <ClipboardList className="w-5 h-5" />
          </div>
          <div>
            <p className="text-xs text-[var(--text-tertiary)]">Total Manifests</p>
            <p className="text-xl font-bold text-[var(--text-primary)]">{TOTALS.totalManifests}</p>
          </div>
        </div>
        <div className="enterprise-card p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-purple-500 flex items-center justify-center text-white shrink-0">
            <Package className="w-5 h-5" />
          </div>
          <div>
            <p className="text-xs text-[var(--text-tertiary)]">Total Shipments</p>
            <p className="text-xl font-bold text-[var(--text-primary)]">{TOTALS.totalShipments.toLocaleString()}</p>
          </div>
        </div>
        <div className="enterprise-card p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-green-500 flex items-center justify-center text-white shrink-0">
            <DollarSign className="w-5 h-5" />
          </div>
          <div>
            <p className="text-xs text-[var(--text-tertiary)]">Total Cost</p>
            <p className="text-xl font-bold text-[var(--text-primary)]">${TOTALS.totalCost.toFixed(2)}</p>
          </div>
        </div>
        <div className="enterprise-card p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-amber-500 flex items-center justify-center text-white shrink-0">
            <Truck className="w-5 h-5" />
          </div>
          <div>
            <p className="text-xs text-[var(--text-tertiary)]">Avg Cost / Shipment</p>
            <p className="text-xl font-bold text-[var(--text-primary)]">${TOTALS.avgCost.toFixed(2)}</p>
          </div>
        </div>
      </div>

      <div className="enterprise-card p-5">
        <div className="flex items-center gap-2 mb-4">
          <div className="w-8 h-8 rounded-lg bg-blue-500 flex items-center justify-center text-white">
            <Plus className="w-4 h-4" />
          </div>
          <div>
            <h2 className="font-semibold text-[var(--text-primary)]">Create Manifest</h2>
            <p className="text-xs text-[var(--text-tertiary)]">Generate an end-of-day carrier manifest</p>
          </div>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
          <div className="space-y-1.5">
            <label className="block text-xs font-medium text-[var(--text-secondary)]">Carrier</label>
            <select value={createCarrier} onChange={e => setCreateCarrier(e.target.value)}
              className="enterprise-input w-full">
              {CARRIERS.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div className="space-y-1.5">
            <label className="block text-xs font-medium text-[var(--text-secondary)]">Date From</label>
            <input type="date" value={dateFrom} onChange={e => setDateFrom(e.target.value)}
              className="enterprise-input w-full" />
          </div>
          <div className="space-y-1.5">
            <label className="block text-xs font-medium text-[var(--text-secondary)]">Date To</label>
            <input type="date" value={dateTo} onChange={e => setDateTo(e.target.value)}
              className="enterprise-input w-full" />
          </div>
          <div className="flex items-end">
            <button onClick={handleFetchShipments} disabled={isFetching}
              className="enterprise-btn enterprise-btn-primary w-full justify-center">
              {isFetching ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
              Fetch Shipments
            </button>
          </div>
        </div>

        {fetched && (
          <div className="border border-[var(--border-color)] rounded-lg overflow-hidden">
            <div className="p-3 bg-[var(--bg-tertiary)] border-b border-[var(--border-color)] flex items-center justify-between">
              <span className="text-sm font-medium text-[var(--text-primary)]">
                {selectedShipments.length} of {fetchedShipments.length} shipments selected
              </span>
              <div className="flex items-center gap-2">
                <button onClick={() => setSelectedShipments(fetchedShipments.map(s => s.id))}
                  className="text-xs text-blue-600 hover:underline">Select All</button>
                <button onClick={() => setSelectedShipments([])}
                  className="text-xs text-[var(--text-tertiary)] hover:underline">Clear</button>
              </div>
            </div>
            <div className="max-h-60 overflow-y-auto divide-y divide-[var(--border-subtle)]">
              {fetchedShipments.map(s => (
                <label key={s.id}
                  className={clsx(
                    'flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors text-sm',
                    selectedShipments.includes(s.id) ? 'bg-blue-50 dark:bg-blue-900/10' : 'hover:bg-[var(--bg-tertiary)]'
                  )}>
                  <input type="checkbox" checked={selectedShipments.includes(s.id)}
                    onChange={() => toggleShipment(s.id)}
                    className="w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
                  <div className="flex-1 flex items-center justify-between">
                    <div>
                      <span className="text-[var(--text-primary)] font-medium">{s.orderId}</span>
                      <span className="text-[var(--text-tertiary)] ml-2 text-xs">{s.service}</span>
                    </div>
                    <div className="flex items-center gap-4 text-xs text-[var(--text-secondary)]">
                      <span>{s.weight} lbs</span>
                      <span className="font-mono">${s.cost.toFixed(2)}</span>
                      <span className="text-[var(--text-tertiary)]">{s.destination}</span>
                    </div>
                  </div>
                </label>
              ))}
            </div>
            <div className="p-3 bg-[var(--bg-tertiary)] border-t border-[var(--border-color)] flex justify-end">
                <button onClick={() => handleCreateManifest({
                      carrier: createCarrier,
                      date: new Date().toISOString().split('T')[0],
                      shipments: fetchedShipments.filter(s => selectedShipments.includes(s.id)),
                    })}
                className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors inline-flex items-center gap-2 text-sm font-medium">
                <FileText className="w-4 h-4" /> Generate Manifest
              </button>
            </div>
          </div>
        )}
      </div>

      <div className="enterprise-card overflow-hidden">
        <div className="p-4 border-b border-[var(--border-color)] flex items-center justify-between flex-wrap gap-3">
          <h3 className="font-semibold text-[var(--text-primary)] flex items-center gap-2">
            <ClipboardList className="w-4 h-4 text-[var(--text-tertiary)]" /> Manifests
          </h3>
          <Autocomplete value={searchManifest} onChange={setSearchManifest} placeholder="Search manifests..." minChars={0} />
        </div>
        <div className="overflow-x-auto">
          <table className="enterprise-table w-full text-sm">
            <thead>
              <tr>
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Manifest ID</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Carrier</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Date</th>
                <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Shipments</th>
                <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Total Weight</th>
                <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Total Cost</th>
                <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--border-subtle)]">
              {filteredManifests.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-sm text-[var(--text-tertiary)]">
                    No manifests found
                  </td>
                </tr>
              ) : (
                filteredManifests.map(m => (
                  <tr key={m.id} className="enterprise-table-row">
                    <td className="px-4 py-3 font-mono text-xs text-[var(--text-primary)]">{m.id}</td>
                    <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{m.carrier}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{m.date}</td>
                    <td className="px-4 py-3 text-right text-sm text-[var(--text-secondary)]">{m.shipments.length}</td>
                    <td className="px-4 py-3 text-right text-sm text-[var(--text-secondary)]">{m.totalWeight.toFixed(1)} lbs</td>
                    <td className="px-4 py-3 text-right text-sm font-mono text-[var(--text-primary)]">${m.totalCost.toFixed(2)}</td>
                    <td className="px-4 py-3 text-center">
                      <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', STATUS_STYLES[m.status])}>
                        {m.status}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-center gap-1">
                        <button onClick={() => handleViewDetail(m)}
                          className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-blue-600 transition-colors"
                          title="View">
                          <Eye className="w-3.5 h-3.5" />
                        </button>
                        <button onClick={() => handleDownload(m)}
                          className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-green-600 transition-colors"
                          title="Download">
                          <Download className="w-3.5 h-3.5" />
                        </button>
                        {m.status !== 'Submitted' && (
                          <button onClick={() => handleUpdateManifest(m.id, { status: 'Submitted' })}
                            className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-blue-600 transition-colors"
                            title="Submit">
                            <Send className="w-3.5 h-3.5" />
                          </button>
                        )}
                        <button onClick={() => handleVoid(m)}
                          className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-red-600 transition-colors"
                          title="Void">
                          <Ban className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="enterprise-card p-5">
          <div className="flex items-center gap-2 mb-4">
            <div className="w-8 h-8 rounded-lg bg-green-500 flex items-center justify-center text-white">
              <DollarSign className="w-4 h-4" />
            </div>
            <div>
              <h2 className="font-semibold text-[var(--text-primary)]">End-of-Day Summary</h2>
              <p className="text-xs text-[var(--text-tertiary)]">Today's manifest overview</p>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-[var(--bg-tertiary)] rounded-lg p-4">
              <p className="text-xs text-[var(--text-tertiary)]">Total Manifests</p>
              <p className="text-2xl font-bold text-[var(--text-primary)]">{TOTALS.totalManifests}</p>
            </div>
            <div className="bg-[var(--bg-tertiary)] rounded-lg p-4">
              <p className="text-xs text-[var(--text-tertiary)]">Total Shipments</p>
              <p className="text-2xl font-bold text-[var(--text-primary)]">{TOTALS.totalShipments.toLocaleString()}</p>
            </div>
            <div className="bg-[var(--bg-tertiary)] rounded-lg p-4">
              <p className="text-xs text-[var(--text-tertiary)]">Total Cost</p>
              <p className="text-2xl font-bold text-[var(--text-primary)]">${TOTALS.totalCost.toFixed(2)}</p>
            </div>
            <div className="bg-[var(--bg-tertiary)] rounded-lg p-4">
              <p className="text-xs text-[var(--text-tertiary)]">Avg Cost / Shipment</p>
              <p className="text-2xl font-bold text-[var(--text-primary)]">${TOTALS.avgCost.toFixed(2)}</p>
            </div>
          </div>
        </div>

        <div className="enterprise-card p-5">
          <div className="flex items-center gap-2 mb-4">
            <div className="w-8 h-8 rounded-lg bg-amber-500 flex items-center justify-center text-white">
              <PenLine className="w-4 h-4" />
            </div>
            <div>
              <h2 className="font-semibold text-[var(--text-primary)]">Carrier Close-out</h2>
              <p className="text-xs text-[var(--text-tertiary)]">Scan / digitally sign BOL</p>
            </div>
          </div>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-xs font-medium text-[var(--text-secondary)]">Scan BOL Number</label>
              <div className="flex gap-2">
                <input value={scanInput} onChange={e => setScanInput(e.target.value)}
                  placeholder="Scan or type BOL number..."
                  className="enterprise-input flex-1" />
                <button onClick={handleBOLScan}
                  className="enterprise-btn enterprise-btn-secondary">
                  <Search className="w-4 h-4" /> Scan
                </button>
              </div>
            </div>
            <div className="space-y-1.5">
              <label className="block text-xs font-medium text-[var(--text-secondary)]">Digital Signature</label>
              <textarea value={signature} onChange={e => setSignature(e.target.value)}
                placeholder="Type your full name to sign..."
                rows={2} className="enterprise-input w-full" />
            </div>
            <button onClick={handleSign}
              className="enterprise-btn enterprise-btn-primary w-full justify-center">
              <PenLine className="w-4 h-4" /> Sign & Close Out
            </button>
          </div>
        </div>
      </div>

      {showDetail && selectedManifest && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
          onClick={() => setShowDetail(false)}>
          <div className="enterprise-card p-6 w-full max-w-3xl mx-4 max-h-[85vh] overflow-y-auto"
            onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-6">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-blue-500 flex items-center justify-center text-white">
                  <ClipboardList className="w-5 h-5" />
                </div>
                <div>
                  <h2 className="text-lg font-semibold text-[var(--text-primary)]">Manifest {selectedManifest.id}</h2>
                  <p className="text-xs text-[var(--text-tertiary)]">{selectedManifest.carrier} · {selectedManifest.date}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className={clsx('inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium', STATUS_STYLES[selectedManifest.status])}>
                  {selectedManifest.status}
                </span>
                <button onClick={() => setShowDetail(false)}
                  className="p-1 hover:bg-[var(--bg-tertiary)] rounded transition-colors">
                  <X className="w-5 h-5 text-[var(--text-secondary)]" />
                </button>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4 mb-6">
              <div className="bg-[var(--bg-tertiary)] rounded-lg p-3 text-center">
                <p className="text-xs text-[var(--text-tertiary)]">Total Packages</p>
                <p className="text-xl font-bold text-[var(--text-primary)]">{selectedManifest.shipments.length}</p>
              </div>
              <div className="bg-[var(--bg-tertiary)] rounded-lg p-3 text-center">
                <p className="text-xs text-[var(--text-tertiary)]">Total Weight</p>
                <p className="text-xl font-bold text-[var(--text-primary)]">{selectedManifest.totalWeight.toFixed(1)} lbs</p>
              </div>
              <div className="bg-[var(--bg-tertiary)] rounded-lg p-3 text-center">
                <p className="text-xs text-[var(--text-tertiary)]">Total Cost</p>
                <p className="text-xl font-bold text-[var(--text-primary)]">${selectedManifest.totalCost.toFixed(2)}</p>
              </div>
            </div>

            {selectedManifest.bolNumber && (
              <div className="mb-4 p-3 bg-blue-50 dark:bg-blue-900/10 rounded-lg border border-blue-200 dark:border-blue-800">
                <p className="text-xs text-[var(--text-tertiary)]">BOL Number</p>
                <p className="text-sm font-mono font-semibold text-blue-700 dark:text-blue-400">{selectedManifest.bolNumber}</p>
              </div>
            )}

            <h4 className="text-sm font-semibold text-[var(--text-primary)] mb-3">Shipments</h4>
            <div className="overflow-x-auto border border-[var(--border-color)] rounded-lg">
              <table className="enterprise-table w-full text-sm">
                <thead>
                  <tr>
                    <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order</th>
                    <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Tracking</th>
                    <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Service</th>
                    <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Weight</th>
                    <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Cost</th>
                    <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Destination</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border-subtle)]">
                  {selectedManifest.shipments.map(s => (
                    <tr key={s.id} className="enterprise-table-row">
                      <td className="px-3 py-2 font-medium text-[var(--text-primary)]">{s.orderId}</td>
                      <td className="px-3 py-2 font-mono text-xs text-[var(--color-primary)]">{s.tracking}</td>
                      <td className="px-3 py-2 text-[var(--text-secondary)]">{s.service}</td>
                      <td className="px-3 py-2 text-right text-[var(--text-secondary)]">{s.weight} lbs</td>
                      <td className="px-3 py-2 text-right font-mono text-[var(--text-primary)]">${s.cost.toFixed(2)}</td>
                      <td className="px-3 py-2 text-[var(--text-secondary)]">{s.destination}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex justify-end gap-3 mt-6">
              <button onClick={() => setShowDetail(false)}
                className="enterprise-btn enterprise-btn-secondary">Close</button>
              {selectedManifest.status === 'Draft' && (
                <button onClick={handleCloseManifest}
                  className="enterprise-btn enterprise-btn-primary">
                  <CheckCircle className="w-4 h-4" /> Close Manifest
                </button>
              )}
              {selectedManifest.status !== 'Submitted' && (
                <button onClick={handleSubmitFromDetail}
                  className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors inline-flex items-center gap-2 text-sm font-medium">
                  <Send className="w-4 h-4" /> Submit Manifest
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
