import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import ErrorBoundary from '../../components/layout/ErrorBoundary'

function ThrowingComponent() {
  throw new Error('Test error message')
}

function GoodComponent() {
  return <div>Child rendered</div>
}

let realStore: Record<string, string>
const noopStorage = { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn(), clear: vi.fn(), get length() { return 0 }, key: vi.fn() }

function installRealLocalStorage() {
  realStore = {}
  Object.defineProperty(window, 'localStorage', {
    value: {
      getItem: vi.fn((key: string) => realStore[key] ?? null),
      setItem: vi.fn((key: string, value: string) => { realStore[key] = value }),
      removeItem: vi.fn((key: string) => { delete realStore[key] }),
      clear: vi.fn(() => { realStore = {} }),
      get length() { return Object.keys(realStore).length },
      key: vi.fn((i: number) => Object.keys(realStore)[i] ?? null),
    },
    writable: true,
    configurable: true,
  })
}

describe('ErrorBoundary', () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    vi.clearAllMocks()
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    Object.defineProperty(window, 'localStorage', { value: noopStorage, writable: true, configurable: true })
  })

  it('renders children when no error', () => {
    render(
      <ErrorBoundary>
        <GoodComponent />
      </ErrorBoundary>,
    )
    expect(screen.getByText('Child rendered')).toBeInTheDocument()
  })

  it('catches errors and shows error UI', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>,
    )
    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
    expect(screen.getByText('Test error message')).toBeInTheDocument()
    expect(screen.getByText(/unexpected error occurred/i)).toBeInTheDocument()
  })

  it('renders custom fallback when provided', () => {
    render(
      <ErrorBoundary fallback={<div>Custom fallback</div>}>
        <ThrowingComponent />
      </ErrorBoundary>,
    )
    expect(screen.getByText('Custom fallback')).toBeInTheDocument()
    expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument()
  })

  it('shows Try Again button that resets error state', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>,
    )
    expect(screen.getByText('Something went wrong')).toBeInTheDocument()

    const tryAgainBtn = screen.getByRole('button', { name: /try again/i })
    expect(tryAgainBtn).toBeInTheDocument()

    fireEvent.click(tryAgainBtn)
    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
  })

  it('shows Show/Hide details toggle', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>,
    )

    const detailsBtn = screen.getByRole('button', { name: /show details/i })
    expect(detailsBtn).toBeInTheDocument()

    fireEvent.click(detailsBtn)
    expect(screen.getByRole('button', { name: /hide details/i })).toBeInTheDocument()
    expect(screen.getByText(/Error: Test error message/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /hide details/i }))
    expect(screen.getByRole('button', { name: /show details/i })).toBeInTheDocument()
  })

  it('stores error report in localStorage', () => {
    installRealLocalStorage()

    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>,
    )

    const stored = realStore['nexus_last_error']
    expect(stored).toBeDefined()
    const report = JSON.parse(stored)
    expect(report.message).toBe('Test error message')
    expect(report.timestamp).toBeDefined()
    expect(report.url).toBeDefined()
  })

  it('handles Copy Error button', () => {
    const writeTextMock = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText: writeTextMock } })

    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>,
    )

    fireEvent.click(screen.getByRole('button', { name: /copy error/i }))
    expect(writeTextMock).toHaveBeenCalled()
    const copiedText = writeTextMock.mock.calls[0][0]
    expect(copiedText).toContain('Error: Test error message')
  })
})
