import { useState, useMemo, useEffect } from 'react'
import {
  Printer, Search, Plus, X, Loader2, CheckCircle, AlertCircle,
  RefreshCw, Trash2, Download, FileText, Box, Settings, ChevronRight,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import clsx from 'clsx'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import { generateLabel, generateBulkLabels, fetchLabels, fetchCarriers, fetchOrders } from '../api/newBackend'

interface Label {
  id: string
  orderId: string
  carrier: string
  service: string
  weight: number
  cost: number
  tracking: string
  status: 'Generated' | 'Printed' | 'Shipped'
  createdAt: string
}

const CARRIERS = ['FedEx', 'UPS', 'USPS', 'DHL'] as const

const SERVICE_LEVELS = ['Ground', '2Day', 'Overnight', 'International'] as const

const STATUS_STYLES: Record<string, string> = {
  Generated: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]',
  Printed: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)] dark:bg-[var(--nexus-ai-900)]/30 dark:text-[var(--nexus-ai-400)]',
  Shipped: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] dark:bg-[var(--nexus-success-900)]/30 dark:text-[var(--nexus-success-400)]',
}

export default function LabelPrintingPage() {
  const { addToast } = useToast()

  const [labels, setLabels] = useState<any[]>([])
  const [carriers, setCarriers] = useState<any[]>([])
  const [orders, setOrders] = useState<string[]>([])

  useEffect(() => {
    Promise.all([fetchLabels(), fetchCarriers(), fetchOrders()]).then(([l, c, o]) => {
      if (l?.labels) setLabels(l.labels)
      if (c?.carriers) setCarriers(c.carriers)
      if (o?.orders) setOrders(o.orders.map((ord: any) => ord.orderNumber || ord.id).filter(Boolean))
    }).catch((err) => {
      addToast({ type: 'error', title: 'Failed to load data', description: err?.message })
    })
  }, [addToast])

  const [createOrder, setCreateOrder] = useState('')
  const [createCarrier, setCreateCarrier] = useState<string>('FedEx')
  const [createService, setCreateService] = useState<string>('Ground')
  const [createWeight, setCreateWeight] = useState('2.0')
  const [createLength, setCreateLength] = useState('12')
  const [createWidth, setCreateWidth] = useState('10')
  const [createHeight, setCreateHeight] = useState('5')
  const [createPackages, setCreatePackages] = useState('1')

  const [searchLabel, setSearchLabel] = useState('')

  const [bulkOrders, setBulkOrders] = useState<string[]>([])
  const [bulkProgress, setBulkProgress] = useState(0)
  const [isBulkGenerating, setIsBulkGenerating] = useState(false)

  const [printerName, setPrinterName] = useState('Zebra ZD621')
  const [labelSize, setLabelSize] = useState('4x6')
  const [copies, setCopies] = useState('1')

  const [showRecent, setShowRecent] = useState(true)

  const filteredLabels = useMemo(() => {
    if (!searchLabel) return labels
    const q = searchLabel.toLowerCase()
    return labels.filter(l =>
      l.id.toLowerCase().includes(q) ||
      l.orderId.toLowerCase().includes(q) ||
      l.tracking.toLowerCase().includes(q) ||
      l.carrier.toLowerCase().includes(q)
    )
  }, [labels, searchLabel])

  async function handleGenerate() {
    if (!createOrder) {
      addToast({ type: 'error', title: 'Please select an order' })
      return
    }
    const res = await generateLabel({ carrier: createCarrier, service: createService, weight: parseFloat(createWeight), dimensions: { length: parseFloat(createLength), width: parseFloat(createWidth), height: parseFloat(createHeight) } })
    if (res?.label) {
      setLabels(prev => [res.label, ...prev])
      setCreateOrder('')
      addToast({ type: 'success', title: `Label ${res.label.trackingNumber} generated` })
    }
  }

  function handleReprint(label: Label) {
    addToast({ type: 'success', title: `Reprinting label ${label.id}` })
  }

  function handleVoid(label: Label) {
    setLabels(prev => prev.filter(l => l.id !== label.id))
    addToast({ type: 'success', title: `Label ${label.id} voided` })
  }

  async function handleBulkGenerate() {
    const res = await generateBulkLabels(bulkOrders.length)
    if (res?.labels) {
      setLabels(prev => [...res.labels, ...prev])
      addToast({ type: 'success', title: `${res.labels.length} labels generated` })
    }
  }

  function toggleBulkOrder(orderId: string) {
    setBulkOrders(prev =>
      prev.includes(orderId) ? prev.filter(o => o !== orderId) : [...prev, orderId]
    )
  }

  function handleTestPrint() {
    addToast({ type: 'success', title: `Test page sent to ${printerName}` })
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Printer className="w-5 h-5" />Label Printing
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-0.5">Generate & print shipping labels</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <div className="enterprise-card p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 rounded-lg bg-[var(--nexus-primary-50)]0 flex items-center justify-center text-white">
                <FileText className="w-4 h-4" />
              </div>
              <div>
                <h2 className="font-semibold text-[var(--text-primary)]">Create Label</h2>
                <p className="text-xs text-[var(--text-tertiary)]">Generate a new shipping label</p>
              </div>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Order</label>
                <select value={createOrder} onChange={e => setCreateOrder(e.target.value)}
                  className="enterprise-input w-full">
                  <option value="">Select order...</option>
                  {orders.map(o => <option key={o} value={o}>{o}</option>)}
                </select>
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Carrier</label>
                <select value={createCarrier} onChange={e => setCreateCarrier(e.target.value)}
                  className="enterprise-input w-full">
                  {CARRIERS.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Service Level</label>
                <select value={createService} onChange={e => setCreateService(e.target.value)}
                  className="enterprise-input w-full">
                  {SERVICE_LEVELS.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Weight (lbs)</label>
                <input type="number" step="0.1" min="0" value={createWeight}
                  onChange={e => setCreateWeight(e.target.value)} className="enterprise-input w-full" />
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Length (in)</label>
                <input type="number" step="0.1" min="0" value={createLength}
                  onChange={e => setCreateLength(e.target.value)} className="enterprise-input w-full" />
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Width (in)</label>
                <input type="number" step="0.1" min="0" value={createWidth}
                  onChange={e => setCreateWidth(e.target.value)} className="enterprise-input w-full" />
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Height (in)</label>
                <input type="number" step="0.1" min="0" value={createHeight}
                  onChange={e => setCreateHeight(e.target.value)} className="enterprise-input w-full" />
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Packages</label>
                <input type="number" min="1" value={createPackages}
                  onChange={e => setCreatePackages(e.target.value)} className="enterprise-input w-full" />
              </div>
            </div>
            <div className="mt-4 flex justify-end">
              <PermissionGate resource="settings" action="create">
                <button onClick={handleGenerate}
                  className="bg-[var(--nexus-primary-600)] text-white px-4 py-2 rounded-lg hover:bg-[var(--nexus-primary-700)] transition-colors inline-flex items-center gap-2 text-sm font-medium">
                  <Plus className="w-4 h-4" /> Generate Label
                </button>
              </PermissionGate>
            </div>
          </div>

          <div className="enterprise-card overflow-hidden">
            <div className="p-4 border-b border-[var(--border-color)] flex items-center justify-between flex-wrap gap-3">
              <h3 className="font-semibold text-[var(--text-primary)] flex items-center gap-2">
                <FileText className="w-4 h-4 text-[var(--text-tertiary)]" /> Generated Labels
              </h3>
              <Autocomplete value={searchLabel} onChange={setSearchLabel} placeholder="Search labels..." minChars={0} />
            </div>
            <div className="overflow-x-auto">
              <table className="enterprise-table w-full text-sm">
                <thead>
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Label ID</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Carrier</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Service</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Weight</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Cost</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Tracking</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border-subtle)]">
                  {filteredLabels.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="px-4 py-8 text-center text-sm text-[var(--text-tertiary)]">
                        No labels found
                      </td>
                    </tr>
                  ) : (
                    filteredLabels.map(label => (
                      <tr key={label.id} className="enterprise-table-row">
                        <td className="px-4 py-3 font-mono text-xs text-[var(--text-primary)]">{label.id}</td>
                        <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{label.orderId}</td>
                        <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{label.carrier}</td>
                        <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{label.service}</td>
                        <td className="px-4 py-3 text-right text-sm text-[var(--text-secondary)]">{label.weight} lbs</td>
                        <td className="px-4 py-3 text-right text-sm font-mono text-[var(--text-primary)]">${label.cost.toFixed(2)}</td>
                        <td className="px-4 py-3 font-mono text-xs text-[var(--color-primary)]">{label.tracking}</td>
                        <td className="px-4 py-3 text-center">
                          <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', STATUS_STYLES[label.status])}>
                            {label.status}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center justify-center gap-1">
                            <PermissionGate resource="settings" action="edit">
                              <button onClick={() => handleReprint(label)}
                                className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-[var(--nexus-primary-600)] transition-colors"
                                title="Reprint">
                                <RefreshCw className="w-3.5 h-3.5" />
                              </button>
                            </PermissionGate>
                            <PermissionGate resource="settings" action="delete">
                              <button onClick={() => handleVoid(label)}
                                className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-[var(--nexus-error-600)] transition-colors"
                                title="Void">
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </PermissionGate>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="enterprise-card p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 rounded-lg bg-[var(--nexus-ai-50)]0 flex items-center justify-center text-white">
                <Box className="w-4 h-4" />
              </div>
              <div>
                <h2 className="font-semibold text-[var(--text-primary)]">Bulk Generation</h2>
                <p className="text-xs text-[var(--text-tertiary)]">Generate labels for multiple orders at once</p>
              </div>
            </div>
            <div className="border border-[var(--border-color)] rounded-lg divide-y divide-[var(--border-color)] max-h-48 overflow-y-auto mb-4">
              {orders.map(orderId => (
                <label key={orderId}
                  className={clsx(
                    'flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors text-sm',
                    bulkOrders.includes(orderId) ? 'bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/10' : 'hover:bg-[var(--bg-tertiary)]'
                  )}>
                  <input type="checkbox" checked={bulkOrders.includes(orderId)}
                    onChange={() => toggleBulkOrder(orderId)}
                    className="w-4 h-4 rounded border-[var(--border-default)] text-[var(--nexus-primary-600)] focus:ring-blue-500" />
                  <span className="text-[var(--text-primary)]">{orderId}</span>
                </label>
              ))}
            </div>
            {isBulkGenerating && (
              <div className="mb-4 space-y-1.5">
                <div className="flex items-center justify-between text-xs text-[var(--text-secondary)]">
                  <span>Generating labels...</span>
                  <span>{Math.round(bulkProgress)}%</span>
                </div>
                <div className="w-full h-2 bg-[var(--bg-tertiary)] rounded-full overflow-hidden">
                  <div className="h-full bg-[var(--nexus-primary-600)] rounded-full transition-all duration-300"
                    style={{ width: `${bulkProgress}%` }} />
                </div>
              </div>
            )}
            <div className="flex items-center justify-between">
              <span className="text-xs text-[var(--text-tertiary)]">{bulkOrders.length} order(s) selected</span>
              <PermissionGate resource="settings" action="create">
                <button onClick={handleBulkGenerate} disabled={bulkOrders.length === 0 || isBulkGenerating}
                  className="bg-[var(--nexus-primary-600)] text-white px-4 py-2 rounded-lg hover:bg-[var(--nexus-primary-700)] transition-colors inline-flex items-center gap-2 text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed">
                  {isBulkGenerating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                  Generate Labels for Selected
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>

        <div className="space-y-6">
          <div className="enterprise-card p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 rounded-lg bg-[var(--nexus-success-50)]0 flex items-center justify-center text-white">
                <Settings className="w-4 h-4" />
              </div>
              <div>
                <h2 className="font-semibold text-[var(--text-primary)]">Printer Configuration</h2>
                <p className="text-xs text-[var(--text-tertiary)]">Default print settings</p>
              </div>
            </div>
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Default Printer</label>
                <select value={printerName} onChange={e => setPrinterName(e.target.value)}
                  className="enterprise-input w-full">
                  <option>Zebra ZD621</option>
                  <option>Zebra ZT411</option>
                  <option>Brother QL-820NWB</option>
                  <option>DYMO LabelWriter 550</option>
                </select>
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Label Size</label>
                <select value={labelSize} onChange={e => setLabelSize(e.target.value)}
                  className="enterprise-input w-full">
                  <option value="4x6">4 × 6 in</option>
                  <option value="4x8">4 × 8 in</option>
                </select>
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Copies</label>
                <input type="number" min="1" max="10" value={copies}
                  onChange={e => setCopies(e.target.value)} className="enterprise-input w-full" />
              </div>
              <button onClick={handleTestPrint}
                className="enterprise-btn enterprise-btn-secondary w-full justify-center">
                <Printer className="w-4 h-4" /> Test Print
              </button>
            </div>
          </div>

          <div className="enterprise-card p-5">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-[var(--nexus-warning-50)]0 flex items-center justify-center text-white">
                  <FileText className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="font-semibold text-[var(--text-primary)]">Recent Labels</h2>
                  <p className="text-xs text-[var(--text-tertiary)]">Last 10 generated today</p>
                </div>
              </div>
              <button onClick={() => setShowRecent(!showRecent)}
                className="p-1 rounded hover:bg-[var(--bg-tertiary)] transition-colors">
                <ChevronRight className={clsx('w-4 h-4 text-[var(--text-tertiary)] transition-transform', showRecent && 'rotate-90')} />
              </button>
            </div>
            {showRecent && (
              <div className="space-y-2">
                {labels.slice(0, 10).map(label => (
                  <div key={label.id}
                    className="flex items-center justify-between p-2.5 rounded-lg bg-[var(--bg-tertiary)]/50 hover:bg-[var(--bg-tertiary)] transition-colors">
                    <div className="min-w-0 flex-1">
                      <p className="text-xs font-medium text-[var(--text-primary)] truncate">{label.orderId}</p>
                      <p className="text-[11px] text-[var(--text-tertiary)]">{label.carrier} · {label.service}</p>
                    </div>
                    <div className="flex items-center gap-2 ml-2">
                      <span className={clsx('text-[10px] px-1.5 py-0.5 rounded font-medium', STATUS_STYLES[label.status])}>
                        {label.status}
                      </span>
                      <span className="text-[11px] text-[var(--text-tertiary)]">${label.cost.toFixed(2)}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
