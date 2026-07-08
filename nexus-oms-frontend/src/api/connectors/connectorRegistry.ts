import type { Marketplace, ConnectorConfig } from './types'
import { getConnectorStatus, configureConnector } from './connectorClient'

export interface ConnectorState {
  amazon: ConnectorConfig
  ebay: ConnectorConfig
  walmart: ConnectorConfig
  bigcommerce: ConnectorConfig
}

let cachedStatus: ConnectorState | null = null
let lastFetch = 0

export async function fetchAllStatus(signal?: AbortSignal, force = false): Promise<ConnectorState> {
  const now = Date.now()
  if (cachedStatus && !force && now - lastFetch < 30000) return cachedStatus!
  try {
    const res = await getConnectorStatus(signal)
    if (res.success) {
      cachedStatus = res.connectors as ConnectorState
      lastFetch = now
      return cachedStatus!
    }
  } catch {
    // fallback to defaults
  }
  return getDefaultStatus()
}

function getDefaultStatus(): ConnectorState {
  return {
    amazon: { active: false, sandbox: true, lastSync: null, ordersSynced: 0, error: null, hasCredentials: false },
    ebay: { active: false, sandbox: true, lastSync: null, ordersSynced: 0, error: null, hasCredentials: false },
    walmart: { active: false, sandbox: true, lastSync: null, ordersSynced: 0, error: null, hasCredentials: false },
    bigcommerce: { active: false, sandbox: true, lastSync: null, ordersSynced: 0, error: null, hasCredentials: false },
  }
}

export function getStatus(): ConnectorState {
  return cachedStatus || getDefaultStatus()
}

export async function saveConfig(marketplace: Marketplace, config: Record<string, unknown>, signal?: AbortSignal) {
  const res = await configureConnector(marketplace, config, signal)
  if (res.success) {
    cachedStatus = null
  }
  return res
}

export function getConnectorLabel(marketplace: Marketplace): string {
  const map: Record<Marketplace, string> = {
    amazon: 'Amazon',
    ebay: 'eBay',
    walmart: 'Walmart',
    bigcommerce: 'BigCommerce',
  }
  return map[marketplace]
}
