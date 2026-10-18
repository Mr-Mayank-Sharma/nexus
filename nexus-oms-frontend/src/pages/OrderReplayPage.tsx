import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { AlertTriangle, Download, FlaskConical, Loader2, Play, Upload } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import { getSample, runReplay, type ReplayResponse } from '../features/replay/replayApi'
import { changeText, comparisonCsv, differencesCsv, fileFrom, headline, money, pct } from '../features/replay/replayRules'

const field = 'rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)] w-full'
const primary = 'inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-primary-600)] px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-[var(--nexus-primary-700)] disabled:opacity-50'
const ghost = 'inline-flex items-center gap-2 rounded-lg border border-[var(--border-default)] px-3 py-1.5 text-sm font-semibold text-[var(--text-secondary)] transition hover:bg-[var(--interactive-hover)] disabled:opacity-50'
const card = 'rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5 shadow-sm'

interface Picked { orders: File | null; stock: File | null; nodes: File | null; sample: boolean }

function save(text: string, name: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

function FilePick({ label, help, file, onPick, required }: { label: string; help: string; file: File | null; onPick: (f: File | null) => void; required?: boolean }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-medium text-[var(--text-secondary)]">{label}{required ? ' *' : ''}</span>
      <input type="file" accept=".csv,text/csv" className={field} aria-label={label} onChange={e => onPick(e.target.files?.[0] ?? null)} />
      <span className="mt-1 block text-xs text-[var(--text-tertiary)]">{file ? `${file.name} (${Math.round(file.size / 1024)} KB)` : help}</span>
    </label>
  )
}

export default function OrderReplayPage() {
  const { addToast } = useToast()
  const [files, setFiles] = useState<Picked>({ orders: null, stock: null, nodes: null, sample: false })
  const [costs, setCosts] = useState({ perShipment: 6.5, perKm: 0, perFailedUnit: '' })
  const [response, setResponse] = useState<ReplayResponse | null>(null)

  const fail = (title: string) => (e: unknown) =>
    addToast({ type: 'error', title, message: (e as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message ?? (e as Error)?.message })

  const sample = useMutation({
    mutationFn: getSample,
    onSuccess: s => {
      setFiles({ orders: fileFrom(s.orders, 'sample-orders.csv'), stock: fileFrom(s.stock, 'sample-stock.csv'), nodes: fileFrom(s.nodes, 'sample-locations.csv'), sample: true })
      setResponse(null)
    },
    onError: fail('Could not load the sample'),
  })

  const run = useMutation({
    mutationFn: () => runReplay(files.orders!, files.stock!, files.nodes, {
      perShipment: costs.perShipment, perKm: costs.perKm, perFailedUnit: costs.perFailedUnit === '' ? null : Number(costs.perFailedUnit),
    }),
    onSuccess: setResponse,
    onError: fail('The replay could not run'),
  })

  const ready = !!files.orders && !!files.stock
  const r = response?.result
  const [base] = r?.strategies ?? []

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header>
        <h1 className="flex items-center gap-2 text-2xl font-semibold text-[var(--text-primary)]"><FlaskConical className="h-6 w-6" aria-hidden /> Routing what-if</h1>
        <p className="mt-1 max-w-3xl text-sm text-[var(--text-secondary)]">
          Replay your own orders against your stock under different routing rules and see how many would have split, what that costs, and what
          avoiding unreliable stores changes. Nothing is saved and nothing in Nexus changes.
        </p>
      </header>

      <section className={card} aria-label="Your data">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold text-[var(--text-primary)]">1. Your data</h2>
          <button className={ghost} onClick={() => sample.mutate()} disabled={sample.isPending}>
            {sample.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Upload className="h-4 w-4" aria-hidden />} Try it with sample data
          </button>
        </div>
        {files.sample && (
          <p className="mb-3 rounded-lg bg-[var(--nexus-warning-50)] p-3 text-sm text-[var(--nexus-warning-700)]" role="note">
            This is invented example data, so you can see how the page works. Upload your own files for a result that means something.
          </p>
        )}
        <div className="grid gap-4 md:grid-cols-3">
          <FilePick label="Orders" required help="order_id, sku, qty (optional: lat, lon of the customer)" file={files.orders} onPick={f => setFiles(x => ({ ...x, orders: f, sample: false }))} />
          <FilePick label="Stock" required help="sku, node_id, qty: what each location held at the start" file={files.stock} onPick={f => setFiles(x => ({ ...x, stock: f, sample: false }))} />
          <FilePick label="Locations (optional)" help="node_id, name, lat, lon, failure_rate (0 to 1, or a percentage)" file={files.nodes} onPick={f => setFiles(x => ({ ...x, nodes: f, sample: false }))} />
        </div>
        <details className="mt-3 text-sm text-[var(--text-secondary)]">
          <summary className="cursor-pointer font-semibold">What do these files need?</summary>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            <li><b>Orders</b>: one row per order line. Several rows with the same order_id make one order.</li>
            <li><b>Stock</b>: the units each location held when the period began. Rows for the same SKU and location are added together.</li>
            <li><b>Locations</b>: optional. Without coordinates, "nearest" follows the order locations are listed in. Without failure_rate, every location is assumed to fail 5% of picks and the failure-aware rule cannot differ. Your own pick-failure history is the number that makes this useful.</li>
          </ul>
        </details>
      </section>

      <section className={card} aria-label="Cost model">
        <h2 className="mb-3 text-lg font-semibold text-[var(--text-primary)]">2. A simple cost model (change it to match yours)</h2>
        <div className="grid gap-4 md:grid-cols-3">
          <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Cost of one shipment ($)</span>
            <input type="number" min={0} step="0.1" className={field} value={costs.perShipment} onChange={e => setCosts(c => ({ ...c, perShipment: Number(e.target.value) }))} /></label>
          <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Extra per kilometre ($, needs coordinates)</span>
            <input type="number" min={0} step="0.001" className={field} value={costs.perKm} onChange={e => setCosts(c => ({ ...c, perKm: Number(e.target.value) }))} /></label>
          <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Cost of each failed pick ($, default = one shipment)</span>
            <input type="number" min={0} step="0.1" className={field} placeholder="same as a shipment" value={costs.perFailedUnit} onChange={e => setCosts(c => ({ ...c, perFailedUnit: e.target.value }))} /></label>
        </div>
        <div className="mt-4">
          <button className={primary} disabled={!ready || run.isPending} onClick={() => run.mutate()}>
            {run.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Play className="h-4 w-4" aria-hidden />} Run the replay
          </button>
          {!ready && <span className="ml-3 text-sm text-[var(--text-tertiary)]">Add the orders and stock files first.</span>}
        </div>
      </section>

      {r && response && (
        <>
          <section className={card} aria-label="Result">
            <h2 className="text-lg font-semibold text-[var(--text-primary)]">3. What would have happened</h2>
            <p className="mt-1 text-sm text-[var(--text-secondary)]">{response.orders} orders ({response.orderLines} lines), {response.skus} SKUs, {response.locations} locations.</p>
            <p className="mt-3 text-base text-[var(--text-primary)]">{headline(r)}</p>

            <div className="mt-4 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase text-[var(--text-tertiary)]">
                  <tr><th className="py-2 pr-3">Routing rule</th><th className="pr-3 text-right">Shipments</th><th className="pr-3 text-right">Orders split</th><th className="pr-3 text-right">Not filled (units)</th><th className="pr-3 text-right">Expected failed picks</th><th className="pr-3 text-right">Estimated cost</th></tr>
                </thead>
                <tbody>
                  {r.strategies.map((m, i) => (
                    <tr key={m.strategy} className="border-t border-[var(--border-subtle)]">
                      <td className="py-2 pr-3 font-medium text-[var(--text-primary)]">{m.label}{i === 0 && <span className="ml-2 text-xs font-normal text-[var(--text-tertiary)]">(starting point)</span>}</td>
                      <td className="pr-3 text-right">{m.shipments}<Delta show={i > 0} base={base.shipments} value={m.shipments} /></td>
                      <td className="pr-3 text-right">{m.splitOrders} <span className="text-[var(--text-tertiary)]">({pct(m.splitRate)})</span></td>
                      <td className="pr-3 text-right">{m.unfilledUnits}</td>
                      <td className="pr-3 text-right">{m.expectedFailedUnits}<Delta show={i > 0} base={base.expectedFailedUnits} value={m.expectedFailedUnits} /></td>
                      <td className="pr-3 text-right font-semibold">{money(m.totalCost)}<Delta show={i > 0} base={base.totalCost} value={m.totalCost} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <button className={ghost} onClick={() => save(comparisonCsv(r.strategies), 'routing-what-if-comparison.csv')}><Download className="h-4 w-4" aria-hidden /> Comparison (CSV)</button>
              {r.differencesTotal > 0 && (
                <button className={ghost} onClick={() => save(differencesCsv(r.differences), 'routing-what-if-orders.csv')}><Download className="h-4 w-4" aria-hidden /> Orders that change (CSV)</button>
              )}
            </div>
          </section>

          <section className={card} aria-label="How to read this">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-[var(--text-primary)]"><AlertTriangle className="h-5 w-5 text-[var(--nexus-warning-600)]" aria-hidden /> How to read this</h2>
            <p className="mt-1 text-sm text-[var(--text-secondary)]">This is a simulation to compare rules fairly on the same orders and the same stock. It is not a forecast of your savings.</p>
            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-[var(--text-secondary)]">{r.assumptions.map(a => <li key={a}>{a}</li>)}</ul>
          </section>

          {r.differencesTotal > 0 && (
            <section className={card} aria-label="Orders that change">
              <h2 className="mb-2 text-lg font-semibold text-[var(--text-primary)]">Orders that would ship differently ({r.differencesTotal}{r.differences.length < r.differencesTotal ? `, first ${r.differences.length} shown` : ''})</h2>
              <div className="max-h-96 overflow-auto">
                <table className="w-full text-left text-sm">
                  <thead className="sticky top-0 bg-[var(--surface-raised)] text-xs uppercase text-[var(--text-tertiary)]"><tr><th className="py-1 pr-3">Order</th><th className="pr-3">Nearest rule</th><th>Failure-aware rule</th></tr></thead>
                  <tbody>
                    {r.differences.map(d => (
                      <tr key={d.orderId} className="border-t border-[var(--border-subtle)]">
                        <td className="py-1 pr-3 font-mono text-xs">{d.orderId}</td>
                        <td className="pr-3">{d.shipmentsBefore} {d.shipmentsBefore === 1 ? 'shipment' : 'shipments'} <span className="text-[var(--text-tertiary)]">({d.before})</span></td>
                        <td>{d.shipmentsAfter} {d.shipmentsAfter === 1 ? 'shipment' : 'shipments'} <span className="text-[var(--text-tertiary)]">({d.after})</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </>
      )}
    </div>
  )
}

function Delta({ show, base, value }: { show: boolean; base: number; value: number }) {
  if (!show) return null
  const t = changeText(base, value)
  const good = value < base
  return <span className={`ml-2 text-xs ${t === 'same' || t === '—' ? 'text-[var(--text-tertiary)]' : good ? 'text-[var(--nexus-success-700)]' : 'text-[var(--nexus-danger-700)]'}`}>{t}</span>
}
