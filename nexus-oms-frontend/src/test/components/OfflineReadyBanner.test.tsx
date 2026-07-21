import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import OfflineReadyBanner from '../../components/pwa/OfflineReadyBanner'

describe('OfflineReadyBanner', () => {
  it('renders ready message', () => {
    render(<OfflineReadyBanner onDismiss={vi.fn()} />)
    expect(screen.getByText('App ready for offline use')).toBeInTheDocument()
  })

  it('renders cached content description', () => {
    render(<OfflineReadyBanner onDismiss={vi.fn()} />)
    expect(screen.getByText(/content is cached/i)).toBeInTheDocument()
  })

  it('calls onDismiss when Dismiss clicked', () => {
    const onDismiss = vi.fn()
    render(<OfflineReadyBanner onDismiss={onDismiss} />)
    fireEvent.click(screen.getByTestId('dismiss-offline'))
    expect(onDismiss).toHaveBeenCalledOnce()
  })
})
