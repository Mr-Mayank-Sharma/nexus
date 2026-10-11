import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '../../components/common/ToastProvider'
import InventoryPublishingPanel from '../../features/shopify/InventoryPublishingPanel'
import * as inventoryApi from '../../features/shopify/inventoryApi'

vi.mock('../../components/rbac/PermissionGate', () => ({ default: ({ children }: { children: React.ReactNode }) => <>{children}</> }))
vi.mock('../../api/warehouse', () => ({
  getWarehouses: vi.fn().mockResolvedValue({ success: true, data: { content: [
    { id: 'n1', name: 'Online warehouse' }, { id: 'n2', name: 'Store A' },
  ] } }),
}))
vi.mock('../../features/shopify/inventoryApi', async () => {
  const actual = await vi.importActual<typeof import('../../features/shopify/inventoryApi')>('../../features/shopify/inventoryApi')
  return {
    ...actual,
    getMappings: vi.fn(), saveMappings: vi.fn(), getShopifyLocations: vi.fn(), publish: vi.fn(), getRuns: vi.fn(), getRunLines: vi.fn(),
  }
})

const api = vi.mocked(inventoryApi)

const run = (patch: Partial<inventoryApi.PublishRun> = {}): inventoryApi.PublishRun => ({
  id: 'r1', status: 'COMPLETED', dryRun: true, triggeredBy: 'alice', startedAt: '2026-10-11T10:00:00', completedAt: '2026-10-11T10:00:05',
  itemsPlanned: 1, itemsPublished: 0, itemsUnchanged: 0, itemsFailed: 0, itemsSkipped: 0, message: 'Dry run: 1 change(s) planned, nothing written to Shopify.', ...patch,
})

function renderPanel() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ToastProvider><InventoryPublishingPanel storeId="s1" /></ToastProvider></QueryClientProvider>)
}

const mapped = [{ nodeId: 'n1', externalLocationId: '111', externalLocationName: 'Main', enabled: true, bufferUnits: 0, contributionCap: null }]

beforeEach(() => {
  vi.clearAllMocks()
  api.getShopifyLocations.mockResolvedValue([{ id: '111', name: 'Main', active: true }, { id: '222', name: 'Pop-up', active: true }])
  api.getRuns.mockResolvedValue([])
  api.getRunLines.mockResolvedValue([])
})

describe('InventoryPublishingPanel', () => {
  it('says nothing is published until a mapping exists, and offers no way to publish', async () => {
    api.getMappings.mockResolvedValue([])
    renderPanel()

    expect(await screen.findByText('Not configured')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(/not being published/i)
    expect(screen.getByRole('button', { name: /preview/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /publish to shopify/i })).toBeDisabled()
  })

  it('blocks a Nexus location mapped to two Shopify locations before saving', async () => {
    api.getMappings.mockResolvedValue(mapped)
    renderPanel()
    await screen.findByLabelText('Nexus location, row 1')

    fireEvent.click(screen.getByRole('button', { name: /add mapping/i }))
    fireEvent.change(screen.getByLabelText('Nexus location, row 2'), { target: { value: 'n1' } })
    fireEvent.change(screen.getByLabelText('Shopify location, row 2'), { target: { value: '222' } })

    expect(await screen.findByRole('alert')).toHaveTextContent(/already mapped/i)
    expect(screen.getByRole('button', { name: /save mappings/i })).toBeDisabled()
  })

  it('saves edited mappings, and previewing is refused until they are saved', async () => {
    api.getMappings.mockResolvedValue(mapped)
    api.saveMappings.mockResolvedValue([{ ...mapped[0], bufferUnits: 3 }])
    renderPanel()
    const buffer = await screen.findByLabelText('Buffer, row 1')

    fireEvent.change(buffer, { target: { value: '3' } })
    expect(screen.getByRole('button', { name: /preview/i })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: /save mappings/i }))
    await waitFor(() => expect(api.saveMappings).toHaveBeenCalledWith('s1', [expect.objectContaining({ nodeId: 'n1', bufferUnits: 3, contributionCap: null })]))
    await waitFor(() => expect(screen.getByRole('button', { name: /preview/i })).toBeEnabled())
  })

  it('previews without writing, then shows how each quantity was calculated', async () => {
    api.getMappings.mockResolvedValue(mapped)
    api.publish.mockResolvedValue(run())
    api.getRuns.mockResolvedValue([run()])
    api.getRunLines.mockResolvedValue([{
      id: 'l1', sku: 'SKU-1', externalLocationId: '111', calculatedQty: 30, channelQtyBefore: 10, publishedQty: null, outcome: 'PLANNED', drift: true, error: null,
      calculation: JSON.stringify([{ nodeName: 'Online warehouse', onHand: 35, allocated: 5, reserved: 0, damaged: 0, safetyStock: 0, buffer: 0, expiredLotUnits: 0, sellable: 30, cappedTo: null, contributed: 30, excluded: null }]),
    }])
    renderPanel()

    await waitFor(() => expect(screen.getByRole('button', { name: /preview/i })).toBeEnabled())
    fireEvent.click(screen.getByRole('button', { name: /preview/i }))

    await waitFor(() => expect(api.publish).toHaveBeenCalledWith('s1', true))
    const report = await screen.findByText('SKU-1')
    const line = report.closest('tr') as HTMLElement
    expect(within(line).getByText('PLANNED')).toBeInTheDocument()
    expect(within(line).getByText('Changed on Shopify')).toBeInTheDocument()
    fireEvent.click(within(line).getByRole('button', { name: /show how SKU-1 was calculated/i }))
    expect(await screen.findByText(/35 on hand − 5 allocated = 30/)).toBeInTheDocument()
  })

  it('asks for confirmation before changing live Shopify quantities', async () => {
    api.getMappings.mockResolvedValue(mapped)
    api.publish.mockResolvedValue(run({ dryRun: false, message: '1 published, 0 already correct, 0 skipped, 0 failed' }))
    renderPanel()

    await waitFor(() => expect(screen.getByRole('button', { name: /publish to shopify/i })).toBeEnabled())
    fireEvent.click(screen.getByRole('button', { name: /publish to shopify/i }))
    expect(api.publish).not.toHaveBeenCalled()
    expect(screen.getByText(/changes live quantities on shopify/i)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))
    expect(api.publish).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /publish to shopify/i }))
    fireEvent.click(screen.getByRole('button', { name: /confirm publish/i }))
    await waitFor(() => expect(api.publish).toHaveBeenCalledWith('s1', false))
  })

  it('shows why Shopify locations could not be loaded instead of an empty dropdown', async () => {
    api.getMappings.mockResolvedValue(mapped)
    api.getShopifyLocations.mockRejectedValue(new Error('Shopify API error: 401'))
    renderPanel()

    expect(await screen.findByText(/Couldn't load this store's Shopify locations: Shopify API error: 401/)).toBeInTheDocument()
  })
})
