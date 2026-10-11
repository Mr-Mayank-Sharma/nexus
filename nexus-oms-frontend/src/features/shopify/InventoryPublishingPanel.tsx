import { Fragment, useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ChevronDown, ChevronRight, Eye, Loader2, Plus, Save, Send, Trash2 } from 'lucide-react'
import { getWarehouses } from '../../api/warehouse'
import { useToast } from '../../hooks/useToast'
import PermissionGate from '../../components/rbac/PermissionGate'
import {
  getMappings, getRunLines, getRuns, getShopifyLocations, parseCalculation, publish, saveMappings,
  type LineOutcome, type PublishLine, type PublishRun, type RunStatus,
} from './inventoryApi'
import { emptyRow, explain, fromMapping, toPayload, validateMappings, type MappingRow } from './mappingRules'

const field = 'w-full rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-2.5 py-1.5 text-sm text-[var(--text-primary)] disabled:opacity-60'
const number = 'w-20 rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-2.5 py-1.5 text-right text-sm text-[var(--text-primary)]'
const primary = 'inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-primary-600)] px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-[var(--nexus-primary-700)] disabled:opacity-50'
const ghost = 'inline-flex items-center gap-2 rounded-lg border border-[var(--border-default)] px-3.5 py-2 text-sm font-semibold text-[var(--text-secondary)] transition hover:bg-[var(--interactive-hover)] disabled:opacity-50'

const RUN_TONE: Record<RunStatus, string> = {
  RUNNING: 'bg-[var(--surface-sunken)] text-[var(--text-secondary)]',
  COMPLETED: 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]',
  PARTIAL: 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]',
  FAILED: 'bg-[var(--surface-sunken)] text-[var(--nexus-error-600)]',
  BLOCKED: 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]',
}
const LINE_TONE: Record<LineOutcome, string> = {
  PLANNED: 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]',
  PUBLISHED: 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]',
  UNCHANGED: 'bg-[var(--surface-sunken)] text-[var(--text-secondary)]',
  SKIPPED: 'bg-[var(--surface-sunken)] text-[var(--text-tertiary)]',
  FAILED: 'bg-[var(--surface-sunken)] text-[var(--nexus-error-600)]',
}

const pill = (tone: string, text: string) => (
  <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${tone}`}>{text}</span>
)

const when = (iso: string | null) => (iso ? new Date(iso.endsWith('Z') ? iso : iso + 'Z').toLocaleString() : '')

export default function InventoryPublishingPanel({ storeId }: { storeId: string }) {
  const qc = useQueryClient()
  const { toast } = useToast()
  const mappingsQ = useQuery({ queryKey: ['shopify-mappings', storeId], queryFn: () => getMappings(storeId) })
  const locationsQ = useQuery({ queryKey: ['shopify-locations', storeId], queryFn: () => getShopifyLocations(storeId), retry: false })
  const warehousesQ = useQuery({ queryKey: ['warehouses-all'], queryFn: async () => (await getWarehouses(0, 200)).data?.content ?? [] })
  const runsQ = useQuery({ queryKey: ['shopify-runs', storeId], queryFn: () => getRuns(storeId) })

  const [rows, setRows] = useState<MappingRow[]>([])
  const [dirty, setDirty] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [selectedRun, setSelectedRun] = useState<string | null>(null)
  const [filter, setFilter] = useState<'all' | 'changes' | 'problems'>('all')
  const [open, setOpen] = useState<string | null>(null)

  useEffect(() => {
    if (mappingsQ.data && !dirty) setRows(mappingsQ.data.map(fromMapping))
  }, [mappingsQ.data, dirty])
  useEffect(() => { setSelectedRun(null); setDirty(false); setConfirming(false) }, [storeId])

  const linesQ = useQuery({
    queryKey: ['shopify-lines', storeId, selectedRun],
    queryFn: () => getRunLines(storeId, selectedRun as string),
    enabled: !!selectedRun,
  })

  const problems = useMemo(() => validateMappings(rows), [rows])
  const warehouses = warehousesQ.data ?? []
  const locationName = (id: string) => locationsQ.data?.find(l => l.id === id)?.name ?? id

  const update = (key: string, patch: Partial<MappingRow>) => {
    setRows(rs => rs.map(r => (r.key === key ? { ...r, ...patch } : r)))
    setDirty(true)
    setConfirming(false)
  }

  const save = useMutation({
    mutationFn: () => saveMappings(storeId, toPayload(rows)),
    onSuccess: saved => {
      setDirty(false)
      setRows(saved.map(fromMapping))
      qc.invalidateQueries({ queryKey: ['shopify-mappings', storeId] })
      toast({ title: 'Location mappings saved', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Could not save mappings', description: e.message, variant: 'error' }),
  })

  const run = useMutation({
    mutationFn: (dryRun: boolean) => publish(storeId, dryRun),
    onSuccess: (r: PublishRun) => {
      setConfirming(false)
      setSelectedRun(r.id)
      qc.invalidateQueries({ queryKey: ['shopify-runs', storeId] })
      qc.invalidateQueries({ queryKey: ['shopify-lines', storeId] })
      toast({ title: r.message ?? r.status, variant: r.status === 'COMPLETED' ? 'success' : 'warning' })
    },
    onError: (e: Error) => toast({ title: 'Publish failed', description: e.message, variant: 'error' }),
  })

  const lines = useMemo(() => {
    const all = linesQ.data ?? []
    if (filter === 'changes') return all.filter(l => l.outcome === 'PLANNED' || l.outcome === 'PUBLISHED')
    if (filter === 'problems') return all.filter(l => l.outcome === 'FAILED' || l.outcome === 'SKIPPED' || l.drift)
    return all
  }, [linesQ.data, filter])

  const saved = (mappingsQ.data ?? []).length > 0
  const canRun = saved && !dirty && problems.length === 0 && !run.isPending
  const latest = runsQ.data?.[0]
  const selected = runsQ.data?.find(r => r.id === selectedRun)

  return (
    <div className="card" data-testid="inventory-publishing">
      <div className="card-header flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-[var(--text-primary)]">Inventory publishing</h3>
        {pill(saved ? RUN_TONE.COMPLETED : RUN_TONE.BLOCKED, saved ? `${mappingsQ.data!.length} location mapping${mappingsQ.data!.length === 1 ? '' : 's'}` : 'Not configured')}
      </div>
      <div className="card-body space-y-5">
        <p className="text-sm text-[var(--text-secondary)]">
          Choose which Nexus location feeds which Shopify location. Each Shopify location receives only the sellable stock of the
          locations mapped to it: on hand minus allocated, reserved, damaged and safety stock. Nothing is published until a mapping exists.
        </p>

        {!saved && !dirty && rows.length === 0 && (
          <div role="status" className="flex items-start gap-3 rounded-xl bg-[var(--nexus-warning-50)] p-4 text-sm text-[var(--nexus-warning-700)]">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
            <p>No locations are mapped, so inventory is not being published to this store. Add a mapping below, preview it, then publish.</p>
          </div>
        )}

        {locationsQ.isError && (
          <div role="alert" className="rounded-xl bg-[var(--surface-sunken)] p-4 text-sm text-[var(--nexus-error-600)]">
            Couldn't load this store's Shopify locations: {(locationsQ.error as Error).message}. Check the store's credentials.
          </div>
        )}
        {mappingsQ.isError && <p role="alert" className="text-sm text-[var(--nexus-error-600)]">Couldn't load mappings: {(mappingsQ.error as Error).message}</p>}

        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="border-b border-[var(--border-subtle)] text-left text-xs uppercase tracking-wider text-[var(--text-tertiary)]">
                <th className="py-2 pr-3 font-medium">Nexus location</th>
                <th className="py-2 pr-3 font-medium">Shopify location</th>
                <th className="py-2 pr-3 text-right font-medium" title="Units held back from Shopify for this location, on top of its safety stock">Buffer</th>
                <th className="py-2 pr-3 text-right font-medium" title="Most this location may contribute per SKU; empty means no cap">Cap</th>
                <th className="py-2 pr-3 font-medium">On</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={r.key} className="border-b border-[var(--border-subtle)] align-middle">
                  <td className="py-2 pr-3">
                    <select aria-label={`Nexus location, row ${i + 1}`} value={r.nodeId} onChange={e => update(r.key, { nodeId: e.target.value })} className={field}>
                      <option value="">Choose…</option>
                      {warehouses.map(w => <option key={w.id} value={w.id}>{w.name}</option>)}
                    </select>
                  </td>
                  <td className="py-2 pr-3">
                    <select aria-label={`Shopify location, row ${i + 1}`} value={r.externalLocationId}
                      onChange={e => update(r.key, { externalLocationId: e.target.value, externalLocationName: locationName(e.target.value) })} className={field}>
                      <option value="">Choose…</option>
                      {(locationsQ.data ?? []).map(l => <option key={l.id} value={l.id}>{l.name}{l.active ? '' : ' (inactive)'}</option>)}
                      {r.externalLocationId && !(locationsQ.data ?? []).some(l => l.id === r.externalLocationId) && (
                        <option value={r.externalLocationId}>{r.externalLocationName || r.externalLocationId}</option>
                      )}
                    </select>
                  </td>
                  <td className="py-2 pr-3 text-right">
                    <input aria-label={`Buffer, row ${i + 1}`} type="number" min={0} value={r.bufferUnits} className={number}
                      onChange={e => update(r.key, { bufferUnits: e.target.value === '' ? 0 : Number(e.target.value) })} />
                  </td>
                  <td className="py-2 pr-3 text-right">
                    <input aria-label={`Cap, row ${i + 1}`} inputMode="numeric" placeholder="none" value={r.contributionCap} className={number}
                      onChange={e => update(r.key, { contributionCap: e.target.value })} />
                  </td>
                  <td className="py-2 pr-3">
                    <input aria-label={`Enabled, row ${i + 1}`} type="checkbox" checked={r.enabled} onChange={e => update(r.key, { enabled: e.target.checked })} />
                  </td>
                  <td className="py-2 text-right">
                    <button type="button" aria-label={`Remove row ${i + 1}`} className="rounded p-1.5 text-[var(--text-tertiary)] hover:bg-[var(--interactive-hover)]"
                      onClick={() => { setRows(rs => rs.filter(x => x.key !== r.key)); setDirty(true); setConfirming(false) }}>
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {dirty && problems.length > 0 && (
          <ul role="alert" className="list-disc space-y-0.5 pl-5 text-sm text-[var(--nexus-error-600)]">
            {problems.map(p => <li key={p}>{p}</li>)}
          </ul>
        )}

        <div className="flex flex-wrap items-center gap-2">
          <PermissionGate resource="integration" action="create">
            <button type="button" className={ghost} onClick={() => { setRows(rs => [...rs, emptyRow()]); setDirty(true) }}>
              <Plus className="h-4 w-4" /> Add mapping
            </button>
            <button type="button" className={primary} disabled={!dirty || problems.length > 0 || save.isPending} onClick={() => save.mutate()}>
              {save.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save mappings
            </button>
          </PermissionGate>
          <span className="mx-1 hidden h-6 w-px bg-[var(--border-subtle)] sm:block" />
          <PermissionGate resource="integration" action="create">
            <button type="button" className={ghost} disabled={!canRun} onClick={() => run.mutate(true)}
              title={dirty ? 'Save your changes first' : 'Calculate what would be published, without changing Shopify'}>
              {run.isPending && run.variables === true ? <Loader2 className="h-4 w-4 animate-spin" /> : <Eye className="h-4 w-4" />} Preview (no changes)
            </button>
            {!confirming ? (
              <button type="button" className={primary} disabled={!canRun} onClick={() => setConfirming(true)}
                title={dirty ? 'Save your changes first' : 'Write the calculated quantities to Shopify'}>
                <Send className="h-4 w-4" /> Publish to Shopify
              </button>
            ) : (
              <span className="inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-warning-50)] px-3 py-1.5 text-sm text-[var(--nexus-warning-700)]">
                This changes live quantities on Shopify.
                <button type="button" className={primary} disabled={run.isPending} onClick={() => run.mutate(false)}>
                  {run.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null} Confirm publish
                </button>
                <button type="button" className={ghost} onClick={() => setConfirming(false)}>Cancel</button>
              </span>
            )}
          </PermissionGate>
        </div>

        <section aria-labelledby="runs-heading" className="space-y-3">
          <h4 id="runs-heading" className="text-sm font-semibold text-[var(--text-primary)]">Reconciliation</h4>
          {runsQ.isLoading && <div className="h-10 animate-pulse rounded-lg bg-[var(--surface-sunken)]" />}
          {runsQ.data && runsQ.data.length === 0 && (
            <p className="text-sm text-[var(--text-tertiary)]">No runs yet. Preview a publish to see exactly what would be sent.</p>
          )}
          {latest && !selectedRun && latest.message && (
            <p className="text-sm text-[var(--text-secondary)]">Latest: {latest.message}</p>
          )}
          {runsQ.data && runsQ.data.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[560px] text-sm">
                <thead>
                  <tr className="border-b border-[var(--border-subtle)] text-left text-xs uppercase tracking-wider text-[var(--text-tertiary)]">
                    <th className="py-2 pr-3 font-medium">When</th><th className="py-2 pr-3 font-medium">Type</th>
                    <th className="py-2 pr-3 font-medium">Status</th><th className="py-2 pr-3 font-medium">Result</th>
                  </tr>
                </thead>
                <tbody>
                  {runsQ.data.map(r => (
                    <tr key={r.id} onClick={() => { setSelectedRun(r.id); setOpen(null) }}
                      className={`cursor-pointer border-b border-[var(--border-subtle)] hover:bg-[var(--interactive-hover)] ${r.id === selectedRun ? 'bg-[var(--surface-sunken)]' : ''}`}>
                      <td className="py-2 pr-3">
                        <button type="button" className="text-left underline-offset-2 hover:underline" aria-label={`Open run from ${when(r.startedAt)}`}
                          onClick={e => { e.stopPropagation(); setSelectedRun(r.id); setOpen(null) }}>{when(r.startedAt)}</button>
                      </td>
                      <td className="py-2 pr-3">{r.dryRun ? 'Preview' : 'Publish'}{r.triggeredBy === 'scheduler' ? ' (scheduled)' : ''}</td>
                      <td className="py-2 pr-3">{pill(RUN_TONE[r.status], r.status)}</td>
                      <td className="py-2 pr-3 text-[var(--text-secondary)]">{r.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {selectedRun && (
            <div className="space-y-3 rounded-xl border border-[var(--border-subtle)] p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-[var(--text-secondary)]">{selected?.message}</p>
                <label className="flex items-center gap-2 text-sm text-[var(--text-secondary)]">
                  Show
                  <select aria-label="Filter report lines" value={filter} onChange={e => setFilter(e.target.value as typeof filter)} className={field + ' !w-auto'}>
                    <option value="all">All lines</option>
                    <option value="changes">Changes</option>
                    <option value="problems">Problems and drift</option>
                  </select>
                </label>
              </div>
              {linesQ.isLoading && <div className="h-10 animate-pulse rounded-lg bg-[var(--surface-sunken)]" />}
              {linesQ.isError && <p role="alert" className="text-sm text-[var(--nexus-error-600)]">Couldn't load the report: {(linesQ.error as Error).message}</p>}
              {linesQ.data && lines.length === 0 && (
                <p className="text-sm text-[var(--text-tertiary)]">
                  {linesQ.data.length === 0 ? 'This run recorded no lines (nothing needed changing).' : 'No lines match this filter.'}
                </p>
              )}
              {lines.length > 0 && (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[640px] text-sm">
                    <thead>
                      <tr className="border-b border-[var(--border-subtle)] text-left text-xs uppercase tracking-wider text-[var(--text-tertiary)]">
                        <th className="py-2 pr-3 font-medium">SKU</th><th className="py-2 pr-3 font-medium">Shopify location</th>
                        <th className="py-2 pr-3 text-right font-medium">Calculated</th><th className="py-2 pr-3 text-right font-medium">On Shopify</th>
                        <th className="py-2 pr-3 text-right font-medium">Published</th><th className="py-2 pr-3 font-medium">Outcome</th><th className="py-2" />
                      </tr>
                    </thead>
                    <tbody>
                      {lines.slice(0, 300).map(l => <LineRow key={l.id} line={l} open={open === l.id} onToggle={() => setOpen(open === l.id ? null : l.id)} locationName={locationName} />)}
                    </tbody>
                  </table>
                  {lines.length > 300 && <p className="mt-2 text-xs text-[var(--text-tertiary)]">Showing the first 300 of {lines.length} lines; use the filter to narrow.</p>}
                </div>
              )}
            </div>
          )}
        </section>
      </div>
    </div>
  )

}

function LineRow({ line, open, onToggle, locationName }: { line: PublishLine; open: boolean; onToggle: () => void; locationName: (id: string) => string }) {
  const contributions = parseCalculation(line.calculation)
  return (
    <Fragment>
      <tr className="border-b border-[var(--border-subtle)]">
        <td className="py-2 pr-3 font-mono text-xs">{line.sku}</td>
        <td className="py-2 pr-3">{locationName(line.externalLocationId)}</td>
        <td className="py-2 pr-3 text-right tabular-nums">{line.calculatedQty}</td>
        <td className="py-2 pr-3 text-right tabular-nums">{line.channelQtyBefore ?? '–'}</td>
        <td className="py-2 pr-3 text-right tabular-nums">{line.publishedQty ?? '–'}</td>
        <td className="py-2 pr-3">
          <span className="inline-flex items-center gap-1.5">
            {pill(LINE_TONE[line.outcome], line.outcome)}
            {line.drift && pill('bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]', 'Changed on Shopify')}
          </span>
        </td>
        <td className="py-2 text-right">
          <button type="button" aria-expanded={open} aria-label={`${open ? 'Hide' : 'Show'} how ${line.sku} was calculated`}
            className="rounded p-1.5 text-[var(--text-tertiary)] hover:bg-[var(--interactive-hover)]" onClick={onToggle}>
            {open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          </button>
        </td>
      </tr>
      {open && (
        <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]">
          <td colSpan={7} className="space-y-1 px-3 py-3 text-xs text-[var(--text-secondary)]">
            {line.error && <p className="text-[var(--nexus-error-600)]">{line.error}</p>}
            {contributions.map((c, i) => (
              <p key={i}><span className="font-semibold text-[var(--text-primary)]">{c.nodeName ?? 'Unknown location'}:</span> {explain(c)}</p>
            ))}
            {contributions.length > 1 && <p className="font-semibold text-[var(--text-primary)]">Total offered at this Shopify location: {line.calculatedQty}</p>}
          </td>
        </tr>
      )}
    </Fragment>
  )
}
