import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'

const posApi = vi.hoisted(() => ({
  getSources: vi.fn(), createSource: vi.fn(), rotateKey: vi.fn(), getAggregate: vi.fn(), reconcile: vi.fn(),
}))
vi.mock('../features/stores/posApi', () => posApi)
vi.mock('../hooks/useActiveWarehouse', () => ({
  useActiveWarehouse: () => ({
    warehouses: [{ id: 'n1', name: 'Main Street', code: 'MS' }], warehouseId: 'n1', setWarehouseId: vi.fn(),
    isLoading: false, isError: false, hasWarehouses: true,
  }),
}))
vi.mock('../hooks/useToast', () => ({ useToast: () => ({ addToast: vi.fn(), toast: vi.fn() }) }))
vi.mock('../components/rbac/PermissionGate', () => ({ default: ({ children }: { children: React.ReactNode }) => <>{children}</> }))

import StoreOperationsPage from './StoreOperationsPage'

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter><StoreOperationsPage /></MemoryRouter></QueryClientProvider>)
}

describe('StoreOperationsPage', () => {
  beforeEach(() => {
    Object.values(posApi).forEach(f => f.mockReset())
    posApi.getSources.mockResolvedValue([])
    posApi.getAggregate.mockResolvedValue({
      summary: { DRIFT: 1, MATCH: 1 },
      rows: [
        { sku: 'CARTON', nodeId: 'n1', nexusOnHand: 60, nexusAllocated: 0, posQuantity: 58, delta: -2, posReportedAt: null, status: 'DRIFT' },
        { sku: 'GLOVES', nodeId: 'n1', nexusOnHand: 5, nexusAllocated: 0, posQuantity: 5, delta: 0, posReportedAt: null, status: 'MATCH' },
      ],
    })
  })

  it('shows the till against Nexus, worst difference first, with plain-language status', async () => {
    renderPage()
    expect(await screen.findByText('CARTON')).toBeInTheDocument()
    const skus = screen.getAllByRole('row').slice(1).map(r => r.textContent ?? '')
    expect(skus[0]).toContain('CARTON')
    expect(skus[0]).toContain('-2')
    expect(screen.getAllByText('Differs').length).toBeGreaterThan(0)
  })

  it('says so when no POS is connected, rather than implying one is', async () => {
    renderPage()
    expect(await screen.findByText(/No POS is connected to this location yet/)).toBeInTheDocument()
  })

  it('previews first; the apply button only exists after a preview that has something to apply', async () => {
    posApi.reconcile.mockResolvedValue({ applied: false, lines: [{ sku: 'CARTON', outcome: 'PLANNED', from: 60, to: 58, reason: 'Would adjust by -2' }] })
    renderPage()
    await screen.findByText('CARTON')
    expect(screen.queryByRole('button', { name: /Apply corrections/ })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Preview corrections \(1\)/ }))

    expect(await screen.findByText(/nothing has changed yet/i)).toBeInTheDocument()
    expect(posApi.reconcile).toHaveBeenCalledWith('n1', false)
    expect(screen.getByRole('button', { name: /Apply corrections/ })).toBeInTheDocument()
  })

  it('shows a new key once, with a warning that it cannot be recovered', async () => {
    posApi.createSource.mockResolvedValue({
      source: { id: 's1', tenantId: 't1', nodeId: 'n1', name: 'Till', vendor: 'CUSTOM', staleAfterMinutes: 120, enabled: true, lastSnapshotAt: null, createdAt: '' },
      secret: 'nxpos_abc', note: '',
    })
    renderPage()
    fireEvent.change(await screen.findByPlaceholderText('Front counter'), { target: { value: 'Till' } })
    fireEvent.click(screen.getByRole('button', { name: /Connect a POS/ }))

    expect(await screen.findByText('nxpos_abc')).toBeInTheDocument()
    expect(screen.getByText(/shown once and cannot be recovered/)).toBeInTheDocument()
    await waitFor(() => expect(posApi.createSource).toHaveBeenCalledWith('n1', 'Till', 'CUSTOM', 120))
  })
})
