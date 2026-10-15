import type { CatalogEntry, Evidence, Tier } from './catalogApi'

export const GROUP_LABEL: Record<string, string> = {
  'sales-channels': 'Sales channels',
  'point-of-sale': 'Point of sale',
  shipping: 'Shipping & carriers',
  'erp-accounting': 'ERP, CRM & accounting',
  payments: 'Payments',
  edi: 'EDI & B2B',
  messaging: 'Messaging',
  identity: 'Identity & sign-in',
  ai: 'AI',
  equipment: 'Warehouse equipment',
  analytics: 'Analytics',
}

export const TIER_LABEL: Record<Tier, string> = { NATIVE: 'Built into Nexus', CONNECTOR: 'Connector', GENERIC: 'Custom REST only' }

export const TIER_HELP: Record<Tier, string> = {
  NATIVE: 'Part of Nexus itself, with its own screens, storage and scheduling.',
  CONNECTOR: 'A connector for this platform exists, but it covers only the capabilities listed as working.',
  GENERIC: 'No connector for this platform. You can save an address and test it; nothing is imported or written.',
}

export const EVIDENCE_LABEL: Record<Evidence, string> = {
  TESTED: 'Tested',
  CODE_ONLY: 'Written, not tested',
  NOT_IMPLEMENTED: 'Not built',
}

export interface Tally { tested: number; untested: number; missing: number }

export function tally(e: CatalogEntry): Tally {
  return {
    tested: e.capabilities.filter(c => c.evidence === 'TESTED').length,
    untested: e.capabilities.filter(c => c.evidence === 'CODE_ONLY').length,
    missing: e.capabilities.filter(c => c.evidence === 'NOT_IMPLEMENTED').length,
  }
}

/** "2 tested · 1 written, not tested · 3 not built": the whole truth about an entry on one line. */
export function headline(e: CatalogEntry): string {
  const t = tally(e)
  return [
    t.tested ? `${t.tested} tested` : '',
    t.untested ? `${t.untested} written, not tested` : '',
    t.missing ? `${t.missing} not built` : '',
  ].filter(Boolean).join(' · ')
}

export function filterEntries(entries: CatalogEntry[], tier: Tier | 'ALL', query: string): CatalogEntry[] {
  const q = query.trim().toLowerCase()
  return entries.filter(e => (tier === 'ALL' || e.tier === tier)
    && (!q || e.name.toLowerCase().includes(q) || e.key.toLowerCase().includes(q) || e.summary.toLowerCase().includes(q)))
}

/** Groups in the server's order; within a group, built-in first, then connectors, then generic, then by name. */
export function groupEntries(entries: CatalogEntry[], order: string[]): { group: string; label: string; entries: CatalogEntry[] }[] {
  const rank: Record<Tier, number> = { NATIVE: 0, CONNECTOR: 1, GENERIC: 2 }
  return order
    .map(group => ({
      group,
      label: GROUP_LABEL[group] ?? group,
      entries: entries.filter(e => e.group === group)
        .sort((a, b) => rank[a.tier] - rank[b.tier] || a.name.localeCompare(b.name)),
    }))
    .filter(g => g.entries.length > 0)
}

/** The route a person should be sent to; a generic platform has no page of its own, only the connections screen. */
export const actionLabel = (e: CatalogEntry) => (e.tier === 'NATIVE' ? 'Open' : e.tier === 'CONNECTOR' ? 'Manage connection' : 'Add a custom connection')
