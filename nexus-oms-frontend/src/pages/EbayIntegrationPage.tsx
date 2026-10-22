import { useEffect, useState } from 'react'
import {
  ShoppingBag, Settings, RefreshCw, Link, Loader2,
  CheckCircle, XCircle, Tag, ArrowRight,
} from 'lucide-react'
import { clsx } from 'clsx'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'
import PermissionGate from '../components/rbac/PermissionGate'
import { configureConnector, disconnectConnector } from '../api/connectors/connectorClient'
import { useMarketplaceStatus } from '../features/marketplace/useMarketplaceStatus'
import { loadChannelOrders } from '../features/marketplace/channelOrders'
import NotLiveBanner from '../features/marketplace/NotLiveBanner'

interface EbayOrder {
  id: string
  orderId: string
  buyer: string
  status: string
  items: number
  amount: string
  placedAt: string
  site: string
}

interface CategoryMapping {
  ebayCategory: string
  ebayCategoryId: string
  nexusCategory: string
}

const EBAY_SITES = [
  { value: 'US', label: 'eBay US (eBay Motors)', locale: 'en_US', currency: 'USD' },
  { value: 'UK', label: 'eBay UK', locale: 'en_GB', currency: 'GBP' },
  { value: 'DE', label: 'eBay Germany', locale: 'de_DE', currency: 'EUR' },
  { value: 'AU', label: 'eBay Australia', locale: 'en_AU', currency: 'AUD' },
]






export default function EbayIntegrationPage() {
  const [searchTerm, setSearchTerm] = useState('')
  const [activeTab, setActiveTab] = useState<'settings' | 'categories' | 'orders'>('settings')
  const [filterSite, setFilterSite] = useState('ALL')
  const { addToast } = useToast()
  const { hasCredentials: credentialsSaved, ordersInNexus, refresh } = useMarketplaceStatus('ebay')

  const [fetchedOrders, setFetchedOrders] = useState<EbayOrder[]>([])

  const [ordersError, setOrdersError] = useState<string | null>(null)
  useEffect(() => {
    const ctl = new AbortController()
    loadChannelOrders('ebay', ctl.signal)
      .then(rows => { setOrdersError(null); setFetchedOrders(rows.map(r => (({ id: r.id, orderId: r.orderId, buyer: r.customer, status: r.status, items: r.items, amount: r.amount, placedAt: r.placedAt, site: form.site })))) })
      .catch(err => { if (!ctl.signal.aborted) setOrdersError(err instanceof Error ? err.message : 'Could not load orders') })
    return () => ctl.abort()
  }, [])
  const [connecting, setConnecting] = useState(false)
  const [saving, setSaving] = useState(false)

  const [form, setForm] = useState({
    site: 'US',
    storeName: '',
    apiKey: '',
    authToken: '',
    autoSyncOrders: true,
    syncIntervalMinutes: 15,
    autoSyncInventory: false,
    syncReturns: true,
  })

  const [categoryForm, setCategoryForm] = useState({
    ebayCategory: '',
    ebayCategoryId: '',
    nexusCategory: '',
  })

  const [categories, setCategories] = useState<CategoryMapping[]>([])
  const [showAddCategory, setShowAddCategory] = useState(false)

  async function handleConnect() {
    if (!form.apiKey.trim() || !form.authToken.trim()) {
      addToast({ type: 'warning', title: 'eBay credentials are required' }); return
    }
    setConnecting(true)
    try {
      const res = await configureConnector('ebay', { clientId: form.apiKey, clientSecret: form.authToken, siteId: form.site, sandbox: true }) as { message?: string }
      refresh()
      // Saved, not verified: no check against eBay is made, so this must not read as "credentialsSaved".
      addToast({ type: 'info', title: res.message ?? 'Credentials saved (not verified)' })
    } catch (err) {
      addToast({ type: 'error', title: `Could not save credentials: ${err instanceof Error ? err.message : 'the server did not respond'}` })
    } finally {
      setConnecting(false)
    }
  }

  async function handleDisconnect() {
    try {
      await disconnectConnector('ebay')
      setFetchedOrders([])
      refresh()
      addToast({ type: 'info', title: 'Disconnected from eBay' })
    } catch (err) {
      addToast({ type: 'error', title: `Could not disconnect: ${err instanceof Error ? err.message : 'the server did not respond'}` })
    }
  }

  async function handleSave() {
    setSaving(true)
    try {
      const res = await configureConnector('ebay', { siteId: form.site, sandbox: true }) as { message?: string }
      refresh()
      addToast({ type: 'success', title: res.message ?? 'eBay settings saved' })
    } catch (err) {
      addToast({ type: 'error', title: `Could not save settings: ${err instanceof Error ? err.message : 'the server did not respond'}` })
    } finally {
      setSaving(false)
    }
  }

  function handleAddCategory() {
    if (!categoryForm.ebayCategory.trim() || !categoryForm.nexusCategory.trim()) {
      addToast({ type: 'warning', title: 'eBay category and Nexus category are required' }); return
    }
    setCategories([...categories, { ...categoryForm, ebayCategoryId: categoryForm.ebayCategoryId || 'N/A' }])
    setCategoryForm({ ebayCategory: '', ebayCategoryId: '', nexusCategory: '' })
    setShowAddCategory(false)
    addToast({ type: 'success', title: 'Category mapping added' })
  }

  function handleRemoveCategory(index: number) {
    setCategories(categories.filter((_, i) => i !== index))
    addToast({ type: 'info', title: 'Category mapping removed' })
  }

  const displayOrders = fetchedOrders
  const filteredOrders = displayOrders.filter(o => {
    const matchesSearch = !searchTerm || o.orderId.toLowerCase().includes(searchTerm.toLowerCase()) || o.buyer.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesSite = filterSite === 'ALL' || o.site === filterSite
    return matchesSearch && matchesSite
  })

  const tabs = [
    { id: 'settings', label: 'Settings', icon: <Settings className="w-4 h-4" /> },
    { id: 'categories', label: 'Category Mapping', icon: <Tag className="w-4 h-4" /> },
    { id: 'orders', label: 'Orders', icon: <ShoppingBag className="w-4 h-4" /> },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><ShoppingBag className="w-7 h-7 text-[var(--nexus-ai-500)]" /> eBay Integration</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Connect your eBay store to synchronize orders, categories, and inventory</p>
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

      <NotLiveBanner marketplace="eBay" credentialsSaved={credentialsSaved} />
      {ordersError && <p role="alert" className="text-sm text-[var(--nexus-error-600)]">Could not load orders: {ordersError}</p>}

      {/* Connection Card */}
      <div className="card">
        <div className="card-header">
          <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Link className="w-4 h-4 text-[var(--nexus-ai-500)]" /> eBay API Connection</h3>
        </div>
        <div className="card-body">
          <div className="flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-sm text-[var(--text-secondary)]">
                Status: <span className={clsx('font-semibold', credentialsSaved ? 'text-[var(--nexus-success-600)]' : 'text-[var(--text-secondary)]')}>{credentialsSaved ? 'Credentials saved (not verified)' : 'Not configured'}</span>
              </p>
              {credentialsSaved && <p className="text-xs text-[var(--text-tertiary)]">eBay {EBAY_SITES.find(s => s.value === form.site)?.label} · Store: {form.storeName || 'N/A'}</p>}
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
                  {connecting ? <Loader2 className="w-4 h-4 animate-spin" /> : <ShoppingBag className="w-4 h-4" />}
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
              activeTab === tab.id ? 'border-[var(--nexus-ai-600)] text-[var(--nexus-ai-600)]' : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-secondary)]'
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
              <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Settings className="w-4 h-4 text-[var(--nexus-ai-500)]" /> Store Settings</h3>
            </div>
            <div className="card-body space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">eBay Site</label>
                  <select value={form.site} onChange={e => setForm({ ...form, site: e.target.value })} className="input w-full text-sm">
                    {EBAY_SITES.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Store Name</label>
                  <input value={form.storeName} onChange={e => setForm({ ...form, storeName: e.target.value })} className="input w-full text-sm" placeholder="My eBay Store" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">eBay API Key (Client ID)</label>
                  <input value={form.apiKey} onChange={e => setForm({ ...form, apiKey: e.target.value })} className="input w-full font-mono text-sm" placeholder="Your-eBay-API-Key" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Auth Token (RuName)</label>
                  <input type="password" value={form.authToken} onChange={e => setForm({ ...form, authToken: e.target.value })} className="input w-full font-mono text-sm" placeholder="v^1.1#i..." />
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
                  <input type="checkbox" checked={form.autoSyncInventory} onChange={e => setForm({ ...form, autoSyncInventory: e.target.checked })} className="rounded border-[var(--border-default)]" />
                  Auto-sync inventory
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={form.syncReturns} onChange={e => setForm({ ...form, syncReturns: e.target.checked })} className="rounded border-[var(--border-default)]" />
                  Sync returns
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
                <button type="button" disabled title="Live sync with eBay is not available yet" className="enterprise-btn enterprise-btn-secondary text-sm">
                  <RefreshCw className="w-4 h-4" />
                  Run Sync
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
        </>
      )}

      {activeTab === 'categories' && (
        <div className="card">
          <div className="card-header flex justify-between items-center">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Tag className="w-4 h-4 text-[var(--nexus-ai-500)]" /> eBay → Nexus Category Mapping</h3>
            <PermissionGate resource="integrations" action="create">
              <button type="button" onClick={() => setShowAddCategory(true)} className="enterprise-btn enterprise-btn-secondary text-xs">
                <Tag className="w-3.5 h-3.5" /> Add Mapping
              </button>
            </PermissionGate>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">eBay Category ID</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">eBay Category</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Nexus Category</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--surface-sunken)]">
                {categories.map((cat, index) => (
                  <tr key={index} className="hover:bg-[var(--surface-sunken)]">
                    <td className="px-6 py-3 text-sm font-mono text-[var(--text-secondary)]">{cat.ebayCategoryId}</td>
                    <td className="px-6 py-3 text-sm text-[var(--text-primary)]">{cat.ebayCategory}</td>
                    <td className="px-6 py-3">
                      <div className="flex items-center gap-2">
                        <ArrowRight className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />
                        <span className="text-sm font-medium text-[var(--nexus-ai-600)]">{cat.nexusCategory}</span>
                      </div>
                    </td>
                    <td className="px-6 py-3 text-right">
                      <PermissionGate resource="integrations" action="delete">
                        <button type="button" onClick={() => handleRemoveCategory(index)} className="enterprise-btn enterprise-btn-ghost text-xs text-[var(--nexus-error-500)]">
                          <XCircle className="w-3.5 h-3.5" />
                        </button>
                      </PermissionGate>
                    </td>
                  </tr>
                ))}
                {categories.length === 0 && (
                  <tr>
                    <td colSpan={4} className="px-6 py-8 text-center text-sm text-[var(--text-tertiary)]">No category mappings defined yet</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {showAddCategory && (
            <div className="border-t border-[var(--border-subtle)] p-6 bg-[var(--surface-sunken)]/50">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">eBay Category ID</label>
                  <input value={categoryForm.ebayCategoryId} onChange={e => setCategoryForm({ ...categoryForm, ebayCategoryId: e.target.value })}
                    className="input w-full font-mono text-sm" placeholder="e.g. 293" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">eBay Category Name</label>
                  <input value={categoryForm.ebayCategory} onChange={e => setCategoryForm({ ...categoryForm, ebayCategory: e.target.value })}
                    className="input w-full text-sm" placeholder="e.g. Consumer Electronics" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Nexus Category</label>
                  <select value={categoryForm.nexusCategory} onChange={e => setCategoryForm({ ...categoryForm, nexusCategory: e.target.value })} className="input w-full text-sm">
                    <option value="">Select...</option>
                    <option value="Electronics">Electronics</option>
                    <option value="Apparel">Apparel</option>
                    <option value="Home Goods">Home Goods</option>
                    <option value="Sports">Sports</option>
                    <option value="Toys">Toys</option>
                    <option value="Beauty">Beauty</option>
                    <option value="Automotive">Automotive</option>
                    <option value="Books">Books</option>
                  </select>
                </div>
              </div>
              <div className="flex justify-end gap-2 mt-4">
                <button type="button" onClick={() => setShowAddCategory(false)} className="enterprise-btn enterprise-btn-secondary text-xs">Cancel</button>
                <PermissionGate resource="integrations" action="create">
                  <button type="button" onClick={handleAddCategory} className="enterprise-btn enterprise-btn-primary text-xs">
                    <Tag className="w-3.5 h-3.5" /> Add Mapping
                  </button>
                </PermissionGate>
              </div>
            </div>
          )}
        </div>
      )}

      {activeTab === 'orders' && (
        <div className="card">
          <div className="card-header">
            <div className="flex items-center justify-between w-full">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><ShoppingBag className="w-4 h-4 text-[var(--nexus-ai-500)]" /> eBay-channel orders in Nexus ({ordersInNexus})</h3>
              <div className="flex items-center gap-3">
                <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder="Search orders..." minChars={0} />
                <select value={filterSite} onChange={e => setFilterSite(e.target.value)} className="input text-xs w-28">
                  <option value="ALL">All Sites</option>
                  {EBAY_SITES.map(s => <option key={s.value} value={s.value}>{s.value}</option>)}
                </select>
              </div>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Order ID</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Buyer</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Site</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
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
                    <td className="px-6 py-3 text-sm font-mono text-[var(--text-primary)]">{order.orderId}</td>
                    <td className="px-6 py-3 text-sm text-[var(--text-secondary)]">{order.buyer}</td>
                    <td className="px-6 py-3 text-sm text-[var(--text-secondary)]">{order.site}</td>
                    <td className="px-6 py-3">
                      <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', {
                        'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]': order.status === 'Shipped',
                        'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)]': order.status === 'Paid',
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
            <div className="flex items-center gap-2">
              <PermissionGate resource="integrations" action="create">
                <button type="button" disabled title="Live sync with eBay is not available yet" className="enterprise-btn enterprise-btn-ghost text-xs">
                  <RefreshCw className="w-3.5 h-3.5" /> Sync Now
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
