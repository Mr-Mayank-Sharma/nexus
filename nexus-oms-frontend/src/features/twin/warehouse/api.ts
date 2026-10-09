import client from '../../../api/client'

/** Mirrors WarehouseTwinService records on the backend (GET /twin/warehouses[/id]). */
export interface TwinSite {
  id: string
  code: string | null
  name: string | null
  type: string | null
  city: string | null
  state: string | null
  status: string | null
  fillPct: number | null
  doorsTotal: number
  doorsOccupied: number
}

export interface TwinKpis {
  stockOnHandUnits: number
  skuCount: number
  trucksOnSite: number
  trucksInbound: number
  trucksOutbound: number
  arrivalsToday: number
  outboundShipmentsToday: number
  onTimeDeliveryPct30d: number | null
  onTimeSample30d: number | null
}

export interface TwinZone {
  id: string
  code: string | null
  name: string | null
  type: string | null
  capacitySqm: number | null
  usedSqm: number | null
  hazardous: boolean | null
}

export interface TwinDock {
  id: string
  doorNumber: string | null
  doorType: string | null
  status: string | null
  appointmentId: string | null
  vehicleLabel: string | null
}

export interface TwinYardSlot {
  id: string
  code: string | null
  type: string | null
  status: string | null
  capacity: number | null
  occupancy: number | null
}

export interface TwinTruck {
  id: string
  source: 'APPOINTMENT' | 'TRAILER'
  label: string | null
  carrier: string | null
  direction: 'INBOUND' | 'OUTBOUND'
  status: string | null
  dockDoorNumber: string | null
  yardLocationCode: string | null
  eta: string | null
  arrivedAt: string | null
  palletCount: number | null
  weightKg: number | null
  driverName: string | null
  poNumbers: string | null
}

export interface TwinEquipment {
  id: string
  code: string | null
  type: string | null
  model: string | null
  status: string | null
  batteryLevel: number | null
  assigned: boolean
}

export interface TwinStockLine {
  sku: string
  onHand: number
  allocated: number
  available: number
  stockStatus: 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK'
}

export interface TwinShipment {
  id: string
  orderId: string | null
  carrier: string | null
  serviceLevel: string | null
  trackingNumber: string | null
  status: string | null
  createdAt: string | null
  shippedAt: string | null
  estimatedDelivery: string | null
  actualDelivery: string | null
}

export interface TwinSnapshot {
  site: TwinSite
  address: string
  latitude: number | null
  longitude: number | null
  totalCapacitySqm: number | null
  usedCapacitySqm: number | null
  kpis: TwinKpis
  zones: TwinZone[]
  docks: TwinDock[]
  yard: TwinYardSlot[]
  trucks: TwinTruck[]
  equipment: TwinEquipment[]
  topInventory: TwinStockLine[]
  shipments: TwinShipment[]
  generatedAt: string
}

function unwrap<T>(payload: { success?: boolean; data?: T; message?: string; error?: string }): T {
  if (payload && payload.success === false) {
    throw new Error(payload.message || payload.error || 'Request failed')
  }
  return payload.data as T
}

export async function fetchTwinSites(): Promise<TwinSite[]> {
  const { data } = await client.get('/twin/warehouses')
  return unwrap<TwinSite[]>(data) ?? []
}

export async function fetchTwinSnapshot(id: string): Promise<TwinSnapshot> {
  const { data } = await client.get(`/twin/warehouses/${id}`)
  return unwrap<TwinSnapshot>(data)
}
