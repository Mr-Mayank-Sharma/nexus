import { useCallback, useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getWarehouses } from '../api/warehouse'
import type { Warehouse } from '../types'

const STORAGE_KEY = 'nexus_active_warehouse'

function remembered(): string {
  try { return localStorage.getItem(STORAGE_KEY) ?? '' } catch { return '' }
}

/**
 * The warehouse a warehouse-scoped screen is working on.
 *
 * It comes from the signed-in organisation's own warehouses, never a constant: several screens
 * used to hard-code one tenant's warehouse id, so for everyone else they queried a warehouse that
 * did not exist. The choice is remembered per browser and falls back to the first warehouse if the
 * remembered one is gone. `warehouseId` is '' until warehouses have loaded (or if there are none),
 * so callers must not fetch while it is empty.
 */
export function useActiveWarehouse() {
  const query = useQuery({
    queryKey: ['warehouses-all'],
    queryFn: async (): Promise<Warehouse[]> => (await getWarehouses(0, 200)).data?.content ?? [],
    staleTime: 5 * 60_000,
  })
  const warehouses = useMemo(() => query.data ?? [], [query.data])
  const [chosen, setChosen] = useState(remembered)

  const warehouseId = warehouses.some(w => w.id === chosen) ? chosen : (warehouses[0]?.id ?? '')

  useEffect(() => {
    if (warehouseId && warehouseId !== chosen) setChosen(warehouseId)
  }, [warehouseId, chosen])

  const setWarehouseId = useCallback((id: string) => {
    setChosen(id)
    try { localStorage.setItem(STORAGE_KEY, id) } catch { /* private mode: selection just isn't remembered */ }
  }, [])

  return {
    warehouses,
    warehouseId,
    setWarehouseId,
    warehouse: warehouses.find(w => w.id === warehouseId),
    isLoading: query.isLoading,
    isError: query.isError,
    hasWarehouses: warehouses.length > 0,
  }
}
