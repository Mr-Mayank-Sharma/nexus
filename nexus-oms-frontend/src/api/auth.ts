import client from './client'
import {
  ApiResponse,
  AuthResponse,
  LoginRequest,
  MfaVerificationRequest,
  SsoLoginRequest,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  TenantInfo,
  User,
} from '../types'

export async function login(credentials: LoginRequest): Promise<ApiResponse<AuthResponse>> {
  try {
    const { data } = await client.post('/auth/login', credentials)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Login failed'
    return { success: false, error: msg } as any
  }
}

export async function verifyMfa(request: MfaVerificationRequest): Promise<ApiResponse<AuthResponse>> {
  try {
    const { data } = await client.post('/auth/mfa/verify', request)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'MFA verification failed'
    return { success: false, error: msg } as any
  }
}

export async function ssoLogin(request: SsoLoginRequest): Promise<ApiResponse<AuthResponse>> {
  try {
    const { data } = await client.post(`/auth/sso/${request.provider}`, request)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'SSO login failed'
    return { success: false, error: msg } as any
  }
}

export async function forgotPassword(request: ForgotPasswordRequest): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.post('/auth/forgot-password', request)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Forgot password request failed'
    return { success: false, error: msg } as any
  }
}

export async function resetPassword(request: ResetPasswordRequest): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.post('/auth/reset-password', request)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Password reset failed'
    return { success: false, error: msg } as any
  }
}

export async function getTenants(): Promise<ApiResponse<TenantInfo[]>> {
  try {
    const { data } = await client.get('/auth/tenants')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get tenants'
    return { success: false, error: msg } as any
  }
}

export async function getSsoProviders(): Promise<ApiResponse<string[]>> {
  try {
    const { data } = await client.get('/auth/sso/providers')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get SSO providers'
    return { success: false, error: msg } as any
  }
}

export async function getCurrentUser(): Promise<ApiResponse<User>> {
  try {
    const { data } = await client.get('/auth/me')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get current user'
    return { success: false, error: msg } as any
  }
}

export async function refreshToken(token: string): Promise<ApiResponse<{ token: string }>> {
  try {
    const { data } = await client.post('/auth/refresh', { refreshToken: token })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Token refresh failed'
    return { success: false, error: msg } as any
  }
}
