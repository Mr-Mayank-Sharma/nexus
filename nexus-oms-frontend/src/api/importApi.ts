import client from './client'
import type { ApiResponse, ImportResult, ImportFormat, ImportHistorySummary, ImportRecordLogEntry, ImportHistoryDetail, PagedResponse } from '../types'
export type { ImportResult, ImportFormat, ImportHistorySummary, ImportRecordLogEntry, ImportHistoryDetail, PagedResponse }

export async function importFile(
  entityType: string,
  file: File,
  format?: string
): Promise<ApiResponse<ImportResult>> {
  const formData = new FormData()
  formData.append('file', file)
  if (format) formData.append('format', format)

  try {
    const { data } = await client.post(`/import/${entityType}`, formData)
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



export async function getImportHistory(
  params?: { status?: string; page?: number; size?: number }
): Promise<ApiResponse<PagedResponse<ImportHistorySummary>>> {
  try {
    const { data } = await client.get('/import/history', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to fetch import history'
    return { success: false, error: msg } as any
  }
}

export async function getImportDetail(id: string): Promise<ApiResponse<ImportHistoryDetail>> {
  try {
    const { data } = await client.get(`/import/history/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to fetch import detail'
    return { success: false, error: msg } as any
  }
}

export async function getProcessingLogs(
  id: string,
  params?: { status?: string; page?: number; size?: number }
): Promise<ApiResponse<PagedResponse<ImportRecordLogEntry>>> {
  try {
    const { data } = await client.get(`/import/history/${id}/logs`, { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to fetch processing logs'
    return { success: false, error: msg } as any
  }
}

export async function downloadOriginalFile(id: string): Promise<Blob> {
  const response = await client.get(`/import/history/${id}/download/original`, {
    responseType: 'blob',
  })
  return response.data
}

export async function downloadErrorFile(id: string): Promise<Blob> {
  const response = await client.get(`/import/history/${id}/download/errors`, {
    responseType: 'blob',
  })
  return response.data
}

export async function reprocessImport(id: string): Promise<ApiResponse<ImportResult>> {
  try {
    const { data } = await client.post(`/import/history/${id}/reprocess`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Reprocess failed'
    return { success: false, error: msg } as any
  }
}

export async function getImportModes(): Promise<ApiResponse<string[]>> {
  try {
    const { data } = await client.get('/import/modes')
    return data
  } catch {
    return {
      success: true,
      data: ['VALIDATE_ONLY', 'CONTINUE_ON_ERROR', 'STOP_ON_FIRST_ERROR', 'INSERT_ONLY', 'UPDATE_ONLY', 'UPSERT'],
    }
  }
}

export async function getSampleDataEntityTypes(): Promise<ApiResponse<string[]>> {
  try {
    const { data } = await client.get('/sample-data/entity-types')
    return data
  } catch {
    return {
      success: true,
      data: ['products', 'orders', 'inventory', 'customers', 'shipments', 'returns', 'suppliers', 'purchase-orders', 'invoices', 'warehouses'],
    }
  }
}

export async function downloadSampleData(
  entityType: string,
  count: number = 10,
  format: string = 'csv'
): Promise<Blob> {
  const response = await client.get(`/sample-data/generate/${entityType}`, {
    params: { count, format },
    responseType: 'blob',
  })
  return response.data
}
