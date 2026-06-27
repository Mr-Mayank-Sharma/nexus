import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { EnterpriseKPICard } from '../../components/enterprise'

describe('EnterpriseKPICard', () => {
  it('renders title and value', () => {
    render(<EnterpriseKPICard title="Total Orders" value="1,234" />)
    expect(screen.getByText('Total Orders')).toBeInTheDocument()
    expect(screen.getByText('1,234')).toBeInTheDocument()
  })

  it('renders subtitle when provided', () => {
    render(<EnterpriseKPICard title="Revenue" value="$50K" subtitle="vs last month" />)
    expect(screen.getByText('vs last month')).toBeInTheDocument()
  })

  it('shows trend up icon', () => {
    render(<EnterpriseKPICard title="Sales" value="500" trend="up" trendValue="+12%" />)
    expect(screen.getByText('+12%')).toBeInTheDocument()
  })

  it('shows skeleton when loading', () => {
    const { container } = render(<EnterpriseKPICard title="Loading" value="0" loading />)
    expect(container.querySelector('.enterprise-skeleton')).toBeInTheDocument()
  })
})
