import { useState, useEffect } from 'react'
import {
  Settings, ShoppingCart, Package, Truck, RotateCcw, RefreshCw, Database, Link, ExternalLink,
  Clock, CheckCircle, XCircle, Loader2, AlertTriangle, Activity,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import * as bcApi from '../api/bigcommerce'
import { BigCommerceConfig, SyncResult } from '../api/bigcommerce'
import { SyncLog } from '../types'
import StatusBadge from '../components/common/StatusBadge'

export default function BigCommercePage() {
  const [activeTab, setActiveTab] = useState<'config' | 'sync' | 'logs'>('config')
  const [config, setConfig] = useState<BigCommerceConfig | null>(null)
  const [configLoading, setConfigLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [syncing, setSyncing] = useState<string | null>(null)
  const [syncLogs, setSyncLogs] = useState<SyncLog[]>([])
  const [logsLoading, setLogsLoading] = useState(false)
  const { addToast } = useToast()

  const [form, setForm] = useState({
    storeHash: '', accessToken: '', clientId: '', apiPath: 'https://api.bigcommerce.com',
    autoSyncOrders: false, autoSyncInventory: false, syncIntervalMinutes: 15,
  })

  useEffect(() => { fetchConfig() }, [])

  async function fetchConfig() {
    try {
      setConfigLoading(true)
      const res = await bcApi.getConfig()
      if (res.data) {
        setConfig(res.data)
        setForm({
          storeHash: res.data.storeHash || '',
          accessToken: res.data.accessToken || '',
          clientId: res.data.clientId || '',
          apiPath: res.data.apiPath || 'https://api.bigcommerce.com',
          autoSyncOrders: res.data.autoSyncOrders || false,
          autoSyncInventory: res.data.autoSyncInventory || false,
          syncIntervalMinutes: res.data.syncIntervalMinutes || 15,
        })
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to load BigCommerce config' })
    } finally { setConfigLoading(false) }
  }

  async function handleSave() {
    if (!form.storeHash.trim() || !form.accessToken.trim()) {
      addToast({ type: 'warning', title: 'Store Hash and Access Token are required' }); return
    }
    setSaving(true)
    try {
      const res = await bcApi.updateConfig(form)
      setConfig(res.data)
      addToast({ type: 'success', title: 'Configuration saved' })
    } catch {
      addToast({ type: 'error', title: 'Failed to save config' })
    } finally { setSaving(false) }
  }

  async function handleSync(type: string) {
    setSyncing(type)
    try {
      let res: { data: SyncResult }
      switch (type) {
        case 'orders': res = await bcApi.syncOrders(); break
        case 'products': res = await bcApi.syncProducts(); break
        case 'inventory': res = await bcApi.pushInventory(); break
        case 'shipments': res = await bcApi.pushShipments(); break
        case 'refunds': res = await bcApi.pushRefunds(); break
        default: return
      }
      addToast({ type: res.data.status === 'COMPLETED' ? 'success' : 'error',
        title: `${type} sync: ${res.data.itemsSucceeded} OK, ${res.data.itemsFailed} failed` })
      if (activeTab === 'logs') fetchLogs()
    } catch {
      addToast({ type: 'error', title: `Failed to sync ${type}` })
    } finally { setSyncing(null) }
  }

  async function handleRegisterWebhooks() {
    try {
      await bcApi.registerWebhooks(window.location.origin)
      addToast({ type: 'success', title: 'Webhooks registered' })
    } catch {
      addToast({ type: 'error', title: 'Failed to register webhooks' })
    }
  }

  async function fetchLogs() {
    try {
      setLogsLoading(true)
      const res = await bcApi.getSyncLogs()
      setSyncLogs(res.data || [])
    } catch {
      addToast({ type: 'error', title: 'Failed to load sync logs' })
    } finally { setLogsLoading(false) }
  }

  useEffect(() => { if (activeTab === 'logs') fetchLogs() }, [activeTab])

  const tabs = [
    { id: 'config', label: 'Configuration', icon: <Settings className="w-4 h-4" /> },
    { id: 'sync', label: 'Sync Actions', icon: <RefreshCw className="w-4 h-4" /> },
    { id: 'logs', label: 'Sync Logs', icon: <Activity className="w-4 h-4" /> },
  ]

  const syncActions = [
    { id: 'orders', label: 'Import Orders', description: 'Pull new/updated orders from BigCommerce', icon: <ShoppingCart className="w-5 h-5" />, color: 'bg-blue-500' },
    { id: 'products', label: 'Sync Products', description: 'Sync product catalog and create mappings', icon: <Package className="w-5 h-5" />, color: 'bg-purple-500' },
    { id: 'inventory', label: 'Push Inventory', description: 'Push NexusShip inventory levels to BigCommerce', icon: <Database className="w-5 h-5" />, color: 'bg-green-500' },
    { id: 'shipments', label: 'Push Shipments', description: 'Push tracking info to BigCommerce orders', icon: <Truck className="w-5 h-5" />, color: 'bg-yellow-500' },
    { id: 'refunds', label: 'Push Refunds', description: 'Push NexusShip return refunds to BigCommerce', icon: <RotateCcw className="w-5 h-5" />, color: 'bg-red-500' },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">BigCommerce Integration</h1>
          <p className="text-sm text-gray-500 mt-1">Connect your BigCommerce store to synchronize orders, inventory, shipments, and refunds</p>
        </div>
        {config?.isActive && (
          <a href={`https://store-${config.storeHash}.mybigcommerce.com`} target="_blank" rel="noopener noreferrer"
            className="btn-secondary text-sm">
            <ExternalLink className="w-4 h-4" /> Open Store
          </a>
        )}
      </div>

      <div className="flex items-center gap-1 border-b border-gray-200">
        {tabs.map(tab => (
          <button key={tab.id} onClick={() => setActiveTab(tab.id as any)}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              activeTab === tab.id ? 'border-primary-600 text-primary-600' : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}>
            {tab.icon}{tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'config' && (
        <div className="card max-w-2xl">
          <div className="card-header"><h3 className="text-sm font-semibold text-gray-900">API Credentials</h3></div>
          <div className="card-body space-y-4">
            <p className="text-xs text-gray-500">
              Create API credentials in your BigCommerce control panel under <strong>Advanced Settings &gt; API Accounts &gt; Create API Account</strong>.
              Ensure the account has Orders, Products, Inventory, and Shipments permissions.
            </p>
            <div className="grid grid-cols-2 gap-4">
              <div className="col-span-2">
                <label className="block text-sm font-medium text-gray-700 mb-1">Store Hash</label>
                <input value={form.storeHash} onChange={e => setForm({ ...form, storeHash: e.target.value })} className="input w-full font-mono text-sm" placeholder="abc123" />
                <p className="text-xs text-gray-400 mt-1">Found in your BigCommerce store URL: https://store-<strong>abc123</strong>.mybigcommerce.com</p>
              </div>
              <div className="col-span-2">
                <label className="block text-sm font-medium text-gray-700 mb-1">Access Token</label>
                <input value={form.accessToken} onChange={e => setForm({ ...form, accessToken: e.target.value })} className="input w-full font-mono text-sm" type="password" placeholder="xxxxxxxxxxxx" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Client ID</label>
                <input value={form.clientId} onChange={e => setForm({ ...form, clientId: e.target.value })} className="input w-full font-mono text-sm" placeholder="Optional" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">API Path</label>
                <input value={form.apiPath} onChange={e => setForm({ ...form, apiPath: e.target.value })} className="input w-full font-mono text-sm" />
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
                <input type="checkbox" checked={form.autoSyncInventory} onChange={e => setForm({ ...form, autoSyncInventory: e.target.checked })} className="rounded border-gray-300" />
                Auto-sync inventory
              </label>
            </div>
            <div className="flex items-center gap-3">
              <label className="text-sm text-gray-700">Sync interval (minutes):</label>
              <input type="number" value={form.syncIntervalMinutes} onChange={e => setForm({ ...form, syncIntervalMinutes: parseInt(e.target.value) || 15 })}
                className="input w-20 text-sm" min={5} max={1440} />
            </div>

            {config?.lastOrderSyncAt && (
              <div className="bg-gray-50 rounded-lg p-3 text-xs text-gray-500 space-y-1">
                <p>Last order sync: {new Date(config.lastOrderSyncAt).toLocaleString()}</p>
                <p>Last product sync: {config.lastProductSyncAt ? new Date(config.lastProductSyncAt).toLocaleString() : 'Never'}</p>
                <p>Last inventory sync: {config.lastInventorySyncAt ? new Date(config.lastInventorySyncAt).toLocaleString() : 'Never'}</p>
              </div>
            )}
          </div>
          <div className="card-footer flex justify-between">
            <button onClick={handleRegisterWebhooks} className="btn-secondary text-sm">
              <Link className="w-4 h-4" /> Register Webhooks
            </button>
            <button onClick={handleSave} disabled={saving} className="btn-primary text-sm">
              {saving && <Loader2 className="w-4 h-4 animate-spin" />}
              Save Configuration
            </button>
          </div>
        </div>
      )}

      {activeTab === 'sync' && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {syncActions.map(action => (
            <div key={action.id} className="card p-5">
              <div className={`w-10 h-10 ${action.color} rounded-lg flex items-center justify-center mb-3`}>
                <div className="text-white">{action.icon}</div>
              </div>
              <h3 className="text-sm font-semibold text-gray-900">{action.label}</h3>
              <p className="text-xs text-gray-500 mt-1 mb-4">{action.description}</p>
              <button onClick={() => handleSync(action.id)} disabled={syncing === action.id}
                className="btn-primary text-xs w-full">
                {syncing === action.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
                {syncing === action.id ? 'Running...' : 'Run Now'}
              </button>
            </div>
          ))}
        </div>
      )}

      {activeTab === 'logs' && (
        <div className="card">
          <div className="card-header flex justify-between items-center">
            <h3 className="text-sm font-semibold text-gray-900">Sync History</h3>
            <button onClick={fetchLogs} className="btn-ghost p-1"><RefreshCw className="w-4 h-4" /></button>
          </div>
          {logsLoading ? (
            <div className="flex items-center justify-center h-32">
              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary-600" />
            </div>
          ) : syncLogs.length === 0 ? (
            <div className="p-6 text-center text-sm text-gray-500">No sync logs yet. Run a sync to see results.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/50">
                    <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Type</th>
                    <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Status</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Processed</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Succeeded</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Failed</th>
                    <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Started</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {syncLogs.map(log => (
                    <tr key={log.id} className="hover:bg-gray-50">
                      <td className="px-6 py-3 text-sm font-medium text-gray-900">{log.syncType.replace(/_/g, ' ')}</td>
                      <td className="px-6 py-3"><StatusBadge status={log.status} size="sm" /></td>
                      <td className="px-6 py-3 text-sm text-gray-700 text-right">{log.itemsProcessed}</td>
                      <td className="px-6 py-3 text-sm text-green-600 text-right">{log.itemsSucceeded}</td>
                      <td className="px-6 py-3 text-sm text-red-600 text-right">{log.itemsFailed}</td>
                      <td className="px-6 py-3 text-sm text-gray-400 text-right">{new Date(log.startedAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
