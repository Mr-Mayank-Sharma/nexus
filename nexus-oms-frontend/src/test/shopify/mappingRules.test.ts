import { describe, expect, it } from 'vitest'
import { parseCalculation } from '../../features/shopify/inventoryApi'
import { emptyRow, explain, fromMapping, toPayload, validateMappings, type MappingRow } from '../../features/shopify/mappingRules'

const row = (patch: Partial<MappingRow> = {}): MappingRow => ({ ...emptyRow(), nodeId: 'n1', externalLocationId: '111', ...patch })

describe('validateMappings', () => {
  it('accepts a complete row', () => {
    expect(validateMappings([row()])).toEqual([])
  })

  it('reports missing selections by row number', () => {
    const problems = validateMappings([row({ nodeId: '' }), row({ nodeId: 'n2', externalLocationId: '' })])
    expect(problems).toEqual(['Row 1: choose a Nexus location', 'Row 2: choose a Shopify location'])
  })

  it('rejects a Nexus location mapped twice (it would be offered twice)', () => {
    const problems = validateMappings([row({ nodeId: 'n1', externalLocationId: '111' }), row({ nodeId: 'n1', externalLocationId: '222' })])
    expect(problems).toHaveLength(1)
    expect(problems[0]).toMatch(/Row 2.*only one Shopify location/)
  })

  it('allows several Nexus locations to share one Shopify location', () => {
    expect(validateMappings([row({ nodeId: 'n1' }), row({ nodeId: 'n2' })])).toEqual([])
  })

  it('rejects negative or fractional buffers and non-numeric caps', () => {
    expect(validateMappings([row({ bufferUnits: -1 })])[0]).toMatch(/buffer/)
    expect(validateMappings([row({ bufferUnits: 1.5 })])[0]).toMatch(/buffer/)
    expect(validateMappings([row({ contributionCap: 'abc' })])[0]).toMatch(/cap/)
    expect(validateMappings([row({ contributionCap: '-3' })])[0]).toMatch(/cap/)
    expect(validateMappings([row({ contributionCap: '' })])).toEqual([])
    expect(validateMappings([row({ contributionCap: ' 25 ' })])).toEqual([])
  })
})

describe('payload conversion', () => {
  it('round-trips a mapping, treating an empty cap as no cap', () => {
    const rows = [
      fromMapping({ nodeId: 'n1', externalLocationId: '111', enabled: true, bufferUnits: 2, contributionCap: 50 }, 0),
      fromMapping({ nodeId: 'n2', externalLocationId: '111', enabled: false, bufferUnits: 0, contributionCap: null }, 1),
    ]
    expect(rows[1].contributionCap).toBe('')
    expect(toPayload(rows)).toEqual([
      { nodeId: 'n1', externalLocationId: '111', externalLocationName: null, enabled: true, bufferUnits: 2, contributionCap: 50 },
      { nodeId: 'n2', externalLocationId: '111', externalLocationName: null, enabled: false, bufferUnits: 0, contributionCap: null },
    ])
  })
})

describe('explain', () => {
  const base = { onHand: 100, allocated: 0, reserved: 0, damaged: 0, safetyStock: 0, buffer: 0, expiredLotUnits: 0, sellable: 100, cappedTo: null, contributed: 100, excluded: null }

  it('shows only the terms that apply', () => {
    expect(explain(base)).toBe('100 on hand = 100')
    expect(explain({ ...base, allocated: 10, reserved: 5, safetyStock: 7, sellable: 78, contributed: 78 })).toBe('100 on hand − 10 allocated − 5 reserved − 7 safety stock = 78')
  })

  it('mentions a cap only when it bites, and expired units when present', () => {
    expect(explain({ ...base, cappedTo: 150 })).toBe('100 on hand = 100')
    expect(explain({ ...base, cappedTo: 25, contributed: 25 })).toBe('100 on hand = 100, capped at 25')
    expect(explain({ ...base, expiredLotUnits: 12 })).toContain('12 expired units ignored')
  })

  it('explains exclusions in words', () => {
    expect(explain({ ...base, excluded: 'NODE_INACTIVE', contributed: 0 })).toMatch(/inactive/)
    expect(explain({ ...base, excluded: 'NODE_NOT_FOUND', contributed: 0 })).toMatch(/no longer exists/)
  })
})

describe('parseCalculation', () => {
  it('parses the recorded arithmetic and tolerates bad data', () => {
    expect(parseCalculation('[{"nodeName":"Store A","onHand":5}]')[0].nodeName).toBe('Store A')
    expect(parseCalculation(null)).toEqual([])
    expect(parseCalculation('not json')).toEqual([])
    expect(parseCalculation('{"a":1}')).toEqual([])
  })
})
