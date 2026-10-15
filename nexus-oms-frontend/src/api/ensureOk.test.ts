import { describe, expect, it } from 'vitest'
import { ensureOk } from './ensureOk'

describe('ensureOk', () => {
  it('returns a successful result untouched', () => {
    const ok = { success: true, data: [1] }
    expect(ensureOk(ok)).toBe(ok)
  })
  it('passes through results that carry no success flag', () => {
    expect(ensureOk([1, 2])).toEqual([1, 2])
    expect(ensureOk(undefined)).toBeUndefined()
  })
  it('turns a swallowed failure back into an error with the wrapper\'s message', () => {
    expect(() => ensureOk({ success: false, error: 'Failed to push refunds' })).toThrow('Failed to push refunds')
    expect(() => ensureOk({ success: false })).toThrow('The request failed')
  })
})
