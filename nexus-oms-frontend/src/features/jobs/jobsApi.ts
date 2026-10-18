import client from '../../api/client'

export type RunStatus = 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'
export type ParamKind = 'STORE' | 'BOOLEAN' | 'TEXT'

export interface JobParam { name: string; label: string; kind: ParamKind; required: boolean; help: string }
export interface JobType { type: string; label: string; description: string; params: JobParam[] }

export interface Job {
  id: string
  name: string
  type: string
  typeLabel: string
  params: Record<string, unknown>
  cron: string | null
  timezone: string
  enabled: boolean
  nextRunAt: string | null
  lastRunAt: string | null
  lastStatus: RunStatus | null
  createdBy: string | null
}

export interface JobRun {
  id: string
  jobId: string
  status: RunStatus
  triggerKind: 'MANUAL' | 'SCHEDULE'
  triggeredBy: string | null
  startedAt: string
  finishedAt: string | null
  total: number
  succeeded: number
  failed: number
  issueCount: number
  message: string | null
  outputName: string | null
  hasOutput: boolean
}

export interface JobIssue { id: string; rowRef: string | null; reason: string; raw: string | null }
export interface JobInput { name: string; type: string; params: Record<string, unknown>; cron: string | null; timezone: string }

export async function getJobTypes(): Promise<JobType[]> {
  return (await client.get('/jobs/types')).data?.data ?? []
}
export async function getJobs(): Promise<Job[]> {
  return (await client.get('/jobs')).data?.data ?? []
}
export async function createJob(input: JobInput): Promise<Job> {
  return (await client.post('/jobs', input)).data.data
}
export async function updateJob(id: string, input: JobInput): Promise<Job> {
  return (await client.put(`/jobs/${id}`, input)).data.data
}
export async function deleteJob(id: string): Promise<void> {
  await client.delete(`/jobs/${id}`)
}
export async function setJobEnabled(id: string, enabled: boolean): Promise<Job> {
  return (await client.post(`/jobs/${id}/enable`, null, { params: { enabled } })).data.data
}
export async function runJob(id: string): Promise<JobRun> {
  return (await client.post(`/jobs/${id}/run`)).data.data
}
export async function getRuns(jobId: string): Promise<JobRun[]> {
  return (await client.get(`/jobs/${jobId}/runs`, { params: { limit: 30 } })).data?.data ?? []
}
export async function getRun(runId: string): Promise<JobRun> {
  return (await client.get(`/jobs/runs/${runId}`)).data.data
}
export async function getRunLog(runId: string): Promise<string> {
  return (await client.get(`/jobs/runs/${runId}/log`, { responseType: 'text' })).data ?? ''
}
export async function getRunIssues(runId: string): Promise<JobIssue[]> {
  return (await client.get(`/jobs/runs/${runId}/issues`, { params: { limit: 200 } })).data?.data ?? []
}
export async function downloadRunFile(runId: string, which: 'errors' | 'output'): Promise<Blob> {
  return (await client.get(`/jobs/runs/${runId}/${which}.csv`, { responseType: 'blob' })).data
}
export async function getStoreConnections(): Promise<{ id: string; storeName: string; platform: string }[]> {
  const d = (await client.get('/integration-stores')).data?.data
  return Array.isArray(d) ? d : d?.content ?? []
}
