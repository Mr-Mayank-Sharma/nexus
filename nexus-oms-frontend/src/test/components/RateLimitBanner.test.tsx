import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import RateLimitBanner from '../../components/common/RateLimitBanner'

describe('RateLimitBanner', () => {
  it('renders with seconds when under 60s', () => {
    render(<RateLimitBanner remainingMs={15_000} />)
    expect(screen.getByText(/try again in 15s/i)).toBeInTheDocument()
  })

  it('renders with minutes when over 60s', () => {
    render(<RateLimitBanner remainingMs={90_000} />)
    expect(screen.getByText(/try again in 2m/i)).toBeInTheDocument()
  })

  it('has alert role', () => {
    render(<RateLimitBanner remainingMs={10_000} />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })
})
