import { useState, useEffect } from 'react'
import clsx from 'clsx'
import {
  BarChart3, Plus, MoreHorizontal, RefreshCw, Edit3, Trash2, Play, Calendar,
  Clock, Download, FileText, PieChart, TrendingUp, TrendingDown, CheckCircle,
  XCircle, AlertCircle, Mail, Save, Loader2, LayoutDashboard, FileBarChart,
  CalendarClock, BookmarkCheck, SlidersHorizontal, Eye, X, Search, Filter,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import { fetchDashboardWidgets, generateReport, fetchReportTemplates, fetchScheduledReports, createScheduledReport } from '../api/newBackend'

type TabId = 'dashboard' | 'reports' | 'scheduled' | 'saved'

interface Widget {
  id: string
  type: string
  title: string
  menuOpen: boolean
}

interface ReportTemplate {
  id: string
  name: string
  description: string
  lastRun: string
}

interface GeneratedReport {
  id: string
  name: string
  date: string
  format: string
  status: string
}

interface Schedule {
  id: string
  name: string
  report: string
  frequency: string
  recipients: string
  lastSent: string
  nextSend: string
  status: 'Active' | 'Paused'
}

interface SavedReport {
  id: string
  name: string
  description: string
  lastModified: string
}

const WIDGET_TYPES = [
  { value: 'revenue', label: 'Revenue Over Time' },
  { value: 'orders', label: 'Order Status Breakdown' },
  { value: 'products', label: 'Top Products by Revenue' },
  { value: 'carrier', label: 'Carrier Performance' },
  { value: 'sla', label: 'Fulfillment SLA' },
  { value: 'warehouse', label: 'Warehouse Utilization' },
]

const REVENUE_DATA = [
  { month: 'Jan', value: 185000 }, { month: 'Feb', value: 195000 }, { month: 'Mar', value: 210000 },
  { month: 'Apr', value: 225000 }, { month: 'May', value: 240000 }, { month: 'Jun', value: 255000 },
  { month: 'Jul', value: 270000 }, { month: 'Aug', value: 265000 }, { month: 'Sep', value: 280000 },
  { month: 'Oct', value: 295000 }, { month: 'Nov', value: 310000 }, { month: 'Dec', value: 340000 },
]

const ORDER_STATUS = [
  { label: 'Pending', value: 15, color: '#f59e0b' },
  { label: 'Processing', value: 25, color: '#3b82f6' },
  { label: 'Shipped', value: 35, color: '#10b981' },
  { label: 'Delivered', value: 20, color: '#6366f1' },
  { label: 'Returned', value: 5, color: '#ef4444' },
]

const TOP_PRODUCTS = [
  { name: 'Premium Wireless Headphones', revenue: 425000 },
  { name: 'Ergonomic Keyboard Pro', revenue: 318000 },
  { name: 'Ultra HD Webcam', revenue: 276000 },
  { name: 'Smart Home Hub', revenue: 234000 },
  { name: 'Portable SSD 2TB', revenue: 198000 },
  { name: 'Mechanical Gaming Mouse', revenue: 167000 },
]

const CARRIER_DATA = [
  { carrier: 'FedEx', otd: 96.2, cost: 8.45, avgDelivery: 2.1 },
  { carrier: 'UPS', otd: 94.8, cost: 9.12, avgDelivery: 2.4 },
  { carrier: 'USPS', otd: 91.5, cost: 6.78, avgDelivery: 3.2 },
  { carrier: 'DHL', otd: 97.1, cost: 11.34, avgDelivery: 1.8 },
]

const WAREHOUSE_DATA = [
  { name: 'Atlanta', utilization: 87, capacity: 50000, used: 43500 },
  { name: 'Chicago', utilization: 72, capacity: 45000, used: 32400 },
  { name: 'Dallas', utilization: 93, capacity: 35000, used: 32550 },
  { name: 'Los Angeles', utilization: 68, capacity: 60000, used: 40800 },
  { name: 'Newark', utilization: 81, capacity: 40000, used: 32400 },
]

const REPORT_TEMPLATES: ReportTemplate[] = [
  { id: 'r1', name: 'Daily Sales Summary', description: 'Revenue, orders, and AOV for the current day', lastRun: '2026-06-30 23:45' },
  { id: 'r2', name: 'Inventory Valuation', description: 'Current inventory value by warehouse and category', lastRun: '2026-06-30 06:00' },
  { id: 'r3', name: 'Pick/Pack Accuracy', description: 'Order accuracy rates, error breakdown, and trends', lastRun: '2026-06-29 23:59' },
  { id: 'r4', name: 'Carrier Cost Analysis', description: 'Per-carrier cost breakdown, transit times, and SLA adherence', lastRun: '2026-06-28 12:30' },
  { id: 'r5', name: 'Returns Analysis', description: 'Return reasons, rates by product category, and refund totals', lastRun: '2026-06-27 08:15' },
  { id: 'r6', name: 'BOPIS Performance', description: 'Buy online pick up in-store metrics and fulfillment times', lastRun: '2026-06-26 14:00' },
]

const GENERATED_REPORTS: GeneratedReport[] = [
  { id: 'g1', name: 'Daily Sales Summary - Jun 30', date: '2026-06-30', format: 'PDF', status: 'Ready' },
  { id: 'g2', name: 'Weekly Carrier Analysis W26', date: '2026-06-29', format: 'CSV', status: 'Ready' },
  { id: 'g3', name: 'Monthly Inventory Valuation', date: '2026-06-28', format: 'Excel', status: 'Generating' },
  { id: 'g4', name: 'Returns Analysis Q2 2026', date: '2026-06-25', format: 'PDF', status: 'Ready' },
]

const SCHEDULES: Schedule[] = [
  { id: 's1', name: 'Daily Ops Report', report: 'Daily Sales Summary', frequency: 'Daily', recipients: 'ops@nexus.com', lastSent: '2026-06-30 06:00', nextSend: '2026-07-01 06:00', status: 'Active' },
  { id: 's2', name: 'Weekly Carrier Review', report: 'Carrier Cost Analysis', frequency: 'Weekly', recipients: 'logistics@nexus.com', lastSent: '2026-06-28 07:00', nextSend: '2026-07-05 07:00', status: 'Active' },
  { id: 's3', name: 'Monthly Executive Summary', report: 'Inventory Valuation', frequency: 'Monthly', recipients: 'exec@nexus.com, finance@nexus.com', lastSent: '2026-06-01 08:00', nextSend: '2026-07-01 08:00', status: 'Paused' },
  { id: 's4', name: 'Weekly Accuracy Report', report: 'Pick/Pack Accuracy', frequency: 'Weekly', recipients: 'warehouse@nexus.com', lastSent: '2026-06-27 06:30', nextSend: '2026-07-04 06:30', status: 'Active' },
]

const SAVED_REPORTS: SavedReport[] = [
  { id: 'sv1', name: 'Weekly Ops Dashboard', description: 'Key operational metrics for weekly review', lastModified: '2026-06-28 14:22' },
  { id: 'sv2', name: 'Executive Monthly Package', description: 'Executive-level summary with revenue and SLA data', lastModified: '2026-06-25 09:15' },
  { id: 'sv3', name: 'Warehouse Efficiency Report', description: 'Per-warehouse productivity and utilization metrics', lastModified: '2026-06-20 16:30' },
  { id: 'sv4', name: 'Carrier Scorecard Archive', description: 'Monthly carrier performance comparison', lastModified: '2026-06-15 11:00' },
]

const METRICS = ['Revenue', 'Orders', 'Fulfillment Time', 'Returns', 'SLA Compliance', 'Carrier Cost', 'Inventory Value', 'Pick Accuracy']

const FORMATS = ['PDF', 'CSV', 'Excel']

function Sparkline({ data, color = '#3b82f6' }: { data: { value: number }[]; color?: string }) {
  const w = 160
  const h = 40
  const max = Math.max(...data.map(d => d.value))
  const min = Math.min(...data.map(d => d.value))
  const range = max - min || 1
  const pts = data.map((d, i) => `${(i / (data.length - 1)) * w},${h - ((d.value - min) / range) * (h - 4) - 2}`).join(' ')
  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="w-full h-10">
      <polyline fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" points={pts} />
      <polygon fill={`${color}15`} points={`${pts} ${w},${h} 0,${h}`} />
    </svg>
  )
}

export default function ReportBuilderPage() {
  const [activeTab, setActiveTab] = useState<TabId>('dashboard')
  const [widgets, setWidgets] = useState<Widget[]>([
    { id: 'w1', type: 'revenue', title: 'Revenue Over Time', menuOpen: false },
    { id: 'w2', type: 'orders', title: 'Order Status Breakdown', menuOpen: false },
    { id: 'w3', type: 'products', title: 'Top Products by Revenue', menuOpen: false },
    { id: 'w4', type: 'carrier', title: 'Carrier Performance', menuOpen: false },
    { id: 'w5', type: 'sla', title: 'Fulfillment SLA', menuOpen: false },
    { id: 'w6', type: 'warehouse', title: 'Warehouse Utilization', menuOpen: false },
  ])
  const [showAddWidget, setShowAddWidget] = useState(false)
  const [selectedWidgetType, setSelectedWidgetType] = useState('revenue')
  const [showCustomReport, setShowCustomReport] = useState(false)
  const [customReportName, setCustomReportName] = useState('')
  const [customDateRange, setCustomDateRange] = useState('last30')
  const [customMetrics, setCustomMetrics] = useState<string[]>(['Revenue', 'Orders'])
  const [customGroupBy, setCustomGroupBy] = useState('Day')
  const [customFormat, setCustomFormat] = useState('PDF')
  const [generating, setGenerating] = useState(false)
  const [showCreateSchedule, setShowCreateSchedule] = useState(false)
  const [scheduleForm, setScheduleForm] = useState({ name: '', report: 'Daily Sales Summary', frequency: 'Daily', recipients: '' })
  const [savedReports, setSavedReports] = useState<SavedReport[]>(SAVED_REPORTS)
  const [schedules, setSchedules] = useState<Schedule[]>(SCHEDULES)
  const [generatedReports, setGeneratedReports] = useState<GeneratedReport[]>(GENERATED_REPORTS)
  const [renamingId, setRenamingId] = useState<string | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const { addToast } = useToast()

  const [widgetData, setWidgetData] = useState<any[]>([])
  const [reportTemplates, setReportTemplates] = useState<any[]>([])
  const [scheduledReports, setScheduledReports] = useState<any[]>([])

  useEffect(() => {
    Promise.all([fetchDashboardWidgets(), fetchReportTemplates(), fetchScheduledReports()]).then(([w, t, s]) => {
      if (w?.widgets) setWidgetData(w.widgets)
      if (t?.templates) setReportTemplates(t.templates)
      if (s?.reports) setScheduledReports(s.reports)
    }).catch(() => {})
  }, [])

  function toggleMenu(id: string) {
    setWidgets(prev => prev.map(w => w.id === id ? { ...w, menuOpen: !w.menuOpen } : { ...w, menuOpen: false }))
  }

  function closeMenus() {
    setWidgets(prev => prev.map(w => ({ ...w, menuOpen: false })))
  }

  function addWidget() {
    const typeInfo = WIDGET_TYPES.find(t => t.value === selectedWidgetType)
    const newWidget: Widget = {
      id: `w${Date.now()}`,
      type: selectedWidgetType,
      title: typeInfo?.label || selectedWidgetType,
      menuOpen: false,
    }
    setWidgets(prev => [...prev, newWidget])
    setShowAddWidget(false)
    setSelectedWidgetType('revenue')
    addToast({ type: 'success', title: 'Widget added', message: `${newWidget.title} has been added to the dashboard` })
  }

  function removeWidget(id: string) {
    const removed = widgets.find(w => w.id === id)
    setWidgets(prev => prev.filter(w => w.id !== id))
    addToast({ type: 'info', title: 'Widget removed', message: `${removed?.title || 'Widget'} removed from dashboard` })
  }

  function refreshWidget(id: string) {
    const w = widgets.find(wg => wg.id === id)
    toggleMenu(id)
    addToast({ type: 'success', title: 'Refreshed', message: `${w?.title || 'Widget'} data refreshed` })
  }

  async function handleGenerateReport() {
    const res = await generateReport('custom', 'PDF')
    if (res?.report) addToast({ type: 'success', title: 'Report generated' })
  }

  function toggleMetric(metric: string) {
    setCustomMetrics(prev =>
      prev.includes(metric) ? prev.filter(m => m !== metric) : [...prev, metric]
    )
  }

  async function handleScheduleReport(data: any) {
    const res = await createScheduledReport(data)
    if (res?.report) {
      setScheduledReports(prev => [...prev, res.report])
      addToast({ type: 'success', title: 'Report scheduled' })
    }
  }

  function toggleScheduleStatus(id: string) {
    setSchedules(prev => prev.map(s =>
      s.id === id ? { ...s, status: s.status === 'Active' ? 'Paused' as const : 'Active' as const } : s
    ))
    const s = schedules.find(sch => sch.id === id)
    addToast({ type: 'info', title: `Schedule ${s?.status === 'Active' ? 'paused' : 'activated'}`, message: s?.name })
  }

  function loadSavedReport(id: string) {
    const report = savedReports.find(r => r.id === id)
    addToast({ type: 'info', title: 'Report loaded', message: report?.name })
  }

  function startRename(id: string) {
    const report = savedReports.find(r => r.id === id)
    setRenamingId(id)
    setRenameValue(report?.name || '')
  }

  function confirmRename(id: string) {
    if (!renameValue.trim()) return
    setSavedReports(prev => prev.map(r =>
      r.id === id ? { ...r, name: renameValue.trim(), lastModified: new Date().toISOString().split('T')[0] } : r
    ))
    setRenamingId(null)
    setRenameValue('')
    addToast({ type: 'success', title: 'Renamed' })
  }

  function deleteSavedReport(id: string) {
    const report = savedReports.find(r => r.id === id)
    setSavedReports(prev => prev.filter(r => r.id !== id))
    addToast({ type: 'info', title: 'Report deleted', message: report?.name })
  }

  const slaPercent = 96.2
  const slaAngle = (slaPercent / 100) * 360
  const slaRad = (slaAngle * Math.PI) / 180
  const slaRadius = 40
  const slaX = 50 + slaRadius * Math.sin(slaRad)
  const slaY = 50 - slaRadius * Math.cos(slaRad)
  const slaLargeArc = slaAngle > 180 ? 1 : 0

  const kpis = [
    { label: 'Revenue this month', value: '$2.84M', icon: TrendingUp, color: 'text-blue-600', bg: 'bg-blue-50' },
    { label: 'Orders', value: '12,438', icon: BarChart3, color: 'text-emerald-600', bg: 'bg-emerald-50' },
    { label: 'Avg order value', value: '$228', icon: TrendingUp, color: 'text-violet-600', bg: 'bg-violet-50' },
    { label: 'SLA compliance', value: '96.2%', icon: CheckCircle, color: 'text-green-600', bg: 'bg-green-50' },
    { label: 'Return rate', value: '2.1%', icon: TrendingDown, color: 'text-amber-600', bg: 'bg-amber-50' },
  ]

  function renderDashboard() {
    return (
      <>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 mb-6">
          {kpis.map(kpi => {
            const Icon = kpi.icon
            return (
              <div key={kpi.label} className="enterprise-card p-4 flex items-center gap-3">
                <div className={clsx('p-2.5 rounded-lg', kpi.bg)}>
                  <Icon className={clsx('w-5 h-5', kpi.color)} />
                </div>
                <div>
                  <p className="text-xs text-gray-500">{kpi.label}</p>
                  <p className="text-lg font-bold text-gray-900">{kpi.value}</p>
                </div>
              </div>
            )
          })}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
          {widgetData.map(widget => (
            <div key={widget.id} className="enterprise-card relative">
              <div className="enterprise-card-header">
                <h3 className="text-sm font-semibold text-gray-900">{widget.title}</h3>
                <div className="relative">
                  <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-icon !w-7 !h-7" onClick={() => toggleMenu(widget.id)}>
                    <MoreHorizontal className="w-4 h-4" />
                  </button>
                  {widget.menuOpen && (
                    <div className="absolute right-0 top-8 z-10 w-36 bg-white border border-gray-200 rounded-lg shadow-lg py-1 text-sm">
                      <PermissionGate resource="reports" action="edit">
                        <button className="w-full px-3 py-1.5 text-left text-gray-700 hover:bg-gray-50 flex items-center gap-2" onClick={() => refreshWidget(widget.id)}>
                          <RefreshCw className="w-3.5 h-3.5" /> Refresh
                        </button>
                      </PermissionGate>
                      <PermissionGate resource="reports" action="edit">
                        <button className="w-full px-3 py-1.5 text-left text-gray-700 hover:bg-gray-50 flex items-center gap-2" onClick={() => { toggleMenu(widget.id); addToast({ type: 'info', title: 'Edit widget', message: widget.title }) }}>
                          <Edit3 className="w-3.5 h-3.5" /> Edit
                        </button>
                      </PermissionGate>
                      <PermissionGate resource="reports" action="delete">
                        <button className="w-full px-3 py-1.5 text-left text-red-600 hover:bg-red-50 flex items-center gap-2" onClick={() => removeWidget(widget.id)}>
                          <Trash2 className="w-3.5 h-3.5" /> Remove
                        </button>
                      </PermissionGate>
                    </div>
                  )}
                </div>
              </div>
              <div className="card-body">
                {widget.type === 'revenue' && (
                  <div>
                    <div className="flex items-end gap-2 mb-3">
                      <span className="text-2xl font-bold text-gray-900">$3.15M</span>
                      <span className="text-sm text-green-600 flex items-center gap-0.5 mb-1"><TrendingUp className="w-3.5 h-3.5" />+12.3%</span>
                    </div>
                    <Sparkline data={REVENUE_DATA} />
                    <div className="flex justify-between text-xs text-gray-400 mt-2">
                      <span>Jan</span><span>Jun</span><span>Dec</span>
                    </div>
                  </div>
                )}
                {widget.type === 'orders' && (
                  <div className="flex items-center justify-center gap-4 py-2">
                    <svg width="120" height="120" viewBox="0 0 120 120">
                      {ORDER_STATUS.map((s, i) => {
                        const total = ORDER_STATUS.reduce((a, b) => a + b.value, 0)
                        const offset = ORDER_STATUS.slice(0, i).reduce((a, b) => a + (b.value / total) * 360, 0)
                        const angle = (s.value / total) * 360
                        const r = 50
                        const x1 = 60 + r * Math.cos(((offset - 90) * Math.PI) / 180)
                        const y1 = 60 + r * Math.sin(((offset - 90) * Math.PI) / 180)
                        const x2 = 60 + r * Math.cos((((offset + angle) - 90) * Math.PI) / 180)
                        const y2 = 60 + r * Math.sin((((offset + angle) - 90) * Math.PI) / 180)
                        const large = angle > 180 ? 1 : 0
                        return (
                          <path key={s.label}
                            d={`M60,60 L${x1},${y1} A${r},${r} 0 ${large},1 ${x2},${y2} Z`}
                            fill={s.color}
                            stroke="white"
                            strokeWidth="1"
                          />
                        )
                      })}
                      <circle cx="60" cy="60" r="25" fill="white" />
                      <text x="60" y="56" textAnchor="middle" fontSize="11" fontWeight="bold" fill="#1f2937">12.4K</text>
                      <text x="60" y="68" textAnchor="middle" fontSize="8" fill="#6b7280">Orders</text>
                    </svg>
                    <div className="space-y-1.5">
                      {ORDER_STATUS.map(s => (
                        <div key={s.label} className="flex items-center gap-2 text-xs">
                          <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: s.color }} />
                          <span className="text-gray-600">{s.label}</span>
                          <span className="text-gray-900 font-medium ml-auto">{s.value}%</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                {widget.type === 'products' && (
                  <div className="space-y-2">
                    {TOP_PRODUCTS.map(p => {
                      const maxRev = TOP_PRODUCTS[0].revenue
                      const pct = (p.revenue / maxRev) * 100
                      return (
                        <div key={p.name}>
                          <div className="flex justify-between text-xs mb-0.5">
                            <span className="text-gray-700 truncate">{p.name}</span>
                            <span className="text-gray-900 font-medium">${(p.revenue / 1000).toFixed(0)}k</span>
                          </div>
                          <div className="w-full bg-gray-100 rounded-full h-2">
                            <div className="h-2 rounded-full bg-blue-600" style={{ width: `${pct}%` }} />
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
                {widget.type === 'carrier' && (
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="border-b border-gray-100">
                          <th className="text-left py-1.5 font-semibold text-gray-500">Carrier</th>
                          <th className="text-right py-1.5 font-semibold text-gray-500">OTD %</th>
                          <th className="text-right py-1.5 font-semibold text-gray-500">Cost</th>
                          <th className="text-right py-1.5 font-semibold text-gray-500">Avg Del.</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-50">
                        {CARRIER_DATA.map(c => (
                          <tr key={c.carrier}>
                            <td className="py-1.5 font-medium text-gray-800">{c.carrier}</td>
                            <td className="py-1.5 text-right text-green-600">{c.otd}%</td>
                            <td className="py-1.5 text-right text-gray-700">${c.cost.toFixed(2)}</td>
                            <td className="py-1.5 text-right text-gray-700">{c.avgDelivery}d</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                {widget.type === 'sla' && (
                  <div className="flex items-center justify-center py-2">
                    <svg width="130" height="90" viewBox="0 0 130 90">
                      <path d="M15,80 A55,55 0 1,1 115,80" fill="none" stroke="#e5e7eb" strokeWidth="12" strokeLinecap="round" />
                      <path d={`M15,80 A55,55 0 ${slaLargeArc},1 ${slaX * 1.1 + 7},${slaY * 0.9 + 8}`}
                        fill="none" stroke="#10b981" strokeWidth="12" strokeLinecap="round"
                        transform={`translate(-7, -8) scale(1.1)`}
                      />
                      <text x="65" y="60" textAnchor="middle" fontSize="24" fontWeight="bold" fill="#1f2937">{slaPercent}%</text>
                      <text x="65" y="74" textAnchor="middle" fontSize="10" fill="#6b7280">SLA Compliance</text>
                    </svg>
                  </div>
                )}
                {widget.type === 'warehouse' && (
                  <div className="space-y-3">
                    {WAREHOUSE_DATA.map(w => (
                      <div key={w.name}>
                        <div className="flex justify-between text-xs mb-0.5">
                          <span className="text-gray-700 font-medium">{w.name}</span>
                          <span className="text-gray-500">{w.utilization}%</span>
                        </div>
                        <div className="w-full bg-gray-100 rounded-full h-2.5">
                          <div
                            className={clsx('h-2.5 rounded-full transition-all', w.utilization > 90 ? 'bg-red-500' : w.utilization > 80 ? 'bg-amber-500' : 'bg-blue-500')}
                            style={{ width: `${w.utilization}%` }}
                          />
                        </div>
                        <div className="flex justify-between text-xs text-gray-400 mt-0.5">
                          <span>{(w.used / 1000).toFixed(0)}k used</span>
                          <span>{(w.capacity / 1000).toFixed(0)}k capacity</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}

          <PermissionGate resource="reports" action="create">
            <button
              onClick={() => setShowAddWidget(true)}
              className="enterprise-card border-2 border-dashed border-gray-300 hover:border-blue-400 hover:bg-blue-50/30 transition-all flex items-center justify-center min-h-[200px] group cursor-pointer"
            >
              <div className="text-center">
                <Plus className="w-8 h-8 mx-auto text-gray-400 group-hover:text-blue-500 mb-2" />
                <p className="text-sm font-medium text-gray-500 group-hover:text-blue-600">Add Widget</p>
              </div>
            </button>
          </PermissionGate>
        </div>
      </>
    )
  }

  function renderReports() {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">Report Templates</h2>
          <PermissionGate resource="reports" action="create">
            <button className="enterprise-btn enterprise-btn-primary" onClick={() => setShowCustomReport(true)}>
              <Plus className="w-4 h-4" /> Create Custom Report
            </button>
          </PermissionGate>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {reportTemplates.map(t => (
            <div key={t.id} className="enterprise-card p-5">
              <div className="flex items-start justify-between mb-3">
                <div className="p-2 rounded-lg bg-blue-50">
                  <FileBarChart className="w-5 h-5 text-blue-600" />
                </div>
                <div className="flex gap-1">
                  <PermissionGate resource="reports" action="create">
                    <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" onClick={() => addToast({ type: 'success', title: 'Running report', message: t.name })}>
                      <Play className="w-3.5 h-3.5" /> Run Now
                    </button>
                  </PermissionGate>
                  <PermissionGate resource="reports" action="create">
                    <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" onClick={() => addToast({ type: 'info', title: 'Schedule report', message: t.name })}>
                      <Calendar className="w-3.5 h-3.5" />
                    </button>
                  </PermissionGate>
                  <PermissionGate resource="reports" action="edit">
                    <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" onClick={() => addToast({ type: 'info', title: 'Edit template', message: t.name })}>
                      <Edit3 className="w-3.5 h-3.5" />
                    </button>
                  </PermissionGate>
                </div>
              </div>
              <h3 className="text-sm font-semibold text-gray-900 mb-1">{t.name}</h3>
              <p className="text-xs text-gray-500 mb-3">{t.description}</p>
              <div className="flex items-center gap-1.5 text-xs text-gray-400">
                <Clock className="w-3 h-3" />
                Last run: {t.lastRun}
              </div>
            </div>
          ))}
        </div>

        <div className="enterprise-card">
          <div className="enterprise-card-header">
            <h3 className="text-sm font-semibold text-gray-900">Recently Generated Reports</h3>
            <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" onClick={() => addToast({ type: 'info', title: 'View all reports' })}>
              <Eye className="w-3.5 h-3.5" /> View All
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="enterprise-table">
              <thead>
                <tr>
                  <th>Report Name</th>
                  <th>Date</th>
                  <th>Format</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {generatedReports.map(r => (
                  <tr key={r.id}>
                    <td className="font-medium text-gray-900">{r.name}</td>
                    <td>{r.date}</td>
                    <td>
                      <span className="enterprise-badge enterprise-badge-neutral">{r.format}</span>
                    </td>
                    <td>
                      <span className={clsx('enterprise-badge', r.status === 'Ready' ? 'enterprise-badge-success' : 'enterprise-badge-warning')}>
                        {r.status === 'Ready' ? <CheckCircle className="w-3 h-3" /> : <Loader2 className="w-3 h-3 animate-spin" />}
                        {r.status}
                      </span>
                    </td>
                    <td>
                      <div className="flex gap-1">
                        <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" disabled={r.status !== 'Ready'} onClick={() => addToast({ type: 'success', title: 'Download started' })}>
                          <Download className="w-3.5 h-3.5" />
                        </button>
                        <PermissionGate resource="reports" action="create">
                          <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" onClick={() => addToast({ type: 'info', title: 'Scheduling report', message: r.name })}>
                            <Calendar className="w-3.5 h-3.5" />
                          </button>
                        </PermissionGate>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    )
  }

  function renderScheduled() {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">Scheduled Reports</h2>
          <PermissionGate resource="reports" action="create">
            <button className="enterprise-btn enterprise-btn-primary" onClick={() => setShowCreateSchedule(true)}>
              <Plus className="w-4 h-4" /> Create Schedule
            </button>
          </PermissionGate>
        </div>

        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="enterprise-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Report</th>
                  <th>Frequency</th>
                  <th>Recipients</th>
                  <th>Last Sent</th>
                  <th>Next Send</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {scheduledReports.map(s => (
                  <tr key={s.id}>
                    <td className="font-medium text-gray-900">{s.name}</td>
                    <td>{s.report}</td>
                    <td>
                      <span className="enterprise-badge enterprise-badge-neutral">{s.frequency}</span>
                    </td>
                    <td className="text-xs text-gray-500">{s.recipients}</td>
                    <td className="text-sm">{s.lastSent}</td>
                    <td className="text-sm">{s.nextSend}</td>
                    <td>
                      <PermissionGate resource="reports" action="edit">
                        <button
                          onClick={() => toggleScheduleStatus(s.id)}
                          className={clsx('enterprise-badge cursor-pointer border-0', s.status === 'Active' ? 'enterprise-badge-success' : 'enterprise-badge-error')}
                        >
                          {s.status === 'Active' ? <CheckCircle className="w-3 h-3" /> : <XCircle className="w-3 h-3" />}
                          {s.status}
                        </button>
                      </PermissionGate>
                    </td>
                    <td>
                      <div className="flex gap-1">
                        <PermissionGate resource="reports" action="create">
                          <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" onClick={() => addToast({ type: 'success', title: 'Running schedule', message: s.name })}>
                            <Play className="w-3.5 h-3.5" />
                          </button>
                        </PermissionGate>
                        <PermissionGate resource="reports" action="edit">
                          <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" onClick={() => addToast({ type: 'info', title: 'Edit schedule', message: s.name })}>
                            <Edit3 className="w-3.5 h-3.5" />
                          </button>
                        </PermissionGate>
                        <PermissionGate resource="reports" action="delete">
                          <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm text-red-500" onClick={() => {
                            setSchedules(prev => prev.filter(x => x.id !== s.id))
                            addToast({ type: 'info', title: 'Schedule deleted', message: s.name })
                          }}>
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </PermissionGate>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {scheduledReports.length === 0 && (
            <div className="enterprise-empty-state">
              <Calendar className="w-10 h-10" />
              <h3>No schedules yet</h3>
              <p>Create a schedule to automate report deliveries</p>
            </div>
          )}
        </div>
      </div>
    )
  }

  function renderSaved() {
    return (
      <div className="space-y-6">
        <h2 className="text-lg font-semibold text-gray-900">Saved Report Configurations</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {savedReports.map(r => (
            <div key={r.id} className="enterprise-card p-5">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-violet-50">
                    <Save className="w-5 h-5 text-violet-600" />
                  </div>
                  <div>
                    {renamingId === r.id ? (
                      <div className="flex items-center gap-2">
                        <Autocomplete
                          value={renameValue}
                          onChange={setRenameValue}
                          minChars={0}
                          inputClassName="enterprise-input text-sm !h-8 !py-1"
                        />
                        <PermissionGate resource="reports" action="edit">
                          <button className="enterprise-btn enterprise-btn-primary enterprise-btn-sm" onClick={() => confirmRename(r.id)}>
                            <CheckCircle className="w-3 h-3" />
                          </button>
                        </PermissionGate>
                        <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" onClick={() => setRenamingId(null)}>
                          <XCircle className="w-3 h-3" />
                        </button>
                      </div>
                    ) : (
                      <h3 className="text-sm font-semibold text-gray-900">{r.name}</h3>
                    )}
                    <p className="text-xs text-gray-500 mt-0.5">{r.description}</p>
                    <p className="text-xs text-gray-400 mt-1">Modified: {r.lastModified}</p>
                  </div>
                </div>
                <div className="flex gap-1">
                  <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" onClick={() => loadSavedReport(r.id)}>
                    <Eye className="w-3.5 h-3.5" />
                  </button>
                  <PermissionGate resource="reports" action="edit">
                    <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" onClick={() => startRename(r.id)}>
                      <Edit3 className="w-3.5 h-3.5" />
                    </button>
                  </PermissionGate>
                  <PermissionGate resource="reports" action="delete">
                    <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm text-red-500" onClick={() => deleteSavedReport(r.id)}>
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </PermissionGate>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6" onClick={() => closeMenus()}>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2.5">
            <BarChart3 className="w-5 h-5" /> Report Builder &amp; BI
          </h1>
          <p className="text-sm text-gray-500 mt-1">Custom reports, saved dashboards &amp; scheduled exports</p>
        </div>
      </div>

      <div className="enterprise-tabs">
        {([
          { id: 'dashboard' as TabId, label: 'Dashboard', icon: LayoutDashboard },
          { id: 'reports' as TabId, label: 'Reports', icon: FileBarChart },
          { id: 'scheduled' as TabId, label: 'Scheduled', icon: CalendarClock },
          { id: 'saved' as TabId, label: 'Saved', icon: BookmarkCheck },
        ]).map(tab => {
          const Icon = tab.icon
          return (
            <button
              key={tab.id}
              className={clsx('enterprise-tab flex items-center gap-1.5', activeTab === tab.id && 'enterprise-tab-active')}
              onClick={() => setActiveTab(tab.id)}
            >
              <Icon className="w-4 h-4" />
              {tab.label}
            </button>
          )
        })}
      </div>

      {activeTab === 'dashboard' && renderDashboard()}
      {activeTab === 'reports' && renderReports()}
      {activeTab === 'scheduled' && renderScheduled()}
      {activeTab === 'saved' && renderSaved()}

      {showAddWidget && (
        <div className="enterprise-modal-overlay" onClick={() => setShowAddWidget(false)}>
          <div className="enterprise-modal" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-base font-semibold text-gray-900">Add Widget</h2>
              <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-icon" onClick={() => setShowAddWidget(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="enterprise-modal-body space-y-4">
              <div className="enterprise-form-group">
                <label>Widget Type</label>
                <Autocomplete value={selectedWidgetType} onChange={setSelectedWidgetType} suggestions={WIDGET_TYPES.map(t => t.value)} minChars={0} />
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button className="enterprise-btn enterprise-btn-secondary" onClick={() => setShowAddWidget(false)}>Cancel</button>
              <PermissionGate resource="reports" action="create">
                <button className="enterprise-btn enterprise-btn-primary" onClick={addWidget}>
                  <Plus className="w-4 h-4" /> Add to Dashboard
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {showCustomReport && (
        <div className="enterprise-modal-overlay" onClick={() => setShowCustomReport(false)}>
          <div className="enterprise-modal !min-w-[560px]" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-base font-semibold text-gray-900">Create Custom Report</h2>
              <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-icon" onClick={() => setShowCustomReport(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="enterprise-modal-body space-y-5">
              <div className="enterprise-form-group">
                <label>Report Name</label>
                <Autocomplete value={customReportName} onChange={setCustomReportName} placeholder="e.g. Monthly Operations Summary" minChars={0} inputClassName="enterprise-input w-full" />
              </div>
              <div className="enterprise-form-group">
                <label>Date Range</label>
                <Autocomplete value={customDateRange} onChange={setCustomDateRange} suggestions={['today', 'last7', 'last30', 'last90', 'thisYear', 'custom']} minChars={0} />
              </div>
              <div>
                <label className="text-sm font-medium text-gray-700 mb-2 block">Metrics</label>
                <div className="grid grid-cols-2 gap-2">
                  {METRICS.map(m => (
                    <label key={m} className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                      <Autocomplete value={customMetrics.includes(m) ? 'true' : 'false'} onChange={() => toggleMetric(m)} minChars={0} inputClassName="rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
                      {m}
                    </label>
                  ))}
                </div>
              </div>
              <div className="enterprise-form-row">
                <div className="enterprise-form-group">
                  <label>Group By</label>
                  <Autocomplete value={customGroupBy} onChange={setCustomGroupBy} suggestions={['Day', 'Week', 'Month', 'Location', 'SKU']} minChars={0} />
                </div>
                <div className="enterprise-form-group">
                  <label>Format</label>
                  <Autocomplete value={customFormat} onChange={setCustomFormat} suggestions={FORMATS} minChars={0} />
                </div>
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button className="enterprise-btn enterprise-btn-secondary" onClick={() => setShowCustomReport(false)}>Cancel</button>
              <PermissionGate resource="reports" action="create">
                <button className="enterprise-btn enterprise-btn-primary" disabled={generating} onClick={handleGenerateReport}>
                  {generating ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                  {generating ? 'Generating...' : 'Generate Report'}
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {showCreateSchedule && (
        <div className="enterprise-modal-overlay" onClick={() => setShowCreateSchedule(false)}>
          <div className="enterprise-modal" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-base font-semibold text-gray-900">Create Schedule</h2>
              <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-icon" onClick={() => setShowCreateSchedule(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="enterprise-modal-body space-y-4">
              <div className="enterprise-form-group">
                <label>Schedule Name</label>
                <Autocomplete value={scheduleForm.name} onChange={val => setScheduleForm(p => ({ ...p, name: val }))} placeholder="e.g. Weekly Ops Report" minChars={0} inputClassName="enterprise-input w-full" />
              </div>
              <div className="enterprise-form-group">
                <label>Report</label>
                <Autocomplete value={scheduleForm.report} onChange={val => setScheduleForm(p => ({ ...p, report: val }))} suggestions={reportTemplates.map(t => t.name)} minChars={0} />
              </div>
              <div className="enterprise-form-row">
                <div className="enterprise-form-group">
                  <label>Frequency</label>
                  <Autocomplete value={scheduleForm.frequency} onChange={val => setScheduleForm(p => ({ ...p, frequency: val }))} suggestions={['Daily', 'Weekly', 'Monthly']} minChars={0} />
                </div>
                <div className="enterprise-form-group">
                  <label>Recipients (comma-separated)</label>
                  <Autocomplete value={scheduleForm.recipients} onChange={val => setScheduleForm(p => ({ ...p, recipients: val }))} placeholder="email@domain.com" minChars={0} inputClassName="enterprise-input" />
                </div>
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button className="enterprise-btn enterprise-btn-secondary" onClick={() => setShowCreateSchedule(false)}>Cancel</button>
              <PermissionGate resource="reports" action="create">
                <button className="enterprise-btn enterprise-btn-primary" onClick={() => handleScheduleReport(scheduleForm)}>
                  <Calendar className="w-4 h-4" /> Create Schedule
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
