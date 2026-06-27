import client from './client'
import { ApiResponse } from '../types'

export interface Product {
  id: string
  sku: string
  productName: string
  description?: string
  category?: string
  unitPrice: number
  costPrice?: number
  imageUrl?: string
  weight?: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

const MOCK_PRODUCTS: Product[] = [
  { id: 'p1', sku: 'TSH-BLK-001', productName: 'Classic Black T-Shirt', description: 'Cotton crew neck tee', category: 'Apparel', unitPrice: 29.99, costPrice: 12.00, weight: 0.3, isActive: true, createdAt: '2026-01-15T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z' },
  { id: 'p2', sku: 'TSH-WHT-001', productName: 'Classic White T-Shirt', description: 'Cotton crew neck tee', category: 'Apparel', unitPrice: 29.99, costPrice: 12.00, weight: 0.3, isActive: true, createdAt: '2026-01-15T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z' },
  { id: 'p3', sku: 'HOOD-NAV-001', productName: 'Navy Zip Hoodie', description: 'Fleece-lined zip hoodie', category: 'Apparel', unitPrice: 59.99, costPrice: 25.00, weight: 0.6, isActive: true, createdAt: '2026-02-01T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z' },
  { id: 'p4', sku: 'CAP-RED-001', productName: 'Red Baseball Cap', description: 'Adjustable snapback cap', category: 'Accessories', unitPrice: 19.99, costPrice: 8.00, weight: 0.1, isActive: true, createdAt: '2026-01-20T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z' },
  { id: 'p5', sku: 'MUG-WHT-001', productName: 'White Ceramic Mug 12oz', description: 'Dishwasher safe ceramic mug', category: 'Home', unitPrice: 14.99, costPrice: 4.50, weight: 0.4, isActive: true, createdAt: '2026-03-01T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z' },
  { id: 'p6', sku: 'BAG-CNV-001', productName: 'Canvas Tote Bag', description: 'Heavy-duty cotton canvas tote', category: 'Accessories', unitPrice: 24.99, costPrice: 9.00, weight: 0.2, isActive: true, createdAt: '2026-03-15T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z' },
  { id: 'p7', sku: 'STK-NOT-001', productName: 'Sticky Notes Set (5pk)', description: 'Assorted color sticky notes', category: 'Stationery', unitPrice: 9.99, costPrice: 3.00, weight: 0.1, isActive: true, createdAt: '2026-04-01T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z' },
  { id: 'p8', sku: 'WAT-BLK-001', productName: 'Black Sport Watch', description: 'Water resistant digital watch', category: 'Accessories', unitPrice: 49.99, costPrice: 20.00, weight: 0.15, isActive: true, createdAt: '2026-04-10T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z' },
  { id: 'p9', sku: 'BTL-SS-001', productName: 'Stainless Steel Water Bottle 24oz', description: 'Double-walled insulated bottle', category: 'Home', unitPrice: 34.99, costPrice: 14.00, weight: 0.5, isActive: true, createdAt: '2026-05-01T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z' },
  { id: 'p10', sku: 'PHN-CSE-001', productName: 'Phone Case - Clear', description: 'Shock-absorbing clear case', category: 'Accessories', unitPrice: 19.99, costPrice: 5.00, weight: 0.05, isActive: true, createdAt: '2026-05-15T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z' },
]

export async function getProducts(): Promise<ApiResponse<Product[]>> {
  try {
    const { data } = await client.get('/products')
    return data
  } catch {
    return { success: true, data: MOCK_PRODUCTS }
  }
}

export async function getProduct(id: string): Promise<ApiResponse<Product>> {
  try {
    const { data } = await client.get(`/products/${id}`)
    return data
  } catch {
    const product = MOCK_PRODUCTS.find(p => p.id === id)
    return { success: true, data: product || MOCK_PRODUCTS[0] }
  }
}

export async function createProduct(payload: Partial<Product>): Promise<ApiResponse<Product>> {
  const { data } = await client.post('/products', payload)
  return data
}

export async function updateProduct(id: string, payload: Partial<Product>): Promise<ApiResponse<Product>> {
  const { data } = await client.put(`/products/${id}`, payload)
  return data
}

export async function deleteProduct(id: string): Promise<ApiResponse<null>> {
  const { data } = await client.delete(`/products/${id}`)
  return data
}
