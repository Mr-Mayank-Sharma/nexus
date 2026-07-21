import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../../context/AuthContext'
import { ThemeProvider } from '../../context/ThemeContext'
import { ToastProvider } from '../../components/common/ToastProvider'
import OrderDetailPage from '../../pages/OrderDetailPage'

const mockGetOrderById = vi.fn()
const mockConfirmOrder = vi.fn()
const mockAllocateOrder = vi.fn()
const mockShipOrder = vi.fn()
const mockCancelOrder = vi.fn()

vi.mock('../../api/orders', () => ({
  getOrderById: (...args: unknown[]) => mockGetOrderById(...args),
  confirmOrder: (...args: unknown[]) => mockConfirmOrder(...args),
  allocateOrder: (...args: unknown[]) => mockAllocateOrder(...args),
  shipOrder: (...args: unknown[]) => mockShipOrder(...args),
  cancelOrder: (...args: unknown[]) => mockCancelOrder(...args),
  getOrders: vi.fn().mockResolvedValue({ data: [] }),
  modifyOrder: vi.fn().mockResolvedValue({}),
  splitOrder: vi.fn().mockResolvedValue({}),
  mergeOrders: vi.fn().mockResolvedValue({}),
}))

vi.mock('../../api/aiPlatform', () => ({
  predict: vi.fn().mockResolvedValue({ data: null }),
}))

vi.mock('../../api/aiOrders', () => ({
  getAiSuggestions: vi.fn().mockResolvedValue({ data: [] }),
  getAiHistory: vi.fn().mockResolvedValue({ data: [] }),
  executeAiAction: vi.fn().mockResolvedValue({ data: { status: 'SUCCESS', label: 'Done' } }),
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



const mockOrder = {
  id: 'ord-123',
  orderNumber: 'ORD-001',
  status: 'PENDING',
  channel: 'WEB',
  customerName: 'Jane Doe',
  customerEmail: 'jane@example.com',
  items: [
    { id: 'i1', sku: 'SKU-1', productName: 'Widget A', quantity: 2, unitPrice: 25.0, totalPrice: 50.0 },
    { id: 'i2', sku: 'SKU-2', productName: 'Widget B', quantity: 1, unitPrice: 100.0, totalPrice: 100.0 },
  ],
  total: 150.0,
  subtotal: 150.0,
  shippingCost: 0,
  tax: 0,
  currency: 'USD',
  shippingAddress: { street: '123 Main St', city: 'Springfield', state: 'IL', zip: '62701', country: 'US' },
  billingAddress: { street: '', city: '', state: '', zip: '', country: '' },
  fulfillmentType: 'STANDARD',
  allocationNodeId: '',
  carrier: '',
  trackingNumber: '',
  promisedDeliveryDate: '',
  estimatedShipDate: '',
  shippedDate: '',
  deliveredDate: '',
  createdAt: '2026-01-15T10:00:00Z',
  updatedAt: '2026-01-15T10:00:00Z',
  paymentStatus: 'PAID',
  paymentReference: 'PAY-REF-001',
}

function renderPage(orderId = 'ord-123') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  localStorage.setItem('nexus_token', 'test-token')
  localStorage.setItem('nexus_user', JSON.stringify({
    id: 'u1', username: 'admin', role: 'ADMIN', fullName: 'Admin User',
    email: 'admin@test.com', permissions: ['*'],
  }))
  return render(
    <MemoryRouter initialEntries={[`/orders/${orderId}`]}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <AuthProvider>
            <ToastProvider>
              <Routes>
                <Route path="/orders/:id" element={<OrderDetailPage />} />
              </Routes>
            </ToastProvider>
          </AuthProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('OrderDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetOrderById.mockResolvedValue({ data: mockOrder })
    mockConfirmOrder.mockResolvedValue({})
    mockAllocateOrder.mockResolvedValue({})
    mockShipOrder.mockResolvedValue({})
    mockCancelOrder.mockResolvedValue({})
  })

  it('shows loading spinner initially', () => {
    mockGetOrderById.mockReturnValue(new Promise(() => {})) // never resolves
    renderPage()
    expect(document.querySelector('.animate-spin')).toBeInTheDocument()
  })

  it('renders order number and status badge', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getAllByText('ORD-001').length).toBeGreaterThanOrEqual(1)
    })
    expect(screen.getByText('PENDING')).toBeInTheDocument()
  })

  it('displays customer name and email', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Jane Doe')).toBeInTheDocument()
    })
    expect(screen.getByText('jane@example.com')).toBeInTheDocument()
  })

  it('displays order items', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Widget A')).toBeInTheDocument()
    })
    expect(screen.getByText('Widget B')).toBeInTheDocument()
    expect(screen.getByText('SKU-1')).toBeInTheDocument()
  })

  it('shows order total', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getAllByText('ORD-001').length).toBeGreaterThanOrEqual(1)
    })
    expect(screen.getAllByText(/USD 150\.00/).length).toBeGreaterThanOrEqual(1)
  })

  it('shows Confirm Order button for PENDING status', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /confirm order/i })).toBeInTheDocument()
    })
  })

  it('calls confirmOrder on Confirm button click', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /confirm order/i })).toBeInTheDocument()
    })
    fireEvent.click(screen.getByRole('button', { name: /confirm order/i }))
    await waitFor(() => {
      expect(mockConfirmOrder).toHaveBeenCalledWith('ord-123')
    })
  })

  it('shows order not found when API returns error', async () => {
    mockGetOrderById.mockRejectedValue(new Error('Not found'))
    renderPage()
    await waitFor(() => {
      expect(screen.getByText(/order not found/i)).toBeInTheDocument()
    })
  })

  it('renders back to orders link', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Orders' })).toBeInTheDocument()
    })
  })

  it('shows shipping address', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('123 Main St')).toBeInTheDocument()
    })
    expect(screen.getByText(/Springfield/)).toBeInTheDocument()
  })

  it('shows payment info when payment status exists', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getAllByText('ORD-001').length).toBeGreaterThanOrEqual(1)
    })
    expect(screen.getByText('CAPTURED')).toBeInTheDocument()
  })

  it('shows Print Label and Invoice buttons', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText(/print label/i)).toBeInTheDocument()
    })
    expect(screen.getByText(/invoice/i)).toBeInTheDocument()
  })

  it('shows order timeline section', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Order Timeline')).toBeInTheDocument()
    })
  })

  it('shows order items count header', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText(/order items \(2\)/i)).toBeInTheDocument()
    })
  })
})
