import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { CalendarClock, CheckCircle2, Loader2, Play, Save, X } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import PermissionGate from '../components/rbac/PermissionGate'
import { getHeld, getPolicy, releaseNow, reschedule, savePolicy, type HeldOrder, type ReleasePolicy } from '../features/release/releaseApi'
import { policyProblems, summary, toInputValue, untilText } from '../features/release/releaseRules'

const field = 'rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)]'
const primary = 'inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-primary-600)] px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-[var(--nexus-primary-700)] disabled:opacity-50'
const ghost = 'inline-flex items-center gap-2 rounded-lg border border-[var(--border-default)] px-3 py-1.5 text-sm font-semibold text-[var(--text-secondary)] transition hover:bg-[var(--interactive-hover)] disabled:opacity-50'
const card = 'rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5 shadow-sm'

const when = (iso: string | null) => (iso ? new Date(iso + (iso.endsWith('Z') ? '' : 'Z')).toLocaleString() : '—')
const errorText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? (e as Error)?.message ?? 'Something went wrong'

export default function ReleaseSchedulePage() {
  const { addToast } = useToast()
  const qc = useQueryClient()
  const policy = useQuery({ queryKey: ['release-policy'], queryFn: getPolicy })
  const held = useQuery({ queryKey: ['release-held'], queryFn: getHeld, refetchInterval: 60_000 })
  const [draft, setDraft] = useState<ReleasePolicy | null>(null)
  const [editing, setEditing] = useState<{ order: HeldOrder; type: string; date: string } | null>(null)
  useEffect(() => { if (policy.data && !draft) setDraft(policy.data) }, [policy.data, draft])

  const refresh = () => { qc.invalidateQueries({ queryKey: ['release-held'] }); qc.invalidateQueries({ queryKey: ['release-policy'] }) }
  const fail = (title: string) => (e: unknown) => addToast({ type: 'error', title, message: errorText(e) })
  const save = useMutation({ mutationFn: savePolicy, onSuccess: () => { addToast({ type: 'success', title: 'Release windows saved', message: 'Orders on hold were re-planned.' }); refresh() }, onError: fail('Could not save') })
  const release = useMutation({ mutationFn: releaseNow, onSuccess: o => { addToast({ type: 'success', title: `Order ${o.orderNumber ?? ''} released`, message: 'It goes to brokering now and will reserve stock.' }); refresh() }, onError: fail('Could not release') })
  const move = useMutation({
    mutationFn: (e: { id: string; type: string; date: string }) => reschedule(e.id, e.type, e.date ? e.date + ':00' : null),
    onSuccess: () => { addToast({ type: 'success', title: 'Ship date saved' }); setEditing(null); refresh() },
    onError: fail('Could not reschedule'),
  })

  const problems = draft ? policyProblems(draft) : []
  const s = summary(held.data ?? [])

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-4 md:p-6">
      <header>
        <h1 className="flex items-center gap-2 text-2xl font-bold text-[var(--text-primary)]"><CalendarClock className="h-6 w-6" aria-hidden /> Release by ship date</h1>
        <p className="mt-1 max-w-3xl text-sm text-[var(--text-secondary)]">
          Orders with a requested ship date far away (usually B2B, placed weeks ahead) are not allocated until their release time,
          so they reserve no stock while orders that ship sooner need it. At the release time they go to <Link className="underline" to="/brokering">brokering</Link> automatically.
        </p>
      </header>

      <section className={card} aria-labelledby="pol-h">
        <h2 id="pol-h" className="text-base font-semibold text-[var(--text-primary)]">Release windows</h2>
        <p className="mt-1 text-sm text-[var(--text-secondary)]">How many days before the requested ship date fulfilment starts. Orders without a ship date are never held.</p>
        {draft && (
          <div className="mt-4 flex flex-wrap items-end gap-4">
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">B2B orders (days)</span>
              <input type="number" min={0} max={180} className={field + ' w-32'} value={draft.b2bDays} onChange={e => setDraft({ ...draft, b2bDays: Number(e.target.value) })} /></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">B2C orders (days)</span>
              <input type="number" min={0} max={180} className={field + ' w-32'} value={draft.b2cDays} onChange={e => setDraft({ ...draft, b2cDays: Number(e.target.value) })} /></label>
            <PermissionGate resource="orders" action="update">
              <button className={primary} disabled={problems.length > 0 || save.isPending} onClick={() => save.mutate(draft)}>
                {save.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Save className="h-4 w-4" aria-hidden />} Save
              </button>
            </PermissionGate>
          </div>
        )}
        {problems.length > 0 && <ul className="mt-2 list-disc pl-5 text-sm text-[var(--nexus-danger-700)]">{problems.map(p => <li key={p}>{p}</li>)}</ul>}
      </section>

      <section className={card} aria-labelledby="held-h">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="held-h" className="text-base font-semibold text-[var(--text-primary)]">On hold</h2>
          <p className="text-sm text-[var(--text-secondary)]">{s.b2b} B2B · {s.b2c} B2C{s.nextRelease ? ` · next release ${untilText(s.nextRelease)}` : ''}</p>
        </div>
        {held.isLoading && <p className="mt-3 text-sm text-[var(--text-tertiary)]">Loading…</p>}
        {held.data?.length === 0 && <p className="mt-3 flex items-center gap-2 text-sm text-[var(--text-tertiary)]"><CheckCircle2 className="h-4 w-4" aria-hidden /> Nothing on hold: every open order can be fulfilled now.</p>}
        {(held.data?.length ?? 0) > 0 && (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-sm">
              <thead><tr className="text-left text-xs uppercase tracking-wide text-[var(--text-tertiary)]">
                <th className="py-2 pr-3">Order</th><th className="py-2 pr-3">Type</th><th className="py-2 pr-3">Ship by</th><th className="py-2 pr-3">Released</th><th className="py-2 pr-3">Customer</th><th className="py-2" />
              </tr></thead>
              <tbody>
                {held.data!.map(o => (
                  <tr key={o.id} className="border-t border-[var(--border-subtle)]">
                    <td className="py-2 pr-3"><Link className="font-semibold text-[var(--nexus-primary-600)]" to={`/orders/${o.id}`}>{o.orderNumber ?? o.id.slice(0, 8)}</Link><span className="block text-xs text-[var(--text-tertiary)]">{o.channel}</span></td>
                    <td className="py-2 pr-3">{o.orderType}</td>
                    <td className="py-2 pr-3">{when(o.requestedShipDate)}</td>
                    <td className="py-2 pr-3">{when(o.releaseAt)}<span className="block text-xs text-[var(--text-tertiary)]">{untilText(o.releaseAt)}</span></td>
                    <td className="py-2 pr-3">{o.customerEmail ?? '—'}</td>
                    <td className="py-2 text-right">
                      <PermissionGate resource="orders" action="update">
                        <span className="inline-flex gap-2">
                          <button className={ghost} onClick={() => setEditing({ order: o, type: o.orderType, date: toInputValue(o.requestedShipDate) })}>Change date</button>
                          <button className={ghost} disabled={release.isPending} onClick={() => { if (window.confirm('Release this order now? It will reserve stock straight away.')) release.mutate(o.id) }}><Play className="h-4 w-4" aria-hidden /> Release now</button>
                        </span>
                      </PermissionGate>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {editing && (
        <section className={card} aria-labelledby="edit-h">
          <div className="flex items-center justify-between">
            <h2 id="edit-h" className="text-base font-semibold text-[var(--text-primary)]">Ship date for {editing.order.orderNumber ?? 'order'}</h2>
            <button className={ghost} onClick={() => setEditing(null)} aria-label="Close"><X className="h-4 w-4" aria-hidden /></button>
          </div>
          <div className="mt-3 flex flex-wrap items-end gap-4">
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Order type</span>
              <select className={field} value={editing.type} onChange={e => setEditing({ ...editing, type: e.target.value })}><option>B2B</option><option>B2C</option></select></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Requested ship date</span>
              <input type="datetime-local" className={field} value={editing.date} onChange={e => setEditing({ ...editing, date: e.target.value })} /></label>
            <button className={primary} disabled={move.isPending} onClick={() => move.mutate({ id: editing.order.id, type: editing.type, date: editing.date })}>Save</button>
          </div>
          <p className="mt-2 text-xs text-[var(--text-tertiary)]">Clearing the date releases the order now.</p>
        </section>
      )}
    </div>
  )
}
