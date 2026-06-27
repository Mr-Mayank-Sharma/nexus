import { ReactNode } from 'react'
import { TrendingUp, TrendingDown, Minus } from 'lucide-react'
import { clsx } from 'clsx'

interface Props {
  title: string
  value: string | number
  subtitle?: string
  icon?: ReactNode
  trend?: 'up' | 'down' | 'neutral'
  trendValue?: string
  color?: 'primary' | 'success' | 'warning' | 'error' | 'info' | 'ai'
  loading?: boolean
  onClick?: () => void
  className?: string
}

const iconColorMap = {
  primary: 'text-blue-600 bg-blue-50 ring-blue-500/20',
  success: 'text-emerald-600 bg-emerald-50 ring-emerald-500/20',
  warning: 'text-amber-600 bg-amber-50 ring-amber-500/20',
  error: 'text-red-600 bg-red-50 ring-red-500/20',
  info: 'text-cyan-600 bg-cyan-50 ring-cyan-500/20',
  ai: 'text-violet-600 bg-violet-50 ring-violet-500/20',
}

export default function EnterpriseKPICard({ title, value, subtitle, icon, trend, trendValue, color = 'primary', loading, onClick, className }: Props) {
  return (
    <div
      className={clsx(
        'bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 transition-all duration-150',
        onClick && 'cursor-pointer hover:shadow-md hover:border-gray-300 dark:hover:border-gray-600',
        loading && 'pointer-events-none',
        className,
      )}
      onClick={onClick}
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
              <p className="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">{title}</p>
              <p className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-1 tracking-tight">{value}</p>
              {subtitle && <p className="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{subtitle}</p>}
            </div>
            {icon && (
              <div className={clsx(
                'w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ring-1',
                iconColorMap[color],
              )}>
                {icon}
              </div>
            )}
          </div>
          {trend && (
            <div className="flex items-center gap-1.5 mt-3 pt-3 border-t border-gray-100 dark:border-gray-700">
              <div className={clsx(
                'flex items-center gap-1 text-xs font-medium',
                trend === 'up' && 'text-emerald-600 dark:text-emerald-400',
                trend === 'down' && 'text-red-600 dark:text-red-400',
                trend === 'neutral' && 'text-gray-400 dark:text-gray-500',
              )}>
                {trend === 'up' && <TrendingUp className="w-3 h-3" />}
                {trend === 'down' && <TrendingDown className="w-3 h-3" />}
                {trend === 'neutral' && <Minus className="w-3 h-3" />}
                {trendValue}
              </div>
              {trend !== 'neutral' && (
                <span className="text-xs text-gray-400 dark:text-gray-500">vs last period</span>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}
