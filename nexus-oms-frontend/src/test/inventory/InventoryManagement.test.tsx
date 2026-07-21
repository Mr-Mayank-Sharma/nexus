import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../../context/AuthContext'
import { ThemeProvider } from '../../context/ThemeContext'
import { ToastProvider } from '../../components/common/ToastProvider'
import InventoryPage from '../../pages/InventoryPage'

vi.mock('../../api/inventory', () => ({
  getInventory: vi.fn().mockResolvedValue({
    success: true,
    data: [
      {
        sku: 'TEST-001',
        productName: 'Test Product',
        category: 'Electronics',
        nodeId: 'WH-001',
        nodeName: 'Main Warehouse',
        quantityOnHand: 100,
        quantityAllocated: 10,
        quantityReserved: 5,
        quantityInTransit: 0,
        quantityDamaged: 0,
        safetyStock: 20,
        reorderPoint: 15,
        unitCost: 50.00,
        updatedAt: '2024-01-01T00:00:00Z',
      },
    ],
  }),
  exportInventory: vi.fn().mockResolvedValue({ success: true }),
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

describe('Inventory Management', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('nexus_token', 'test-token')
    localStorage.setItem('nexus_user', JSON.stringify({
      id: 'u1', username: 'admin', role: 'ADMIN', fullName: 'Admin User',
      email: 'admin@test.com', permissions: ['*'],
    }))
  })

  it('renders inventory page', async () => {
    const { container } = renderWithProviders(<InventoryPage />)
    await waitFor(() => {
      expect(container.querySelector('.space-y-4')).toBeInTheDocument()
    }, { timeout: 5000 })
  })

  it('displays inventory data', async () => {
    renderWithProviders(<InventoryPage />)
    await waitFor(() => {
      expect(screen.getByText('TEST-001')).toBeInTheDocument()
    }, { timeout: 5000 })
  })

  it('shows search input', async () => {
    renderWithProviders(<InventoryPage />)
    await waitFor(() => {
      expect(screen.getByPlaceholderText(/search/i)).toBeInTheDocument()
    }, { timeout: 5000 })
  })
})
