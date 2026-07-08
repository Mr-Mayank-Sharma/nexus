import client from './client'
import { ApiResponse, Workflow, WorkflowStep, WorkflowExecution } from '../types'
export type { Workflow, WorkflowStep, WorkflowExecution }

export async function getWorkflows(page: number, size: number): Promise<ApiResponse<{ content: Workflow[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/workflows', { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get workflows'
    return { success: false, error: msg } as any
  }
}

export async function getWorkflow(id: string): Promise<ApiResponse<Workflow>> {
  try {
    const { data } = await client.get(`/workflows/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get workflow'
    return { success: false, error: msg } as any
  }
}

export async function createWorkflow(workflowData: Record<string, any>): Promise<ApiResponse<Workflow>> {
  try {
    const { data } = await client.post('/workflows', workflowData)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create workflow'
    return { success: false, error: msg } as any
  }
}

export async function updateWorkflowStatus(id: string, status: string): Promise<ApiResponse<Workflow>> {
  try {
    const { data } = await client.put(`/workflows/${id}/status`, { status })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update workflow status'
    return { success: false, error: msg } as any
  }
}

export async function toggleWorkflow(id: string, active: boolean): Promise<ApiResponse<Workflow>> {
  try {
    const { data } = await client.post(`/workflows/${id}/toggle`, { active })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to toggle workflow'
    return { success: false, error: msg } as any
  }
}

export async function getWorkflowSteps(workflowId: string): Promise<ApiResponse<WorkflowStep[]>> {
  try {
    const { data } = await client.get(`/workflows/${workflowId}/steps`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get workflow steps'
    return { success: false, error: msg } as any
  }
}

export async function executeWorkflow(workflowId: string, input?: Record<string, any>): Promise<ApiResponse<WorkflowExecution>> {
  try {
    const { data } = await client.post(`/workflows/${workflowId}/execute`, input || {})
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to execute workflow'
    return { success: false, error: msg } as any
  }
}

export async function getExecutions(workflowId: string, page: number, size: number): Promise<ApiResponse<{ content: WorkflowExecution[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get(`/workflows/${workflowId}/executions`, { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get executions'
    return { success: false, error: msg } as any
  }
}
