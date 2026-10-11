import { describe, expect, it } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { ToastProvider } from '../../components/common/ToastProvider'
import { useToast } from '../../hooks/useToast'

let api: ReturnType<typeof useToast>
function Capture() {
  api = useToast()
  return null
}

function mount() {
  render(<ToastProvider><Capture /></ToastProvider>)
}

/** Six screens call toast(); it used to be undefined, so every one of those calls threw. */
describe('toast()', () => {
  it('shows a message given as (text, variant), the form the RFID, MCP, Returns and Sync pages use', () => {
    mount()
    act(() => api.toast('RFID session opened', 'success'))
    expect(screen.getByText('RFID session opened')).toBeInTheDocument()
  })

  it('shows an object with a description, the form the live-notification handlers use', () => {
    mount()
    act(() => api.toast({ title: 'Inventory Alert', description: 'SKU-1 is low', variant: 'warning' }))
    expect(screen.getByText('Inventory Alert')).toBeInTheDocument()
    expect(screen.getByText('SKU-1 is low')).toBeInTheDocument()
  })

  it('treats a missing or "default" variant as info, and still supports addToast', () => {
    mount()
    expect(() => act(() => { api.toast('plain'); api.toast({ title: 'dflt', variant: 'default' }); api.addToast({ type: 'error', title: 'legacy' }) })).not.toThrow()
    expect(screen.getByText('plain')).toBeInTheDocument()
    expect(screen.getByText('legacy')).toBeInTheDocument()
  })
})
