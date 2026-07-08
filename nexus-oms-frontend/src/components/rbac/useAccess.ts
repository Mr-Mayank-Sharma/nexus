import { useAuth } from '../../context/AuthContext'
import type { UserRole } from '../../types'

export function usePermission(resource: string, action?: string): boolean {
  const { hasPermission } = useAuth()
  return hasPermission(resource, action)
}

export function useRoleAccess(...roles: UserRole[]): boolean {
  const { hasRole } = useAuth()
  return hasRole(...roles)
}

export function useRoleLevel(): number {
  const { roleLevel } = useAuth()
  return roleLevel
}
