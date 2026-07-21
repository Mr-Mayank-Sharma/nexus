import '@testing-library/jest-dom'
import { vi } from 'vitest'

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// Mock IntersectionObserver
class MockIntersectionObserver {
  observe = vi.fn()
  unobserve = vi.fn()
  disconnect = vi.fn()
}

Object.defineProperty(window, 'IntersectionObserver', {
  writable: true,
  value: MockIntersectionObserver,
})

// Mock ResizeObserver
class MockResizeObserver {
  observe = vi.fn()
  unobserve = vi.fn()
  disconnect = vi.fn()
}

Object.defineProperty(window, 'ResizeObserver', {
  writable: true,
  value: MockResizeObserver,
})

// Mock scrollTo
window.scrollTo = vi.fn()

const realStore: Record<string, string> = {}
Object.defineProperty(window, 'localStorage', {
  value: {
    getItem: (key: string) => realStore[key] ?? null,
    setItem: (key: string, val: string) => { realStore[key] = String(val) },
    removeItem: (key: string) => { delete realStore[key] },
    clear: () => { Object.keys(realStore).forEach(k => delete realStore[k]) },
    get length() { return Object.keys(realStore).length },
    key: (i: number) => Object.keys(realStore)[i] ?? null,
  },
})

const sessionStore: Record<string, string> = {}
Object.defineProperty(window, 'sessionStorage', {
  value: {
    getItem: (key: string) => sessionStore[key] ?? null,
    setItem: (key: string, val: string) => { sessionStore[key] = String(val) },
    removeItem: (key: string) => { delete sessionStore[key] },
    clear: () => { Object.keys(sessionStore).forEach(k => delete sessionStore[k]) },
    get length() { return Object.keys(sessionStore).length },
    key: (i: number) => Object.keys(sessionStore)[i] ?? null,
  },
})

// Mock fetch
global.fetch = vi.fn().mockResolvedValue({
  ok: true,
  json: vi.fn().mockResolvedValue({}),
})

// Mock console.error to reduce noise in tests
const originalError = console.error
beforeAll(() => {
  console.error = (...args: unknown[]) => {
    if (
      typeof args[0] === 'string' &&
      args[0].includes('Warning: ReactDOM.render is no longer supported')
    ) {
      return
    }
    originalError.call(console, ...args)
  }
})

afterAll(() => {
  console.error = originalError
})

// Clean up after each test
afterEach(() => {
  vi.clearAllMocks()
})
