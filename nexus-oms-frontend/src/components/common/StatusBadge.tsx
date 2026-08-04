import EnterpriseStatusBadge from '../enterprise/EnterpriseStatusBadge'

interface StatusBadgeProps {
  status: string
  size?: 'sm' | 'md' | 'lg'
}

export default function StatusBadge({ status, size = 'md' }: StatusBadgeProps) {
  return <EnterpriseStatusBadge status={status} label={status.replace(/_/g, ' ')} size={size} />
}
