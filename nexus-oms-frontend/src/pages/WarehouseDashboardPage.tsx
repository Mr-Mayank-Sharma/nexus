import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Building2, Users, Package, ClipboardCheck, PackagePlus, Truck,
  BarChart3, Clock, TrendingUp, AlertTriangle, Search, Eye, ArrowRight,
  UserCheck, Calendar, RefreshCw, Target, Gauge,
} from 'lucide-react'
import clsx from 'clsx'
import * as pickingApi from '../api/picking'
import * as packingApi from '../api/packing'
import * as ordersApi from '../api/orders'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'

export default function WarehouseDashboardPage() {
  const navigate = useNavigate()
  const [selectedTab, setSelectedTab] = useState<'overview' | 'labor' | 'dock'>('overview')

  const { data: pieData } = useQuery({
    queryKey: ['wh-kpis'],
    queryFn: async () => {
      const [pickRes, packRes, orderRes] = await Promise.all([
        pickingApi.getPicklists({}).catch(() => ({ data: { content: [] } })),
        packingApi.getPackages({}).catch(() => ({ data: { content: [] } })),
        ordersApi.getOrders({}).catch(() => ({ data: { content: [] } })),
      ])
      return { picks: pickRes.data, packs: packRes.data, orders: orderRes.data }
    },
  })

  const kpis = [
    { title: 'Active Pickers', value: '12', icon: <UserCheck className="w-5 h-5" />, color: 'primary' as const, trend: { value: 8, isUp: true } },
    { title: 'Pick Rate', value: '148/hr', icon: <Target className="w-5 h-5" />, color: 'success' as const, trend: { value: 12, isUp: true } },
    { title: 'Open Picklists', value: '24', icon: <ClipboardCheck className="w-5 h-5" />, color: 'warning' as const, trend: null },
    { title: 'Packing Queue', value: '18', icon: <PackagePlus className="w-5 h-5" />, color: 'info' as const, trend: { value: 5, isUp: false } },
  ]

  const laborData = [
    { name: 'Alex M.', role: 'Picker', status: 'active', picks: 47, hours: 6.5, efficiency: 98 },
    { name: 'Sarah K.', role: 'Picker', status: 'active', picks: 52, hours: 7.0, efficiency: 104 },
    { name: 'James R.', role: 'Packer', status: 'active', packs: 38, hours: 6.5, efficiency: 95 },
    { name: 'Maria G.', role: 'Packer', status: 'break', packs: 22, hours: 4.0, efficiency: 88 },
    { name: 'Tom W.', role: 'Loader', status: 'active', loads: 6, hours: 6.0, efficiency: 100 },
    { name: 'Lisa C.', role: 'Picker', status: 'idle', picks: 15, hours: 5.0, efficiency: 50 },
  ]

  const dockData = [
    { door: 'D1', carrier: 'UPS', status: 'loading', eta: '—', orders: 42, driver: 'John D.' },
    { door: 'D2', carrier: 'FedEx', status: 'arrived', eta: '10:30', orders: 28, driver: 'Mike S.' },
    { door: 'D3', carrier: 'USPS', status: 'scheduled', eta: '11:00', orders: 15, driver: '—' },
    { door: 'D4', carrier: 'OnTrac', status: 'available', eta: '—', orders: 0, driver: '—' },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2.5">
            <Building2 className="w-7 h-7 text-amber-500" />
            Warehouse Dashboard
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Main Distribution Center · Operations overview</p>
        </div>
        <button onClick={() => navigate('/warehouse')} className="enterprise-btn-secondary text-sm flex items-center gap-1.5 px-4 py-2">
          <Building2 className="w-4 h-4" /> Full Warehouse
        </button>
      </div>

      {/* KPI Row */}
      <div className="grid grid-cols-4 gap-4">
        {kpis.map(k => (
          <EnterpriseKPICard key={k.title} title={k.title} value={k.value} icon={k.icon} color={k.color} trend={k.trend} />
        ))}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-gray-100 dark:bg-gray-700 rounded-lg p-0.5 w-fit">
        {(['overview', 'labor', 'dock'] as const).map(t => (
          <button key={t} onClick={() => setSelectedTab(t)}
            className={clsx('px-4 py-2 text-sm font-medium rounded-md capitalize transition-all',
              selectedTab === t ? 'bg-white dark:bg-gray-600 text-gray-900 dark:text-gray-100 shadow-sm' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700')}>
            {t === 'overview' ? 'Overview' : t === 'labor' ? 'Labor Tracking' : 'Dock Management'}
          </button>
        ))}
      </div>

      {/* Overview Tab */}
      {selectedTab === 'overview' && (
        <div className="grid grid-cols-3 gap-6">
          <div className="col-span-2 space-y-4">
            <div className="enterprise-card p-5">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center gap-2">
                <Target className="w-4 h-4 text-primary-500" /> Fulfillment Pipeline
              </h3>
              <div className="relative">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-medium text-gray-500 dark:text-gray-400">Picked Today</span>
                  <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">1,247 / 1,500</span>
                </div>
                <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2.5">
                  <div className="bg-primary-500 h-2.5 rounded-full" style={{ width: '83%' }} />
                </div>
                <div className="flex items-center justify-between mt-4 mb-2">
                  <span className="text-xs font-medium text-gray-500 dark:text-gray-400">Packed Today</span>
                  <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">892 / 1,200</span>
                </div>
                <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2.5">
                  <div className="bg-emerald-500 h-2.5 rounded-full" style={{ width: '74%' }} />
                </div>
                <div className="flex items-center justify-between mt-4 mb-2">
                  <span className="text-xs font-medium text-gray-500 dark:text-gray-400">Shipped Today</span>
                  <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">756 / 1,000</span>
                </div>
                <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2.5">
                  <div className="bg-blue-500 h-2.5 rounded-full" style={{ width: '76%' }} />
                </div>
              </div>
            </div>

            <div className="enterprise-card p-5">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-amber-500" /> Active Alerts
              </h3>
              <div className="space-y-2">
                {[
                  { severity: 'high', message: 'Pick wave W-1023 behind schedule by 45 min' },
                  { severity: 'medium', message: 'Packing station P-4 low on tape supplies' },
                  { severity: 'low', message: 'Dock D2 scheduled carrier arriving shortly' },
                ].map((a, i) => (
                  <div key={i} className={clsx('flex items-center gap-3 p-2.5 rounded-lg text-sm',
                    a.severity === 'high' ? 'bg-red-50 dark:bg-red-900/10 text-red-700 dark:text-red-400' :
                    a.severity === 'medium' ? 'bg-amber-50 dark:bg-amber-900/10 text-amber-700 dark:text-amber-400' :
                    'bg-blue-50 dark:bg-blue-900/10 text-blue-700 dark:text-blue-400')}>
                    <AlertTriangle className="w-4 h-4 shrink-0" />
                    <span>{a.message}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="space-y-4">
            <div className="enterprise-card p-5">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3 flex items-center gap-2">
                <Clock className="w-4 h-4 text-gray-400" /> Quick Actions
              </h3>
              <div className="space-y-2">
                {[
                  { label: 'Picking Board', path: '/picking', icon: <ClipboardCheck className="w-4 h-4" /> },
                  { label: 'Packing Stations', path: '/packing', icon: <PackagePlus className="w-4 h-4" /> },
                  { label: 'Dock Schedule', path: '/shipping', icon: <Truck className="w-4 h-4" /> },
                  { label: 'Inventory Check', path: '/inventory', icon: <Package className="w-4 h-4" /> },
                  { label: 'Task Queues', path: '/task-queues', icon: <AlertTriangle className="w-4 h-4" /> },
                ].map((a, i) => (
                  <button key={i} onClick={() => navigate(a.path)}
                    className="w-full flex items-center justify-between p-2.5 rounded-lg bg-gray-50 dark:bg-gray-800/50 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors text-left">
                    <span className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                      {a.icon} {a.label}
                    </span>
                    <ArrowRight className="w-3.5 h-3.5 text-gray-400" />
                  </button>
                ))}
              </div>
            </div>

            <div className="enterprise-card p-5">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3">Today's Summary</h3>
              <div className="space-y-3">
                {[
                  { label: 'Orders Fulfilled', value: '756', icon: <Package className="w-4 h-4 text-green-500" /> },
                  { label: 'Active Workers', value: '14', icon: <Users className="w-4 h-4 text-blue-500" /> },
                  { label: 'On-Time Rate', value: '96.2%', icon: <TrendingUp className="w-4 h-4 text-emerald-500" /> },
                  { label: 'Avg Pick Time', value: '4.2 min', icon: <Clock className="w-4 h-4 text-amber-500" /> },
                ].map((s, i) => (
                  <div key={i} className="flex items-center justify-between">
                    <span className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">{s.icon} {s.label}</span>
                    <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">{s.value}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Labor Tab */}
      {selectedTab === 'labor' && (
        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Worker</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Role</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Output</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Hours</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Efficiency</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {laborData.map((w, i) => (
                  <tr key={i} className="enterprise-table-row">
                    <td className="px-4 py-3 text-sm font-medium text-gray-900 dark:text-gray-100">{w.name}</td>
                    <td className="px-4 py-3 text-sm text-gray-600 dark:text-gray-400">{w.role}</td>
                    <td className="px-4 py-3 text-center">
                      <EnterpriseStatusBadge status={w.status === 'active' ? 'success' : w.status === 'break' ? 'warning' : 'error'} label={w.status} />
                    </td>
                    <td className="px-4 py-3 text-center text-sm font-semibold text-gray-700 dark:text-gray-300">
                      {w.picks || w.packs || w.loads || 0}
                    </td>
                    <td className="px-4 py-3 text-center text-sm text-gray-600 dark:text-gray-400">{w.hours}h</td>
                    <td className="px-4 py-3 text-center">
                      <span className={clsx('text-sm font-semibold', w.efficiency >= 90 ? 'text-green-600' : w.efficiency >= 70 ? 'text-amber-600' : 'text-red-600')}>
                        {w.efficiency}%
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Dock Tab */}
      {selectedTab === 'dock' && (
        <div className="grid grid-cols-2 gap-4">
          {dockData.map((d, i) => (
            <div key={i} className={clsx('enterprise-card p-4 border-l-4',
              d.status === 'loading' ? 'border-l-green-500' :
              d.status === 'arrived' ? 'border-l-blue-500' :
              d.status === 'scheduled' ? 'border-l-amber-500' :
              'border-l-gray-300 dark:border-l-gray-600')}>
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <Truck className={clsx('w-5 h-5',
                    d.status === 'loading' ? 'text-green-500' :
                    d.status === 'arrived' ? 'text-blue-500' :
                    d.status === 'scheduled' ? 'text-amber-500' :
                    'text-gray-400')} />
                  <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">Door {d.door}</span>
                </div>
                <EnterpriseStatusBadge status={d.status === 'loading' ? 'success' : d.status === 'arrived' ? 'info' : d.status === 'scheduled' ? 'warning' : 'pending'} label={d.status} />
              </div>
              <div className="grid grid-cols-3 gap-3 text-xs text-gray-500 dark:text-gray-400 mt-3">
                <div><span className="block font-medium text-gray-700 dark:text-gray-300">{d.carrier}</span>Carrier</div>
                <div><span className="block font-medium text-gray-700 dark:text-gray-300">{d.orders}</span>Orders</div>
                <div><span className="block font-medium text-gray-700 dark:text-gray-300">{d.driver !== '—' ? d.driver : d.eta}</span>{d.driver !== '—' ? 'Driver' : 'ETA'}</div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
