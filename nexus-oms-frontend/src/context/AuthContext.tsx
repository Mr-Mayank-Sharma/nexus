import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { User, LoginRequest, MfaVerificationRequest, SsoLoginRequest, UserRole, ROLE_HIERARCHY } from '../types'
import * as authApi from '../api/auth'
import * as rbacApi from '../api/rbac'

interface AuthContextType {
  user: User | null
  token: string | null
  isAuthenticated: boolean
  isLoading: boolean
  mfaRequired: boolean
  mfaToken: string | null
  login: (credentials: LoginRequest) => Promise<void>
  verifyMfa: (code: string) => Promise<void>
  ssoLogin: (provider: string, idToken: string, tenantId?: string) => Promise<void>
  logout: () => void
  clearError: () => void
  error: string | null
  hasPermission: (resource: string, action?: string) => boolean
  hasRole: (...roles: UserRole[]) => boolean
  hasAnyRole: (...roles: UserRole[]) => boolean
  roleLevel: number
  switchRole: (role: UserRole) => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [mfaRequired, setMfaRequired] = useState(false)
  const [mfaToken, setMfaToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const storedToken = localStorage.getItem('nexus_token')
    const storedUser = localStorage.getItem('nexus_user')
    const storedPermissions = localStorage.getItem('nexus_permissions')
    const storedSecurityGroups = localStorage.getItem('nexus_security_groups')
    if (storedToken && storedUser) {
      setToken(storedToken)
      try {
        const parsed = JSON.parse(storedUser)
        if (storedPermissions) parsed.permissions = JSON.parse(storedPermissions)
        if (storedSecurityGroups) parsed.securityGroups = JSON.parse(storedSecurityGroups)
        setUser(parsed)
      } catch {
        localStorage.removeItem('nexus_token')
        localStorage.removeItem('nexus_user')
        localStorage.removeItem('nexus_permissions')
        localStorage.removeItem('nexus_security_groups')
      }
    }
    setIsLoading(false)
  }, [])

  const login = useCallback(async (credentials: LoginRequest) => {
    setError(null)
    const response = await authApi.login(credentials)
    const authData = response.data

    if (authData.mfaRequired) {
      setMfaRequired(true)
      setMfaToken(authData.mfaToken ?? null)
      return
    }

    storeAuth(authData)
  }, [])

  const verifyMfa = useCallback(async (code: string) => {
    if (!mfaToken) throw new Error('No MFA session')
    setError(null)
    const response = await authApi.verifyMfa({ mfaToken, totpCode: code })
    storeAuth(response.data)
    setMfaRequired(false)
    setMfaToken(null)
  }, [mfaToken])

  const ssoLogin = useCallback(async (provider: string, idToken: string, tenantId?: string) => {
    setError(null)
    const response = await authApi.ssoLogin({ provider, idToken, tenantId })
    storeAuth(response.data)
  }, [])

  const storeAuth = useCallback((authData: Record<string, unknown>) => {
    const accessToken = authData.accessToken as string
    const username = authData.username as string
    const role = authData.role as string
    const tenantId = authData.tenantId as string
    const tenantName = authData.tenantName as string
    const email = authData.email as string
    const fullName = authData.fullName as string
    const permissions = (authData.permissions as string[]) || []
    const securityGroups = (authData.securityGroups as string[]) || []

    const validRole = ['ADMIN','CEO','OPS_MANAGER','WAREHOUSE_MANAGER','PICKER','PACKER','LOADER','STORE_MANAGER','BOPIS_OWNER','CUSTOMER_SUPPORT','PROCUREMENT_MANAGER','FINANCE','LOGISTICS_MANAGER','VIEWER'].includes(role)
      ? role as UserRole
      : 'VIEWER' as UserRole

    const user: User = {
      id: '',
      username,
      email: email || '',
      fullName: fullName || username,
      role: validRole,
      permissions,
      securityGroups,
    }
    setUser(user)
    setToken(accessToken)
    localStorage.setItem('nexus_token', accessToken)
    localStorage.setItem('nexus_user', JSON.stringify(user))
    localStorage.setItem('nexus_permissions', JSON.stringify(permissions))
    localStorage.setItem('nexus_security_groups', JSON.stringify(securityGroups))
    if (tenantId) localStorage.setItem('nexus_tenant_id', tenantId)
    if (tenantName) localStorage.setItem('nexus_tenant_name', tenantName)
  }, [])

  const logout = useCallback(() => {
    setUser(null)
    setToken(null)
    setMfaRequired(false)
    setMfaToken(null)
    setError(null)
    localStorage.removeItem('nexus_token')
    localStorage.removeItem('nexus_user')
    localStorage.removeItem('nexus_permissions')
    localStorage.removeItem('nexus_security_groups')
    localStorage.removeItem('nexus_tenant_id')
    localStorage.removeItem('nexus_tenant_name')
    window.location.href = '/login'
  }, [])

  const clearError = useCallback(() => setError(null), [])

  const hasPermission = useCallback((resource: string, action?: string): boolean => {
    if (!user) return false
    if (user.role === 'ADMIN') return true
    if (!action) return user.permissions.includes(resource) || user.permissions.includes(`${resource}:ALL`)
    return user.permissions.includes(`${resource}:${action}`) || user.permissions.includes(`${resource}:ALL`)
  }, [user])

  const hasRole = useCallback((...roles: UserRole[]): boolean => {
    if (!user) return false
    return roles.includes(user.role)
  }, [user])

  const hasAnyRole = useCallback((...roles: UserRole[]): boolean => {
    if (!user) return false
    return roles.includes(user.role)
  }, [user])

  const roleLevel = user ? (ROLE_HIERARCHY[user.role] || 0) : 0

  const switchRole = useCallback(async (newRole: UserRole) => {
    if (!user || user.role !== 'ADMIN') return
    try {
      const permRes = await rbacApi.getPermissionsByRole(newRole)
      const rolePerms = permRes.data?.data ?? []
      const permStrings: string[] = rolePerms
        .filter((rp: { canView?: boolean; canCreate?: boolean; canEdit?: boolean; canDelete?: boolean }) => rp.canView || rp.canCreate || rp.canEdit || rp.canDelete)
        .flatMap((rp: { permissionGroup: string; canView?: boolean; canCreate?: boolean; canEdit?: boolean; canDelete?: boolean }) => {
          const perms: string[] = []
          if (rp.canView) perms.push(`${rp.permissionGroup}:view`)
          if (rp.canCreate) perms.push(`${rp.permissionGroup}:create`)
          if (rp.canEdit) perms.push(`${rp.permissionGroup}:edit`)
          if (rp.canDelete) perms.push(`${rp.permissionGroup}:delete`)
          return perms
        })
      const updated = { ...user, role: newRole, permissions: permStrings }
      setUser(updated)
      localStorage.setItem('nexus_user', JSON.stringify(updated))
      localStorage.setItem('nexus_permissions', JSON.stringify(permStrings))
    } catch {
      const updated = { ...user, role: newRole }
      setUser(updated)
      localStorage.setItem('nexus_user', JSON.stringify(updated))
    }
  }, [user])

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user && !!token,
        isLoading,
        mfaRequired,
        mfaToken,
        login,
        verifyMfa,
        ssoLogin,
        logout,
        clearError,
        error,
        hasPermission,
        hasRole,
        hasAnyRole,
        roleLevel,
        switchRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
