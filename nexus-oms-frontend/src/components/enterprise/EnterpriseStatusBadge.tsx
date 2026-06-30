import { clsx } from 'clsx'
import { ReactNode } from 'react'

type StatusType = 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary'

interface Props {
  status: StatusType | string
  label?: string
  children?: ReactNode
  size?: 'sm' | 'md'
}

const colorMap: Record<string, string> = {
  success: 'enterprise-badge-success',
  warning: 'enterprise-badge-warning',
  error: 'enterprise-badge-error',
  info: 'enterprise-badge-info',
  neutral: 'enterprise-badge-neutral',
  primary: 'enterprise-badge-info',
  active: 'enterprise-badge-success',
  inactive: 'enterprise-badge-neutral',
  pending: 'enterprise-badge-warning',
  draft: 'enterprise-badge-neutral',
  completed: 'enterprise-badge-success',
  failed: 'enterprise-badge-error',
  cancelled: 'enterprise-badge-error',
  paid: 'enterprise-badge-success',
  shipped: 'enterprise-badge-info',
  delivered: 'enterprise-badge-success',
  processing: 'enterprise-badge-warning',
  running: 'enterprise-badge-info',
  paused: 'enterprise-badge-warning',
  requested: 'enterprise-badge-warning',
  approved: 'enterprise-badge-info',
  received: 'enterprise-badge-info',
  inspected: 'enterprise-badge-info',
  refunded: 'enterprise-badge-success',
  rejected: 'enterprise-badge-error',
  blacklisted: 'enterprise-badge-error',
  expired: 'enterprise-badge-warning',
  terminated: 'enterprise-badge-error',
  sent: 'enterprise-badge-info',
  under_review: 'enterprise-badge-warning',
  awarded: 'enterprise-badge-success',
  pending_approval: 'enterprise-badge-warning',
  partially_received: 'enterprise-badge-info',
}

export default function EnterpriseStatusBadge({ status, label, children, size = 'md' }: Props) {
  const content = label || children || status
  const className = colorMap[status.toLowerCase()] || 'enterprise-badge-neutral'

  return (
    <span className={clsx(className, size === 'sm' && 'text-[10px] px-1.5 py-0.5')}>
      {content}
    </span>
  )
}
