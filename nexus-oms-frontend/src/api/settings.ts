import client from './client'
import { ApiResponse } from '../types'

export interface CompanySettings {
  id: string
  companyName: string
  legalName: string
  taxId: string
  registrationNumber: string
  logo: string
  address: {
    street: string
    city: string
    state: string
    zip: string
    country: string
  }
  phone: string
  email: string
  website: string
  timezone: string
  currency: string
  dateFormat: string
  languages: string[]
  featureFlags: Record<string, boolean>
  securityPolicy: {
    passwordMinLength: number
    requireSpecialChars: boolean
    requireNumbers: boolean
    requireUppercase: boolean
    maxLoginAttempts: number
    sessionTimeoutMinutes: number
    twoFactorRequired: boolean
    allowedIpRanges: string[]
  }
  createdAt: string
  updatedAt: string
}

export async function getCompanySettings(): Promise<ApiResponse<CompanySettings>> {
  try {
    const { data } = await client.get('/settings/company')
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch company settings')
  }
}

export async function updateCompanySettings(settingsData: Record<string, any>): Promise<ApiResponse<CompanySettings>> {
  try {
    const { data } = await client.put('/settings/company', settingsData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update company settings')
  }
}

export async function updateFeatureFlags(featureFlags: Record<string, boolean>): Promise<ApiResponse<CompanySettings>> {
  try {
    const { data } = await client.put('/settings/feature-flags', { featureFlags })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update feature flags')
  }
}

export async function getFeatureFlag(key: string): Promise<ApiResponse<{ key: string; enabled: boolean }>> {
  try {
    const { data } = await client.get(`/settings/feature-flags/${key}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch feature flag')
  }
}

export async function updateSecurityPolicy(policyData: Record<string, any>): Promise<ApiResponse<CompanySettings>> {
  try {
    const { data } = await client.put('/settings/security-policy', policyData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update security policy')
  }
}

export async function isFeatureEnabled(key: string): Promise<ApiResponse<{ enabled: boolean }>> {
  try {
    const { data } = await client.get(`/settings/feature-flags/${key}/enabled`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to check feature flag')
  }
}
