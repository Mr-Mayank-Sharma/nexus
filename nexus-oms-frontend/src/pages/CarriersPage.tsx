import { useState, useEffect, useMemo } from 'react'
import { Ship, Truck, Plus, Edit, Trash2, DollarSign, CheckCircle, XCircle, Loader2, X, ChevronDown, ChevronRight, Search } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import client from '../api/client'
import { EnterpriseToolbar, EnterpriseDataGrid, EnterpriseKPICard, EnterpriseBreadcrumbs, EnterpriseStatusBadge, EnterpriseTabs, EnterpriseFormSection } from '../components/enterprise'
import type { Column } from '../components/enterprise'

interface Carrier {
  id: string
  name: string
  code: string
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'
  type: string
  accountNumber: string
  apiKey?: string
  apiSecret?: string
  otdRate: number
  avgCost: number
  totalShipments: number
  damageRate: number
  isActive: boolean
  metadata: any
}

interface CarrierRate {
  serviceType: string
  rate: number
  transitTime: string
  zone: string
}

interface CarrierFormData {
  name: string
  code: string
  type: string
  accountNumber: string
  apiKey: string
  apiSecret: string
  isActive: boolean
}

const MOCK_CARRIERS: Carrier[] = [
  { id: '1', name: 'FedEx', code: 'FEDEX', status: 'ACTIVE', type: 'SHIPPING', accountNumber: 'FDX-78291', otdRate: 96.2, avgCost: 8.45, totalShipments: 45000, damageRate: 0.8, isActive: true, metadata: {} },
  { id: '2', name: 'UPS', code: 'UPS', status: 'ACTIVE', type: 'SHIPPING', accountNumber: 'UPS-45312', otdRate: 94.8, avgCost: 9.12, totalShipments: 38000, damageRate: 1.2, isActive: true, metadata: {} },
  { id: '3', name: 'USPS', code: 'USPS', status: 'ACTIVE', type: 'SHIPPING', accountNumber: 'USPS-90876', otdRate: 91.5, avgCost: 6.78, totalShipments: 28000, damageRate: 1.5, isActive: true, metadata: {} },
  { id: '4', name: 'DHL', code: 'DHL', status: 'ACTIVE', type: 'COURIER', accountNumber: 'DHL-56432', otdRate: 97.1, avgCost: 11.34, totalShipments: 12000, damageRate: 0.5, isActive: true, metadata: {} },
  { id: '5', name: 'Regional Freight', code: 'REG-FRT', status: 'INACTIVE', type: 'FREIGHT', accountNumber: 'RF-23109', otdRate: 89.3, avgCost: 7.22, totalShipments: 3000, damageRate: 2.1, isActive: false, metadata: {} },
]

const MOCK_RATES: Record<string, CarrierRate[]> = {
  '1': [
    { serviceType: 'Ground', rate: 7.20, transitTime: '3 days', zone: 'A' },
    { serviceType: '2-Day', rate: 12.50, transitTime: '2 days', zone: 'B' },
    { serviceType: 'Overnight', rate: 22.00, transitTime: '1 day', zone: 'C' },
  ],
  '2': [
    { serviceType: 'Ground', rate: 7.85, transitTime: '3 days', zone: 'A' },
    { serviceType: '2-Day', rate: 13.20, transitTime: '2 days', zone: 'B' },
    { serviceType: 'Overnight', rate: 23.50, transitTime: '1 day', zone: 'C' },
  ],
  '3': [
    { serviceType: 'Ground', rate: 5.50, transitTime: '5 days', zone: 'A' },
    { serviceType: '2-Day', rate: 9.75, transitTime: '2 days', zone: 'B' },
    { serviceType: 'Overnight', rate: 18.00, transitTime: '1 day', zone: 'C' },
  ],
  '4': [
    { serviceType: 'Ground', rate: 9.80, transitTime: '2 days', zone: 'B' },
    { serviceType: '2-Day', rate: 15.00, transitTime: '2 days', zone: 'B' },
    { serviceType: 'Overnight', rate: 26.00, transitTime: '1 day', zone: 'C' },
  ],
  '5': [
    { serviceType: 'Ground', rate: 6.50, transitTime: '4 days', zone: 'A' },
    { serviceType: '2-Day', rate: 11.00, transitTime: '2 days', zone: 'B' },
    { serviceType: 'Overnight', rate: 20.00, transitTime: '1 day', zone: 'C' },
  ],
}

const OTD_HISTORY: Record<string, number[]> = {
  '1': [95.8, 96.1, 95.9, 96.5, 96.3, 96.2],
  '2': [94.2, 94.5, 94.0, 94.8, 95.1, 94.8],
  '3': [90.5, 91.0, 90.8, 91.5, 91.8, 91.5],
  '4': [96.5, 96.8, 97.0, 97.2, 97.1, 97.1],
  '5': [88.5, 89.0, 88.8, 89.2, 89.5, 89.3],
}

const MONTH_LABELS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun']

const SERVICE_TYPES = ['Ground', '2-Day', 'Overnight']

export async function fetchCarriers(): Promise<Carrier[]> {
  try {
    const { data } = await client.get('/carriers')
    return data.data ?? data
  } catch {
    return MOCK_CARRIERS
  }
}

export async function createCarrierApi(payload: CarrierFormData): Promise<Carrier> {
  const { data } = await client.post('/carriers', payload)
  return data.data ?? data
}

export async function updateCarrierApi(id: string, payload: Partial<CarrierFormData>): Promise<Carrier> {
  const { data } = await client.put(`/carriers/${id}`, payload)
  return data.data ?? data
}

export async function deleteCarrierApi(id: string): Promise<void> {
  await client.delete(`/carriers/${id}`)
}

const fmtCurrency = (v: number) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(v)
const fmtNumber = (v: number) => v.toLocaleString()

function OTDBadge({ value }: { value: number }) {
  const color =
    value >= 95
      ? 'text-green-700 bg-green-100 dark:bg-green-900/30'
      : value >= 90
        ? 'text-yellow-700 bg-yellow-100 dark:bg-yellow-900/30'
        : 'text-red-700 bg-red-100 dark:bg-red-900/30'
  return <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${color}`}>{value.toFixed(1)}%</span>
}

export default function CarriersPage() {
  const [carriers, setCarriers] = useState<Carrier[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [activeTab, setActiveTab] = useState('all')
  const [showModal, setShowModal] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editingCarrier, setEditingCarrier] = useState<Carrier | null>(null)
  const [expandedCarrier, setExpandedCarrier] = useState<Carrier | null>(null)
  const [form, setForm] = useState<CarrierFormData>({
    name: '', code: '', type: 'SHIPPING', accountNumber: '', apiKey: '', apiSecret: '', isActive: true,
  })
  const { addToast } = useToast()

  useEffect(() => { loadCarriers() }, [])

  async function loadCarriers() {
    setLoading(true)
    try {
      const data = await fetchCarriers()
      setCarriers(data)
    } catch {
      setCarriers(MOCK_CARRIERS)
    } finally {
      setLoading(false)
    }
  }

  const activeCarriers = carriers.filter(c => c.status === 'ACTIVE')
  const totalShipments = carriers.reduce((s, c) => s + c.totalShipments, 0)
  const avgOTD = carriers.length > 0 ? carriers.reduce((s, c) => s + c.otdRate, 0) / carriers.length : 0
  const avgCostAll = carriers.length > 0 ? carriers.reduce((s, c) => s + c.avgCost, 0) / carriers.length : 0

  const filteredCarriers = useMemo(() => {
    if (!search) return carriers
    const q = search.toLowerCase()
    return carriers.filter(c =>
      c.name.toLowerCase().includes(q) || c.code.toLowerCase().includes(q) || c.type.toLowerCase().includes(q)
    )
  }, [carriers, search])

  function openAddModal() {
    setEditingCarrier(null)
    setForm({ name: '', code: '', type: 'SHIPPING', accountNumber: '', apiKey: '', apiSecret: '', isActive: true })
    setShowModal(true)
  }

  function openEditModal(carrier: Carrier) {
    setEditingCarrier(carrier)
    setForm({
      name: carrier.name,
      code: carrier.code,
      type: carrier.type,
      accountNumber: carrier.accountNumber,
      apiKey: carrier.apiKey || '',
      apiSecret: carrier.apiSecret || '',
      isActive: carrier.isActive,
    })
    setShowModal(true)
  }

  async function handleSave() {
    if (!form.name || !form.code) {
      addToast({ type: 'error', title: 'Name and Code are required' })
      return
    }
    setSaving(true)
    try {
      if (editingCarrier) {
        const updated = await updateCarrierApi(editingCarrier.id, form)
        setCarriers(prev => prev.map(c => c.id === editingCarrier.id ? { ...c, ...updated } : c))
        addToast({ type: 'success', title: `${form.name} updated` })
      } else {
        const created = await createCarrierApi(form)
        setCarriers(prev => [...prev, created])
        addToast({ type: 'success', title: `${form.name} added` })
      }
      setShowModal(false)
    } catch {
      const mockCarrier: Carrier = {
        id: editingCarrier ? editingCarrier.id : String(Date.now()),
        name: form.name,
        code: form.code,
        status: form.isActive ? 'ACTIVE' : 'INACTIVE',
        type: form.type,
        accountNumber: form.accountNumber,
        apiKey: form.apiKey,
        apiSecret: form.apiSecret,
        otdRate: editingCarrier?.otdRate ?? 0,
        avgCost: editingCarrier?.avgCost ?? 0,
        totalShipments: editingCarrier?.totalShipments ?? 0,
        damageRate: editingCarrier?.damageRate ?? 0,
        isActive: form.isActive,
        metadata: {},
      }
      if (editingCarrier) {
        setCarriers(prev => prev.map(c => c.id === editingCarrier.id ? mockCarrier : c))
        addToast({ type: 'success', title: `${form.name} updated (offline)` })
      } else {
        setCarriers(prev => [...prev, mockCarrier])
        addToast({ type: 'success', title: `${form.name} added (offline)` })
      }
      setShowModal(false)
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(carrier: Carrier) {
    try {
      await deleteCarrierApi(carrier.id)
      setCarriers(prev => prev.filter(c => c.id !== carrier.id))
      addToast({ type: 'success', title: `${carrier.name} removed` })
    } catch {
      setCarriers(prev => prev.filter(c => c.id !== carrier.id))
      addToast({ type: 'success', title: `${carrier.name} removed (offline)` })
    }
    if (expandedCarrier?.id === carrier.id) setExpandedCarrier(null)
  }

  function handleRowClick(carrier: Carrier) {
    setExpandedCarrier(prev => prev?.id === carrier.id ? null : carrier)
  }

  const tabs = [
    { id: 'all', label: 'All Carriers', badge: carriers.length },
    { id: 'rates', label: 'Rate Comparison' },
  ]

  // Rate comparison data
  const rateComparison = useMemo(() => {
    return SERVICE_TYPES.map(st => {
      const entries = carriers.map(c => {
        const rates = MOCK_RATES[c.id] || []
        const rate = rates.find(r => r.serviceType === st)
        return { carrier: c, rate }
      })
      const best = entries
        .filter(e => e.rate && c.status !== 'INACTIVE')
        .sort((a, b) => (a.rate?.rate ?? Infinity) - (b.rate?.rate ?? Infinity))[0]
      return { serviceType: st, entries, bestCarrier: best?.carrier }
    })
  }, [carriers])

  const columns: Column<Carrier>[] = [
    {
      key: 'name', label: 'Name / Code', sortable: true, minWidth: '200px',
      render: (_, row) => (
        <div className="flex items-center gap-3">
          <div className={`w-8 h-8 rounded-lg flex items-center justify-center text-white text-xs font-bold
            ${row.status === 'ACTIVE' ? 'bg-blue-500' : row.status === 'INACTIVE' ? 'bg-gray-400' : 'bg-red-400'}`}>
            {row.name.charAt(0)}
          </div>
          <div>
            <p className="font-medium text-[var(--text-primary)]">{row.name}</p>
            <p className="text-xs text-[var(--text-tertiary)] font-mono">{row.code}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'type', label: 'Type', sortable: true,
      render: v => <span className="text-sm text-[var(--text-secondary)]">{v}</span>,
    },
    {
      key: 'status', label: 'Status', sortable: true, width: '120px',
      render: v => <EnterpriseStatusBadge status={v === 'ACTIVE' ? 'success' : v === 'INACTIVE' ? 'neutral' : 'error'} label={v} />,
    },
    {
      key: 'otdRate', label: 'On-Time Rate', sortable: true, align: 'center', width: '140px',
      render: v => <OTDBadge value={v} />,
    },
    {
      key: 'avgCost', label: 'Avg Cost', sortable: true, align: 'right', width: '120px',
      render: v => <span className="text-sm font-medium text-[var(--text-primary)]">{fmtCurrency(v)}</span>,
    },
    {
      key: 'totalShipments', label: 'Shipments', sortable: true, align: 'right', width: '120px',
      render: v => <span className="text-sm text-[var(--text-secondary)]">{fmtNumber(v)}</span>,
    },
    {
      key: 'actions', label: 'Actions', width: '100px', align: 'center',
      render: (_, row) => (
        <div className="flex items-center justify-center gap-1">
          <button onClick={e => { e.stopPropagation(); openEditModal(row) }}
            className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-blue-600 transition-colors">
            <Edit className="w-3.5 h-3.5" />
          </button>
          <button onClick={e => { e.stopPropagation(); handleDelete(row) }}
            className="p-1.5 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-tertiary)] hover:text-red-600 transition-colors">
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[
        { label: 'Home', path: '/' },
        { label: 'Logistics' },
        { label: 'Carriers' },
      ]} />

      <EnterpriseToolbar
        title="Carriers"
        subtitle="Manage carrier accounts and rate cards"
        searchValue={search}
        onSearch={setSearch}
        searchPlaceholder="Search carriers..."
        actions={[
          { label: 'Add Carrier', icon: <Plus className="w-4 h-4" />, onClick: openAddModal, variant: 'primary' },
        ]}
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Active Carriers"
          value={activeCarriers.length}
          subtitle={`${carriers.length - activeCarriers.length} inactive`}
          icon={<Ship className="w-5 h-5" />}
          color="primary"
          trend={activeCarriers.length > 0 ? 'up' : 'neutral'}
          trendValue={`${((activeCarriers.length / Math.max(carriers.length, 1)) * 100).toFixed(0)}% active`}
        />
        <EnterpriseKPICard
          title="Total Shipments"
          value={fmtNumber(totalShipments)}
          subtitle="All time"
          icon={<Truck className="w-5 h-5" />}
          color="success"
          trend="up"
          trendValue="+12.3% vs last month"
        />
        <EnterpriseKPICard
          title="Avg On-Time Rate"
          value={`${avgOTD.toFixed(1)}%`}
          subtitle="Across all carriers"
          icon={<CheckCircle className="w-5 h-5" />}
          color="success"
          trend={avgOTD >= 95 ? 'up' : avgOTD >= 90 ? 'neutral' : 'down'}
          trendValue={avgOTD >= 95 ? 'Excellent' : avgOTD >= 90 ? 'Good' : 'Needs attention'}
        />
        <EnterpriseKPICard
          title="Avg Cost per Shipment"
          value={fmtCurrency(avgCostAll)}
          subtitle="Fleet average"
          icon={<DollarSign className="w-5 h-5" />}
          color="info"
          trend="down"
          trendValue={`-$${(0.15).toFixed(2)} vs last month`}
        />
      </div>

      <EnterpriseTabs tabs={tabs} activeTab={activeTab} onChange={setActiveTab} variant="underline" />

      {activeTab === 'all' && (
        <div>
          <EnterpriseDataGrid
            columns={columns}
            data={filteredCarriers}
            loading={loading}
            sortable
            rowKey="id"
            onRowClick={handleRowClick}
            emptyMessage="No carriers found"
            emptyIcon={<Truck className="w-12 h-12 text-[var(--text-tertiary)]" />}
          />

          {expandedCarrier && (
            <div className="enterprise-card mt-4 overflow-hidden">
              <div className="p-5 border-b border-[var(--border-color)] flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-white text-sm font-bold
                    ${expandedCarrier.status === 'ACTIVE' ? 'bg-blue-500' : expandedCarrier.status === 'INACTIVE' ? 'bg-gray-400' : 'bg-red-400'}`}>
                    {expandedCarrier.name.charAt(0)}
                  </div>
                  <div>
                    <h3 className="font-semibold text-[var(--text-primary)]">{expandedCarrier.name}</h3>
                    <p className="text-xs text-[var(--text-tertiary)]">Account: {expandedCarrier.accountNumber}</p>
                  </div>
                </div>
                <div className="flex items-center gap-4 text-sm">
                  <div className="text-center">
                    <p className="font-bold text-[var(--text-primary)]">{expandedCarrier.otdRate.toFixed(1)}%</p>
                    <p className="text-xs text-[var(--text-tertiary)]">OTD Rate</p>
                  </div>
                  <div className="text-center">
                    <p className="font-bold text-[var(--text-primary)]">{fmtCurrency(expandedCarrier.avgCost)}</p>
                    <p className="text-xs text-[var(--text-tertiary)]">Avg Cost</p>
                  </div>
                  <div className="text-center">
                    <p className="font-bold text-[var(--text-primary)]">{expandedCarrier.damageRate.toFixed(1)}%</p>
                    <p className="text-xs text-[var(--text-tertiary)]">Damage Rate</p>
                  </div>
                </div>
              </div>

              <div className="p-5 grid grid-cols-1 lg:grid-cols-2 gap-6">
                <div>
                  <h4 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
                    <Truck className="w-4 h-4 text-[var(--text-tertiary)]" /> Rate Cards
                  </h4>
                  <div className="overflow-hidden rounded-lg border border-[var(--border-color)]">
                    <table className="enterprise-table w-full text-sm">
                      <thead>
                        <tr>
                          <th className="text-left">Service</th>
                          <th className="text-right">Rate</th>
                          <th className="text-center">Transit</th>
                          <th className="text-center">Zone</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(MOCK_RATES[expandedCarrier.id] || []).map((rate, i) => (
                          <tr key={i}>
                            <td className="font-medium text-[var(--text-primary)]">{rate.serviceType}</td>
                            <td className="text-right font-mono text-[var(--text-primary)]">{fmtCurrency(rate.rate)}</td>
                            <td className="text-center text-[var(--text-secondary)]">{rate.transitTime}</td>
                            <td className="text-center"><span className="text-xs bg-[var(--bg-tertiary)] px-2 py-0.5 rounded">{rate.zone}</span></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                <div>
                  <h4 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
                    <BarChart3 className="w-4 h-4 text-[var(--text-tertiary)]" /> OTD Performance (Last 6 Months)
                  </h4>
                  <div className="flex items-end gap-3 h-32 pt-2">
                    {(OTD_HISTORY[expandedCarrier.id] || []).map((val, i) => {
                      const maxOTD = Math.max(...(OTD_HISTORY[expandedCarrier.id] || [100]))
                      const height = Math.max(4, (val / maxOTD) * 100)
                      const barColor =
                        val >= 95 ? 'bg-green-500' : val >= 90 ? 'bg-yellow-500' : 'bg-red-500'
                      return (
                        <div key={i} className="flex-1 flex flex-col items-center justify-end gap-1 h-full">
                          <span className="text-[10px] font-medium text-[var(--text-secondary)]">{val.toFixed(1)}%</span>
                          <div className="w-full rounded-t-md transition-all duration-300" style={{ height: `${height}%` }}>
                            <div className={`w-full h-full rounded-t-md ${barColor} opacity-80 hover:opacity-100 transition-opacity`} />
                          </div>
                          <span className="text-[10px] text-[var(--text-tertiary)]">{MONTH_LABELS[i]}</span>
                        </div>
                      )
                    })}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {activeTab === 'rates' && (
        <div className="enterprise-card overflow-hidden">
          <div className="p-5 border-b border-[var(--border-color)]">
            <h3 className="font-semibold text-[var(--text-primary)]">Rate Comparison</h3>
            <p className="text-sm text-[var(--text-secondary)] mt-0.5">Compare carrier rates across service tiers</p>
          </div>
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full text-sm">
              <thead>
                <tr>
                  <th className="text-left min-w-[140px]">Service Type</th>
                  {carriers.map(c => (
                    <th key={c.id} className={`text-center min-w-[130px] ${c.status === 'INACTIVE' ? 'opacity-50' : ''}`}>
                      <div className="flex flex-col items-center gap-1">
                        <div className={`w-6 h-6 rounded-lg flex items-center justify-center text-white text-[10px] font-bold
                          ${c.status === 'ACTIVE' ? 'bg-blue-500' : 'bg-gray-400'}`}>{c.name.charAt(0)}</div>
                        <span className="text-xs">{c.name}</span>
                      </div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rateComparison.map(({ serviceType, entries, bestCarrier }) => (
                  <tr key={serviceType}>
                    <td className="font-semibold text-[var(--text-primary)]">{serviceType}</td>
                    {entries.map(({ carrier, rate }) => (
                      <td key={carrier.id} className={`text-center ${carrier.status === 'INACTIVE' ? 'opacity-50' : ''}`}>
                        {rate ? (
                          <div className="flex flex-col items-center">
                            <span className="font-mono text-[var(--text-primary)]">{fmtCurrency(rate.rate)}</span>
                            <span className="text-[10px] text-[var(--text-tertiary)]">{rate.transitTime}</span>
                          </div>
                        ) : (
                          <span className="text-[var(--text-tertiary)]">—</span>
                        )}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="p-4 bg-[var(--bg-tertiary)] border-t border-[var(--border-color)] flex items-center gap-2 text-sm">
            <CheckCircle className="w-4 h-4 text-green-600" />
            <span className="text-[var(--text-secondary)]">Best rate for each tier:</span>
            {rateComparison.map(({ serviceType, bestCarrier }) => (
              bestCarrier && (
                <span key={serviceType} className="inline-flex items-center gap-1 bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 px-2 py-0.5 rounded-full text-xs font-medium">
                  {serviceType}: {bestCarrier.name}
                </span>
              )
            ))}
          </div>
        </div>
      )}

      {showModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" onClick={() => !saving && setShowModal(false)}>
          <div className="bg-[var(--bg-card)] rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-color)]">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-blue-500 flex items-center justify-center text-white">
                  <Ship className="w-4 h-4" />
                </div>
                <h2 className="text-lg font-semibold text-[var(--text-primary)]">
                  {editingCarrier ? `Edit ${editingCarrier.name}` : 'Add Carrier'}
                </h2>
              </div>
              <button disabled={saving} onClick={() => setShowModal(false)} className="p-1 hover:bg-[var(--bg-tertiary)] rounded transition-colors">
                <X className="w-5 h-5 text-[var(--text-secondary)]" />
              </button>
            </div>

            <div className="p-6 space-y-6">
              <EnterpriseFormSection title="General Information" columns={2}>
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-[var(--text-secondary)]">Carrier Name</label>
                  <input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })}
                    className="enterprise-input w-full" placeholder="e.g. FedEx" />
                </div>
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-[var(--text-secondary)]">Code</label>
                  <input value={form.code} onChange={e => setForm({ ...form, code: e.target.value })}
                    className="enterprise-input w-full font-mono" placeholder="e.g. FEDEX" />
                </div>
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-[var(--text-secondary)]">Type</label>
                  <select value={form.type} onChange={e => setForm({ ...form, type: e.target.value })}
                    className="enterprise-input w-full">
                    <option value="SHIPPING">SHIPPING</option>
                    <option value="FREIGHT">FREIGHT</option>
                    <option value="COURIER">COURIER</option>
                    <option value="LAST_MILE">LAST_MILE</option>
                  </select>
                </div>
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-[var(--text-secondary)]">Account Number</label>
                  <input value={form.accountNumber} onChange={e => setForm({ ...form, accountNumber: e.target.value })}
                    className="enterprise-input w-full" placeholder="e.g. FDX-78291" />
                </div>
              </EnterpriseFormSection>

              <EnterpriseFormSection title="API Credentials" description="Optional — used for automated rate fetching and label generation" columns={2}>
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-[var(--text-secondary)]">API Key</label>
                  <input type="password" value={form.apiKey} onChange={e => setForm({ ...form, apiKey: e.target.value })}
                    className="enterprise-input w-full font-mono" placeholder="••••••••" />
                </div>
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-[var(--text-secondary)]">API Secret</label>
                  <input type="password" value={form.apiSecret} onChange={e => setForm({ ...form, apiSecret: e.target.value })}
                    className="enterprise-input w-full font-mono" placeholder="••••••••" />
                </div>
              </EnterpriseFormSection>

              <EnterpriseFormSection title="Status" columns={1}>
                <label className="flex items-center gap-3 cursor-pointer">
                  <div onClick={() => setForm({ ...form, isActive: !form.isActive })}
                    className={`w-10 h-6 rounded-full transition-colors relative ${form.isActive ? 'bg-green-500' : 'bg-gray-300 dark:bg-gray-600'}`}>
                    <div className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${form.isActive ? 'translate-x-4' : ''}`} />
                  </div>
                  <div>
                    <span className="text-sm font-medium text-[var(--text-primary)]">{form.isActive ? 'Active' : 'Inactive'}</span>
                    <p className="text-xs text-[var(--text-tertiary)]">{form.isActive ? 'Carrier is available for shipping' : 'Carrier is disabled'}</p>
                  </div>
                </label>
              </EnterpriseFormSection>
            </div>

            <div className="p-6 border-t border-[var(--border-color)] flex justify-end gap-3">
              <button disabled={saving} onClick={() => setShowModal(false)} className="enterprise-btn enterprise-btn-secondary">
                Cancel
              </button>
              <button disabled={saving} onClick={handleSave} className="enterprise-btn enterprise-btn-primary">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                {editingCarrier ? 'Update Carrier' : 'Add Carrier'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
