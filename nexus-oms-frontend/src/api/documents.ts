import client from './client'
import { ApiResponse } from '../types'

export interface Document {
  id: string
  name: string
  description: string
  type: string
  mimeType: string
  size: number
  category: string
  tags: string[]
  entityType: string
  entityId: string
  currentVersion: number
  uploadedBy: string
  uploadedByName: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface DocumentVersion {
  id: string
  documentId: string
  version: number
  mimeType: string
  size: number
  url: string
  uploadedBy: string
  notes: string
  createdAt: string
}

export async function getDocuments(entityType?: string, entityId?: string): Promise<ApiResponse<Document[]>> {
  try {
    const { data } = await client.get('/documents', { params: { entityType, entityId } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch documents')
  }
}

export async function getDocument(id: string): Promise<ApiResponse<Document>> {
  try {
    const { data } = await client.get(`/documents/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch document')
  }
}

export async function createDocument(documentData: Record<string, any>): Promise<ApiResponse<Document>> {
  try {
    const { data } = await client.post('/documents', documentData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create document')
  }
}

export async function updateDocument(id: string, documentData: Record<string, any>): Promise<ApiResponse<Document>> {
  try {
    const { data } = await client.put(`/documents/${id}`, documentData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update document')
  }
}

export async function deleteDocument(id: string): Promise<ApiResponse<void>> {
  try {
    const { data } = await client.delete(`/documents/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to delete document')
  }
}

export async function uploadNewVersion(documentId: string, formData: FormData): Promise<ApiResponse<Document>> {
  try {
    const { data } = await client.post(`/documents/${documentId}/versions`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to upload document version')
  }
}

export async function getDocumentVersions(documentId: string): Promise<ApiResponse<DocumentVersion[]>> {
  try {
    const { data } = await client.get(`/documents/${documentId}/versions`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch document versions')
  }
}

export async function getDocumentsByEntity(entityType: string, entityId: string): Promise<ApiResponse<Document[]>> {
  try {
    const { data } = await client.get(`/documents/entity/${entityType}/${entityId}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch documents by entity')
  }
}
