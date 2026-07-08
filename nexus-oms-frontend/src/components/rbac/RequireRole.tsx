import type { ReactNode } from 'react'
import { useAuth } from '../../context/AuthContext'
import type { UserRole } from '../../types'

interface RequireRoleProps {
  roles: UserRole | UserRole[]
  fallback?: ReactNode
  children: ReactNode
}

export default function RequireRole({ roles, fallback = null, children }: RequireRoleProps) {
  const { hasRole } = useAuth()
  const roleList = Array.isArray(roles) ? roles : [roles]
  if (hasRole(...roleList)) return <>{children}</>
  return <>{fallback}</>
}
