import { useState, useEffect } from 'react'
import {
  Plus,   Trash2, RefreshCw, ExternalLink, Store, ShoppingBag, Globe,
  Loader2, X, Clock, CheckCircle, XCircle, Link,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import * as api from '../api/integrationStores'
import { IntegrationStore, StoreSyncStatus } from '../api/integrationStores'
import StatusBadge from '../components/common/StatusBadge'

const PLATFORMS = [
  { value: 'SHOPIFY', label: 'Shopify', icon: <ShoppingBag className="w-5 h-5" />, color: 'bg-emerald-500' },
  { value: 'BIGCOMMERCE', label: 'BigCommerce', icon: <Store className="w-5 h-5" />, color: 'bg-blue-500' },
  { value: 'AMAZON', label: 'Amazon', icon: <Globe className="w-5 h-5" />, color: 'bg-orange-500' },
  { value: 'WOOCOMMERCE', label: 'WooCommerce', icon: <ShoppingBag className="w-5 h-5" />, color: 'bg-purple-500' },
  { value: 'MANUAL', label: 'Manual', icon: <Store className="w-5 h-5" />, color: 'bg-gray-500' },
]

const SYNC_DEFS: Record<string, { label: string; description: string }> = {
  ORDER_IMPORT: { label: 'Import Orders', description: 'Pull orders from platform' },
  PRODUCT_SYNC: { label: 'Sync Products', description: 'Sync product catalog' },
  INVENTORY_PUSH: { label: 'Push Inventory', description: 'Push inventory levels' },
  FULFILLMENT_PUSH: { label: 'Push Fulfillments', description: 'Push tracking to platform' },
  REFUND_PUSH: { label: 'Push Refunds', description: 'Push refunds to platform' },
}

export default function IntegrationStoresPage() {
  const [stores, setStores] = useState<IntegrationStore[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const [selectedStore, setSelectedStore] = useState<IntegrationStore | null>(null)
  const [storeStatus, setStoreStatus] = useState<StoreSyncStatus | null>(null)
  const [syncing, setSyncing] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const { addToast } = useToast()

  const [form, setForm] = useState({
    storeCode: '', storeName: '', platform: 'SHOPIFY', currency: 'USD',
    defaultLocale: 'en_US', timezone: 'UTC', externalStoreId: '', externalDomain: '',
    settings: {} as Record<string, string>,
  })

  useEffect(() => { fetchStores() }, [])

  async function fetchStores() {
    try {
      setLoading(true)
      const res = await api.getStores()
      setStores(res.data || [])
    } catch { addToast({ type: 'error', title: 'Failed to load stores' })
    } finally { setLoading(false) }
  }

  async function openStore(st: IntegrationStore) {
    setSelectedStore(st)
    setStoreStatus(null)
    try {
      const res = await api.getStoreSyncStatus(st.id)
      setStoreStatus(res.data)
    } catch { addToast({ type: 'error', title: 'Failed to load sync status' }) }
  }

  function openCreate() {
    setForm({
      storeCode: '', storeName: '', platform: 'SHOPIFY', currency: 'USD',
      defaultLocale: 'en_US', timezone: 'UTC', externalStoreId: '', externalDomain: '',
      settings: {},
    })
    setShowCreate(true)
  }

  async function handleCreate() {
    if (!form.storeCode.trim() || !form.storeName.trim()) {
      addToast({ type: 'warning', title: 'Store code and name are required' }); return
    }
    setSaving(true)
    try {
      await api.createStore(form)
      addToast({ type: 'success', title: 'Store created' })
      setShowCreate(false)
      await fetchStores()
    } catch { addToast({ type: 'error', title: 'Failed to create store' })
    } finally { setSaving(false) }
  }

  async function handleDelete(id: string) {
    try {
      await api.deleteStore(id)
      addToast({ type: 'success', title: 'Store deleted' })
      if (selectedStore?.id === id) { setSelectedStore(null); setStoreStatus(null) }
      await fetchStores()
    } catch { addToast({ type: 'error', title: 'Failed to delete store' }) }
  }

  async function handleSync(type: string) {
    if (!selectedStore) return
    setSyncing(type)
    try {
      let res: any
      const platform = selectedStore.platform
      if (platform === 'SHOPIFY') {
        switch (type) {
          case 'ORDER_IMPORT': res = await api.shopifySyncOrders(selectedStore.id); break
          case 'PRODUCT_SYNC': res = await api.shopifySyncProducts(selectedStore.id); break
          case 'INVENTORY_PUSH': res = await api.shopifyPushInventory(selectedStore.id); break
          case 'FULFILLMENT_PUSH': res = await api.shopifyPushFulfillments(selectedStore.id); break
          case 'REFUND_PUSH': res = await api.shopifyPushRefunds(selectedStore.id); break
        }
      } else if (platform === 'BIGCOMMERCE') {
        const { default: bc } = await import('../api/bigcommerce')
        switch (type) {
          case 'ORDER_IMPORT': res = await bc.syncOrders(); break
          case 'PRODUCT_SYNC': res = await bc.syncProducts(); break
          case 'INVENTORY_PUSH': res = await bc.pushInventory(); break
          case 'FULFILLMENT_PUSH': res = await bc.pushShipments(); break
          case 'REFUND_PUSH': res = await bc.pushRefunds(); break
        }
      } else {
        addToast({ type: 'info', title: 'Sync not yet implemented for this platform' })
      }
      if (res?.data) {
        addToast({ type: res.data.status === 'COMPLETED' ? 'success' : 'error',
          title: `${type}: ${res.data.itemsSucceeded} OK, ${res.data.itemsFailed} failed` })
      }
      // Refresh status
      const statusRes = await api.getStoreSyncStatus(selectedStore.id)
      setStoreStatus(statusRes.data)
    } catch {
      addToast({ type: 'error', title: `Sync ${type} failed` })
    } finally { setSyncing(null) }
  }

  async function handleRegisterWebhooks() {
    if (!selectedStore) return
    try {
      if (selectedStore.platform === 'SHOPIFY') {
        await api.shopifyRegisterWebhooks(selectedStore.id, window.location.origin)
      }
      addToast({ type: 'success', title: 'Webhooks registered' })
    } catch { addToast({ type: 'error', title: 'Failed to register webhooks' }) }
  }

  const platformInfo = (p: string) => PLATFORMS.find(pf => pf.value === p) || PLATFORMS[PLATFORMS.length - 1]
  const settingFields = (platform: string): { key: string; label: string; type?: string }[] => {
    if (platform === 'SHOPIFY') return [
      { key: 'shop_domain', label: 'Shop Domain (e.g. mystore.myshopify.com)' },
      { key: 'access_token', label: 'Admin API Access Token', type: 'password' },
      { key: 'api_version', label: 'API Version (default: 2024-10)' },
    ]
    if (platform === 'BIGCOMMERCE') return [
      { key: 'store_hash', label: 'Store Hash' },
      { key: 'access_token', label: 'API Access Token', type: 'password' },
      { key: 'client_id', label: 'Client ID' },
    ]
    return [{ key: 'api_key', label: 'API Key', type: 'password' }]
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Sales Channels</h1>
          <p className="text-sm text-gray-500 mt-1">OFBiz-style integration store management — connect Shopify, BigCommerce, and more</p>
        </div>
        <button onClick={openCreate} className="btn-primary text-sm">
          <Plus className="w-4 h-4" /> New Store
        </button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
        </div>
      ) : stores.length === 0 ? (
        <div className="text-center py-16 card">
          <Store className="w-12 h-12 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 text-sm">No connected stores. Add your first sales channel to begin syncing.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-1 space-y-2">
            {stores.map(st => {
              const pf = platformInfo(st.platform)
              return (
                <button key={st.id} onClick={() => openStore(st)}
                  className={`w-full text-left card p-4 flex items-center gap-3 transition-all ${
                    selectedStore?.id === st.id ? 'ring-2 ring-primary-500' : ''
                  }`}>
                  <div className={`w-10 h-10 ${pf.color} rounded-lg flex items-center justify-center flex-shrink-0`}>
                    <div className="text-white">{pf.icon}</div>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-gray-900 truncate">{st.storeName}</p>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-xs text-gray-500">{st.storeCode}</span>
                      <span className="text-xs bg-gray-100 px-1.5 py-0.5 rounded text-gray-600">{st.platform}</span>
                      <StatusBadge status={st.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                    </div>
                  </div>
                </button>
              )
            })}
          </div>

          <div className="lg:col-span-2">
            {selectedStore ? (
              <div className="space-y-4">
                <div className="card">
                  <div className="card-header flex justify-between items-center">
                    <div className="flex items-center gap-3">
                      <div className={`w-8 h-8 ${platformInfo(selectedStore.platform).color} rounded-lg flex items-center justify-center`}>
                        <div className="text-white w-4 h-4">{platformInfo(selectedStore.platform).icon}</div>
                      </div>
                      <div>
                        <h3 className="text-sm font-semibold text-gray-900">{selectedStore.storeName}</h3>
                        <p className="text-xs text-gray-500">{selectedStore.platform} · {selectedStore.storeCode}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      {selectedStore.externalDomain && (
                        <a href={`https://${selectedStore.externalDomain}`} target="_blank" rel="noopener noreferrer"
                          className="btn-ghost text-xs"><ExternalLink className="w-3 h-3" /></a>
                      )}
                      <button onClick={() => handleDelete(selectedStore.id)} className="btn-ghost text-xs text-red-500">
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>

                  {storeStatus && (
                    <div className="px-6 pb-4">
                      <h4 className="text-xs font-semibold text-gray-500 uppercase mb-3">Sync Actions</h4>
                      <div className="grid grid-cols-2 gap-3">
                        {storeStatus.syncTypes.map(st => {
                          const def = SYNC_DEFS[st.syncType] || { label: st.syncType, description: '' }
                          return (
                            <div key={st.syncType} className="border border-gray-200 rounded-lg p-3">
                              <div className="flex items-center justify-between mb-2">
                                <span className="text-xs font-medium text-gray-700">{def.label}</span>
                                {st.lastSyncStatus === 'COMPLETED' ? <CheckCircle className="w-3.5 h-3.5 text-green-500" /> :
                                 st.lastSyncStatus === 'FAILED' ? <XCircle className="w-3.5 h-3.5 text-red-500" /> :
                                 <Clock className="w-3.5 h-3.5 text-gray-400" />}
                              </div>
                              <p className="text-xs text-gray-400 mb-2">{def.description}</p>
                              {st.lastSyncAt && (
                                <p className="text-xs text-gray-400 mb-2">Last: {new Date(st.lastSyncAt).toLocaleString()}</p>
                              )}
                              <button onClick={() => handleSync(st.syncType)} disabled={syncing === st.syncType}
                                className="btn-primary text-xs w-full">
                                {syncing === st.syncType ? <Loader2 className="w-3 h-3 animate-spin" /> : <RefreshCw className="w-3 h-3" />}
                                {syncing === st.syncType ? 'Running...' : 'Run Sync'}
                              </button>
                            </div>
                          )
                        })}
                      </div>

                      <div className="flex items-center gap-2 mt-4">
                        <button onClick={handleRegisterWebhooks} className="btn-secondary text-xs">
                          <Link className="w-3.5 h-3.5" /> Register Webhooks
                        </button>
                      </div>
                    </div>
                  )}
                </div>

                <div className="card">
                  <div className="card-header"><h3 className="text-sm font-semibold text-gray-900">Connection Settings</h3></div>
                  <div className="card-body space-y-3">
                    {settingFields(selectedStore.platform).map(field => (
                      <div key={field.key}>
                        <label className="block text-xs font-medium text-gray-600 mb-1">{field.label}</label>
                        <input className="input w-full text-sm font-mono" type={field.type || 'text'}
                          value={storeStatus?.settings?.[field.key] || ''} disabled />
                      </div>
                    ))}
                    <p className="text-xs text-gray-400 mt-2">Manage credentials via the store's configuration in control panel.</p>
                  </div>
                </div>
              </div>
            ) : (
              <div className="card p-12 text-center text-gray-400">
                <Store className="w-16 h-16 mx-auto mb-4 text-gray-200" />
                <p className="text-sm">Select a store to view sync status and actions</p>
              </div>
            )}
          </div>
        </div>
      )}

      {showCreate && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">New Sales Channel</h2>
              <button onClick={() => setShowCreate(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Platform</label>
                <select value={form.platform} onChange={e => setForm({ ...form, platform: e.target.value, settings: {} })} className="input w-full">
                  {PLATFORMS.map(pf => <option key={pf.value} value={pf.value}>{pf.label}</option>)}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Store Code</label>
                  <input value={form.storeCode} onChange={e => setForm({ ...form, storeCode: e.target.value })} className="input w-full font-mono" placeholder="shopify-us" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Store Name</label>
                  <input value={form.storeName} onChange={e => setForm({ ...form, storeName: e.target.value })} className="input w-full" placeholder="US Shopify Store" />
                </div>
              </div>

              <div className="border-t border-gray-100 pt-4">
                <h4 className="text-sm font-medium text-gray-700 mb-3">API Credentials</h4>
                {settingFields(form.platform).map(field => (
                  <div key={field.key} className="mb-3">
                    <label className="block text-xs font-medium text-gray-600 mb-1">{field.label}</label>
                    <input type={field.type || 'text'} value={form.settings[field.key] || ''}
                      onChange={e => setForm({ ...form, settings: { ...form.settings, [field.key]: e.target.value } })}
                      className="input w-full font-mono text-sm" />
                  </div>
                ))}
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Currency</label>
                  <select value={form.currency} onChange={e => setForm({ ...form, currency: e.target.value })} className="input w-full">
                    <option value="USD">USD</option>
                    <option value="INR">INR</option>
                    <option value="EUR">EUR</option>
                    <option value="GBP">GBP</option>
                    <option value="CAD">CAD</option>
                    <option value="AUD">AUD</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Locale</label>
                  <select value={form.defaultLocale} onChange={e => setForm({ ...form, defaultLocale: e.target.value })} className="input w-full">
                    <option value="en_US">en_US</option>
                    <option value="en_IN">en_IN</option>
                    <option value="en_GB">en_GB</option>
                    <option value="fr_FR">fr_FR</option>
                    <option value="de_DE">de_DE</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Timezone</label>
                  <select value={form.timezone} onChange={e => setForm({ ...form, timezone: e.target.value })} className="input w-full">
                    <option value="UTC">UTC</option>
                    <option value="America/New_York">ET</option>
                    <option value="America/Chicago">CT</option>
                    <option value="America/Los_Angeles">PT</option>
                    <option value="Asia/Kolkata">IST</option>
                    <option value="Europe/London">GMT</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">External Store ID</label>
                  <input value={form.externalStoreId} onChange={e => setForm({ ...form, externalStoreId: e.target.value })} className="input w-full font-mono" placeholder="Shopify location ID" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">External Domain</label>
                  <input value={form.externalDomain} onChange={e => setForm({ ...form, externalDomain: e.target.value })} className="input w-full font-mono" placeholder="mystore.myshopify.com" />
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowCreate(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleCreate} disabled={saving} className="btn-primary text-sm">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                Create Store
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
