import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { PackagePlus, CheckCircle2 } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../hooks/useToast'
import * as receiptsApi from '../../api/inventoryReceipts'
import { RfButton, RfCard, RfBadge, RfEmpty, ScreenHeader } from '../components'

export default function ReceiveScreen() {
  const { user } = useAuth()
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const workerName = user?.username ?? 'worker'

  const { data: receipts = [], isLoading } = useQuery({
    queryKey: ['rf-receipts'],
    queryFn: async () => {
      const res = await receiptsApi.getReceipts({})
      const d = res.data as any
      const list = Array.isArray(d) ? d : (d?.content ?? [])
      return list.filter((r: any) => !r.receivedAt && r.status !== 'RECEIVED' && r.status !== 'COMPLETED')
    },
  })

  const receiveMutation = useMutation({
    mutationFn: async (id: string) => await receiptsApi.receiveInventory(id, workerName),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['rf-receipts'] })
      if (res.success) addToast({ type: 'success', title: 'Inventory received' })
      else addToast({ type: 'error', title: res.error || 'Failed to receive' })
    },
    onError: (e: any) => addToast({ type: 'error', title: e?.message || 'Failed to receive' }),
  })

  return (
    <div className="px-4 pt-4 pb-24">
      <ScreenHeader title="Receive" subtitle="Receive incoming inventory" />
      {isLoading ? (
        <div className="py-16 text-center text-[var(--text-tertiary)]">Loading…</div>
      ) : receipts.length === 0 ? (
        <RfEmpty icon={<PackagePlus className="w-10 h-10" />} text="No receipts waiting" />
      ) : (
        <div className="space-y-3">
          {receipts.map((r: any) => (
            <RfCard key={r.id} className="p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-[var(--text-primary)] truncate">{r.productName || r.sku}</p>
                  <p className="text-xs text-[var(--text-secondary)] mt-0.5">SKU: {r.sku}</p>
                  <p className="text-xs text-[var(--text-secondary)]">Ref: {r.referenceNumber || '—'}</p>
                  {r.lotNumber && <p className="text-xs text-[var(--text-secondary)]">Lot: {r.lotNumber}</p>}
                </div>
                <div className="text-right shrink-0">
                  <p className="text-lg font-bold text-[var(--text-primary)]">×{r.quantity}</p>
                  <RfBadge tone="warning">{r.receiptType || 'RECEIPT'}</RfBadge>
                </div>
              </div>
              <div className="mt-3">
                <RfButton
                  variant="success"
                  full
                  disabled={receiveMutation.isPending}
                  onClick={() => receiveMutation.mutate(r.id)}
                >
                  <CheckCircle2 className="w-5 h-5 inline mr-1 -mt-0.5" />
                  Receive
                </RfButton>
              </div>
            </RfCard>
          ))}
        </div>
      )}
    </div>
  )
}
