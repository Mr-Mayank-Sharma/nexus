import client from './client'
import { ApiResponse, RoutingRule } from '../types'

export async function getRules(): Promise<ApiResponse<RoutingRule[]>> {
  try {
    const { data } = await client.get('/routing-rules')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get rules'
    return { success: false, error: msg } as any
  }
}

export async function getRule(id: string): Promise<ApiResponse<RoutingRule>> {
  try {
    const { data } = await client.get(`/routing-rules/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get rule'
    return { success: false, error: msg } as any
  }
}

export async function createRule(rule: Partial<RoutingRule>): Promise<ApiResponse<RoutingRule>> {
  try {
    const { data } = await client.post('/routing-rules', rule)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create rule'
    return { success: false, error: msg } as any
  }
}

export async function updateRule(id: string, rule: Partial<RoutingRule>): Promise<ApiResponse<RoutingRule>> {
  try {
    const { data } = await client.put(`/routing-rules/${id}`, rule)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update rule'
    return { success: false, error: msg } as any
  }
}

export async function deleteRule(id: string): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.delete(`/routing-rules/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to delete rule'
    return { success: false, error: msg } as any
  }
}

export async function reorderRules(ruleIds: string[]): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.put('/routing-rules/reorder', ruleIds)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to reorder rules'
    return { success: false, error: msg } as any
  }
}
