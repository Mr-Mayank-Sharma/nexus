import client from '../../api/client'

export type Direction = 'IMPORT' | 'EXPORT'

export interface GenericConnection {
  id: string
  name: string
  direction: Direction
  entityType: string
  channel: string | null
  config: string
  watermark: string | null
  hasSecret: boolean
  createdAt: string
  updatedAt: string
}

export interface GenericTemplate {
  key: string
  label: string
  direction: Direction
  entityType: string
  channel: string | null
  note: string
  config: Record<string, unknown>
}

export interface PreviewRecord { ref: string; source: unknown; mapped: Record<string, string> | null; lines: Record<string, string>[] | null; problems: string[] }
export interface Preview {
  direction: Direction
  entityType: string
  pages: { number: number; url: string; records: number }[]
  fetched: number
  valid: number
  records: PreviewRecord[]
  payload: unknown
  csv: string | null
  note: string
}

export interface ConnectionInput { name: string; direction: Direction; entityType: string; channel: string | null; config: string; secret: string }

export interface FieldGuide { export: Record<string, string[]>; import: Record<string, string[]>; transforms: string[] }

export async function getConnections(): Promise<GenericConnection[]> {
  return (await client.get('/generic-connections')).data?.data ?? []
}
export async function getTemplates(): Promise<GenericTemplate[]> {
  return (await client.get('/generic-connections/templates')).data?.data ?? []
}
export async function getFieldGuide(): Promise<FieldGuide> {
  return (await client.get('/generic-connections/fields')).data?.data
}
export async function createConnection(input: ConnectionInput): Promise<GenericConnection> {
  return (await client.post('/generic-connections', input)).data.data
}
export async function updateConnection(id: string, input: ConnectionInput): Promise<GenericConnection> {
  return (await client.put(`/generic-connections/${id}`, input)).data.data
}
export async function deleteConnection(id: string): Promise<void> {
  await client.delete(`/generic-connections/${id}`)
}
export async function previewConnection(id: string): Promise<Preview> {
  return (await client.post(`/generic-connections/${id}/preview`)).data.data
}
export async function resetWatermark(id: string): Promise<GenericConnection> {
  return (await client.post(`/generic-connections/${id}/reset-watermark`)).data.data
}
