import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useRateLimit } from '../../hooks/useRateLimit'

describe('useRateLimit', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.useFakeTimers()
  })

  it('allows attempts under limit', () => {
    const { result } = renderHook(() => useRateLimit('login'))

    act(() => { result.current.recordAttempt() })
    expect(result.current.isBlocked).toBe(false)
    expect(result.current.attempts).toBe(1)
  })

  it('blocks after max attempts', () => {
    const { result } = renderHook(() => useRateLimit('login'))

    for (let i = 0; i < 5; i++) {
      act(() => { result.current.recordAttempt() })
    }
    expect(result.current.isBlocked).toBe(true)
  })

  it('unblocks after lockout expires', () => {
    const { result, rerender } = renderHook(() => useRateLimit('login'))

    for (let i = 0; i < 5; i++) {
      act(() => { result.current.recordAttempt() })
    }
    expect(result.current.isBlocked).toBe(true)

    act(() => { vi.advanceTimersByTime(31_000) })
    rerender()
    expect(result.current.isBlocked).toBe(false)
  })

  it('resets attempts', () => {
    const { result } = renderHook(() => useRateLimit('login'))

    act(() => { result.current.recordAttempt() })
    act(() => { result.current.recordAttempt() })
    expect(result.current.attempts).toBe(2)

    act(() => { result.current.reset() })
    expect(result.current.attempts).toBe(0)
    expect(result.current.isBlocked).toBe(false)
  })

  it('persists state to sessionStorage', () => {
    const { result } = renderHook(() => useRateLimit('login'))

    act(() => { result.current.recordAttempt() })
    const stored = sessionStorage.getItem('rl_login')
    expect(stored).toBeTruthy()
    expect(JSON.parse(stored!).attempts).toBe(1)
  })
})
