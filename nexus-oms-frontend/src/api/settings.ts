import client from './client'
import { ApiResponse, CompanySettings } from '../types'
export type { CompanySettings }

export async function getCompanySettings(): Promise<ApiResponse<CompanySettings>> {
  try {
    const { data } = await client.get('/settings')
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch company settings')
  }
}

export async function updateCompanySettings(settingsData: Record<string, any>): Promise<ApiResponse<CompanySettings>> {
  try {
    const { data } = await client.put('/settings', settingsData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update company settings')
  }
}

export async function updateFeatureFlags(featureFlags: Record<string, boolean>): Promise<ApiResponse<CompanySettings>> {
  try {
    const { data } = await client.put('/settings', { featureFlags })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update feature flags')
  }
}

export async function getFeatureFlag(key: string): Promise<ApiResponse<{ key: string; enabled: boolean }>> {
  try {
    const { data } = await client.get('/settings', { params: { key } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch feature flag')
  }
}

export async function updateSecurityPolicy(policyData: Record<string, any>): Promise<ApiResponse<CompanySettings>> {
  try {
    const { data } = await client.put('/settings', policyData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update security policy')
  }
}

export async function isFeatureEnabled(key: string): Promise<ApiResponse<{ enabled: boolean }>> {
  try {
    const { data } = await client.get('/settings', { params: { key } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to check feature flag')
  }
}
