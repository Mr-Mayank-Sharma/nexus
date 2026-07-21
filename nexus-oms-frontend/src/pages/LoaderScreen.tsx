import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  Truck, Ship, ClipboardCheck, ArrowRight, CheckCircle, Search,
  MapPin, Clock, AlertTriangle, User, Box, QrCode, ExternalLink,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as shippingApi from '../api/shipping'
import Autocomplete from '../components/common/Autocomplete'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'

interface DockDoor {
  id: string
  door: string
  carrier: string
  trailer: string
  driver: string
  status: 'loading' | 'ready' | 'waiting' | 'available'
  orders: number
  pallets: number
  eta: string
}

export default function LoaderScreen() {
  const navigate = useNavigate()
  const { addToast } = useToast()

  const [scanInput, setScanInput] = useState('')

  const { data: shipments = [], isLoading } = useQuery({
    queryKey: ['loader-shipments'],
    queryFn: async () => {
      const res = await shippingApi.getShipments({})
      const d = res.data
      return (Array.isArray(d) ? d : (d?.content ?? [])).slice(0, 20)
    },
  })

  const dockDoors: DockDoor[] = [
    { id: 'd1', door: 'D1', carrier: 'UPS', trailer: 'TL-4821', driver: 'John D.', status: 'loading', orders: 42, pallets: 8, eta: '—' },
    { id: 'd2', door: 'D2', carrier: 'FedEx', trailer: 'TL-9033', driver: 'Mike S.', status: 'ready', orders: 28, pallets: 5, eta: '10:30' },
    { id: 'd3', door: 'D3', carrier: 'USPS', trailer: '—', driver: '—', status: 'available', orders: 0, pallets: 0, eta: '—' },
    { id: 'd4', door: 'D4', carrier: 'OnTrac', trailer: 'TL-117X', driver: 'Lisa C.', status: 'waiting', orders: 15, pallets: 3, eta: '11:00' },
    { id: 'd5', door: 'D5', carrier: 'Amazon Relay', trailer: 'TL-6624', driver: 'Tom W.', status: 'loading', orders: 56, pallets: 10, eta: '—' },
    { id: 'd6', door: 'D6', carrier: '—', trailer: '—', driver: '—', status: 'available', orders: 0, pallets: 0, eta: '—' },
  ]

  const confirmLoad = useMutation({
    mutationFn: async (doorId: string) => { await new Promise(r => setTimeout(r, 300)); return doorId },
    onSuccess: () => addToast({ type: 'success', title: 'Loading confirmed' }),
  })

  const kpis = [
    { title: 'Loading Now', value: dockDoors.filter(d => d.status === 'loading').length.toString(), icon: <Truck className="w-5 h-5" />, color: 'primary' as const, trend: null },
    { title: 'Ready to Ship', value: dockDoors.filter(d => d.status === 'ready').length.toString(), icon: <CheckCircle className="w-5 h-5" />, color: 'success' as const, trend: null },
    { title: "Today's Shipments", value: '156', icon: <Ship className="w-5 h-5" />, color: 'info' as const, trend: { value: 8, isUp: true } },
    { title: 'Pallets Loaded', value: '34', icon: <Box className="w-5 h-5" />, color: 'warning' as const, trend: { value: 15, isUp: true } },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Truck className="w-7 h-7 text-orange-500" />
            Loading Dock
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Manage loading, dispatch, and carrier coordination</p>
        </div>
        <button onClick={() => navigate('/shipping')} className="enterprise-btn-secondary text-sm flex items-center gap-1.5 px-4 py-2">
          <Ship className="w-4 h-4" /> Full Shipping View
        </button>
      </div>

      <div className="grid grid-cols-4 gap-4">
        {kpis.map(k => (
          <EnterpriseKPICard key={k.title} title={k.title} value={k.value} icon={k.icon} color={k.color} trend={k.trend} />
        ))}
      </div>

      {/* Scan */}
      <div className="enterprise-card p-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-orange-50 dark:bg-orange-900/20 flex items-center justify-center">
            <QrCode className="w-5 h-5 text-orange-600" />
          </div>
          <div className="relative flex-1">
            <Autocomplete value={scanInput} onChange={setScanInput} placeholder="Scan trailer barcode or shipment ID..." minChars={0} showSearchIcon={false} clearable={false} inputClassName="w-full pl-10 pr-4 py-3 text-sm border-2 border-orange-200 dark:border-orange-800 rounded-xl bg-[var(--surface-base)] text-[var(--text-primary)] focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-orange-500" />
            <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-orange-400 pointer-events-none" />
          </div>
          <button className="enterprise-btn-primary text-sm px-6 py-3 bg-orange-600 hover:bg-orange-700"><Search className="w-4 h-4" /> Find</button>
        </div>
      </div>

      {/* Dock Doors Grid */}
      <div className="grid grid-cols-3 gap-4">
        {dockDoors.map(door => (
          <div key={door.id} className={clsx('enterprise-card p-4 border-l-4 transition-all',
            door.status === 'loading' ? 'border-l-green-500 bg-[var(--nexus-success-50)]/30 dark:bg-[var(--nexus-success-900)]/5' :
            door.status === 'ready' ? 'border-l-blue-500 bg-[var(--nexus-primary-50)]/30 dark:bg-[var(--nexus-primary-900)]/5' :
            door.status === 'waiting' ? 'border-l-amber-500 bg-[var(--nexus-warning-50)]/30 dark:bg-[var(--nexus-warning-900)]/5' :
            'border-l-gray-300 dark:border-l-gray-600')}>
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <div className={clsx('w-10 h-10 rounded-xl flex items-center justify-center',
                  door.status === 'loading' ? 'bg-[var(--nexus-success-100)] dark:bg-[var(--nexus-success-900)]/20 text-[var(--nexus-success-600)]' :
                  door.status === 'ready' ? 'bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)]/20 text-[var(--nexus-primary-600)]' :
                  door.status === 'waiting' ? 'bg-[var(--nexus-warning-100)] dark:bg-[var(--nexus-warning-900)]/20 text-[var(--nexus-warning-600)]' :
                  'bg-[var(--surface-muted)] bg-[var(--surface-base)] text-[var(--text-tertiary)]')}>
                  <Truck className="w-5 h-5" />
                </div>
                <div>
                  <span className="text-base font-bold text-[var(--text-primary)]">{door.door}</span>
                  <p className="text-xs text-[var(--text-secondary)]">{door.carrier}</p>
                </div>
              </div>
              <EnterpriseStatusBadge status={door.status === 'loading' ? 'success' : door.status === 'ready' ? 'info' : door.status === 'waiting' ? 'warning' : 'pending'} label={door.status} />
            </div>

            {door.status !== 'available' && (
              <div className="space-y-2 text-xs text-[var(--text-secondary)]">
                <div className="flex items-center justify-between"><span>Trailer</span><span className="font-medium text-[var(--text-secondary)]">{door.trailer}</span></div>
                <div className="flex items-center justify-between"><span>Driver</span><span className="font-medium text-[var(--text-secondary)]">{door.driver}</span></div>
                <div className="flex items-center justify-between"><span>Orders</span><span className="font-medium text-[var(--text-secondary)]">{door.orders}</span></div>
                <div className="flex items-center justify-between"><span>Pallets</span><span className="font-medium text-[var(--text-secondary)]">{door.pallets}</span></div>
                {door.status === 'ready' && (
                  <button onClick={() => confirmLoad.mutate(door.id)} className="w-full mt-2 enterprise-btn-primary text-xs py-2 bg-[var(--nexus-success-600)] hover:bg-[var(--nexus-success-700)] flex items-center justify-center gap-1">
                    <CheckCircle className="w-3.5 h-3.5" /> Confirm Load Complete
                  </button>
                )}
                {door.status === 'loading' && (
                  <button onClick={() => confirmLoad.mutate(door.id)} className="w-full mt-2 enterprise-btn-secondary text-xs py-2 flex items-center justify-center gap-1">
                    <ClipboardCheck className="w-3.5 h-3.5" /> Mark Ready
                  </button>
                )}
              </div>
            )}

            {door.status === 'available' && (
              <div className="py-4 text-center">
                <p className="text-xs text-[var(--text-tertiary)]">Door available for assignment</p>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
