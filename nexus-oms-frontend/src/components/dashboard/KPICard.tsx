import { TrendingUp, TrendingDown, Minus } from 'lucide-react'
import { DashboardKPI } from '../../types'
import * as Icons from 'lucide-react'
import { clsx } from 'clsx'

interface KPICardProps {
  kpi: DashboardKPI
}

const iconMap: Record<string, React.ComponentType<{ className?: string }>> = {
  ShoppingCart: Icons.ShoppingCart,
  CheckCircle: Icons.CheckCircle,
  DollarSign: Icons.DollarSign,
  AlertTriangle: Icons.AlertTriangle,
  Truck: Icons.Truck,
  Package: Icons.Package,
  Users: Icons.Users,
  TrendingUp: Icons.TrendingUp,
}

export default function KPICard({ kpi }: KPICardProps) {
  const Icon = iconMap[kpi.icon] || Icons.ShoppingCart

  return (
    <div className="card p-5 hover:shadow-md transition-shadow">
      <div className="flex items-start justify-between">
        <div className="space-y-2">
          <p className="text-sm font-medium text-gray-500">{kpi.label}</p>
          <div className="flex items-baseline gap-2">
            {kpi.prefix && <span className="text-sm text-gray-500">{kpi.prefix}</span>}
            <span className="text-2xl font-bold text-gray-900">{kpi.value}</span>
            {kpi.suffix && <span className="text-sm text-gray-500">{kpi.suffix}</span>}
          </div>
          <div className="flex items-center gap-1.5">
            {kpi.trend === 'up' && <TrendingUp className="w-4 h-4 text-green-500" />}
            {kpi.trend === 'down' && <TrendingDown className="w-4 h-4 text-red-500" />}
            {kpi.trend === 'neutral' && <Minus className="w-4 h-4 text-gray-400" />}
            <span
              className={clsx(
                'text-sm font-medium',
                kpi.trend === 'up' && 'text-green-600',
                kpi.trend === 'down' && 'text-red-600',
                kpi.trend === 'neutral' && 'text-gray-500',
              )}
            >
              {kpi.change > 0 ? '+' : ''}{kpi.change}%
            </span>
          </div>
        </div>
        <div className="p-3 bg-primary-50 rounded-lg">
          <Icon className="w-6 h-6 text-primary-600" />
        </div>
      </div>
      <div className="mt-4 h-1 bg-gray-100 rounded-full overflow-hidden">
        <div
          className={clsx(
            'h-full rounded-full transition-all duration-500',
            kpi.trend === 'up' ? 'bg-green-500' : kpi.trend === 'down' ? 'bg-red-500' : 'bg-gray-300',
          )}
          style={{ width: `${Math.min(Math.abs(kpi.change) * 5, 100)}%` }}
        />
      </div>
    </div>
  )
}
