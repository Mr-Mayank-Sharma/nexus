import { useQuery } from '@tanstack/react-query'
import client from '../../api/client'
import { useAuth } from '../../context/AuthContext'
import type { ProductModule } from './modules'

export interface Entitlements {
  edition: 'OMS' | 'WMS' | 'SUITE'
  modules: ProductModule[]
  validUntil: string | null
  expired: boolean
}

async function fetchEntitlements(): Promise<Entitlements> {
  const { data } = await client.get('/entitlements')
  return data.data as Entitlements
}

/**
 * The organisation's plan. Until it loads (or if the call fails) nothing is hidden: the server
 * still refuses unlicensed calls, so hiding is a convenience, never the enforcement.
 */
export function useEntitlements() {
  const { isAuthenticated } = useAuth()
  const query = useQuery({ queryKey: ['entitlements'], queryFn: fetchEntitlements, enabled: isAuthenticated, staleTime: 5 * 60_000 })
  const allows = (m: ProductModule) => m === 'PLATFORM' || !query.data || query.data.modules.includes(m)
  return { entitlements: query.data, allows }
}
