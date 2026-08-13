import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Package, PackageCheck, ChevronLeft } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../hooks/useToast'
import * as packingApi from '../../api/packing'
import { RfButton, RfCard, RfBadge, RfEmpty, ScreenHeader } from '../components'

const ACTIVE_STATUSES = ['PENDING_PACK', 'PENDING', 'PACKING']

export default function PackScreen() {
  const { user } = useAuth()
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const workerName = user?.username ?? 'worker'
  const [activeId, setActiveId] = useState<string | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const idParam = searchParams.get('id')

  useEffect(() => {
    if (idParam && idParam !== activeId) setActiveId(idParam)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idParam])

  const closeDetail = () => {
    setActiveId(null)
    setSearchParams({}, { replace: true })
  }

  const { data: packages = [], isLoading } = useQuery({
    queryKey: ['rf-packages'],
    queryFn: async () => {
      const res = await packingApi.getPackages()
      const d = res.data as any
      const list = Array.isArray(d) ? d : (d?.content ?? [])
      return list.filter((p: any) => ACTIVE_STATUSES.includes(p.status))
    },
  })

  const startMutation = useMutation({
    mutationFn: async (id: string) => await packingApi.startPacking(id),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['rf-packages'] })
      if (res.success) addToast({ type: 'success', title: 'Packing started' })
      else addToast({ type: 'error', title: res.error || 'Failed to start packing' })
    },
  })

  const completeMutation = useMutation({
    mutationFn: async (id: string) => await packingApi.completePacking(id, workerName),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['rf-packages'] })
      if (res.success) {
        addToast({ type: 'success', title: 'Package completed' })
        closeDetail()
      } else {
        addToast({ type: 'error', title: res.error || 'Failed to complete packing' })
      }
    },
  })

  const inList = !!packages.find((p: any) => p.id === activeId)

  const { data: directPackage } = useQuery({
    queryKey: ['rf-package-direct', activeId],
    queryFn: async () => {
      if (!activeId || inList) return null
      const res = await packingApi.getPackage(activeId)
      return res.success ? res.data : null
    },
    enabled: !!activeId && !inList,
  })

  const active = packages.find((p: any) => p.id === activeId) ?? directPackage ?? null

  if (!active) {
    return (
      <div className="px-4 pt-4 pb-24">
        <ScreenHeader title="Pack" subtitle="Pack orders for shipment" />
        {isLoading ? (
          <div className="py-16 text-center text-[var(--text-tertiary)]">Loading…</div>
        ) : packages.length === 0 ? (
          <RfEmpty icon={<Package className="w-10 h-10" />} text="Nothing to pack" />
        ) : (
          <div className="space-y-3">
            {packages.map((p: any) => {
              const inProgress = p.status === 'PACKING'
              return (
                <RfCard key={p.id} className="p-4 active:scale-[0.99] transition-transform">
                  <button type="button" className="w-full text-left" onClick={() => setActiveId(p.id)}>
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-[var(--text-primary)] truncate">
                        Package #{p.id?.slice(0, 8)}
                      </span>
                      <RfBadge tone={inProgress ? 'info' : 'warning'}>{p.status.replace('_', ' ')}</RfBadge>
                    </div>
                    <p className="text-xs text-[var(--text-secondary)] mt-1">Order: {p.orderId?.slice(0, 8) || '—'}</p>
                    <p className="text-xs text-[var(--text-secondary)] mt-0.5">Items: {p.itemCount ?? '—'} · Box: {p.boxName || p.packageType}</p>
                  </button>
                </RfCard>
              )
            })}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="px-4 pt-4 pb-24">
      <div className="flex items-center gap-2 mb-1">
        <button type="button" onClick={closeDetail} className="p-1.5 -ml-1.5 rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-muted)]">
          <ChevronLeft className="w-6 h-6" />
        </button>
        <ScreenHeader title="Package" subtitle={`#${active.id?.slice(0, 8)}`} />
      </div>

      <RfCard className="p-4 space-y-2 mb-5">
        <div className="flex justify-between text-sm">
          <span className="text-[var(--text-secondary)]">Order</span>
          <span className="font-medium text-[var(--text-primary)]">{active.orderId}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-[var(--text-secondary)]">Items</span>
          <span className="font-medium text-[var(--text-primary)]">{active.itemCount ?? 0}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-[var(--text-secondary)]">Box</span>
          <span className="font-medium text-[var(--text-primary)]">{active.boxName || active.packageType}</span>
        </div>
      </RfCard>

      {!ACTIVE_STATUSES.includes(active.status) ? (
        <div className="flex flex-col items-center gap-2 py-4">
          <RfBadge tone={active.status === 'SHIPPED' ? 'success' : 'default'}>{active.status.replace('_', ' ')}</RfBadge>
          <p className="text-xs text-[var(--text-secondary)]">This package is no longer active</p>
        </div>
      ) : active.status === 'PACKING' ? (
        <RfButton
          variant="success"
          full
          disabled={completeMutation.isPending}
          onClick={() => completeMutation.mutate(active.id)}
        >
          <PackageCheck className="w-5 h-5 inline mr-1 -mt-0.5" />
          Complete &amp; Packed
        </RfButton>
      ) : (
        <RfButton full disabled={startMutation.isPending} onClick={() => startMutation.mutate(active.id)}>
          Start Packing
        </RfButton>
      )}
    </div>
  )
}
