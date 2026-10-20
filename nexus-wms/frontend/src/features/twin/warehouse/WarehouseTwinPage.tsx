import { Suspense, lazy, useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import {
  Box, Boxes, Building2, ChevronDown, Clock, Forklift as ForkliftIcon, Home, Minus, PackageCheck,
  Plus, RotateCcw, RotateCw, Search, Truck as TruckIcon, Warehouse, X,
} from 'lucide-react'
import { fetchTwinSites, fetchTwinSnapshot, type TwinSnapshot, type TwinTruck } from './api'
import { Chip, Glass, LivePill, Progress, Row } from '../ui'
import { fmtNumber, fmtTime } from '../format'
import { buildLayout, isLift, minutesUntil } from './layout'
import type { SceneHandle, Selection } from './WarehouseScene'

const WarehouseScene = lazy(() => import('./WarehouseScene').then(m => ({ default: m.WarehouseScene })))

const REFRESH_MS = 10_000

// ------------------------------------------------------------------ panels

function KpiTiles({ snap }: { snap: TwinSnapshot }) {
  const k = snap.kpis
  const tiles = [
    { icon: <Boxes className="h-5 w-5" />, label: 'Stock on hand', value: fmtNumber(k.stockOnHandUnits), sub: `${k.skuCount} SKUs · units · ${snap.site.code ?? ''}` },
    { icon: <TruckIcon className="h-5 w-5" />, label: 'Trucks on site', value: fmtNumber(k.trucksOnSite), sub: `${k.trucksInbound} inbound · ${k.trucksOutbound} outbound` },
    {
      icon: <Clock className="h-5 w-5" />,
      label: 'On-time delivery',
      value: k.onTimeDeliveryPct30d === null ? '—' : `${k.onTimeDeliveryPct30d}%`,
      sub: k.onTimeSample30d ? `last 30 days · ${k.onTimeSample30d} deliveries` : 'no delivered shipments in 30 days',
    },
  ]
  return (
    <div className="flex flex-wrap gap-3">
      {tiles.map(t => (
        <Glass key={t.label} className="flex min-w-[200px] items-center gap-3 px-4 py-3">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-blue-50 text-blue-600">{t.icon}</div>
          <div className="min-w-0">
            <div className="text-[12px] font-medium text-slate-500">{t.label}</div>
            <div className="text-xl font-semibold tracking-tight text-slate-900">{t.value}</div>
            <div className="truncate text-[11px] text-slate-400">{t.sub}</div>
          </div>
        </Glass>
      ))}
    </div>
  )
}

function SiteSwitcher({ sites, current, onChange }: {
  sites: { id: string; code: string | null; name: string | null; fillPct: number | null; doorsOccupied: number; doorsTotal: number }[]
  current: string | null
  onChange: (id: string) => void
}) {
  const [open, setOpen] = useState(false)
  const site = sites.find(s => s.id === current)
  return (
    <div className="relative">
      <button onClick={() => setOpen(o => !o)}
              className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white/90 py-1.5 pl-1.5 pr-3 text-left shadow-sm transition hover:border-blue-300">
        <span className="rounded-lg bg-blue-600 px-2 py-1 text-[11px] font-bold text-white">{site?.code ?? '—'}</span>
        <span className="leading-tight">
          <span className="block max-w-[180px] truncate text-[13px] font-semibold text-slate-800">{site?.name ?? 'Select site'}</span>
          <span className="block text-[11px] text-slate-500">
            {site?.fillPct !== null && site?.fillPct !== undefined ? `${site.fillPct}% full · ` : ''}{site ? `${site.doorsOccupied}/${site.doorsTotal} docked` : ''}
          </span>
        </span>
        <ChevronDown className="h-4 w-4 text-slate-400" />
      </button>
      {open && (
        <div className="absolute left-0 top-full z-30 mt-2 max-h-80 w-72 overflow-auto rounded-xl border border-slate-200 bg-white p-1 shadow-xl">
          {sites.map(s => (
            <button key={s.id} onClick={() => { onChange(s.id); setOpen(false) }}
                    className={`flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left hover:bg-slate-50 ${s.id === current ? 'bg-blue-50' : ''}`}>
              <span className="rounded-md bg-blue-600/10 px-1.5 py-0.5 text-[10px] font-bold text-blue-700">{s.code}</span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[13px] font-medium text-slate-800">{s.name}</span>
                <span className="block text-[11px] text-slate-500">
                  {s.fillPct !== null ? `${s.fillPct}% full · ` : ''}{s.doorsOccupied}/{s.doorsTotal} docked
                </span>
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function Inspector({ snap, selection, onClose }: { snap: TwinSnapshot; selection: Selection; onClose: () => void }) {
  if (selection?.kind === 'truck') {
    const t = snap.trucks.find(x => x.id === selection.id)
    if (!t) return null
    const mins = minutesUntil(t.eta, new Date())
    return (
      <Glass className="w-[300px] p-4">
        <div className="mb-3 flex items-start justify-between gap-2">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-xl bg-blue-50 text-blue-600"><TruckIcon className="h-5 w-5" /></div>
            <div>
              <div className="text-[11px] font-semibold uppercase tracking-wide text-blue-600">{t.carrier ?? 'Carrier'}</div>
              <div className="text-[15px] font-semibold text-slate-900">{t.label ?? 'Truck'}</div>
              <div className="text-[11px] text-slate-500">{t.driverName ?? (t.source === 'TRAILER' ? 'Trailer record' : 'Appointment')}</div>
            </div>
          </div>
          <button onClick={onClose} className="rounded-md p-1 text-slate-400 hover:bg-slate-100" aria-label="Close"><X className="h-4 w-4" /></button>
        </div>
        <div className="mb-3 flex items-center gap-2">
          <Chip status={t.status} />
          <span className="text-[12px] text-slate-500">
            {t.dockDoorNumber ? `At ${t.dockDoorNumber}` : t.yardLocationCode ? `Yard ${t.yardLocationCode}` : mins !== null && mins > 0 ? `ETA ${mins} min` : 'At gate'}
          </span>
        </div>
        <div className="divide-y divide-slate-100">
          <Row label="Direction" value={t.direction === 'OUTBOUND' ? 'Outbound' : 'Inbound'} />
          <Row label="ETA" value={fmtTime(t.eta)} />
          <Row label="Arrived" value={fmtTime(t.arrivedAt)} />
          <Row label="Dock" value={t.dockDoorNumber ?? '—'} />
          <Row label="Cargo" value={`${t.palletCount ?? 0} pallets${t.weightKg ? ` · ${Math.round(t.weightKg).toLocaleString()} kg` : ''}`} />
          <Row label="PO numbers" value={t.poNumbers ?? '—'} />
        </div>
      </Glass>
    )
  }

  if (selection?.kind === 'dock') {
    const d = snap.docks.find(x => x.id === selection.id)
    if (!d) return null
    const truck = snap.trucks.find(t => t.dockDoorNumber === d.doorNumber)
    return (
      <Glass className="w-[300px] p-4">
        <div className="mb-3 flex items-start justify-between">
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wide text-blue-600">Dock door · {snap.site.code}</div>
            <div className="text-[15px] font-semibold text-slate-900">{d.doorNumber}</div>
          </div>
          <button onClick={onClose} className="rounded-md p-1 text-slate-400 hover:bg-slate-100" aria-label="Close"><X className="h-4 w-4" /></button>
        </div>
        <div className="mb-2"><Chip status={d.status} /></div>
        <div className="divide-y divide-slate-100">
          <Row label="Type" value={d.doorType ?? '—'} />
          <Row label="Vehicle" value={d.vehicleLabel ?? 'No truck assigned'} />
          <Row label="Carrier" value={truck?.carrier ?? '—'} />
          <Row label="Activity" value={truck ? <Chip status={truck.status} /> : '—'} />
        </div>
        <Link to="/yard-dock" className="mt-3 block rounded-lg bg-slate-900 px-3 py-2 text-center text-[12px] font-semibold text-white hover:bg-slate-800">
          Manage in Yard &amp; Dock
        </Link>
      </Glass>
    )
  }

  if (selection?.kind === 'equipment') {
    const e = snap.equipment.find(x => x.id === selection.id)
    if (!e) return null
    return (
      <Glass className="w-[300px] p-4">
        <div className="mb-3 flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-xl bg-amber-50 text-amber-600"><ForkliftIcon className="h-5 w-5" /></div>
            <div>
              <div className="text-[11px] font-semibold uppercase tracking-wide text-amber-600">{e.type ?? 'Equipment'}</div>
              <div className="text-[15px] font-semibold text-slate-900">{e.code}</div>
            </div>
          </div>
          <button onClick={onClose} className="rounded-md p-1 text-slate-400 hover:bg-slate-100" aria-label="Close"><X className="h-4 w-4" /></button>
        </div>
        <div className="mb-2"><Chip status={e.status} /></div>
        <div className="divide-y divide-slate-100">
          <Row label="Model" value={e.model ?? '—'} />
          <Row label="Assigned" value={e.assigned ? 'Yes' : 'No'} />
          <Row label="Battery" value={e.batteryLevel !== null ? `${e.batteryLevel}%` : '—'} />
        </div>
        {e.batteryLevel !== null && <div className="mt-2"><Progress value={e.batteryLevel} /></div>}
        <p className="mt-3 text-[11px] leading-snug text-slate-400">
          Position shown by assignment (no telemetry): busy lifts work at occupied doors, idle lifts are parked.
        </p>
      </Glass>
    )
  }

  // Default: the site card.
  const k = snap.kpis
  const lifts = snap.equipment.filter(isLift)
  const busyLifts = lifts.filter(l => l.assigned || ['IN_USE', 'ACTIVE', 'BUSY'].includes((l.status ?? '').toUpperCase()))
  return (
    <Glass className="w-[320px] p-4">
      <div className="mb-3 flex items-center gap-3">
        <div className="grid h-10 w-10 place-items-center rounded-xl bg-blue-600 text-white"><Warehouse className="h-5 w-5" /></div>
        <div className="min-w-0">
          <div className="text-[11px] font-semibold uppercase tracking-wide text-blue-600">{snap.site.type ?? 'Warehouse'} · {snap.site.code}</div>
          <div className="truncate text-[15px] font-semibold text-slate-900">{snap.site.name}</div>
          <div className="truncate text-[11px] text-slate-500">{snap.address || 'No address on file'}</div>
        </div>
      </div>
      <div className="mb-3 flex flex-wrap items-center gap-2 text-[12px] text-slate-500">
        <Chip status={snap.site.status ?? 'OPERATIONAL'} />
        <span>{snap.site.doorsOccupied} docked · {snap.trucks.filter(t => !t.dockDoorNumber).length} arriving/yard</span>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-xl bg-slate-50 p-2.5">
          <div className="text-[11px] text-slate-500">Space used</div>
          <div className="text-[15px] font-semibold text-slate-900">{snap.site.fillPct !== null ? `${snap.site.fillPct}%` : '—'}</div>
          {snap.site.fillPct !== null && <Progress value={snap.site.fillPct} tone="blue" />}
        </div>
        <div className="rounded-xl bg-slate-50 p-2.5">
          <div className="text-[11px] text-slate-500">Truck bays</div>
          <div className="text-[15px] font-semibold text-slate-900">{snap.site.doorsOccupied} / {snap.site.doorsTotal} busy</div>
          <Progress value={snap.site.doorsTotal ? (snap.site.doorsOccupied / snap.site.doorsTotal) * 100 : 0} />
        </div>
        <div className="rounded-xl bg-slate-50 p-2.5">
          <div className="text-[11px] text-slate-500">Outbound today</div>
          <div className="text-[15px] font-semibold text-slate-900">{k.outboundShipmentsToday} shipments</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-2.5">
          <div className="text-[11px] text-slate-500">Arrivals today</div>
          <div className="text-[15px] font-semibold text-slate-900">{k.arrivalsToday} trucks</div>
        </div>
      </div>
      <div className="mt-4">
        <div className="mb-1 flex items-center justify-between text-[12px] font-semibold text-slate-700">
          <span>Inventory</span><span className="font-normal text-slate-400">available units</span>
        </div>
        {snap.topInventory.length === 0 && <p className="py-2 text-[12px] text-slate-400">No stock recorded at this site yet.</p>}
        {snap.topInventory.map(line => (
          <div key={line.sku} className="flex items-center justify-between gap-2 py-1.5 text-[13px]">
            <span className="flex min-w-0 items-center gap-2">
              <Box className="h-4 w-4 shrink-0 text-amber-500" />
              <span className="truncate text-slate-700">{line.sku}</span>
            </span>
            <span className="flex items-center gap-2">
              <span className="font-semibold tabular-nums text-slate-800">{line.available.toLocaleString()}</span>
              <Chip status={line.stockStatus} label={line.stockStatus === 'IN_STOCK' ? 'In stock' : line.stockStatus === 'LOW_STOCK' ? 'Low stock' : 'Out'} />
            </span>
          </div>
        ))}
      </div>
      {lifts.length > 0 && (
        <div className="mt-3 border-t border-slate-100 pt-3">
          <div className="mb-1 flex items-center justify-between text-[12px] font-semibold text-slate-700">
            <span>Forklift fleet</span><span className="font-normal text-slate-400">{busyLifts.length}/{lifts.length} working</span>
          </div>
          {busyLifts.slice(0, 2).map(l => (
            <div key={l.id} className="flex items-center justify-between gap-2 py-1 text-[12px]">
              <span className="font-semibold text-slate-700">{l.code}</span>
              <span className="flex-1 truncate text-slate-500">{l.status}</span>
              {l.batteryLevel !== null && <span className="w-16"><Progress value={l.batteryLevel} /></span>}
            </div>
          ))}
        </div>
      )}
    </Glass>
  )
}

const APPOINTMENT_STEPS = ['Scheduled', 'Arrived', 'At dock', 'Completed']
const SHIPMENT_STEPS = ['Created', 'Shipped', 'In transit', 'Delivered']

function stepFromTruck(t: TwinTruck): number {
  const s = (t.status ?? '').toUpperCase()
  if (s === 'COMPLETED') return 3
  if (t.dockDoorNumber && (s === 'IN_PROGRESS' || t.source === 'TRAILER')) return 2
  if (s === 'CHECKED_IN' || t.arrivedAt) return 1
  return 0
}

function Tracker({ snap, selection }: { snap: TwinSnapshot; selection: Selection }) {
  const truck = selection?.kind === 'truck' ? snap.trucks.find(t => t.id === selection.id) : undefined
  const shipment = snap.shipments[0]
  let title = 'Shipment tracking'
  let ref = ''
  let steps = SHIPMENT_STEPS
  let current = -1
  let times: (string | null)[] = []
  let detail: React.ReactNode = <span className="text-[12px] text-slate-400">No shipments from this site yet.</span>

  if (truck) {
    title = 'Truck tracking'
    ref = `${truck.label ?? ''} · ${truck.carrier ?? ''}`
    steps = APPOINTMENT_STEPS
    current = stepFromTruck(truck)
    times = [truck.eta, truck.arrivedAt, null, null]
    detail = (
      <div className="text-[12px] text-slate-600">
        <div className="font-semibold text-slate-800">{truck.direction === 'OUTBOUND' ? 'Outbound load' : 'Inbound delivery'}</div>
        <div>{truck.palletCount ?? 0} pallets{truck.dockDoorNumber ? ` · ${truck.dockDoorNumber}` : ''}</div>
        <div className="mt-1"><Chip status={truck.status} /></div>
      </div>
    )
  } else if (shipment) {
    const s = (shipment.status ?? '').toUpperCase()
    current = s === 'DELIVERED' ? 3 : ['IN_TRANSIT', 'OUT_FOR_DELIVERY'].includes(s) ? 2 : shipment.shippedAt ? 1 : 0
    ref = shipment.trackingNumber ?? shipment.id.slice(0, 8)
    times = [shipment.createdAt, shipment.shippedAt, null, shipment.actualDelivery ?? shipment.estimatedDelivery]
    detail = (
      <div className="text-[12px] text-slate-600">
        <div className="font-semibold text-slate-800">{shipment.carrier ?? 'Carrier'} {shipment.serviceLevel ? `· ${shipment.serviceLevel}` : ''}</div>
        <div>ETA {shipment.estimatedDelivery ? new Date(shipment.estimatedDelivery).toLocaleDateString() : '—'}</div>
        <div className="mt-1"><Chip status={shipment.status} /></div>
      </div>
    )
  }

  return (
    <Glass className="flex w-full max-w-[720px] flex-wrap items-center gap-4 px-5 py-4">
      <div className="min-w-[380px] flex-1">
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2 text-[14px] font-semibold text-slate-800">
            <TruckIcon className="h-4 w-4 text-blue-600" /> {title}
          </div>
          <span className="text-[11px] text-slate-400">{ref}</span>
        </div>
        <div className="flex items-start">
          {steps.map((label, i) => (
            <div key={label} className="flex flex-1 flex-col items-center text-center">
              <div className="flex w-full items-center">
                <div className={`h-0.5 flex-1 ${i === 0 ? 'opacity-0' : i <= current ? 'bg-blue-600' : 'bg-slate-200'}`} />
                <div className={`grid h-7 w-7 place-items-center rounded-full text-white ${i < current ? 'bg-blue-600' : i === current ? 'bg-blue-600 ring-4 ring-blue-100' : 'bg-slate-200 text-slate-400'}`}>
                  {i < current ? <PackageCheck className="h-3.5 w-3.5" /> : <span className="text-[10px] font-bold">{i + 1}</span>}
                </div>
                <div className={`h-0.5 flex-1 ${i === steps.length - 1 ? 'opacity-0' : i < current ? 'bg-blue-600' : 'bg-slate-200'}`} />
              </div>
              <div className={`mt-1.5 text-[11px] font-medium ${i <= current ? 'text-slate-800' : 'text-slate-400'}`}>{label}</div>
              <div className="text-[10px] text-slate-400">{fmtTime(times[i] ?? null)}</div>
            </div>
          ))}
        </div>
      </div>
      <div className="min-w-[180px] rounded-xl bg-slate-50 p-3">{detail}</div>
    </Glass>
  )
}

function OpsTabs({ snap, onSelect, selection }: { snap: TwinSnapshot; onSelect: (s: Selection) => void; selection: Selection }) {
  const [tab, setTab] = useState<'docks' | 'lifts' | 'trucks'>('docks')
  const lifts = snap.equipment.filter(isLift)
  const busy = lifts.filter(l => l.assigned || ['IN_USE', 'ACTIVE', 'BUSY'].includes((l.status ?? '').toUpperCase()))
  const tabs = [
    { id: 'docks' as const, label: 'Docks', count: `${snap.site.doorsOccupied}/${snap.site.doorsTotal}` },
    { id: 'lifts' as const, label: 'Forklifts', count: `${busy.length}/${lifts.length}` },
    { id: 'trucks' as const, label: 'Trucks', count: String(snap.trucks.length) },
  ]
  const isSel = (kind: string, id: string) => selection && 'id' in selection && selection.kind === kind && selection.id === id
  return (
    <Glass className="w-[380px] p-3">
      <div className="mb-2 flex items-center gap-1">
        {tabs.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)}
                  className={`rounded-lg px-2.5 py-1 text-[12px] font-semibold transition ${tab === t.id ? 'bg-white text-slate-900 shadow-sm ring-1 ring-slate-200' : 'text-slate-500 hover:text-slate-800'}`}>
            {t.label} <span className="text-slate-400">{t.count}</span>
          </button>
        ))}
        <span className="ml-auto truncate text-[11px] text-slate-400">{snap.site.name}</span>
      </div>
      <div className="max-h-44 overflow-auto">
        {tab === 'docks' && (snap.docks.length === 0
          ? <Empty text="No dock doors configured." link="/yard-dock" cta="Add doors" />
          : snap.docks.map(d => {
            const truck = snap.trucks.find(t => t.dockDoorNumber === d.doorNumber)
            return (
              <button key={d.id} onClick={() => onSelect({ kind: 'dock', id: d.id })}
                      className={`grid w-full grid-cols-[64px_1fr_auto] items-center gap-2 rounded-lg px-2 py-1.5 text-left text-[12px] hover:bg-slate-50 ${isSel('dock', d.id) ? 'bg-blue-50' : ''}`}>
                <span className="font-semibold text-slate-800">{d.doorNumber}</span>
                <span className="truncate text-slate-600">{d.vehicleLabel ? `● ${d.vehicleLabel}${truck?.carrier ? ` · ${truck.carrier}` : ''}` : 'No truck assigned'}</span>
                <Chip status={truck?.status ?? d.status} />
              </button>
            )
          }))}
        {tab === 'lifts' && (lifts.length === 0
          ? <Empty text="No forklifts registered at this site." link="/warehouse" cta="Add equipment" />
          : lifts.map(l => (
            <button key={l.id} onClick={() => onSelect({ kind: 'equipment', id: l.id })}
                    className={`grid w-full grid-cols-[64px_1fr_auto] items-center gap-2 rounded-lg px-2 py-1.5 text-left text-[12px] hover:bg-slate-50 ${isSel('equipment', l.id) ? 'bg-blue-50' : ''}`}>
              <span className="font-semibold text-slate-800">{l.code}</span>
              <span className="truncate text-slate-600">{l.model ?? l.type}{l.batteryLevel !== null ? ` · ${l.batteryLevel}%` : ''}</span>
              <Chip status={l.status} />
            </button>
          )))}
        {tab === 'trucks' && (snap.trucks.length === 0
          ? <Empty text="No trucks scheduled or on site." link="/yard-dock" cta="Book appointment" />
          : snap.trucks.map(t => {
            const mins = minutesUntil(t.eta, new Date())
            return (
              <button key={t.id} onClick={() => onSelect({ kind: 'truck', id: t.id })}
                      className={`grid w-full grid-cols-[84px_1fr_auto_44px] items-center gap-2 rounded-lg px-2 py-1.5 text-left text-[12px] hover:bg-slate-50 ${isSel('truck', t.id) ? 'bg-blue-50' : ''}`}>
                <span className="leading-tight">
                  <span className="block font-semibold text-slate-800">{t.label}</span>
                  <span className="block text-[10px] text-slate-400">{t.carrier}</span>
                </span>
                <span className="truncate text-slate-600">{t.dockDoorNumber ? `${snap.site.code} · ${t.dockDoorNumber}` : `To ${snap.site.code}`}</span>
                <Chip status={t.status} />
                <span className="text-right text-[11px] text-slate-400">{mins !== null && mins > 0 ? `${mins} min` : ''}</span>
              </button>
            )
          }))}
      </div>
    </Glass>
  )
}

function Empty({ text, link, cta }: { text: string; link: string; cta: string }) {
  return (
    <div className="flex items-center justify-between gap-2 px-2 py-3 text-[12px] text-slate-500">
      <span>{text}</span>
      <Link to={link} className="font-semibold text-blue-600 hover:underline">{cta}</Link>
    </div>
  )
}

function MapControls({ scene }: { scene: React.RefObject<SceneHandle> }) {
  const btn = 'grid h-9 w-9 place-items-center text-slate-600 transition hover:bg-slate-50 hover:text-slate-900'
  return (
    <Glass className="flex flex-col overflow-hidden p-0">
      <button className={btn} aria-label="Zoom in" onClick={() => scene.current?.zoom(0.8)}><Plus className="h-4 w-4" /></button>
      <button className={btn} aria-label="Zoom out" onClick={() => scene.current?.zoom(1.25)}><Minus className="h-4 w-4" /></button>
      <div className="mx-2 h-px bg-slate-100" />
      <button className={btn} aria-label="Rotate left" onClick={() => scene.current?.rotate(Math.PI / 8)}><RotateCcw className="h-4 w-4" /></button>
      <button className={btn} aria-label="Rotate right" onClick={() => scene.current?.rotate(-Math.PI / 8)}><RotateCw className="h-4 w-4" /></button>
      <div className="mx-2 h-px bg-slate-100" />
      <button className={btn} aria-label="Reset view" onClick={() => scene.current?.reset()}><Home className="h-4 w-4" /></button>
    </Glass>
  )
}

// ------------------------------------------------------------------ page

export default function WarehouseTwinPage() {
  const [params, setParams] = useSearchParams()
  const [selection, setSelection] = useState<Selection>(null)
  const [query, setQuery] = useState('')
  const searchRef = useRef<HTMLInputElement>(null)
  const sceneRef = useRef<SceneHandle>(null)
  const reducedMotion = useMemo(() => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches, [])

  const sitesQuery = useQuery({ queryKey: ['twin', 'sites'], queryFn: fetchTwinSites, refetchInterval: 60_000 })
  const siteId = params.get('site') ?? sitesQuery.data?.[0]?.id ?? null

  const snapQuery = useQuery({
    queryKey: ['twin', 'warehouse', siteId],
    queryFn: () => fetchTwinSnapshot(siteId!),
    enabled: Boolean(siteId),
    refetchInterval: REFRESH_MS,
    refetchIntervalInBackground: false,
    placeholderData: prev => prev,
  })
  const snap = snapQuery.data
  const layout = useMemo(() => (snap ? buildLayout(snap) : null), [snap])

  // "/" focuses search, Esc clears selection.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = (e.target as HTMLElement)?.tagName === 'INPUT'
      if (e.key === '/' && !typing) { e.preventDefault(); searchRef.current?.focus() }
      if (e.key === 'Escape') { setSelection(null); searchRef.current?.blur() }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const results = useMemo(() => {
    if (!snap || query.trim().length < 1) return []
    const q = query.toLowerCase()
    const out: { label: string; sub: string; sel: Selection }[] = []
    snap.trucks.forEach(t => { if (`${t.label} ${t.carrier} ${t.poNumbers}`.toLowerCase().includes(q)) out.push({ label: t.label ?? 'Truck', sub: `Truck · ${t.carrier ?? ''}`, sel: { kind: 'truck', id: t.id } }) })
    snap.docks.forEach(d => { if (`${d.doorNumber} ${d.vehicleLabel}`.toLowerCase().includes(q)) out.push({ label: d.doorNumber ?? 'Door', sub: 'Dock door', sel: { kind: 'dock', id: d.id } }) })
    snap.equipment.forEach(e => { if (`${e.code} ${e.type} ${e.model}`.toLowerCase().includes(q)) out.push({ label: e.code ?? 'Equipment', sub: e.type ?? 'Equipment', sel: { kind: 'equipment', id: e.id } }) })
    snap.topInventory.forEach(s => { if (s.sku.toLowerCase().includes(q)) out.push({ label: s.sku, sub: `${s.available} available`, sel: { kind: 'site' } }) })
    return out.slice(0, 8)
  }, [snap, query])

  const sites = sitesQuery.data ?? []

  if (sitesQuery.isSuccess && sites.length === 0) {
    return (
      <div className="grid h-full min-h-[560px] place-items-center bg-[#eef2f8] p-6">
        <Glass className="max-w-md p-8 text-center">
          <div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-2xl bg-blue-600 text-white"><Building2 className="h-7 w-7" /></div>
          <h1 className="text-lg font-semibold text-slate-900">Your warehouse twin starts with a site</h1>
          <p className="mt-2 text-[13px] text-slate-500">Add a warehouse with its dock doors and equipment, and it will appear here live.</p>
          <Link to="/warehouse" className="mt-5 inline-block rounded-lg bg-blue-600 px-4 py-2 text-[13px] font-semibold text-white hover:bg-blue-700">Add a warehouse</Link>
        </Glass>
      </div>
    )
  }

  return (
    <div className="relative h-full min-h-[560px] w-full overflow-hidden bg-[#eef2f8]">
      {/* 3D scene */}
      <div className="absolute inset-0">
        {layout && snap ? (
          <Suspense fallback={<SceneLoading />}>
            <WarehouseScene ref={sceneRef} layout={layout} siteCode={snap.site.code ?? ''}
                            selection={selection} onSelect={setSelection} reducedMotion={reducedMotion} />
          </Suspense>
        ) : (
          <SceneLoading error={snapQuery.error instanceof Error ? snapQuery.error.message : sitesQuery.error instanceof Error ? sitesQuery.error.message : null} />
        )}
      </div>

      {/* Top bar */}
      <div className="pointer-events-none absolute inset-x-0 top-0 z-40 flex flex-wrap items-center gap-3 p-4">
        <div className="pointer-events-auto flex items-center gap-2">
          <div className="grid h-9 w-9 place-items-center rounded-xl bg-blue-600 text-white shadow"><Warehouse className="h-5 w-5" /></div>
          <div className="leading-tight">
            <div className="text-[15px] font-bold tracking-tight text-slate-900">Warehouse Twin</div>
            <div className="text-[11px] text-slate-500">Live operations view</div>
          </div>
        </div>
        <div className="pointer-events-auto relative ml-2 w-full max-w-sm flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input ref={searchRef} value={query} onChange={e => setQuery(e.target.value)}
                 onKeyDown={e => { if (e.key === 'Enter' && results[0]) { setSelection(results[0].sel); setQuery('') } }}
                 placeholder="Search trucks, doors, forklifts, SKUs…"
                 className="w-full rounded-xl border border-slate-200 bg-white/90 py-2 pl-9 pr-8 text-[13px] text-slate-800 shadow-sm outline-none placeholder:text-slate-400 focus:border-blue-400 focus:ring-4 focus:ring-blue-100" />
          <kbd className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded border border-slate-200 px-1.5 text-[10px] text-slate-400">/</kbd>
          {results.length > 0 && (
            <div className="absolute left-0 right-0 top-full z-30 mt-2 rounded-xl border border-slate-200 bg-white p-1 shadow-xl">
              {results.map((r, i) => (
                <button key={i} onClick={() => { setSelection(r.sel); setQuery('') }}
                        className="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-[13px] hover:bg-slate-50">
                  <span className="font-medium text-slate-800">{r.label}</span>
                  <span className="text-[11px] text-slate-400">{r.sub}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="pointer-events-auto ml-auto flex items-center gap-3">
          <SiteSwitcher sites={sites} current={siteId}
                        onChange={id => { setSelection(null); setParams({ site: id }) }} />
          <LivePill updatedAt={snapQuery.dataUpdatedAt} failing={snapQuery.isError} />
        </div>
      </div>

      {snap && (
        <>
          <div className="pointer-events-none absolute left-4 top-20 z-30"><div className="pointer-events-auto"><KpiTiles snap={snap} /></div></div>
          <div className="absolute right-4 top-20 z-30 flex items-start gap-3">
            <MapControls scene={sceneRef} />
            <div className="max-h-[calc(100vh-25rem)] overflow-y-auto rounded-2xl [scrollbar-width:thin]">
              <Inspector snap={snap} selection={selection} onClose={() => setSelection(null)} />
            </div>
          </div>
          <div className="absolute bottom-4 left-4 right-[410px] z-30"><Tracker snap={snap} selection={selection} /></div>
          <div className="absolute bottom-4 right-4 z-30"><OpsTabs snap={snap} onSelect={setSelection} selection={selection} /></div>
        </>
      )}
    </div>
  )
}

function SceneLoading({ error }: { error?: string | null }) {
  return (
    <div className="grid h-full w-full place-items-center">
      {error ? (
        <Glass className="max-w-sm p-6 text-center">
          <div className="text-[14px] font-semibold text-slate-800">Couldn't load the warehouse</div>
          <div className="mt-1 text-[12px] text-slate-500">{error}</div>
        </Glass>
      ) : (
        <div className="flex items-center gap-3 text-[13px] text-slate-500">
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
          Building your warehouse…
        </div>
      )}
    </div>
  )
}
