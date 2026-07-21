import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../../context/AuthContext'
import { ThemeProvider } from '../../context/ThemeContext'
import { ToastProvider } from '../../components/common/ToastProvider'
import AiPlatformPage from '../../pages/AiPlatformPage'

const mockGetTenantDashboard = vi.fn()
const mockGetMonitoringDashboard = vi.fn()
const mockGetInferenceLogs = vi.fn()
const mockGetModels = vi.fn()
const mockGetModelSummary = vi.fn()
const mockGetModelVersions = vi.fn()
const mockGetTrainingJobs = vi.fn()
const mockGetFeatureGroups = vi.fn()

vi.mock('../../api/aiPlatform', () => ({
  getTenantDashboard: (...args: unknown[]) => mockGetTenantDashboard(...args),
  getMonitoringDashboard: (...args: unknown[]) => mockGetMonitoringDashboard(...args),
  getInferenceLogs: (...args: unknown[]) => mockGetInferenceLogs(...args),
  getModels: (...args: unknown[]) => mockGetModels(...args),
  getModelSummary: (...args: unknown[]) => mockGetModelSummary(...args),
  getModelVersions: (...args: unknown[]) => mockGetModelVersions(...args),
  getModel: vi.fn().mockResolvedValue({ data: null }),
  getTrainingJobs: (...args: unknown[]) => mockGetTrainingJobs(...args),
  getFeatureGroups: (...args: unknown[]) => mockGetFeatureGroups(...args),
  startTrainingJob: vi.fn().mockResolvedValue({}),
  deployModel: vi.fn().mockResolvedValue({}),
  predict: vi.fn().mockResolvedValue({ data: null }),
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



function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
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
              <AiPlatformPage />
            </ToastProvider>
          </AuthProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('AiPlatformPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetTenantDashboard.mockResolvedValue({ data: {
      predictionsToday: 250,
      fallbacksToday: 12,
      costsThisMonth: { inference: 45.50, training: 120.00 },
      deployments: [{ id: 'd1', modelName: 'Demand Forecaster', environment: 'prod', status: 'ACTIVE' }],
      modelPerformance: [{ id: 'm1', name: 'Demand Forecaster', status: 'ACTIVE', accuracy: 0.92, avgLatencyMs: 45, predictionsToday: 200, driftDetected: false }],
    }})
    mockGetMonitoringDashboard.mockResolvedValue({ data: {
      activeModels: 5,
      modelsInTraining: 1,
      models: [{ id: 'm1', name: 'Demand Forecaster', displayName: 'Demand Forecaster', status: 'ACTIVE' }],
    }})
    mockGetInferenceLogs.mockResolvedValue({ data: { content: [] } })
    mockGetModels.mockResolvedValue({ data: { content: [] } })
    mockGetModelSummary.mockResolvedValue({ data: { totalModels: 0, activeModels: 0, globalModels: 0, tenantModels: 0, modelsInTraining: 0 } })
    mockGetModelVersions.mockResolvedValue({ data: [] })
    mockGetTrainingJobs.mockResolvedValue({ data: { content: [] } })
    mockGetFeatureGroups.mockResolvedValue({ data: [] })
  })

  it('renders AI Platform heading', async () => {
    renderPage()
    expect(screen.getAllByText('AI Platform').length).toBeGreaterThanOrEqual(1)
  })

  it('shows loading spinner initially', () => {
    mockGetTenantDashboard.mockReturnValue(new Promise(() => {}))
    renderPage()
    expect(document.querySelector('.animate-spin')).toBeInTheDocument()
  })

  it('renders all four tabs', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Overview')).toBeInTheDocument()
    })
    expect(screen.getByText('Model Registry')).toBeInTheDocument()
    expect(screen.getByText('Training')).toBeInTheDocument()
    expect(screen.getByText('Feature Store')).toBeInTheDocument()
  })

  it('shows overview KPI cards after loading', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Active Models')).toBeInTheDocument()
    })
    expect(screen.getAllByText('Predictions Today').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('Monthly Cost')).toBeInTheDocument()
  })

  it('displays active models count from dashboard', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('5')).toBeInTheDocument()
    })
  })

  it('displays predictions today count', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('250')).toBeInTheDocument()
    })
  })

  it('displays monthly cost', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('$165.50')).toBeInTheDocument()
    })
  })

  it('shows Model Health section', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Model Health')).toBeInTheDocument()
    })
  })

  it('shows Model Performance section', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Model Performance')).toBeInTheDocument()
    })
  })

  it('shows Cost Breakdown section', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText(/Cost Breakdown/)).toBeInTheDocument()
    })
  })

  it('shows Inference Activity section', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Inference Activity')).toBeInTheDocument()
    })
  })

  it('shows No recent inference activity when logs empty', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('No recent inference activity')).toBeInTheDocument()
    })
  })

  it('switches to Model Registry tab', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Model Registry')).toBeInTheDocument()
    })
    fireEvent.click(screen.getByText('Model Registry'))
    await waitFor(() => {
      expect(screen.getByText('Total Models')).toBeInTheDocument()
    })
  })

  it('switches to Training tab', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Training')).toBeInTheDocument()
    })
    fireEvent.click(screen.getByText('Training'))
    await waitFor(() => {
      expect(screen.getByText('Total Jobs')).toBeInTheDocument()
    })
  })

  it('switches to Feature Store tab', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Feature Store')).toBeInTheDocument()
    })
    fireEvent.click(screen.getByText('Feature Store'))
    await waitFor(() => {
      expect(screen.getByText('Feature Groups')).toBeInTheDocument()
    })
  })

  it('shows no models found in Model Registry when empty', async () => {
    renderPage()
    fireEvent.click(screen.getByText('Model Registry'))
    await waitFor(() => {
      expect(screen.getByText('No models found')).toBeInTheDocument()
    })
  })

  it('shows no training jobs when empty', async () => {
    renderPage()
    fireEvent.click(screen.getByText('Training'))
    await waitFor(() => {
      expect(screen.getByText('No training jobs found')).toBeInTheDocument()
    })
  })

  it('shows no feature groups when empty', async () => {
    renderPage()
    fireEvent.click(screen.getByText('Feature Store'))
    await waitFor(() => {
      expect(screen.getByText('No feature groups defined')).toBeInTheDocument()
    })
  })

  it('shows Fallbacks Today KPI on overview', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Fallbacks Today')).toBeInTheDocument()
    })
    expect(screen.getByText('12')).toBeInTheDocument()
  })

  it('shows Rule Engine Usage on overview', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Rule Engine Usage')).toBeInTheDocument()
    })
  })

  it('loads inference logs for first model performance', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Active Models')).toBeInTheDocument()
    })
    expect(mockGetInferenceLogs).toHaveBeenCalledWith('m1', 0, 20)
  })

  it('renders breadcrumbs', async () => {
    renderPage()
    expect(screen.getByText('Home')).toBeInTheDocument()
    expect(screen.getAllByText('AI Platform').length).toBeGreaterThanOrEqual(1)
  })
})
