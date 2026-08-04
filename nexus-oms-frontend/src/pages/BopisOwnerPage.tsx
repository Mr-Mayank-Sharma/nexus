import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ShoppingBag, Package, Users, Clock, CheckCircle, Search, Eye,
  Calendar, TrendingUp, Phone,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import pickupApi, { type PickupOrder } from '../api/pickup'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'

const asArray = (d: unknown): any[] => (Array.isArray(d) ? d : Array.isArray((d as any)?.content) ? (d as any).content : [])

function waitingMinutes(order: PickupOrder): number | null {
  const ref = order.readyAt || order.createdAt
  if (!ref) return null
  const t = new Date(ref).getTime()
  if (Number.isNaN(t)) return null
  return Math.max(0, Math.floor((Date.now() - t) / 60000))
}

const dayKey = (o: PickupOrder): string => {
  const ref = o.readyAt || o.createdAt
  if (!ref) return 'Unknown'
  const d = new Date(ref)
  return Number.isNaN(d.getTime()) ? 'Unknown' : d.toLocaleDateString(undefined, { weekday: 'short' })
}

const hourBucket = (o: PickupOrder): string | null => {
  const ref = o.readyAt || o.createdAt
  if (!ref) return null
  const d = new Date(ref)
  if (Number.isNaN(d.getTime())) return null
  const h = d.getHours()
  if (h < 8) return 'Early (0-8)'
  if (h < 11) return 'Morning (8-11)'
  if (h < 14) return 'Lunch (11-2)'
  if (h < 17) return 'Afternoon (2-5)'
  return 'Evening (5-8)'
}

interface DerivedCustomer {
  key: string
  name: string
  email: string
  phone: string
  orderCount: number
  lastReadyAt?: string
  lastReadyFromNow: string
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
      const res = await pickupApi.getReadyForHandoff().catch(() => null)
      return asArray(res?.data).filter((o: PickupOrder) => o.pickupType === 'BOPIS')
    },
    refetchInterval: 60000,
  })

  const confirmPickup = useMutation({
    mutationFn: async (pickupOrderId: string) => {
      const res = await pickupApi.handoffOrder(pickupOrderId)
      return res
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bopis-owner-ready'] })
      addToast({ type: 'success', title: 'Pickup confirmed' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to confirm pickup' }),
  })

  const customers: DerivedCustomer[] = useMemo(() => {
    const map = new Map<string, DerivedCustomer>()
    for (const o of readyOrders) {
      const key = (o.customerEmail || o.customerName || o.orderNumber).trim().toLowerCase()
      if (!key) continue
      const existing = map.get(key)
      const fromNow = waitingMinutes(o)
      if (existing) {
        existing.orderCount += 1
      } else {
        map.set(key, {
          key,
          name: o.customerName || '—',
          email: o.customerEmail || '—',
          phone: o.customerPhone || '—',
          orderCount: 1,
          lastReadyAt: o.readyAt || o.createdAt,
          lastReadyFromNow: fromNow != null ? `${fromNow} min ago` : '—',
        })
      }
    }
    return Array.from(map.values())
  }, [readyOrders])

  const filteredCustomers = customers.filter(c =>
    !searchTerm ||
    c.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    c.email.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const todayStr = new Date().toISOString().split('T')[0]
  const readyToday = readyOrders.filter(o => (o.readyAt || o.createdAt || '').slice(0, 10) === todayStr)

  const waits = readyOrders.map(waitingMinutes).filter((w): w is number => w != null)
  const avgWait = waits.length > 0 ? Math.round(waits.reduce((a, b) => a + b, 0) / waits.length) : null

  const kpis = [
    { title: 'Ready for Pickup', value: readyOrders.length.toString(), icon: <ShoppingBag className="w-5 h-5" />, color: 'success' as const, trend: null },
    { title: 'Avg Wait Time', value: avgWait != null ? `${avgWait} min` : '—', icon: <Clock className="w-5 h-5" />, color: 'primary' as const, trend: null },
    { title: 'Ready Today', value: readyToday.length.toString(), icon: <CheckCircle className="w-5 h-5" />, color: 'info' as const, trend: null },
    { title: 'Customers Waiting', value: customers.length.toString(), icon: <Users className="w-5 h-5" />, color: 'warning' as const, trend: null },
  ]

  const hourBuckets = useMemo(() => {
    const order = ['Early (0-8)', 'Morning (8-11)', 'Lunch (11-2)', 'Afternoon (2-5)', 'Evening (5-8)']
    const counts = new Map<string, number>()
    for (const o of readyOrders) {
      const b = hourBucket(o)
      if (b) counts.set(b, (counts.get(b) ?? 0) + 1)
    }
    return order.map(b => ({ bucket: b, count: counts.get(b) ?? 0 })).filter(b => b.count > 0)
  }, [readyOrders])

  const weeklyTrend = useMemo(() => {
    const order = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
    const counts = new Map<string, number>()
    for (const o of readyOrders) {
      const k = dayKey(o)
      if (k !== 'Unknown') counts.set(k, (counts.get(k) ?? 0) + 1)
    }
    return order.map(d => ({ day: d, count: counts.get(d) ?? 0 }))
  }, [readyOrders])

  const maxWeek = Math.max(...weeklyTrend.map(d => d.count), 1)
  const maxBucket = Math.max(...hourBuckets.map(b => b.count), 1)

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
        <button type="button" onClick={() => navigate('/bopis')} className="enterprise-btn-secondary text-sm flex items-center gap-1.5 px-4 py-2">
          <Package className="w-4 h-4" /> Full BOPIS View
        </button>
      </div>

      <div className="grid grid-cols-4 gap-4">
        {kpis.map(k => (<EnterpriseKPICard key={k.title} title={k.title} value={k.value} icon={k.icon} color={k.color} trend={k.trend} />))}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-[var(--surface-muted)] rounded-lg p-0.5 w-fit">
        {(['ready', 'customers', 'insights'] as const).map(t => (
          <button type="button" key={t} onClick={() => setSelectedTab(t)}
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
              {readyOrders.map((order: PickupOrder) => {
                const wait = waitingMinutes(order)
                return (
                  <div key={order.id} className="enterprise-card p-4 border-l-4 border-l-green-500">
                    <div className="flex items-start justify-between">
                      <div className="flex items-start gap-3">
                        <div className="w-10 h-10 rounded-xl bg-[var(--nexus-success-50)] dark:bg-[var(--nexus-success-900)]/20 flex items-center justify-center text-[var(--nexus-success-600)] mt-0.5">
                          <ShoppingBag className="w-5 h-5" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-semibold text-[var(--text-primary)]">{order.orderNumber || order.id}</span>
                            <EnterpriseStatusBadge status="success" label="Ready" />
                          </div>
                          <p className="text-xs text-[var(--text-secondary)] mt-0.5">
                            {order.customerName || '—'}
                            {order.pickupCode ? ` · Code ${order.pickupCode}` : ''}
                          </p>
                          {wait != null && (
                            <p className="text-xs text-[var(--text-tertiary)] mt-1">
                              <Clock className="w-3 h-3 inline mr-1" />
                              Waiting {wait} min
                            </p>
                          )}
                          <div className="mt-3 flex items-center gap-2">
                            <PermissionGate resource="orders" action="edit">
                              <button type="button" onClick={() => confirmPickup.mutate(order.id)} disabled={confirmPickup.isPending} className="enterprise-btn-primary text-xs px-3 py-1.5 bg-[var(--nexus-success-600)] hover:bg-[var(--nexus-success-700)]">
                                <CheckCircle className="w-3.5 h-3.5" /> Confirm Pickup
                              </button>
                            </PermissionGate>
                            {order.customerPhone && (
                              <a href={`tel:${order.customerPhone}`} className="enterprise-btn-secondary text-xs px-2 py-1.5" title="Call customer">
                                <Phone className="w-3.5 h-3.5" />
                              </a>
                            )}
                            <button type="button" onClick={() => navigate(`/orders/${order.orderId}`)} className="enterprise-btn-secondary text-xs px-2 py-1.5" title="View order">
                              <Eye className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>
                      </div>
                      <span className="text-xs text-[var(--text-tertiary)]">{order.readyAt ? new Date(order.readyAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}</span>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </>
      )}

      {/* Customers Tab */}
      {selectedTab === 'customers' && (
        <>
          <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder="Search customers..." minChars={0} className="max-w-md" />
          {filteredCustomers.length === 0 ? (
            <div className="enterprise-card p-12 text-center">
              <Users className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
              <p className="font-medium text-[var(--text-secondary)]">No customers with active pickups</p>
              <p className="text-sm text-[var(--text-tertiary)] mt-1">Customers with orders ready for handoff will appear here.</p>
            </div>
          ) : (
            <div className="enterprise-card overflow-hidden">
              <table className="enterprise-table w-full">
                <thead>
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Customer</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Contact</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase">Orders</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Last Ready</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--surface-sunken)] dark:divide-gray-800">
                  {filteredCustomers.map(c => (
                    <tr key={c.key} className="enterprise-table-row">
                      <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{c.name}</td>
                      <td className="px-4 py-3">
                        <p className="text-xs text-[var(--text-secondary)]">{c.email}</p>
                        <p className="text-xs text-[var(--text-tertiary)]">{c.phone}</p>
                      </td>
                      <td className="px-4 py-3 text-center text-sm font-semibold text-[var(--text-secondary)]">{c.orderCount}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{c.lastReadyFromNow}</td>
                      <td className="px-4 py-3 text-right">
                        {c.phone !== '—' && <a href={`tel:${c.phone}`} className="enterprise-btn-secondary text-xs px-2 py-1"><Phone className="w-3 h-3" /></a>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {/* Insights Tab */}
      {selectedTab === 'insights' && (
        readyOrders.length === 0 ? (
          <div className="enterprise-card p-12 text-center">
            <TrendingUp className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
            <p className="font-medium text-[var(--text-secondary)]">Not enough pickup data yet</p>
            <p className="text-sm text-[var(--text-tertiary)] mt-1">Insights will appear once orders are ready for handoff.</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-6">
            <div className="enterprise-card p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
                <Calendar className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Pickup Time Distribution
              </h3>
              {hourBuckets.length === 0 ? (
                <p className="text-sm text-[var(--text-tertiary)]">No pickup times recorded.</p>
              ) : (
                <div className="space-y-2">
                  {hourBuckets.map(b => (
                    <div key={b.bucket}>
                      <div className="flex justify-between text-xs text-[var(--text-secondary)] mb-1"><span>{b.bucket}</span><span>{b.count} {b.count === 1 ? 'order' : 'orders'}</span></div>
                      <div className="w-full bg-[var(--surface-muted)] rounded-full h-2">
                        <div className="h-2 rounded-full bg-[var(--nexus-primary-400)]" style={{ width: `${(b.count / maxBucket) * 100}%` }} />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
            <div className="enterprise-card p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
                <TrendingUp className="w-4 h-4 text-[var(--nexus-success-500)]" /> Ready Orders by Weekday
              </h3>
              <div className="space-y-3">
                {weeklyTrend.map(d => (
                  <div key={d.day} className="flex items-center gap-3">
                    <span className="text-xs font-medium text-[var(--text-secondary)] w-8">{d.day}</span>
                    <div className="flex-1 h-4 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                      <div className="h-full bg-[var(--nexus-primary-400)] rounded-full" style={{ width: `${(d.count / maxWeek) * 100}%` }} />
                    </div>
                    <span className="text-xs text-[var(--text-tertiary)] w-8 text-right">{d.count}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )
      )}
    </div>
  )
}
