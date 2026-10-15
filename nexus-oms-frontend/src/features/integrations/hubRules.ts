import type { Connection, HubJob, Platform } from './hubApi'

export interface View { label: string; tone: 'good' | 'warn' | 'bad' | 'muted'; detail?: string }

const when = (iso?: string) => (iso ? new Date(iso.endsWith('Z') || /[+-]\d\d:?\d\d$/.test(iso) ? iso : iso + 'Z').toLocaleString() : '')

/** A connection is only "verified" when its last real test passed. Saving never makes it so. */
export function verificationView(c: Connection): View {
  if (c.verification === 'VERIFIED') return { label: 'Verified', tone: 'good', detail: `Last test passed ${when(c.lastTestAt)}` }
  if (c.verification === 'FAILING') return { label: 'Failing', tone: 'bad', detail: c.lastTestMessage || `Last test failed ${when(c.lastTestAt)}` }
  return { label: 'Never tested', tone: 'muted', detail: 'Saved, but nobody has checked that the platform accepts it' }
}

/** What a run really did. "Preview only" means the platform answered but nothing was stored in Nexus. */
export function jobView(j: HubJob): View {
  const said = j.message || j.error || undefined
  switch (j.status) {
    case 'COMPLETED': return { label: 'Done', tone: 'good', detail: `${j.itemsSucceeded} succeeded${j.itemsFailed ? `, ${j.itemsFailed} failed` : ''}` }
    case 'PARTIAL': return { label: 'Partly done', tone: 'warn', detail: said ?? `${j.itemsSucceeded} succeeded, ${j.itemsFailed} failed` }
    case 'NOT_SUPPORTED': return { label: 'Nothing imported', tone: 'warn', detail: said ?? 'This connector does not do this' }
    case 'FAILED': return { label: 'Failed', tone: 'bad', detail: said }
    case 'RUNNING':
    case 'PENDING': return { label: 'Running', tone: 'muted' }
    default: return { label: j.status, tone: 'muted', detail: said }
  }
}

export const OPERATION_LABEL: Record<string, string> = {
  ORDER_IMPORT: 'orders', PRODUCT_SYNC: 'products', PAYMENT_IMPORT: 'charges', INVOICE_SYNC: 'invoices', USER_SYNC: 'users',
  INVENTORY_PUSH: 'Push inventory', FULFILLMENT_PUSH: 'Push fulfilment', REFUND_PUSH: 'Push refund', SEND_SMS: 'Send an SMS',
}

/** Read-only checks are phrased as a check, never as a sync. */
export const previewLabel = (t: string) => `Check for ${OPERATION_LABEL[t] ?? t.toLowerCase()}`
export const actionLabel = (t: string) => OPERATION_LABEL[t] ?? t

/** Operations that spend money, message people or change another system need an explicit confirmation. */
export const NEEDS_CONFIRM = new Set(['REFUND_PUSH', 'FULFILLMENT_PUSH', 'SEND_SMS', 'INVENTORY_PUSH'])

/** Settings the form must ask for: what the platform says it requires, always with a base address first. */
export function requiredFields(p: Platform): string[] {
  const fields = [...(p.requiredSettings ?? [])]
  if (!fields.includes('base_url')) fields.unshift('base_url')
  return fields
}

/** Secrets are never echoed; the field type says so. */
export const isSecretField = (key: string) => /(secret|token|password|api_?key|private|credential)/i.test(key)

export function groupPlatforms(platforms: Platform[]): { category: string; platforms: Platform[] }[] {
  const byCategory = new Map<string, Platform[]>()
  for (const p of platforms) byCategory.set(p.category, [...(byCategory.get(p.category) ?? []), p])
  return [...byCategory.entries()].sort(([a], [b]) => a.localeCompare(b))
    .map(([category, list]) => ({ category, platforms: list.sort((a, b) => a.name.localeCompare(b.name)) }))
}
