import type { ReactNode } from 'react'
import { useAuth } from '../../context/AuthContext'

interface PermissionGateProps {
  resource: string
  action?: string
  fallback?: ReactNode
  children: ReactNode
}

export default function PermissionGate({ resource, action, fallback = null, children }: PermissionGateProps) {
  const { hasPermission } = useAuth()
  if (hasPermission(resource, action)) return <>{children}</>
  return <>{fallback}</>
}
