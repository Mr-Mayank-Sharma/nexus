import client from './client'
import { ApiResponse, Workflow, WorkflowStep, WorkflowExecution } from '../types'
export type { Workflow, WorkflowStep, WorkflowExecution }

export async function getWorkflows(page: number, size: number): Promise<ApiResponse<{ content: Workflow[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/workflows', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch workflows')
  }
}

export async function getWorkflow(id: string): Promise<ApiResponse<Workflow>> {
  try {
    const { data } = await client.get(`/workflows/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch workflow')
  }
}

export async function createWorkflow(workflowData: Record<string, any>): Promise<ApiResponse<Workflow>> {
  try {
    const { data } = await client.post('/workflows', workflowData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create workflow')
  }
}

export async function updateWorkflowStatus(id: string, status: string): Promise<ApiResponse<Workflow>> {
  try {
    const { data } = await client.put(`/workflows/${id}/status`, null, { params: { status } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update workflow status')
  }
}

export async function toggleWorkflow(id: string, active: boolean): Promise<ApiResponse<Workflow>> {
  try {
    const { data } = await client.put(`/workflows/${id}/toggle`, null, { params: { active } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to toggle workflow')
  }
}

export async function getWorkflowSteps(workflowId: string): Promise<ApiResponse<WorkflowStep[]>> {
  try {
    const { data } = await client.get(`/workflows/${workflowId}/steps`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch workflow steps')
  }
}

export async function executeWorkflow(workflowId: string, input?: Record<string, any>): Promise<ApiResponse<WorkflowExecution>> {
  try {
    const { data } = await client.post(`/workflows/${workflowId}/execute`, input || {})
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to execute workflow')
  }
}

export async function getExecutions(workflowId: string, page: number, size: number): Promise<ApiResponse<{ content: WorkflowExecution[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get(`/workflows/${workflowId}/executions`, { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch workflow executions')
  }
}
