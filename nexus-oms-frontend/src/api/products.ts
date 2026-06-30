import client from './client'
import { ApiResponse, Product } from '../types'
export type { Product }

export async function getProducts(): Promise<ApiResponse<Product[]>> {
  try {
    const { data } = await client.get('/products')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get products'
    return { success: false, error: msg } as any
  }
}

export async function getProduct(id: string): Promise<ApiResponse<Product>> {
  try {
    const { data } = await client.get(`/products/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get product'
    return { success: false, error: msg } as any
  }
}

export async function createProduct(payload: Partial<Product>): Promise<ApiResponse<Product>> {
  try {
    const { data } = await client.post('/products', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create product'
    return { success: false, error: msg } as any
  }
}

export async function updateProduct(id: string, payload: Partial<Product>): Promise<ApiResponse<Product>> {
  try {
    const { data } = await client.put(`/products/${id}`, payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update product'
    return { success: false, error: msg } as any
  }
}

export async function deleteProduct(id: string): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.delete(`/products/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to delete product'
    return { success: false, error: msg } as any
  }
}
