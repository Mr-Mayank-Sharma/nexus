import type { ChannelMapping } from './inventoryApi'

/** A row being edited; the selects start empty. */
export interface MappingRow extends Omit<ChannelMapping, 'nodeId' | 'externalLocationId' | 'contributionCap'> {
  key: string
  nodeId: string
  externalLocationId: string
  contributionCap: string   // text while editing; empty means no cap
}

/**
 * Mirrors what the server enforces, so the user hears about a problem before saving rather than
 * from a 400. The server remains the authority.
 */
export function validateMappings(rows: MappingRow[]): string[] {
  const problems: string[] = []
  const seen = new Set<string>()
  rows.forEach((row, i) => {
    const n = i + 1
    if (!row.nodeId) problems.push(`Row ${n}: choose a Nexus location`)
    if (!row.externalLocationId) problems.push(`Row ${n}: choose a Shopify location`)
    if (row.nodeId) {
      if (seen.has(row.nodeId)) problems.push(`Row ${n}: this Nexus location is already mapped; it can feed only one Shopify location`)
      seen.add(row.nodeId)
    }
    if (!Number.isInteger(row.bufferUnits) || row.bufferUnits < 0) problems.push(`Row ${n}: the buffer must be a whole number, 0 or more`)
    if (row.contributionCap.trim() !== '' && !/^\d+$/.test(row.contributionCap.trim())) {
      problems.push(`Row ${n}: the cap must be a whole number, 0 or more, or empty for no cap`)
    }
  })
  return problems
}

export function toPayload(rows: MappingRow[]): ChannelMapping[] {
  return rows.map(r => ({
    nodeId: r.nodeId,
    externalLocationId: r.externalLocationId,
    externalLocationName: r.externalLocationName ?? null,
    enabled: r.enabled,
    bufferUnits: r.bufferUnits,
    contributionCap: r.contributionCap.trim() === '' ? null : Number(r.contributionCap),
  }))
}

export function fromMapping(m: ChannelMapping, index: number): MappingRow {
  return {
    key: `${m.nodeId}-${index}`,
    nodeId: m.nodeId,
    externalLocationId: m.externalLocationId,
    externalLocationName: m.externalLocationName,
    enabled: m.enabled,
    bufferUnits: m.bufferUnits,
    contributionCap: m.contributionCap == null ? '' : String(m.contributionCap),
  }
}

let counter = 0
export const emptyRow = (): MappingRow => ({
  key: `new-${++counter}`, nodeId: '', externalLocationId: '', externalLocationName: null, enabled: true, bufferUnits: 0, contributionCap: '',
})

/** Plain-language form of a node's arithmetic, shown in the reconciliation report. */
export function explain(c: { onHand: number; allocated: number; reserved: number; damaged: number; safetyStock: number; buffer: number; expiredLotUnits: number; sellable: number; cappedTo: number | null; contributed: number; excluded: string | null }): string {
  if (c.excluded === 'NODE_INACTIVE') return 'Location is inactive: contributes 0'
  if (c.excluded === 'NODE_NOT_FOUND') return 'Mapped location no longer exists: contributes 0'
  const parts = [`${c.onHand} on hand`]
  if (c.allocated) parts.push(`− ${c.allocated} allocated`)
  if (c.reserved) parts.push(`− ${c.reserved} reserved`)
  if (c.damaged) parts.push(`− ${c.damaged} damaged`)
  if (c.safetyStock) parts.push(`− ${c.safetyStock} safety stock`)
  if (c.buffer) parts.push(`− ${c.buffer} buffer`)
  let text = `${parts.join(' ')} = ${c.sellable}`
  if (c.cappedTo != null && c.contributed < c.sellable) text += `, capped at ${c.cappedTo}`
  if (c.expiredLotUnits) text += ` (${c.expiredLotUnits} expired units ignored)`
  return text
}
