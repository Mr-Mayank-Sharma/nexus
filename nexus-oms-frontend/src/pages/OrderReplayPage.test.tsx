import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'

const api = vi.hoisted(() => ({ getSample: vi.fn(), runReplay: vi.fn() }))
vi.mock('../features/replay/replayApi', () => api)
vi.mock('../hooks/useToast', () => ({ useToast: () => ({ addToast: vi.fn(), toast: vi.fn() }) }))

import OrderReplayPage from './OrderReplayPage'

const metric = (o: object) => ({
  strategy: 'NEAREST_FIRST', label: 'Nearest', orders: 100, filled: 100, partial: 0, unfillable: 0, shipments: 120, splitOrders: 20, splitRate: 0.2,
  shipmentsPerOrder: 1.2, unfilledUnits: 0, expectedFailedUnits: 10, shippingCost: 780, reworkCost: 65, totalCost: 845, ...o,
})
const response = {
  orders: 100, orderLines: 180, skus: 24, locations: 4,
  result: {
    strategies: [metric({}), metric({ strategy: 'FEWEST_SHIPMENTS', label: 'Fewest shipments', shipments: 100, splitOrders: 0, splitRate: 0, totalCost: 700 }),
      metric({ strategy: 'FAILURE_AWARE', label: 'Avoiding stores that fail picks', shipments: 100, splitOrders: 0, splitRate: 0, expectedFailedUnits: 4, totalCost: 676 })],
    differences: [{ orderId: 'ORD-7', shipmentsBefore: 2, shipmentsAfter: 1, before: 'A + B', after: 'C' }], differencesTotal: 1,
    usedDistance: true, usedFailureRates: true, assumptions: ['Stock is the snapshot you supplied.'],
  },
}

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter><OrderReplayPage /></MemoryRouter></QueryClientProvider>)
}

describe('OrderReplayPage', () => {
  beforeEach(() => {
    Object.values(api).forEach(f => f.mockReset())
    api.getSample.mockResolvedValue({ orders: 'order_id,sku,qty\nA,X,1\n', stock: 'sku,node_id,qty\nX,N,1\n', nodes: 'node_id\nN\n' })
    api.runReplay.mockResolvedValue(response)
  })

  it('cannot run until orders and stock are supplied', () => {
    renderPage()
    expect(screen.getByRole('button', { name: /Run the replay/ })).toBeDisabled()
    expect(screen.getByText('Add the orders and stock files first.')).toBeInTheDocument()
  })

  it('the sample is labelled as invented, and a run shows the result with the caveats beside it', async () => {
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Try it with sample data/ }))
    expect(await screen.findByText(/invented example data/)).toBeInTheDocument()

    const run = screen.getByRole('button', { name: /Run the replay/ })
    await waitFor(() => expect(run).not.toBeDisabled())
    fireEvent.click(run)

    expect(await screen.findByText(/would have cut shipments from 120 to 100/)).toBeInTheDocument()
    expect(screen.getByText('How to read this')).toBeInTheDocument()
    expect(screen.getByText(/not a forecast of your savings/)).toBeInTheDocument()
    expect(screen.getByText('ORD-7')).toBeInTheDocument()
    expect(screen.getByText(/1 shipment(?!s)/)).toBeInTheDocument()   // not "1 shipments"
    expect(screen.getByRole('button', { name: /Comparison \(CSV\)/ })).toBeInTheDocument()
  })
})
