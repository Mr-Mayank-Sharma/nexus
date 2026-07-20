import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { ToastProvider } from '../../components/common/ToastProvider'
import { useToast } from '../../hooks/useToast'
import { ReactNode } from 'react'

function wrapper({ children }: { children: ReactNode }) {
  return <ToastProvider>{children}</ToastProvider>
}

describe('useToast Hook', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('provides addToast and removeToast functions', () => {
    const { result } = renderHook(() => useToast(), { wrapper })
    expect(typeof result.current.addToast).toBe('function')
    expect(typeof result.current.removeToast).toBe('function')
    expect(Array.isArray(result.current.toasts)).toBe(true)
  })

  it('creates a toast with default values', () => {
    const { result } = renderHook(() => useToast(), { wrapper })

    act(() => {
      result.current.addToast({
        type: 'info',
        title: 'Test Toast',
        message: 'This is a test',
      })
    })

    expect(result.current.toasts.length).toBe(1)
    expect(result.current.toasts[0].title).toBe('Test Toast')
    expect(result.current.toasts[0].message).toBe('This is a test')
    expect(result.current.toasts[0].type).toBe('info')
  })

  it('creates a toast with error variant', () => {
    const { result } = renderHook(() => useToast(), { wrapper })

    act(() => {
      result.current.addToast({
        type: 'error',
        title: 'Error Toast',
        message: 'Something went wrong',
      })
    })

    expect(result.current.toasts.length).toBe(1)
    expect(result.current.toasts[0].type).toBe('error')
  })

  it('displays multiple toasts', () => {
    const { result } = renderHook(() => useToast(), { wrapper })

    act(() => {
      result.current.addToast({ type: 'info', title: 'Toast 1' })
      result.current.addToast({ type: 'success', title: 'Toast 2' })
      result.current.addToast({ type: 'warning', title: 'Toast 3' })
    })

    expect(result.current.toasts.length).toBe(3)
  })

  it('dismisses a toast', () => {
    const { result } = renderHook(() => useToast(), { wrapper })

    act(() => {
      result.current.addToast({ type: 'info', title: 'Test Toast' })
    })

    const toastId = result.current.toasts[0].id

    act(() => {
      result.current.removeToast(toastId)
    })

    expect(result.current.toasts.length).toBe(0)
  })

  it('creates toast with different types', () => {
    const { result } = renderHook(() => useToast(), { wrapper })

    act(() => {
      result.current.addToast({ type: 'success', title: 'Success' })
      result.current.addToast({ type: 'error', title: 'Error' })
      result.current.addToast({ type: 'warning', title: 'Warning' })
      result.current.addToast({ type: 'info', title: 'Info' })
    })

    expect(result.current.toasts.length).toBe(4)
    expect(result.current.toasts[0].type).toBe('success')
    expect(result.current.toasts[1].type).toBe('error')
    expect(result.current.toasts[2].type).toBe('warning')
    expect(result.current.toasts[3].type).toBe('info')
  })

  it('each toast has a unique id', () => {
    const { result } = renderHook(() => useToast(), { wrapper })

    act(() => {
      result.current.addToast({ type: 'info', title: 'Toast 1' })
      result.current.addToast({ type: 'info', title: 'Toast 2' })
    })

    const ids = result.current.toasts.map(t => t.id)
    expect(new Set(ids).size).toBe(ids.length)
  })
})
