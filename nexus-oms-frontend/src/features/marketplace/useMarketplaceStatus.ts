import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getConnectorStatus } from '../../api/connectors/connectorClient'

export type MarketplaceName = 'amazon' | 'ebay' | 'walmart'

export interface MarketplaceStatus {
  state: 'NOT_CONFIGURED' | 'CREDENTIALS_SAVED_UNVERIFIED'
  /** Always false today: nothing verifies a marketplace connection. */
  connected: boolean
  hasCredentials: boolean
  /** Whether a live sync with the marketplace exists. */
  liveSync: boolean
  /** Orders already in Nexus with this channel (not fetched from the marketplace). */
  ordersInNexus: number
  note?: string
}

const KEY = ['marketplace-status']

/** What the server really knows about a marketplace connection. */
export function useMarketplaceStatus(marketplace: MarketplaceName) {
  const qc = useQueryClient()
  const query = useQuery({
    queryKey: KEY,
    queryFn: async (): Promise<Record<string, MarketplaceStatus>> => (await getConnectorStatus()).connectors ?? {},
    staleTime: 15_000,
  })
  const status = query.data?.[marketplace]
  return {
    status,
    isLoading: query.isLoading,
    hasCredentials: !!status?.hasCredentials,
    liveSync: !!status?.liveSync,
    ordersInNexus: status?.ordersInNexus ?? 0,
    refresh: () => qc.invalidateQueries({ queryKey: KEY }),
  }
}
