import type { TwinDock, TwinEquipment, TwinSnapshot, TwinTruck, TwinYardSlot, TwinZone } from './api'

/**
 * Deterministic scene layout derived from the snapshot. Positions come from real records
 * (which door a truck is at, which yard slot, its ETA); nothing is placed at random.
 * Units are metres-ish scene units; +z faces the yard and road, -z is the back wall.
 */

export type Vec3 = [number, number, number]

export interface HallLayout { zone: TwinZone | null; x: number; width: number; depth: number; height: number; label: string }
export interface DockLayout { dock: TwinDock; position: Vec3; occupied: boolean }
export interface TruckLayout { truck: TwinTruck; position: Vec3; rotationY: number; placement: 'DOCK' | 'YARD' | 'ROAD' | 'GATE' }
export interface ForkliftLayout { equipment: TwinEquipment; home: Vec3; target: Vec3 | null }
export interface PalletLayout { key: string; position: Vec3; tint: 'cardboard' | 'blue' }
export interface YardSlotLayout { slot: TwinYardSlot | null; position: Vec3; label: string }

export interface SceneLayout {
  halls: HallLayout[]
  buildingWidth: number
  buildingDepth: number
  front: number
  docks: DockLayout[]
  trucks: TruckLayout[]
  forklifts: ForkliftLayout[]
  pallets: PalletLayout[]
  yardSlots: YardSlotLayout[]
  gate: Vec3
  roadZ: number
  bounds: { minX: number; maxX: number; minZ: number; maxZ: number }
}

const BUILDING_DEPTH = 18
const DOCK_SPACING = 5.2
const MAX_HALLS = 4
const ON_SITE = new Set(['CHECKED_IN', 'IN_PROGRESS', 'DOCKED', 'LOADING', 'UNLOADING', 'AT_DOCK', 'IN_YARD', 'CHECKED-IN'])
const LIFT_TYPES = ['FORKLIFT', 'REACH', 'PALLET', 'JACK', 'TUGGER', 'ORDER_PICKER', 'LIFT']

export function isLift(e: TwinEquipment): boolean {
  const t = (e.type ?? '').toUpperCase()
  return LIFT_TYPES.some(k => t.includes(k))
}

export function isOccupiedDock(d: TwinDock): boolean {
  return Boolean(d.appointmentId || d.vehicleLabel) || (d.status ?? '').toUpperCase() === 'OCCUPIED'
}

export function minutesUntil(iso: string | null, now: Date): number | null {
  if (!iso) return null
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return null
  return Math.round((t - now.getTime()) / 60000)
}

export function buildLayout(snap: TwinSnapshot, now: Date = new Date()): SceneLayout {
  const dockCount = Math.max(snap.docks.length, 2)
  const buildingWidth = Math.max(30, dockCount * DOCK_SPACING + 10)
  const front = BUILDING_DEPTH / 2

  // Halls: one per zone (largest first, capped), widths proportional to zone capacity.
  const zones = [...snap.zones].sort((a, b) => (b.capacitySqm ?? 0) - (a.capacitySqm ?? 0)).slice(0, MAX_HALLS)
  const weights = zones.length ? zones.map(z => Math.max(Number(z.capacitySqm ?? 1), 1)) : [1]
  const totalWeight = weights.reduce((a, b) => a + b, 0)
  const gap = 0.8
  const usable = buildingWidth - gap * (weights.length - 1)
  let cursor = -buildingWidth / 2
  const halls: HallLayout[] = weights.map((w, i) => {
    const width = (usable * w) / totalWeight
    const zone = zones[i] ?? null
    const hall: HallLayout = {
      zone,
      x: cursor + width / 2,
      width,
      depth: BUILDING_DEPTH - (i % 2) * 2,
      height: 6.5 + (i === 0 ? 1.2 : 0),
      label: zone?.name || zone?.code || snap.site.code || 'Warehouse',
    }
    cursor += width + gap
    return hall
  })

  // Dock doors evenly along the front wall.
  const sortedDocks = [...snap.docks]
  const span = (sortedDocks.length - 1) * DOCK_SPACING
  const docks: DockLayout[] = sortedDocks.map((dock, i) => ({
    dock,
    position: [-span / 2 + i * DOCK_SPACING, 0, front] as Vec3,
    occupied: isOccupiedDock(dock),
  }))
  const dockByNumber = new Map(docks.map(d => [(d.dock.doorNumber ?? '').toLowerCase(), d]))

  // Yard: real yard locations as painted slots to the right of the building.
  const yardOriginX = buildingWidth / 2 + 9
  const yardCols = 3
  const yardSlotList = snap.yard.length > 0 ? snap.yard : []
  const yardSlots: YardSlotLayout[] = yardSlotList.map((slot, i) => ({
    slot,
    position: [yardOriginX + (i % yardCols) * 5, 0, front + 5 + Math.floor(i / yardCols) * 11] as Vec3,
    label: slot.code ?? `Y${i + 1}`,
  }))
  const yardByCode = new Map(yardSlots.map(y => [(y.label ?? '').toLowerCase(), y]))

  const roadZ = front + 30
  const gate: Vec3 = [buildingWidth / 2 + 4, 0, roadZ - 6]

  let overflow = 0
  const trucks: TruckLayout[] = snap.trucks.map(truck => {
    const dock = truck.dockDoorNumber ? dockByNumber.get(truck.dockDoorNumber.toLowerCase()) : undefined
    const status = (truck.status ?? '').toUpperCase()
    if (dock && (ON_SITE.has(status) || truck.source === 'TRAILER')) {
      // Backed into the door: trailer rear at the wall, cab facing the yard.
      return { truck, position: [dock.position[0], 0, front + 6.2], rotationY: 0, placement: 'DOCK' }
    }
    const slot = truck.yardLocationCode ? yardByCode.get(truck.yardLocationCode.toLowerCase()) : undefined
    if (slot) {
      return { truck, position: [slot.position[0], 0, slot.position[2] + 1], rotationY: 0, placement: 'YARD' }
    }
    if (ON_SITE.has(status) || truck.source === 'TRAILER') {
      const i = overflow++
      return {
        truck,
        position: [yardOriginX + (i % yardCols) * 5, 0, front + 5 + Math.floor(i / yardCols) * 11 + (yardSlots.length ? 22 : 0)],
        rotationY: 0,
        placement: 'YARD',
      }
    }
    // Not yet arrived: on the approach road, distance proportional to minutes until ETA (≤ 60 min shown).
    const mins = minutesUntil(truck.eta, now)
    if (mins === null || mins <= 0) {
      return { truck, position: [gate[0] + 3, 0, gate[2] + 6], rotationY: -Math.PI / 2, placement: 'GATE' }
    }
    const t = Math.min(mins, 60) / 60
    return { truck, position: [gate[0] + 6 + t * 70, 0, roadZ + 1.6], rotationY: -Math.PI / 2, placement: 'ROAD' }
  })

  // Lifts: assigned/active ones shuttle between staging and an occupied door; idle ones park.
  const activeDocks = docks.filter(d => d.occupied)
  const lifts = snap.equipment.filter(isLift)
  let parked = 0
  let working = 0
  const forklifts: ForkliftLayout[] = lifts.map(equipment => {
    const status = (equipment.status ?? '').toUpperCase()
    const busy = equipment.assigned || ['IN_USE', 'ACTIVE', 'BUSY', 'OPERATING', 'WORKING'].includes(status)
    if (busy && activeDocks.length > 0) {
      const dock = activeDocks[working++ % activeDocks.length]
      const offset = ((working % 2) * 2 - 1) * 1.4
      return {
        equipment,
        home: [dock.position[0] + offset, 0, front + 3.4],
        target: [dock.position[0] + offset * 0.4, 0, front + 0.9],
      }
    }
    const i = parked++
    return { equipment, home: [-buildingWidth / 2 - 4 - (i % 3) * 2.6, 0, front + 2 + Math.floor(i / 3) * 3], target: null }
  })

  // Staged pallets in front of busy doors, one stack per pallet on the appointment (max 6).
  const pallets: PalletLayout[] = []
  for (const d of activeDocks) {
    const t = trucks.find(tr => tr.placement === 'DOCK' && tr.truck.dockDoorNumber === d.dock.doorNumber)
    const count = Math.min(t?.truck.palletCount ?? 2, 6)
    for (let i = 0; i < count; i++) {
      pallets.push({
        key: `${d.dock.id}-${i}`,
        position: [d.position[0] - 2.3 + (i % 2) * 4.6, 0, front + 1.8 + Math.floor(i / 2) * 1.5],
        tint: i % 3 === 2 ? 'blue' : 'cardboard',
      })
    }
  }

  const maxX = Math.max(yardOriginX + yardCols * 5 + 6, gate[0] + 20)
  return {
    halls,
    buildingWidth,
    buildingDepth: BUILDING_DEPTH,
    front,
    docks,
    trucks,
    forklifts,
    pallets,
    yardSlots,
    gate,
    roadZ,
    bounds: { minX: -buildingWidth / 2 - 14, maxX, minZ: -front - 8, maxZ: roadZ + 8 },
  }
}

/** Human status label + tone for chips, shared by the 3D labels and the panels. */
export function statusTone(status: string | null | undefined): { label: string; tone: 'green' | 'blue' | 'amber' | 'red' | 'slate' } {
  const s = (status ?? '').toUpperCase()
  const label = s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') : 'Unknown'
  if (['AVAILABLE', 'IN_STOCK', 'DELIVERED', 'COMPLETED', 'OPERATIONAL', 'ACTIVE', 'IDLE'].includes(s)) return { label, tone: 'green' }
  if (['IN_PROGRESS', 'LOADING', 'UNLOADING', 'DOCKED', 'OCCUPIED', 'IN_USE', 'CHECKED_IN', 'SHIPPED'].includes(s)) return { label, tone: 'blue' }
  if (['SCHEDULED', 'CONFIRMED', 'IN_TRANSIT', 'EN_ROUTE', 'LOW_STOCK', 'MAINTENANCE', 'CHARGING', 'AT_GATE'].includes(s)) return { label, tone: 'amber' }
  if (['OUT_OF_STOCK', 'CANCELLED', 'NO_SHOW', 'FAILED', 'OFFLINE', 'DOWN', 'BLOCKED'].includes(s)) return { label, tone: 'red' }
  return { label, tone: 'slate' }
}
