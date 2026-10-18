import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'

const api = vi.hoisted(() => ({
  getJobTypes: vi.fn(), getJobs: vi.fn(), createJob: vi.fn(), updateJob: vi.fn(), deleteJob: vi.fn(), setJobEnabled: vi.fn(),
  runJob: vi.fn(), getRuns: vi.fn(), getRun: vi.fn(), getRunLog: vi.fn(), getRunIssues: vi.fn(), downloadRunFile: vi.fn(),
  getStoreConnections: vi.fn(),
}))
vi.mock('../features/jobs/jobsApi', () => api)
vi.mock('../hooks/useToast', () => ({ useToast: () => ({ addToast: vi.fn(), toast: vi.fn() }) }))
vi.mock('../components/rbac/PermissionGate', () => ({ default: ({ children }: { children: React.ReactNode }) => <>{children}</> }))

import JobsPage from './JobsPage'

const job = {
  id: 'j1', name: 'Nightly stock', type: 'STOCK_EXPORT', typeLabel: 'Export stock (CSV)', params: {}, cron: '0 0 2 * * *',
  timezone: 'UTC', enabled: false, nextRunAt: null, lastRunAt: '2026-10-18T08:00:00', lastStatus: 'PARTIAL', createdBy: 'ops',
}
const partialRun = {
  id: 'r1', jobId: 'j1', status: 'PARTIAL', triggerKind: 'MANUAL', triggeredBy: 'ops', startedAt: '2026-10-18T08:00:00', finishedAt: '2026-10-18T08:00:05',
  total: 10, succeeded: 8, failed: 2, issueCount: 2, message: '2 rows failed', outputName: 'stock.csv', hasOutput: true,
}

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter><JobsPage /></MemoryRouter></QueryClientProvider>)
}

describe('JobsPage', () => {
  beforeEach(() => {
    Object.values(api).forEach(f => f.mockReset())
    api.getJobTypes.mockResolvedValue([{ type: 'STOCK_EXPORT', label: 'Export stock (CSV)', description: 'A CSV of stock.', params: [] }])
    api.getJobs.mockResolvedValue([job])
    api.getStoreConnections.mockResolvedValue([])
    api.getRuns.mockResolvedValue([partialRun])
    api.getRun.mockResolvedValue(partialRun)
    api.getRunLog.mockResolvedValue('08:00:00  Started')
    api.getRunIssues.mockResolvedValue([{ id: 'i1', rowRef: 'SKU-9', reason: 'Nexus holds 3 but Shopify holds 5', raw: 'node-1' }])
    api.runJob.mockResolvedValue({ ...partialRun, status: 'RUNNING' })
  })

  it('lists jobs with a plain schedule, and says a new job is off', async () => {
    renderPage()
    expect((await screen.findAllByText('Nightly stock')).length).toBeGreaterThan(0)   // table row and phone card
    expect(screen.getAllByText('Every day at 02:00 (UTC)').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Switched off').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Finished with failures').length).toBeGreaterThan(0)
  })

  it('shows the rows that failed with the reason, and offers both downloads', async () => {
    renderPage()
    fireEvent.click((await screen.findAllByText('Nightly stock'))[0])
    const history = await screen.findByLabelText('Run history')
    fireEvent.click(await within(history).findByText('Finished with failures'))
    expect(await screen.findByText('Nexus holds 3 but Shopify holds 5')).toBeInTheDocument()
    expect(screen.getByText('SKU-9')).toBeInTheDocument()
    expect(screen.getByText('stock.csv')).toBeInTheDocument()
    expect(screen.getByText('Rows to check (CSV)')).toBeInTheDocument()
  })

  it('starts a run on request', async () => {
    renderPage()
    fireEvent.click(await screen.findByLabelText('Run Nightly stock now'))
    await waitFor(() => expect(api.runJob.mock.calls[0]?.[0]).toBe('j1'))
  })

  it('cannot be saved until it is complete, and says what is missing', async () => {
    renderPage()
    fireEvent.click(await screen.findByText('New job'))
    expect(await screen.findByText('Give the job a name.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save job' })).toBeDisabled()
  })

  it('does not let a job without a schedule be switched on', async () => {
    api.getJobs.mockResolvedValue([{ ...job, cron: null }])
    renderPage()
    expect(await screen.findByLabelText('Switch Nightly stock on')).toBeDisabled()
  })
})
