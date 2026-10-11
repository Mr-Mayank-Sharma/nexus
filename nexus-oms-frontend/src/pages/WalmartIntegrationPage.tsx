import { useEffect, useState } from 'react'
import {
  ShoppingCart, Settings, RefreshCw, Link, Loader2,
  CheckCircle, XCircle, DollarSign, Truck, Package,
} from 'lucide-react'
import { clsx } from 'clsx'
import { configureConnector, disconnectConnector } from '../api/connectors/connectorClient'
import { useMarketplaceStatus } from '../features/marketplace/useMarketplaceStatus'
import { loadChannelOrders } from '../features/marketplace/channelOrders'
import NotLiveBanner from '../features/marketplace/NotLiveBanner'
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
  placedAt: string
  fulfillmentType: 'WFS' | 'SELF' | 'UNKNOWN'
}

const MARKETPLACES = [
  { value: 'US', label: 'Walmart US', currency: 'USD' },
  { value: 'CA', label: 'Walmart Canada', currency: 'CAD' },
]




interface PricingRule { id: string; name: string; type: string; value: string; minPrice: string; maxPrice: string; active: boolean }

export default function WalmartIntegrationPage() {
  const [searchTerm, setSearchTerm] = useState('')
  const [activeTab, setActiveTab] = useState<'settings' | 'fulfillment' | 'orders'>('settings')
  const [filterFulfillment, setFilterFulfillment] = useState('ALL')
  const { addToast } = useToast()
  const { hasCredentials: credentialsSaved, ordersInNexus, refresh } = useMarketplaceStatus('walmart')
  const [fetchedOrders, setFetchedOrders] = useState<WalmartOrder[]>([])

  const [ordersError, setOrdersError] = useState<string | null>(null)
  useEffect(() => {
    const ctl = new AbortController()
    loadChannelOrders('walmart', ctl.signal)
      .then(rows => { setOrdersError(null); setFetchedOrders(rows.map(r => (({ id: r.id, purchaseOrderId: r.orderId, customerName: r.customer, status: r.status, items: r.items, amount: r.amount, placedAt: r.placedAt, fulfillmentType: 'UNKNOWN' as const })))) })
      .catch(err => { if (!ctl.signal.aborted) setOrdersError(err instanceof Error ? err.message : 'Could not load orders') })
    return () => ctl.abort()
  }, [])
  const [connecting, setConnecting] = useState(false)
  const [saving, setSaving] = useState(false)

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

  // Repricing rules are not stored by the backend yet; start empty rather than show invented rules.
  const [pricingRules, setPricingRules] = useState<PricingRule[]>([])

  async function handleConnect() {
    if (!form.clientId.trim() || !form.clientSecret.trim()) {
      addToast({ type: 'warning', title: 'Walmart credentials are required' }); return
    }
    setConnecting(true)
    try {
      const res = await configureConnector('walmart', { clientId: form.clientId, clientSecret: form.clientSecret, channelType: form.channelType, sandbox: true }) as { message?: string }
      refresh()
      // Saved, not verified: no check against Walmart is made, so this must not read as "credentialsSaved".
      addToast({ type: 'info', title: res.message ?? 'Credentials saved (not verified)' })
    } catch (err) {
      addToast({ type: 'error', title: `Could not save credentials: ${err instanceof Error ? err.message : 'the server did not respond'}` })
    } finally {
      setConnecting(false)
    }
  }

  async function handleDisconnect() {
    try {
      await disconnectConnector('walmart')
      setFetchedOrders([])
      refresh()
      addToast({ type: 'info', title: 'Disconnected from Walmart' })
    } catch (err) {
      addToast({ type: 'error', title: `Could not disconnect: ${err instanceof Error ? err.message : 'the server did not respond'}` })
    }
  }

  async function handleSave() {
    setSaving(true)
    try {
      const res = await configureConnector('walmart', { channelType: form.channelType, sandbox: true }) as { message?: string }
      refresh()
      addToast({ type: 'success', title: res.message ?? 'Walmart settings saved' })
    } catch (err) {
      addToast({ type: 'error', title: `Could not save settings: ${err instanceof Error ? err.message : 'the server did not respond'}` })
    } finally {
      setSaving(false)
    }
  }

  function togglePricingRule(id: string) {
    setPricingRules(pricingRules.map(r => r.id === id ? { ...r, active: !r.active } : r))
    addToast({ type: 'success', title: 'Pricing rule updated' })
  }

  const displayOrders = fetchedOrders
  const filteredOrders = displayOrders.filter(o => {
    const matchesSearch = !searchTerm || o.purchaseOrderId.toLowerCase().includes(searchTerm.toLowerCase()) || o.customerName.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesFulfillment = filterFulfillment === 'ALL' || o.fulfillmentType === filterFulfillment
    return matchesSearch && matchesFulfillment
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
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><ShoppingCart className="w-7 h-7 text-sky-500" /> Walmart Integration</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Connect Walmart Marketplace to manage orders, fulfillment, and pricing</p>
          <p className="mt-2 inline-flex items-center gap-1.5 rounded-md bg-[var(--nexus-warning-50)] px-2 py-1 text-xs font-medium text-[var(--nexus-warning-700)]">
            Preview: the Walmart connector is not live yet, so orders and pricing here are not synced with Walmart.
          </p>
        </div>
        <div className="flex items-center gap-3">
          {credentialsSaved ? (
            <div className="flex items-center gap-2 px-3 py-1.5 bg-[var(--nexus-success-50)] border border-[var(--nexus-success-200)] rounded-lg">
              <CheckCircle className="w-4 h-4 text-[var(--nexus-success-600)]" />
              <span className="text-xs font-medium text-[var(--nexus-success-700)]">Credentials saved</span>
            </div>
          ) : (
            <div className="flex items-center gap-2 px-3 py-1.5 bg-[var(--surface-sunken)] border border-[var(--border-default)] rounded-lg">
              <XCircle className="w-4 h-4 text-[var(--text-tertiary)]" />
              <span className="text-xs font-medium text-[var(--text-secondary)]">Not configured</span>
            </div>
          )}
        </div>
      </div>

      <NotLiveBanner marketplace="Walmart" credentialsSaved={credentialsSaved} />
      {ordersError && <p role="alert" className="text-sm text-[var(--nexus-error-600)]">Could not load orders: {ordersError}</p>}

      {/* Connection Card */}
      <div className="card">
        <div className="card-header">
          <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Link className="w-4 h-4 text-sky-500" /> Walmart Marketplace Connection</h3>
        </div>
        <div className="card-body">
          <div className="flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-sm text-[var(--text-secondary)]">
                Status: <span className={clsx('font-semibold', credentialsSaved ? 'text-[var(--nexus-success-600)]' : 'text-[var(--text-secondary)]')}>{credentialsSaved ? 'Credentials saved (not verified)' : 'Not configured'}</span>
              </p>
              {credentialsSaved && <p className="text-xs text-[var(--text-tertiary)]">{MARKETPLACES.find(m => m.value === form.marketplace)?.label} · Channel: {form.channelType}</p>}
            </div>
            {credentialsSaved ? (
              <PermissionGate resource="integrations" action="delete">
                <button type="button" onClick={handleDisconnect} className="enterprise-btn enterprise-btn-secondary text-sm text-[var(--nexus-error-600)] border-[var(--nexus-error-200)] hover:bg-[var(--nexus-error-50)]">
                  <XCircle className="w-4 h-4" /> Disconnect
                </button>
              </PermissionGate>
            ) : (
              <PermissionGate resource="integrations" action="create">
                <button type="button" onClick={handleConnect} disabled={connecting} className="enterprise-btn enterprise-btn-primary text-sm">
                  {connecting ? <Loader2 className="w-4 h-4 animate-spin" /> : <ShoppingCart className="w-4 h-4" />}
                  {connecting ? 'Connecting...' : 'Connect'}
                </button>
              </PermissionGate>
            )}
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-1 border-b border-[var(--border-default)]">
        {tabs.map(tab => (
          <button type="button" key={tab.id} onClick={() => setActiveTab(tab.id as any)}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              activeTab === tab.id ? 'border-sky-600 text-sky-600' : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-secondary)]'
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
              <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Settings className="w-4 h-4 text-sky-500" /> API Credentials & Store Settings</h3>
            </div>
            <div className="card-body space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Marketplace</label>
                  <select value={form.marketplace} onChange={e => setForm({ ...form, marketplace: e.target.value })} className="input w-full text-sm">
                    {MARKETPLACES.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Store Name</label>
                  <input value={form.storeName} onChange={e => setForm({ ...form, storeName: e.target.value })} className="input w-full text-sm" placeholder="My Walmart Store" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Client ID</label>
                  <input value={form.clientId} onChange={e => setForm({ ...form, clientId: e.target.value })} className="input w-full font-mono text-sm" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Client Secret</label>
                  <input type="password" value={form.clientSecret} onChange={e => setForm({ ...form, clientSecret: e.target.value })} className="input w-full font-mono text-sm" placeholder="Your client secret" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Channel Type</label>
                  <select value={form.channelType} onChange={e => setForm({ ...form, channelType: e.target.value })} className="input w-full text-sm">
                    <option value="WALMART_US">Walmart US</option>
                    <option value="WALMART_CA">Walmart Canada</option>
                  </select>
                </div>
              </div>

              <hr className="border-[var(--border-subtle)]" />
              <h4 className="text-sm font-medium text-[var(--text-secondary)]">Auto-Sync Settings</h4>
              <div className="flex items-center gap-6">
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={form.autoSyncOrders} onChange={e => setForm({ ...form, autoSyncOrders: e.target.checked })} className="rounded border-[var(--border-default)]" />
                  Auto-sync orders
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={form.syncInventory} onChange={e => setForm({ ...form, syncInventory: e.target.checked })} className="rounded border-[var(--border-default)]" />
                  Sync inventory
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={form.syncPricing} onChange={e => setForm({ ...form, syncPricing: e.target.checked })} className="rounded border-[var(--border-default)]" />
                  Sync pricing
                </label>
              </div>
              <div className="flex items-center gap-3">
                <label className="text-sm text-[var(--text-secondary)]">Sync interval (minutes):</label>
                <input type="number" value={form.syncIntervalMinutes} onChange={e => setForm({ ...form, syncIntervalMinutes: parseInt(e.target.value) || 15 })}
                  className="input w-20 text-sm" min={5} max={1440} />
              </div>
            </div>
            <div className="card-footer flex justify-between">
              <div className="flex items-center gap-2">
                <PermissionGate resource="integrations" action="create">
                  <button type="button" disabled title="Live sync with Walmart is not available yet" className="enterprise-btn enterprise-btn-secondary text-sm">
                    <RefreshCw className="w-4 h-4" />
                    Force Sync
                  </button>
                </PermissionGate>
              </div>
              <PermissionGate resource="integrations" action="edit">
                <button type="button" onClick={handleSave} disabled={saving} className="enterprise-btn enterprise-btn-primary text-sm">
                  {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                  Save Settings
                </button>
              </PermissionGate>
            </div>
          </div>

          {/* Pricing Rules */}
          <div className="card">
            <div className="card-header">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><DollarSign className="w-4 h-4 text-sky-500" /> Pricing Rules</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                    <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Rule Name</th>
                    <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Type</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Adjustment</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Min Price</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Max Price</th>
                    <th className="px-6 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Active</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--surface-sunken)]">
                  {pricingRules.length === 0 && (
                    <tr><td colSpan={6} className="py-6 text-center text-sm text-[var(--text-tertiary)]">No repricing rules yet.</td></tr>
                  )}
                  {pricingRules.map(rule => (
                    <tr key={rule.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-6 py-3 text-sm font-medium text-[var(--text-primary)]">{rule.name}</td>
                      <td className="px-6 py-3 text-sm text-[var(--text-secondary)] capitalize">{rule.type}</td>
                      <td className={clsx('px-6 py-3 text-sm text-right font-mono', rule.value.startsWith('+') ? 'text-[var(--nexus-success-600)]' : rule.value.startsWith('-') ? 'text-[var(--nexus-error-600)]' : 'text-[var(--text-primary)]')}>{rule.value}</td>
                      <td className="px-6 py-3 text-sm text-[var(--text-secondary)] text-right">{rule.minPrice}</td>
                      <td className="px-6 py-3 text-sm text-[var(--text-secondary)] text-right">{rule.maxPrice}</td>
                      <td className="px-6 py-3 text-center">
                        <PermissionGate resource="integrations" action="edit">
                          <button type="button" onClick={() => togglePricingRule(rule.id)}
                            className={clsx('relative inline-flex h-5 w-9 items-center rounded-full transition-colors', rule.active ? 'bg-sky-500' : 'bg-[var(--surface-muted)]')}>
                            <span className={clsx('inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform', rule.active ? 'translate-x-[18px]' : 'translate-x-[3px]')} />
                          </button>
                        </PermissionGate>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="px-6 py-3 border-t border-[var(--border-subtle)]">
              <PermissionGate resource="integrations" action="create">
                <button disabled title="Available once the Walmart connector is live" type="button" className="enterprise-btn enterprise-btn-secondary text-xs"><DollarSign className="w-3.5 h-3.5" /> Add Pricing Rule</button>
              </PermissionGate>
            </div>
          </div>
        </>
      )}

      {activeTab === 'fulfillment' && (
        <div className="card">
          <div className="card-header">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Truck className="w-4 h-4 text-sky-500" /> Fulfillment Settings</h3>
          </div>
          <div className="card-body space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Fulfillment Method</label>
                <select value={fulfillmentForm.fulfillmentMethod} onChange={e => setFulfillmentForm({ ...fulfillmentForm, fulfillmentMethod: e.target.value as 'WFS' | 'SELF' })} className="input w-full text-sm">
                  <option value="SELF">Self-Fulfillment (Seller)</option>
                  <option value="WFS">Walmart Fulfillment Services (WFS)</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Preferred Carrier (Self-Fulfill)</label>
                <select value={fulfillmentForm.selfShipCarrier} onChange={e => setFulfillmentForm({ ...fulfillmentForm, selfShipCarrier: e.target.value })} className="input w-full text-sm" disabled={fulfillmentForm.fulfillmentMethod === 'WFS'}>
                  <option value="FEDEX">FedEx</option>
                  <option value="UPS">UPS</option>
                  <option value="USPS">USPS</option>
                  <option value="DHL">DHL</option>
                </select>
              </div>
              {fulfillmentForm.fulfillmentMethod === 'WFS' && (
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">WFS Shipping Speed</label>
                  <select value={fulfillmentForm.wfsShippingSpeed} onChange={e => setFulfillmentForm({ ...fulfillmentForm, wfsShippingSpeed: e.target.value })} className="input w-full text-sm">
                    <option value="STANDARD">Standard (3-5 days)</option>
                    <option value="EXPEDITED">Expedited (2 days)</option>
                    <option value="NEXT_DAY">Next Day</option>
                  </select>
                </div>
              )}
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Handling Time (days)</label>
                <input type="number" value={fulfillmentForm.handlingTimeDays} onChange={e => setFulfillmentForm({ ...fulfillmentForm, handlingTimeDays: parseInt(e.target.value) || 2 })}
                  className="input w-full text-sm" min={0} max={30} />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Order Cutoff Time</label>
                <input type="time" value={fulfillmentForm.cutoffTime} onChange={e => setFulfillmentForm({ ...fulfillmentForm, cutoffTime: e.target.value })} className="input w-full text-sm" />
              </div>
            </div>

            <hr className="border-[var(--border-subtle)]" />
            <h4 className="text-sm font-medium text-[var(--text-secondary)]">Order Processing Rules</h4>
            <div className="flex items-center gap-6">
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={fulfillmentForm.autoAcceptOrders} onChange={e => setFulfillmentForm({ ...fulfillmentForm, autoAcceptOrders: e.target.checked })} className="rounded border-[var(--border-default)]" />
                Auto-accept orders
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={fulfillmentForm.autoShipOrders} onChange={e => setFulfillmentForm({ ...fulfillmentForm, autoShipOrders: e.target.checked })} className="rounded border-[var(--border-default)]" />
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
              <button type="button" onClick={handleSave} disabled={saving} className="enterprise-btn enterprise-btn-primary text-sm">
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
              <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><ShoppingCart className="w-4 h-4 text-sky-500" /> Walmart-channel orders in Nexus ({ordersInNexus})</h3>
              <div className="flex items-center gap-3">
                <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder="Search PO or customer..." minChars={0} />
                <select value={filterFulfillment} onChange={e => setFilterFulfillment(e.target.value)} className="input text-xs w-28">
                  <option value="ALL">All Fulfillment</option>
                  <option value="WFS">WFS</option>
                  <option value="SELF">Self-Fulfill</option>
                </select>
              </div>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Purchase Order ID</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Customer</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Fulfillment</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Items</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Amount</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Placed At</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--surface-sunken)]">
                {filteredOrders.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-6 py-8 text-center text-sm text-[var(--text-tertiary)]">No orders found matching your filters</td>
                  </tr>
                ) : filteredOrders.map(order => (
                  <tr key={order.id} className="hover:bg-[var(--surface-sunken)]">
                    <td className="px-6 py-3 text-sm font-mono text-[var(--text-primary)]">{order.purchaseOrderId}</td>
                    <td className="px-6 py-3 text-sm text-[var(--text-secondary)]">{order.customerName}</td>
                    <td className="px-6 py-3">
                      <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', {
                        'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]': order.status === 'Shipped',
                        'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)]': order.status === 'Acknowledged',
                        'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]': order.status === 'Created',
                        'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)]': order.status === 'Cancelled',
                      })}>{order.status}</span>
                    </td>
                    <td className="px-6 py-3">
                      <span className={clsx('inline-flex items-center gap-1 text-xs font-medium', {
                        'text-sky-600': order.fulfillmentType === 'WFS',
                        'text-[var(--text-secondary)]': order.fulfillmentType === 'SELF',
                      })}>
                        {order.fulfillmentType === 'UNKNOWN' ? '—' : <>
                          {order.fulfillmentType === 'WFS' ? <Package className="w-3 h-3" /> : <Truck className="w-3 h-3" />}
                          {order.fulfillmentType === 'WFS' ? 'WFS' : 'Self'}
                        </>}
                      </span>
                    </td>
                    <td className="px-6 py-3 text-sm text-[var(--text-secondary)] text-right">{order.items}</td>
                    <td className="px-6 py-3 text-sm text-[var(--text-primary)] text-right font-medium">{order.amount}</td>
                    <td className="px-6 py-3 text-sm text-[var(--text-tertiary)] text-right">{order.placedAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="px-6 py-3 border-t border-[var(--border-subtle)] flex items-center justify-between text-xs text-[var(--text-tertiary)]">
            <span>Showing {filteredOrders.length} of {displayOrders.length} orders</span>
            <PermissionGate resource="integrations" action="create">
              <button type="button" disabled title="Live sync with Walmart is not available yet" className="enterprise-btn enterprise-btn-ghost text-xs">
                <RefreshCw className="w-3.5 h-3.5" /> Sync Now
              </button>
            </PermissionGate>
          </div>
        </div>
      )}
    </div>
  )
}
