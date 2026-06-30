import client from './client'
import { ConnectorMetadata, ConnectorInstance, BatchJob } from '../types'
export type { ConnectorMetadata, ConnectorInstance, BatchJob }

export const integrationHub = {
  async getPlatforms() {
    const { data } = await client.get('/integration-hub/platforms')
    return data as ConnectorMetadata[]
  },
  async getConnectors() {
    const { data } = await client.get('/integration-hub/connectors')
    return data as ConnectorInstance[]
  },
  async getConnector(id: string) {
    const { data } = await client.get(`/integration-hub/connectors/${id}`)
    return data
  },
  async createConnector(config: Record<string, any>) {
    const { data } = await client.post('/integration-hub/connectors', config)
    return data
  },
  async deleteConnector(id: string) {
    await client.delete(`/integration-hub/connectors/${id}`)
  },
  async testConnection(id: string) {
    const { data } = await client.post(`/integration-hub/connectors/${id}/test`)
    return data
  },
  async runSync(id: string, syncType: string, params?: Record<string, any>) {
    const { data } = await client.post(`/integration-hub/connectors/${id}/sync/${syncType}`, params || {})
    return data as BatchJob
  },
  async registerWebhooks(id: string, baseUrl?: string) {
    const { data } = await client.post(`/integration-hub/connectors/${id}/webhooks`, null, {
      params: { baseUrl: baseUrl || window.location.origin }
    })
    return data
  },
  async getJobs(connectorId?: string) {
    const { data } = await client.get('/integration-hub/jobs', { params: { connectorId } })
    return data as BatchJob[]
  },
  async getJob(jobId: string) {
    const { data } = await client.get(`/integration-hub/jobs/${jobId}`)
    return data as BatchJob
  },
}
