import { useState, useEffect } from 'react'
import {
  ShoppingBag, RefreshCw, Link, ExternalLink, Loader2, Clock, CheckCircle, XCircle,
  Search, Package, History, Globe, ArrowRight, AlertCircle,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import * as api from '../api/integrationStores'
import { IntegrationStore, StoreSyncStatus } from '../api/integrationStores'
import StatusBadge from '../components/common/StatusBadge'
import PermissionGate from '../components/rbac/PermissionGate'
import { getOrderById } from '../api/orders'
import { Order } from '../types'

const SYNC_DEFS: Record<string, { label: string; description: string }> = {
  ORDER_IMPORT: { label: 'Import Orders', description: 'Pull orders from Shopify' },
  PRODUCT_SYNC: { label: 'Sync Products', description: 'Sync product catalog' },
  INVENTORY_PUSH: { label: 'Push Inventory', description: 'Push inventory levels' },
  FULFILLMENT_PUSH: { label: 'Push Fulfillments', description: 'Push tracking to Shopify' },
  REFUND_PUSH: { label: 'Push Refunds', description: 'Push refunds to Shopify' },
}

const SYNC_TYPE_LABELS: Record<string, string> = {
  ORDER_IMPORT: 'Import Orders', PRODUCT_SYNC: 'Sync Products', INVENTORY_PUSH: 'Push Inventory',
  FULFILLMENT_PUSH: 'Push Fulfillments', REFUND_PUSH: 'Push Refunds',
}

export default function ShopifyIntegrationPage() {
  const [stores, setStores] = useState<IntegrationStore[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedStore, setSelectedStore] = useState<IntegrationStore | null>(null)
  const [storeStatus, setStoreStatus] = useState<StoreSyncStatus | null>(null)
  const [syncLogs, setSyncLogs] = useState<any[]>([])
  const [syncing, setSyncing] = useState<string | null>(null)
  const [registering, setRegistering] = useState(false)
  const [orderSearch, setOrderSearch] = useState('')
  const [orderResult, setOrderResult] = useState<Order | null>(null)
  const [orderError, setOrderError] = useState<string | null>(null)
  const [orderSearching, setOrderSearching] = useState(false)
  const { addToast } = useToast()

  async function fetchStores() {
    try {
      setLoading(true)
      const res = await api.getStores('SHOPIFY')
      const shopifyStores = (res.data || []).filter(s => s.platform === 'SHOPIFY')
      setStores(shopifyStores)
      if (shopifyStores.length > 0 && !selectedStore) {
        openStore(shopifyStores[0])
      }
    } catch { addToast({ type: 'error', title: 'Failed to load stores' })
    } finally { setLoading(false) }
  }

  useEffect(() => { fetchStores() }, [])

  async function openStore(st: IntegrationStore) {
    setSelectedStore(st)
    setStoreStatus(null)
    setSyncLogs([])
    try {
      const [statusRes, logRes] = await Promise.all([
        api.getStoreSyncStatus(st.id),
        api.getStoreSyncLogs(st.id, 20),
      ])
      setStoreStatus(statusRes.data)
      setSyncLogs(logRes.data || [])
    } catch { addToast({ type: 'error', title: 'Failed to load store status' }) }
  }

  async function handleSync(type: string) {
    if (!selectedStore) return
    setSyncing(type)
    try {
      let res: any
      switch (type) {
        case 'ORDER_IMPORT': res = await api.shopifySyncOrders(selectedStore.id); break
        case 'PRODUCT_SYNC': res = await api.shopifySyncProducts(selectedStore.id); break
        case 'INVENTORY_PUSH': res = await api.shopifyPushInventory(selectedStore.id); break
        case 'FULFILLMENT_PUSH': res = await api.shopifyPushFulfillments(selectedStore.id); break
        case 'REFUND_PUSH': res = await api.shopifyPushRefunds(selectedStore.id); break
      }
      if (res?.data) {
        addToast({ type: res.data.status === 'COMPLETED' ? 'success' : 'error',
          title: `${SYNC_TYPE_LABELS[type] || type}: ${res.data.itemsSucceeded} OK, ${res.data.itemsFailed} failed` })
      }
      await openStore(selectedStore)
    } catch {
      addToast({ type: 'error', title: `Sync ${type} failed` })
    } finally { setSyncing(null) }
  }

  async function handleRegisterWebhooks() {
    if (!selectedStore) return
    setRegistering(true)
    try {
      const baseUrl = window.location.origin
      await api.shopifyRegisterWebhooks(selectedStore.id, baseUrl)
      addToast({ type: 'success', title: `Webhooks registered to ${baseUrl}` })
    } catch {
      addToast({ type: 'error', title: 'Failed to register webhooks' })
    } finally { setRegistering(false) }
  }

  async function handleOrderSearch(e: React.FormEvent) {
    e.preventDefault()
    const id = orderSearch.trim()
    if (!id) return
    setOrderSearching(true)
    setOrderError(null)
    setOrderResult(null)
    try {
      const res = await getOrderById(id)
      if (res.success && res.data) {
        setOrderResult(res.data)
      } else {
        setOrderError(res.error || `Order "${id}" not found`)
      }
    } catch {
      setOrderError('Failed to fetch order')
    } finally { setOrderSearching(false) }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <ShoppingBag className="w-7 h-7 text-[var(--nexus-success-500)]" /> Shopify
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Connect your Shopify store — orders, products, inventory, fulfillments, and refunds
          </p>
        </div>
        {selectedStore?.externalDomain && (
          <a href={`https://${selectedStore.externalDomain}`} target="_blank" rel="noopener noreferrer"
            className="enterprise-btn enterprise-btn-ghost text-sm">
            <ExternalLink className="w-4 h-4" /> Open Store
          </a>
        )}
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <Loader2 className="w-8 h-8 animate-spin text-[var(--text-brand)]" />
        </div>
      ) : stores.length === 0 ? (
        <div className="card p-12 text-center text-[var(--text-tertiary)]">
          <ShoppingBag className="w-16 h-16 mx-auto mb-4" />
          <p className="text-sm text-[var(--text-secondary)]">No Shopify store connected.</p>
          <p className="text-xs mt-1">Go to Stores to create a Shopify sales channel.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Store list */}
          <div className="lg:col-span-1 space-y-2">
            {stores.map(st => (
              <button type="button" key={st.id} onClick={() => openStore(st)}
                className={`w-full text-left card p-4 flex items-center gap-3 transition-all ${
                  selectedStore?.id === st.id ? 'ring-2 ring-[var(--nexus-primary-500)]' : ''
                }`}>
                <div className="w-10 h-10 bg-[var(--nexus-success-500)] rounded-lg flex items-center justify-center flex-shrink-0">
                  <ShoppingBag className="w-5 h-5 text-white" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-[var(--text-primary)] truncate">{st.storeName}</p>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-xs text-[var(--text-secondary)]">{st.storeCode}</span>
                    <StatusBadge status={st.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                  </div>
                </div>
              </button>
            ))}
          </div>

          {/* Details */}
          <div className="lg:col-span-2 space-y-4">
            {/* Sync status + actions */}
            {selectedStore && storeStatus && (
              <div className="card">
                <div className="card-header flex justify-between items-center">
                  <div>
                    <h3 className="text-sm font-semibold text-[var(--text-primary)]">{storeStatus.storeName}</h3>
                    <p className="text-xs text-[var(--text-secondary)]">{storeStatus.platform} · {storeStatus.storeCode}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusBadge status={storeStatus.connected ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                  </div>
                </div>
                <div className="card-body">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
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
                            <button type="button" onClick={() => handleSync(st.syncType)} disabled={syncing === st.syncType}
                              className="enterprise-btn enterprise-btn-primary text-xs w-full">
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
                      <button type="button" onClick={handleRegisterWebhooks} disabled={registering}
                        className="enterprise-btn enterprise-btn-secondary text-xs">
                        {registering ? <Loader2 className="w-3 h-3 animate-spin" /> : <Link className="w-3 h-3" />}
                        Register Webhooks
                      </button>
                    </PermissionGate>
                  </div>
                </div>
              </div>
            )}

            {/* Find order */}
            <div className="card">
              <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Search className="w-4 h-4" /> Find Imported Order</h3></div>
              <div className="card-body">
                <form onSubmit={handleOrderSearch} className="flex gap-2">
                  <input type="text" value={orderSearch} onChange={e => setOrderSearch(e.target.value)}
                    placeholder="Order number (e.g. 1008) or order ID"
                    className="input flex-1 text-sm" />
                  <button type="submit" disabled={!orderSearch.trim() || orderSearching}
                    className="enterprise-btn enterprise-btn-primary text-sm">
                    {orderSearching ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
                    Search
                  </button>
                </form>

                {orderError && (
                  <div className="flex items-center gap-2 mt-3 p-3 rounded-lg bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 border border-[var(--nexus-error-200)] dark:border-[var(--nexus-error-800)]">
                    <AlertCircle className="w-4 h-4 text-[var(--nexus-error-500)] shrink-0" />
                    <p className="text-xs text-[var(--nexus-error-700)] dark:text-[var(--nexus-error-400)]">{orderError}</p>
                  </div>
                )}

                {orderResult && (
                  <div className="mt-3 border border-[var(--border-default)] rounded-lg overflow-hidden">
                    <div className="p-4 flex items-center justify-between border-b border-[var(--border-subtle)]">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-lg bg-[var(--nexus-success-100)] dark:bg-[var(--nexus-success-900)]/30 flex items-center justify-center">
                          <Package className="w-4 h-4 text-[var(--nexus-success-600)]" />
                        </div>
                        <div>
                          <p className="text-sm font-bold text-[var(--text-primary)]">
                            #{orderResult.channelOrderId || orderResult.id}
                          </p>
                          <p className="text-xs text-[var(--text-secondary)]">
                            {orderResult.customerEmail || 'No email'} · {orderResult.createdAt ? new Date(orderResult.createdAt).toLocaleDateString() : ''}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <StatusBadge status={orderResult.status} size="sm" />
                        <a href={`/orders/${orderResult.id}`} className="enterprise-btn enterprise-btn-ghost text-xs">
                          Details <ArrowRight className="w-3 h-3" />
                        </a>
                      </div>
                    </div>
                    <div className="p-4 grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                      <div>
                        <p className="text-xs text-[var(--text-tertiary)] uppercase tracking-wider mb-0.5">Total</p>
                        <p className="font-medium text-[var(--text-primary)]">
                          {orderResult.total != null ? `${orderResult.currency || 'USD'} ${Number(orderResult.total).toFixed(2)}` : 'N/A'}
                        </p>
                      </div>
                      <div>
                        <p className="text-xs text-[var(--text-tertiary)] uppercase tracking-wider mb-0.5">Items</p>
                        <p className="font-medium text-[var(--text-primary)]">{orderResult.items?.length || 0} item(s)</p>
                      </div>
                      <div>
                        <p className="text-xs text-[var(--text-tertiary)] uppercase tracking-wider mb-0.5">Channel</p>
                        <p className="font-medium text-[var(--text-primary)]">{orderResult.channel || 'N/A'}</p>
                      </div>
                      <div>
                        <p className="text-xs text-[var(--text-tertiary)] uppercase tracking-wider mb-0.5">Tracking</p>
                        <p className="font-medium text-[var(--text-primary)]">{orderResult.trackingNumber || 'Not shipped'}</p>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Sync history */}
            <div className="card">
              <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><History className="w-4 h-4" /> Sync History</h3></div>
              <div className="card-body">
                {syncLogs.length === 0 ? (
                  <p className="text-sm text-[var(--text-tertiary)] text-center py-4">No sync activity yet. Run a sync to see results.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-[var(--border-subtle)]">
                          <th className="text-left py-2 text-[var(--text-tertiary)] font-medium">Sync Type</th>
                          <th className="text-left py-2 text-[var(--text-tertiary)] font-medium">Status</th>
                          <th className="text-right py-2 text-[var(--text-tertiary)] font-medium">Processed</th>
                          <th className="text-right py-2 text-[var(--text-tertiary)] font-medium">OK</th>
                          <th className="text-right py-2 text-[var(--text-tertiary)] font-medium">Failed</th>
                          <th className="text-left py-2 text-[var(--text-tertiary)] font-medium">Error</th>
                          <th className="text-left py-2 text-[var(--text-tertiary)] font-medium">Time</th>
                        </tr>
                      </thead>
                      <tbody>
                        {syncLogs.map((log: any) => (
                          <tr key={log.id} className="border-b border-[var(--border-subtle)] last:border-0">
                            <td className="py-2.5 text-[var(--text-primary)] font-medium">
                              {SYNC_TYPE_LABELS[log.syncType] || log.syncType}
                            </td>
                            <td className="py-2.5"><StatusBadge status={log.status} size="sm" /></td>
                            <td className="py-2.5 text-right text-[var(--text-secondary)]">{log.itemsProcessed ?? 0}</td>
                            <td className="py-2.5 text-right text-[var(--nexus-success-600)]">{log.itemsSucceeded ?? 0}</td>
                            <td className="py-2.5 text-right text-[var(--nexus-error-500)]">{log.itemsFailed ?? 0}</td>
                            <td className="py-2.5 text-[var(--text-tertiary)] max-w-[200px] truncate" title={log.errorMessage || ''}>
                              {log.errorMessage || '—'}
                            </td>
                            <td className="py-2.5 text-[var(--text-secondary)] whitespace-nowrap">
                              {log.createdAt ? new Date(log.createdAt).toLocaleString() : '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>

            {/* Connection settings */}
            {selectedStore && storeStatus && (
              <div className="card">
                <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2"><Globe className="w-4 h-4" /> Connection Settings</h3></div>
                <div className="card-body space-y-3">
                  <div>
                    <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Shop Domain</label>
                    <div className="flex items-center gap-2">
                      <input className="input w-full text-sm font-mono" value={selectedStore.externalDomain || ''} readOnly />
                      {selectedStore.externalDomain && (
                        <a href={`https://${selectedStore.externalDomain}`} target="_blank" rel="noopener noreferrer"
                          className="enterprise-btn enterprise-btn-ghost text-xs"><ExternalLink className="w-3 h-3" /></a>
                      )}
                    </div>
                  </div>
                  <p className="text-xs text-[var(--text-tertiary)]">Credentials are stored encrypted server-side and refreshed automatically on restart.</p>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
