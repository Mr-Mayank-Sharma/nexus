import client from './client'
import { ApiResponse } from '../types'

export interface Warehouse {
  id: string
  name: string
  code: string
  address: {
    street: string
    city: string
    state: string
    zip: string
    country: string
  }
  status: string
  capacity: number
  utilizedCapacity: number
  managerName: string
  contactEmail: string
  contactPhone: string
  timezone: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface WarehouseZone {
  id: string
  warehouseId: string
  name: string
  code: string
  type: 'RECEIVING' | 'PICKING' | 'PACKING' | 'STORAGE' | 'SHIPPING'
  capacity: number
  utilizedCapacity: number
  isActive: boolean
  createdAt: string
}

export interface WarehouseBin {
  id: string
  warehouseId: string
  zoneId: string
  code: string
  aisle: string
  rack: string
  shelf: string
  position: string
  type: string
  width: number
  height: number
  depth: number
  maxWeight: number
  currentWeight: number
  status: 'EMPTY' | 'OCCUPIED' | 'RESERVED' | 'BLOCKED'
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface WarehouseStaff {
  id: string
  warehouseId: string
  userId: string
  name: string
  email: string
  role: string
  shift: string
  isActive: boolean
  pickCount: number
  createdAt: string
}

export interface WarehouseEquipment {
  id: string
  warehouseId: string
  name: string
  type: 'FORKLIFT' | 'PALLET_JACK' | 'CONVEYOR' | 'SCANNER' | 'DOLLY' | 'OTHER'
  status: 'AVAILABLE' | 'IN_USE' | 'MAINTENANCE' | 'OUT_OF_SERVICE'
  model: string
  serialNumber: string
  lastMaintenanceDate: string
  nextMaintenanceDate: string
  isActive: boolean
  createdAt: string
}

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
