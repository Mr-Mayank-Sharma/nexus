import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'

const api = vi.hoisted(() => ({
  getPlatforms: vi.fn(), getConnections: vi.fn(), getJobs: vi.fn(), createConnection: vi.fn(),
  testConnection: vi.fn(), removeConnection: vi.fn(), runOperation: vi.fn(),
}))
const toasts = vi.hoisted(() => ({ addToast: vi.fn() }))
vi.mock('../features/integrations/hubApi', () => api)
vi.mock('../hooks/useToast', () => ({ useToast: () => toasts }))
vi.mock('../components/rbac/PermissionGate', () => ({ default: ({ children }: { children: React.ReactNode }) => <>{children}</> }))

import IntegrationHubPage from './IntegrationHubPage'

const platforms = [
  { name: 'Magento', platformType: 'MAGENTO', category: 'E-Commerce', supportedSyncTypes: [], previewSyncTypes: ['ORDER_IMPORT'], requiredSettings: ['base_url'] },
  { name: 'UPS', platformType: 'UPS', category: 'Shipping', supportedSyncTypes: [], previewSyncTypes: ['ORDER_IMPORT'], requiredSettings: [] },
]

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter><IntegrationHubPage /></MemoryRouter></QueryClientProvider>)
}

describe('IntegrationHubPage', () => {
  beforeEach(() => {
    Object.values(api).forEach(f => f.mockReset())
    toasts.addToast.mockReset()
    api.getPlatforms.mockResolvedValue(platforms)
    api.getConnections.mockResolvedValue([])
    api.getJobs.mockResolvedValue([])
  })

  it('says plainly that saving proves nothing and previews import nothing', async () => {
    renderPage()
    expect(await screen.findByText(/saving one proves nothing/i)).toBeInTheDocument()
    expect(screen.getByText(/Previews never import anything into Nexus/)).toBeInTheDocument()
  })

  it('a saved connection whose test failed is reported as failed, never as connected', async () => {
    api.createConnection.mockResolvedValue({ id: 'c1', saved: true, connectionVerified: false, success: false, message: '401 from the platform' })
    renderPage()
    fireEvent.change(await screen.findByLabelText('Platform'), { target: { value: 'MAGENTO' } })
    fireEvent.change(screen.getByLabelText('base url'), { target: { value: 'https://shop.example.test' } })
    fireEvent.click(screen.getByRole('button', { name: /Save and test/ }))

    await waitFor(() => expect(toasts.addToast).toHaveBeenCalled())
    const toast = toasts.addToast.mock.calls[0][0]
    expect(toast.type).toBe('error')
    expect(toast.title).toMatch(/test failed/i)
    expect(toast.message).toBe('401 from the platform')
    expect(JSON.stringify(toasts.addToast.mock.calls)).not.toMatch(/connected/i)
  })

  it('shows each connection as verified, failing or never tested', async () => {
    api.getConnections.mockResolvedValue([
      { id: 'a', platform: 'STRIPE', name: 'Stripe live', category: 'Payments', supportedSyncTypes: [], verification: 'VERIFIED', lastTestAt: '2026-10-15T10:00:00' },
      { id: 'b', platform: 'MAGENTO', name: 'Magento EU', category: 'E-Commerce', supportedSyncTypes: [], verification: 'FAILING', lastTestMessage: 'timed out' },
      { id: 'c', platform: 'UPS', name: 'UPS', category: 'Shipping', supportedSyncTypes: [], verification: 'UNTESTED' },
    ])
    renderPage()
    expect(await screen.findByText('Verified')).toBeInTheDocument()
    expect(screen.getByText('Failing')).toBeInTheDocument()
    expect(screen.getByText('Never tested')).toBeInTheDocument()
    expect(screen.getByText('timed out')).toBeInTheDocument()
  })

  it('a preview run is shown as "nothing imported" with the connector\'s explanation', async () => {
    api.getConnections.mockResolvedValue([{ id: 'b', platform: 'MAGENTO', name: 'Magento EU', category: 'E-Commerce', supportedSyncTypes: [], verification: 'VERIFIED' }])
    api.getJobs.mockResolvedValue([{ jobId: 'j1', connectorId: 'b', syncType: 'ORDER_IMPORT', status: 'NOT_SUPPORTED', itemsSucceeded: 0, itemsFailed: 0,
      message: 'Magento returned 3 orders, but this connector only reads them; nothing was imported into Nexus', startedAt: '2026-10-15T10:00:00' }])
    renderPage()
    fireEvent.click(await screen.findByText('Magento EU'))
    expect(await screen.findByText('Nothing imported')).toBeInTheDocument()
    expect(screen.getByText(/returned 3 orders/)).toBeInTheDocument()
  })

  it('says so when there is nothing to do beyond testing', async () => {
    api.getPlatforms.mockResolvedValue([{ name: 'SAP', platformType: 'SAP', category: 'ERP', supportedSyncTypes: [], previewSyncTypes: [], requiredSettings: ['base_url'] }])
    api.getConnections.mockResolvedValue([{ id: 's', platform: 'SAP', name: 'SAP prod', category: 'ERP', supportedSyncTypes: [], verification: 'UNTESTED' }])
    renderPage()
    fireEvent.click(await screen.findByText('SAP prod'))
    expect(await screen.findByText(/Nexus does nothing with SAP prod/)).toBeInTheDocument()
  })
})
