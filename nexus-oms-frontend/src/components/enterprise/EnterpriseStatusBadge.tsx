import { clsx } from 'clsx'
import { memo, ReactNode } from 'react'

type StatusType = 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary'

interface Props {
  status: StatusType | string
  label?: string
  children?: ReactNode
  size?: 'sm' | 'md' | 'lg'
}

/* Canonical semantic contract:
   success = completed / healthy
   warning = in progress / needs attention
   error   = failed / blocked
   info    = in motion / progressing
   neutral = static / inactive */
const colorMap: Record<string, string> = {
  // Semantic tokens
  success: 'enterprise-badge-success',
  warning: 'enterprise-badge-warning',
  error: 'enterprise-badge-error',
  info: 'enterprise-badge-info',
  neutral: 'enterprise-badge-neutral',
  primary: 'enterprise-badge-brand',

  // success — completed / healthy
  completed: 'enterprise-badge-success',
  delivered: 'enterprise-badge-success',
  active: 'enterprise-badge-success',
  paid: 'enterprise-badge-success',
  refunded: 'enterprise-badge-success',
  awarded: 'enterprise-badge-success',
  up: 'enterprise-badge-success',

  // warning — in progress / needs attention
  pending: 'enterprise-badge-warning',
  processing: 'enterprise-badge-warning',
  requested: 'enterprise-badge-warning',
  under_review: 'enterprise-badge-warning',
  pending_approval: 'enterprise-badge-warning',
  paused: 'enterprise-badge-warning',
  expired: 'enterprise-badge-warning',
  returned: 'enterprise-badge-warning',
  out_for_delivery: 'enterprise-badge-warning',
  pending_payment: 'enterprise-badge-warning',

  // error — failed / blocked
  failed: 'enterprise-badge-error',
  cancelled: 'enterprise-badge-error',
  rejected: 'enterprise-badge-error',
  blacklisted: 'enterprise-badge-error',
  terminated: 'enterprise-badge-error',
  down: 'enterprise-badge-error',
  exception: 'enterprise-badge-error',

  // info — in motion / progressing
  shipped: 'enterprise-badge-info',
  in_transit: 'enterprise-badge-info',
  allocated: 'enterprise-badge-info',
  confirmed: 'enterprise-badge-info',
  running: 'enterprise-badge-info',
  scheduled: 'enterprise-badge-info',
  sent: 'enterprise-badge-info',
  received: 'enterprise-badge-info',
  inspected: 'enterprise-badge-info',
  approved: 'enterprise-badge-info',
  labeled: 'enterprise-badge-info',
  picked_up: 'enterprise-badge-info',
  training: 'enterprise-badge-info',
  partially_received: 'enterprise-badge-info',
  reserved: 'enterprise-badge-info',

  // neutral — static / inactive
  inactive: 'enterprise-badge-neutral',
  disabled: 'enterprise-badge-neutral',
  draft: 'enterprise-badge-neutral',
  unknown: 'enterprise-badge-neutral',
  created: 'enterprise-badge-neutral',
}

export default memo(function EnterpriseStatusBadge({ status, label, children, size = 'md' }: Props) {
  const content = label || children || status
  const variant = colorMap[status.toLowerCase()] || 'enterprise-badge-neutral'

  return (
    <span
      className={clsx(
        'enterprise-badge',
        variant,
        size === 'sm' && 'text-[10px] px-1.5 py-0.5',
        size === 'lg' && 'enterprise-badge-lg',
      )}
      role="status"
      aria-label={`Status: ${content}`}
    >
      {content}
    </span>
  )
})
