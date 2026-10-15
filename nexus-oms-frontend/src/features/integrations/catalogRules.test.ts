import { describe, expect, it } from 'vitest'
import { actionLabel, filterEntries, groupEntries, headline, tally } from './catalogRules'
import type { CatalogEntry } from './catalogApi'

const e = (key: string, tier: CatalogEntry['tier'], group: string, evidences: ('TESTED' | 'CODE_ONLY' | 'NOT_IMPLEMENTED')[]): CatalogEntry => ({
  key, name: key.toLowerCase(), group, tier, route: '/x', summary: `${key} summary`,
  capabilities: evidences.map((evidence, i) => ({ name: `c${i}`, evidence, liveVerified: false, note: '' })),
})

describe('catalogRules', () => {
  const entries = [
    e('UPS', 'GENERIC', 'shipping', ['CODE_ONLY', 'NOT_IMPLEMENTED']),
    e('FEDEX', 'CONNECTOR', 'shipping', ['CODE_ONLY', 'NOT_IMPLEMENTED']),
    e('LABELS', 'NATIVE', 'shipping', ['TESTED', 'TESTED', 'CODE_ONLY', 'NOT_IMPLEMENTED']),
    e('SHOPIFY', 'NATIVE', 'sales-channels', ['TESTED']),
  ]

  it('counts what is tested, written and missing', () => {
    expect(tally(entries[2])).toEqual({ tested: 2, untested: 1, missing: 1 })
  })

  it('states the whole truth on one line, omitting empty parts', () => {
    expect(headline(entries[2])).toBe('2 tested · 1 written, not tested · 1 not built')
    expect(headline(entries[3])).toBe('1 tested')
  })

  it('filters by tier and by text', () => {
    expect(filterEntries(entries, 'GENERIC', '').map(x => x.key)).toEqual(['UPS'])
    expect(filterEntries(entries, 'ALL', 'shopify').map(x => x.key)).toEqual(['SHOPIFY'])
    expect(filterEntries(entries, 'ALL', '').length).toBe(4)
  })

  it('groups in the server order, built-in before connector before generic, and drops empty groups', () => {
    const groups = groupEntries(entries, ['sales-channels', 'point-of-sale', 'shipping'])
    expect(groups.map(g => g.group)).toEqual(['sales-channels', 'shipping'])
    expect(groups[1].entries.map(x => x.key)).toEqual(['LABELS', 'FEDEX', 'UPS'])
  })

  it('does not offer to manage something that has no connector', () => {
    expect(actionLabel(entries[0])).toBe('Add a custom connection')
    expect(actionLabel(entries[1])).toBe('Manage connection')
    expect(actionLabel(entries[2])).toBe('Open')
  })
})
