import { Suspense, lazy, useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  AlertTriangle, Coins, GitFork, Home, ListOrdered, Minus, Network, Plus, Route, RotateCcw, RotateCw,
  Search, ShoppingBag, Store, Target, Warehouse, X,
} from 'lucide-react'
import { fetchNetworkSnapshot, type NetworkSnapshot, type OrderView } from './api'
import { buildNetworkLayout, channelColor, regionLabel } from './layout'
import { Chip, Glass, LivePill, Progress, Row } from '../ui'
import { fmtNumber, fmtTime } from '../format'
import type { FlowSceneHandle } from './OrderFlowScene'

const OrderFlowScene = lazy(() => import('./OrderFlowScene').then(m => ({ default: m.OrderFlowScene })))

const REFRESH_MS = 15_000
const WINDOWS = [{ h: 1, label: '1h' }, { h: 6, label: '6h' }, { h: 24, label: '24h' }, { h: 168, label: '7d' }]

function money(n: number | null | undefined, currency = 'USD'): string {
  if (n === null || n === undefined) return '—'
  return new Intl.NumberFormat(undefined, { style: 'currency', currency, maximumFractionDigits: n >= 1000 ? 0 : 2 }).format(n)
}

function pct(n: number | null | undefined): string {
  return n === null || n === undefined ? '—' : `${n}%`
}

// ------------------------------------------------------------------ panels

function KpiTiles({ snap }: { snap: NetworkSnapshot }) {
  const k = snap.kpis
  const tiles = [
    { icon: <ShoppingBag className="h-5 w-5" />, label: 'Orders', value: fmtNumber(k.orders), sub: `${money(k.revenue)} revenue` },
    { icon: <Route className="h-5 w-5" />, label: 'Routed', value: pct(k.routedPct), sub: `${k.backorders} backordered · ${k.queueDepth} queued` },
    { icon: <GitFork className="h-5 w-5" />, label: 'Split rate', value: pct(k.splitRatePct), sub: 'of routed orders, >1 node' },
    { icon: <Coins className="h-5 w-5" />, label: 'Cost / order', value: money(k.avgEstimatedCostPerOrder), sub: 'allocator estimate' },
    { icon: <Target className="h-5 w-5" />, label: 'Promise confidence', value: pct(k.avgPromiseConfidencePct), sub: `${k.openExceptions} open exceptions` },
  ]
  return (
    <div className="flex flex-wrap gap-3">
      {tiles.map(t => (
        <Glass key={t.label} className="flex min-w-[176px] items-center gap-3 px-4 py-3">
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

function AllocationExplanation({ order }: { order: OrderView }) {
  if (order.allocations.length === 0) {
    return <p className="text-[12px] text-slate-500">Not routed yet{order.status ? ` (${order.status.toLowerCase().replace(/_/g, ' ')})` : ''}.</p>
  }
  return (
    <div className="space-y-2">
      {order.allocations.map((a, i) => (
        <div key={i} className="rounded-xl bg-slate-50 p-3">
          <div className="mb-1 flex items-center justify-between">
            <span className="text-[13px] font-semibold text-slate-800">{a.nodeName ?? 'Node'}</span>
            <Chip status={a.status} />
          </div>
          <div className="text-[12px] leading-relaxed text-slate-600">
            {a.ruleName ? <>Rule <span className="font-medium text-slate-800">“{a.ruleName}”</span>{a.strategy ? ` · ${a.strategy.toLowerCase().replace(/_/g, ' ')}` : ''}<br /></> : null}
            {a.quantityAllocated ?? 0}/{a.quantityRequested ?? a.quantityAllocated ?? 0} units
            {a.estimatedDistanceKm !== null ? ` · ~${Math.round(a.estimatedDistanceKm)} km` : ''}
            {a.estimatedCost !== null ? ` · ~${money(a.estimatedCost)}` : ''}
            {a.confidence !== null ? ` · ${Math.round(a.confidence * 100)}% confidence` : ''}
            {a.promiseDate ? <><br />Promise {new Date(a.promiseDate).toLocaleDateString()}</> : null}
          </div>
        </div>
      ))}
      <p className="text-[11px] text-slate-400">Distance and cost are the allocator's estimates, not carrier quotes.</p>
    </div>
  )
}

function Inspector({ snap, selectedKey, onClose }: { snap: NetworkSnapshot; selectedKey: string | null; onClose: () => void }) {
  const close = <button onClick={onClose} className="rounded-md p-1 text-slate-400 hover:bg-slate-100" aria-label="Close"><X className="h-4 w-4" /></button>

  if (selectedKey?.startsWith('order:')) {
    const o = snap.recentOrders.find(x => `order:${x.id}` === selectedKey)
    if (!o) return null
    return (
      <Glass className="w-[320px] p-4">
        <div className="mb-3 flex items-start justify-between">
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wide" style={{ color: channelColor(o.channel) }}>{o.channel}</div>
            <div className="text-[15px] font-semibold text-slate-900">{o.reference}</div>
            <div className="text-[11px] text-slate-500">{o.city ? `${o.city}, ` : ''}{o.region}</div>
          </div>
          {close}
        </div>
        <div className="mb-3 flex items-center gap-2"><Chip status={o.status} /><span className="text-[13px] font-semibold text-slate-800">{money(o.total, o.currency ?? 'USD')}</span></div>
        <div className="mb-3 divide-y divide-slate-100">
          <Row label="Created" value={fmtTime(o.createdAt)} />
          <Row label="Shipped" value={fmtTime(o.shippedAt)} />
          <Row label="Delivered" value={fmtTime(o.deliveredAt)} />
        </div>
        <div className="mb-2 text-[12px] font-semibold text-slate-700">Why it was routed here</div>
        <AllocationExplanation order={o} />
        <Link to={`/orders/${o.id}`} className="mt-3 block rounded-lg bg-slate-900 px-3 py-2 text-center text-[12px] font-semibold text-white hover:bg-slate-800">Open order</Link>
      </Glass>
    )
  }

  if (selectedKey?.startsWith('node:')) {
    const n = snap.nodes.find(x => `node:${x.id}` === selectedKey)
    if (!n) return null
    const inbound = snap.flows.filter(f => f.to === selectedKey)
    const outbound = snap.flows.filter(f => f.from === selectedKey).sort((a, b) => b.orders - a.orders)
    const isStore = (n.type ?? '').toUpperCase().includes('STORE')
    return (
      <Glass className="w-[320px] p-4">
        <div className="mb-3 flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-xl bg-blue-600 text-white">{isStore ? <Store className="h-5 w-5" /> : <Warehouse className="h-5 w-5" />}</div>
            <div>
              <div className="text-[11px] font-semibold uppercase tracking-wide text-blue-600">{(n.type ?? 'Node').replace(/_/g, ' ')} · {n.code}</div>
              <div className="text-[15px] font-semibold text-slate-900">{n.name}</div>
              <div className="text-[11px] text-slate-500">{[n.city, n.state].filter(Boolean).join(', ')}</div>
            </div>
          </div>
          {close}
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-xl bg-slate-50 p-2.5"><div className="text-[11px] text-slate-500">Allocations</div><div className="text-[15px] font-semibold">{n.allocations}</div></div>
          <div className="rounded-xl bg-slate-50 p-2.5"><div className="text-[11px] text-slate-500">Units allocated</div><div className="text-[15px] font-semibold">{fmtNumber(n.unitsAllocated)}</div></div>
          <div className="rounded-xl bg-slate-50 p-2.5"><div className="text-[11px] text-slate-500">Available (ATP)</div><div className="text-[15px] font-semibold">{fmtNumber(n.availableUnits)}</div><div className="text-[10px] text-slate-400">{n.skuCount} SKUs</div></div>
          <div className="rounded-xl bg-slate-50 p-2.5"><div className="text-[11px] text-slate-500">Space used</div><div className="text-[15px] font-semibold">{pct(n.fillPct)}</div>{n.fillPct !== null && <Progress value={n.fillPct} tone="blue" />}</div>
        </div>
        <div className="mt-3 text-[12px] font-semibold text-slate-700">Demand from</div>
        {inbound.length === 0 && <p className="text-[12px] text-slate-400">No orders routed here in this window.</p>}
        {inbound.map(f => (
          <div key={f.from} className="flex items-center justify-between py-1 text-[12px]">
            <span className="flex items-center gap-2"><span className="h-2 w-2 rounded-full" style={{ background: channelColor(f.from.replace('channel:', '')) }} />{f.from.replace('channel:', '')}</span>
            <span className="font-semibold tabular-nums">{f.orders}</span>
          </div>
        ))}
        <div className="mt-2 text-[12px] font-semibold text-slate-700">Ships to</div>
        {outbound.slice(0, 6).map(f => (
          <div key={f.to} className="flex items-center justify-between py-1 text-[12px]"><span>{regionLabel(f.to)}</span><span className="font-semibold tabular-nums">{f.orders}</span></div>
        ))}
        <Link to="/order-routing" className="mt-3 block rounded-lg bg-slate-900 px-3 py-2 text-center text-[12px] font-semibold text-white hover:bg-slate-800">Routing rules</Link>
      </Glass>
    )
  }

  if (selectedKey?.startsWith('channel:') || selectedKey?.startsWith('region:')) {
    const isChannel = selectedKey.startsWith('channel:')
    const name = isChannel ? selectedKey.replace('channel:', '') : regionLabel(selectedKey)
    const ch = snap.channels.find(c => c.id === selectedKey)
    const flows = snap.flows.filter(f => (isChannel ? f.from : f.to) === selectedKey).sort((a, b) => b.orders - a.orders)
    const nodeName = (key: string) => snap.nodes.find(n => `node:${n.id}` === key)?.code ?? key
    const orders = snap.recentOrders.filter(o => (isChannel ? o.channel === name : o.region === name)).slice(0, 5)
    const count = isChannel ? ch?.orders : snap.regions.find(r => r.id === selectedKey)?.orders
    return (
      <Glass className="w-[320px] p-4">
        <div className="mb-3 flex items-start justify-between">
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wide text-blue-600">{isChannel ? 'Sales channel' : 'Ship-to region'}</div>
            <div className="text-[15px] font-semibold" style={isChannel ? { color: channelColor(name) } : undefined}>{name}</div>
          </div>
          {close}
        </div>
        <div className="mb-3 grid grid-cols-2 gap-3">
          <div className="rounded-xl bg-slate-50 p-2.5"><div className="text-[11px] text-slate-500">Orders</div><div className="text-[15px] font-semibold">{count ?? 0}</div></div>
          {isChannel && <div className="rounded-xl bg-slate-50 p-2.5"><div className="text-[11px] text-slate-500">Revenue</div><div className="text-[15px] font-semibold">{money(ch?.revenue)}</div></div>}
        </div>
        <div className="text-[12px] font-semibold text-slate-700">{isChannel ? 'Fulfilled from' : 'Shipped from'}</div>
        {flows.length === 0 && <p className="py-1 text-[12px] text-slate-400">No routed orders yet.</p>}
        {flows.map(f => {
          const key = isChannel ? f.to : f.from
          return <div key={key} className="flex items-center justify-between py-1 text-[12px]"><span>{nodeName(key)}</span><span className="font-semibold tabular-nums">{f.orders}</span></div>
        })}
        {orders.length > 0 && <div className="mt-2 text-[12px] font-semibold text-slate-700">Latest orders</div>}
        {orders.map(o => (
          <div key={o.id} className="flex items-center justify-between py-1 text-[12px]"><span className="truncate">{o.reference}</span><Chip status={o.status} /></div>
        ))}
      </Glass>
    )
  }

  // Default: network summary.
  const total = Math.max(1, snap.lifecycle.reduce((a, s) => a + s.orders, 0))
  return (
    <Glass className="w-[320px] p-4">
      <div className="mb-3 flex items-center gap-3">
        <div className="grid h-10 w-10 place-items-center rounded-xl bg-blue-600 text-white"><Network className="h-5 w-5" /></div>
        <div>
          <div className="text-[11px] font-semibold uppercase tracking-wide text-blue-600">Order network · last {snap.windowHours >= 168 ? '7 days' : `${snap.windowHours}h`}</div>
          <div className="text-[15px] font-semibold text-slate-900">{snap.channels.length} channels → {snap.nodes.length} nodes → {snap.regions.length} regions</div>
        </div>
      </div>
      <div className="text-[12px] font-semibold text-slate-700">Status mix</div>
      {snap.lifecycle.length === 0 && <p className="py-1 text-[12px] text-slate-400">No orders in this window.</p>}
      {[...snap.lifecycle].sort((a, b) => b.orders - a.orders).map(s => (
        <div key={s.status} className="py-1">
          <div className="flex items-center justify-between text-[12px]"><Chip status={s.status} /><span className="font-semibold tabular-nums">{s.orders}</span></div>
          <div className="mt-1"><Progress value={(s.orders / total) * 100} tone="blue" /></div>
        </div>
      ))}
      <div className="mt-3 text-[12px] font-semibold text-slate-700">Busiest nodes</div>
      {snap.nodes.slice(0, 4).map(n => (
        <div key={n.id} className="flex items-center justify-between py-1 text-[12px]"><span>{n.code} <span className="text-slate-400">{n.name}</span></span><span className="font-semibold tabular-nums">{n.allocations}</span></div>
      ))}
    </Glass>
  )
}

const PIPELINE = [
  { label: 'Pending', statuses: ['PENDING', 'CONFIRMED', 'APPROVED', 'PARKED'] },
  { label: 'Routed', statuses: ['ALLOCATED', 'PARTIALLY_ALLOCATED'] },
  { label: 'Shipped', statuses: ['SHIPPED', 'IN_TRANSIT', 'FULFILLED'] },
  { label: 'Delivered', statuses: ['DELIVERED'] },
]

function Pipeline({ snap }: { snap: NetworkSnapshot }) {
  const count = (statuses: string[]) => snap.lifecycle.filter(s => statuses.includes(s.status)).reduce((a, s) => a + s.orders, 0)
  const backordered = count(['FAILED', 'PARTIALLY_ALLOCATED', 'BACKORDERED'])
  const cancelled = count(['CANCELLED', 'REJECTED'])
  const max = Math.max(1, ...PIPELINE.map(p => count(p.statuses)))
  return (
    <Glass className="flex w-full max-w-[720px] flex-wrap items-center gap-5 px-5 py-4">
      <div className="min-w-[380px] flex-1">
        <div className="mb-3 flex items-center gap-2 text-[14px] font-semibold text-slate-800"><ListOrdered className="h-4 w-4 text-blue-600" /> Order lifecycle</div>
        <div className="flex items-end gap-2">
          {PIPELINE.map((p, i) => {
            const c = count(p.statuses)
            return (
              <div key={p.label} className="flex flex-1 items-end gap-2">
                <div className="flex-1">
                  <div className="text-[18px] font-semibold tabular-nums text-slate-900">{c}</div>
                  <div className="mb-1 text-[11px] font-medium text-slate-500">{p.label}</div>
                  <div className="h-1.5 rounded-full bg-slate-100"><div className="h-full rounded-full bg-blue-600" style={{ width: `${(c / max) * 100}%` }} /></div>
                </div>
                {i < PIPELINE.length - 1 && <span className="mb-1 text-slate-300">→</span>}
              </div>
            )
          })}
        </div>
      </div>
      <div className="flex gap-3">
        <div className="rounded-xl bg-amber-50 px-3 py-2"><div className="text-[18px] font-semibold text-amber-700">{backordered}</div><div className="text-[11px] text-amber-700/80">Backordered</div></div>
        <div className="rounded-xl bg-slate-100 px-3 py-2"><div className="text-[18px] font-semibold text-slate-700">{cancelled}</div><div className="text-[11px] text-slate-500">Cancelled</div></div>
      </div>
    </Glass>
  )
}

function OpsTabs({ snap, onSelect, selectedKey }: { snap: NetworkSnapshot; onSelect: (k: string) => void; selectedKey: string | null }) {
  const [tab, setTab] = useState<'orders' | 'queue' | 'exceptions'>('orders')
  const tabs = [
    { id: 'orders' as const, label: 'Orders', count: snap.recentOrders.length },
    { id: 'queue' as const, label: 'Queue', count: snap.kpis.queueDepth },
    { id: 'exceptions' as const, label: 'Exceptions', count: snap.kpis.openExceptions },
  ]
  const orderIdx = new Map(snap.recentOrders.map(o => [o.id, o]))
  const pick = (orderId: string) => { if (orderIdx.has(orderId)) onSelect(`order:${orderId}`) }
  return (
    <Glass className="w-[400px] p-3">
      <div className="mb-2 flex items-center gap-1">
        {tabs.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)}
                  className={`rounded-lg px-2.5 py-1 text-[12px] font-semibold transition ${tab === t.id ? 'bg-white text-slate-900 shadow-sm ring-1 ring-slate-200' : 'text-slate-500 hover:text-slate-800'}`}>
            {t.label} <span className="text-slate-400">{t.count}</span>
          </button>
        ))}
      </div>
      <div className="max-h-48 overflow-auto">
        {tab === 'orders' && (snap.recentOrders.length === 0 ? <p className="px-2 py-3 text-[12px] text-slate-500">No orders in this window.</p> :
          snap.recentOrders.map(o => (
            <button key={o.id} onClick={() => onSelect(`order:${o.id}`)}
                    className={`grid w-full grid-cols-[1fr_auto_auto] items-center gap-2 rounded-lg px-2 py-1.5 text-left text-[12px] hover:bg-slate-50 ${selectedKey === `order:${o.id}` ? 'bg-blue-50' : ''}`}>
              <span className="min-w-0 leading-tight">
                <span className="block truncate font-semibold text-slate-800">{o.reference}</span>
                <span className="block text-[10px] text-slate-400">{o.channel} → {o.allocations.map(a => a.nodeName).filter(Boolean).join(' + ') || '—'} → {o.region}</span>
              </span>
              <span className="tabular-nums text-slate-600">{money(o.total, o.currency ?? 'USD')}</span>
              <Chip status={o.status} />
            </button>
          )))}
        {tab === 'queue' && (snap.queue.length === 0 ? <p className="px-2 py-3 text-[12px] text-slate-500">Brokering queue is empty.</p> :
          snap.queue.map(q => (
            <button key={q.id} onClick={() => pick(q.orderId)} className="grid w-full grid-cols-[1fr_auto] items-center gap-2 rounded-lg px-2 py-1.5 text-left text-[12px] hover:bg-slate-50">
              <span className="min-w-0 leading-tight">
                <span className="block truncate font-semibold text-slate-800">{orderIdx.get(q.orderId)?.reference ?? q.orderId.slice(0, 8)}</span>
                <span className="block truncate text-[10px] text-slate-400">{q.priority} · {q.attempts ?? 0} attempts{q.failureReason ? ` · ${q.failureReason}` : ''}</span>
              </span>
              <Chip status={q.status} />
            </button>
          )))}
        {tab === 'exceptions' && (snap.exceptions.length === 0 ? <p className="px-2 py-3 text-[12px] text-slate-500">No open exceptions.</p> :
          snap.exceptions.map(x => (
            <button key={x.id} onClick={() => pick(x.orderId)} className="grid w-full grid-cols-[auto_1fr_auto] items-center gap-2 rounded-lg px-2 py-1.5 text-left text-[12px] hover:bg-slate-50">
              <AlertTriangle className={`h-4 w-4 ${x.severity === 'HIGH' || x.severity === 'CRITICAL' ? 'text-rose-500' : 'text-amber-500'}`} />
              <span className="min-w-0 leading-tight">
                <span className="block truncate font-semibold text-slate-800">{x.title}</span>
                <span className="block truncate text-[10px] text-slate-400">{x.suggestedAction ?? x.type}</span>
              </span>
              <Chip status={x.severity === 'HIGH' || x.severity === 'CRITICAL' ? 'FAILED' : 'SCHEDULED'} label={x.severity ?? ''} />
            </button>
          )))}
      </div>
    </Glass>
  )
}

function MapControls({ scene }: { scene: React.RefObject<FlowSceneHandle> }) {
  const btn = 'grid h-9 w-9 place-items-center text-slate-600 transition hover:bg-slate-50 hover:text-slate-900'
  return (
    <Glass className="flex flex-col overflow-hidden p-0">
      <button className={btn} aria-label="Zoom in" onClick={() => scene.current?.zoom(0.8)}><Plus className="h-4 w-4" /></button>
      <button className={btn} aria-label="Zoom out" onClick={() => scene.current?.zoom(1.25)}><Minus className="h-4 w-4" /></button>
      <div className="mx-2 h-px bg-slate-100" />
      <button className={btn} aria-label="Rotate left" onClick={() => scene.current?.rotate(Math.PI / 10)}><RotateCcw className="h-4 w-4" /></button>
      <button className={btn} aria-label="Rotate right" onClick={() => scene.current?.rotate(-Math.PI / 10)}><RotateCw className="h-4 w-4" /></button>
      <div className="mx-2 h-px bg-slate-100" />
      <button className={btn} aria-label="Reset view" onClick={() => scene.current?.reset()}><Home className="h-4 w-4" /></button>
    </Glass>
  )
}

// ------------------------------------------------------------------ page

export default function OrderFlowTwinPage() {
  const [hours, setHours] = useState(24)
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const searchRef = useRef<HTMLInputElement>(null)
  const sceneRef = useRef<FlowSceneHandle>(null)
  const reducedMotion = useMemo(() => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches, [])

  const snapQuery = useQuery({
    queryKey: ['twin', 'orders', hours],
    queryFn: () => fetchNetworkSnapshot(hours),
    refetchInterval: REFRESH_MS,
    refetchIntervalInBackground: false,
    placeholderData: prev => prev,
  })
  const snap = snapQuery.data
  const layout = useMemo(() => (snap ? buildNetworkLayout(snap) : null), [snap])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = (e.target as HTMLElement)?.tagName === 'INPUT'
      if (e.key === '/' && !typing) { e.preventDefault(); searchRef.current?.focus() }
      if (e.key === 'Escape') { setSelectedKey(null); searchRef.current?.blur() }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const results = useMemo(() => {
    if (!snap || !query.trim()) return []
    const q = query.toLowerCase()
    const out: { label: string; sub: string; key: string }[] = []
    snap.recentOrders.forEach(o => { if (`${o.reference} ${o.channel} ${o.region} ${o.city}`.toLowerCase().includes(q)) out.push({ label: o.reference, sub: `Order · ${o.channel} → ${o.region}`, key: `order:${o.id}` }) })
    snap.nodes.forEach(n => { if (`${n.code} ${n.name} ${n.city}`.toLowerCase().includes(q)) out.push({ label: n.code ?? 'Node', sub: n.name ?? 'Node', key: `node:${n.id}` }) })
    snap.channels.forEach(c => { if (c.name.toLowerCase().includes(q)) out.push({ label: c.name, sub: 'Channel', key: c.id }) })
    snap.regions.forEach(r => { if (regionLabel(r.id).toLowerCase().includes(q)) out.push({ label: regionLabel(r.id), sub: 'Region', key: r.id }) })
    return out.slice(0, 8)
  }, [snap, query])

  // Orders are shown through their node in the 3D scene.
  const sceneKey = selectedKey?.startsWith('order:')
    ? (() => { const o = snap?.recentOrders.find(x => `order:${x.id}` === selectedKey); return o?.allocations[0]?.nodeId ? `node:${o.allocations[0].nodeId}` : null })()
    : selectedKey

  return (
    <div className="relative h-full min-h-[560px] w-full overflow-hidden bg-[#eef2f8]">
      <div className="absolute inset-0">
        {layout ? (
          <Suspense fallback={<Loading />}>
            <OrderFlowScene ref={sceneRef} layout={layout} selectedKey={sceneKey ?? null} reducedMotion={reducedMotion}
                            onSelect={k => setSelectedKey(k)} />
          </Suspense>
        ) : <Loading error={snapQuery.error instanceof Error ? snapQuery.error.message : null} />}
      </div>

      <div className="pointer-events-none absolute inset-x-0 top-0 z-40 flex flex-wrap items-center gap-3 p-4">
        <div className="pointer-events-auto flex items-center gap-2">
          <div className="grid h-9 w-9 place-items-center rounded-xl bg-blue-600 text-white shadow"><Network className="h-5 w-5" /></div>
          <div className="leading-tight">
            <div className="text-[15px] font-bold tracking-tight text-slate-900">Order Flow Twin</div>
            <div className="text-[11px] text-slate-500">Channels → nodes → customers</div>
          </div>
        </div>
        <div className="pointer-events-auto relative ml-2 w-full max-w-sm flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input ref={searchRef} value={query} onChange={e => setQuery(e.target.value)}
                 onKeyDown={e => { if (e.key === 'Enter' && results[0]) { setSelectedKey(results[0].key); setQuery('') } }}
                 placeholder="Search orders, nodes, channels, regions…"
                 className="w-full rounded-xl border border-slate-200 bg-white/90 py-2 pl-9 pr-8 text-[13px] text-slate-800 shadow-sm outline-none placeholder:text-slate-400 focus:border-blue-400 focus:ring-4 focus:ring-blue-100" />
          <kbd className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded border border-slate-200 px-1.5 text-[10px] text-slate-400">/</kbd>
          {results.length > 0 && (
            <div className="absolute left-0 right-0 top-full z-30 mt-2 rounded-xl border border-slate-200 bg-white p-1 shadow-xl">
              {results.map(r => (
                <button key={r.key} onClick={() => { setSelectedKey(r.key); setQuery('') }}
                        className="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-[13px] hover:bg-slate-50">
                  <span className="font-medium text-slate-800">{r.label}</span>
                  <span className="text-[11px] text-slate-400">{r.sub}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="pointer-events-auto ml-auto flex items-center gap-3">
          <div className="flex rounded-xl border border-slate-200 bg-white/90 p-0.5 shadow-sm" role="tablist" aria-label="Time window">
            {WINDOWS.map(w => (
              <button key={w.h} role="tab" aria-selected={hours === w.h} onClick={() => setHours(w.h)}
                      className={`rounded-lg px-2.5 py-1 text-[12px] font-semibold ${hours === w.h ? 'bg-blue-600 text-white' : 'text-slate-500 hover:text-slate-800'}`}>
                {w.label}
              </button>
            ))}
          </div>
          <LivePill updatedAt={snapQuery.dataUpdatedAt} failing={snapQuery.isError} refreshMs={REFRESH_MS} />
        </div>
      </div>

      {snap && (
        <>
          <div className="pointer-events-none absolute left-4 top-20 z-30"><div className="pointer-events-auto"><KpiTiles snap={snap} /></div></div>
          <div className="absolute right-4 top-44 z-30 flex items-start gap-3">
            <MapControls scene={sceneRef} />
            <div className="max-h-[calc(100vh-29rem)] overflow-y-auto rounded-2xl [scrollbar-width:thin]">
              <Inspector snap={snap} selectedKey={selectedKey} onClose={() => setSelectedKey(null)} />
            </div>
          </div>
          <div className="absolute bottom-4 left-4 right-[430px] z-30"><Pipeline snap={snap} /></div>
          <div className="absolute bottom-4 right-4 z-30"><OpsTabs snap={snap} onSelect={setSelectedKey} selectedKey={selectedKey} /></div>
          {snap.kpis.orders === 0 && (
            <div className="absolute inset-0 z-20 grid place-items-center">
              <Glass className="max-w-sm p-6 text-center">
                <div className="text-[14px] font-semibold text-slate-800">No orders in the last {snap.windowHours >= 168 ? '7 days' : `${snap.windowHours} hours`}</div>
                <p className="mt-1 text-[12px] text-slate-500">Widen the window, or connect a sales channel to start the flow.</p>
                <Link to="/integrations/marketplace" className="mt-3 inline-block text-[12px] font-semibold text-blue-600 hover:underline">Connect a channel</Link>
              </Glass>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function Loading({ error }: { error?: string | null }) {
  return (
    <div className="grid h-full w-full place-items-center">
      {error ? (
        <Glass className="max-w-sm p-6 text-center">
          <div className="text-[14px] font-semibold text-slate-800">Couldn't load the order network</div>
          <div className="mt-1 text-[12px] text-slate-500">{error}</div>
        </Glass>
      ) : (
        <div className="flex items-center gap-3 text-[13px] text-slate-500">
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" /> Mapping your order network…
        </div>
      )}
    </div>
  )
}
