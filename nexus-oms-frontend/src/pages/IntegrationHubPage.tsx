import { useState, useEffect } from 'react'
import {
  Plug, Plus, Trash2, RefreshCw, TestTube, Globe, Database, Truck, CreditCard,
  MessageSquare, UserCheck, BarChart3, Brain, Package, Settings, Link, Loader2,
  CheckCircle, XCircle, AlertTriangle, Activity, Zap, Wifi, Clock, X, ChevronDown,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import { integrationHub, ConnectorMetadata, ConnectorInstance, BatchJob } from '../api/integrationHub'
import StatusBadge from '../components/common/StatusBadge'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'

const CATEGORY_ICONS: Record<string, React.ReactNode> = {
  'E-Commerce': <Package className="w-4 h-4" />,
  'Marketplace': <Globe className="w-4 h-4" />,
  'ERP': <Database className="w-4 h-4" />,
  'CRM': <UserCheck className="w-4 h-4" />,
  'WMS': <Database className="w-4 h-4" />,
  'TMS': <Truck className="w-4 h-4" />,
  'Shipping': <Truck className="w-4 h-4" />,
  'Payments': <CreditCard className="w-4 h-4" />,
  'Accounting': <Database className="w-4 h-4" />,
  'POS': <CreditCard className="w-4 h-4" />,
  'Communication': <MessageSquare className="w-4 h-4" />,
  'Identity & SSO': <UserCheck className="w-4 h-4" />,
  'Analytics': <BarChart3 className="w-4 h-4" />,
  'AI Platform': <Brain className="w-4 h-4" />,
}

const CATEGORY_COLORS: Record<string, string> = {
  'E-Commerce': 'bg-[var(--nexus-primary-50)]0', 'Marketplace': 'bg-[var(--nexus-ai-50)]0', 'ERP': 'bg-orange-500',
  'CRM': 'bg-[var(--nexus-success-50)]0', 'WMS': 'bg-[var(--nexus-success-50)]0', 'TMS': 'bg-[var(--nexus-info-50)]0',
  'Shipping': 'bg-[var(--nexus-warning-50)]0', 'Payments': 'bg-emerald-500', 'Accounting': 'bg-violet-500',
  'POS': 'bg-pink-500', 'Communication': 'bg-[var(--nexus-primary-50)]0', 'Identity & SSO': 'bg-rose-500',
  'Analytics': 'bg-sky-500', 'AI Platform': 'bg-fuchsia-500',
}

export default function IntegrationHubPage() {
  const [platforms, setPlatforms] = useState<ConnectorMetadata[]>([])
  const [connectors, setConnectors] = useState<ConnectorInstance[]>([])
  const [jobs, setJobs] = useState<BatchJob[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const [selectedPlatform, setSelectedPlatform] = useState<ConnectorMetadata | null>(null)
  const [selectedConnector, setSelectedConnector] = useState<ConnectorInstance | null>(null)
  const [syncing, setSyncing] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const { addToast } = useToast()

  const [form, setForm] = useState<Record<string, string>>({})

  useEffect(() => { loadData() }, [])

  async function loadData() {
    try {
      setLoading(true)
      const [p, c, j] = await Promise.all([
        integrationHub.getPlatforms(), integrationHub.getConnectors(), integrationHub.getJobs()
      ])
      setPlatforms(p)
      setConnectors(c)
      setJobs(j)
    } catch { addToast({ type: 'error', title: 'Failed to load Integration Hub' })
    } finally { setLoading(false) }
  }

  async function openCreate(platform: ConnectorMetadata) {
    setSelectedPlatform(platform)
    const initial: Record<string, string> = {}
    platform.requiredSettings.forEach(s => initial[s] = '')
    platform.supportedAuthTypes.forEach(a => initial[a.toLowerCase() + '_key'] = '')
    setForm(initial)
    setShowCreate(true)
  }

  async function handleCreate() {
    if (!selectedPlatform) return
    setSaving(true)
    try {
      const credentials: Record<string, string> = {}
      const settings: Record<string, string> = {}
      Object.entries(form).forEach(([k, v]) => {
        if (k.includes('secret') || k.includes('token') || k.includes('key') || k.includes('password')) {
          credentials[k] = v
        } else {
          settings[k] = v
        }
      })

      await integrationHub.createConnector({
        platformType: selectedPlatform.platformType,
        storeCode: form.storeCode || selectedPlatform.platformType.toLowerCase(),
        storeName: form.storeName || selectedPlatform.name,
        settings,
        credentials,
      })
      addToast({ type: 'success', title: `${selectedPlatform.name} connected` })
      setShowCreate(false)
      await loadData()
    } catch { addToast({ type: 'error', title: 'Failed to create connector' })
    } finally { setSaving(false) }
  }

  async function handleDelete(id: string) {
    try {
      await integrationHub.deleteConnector(id)
      addToast({ type: 'success', title: 'Connector removed' })
      if (selectedConnector?.id === id) setSelectedConnector(null)
      await loadData()
    } catch { addToast({ type: 'error', title: 'Failed to remove connector' }) }
  }

  async function handleTest(id: string) {
    try {
      const res = await integrationHub.testConnection(id)
      addToast({ type: res.success ? 'success' : 'error', title: res.message })
    } catch { addToast({ type: 'error', title: 'Test failed' }) }
  }

  async function handleSync(id: string, syncType: string) {
    setSyncing(`${id}:${syncType}`)
    try {
      const job = await integrationHub.runSync(id, syncType)
      addToast({ type: 'success', title: `Sync ${syncType} started (job: ${job.jobId})` })
      await loadData()
    } catch { addToast({ type: 'error', title: `Sync ${syncType} failed` })
    } finally { setSyncing(null) }
  }

  async function handleRegisterWebhooks(id: string) {
    try {
      const res = await integrationHub.registerWebhooks(id)
      addToast({ type: 'success', title: res.status || 'Webhooks registered' })
    } catch { addToast({ type: 'error', title: 'Webhook registration failed' }) }
  }

  const categories = [...new Set(platforms.map(p => p.category))]

  const platformTypeColor = (type: string) => {
    const meta = platforms.find(p => p.platformType === type)
    return CATEGORY_COLORS[meta?.category || ''] || 'bg-[var(--surface-muted)]'
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><Plug className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Integration Hub</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Enterprise connector framework — 40+ platforms, REST/GraphQL/SOAP/EDI</p>
        </div>
        <div className="flex items-center gap-2 text-xs text-[var(--text-secondary)]">
          <Activity className="w-3.5 h-3.5" />
          <span>{connectors.length} active connectors</span>
          <Zap className="w-3.5 h-3.5 ml-2" />
          <span>{platforms.length} platforms</span>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <Loader2 className="w-8 h-8 animate-spin text-[var(--text-brand)]" />
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          <div className="lg:col-span-1 space-y-4">
            {categories.map(cat => (
              <div key={cat}>
                <h3 className="text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider mb-2 px-2">{cat}</h3>
                <div className="space-y-1">
                  {platforms.filter(p => p.category === cat).map(p => {
                    const active = connectors.find(c => c.platform === p.platformType)
                    return (
                      <button key={p.platformType} onClick={() => active ? setSelectedConnector(active) : openCreate(p)}
                        className="w-full text-left flex items-center gap-2 px-3 py-2 rounded-lg text-sm hover:bg-[var(--surface-muted)] hover:bg-[var(--surface-base)] transition-colors group">
                        <div className={`w-6 h-6 rounded ${CATEGORY_COLORS[p.category] || 'bg-[var(--surface-muted)]'} flex items-center justify-center text-white`}>
                          {CATEGORY_ICONS[p.category] || <Plug className="w-3 h-3" />}
                        </div>
                        <span className="flex-1 text-[var(--text-secondary)] group-hover:text-[var(--text-primary)]">{p.name}</span>
                        {active ? (
                          <span className={`w-2 h-2 rounded-full ${active.health?.status === 'UP' ? 'bg-[var(--nexus-success-50)]0' : 'bg-[var(--nexus-error-50)]0'}`} />
                        ) : (
                          <Plus className="w-3.5 h-3.5 text-[var(--text-tertiary)] group-hover:text-[var(--nexus-primary-500)]" />
                        )}
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>

          <div className="lg:col-span-3 space-y-4">
            {selectedConnector ? (
              <div className="space-y-4">
                <div className="card">
                  <div className="card-header flex justify-between items-center">
                    <div className="flex items-center gap-3">
                      <div className={`w-10 h-10 ${platformTypeColor(selectedConnector.platform)} rounded-lg flex items-center justify-center text-white`}>
                        {CATEGORY_ICONS[selectedConnector.category] || <Plug className="w-5 h-5" />}
                      </div>
                      <div>
                        <h3 className="text-sm font-semibold text-[var(--text-primary)]">{selectedConnector.name}</h3>
                        <div className="flex items-center gap-2 mt-0.5">
                          <StatusBadge status={selectedConnector.health?.status || 'UNKNOWN'} size="sm" />
                          <span className="text-xs text-[var(--text-tertiary)]">{selectedConnector.platform}</span>
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <PermissionGate resource="integrations" action="edit">
                        <button onClick={() => handleTest(selectedConnector.id)} className="btn-ghost text-xs">
                          <TestTube className="w-3.5 h-3.5" /> Test
                        </button>
                      </PermissionGate>
                      <PermissionGate resource="integrations" action="edit">
                        <button onClick={() => handleRegisterWebhooks(selectedConnector.id)} className="btn-ghost text-xs">
                          <Link className="w-3.5 h-3.5" /> Webhooks
                        </button>
                      </PermissionGate>
                      <PermissionGate resource="integrations" action="delete">
                        <button onClick={() => handleDelete(selectedConnector.id)} className="btn-ghost text-xs text-[var(--nexus-error-500)]">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </PermissionGate>
                    </div>
                  </div>

                  <div className="card-body">
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">
                      <div className="text-center p-3 bg-[var(--surface-sunken)] bg-[var(--surface-base)] rounded-lg">
                        <p className="text-lg font-bold text-[var(--text-primary)]">{selectedConnector.health?.consecutiveFailures || 0}</p>
                        <p className="text-xs text-[var(--text-secondary)]">Failures</p>
                      </div>
                      <div className="text-center p-3 bg-[var(--surface-sunken)] bg-[var(--surface-base)] rounded-lg">
                        <p className="text-lg font-bold text-[var(--nexus-success-600)]">{selectedConnector.health?.lastSuccessAt ? 'OK' : '-'}</p>
                        <p className="text-xs text-[var(--text-secondary)]">Last Success</p>
                      </div>
                      <div className="text-center p-3 bg-[var(--surface-sunken)] bg-[var(--surface-base)] rounded-lg">
                        <p className="text-lg font-bold text-[var(--text-primary)]">{selectedConnector.supportedSyncTypes?.length || 0}</p>
                        <p className="text-xs text-[var(--text-secondary)]">Sync Types</p>
                      </div>
                      <div className="text-center p-3 bg-[var(--surface-sunken)] bg-[var(--surface-base)] rounded-lg">
                        <p className="text-lg font-bold text-[var(--text-primary)]">
                          {jobs.filter(j => j.connectorId === selectedConnector.id).length}
                        </p>
                        <p className="text-xs text-[var(--text-secondary)]">Jobs</p>
                      </div>
                    </div>

                    <h4 className="text-xs font-semibold text-[var(--text-secondary)] uppercase mb-3">Sync Actions</h4>
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                      {selectedConnector.supportedSyncTypes?.map(st => (
                        <PermissionGate key={st} resource="integrations" action="edit">
                          <button onClick={() => handleSync(selectedConnector.id, st)}
                            disabled={syncing === `${selectedConnector.id}:${st}`}
                            className="border border-[var(--border-default)] rounded-lg p-3 text-left hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)] transition-colors">
                            <div className="flex items-center justify-between mb-1">
                              <span className="text-sm font-medium text-[var(--text-secondary)]">{st.replace(/_/g, ' ')}</span>
                              {syncing === `${selectedConnector.id}:${st}` ?
                                <Loader2 className="w-3.5 h-3.5 animate-spin text-[var(--text-brand)]" /> :
                                <RefreshCw className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />}
                            </div>
                            <p className="text-xs text-[var(--text-tertiary)]">Click to run</p>
                          </button>
                        </PermissionGate>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="card">
                  <div className="card-header"><h3 className="text-sm font-semibold text-[var(--text-primary)]">Recent Jobs</h3></div>
                  <div className="card-body">
                    {jobs.filter(j => j.connectorId === selectedConnector.id).length === 0 ? (
                      <p className="text-sm text-[var(--text-tertiary)] text-center py-4">No jobs yet. Run a sync to see results.</p>
                    ) : (
                      <div className="space-y-2">
                        {jobs.filter(j => j.connectorId === selectedConnector.id).slice(0, 10).map(j => (
                          <div key={j.jobId} className="flex items-center justify-between p-3 bg-[var(--surface-sunken)] bg-[var(--surface-base)] rounded-lg text-sm">
                            <div className="flex items-center gap-3">
                              <StatusBadge status={j.status} size="sm" />
                              <span className="text-[var(--text-secondary)] font-medium">{j.syncType}</span>
                            </div>
                            <div className="flex items-center gap-4 text-xs text-[var(--text-secondary)]">
                              <span>{j.itemsSucceeded} OK / {j.itemsFailed} failed</span>
                              {j.durationMs > 0 && <span>{(j.durationMs / 1000).toFixed(1)}s</span>}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ) : (
              <div className="card p-12 text-center text-[var(--text-tertiary)]">
                <Plug className="w-16 h-16 mx-auto mb-4 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
                <p className="text-sm">Select a platform from the left panel to view or create a connector</p>
                <p className="text-xs mt-1">Connected platforms show a green dot</p>
              </div>
            )}
          </div>
        </div>
      )}

      {showCreate && selectedPlatform && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-lg">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <div className="flex items-center gap-3">
                <div className={`w-8 h-8 rounded ${CATEGORY_COLORS[selectedPlatform.category] || 'bg-[var(--surface-muted)]'} flex items-center justify-center text-white`}>
                  {CATEGORY_ICONS[selectedPlatform.category] || <Plug className="w-4 h-4" />}
                </div>
                <h2 className="text-lg font-semibold text-[var(--text-primary)]">Connect {selectedPlatform.name}</h2>
              </div>
              <button onClick={() => setShowCreate(false)} className="p-1 hover:bg-[var(--surface-muted)] hover:bg-[var(--surface-base)] rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <p className="text-sm text-[var(--text-secondary)]">{selectedPlatform.description}</p>

              <div className="flex gap-2 text-xs text-[var(--text-secondary)] mb-3">
                {selectedPlatform.supportedProtocols.map(p => (
                  <span key={p} className="bg-[var(--surface-muted)] bg-[var(--surface-base)] px-2 py-1 rounded">{p}</span>
                ))}
                {selectedPlatform.supportedAuthTypes.map(a => (
                  <span key={a} className="bg-[var(--interactive-selected)] text-[var(--text-brand)] px-2 py-1 rounded">{a}</span>
                ))}
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Store Code</label>
                  <Autocomplete value={form.storeCode || ''} onChange={v => setForm({ ...form, storeCode: v })}
                    className="input w-full font-mono text-sm" placeholder={selectedPlatform.platformType.toLowerCase()} minChars={0} />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Store Name</label>
                  <Autocomplete value={form.storeName || ''} onChange={v => setForm({ ...form, storeName: v })}
                    className="input w-full text-sm" placeholder={`${selectedPlatform.name} Store`} minChars={0} />
                </div>
              </div>

              <div className="border-t border-[var(--border-subtle)] pt-4">
                <h4 className="text-xs font-semibold text-[var(--text-secondary)] uppercase mb-3">API Credentials</h4>
                {selectedPlatform.requiredSettings.map(key => (
                  <div key={key} className="mb-3">
                    <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">
                      {key.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}
                    </label>
                    <Autocomplete value={form[key] || ''}
                      onChange={v => setForm({ ...form, [key]: v })}
                      className="input w-full font-mono text-sm" placeholder={key} minChars={0} />
                  </div>
                ))}
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowCreate(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="integrations" action="create">
                <button onClick={handleCreate} disabled={saving} className="btn-primary text-sm">
                  {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                  Connect {selectedPlatform.name}
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
