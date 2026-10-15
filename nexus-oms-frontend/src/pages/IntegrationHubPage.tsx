import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, Plug, Plus, RefreshCw, ShieldAlert, Trash2 } from 'lucide-react'
import PermissionGate from '../components/rbac/PermissionGate'
import { useToast } from '../hooks/useToast'
import {
  createConnection, getConnections, getJobs, getPlatforms, removeConnection, runOperation, testConnection,
  type Connection, type Platform,
} from '../features/integrations/hubApi'
import {
  actionLabel, groupPlatforms, isSecretField, jobView, NEEDS_CONFIRM, previewLabel, requiredFields, verificationView, type View,
} from '../features/integrations/hubRules'

const field = 'w-full rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)]'
const primary = 'inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-primary-600)] px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-[var(--nexus-primary-700)] disabled:opacity-50'
const ghost = 'inline-flex items-center gap-2 rounded-lg border border-[var(--border-default)] px-3 py-1.5 text-xs font-semibold text-[var(--text-secondary)] transition hover:bg-[var(--interactive-hover)] disabled:opacity-50'
const card = 'rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5 shadow-sm'

const TONE: Record<View['tone'], string> = {
  good: 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]',
  warn: 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]',
  bad: 'bg-[var(--surface-sunken)] text-[var(--nexus-error-600)]',
  muted: 'bg-[var(--surface-sunken)] text-[var(--text-tertiary)]',
}
const chip = (v: View) => <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${TONE[v.tone]}`}>{v.label}</span>

const errorText = (e: unknown) => {
  const r = e as { response?: { data?: { error?: string; message?: string } }; message?: string }
  return r.response?.data?.error ?? r.response?.data?.message ?? r.message ?? 'Request failed'
}

export default function IntegrationHubPage() {
  const qc = useQueryClient()
  const { addToast } = useToast()
  const [platformKey, setPlatformKey] = useState('')
  const [storeName, setStoreName] = useState('')
  const [values, setValues] = useState<Record<string, string>>({})
  const [selectedId, setSelectedId] = useState('')
  const [params, setParams] = useState('{}')

  const platforms = useQuery({ queryKey: ['hub-platforms'], queryFn: getPlatforms, staleTime: 10 * 60_000 })
  const connections = useQuery({ queryKey: ['hub-connections'], queryFn: getConnections })
  const jobs = useQuery({ queryKey: ['hub-jobs'], queryFn: getJobs, refetchInterval: 15_000 })

  const platform: Platform | undefined = platforms.data?.find(p => p.platformType === platformKey)
  const selected: Connection | undefined = connections.data?.find(c => c.id === selectedId)
  const selectedPlatform = platforms.data?.find(p => p.platformType === selected?.platform)
  const myJobs = useMemo(() => (jobs.data ?? []).filter(j => j.connectorId === selectedId).sort((a, b) => b.startedAt.localeCompare(a.startedAt)), [jobs.data, selectedId])

  const refresh = () => { qc.invalidateQueries({ queryKey: ['hub-connections'] }); qc.invalidateQueries({ queryKey: ['integration-health'] }) }

  const save = useMutation({
    mutationFn: () => {
      const settings: Record<string, string> = {}
      const credentials: Record<string, string> = {}
      for (const [k, v] of Object.entries(values)) if (v.trim()) (isSecretField(k) ? credentials : settings)[k] = v.trim()
      return createConnection(platformKey, storeName.trim() || platform?.name || platformKey, settings, credentials)
    },
    onSuccess: r => {
      refresh(); setSelectedId(r.id); setValues({}); setStoreName('')
      addToast(r.connectionVerified
        ? { type: 'success', title: 'Saved, and the platform accepted the connection' }
        : { type: 'error', title: 'Saved, but the connection test failed', message: r.message })
    },
    onError: e => addToast({ type: 'error', title: 'Could not save the connection', message: errorText(e) }),
  })
  const test = useMutation({
    mutationFn: (id: string) => testConnection(id),
    onSuccess: r => { refresh(); addToast({ type: r.success ? 'success' : 'error', title: r.success ? 'Test passed' : 'Test failed', message: r.message }) },
    onError: e => addToast({ type: 'error', title: 'Test could not run', message: errorText(e) }),
  })
  const remove = useMutation({
    mutationFn: (id: string) => removeConnection(id),
    onSuccess: () => { refresh(); setSelectedId('') },
    onError: e => addToast({ type: 'error', title: 'Could not remove the connection', message: errorText(e) }),
  })
  const run = useMutation({
    mutationFn: (v: { type: string; body: Record<string, unknown> }) => runOperation(selectedId, v.type, v.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hub-jobs'] }),
    onError: e => addToast({ type: 'error', title: 'Could not run it', message: errorText(e) }),
  })

  const start = (type: string) => {
    let body: Record<string, unknown> = {}
    try { body = JSON.parse(params || '{}') } catch { addToast({ type: 'error', title: 'Parameters are not valid JSON' }); return }
    if (NEEDS_CONFIRM.has(type) && !window.confirm(`${actionLabel(type)} on ${selected?.name}? This changes something on the platform and cannot be undone from Nexus.`)) return
    run.mutate({ type, body })
  }

  if (platforms.isLoading || connections.isLoading) return <div className="grid place-items-center p-16"><Loader2 className="h-6 w-6 animate-spin text-[var(--text-tertiary)]" /></div>

  return (
    <div className="space-y-6">
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5"><Plug className="h-7 w-7 text-[var(--nexus-primary-500)]" /> Connections</h1>
          <p>Saved logins to outside platforms. A connection counts as verified only when its last real test passed; saving one proves nothing.</p>
        </div>
        <Link to="/integrations/directory" className={ghost}>What each platform can do</Link>
      </div>

      <div role="note" className="flex gap-3 rounded-xl border border-[var(--nexus-warning-300)] bg-[var(--nexus-warning-50)] p-4 text-sm text-[var(--nexus-warning-700)]">
        <ShieldAlert className="mt-0.5 h-4 w-4 flex-shrink-0" />
        <span>Shopify and BigCommerce are managed on their own pages under Sales Channels. Everything here is a lighter connection: it can be tested, can read a preview, and a few platforms can perform the actions listed on them. Previews never import anything into Nexus. See the Directory for the detail.</span>
      </div>

      <section className={card} aria-labelledby="hub-list">
        <h2 id="hub-list" className="text-base font-semibold text-[var(--text-primary)]">Your connections</h2>
        {(connections.data ?? []).length === 0 ? (
          <p className="mt-3 rounded-lg bg-[var(--surface-sunken)] p-4 text-sm text-[var(--text-secondary)]">No connections yet.</p>
        ) : (
          <ul className="mt-3 divide-y divide-[var(--border-subtle)]">
            {connections.data!.map(c => {
              const v = verificationView(c)
              return (
                <li key={c.id} className={`flex flex-wrap items-center justify-between gap-3 py-3 ${c.id === selectedId ? 'bg-[var(--surface-sunken)]' : ''}`}>
                  <button type="button" onClick={() => setSelectedId(c.id)} className="text-left">
                    <div className="font-medium text-[var(--text-primary)]">{c.name} <span className="ml-1 text-xs text-[var(--text-tertiary)]">{c.platform}</span></div>
                    <div className="text-xs text-[var(--text-tertiary)]">{v.detail}</div>
                  </button>
                  <div className="flex items-center gap-2">
                    {chip(v)}
                    <button type="button" className={ghost} disabled={test.isPending} onClick={() => test.mutate(c.id)}>
                      <RefreshCw className={`h-3.5 w-3.5 ${test.isPending && test.variables === c.id ? 'animate-spin' : ''}`} /> Test now
                    </button>
                    <PermissionGate resource="integration" action="delete">
                      <button type="button" className={ghost} aria-label={`Remove ${c.name}`}
                        onClick={() => { if (window.confirm(`Remove ${c.name}? Its saved credentials are deleted.`)) remove.mutate(c.id) }}>
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </PermissionGate>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      {selected && (
        <section className={card} aria-labelledby="hub-ops">
          <h2 id="hub-ops" className="text-base font-semibold text-[var(--text-primary)]">{selected.name}: what you can do</h2>
          {selectedPlatform && selectedPlatform.supportedSyncTypes.length === 0 && selectedPlatform.previewSyncTypes.length === 0 && (
            <p className="mt-2 text-sm text-[var(--text-secondary)]">Beyond testing the connection, Nexus does nothing with {selected.name}.</p>
          )}
          <div className="mt-3 flex flex-wrap gap-2">
            {selectedPlatform?.previewSyncTypes.map(t => (
              <button key={t} type="button" className={ghost} disabled={run.isPending} onClick={() => start(t)}>{previewLabel(t)}</button>
            ))}
            <PermissionGate resource="integration" action="create">
              {selectedPlatform?.supportedSyncTypes.map(t => (
                <button key={t} type="button" className={primary} disabled={run.isPending} onClick={() => start(t)}>{actionLabel(t)}</button>
              ))}
            </PermissionGate>
          </div>
          {(selectedPlatform?.supportedSyncTypes.length ?? 0) > 0 && (
            <label className="mt-3 block text-xs text-[var(--text-secondary)]">Details for the action (JSON), for example the charge to refund
              <textarea className={`${field} mt-1 font-mono`} rows={3} value={params} onChange={e => setParams(e.target.value)} />
            </label>
          )}
          <p className="mt-3 text-xs text-[var(--text-tertiary)]">Automatic webhook registration is not offered for these platforms; set up event delivery in the platform&apos;s own console.</p>

          {myJobs.length > 0 && (
            <ul className="mt-4 space-y-2 text-sm" aria-label="Recent runs">
              {myJobs.slice(0, 8).map(j => {
                const v = jobView(j)
                return (
                  <li key={j.jobId} className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span className="font-medium text-[var(--text-primary)]">{actionLabel(j.syncType)}</span>
                    {chip(v)}
                    {v.detail && <span className="text-xs text-[var(--text-tertiary)]">{v.detail}</span>}
                  </li>
                )
              })}
            </ul>
          )}
        </section>
      )}

      <PermissionGate resource="integration" action="create">
        <section className={card} aria-labelledby="hub-add">
          <h2 id="hub-add" className="text-base font-semibold text-[var(--text-primary)]">Add a connection</h2>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            <label className="text-sm text-[var(--text-secondary)]">Platform
              <select className={`${field} mt-1`} value={platformKey} onChange={e => { setPlatformKey(e.target.value); setValues({}) }}>
                <option value="">Choose a platform</option>
                {groupPlatforms(platforms.data ?? []).map(g => (
                  <optgroup key={g.category} label={g.category}>
                    {g.platforms.map(p => <option key={p.platformType} value={p.platformType}>{p.name}</option>)}
                  </optgroup>
                ))}
              </select>
            </label>
            <label className="text-sm text-[var(--text-secondary)]">Name (optional)
              <input className={`${field} mt-1`} value={storeName} onChange={e => setStoreName(e.target.value)} placeholder={platform?.name ?? 'e.g. Production account'} />
            </label>
          </div>

          {platform && (
            <>
              {platform.supportedSyncTypes.length === 0 && platform.previewSyncTypes.length === 0 && (
                <p className="mt-3 text-sm text-[var(--text-secondary)]">
                  There is no connector for {platform.name} beyond testing that an address answers. It reads nothing into Nexus and writes nothing back.
                </p>
              )}
              <div className="mt-3 grid gap-3 md:grid-cols-2">
                {[...requiredFields(platform), ...(isGenericLike(platform) ? ['api_key'] : [])].filter((k, i, a) => a.indexOf(k) === i).map(k => (
                  <label key={k} className="text-sm text-[var(--text-secondary)]">{k.replace(/_/g, ' ')}
                    <input className={`${field} mt-1`} type={isSecretField(k) ? 'password' : 'text'} autoComplete="off"
                      value={values[k] ?? ''} placeholder={platform.defaultSettings?.[k] ?? ''} onChange={e => setValues({ ...values, [k]: e.target.value })} />
                  </label>
                ))}
              </div>
              <button type="button" className={`${primary} mt-4`} disabled={save.isPending || !(values.base_url ?? '').trim()} onClick={() => save.mutate()}>
                {save.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />} Save and test
              </button>
            </>
          )}
        </section>
      </PermissionGate>
    </div>
  )
}

/** Platforms with no dedicated connector authenticate with a single key. */
const isGenericLike = (p: Platform) => p.supportedSyncTypes.length === 0 && !p.requiredSettings?.length
