import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { PackageCheck, Package, Truck, ClipboardCheck, PackagePlus, ScanLine, LogOut } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import * as pickingApi from '../../api/picking'
import * as packingApi from '../../api/packing'
import * as receiptsApi from '../../api/inventoryReceipts'
import * as cycleCountsApi from '../../api/cycleCounts'
import { RfCard, ConnectivityPill } from '../components'

function unwrapList<T>(data: unknown): T[] {
  const d = data as { content?: T[] } | T[] | undefined
  if (Array.isArray(d)) return d
  return (d?.content ?? []) as T[]
}

export default function HomeScreen() {
  const { user, logout } = useAuth()

  const { data: picklists = [] } = useQuery({
    queryKey: ['rf-home-picklists'],
    queryFn: async () => {
      const res = await pickingApi.getPicklists()
      return unwrapList<any>(res.data).filter((p) => p.status === 'OPEN' || p.status === 'IN_PROGRESS')
    },
  })

  const { data: packages = [] } = useQuery({
    queryKey: ['rf-home-packages'],
    queryFn: async () => {
      const res = await packingApi.getPackages()
      return unwrapList<any>(res.data).filter((p) => p.status === 'PENDING_PACK' || p.status === 'PACKING' || p.status === 'PENDING')
    },
  })

  const { data: receipts = [] } = useQuery({
    queryKey: ['rf-home-receipts'],
    queryFn: async () => {
      const res = await receiptsApi.getReceipts({})
      return unwrapList<any>(res.data).filter((r) => !r.receivedAt && r.status !== 'RECEIVED' && r.status !== 'COMPLETED')
    },
  })

  const { data: counts = [] } = useQuery({
    queryKey: ['rf-home-counts'],
    queryFn: async () => {
      const res = await cycleCountsApi.getCycleCounts({})
      const page = res.data as any
      return unwrapList<any>(page?.content ?? page).filter((c) => !c.countedAt && c.status !== 'COUNTED' && c.status !== 'COMPLETED')
    },
  })

  const stats = [
    { label: 'To Pick', value: picklists.reduce((n, p) => n + (p.totalItems - p.pickedItems), 0), to: '/rf/pick', icon: PackageCheck, tone: 'text-[var(--nexus-primary-600)]' },
    { label: 'To Pack', value: packages.length, to: '/rf/pack', icon: Package, tone: 'text-[var(--nexus-warning-600)]' },
    { label: 'To Receive', value: receipts.length, to: '/rf/receive', icon: PackagePlus, tone: 'text-[var(--nexus-success-600)]' },
    { label: 'To Count', value: counts.length, to: '/rf/count', icon: ClipboardCheck, tone: 'text-[var(--nexus-info-600)]' },
  ]

  const tasks = [
    { label: 'Picklists open', count: picklists.length, to: '/rf/pick', icon: PackageCheck },
    { label: 'Packages to pack', count: packages.length, to: '/rf/pack', icon: Package },
    { label: 'Receipts to receive', count: receipts.length, to: '/rf/receive', icon: Truck },
    { label: 'Cycle counts pending', count: counts.length, to: '/rf/count', icon: ClipboardCheck },
  ]

  return (
    <div className="px-4 pt-4 pb-24">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-[var(--text-primary)]">Nexus RF</h1>
          <p className="text-xs text-[var(--text-secondary)] mt-0.5">Warehouse handheld · {user?.fullName || user?.username}</p>
        </div>
        <div className="flex items-center gap-3">
          <ConnectivityPill />
          <button type="button" onClick={logout} className="p-2 rounded-lg text-[var(--text-tertiary)] hover:text-[var(--nexus-error-600)]" aria-label="Sign out">
            <LogOut className="w-5 h-5" />
          </button>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-2 mt-4">
        {stats.map((s) => (
          <Link key={s.label} to={s.to} className="rounded-2xl bg-[var(--surface-base)] border border-[var(--border-default)] p-3 flex flex-col items-center gap-1 active:scale-95 transition-transform">
            <s.icon className={`w-5 h-5 ${s.tone}`} />
            <span className="text-xl font-bold text-[var(--text-primary)]">{s.value}</span>
            <span className="text-[10px] text-[var(--text-secondary)]">{s.label}</span>
          </Link>
        ))}
      </div>

      <div className="mt-5">
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-sm font-semibold text-[var(--text-primary)]">Today's Tasks</h2>
          <Link to="/rf/scan" className="flex items-center gap-1 text-xs font-semibold text-emerald-500">
            <ScanLine className="w-3.5 h-3.5" /> Scan
          </Link>
        </div>
        <div className="space-y-3">
          {tasks.map((t) => (
            <Link key={t.label} to={t.to}>
              <RfCard className="p-4 flex items-center justify-between active:scale-[0.99] transition-transform">
                <div className="flex items-center gap-3">
                  <t.icon className="w-5 h-5 text-[var(--text-secondary)]" />
                  <span className="text-sm font-medium text-[var(--text-primary)]">{t.label}</span>
                </div>
                <span className="text-lg font-bold text-[var(--text-primary)]">{t.count}</span>
              </RfCard>
            </Link>
          ))}
        </div>
      </div>
    </div>
  )
}
