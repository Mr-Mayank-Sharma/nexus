import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import PermissionGate from '../../components/rbac/PermissionGate'
import { useAuth } from '../../context/AuthContext'

vi.mock('../../context/AuthContext')

const mockUseAuth = vi.mocked(useAuth)

function renderGate(
  ui: React.ReactElement,
  authOverrides: Partial<ReturnType<typeof useAuth>> = {},
) {
  mockUseAuth.mockReturnValue({
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    mfaRequired: false,
    mfaToken: null,
    login: vi.fn(),
    verifyMfa: vi.fn(),
    ssoLogin: vi.fn(),
    logout: vi.fn(),
    clearError: vi.fn(),
    error: null,
    hasPermission: vi.fn().mockReturnValue(false),
    hasRole: vi.fn().mockReturnValue(false),
    hasAnyRole: vi.fn().mockReturnValue(false),
    roleLevel: 0,
    switchRole: vi.fn(),
    ...authOverrides,
  } as ReturnType<typeof useAuth>)
  return render(ui)
}

describe('PermissionGate', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders children when user has ADMIN role', () => {
    renderGate(
      <PermissionGate resource="orders" action="view">
        <div>Protected content</div>
      </PermissionGate>,
      {
        user: { id: 'u1', username: 'admin', role: 'ADMIN', permissions: [], fullName: 'Admin', email: '', securityGroups: [] },
        isAuthenticated: true,
        hasPermission: vi.fn().mockReturnValue(true),
      },
    )
    expect(screen.getByText('Protected content')).toBeInTheDocument()
  })

  it('renders children when user has matching permission', () => {
    renderGate(
      <PermissionGate resource="orders" action="view">
        <div>Protected content</div>
      </PermissionGate>,
      {
        user: { id: 'u1', username: 'user1', role: 'VIEWER', permissions: ['orders:view'], fullName: 'User', email: '', securityGroups: [] },
        isAuthenticated: true,
        hasPermission: vi.fn().mockReturnValue(true),
      },
    )
    expect(screen.getByText('Protected content')).toBeInTheDocument()
  })

  it('renders children when user has ALL permission for resource', () => {
    renderGate(
      <PermissionGate resource="orders" action="delete">
        <div>Protected content</div>
      </PermissionGate>,
      {
        user: { id: 'u1', username: 'user1', role: 'VIEWER', permissions: ['orders:ALL'], fullName: 'User', email: '', securityGroups: [] },
        isAuthenticated: true,
        hasPermission: vi.fn().mockReturnValue(true),
      },
    )
    expect(screen.getByText('Protected content')).toBeInTheDocument()
  })

  it('renders fallback when user lacks permission', () => {
    renderGate(
      <PermissionGate resource="orders" action="delete" fallback={<div>Access denied</div>}>
        <div>Protected content</div>
      </PermissionGate>,
      {
        user: { id: 'u1', username: 'user1', role: 'VIEWER', permissions: ['orders:view'], fullName: 'User', email: '', securityGroups: [] },
        isAuthenticated: true,
        hasPermission: vi.fn().mockReturnValue(false),
      },
    )
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument()
    expect(screen.getByText('Access denied')).toBeInTheDocument()
  })

  it('renders null (default fallback) when user lacks permission', () => {
    const { container } = renderGate(
      <PermissionGate resource="orders" action="delete">
        <div>Protected content</div>
      </PermissionGate>,
      {
        user: { id: 'u1', username: 'user1', role: 'VIEWER', permissions: [], fullName: 'User', email: '', securityGroups: [] },
        isAuthenticated: true,
        hasPermission: vi.fn().mockReturnValue(false),
      },
    )
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument()
    expect(container.textContent).toBe('')
  })

  it('renders children when no action specified and resource matches', () => {
    renderGate(
      <PermissionGate resource="orders">
        <div>Protected content</div>
      </PermissionGate>,
      {
        user: { id: 'u1', username: 'user1', role: 'VIEWER', permissions: ['orders'], fullName: 'User', email: '', securityGroups: [] },
        isAuthenticated: true,
        hasPermission: vi.fn().mockReturnValue(true),
      },
    )
    expect(screen.getByText('Protected content')).toBeInTheDocument()
  })

  it('renders fallback when no user is logged in', () => {
    const { container } = renderGate(
      <PermissionGate resource="orders" action="view">
        <div>Protected content</div>
      </PermissionGate>,
    )
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument()
    expect(container.textContent).toBe('')
  })
})
