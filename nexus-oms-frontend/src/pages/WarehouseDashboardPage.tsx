import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  AlertTriangle, ArrowRight, Boxes, Building2, ClipboardCheck, Clock, Layers, Package, PackagePlus, Truck, UserCheck,
} from 'lucide-react'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'
import { fetchTwinSites, fetchTwinSnapshot } from '../features/twin/warehouse/api'
import { getPickingKPIs } from '../api/picking'
import { getPackingKPIs } from '../api/packing'
import { pickersApi } from '../api/pickers'

const card = 'rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5'

function tone(status: string | null | undefined): 'success' | 'warning' | 'error' | 'info' | 'neutral' {
  const s = (status ?? '').toUpperCase()
  if (['AVAILABLE', 'IDLE', 'COMPLETED', 'IN_STOCK'].includes(s)) return 'success'
  if (['IN_PROGRESS', 'LOADING', 'UNLOADING', 'OCCUPIED', 'PICKING', 'CHECKED_IN'].includes(s)) return 'info'
  if (['SCHEDULED', 'CONFIRMED', 'ON_BREAK', 'LOW_STOCK'].includes(s)) return 'warning'
  if (['OFFLINE', 'OUT_OF_STOCK', 'NO_SHOW', 'CANCELLED'].includes(s)) return 'error'
  return 'neutral'
}

const human = (s: string | null | undefined) => (s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') : '—')

/**
 * Warehouse manager home. Every figure comes from the API: the site snapshot used by the
 * Warehouse Twin, picking/packing KPIs and the site's pickers. Nothing is hardcoded.
 */
export default function WarehouseDashboardPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  const sites = useQuery({ queryKey: ['twin', 'sites'], queryFn: fetchTwinSites })
  const siteId = params.get('site') ?? sites.data?.[0]?.id ?? null
  const snap = useQuery({ queryKey: ['twin', 'warehouse', siteId], queryFn: () => fetchTwinSnapshot(siteId!), enabled: Boolean(siteId), refetchInterval: 30_000 })
  const picking = useQuery({ queryKey: ['picking-kpis'], queryFn: async () => (await getPickingKPIs()).data ?? {}, refetchInterval: 30_000 })
  const packing = useQuery({ queryKey: ['packing-kpis'], queryFn: async () => (await getPackingKPIs()).data ?? {}, refetchInterval: 30_000 })
  const pickers = useQuery({ queryKey: ['pickers', siteId], queryFn: async () => (await pickersApi.getPickers(siteId!)).data ?? [], enabled: Boolean(siteId) })

  const s = snap.data
  const k = s?.kpis
  const pk = picking.data ?? {}
  const pa = packing.data ?? {}
  const activePickers = (pickers.data ?? []).filter(p => p.active && p.status !== 'OFFLINE')
  const lowStock = (s?.topInventory ?? []).filter(i => i.stockStatus !== 'IN_STOCK')

  if (sites.isSuccess && sites.data.length === 0) {
    return (
      <div className="grid min-h-[60vh] place-items-center">
        <div className={`${card} max-w-md text-center`}>
          <Building2 className="mx-auto mb-3 h-10 w-10 text-[var(--text-tertiary)]" />
          <h1 className="text-lg font-semibold text-[var(--text-primary)]">No warehouses yet</h1>
          <p className="mt-1 text-sm text-[var(--text-tertiary)]">Add your first site to see live operations here.</p>
          <button type="button" onClick={() => navigate('/warehouse')} className="enterprise-btn-primary mt-4 px-4 py-2 text-sm">Add a warehouse</button>
        </div>
      </div>
    )
  }

  return (
    <PermissionGate resource="warehouse" action="view">
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="flex items-center gap-2.5 text-2xl font-bold text-[var(--text-primary)]">
              <Building2 className="h-7 w-7 text-[var(--nexus-warning-500)]" /> Warehouse Dashboard
            </h1>
            <p className="mt-1 text-sm text-[var(--text-secondary)]">
              {s ? `${s.site.name} · ${s.address || s.site.code}` : 'Loading site…'}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {sites.data && sites.data.length > 1 && (
              <select aria-label="Site" value={siteId ?? ''} onChange={e => setParams({ site: e.target.value })}
                      className="rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)]">
                {sites.data.map(x => <option key={x.id} value={x.id}>{x.code} · {x.name}</option>)}
              </select>
            )}
            <button type="button" onClick={() => navigate(siteId ? `/warehouse-twin?site=${siteId}` : '/warehouse-twin')}
                    className="enterprise-btn-secondary flex items-center gap-1.5 px-4 py-2 text-sm">
              <Layers className="h-4 w-4" /> Open 3D twin
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <EnterpriseKPICard title="Active pickers" value={pickers.isSuccess ? activePickers.length : '—'} icon={<UserCheck className="h-5 w-5" />} color="primary"
                             subtitle={pickers.isSuccess ? `${(pickers.data ?? []).length} on the roster` : undefined} loading={pickers.isLoading} />
          <EnterpriseKPICard title="Active picklists" value={pk.activePicklists ?? '—'} icon={<ClipboardCheck className="h-5 w-5" />} color="warning"
                             subtitle={`All sites · ${pk.completedToday ?? 0} completed today · ${pk.pendingItems ?? 0} items to pick`} loading={picking.isLoading} />
          <EnterpriseKPICard title="Packing queue" value={(pa.pendingPack ?? 0) + (pa.packing ?? 0)} icon={<PackagePlus className="h-5 w-5" />} color="info"
                             subtitle={`All sites · ${pa.packed ?? 0} packed · ${pa.shipped ?? 0} shipped`} loading={packing.isLoading} />
          <EnterpriseKPICard title="Docks in use" value={s ? `${s.site.doorsOccupied}/${s.site.doorsTotal}` : '—'} icon={<Truck className="h-5 w-5" />} color="success"
                             subtitle={k ? `${k.trucksOnSite} trucks on site · ${k.arrivalsToday} arrivals today` : undefined} loading={snap.isLoading} />
        </div>

        <div className="grid gap-6 lg:grid-cols-3">
          <section className={`${card} lg:col-span-2`} aria-labelledby="dock-heading">
            <div className="mb-3 flex items-center justify-between">
              <h2 id="dock-heading" className="flex items-center gap-2 text-sm font-semibold text-[var(--text-primary)]"><Truck className="h-4 w-4" /> Dock doors</h2>
              <button type="button" onClick={() => navigate('/yard-dock')} className="flex items-center gap-1 text-xs font-semibold text-[var(--text-brand)] hover:underline">
                Yard &amp; Dock <ArrowRight className="h-3 w-3" />
              </button>
            </div>
            {s && s.docks.length === 0 && <p className="py-6 text-center text-sm text-[var(--text-tertiary)]">No dock doors configured for this site.</p>}
            {s && s.docks.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-[var(--border-subtle)] text-left text-xs uppercase tracking-wide text-[var(--text-tertiary)]">
                      <th className="py-2 pr-4 font-medium">Door</th><th className="py-2 pr-4 font-medium">Vehicle</th>
                      <th className="py-2 pr-4 font-medium">Carrier</th><th className="py-2 pr-4 font-medium">Pallets</th><th className="py-2 font-medium">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {s.docks.map(d => {
                      const t = s.trucks.find(x => x.dockDoorNumber === d.doorNumber)
                      return (
                        <tr key={d.id} className="border-b border-[var(--border-subtle)] last:border-0">
                          <td className="py-2.5 pr-4 font-medium text-[var(--text-primary)]">{d.doorNumber}</td>
                          <td className="py-2.5 pr-4 text-[var(--text-secondary)]">{d.vehicleLabel ?? '—'}</td>
                          <td className="py-2.5 pr-4 text-[var(--text-secondary)]">{t?.carrier ?? '—'}</td>
                          <td className="py-2.5 pr-4 tabular-nums text-[var(--text-secondary)]">{t?.palletCount ?? '—'}</td>
                          <td className="py-2.5"><EnterpriseStatusBadge status={tone(t?.status ?? d.status)} label={human(t?.status ?? d.status)} /></td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
            {s && s.trucks.some(t => !t.dockDoorNumber) && (
              <div className="mt-4 border-t border-[var(--border-subtle)] pt-3">
                <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-[var(--text-tertiary)]">Arriving / in yard</div>
                <div className="flex flex-wrap gap-2">
                  {s.trucks.filter(t => !t.dockDoorNumber).map(t => (
                    <span key={t.id} className="rounded-lg bg-[var(--surface-sunken)] px-2.5 py-1 text-xs text-[var(--text-secondary)]">
                      <span className="font-semibold text-[var(--text-primary)]">{t.label}</span> · {t.carrier ?? '—'} · {human(t.status)}
                      {t.eta ? ` · ETA ${new Date(t.eta).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : ''}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </section>

          <div className="space-y-6">
            <section className={card} aria-labelledby="today-heading">
              <h2 id="today-heading" className="mb-3 text-sm font-semibold text-[var(--text-primary)]">Today</h2>
              {[
                { label: 'Outbound shipments', value: k?.outboundShipmentsToday, icon: <Package className="h-4 w-4 text-[var(--nexus-success-500)]" /> },
                { label: 'Truck arrivals', value: k?.arrivalsToday, icon: <Truck className="h-4 w-4 text-[var(--nexus-primary-500)]" /> },
                { label: 'Picklists completed (all sites)', value: pk.completedToday, icon: <ClipboardCheck className="h-4 w-4 text-[var(--nexus-primary-500)]" /> },
                { label: 'On-time delivery (30 d)', value: k?.onTimeDeliveryPct30d != null ? `${k.onTimeDeliveryPct30d}%` : undefined, icon: <Clock className="h-4 w-4 text-[var(--nexus-warning-500)]" /> },
              ].map(row => (
                <div key={row.label} className="flex items-center justify-between py-1.5">
                  <span className="flex items-center gap-2 text-xs text-[var(--text-secondary)]">{row.icon} {row.label}</span>
                  <span className="text-sm font-semibold tabular-nums text-[var(--text-primary)]">{row.value ?? '—'}</span>
                </div>
              ))}
            </section>

            <section className={card} aria-labelledby="stock-heading">
              <h2 id="stock-heading" className="mb-3 flex items-center gap-2 text-sm font-semibold text-[var(--text-primary)]"><Boxes className="h-4 w-4" /> Stock attention</h2>
              {s && lowStock.length === 0 && <p className="text-sm text-[var(--text-tertiary)]">No low-stock SKUs among the top items at this site.</p>}
              {lowStock.map(i => (
                <div key={i.sku} className="flex items-center justify-between py-1.5 text-sm">
                  <span className="flex items-center gap-2 text-[var(--text-secondary)]"><AlertTriangle className="h-4 w-4 text-[var(--nexus-warning-500)]" />{i.sku}</span>
                  <span className="flex items-center gap-2"><span className="tabular-nums text-[var(--text-primary)]">{i.available}</span>
                    <EnterpriseStatusBadge status={tone(i.stockStatus)} label={human(i.stockStatus)} /></span>
                </div>
              ))}
            </section>
          </div>
        </div>

        <section className={card} aria-labelledby="team-heading">
          <div className="mb-3 flex items-center justify-between">
            <h2 id="team-heading" className="flex items-center gap-2 text-sm font-semibold text-[var(--text-primary)]"><UserCheck className="h-4 w-4" /> Pickers on this site</h2>
            <button type="button" onClick={() => navigate('/pickers')} className="flex items-center gap-1 text-xs font-semibold text-[var(--text-brand)] hover:underline">
              Manage pickers <ArrowRight className="h-3 w-3" />
            </button>
          </div>
          {pickers.isSuccess && (pickers.data ?? []).length === 0 && (
            <p className="py-4 text-center text-sm text-[var(--text-tertiary)]">No pickers registered for this site yet.</p>
          )}
          {(pickers.data ?? []).length > 0 && (
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {(pickers.data ?? []).map(p => (
                <div key={p.id} className="flex items-center justify-between rounded-lg bg-[var(--surface-sunken)] px-3 py-2">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-medium text-[var(--text-primary)]">{p.name}</div>
                    <div className="text-xs text-[var(--text-tertiary)]">{p.ordersCompletedToday} orders · {p.itemsPickedToday} items today</div>
                  </div>
                  <EnterpriseStatusBadge status={tone(p.status)} label={human(p.status)} />
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </PermissionGate>
  )
}
