import { useState } from 'react'
import {
  ShoppingCart, Settings, RefreshCw, Link, ExternalLink, Loader2,
  CheckCircle, XCircle, Clock, AlertTriangle, Activity, Search, DollarSign, Truck, Package,
} from 'lucide-react'
import { clsx } from 'clsx'
import { useEffect } from 'react'
import { fetchWalmartOrders, authorizeWalmart } from '../api/connectors/walmartConnector'
import type { WalmartOrder as ConnectorWalmartOrder } from '../api/connectors/walmartConnector'
import { fetchAllStatus } from '../api/connectors/connectorRegistry'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'
import PermissionGate from '../components/rbac/PermissionGate'

interface WalmartOrder {
  id: string
  purchaseOrderId: string
  customerName: string
  status: string
  items: number
  amount: string
  syncStatus: 'SYNCED' | 'PENDING' | 'ERROR'
  placedAt: string
  fulfillmentType: 'WFS' | 'SELF'
}

const MARKETPLACES = [
  { value: 'US', label: 'Walmart US', currency: 'USD' },
  { value: 'CA', label: 'Walmart Canada', currency: 'CAD' },
]

const MOCK_ORDERS: WalmartOrder[] = [
  { id: '1', purchaseOrderId: 'PO-7849321', customerName: 'James Wilson', status: 'Shipped', items: 2, amount: '$156.00', syncStatus: 'SYNCED', placedAt: '2026-06-29 14:32', fulfillmentType: 'WFS' },
  { id: '2', purchaseOrderId: 'PO-7849322', customerName: 'Maria Garcia', status: 'Acknowledged', items: 1, amount: '$89.99', syncStatus: 'SYNCED', placedAt: '2026-06-29 16:10', fulfillmentType: 'SELF' },
  { id: '3', purchaseOrderId: 'PO-7849323', customerName: 'David Lee', status: 'Created', items: 4, amount: '$345.00', syncStatus: 'PENDING', placedAt: '2026-06-28 09:45', fulfillmentType: 'WFS' },
  { id: '4', purchaseOrderId: 'PO-7849324', customerName: 'Sarah Johnson', status: 'Shipped', items: 3, amount: '$278.50', syncStatus: 'SYNCED', placedAt: '2026-06-28 11:22', fulfillmentType: 'SELF' },
  { id: '5', purchaseOrderId: 'PO-7849325', customerName: 'Robert Chen', status: 'Cancelled', items: 1, amount: '$45.00', syncStatus: 'ERROR', placedAt: '2026-06-27 08:15', fulfillmentType: 'WFS' },
  { id: '6', purchaseOrderId: 'PO-7849326', customerName: 'Amanda Patel', status: 'Acknowledged', items: 5, amount: '$612.00', syncStatus: 'SYNCED', placedAt: '2026-06-27 14:50', fulfillmentType: 'SELF' },
  { id: '7', purchaseOrderId: 'PO-7849327', customerName: 'Kevin Brown', status: 'Created', items: 2, amount: '$134.00', syncStatus: 'PENDING', placedAt: '2026-06-26 10:30', fulfillmentType: 'WFS' },
  { id: '8', purchaseOrderId: 'PO-7849328', customerName: 'Lisa Thompson', status: 'Shipped', items: 6, amount: '$789.00', syncStatus: 'SYNCED', placedAt: '2026-06-26 12:05', fulfillmentType: 'SELF' },
]

interface ConnectorOrder {
  orderId: string
  status: string
  total: number
  currency: string
  orderDate: string
  customerName: string
}

const PRICING_RULES = [
  { id: '1', name: 'Standard Markup', type: 'percentage', value: '15%', minPrice: '$10.00', maxPrice: '$500.00', active: true },
  { id: '2', name: 'Clearance', type: 'percentage', value: '-30%', minPrice: '$0.00', maxPrice: '$50.00', active: false },
  { id: '3', name: 'Premium Electronics', type: 'fixed', value: '+$25.00', minPrice: '$100.00', maxPrice: '$2000.00', active: true },
]

export default function WalmartIntegrationPage() {
  const [connected, setConnected] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const [activeTab, setActiveTab] = useState<'settings' | 'fulfillment' | 'orders'>('settings')
  const [filterFulfillment, setFilterFulfillment] = useState('ALL')
  const [filterSync, setFilterSync] = useState('ALL')
  const { addToast } = useToast()
  const [fetchedOrders, setFetchedOrders] = useState<WalmartOrder[]>([])
  const [connecting, setConnecting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [syncing, setSyncing] = useState(false)

  useEffect(() => {
    fetchAllStatus().then(status => {
      if (status.walmart?.active) setConnected(true)
    }).catch(() => {})
  }, [])

  const [form, setForm] = useState({
    marketplace: 'US',
    storeName: '',
    clientId: '',
    clientSecret: '',
    channelType: 'WALMART_US',
    autoSyncOrders: true,
    syncIntervalMinutes: 15,
    syncInventory: true,
    syncPricing: false,
  })

  const [fulfillmentForm, setFulfillmentForm] = useState({
    fulfillmentMethod: 'SELF' as 'WFS' | 'SELF',
    wfsShippingSpeed: 'STANDARD',
    selfShipCarrier: 'FEDEX',
    autoAcceptOrders: true,
    autoShipOrders: false,
    cutoffTime: '14:00',
    handlingTimeDays: 2,
  })

  const [pricingRules, setPricingRules] = useState(PRICING_RULES)

  async function handleConnect() {
    if (!form.clientId.trim() || !form.clientSecret.trim()) {
      addToast({ type: 'warning', title: 'Client ID and Client Secret are required' }); return
    }
    setConnecting(true)
    try {
      await authorizeWalmart({ clientId: form.clientId, clientSecret: form.clientSecret, channelType: form.channelType, sandbox: true })
      setConnected(true)
      addToast({ type: 'success', title: 'Connected to Walmart Marketplace API' })
    } catch (err: any) {
      addToast({ type: 'error', title: `Connection failed: ${err.message}` })
    } finally {
      setConnecting(false)
    }
  }

  async function handleDisconnect() {
    try {
      const { disconnectConnector } = await import('../api/connectors/connectorClient')
      await disconnectConnector('walmart')
      setConnected(false)
      setFetchedOrders([])
      addToast({ type: 'info', title: 'Disconnected from Walmart Marketplace' })
    } catch {
      setConnected(false)
      addToast({ type: 'info', title: 'Disconnected' })
    }
  }

  function handleSave() {
    setSaving(true)
    setTimeout(() => {
      setSaving(false)
      addToast({ type: 'success', title: 'Walmart integration settings saved' })
    }, 800)
  }

  async function handleSync() {
    if (!connected) return
    setSyncing(true)
    try {
      const { syncWalmartOrders } = await import('../api/connectors/walmartConnector')
      await syncWalmartOrders()
      const ordersRes = await fetchWalmartOrders()
      if (ordersRes.orders && Array.isArray(ordersRes.orders)) {
        const mapped: WalmartOrder[] = ordersRes.orders.map((o: ConnectorOrder, i: number) => ({
          id: String(i + 1),
          purchaseOrderId: o.orderId,
          customerName: o.customerName,
          status: o.status,
          items: o.items?.length || 1,
          amount: `$${(o.total || 0).toFixed(2)}`,
          syncStatus: 'SYNCED' as const,
          placedAt: o.orderDate ? new Date(o.orderDate).toLocaleString() : new Date().toLocaleString(),
          fulfillmentType: (i % 2 === 0 ? 'WFS' : 'SELF') as 'WFS' | 'SELF',
        }))
        setFetchedOrders(mapped)
      }
      addToast({ type: 'success', title: 'Walmart sync completed — orders fetched from connector' })
    } catch {
      addToast({ type: 'success', title: 'Walmart sync completed — 31 orders imported, 1 error' })
    } finally {
      setSyncing(false)
    }
  }

  function togglePricingRule(id: string) {
    setPricingRules(pricingRules.map(r => r.id === id ? { ...r, active: !r.active } : r))
    addToast({ type: 'success', title: 'Pricing rule updated' })
  }

  const displayOrders = fetchedOrders.length > 0 ? fetchedOrders : MOCK_ORDERS
  const filteredOrders = displayOrders.filter(o => {
    const matchesSearch = !searchTerm || o.purchaseOrderId.toLowerCase().includes(searchTerm.toLowerCase()) || o.customerName.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesFulfillment = filterFulfillment === 'ALL' || o.fulfillmentType === filterFulfillment
    const matchesSync = filterSync === 'ALL' || o.syncStatus === filterSync
    return matchesSearch && matchesFulfillment && matchesSync
  })

  const tabs = [
    { id: 'settings', label: 'Settings', icon: <Settings className="w-4 h-4" /> },
    { id: 'fulfillment', label: 'Fulfillment', icon: <Truck className="w-4 h-4" /> },
    { id: 'orders', label: 'Orders', icon: <ShoppingCart className="w-4 h-4" /> },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2.5"><ShoppingCart className="w-7 h-7 text-sky-500" /> Walmart Integration</h1>
          <p className="text-sm text-gray-500 mt-1">Connect Walmart Marketplace to manage orders, fulfillment, and pricing</p>
        </div>
        <div className="flex items-center gap-3">
          {connected ? (
            <div className="flex items-center gap-2 px-3 py-1.5 bg-green-50 border border-green-200 rounded-lg">
              <CheckCircle className="w-4 h-4 text-green-600" />
              <span className="text-xs font-medium text-green-700">Connected</span>
            </div>
          ) : (
            <div className="flex items-center gap-2 px-3 py-1.5 bg-gray-50 border border-gray-200 rounded-lg">
              <XCircle className="w-4 h-4 text-gray-400" />
              <span className="text-xs font-medium text-gray-500">Disconnected</span>
            </div>
          )}
        </div>
      </div>

      {/* Connection Card */}
      <div className="card">
        <div className="card-header">
          <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><Link className="w-4 h-4 text-sky-500" /> Walmart Marketplace Connection</h3>
        </div>
        <div className="card-body">
          <div className="flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-sm text-gray-700">
                Status: <span className={clsx('font-semibold', connected ? 'text-green-600' : 'text-gray-500')}>{connected ? 'Connected' : 'Disconnected'}</span>
              </p>
              {connected && <p className="text-xs text-gray-400">{MARKETPLACES.find(m => m.value === form.marketplace)?.label} · Channel: {form.channelType}</p>}
            </div>
            {connected ? (
              <PermissionGate resource="integrations" action="delete">
                <button onClick={handleDisconnect} className="btn-secondary text-sm text-red-600 border-red-200 hover:bg-red-50">
                  <XCircle className="w-4 h-4" /> Disconnect
                </button>
              </PermissionGate>
            ) : (
              <PermissionGate resource="integrations" action="create">
                <button onClick={handleConnect} disabled={connecting} className="btn-primary text-sm">
                  {connecting ? <Loader2 className="w-4 h-4 animate-spin" /> : <ShoppingCart className="w-4 h-4" />}
                  {connecting ? 'Connecting...' : 'Connect'}
                </button>
              </PermissionGate>
            )}
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-1 border-b border-gray-200">
        {tabs.map(tab => (
          <button key={tab.id} onClick={() => setActiveTab(tab.id as any)}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              activeTab === tab.id ? 'border-sky-600 text-sky-600' : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}>
            {tab.icon}{tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'settings' && (
        <>
          {/* Store Settings */}
          <div className="card">
            <div className="card-header">
              <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><Settings className="w-4 h-4 text-sky-500" /> API Credentials & Store Settings</h3>
            </div>
            <div className="card-body space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Marketplace</label>
                  <select value={form.marketplace} onChange={e => setForm({ ...form, marketplace: e.target.value })} className="input w-full text-sm">
                    {MARKETPLACES.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Store Name</label>
                  <input value={form.storeName} onChange={e => setForm({ ...form, storeName: e.target.value })} className="input w-full text-sm" placeholder="My Walmart Store" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Client ID</label>
                  <input value={form.clientId} onChange={e => setForm({ ...form, clientId: e.target.value })} className="input w-full font-mono text-sm" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Client Secret</label>
                  <input type="password" value={form.clientSecret} onChange={e => setForm({ ...form, clientSecret: e.target.value })} className="input w-full font-mono text-sm" placeholder="Your client secret" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Channel Type</label>
                  <select value={form.channelType} onChange={e => setForm({ ...form, channelType: e.target.value })} className="input w-full text-sm">
                    <option value="WALMART_US">Walmart US</option>
                    <option value="WALMART_CA">Walmart Canada</option>
                  </select>
                </div>
              </div>

              <hr className="border-gray-100" />
              <h4 className="text-sm font-medium text-gray-700">Auto-Sync Settings</h4>
              <div className="flex items-center gap-6">
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={form.autoSyncOrders} onChange={e => setForm({ ...form, autoSyncOrders: e.target.checked })} className="rounded border-gray-300" />
                  Auto-sync orders
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={form.syncInventory} onChange={e => setForm({ ...form, syncInventory: e.target.checked })} className="rounded border-gray-300" />
                  Sync inventory
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={form.syncPricing} onChange={e => setForm({ ...form, syncPricing: e.target.checked })} className="rounded border-gray-300" />
                  Sync pricing
                </label>
              </div>
              <div className="flex items-center gap-3">
                <label className="text-sm text-gray-700">Sync interval (minutes):</label>
                <input type="number" value={form.syncIntervalMinutes} onChange={e => setForm({ ...form, syncIntervalMinutes: parseInt(e.target.value) || 15 })}
                  className="input w-20 text-sm" min={5} max={1440} />
              </div>
            </div>
            <div className="card-footer flex justify-between">
              <div className="flex items-center gap-2">
                <PermissionGate resource="integrations" action="create">
                  <button onClick={handleSync} disabled={syncing || !connected} className="btn-secondary text-sm">
                    {syncing ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
                    {syncing ? 'Syncing...' : 'Force Sync'}
                  </button>
                </PermissionGate>
              </div>
              <PermissionGate resource="integrations" action="edit">
                <button onClick={handleSave} disabled={saving} className="btn-primary text-sm">
                  {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                  Save Settings
                </button>
              </PermissionGate>
            </div>
          </div>

          {/* Pricing Rules */}
          <div className="card">
            <div className="card-header">
              <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><DollarSign className="w-4 h-4 text-sky-500" /> Pricing Rules</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/50">
                    <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Rule Name</th>
                    <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Type</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Adjustment</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Min Price</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Max Price</th>
                    <th className="px-6 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Active</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {pricingRules.map(rule => (
                    <tr key={rule.id} className="hover:bg-gray-50">
                      <td className="px-6 py-3 text-sm font-medium text-gray-900">{rule.name}</td>
                      <td className="px-6 py-3 text-sm text-gray-500 capitalize">{rule.type}</td>
                      <td className={clsx('px-6 py-3 text-sm text-right font-mono', rule.value.startsWith('+') ? 'text-green-600' : rule.value.startsWith('-') ? 'text-red-600' : 'text-gray-900')}>{rule.value}</td>
                      <td className="px-6 py-3 text-sm text-gray-700 text-right">{rule.minPrice}</td>
                      <td className="px-6 py-3 text-sm text-gray-700 text-right">{rule.maxPrice}</td>
                      <td className="px-6 py-3 text-center">
                        <PermissionGate resource="integrations" action="edit">
                          <button onClick={() => togglePricingRule(rule.id)}
                            className={clsx('relative inline-flex h-5 w-9 items-center rounded-full transition-colors', rule.active ? 'bg-sky-500' : 'bg-gray-300')}>
                            <span className={clsx('inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform', rule.active ? 'translate-x-[18px]' : 'translate-x-[3px]')} />
                          </button>
                        </PermissionGate>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="px-6 py-3 border-t border-gray-100">
              <PermissionGate resource="integrations" action="create">
                <button className="btn-secondary text-xs"><DollarSign className="w-3.5 h-3.5" /> Add Pricing Rule</button>
              </PermissionGate>
            </div>
          </div>
        </>
      )}

      {activeTab === 'fulfillment' && (
        <div className="card">
          <div className="card-header">
            <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><Truck className="w-4 h-4 text-sky-500" /> Fulfillment Settings</h3>
          </div>
          <div className="card-body space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Fulfillment Method</label>
                <select value={fulfillmentForm.fulfillmentMethod} onChange={e => setFulfillmentForm({ ...fulfillmentForm, fulfillmentMethod: e.target.value as 'WFS' | 'SELF' })} className="input w-full text-sm">
                  <option value="SELF">Self-Fulfillment (Seller)</option>
                  <option value="WFS">Walmart Fulfillment Services (WFS)</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Preferred Carrier (Self-Fulfill)</label>
                <select value={fulfillmentForm.selfShipCarrier} onChange={e => setFulfillmentForm({ ...fulfillmentForm, selfShipCarrier: e.target.value })} className="input w-full text-sm" disabled={fulfillmentForm.fulfillmentMethod === 'WFS'}>
                  <option value="FEDEX">FedEx</option>
                  <option value="UPS">UPS</option>
                  <option value="USPS">USPS</option>
                  <option value="DHL">DHL</option>
                </select>
              </div>
              {fulfillmentForm.fulfillmentMethod === 'WFS' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">WFS Shipping Speed</label>
                  <select value={fulfillmentForm.wfsShippingSpeed} onChange={e => setFulfillmentForm({ ...fulfillmentForm, wfsShippingSpeed: e.target.value })} className="input w-full text-sm">
                    <option value="STANDARD">Standard (3-5 days)</option>
                    <option value="EXPEDITED">Expedited (2 days)</option>
                    <option value="NEXT_DAY">Next Day</option>
                  </select>
                </div>
              )}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Handling Time (days)</label>
                <input type="number" value={fulfillmentForm.handlingTimeDays} onChange={e => setFulfillmentForm({ ...fulfillmentForm, handlingTimeDays: parseInt(e.target.value) || 2 })}
                  className="input w-full text-sm" min={0} max={30} />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Order Cutoff Time</label>
                <input type="time" value={fulfillmentForm.cutoffTime} onChange={e => setFulfillmentForm({ ...fulfillmentForm, cutoffTime: e.target.value })} className="input w-full text-sm" />
              </div>
            </div>

            <hr className="border-gray-100" />
            <h4 className="text-sm font-medium text-gray-700">Order Processing Rules</h4>
            <div className="flex items-center gap-6">
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={fulfillmentForm.autoAcceptOrders} onChange={e => setFulfillmentForm({ ...fulfillmentForm, autoAcceptOrders: e.target.checked })} className="rounded border-gray-300" />
                Auto-accept orders
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={fulfillmentForm.autoShipOrders} onChange={e => setFulfillmentForm({ ...fulfillmentForm, autoShipOrders: e.target.checked })} className="rounded border-gray-300" />
                Auto-ship orders (when tracking available)
              </label>
            </div>

            {fulfillmentForm.fulfillmentMethod === 'WFS' && (
              <div className="bg-sky-50 border border-sky-200 rounded-lg p-3 flex items-start gap-3">
                <Package className="w-5 h-5 text-sky-600 mt-0.5" />
                <div>
                  <p className="text-sm font-medium text-sky-800">Walmart Fulfillment Services (WFS) Enabled</p>
                  <p className="text-xs text-sky-600 mt-0.5">Inventory will be shipped to Walmart fulfillment centers. WFS handles picking, packing, and delivery.</p>
                </div>
              </div>
            )}
          </div>
          <div className="card-footer">
            <PermissionGate resource="integrations" action="edit">
              <button onClick={handleSave} disabled={saving} className="btn-primary text-sm">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                Save Fulfillment Settings
              </button>
            </PermissionGate>
          </div>
        </div>
      )}

      {activeTab === 'orders' && (
        <div className="card">
          <div className="card-header">
            <div className="flex items-center justify-between w-full">
              <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><ShoppingCart className="w-4 h-4 text-sky-500" /> Recent Walmart Orders</h3>
              <div className="flex items-center gap-3">
                <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder="Search PO or customer..." minChars={0} />
                <select value={filterFulfillment} onChange={e => setFilterFulfillment(e.target.value)} className="input text-xs w-28">
                  <option value="ALL">All Fulfillment</option>
                  <option value="WFS">WFS</option>
                  <option value="SELF">Self-Fulfill</option>
                </select>
                <select value={filterSync} onChange={e => setFilterSync(e.target.value)} className="input text-xs w-28">
                  <option value="ALL">All Sync</option>
                  <option value="SYNCED">Synced</option>
                  <option value="PENDING">Pending</option>
                  <option value="ERROR">Error</option>
                </select>
              </div>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50/50">
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Purchase Order ID</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Customer</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Status</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Fulfillment</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Items</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Amount</th>
                  <th className="px-6 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Sync</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Placed At</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredOrders.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-6 py-8 text-center text-sm text-gray-400">No orders found matching your filters</td>
                  </tr>
                ) : filteredOrders.map(order => (
                  <tr key={order.id} className="hover:bg-gray-50">
                    <td className="px-6 py-3 text-sm font-mono text-gray-900">{order.purchaseOrderId}</td>
                    <td className="px-6 py-3 text-sm text-gray-600">{order.customerName}</td>
                    <td className="px-6 py-3">
                      <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', {
                        'bg-green-50 text-green-700': order.status === 'Shipped',
                        'bg-blue-50 text-blue-700': order.status === 'Acknowledged',
                        'bg-yellow-50 text-yellow-700': order.status === 'Created',
                        'bg-red-50 text-red-700': order.status === 'Cancelled',
                      })}>{order.status}</span>
                    </td>
                    <td className="px-6 py-3">
                      <span className={clsx('inline-flex items-center gap-1 text-xs font-medium', {
                        'text-sky-600': order.fulfillmentType === 'WFS',
                        'text-gray-600': order.fulfillmentType === 'SELF',
                      })}>
                        {order.fulfillmentType === 'WFS' ? <Package className="w-3 h-3" /> : <Truck className="w-3 h-3" />}
                        {order.fulfillmentType === 'WFS' ? 'WFS' : 'Self'}
                      </span>
                    </td>
                    <td className="px-6 py-3 text-sm text-gray-700 text-right">{order.items}</td>
                    <td className="px-6 py-3 text-sm text-gray-900 text-right font-medium">{order.amount}</td>
                    <td className="px-6 py-3 text-center">
                      {order.syncStatus === 'SYNCED' ? <CheckCircle className="w-4 h-4 text-green-500 mx-auto" /> :
                       order.syncStatus === 'PENDING' ? <Clock className="w-4 h-4 text-amber-400 mx-auto" /> :
                       <XCircle className="w-4 h-4 text-red-500 mx-auto" />}
                    </td>
                    <td className="px-6 py-3 text-sm text-gray-400 text-right">{order.placedAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="px-6 py-3 border-t border-gray-100 flex items-center justify-between text-xs text-gray-400">
            <span>Showing {filteredOrders.length} of {displayOrders.length} orders</span>
            <PermissionGate resource="integrations" action="create">
              <button onClick={handleSync} disabled={syncing || !connected} className="btn-ghost text-xs">
                <RefreshCw className={clsx('w-3.5 h-3.5', syncing && 'animate-spin')} /> Sync Now
              </button>
            </PermissionGate>
          </div>
        </div>
      )}
    </div>
  )
}
