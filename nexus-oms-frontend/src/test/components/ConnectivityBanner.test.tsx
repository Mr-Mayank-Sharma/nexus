import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import ConnectivityBanner from '../../components/layout/ConnectivityBanner'

describe('ConnectivityBanner', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders nothing when online', () => {
    // navigator.onLine is true by default in jsdom
    const { container } = render(<ConnectivityBanner />)
    expect(container.innerHTML).toBe('')
  })

  it('shows banner when offline', () => {
    Object.defineProperty(navigator, 'onLine', { value: false, writable: true, configurable: true })
    render(<ConnectivityBanner />)
    expect(screen.getByText(/you are offline/i)).toBeInTheDocument()
  })

  it('hides banner when coming back online', () => {
    Object.defineProperty(navigator, 'onLine', { value: false, writable: true, configurable: true })
    const { container } = render(<ConnectivityBanner />)
    expect(screen.getByText(/you are offline/i)).toBeInTheDocument()

    fireEvent(window, new Event('online'))
    expect(container.innerHTML).toBe('')
  })

  it('shows banner when going offline', () => {
    Object.defineProperty(navigator, 'onLine', { value: true, writable: true, configurable: true })
    const { container } = render(<ConnectivityBanner />)
    expect(container.innerHTML).toBe('')

    fireEvent(window, new Event('offline'))
    expect(screen.getByText(/you are offline/i)).toBeInTheDocument()
  })

  it('cleans up event listeners on unmount', () => {
    const removeSpy = vi.spyOn(window, 'removeEventListener')
    const { unmount } = render(<ConnectivityBanner />)
    unmount()
    expect(removeSpy).toHaveBeenCalledWith('online', expect.any(Function))
    expect(removeSpy).toHaveBeenCalledWith('offline', expect.any(Function))
  })
})
