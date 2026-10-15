import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'

const api = vi.hoisted(() => ({ getCatalog: vi.fn(), getConnectionHealth: vi.fn() }))
vi.mock('../features/integrations/catalogApi', () => api)

import IntegrationDirectoryPage from './IntegrationDirectoryPage'

const cap = (name: string, evidence: string) => ({ name, evidence, liveVerified: false, note: '' })
const catalog = {
  groupOrder: ['sales-channels', 'erp-accounting'],
  entries: [
    { key: 'SHOPIFY', name: 'Shopify', group: 'sales-channels', tier: 'NATIVE', route: '/integrations/shopify', summary: 'Built in.', capabilities: [cap('Order import', 'CODE_ONLY'), cap('Inventory publishing', 'TESTED')] },
    { key: 'SAP', name: 'SAP S/4HANA', group: 'erp-accounting', tier: 'CONNECTOR', route: '/integration-hub', summary: 'Not integrated.', capabilities: [cap('Connection test', 'CODE_ONLY'), cap('Orders', 'NOT_IMPLEMENTED')] },
    { key: 'NETSUITE', name: 'NetSuite', group: 'erp-accounting', tier: 'GENERIC', route: '/integration-hub', summary: 'No connector.', capabilities: [cap('Orders', 'NOT_IMPLEMENTED')] },
  ],
  counts: { native: 1, connector: 1, generic: 1, total: 3 },
  evidenceLegend: {}, liveVerifiedNote: 'No capability has yet been run against the real platform with real credentials.',
}

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter><IntegrationDirectoryPage /></MemoryRouter></QueryClientProvider>)
}

describe('IntegrationDirectoryPage', () => {
  beforeEach(() => {
    api.getCatalog.mockResolvedValue(catalog)
    api.getConnectionHealth.mockResolvedValue({ connections: 0, verified: 0, failing: 0, untested: 0, connectors: [] })
  })

  it('always states that nothing has been run against a real platform', async () => {
    renderPage()
    expect(await screen.findByText(/No capability has yet been run against the real platform/)).toBeInTheDocument()
  })

  it('labels each entry by what it really is, with the evidence tally', async () => {
    renderPage()
    expect(await screen.findByText('Built into Nexus', { selector: 'span' })).toBeInTheDocument()
    expect(screen.getByText('1 tested · 1 written, not tested')).toBeInTheDocument()
    expect(screen.getByText('1 written, not tested · 1 not built')).toBeInTheDocument()
  })

  it('a generic platform offers a custom connection, not a managed one', async () => {
    renderPage()
    expect(await screen.findByText('Add a custom connection')).toBeInTheDocument()
    expect(screen.getByText('Manage connection')).toBeInTheDocument()
  })

  it('the tier filter and search narrow the list', async () => {
    renderPage()
    await screen.findByText('NetSuite')
    fireEvent.click(screen.getByRole('button', { name: 'Built into Nexus' }))
    expect(screen.queryByText('NetSuite')).not.toBeInTheDocument()
    expect(screen.getByText('Shopify')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'All' }))
    fireEvent.change(screen.getByLabelText('Search integrations'), { target: { value: 'sap' } })
    expect(screen.queryByText('Shopify')).not.toBeInTheDocument()
    expect(screen.getByText('SAP S/4HANA')).toBeInTheDocument()
  })

  it('expanding an entry shows what is not built, in plain words', async () => {
    renderPage()
    fireEvent.click((await screen.findAllByText(/Show what it can do/))[1])
    expect(await screen.findByText('Orders')).toBeInTheDocument()
    expect(screen.getAllByText(/Not built/).length).toBeGreaterThan(1)   // the legend and the item itself
  })
})
