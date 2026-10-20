import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, Loader2, Pencil, Plus, Save, X } from 'lucide-react'
import client from '../api/client'
import { useToast } from '../hooks/useToast'
import PermissionGate from '../components/rbac/PermissionGate'

/** A location as the backend stores it (GET /warehouses). Bins, zones and staff belong to Nexus WMS. */
interface Location {
  id: string
  code: string
  name: string
  type?: string | null
  status?: string | null
  addressLine1?: string | null
  city?: string | null
  state?: string | null
  zipCode?: string | null
  country?: string | null
  latitude?: number | null
  longitude?: number | null
  isActive?: boolean | null
  contactName?: string | null
  contactPhone?: string | null
  contactEmail?: string | null
}

type Draft = Omit<Location, 'id'> & { id?: string }

const TYPES = ['WAREHOUSE', 'DISTRIBUTION_CENTER', 'STORE', 'DARK_STORE', '3PL']
const empty: Draft = { code: '', name: '', type: 'WAREHOUSE', status: 'ACTIVE', isActive: true, country: '' }

const field = 'w-full rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)]'
const primary = 'inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-primary-600)] px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-[var(--nexus-primary-700)] disabled:opacity-50'
const ghost = 'inline-flex items-center gap-2 rounded-lg border border-[var(--border-default)] px-3 py-1.5 text-sm font-semibold text-[var(--text-secondary)] transition hover:bg-[var(--interactive-hover)] disabled:opacity-50'
const card = 'rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5 shadow-sm'
const errorText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? (e as Error)?.message ?? 'Something went wrong'
const label = (t?: string | null) => (t ? t.charAt(0) + t.slice(1).toLowerCase().replace(/_/g, ' ') : '—')

async function listLocations(): Promise<Location[]> {
  const { data } = await client.get('/warehouses', { params: { page: 0, size: 500 } })
  const page = data?.data ?? data
  return Array.isArray(page) ? page : page?.content ?? []
}

async function saveLocation(d: Draft): Promise<Location> {
  const body = { ...d, latitude: d.latitude ?? null, longitude: d.longitude ?? null }
  const { data } = d.id ? await client.put(`/warehouses/${d.id}`, body) : await client.post('/warehouses', body)
  return data?.data ?? data
}

/** Draft problems shown before saving, so the user is not left guessing why a save fails. */
export function locationProblems(d: Draft, existing: Location[]): string[] {
  const out: string[] = []
  if (!d.code.trim()) out.push('Enter a code, for example DC-EAST or STORE-012.')
  if (!d.name.trim()) out.push('Enter a name.')
  if (d.code.trim() && existing.some(l => l.id !== d.id && l.code.toLowerCase() === d.code.trim().toLowerCase())) out.push(`The code ${d.code.trim()} is already used.`)
  const lat = d.latitude, lng = d.longitude
  if ((lat == null) !== (lng == null)) out.push('Enter both latitude and longitude, or neither.')
  if (lat != null && (lat < -90 || lat > 90)) out.push('Latitude must be between -90 and 90.')
  if (lng != null && (lng < -180 || lng > 180)) out.push('Longitude must be between -180 and 180.')
  return out
}

export default function LocationsPage() {
  const { addToast } = useToast()
  const qc = useQueryClient()
  const locations = useQuery({ queryKey: ['locations'], queryFn: listLocations })
  const [draft, setDraft] = useState<Draft | null>(null)

  const save = useMutation({
    mutationFn: saveLocation,
    onSuccess: l => {
      addToast({ type: 'success', title: draft?.id ? `${l.name} saved` : `${l.name} added` })
      setDraft(null)
      qc.invalidateQueries({ queryKey: ['locations'] })
    },
    onError: e => addToast({ type: 'error', title: 'Could not save the location', message: errorText(e) }),
  })
  const toggle = useMutation({
    mutationFn: (l: Location) => saveLocation({ ...l, isActive: !l.isActive, status: l.isActive ? 'INACTIVE' : 'ACTIVE' }),
    onSuccess: l => { addToast({ type: 'success', title: l.isActive ? `${l.name} is active` : `${l.name} is inactive`, message: l.isActive ? 'Routing can send orders here again.' : 'Routing stops sending new orders here.' }); qc.invalidateQueries({ queryKey: ['locations'] }) },
    onError: e => addToast({ type: 'error', title: 'Could not change the location', message: errorText(e) }),
  })

  const rows = locations.data ?? []
  const problems = draft ? locationProblems(draft, rows) : []
  const set = (patch: Partial<Draft>) => setDraft(d => (d ? { ...d, ...patch } : d))
  const num = (v: string) => (v.trim() === '' ? null : Number(v))

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-4 md:p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold text-[var(--text-primary)]"><Building2 className="h-6 w-6" aria-hidden /> Locations</h1>
          <p className="mt-1 max-w-3xl text-sm text-[var(--text-secondary)]">
            Warehouses, distribution centres and stores that hold stock and ship orders. Inactive locations receive no new orders.
            Set the map position so distance to the customer can be measured.
          </p>
        </div>
        <PermissionGate resource="warehouse" action="create">
          <button className={primary} onClick={() => setDraft({ ...empty })}><Plus className="h-4 w-4" aria-hidden /> Add location</button>
        </PermissionGate>
      </header>

      {draft && (
        <section className={card} aria-labelledby="loc-form-h">
          <div className="flex items-center justify-between">
            <h2 id="loc-form-h" className="text-base font-semibold text-[var(--text-primary)]">{draft.id ? `Edit ${draft.name || 'location'}` : 'New location'}</h2>
            <button className={ghost} onClick={() => setDraft(null)} aria-label="Close"><X className="h-4 w-4" aria-hidden /></button>
          </div>
          <div className="mt-4 grid gap-4 md:grid-cols-3">
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Code</span>
              <input id="loc-code" className={field} value={draft.code} onChange={e => set({ code: e.target.value })} placeholder="DC-EAST" /></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Name</span>
              <input id="loc-name" className={field} value={draft.name} onChange={e => set({ name: e.target.value })} placeholder="East distribution centre" /></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Type</span>
              <select id="loc-type" className={field} value={draft.type ?? 'WAREHOUSE'} onChange={e => set({ type: e.target.value })}>{[...new Set([...TYPES, ...(draft.type ? [draft.type] : [])])].map(t => <option key={t} value={t}>{label(t)}</option>)}</select></label>
            <label className="text-sm md:col-span-2"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Address</span>
              <input id="loc-address" className={field} value={draft.addressLine1 ?? ''} onChange={e => set({ addressLine1: e.target.value })} /></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">City</span>
              <input id="loc-city" className={field} value={draft.city ?? ''} onChange={e => set({ city: e.target.value })} /></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">State or region</span>
              <input id="loc-state" className={field} value={draft.state ?? ''} onChange={e => set({ state: e.target.value })} /></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Postal code</span>
              <input id="loc-zip" className={field} value={draft.zipCode ?? ''} onChange={e => set({ zipCode: e.target.value })} /></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Country</span>
              <input id="loc-country" className={field} value={draft.country ?? ''} onChange={e => set({ country: e.target.value })} placeholder="US" /></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Latitude</span>
              <input id="loc-lat" type="number" step="any" className={field} value={draft.latitude ?? ''} onChange={e => set({ latitude: num(e.target.value) })} /></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Longitude</span>
              <input id="loc-lng" type="number" step="any" className={field} value={draft.longitude ?? ''} onChange={e => set({ longitude: num(e.target.value) })} /></label>
            <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Contact email</span>
              <input id="loc-email" type="email" className={field} value={draft.contactEmail ?? ''} onChange={e => set({ contactEmail: e.target.value })} /></label>
          </div>
          {problems.length > 0 && <ul className="mt-3 list-disc pl-5 text-sm text-[var(--nexus-danger-700)]">{problems.map(p => <li key={p}>{p}</li>)}</ul>}
          <div className="mt-4 flex gap-2">
            <button className={primary} disabled={problems.length > 0 || save.isPending} onClick={() => save.mutate({ ...draft, code: draft.code.trim(), name: draft.name.trim() })}>
              {save.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Save className="h-4 w-4" aria-hidden />} {draft.id ? 'Save' : 'Add location'}
            </button>
            <button className={ghost} onClick={() => setDraft(null)}>Cancel</button>
          </div>
        </section>
      )}

      <section className={card} aria-labelledby="loc-list-h">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="loc-list-h" className="text-base font-semibold text-[var(--text-primary)]">All locations</h2>
          <p className="text-sm text-[var(--text-secondary)]">{rows.filter(r => r.isActive !== false).length} active of {rows.length}</p>
        </div>
        {locations.isLoading && <p className="mt-3 text-sm text-[var(--text-tertiary)]">Loading…</p>}
        {locations.isError && <p className="mt-3 text-sm text-[var(--nexus-danger-700)]">Could not load locations: {errorText(locations.error)}</p>}
        {!locations.isLoading && rows.length === 0 && !locations.isError && (
          <p className="mt-3 text-sm text-[var(--text-tertiary)]">No locations yet. Add your first warehouse or store so orders have somewhere to ship from.</p>
        )}
        {rows.length > 0 && (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-sm">
              <thead><tr className="text-left text-xs uppercase tracking-wide text-[var(--text-tertiary)]">
                <th className="py-2 pr-3">Location</th><th className="py-2 pr-3">Type</th><th className="py-2 pr-3">Place</th><th className="py-2 pr-3">Map position</th><th className="py-2 pr-3">Status</th><th className="py-2" />
              </tr></thead>
              <tbody>
                {rows.map(l => (
                  <tr key={l.id} className="border-t border-[var(--border-subtle)]">
                    <td className="py-2 pr-3"><span className="font-semibold text-[var(--text-primary)]">{l.name}</span><span className="block font-mono text-xs text-[var(--text-tertiary)]">{l.code}</span></td>
                    <td className="py-2 pr-3">{label(l.type)}</td>
                    <td className="py-2 pr-3">{[l.city, l.state, l.country].filter(Boolean).join(', ') || '—'}</td>
                    <td className="py-2 pr-3">{l.latitude != null && l.longitude != null ? `${Number(l.latitude).toFixed(3)}, ${Number(l.longitude).toFixed(3)}` : <span className="text-[var(--nexus-warning-700)]">Not set</span>}</td>
                    <td className="py-2 pr-3">{l.isActive === false ? 'Inactive' : 'Active'}</td>
                    <td className="py-2 text-right">
                      <PermissionGate resource="warehouse" action="update">
                        <span className="inline-flex gap-2">
                          <button className={ghost} onClick={() => setDraft({ ...l })}><Pencil className="h-4 w-4" aria-hidden /> Edit</button>
                          <button className={ghost} disabled={toggle.isPending} onClick={() => toggle.mutate(l)}>{l.isActive === false ? 'Activate' : 'Deactivate'}</button>
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
    </div>
  )
}
