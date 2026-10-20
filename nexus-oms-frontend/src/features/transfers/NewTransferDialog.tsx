import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { transfersApi } from '../../api/transfers'
import { listLocations } from '../../api/warehouse'
import { useToast } from '../../hooks/useToast'

const field = 'mt-1 w-full rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)]'
const TYPES = [
  { value: 'WAREHOUSE_TO_STORE', label: 'Warehouse → store' },
  { value: 'STORE_TO_STORE', label: 'Store → store' },
  { value: 'STORE_TO_WAREHOUSE', label: 'Store → warehouse' },
]

interface Line { sku: string; quantity: number }

/** Creates a transfer order (POST /transfers) between two of the tenant's sites. */
export default function NewTransferDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const { addToast } = useToast()
  const { data: sites = [] } = useQuery({ queryKey: ['locations'], queryFn: listLocations })
  const [source, setSource] = useState('')
  const [destination, setDestination] = useState('')
  const [priority, setPriority] = useState('NORMAL')
  const [arrival, setArrival] = useState('')
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState<Line[]>([{ sku: '', quantity: 1 }])
  const [typeOverride, setTypeOverride] = useState<string | null>(null)

  const isStore = (id: string) => (sites.find(s => s.id === id)?.type ?? '').toUpperCase().includes('STORE')
  const suggestedType = useMemo(() => {
    if (!source || !destination) return 'WAREHOUSE_TO_STORE'
    if (isStore(source) && isStore(destination)) return 'STORE_TO_STORE'
    if (isStore(source)) return 'STORE_TO_WAREHOUSE'
    return 'WAREHOUSE_TO_STORE'
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source, destination, sites])
  const transferType = typeOverride ?? suggestedType

  const validLines = lines.filter(l => l.sku.trim() && l.quantity > 0)
  const problem = !source || !destination ? 'Choose a source and a destination.'
    : source === destination ? 'Source and destination must be different sites.'
    : validLines.length === 0 ? 'Add at least one SKU with a quantity.' : null

  const create = useMutation({
    mutationFn: () => transfersApi.createTransfer({
      transferType, sourceNodeId: source, destinationNodeId: destination, priority,
      expectedArrival: arrival ? `${arrival}T12:00:00` : undefined,
      notes: notes.trim() || undefined,
      items: validLines.map(l => ({ sku: l.sku.trim().toUpperCase(), quantityRequested: l.quantity })),
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transfers'] })
      qc.invalidateQueries({ queryKey: ['transfer-stats'] })
      addToast({ type: 'success', title: 'Transfer created' })
      onClose()
    },
    onError: (e: Error) => addToast({ type: 'error', title: 'Could not create transfer', message: e.message }),
  })

  const setLine = (i: number, patch: Partial<Line>) => setLines(ls => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)))

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" role="dialog" aria-modal="true" aria-labelledby="new-transfer-title" onClick={onClose}>
      <form className="max-h-[90vh] w-full max-w-xl space-y-4 overflow-y-auto rounded-2xl bg-[var(--surface-raised)] p-6 shadow-xl"
            onClick={e => e.stopPropagation()} onSubmit={e => { e.preventDefault(); if (!problem) create.mutate() }}>
        <h2 id="new-transfer-title" className="text-lg font-semibold text-[var(--text-primary)]">New transfer</h2>

        {sites.length < 2 && (
          <p className="rounded-lg bg-[var(--nexus-warning-50)] p-3 text-sm text-[var(--nexus-warning-700)]">
            Transfers need at least two sites. Add another warehouse or store first.
          </p>
        )}

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm"><span className="text-[var(--text-secondary)]">From</span>
            <select aria-label="From" required value={source} onChange={e => setSource(e.target.value)} className={field}>
              <option value="">Select site…</option>
              {sites.map(s => <option key={s.id} value={s.id}>{s.code} · {s.name}</option>)}
            </select>
          </label>
          <label className="block text-sm"><span className="text-[var(--text-secondary)]">To</span>
            <select aria-label="To" required value={destination} onChange={e => setDestination(e.target.value)} className={field}>
              <option value="">Select site…</option>
              {sites.filter(s => s.id !== source).map(s => <option key={s.id} value={s.id}>{s.code} · {s.name}</option>)}
            </select>
          </label>
          <label className="block text-sm"><span className="text-[var(--text-secondary)]">Type</span>
            <select aria-label="Type" value={transferType} onChange={e => setTypeOverride(e.target.value)} className={field}>
              {TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
          </label>
          <label className="block text-sm"><span className="text-[var(--text-secondary)]">Priority</span>
            <select aria-label="Priority" value={priority} onChange={e => setPriority(e.target.value)} className={field}>
              {['LOW', 'NORMAL', 'HIGH', 'URGENT'].map(p => <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>)}
            </select>
          </label>
          <label className="block text-sm"><span className="text-[var(--text-secondary)]">Expected arrival (optional)</span>
            <input aria-label="Expected arrival" type="date" value={arrival} onChange={e => setArrival(e.target.value)} className={field} />
          </label>
        </div>

        <fieldset>
          <legend className="mb-1 text-sm text-[var(--text-secondary)]">Items</legend>
          <div className="space-y-2">
            {lines.map((l, i) => (
              <div key={i} className="flex items-center gap-2">
                <input aria-label={`SKU ${i + 1}`} placeholder="SKU" value={l.sku} onChange={e => setLine(i, { sku: e.target.value })} className={`${field} mt-0 flex-1`} />
                <input aria-label={`Quantity ${i + 1}`} type="number" min={1} value={l.quantity}
                       onChange={e => setLine(i, { quantity: Math.max(1, Number(e.target.value) || 1) })} className={`${field} mt-0 w-24`} />
                <button type="button" aria-label={`Remove line ${i + 1}`} disabled={lines.length === 1}
                        onClick={() => setLines(ls => ls.filter((_, j) => j !== i))}
                        className="rounded-lg p-2 text-[var(--text-tertiary)] hover:bg-[var(--interactive-hover)] disabled:opacity-30">
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            ))}
          </div>
          <button type="button" onClick={() => setLines(ls => [...ls, { sku: '', quantity: 1 }])}
                  className="mt-2 inline-flex items-center gap-1 text-sm font-semibold text-[var(--text-brand)] hover:underline">
            <Plus className="h-4 w-4" /> Add line
          </button>
        </fieldset>

        <label className="block text-sm"><span className="text-[var(--text-secondary)]">Notes (optional)</span>
          <textarea rows={2} value={notes} onChange={e => setNotes(e.target.value)} className={field} />
        </label>

        {problem && (source || destination || validLines.length > 0) && <p className="text-sm text-[var(--nexus-error-600)]">{problem}</p>}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-lg border border-[var(--border-default)] px-4 py-2 text-sm text-[var(--text-secondary)]">Cancel</button>
          <button type="submit" disabled={Boolean(problem) || create.isPending}
                  className="rounded-lg bg-[var(--nexus-primary-600)] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
            {create.isPending ? 'Creating…' : 'Create transfer'}
          </button>
        </div>
      </form>
    </div>
  )
}
