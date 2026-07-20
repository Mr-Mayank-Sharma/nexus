import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../../context/AuthContext'
import { ThemeProvider } from '../../context/ThemeContext'
import { ToastProvider } from '../../components/common/ToastProvider'
import CreateOrderPage from '../../pages/CreateOrderPage'

vi.mock('../../api/newBackend', () => ({
  createOrder: vi.fn().mockResolvedValue({ id: 'ORD-001', status: 'CREATED' }),
  fetchCustomers: vi.fn().mockResolvedValue({
    customers: [{ id: 'CUST-001', name: 'Test Customer', email: 'test@example.com' }],
  }),
  fetchProducts: vi.fn().mockResolvedValue({
    products: [{ id: 'PROD-001', name: 'Test Product', sku: 'TEST-001', price: 99.99 }],
  }),
}))

vi.mock('../../api/auth', () => ({
  login: vi.fn(),
  getTenants: vi.fn().mockResolvedValue({ success: true, data: [] }),
  getCurrentUser: vi.fn().mockResolvedValue({
    success: true,
    data: { id: 'u1', username: 'admin', role: 'ADMIN', fullName: 'Admin User' },
  }),
  forgotPassword: vi.fn(),
  verifyMfa: vi.fn(),
}))

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  // Set up auth state
  localStorage.setItem('nexus_token', 'test-token')
  localStorage.setItem('nexus_user', JSON.stringify({
    id: 'u1', username: 'admin', role: 'ADMIN', fullName: 'Admin User',
    email: 'admin@test.com', permissions: ['*'],
  }))

  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <AuthProvider>
            <ToastProvider>
              {ui}
            </ToastProvider>
          </AuthProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('Order Creation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('nexus_token', 'test-token')
    localStorage.setItem('nexus_user', JSON.stringify({
      id: 'u1', username: 'admin', role: 'ADMIN', fullName: 'Admin User',
      email: 'admin@test.com', permissions: ['*'],
    }))
  })

  it('renders order creation page', async () => {
    renderWithProviders(<CreateOrderPage />)
    await waitFor(() => {
      expect(screen.getByText(/create order/i)).toBeInTheDocument()
    }, { timeout: 5000 })
  })

  it('loads customer list', async () => {
    renderWithProviders(<CreateOrderPage />)
    await waitFor(() => {
      expect(screen.getByText('Test Customer')).toBeInTheDocument()
    }, { timeout: 5000 })
  })
})
