import client from './client'
import { ConnectorMetadata, ConnectorInstance, BatchJob } from '../types'
export type { ConnectorMetadata, ConnectorInstance, BatchJob }

export const integrationHub = {
  async getPlatforms() {
    try {
      const { data } = await client.get('/integration-hub/platforms')
      return data as ConnectorMetadata[]
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to get platforms'
      return { success: false, error: msg } as any
    }
  },
  async getConnectors() {
    try {
      const { data } = await client.get('/integration-hub/connectors')
      return data as ConnectorInstance[]
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to get connectors'
      return { success: false, error: msg } as any
    }
  },
  async getConnector(id: string) {
    try {
      const { data } = await client.get(`/integration-hub/connectors/${id}`)
      return data
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to get connector'
      return { success: false, error: msg } as any
    }
  },
  async createConnector(config: Record<string, any>) {
    try {
      const { data } = await client.post('/integration-hub/connectors', config)
      return data
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to create connector'
      return { success: false, error: msg } as any
    }
  },
  async deleteConnector(id: string) {
    try {
      await client.delete(`/integration-hub/connectors/${id}`)
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to delete connector'
      return { success: false, error: msg } as any
    }
  },
  async testConnection(id: string) {
    try {
      const { data } = await client.post(`/integration-hub/connectors/${id}/test`)
      return data
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to test connection'
      return { success: false, error: msg } as any
    }
  },
  async runSync(id: string, syncType: string, params?: Record<string, any>) {
    try {
      const { data } = await client.post(`/integration-hub/connectors/${id}/sync/${syncType}`, params || {})
      return data as BatchJob
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to run sync'
      return { success: false, error: msg } as any
    }
  },
  async registerWebhooks(id: string, baseUrl?: string) {
    try {
      const { data } = await client.post(`/integration-hub/connectors/${id}/webhooks`, null, {
        params: { baseUrl: baseUrl || window.location.origin }
      })
      return data
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to register webhooks'
      return { success: false, error: msg } as any
    }
  },
  async getJobs(connectorId?: string) {
    try {
      const { data } = await client.get('/integration-hub/jobs', { params: { connectorId } })
      return data as BatchJob[]
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to get jobs'
      return { success: false, error: msg } as any
    }
  },
  async getJob(jobId: string) {
    try {
      const { data } = await client.get(`/integration-hub/jobs/${jobId}`)
      return data as BatchJob
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to get job'
      return { success: false, error: msg } as any
    }
  },
}
