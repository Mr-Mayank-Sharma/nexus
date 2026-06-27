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
  const { data } = await client.post('/auth/login', credentials)
  return data
}

export async function verifyMfa(request: MfaVerificationRequest): Promise<ApiResponse<AuthResponse>> {
  const { data } = await client.post('/auth/mfa/verify', request)
  return data
}

export async function ssoLogin(request: SsoLoginRequest): Promise<ApiResponse<AuthResponse>> {
  const { data } = await client.post(`/auth/sso/${request.provider}`, request)
  return data
}

export async function forgotPassword(request: ForgotPasswordRequest): Promise<ApiResponse<null>> {
  const { data } = await client.post('/auth/forgot-password', request)
  return data
}

export async function resetPassword(request: ResetPasswordRequest): Promise<ApiResponse<null>> {
  const { data } = await client.post('/auth/reset-password', request)
  return data
}

export async function getTenants(): Promise<ApiResponse<TenantInfo[]>> {
  const { data } = await client.get('/auth/tenants')
  return data
}

export async function getSsoProviders(): Promise<ApiResponse<string[]>> {
  const { data } = await client.get('/auth/sso/providers')
  return data
}

export async function getCurrentUser(): Promise<ApiResponse<User>> {
  const { data } = await client.get('/auth/me')
  return data
}

export async function refreshToken(token: string): Promise<ApiResponse<{ token: string }>> {
  const { data } = await client.post('/auth/refresh', { refreshToken: token })
  return data
}
