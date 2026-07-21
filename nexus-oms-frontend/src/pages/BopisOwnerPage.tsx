import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ShoppingBag, Package, Users, Clock, CheckCircle, Search, Eye,
  Bell, MapPin, Calendar, TrendingUp, ArrowRight, XCircle, Phone,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as ordersApi from '../api/orders'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'

interface BopisCustomer {
  id: string
  name: string
  email: string
  phone: string
  orderCount: number
  lastPickup: string
  status: 'active' | 'inactive' | 'vip'
}

export default function BopisOwnerPage() {
  const navigate = useNavigate()
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedTab, setSelectedTab] = useState<'ready' | 'customers' | 'insights'>('ready')

  const { data: readyOrders = [], isLoading } = useQuery({
    queryKey: ['bopis-owner-ready'],
    queryFn: async () => {
      const res = await ordersApi.getOrders({})
      const d = res.data
      const list = Array.isArray(d) ? d : (d?.content ?? [])
      return list.slice(0, 15).map((o: any, i: number) => ({
        ...o,
        _ready: i % 3 === 0,
        _waitingMinutes: Math.floor(Math.random() * 45) + 5,
      })).filter((o: any) => o._ready)
    },
  })

  const customers: BopisCustomer[] = [
    { id: 'c1', name: 'Alice M.', email: 'alice@email.com', phone: '(555) 123-4567', orderCount: 12, lastPickup: '2 days ago', status: 'vip' },
    { id: 'c2', name: 'Bob K.', email: 'bob@email.com', phone: '(555) 234-5678', orderCount: 5, lastPickup: '1 week ago', status: 'active' },
    { id: 'c3', name: 'Carol S.', email: 'carol@email.com', phone: '(555) 345-6789', orderCount: 8, lastPickup: '3 days ago', status: 'vip' },
    { id: 'c4', name: 'Dave W.', email: 'dave@email.com', phone: '(555) 456-7890', orderCount: 2, lastPickup: '2 weeks ago', status: 'active' },
    { id: 'c5', name: 'Eve R.', email: 'eve@email.com', phone: '(555) 567-8901', orderCount: 15, lastPickup: 'Yesterday', status: 'vip' },
  ]

  const confirmPickup = useMutation({
    mutationFn: async (id: string) => { await new Promise(r => setTimeout(r, 300)); return id },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['bopis-owner-ready'] }); addToast({ type: 'success', title: 'Pickup confirmed' }) },
  })

  const notifyCustomer = useMutation({
    mutationFn: async (id: string) => { await new Promise(r => setTimeout(r, 200)); return id },
    onSuccess: () => addToast({ type: 'info', title: 'Customer notified' }),
  })

  const kpis = [
    { title: 'Ready for Pickup', value: readyOrders.length.toString(), icon: <ShoppingBag className="w-5 h-5" />, color: 'success' as const, trend: null },
    { title: 'Avg Wait Time', value: '12 min', icon: <Clock className="w-5 h-5" />, color: 'primary' as const, trend: { value: 8, isUp: true } },
    { title: 'Today Pickups', value: '23', icon: <CheckCircle className="w-5 h-5" />, color: 'info' as const, trend: { value: 15, isUp: true } },
    { title: 'VIP Customers', value: customers.filter(c => c.status === 'vip').length.toString(), icon: <Users className="w-5 h-5" />, color: 'warning' as const, trend: null },
  ]

  const filteredCustomers = customers.filter(c =>
    !searchTerm || c.name.toLowerCase().includes(searchTerm.toLowerCase()) || c.email.toLowerCase().includes(searchTerm.toLowerCase())
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <ShoppingBag className="w-7 h-7 text-[var(--nexus-success-500)]" />
            BOPIS Owner Dashboard
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Buy Online Pickup In Store — customer experience management</p>
        </div>
        <button onClick={() => navigate('/bopis')} className="enterprise-btn-secondary text-sm flex items-center gap-1.5 px-4 py-2">
          <Package className="w-4 h-4" /> Full BOPIS View
        </button>
      </div>

      <div className="grid grid-cols-4 gap-4">
        {kpis.map(k => (<EnterpriseKPICard key={k.title} title={k.title} value={k.value} icon={k.icon} color={k.color} trend={k.trend} />))}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-[var(--surface-muted)] rounded-lg p-0.5 w-fit">
        {(['ready', 'customers', 'insights'] as const).map(t => (
          <button key={t} onClick={() => setSelectedTab(t)}
            className={clsx('px-4 py-2 text-sm font-medium rounded-md capitalize transition-all',
              selectedTab === t ? 'bg-[var(--surface-muted)] text-[var(--text-primary)] shadow-sm' : 'text-[var(--text-secondary)]')}>
            {t === 'ready' ? 'Ready for Pickup' : t === 'customers' ? 'Customers' : 'Insights'}
          </button>
        ))}
      </div>

      {/* Ready for Pickup Tab */}
      {selectedTab === 'ready' && (
        <>
          {isLoading ? (
            <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" /></div>
          ) : readyOrders.length === 0 ? (
            <div className="enterprise-card p-12 text-center"><CheckCircle className="w-12 h-12 mx-auto mb-3 text-[var(--nexus-success-300)]" /><p className="font-medium text-[var(--text-secondary)]">All pickups completed!</p></div>
          ) : (
            <div className="space-y-3">
              {readyOrders.map((order: any, i: number) => (
                <div key={order.id} className="enterprise-card p-4 border-l-4 border-l-green-500">
                  <div className="flex items-start justify-between">
                    <div className="flex items-start gap-3">
                      <div className="w-10 h-10 rounded-xl bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 flex items-center justify-center text-[var(--nexus-success-600)] mt-0.5">
                        <ShoppingBag className="w-5 h-5" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-semibold text-[var(--text-primary)]">{order.orderNumber || `ORD-${i + 1}`}</span>
                          <EnterpriseStatusBadge status="success" label="Ready" />
                        </div>
                        <p className="text-xs text-[var(--text-secondary)] mt-0.5">Order #{order.id?.slice(0, 8)} · {order.items?.length || 3} items</p>
                        <p className="text-xs text-[var(--text-tertiary)] mt-1">
                          <Clock className="w-3 h-3 inline mr-1" />
                          Waiting {order._waitingMinutes} min
                        </p>
                        <div className="mt-3 flex items-center gap-2">
                          <PermissionGate resource="orders" action="edit">
                            <button onClick={() => confirmPickup.mutate(order.id)} className="enterprise-btn-primary text-xs px-3 py-1.5 bg-[var(--nexus-success-600)] hover:bg-[var(--nexus-success-700)]">
                              <CheckCircle className="w-3.5 h-3.5" /> Confirm Pickup
                            </button>
                          </PermissionGate>
                          <PermissionGate resource="orders" action="edit">
                            <button onClick={() => notifyCustomer.mutate(order.id)} className="enterprise-btn-secondary text-xs px-3 py-1.5">
                              <Bell className="w-3.5 h-3.5" /> Notify
                            </button>
                          </PermissionGate>
                          <button onClick={() => navigate(`/orders/${order.id}`)} className="enterprise-btn-secondary text-xs px-2 py-1.5">
                            <Eye className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    </div>
                    <span className="text-xs text-[var(--text-tertiary)]">{order.createdAt ? new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {/* Customers Tab */}
      {selectedTab === 'customers' && (
        <>
          <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder="Search customers..." minChars={0} className="max-w-md" />
          <div className="enterprise-card overflow-hidden">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Customer</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Contact</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Orders</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Last Pickup</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {filteredCustomers.map(c => (
                  <tr key={c.id} className="enterprise-table-row">
                    <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{c.name}</td>
                    <td className="px-4 py-3">
                      <p className="text-xs text-[var(--text-secondary)]">{c.email}</p>
                      <p className="text-xs text-[var(--text-tertiary)]">{c.phone}</p>
                    </td>
                    <td className="px-4 py-3 text-center text-sm font-semibold text-[var(--text-secondary)]">{c.orderCount}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{c.lastPickup}</td>
                    <td className="px-4 py-3 text-center">
                      <EnterpriseStatusBadge status={c.status === 'vip' ? 'warning' : c.status === 'active' ? 'success' : 'pending'} label={c.status} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button className="enterprise-btn-secondary text-xs px-2 py-1"><Phone className="w-3 h-3" /></button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {/* Insights Tab */}
      {selectedTab === 'insights' && (
        <div className="grid grid-cols-2 gap-6">
          <div className="enterprise-card p-5">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
              <Calendar className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Pickup Time Distribution
            </h3>
            <div className="space-y-2">
              {[
                { time: 'Morning (8-11)', pct: 22, orders: 28 },
                { time: 'Lunch (11-2)', pct: 35, orders: 45 },
                { time: 'Afternoon (2-5)', pct: 28, orders: 36 },
                { time: 'Evening (5-8)', pct: 15, orders: 19 },
              ].map((s, i) => (
                <div key={i}>
                  <div className="flex justify-between text-xs text-[var(--text-secondary)] mb-1"><span>{s.time}</span><span>{s.orders} orders</span></div>
                  <div className="w-full bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded-full h-2">
                    <div className={clsx('h-2 rounded-full', i === 0 ? 'bg-[var(--nexus-primary-400)]' : i === 1 ? 'bg-[var(--nexus-primary-50)]0' : i === 2 ? 'bg-[var(--nexus-warning-400)]' : 'bg-[var(--nexus-ai-400)]')} style={{ width: `${s.pct}%` }} />
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="enterprise-card p-5">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-emerald-500" /> Weekly Trend
            </h3>
            <div className="space-y-3">
              {[
                { day: 'Mon', orders: 18, pickup: 16 },
                { day: 'Tue', orders: 22, pickup: 20 },
                { day: 'Wed', orders: 15, pickup: 14 },
                { day: 'Thu', orders: 25, pickup: 22 },
                { day: 'Fri', orders: 30, pickup: 28 },
                { day: 'Sat', orders: 20, pickup: 18 },
                { day: 'Sun', orders: 8, pickup: 7 },
              ].map((d, i) => (
                <div key={i} className="flex items-center gap-3">
                  <span className="text-xs font-medium text-[var(--text-secondary)] w-8">{d.day}</span>
                  <div className="flex-1 h-4 bg-[var(--surface-muted)] bg-[var(--surface-base)] rounded-full overflow-hidden flex">
                    <div className="h-full bg-[var(--nexus-primary-400)] rounded-full" style={{ width: `${(d.orders / 30) * 100}%` }} />
                    <div className="h-full bg-[var(--nexus-success-400)] rounded-full" style={{ width: `${(d.pickup / 30) * 100}%`, marginLeft: -4 }} />
                  </div>
                  <span className="text-xs text-[var(--text-tertiary)] w-12 text-right">{d.orders}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
