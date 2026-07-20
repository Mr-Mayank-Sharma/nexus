import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../../context/AuthContext'
import { ThemeProvider } from '../../context/ThemeContext'
import { ToastProvider } from '../../components/common/ToastProvider'
import LoginPage from '../../pages/LoginPage'

vi.mock('../../api/auth', () => ({
  login: vi.fn().mockResolvedValue({
    success: true,
    data: { token: 'test-token', refreshToken: 'test-refresh', user: { id: 'u1', username: 'admin' } },
  }),
  getTenants: vi.fn().mockResolvedValue({
    success: true,
    data: [{ id: 't1', name: 'Test Tenant' }],
  }),
  forgotPassword: vi.fn().mockResolvedValue({ success: true }),
  verifyMfa: vi.fn().mockResolvedValue({ success: true }),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

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

describe('Auth Flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('renders login page with form fields', () => {
    renderWithProviders(<LoginPage />)
    expect(screen.getByPlaceholderText(/username/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/password/i)).toBeInTheDocument()
  })

  it('shows error on empty submission', async () => {
    renderWithProviders(<LoginPage />)
    const submitButton = screen.getByRole('button', { name: /sign in/i })
    fireEvent.click(submitButton)
    await waitFor(() => {
      expect(screen.getByText(/please fill in all fields/i)).toBeInTheDocument()
    })
  })

  it('submits login with credentials', async () => {
    renderWithProviders(<LoginPage />)
    const usernameInput = screen.getByPlaceholderText(/username/i)
    const passwordInput = screen.getByPlaceholderText(/password/i)

    fireEvent.change(usernameInput, { target: { value: 'admin' } })
    fireEvent.change(passwordInput, { target: { value: 'pass123' } })

    const submitButton = screen.getByRole('button', { name: /sign in/i })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true })
    })
  })

  it('toggles password visibility', () => {
    renderWithProviders(<LoginPage />)
    const passwordInput = screen.getByPlaceholderText(/password/i)
    expect(passwordInput).toHaveAttribute('type', 'password')

    // Find the toggle button (the button with Eye icon inside the password field's container)
    const toggleBtn = passwordInput.parentElement?.querySelector('button[type="button"]')
    expect(toggleBtn).toBeInTheDocument()

    if (toggleBtn) {
      fireEvent.click(toggleBtn)
      expect(passwordInput).toHaveAttribute('type', 'text')

      fireEvent.click(toggleBtn)
      expect(passwordInput).toHaveAttribute('type', 'password')
    }
  })

  it('displays error on failed login', async () => {
    const { login } = await import('../../api/auth')
    vi.mocked(login).mockRejectedValue({
      response: { data: { message: 'Invalid credentials' } },
    })

    renderWithProviders(<LoginPage />)
    const usernameInput = screen.getByPlaceholderText(/username/i)
    const passwordInput = screen.getByPlaceholderText(/password/i)

    fireEvent.change(usernameInput, { target: { value: 'admin' } })
    fireEvent.change(passwordInput, { target: { value: 'wrong' } })

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(screen.getByText(/invalid credentials/i)).toBeInTheDocument()
    })
  })

  it('shows remember me checkbox', () => {
    renderWithProviders(<LoginPage />)
    expect(screen.getByText(/remember me/i)).toBeInTheDocument()
  })
})
