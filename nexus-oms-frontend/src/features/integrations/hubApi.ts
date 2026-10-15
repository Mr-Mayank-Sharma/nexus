import client from '../../api/client'

/** Unlike the older `api/integrationHub.ts`, nothing here swallows an error: a failed call throws and the screen says so. */

export interface Platform {
  name: string
  platformType: string
  category: string
  description?: string
  supportedSyncTypes: string[]
  previewSyncTypes: string[]
  requiredSettings: string[]
  defaultSettings?: Record<string, string>
}

export type Verification = 'VERIFIED' | 'FAILING' | 'UNTESTED'

export interface Connection {
  id: string
  platform: string
  name: string
  category: string
  supportedSyncTypes: string[]
  verification: Verification
  lastTestAt?: string
  lastTestOk?: boolean
  lastTestMessage?: string
}

export interface HubJob {
  jobId: string
  connectorId: string
  syncType: string
  status: string
  itemsSucceeded: number
  itemsFailed: number
  message?: string | null
  error?: string | null
  startedAt: string
}

export interface TestOutcome { connectionVerified?: boolean; success: boolean; message: string }

export const getPlatforms = async (): Promise<Platform[]> => (await client.get('/integration-hub/platforms')).data
export const getConnections = async (): Promise<Connection[]> => (await client.get('/integration-hub/connectors')).data
export const getJobs = async (): Promise<HubJob[]> => (await client.get('/integration-hub/jobs')).data

export async function createConnection(platformType: string, storeName: string, settings: Record<string, string>, credentials: Record<string, string>):
  Promise<{ id: string; saved: boolean } & TestOutcome> {
  return (await client.post('/integration-hub/connectors', { platformType, storeName, settings, credentials })).data
}

export const testConnection = async (id: string): Promise<TestOutcome> => (await client.post(`/integration-hub/connectors/${id}/test`)).data
export const removeConnection = async (id: string) => { await client.delete(`/integration-hub/connectors/${id}`) }
export const runOperation = async (id: string, syncType: string, params?: Record<string, unknown>): Promise<HubJob> =>
  (await client.post(`/integration-hub/connectors/${id}/sync/${syncType}`, params ?? {})).data
