import client from './client'
import { ApiResponse } from '../types'

export interface Customer {
  id: string
  tenantId: string
  externalId?: string
  name: string
  email?: string
  phone?: string
  address?: string
  createdAt: string
}

export async function getCustomers(): Promise<ApiResponse<Customer[]>> {
  const { data } = await client.get('/customers')
  return data
}

export async function getCustomer(id: string): Promise<ApiResponse<Customer>> {
  const { data } = await client.get(`/customers/${id}`)
  return data
}

export async function createCustomer(payload: Partial<Customer>): Promise<ApiResponse<Customer>> {
  const { data } = await client.post('/customers', payload)
  return data
}

export async function updateCustomer(id: string, payload: Partial<Customer>): Promise<ApiResponse<Customer>> {
  const { data } = await client.put(`/customers/${id}`, payload)
  return data
}

export async function deleteCustomer(id: string): Promise<ApiResponse<null>> {
  const { data } = await client.delete(`/customers/${id}`)
  return data
}
