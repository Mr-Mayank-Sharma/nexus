import { describe, expect, it } from 'vitest'
import { buildLayout, isLift, statusTone } from './layout'
import type { TwinSnapshot } from './api'

const now = new Date('2026-10-09T10:00:00Z')

function snapshot(partial: Partial<TwinSnapshot> = {}): TwinSnapshot {
  return {
    site: { id: 'w1', code: 'WH-01', name: 'Riverside', type: null, city: null, state: null, status: 'ACTIVE', fillPct: 78, doorsTotal: 2, doorsOccupied: 1 },
    address: '',
    latitude: null,
    longitude: null,
    totalCapacitySqm: null,
    usedCapacitySqm: null,
    kpis: { stockOnHandUnits: 0, skuCount: 0, trucksOnSite: 0, trucksInbound: 0, trucksOutbound: 0, arrivalsToday: 0, outboundShipmentsToday: 0, onTimeDeliveryPct30d: null, onTimeSample30d: null },
    zones: [],
    docks: [
      { id: 'd1', doorNumber: 'Bay 1', doorType: null, status: 'OCCUPIED', appointmentId: 'a1', vehicleLabel: 'TRK-1' },
      { id: 'd2', doorNumber: 'Bay 2', doorType: null, status: 'AVAILABLE', appointmentId: null, vehicleLabel: null },
    ],
    yard: [],
    trucks: [],
    equipment: [],
    topInventory: [],
    shipments: [],
    generatedAt: now.toISOString(),
    ...partial,
  }
}

const truck = (over: Record<string, unknown>) => ({
  id: 't', source: 'APPOINTMENT', label: 'TRK', carrier: null, direction: 'INBOUND', status: 'SCHEDULED',
  dockDoorNumber: null, yardLocationCode: null, eta: null, arrivedAt: null, palletCount: null, weightKg: null,
  driverName: null, poNumbers: null, ...over,
}) as TwinSnapshot['trucks'][number]

describe('buildLayout', () => {
  it('backs on-site trucks into their assigned door', () => {
    const layout = buildLayout(snapshot({ trucks: [truck({ id: 'a', status: 'IN_PROGRESS', dockDoorNumber: 'Bay 1' })] }), now)
    const dock = layout.docks.find(d => d.dock.doorNumber === 'Bay 1')!
    expect(layout.trucks[0].placement).toBe('DOCK')
    expect(layout.trucks[0].position[0]).toBe(dock.position[0])
  })

  it('places arriving trucks on the road by minutes until ETA', () => {
    const near = buildLayout(snapshot({ trucks: [truck({ eta: '2026-10-09T10:05:00Z' })] }), now).trucks[0]
    const far = buildLayout(snapshot({ trucks: [truck({ eta: '2026-10-09T10:50:00Z' })] }), now).trucks[0]
    expect(near.placement).toBe('ROAD')
    expect(far.position[0]).toBeGreaterThan(near.position[0])
  })

  it('puts overdue arrivals at the gate', () => {
    expect(buildLayout(snapshot({ trucks: [truck({ eta: '2026-10-09T09:00:00Z' })] }), now).trucks[0].placement).toBe('GATE')
  })

  it('sends busy lifts to occupied doors and parks idle ones', () => {
    const layout = buildLayout(snapshot({
      equipment: [
        { id: 'f1', code: 'FL-07', type: 'FORKLIFT', model: null, status: 'IN_USE', batteryLevel: 79, assigned: true },
        { id: 'f2', code: 'FL-08', type: 'FORKLIFT', model: null, status: 'IDLE', batteryLevel: 100, assigned: false },
        { id: 's1', code: 'SC-1', type: 'SCANNER', model: null, status: 'IDLE', batteryLevel: 50, assigned: false },
      ],
    }), now)
    expect(layout.forklifts).toHaveLength(2)
    expect(layout.forklifts[0].target).not.toBeNull()
    expect(layout.forklifts[1].target).toBeNull()
  })

  it('creates one hall per zone, capped at four', () => {
    const zones = Array.from({ length: 6 }, (_, i) => ({ id: `z${i}`, code: `Z${i}`, name: null, type: null, capacitySqm: 100 * (i + 1), usedSqm: null, hazardous: null }))
    expect(buildLayout(snapshot({ zones }), now).halls).toHaveLength(4)
  })
})

describe('helpers', () => {
  it('recognises lift equipment types', () => {
    expect(isLift({ id: 'x', code: null, type: 'Reach Truck', model: null, status: null, batteryLevel: null, assigned: false })).toBe(true)
    expect(isLift({ id: 'x', code: null, type: 'RF_SCANNER', model: null, status: null, batteryLevel: null, assigned: false })).toBe(false)
  })

  it('maps statuses to tones', () => {
    expect(statusTone('LOW_STOCK').tone).toBe('amber')
    expect(statusTone('IN_PROGRESS').label).toBe('In progress')
  })
})
