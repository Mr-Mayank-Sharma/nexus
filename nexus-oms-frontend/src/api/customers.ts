import client from './client'
import { ApiResponse, Customer } from '../types'
export type { Customer }

export async function getCustomers(): Promise<ApiResponse<Customer[]>> {
  try {
    const { data } = await client.get('/customers')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get customers'
    return { success: false, error: msg } as any
  }
}

export async function getCustomer(id: string): Promise<ApiResponse<Customer>> {
  try {
    const { data } = await client.get(`/customers/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get customer'
    return { success: false, error: msg } as any
  }
}

export async function createCustomer(payload: Partial<Customer>): Promise<ApiResponse<Customer>> {
  try {
    const { data } = await client.post('/customers', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create customer'
    return { success: false, error: msg } as any
  }
}

export async function updateCustomer(id: string, payload: Partial<Customer>): Promise<ApiResponse<Customer>> {
  try {
    const { data } = await client.put(`/customers/${id}`, payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update customer'
    return { success: false, error: msg } as any
  }
}

export async function deleteCustomer(id: string): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.delete(`/customers/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to delete customer'
    return { success: false, error: msg } as any
  }
}
