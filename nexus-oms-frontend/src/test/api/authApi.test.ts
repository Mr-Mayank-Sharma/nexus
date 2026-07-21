import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  login,
  verifyMfa,
  ssoLogin,
  forgotPassword,
  resetPassword,
  register,
  getTenants,
  getSsoProviders,
  getCurrentUser,
  refreshToken,
} from '../../api/auth'

const mockGet = vi.fn()
const mockPost = vi.fn()

vi.mock('../../api/client', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
}))

describe('Auth API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: { success: true } })
    mockPost.mockResolvedValue({ data: { success: true } })
  })

  describe('login', () => {
    it('should POST /auth/login with credentials', async () => {
      const credentials = { email: 'user@test.com', password: 'pass123' }
      const result = await login(credentials)
      expect(mockPost).toHaveBeenCalledWith('/auth/login', credentials)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Invalid credentials'))
      const result = await login({ email: 'bad@test.com', password: 'wrong' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Invalid credentials')
    })

    it('should use response data message on axios error', async () => {
      mockPost.mockRejectedValueOnce({ response: { data: { message: 'Account locked' } } })
      const result = await login({ email: 'locked@test.com', password: 'pass' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Account locked')
    })
  })

  describe('verifyMfa', () => {
    it('should POST /auth/mfa/verify with request', async () => {
      const request = { code: '123456', userId: 'U-001' }
      const result = await verifyMfa(request)
      expect(mockPost).toHaveBeenCalledWith('/auth/mfa/verify', request)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Invalid MFA code'))
      const result = await verifyMfa({ code: '000000', userId: 'U-001' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Invalid MFA code')
    })
  })

  describe('ssoLogin', () => {
    it('should POST /auth/sso/:provider with request', async () => {
      const request = { provider: 'google', token: 'sso-token-123' }
      const result = await ssoLogin(request)
      expect(mockPost).toHaveBeenCalledWith('/auth/sso/google', request)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('SSO failed'))
      const result = await ssoLogin({ provider: 'azure', token: 'tok' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('SSO failed')
    })
  })

  describe('forgotPassword', () => {
    it('should POST /auth/forgot-password with request', async () => {
      const request = { email: 'user@test.com' }
      const result = await forgotPassword(request)
      expect(mockPost).toHaveBeenCalledWith('/auth/forgot-password', request)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Email not found'))
      const result = await forgotPassword({ email: 'unknown@test.com' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Email not found')
    })
  })

  describe('resetPassword', () => {
    it('should POST /auth/reset-password with request', async () => {
      const request = { token: 'reset-token', newPassword: 'newPass123' }
      const result = await resetPassword(request)
      expect(mockPost).toHaveBeenCalledWith('/auth/reset-password', request)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Token expired'))
      const result = await resetPassword({ token: 'expired', newPassword: 'x' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Token expired')
    })
  })

  describe('register', () => {
    it('should POST /auth/register with data', async () => {
      const data = { username: 'newuser', email: 'new@test.com', password: 'Pass123!' }
      const result = await register(data)
      expect(mockPost).toHaveBeenCalledWith('/auth/register', data)
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Username taken'))
      const result = await register({ username: 'taken', email: 'x@test.com', password: 'p' })
      expect(result.success).toBe(false)
      expect(result.error).toBe('Username taken')
    })
  })

  describe('getTenants', () => {
    it('should GET /auth/tenants', async () => {
      const result = await getTenants()
      expect(mockGet).toHaveBeenCalledWith('/auth/tenants')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Unauthorized'))
      const result = await getTenants()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Unauthorized')
    })
  })

  describe('getSsoProviders', () => {
    it('should GET /auth/sso/providers', async () => {
      const result = await getSsoProviders()
      expect(mockGet).toHaveBeenCalledWith('/auth/sso/providers')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('SSO unavailable'))
      const result = await getSsoProviders()
      expect(result.success).toBe(false)
      expect(result.error).toBe('SSO unavailable')
    })
  })

  describe('getCurrentUser', () => {
    it('should GET /auth/me', async () => {
      const result = await getCurrentUser()
      expect(mockGet).toHaveBeenCalledWith('/auth/me')
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockGet.mockRejectedValueOnce(new Error('Session expired'))
      const result = await getCurrentUser()
      expect(result.success).toBe(false)
      expect(result.error).toBe('Session expired')
    })
  })

  describe('refreshToken', () => {
    it('should POST /auth/refresh with { refreshToken: token }', async () => {
      const result = await refreshToken('refresh-abc')
      expect(mockPost).toHaveBeenCalledWith('/auth/refresh', { refreshToken: 'refresh-abc' })
      expect(result.success).toBe(true)
    })

    it('should return error on failure', async () => {
      mockPost.mockRejectedValueOnce(new Error('Refresh token invalid'))
      const result = await refreshToken('bad-token')
      expect(result.success).toBe(false)
      expect(result.error).toBe('Refresh token invalid')
    })
  })
})
