import client from './client'
import type { ApiResponse } from '../types'

export interface ImportResult {
  entityType: string
  fileName: string
  format: string
  totalRecords: number
  successCount: number
  errorCount: number
  errors: string[]
  warnings: string[]
  skippedCount: number
  processingTimeMs: number
}

export interface ImportFormat {
  id: string
  label: string
  extensions: string
}

export async function importFile(
  entityType: string,
  file: File,
  format?: string
): Promise<ApiResponse<ImportResult>> {
  const formData = new FormData()
  formData.append('file', file)
  if (format) formData.append('format', format)

  try {
    const { data } = await client.post(`/import/${entityType}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Import failed'
    return {
      success: false,
      data: {
        entityType,
        fileName: file.name,
        format: format || 'csv',
        totalRecords: 0,
        successCount: 0,
        errorCount: 1,
        errors: [msg],
        warnings: [],
        skippedCount: 0,
        processingTimeMs: 0,
      },
    }
  }
}

export async function getEntityTypes(): Promise<ApiResponse<string[]>> {
  try {
    const { data } = await client.get('/import/entity-types')
    return data
  } catch {
    return {
      success: true,
      data: ['products', 'orders', 'inventory', 'customers', 'shipments', 'returns', 'suppliers', 'purchase-orders', 'invoices', 'warehouses'],
    }
  }
}

export async function getImportFormats(): Promise<ApiResponse<ImportFormat[]>> {
  try {
    const { data } = await client.get('/import/formats')
    return data
  } catch {
    return {
      success: true,
      data: [
        { id: 'csv', label: 'CSV (Comma-Separated Values)', extensions: '.csv' },
        { id: 'json', label: 'JSON (JavaScript Object Notation)', extensions: '.json' },
        { id: 'xml', label: 'XML (Extensible Markup Language)', extensions: '.xml' },
        { id: 'edi', label: 'EDI X12 (Electronic Data Interchange)', extensions: '.edi,.850,.856,.810' },
        { id: 'xlsx', label: 'Excel Spreadsheet', extensions: '.xlsx,.xls' },
      ],
    }
  }
}
