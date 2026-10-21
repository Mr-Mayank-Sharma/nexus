import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRightLeft, Loader2, Plus, Search, Trash2, X } from 'lucide-react'
import client from '../api/client'
import { useToast } from '../hooks/useToast'

/** Store network on the core model (/core/network): transfers from request to receipt, and stock lookup across stores. */

interface Location { facilityId: string; facilityName: string; facilityTypeId: string }
interface ProductRef { productId: string; productName: string; sku: string }
interface StockRow { productId: string; productName: string; sku: string; sellable: number; available: number }
interface Line { inventoryTransferId: string; productId: string; productName: string; quantity: number; statusId: string; comments?: string | null }
interface Transfer { shipmentId: string; statusId: string; fromFacilityId: string; toFacilityId: string; createdDate: string; lines: Line[] }
interface Receipt { productId: string; shipped: number; received: number; difference: number; outcome: string }
interface Availability { facilityId: string; facilityName: string; sellable: number; confidence: 'HIGH' | 'MEDIUM' | 'LOW'; reasons: string[] }
interface DraftLine { productId: string; label: string; quantity: number; max: number }

const field = 'w-full rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)]'
const primary = 'inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-primary-600)] px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-[var(--nexus-primary-700)] disabled:opacity-50'
const ghost = 'inline-flex items-center gap-2 rounded-lg border border-[var(--border-default)] px-3 py-1.5 text-sm font-semibold text-[var(--text-secondary)] transition hover:bg-[var(--interactive-hover)] disabled:opacity-50'
const card = 'rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5 shadow-sm'
const errorText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? (e as Error)?.message ?? 'Something went wrong'
const unwrap = <T,>(d: { data?: T } | T): T => ((d as { data?: T })?.data ?? d) as T

const STATUS: Record<string, string> = {
  IXF_REQUESTED: 'Requested', IXF_SCHEDULED: 'Approved: stock held', IXF_EN_ROUTE: 'In transit', IXF_COMPLETE: 'Received',
  IXF_RECEIVED_SHORT: 'Short: needs a decision', IXF_CANCELLED: 'Cancelled',
}

/** The step a transfer is at, from its lines: the least advanced open line decides. */
export function transferStage(t: Pick<Transfer, 'lines'>): 'REQUESTED' | 'APPROVED' | 'IN_TRANSIT' | 'NEEDS_DECISION' | 'DONE' | 'CANCELLED' {
  const s = t.lines.map(l => l.statusId)
  if (s.length === 0 || s.every(x => x === 'IXF_CANCELLED')) return 'CANCELLED'
  if (s.some(x => x === 'IXF_REQUESTED')) return 'REQUESTED'
  if (s.some(x => x === 'IXF_SCHEDULED')) return 'APPROVED'
  if (s.some(x => x === 'IXF_EN_ROUTE')) return 'IN_TRANSIT'
  if (s.some(x => x === 'IXF_RECEIVED_SHORT')) return 'NEEDS_DECISION'
  return 'DONE'
}

/** Problems with a new transfer, shown before it is sent. */
export function transferProblems(from: string, to: string, lines: DraftLine[]): string[] {
  const out: string[] = []
  if (!from) out.push('Choose where the stock comes from.')
  if (!to) out.push('Choose where it goes.')
  if (from && to && from === to) out.push('The two locations must be different.')
  if (lines.length === 0) out.push('Add at least one product.')
  lines.forEach(l => {
    if (!(l.quantity > 0)) out.push(`${l.label}: enter a quantity above zero.`)
    else if (l.quantity > l.max) out.push(`${l.label}: only ${l.max} can be sold at the sending location.`)
  })
  return out
}

export default function StoreNetworkPage() {
  const { addToast } = useToast()
  const qc = useQueryClient()
  const [tab, setTab] = useState<'transfers' | 'lookup'>('transfers')
  const locations = useQuery({ queryKey: ['core-locations'], queryFn: async () => unwrap<Location[]>((await client.get('/core/network/locations')).data) })
  const transfers = useQuery({ queryKey: ['core-transfers'], queryFn: async () => unwrap<Transfer[]>((await client.get('/core/network/transfers')).data) })
  const name = (id: string) => locations.data?.find(l => l.facilityId === id)?.facilityName ?? id
  const refresh = () => qc.invalidateQueries({ queryKey: ['core-transfers'] })
  const fail = (title: string) => (e: unknown) => addToast({ type: 'error', title, message: errorText(e) })

  // new transfer
  const [creating, setCreating] = useState(false)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [search, setSearch] = useState('')
  const [lines, setLines] = useState<DraftLine[]>([])
  const stockAtFrom = useQuery({
    queryKey: ['core-stock', from, search],
    enabled: creating && !!from,
    queryFn: async () => unwrap<StockRow[]>((await client.get(`/core/network/locations/${from}/products`, { params: { q: search } })).data),
  })
  const problems = transferProblems(from, to, lines)
  const create = useMutation({
    mutationFn: async () => unwrap<string>((await client.post('/core/network/transfers', {
      fromFacilityId: from, toFacilityId: to, lines: lines.map(l => ({ productId: l.productId, quantity: l.quantity })),
    })).data),
    onSuccess: id => { addToast({ type: 'success', title: `Transfer ${id} requested`, message: 'Approve it to hold the stock at the sending location.' }); setCreating(false); setLines([]); refresh() },
    onError: fail('Could not request the transfer'),
  })

  // actions
  const act = useMutation({
    mutationFn: async ({ id, step }: { id: string; step: 'approve' | 'ship' | 'cancel' }) => client.post(`/core/network/transfers/${id}/${step}`, step === 'ship' ? {} : undefined),
    onSuccess: (_d, v) => { addToast({ type: 'success', title: { approve: 'Approved: stock held', ship: 'Shipped: now in transit', cancel: 'Cancelled' }[v.step] }); refresh() },
    onError: fail('That step did not work'),
  })
  const [receiving, setReceiving] = useState<Transfer | null>(null)
  const [scans, setScans] = useState<Record<string, string>>({})
  const receive = useMutation({
    mutationFn: async (t: Transfer) => unwrap<Receipt[]>((await client.post(`/core/network/transfers/${t.shipmentId}/receive`,
      Object.fromEntries(t.lines.map(l => [l.productId, Number(scans[l.productId] ?? l.quantity)])))).data),
    onSuccess: r => {
      const waiting = r.filter(x => x.outcome === 'WAITING_FOR_DECISION' || x.outcome === 'MORE_THAN_SENT').length
      addToast({ type: waiting ? 'warning' : 'success', title: waiting ? `${waiting} line(s) need a decision` : 'Received', message: r.map(x => `${x.received} of ${x.shipped}`).join(', ') })
      setReceiving(null); setScans({}); refresh()
    },
    onError: fail('Could not receive'),
  })
  const settle = useMutation({
    mutationFn: async ({ id, productId, found }: { id: string; productId: string; found: number }) =>
      client.post(`/core/network/transfers/${id}/settle`, { productId, found, note: 'Settled on the store network screen' }),
    onSuccess: () => { addToast({ type: 'success', title: 'Difference settled' }); refresh() },
    onError: fail('Could not settle'),
  })

  // lookup
  const [q, setQ] = useState('')
  const [picked, setPicked] = useState<ProductRef | null>(null)
  const found = useQuery({ queryKey: ['core-products', q], enabled: tab === 'lookup' && q.trim().length >= 2,
    queryFn: async () => unwrap<ProductRef[]>((await client.get('/core/network/products', { params: { q } })).data) })
  const availability = useQuery({ queryKey: ['core-lookup', picked?.productId], enabled: !!picked,
    queryFn: async () => unwrap<Availability[]>((await client.get(`/core/network/lookup/${picked!.productId}`)).data) })

  const rows = transfers.data ?? []
  return (
    <div className="mx-auto max-w-6xl space-y-6 p-4 md:p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold text-[var(--text-primary)]"><ArrowRightLeft className="h-6 w-6" aria-hidden /> Store network</h1>
          <p className="mt-1 max-w-3xl text-sm text-[var(--text-secondary)]">
            Move stock between locations and see who can sell what. Approving a transfer holds the stock at once; shipping puts it in transit,
            where it is owned but not for sale; receiving puts what arrived on sale. Small shortfalls settle themselves; bigger ones wait for you.
          </p>
        </div>
        <div className="flex gap-2" role="tablist">
          <button role="tab" aria-selected={tab === 'transfers'} className={tab === 'transfers' ? primary : ghost} onClick={() => setTab('transfers')}>Transfers</button>
          <button role="tab" aria-selected={tab === 'lookup'} className={tab === 'lookup' ? primary : ghost} onClick={() => setTab('lookup')}>Who has it</button>
        </div>
      </header>

      {tab === 'transfers' && (
        <>
          {!creating && <button className={primary} onClick={() => setCreating(true)}><Plus className="h-4 w-4" aria-hidden /> New transfer</button>}
          {creating && (
            <section className={card} aria-labelledby="xfer-new-h">
              <div className="flex items-center justify-between">
                <h2 id="xfer-new-h" className="text-base font-semibold text-[var(--text-primary)]">New transfer</h2>
                <button className={ghost} aria-label="Close" onClick={() => { setCreating(false); setLines([]) }}><X className="h-4 w-4" aria-hidden /></button>
              </div>
              <div className="mt-4 grid gap-4 md:grid-cols-2">
                <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">From</span>
                  <select className={field} value={from} onChange={e => { setFrom(e.target.value); setLines([]) }}>
                    <option value="">Choose a location</option>
                    {locations.data?.map(l => <option key={l.facilityId} value={l.facilityId}>{l.facilityName}</option>)}
                  </select></label>
                <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">To</span>
                  <select className={field} value={to} onChange={e => setTo(e.target.value)}>
                    <option value="">Choose a location</option>
                    {locations.data?.filter(l => l.facilityId !== from).map(l => <option key={l.facilityId} value={l.facilityId}>{l.facilityName}</option>)}
                  </select></label>
              </div>
              {from && (
                <div className="mt-4">
                  <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Find a product at {name(from)}</span>
                    <input className={field} value={search} onChange={e => setSearch(e.target.value)} placeholder="Name or SKU" /></label>
                  <ul className="mt-2 max-h-48 overflow-y-auto text-sm">
                    {stockAtFrom.data?.filter(s => !lines.some(l => l.productId === s.productId)).map(s => (
                      <li key={s.productId} className="flex items-center justify-between border-t border-[var(--border-subtle)] py-1.5">
                        <span><span className="font-medium text-[var(--text-primary)]">{s.productName || s.sku}</span> <span className="font-mono text-xs text-[var(--text-tertiary)]">{s.sku}</span></span>
                        <span className="flex items-center gap-3"><span className="text-[var(--text-secondary)]">{Number(s.available)} can be sold</span>
                          <button className={ghost} onClick={() => setLines(ls => [...ls, { productId: s.productId, label: s.productName || s.sku, quantity: 1, max: Number(s.available) }])}>Add</button></span>
                      </li>
                    ))}
                    {stockAtFrom.data?.length === 0 && <li className="py-1.5 text-[var(--text-tertiary)]">Nothing that can be sold there matches.</li>}
                  </ul>
                </div>
              )}
              {lines.length > 0 && (
                <table className="mt-4 w-full text-sm"><tbody>
                  {lines.map((l, i) => (
                    <tr key={l.productId} className="border-t border-[var(--border-subtle)]">
                      <td className="py-2 pr-3">{l.label}</td>
                      <td className="w-32 py-2 pr-3"><input type="number" min={1} aria-label={`Quantity of ${l.label}`} className={field} value={l.quantity}
                        onChange={e => setLines(ls => ls.map((x, j) => (j === i ? { ...x, quantity: Number(e.target.value) } : x)))} /></td>
                      <td className="py-2 text-right"><button className={ghost} aria-label={`Remove ${l.label}`} onClick={() => setLines(ls => ls.filter((_, j) => j !== i))}><Trash2 className="h-4 w-4" aria-hidden /></button></td>
                    </tr>
                  ))}
                </tbody></table>
              )}
              {problems.length > 0 && (from || lines.length > 0) && <ul className="mt-3 list-disc pl-5 text-sm text-[var(--nexus-danger-700)]">{problems.map(p => <li key={p}>{p}</li>)}</ul>}
              <div className="mt-4"><button className={primary} disabled={problems.length > 0 || create.isPending} onClick={() => create.mutate()}>
                {create.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />} Request transfer</button></div>
            </section>
          )}

          <section className={card} aria-labelledby="xfer-list-h">
            <h2 id="xfer-list-h" className="text-base font-semibold text-[var(--text-primary)]">Transfers</h2>
            {transfers.isLoading && <p className="mt-3 text-sm text-[var(--text-tertiary)]">Loading…</p>}
            {transfers.isError && <p className="mt-3 text-sm text-[var(--nexus-danger-700)]">Could not load transfers: {errorText(transfers.error)}</p>}
            {!transfers.isLoading && rows.length === 0 && !transfers.isError && <p className="mt-3 text-sm text-[var(--text-tertiary)]">No transfers yet.</p>}
            <ul className="mt-3 space-y-3">
              {rows.map(t => {
                const stage = transferStage(t)
                return (
                  <li key={t.shipmentId} className="rounded-xl border border-[var(--border-subtle)] p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="text-sm"><span className="font-semibold text-[var(--text-primary)]">#{t.shipmentId}</span>
                        <span className="ml-2 text-[var(--text-secondary)]">{name(t.fromFacilityId)} → {name(t.toFacilityId)}</span></div>
                      <div className="flex gap-2">
                        {stage === 'REQUESTED' && <button className={primary} disabled={act.isPending} onClick={() => act.mutate({ id: t.shipmentId, step: 'approve' })}>Approve</button>}
                        {stage === 'APPROVED' && <button className={primary} disabled={act.isPending} onClick={() => act.mutate({ id: t.shipmentId, step: 'ship' })}>Ship</button>}
                        {stage === 'IN_TRANSIT' && <button className={primary} onClick={() => { setReceiving(t); setScans({}) }}>Receive</button>}
                        {(stage === 'REQUESTED' || stage === 'APPROVED') && <button className={ghost} disabled={act.isPending} onClick={() => act.mutate({ id: t.shipmentId, step: 'cancel' })}>Cancel</button>}
                      </div>
                    </div>
                    <table className="mt-2 w-full text-sm"><tbody>
                      {t.lines.map(l => (
                        <tr key={l.inventoryTransferId} className="border-t border-[var(--border-subtle)]">
                          <td className="py-1.5 pr-3">{l.productName || l.productId}</td>
                          <td className="py-1.5 pr-3">{Number(l.quantity)}</td>
                          <td className="py-1.5 pr-3">{STATUS[l.statusId] ?? l.statusId}{l.comments ? <span className="block text-xs text-[var(--text-tertiary)]">{l.comments}</span> : null}</td>
                          <td className="py-1.5 text-right">
                            {l.statusId === 'IXF_RECEIVED_SHORT' && (
                              <span className="inline-flex gap-2">
                                <button className={ghost} disabled={settle.isPending} onClick={() => settle.mutate({ id: t.shipmentId, productId: l.productId, found: 0 })}>Write off as lost</button>
                              </span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody></table>
                    {receiving?.shipmentId === t.shipmentId && (
                      <div className="mt-3 rounded-lg bg-[var(--surface-sunken,transparent)] p-3">
                        <p className="text-sm font-medium text-[var(--text-primary)]">Count what arrived</p>
                        {t.lines.map(l => (
                          <label key={l.productId} className="mt-2 flex items-center justify-between gap-3 text-sm">
                            <span>{l.productName || l.productId} <span className="text-[var(--text-tertiary)]">(sent {Number(l.quantity)})</span></span>
                            <input type="number" min={0} className={`${field} w-28`} aria-label={`Received of ${l.productName || l.productId}`}
                              value={scans[l.productId] ?? String(Number(l.quantity))} onChange={e => setScans(s => ({ ...s, [l.productId]: e.target.value }))} />
                          </label>
                        ))}
                        <div className="mt-3 flex gap-2">
                          <button className={primary} disabled={receive.isPending} onClick={() => receive.mutate(t)}>{receive.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />} Receive</button>
                          <button className={ghost} onClick={() => setReceiving(null)}>Cancel</button>
                        </div>
                      </div>
                    )}
                  </li>
                )
              })}
            </ul>
          </section>
        </>
      )}

      {tab === 'lookup' && (
        <section className={card} aria-labelledby="lookup-h">
          <h2 id="lookup-h" className="text-base font-semibold text-[var(--text-primary)]">Who has it</h2>
          <label className="mt-3 block text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Product</span>
            <span className="relative block"><Search className="absolute left-3 top-2.5 h-4 w-4 text-[var(--text-tertiary)]" aria-hidden />
              <input className={`${field} pl-9`} value={q} onChange={e => { setQ(e.target.value); setPicked(null) }} placeholder="Name or SKU, at least 2 letters" /></span></label>
          {!picked && found.data && (
            <ul className="mt-2 text-sm">
              {found.data.map(p => <li key={p.productId}><button className="w-full border-t border-[var(--border-subtle)] py-1.5 text-left hover:bg-[var(--interactive-hover)]" onClick={() => setPicked(p)}>
                {p.productName} <span className="font-mono text-xs text-[var(--text-tertiary)]">{p.sku}</span></button></li>)}
              {found.data.length === 0 && <li className="py-1.5 text-[var(--text-tertiary)]">No product matches.</li>}
            </ul>
          )}
          {picked && (
            <div className="mt-4">
              <p className="text-sm text-[var(--text-secondary)]">{picked.productName} <span className="font-mono text-xs">{picked.sku}</span></p>
              {availability.isLoading && <p className="mt-2 text-sm text-[var(--text-tertiary)]">Loading…</p>}
              {availability.data?.length === 0 && <p className="mt-2 text-sm text-[var(--text-tertiary)]">No location can sell it now.</p>}
              <table className="mt-2 w-full text-sm"><tbody>
                {availability.data?.map(a => (
                  <tr key={a.facilityId} className="border-t border-[var(--border-subtle)]">
                    <td className="py-1.5 pr-3 font-medium text-[var(--text-primary)]">{a.facilityName}</td>
                    <td className="py-1.5 pr-3">{Number(a.sellable)} to sell</td>
                    <td className="py-1.5 pr-3"><span className={a.confidence === 'HIGH' ? 'text-[var(--nexus-success-700)]' : a.confidence === 'LOW' ? 'text-[var(--nexus-danger-700)]' : 'text-[var(--nexus-warning-700)]'}>
                      {a.confidence === 'HIGH' ? 'Sure' : a.confidence === 'MEDIUM' ? 'Probably' : 'Unsure'}</span></td>
                    <td className="py-1.5 text-xs text-[var(--text-tertiary)]">{a.reasons.join('; ')}</td>
                  </tr>
                ))}
              </tbody></table>
            </div>
          )}
        </section>
      )}
    </div>
  )
}
