import { useState, useEffect } from 'react'
import { Bell, Package, AlertTriangle, CreditCard, Truck, CheckCircle, X, RefreshCw, Settings } from 'lucide-react'
import { clsx } from 'clsx'
import { useNavigate } from 'react-router-dom'

interface Notification {
  id: string
  type: 'order' | 'alert' | 'payment' | 'shipment' | 'system' | 'success'
  title: string
  description: string
  timestamp: string
  read: boolean
  actionable?: boolean
  actionLabel?: string
  actionPath?: string
}

const mockNotifications: Notification[] = [
  { id: '1', type: 'order', title: 'New Order #10002459', description: 'Order received from Acme Corp - $12,450', timestamp: '2 min ago', read: false, actionable: true, actionLabel: 'View Order', actionPath: '/orders' },
  { id: '2', type: 'alert', title: 'Low Stock Alert', description: 'SKU NEXUS-PRO-X1 is below threshold (12 remaining)', timestamp: '15 min ago', read: false, actionable: true, actionLabel: 'View Inventory', actionPath: '/inventory' },
  { id: '3', type: 'payment', title: 'Payment Confirmed', description: 'Payment of $8,230 received for Order #10002455', timestamp: '1 hour ago', read: false },
  { id: '4', type: 'shipment', title: 'Shipment Delayed', description: 'Carrier FedEx reported delay for shipment SHP-2024-892', timestamp: '2 hours ago', read: true },
  { id: '5', type: 'success', title: 'Integration Sync Complete', description: 'Shopify store synced successfully - 245 orders imported', timestamp: '3 hours ago', read: true },
  { id: '6', type: 'system', title: 'System Update', description: 'Scheduled maintenance completed at 02:00 AM', timestamp: '5 hours ago', read: true },
]

const typeIcons = {
  order: Package,
  alert: AlertTriangle,
  payment: CreditCard,
  shipment: Truck,
  system: Settings,
  success: CheckCircle,
}

const typeColors = {
  order: 'text-blue-600 bg-blue-100',
  alert: 'text-red-600 bg-red-100',
  payment: 'text-green-600 bg-green-100',
  shipment: 'text-purple-600 bg-purple-100',
  system: 'text-gray-600 bg-gray-100',
  success: 'text-green-600 bg-green-100',
}

interface Props {
  open: boolean
  onClose: () => void
}

export default function NotificationsPanel({ open, onClose }: Props) {
  const navigate = useNavigate()
  const [notifications, setNotifications] = useState<Notification[]>(mockNotifications)

  const unreadCount = notifications.filter(n => !n.read).length

  const markAllRead = () => {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })))
  }

  if (!open) return null

  return (
    <>
      <div className="fixed inset-0 z-30" onClick={onClose} />
      <div className="absolute right-0 top-full mt-2 w-[420px] bg-[var(--bg-card)] rounded-xl shadow-xl border border-[var(--border-color)] z-40 animate-[slideUp_200ms_ease-out]">
        <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--border-color)]">
          <div className="flex items-center gap-2">
            <Bell className="w-4 h-4 text-[var(--text-primary)]" />
            <span className="font-semibold text-sm text-[var(--text-primary)]">Notifications</span>
            {unreadCount > 0 && (
              <span className="text-xs bg-[var(--color-primary-100)] text-[var(--color-primary-700)] px-1.5 py-0.5 rounded-full font-semibold">
                {unreadCount}
              </span>
            )}
          </div>
          <div className="flex items-center gap-1">
            {unreadCount > 0 && (
              <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm text-xs" onClick={markAllRead}>
                Mark all read
              </button>
            )}
            <button onClick={onClose} className="p-1 rounded-md hover:bg-[var(--bg-tertiary)] text-[var(--text-secondary)]">
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        <div className="max-h-[400px] overflow-y-auto">
          {notifications.length === 0 ? (
            <div className="enterprise-empty-state py-8">
              <CheckCircle className="w-8 h-8" />
              <h3>All caught up!</h3>
              <p>No new notifications</p>
            </div>
          ) : (
            notifications.map(n => {
              const Icon = typeIcons[n.type]
              return (
                <div key={n.id} className={clsx(
                  'flex gap-3 px-4 py-3 border-b border-[var(--border-color)] last:border-0 transition-colors cursor-pointer',
                  !n.read ? 'bg-[var(--color-primary-50)]' : 'hover:bg-[var(--bg-tertiary)]'
                )}>
                  <div className={clsx('w-8 h-8 rounded-lg flex items-center justify-center shrink-0', typeColors[n.type])}>
                    <Icon className="w-4 h-4" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-2">
                      <p className={clsx('text-sm', !n.read ? 'font-semibold text-[var(--text-primary)]' : 'text-[var(--text-primary)]')}>
                        {n.title}
                      </p>
                      {!n.read && <span className="w-2 h-2 rounded-full bg-[var(--color-primary-500)] shrink-0 mt-1" />}
                    </div>
                    <p className="text-xs text-[var(--text-secondary)] mt-0.5">{n.description}</p>
                    <div className="flex items-center gap-3 mt-2">
                      <span className="text-[10px] text-[var(--text-tertiary)]">{n.timestamp}</span>
                      {n.actionable && n.actionLabel && (
                        <button
                          className="text-xs font-medium text-[var(--color-primary-600)] hover:text-[var(--color-primary-700)]"
                          onClick={() => { navigate(n.actionPath!); onClose() }}
                        >{n.actionLabel}</button>
                      )}
                    </div>
                  </div>
                </div>
              )
            })
          )}
        </div>

        <div className="px-4 py-2.5 border-t border-[var(--border-color)] bg-[var(--bg-secondary)]">
          <button
            className="w-full text-center text-xs font-medium text-[var(--color-primary-600)] hover:text-[var(--color-primary-700)]"
            onClick={() => { navigate('/notifications'); onClose() }}
          >
            View all notifications
          </button>
        </div>
      </div>
    </>
  )
}
