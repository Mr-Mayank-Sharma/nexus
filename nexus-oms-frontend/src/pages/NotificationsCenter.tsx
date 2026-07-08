import { useState, useMemo, useEffect, useCallback } from 'react'
import {
  Bell, BellRing, AlertTriangle, CheckCircle, XCircle, Settings,
  Filter, CheckCheck, Trash2, Eye, ShoppingBag,
  Info, X, RefreshCw,
} from 'lucide-react'
import {
  EnterpriseToolbar, EnterpriseTabs, EnterpriseDataGrid, EnterpriseKPICard,
  EnterpriseBreadcrumbs, EnterpriseStatusBadge,
} from '../components/enterprise'
import type { Column, Tab } from '../components/enterprise'
import * as notificationsApi from '../api/notifications'
import type { NotificationLog } from '../api/notifications'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'

interface NotificationItem {
  id: string
  type: 'order' | 'alert' | 'payment' | 'shipment' | 'system' | 'success'
  title: string
  description: string
  timestamp: string
  read: boolean
  category: string
  metadata?: Record<string, any>
  entityId?: string
}

const typeIcons: Record<string, React.ReactNode> = {
  order: <ShoppingBag className="w-4 h-4" />,
  alert: <AlertTriangle className="w-4 h-4" />,
  payment: <CreditCardIcon className="w-4 h-4" />,
  shipment: <TruckIcon className="w-4 h-4" />,
  system: <Settings className="w-4 h-4" />,
  success: <CheckCircle className="w-4 h-4" />,
}

const typeLabels: Record<string, string> = {
  order: 'Order',
  alert: 'Alert',
  payment: 'Payment',
  shipment: 'Shipment',
  system: 'System',
  success: 'Success',
}

const typeBadgeColors: Record<string, string> = {
  order: 'info',
  alert: 'warning',
  payment: 'primary',
  shipment: 'info',
  system: 'neutral',
  success: 'success',
}

const typeOptions = ['order', 'alert', 'payment', 'shipment', 'system', 'success']

const readFilterOpts = [
  { value: 'all', label: 'All' },
  { value: 'read', label: 'Read' },
  { value: 'unread', label: 'Unread' },
]

function CreditCardIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1" y="4" width="22" height="16" rx="2" ry="2" />
      <line x1="1" y1="10" x2="23" y2="10" />
    </svg>
  )
}

function TruckIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1" y="3" width="15" height="13" />
      <polygon points="16 8 20 8 23 11 23 16 16 16 16 8" />
      <circle cx="5.5" cy="18.5" r="2.5" />
      <circle cx="18.5" cy="18.5" r="2.5" />
    </svg>
  )
}

function channelToType(channel: string): NotificationItem['type'] {
  const map: Record<string, NotificationItem['type']> = {
    EMAIL: 'system',
    SMS: 'alert',
    PUSH: 'alert',
    IN_APP: 'order',
    SLACK: 'system',
  }
  return map[channel] || 'system'
}

function channelToCategory(channel: string): string {
  const map: Record<string, string> = {
    EMAIL: 'system',
    SMS: 'alerts',
    PUSH: 'alerts',
    IN_APP: 'orders',
    SLACK: 'system',
  }
  return map[channel] || 'system'
}

function mapLogToItem(log: NotificationLog): NotificationItem {
  return {
    id: log.id,
    type: channelToType(log.channel),
    title: log.subject,
    description: log.body,
    timestamp: log.sentAt,
    read: log.status === 'READ' || !!log.readAt,
    category: channelToCategory(log.channel),
  }
}

const PAGE_SIZE = 15

export default function NotificationsCenter() {
  const [activeTab, setActiveTab] = useState('all')
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [typeFilter, setTypeFilter] = useState<Set<string>>(new Set())
  const [readFilter, setReadFilter] = useState<'all' | 'read' | 'unread'>('all')

  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [alertRulesCount, setAlertRulesCount] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const { addToast } = useToast()

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [logsRes, rulesRes, unreadRes] = await Promise.allSettled([
        notificationsApi.getNotificationLogs(page, PAGE_SIZE),
        notificationsApi.getAlertRules(0, 1),
        notificationsApi.getUnreadCount(),
      ])

      if (logsRes.status === 'fulfilled') {
        const d = logsRes.value.data
        const items = Array.isArray(d) ? d : (d?.content || [])
        setNotifications(items.map(mapLogToItem))
        setTotalElements(Array.isArray(d) ? (logsRes.value.pagination?.total ?? 0) : (d?.totalElements ?? 0))
      } else {
        setNotifications([])
        setTotalElements(0)
      }

      if (rulesRes.status === 'fulfilled') {
        const d = rulesRes.value.data
        setAlertRulesCount(Array.isArray(d) ? (rulesRes.value.pagination?.total ?? 0) : (d?.totalElements ?? 0))
      } else {
        setAlertRulesCount(0)
      }

      if (unreadRes.status === 'fulfilled') {
        setUnreadCount(unreadRes.value.data.count ?? 0)
      } else {
        setUnreadCount(0)
      }
    } catch {
      setError('Failed to load notifications')
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const tabs: Tab[] = [
    { id: 'all', label: 'All', icon: <Bell className="w-4 h-4" />, badge: unreadCount },
    { id: 'orders', label: 'Orders', icon: <ShoppingBag className="w-4 h-4" />, badge: notifications.filter(n => n.category === 'orders' && !n.read).length },
    { id: 'alerts', label: 'Alerts', icon: <AlertTriangle className="w-4 h-4" />, badge: notifications.filter(n => n.category === 'alerts' && !n.read).length },
    { id: 'system', label: 'System', icon: <Settings className="w-4 h-4" />, badge: notifications.filter(n => n.category === 'system' && !n.read).length },
  ]

  const filtered = useMemo(() => {
    let items = notifications
    if (activeTab !== 'all') {
      items = items.filter(n => n.category === activeTab)
    }
    if (dateFrom) {
      items = items.filter(n => n.timestamp >= dateFrom)
    }
    if (dateTo) {
      items = items.filter(n => n.timestamp <= dateTo)
    }
    if (typeFilter.size > 0) {
      items = items.filter(n => typeFilter.has(n.type))
    }
    if (readFilter === 'read') {
      items = items.filter(n => n.read)
    } else if (readFilter === 'unread') {
      items = items.filter(n => !n.read)
    }
    return items
  }, [notifications, activeTab, dateFrom, dateTo, typeFilter, readFilter])

  const kpis = useMemo(() => {
    const today = new Date().toISOString().slice(0, 10)
    const todayItems = notifications.filter(n => n.timestamp.startsWith(today))
    const alerts = notifications.filter(n => n.type === 'alert')
    const system = notifications.filter(n => n.category === 'system')
    return { totalToday: todayItems.length, alerts: alerts.length, system: system.length }
  }, [notifications])

  function toggleTypeFilter(type: string) {
    const next = new Set(typeFilter)
    if (next.has(type)) { next.delete(type) } else { next.add(type) }
    setTypeFilter(next)
  }

  function handleMarkAllRead() {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })))
    setSelectedIds([])
    addToast({ type: 'success', title: 'All notifications marked as read' })
  }

  function handleClearAll() {
    const keep = new Set(selectedIds)
    setNotifications(prev => prev.filter(n => !keep.has(n.id)))
    setSelectedIds([])
    addToast({ type: 'success', title: 'Selected notifications cleared' })
  }

  function toggleRead(id: string) {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: !n.read } : n))
    addToast({ type: 'success', title: 'Read status updated' })
  }

  function handleDelete(id: string) {
    setNotifications(prev => prev.filter(n => n.id !== id))
    if (expandedId === id) setExpandedId(null)
    addToast({ type: 'success', title: 'Notification deleted' })
  }

  function handlePageChange(newPage: number) {
    setPage(newPage)
    setExpandedId(null)
    setSelectedIds([])
  }

  const columns: Column<NotificationItem>[] = [
    {
      key: 'status', label: '', width: '32px',
      render: (_: any, row: NotificationItem) => (
        <div className="flex items-center justify-center">
          {!row.read && <span className="w-2 h-2 rounded-full bg-[var(--color-primary-500)]" />}
        </div>
      ),
    },
    {
      key: 'type', label: 'Type', width: '120px', sortable: true,
      render: (val: string) => (
        <div className="flex items-center gap-1.5">
          <span className={val === 'alert' ? 'text-[var(--color-error)]' : val === 'success' ? 'text-[var(--color-success)]' : 'text-[var(--text-secondary)]'}>
            {typeIcons[val]}
          </span>
          <EnterpriseStatusBadge status={typeBadgeColors[val] || 'neutral'} size="sm">
            {typeLabels[val]}
          </EnterpriseStatusBadge>
        </div>
      ),
    },
    { key: 'title', label: 'Title', sortable: true, minWidth: '200px' },
    {
      key: 'description', label: 'Description', minWidth: '300px',
      render: (val: string) => (
        <span className="truncate block max-w-[300px] text-[var(--text-secondary)] text-sm" title={val}>
          {val}
        </span>
      ),
    },
    { key: 'timestamp', label: 'Timestamp', sortable: true, minWidth: '150px' },
    {
      key: 'actions', label: 'Actions', width: '120px',
      render: (_: any, row: NotificationItem) => (
        <div className="flex items-center gap-1" onClick={e => e.stopPropagation()}>
          <button
            className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1"
            title={row.read ? 'Mark Unread' : 'Mark Read'}
            onClick={() => toggleRead(row.id)}
          >
            {row.read ? <BellRing className="w-4 h-4" /> : <CheckCheck className="w-4 h-4" />}
          </button>
          <button
            className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1 text-[var(--color-error)]"
            title="Delete"
            onClick={() => handleDelete(row.id)}
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      ),
    },
  ]

  function handleRowClick(row: NotificationItem) {
    setExpandedId(expandedId === row.id ? null : row.id)
  }

  const handleDetailAction = (action: string, item: NotificationItem) => {
    switch (action) {
      case 'View Order':
        window.open(`/orders/${item.metadata?.orderId || item.entityId || ''}`, '_blank')
        break
      case 'View Inventory':
        window.open(`/inventory`, '_blank')
        break
      case 'View Dashboard':
        window.open(`/`, '_blank')
        break
      case 'View Invoice':
        window.open(`/invoices`, '_blank')
        break
      case 'Track Shipment':
        window.open(`/shipping`, '_blank')
        break
      default:
        break
    }
  }

  const detailActions: Record<string, { label: string; icon: React.ReactNode; onClick: (item: NotificationItem) => void }[]> = {
    order: [
      { label: 'View Order', icon: <Eye className="w-4 h-4" />, onClick: (item) => handleDetailAction('View Order', item) },
      { label: 'View Inventory', icon: <Info className="w-4 h-4" />, onClick: (item) => handleDetailAction('View Inventory', item) },
    ],
    alert: [
      { label: 'View Inventory', icon: <Info className="w-4 h-4" />, onClick: (item) => handleDetailAction('View Inventory', item) },
      { label: 'View Dashboard', icon: <Eye className="w-4 h-4" />, onClick: (item) => handleDetailAction('View Dashboard', item) },
    ],
    payment: [
      { label: 'View Invoice', icon: <Eye className="w-4 h-4" />, onClick: (item) => handleDetailAction('View Invoice', item) },
    ],
    shipment: [
      { label: 'View Order', icon: <Eye className="w-4 h-4" />, onClick: (item) => handleDetailAction('View Order', item) },
      { label: 'Track Shipment', icon: <TruckIcon className="w-4 h-4" />, onClick: (item) => handleDetailAction('Track Shipment', item) },
    ],
    system: [
      { label: 'View Details', icon: <Eye className="w-4 h-4" />, onClick: (item) => handleDetailAction('View Details', item) },
    ],
    success: [
      { label: 'View Details', icon: <Eye className="w-4 h-4" />, onClick: (item) => handleDetailAction('View Details', item) },
    ],
  }

  if (error) {
    return (
      <div className="space-y-6">
        <EnterpriseBreadcrumbs crumbs={[
          { label: 'Home', path: '/' },
          { label: 'System', path: '/system' },
          { label: 'Notifications' },
        ]} />
        <EnterpriseToolbar
          title="Notifications Center"
          subtitle="Manage system alerts and notifications"
          actions={[
            { label: 'Settings', icon: <Settings className="w-4 h-4" />, onClick: () => addToast({ type: 'info', title: 'Notification settings' }), variant: 'ghost' },
          ]}
        />
        <div className="enterprise-card p-12 text-center">
          <AlertTriangle className="w-14 h-14 mx-auto text-[var(--color-error)] mb-4" />
          <h2 className="text-xl font-semibold text-[var(--text-primary)] mb-2">Failed to load notifications</h2>
          <p className="text-sm text-[var(--text-secondary)] mb-6 max-w-md mx-auto">{error}. Check your connection and try again.</p>
          <button onClick={fetchData} className="enterprise-btn enterprise-btn-primary inline-flex items-center gap-2">
            <RefreshCw className="w-4 h-4" />
            Retry
          </button>
        </div>
      </div>
    )
  }

  if (loading && notifications.length === 0) {
    return (
      <div className="space-y-6">
        <EnterpriseBreadcrumbs crumbs={[
          { label: 'Home', path: '/' },
          { label: 'System', path: '/system' },
          { label: 'Notifications' },
        ]} />
        <EnterpriseToolbar
          title="Notifications Center"
          subtitle="Manage system alerts and notifications"
          actions={[
            { label: 'Settings', icon: <Settings className="w-4 h-4" />, onClick: () => addToast({ type: 'info', title: 'Notification settings' }), variant: 'ghost' },
          ]}
        />
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <EnterpriseKPICard key={i} title="" value="" loading color="primary" />
          ))}
        </div>
        <div className="enterprise-card p-4">
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex gap-4 p-3">
                <div className="enterprise-skeleton w-8 h-4 shrink-0" />
                <div className="enterprise-skeleton h-4 w-20 shrink-0" />
                <div className="enterprise-skeleton h-4 flex-1" />
                <div className="enterprise-skeleton h-4 w-40 shrink-0" />
                <div className="enterprise-skeleton h-4 w-24 shrink-0" />
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[
        { label: 'Home', path: '/' },
        { label: 'System', path: '/system' },
        { label: 'Notifications' },
      ]} />

      <EnterpriseToolbar
        title="Notifications Center"
        subtitle="Manage system alerts and notifications"
        actions={[
          { label: 'Settings', icon: <Settings className="w-4 h-4" />, onClick: () => addToast({ type: 'info', title: 'Notification settings' }), variant: 'ghost' },
        ]}
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Total Today"
          value={kpis.totalToday}
          icon={<Bell className="w-5 h-5" />}
          color="primary"
          trend={kpis.totalToday > 10 ? 'up' : 'down'}
          trendValue={kpis.totalToday > 10 ? '+12% vs yesterday' : '-8% vs yesterday'}
        />
        <EnterpriseKPICard
          title="Unread"
          value={unreadCount}
          icon={<BellRing className="w-5 h-5" />}
          color="warning"
          trend={unreadCount > 0 ? 'up' : 'neutral'}
          trendValue={unreadCount > 0 ? `${unreadCount} pending` : 'All caught up'}
        />
        <EnterpriseKPICard
          title="Alerts"
          value={alertRulesCount}
          icon={<AlertTriangle className="w-5 h-5" />}
          color="error"
          trend={alertRulesCount > 0 ? 'up' : 'down'}
          trendValue={alertRulesCount > 0 ? `${alertRulesCount} active rules` : 'No active rules'}
        />
        <EnterpriseKPICard
          title="System Notifications"
          value={kpis.system}
          icon={<Settings className="w-5 h-5" />}
          color="info"
          trend="neutral"
          trendValue={`${notifications.filter(n => n.category === 'system').length} loaded`}
        />
      </div>

      <EnterpriseTabs tabs={tabs} activeTab={activeTab} onChange={setActiveTab} variant="underline" />

      <div className="enterprise-card p-4">
        <div className="flex items-center gap-4 flex-wrap">
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium text-[var(--text-secondary)]">From</span>
            <Autocomplete
              className="enterprise-input text-sm py-1.5"
              value={dateFrom}
              onChange={v => setDateFrom(v)}
              minChars={0}
            />
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium text-[var(--text-secondary)]">To</span>
            <Autocomplete
              className="enterprise-input text-sm py-1.5"
              value={dateTo}
              onChange={v => setDateTo(v)}
              minChars={0}
            />
          </div>
          <div className="w-px h-6 bg-[var(--border-color)]" />
          <div className="flex items-center gap-1.5 flex-wrap">
            <Filter className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />
            {typeOptions.map(type => (
              <button
                key={type}
                className={`text-xs px-2 py-1 rounded-full border transition-colors ${
                  typeFilter.has(type)
                    ? 'bg-[var(--color-primary-100)] border-[var(--color-primary-300)] text-[var(--color-primary-700)]'
                    : 'border-[var(--border-color)] text-[var(--text-secondary)] hover:border-[var(--text-tertiary)]'
                }`}
                onClick={() => toggleTypeFilter(type)}
              >
                {typeLabels[type]}
              </button>
            ))}
          </div>
          <div className="w-px h-6 bg-[var(--border-color)]" />
          <Autocomplete
            className="enterprise-input text-sm py-1.5"
            value={readFilter}
            onChange={v => setReadFilter(v as 'all' | 'read' | 'unread')}
            suggestions={readFilterOpts}
            getOptionLabel={o => o.label}
            getOptionValue={o => o.value}
            minChars={0}
          />
          <div className="flex-1" />
          <button
            className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm"
            onClick={handleMarkAllRead}
          >
            <CheckCheck className="w-4 h-4" /> Mark All Read
          </button>
          <button
            className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm text-[var(--color-error)]"
            onClick={handleClearAll}
          >
            <XCircle className="w-4 h-4" /> Clear All
          </button>
        </div>
      </div>

      <div className="space-y-2">
        <EnterpriseDataGrid
          columns={columns}
          data={filtered}
          rowKey="id"
          selectable
          onSelectionChange={setSelectedIds}
          onRowClick={handleRowClick}
          page={page}
          pageSize={PAGE_SIZE}
          totalElements={totalElements}
          onPageChange={handlePageChange}
        />

        {expandedId && (
          <div className="enterprise-card p-5 space-y-4">
            {(() => {
              const item = notifications.find(n => n.id === expandedId)
              if (!item) return null
              const actions = detailActions[item.type] || []
              return (
                <>
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                      <div className={`p-2 rounded-lg ${
                        item.type === 'alert' ? 'bg-red-100 text-red-600' :
                        item.type === 'success' ? 'bg-green-100 text-green-600' :
                        'bg-[var(--bg-tertiary)] text-[var(--text-secondary)]'
                      }`}>
                        {typeIcons[item.type]}
                      </div>
                      <div>
                        <h3 className="text-base font-semibold text-[var(--text-primary)]">{item.title}</h3>
                        <p className="text-xs text-[var(--text-tertiary)]">{item.timestamp}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm"
                        onClick={() => toggleRead(item.id)}
                      >
                        {item.read ? <BellRing className="w-4 h-4" /> : <CheckCheck className="w-4 h-4" />}
                        {item.read ? 'Mark Unread' : 'Mark Read'}
                      </button>
                      <button
                        className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm text-[var(--color-error)]"
                        onClick={() => { handleDelete(item.id); setExpandedId(null) }}
                      >
                        <Trash2 className="w-4 h-4" /> Delete
                      </button>
                      <button
                        className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1"
                        onClick={() => setExpandedId(null)}
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                  <div className="border-t border-[var(--border-color)] pt-4">
                    <p className="text-sm text-[var(--text-secondary)] leading-relaxed">{item.description}</p>
                  </div>
                  <div className="border-t border-[var(--border-color)] pt-4">
                    <div className="grid grid-cols-2 gap-4 text-sm">
                      <div>
                        <span className="text-xs font-medium text-[var(--text-tertiary)] uppercase">Type</span>
                        <p className="text-[var(--text-primary)] mt-0.5">
                          <EnterpriseStatusBadge status={typeBadgeColors[item.type] || 'neutral'} size="sm">
                            {typeLabels[item.type]}
                          </EnterpriseStatusBadge>
                        </p>
                      </div>
                      <div>
                        <span className="text-xs font-medium text-[var(--text-tertiary)] uppercase">Category</span>
                        <p className="text-[var(--text-primary)] mt-0.5 capitalize">{item.category}</p>
                      </div>
                      <div>
                        <span className="text-xs font-medium text-[var(--text-tertiary)] uppercase">Status</span>
                        <p className="text-[var(--text-primary)] mt-0.5">
                          <EnterpriseStatusBadge status={item.read ? 'neutral' : 'info'} size="sm">
                            {item.read ? 'Read' : 'Unread'}
                          </EnterpriseStatusBadge>
                        </p>
                      </div>
                      <div>
                        <span className="text-xs font-medium text-[var(--text-tertiary)] uppercase">ID</span>
                        <p className="text-[var(--text-primary)] mt-0.5 font-mono text-xs">{item.id}</p>
                      </div>
                    </div>
                  </div>
                  {actions.length > 0 && (
                    <div className="border-t border-[var(--border-color)] pt-4 flex items-center gap-2">
                      {actions.map((action, i) => (
                        <button key={i} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm" onClick={() => action.onClick?.(item)}>
                          {action.icon} {action.label}
                        </button>
                      ))}
                    </div>
                  )}
                </>
              )
            })()}
          </div>
        )}
      </div>
    </div>
  )
}
