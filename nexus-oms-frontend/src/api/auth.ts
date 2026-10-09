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

/** Swaps the one-time `sso_code` from the SSO callback redirect for tokens. */
export async function exchangeSsoCode(code: string): Promise<ApiResponse<AuthResponse>> {
  try {
    const { data } = await client.post('/auth/sso/exchange', { code })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'SSO sign-in failed'
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

export async function register(data: { username: string; email: string; password: string; tenantId?: string }): Promise<ApiResponse<AuthResponse>> {
  try {
    const { data: res } = await client.post('/auth/register', data)
    return res
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Registration failed'
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

export async function refreshToken(token: string): Promise<ApiResponse<{ token: string }>> {
  try {
    const { data } = await client.post('/auth/refresh', { refreshToken: token })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Token refresh failed'
    return { success: false, error: msg } as any
  }
}

// ---------------------------------------------------------------- MFA (authenticator app)

export interface MfaStatus { enabled: boolean; requiredByTenant: boolean; recoveryCodesRemaining: number }
export interface MfaEnrollment { secret: string; otpauthUri: string }

async function authCall<T>(method: 'get' | 'post', url: string, body?: unknown): Promise<T> {
  try {
    const { data } = method === 'get' ? await client.get(url) : await client.post(url, body ?? {})
    if (data && data.success === false) throw new Error(data.message || 'Request failed')
    return data.data as T
  } catch (err: any) {
    throw new Error(err?.response?.data?.message || err?.message || 'Request failed')
  }
}

export const getMfaStatus = () => authCall<MfaStatus>('get', '/auth/mfa/status')
export const beginMfaEnrollment = () => authCall<MfaEnrollment>('post', '/auth/mfa/enroll')
export const activateMfa = (code: string) => authCall<string[]>('post', '/auth/mfa/activate', { code })
export const disableMfa = (code: string) => authCall<null>('post', '/auth/mfa/disable', { code })
