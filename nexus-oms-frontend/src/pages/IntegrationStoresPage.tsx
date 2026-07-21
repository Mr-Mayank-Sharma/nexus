import { useState, useEffect } from 'react'
import {
  Plus,   Trash2, RefreshCw, ExternalLink, Store, ShoppingBag, Globe,
  Loader2, X, Clock, CheckCircle, XCircle, Link,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import * as api from '../api/integrationStores'
import { IntegrationStore, StoreSyncStatus } from '../api/integrationStores'
import StatusBadge from '../components/common/StatusBadge'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'

const PLATFORMS = [
  { value: 'SHOPIFY', label: 'Shopify', icon: <ShoppingBag className="w-5 h-5" />, color: 'bg-emerald-500' },
  { value: 'BIGCOMMERCE', label: 'BigCommerce', icon: <Store className="w-5 h-5" />, color: 'bg-[var(--nexus-primary-50)]0' },
  { value: 'AMAZON', label: 'Amazon', icon: <Globe className="w-5 h-5" />, color: 'bg-orange-500' },
  { value: 'WOOCOMMERCE', label: 'WooCommerce', icon: <ShoppingBag className="w-5 h-5" />, color: 'bg-[var(--nexus-ai-50)]0' },
  { value: 'MANUAL', label: 'Manual', icon: <Store className="w-5 h-5" />, color: 'bg-[var(--surface-muted)]' },
]

const currencyOpts = [
  { value: 'USD', label: 'USD' },
  { value: 'INR', label: 'INR' },
  { value: 'EUR', label: 'EUR' },
  { value: 'GBP', label: 'GBP' },
  { value: 'CAD', label: 'CAD' },
  { value: 'AUD', label: 'AUD' },
]

const localeOpts = [
  { value: 'en_US', label: 'en_US' },
  { value: 'en_IN', label: 'en_IN' },
  { value: 'en_GB', label: 'en_GB' },
  { value: 'fr_FR', label: 'fr_FR' },
  { value: 'de_DE', label: 'de_DE' },
]

const timezoneOpts = [
  { value: 'UTC', label: 'UTC' },
  { value: 'America/New_York', label: 'ET' },
  { value: 'America/Chicago', label: 'CT' },
  { value: 'America/Los_Angeles', label: 'PT' },
  { value: 'Asia/Kolkata', label: 'IST' },
  { value: 'Europe/London', label: 'GMT' },
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
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><Store className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Sales Channels</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">OFBiz-style integration store management — connect Shopify, BigCommerce, and more</p>
        </div>
        <PermissionGate resource="integrations" action="create">
          <button onClick={openCreate} className="btn-primary text-sm">
            <Plus className="w-4 h-4" /> New Store
          </button>
        </PermissionGate>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      ) : stores.length === 0 ? (
        <div className="text-center py-16 card">
          <Store className="w-12 h-12 text-[var(--text-tertiary)] mx-auto mb-3" />
          <p className="text-[var(--text-secondary)] text-sm">No connected stores. Add your first sales channel to begin syncing.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-1 space-y-2">
            {stores.map(st => {
              const pf = platformInfo(st.platform)
              return (
                <button key={st.id} onClick={() => openStore(st)}
                  className={`w-full text-left card p-4 flex items-center gap-3 transition-all ${
                    selectedStore?.id === st.id ? 'ring-2 ring-[var(--nexus-primary-500)]' : ''
                  }`}>
                  <div className={`w-10 h-10 ${pf.color} rounded-lg flex items-center justify-center flex-shrink-0`}>
                    <div className="text-white">{pf.icon}</div>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-[var(--text-primary)] truncate">{st.storeName}</p>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-xs text-[var(--text-secondary)]">{st.storeCode}</span>
                      <span className="text-xs bg-[var(--surface-muted)] px-1.5 py-0.5 rounded text-[var(--text-secondary)]">{st.platform}</span>
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
                        <h3 className="text-sm font-semibold text-[var(--text-primary)]">{selectedStore.storeName}</h3>
                        <p className="text-xs text-[var(--text-secondary)]">{selectedStore.platform} · {selectedStore.storeCode}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      {selectedStore.externalDomain && (
                        <a href={`https://${selectedStore.externalDomain}`} target="_blank" rel="noopener noreferrer"
                          className="btn-ghost text-xs"><ExternalLink className="w-3 h-3" /></a>
                      )}
                      <PermissionGate resource="integrations" action="delete">
                        <button onClick={() => handleDelete(selectedStore.id)} className="btn-ghost text-xs text-[var(--nexus-error-500)]">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </PermissionGate>
                    </div>
                  </div>

                  {storeStatus && (
                    <div className="px-6 pb-4">
                      <h4 className="text-xs font-semibold text-[var(--text-secondary)] uppercase mb-3">Sync Actions</h4>
                      <div className="grid grid-cols-2 gap-3">
                        {storeStatus.syncTypes.map(st => {
                          const def = SYNC_DEFS[st.syncType] || { label: st.syncType, description: '' }
                          return (
                            <div key={st.syncType} className="border border-[var(--border-default)] rounded-lg p-3">
                              <div className="flex items-center justify-between mb-2">
                                <span className="text-xs font-medium text-[var(--text-secondary)]">{def.label}</span>
                                {st.lastSyncStatus === 'COMPLETED' ? <CheckCircle className="w-3.5 h-3.5 text-[var(--nexus-success-500)]" /> :
                                 st.lastSyncStatus === 'FAILED' ? <XCircle className="w-3.5 h-3.5 text-[var(--nexus-error-500)]" /> :
                                 <Clock className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />}
                              </div>
                              <p className="text-xs text-[var(--text-tertiary)] mb-2">{def.description}</p>
                              {st.lastSyncAt && (
                                <p className="text-xs text-[var(--text-tertiary)] mb-2">Last: {new Date(st.lastSyncAt).toLocaleString()}</p>
                              )}
                               <PermissionGate resource="integrations" action="edit">
                                 <button onClick={() => handleSync(st.syncType)} disabled={syncing === st.syncType}
                                   className="btn-primary text-xs w-full">
                                   {syncing === st.syncType ? <Loader2 className="w-3 h-3 animate-spin" /> : <RefreshCw className="w-3 h-3" />}
                                   {syncing === st.syncType ? 'Running...' : 'Run Sync'}
                                 </button>
                               </PermissionGate>
                            </div>
                          )
                        })}
                      </div>

                      <div className="flex items-center gap-2 mt-4">
                        <PermissionGate resource="integrations" action="create">
                          <button onClick={handleRegisterWebhooks} className="btn-secondary text-xs">
                            <Link className="w-3.5 h-3.5" /> Register Webhooks
                          </button>
                        </PermissionGate>
                      </div>
                    </div>
                  )}
                </div>

                <div className="card">
                  <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]">Connection Settings</h3></div>
                  <div className="card-body space-y-3">
                    {settingFields(selectedStore.platform).map(field => (
                      <div key={field.key}>
                        <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">{field.label}</label>
                        <Autocomplete className="input w-full text-sm font-mono"
                          value={storeStatus?.settings?.[field.key] || ''} onChange={() => {}} disabled minChars={0} />
                      </div>
                    ))}
                    <p className="text-xs text-[var(--text-tertiary)] mt-2">Manage credentials via the store's configuration in control panel.</p>
                  </div>
                </div>
              </div>
            ) : (
              <div className="card p-12 text-center text-[var(--text-tertiary)]">
                <Store className="w-16 h-16 mx-auto mb-4 text-[var(--text-tertiary)]" />
                <p className="text-sm">Select a store to view sync status and actions</p>
              </div>
            )}
          </div>
        </div>
      )}

      {showCreate && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-lg">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">New Sales Channel</h2>
              <button onClick={() => setShowCreate(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Platform</label>
                <Autocomplete value={form.platform} onChange={v => setForm({ ...form, platform: v, settings: {} })} suggestions={PLATFORMS} getOptionLabel={o => o.label} getOptionValue={o => o.value} className="input w-full" minChars={0} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Store Code</label>
                  <Autocomplete value={form.storeCode} onChange={v => setForm({ ...form, storeCode: v })} className="input w-full font-mono" placeholder="shopify-us" minChars={0} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Store Name</label>
                  <Autocomplete value={form.storeName} onChange={v => setForm({ ...form, storeName: v })} className="input w-full" placeholder="US Shopify Store" minChars={0} />
                </div>
              </div>

              <div className="border-t border-[var(--border-subtle)] pt-4">
                <h4 className="text-sm font-medium text-[var(--text-secondary)] mb-3">API Credentials</h4>
                {settingFields(form.platform).map(field => (
                  <div key={field.key} className="mb-3">
                    <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">{field.label}</label>
                    <Autocomplete value={form.settings[field.key] || ''}
                      onChange={v => setForm({ ...form, settings: { ...form.settings, [field.key]: v } })}
                      className="input w-full font-mono text-sm" minChars={0} />
                  </div>
                ))}
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Currency</label>
                  <Autocomplete value={form.currency} onChange={v => setForm({ ...form, currency: v })} suggestions={currencyOpts} getOptionLabel={o => o.label} getOptionValue={o => o.value} className="input w-full" minChars={0} />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Locale</label>
                  <Autocomplete value={form.defaultLocale} onChange={v => setForm({ ...form, defaultLocale: v })} suggestions={localeOpts} getOptionLabel={o => o.label} getOptionValue={o => o.value} className="input w-full" minChars={0} />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Timezone</label>
                  <Autocomplete value={form.timezone} onChange={v => setForm({ ...form, timezone: v })} suggestions={timezoneOpts} getOptionLabel={o => o.label} getOptionValue={o => o.value} className="input w-full" minChars={0} />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">External Store ID</label>
                  <Autocomplete value={form.externalStoreId} onChange={v => setForm({ ...form, externalStoreId: v })} className="input w-full font-mono" placeholder="Shopify location ID" minChars={0} />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">External Domain</label>
                  <Autocomplete value={form.externalDomain} onChange={v => setForm({ ...form, externalDomain: v })} className="input w-full font-mono" placeholder="mystore.myshopify.com" minChars={0} />
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowCreate(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="integrations" action="create">
                <button onClick={handleCreate} disabled={saving} className="btn-primary text-sm">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Store className="w-4 h-4" />}
                  Create Store
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
