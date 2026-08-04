import client from './client'
import { ApiResponse, WarehouseStaff, Warehouse, WarehouseZone, WarehouseBin, WarehouseEquipment } from '../types'
export type { Warehouse, WarehouseZone, WarehouseBin, WarehouseEquipment }

export async function getWarehouses(page: number, size: number): Promise<ApiResponse<{ content: Warehouse[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/warehouses', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch warehouses')
  }
}

export async function getWarehouse(id: string): Promise<ApiResponse<Warehouse>> {
  try {
    const { data } = await client.get(`/warehouses/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch warehouse')
  }
}

export async function createWarehouse(warehouseData: Record<string, any>): Promise<ApiResponse<Warehouse>> {
  try {
    const { data } = await client.post('/warehouses', warehouseData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create warehouse')
  }
}

export async function updateWarehouse(id: string, warehouseData: Record<string, any>): Promise<ApiResponse<Warehouse>> {
  try {
    const { data } = await client.put(`/warehouses/${id}`, warehouseData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update warehouse')
  }
}

export async function deleteWarehouse(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.delete(`/warehouses/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to delete warehouse')
  }
}

export async function getZones(warehouseId: string): Promise<ApiResponse<WarehouseZone[]>> {
  try {
    const { data } = await client.get(`/warehouses/${warehouseId}/zones`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch zones')
  }
}

export async function createZone(zoneData: Record<string, any>): Promise<ApiResponse<WarehouseZone>> {
  try {
    const { data } = await client.post('/warehouses/zones', zoneData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create zone')
  }
}

export async function getBins(warehouseId: string): Promise<ApiResponse<WarehouseBin[]>> {
  try {
    const { data } = await client.get(`/warehouses/${warehouseId}/bins`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch bins')
  }
}

export async function getEmptyBins(warehouseId: string): Promise<ApiResponse<WarehouseBin[]>> {
  try {
    const { data } = await client.get(`/warehouses/${warehouseId}/bins/empty`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch empty bins')
  }
}

export async function createBin(binData: Record<string, any>): Promise<ApiResponse<WarehouseBin>> {
  try {
    const { data } = await client.post('/warehouses/bins', binData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create bin')
  }
}

export async function reserveBin(id: string): Promise<ApiResponse<WarehouseBin>> {
  try {
    const { data } = await client.put(`/warehouses/bins/${id}/reserve`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to reserve bin')
  }
}

export async function releaseBin(id: string): Promise<ApiResponse<WarehouseBin>> {
  try {
    const { data } = await client.put(`/warehouses/bins/${id}/release`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to release bin')
  }
}

export async function getStaff(warehouseId: string): Promise<ApiResponse<WarehouseStaff[]>> {
  try {
    const { data } = await client.get(`/warehouses/${warehouseId}/staff`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch staff')
  }
}

export async function createStaff(staffData: Record<string, any>): Promise<ApiResponse<WarehouseStaff>> {
  try {
    const { data } = await client.post('/warehouses/staff', staffData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create staff')
  }
}

export async function incrementPickCount(id: string): Promise<ApiResponse<WarehouseStaff>> {
  try {
    const { data } = await client.put(`/warehouses/staff/${id}/increment-picks`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to increment pick count')
  }
}

export async function getEquipment(warehouseId: string): Promise<ApiResponse<WarehouseEquipment[]>> {
  try {
    const { data } = await client.get(`/warehouses/${warehouseId}/equipment`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch equipment')
  }
}

export async function createEquipment(equipmentData: Record<string, any>): Promise<ApiResponse<WarehouseEquipment>> {
  try {
    const { data } = await client.post('/warehouses/equipment', equipmentData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create equipment')
  }
}

export async function updateEquipmentStatus(id: string, status: string): Promise<ApiResponse<WarehouseEquipment>> {
  try {
    const { data } = await client.put(`/warehouses/equipment/${id}/status`, { status })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update equipment status')
  }
}

export async function getWarehouseSummary(warehouseId: string): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.get(`/warehouses/${warehouseId}/summary`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch warehouse summary')
  }
}
