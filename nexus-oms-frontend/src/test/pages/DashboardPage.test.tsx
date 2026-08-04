import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from '../../pages/DashboardPage'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../../context/AuthContext'
import { ThemeProvider } from '../../context/ThemeContext'
import { ToastProvider } from '../../components/common/ToastProvider'

vi.mock('../../api/analytics', () => ({
  getDashboardKpis: vi.fn().mockResolvedValue({
    data: {
      ordersToday: 1284,
      onTimeDelivery: '97.2%',
      activeExceptions: 23,
      avgShipTime: '4.2h',
      revenueToday: 127450,
      activePickers: 18,
    },
  }),
  getOrderVelocity: vi.fn().mockResolvedValue({
    data: Array.from({ length: 24 }, (_, i) => ({ hour: `${i}:00`, orders: 50, fulfilled: 40 })),
  }),
  getAlerts: vi.fn().mockResolvedValue({ data: [] }),
  getOrderStatusDistribution: vi.fn().mockResolvedValue({
    data: [
      { name: 'Pending', value: 45 },
      { name: 'Processing', value: 120 },
      { name: 'Shipped', value: 320 },
      { name: 'Exceptions', value: 23 },
    ],
  }),
  getTaskQueueSummary: vi.fn().mockResolvedValue({ data: { holdTasks: { substituteItems: 5, badAddress: 2, fraudRisk: 1, onHold: 8 }, unbrokered: { brokeringQueue: 12, unallocated: 30 } } }),
  getWarehousesSummary: vi.fn().mockResolvedValue({ data: [] }),
  getActivity: vi.fn().mockResolvedValue({ data: [] }),
}))

vi.mock('../../api/aiPlatform', () => ({
  predict: vi.fn().mockResolvedValue({ data: { predictedOrders: 182, confidence: 0.87, explanation: 'Based on historical trends' } }),
}))

vi.mock('../../api/picking', () => ({
  getPicklists: vi.fn().mockResolvedValue({ data: [] }),
}))

vi.mock('../../api/orders', () => ({
  getOrders: vi.fn().mockResolvedValue({ data: [] }),
}))

vi.mock('../../api/packing', () => ({
  getPackages: vi.fn().mockResolvedValue({ data: [] }),
}))

vi.mock('../../api/promotions', () => ({
  default: {
    getPromotions: vi.fn().mockResolvedValue({ data: [] }),
  },
}))

vi.mock('../../api/endlessAisle', () => ({
  default: {
    getEndlessAisleOrders: vi.fn().mockResolvedValue({ data: [] }),
  },
}))

vi.mock('../../components/rbac/PermissionGate', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

vi.mock('../../api/rbac', () => ({
  getRoles: vi.fn().mockResolvedValue({ data: [] }),
  getPermissions: vi.fn().mockResolvedValue({ data: [] }),
}))

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
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

describe('DashboardPage', () => {
  it('renders dashboard page', async () => {
    renderWithProviders(<DashboardPage />)
    const container = document.querySelector('.space-y-6')
    expect(container).toBeInTheDocument()
  })
})
