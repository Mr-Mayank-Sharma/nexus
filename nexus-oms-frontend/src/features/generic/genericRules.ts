import type { ConnectionInput, GenericConnection, GenericTemplate } from './genericApi'

export const ENTITIES: Record<'IMPORT' | 'EXPORT', { value: string; label: string }[]> = {
  IMPORT: [{ value: 'orders', label: 'Orders' }, { value: 'inventory', label: 'Stock counts' }],
  EXPORT: [{ value: 'orders', label: 'Orders' }, { value: 'shipments', label: 'Shipments and tracking' }, { value: 'inventory', label: 'Stock' }],
}

export function pretty(json: string | Record<string, unknown>): string {
  try {
    return JSON.stringify(typeof json === 'string' ? JSON.parse(json) : json, null, 2)
  } catch {
    return typeof json === 'string' ? json : ''
  }
}

/** Problems the form can catch before the server does. The server checks the mapping itself. */
export function inputProblems(f: ConnectionInput): string[] {
  const p: string[] = []
  if (!f.name.trim()) p.push('Give the connection a name')
  if (!ENTITIES[f.direction].some(e => e.value === f.entityType)) p.push('Choose what to ' + (f.direction === 'IMPORT' ? 'import' : 'export'))
  try {
    const parsed = JSON.parse(f.config)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) p.push('The configuration must be a JSON object')
  } catch (e) {
    p.push('The configuration is not valid JSON: ' + (e as Error).message)
  }
  return p
}

export function fromTemplate(t: GenericTemplate): ConnectionInput {
  return { name: t.label, direction: t.direction, entityType: t.entityType, channel: t.channel, config: pretty(t.config), secret: '' }
}

export function fromConnection(c: GenericConnection): ConnectionInput {
  return { name: c.name, direction: c.direction, entityType: c.entityType, channel: c.channel, config: pretty(c.config), secret: '' }
}

export function blankInput(): ConnectionInput {
  return { name: '', direction: 'IMPORT', entityType: 'orders', channel: '', config: '{\n  "request": { "baseUrl": "https://", "path": "/" },\n  "fields": {}\n}', secret: '' }
}

export function describe(c: GenericConnection): string {
  const what = ENTITIES[c.direction].find(e => e.value === c.entityType)?.label ?? c.entityType
  return (c.direction === 'IMPORT' ? 'Imports ' : 'Exports ') + what.toLowerCase() + (c.channel ? ' · channel ' + c.channel : '')
}

export function sinceText(c: GenericConnection): string {
  if (!c.watermark) return 'From the beginning'
  return 'Since ' + new Date(c.watermark + (c.watermark.endsWith('Z') ? '' : 'Z')).toLocaleString()
}
