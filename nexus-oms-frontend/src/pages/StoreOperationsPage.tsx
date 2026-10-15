import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Copy, KeyRound, Loader2, Plus, RefreshCw, Store } from 'lucide-react'
import { useActiveWarehouse } from '../hooks/useActiveWarehouse'
import WarehouseSelect from '../components/common/WarehouseSelect'
import NoWarehouses from '../components/common/NoWarehouses'
import PermissionGate from '../components/rbac/PermissionGate'
import { useToast } from '../hooks/useToast'
import {
  createSource, getAggregate, getSources, reconcile, rotateKey,
  type CreatedSource, type PosStatus, type ReconcileResult,
} from '../features/stores/posApi'
import { byImpact, filterRows, formatDelta, reconcilable, STATUS_HELP, STATUS_LABEL, whenText } from '../features/stores/posRules'

const field = 'rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)]'
const primary = 'inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-primary-600)] px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-[var(--nexus-primary-700)] disabled:opacity-50'
const ghost = 'inline-flex items-center gap-2 rounded-lg border border-[var(--border-default)] px-3.5 py-2 text-sm font-semibold text-[var(--text-secondary)] transition hover:bg-[var(--interactive-hover)] disabled:opacity-50'
const card = 'rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5 shadow-sm'

const TONE: Record<PosStatus, string> = {
  MATCH: 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]',
  DRIFT: 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]',
  STALE: 'bg-[var(--surface-sunken)] text-[var(--text-secondary)]',
  NO_POS_DATA: 'bg-[var(--surface-sunken)] text-[var(--text-tertiary)]',
  NO_NEXUS_ROW: 'bg-[var(--surface-sunken)] text-[var(--text-tertiary)]',
}
const VENDORS = ['CUSTOM', 'SQUARE', 'LIGHTSPEED', 'CLOVER', 'SHOPIFY_POS']

export default function StoreOperationsPage() {
  const { warehouses, warehouseId, setWarehouseId, isLoading, hasWarehouses } = useActiveWarehouse()
  const qc = useQueryClient()
  const { addToast } = useToast()
  const [filter, setFilter] = useState<PosStatus | 'ALL'>('ALL')
  const [search, setSearch] = useState('')
  const [form, setForm] = useState({ name: '', vendor: 'CUSTOM', staleAfterMinutes: 120 })
  const [created, setCreated] = useState<CreatedSource | null>(null)
  const [preview, setPreview] = useState<ReconcileResult | null>(null)
  const [tenantId, setTenantId] = useState('')

  const sources = useQuery({ queryKey: ['pos-sources'], queryFn: getSources, enabled: hasWarehouses })
  const aggregate = useQuery({
    queryKey: ['pos-aggregate', warehouseId], queryFn: () => getAggregate(warehouseId), enabled: !!warehouseId,
  })
  const here = useMemo(() => (sources.data ?? []).filter(s => s.nodeId === warehouseId), [sources.data, warehouseId])
  const rows = useMemo(() => byImpact(filterRows(aggregate.data?.rows ?? [], filter, search)), [aggregate.data, filter, search])
  const fixable = reconcilable(aggregate.data?.rows ?? []).length

  const fail = (title: string) => (e: unknown) =>
    addToast({ type: 'error', title, message: (e as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message ?? (e as Error)?.message })

  const add = useMutation({
    mutationFn: () => createSource(warehouseId, form.name.trim(), form.vendor, form.staleAfterMinutes),
    onSuccess: c => { setCreated(c); setTenantId(c.source.tenantId ?? ''); setForm(f => ({ ...f, name: '' })); qc.invalidateQueries({ queryKey: ['pos-sources'] }) },
    onError: fail('Could not add the POS source'),
  })
  const rotate = useMutation({
    mutationFn: (id: string) => rotateKey(id),
    onSuccess: c => { setCreated(c); qc.invalidateQueries({ queryKey: ['pos-sources'] }); addToast({ type: 'success', title: 'New key issued; the old one no longer works' }) },
    onError: fail('Could not rotate the key'),
  })
  const run = useMutation({
    mutationFn: (apply: boolean) => reconcile(warehouseId, apply),
    onSuccess: (r, apply) => {
      setPreview(r)
      if (apply) {
        qc.invalidateQueries({ queryKey: ['pos-aggregate', warehouseId] })
        addToast({ type: 'success', title: `Applied ${r.lines.filter(l => l.outcome === 'APPLIED').length} correction(s)` })
      }
    },
    onError: fail('Reconcile failed'),
  })

  if (isLoading) return <div className="grid place-items-center p-16"><Loader2 className="h-6 w-6 animate-spin text-[var(--text-tertiary)]" /></div>
  if (!hasWarehouses) return <NoWarehouses what="Store operations" />

  const endpoint = (c: CreatedSource) => `${window.location.origin}/api/v1/webhooks/pos/${tenantId || '<tenantId>'}/${c.source.id}/inventory`
  const copy = (text: string) => navigator.clipboard?.writeText(text).then(() => addToast({ type: 'success', title: 'Copied' }), () => undefined)

  return (
    <div className="space-y-6">
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5"><Store className="h-7 w-7 text-[var(--nexus-primary-500)]" /> Store Operations</h1>
          <p>Compare what each store&apos;s till counts with what Nexus holds, and correct Nexus when they differ.</p>
        </div>
        <WarehouseSelect warehouses={warehouses} value={warehouseId} onChange={id => { setWarehouseId(id); setPreview(null); setCreated(null) }} />
      </div>

      <section className={card} aria-labelledby="pos-sources">
        <h2 id="pos-sources" className="text-base font-semibold text-[var(--text-primary)]">POS connections for this location</h2>
        <p className="mt-1 text-sm text-[var(--text-tertiary)]">
          A POS (or the middleware in front of it) pushes its stock counts to Nexus. Nexus never reaches into a till and never changes
          stock on its own: counts are compared here and corrected only when you choose.
        </p>

        {here.length === 0 ? (
          <p className="mt-4 rounded-lg bg-[var(--surface-sunken)] p-3 text-sm text-[var(--text-secondary)]">No POS is connected to this location yet.</p>
        ) : (
          <ul className="mt-4 divide-y divide-[var(--border-subtle)]">
            {here.map(s => (
              <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                <div>
                  <div className="font-medium text-[var(--text-primary)]">{s.name} <span className="ml-1 text-xs text-[var(--text-tertiary)]">{s.vendor}</span></div>
                  <div className="text-xs text-[var(--text-tertiary)]">Last report: {whenText(s.lastSnapshotAt)} · counts older than {s.staleAfterMinutes} min are treated as old</div>
                </div>
                <PermissionGate resource="inventory" action="update">
                  <button type="button" className={ghost} disabled={rotate.isPending}
                    onClick={() => { if (window.confirm(`Issue a new key for ${s.name}? The current key stops working immediately.`)) rotate.mutate(s.id) }}>
                    <KeyRound className="h-4 w-4" /> New key
                  </button>
                </PermissionGate>
              </li>
            ))}
          </ul>
        )}

        <PermissionGate resource="inventory" action="create">
          <form className="mt-4 flex flex-wrap items-end gap-3" onSubmit={e => { e.preventDefault(); if (form.name.trim()) add.mutate() }}>
            <label className="text-sm text-[var(--text-secondary)]">Name
              <input className={`${field} mt-1 block`} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="Front counter" />
            </label>
            <label className="text-sm text-[var(--text-secondary)]">System
              <select className={`${field} mt-1 block`} value={form.vendor} onChange={e => setForm({ ...form, vendor: e.target.value })}>
                {VENDORS.map(v => <option key={v} value={v}>{v.replace('_', ' ')}</option>)}
              </select>
            </label>
            <label className="text-sm text-[var(--text-secondary)]">Old after (min)
              <input type="number" min={1} className={`${field} mt-1 block w-28`} value={form.staleAfterMinutes}
                onChange={e => setForm({ ...form, staleAfterMinutes: Math.max(1, Number(e.target.value) || 1) })} />
            </label>
            <button type="submit" className={primary} disabled={!form.name.trim() || add.isPending}>
              {add.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />} Connect a POS
            </button>
          </form>
        </PermissionGate>

        {created && (
          <div role="status" className="mt-4 rounded-xl border border-[var(--nexus-warning-300)] bg-[var(--nexus-warning-50)] p-4 text-sm text-[var(--nexus-warning-700)]">
            <div className="flex items-center gap-2 font-semibold"><AlertTriangle className="h-4 w-4" /> Copy this key now. It is shown once and cannot be recovered.</div>
            <div className="mt-2 flex items-center gap-2">
              <code className="break-all rounded bg-white/70 px-2 py-1 text-[var(--text-primary)]">{created.secret}</code>
              <button type="button" className={ghost} onClick={() => copy(created.secret)}><Copy className="h-4 w-4" /> Copy</button>
            </div>
            <p className="mt-3 text-[var(--text-secondary)]">
              Have the POS send <code>POST</code> with header <code>X-Pos-Key</code> and a JSON body <code>{'{"items":[{"sku":"…","quantity":12}]}'}</code> to:
            </p>
            <label className="mt-2 block text-xs text-[var(--text-secondary)]">Organisation id
              <input className={`${field} ml-2 w-80 max-w-full`} value={tenantId} onChange={e => setTenantId(e.target.value.trim())} placeholder="tenant id" />
            </label>
            <code className="mt-2 block break-all rounded bg-white/70 px-2 py-1 text-[var(--text-primary)]">{endpoint(created)}</code>
            <button type="button" className={`${ghost} mt-3`} onClick={() => setCreated(null)}>I have saved it</button>
          </div>
        )}
      </section>

      <section className={card} aria-labelledby="pos-compare">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="pos-compare" className="text-base font-semibold text-[var(--text-primary)]">Till vs Nexus</h2>
          <div className="flex flex-wrap gap-2">
            <button type="button" className={ghost} onClick={() => aggregate.refetch()} disabled={aggregate.isFetching}>
              <RefreshCw className={`h-4 w-4 ${aggregate.isFetching ? 'animate-spin' : ''}`} /> Refresh
            </button>
            <PermissionGate resource="inventory" action="update">
              <button type="button" className={ghost} disabled={fixable === 0 || run.isPending} onClick={() => run.mutate(false)}>
                Preview corrections ({fixable})
              </button>
            </PermissionGate>
          </div>
        </div>

        {aggregate.isError && <p className="mt-4 text-sm text-[var(--nexus-error-600)]">Could not load the comparison.</p>}

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <button type="button" aria-pressed={filter === 'ALL'} onClick={() => setFilter('ALL')}
            className={`rounded-full px-3 py-1 text-xs font-semibold ${filter === 'ALL' ? 'bg-[var(--nexus-primary-600)] text-white' : 'bg-[var(--surface-sunken)] text-[var(--text-secondary)]'}`}>
            All {aggregate.data?.rows.length ?? 0}
          </button>
          {(Object.keys(STATUS_LABEL) as PosStatus[]).map(st => (aggregate.data?.summary[st] ?? 0) > 0 && (
            <button key={st} type="button" aria-pressed={filter === st} onClick={() => setFilter(st)} title={STATUS_HELP[st]}
              className={`rounded-full px-3 py-1 text-xs font-semibold ${filter === st ? 'ring-2 ring-[var(--nexus-primary-500)]' : ''} ${TONE[st]}`}>
              {STATUS_LABEL[st]} {aggregate.data?.summary[st]}
            </button>
          ))}
          <input className={`${field} ml-auto w-56`} placeholder="Search SKU" aria-label="Search SKU" value={search} onChange={e => setSearch(e.target.value)} />
        </div>

        {aggregate.isLoading ? (
          <div className="grid place-items-center p-10"><Loader2 className="h-5 w-5 animate-spin text-[var(--text-tertiary)]" /></div>
        ) : rows.length === 0 ? (
          <p className="mt-4 rounded-lg bg-[var(--surface-sunken)] p-4 text-sm text-[var(--text-secondary)]">
            {(aggregate.data?.rows.length ?? 0) === 0
              ? 'Nothing to compare yet: this location holds no stock in Nexus and no POS has reported.'
              : 'No SKUs match this filter.'}
          </p>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-[var(--text-tertiary)]">
                  <th className="py-2 pr-4">SKU</th><th className="py-2 pr-4 text-right">Nexus on hand</th>
                  <th className="py-2 pr-4 text-right">Allocated</th><th className="py-2 pr-4 text-right">Till count</th>
                  <th className="py-2 pr-4 text-right">Difference</th><th className="py-2 pr-4">Reported</th><th className="py-2">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {rows.map(r => (
                  <tr key={r.sku}>
                    <td className="py-2 pr-4 font-medium text-[var(--text-primary)]">{r.sku}</td>
                    <td className="py-2 pr-4 text-right tabular-nums">{r.nexusOnHand ?? '—'}</td>
                    <td className="py-2 pr-4 text-right tabular-nums">{r.nexusAllocated ?? '—'}</td>
                    <td className="py-2 pr-4 text-right tabular-nums">{r.posQuantity ?? '—'}</td>
                    <td className={`py-2 pr-4 text-right tabular-nums ${r.delta ? 'font-semibold' : ''}`}>{formatDelta(r.delta)}</td>
                    <td className="py-2 pr-4 text-xs text-[var(--text-tertiary)]">{r.posReportedAt ? whenText(r.posReportedAt) : ''}</td>
                    <td className="py-2"><span title={STATUS_HELP[r.status]} className={`rounded-full px-2 py-0.5 text-xs font-semibold ${TONE[r.status]}`}>{STATUS_LABEL[r.status]}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {preview && (
          <div className="mt-6 rounded-xl border border-[var(--border-default)] p-4" aria-live="polite">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h3 className="font-semibold text-[var(--text-primary)]">
                {preview.applied ? 'Corrections applied' : 'Preview: nothing has changed yet'}
              </h3>
              {!preview.applied && preview.lines.some(l => l.outcome === 'PLANNED') && (
                <PermissionGate resource="inventory" action="update">
                  <button type="button" className={primary} disabled={run.isPending}
                    onClick={() => { if (window.confirm('Set Nexus on-hand to the till count for every PLANNED line? Each change is written to the inventory ledger.')) run.mutate(true) }}>
                    {run.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />} Apply corrections
                  </button>
                </PermissionGate>
              )}
            </div>
            <ul className="mt-3 space-y-1 text-sm">
              {preview.lines.map(l => (
                <li key={l.sku} className="flex flex-wrap gap-x-3 text-[var(--text-secondary)]">
                  <span className="font-medium text-[var(--text-primary)]">{l.sku}</span>
                  <span className="font-semibold">{l.outcome}</span>
                  <span>{l.from ?? '—'} → {l.to ?? '—'}</span>
                  <span className="text-[var(--text-tertiary)]">{l.reason}</span>
                </li>
              ))}
              {preview.lines.length === 0 && <li className="text-[var(--text-tertiary)]">Nothing to correct.</li>}
            </ul>
          </div>
        )}
      </section>
    </div>
  )
}
