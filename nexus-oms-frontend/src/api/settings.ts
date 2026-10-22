import client from './client'
import { ApiResponse, CompanySettings } from '../types'
export type { CompanySettings }

/**
 * Company settings live at /settings/company (GET /settings is only a status check). The backend stores feature flags
 * and the security policy as JSON text and names some fields differently (defaultCurrency, defaultTimezone,
 * defaultLanguage); these functions translate both ways so the Settings page keeps its own field names.
 */

/** The stored record as the backend sends it. */
type StoredCompanySettings = Record<string, any> & {
  companyName?: string
  defaultCurrency?: string
  defaultTimezone?: string
  defaultLanguage?: string
  featureFlags?: string | Record<string, boolean> | null
  securityPolicy?: string | Record<string, any> | null
}

function parseJson<T>(value: unknown, fallback: T): T {
  if (value && typeof value === 'object') return value as T
  if (typeof value !== 'string' || value.trim() === '') return fallback
  try {
    return JSON.parse(value) as T
  } catch {
    return fallback
  }
}

async function readStored(): Promise<StoredCompanySettings> {
  const { data } = await client.get('/settings/company')
  return data.data ?? {}
}

function errorMessage(error: any, fallback: string): string {
  return error?.response?.data?.message || error?.message || fallback
}

export async function getCompanySettings(): Promise<ApiResponse<CompanySettings>> {
  try {
    const stored = await readStored()
    const settings = {
      ...stored,
      currency: stored.defaultCurrency,
      timezone: stored.defaultTimezone,
      languages: stored.defaultLanguage ? [stored.defaultLanguage] : [],
      featureFlags: parseJson<Record<string, boolean>>(stored.featureFlags, {}),
      securityPolicy: parseJson<Record<string, any>>(stored.securityPolicy, {}),
    } as unknown as CompanySettings
    return { success: true, data: settings } as ApiResponse<CompanySettings>
  } catch (error: any) {
    throw new Error(errorMessage(error, 'Failed to fetch company settings'))
  }
}

/** Saves the page's fields onto the stored record (the backend checks the whole record, so it is sent complete). */
export async function updateCompanySettings(settingsData: Record<string, any>): Promise<ApiResponse<CompanySettings>> {
  try {
    const stored = await readStored()
    const { currency, timezone, languages, featureFlags: _flags, securityPolicy: _policy, ...sameNamed } = settingsData
    const body = {
      ...stored,
      ...sameNamed,
      ...(currency ? { defaultCurrency: currency } : {}),
      ...(timezone ? { defaultTimezone: timezone } : {}),
      ...(languages?.[0] ? { defaultLanguage: languages[0] } : {}),
    }
    const { data } = await client.put('/settings/company', body)
    return data
  } catch (error: any) {
    throw new Error(errorMessage(error, 'Failed to update company settings'))
  }
}

export async function updateFeatureFlags(featureFlags: Record<string, boolean>): Promise<ApiResponse<CompanySettings>> {
  try {
    const { data } = await client.put('/settings/company/feature-flags', featureFlags)
    return data
  } catch (error: any) {
    throw new Error(errorMessage(error, 'Failed to update feature flags'))
  }
}

export async function getFeatureFlag(key: string): Promise<ApiResponse<{ key: string; enabled: boolean }>> {
  try {
    const { data } = await client.get(`/settings/company/feature-flags/${encodeURIComponent(key)}`)
    return { ...data, data: { key, enabled: Boolean(data.data) } }
  } catch (error: any) {
    throw new Error(errorMessage(error, 'Failed to fetch feature flag'))
  }
}

export async function updateSecurityPolicy(policyData: Record<string, any>): Promise<ApiResponse<CompanySettings>> {
  try {
    const { data } = await client.put('/settings/company/security-policy', policyData)
    return data
  } catch (error: any) {
    throw new Error(errorMessage(error, 'Failed to update security policy'))
  }
}

export async function isFeatureEnabled(key: string): Promise<ApiResponse<{ enabled: boolean }>> {
  try {
    const { data } = await client.get(`/settings/company/feature-flags/${encodeURIComponent(key)}/enabled`)
    return { ...data, data: { enabled: Boolean(data.data) } }
  } catch (error: any) {
    throw new Error(errorMessage(error, 'Failed to check feature flag'))
  }
}
