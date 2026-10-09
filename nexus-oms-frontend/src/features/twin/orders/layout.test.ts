import { describe, expect, it } from 'vitest'
import { buildNetworkLayout, channelColor, regionLabel } from './layout'
import type { NetworkSnapshot } from './api'

const base: NetworkSnapshot = {
  windowHours: 24,
  since: '',
  kpis: { orders: 0, revenue: 0, routedPct: null, splitRatePct: null, backorders: 0, avgEstimatedCostPerOrder: null, avgPromiseConfidencePct: null, openExceptions: 0, queueDepth: 0 },
  channels: [{ id: 'channel:SHOPIFY', name: 'SHOPIFY', orders: 10, revenue: 100 }, { id: 'channel:AMAZON', name: 'AMAZON', orders: 5, revenue: 50 }],
  nodes: [
    { id: 'n1', code: 'DC-1', name: null, type: 'DC', city: null, state: null, status: null, fillPct: null, allocations: 12, unitsAllocated: 20, availableUnits: 5, skuCount: 1, avgEstimatedCost: null },
    { id: 'n2', code: 'ST-1', name: null, type: 'STORE', city: null, state: null, status: null, fillPct: null, allocations: 3, unitsAllocated: 3, availableUnits: 1, skuCount: 1, avgEstimatedCost: null },
  ],
  regions: Array.from({ length: 12 }, (_, i) => ({ id: `region:S${i}`, orders: 12 - i })),
  flows: [
    { from: 'channel:SHOPIFY', to: 'node:n1', kind: 'CHANNEL_TO_NODE', orders: 9 },
    { from: 'node:n1', to: 'region:S0', kind: 'NODE_TO_REGION', orders: 4 },
    { from: 'node:n1', to: 'region:S11', kind: 'NODE_TO_REGION', orders: 1 },
    { from: 'node:n2', to: 'region:S10', kind: 'NODE_TO_REGION', orders: 2 },
  ],
  lifecycle: [],
  recentOrders: [],
  queue: [],
  exceptions: [],
  generatedAt: '',
}

describe('buildNetworkLayout', () => {
  it('lays channels, nodes and regions out in three columns', () => {
    const l = buildNetworkLayout(base)
    expect(l.channels[0].position[0]).toBeLessThan(l.nodes[0].position[0])
    expect(l.nodes[0].position[0]).toBeLessThan(l.regions[0].position[0])
  })

  it('folds regions beyond the top ten into Other, keeping their flows', () => {
    const l = buildNetworkLayout(base)
    expect(l.regions).toHaveLength(11)
    expect(regionLabel(l.regions[10].key)).toBe('Other')
    expect(l.flows.filter(f => f.toKey === 'region:__other').map(f => f.orders).sort()).toEqual([1, 2])
  })

  it('sizes the busiest flow widest', () => {
    const l = buildNetworkLayout(base)
    const widest = [...l.flows].sort((a, b) => b.width - a.width)[0]
    expect(widest.orders).toBe(9)
  })

  it('colours channel flows by channel and has a stable fallback', () => {
    expect(channelColor('SHOPIFY')).toBe('#5e8e3e')
    expect(channelColor('TIKTOK')).toBe(channelColor('TIKTOK'))
  })
})
