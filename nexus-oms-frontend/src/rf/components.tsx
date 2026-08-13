import { useEffect, useState, type ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { Home, PackageCheck, Package, Truck, ClipboardCheck, Wifi, WifiOff, ScanLine } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import clsx from 'clsx'

export function useOnlineStatus(): boolean {
  const [online, setOnline] = useState(() =>
    typeof navigator !== 'undefined' ? navigator.onLine : true,
  )
  useEffect(() => {
    const on = () => setOnline(true)
    const off = () => setOnline(false)
    window.addEventListener('online', on)
    window.addEventListener('offline', off)
    return () => {
      window.removeEventListener('online', on)
      window.removeEventListener('offline', off)
    }
  }, [])
  return online
}

const BTN_STYLES = {
  primary:
    'bg-[var(--nexus-primary-600)] text-white active:bg-[var(--nexus-primary-700)]',
  success:
    'bg-[var(--nexus-success-600)] text-white active:bg-[var(--nexus-success-700)]',
  danger:
    'bg-[var(--nexus-error-600)] text-white active:bg-[var(--nexus-error-700)]',
  ghost:
    'bg-[var(--surface-muted)] text-[var(--text-primary)] active:bg-[var(--surface-sunken)]',
} as const

export function RfButton({
  variant = 'primary',
  disabled,
  full,
  className,
  children,
  ...rest
}: {
  variant?: keyof typeof BTN_STYLES
  disabled?: boolean
  full?: boolean
  className?: string
  children: ReactNode
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      {...rest}
      disabled={disabled}
      className={clsx(
        'rounded-xl px-5 py-3.5 text-base font-semibold transition-colors touch-manipulation',
        BTN_STYLES[variant],
        disabled && 'opacity-40 pointer-events-none',
        full && 'w-full',
        className,
      )}
    >
      {children}
    </button>
  )
}

export function RfCard({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={clsx('rounded-2xl bg-[var(--surface-base)] border border-[var(--border-default)] shadow-sm', className)}>
      {children}
    </div>
  )
}

export function RfBadge({ tone = 'default', children }: { tone?: 'default' | 'success' | 'warning' | 'danger' | 'info'; children: ReactNode }) {
  const tones: Record<string, string> = {
    default: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
    success: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]',
    warning: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]',
    danger: 'bg-[var(--nexus-error-100)] text-[var(--nexus-error-700)]',
    info: 'bg-[var(--nexus-info-100)] text-[var(--nexus-info-700)]',
  }
  return (
    <span className={clsx('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium', tones[tone])}>
      {children}
    </span>
  )
}

export function RfEmpty({ icon, text }: { icon?: ReactNode; text: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-[var(--text-tertiary)]">
      {icon}
      <p className="mt-2 text-sm">{text}</p>
    </div>
  )
}

export function ScreenHeader({ title, subtitle, right }: { title: string; subtitle?: string; right?: ReactNode }) {
  const { user } = useAuth()
  return (
    <div className="flex items-center justify-between mb-4">
      <div>
        <h1 className="text-xl font-bold text-[var(--text-primary)]">{title}</h1>
        {subtitle && <p className="text-xs text-[var(--text-secondary)] mt-0.5">{subtitle}</p>}
      </div>
      <div className="flex items-center gap-2">
        {right}
        {user && (
          <span className="text-xs text-[var(--text-tertiary)] max-w-[120px] truncate">{user.username}</span>
        )}
      </div>
    </div>
  )
}

const TABS = [
  { to: '/rf', label: 'Home', icon: Home, end: true },
  { to: '/rf/pick', label: 'Pick', icon: PackageCheck },
  { to: '/rf/pack', label: 'Pack', icon: Package },
  { to: '/rf/ship', label: 'Ship', icon: Truck },
  { to: '/rf/count', label: 'Count', icon: ClipboardCheck },
]

export function BottomNav() {
  return (
    <nav className="fixed bottom-0 inset-x-0 z-40 bg-[var(--surface-base)] border-t border-[var(--border-default)] pb-[env(safe-area-inset-bottom)]">
      <div className="grid grid-cols-5 max-w-md mx-auto">
        {TABS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) =>
              clsx(
                'flex flex-col items-center gap-0.5 py-2.5 text-[11px] font-medium transition-colors',
                isActive ? 'text-[var(--nexus-primary-600)]' : 'text-[var(--text-tertiary)]',
              )
            }
          >
            {({ isActive }) => (
              <>
                <tab.icon className={clsx('w-6 h-6', isActive && 'stroke-[2.5]')} />
                {tab.label}
              </>
            )}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}

export function OfflineBanner() {
  const online = useOnlineStatus()
  if (online) return null
  return (
    <div className="sticky top-0 z-30 flex items-center justify-center gap-2 bg-[var(--nexus-warning-600)] text-white text-xs font-medium py-2">
      <WifiOff className="w-4 h-4" />
      Offline — actions are disabled until reconnection
    </div>
  )
}

export function ScanShortcutButton({ onClick, label = 'Scan' }: { onClick: () => void; label?: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-black text-emerald-400 border border-emerald-500/40 text-sm font-semibold active:scale-95 transition-transform touch-manipulation"
    >
      <ScanLine className="w-4 h-4" />
      {label}
    </button>
  )
}

export function ConnectivityPill() {
  const online = useOnlineStatus()
  return (
    <span className={clsx('inline-flex items-center gap-1 text-[11px] font-medium', online ? 'text-[var(--nexus-success-600)]' : 'text-[var(--nexus-error-600)]')}>
      {online ? <Wifi className="w-3.5 h-3.5" /> : <WifiOff className="w-3.5 h-3.5" />}
      {online ? 'Online' : 'Offline'}
    </span>
  )
}
