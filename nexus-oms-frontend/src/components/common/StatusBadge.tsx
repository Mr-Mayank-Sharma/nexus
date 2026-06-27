import { OrderStatus } from '../../types'

interface StatusBadgeProps {
  status: OrderStatus | string
  size?: 'sm' | 'md' | 'lg'
}

const statusStyles: Record<string, string> = {
  PENDING: 'bg-blue-50 text-blue-700 border-blue-200',
  CONFIRMED: 'bg-blue-50 text-blue-700 border-blue-200',
  ALLOCATED: 'bg-purple-50 text-purple-700 border-purple-200',
  SHIPPED: 'bg-yellow-50 text-yellow-700 border-yellow-200',
  IN_TRANSIT: 'bg-yellow-50 text-yellow-700 border-yellow-200',
  DELIVERED: 'bg-green-50 text-green-700 border-green-200',
  CANCELLED: 'bg-red-50 text-red-700 border-red-200',
  EXCEPTION: 'bg-red-50 text-red-700 border-red-200',
  RETURNED: 'bg-orange-50 text-orange-700 border-orange-200',
  REQUESTED: 'bg-blue-50 text-blue-700 border-blue-200',
  APPROVED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  RECEIVED: 'bg-purple-50 text-purple-700 border-purple-200',
  INSPECTED: 'bg-teal-50 text-teal-700 border-teal-200',
  COMPLETED: 'bg-green-50 text-green-700 border-green-200',
  REJECTED: 'bg-red-50 text-red-700 border-red-200',
  ACTIVE: 'bg-green-50 text-green-700 border-green-200',
  TRAINING: 'bg-blue-50 text-blue-700 border-blue-200',
  ERROR: 'bg-red-50 text-red-700 border-red-200',
  DISABLED: 'bg-gray-50 text-gray-700 border-gray-200',
  LABELED: 'bg-cyan-50 text-cyan-700 border-cyan-200',
  PICKED_UP: 'bg-yellow-50 text-yellow-700 border-yellow-200',
  OUT_FOR_DELIVERY: 'bg-orange-50 text-orange-700 border-orange-200',
  CREATED: 'bg-gray-50 text-gray-700 border-gray-200',
}

const sizeStyles = {
  sm: 'px-2 py-0.5 text-xs',
  md: 'px-2.5 py-0.5 text-xs',
  lg: 'px-3 py-1 text-sm',
}

export default function StatusBadge({ status, size = 'md' }: StatusBadgeProps) {
  const style = statusStyles[status] || statusStyles.PENDING
  return (
    <span className={`inline-flex items-center rounded-full border font-medium ${style} ${sizeStyles[size]}`}>
      {status.replace(/_/g, ' ')}
    </span>
  )
}
