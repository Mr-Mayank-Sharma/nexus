import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Truck, CheckCircle2 } from 'lucide-react'
import { useToast } from '../../hooks/useToast'
import * as shippingApi from '../../api/shipping'
import { RfButton, RfCard, RfBadge, RfEmpty, ScreenHeader } from '../components'

export default function ShipScreen() {
  const { addToast } = useToast()
  const queryClient = useQueryClient()

  const { data: shipments = [], isLoading } = useQuery({
    queryKey: ['rf-shipments'],
    queryFn: async () => {
      const res = await shippingApi.getShipments()
      const d = res.data as any
      return (Array.isArray(d) ? d : (d?.content ?? [])).filter(
        (s: any) => s.status !== 'SHIPPED' && s.status !== 'DELIVERED' && s.status !== 'completed',
      )
    },
  })

  const shipMutation = useMutation({
    mutationFn: async (id: string) => await shippingApi.markShipped(id),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['rf-shipments'] })
      if (res.success) addToast({ type: 'success', title: 'Marked as shipped' })
      else addToast({ type: 'error', title: res.error || 'Failed to mark shipped' })
    },
    onError: (e: any) => addToast({ type: 'error', title: e?.message || 'Failed to mark shipped' }),
  })

  return (
    <div className="px-4 pt-4 pb-24">
      <ScreenHeader title="Ship" subtitle="Confirm outbound shipments" />
      {isLoading ? (
        <div className="py-16 text-center text-[var(--text-tertiary)]">Loading…</div>
      ) : shipments.length === 0 ? (
        <RfEmpty icon={<Truck className="w-10 h-10" />} text="Nothing waiting to ship" />
      ) : (
        <div className="space-y-3">
          {shipments.map((s: any) => (
            <RfCard key={s.id} className="p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-[var(--text-primary)] truncate">
                    Shipment #{s.id?.slice(0, 8)}
                  </p>
                  <p className="text-xs text-[var(--text-secondary)] mt-0.5">Tracking: {s.trackingNumber || '—'}</p>
                  <p className="text-xs text-[var(--text-secondary)]">Carrier: {s.carrierName || s.carrierId || '—'}</p>
                </div>
                <RfBadge tone="default">{s.status}</RfBadge>
              </div>
              <div className="mt-3">
                <RfButton
                  variant="success"
                  full
                  disabled={shipMutation.isPending}
                  onClick={() => shipMutation.mutate(s.id)}
                >
                  <CheckCircle2 className="w-5 h-5 inline mr-1 -mt-0.5" />
                  Mark Shipped
                </RfButton>
              </div>
            </RfCard>
          ))}
        </div>
      )}
    </div>
  )
}
