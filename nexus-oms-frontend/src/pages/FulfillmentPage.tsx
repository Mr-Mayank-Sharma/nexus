import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  Play, Package, CheckCircle, Clock, AlertTriangle, Printer, Truck, Plus,
  Loader2, ListOrdered, ClipboardList, TrendingUp, ArrowRight, Eye, Search,
  XCircle, RotateCcw,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import * as ordersApi from '../api/orders'
import * as pickingApi from '../api/picking'
import { Order } from '../types'
import { EnterpriseKPICard, EnterpriseStatusBadge, EnterpriseTabs } from '../components/enterprise'
import type { Tab } from '../components/enterprise'

type FulfillmentTab = 'open' | 'in-progress' | 'completed'

export default function FulfillmentPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState<FulfillmentTab>('open')

  const { data: openOrders = [], isLoading: loadingOpen } = useQuery({
    queryKey: ['fulfillment-open'],
    queryFn: async () => {
      const res = await ordersApi.getOrders({ status: 'ALLOCATED' })
      const d = res.data
      if (Array.isArray(d)) return d as Order[]
      if (d && typeof d === 'object' && 'content' in d) return (d as { content: Order[] }).content
      return []
    },
  })

  const { data: inProgressOrders = [], isLoading: loadingProgress } = useQuery({
    queryKey: ['fulfillment-in-progress'],
    queryFn: async () => {
      const res = await ordersApi.getOrders({ status: 'IN_PROGRESS' })
      const d = res.data
      if (Array.isArray(d)) return d as Order[]
      if (d && typeof d === 'object' && 'content' in d) return (d as { content: Order[] }).content
      return []
    },
  })

  const { data: completedOrders = [], isLoading: loadingCompleted } = useQuery({
    queryKey: ['fulfillment-completed'],
    queryFn: async () => {
      const res = await ordersApi.getOrders({ status: 'SHIPPED' })
      const d = res.data
      if (Array.isArray(d)) return d as Order[]
      if (d && typeof d === 'object' && 'content' in d) return (d as { content: Order[] }).content
      return []
    },
  })

  const { data: picklists = [] } = useQuery({
    queryKey: ['fulfillment-picklists'],
    queryFn: async () => {
      const res = await pickingApi.getPicklists()
      const d = res.data
      return Array.isArray(d) ? d : (d?.content ?? [])
    },
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['fulfillment-open'] })
    queryClient.invalidateQueries({ queryKey: ['fulfillment-in-progress'] })
    queryClient.invalidateQueries({ queryKey: ['fulfillment-completed'] })
    queryClient.invalidateQueries({ queryKey: ['fulfillment-picklists'] })
    queryClient.invalidateQueries({ queryKey: ['orders'] })
    queryClient.invalidateQueries({ queryKey: ['picklists'] })
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

  const activePicklists = picklists.filter((p: any) => ['OPEN', 'IN_PROGRESS'].includes(p.status)).length
  const totalAwaiting = openOrders.length + inProgressOrders.length
  const completedToday = completedOrders.length

  const isLoading = activeTab === 'open' ? loadingOpen : activeTab === 'in-progress' ? loadingProgress : loadingCompleted
  const orders = activeTab === 'open' ? openOrders : activeTab === 'in-progress' ? inProgressOrders : completedOrders

  const tabs: Tab[] = [
    { id: 'open', label: 'Open', badge: openOrders.length },
    { id: 'in-progress', label: 'In Progress', badge: inProgressOrders.length },
    { id: 'completed', label: 'Completed', badge: completedOrders.length },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2"><Package className="w-6 h-6" />Fulfillment</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">End-to-end order fulfillment pipeline</p>
        </div>
        <div className="flex items-center gap-2">
          <button className="enterprise-btn-secondary text-sm" onClick={() => navigate('/picking')}>
            <ClipboardList className="w-4 h-4" /> Picklists
          </button>
          <button className="enterprise-btn-secondary text-sm" onClick={() => navigate('/packing')}>
            <Package className="w-4 h-4" /> Packing
          </button>
          <button className="enterprise-btn-secondary text-sm" onClick={() => navigate('/shipping')}>
            <Truck className="w-4 h-4" /> Shipping
          </button>
        </div>
      </div>

      {/* KPI Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Awaiting Fulfillment"
          value={totalAwaiting}
          icon={<Clock className="w-5 h-5" />}
          trend={null}
          color="amber"
        />
        <EnterpriseKPICard
          title="Active Picklists"
          value={activePicklists}
          icon={<ClipboardList className="w-5 h-5" />}
          trend={null}
          color="blue"
        />
        <EnterpriseKPICard
          title="In Progress"
          value={inProgressOrders.length}
          icon={<Play className="w-5 h-5" />}
          trend={null}
          color="indigo"
        />
        <EnterpriseKPICard
          title="Completed Today"
          value={completedToday}
          icon={<CheckCircle className="w-5 h-5" />}
          trend={{ value: 12, isPositive: true }}
          color="green"
        />
      </div>

      {/* Pipeline flow visualization */}
      <div className="enterprise-card p-5">
        <div className="flex items-center gap-3 text-sm">
          <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-amber-50 dark:bg-amber-950/30 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800">
            <Clock className="w-4 h-4" />
            <span className="font-medium">{openOrders.length} Open</span>
          </div>
          <ArrowRight className="w-4 h-4 text-gray-300 dark:text-gray-600" />
          <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-blue-50 dark:bg-blue-950/30 text-blue-700 dark:text-blue-400 border border-blue-200 dark:border-blue-800">
            <Play className="w-4 h-4" />
            <span className="font-medium">{inProgressOrders.length} In Progress</span>
          </div>
          <ArrowRight className="w-4 h-4 text-gray-300 dark:text-gray-600" />
          <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-green-50 dark:bg-green-950/30 text-green-700 dark:text-green-400 border border-green-200 dark:border-green-800">
            <CheckCircle className="w-4 h-4" />
            <span className="font-medium">{completedOrders.length} Completed</span>
          </div>
        </div>
      </div>

      <EnterpriseTabs tabs={tabs} activeTab={activeTab} onChange={(t) => setActiveTab(t as FulfillmentTab)} />

      {isLoading ? (
        <div className="enterprise-card flex items-center justify-center p-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
        </div>
      ) : orders.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <Package className="w-12 h-12 mx-auto mb-3 text-gray-300 dark:text-gray-600" />
          <p className="font-medium text-gray-500 dark:text-gray-400">No {activeTab} orders</p>
          <p className="text-sm text-gray-400 dark:text-gray-500 mt-1">
            {activeTab === 'open' ? 'Allocated orders will appear here' :
             activeTab === 'in-progress' ? 'Orders being fulfilled will appear here' :
             'Shipped orders will appear here'}
          </p>
        </div>
      ) : (
        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Order</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Customer</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Items</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {orders.map((order) => (
                  <tr key={order.id} className="enterprise-table-row">
                    <td className="px-4 py-3">
                      <button className="text-sm font-medium text-primary-600 dark:text-primary-400 hover:underline" onClick={() => navigate(`/orders/${order.id}`)}>
                        {order.orderNumber || order.id.slice(0, 8)}
                      </button>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{order.customerName || '—'}</td>
                    <td className="px-4 py-3 text-center text-sm text-gray-500 dark:text-gray-400">{order.items?.length || 0}</td>
                    <td className="px-4 py-3">
                      <EnterpriseStatusBadge
                        status={order.status === 'ALLOCATED' ? 'warning' : order.status === 'IN_PROGRESS' ? 'pending' : 'success'}
                        label={order.status || '—'}
                      />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button className="enterprise-btn-secondary text-xs px-2 py-1" onClick={() => navigate(`/orders/${order.id}`)} title="View Details">
                          <Eye className="w-3.5 h-3.5" />
                        </button>
                        {activeTab === 'open' && (
                          <>
                            <button className="enterprise-btn-primary text-xs px-2 py-1" onClick={() => shipMutation.mutate(order.id)} disabled={shipMutation.isPending}>
                              <Truck className="w-3.5 h-3.5" /> Ship
                            </button>
                          </>
                        )}
                        {activeTab === 'in-progress' && (
                          <span className="text-xs text-blue-600 dark:text-blue-400 font-medium flex items-center gap-1">
                            <Play className="w-3 h-3" /> In Progress
                          </span>
                        )}
                        {activeTab === 'completed' && (
                          <span className="text-xs text-green-600 dark:text-green-400 font-medium">Completed</span>
                        )}
                      </div>
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
