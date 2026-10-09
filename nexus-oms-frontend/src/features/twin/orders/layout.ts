import type { NetworkSnapshot } from './api'

/**
 * Schematic 3D layout of the order network: sales channels (left) → fulfilment nodes (centre)
 * → ship-to regions (right), like a Sankey diagram laid on a table. Sizes and flow widths are
 * proportional to real order counts in the window.
 */
export type Vec3 = [number, number, number]

export interface Placed<T> { item: T; key: string; position: Vec3; size: number }
export interface FlowLayout { key: string; from: Vec3; to: Vec3; orders: number; width: number; color: string; fromKey: string; toKey: string }

export interface NetworkLayout {
  channels: Placed<NetworkSnapshot['channels'][number]>[]
  nodes: Placed<NetworkSnapshot['nodes'][number]>[]
  regions: Placed<NetworkSnapshot['regions'][number]>[]
  flows: FlowLayout[]
  depth: number
}

export const COLUMN_X = { channel: -40, node: 0, region: 40 }
const MAX_REGIONS = 10
const CHANNEL_COLORS: Record<string, string> = {
  SHOPIFY: '#5e8e3e',
  AMAZON: '#f59e0b',
  BIGCOMMERCE: '#334155',
  EBAY: '#e11d48',
  WALMART: '#0ea5e9',
  MAGENTO: '#ea580c',
  POS: '#8b5cf6',
  DIRECT: '#64748b',
}
const FALLBACK = ['#14b8a6', '#ec4899', '#6366f1', '#84cc16']

export function channelColor(name: string): string {
  if (CHANNEL_COLORS[name]) return CHANNEL_COLORS[name]
  let h = 0
  for (const c of name) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return FALLBACK[h % FALLBACK.length]
}

function spread<T>(items: T[], spacing: number): number[] {
  const span = (items.length - 1) * spacing
  return items.map((_, i) => -span / 2 + i * spacing)
}

export function buildNetworkLayout(snap: NetworkSnapshot): NetworkLayout {
  const maxChannel = Math.max(1, ...snap.channels.map(c => c.orders))
  const maxNode = Math.max(1, ...snap.nodes.map(n => n.allocations))
  const regionsShown = snap.regions.slice(0, MAX_REGIONS)
  const otherRegions = snap.regions.slice(MAX_REGIONS)
  const regions = otherRegions.length
    ? [...regionsShown, { id: 'region:__other', orders: otherRegions.reduce((a, r) => a + r.orders, 0) }]
    : regionsShown
  const maxRegion = Math.max(1, ...regions.map(r => r.orders))

  const channelZ = spread(snap.channels, 9)
  const nodeZ = spread(snap.nodes, 12)
  const regionZ = spread(regions, 6.5)

  const channels = snap.channels.map((c, i) => ({ item: c, key: c.id, position: [COLUMN_X.channel, 0, channelZ[i]] as Vec3, size: 0.4 + 0.6 * (c.orders / maxChannel) }))
  const nodes = snap.nodes.map((n, i) => ({ item: n, key: `node:${n.id}`, position: [COLUMN_X.node, 0, nodeZ[i]] as Vec3, size: 0.45 + 0.55 * (n.allocations / maxNode) }))
  const regionPlaced = regions.map((r, i) => ({ item: r, key: r.id, position: [COLUMN_X.region, 0, regionZ[i]] as Vec3, size: 0.25 + 0.75 * (r.orders / maxRegion) }))

  const pos = new Map<string, Vec3>()
  channels.forEach(c => pos.set(c.key, c.position))
  nodes.forEach(n => pos.set(n.key, n.position))
  regionPlaced.forEach(r => pos.set(r.key, r.position))
  const otherIds = new Set(otherRegions.map(r => r.id))

  // Merge flows into "other" when their region was folded.
  const merged = new Map<string, { from: string; to: string; orders: number; kind: string }>()
  for (const f of snap.flows) {
    const to = otherIds.has(f.to) ? 'region:__other' : f.to
    const k = `${f.from}→${to}`
    const prev = merged.get(k)
    merged.set(k, { from: f.from, to, kind: f.kind, orders: (prev?.orders ?? 0) + f.orders })
  }
  const maxFlow = Math.max(1, ...[...merged.values()].map(f => f.orders))

  const flows: FlowLayout[] = []
  for (const [key, f] of merged) {
    const from = pos.get(f.from)
    const to = pos.get(f.to)
    if (!from || !to) continue
    const channelName = f.from.startsWith('channel:') ? f.from.slice('channel:'.length) : null
    flows.push({
      key,
      from,
      to,
      orders: f.orders,
      width: 0.08 + 0.32 * Math.sqrt(f.orders / maxFlow),
      color: channelName ? channelColor(channelName) : '#2563eb',
      fromKey: f.from,
      toKey: f.to,
    })
  }

  const depth = Math.max(channelZ.length * 9, nodeZ.length * 11, regionZ.length * 6.5, 30)
  return { channels, nodes, regions: regionPlaced, flows, depth }
}

export function regionLabel(id: string): string {
  if (id === 'region:__other') return 'Other'
  return id.startsWith('region:') ? id.slice('region:'.length) : id
}
