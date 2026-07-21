import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import UpdateBanner from '../../components/pwa/UpdateBanner'

describe('UpdateBanner', () => {
  it('renders update notification', () => {
    render(<UpdateBanner onUpdate={vi.fn()} onDismiss={vi.fn()} />)
    expect(screen.getByText('A new version is available')).toBeInTheDocument()
  })

  it('calls onUpdate when Update button clicked', () => {
    const onUpdate = vi.fn()
    render(<UpdateBanner onUpdate={onUpdate} onDismiss={vi.fn()} />)
    fireEvent.click(screen.getByTestId('update-button'))
    expect(onUpdate).toHaveBeenCalledOnce()
  })

  it('calls onDismiss when dismiss clicked', () => {
    const onDismiss = vi.fn()
    render(<UpdateBanner onUpdate={vi.fn()} onDismiss={onDismiss} />)
    fireEvent.click(screen.getByTestId('dismiss-update'))
    expect(onDismiss).toHaveBeenCalledOnce()
  })
})
