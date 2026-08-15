import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { PackageCheck, ChevronLeft, CheckCircle2, PackageOpen, WifiOff } from 'lucide-react'
import clsx from 'clsx'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../hooks/useToast'
import { useOfflineQueue } from '../../hooks/useOfflineQueue'
import * as pickingApi from '../../api/picking'
import type { Picklist, PicklistItem } from '../../types'
import { RfButton, RfCard, RfBadge, RfEmpty, ScreenHeader } from '../components'
import ScannerOverlay from '../ScannerOverlay'

function matchesItem(code: string, item: PicklistItem): boolean {
  const c = code.trim().toLowerCase()
  return (
    c === String(item.sku ?? '').trim().toLowerCase() ||
    c === String(item.id ?? '').trim().toLowerCase() ||
    (!!item.orderItemId && c === String(item.orderItemId).trim().toLowerCase())
  )
}

export default function PickScreen() {
  const { user } = useAuth()
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const workerId = user?.id ?? 'worker'

  const offlineQueue = useOfflineQueue({
    executor: async (action) => {
      if (action.type === 'PICK_ITEM') {
        const { itemId, staffId } = action.payload
        await pickingApi.pickItem(String(itemId), String(staffId))
      } else if (action.type === 'COMPLETE_PICKLIST') {
        await pickingApi.completePicklist(String(action.payload.picklistId))
      }
    },
  })

  const isOffline = () => !navigator.onLine

  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [scanItem, setScanItem] = useState<PicklistItem | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const idParam = searchParams.get('id')

  useEffect(() => {
    if (idParam && idParam !== selectedId) setSelectedId(idParam)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idParam])

  const closeDetail = () => {
    setSelectedId(null)
    setSearchParams({}, { replace: true })
  }

  const { data: picklists = [], isLoading } = useQuery({
    queryKey: ['rf-picklists'],
    queryFn: async () => {
      const res = await pickingApi.getPicklists()
      const d = res.data
      const list: Picklist[] = Array.isArray(d) ? d : (d?.content ?? [])
      return list.filter((p) => p.status === 'OPEN' || p.status === 'IN_PROGRESS')
    },
  })

  const inList = !!picklists.find((p) => p.id === selectedId)

  const { data: directPicklist } = useQuery({
    queryKey: ['rf-picklist-direct', selectedId],
    queryFn: async () => {
      if (!selectedId || inList) return null
      const res = await pickingApi.getPicklist(selectedId)
      return res.success ? res.data : null
    },
    enabled: !!selectedId && !inList,
  })

  const selected = picklists.find((p) => p.id === selectedId) ?? directPicklist ?? null

  const { data: items = [] } = useQuery({
    queryKey: ['rf-pick-items', selectedId],
    queryFn: async () => {
      if (!selectedId) return []
      const res = await pickingApi.getPicklistItems(selectedId)
      return (Array.isArray(res.data) ? res.data : []) as PicklistItem[]
    },
    enabled: !!selectedId,
  })

  const pickedCount = items.filter((i) => i.status === 'PICKED').length
  const progress = items.length > 0 ? Math.round((pickedCount / items.length) * 100) : 0

  const pickMutation = useMutation({
    mutationFn: async ({ itemId }: { itemId: string }) => await pickingApi.pickItem(itemId, workerId),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['rf-picklists'] })
      queryClient.invalidateQueries({ queryKey: ['rf-pick-items'] })
      if (res.success) {
        addToast({ type: 'success', title: 'Item picked' })
        setScanItem(null)
      } else {
        addToast({ type: 'error', title: res.error || 'Failed to pick item' })
      }
    },
    onError: (e: any) => addToast({ type: 'error', title: e?.message || 'Failed to pick item' }),
  })

  const completeMutation = useMutation({
    mutationFn: async (id: string) => await pickingApi.completePicklist(id),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['rf-picklists'] })
      if (res.success) {
        addToast({ type: 'success', title: 'Picklist completed' })
        closeDetail()
      } else {
        addToast({ type: 'error', title: res.error || 'Failed to complete picklist' })
      }
    },
  })

  const complete = (id: string) => {
    if (isOffline() || offlineQueue.pendingCount > 0) {
      offlineQueue.enqueue('COMPLETE_PICKLIST', { picklistId: id })
      addToast({ type: 'info', title: 'Queued offline — will sync when online' })
      queryClient.invalidateQueries({ queryKey: ['rf-picklists'] })
      return
    }
    completeMutation.mutate(id)
  }

  const handleScan = (code: string) => {
    if (!scanItem) return
    if (matchesItem(code, scanItem)) {
      if (isOffline() || offlineQueue.pendingCount > 0) {
        offlineQueue.enqueue('PICK_ITEM', { itemId: scanItem.id, staffId: workerId })
        addToast({ type: 'info', title: 'Queued offline — will sync when online' })
        setScanItem(null)
      } else {
        pickMutation.mutate({ itemId: scanItem.id })
      }
    } else {
      addToast({ type: 'error', title: `Mismatch — expected "${scanItem.sku}"` })
    }
  }

  if (!selected) {
    return (
      <div className="px-4 pt-4 pb-24">
        <ScreenHeader title="Pick" subtitle="Select a picklist to start picking" />
        {offlineQueue.pendingCount > 0 && (
          <div className="mb-3 flex items-center gap-2 rounded-xl border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-300">
            <WifiOff className="w-4 h-4 shrink-0" />
            <span>
              {offlineQueue.pendingCount} action{offlineQueue.pendingCount === 1 ? '' : 's'} queued offline
              {offlineQueue.isFlushing ? ' — syncing…' : ' — will sync when online'}
            </span>
          </div>
        )}
        {isLoading ? (
          <div className="py-16 text-center text-[var(--text-tertiary)]">Loading…</div>
        ) : picklists.length === 0 ? (
          <RfEmpty icon={<PackageCheck className="w-10 h-10" />} text="No open picklists right now" />
        ) : (
          <div className="space-y-3">
            {picklists.map((pl) => {
              const done = pl.totalItems > 0 ? Math.round((pl.pickedItems / pl.totalItems) * 100) : 0
              return (
                <RfCard key={pl.id} className="p-4 active:scale-[0.99] transition-transform">
                  <button type="button" className="w-full text-left" onClick={() => setSelectedId(pl.id)}>
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-[var(--text-primary)]">{pl.name}</span>
                      <RfBadge tone={pl.status === 'IN_PROGRESS' ? 'info' : 'default'}>{pl.status.replace('_', ' ')}</RfBadge>
                    </div>
                    <div className="flex items-center justify-between mt-3">
                      <span className="text-xs text-[var(--text-secondary)]">
                        {pl.pickedItems}/{pl.totalItems} items · {pl.waveType}
                      </span>
                      <span className="text-xs font-semibold text-[var(--nexus-primary-600)]">{done}%</span>
                    </div>
                    <div className="mt-2 h-1.5 rounded-full bg-[var(--surface-muted)] overflow-hidden">
                      <div className="h-full rounded-full bg-[var(--nexus-primary-600)] transition-all" style={{ width: `${done}%` }} />
                    </div>
                  </button>
                </RfCard>
              )
            })}
          </div>
        )}
      </div>
    )
  }

  const pendingItems = items.filter((i) => i.status !== 'PICKED')
  const canComplete = selected?.status !== 'COMPLETED' && items.length > 0 && pendingItems.length === 0

  return (
    <div className="px-4 pt-4 pb-24">
      <div className="flex items-center gap-2 mb-1">
        <button type="button" onClick={closeDetail} className="p-1.5 -ml-1.5 rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-muted)]">
          <ChevronLeft className="w-6 h-6" />
        </button>
        <ScreenHeader title={selected.name} subtitle={`${pickedCount}/${items.length} picked`} />
      </div>

      {offlineQueue.pendingCount > 0 && (
        <div className="mb-3 flex items-center gap-2 rounded-xl border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-300">
          <WifiOff className="w-4 h-4 shrink-0" />
          <span>
            {offlineQueue.pendingCount} action{offlineQueue.pendingCount === 1 ? '' : 's'} queued offline
            {offlineQueue.isFlushing ? ' — syncing…' : ' — will sync when online'}
          </span>
        </div>
      )}

      <div className="mb-4 h-2 rounded-full bg-[var(--surface-muted)] overflow-hidden">
        <div className="h-full rounded-full bg-[var(--nexus-success-600)] transition-all" style={{ width: `${progress}%` }} />
      </div>

      <div className="space-y-3">
        {items.length === 0 ? (
          <RfEmpty icon={<PackageOpen className="w-10 h-10" />} text="No items on this picklist" />
        ) : (
          items.map((item) => {
            const picked = item.status === 'PICKED'
            return (
              <RfCard key={item.id} className={clsx('p-4', picked && 'opacity-60')}>
                <div className="flex items-start gap-3">
                  {picked ? (
                    <CheckCircle2 className="w-6 h-6 text-[var(--nexus-success-600)] shrink-0 mt-0.5" />
                  ) : (
                    <button
                      type="button"
                      onClick={() => setScanItem(item)}
                      className="w-6 h-6 shrink-0 mt-0.5 rounded-md border-2 border-[var(--nexus-primary-600)] text-[var(--nexus-primary-600)] flex items-center justify-center text-xs font-bold active:scale-90 transition-transform"
                    >
                      {item.sku?.slice(0, 2).toUpperCase() ?? '?'}
                    </button>
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-[var(--text-primary)] truncate">{item.productName || item.sku}</p>
                    <p className="text-xs text-[var(--text-secondary)] mt-0.5">SKU: {item.sku}</p>
                    {item.fromLocation && <p className="text-xs text-[var(--text-secondary)]">Loc: {item.fromLocation}</p>}
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-bold text-[var(--text-primary)]">×{item.quantity}</p>
                    {picked ? (
                      <RfBadge tone="success">Picked</RfBadge>
                    ) : (
                      <button type="button" onClick={() => setScanItem(item)} className="text-xs font-semibold text-[var(--nexus-primary-600)]">
                        Scan →
                      </button>
                    )}
                  </div>
                </div>
              </RfCard>
            )
          })
        )}
      </div>

      <div className="mt-5">
        <RfButton variant="success" full disabled={!canComplete} onClick={() => complete(selected.id)}>
          Complete Picklist
        </RfButton>
      </div>

      <ScannerOverlay
        open={!!scanItem}
        title={`Scan ${scanItem?.sku ?? ''}`}
        placeholder="Scan item barcode…"
        onScan={handleScan}
        onClose={() => setScanItem(null)}
      />
    </div>
  )
}
