import { useState, useCallback } from 'react'

interface RateLimitState {
  attempts: number
  blocked: boolean
  blockedUntil: number | null
}

const LOCKOUT_DURATION = 30_000
const MAX_ATTEMPTS = 5

export function useRateLimit(key: string) {
  const [state, setState] = useState<RateLimitState>(() => {
    const stored = sessionStorage.getItem(`rl_${key}`)
    if (stored) {
      const parsed = JSON.parse(stored)
      if (parsed.blockedUntil && Date.now() < parsed.blockedUntil) {
        return parsed
      }
      sessionStorage.removeItem(`rl_${key}`)
    }
    return { attempts: 0, blocked: false, blockedUntil: null }
  })

  const recordAttempt = useCallback(() => {
    setState(prev => {
      if (prev.blocked && prev.blockedUntil && Date.now() < prev.blockedUntil) {
        return prev
      }
      const next = { ...prev, attempts: prev.attempts + 1 }
      if (next.attempts >= MAX_ATTEMPTS) {
        next.blocked = true
        next.blockedUntil = Date.now() + LOCKOUT_DURATION
      }
      sessionStorage.setItem(`rl_${key}`, JSON.stringify(next))
      return next
    })
  }, [key])

  const reset = useCallback(() => {
    const fresh = { attempts: 0, blocked: false, blockedUntil: null }
    setState(fresh)
    sessionStorage.removeItem(`rl_${key}`)
  }, [key])

  const isBlocked = state.blocked && state.blockedUntil !== null && Date.now() < state.blockedUntil
  const remainingMs = isBlocked ? state.blockedUntil! - Date.now() : 0

  return { isBlocked, remainingMs, attempts: state.attempts, recordAttempt, reset }
}
