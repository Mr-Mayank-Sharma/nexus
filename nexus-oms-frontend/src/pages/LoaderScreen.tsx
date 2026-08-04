import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Truck, Ship, CheckCircle, Search,
  MapPin, ClipboardCheck, Box, QrCode,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as shippingApi from '../api/shipping'
import * as yardApi from '../api/yardManagement'
import { getWarehousesSummary } from '../api/analytics'
import Autocomplete from '../components/common/Autocomplete'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'

const asArray = (d: unknown): any[] => (Array.isArray(d) ? d : Array.isArray((d as any)?.content) ? (d as any).content : [])

export default function LoaderScreen() {
  const navigate = useNavigate()
  const { addToast } = useToast()
  const queryClient = useQueryClient()

  const [scanInput, setScanInput] = useState('')

  const { data: warehouses = [] } = useQuery({
    queryKey: ['loader-warehouses'],
    queryFn: async () => {
      const res = await getWarehousesSummary().catch(() => null)
      return asArray(res?.data ?? res)
    },
  })

  const warehouseId = warehouses[0]?.id as string | undefined

  const { data: dockDoors = [], isLoading: doorsLoading } = useQuery({
    queryKey: ['loader-dock-doors', warehouseId],
    queryFn: async () => {
      if (!warehouseId) return []
      const res = await yardApi.getDockDoors(warehouseId).catch(() => null)
      return asArray(res?.data)
    },
    enabled: !!warehouseId,
    refetchInterval: 60000,
  })

  const { data: trailers = [] } = useQuery({
    queryKey: ['loader-trailers', warehouseId],
    queryFn: async () => {
      if (!warehouseId) return []
      const res = await yardApi.getTrailers(warehouseId).catch(() => null)
      return asArray(res?.data)
    },
    enabled: !!warehouseId,
    refetchInterval: 60000,
  })

  const { data: shipments = [], isLoading: shipmentsLoading } = useQuery({
    queryKey: ['loader-shipments'],
    queryFn: async () => {
      const res = await shippingApi.getShipments({}).catch(() => null)
      return asArray(res?.data).slice(0, 20)
    },
  })

  const releaseDoor = useMutation({
    mutationFn: async (doorId: string) => {
      const res = await yardApi.releaseDoor(doorId)
      return res
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loader-dock-doors'] })
      addToast({ type: 'success', title: 'Door released' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to release door' }),
  })

  const occupiedDoors = dockDoors.filter((d: any) => d.status === 'OCCUPIED')
  const availableDoors = dockDoors.filter((d: any) => d.status === 'AVAILABLE')
  const dockedTrailers = trailers.filter((t: any) => t.currentDockDoorId)
  const palletsDocked = dockedTrailers.reduce((sum: number, t: any) => sum + (t.palletCount ?? 0), 0)

  const trailerByDoor = (doorId: string) => trailers.find((t: any) => t.currentDockDoorId === doorId)

  const kpis = [
    { title: 'Occupied Doors', value: occupiedDoors.length.toString(), icon: <Truck className="w-5 h-5" />, color: 'primary' as const, trend: null },
    { title: 'Available Doors', value: availableDoors.length.toString(), icon: <CheckCircle className="w-5 h-5" />, color: 'success' as const, trend: null },
    { title: 'Docked Trailers', value: dockedTrailers.length.toString(), icon: <Ship className="w-5 h-5" />, color: 'info' as const, trend: null },
    { title: 'Pallets at Dock', value: palletsDocked.toString(), icon: <Box className="w-5 h-5" />, color: 'warning' as const, trend: null },
  ]

  const handleScan = () => {
    const q = scanInput.trim().toLowerCase()
    if (!q) return
    const match = shipments.find((s: any) =>
      (s.id || '').toLowerCase() === q ||
      (s.trackingNumber || '').toLowerCase() === q ||
      (s.orderNumber || s.orderId || '').toLowerCase().includes(q)
    )
    if (match) {
      addToast({ type: 'success', title: `Shipment found: ${match.trackingNumber || match.id}` })
      navigate(`/orders/${match.orderId || match.id}`)
    } else {
      addToast({ type: 'error', title: 'No matching shipment found' })
    }
  }

  if (!warehouseId && !warehousesLoading) {
    return (
      <div className="space-y-6">
        <LoaderHeader onNavigate={() => navigate('/shipping')} />
        <div className="enterprise-card p-12 text-center">
          <Truck className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No warehouse configured</p>
          <p className="text-sm text-[var(--text-tertiary)] mt-1">Dock doors will appear once a warehouse is set up.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <LoaderHeader onNavigate={() => navigate('/shipping')} />

      <div className="grid grid-cols-4 gap-4">
        {kpis.map(k => (
          <EnterpriseKPICard key={k.title} title={k.title} value={k.value} icon={k.icon} color={k.color} trend={k.trend} />
        ))}
      </div>

      {/* Scan */}
      <div className="enterprise-card p-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20 flex items-center justify-center">
            <QrCode className="w-5 h-5 text-[var(--nexus-warning-600)]" />
          </div>
          <div className="relative flex-1">
            <Autocomplete value={scanInput} onChange={setScanInput} placeholder="Scan trailer barcode or shipment ID..." minChars={0} showSearchIcon={false} clearable={false} inputClassName="w-full pl-10 pr-4 py-3 text-sm border-2 border-[var(--nexus-warning-200)] dark:border-[var(--nexus-warning-800)] rounded-xl bg-[var(--surface-base)] text-[var(--text-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--nexus-warning-500)] focus:border-[var(--nexus-warning-500)]" />
            <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--nexus-warning-400)] pointer-events-none" />
          </div>
          <button type="button" onClick={handleScan} className="enterprise-btn-primary text-sm px-6 py-3 bg-[var(--nexus-warning-600)] hover:bg-[var(--nexus-warning-700)]"><Search className="w-4 h-4" /> Find</button>
        </div>
      </div>

      {/* Dock Doors Grid */}
      {doorsLoading ? (
        <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" /></div>
      ) : dockDoors.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <Truck className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No dock doors found</p>
          <p className="text-sm text-[var(--text-tertiary)] mt-1">Configure dock doors to manage loading operations.</p>
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-4">
          {dockDoors.map((door: any) => {
            const occupied = door.status === 'OCCUPIED'
            const trailer = occupied ? trailerByDoor(door.id) : undefined
            return (
              <div key={door.id} className={clsx('enterprise-card p-4 border-l-4 transition-all',
                occupied ? 'border-l-amber-500 bg-[var(--nexus-warning-50)]/30 dark:bg-[var(--nexus-warning-900)]/5' :
                'border-l-gray-300 dark:border-l-gray-600')}>
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <div className={clsx('w-10 h-10 rounded-xl flex items-center justify-center',
                      occupied ? 'bg-[var(--nexus-warning-100)] dark:bg-[var(--nexus-warning-900)]/20 text-[var(--nexus-warning-600)]' : 'bg-[var(--surface-muted)] text-[var(--text-tertiary)]')}>
                      <Truck className="w-5 h-5" />
                    </div>
                    <div>
                      <span className="text-base font-bold text-[var(--text-primary)]">{door.doorNumber}</span>
                      <p className="text-xs text-[var(--text-secondary)]">{door.doorType || 'Dock'}</p>
                    </div>
                  </div>
                  <EnterpriseStatusBadge status={occupied ? 'warning' : 'pending'} label={occupied ? 'Occupied' : 'Available'} />
                </div>

                {occupied ? (
                  <div className="space-y-2 text-xs text-[var(--text-secondary)]">
                    <div className="flex items-center justify-between"><span>Trailer</span><span className="font-medium text-[var(--text-secondary)]">{trailer?.trailerNumber || '—'}</span></div>
                    <div className="flex items-center justify-between"><span>Carrier</span><span className="font-medium text-[var(--text-secondary)]">{trailer?.carrierCode || '—'}</span></div>
                    <div className="flex items-center justify-between"><span>Plate</span><span className="font-medium text-[var(--text-secondary)]">{trailer?.licensePlate || '—'}</span></div>
                    <div className="flex items-center justify-between"><span>Pallets</span><span className="font-medium text-[var(--text-secondary)]">{trailer?.palletCount ?? '—'}</span></div>
                    <button type="button" onClick={() => releaseDoor.mutate(door.id)} disabled={releaseDoor.isPending} className="w-full mt-2 enterprise-btn-secondary text-xs py-2 flex items-center justify-center gap-1">
                      <ClipboardCheck className="w-3.5 h-3.5" /> Release Door
                    </button>
                  </div>
                ) : (
                  <div className="py-4 text-center">
                    <p className="text-xs text-[var(--text-tertiary)]">Door available for assignment</p>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {shipmentsLoading && shipments.length === 0 && (
        <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" /></div>
      )}
    </div>
  )
}

function LoaderHeader({ onNavigate }: { onNavigate: () => void }) {
  return (
    <div className="flex items-center justify-between">
      <div>
        <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
          <Truck className="w-7 h-7 text-[var(--nexus-warning-500)]" />
          Loading Dock
        </h1>
        <p className="text-sm text-[var(--text-secondary)] mt-1">Manage loading, dispatch, and carrier coordination</p>
      </div>
      <button type="button" onClick={onNavigate} className="enterprise-btn-secondary text-sm flex items-center gap-1.5 px-4 py-2">
        <Ship className="w-4 h-4" /> Full Shipping View
      </button>
    </div>
  )
}
