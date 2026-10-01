import { useState, useMemo, useEffect } from 'react'
import {
  Printer, Plus, Loader2,
  Download, ShieldCheck, Save, Trash2, FileText, Box, Settings, ChevronRight,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import clsx from 'clsx'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import {
  generateCarrierLabel, validateCarrierLabel, downloadLabel,
  fetchLabels, fetchCarriers, fetchOrders,
  fetchCarrierLabelConfigs, upsertCarrierLabelConfig, fetchCarrierAdapters,
} from '../api/newBackend'

interface Label {
  id: string
  orderId: string
  orderNumber: string
  carrier: string
  serviceType: string
  weight: number
  trackingNumber: string
  labelSource: string
  adapterName?: string
  status: string
  createdAt?: string
}

const CARRIERS = ['JITSU', 'SAPI', 'VHO', 'FEDEX', 'UPS'] as const

const SERVICE_LEVELS = ['STANDARD', 'EXPRESS', 'OVERNIGHT', 'SAME_DAY'] as const

const LABEL_FORMATS = ['PDF', 'ZPL'] as const

function normalizeStatus(status: string): string {
  switch ((status || '').toUpperCase()) {
    case 'GENERATED': return 'Generated'
    case 'PRINTED': return 'Printed'
    case 'ATTACHED': return 'Shipped'
    case 'CANCELLED': return 'Voided'
    default: return status || 'Generated'
  }
}

const STATUS_STYLES: Record<string, string> = {
  Generated: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]',
  Printed: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)] dark:bg-[var(--nexus-ai-900)]/30 dark:text-[var(--nexus-ai-400)]',
  Shipped: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] dark:bg-[var(--nexus-success-900)]/30 dark:text-[var(--nexus-success-400)]',
  Voided: 'bg-[var(--nexus-error-100)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-400)]',
}

export default function LabelPrintingPage() {
  const { addToast } = useToast()

  const [labels, setLabels] = useState<any[]>([])
  const [orders, setOrders] = useState<{ id: string; orderNumber: string }[]>([])
  const [carrierConfigs, setCarrierConfigs] = useState<any[]>([])
  const [availableAdapters, setAvailableAdapters] = useState<string[]>([])

  useEffect(() => {
    Promise.all([fetchLabels(), fetchCarriers(), fetchOrders(), fetchCarrierLabelConfigs(), fetchCarrierAdapters()]).then(([l, _c, o, cfgs, adapters]) => {
      if (l?.data) setLabels(l.data)
      if (o?.data) {
        const orderList = Array.isArray(o.data) ? o.data : (Array.isArray(o.data?.content) ? o.data.content : [])
        setOrders(orderList.map((ord: any) => ({ id: ord.id, orderNumber: ord.orderNumber || ord.id })).filter((x: any) => x.id))
      }
      if (cfgs?.data) {
        setCarrierConfigs(cfgs.data)
        const codes = cfgs.data.map((c: any) => c.carrierCode).filter(Boolean)
        if (codes.length > 0) setCreateCarrier(codes[0])
      }
      if (adapters?.data && adapters.data.length > 0) {
        setAvailableAdapters(adapters.data)
        setConfigAdapter(prev => prev || adapters.data[0])
      }
    }).catch((err) => {
      addToast({ type: 'error', title: 'Failed to load data', description: err?.message })
    })
  }, [addToast])

  const [createOrder, setCreateOrder] = useState('')
  const [createCarrier, setCreateCarrier] = useState<string>('JITSU')
  const [createService, setCreateService] = useState<string>('STANDARD')
  const [createWeight, setCreateWeight] = useState('2.0')
  const [createLength, setCreateLength] = useState('12')
  const [createWidth, setCreateWidth] = useState('10')
  const [createHeight, setCreateHeight] = useState('5')
  const [createPackages, setCreatePackages] = useState('1')
  const [createToName, setCreateToName] = useState('')
  const [createToAddress, setCreateToAddress] = useState('')

  const [searchLabel, setSearchLabel] = useState('')

  const [bulkOrders, setBulkOrders] = useState<string[]>([])
  const bulkProgress = 0
  const isBulkGenerating = false

  const [printerName, setPrinterName] = useState('Zebra ZD621')
  const [labelSize, setLabelSize] = useState('4x6')
  const [copies, setCopies] = useState('1')

  const [configCarrier, setConfigCarrier] = useState<string>('JITSU')
  const [configAdapter, setConfigAdapter] = useState<string>('')
  const [configFormat, setConfigFormat] = useState<string>('PDF')

  const [showRecent, setShowRecent] = useState(true)

  const carrierOptions = useMemo(() => {
    const codes = carrierConfigs.map(c => c.carrierCode).filter(Boolean)
    return codes.length > 0 ? codes : CARRIERS
  }, [carrierConfigs])

  const filteredLabels = useMemo(() => {
    if (!searchLabel) return labels
    const q = searchLabel.toLowerCase()
    return labels.filter(l =>
      l.id.toLowerCase().includes(q) ||
      (l.orderNumber || l.orderId || '').toLowerCase().includes(q) ||
      (l.trackingNumber || '').toLowerCase().includes(q) ||
      l.carrier.toLowerCase().includes(q)
    )
  }, [labels, searchLabel])

  async function handleGenerate() {
    if (!createOrder) {
      addToast({ type: 'error', title: 'Please select an order' })
      return
    }
    const order = orders.find(o => o.orderNumber === createOrder) || orders.find(o => o.id === createOrder)
    if (!order) {
      addToast({ type: 'error', title: 'Order not found' })
      return
    }
    const res = await generateCarrierLabel({
      orderId: order.id,
      orderNumber: order.orderNumber,
      carrier: createCarrier,
      serviceType: createService,
      weight: parseFloat(createWeight),
      dimensions: `${createLength}x${createWidth}x${createHeight}`,
      fromName: 'Nexus Fulfillment',
      fromAddress: '101 Warehouse Ave, Mumbai 400001',
      toName: createToName,
      toAddress: createToAddress,
    })
    if (res?.data) {
      setLabels(prev => [res.data, ...prev])
      setCreateOrder('')
      addToast({ type: 'success', title: `Carrier label ${res.data.trackingNumber} purchased` })
    } else {
      addToast({ type: 'error', title: 'Carrier label purchase failed', description: res?.message || 'Ensure the carrier has a label config' })
    }
  }

  async function handleDownload(label: Label) {
    const res = await downloadLabel(label.id)
    const payload = res?.data || res
    if (!payload?.labelBase64) {
      addToast({ type: 'error', title: 'No label data available for download' })
      return
    }
    const mime = payload.labelFormat === 'ZPL' ? 'application/zpl' : 'application/pdf'
    const win = window.open()
    if (win) {
      win.document.write(`<iframe src="data:${mime};base64,${payload.labelBase64}" style="width:100%;height:100%;border:0"></iframe>`)
    }
    addToast({ type: 'success', title: `Label ${label.trackingNumber} downloaded` })
  }

  async function handleValidate(label: Label) {
    const res = await validateCarrierLabel(label.id)
    if (!res) {
      addToast({ type: 'error', title: 'Validation failed' })
      return
    }
    const missing = res.data || []
    if (missing.length === 0) {
      addToast({ type: 'success', title: res.message || 'Label is valid for carrier printing' })
    } else {
      addToast({ type: 'error', title: 'Label is missing required fields', description: missing.join(', ') })
    }
  }

  function handleVoid(label: Label) {
    setLabels(prev => prev.map(l => l.id === label.id ? { ...l, status: 'CANCELLED' } : l))
    addToast({ type: 'success', title: `Label ${label.id} voided` })
  }

  async function handleBulkGenerate() {
    if (bulkOrders.length === 0) return
    let success = 0
    for (const orderId of bulkOrders) {
      const order = orders.find(o => o.id === orderId)
      if (!order) continue
      const res = await generateCarrierLabel({
        orderId: order.id,
        orderNumber: order.orderNumber,
        carrier: createCarrier,
        serviceType: createService,
        weight: parseFloat(createWeight),
        dimensions: `${createLength}x${createWidth}x${createHeight}`,
        fromName: 'Nexus Fulfillment',
        fromAddress: '101 Warehouse Ave, Mumbai 400001',
        toName: createToName,
        toAddress: createToAddress,
      })
      if (res?.data) {
        setLabels(prev => [res.data, ...prev])
        success++
      }
    }
    if (success > 0) {
      addToast({ type: 'success', title: `${success} carrier label(s) purchased` })
    } else {
      addToast({ type: 'error', title: 'No labels purchased', description: 'Ensure the carrier has a label config' })
    }
    setBulkOrders([])
  }

  function toggleBulkOrder(orderId: string) {
    setBulkOrders(prev =>
      prev.includes(orderId) ? prev.filter(o => o !== orderId) : [...prev, orderId]
    )
  }

  async function handleSaveConfig() {
    if (!configCarrier || !configAdapter) {
      addToast({ type: 'error', title: 'Select a carrier and adapter' })
      return
    }
    const res = await upsertCarrierLabelConfig({
      carrierCode: configCarrier,
      adapterName: configAdapter,
      labelFormat: configFormat,
    })
    if (res?.data || res?.success) {
      addToast({ type: 'success', title: `Config saved for ${configCarrier}` })
      const cfgs = await fetchCarrierLabelConfigs()
      if (cfgs?.data) setCarrierConfigs(cfgs.data)
    } else {
      addToast({ type: 'error', title: 'Failed to save config', description: res?.message })
    }
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
          <p className="text-sm text-[var(--text-secondary)] mt-0.5">Purchase, validate & print carrier shipping labels</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <div className="enterprise-card p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 rounded-lg bg-[var(--nexus-primary-500)] flex items-center justify-center text-white">
                <FileText className="w-4 h-4" />
              </div>
              <div>
                <h2 className="font-semibold text-[var(--text-primary)]">Purchase Carrier Label</h2>
                <p className="text-xs text-[var(--text-tertiary)]">Buy a real shipping label via the configured adapter</p>
              </div>
            </div>
            {carrierConfigs.length === 0 && (
              <p className="mb-4 text-xs text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]">
                No carrier configs found — configure a carrier in the right panel before purchasing labels.
              </p>
            )}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Order</label>
                <select value={createOrder} onChange={e => setCreateOrder(e.target.value)}
                  className="enterprise-input w-full">
                  <option value="">Select order...</option>
                  {orders.map(o => <option key={o.id} value={o.orderNumber}>{o.orderNumber}</option>)}
                </select>
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Carrier</label>
                <select value={createCarrier} onChange={e => setCreateCarrier(e.target.value)}
                  className="enterprise-input w-full">
                  {carrierOptions.map(c => <option key={c} value={c}>{c}</option>)}
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
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">To Name</label>
                <input type="text" value={createToName} placeholder="Recipient name"
                  onChange={e => setCreateToName(e.target.value)} className="enterprise-input w-full" />
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">To Address</label>
                <input type="text" value={createToAddress} placeholder="Street, City, ZIP"
                  onChange={e => setCreateToAddress(e.target.value)} className="enterprise-input w-full" />
              </div>
            </div>
            <div className="mt-4 flex justify-end">
              <PermissionGate resource="settings" action="create">
                <button type="button" onClick={handleGenerate}
                  className="bg-[var(--nexus-primary-600)] text-white px-4 py-2 rounded-lg hover:bg-[var(--nexus-primary-700)] transition-colors inline-flex items-center gap-2 text-sm font-medium">
                  <Plus className="w-4 h-4" /> Purchase Carrier Label
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
                    <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Source</th>
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
                    filteredLabels.map(label => {
                      const statusKey = normalizeStatus(label.status)
                      return (
                        <tr key={label.id} className="enterprise-table-row">
                          <td className="px-4 py-3 font-mono text-xs text-[var(--text-primary)]">{label.id}</td>
                          <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{label.orderNumber || label.orderId}</td>
                          <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{label.carrier}</td>
                          <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{label.serviceType}</td>
                          <td className="px-4 py-3 text-right text-sm text-[var(--text-secondary)]">{label.weight != null ? `${label.weight} lbs` : '—'}</td>
                          <td className="px-4 py-3 text-center">
                            <span className={clsx(
                              'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium',
                              label.labelSource === 'CARRIER'
                                ? 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]'
                                : 'bg-[var(--bg-tertiary)] text-[var(--text-tertiary)]'
                            )}>
                              {label.labelSource === 'CARRIER' ? 'Carrier' : 'Simulated'}
                            </span>
                          </td>
                          <td className="px-4 py-3 font-mono text-xs text-[var(--color-primary)]">{label.trackingNumber}</td>
                          <td className="px-4 py-3 text-center">
                            <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', STATUS_STYLES[statusKey] || STATUS_STYLES.Generated)}>
                              {statusKey}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center justify-center gap-1">
                              <PermissionGate resource="settings" action="edit">
                                <button type="button" onClick={() => handleDownload(label)}
                                  className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-[var(--nexus-primary-600)] transition-colors"
                                  title="Download">
                                  <Download className="w-3.5 h-3.5" />
                                </button>
                              </PermissionGate>
                              <PermissionGate resource="settings" action="edit">
                                <button type="button" onClick={() => handleValidate(label)}
                                  className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-[var(--nexus-success-600)] transition-colors"
                                  title="Validate">
                                  <ShieldCheck className="w-3.5 h-3.5" />
                                </button>
                              </PermissionGate>
                              <PermissionGate resource="settings" action="delete">
                                <button type="button" onClick={() => handleVoid(label)}
                                  className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-[var(--nexus-error-600)] transition-colors"
                                  title="Void">
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              </PermissionGate>
                            </div>
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="enterprise-card p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 rounded-lg bg-[var(--nexus-ai-500)] flex items-center justify-center text-white">
                <Box className="w-4 h-4" />
              </div>
              <div>
                <h2 className="font-semibold text-[var(--text-primary)]">Bulk Generation</h2>
                <p className="text-xs text-[var(--text-tertiary)]">Purchase carrier labels for multiple orders at once</p>
              </div>
            </div>
            <div className="border border-[var(--border-color)] rounded-lg divide-y divide-[var(--border-color)] max-h-48 overflow-y-auto mb-4">
              {orders.map(order => (
                <label key={order.id}
                  className={clsx(
                    'flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors text-sm',
                    bulkOrders.includes(order.id) ? 'bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/10' : 'hover:bg-[var(--bg-tertiary)]'
                  )}>
                  <input type="checkbox" checked={bulkOrders.includes(order.id)}
                    onChange={() => toggleBulkOrder(order.id)}
                    className="w-4 h-4 rounded border-[var(--border-default)] text-[var(--nexus-primary-600)] focus:ring-[var(--nexus-primary-500)]" />
                  <span className="text-[var(--text-primary)]">{order.orderNumber}</span>
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
                <button type="button" onClick={handleBulkGenerate} disabled={bulkOrders.length === 0 || isBulkGenerating}
                  className="bg-[var(--nexus-primary-600)] text-white px-4 py-2 rounded-lg hover:bg-[var(--nexus-primary-700)] transition-colors inline-flex items-center gap-2 text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed">
                  {isBulkGenerating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                  Purchase Labels for Selected
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>

        <div className="space-y-6">
          <div className="enterprise-card p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 rounded-lg bg-[var(--nexus-warning-500)] flex items-center justify-center text-white">
                <Settings className="w-4 h-4" />
              </div>
              <div>
                <h2 className="font-semibold text-[var(--text-primary)]">Carrier Label Configuration</h2>
                <p className="text-xs text-[var(--text-tertiary)]">Map carriers to adapters</p>
              </div>
            </div>
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Carrier</label>
                <select value={configCarrier} onChange={e => setConfigCarrier(e.target.value)}
                  className="enterprise-input w-full">
                  {CARRIERS.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Adapter</label>
                <select value={configAdapter} onChange={e => setConfigAdapter(e.target.value)}
                  className="enterprise-input w-full">
                  <option value="">Select adapter...</option>
                  {availableAdapters.map(a => <option key={a} value={a}>{a}</option>)}
                </select>
              </div>
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-[var(--text-secondary)]">Label Format</label>
                <select value={configFormat} onChange={e => setConfigFormat(e.target.value)}
                  className="enterprise-input w-full">
                  {LABEL_FORMATS.map(f => <option key={f} value={f}>{f}</option>)}
                </select>
              </div>
              <button type="button" onClick={handleSaveConfig}
                className="enterprise-btn enterprise-btn-secondary w-full justify-center">
                <Save className="w-4 h-4" /> Save Config
              </button>
              {carrierConfigs.length > 0 && (
                <div className="space-y-2">
                  {carrierConfigs.map(c => (
                    <div key={c.id} className="flex items-center justify-between p-2.5 rounded-lg bg-[var(--bg-tertiary)]/50 text-sm">
                      <span className="font-medium text-[var(--text-primary)]">{c.carrierCode}</span>
                      <span className="text-xs text-[var(--text-tertiary)]">{c.adapterName} · {c.labelFormat}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="enterprise-card p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 rounded-lg bg-[var(--nexus-success-500)] flex items-center justify-center text-white">
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
              <button type="button" onClick={handleTestPrint}
                className="enterprise-btn enterprise-btn-secondary w-full justify-center">
                <Printer className="w-4 h-4" /> Test Print
              </button>
            </div>
          </div>

          <div className="enterprise-card p-5">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-[var(--nexus-warning-500)] flex items-center justify-center text-white">
                  <FileText className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="font-semibold text-[var(--text-primary)]">Recent Labels</h2>
                  <p className="text-xs text-[var(--text-tertiary)]">Last 10 generated today</p>
                </div>
              </div>
              <button type="button" onClick={() => setShowRecent(!showRecent)}
                className="p-1 rounded hover:bg-[var(--bg-tertiary)] transition-colors">
                <ChevronRight className={clsx('w-4 h-4 text-[var(--text-tertiary)] transition-transform', showRecent && 'rotate-90')} />
              </button>
            </div>
            {showRecent && (
              <div className="space-y-2">
                {labels.slice(0, 10).map(label => {
                  const statusKey = normalizeStatus(label.status)
                  return (
                    <div key={label.id}
                      className="flex items-center justify-between p-2.5 rounded-lg bg-[var(--bg-tertiary)]/50 hover:bg-[var(--bg-tertiary)] transition-colors">
                      <div className="min-w-0 flex-1">
                        <p className="text-xs font-medium text-[var(--text-primary)] truncate">{label.orderNumber || label.orderId}</p>
                        <p className="text-[11px] text-[var(--text-tertiary)]">{label.carrier} · {label.serviceType}</p>
                      </div>
                      <div className="flex items-center gap-2 ml-2">
                        <span className={clsx('text-[10px] px-1.5 py-0.5 rounded font-medium', STATUS_STYLES[statusKey] || STATUS_STYLES.Generated)}>
                          {statusKey}
                        </span>
                        <span className="text-[11px] text-[var(--text-tertiary)]">{(label.trackingNumber || '').slice(0, 8)}</span>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}