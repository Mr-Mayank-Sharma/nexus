import client from './client'
import { ApiResponse } from '../types'

export interface Supplier {
  id: string
  name: string
  code: string
  status: 'ACTIVE' | 'INACTIVE' | 'BLACKLISTED'
  category: string
  taxId: string
  paymentTerms: string
  currency: string
  primaryContactName: string
  primaryContactEmail: string
  primaryContactPhone: string
  address: {
    street: string
    city: string
    state: string
    zip: string
    country: string
  }
  rating: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface SupplierContact {
  id: string
  supplierId: string
  name: string
  email: string
  phone: string
  title: string
  isPrimary: boolean
}

export interface SupplierContract {
  id: string
  supplierId: string
  title: string
  startDate: string
  endDate: string
  value: number
  terms: string
  status: 'ACTIVE' | 'EXPIRED' | 'TERMINATED'
}

export interface PurchaseRequest {
  id: string
  requestNumber: string
  title: string
  description: string
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'CONVERTED'
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
  requestedBy: string
  department: string
  items: PurchaseRequestItem[]
  totalAmount: number
  notes: string
  createdAt: string
  updatedAt: string
}

export interface PurchaseRequestItem {
  id: string
  requestId: string
  sku: string
  productName: string
  quantity: number
  unitPrice: number
  totalPrice: number
  notes: string
}

export interface PurchaseOrder {
  id: string
  poNumber: string
  supplierId: string
  supplierName: string
  requestId: string
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'SENT' | 'CONFIRMED' | 'SHIPPED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED'
  items: PurchaseOrderItem[]
  subtotal: number
  tax: number
  shippingCost: number
  totalAmount: number
  paymentTerms: string
  expectedDeliveryDate: string
  notes: string
  createdAt: string
  updatedAt: string
}

export interface PurchaseOrderItem {
  id: string
  poId: string
  sku: string
  productName: string
  quantity: number
  quantityReceived: number
  unitPrice: number
  totalPrice: number
}

export interface Rfq {
  id: string
  rfqNumber: string
  title: string
  description: string
  status: 'DRAFT' | 'SENT' | 'UNDER_REVIEW' | 'AWARDED' | 'CANCELLED'
  items: RfqItem[]
  supplierIds: string[]
  responseDeadline: string
  notes: string
  createdAt: string
  updatedAt: string
}

export interface RfqItem {
  id: string
  rfqId: string
  sku: string
  productName: string
  quantity: number
  unitPrice: number
}

export interface RfqResponse {
  id: string
  rfqId: string
  supplierId: string
  supplierName: string
  items: RfqResponseItem[]
  totalAmount: number
  paymentTerms: string
  deliveryDate: string
  notes: string
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  submittedAt: string
}

export interface RfqResponseItem {
  id: string
  responseId: string
  sku: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export async function getSuppliers(page: number, size: number): Promise<ApiResponse<{ content: Supplier[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/procurement/suppliers', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch suppliers')
  }
}

export async function getSupplier(id: string): Promise<ApiResponse<Supplier>> {
  try {
    const { data } = await client.get(`/procurement/suppliers/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch supplier')
  }
}

export async function createSupplier(supplierData: Record<string, any>): Promise<ApiResponse<Supplier>> {
  try {
    const { data } = await client.post('/procurement/suppliers', supplierData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create supplier')
  }
}

export async function updateSupplier(id: string, supplierData: Record<string, any>): Promise<ApiResponse<Supplier>> {
  try {
    const { data } = await client.put(`/procurement/suppliers/${id}`, supplierData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update supplier')
  }
}

export async function deleteSupplier(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.delete(`/procurement/suppliers/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to delete supplier')
  }
}

export async function getSupplierContacts(supplierId: string): Promise<ApiResponse<SupplierContact[]>> {
  try {
    const { data } = await client.get(`/procurement/suppliers/${supplierId}/contacts`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch supplier contacts')
  }
}

export async function addSupplierContact(contactData: Record<string, any>): Promise<ApiResponse<SupplierContact>> {
  try {
    const { data } = await client.post('/procurement/supplier-contacts', contactData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to add supplier contact')
  }
}

export async function getSupplierContracts(supplierId: string): Promise<ApiResponse<SupplierContract[]>> {
  try {
    const { data } = await client.get(`/procurement/suppliers/${supplierId}/contracts`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch supplier contracts')
  }
}

export async function addSupplierContract(contractData: Record<string, any>): Promise<ApiResponse<SupplierContract>> {
  try {
    const { data } = await client.post('/procurement/supplier-contracts', contractData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to add supplier contract')
  }
}

export async function getRequests(page: number, size: number): Promise<ApiResponse<{ content: PurchaseRequest[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/procurement/requests', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch purchase requests')
  }
}

export async function getRequest(id: string): Promise<ApiResponse<PurchaseRequest>> {
  try {
    const { data } = await client.get(`/procurement/requests/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch purchase request')
  }
}

export async function createRequest(requestData: Record<string, any>): Promise<ApiResponse<PurchaseRequest>> {
  try {
    const { data } = await client.post('/procurement/requests', requestData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create purchase request')
  }
}

export async function updateRequestStatus(id: string, status: string): Promise<ApiResponse<PurchaseRequest>> {
  try {
    const { data } = await client.put(`/procurement/requests/${id}/status`, { status })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update request status')
  }
}

export async function addRequestItem(requestId: string, itemData: Record<string, any>): Promise<ApiResponse<PurchaseRequest>> {
  try {
    const { data } = await client.post(`/procurement/requests/${requestId}/items`, itemData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to add request item')
  }
}

export async function submitForApproval(id: string): Promise<ApiResponse<PurchaseRequest>> {
  try {
    const { data } = await client.post(`/procurement/requests/${id}/submit`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to submit for approval')
  }
}

export async function approveRequest(id: string): Promise<ApiResponse<PurchaseRequest>> {
  try {
    const { data } = await client.post(`/procurement/requests/${id}/approve`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to approve request')
  }
}

export async function getRfqs(page: number, size: number): Promise<ApiResponse<{ content: Rfq[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/procurement/rfqs', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch RFQs')
  }
}

export async function getRfq(id: string): Promise<ApiResponse<Rfq>> {
  try {
    const { data } = await client.get(`/procurement/rfqs/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch RFQ')
  }
}

export async function createRfq(rfqData: Record<string, any>): Promise<ApiResponse<Rfq>> {
  try {
    const { data } = await client.post('/procurement/rfqs', rfqData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create RFQ')
  }
}

export async function submitRfq(id: string): Promise<ApiResponse<Rfq>> {
  try {
    const { data } = await client.post(`/procurement/rfqs/${id}/submit`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to submit RFQ')
  }
}

export async function getRfqResponses(rfqId: string): Promise<ApiResponse<RfqResponse[]>> {
  try {
    const { data } = await client.get(`/procurement/rfqs/${rfqId}/responses`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch RFQ responses')
  }
}

export async function addRfqResponse(rfqId: string, responseData: Record<string, any>): Promise<ApiResponse<RfqResponse>> {
  try {
    const { data } = await client.post(`/procurement/rfqs/${rfqId}/responses`, responseData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to add RFQ response')
  }
}

export async function getPurchaseOrders(page: number, size: number): Promise<ApiResponse<{ content: PurchaseOrder[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/procurement/purchase-orders', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch purchase orders')
  }
}

export async function getPurchaseOrder(id: string): Promise<ApiResponse<PurchaseOrder>> {
  try {
    const { data } = await client.get(`/procurement/purchase-orders/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch purchase order')
  }
}

export async function createPurchaseOrder(poData: Record<string, any>): Promise<ApiResponse<PurchaseOrder>> {
  try {
    const { data } = await client.post('/procurement/purchase-orders', poData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create purchase order')
  }
}

export async function updatePurchaseOrderStatus(id: string, status: string): Promise<ApiResponse<PurchaseOrder>> {
  try {
    const { data } = await client.put(`/procurement/purchase-orders/${id}/status`, { status })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update purchase order status')
  }
}

export async function receiveItems(poId: string, items: Record<string, any>[]): Promise<ApiResponse<PurchaseOrder>> {
  try {
    const { data } = await client.post(`/procurement/purchase-orders/${poId}/receive`, { items })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to receive items')
  }
}

export async function approvePurchaseOrder(id: string): Promise<ApiResponse<PurchaseOrder>> {
  try {
    const { data } = await client.post(`/procurement/purchase-orders/${id}/approve`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to approve purchase order')
  }
}
