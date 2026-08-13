import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ClipboardCheck, CheckCircle2 } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../hooks/useToast'
import * as cycleCountsApi from '../../api/cycleCounts'
import { RfButton, RfCard, RfBadge, RfEmpty, ScreenHeader } from '../components'

export default function CountScreen() {
  const { user } = useAuth()
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const workerName = user?.username ?? 'worker'
  const [quantities, setQuantities] = useState<Record<string, string>>({})

  const { data: counts = [], isLoading } = useQuery({
    queryKey: ['rf-counts'],
    queryFn: async () => {
      const res = await cycleCountsApi.getCycleCounts({})
      const d = res.data as any
      return (Array.isArray(d) ? d : (d?.content ?? [])).filter(
        (c: any) => !c.countedAt && c.status !== 'COUNTED' && c.status !== 'COMPLETED',
      )
    },
  })

  const countMutation = useMutation({
    mutationFn: async ({ id, qty }: { id: string; qty: number }) => await cycleCountsApi.performCount(id, qty, workerName),
    onSuccess: (res, vars) => {
      queryClient.invalidateQueries({ queryKey: ['rf-counts'] })
      setQuantities((prev) => ({ ...prev, [vars.id]: '' }))
      if (res.success) addToast({ type: 'success', title: 'Count recorded' })
      else addToast({ type: 'error', title: res.error || 'Failed to record count' })
    },
    onError: (e: any) => addToast({ type: 'error', title: e?.message || 'Failed to record count' }),
  })

  return (
    <div className="px-4 pt-4 pb-24">
      <ScreenHeader title="Cycle Count" subtitle="Record on-hand quantities" />
      {isLoading ? (
        <div className="py-16 text-center text-[var(--text-tertiary)]">Loading…</div>
      ) : counts.length === 0 ? (
        <RfEmpty icon={<ClipboardCheck className="w-10 h-10" />} text="No cycle counts pending" />
      ) : (
        <div className="space-y-3">
          {counts.map((c: any) => (
            <RfCard key={c.id} className="p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-[var(--text-primary)] truncate">{c.productName || c.sku}</p>
                  <p className="text-xs text-[var(--text-secondary)] mt-0.5">SKU: {c.sku}</p>
                </div>
                <div className="text-right shrink-0">
                  <p className="text-sm font-semibold text-[var(--text-primary)]">Expected: {c.expectedQty}</p>
                  <RfBadge tone="info">{c.status}</RfBadge>
                </div>
              </div>
              <div className="mt-3 flex items-center gap-2">
                <input
                  type="number"
                  inputMode="numeric"
                  value={quantities[c.id] ?? ''}
                  onChange={(e) => setQuantities((prev) => ({ ...prev, [c.id]: e.target.value }))}
                  placeholder="Counted qty"
                  className="flex-1 rounded-xl bg-[var(--surface-sunken)] text-[var(--text-primary)] px-4 py-3 text-base placeholder:text-[var(--text-tertiary)] focus:outline-none focus:ring-2 focus:ring-[var(--nexus-primary-600)]"
                />
                <RfButton
                  variant="success"
                  disabled={countMutation.isPending || !quantities[c.id]}
                  onClick={() => countMutation.mutate({ id: c.id, qty: Number(quantities[c.id]) })}
                >
                  <CheckCircle2 className="w-5 h-5 inline mr-1 -mt-0.5" />
                  Count
                </RfButton>
              </div>
            </RfCard>
          ))}
        </div>
      )}
    </div>
  )
}
