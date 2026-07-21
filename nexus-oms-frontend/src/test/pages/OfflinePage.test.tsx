import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import OfflinePage from '../../pages/OfflinePage'

describe('OfflinePage', () => {
  it('renders offline message', () => {
    render(<OfflinePage />)
    expect(screen.getByText(/you.*re offline/i)).toBeInTheDocument()
  })

  it('renders description text', () => {
    render(<OfflinePage />)
    expect(screen.getByText(/no internet connection/i)).toBeInTheDocument()
  })

  it('reloads page when Try Again clicked', () => {
    const reloadSpy = vi.fn()
    Object.defineProperty(window, 'location', { value: { reload: reloadSpy }, writable: true })
    render(<OfflinePage />)
    fireEvent.click(screen.getByTestId('retry-offline'))
    expect(reloadSpy).toHaveBeenCalledOnce()
  })
})
