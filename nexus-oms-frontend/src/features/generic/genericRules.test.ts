import { describe, expect, it } from 'vitest'
import { blankInput, describe as describeConn, fromTemplate, inputProblems, pretty, sinceText } from './genericRules'

describe('generic connection rules', () => {
  it('flags a missing name, an unknown entity and invalid JSON', () => {
    const f = { ...blankInput(), name: ' ', entityType: 'invoices', config: '{nope' }
    const p = inputProblems(f)
    expect(p).toContain('Give the connection a name')
    expect(p.some(x => x.startsWith('Choose what to import'))).toBe(true)
    expect(p.some(x => x.startsWith('The configuration is not valid JSON'))).toBe(true)
  })

  it('accepts a sensible connection', () => {
    expect(inputProblems({ ...blankInput(), name: 'ERP' })).toEqual([])
  })

  it('refuses a JSON array as configuration', () => {
    expect(inputProblems({ ...blankInput(), name: 'x', config: '[]' })).toContain('The configuration must be a JSON object')
  })

  it('a template becomes a form with pretty JSON and no secret', () => {
    const f = fromTemplate({ key: 'k', label: 'Woo', direction: 'IMPORT', entityType: 'orders', channel: 'WOOCOMMERCE', note: '', config: { a: 1 } })
    expect(f.config).toBe('{\n  "a": 1\n}')
    expect(f.secret).toBe('')
    expect(f.channel).toBe('WOOCOMMERCE')
  })

  it('describes connections and their watermark', () => {
    const c = { id: '1', name: 'n', direction: 'EXPORT' as const, entityType: 'shipments', channel: null, config: '{}', watermark: null, hasSecret: false, createdAt: '', updatedAt: '' }
    expect(describeConn(c)).toBe('Exports shipments and tracking')
    expect(sinceText(c)).toBe('From the beginning')
    expect(sinceText({ ...c, watermark: '2026-10-01T10:00:00' })).toMatch(/^Since /)
  })

  it('pretty-prints and leaves broken text alone', () => {
    expect(pretty('{"x":1}')).toBe('{\n  "x": 1\n}')
    expect(pretty('{broken')).toBe('{broken')
  })
})
