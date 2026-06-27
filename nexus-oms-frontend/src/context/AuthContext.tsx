import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { User, LoginRequest, MfaVerificationRequest, SsoLoginRequest } from '../types'
import * as authApi from '../api/auth'

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
    if (storedToken && storedUser) {
      setToken(storedToken)
      try {
        setUser(JSON.parse(storedUser))
      } catch {
        localStorage.removeItem('nexus_token')
        localStorage.removeItem('nexus_user')
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

    const user: User = {
      id: '',
      username,
      email: email || '',
      fullName: fullName || username,
      role: role as User['role'],
      permissions: [],
    }
    setUser(user)
    setToken(accessToken)
    localStorage.setItem('nexus_token', accessToken)
    localStorage.setItem('nexus_user', JSON.stringify(user))
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
    localStorage.removeItem('nexus_tenant_id')
    localStorage.removeItem('nexus_tenant_name')
    window.location.href = '/login'
  }, [])

  const clearError = useCallback(() => setError(null), [])

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
