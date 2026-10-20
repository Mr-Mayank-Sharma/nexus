import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { AlertTriangle, ArrowRightLeft, CheckCircle2, Eye, Loader2, Pencil, Plus, RotateCcw, Trash2, X } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import PermissionGate from '../components/rbac/PermissionGate'
import {
  createConnection, deleteConnection, getConnections, getFieldGuide, getTemplates, previewConnection, resetWatermark, updateConnection,
  type ConnectionInput, type GenericConnection, type Preview,
} from '../features/generic/genericApi'
import { blankInput, describe, ENTITIES, fromConnection, fromTemplate, inputProblems, pretty, sinceText } from '../features/generic/genericRules'

const field = 'rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)] w-full'
const primary = 'inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-primary-600)] px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-[var(--nexus-primary-700)] disabled:opacity-50'
const ghost = 'inline-flex items-center gap-2 rounded-lg border border-[var(--border-default)] px-3 py-1.5 text-sm font-semibold text-[var(--text-secondary)] transition hover:bg-[var(--interactive-hover)] disabled:opacity-50'
const card = 'rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5 shadow-sm'

function errorText(e: unknown): string {
  const r = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
  return r ?? (e as Error)?.message ?? 'Something went wrong'
}

export default function GenericConnectionsPage() {
  const { addToast } = useToast()
  const qc = useQueryClient()
  const connections = useQuery({ queryKey: ['generic-connections'], queryFn: getConnections })
  const templates = useQuery({ queryKey: ['generic-templates'], queryFn: getTemplates })
  const guide = useQuery({ queryKey: ['generic-fields'], queryFn: getFieldGuide })
  const [editing, setEditing] = useState<{ id: string | null; form: ConnectionInput; hasSecret: boolean } | null>(null)
  const [preview, setPreview] = useState<{ name: string; data: Preview } | null>(null)

  const refresh = () => qc.invalidateQueries({ queryKey: ['generic-connections'] })
  const fail = (title: string) => (e: unknown) => addToast({ type: 'error', title, message: errorText(e) })

  const save = useMutation({
    mutationFn: (e: { id: string | null; form: ConnectionInput }) => (e.id ? updateConnection(e.id, e.form) : createConnection(e.form)),
    onSuccess: c => { addToast({ type: 'success', title: 'Connection saved', message: 'Preview it before you schedule it.' }); setEditing(null); refresh(); runPreview.mutate(c) },
    onError: fail('Could not save the connection'),
  })
  const remove = useMutation({ mutationFn: deleteConnection, onSuccess: () => { refresh(); setPreview(null) }, onError: fail('Could not delete') })
  const reset = useMutation({ mutationFn: resetWatermark, onSuccess: () => { addToast({ type: 'success', title: 'The next run starts from the beginning' }); refresh() }, onError: fail('Could not reset') })
  const runPreview = useMutation({
    mutationFn: async (c: GenericConnection) => ({ name: c.name, data: await previewConnection(c.id) }),
    onSuccess: setPreview,
    onError: fail('Preview failed'),
  })

  const form = editing?.form
  const problems = form ? inputProblems(form) : []
  const setForm = (patch: Partial<ConnectionInput>) => editing && setEditing({ ...editing, form: { ...editing.form, ...patch } })

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-4 md:p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold text-[var(--text-primary)]"><ArrowRightLeft className="h-6 w-6" aria-hidden /> Generic connections</h1>
          <p className="mt-1 max-w-3xl text-sm text-[var(--text-secondary)]">
            Connect any system with a JSON API without new code: describe the endpoint, how to sign in, how pages work and which field goes where.
            Preview the result, then schedule it as a <Link className="underline" to="/jobs">job</Link> (Generic import or Generic export).
          </p>
        </div>
        <PermissionGate resource="import" action="create">
          <button className={primary} onClick={() => setEditing({ id: null, form: blankInput(), hasSecret: false })}><Plus className="h-4 w-4" aria-hidden /> New connection</button>
        </PermissionGate>
      </header>

      {!editing && (
        <section className={card} aria-labelledby="tpl-h">
          <h2 id="tpl-h" className="text-base font-semibold text-[var(--text-primary)]">Start from a template</h2>
          <p className="mt-1 text-sm text-[var(--text-secondary)]">Written from each platform's public API documentation. None has been run against a real store yet, so preview before scheduling.</p>
          <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {(templates.data ?? []).map(t => (
              <button key={t.key} className="rounded-xl border border-[var(--border-subtle)] p-4 text-left transition hover:border-[var(--nexus-primary-500)]"
                onClick={() => setEditing({ id: null, form: fromTemplate(t), hasSecret: false })}>
                <span className="text-xs font-semibold uppercase tracking-wide text-[var(--text-tertiary)]">{t.direction === 'IMPORT' ? 'Import' : 'Export'} · {t.entityType}</span>
                <span className="mt-1 block font-semibold text-[var(--text-primary)]">{t.label}</span>
                <span className="mt-1 block text-xs text-[var(--text-secondary)]">{t.note}</span>
              </button>
            ))}
          </div>
        </section>
      )}

      {editing && form && (
        <section className={card} aria-labelledby="edit-h">
          <div className="flex items-center justify-between">
            <h2 id="edit-h" className="text-base font-semibold text-[var(--text-primary)]">{editing.id ? 'Edit connection' : 'New connection'}</h2>
            <button className={ghost} onClick={() => setEditing(null)} aria-label="Close the form"><X className="h-4 w-4" aria-hidden /></button>
          </div>
          <div className="mt-4 grid gap-4 md:grid-cols-4">
            <label className="text-sm md:col-span-2">
              <span className="mb-1 block font-medium text-[var(--text-secondary)]">Name *</span>
              <input className={field} value={form.name} onChange={e => setForm({ name: e.target.value })} />
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-[var(--text-secondary)]">Direction</span>
              <select className={field} value={form.direction} onChange={e => { const d = e.target.value as 'IMPORT' | 'EXPORT'; setForm({ direction: d, entityType: ENTITIES[d][0].value }) }}>
                <option value="IMPORT">Import into Nexus</option>
                <option value="EXPORT">Export from Nexus</option>
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-[var(--text-secondary)]">What</span>
              <select className={field} value={form.entityType} onChange={e => setForm({ entityType: e.target.value })}>
                {ENTITIES[form.direction].map(x => <option key={x.value} value={x.value}>{x.label}</option>)}
              </select>
            </label>
            {form.direction === 'IMPORT' && form.entityType === 'orders' && (
              <label className="text-sm">
                <span className="mb-1 block font-medium text-[var(--text-secondary)]">Channel name</span>
                <input className={field} value={form.channel ?? ''} placeholder="WOOCOMMERCE" onChange={e => setForm({ channel: e.target.value })} />
              </label>
            )}
            <label className="text-sm md:col-span-3">
              <span className="mb-1 block font-medium text-[var(--text-secondary)]">Secret (token, password or API key)</span>
              <input className={field} type="password" autoComplete="new-password" value={form.secret}
                placeholder={editing.hasSecret ? 'Saved. Leave empty to keep it.' : 'Stored encrypted; never shown again'}
                onChange={e => setForm({ secret: e.target.value })} />
            </label>
          </div>
          <div className="mt-4 grid gap-4 lg:grid-cols-3">
            <label className="text-sm lg:col-span-2">
              <span className="mb-1 block font-medium text-[var(--text-secondary)]">Configuration (endpoint, auth, paging, field mapping)</span>
              <textarea className={field + ' min-h-[360px] font-mono text-xs'} spellCheck={false} value={form.config} onChange={e => setForm({ config: e.target.value })} />
            </label>
            <aside className="space-y-3 text-xs text-[var(--text-secondary)]" aria-label="Field guide">
              <p className="font-semibold text-[var(--text-primary)]">Field guide</p>
              <p>A mapped value is a path (<code>billing.email</code>, <code>items[0].sku</code>), a literal (<code>=USD</code>) or a template (<code>{'{first} {last}'}</code>), optionally followed by transforms: {(guide.data?.transforms ?? []).map(t => <code key={t} className="mr-1">|{t}</code>)}</p>
              {form.direction === 'IMPORT'
                ? <div><p className="font-semibold">Fields you can fill</p><ul className="list-disc pl-4">{(guide.data?.import?.[form.entityType] ?? []).map(f => <li key={f}>{f}</li>)}</ul></div>
                : <div><p className="font-semibold">Nexus fields you can send</p><ul className="list-disc pl-4">{(guide.data?.export?.[form.entityType] ?? []).map(f => <li key={f}>{f}</li>)}</ul></div>}
            </aside>
          </div>
          {problems.length > 0 && <ul className="mt-3 list-disc pl-5 text-sm text-[var(--nexus-danger-700)]">{problems.map(p => <li key={p}>{p}</li>)}</ul>}
          <div className="mt-4 flex gap-2">
            <button className={primary} disabled={problems.length > 0 || save.isPending} onClick={() => save.mutate({ id: editing.id, form })}>
              {save.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <CheckCircle2 className="h-4 w-4" aria-hidden />} Save and preview
            </button>
            <button className={ghost} onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </section>
      )}

      <section className={card} aria-labelledby="list-h">
        <h2 id="list-h" className="text-base font-semibold text-[var(--text-primary)]">Your connections</h2>
        {connections.isLoading && <p className="mt-3 text-sm text-[var(--text-tertiary)]">Loading…</p>}
        {connections.data?.length === 0 && <p className="mt-3 text-sm text-[var(--text-tertiary)]">None yet. Start from a template above.</p>}
        <ul className="mt-3 divide-y divide-[var(--border-subtle)]">
          {(connections.data ?? []).map(c => (
            <li key={c.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="font-semibold text-[var(--text-primary)]">{c.name}</p>
                <p className="text-xs text-[var(--text-secondary)]">{describe(c)} · {sinceText(c)}{c.hasSecret ? ' · secret saved' : ''}</p>
              </div>
              <div className="flex flex-wrap gap-2">
                <button className={ghost} onClick={() => runPreview.mutate(c)} disabled={runPreview.isPending}>
                  {runPreview.isPending && runPreview.variables?.id === c.id ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Eye className="h-4 w-4" aria-hidden />} Preview
                </button>
                <PermissionGate resource="import" action="update">
                  <button className={ghost} onClick={() => setEditing({ id: c.id, form: fromConnection(c), hasSecret: c.hasSecret })}><Pencil className="h-4 w-4" aria-hidden /> Edit</button>
                  <button className={ghost} onClick={() => reset.mutate(c.id)} title="The next run starts from the beginning"><RotateCcw className="h-4 w-4" aria-hidden /> Reset</button>
                </PermissionGate>
                <PermissionGate resource="import" action="delete">
                  <button className={ghost} onClick={() => { if (window.confirm(`Delete "${c.name}"? Jobs that use it will fail until you choose another connection.`)) remove.mutate(c.id) }} aria-label={`Delete ${c.name}`}><Trash2 className="h-4 w-4" aria-hidden /></button>
                </PermissionGate>
              </div>
            </li>
          ))}
        </ul>
      </section>

      {preview && (
        <section className={card} aria-labelledby="pv-h">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 id="pv-h" className="text-base font-semibold text-[var(--text-primary)]">Preview: {preview.name}</h2>
              <p className="text-sm text-[var(--text-secondary)]">{preview.data.note}</p>
            </div>
            <button className={ghost} onClick={() => setPreview(null)} aria-label="Close the preview"><X className="h-4 w-4" aria-hidden /></button>
          </div>
          {preview.data.direction === 'IMPORT' ? (
            <>
              <p className="mt-3 text-sm">
                <strong>{preview.data.fetched}</strong> record(s) read, <strong>{preview.data.valid}</strong> would import,{' '}
                <strong className={preview.data.fetched - preview.data.valid > 0 ? 'text-[var(--nexus-danger-700)]' : ''}>{preview.data.fetched - preview.data.valid}</strong> have problems.
              </p>
              <div className="mt-3 space-y-2">
                {preview.data.records.map((r, i) => (
                  <details key={r.ref + i} className="rounded-lg border border-[var(--border-subtle)] p-3 text-sm" open={i === 0}>
                    <summary className="flex cursor-pointer items-center gap-2">
                      {r.problems.length ? <AlertTriangle className="h-4 w-4 text-[var(--nexus-warning-600)]" aria-label="Has problems" /> : <CheckCircle2 className="h-4 w-4 text-[var(--nexus-success-600)]" aria-label="Fine" />}
                      <span className="font-medium">{r.ref}</span>
                      {r.problems.length > 0 && <span className="text-xs text-[var(--nexus-danger-700)]">{r.problems.join('; ')}</span>}
                    </summary>
                    <div className="mt-2 grid gap-3 md:grid-cols-2">
                      <pre className="max-h-64 overflow-auto rounded bg-[var(--surface-sunken)] p-2 text-xs">{pretty({ ...(r.mapped ?? {}), ...(r.lines?.length ? { lines: r.lines } : {}) } as Record<string, unknown>)}</pre>
                      <pre className="max-h-64 overflow-auto rounded bg-[var(--surface-sunken)] p-2 text-xs" aria-label="The record as received">{typeof r.source === 'string' ? r.source : JSON.stringify(r.source, null, 2)}</pre>
                    </div>
                  </details>
                ))}
              </div>
            </>
          ) : (
            <>
              <p className="mt-3 text-sm"><strong>{preview.data.fetched}</strong> record(s) waiting to be exported (first few shown).</p>
              {preview.data.payload != null && <pre className="mt-2 max-h-80 overflow-auto rounded bg-[var(--surface-sunken)] p-2 text-xs">{JSON.stringify(preview.data.payload, null, 2)}</pre>}
              {preview.data.csv && <pre className="mt-2 max-h-80 overflow-auto rounded bg-[var(--surface-sunken)] p-2 text-xs">{preview.data.csv}</pre>}
            </>
          )}
        </section>
      )}
    </div>
  )
}
