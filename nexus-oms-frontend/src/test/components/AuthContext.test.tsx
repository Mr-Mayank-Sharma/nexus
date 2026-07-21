import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from '../../context/AuthContext'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from '../../context/ThemeContext'
import { ToastProvider } from '../../components/common/ToastProvider'

vi.mock('../../api/auth', () => ({
  login: vi.fn(),
  verifyMfa: vi.fn(),
  ssoLogin: vi.fn(),
  forgotPassword: vi.fn(),
  resetPassword: vi.fn(),
  register: vi.fn(),
  getTenants: vi.fn().mockResolvedValue({ success: true, data: [] }),
  getSsoProviders: vi.fn().mockResolvedValue({ success: true, data: [] }),
  getCurrentUser: vi.fn().mockResolvedValue({ success: false, error: 'Not authenticated' }),
  refreshToken: vi.fn(),
}))

vi.mock('../../api/rbac', () => ({
  getMyPermissions: vi.fn().mockResolvedValue({ data: { data: [] } }),
  getPermissionsByRole: vi.fn().mockResolvedValue({ data: { data: [] } }),
}))

function TestConsumer() {
  const auth = useAuth()
  return (
    <div>
      <span data-testid="is-authenticated">{String(auth.isAuthenticated)}</span>
      <span data-testid="is-loading">{String(auth.isLoading)}</span>
      <span data-testid="user-role">{auth.user?.role ?? 'none'}</span>
      <span data-testid="has-orders-perm">{String(auth.hasPermission('orders', 'view'))}</span>
    </div>
  )
}

let realStore: Record<string, string>

function renderWithAuth(user?: Record<string, unknown>) {
  realStore = {}
  if (user) {
    realStore['nexus_token'] = 'test-token'
    realStore['nexus_user'] = JSON.stringify(user)
  }
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <AuthProvider>
            <ToastProvider>
              <TestConsumer />
            </ToastProvider>
          </AuthProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

const noopStorage = { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn(), clear: vi.fn(), get length() { return 0 }, key: vi.fn() }

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    realStore = {}
    Object.defineProperty(window, 'localStorage', {
      value: {
        getItem: vi.fn((key: string) => realStore[key] ?? null),
        setItem: vi.fn((key: string, value: string) => { realStore[key] = value }),
        removeItem: vi.fn((key: string) => { delete realStore[key] }),
        clear: vi.fn(() => { realStore = {} }),
        get length() { return Object.keys(realStore).length },
        key: vi.fn((i: number) => Object.keys(realStore)[i] ?? null),
      },
      writable: true,
      configurable: true,
    })
  })

  afterEach(() => {
    Object.defineProperty(window, 'localStorage', { value: noopStorage, writable: true, configurable: true })
  })

  it('provides unauthenticated state by default', async () => {
    renderWithAuth()
    await waitFor(() => {
      expect(screen.getByTestId('is-loading')).toHaveTextContent('false')
    })
    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    expect(screen.getByTestId('user-role')).toHaveTextContent('none')
  })

  it('restores user from localStorage', async () => {
    renderWithAuth({ id: 'u1', username: 'admin', role: 'ADMIN', permissions: ['orders:view'] })
    await waitFor(() => {
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('true')
    })
    expect(screen.getByTestId('user-role')).toHaveTextContent('ADMIN')
  })

  it('hasPermission returns true for ADMIN role regardless of permissions', async () => {
    renderWithAuth({ id: 'u1', username: 'admin', role: 'ADMIN', permissions: [] })
    await waitFor(() => {
      expect(screen.getByTestId('has-orders-perm')).toHaveTextContent('true')
    })
  })

  it('hasPermission returns true when user has exact permission', async () => {
    renderWithAuth({ id: 'u1', username: 'user', role: 'VIEWER', permissions: ['orders:view'] })
    await waitFor(() => {
      expect(screen.getByTestId('has-orders-perm')).toHaveTextContent('true')
    })
  })

  it('hasPermission returns false when user lacks permission', async () => {
    renderWithAuth({ id: 'u1', username: 'user', role: 'VIEWER', permissions: ['inventory:view'] })
    await waitFor(() => {
      expect(screen.getByTestId('is-loading')).toHaveTextContent('false')
    })
    expect(screen.getByTestId('has-orders-perm')).toHaveTextContent('false')
  })

  it('throws when useAuth is used outside AuthProvider', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => {
      render(<TestConsumer />)
    }).toThrow('useAuth must be used within an AuthProvider')
    consoleError.mockRestore()
  })
})
