import { memo, ReactNode, isValidElement, createElement } from 'react'
import { TrendingUp, TrendingDown, Minus } from 'lucide-react'
import { clsx } from 'clsx'

interface Props {
  title: string
  value: string | number
  subtitle?: string
  icon?: ReactNode | React.ComponentType<{ className?: string }>
  trend?: 'up' | 'down' | 'neutral'
  trendValue?: string
  color?: 'primary' | 'success' | 'warning' | 'error' | 'info' | 'ai'
  loading?: boolean
  onClick?: () => void
  className?: string
}

function renderIcon(icon: Props['icon']): ReactNode {
  if (!icon) return null
  if (isValidElement(icon)) return icon
  if (typeof icon === 'function') return createElement(icon as React.ComponentType<{ className?: string }>)
  return null
}

const iconColorMap = {
  primary: 'text-[var(--text-brand)] bg-[var(--surface-brand)] ring-[var(--border-brand)]',
  success: 'text-[var(--text-success)] bg-[var(--nexus-success-50)] ring-[var(--border-success)]',
  warning: 'text-[var(--text-warning)] bg-[var(--nexus-warning-50)] ring-[var(--border-warning)]',
  error: 'text-[var(--text-error)] bg-[var(--nexus-error-50)] ring-[var(--border-error)]',
  info: 'text-[var(--text-info)] bg-[var(--nexus-info-50)] ring-[var(--border-info)]',
  ai: 'text-[var(--text-ai)] bg-[var(--nexus-ai-50)] ring-[var(--border-ai)]',
}

export default memo(function EnterpriseKPICard({ title, value, subtitle, icon, trend, trendValue, color = 'primary', loading, onClick, className }: Props) {
  return (
    <div
      role="region"
      aria-label={`${title}: ${typeof value === 'string' ? value : String(value)}`}
      className={clsx(
        'bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5 transition-all duration-150',
        onClick && 'cursor-pointer hover:shadow-md hover:border-[var(--border-default)]',
        loading && 'pointer-events-none',
        className,
      )}
      onClick={onClick}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={onClick ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick() } } : undefined}
    >
      {loading ? (
        <div className="space-y-3">
          <div className="enterprise-skeleton h-4 w-24" />
          <div className="enterprise-skeleton h-8 w-32" />
          <div className="enterprise-skeleton h-3 w-20" />
        </div>
      ) : (
        <>
          <div className="flex items-start justify-between">
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-[var(--text-secondary)] truncate">{title}</p>
              <p className="text-2xl font-bold text-[var(--text-primary)] mt-1 tracking-tight tabular-nums">{value}</p>
              {subtitle && <p className="text-xs text-[var(--text-tertiary)] mt-0.5">{subtitle}</p>}
            </div>
            {icon && (
              <div className={clsx(
                'w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ring-1',
                iconColorMap[color],
              )}>
                {renderIcon(icon)}
              </div>
            )}
          </div>
          {trend && (
            <div className="flex items-center gap-1.5 mt-3 pt-3 border-t border-[var(--border-subtle)]">
              <div className={clsx(
                'flex items-center gap-1 text-xs font-medium',
                trend === 'up' && 'text-[var(--text-success)]',
                trend === 'down' && 'text-[var(--text-error)]',
                trend === 'neutral' && 'text-[var(--text-tertiary)]',
              )}>
                {trend === 'up' && <TrendingUp className="w-3 h-3" />}
                {trend === 'down' && <TrendingDown className="w-3 h-3" />}
                {trend === 'neutral' && <Minus className="w-3 h-3" />}
                {trendValue}
              </div>
              {trend !== 'neutral' && (
                <span className="text-xs text-[var(--text-tertiary)]">vs last period</span>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
})
