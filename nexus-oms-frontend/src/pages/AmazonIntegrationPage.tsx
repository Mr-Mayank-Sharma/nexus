import { useEffect, useState } from 'react'
import {
  Globe, ShoppingCart, Settings, RefreshCw, Link, Loader2,
  CheckCircle, XCircle, Download,
} from 'lucide-react'
import { clsx } from 'clsx'
import { configureConnector, disconnectConnector } from '../api/connectors/connectorClient'
import { useMarketplaceStatus } from '../features/marketplace/useMarketplaceStatus'
import { loadChannelOrders } from '../features/marketplace/channelOrders'
import NotLiveBanner from '../features/marketplace/NotLiveBanner'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'
import PermissionGate from '../components/rbac/PermissionGate'
import { downloadCsv } from '../utils/csv'

interface AmazonOrder {
  id: string
  orderId: string
  status: string
  items: number
  amount: string
  placedAt: string
  marketplace: string
}

const MARKETPLACES = [
  { value: 'US', label: 'United States', region: 'North America' },
  { value: 'CA', label: 'Canada', region: 'North America' },
  { value: 'UK', label: 'United Kingdom', region: 'Europe' },
  { value: 'DE', label: 'Germany', region: 'Europe' },
]




export default function AmazonIntegrationPage() {
  const [fetchedOrders, setFetchedOrders] = useState<AmazonOrder[]>([])

  const [ordersError, setOrdersError] = useState<string | null>(null)
  useEffect(() => {
    const ctl = new AbortController()
    loadChannelOrders('amazon', ctl.signal)
      .then(rows => { setOrdersError(null); setFetchedOrders(rows.map(r => (({ id: r.id, orderId: r.orderId, status: r.status, items: r.items, amount: r.amount, placedAt: r.placedAt, marketplace: form.marketplace })))) })
      .catch(err => { if (!ctl.signal.aborted) setOrdersError(err instanceof Error ? err.message : 'Could not load orders') })
    return () => ctl.abort()
  }, [])
  const [connecting, setConnecting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const [filterMarketplace, setFilterMarketplace] = useState('ALL')
  const { addToast } = useToast()
  const { hasCredentials: credentialsSaved, ordersInNexus, refresh } = useMarketplaceStatus('amazon')

  const [form, setForm] = useState({
    marketplace: 'US',
    storeName: '',
    sellerId: '',
    mwsAuthToken: '',
    autoSyncOrders: true,
    syncIntervalMinutes: 15,
    importUnshippedOrders: true,
    syncInventory: false,
  })

  async function handleConnect() {
    if (!form.sellerId.trim() || !form.mwsAuthToken.trim()) {
      addToast({ type: 'warning', title: 'Amazon credentials are required' }); return
    }
    setConnecting(true)
    try {
      const res = await configureConnector('amazon', { clientId: form.sellerId, clientSecret: form.mwsAuthToken, marketplaceId: form.marketplace, sandbox: true }) as { message?: string }
      refresh()
      // Saved, not verified: no check against Amazon is made, so this must not read as "credentialsSaved".
      addToast({ type: 'info', title: res.message ?? 'Credentials saved (not verified)' })
    } catch (err) {
      addToast({ type: 'error', title: `Could not save credentials: ${err instanceof Error ? err.message : 'the server did not respond'}` })
    } finally {
      setConnecting(false)
    }
  }

  async function handleDisconnect() {
    try {
      await disconnectConnector('amazon')
      setFetchedOrders([])
      refresh()
      addToast({ type: 'info', title: 'Disconnected from Amazon' })
    } catch (err) {
      addToast({ type: 'error', title: `Could not disconnect: ${err instanceof Error ? err.message : 'the server did not respond'}` })
    }
  }

  async function handleSave() {
    setSaving(true)
    try {
      const res = await configureConnector('amazon', { marketplaceId: form.marketplace, sandbox: true }) as { message?: string }
      refresh()
      addToast({ type: 'success', title: res.message ?? 'Amazon settings saved' })
    } catch (err) {
      addToast({ type: 'error', title: `Could not save settings: ${err instanceof Error ? err.message : 'the server did not respond'}` })
    } finally {
      setSaving(false)
    }
  }

  const displayOrders = fetchedOrders
  const filteredOrders = displayOrders.filter(o => {
    const matchesSearch = !searchTerm || o.orderId.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesMarketplace = filterMarketplace === 'ALL' || o.marketplace === filterMarketplace
    return matchesSearch && matchesMarketplace
  })

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><Globe className="w-7 h-7 text-[var(--nexus-warning-500)]" /> Amazon Integration</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Connect Amazon Seller Central to sync orders, inventory, and fulfillment data</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
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

      <NotLiveBanner marketplace="Amazon" credentialsSaved={credentialsSaved} />
      {ordersError && <p role="alert" className="text-sm text-[var(--nexus-error-600)]">Could not load orders: {ordersError}</p>}

      {/* Connection Card */}
      <div className="card">
        <div className="card-header">
          <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Link className="w-4 h-4 text-[var(--nexus-warning-500)]" /> Connection</h3>
        </div>
        <div className="card-body">
          <div className="flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-sm text-[var(--text-secondary)]">
                Status: <span className={clsx('font-semibold', credentialsSaved ? 'text-[var(--nexus-success-600)]' : 'text-[var(--text-secondary)]')}>{credentialsSaved ? 'Credentials saved (not verified)' : 'Not configured'}</span>
              </p>
              {credentialsSaved && (
                <p className="text-xs text-[var(--text-tertiary)]">Marketplace: {MARKETPLACES.find(m => m.value === form.marketplace)?.label} · Seller ID: {form.sellerId}</p>
              )}
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
                  {connecting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Globe className="w-4 h-4" />}
                  {connecting ? 'Connecting...' : 'Connect'}
                </button>
              </PermissionGate>
            )}
          </div>
        </div>
      </div>

      {/* Store Settings */}
      <div className="card">
        <div className="card-header">
          <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Settings className="w-4 h-4 text-[var(--nexus-warning-500)]" /> Store Settings</h3>
        </div>
        <div className="card-body space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Marketplace</label>
              <select value={form.marketplace} onChange={e => setForm({ ...form, marketplace: e.target.value })} className="input w-full text-sm">
                {MARKETPLACES.map(m => <option key={m.value} value={m.value}>{m.label} ({m.region})</option>)}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Store Name</label>
              <input value={form.storeName} onChange={e => setForm({ ...form, storeName: e.target.value })} className="input w-full text-sm" placeholder="My Amazon Store" />
            </div>
            <div>
              <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Seller ID</label>
              <input value={form.sellerId} onChange={e => setForm({ ...form, sellerId: e.target.value })} className="input w-full font-mono text-sm" placeholder="AXXXXXXXXXXXXX" />
            </div>
            <div>
              <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">MWS Auth Token</label>
              <input type="password" value={form.mwsAuthToken} onChange={e => setForm({ ...form, mwsAuthToken: e.target.value })} className="input w-full font-mono text-sm" placeholder="amzn.mws.xxxxxxxx" />
            </div>
          </div>
        </div>
      </div>

      {/* Sync Settings */}
      <div className="card">
        <div className="card-header">
          <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><RefreshCw className="w-4 h-4 text-[var(--nexus-warning-500)]" /> Sync Settings</h3>
        </div>
        <div className="card-body space-y-4">
          <div className="flex items-center gap-6">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.autoSyncOrders} onChange={e => setForm({ ...form, autoSyncOrders: e.target.checked })} className="rounded border-[var(--border-default)]" />
              Auto-sync orders
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.importUnshippedOrders} onChange={e => setForm({ ...form, importUnshippedOrders: e.target.checked })} className="rounded border-[var(--border-default)]" />
              Import unshipped orders
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.syncInventory} onChange={e => setForm({ ...form, syncInventory: e.target.checked })} className="rounded border-[var(--border-default)]" />
              Sync inventory
            </label>
          </div>
          <div className="flex items-center gap-3">
            <label className="text-sm text-[var(--text-secondary)]">Sync interval (minutes):</label>
            <input type="number" value={form.syncIntervalMinutes} onChange={e => setForm({ ...form, syncIntervalMinutes: parseInt(e.target.value) || 15 })}
              className="input w-20 text-sm" min={5} max={1440} />
          </div>
        </div>
        <div className="card-footer flex justify-between">
          <PermissionGate resource="integrations" action="create">
            <button type="button" disabled title="Live sync with Amazon is not available yet" className="enterprise-btn enterprise-btn-secondary text-sm">
              <RefreshCw className="w-4 h-4" />
              Force Sync
            </button>
          </PermissionGate>
          <PermissionGate resource="integrations" action="edit">
            <button type="button" onClick={handleSave} disabled={saving} className="enterprise-btn enterprise-btn-primary text-sm">
              {saving && <Loader2 className="w-4 h-4 animate-spin" />}
              Save Settings
            </button>
          </PermissionGate>
        </div>
      </div>

      {/* Order Stats */}
      <div className="card p-5 text-center sm:max-w-xs">
        <ShoppingCart className="w-6 h-6 text-[var(--nexus-primary-500)] mx-auto mb-2" />
        <p className="text-2xl font-bold text-[var(--text-primary)]">{ordersInNexus}</p>
        <p className="text-xs text-[var(--text-secondary)]">Amazon-channel orders in Nexus</p>
      </div>

      {/* Orders Table */}
      <div className="card">
        <div className="card-header">
          <div className="flex items-center justify-between w-full">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><ShoppingCart className="w-4 h-4 text-[var(--nexus-warning-500)]" /> Recent Amazon Orders</h3>
            <div className="flex items-center gap-3">
              <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder="Search orders..." minChars={0} />
              <select value={filterMarketplace} onChange={e => setFilterMarketplace(e.target.value)} className="input text-xs w-28">
                <option value="ALL">All Markets</option>
                {MARKETPLACES.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
              </select>
            </div>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order ID</th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Marketplace</th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Items</th>
                <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Amount</th>
                <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Placed At</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--surface-sunken)]">
              {filteredOrders.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-sm text-[var(--text-tertiary)]">No orders found matching your filters</td>
                </tr>
              ) : filteredOrders.map(order => (
                <tr key={order.id} className="hover:bg-[var(--surface-sunken)]">
                  <td className="px-6 py-3 text-sm font-mono text-[var(--text-primary)]">{order.orderId}</td>
                  <td className="px-6 py-3 text-sm text-[var(--text-secondary)]">{order.marketplace}</td>
                  <td className="px-6 py-3">
                    <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', {
                      'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]': order.status === 'Shipped',
                      'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)]': order.status === 'Unshipped',
                      'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]': order.status === 'Pending',
                      'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)]': order.status === 'Cancelled',
                    })}>{order.status}</span>
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
          <button type="button" disabled={filteredOrders.length === 0} className="enterprise-btn enterprise-btn-ghost text-xs"
            onClick={() => downloadCsv(`amazon-orders-${new Date().toISOString().slice(0, 10)}.csv`,
              ['Amazon order', 'Marketplace', 'Status', 'Items', 'Amount', 'Placed'],
              filteredOrders.map(o => [o.orderId, o.marketplace, o.status, o.items, o.amount, o.placedAt]))}>
            <Download className="w-3.5 h-3.5" /> Export</button>
        </div>
      </div>
    </div>
  )
}
