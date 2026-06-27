import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Play, Package, CheckCircle, Clock, AlertTriangle, Printer, Truck, Plus, Loader2,
} from 'lucide-react'
import StatusBadge from '../components/common/StatusBadge'
import { useToast } from '../hooks/useToast'
import * as ordersApi from '../api/orders'
import { Order } from '../types'

export default function FulfillmentPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<'pending' | 'allocated' | 'shipped'>('pending')

  const { data: orders = [], isLoading } = useQuery({
    queryKey: ['fulfillment-orders', activeTab],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (activeTab === 'pending') params.status = 'PENDING'
      else if (activeTab === 'allocated') params.status = 'ALLOCATED'
      else if (activeTab === 'shipped') params.status = 'SHIPPED'
      const res = await ordersApi.getOrders(params)
      const d = res.data
      if (Array.isArray(d)) return d as Order[]
      if (d && typeof d === 'object' && 'content' in d) return (d as { content: Order[] }).content
      return []
    },
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['fulfillment-orders'] })
    queryClient.invalidateQueries({ queryKey: ['orders'] })
  }

  const confirmMutation = useMutation({
    mutationFn: (id: string) => ordersApi.confirmOrder(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Order confirmed' }) },
    onError: () => addToast({ type: 'error', title: 'Confirmation failed' }),
  })

  const allocateMutation = useMutation({
    mutationFn: (id: string) => ordersApi.allocateOrder(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Order allocated' }) },
    onError: () => addToast({ type: 'error', title: 'Allocation failed' }),
  })

  const shipMutation = useMutation({
    mutationFn: (id: string) => ordersApi.shipOrder(id, 'auto', 'TN-' + Date.now()),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Order shipped' }) },
    onError: () => addToast({ type: 'error', title: 'Shipping failed' }),
  })

  const tabs = [
    { id: 'pending' as const, label: 'Pending', count: orders.length },
    { id: 'allocated' as const, label: 'Allocated', count: orders.length },
    { id: 'shipped' as const, label: 'Shipped', count: orders.length },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Fulfillment</h1>
          <p className="text-sm text-gray-500 mt-1">Manually confirm, allocate, and ship orders</p>
        </div>
      </div>

      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
              activeTab === tab.id ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab.label} ({tab.count})
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center p-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
        </div>
      ) : orders.length === 0 ? (
        <div className="card p-12 text-center text-gray-500">
          <Package className="w-12 h-12 mx-auto mb-3 text-gray-300" />
          <p className="font-medium">No {activeTab} orders</p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50/50">
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Order</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Customer</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Items</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Status</th>
                  <th className="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {orders.map((order) => (
                  <tr key={order.id} className="hover:bg-gray-50">
                    <td className="px-6 py-3 text-sm font-medium text-primary-600">{order.orderNumber || order.id}</td>
                    <td className="px-6 py-3 text-sm text-gray-700">{order.customerName}</td>
                    <td className="px-6 py-3 text-sm text-gray-500">{order.items?.length || 0} items</td>
                    <td className="px-6 py-3"><StatusBadge status={order.status as any} /></td>
                    <td className="px-6 py-3 text-right">
                      {order.status === 'PENDING' && (
                        <button className="btn-primary text-xs" onClick={() => confirmMutation.mutate(order.id)} disabled={confirmMutation.isPending}>
                          {confirmMutation.isPending ? <Loader2 className="w-3 h-3 animate-spin" /> : <CheckCircle className="w-3 h-3" />}
                          Confirm
                        </button>
                      )}
                      {order.status === 'CONFIRMED' && (
                        <button className="btn-primary text-xs" onClick={() => allocateMutation.mutate(order.id)} disabled={allocateMutation.isPending}>
                          {allocateMutation.isPending ? <Loader2 className="w-3 h-3 animate-spin" /> : <Package className="w-3 h-3" />}
                          Allocate
                        </button>
                      )}
                      {order.status === 'ALLOCATED' && (
                        <button className="btn-primary text-xs" onClick={() => shipMutation.mutate(order.id)} disabled={shipMutation.isPending}>
                          {shipMutation.isPending ? <Loader2 className="w-3 h-3 animate-spin" /> : <Truck className="w-3 h-3" />}
                          Ship
                        </button>
                      )}
                      {order.status === 'SHIPPED' && (
                        <span className="text-xs text-green-600 font-medium">Shipped</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
