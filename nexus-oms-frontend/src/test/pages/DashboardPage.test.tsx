import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from '../../pages/DashboardPage'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../../context/AuthContext'
import { ThemeProvider } from '../../context/ThemeContext'

vi.mock('../../api/analytics', () => ({
  getDashboardKpis: vi.fn().mockResolvedValue({
    data: {
      totalOrders: 1500,
      pending: 45,
      shipped: 320,
      delivered: 1100,
      cancelled: 35,
      revenue: 125000,
      activeCustomers: 890,
    },
  }),
  getOrderVelocity: vi.fn().mockResolvedValue({
    data: {
      data: Array.from({ length: 24 }, (_, i) => ({ hour: `${i}:00`, orders: 50, fulfilled: 40 })),
    },
  }),
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
            {ui}
          </AuthProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('DashboardPage', () => {
  it('renders dashboard title', async () => {
    renderWithProviders(<DashboardPage />)
    expect(await screen.findByText('Order Velocity')).toBeInTheDocument()
  })
})
