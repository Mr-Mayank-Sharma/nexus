import PermissionGate from '../components/rbac/PermissionGate'
import { useState, useEffect, useCallback, useRef } from 'react'
import { clsx } from 'clsx'
import {
  Download, Upload, RefreshCw, XCircle, CheckCircle, Clock, AlertTriangle,
  FileText, BarChart3, Activity, Database, Play, Trash2, Eye, X, Loader2,
  FileUp, FileCode, FileSpreadsheet, Table, FileType,
} from 'lucide-react'
import {
  EnterpriseToolbar, EnterpriseTabs, EnterpriseDataGrid, EnterpriseKPICard,
  EnterpriseBreadcrumbs, EnterpriseStatusBadge, EnterpriseFormSection,
} from '../components/enterprise'
import type { Column, Tab } from '../components/enterprise'
import * as integrationApi from '../api/integrationPlatform'
import * as importApi from '../api/importApi'
import type { ImportResult, ImportFormat } from '../api/importApi'
import { downloadSampleData } from '../api/importApi'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'

const FORMAT_ICONS: Record<string, any> = {
  csv: Table,
  json: FileCode,
  xml: FileType,
  edi: FileSpreadsheet,
  xlsx: FileSpreadsheet,
}

const ENTITY_ICONS: Record<string, any> = {
  products: PackageIcon,
  orders: ShoppingCartIcon,
  inventory: Database,
  customers: UsersIcon,
  shipments: TruckIcon,
  returns: RotateIcon,
  suppliers: BuildingIcon,
  'purchase-orders': ClipboardIcon,
  invoices: FileText,
  warehouses: BuildingIcon,
}

function PackageIcon({ className }: { className?: string }) { return <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" /></svg> }
function ShoppingCartIcon({ className }: { className?: string }) { return <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 100 4 2 2 0 000-4z" /></svg> }
function UsersIcon({ className }: { className?: string }) { return <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197m13.5-9a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0z" /></svg> }
function TruckIcon({ className }: { className?: string }) { return <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M13 16V6a1 1 0 00-1-1H4a1 1 0 00-1 1v10l2-1m8 1l2 1m-2-1v-4a1 1 0 011-1h2.172a1 1 0 01.707.293l2.414 2.414a1 1 0 01.293.707V15m-8 1a2 2 0 104 0m-4 0a2 2 0 114 0" /></svg> }
function RotateIcon({ className }: { className?: string }) { return <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" /></svg> }
function BuildingIcon({ className }: { className?: string }) { return <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" /></svg> }
function ClipboardIcon({ className }: { className?: string }) { return <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" /></svg> }

export default function ImportExportCenter() {
  const [activeTab, setActiveTab] = useState('import')
  const [showModal, setShowModal] = useState(false)
  const [modalType, setModalType] = useState<'import' | 'export'>('import')
  const [formData, setFormData] = useState({ jobName: '', sourceOrType: '', format: '', configuration: '', schedule: '' })
  const [processing, setProcessing] = useState(false)
  const [importJobs, setImportJobs] = useState<integrationApi.IntegrationImportJob[]>([])
  const [exportJobs, setExportJobs] = useState<integrationApi.IntegrationExportJob[]>([])
  const [dlqEntries, setDlqEntries] = useState<integrationApi.IntegrationDLQ[]>([])
  const [endpoints, setEndpoints] = useState<integrationApi.IntegrationEndpoint[]>([])
  const [stats, setStats] = useState<Record<string, any> | null>(null)
  const [loading, setLoading] = useState(true)
  const { addToast } = useToast()

  const [importFileOpen, setImportFileOpen] = useState(false)
  const [importEntityType, setImportEntityType] = useState('products')
  const [importFormat, setImportFormat] = useState('csv')
  const [importFile, setImportFile] = useState<File | null>(null)
  const [importProcessing, setImportProcessing] = useState(false)
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [entityTypes, setEntityTypes] = useState<string[]>([])
  const [formats, setFormats] = useState<ImportFormat[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)

  const fetchData = useCallback(async () => {
    try {
      const [importRes, exportRes, dlqRes, endpointRes, statsRes] = await Promise.all([
        integrationApi.getImportJobs({ size: 50 }).catch(() => ({ data: [] })),
        integrationApi.getExportJobs({ size: 50 }).catch(() => ({ data: [] })),
        integrationApi.getDLQEntries({ size: 50 }).catch(() => ({ data: [] })),
        integrationApi.getEndpoints().catch(() => ({ data: [] })),
        integrationApi.getDashboardStats().catch(() => ({ data: {} })),
      ])
      setImportJobs(importRes.data || [])
      setExportJobs(exportRes.data || [])
      setDlqEntries(dlqRes.data || [])
      setEndpoints(Array.isArray(endpointRes.data) ? endpointRes.data : [])
      setStats(statsRes.data as Record<string, any>)
    } catch {
      addToast({ type: 'error', title: 'Failed to load import/export data' })
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchMeta = useCallback(async () => {
    try {
      const [etRes, fmtRes] = await Promise.all([
        importApi.getEntityTypes().catch(() => ({ data: ['products', 'orders', 'inventory', 'customers', 'shipments', 'returns', 'suppliers', 'purchase-orders', 'invoices', 'warehouses'] })),
        importApi.getImportFormats().catch(() => ({ data: [{ id: 'csv', label: 'CSV', extensions: '.csv' }, { id: 'json', label: 'JSON', extensions: '.json' }, { id: 'xml', label: 'XML', extensions: '.xml' }, { id: 'edi', label: 'EDI X12', extensions: '.edi' }, { id: 'xlsx', label: 'Excel', extensions: '.xlsx' }] })),
      ])
      setEntityTypes(Array.isArray(etRes.data) ? etRes.data : [])
      setFormats(Array.isArray(fmtRes.data) ? fmtRes.data : [])
    } catch { addToast({ type: 'error', title: 'Failed to load import/export metadata' }) }
  }, [])

  useEffect(() => { fetchData() }, [fetchData])
  useEffect(() => { fetchMeta() }, [fetchMeta])

  const handleCreateJob = async () => {
    if (!formData.jobName) return
    setProcessing(true)
    try {
      if (modalType === 'import') {
        await integrationApi.createImportJob({
          jobName: formData.jobName,
          sourceType: formData.sourceOrType,
          targetType: 'INTERNAL',
        })
      } else {
        await integrationApi.createExportJob({
          jobName: formData.jobName,
          exportType: formData.sourceOrType,
          format: formData.format,
        })
      }
      addToast({ type: 'success', title: `${modalType === 'import' ? 'Import' : 'Export'} job created` })
      setShowModal(false)
      setFormData({ jobName: '', sourceOrType: '', format: '', configuration: '', schedule: '' })
      fetchData()
    } catch {
      addToast({ type: 'error', title: 'Failed to create job' })
    } finally {
      setProcessing(false)
    }
  }

  const handleRetryImport = async (id: string) => {
    try {
      await integrationApi.retryImportJob(id)
      addToast({ type: 'success', title: 'Import job retrying' })
      fetchData()
    } catch { addToast({ type: 'error', title: 'Retry failed' }) }
  }

  const handleCancelImport = async (id: string) => {
    try {
      await integrationApi.cancelImportJob(id)
      addToast({ type: 'success', title: 'Import job cancelled' })
      fetchData()
    } catch { addToast({ type: 'error', title: 'Cancel failed' }) }
  }

  const handleRetryExport = async (id: string) => {
    try {
      await integrationApi.retryExportJob(id)
      addToast({ type: 'success', title: 'Export job retrying' })
      fetchData()
    } catch { addToast({ type: 'error', title: 'Retry failed' }) }
  }

  const handleCancelExport = async (id: string) => {
    try {
      await integrationApi.cancelExportJob(id)
      addToast({ type: 'success', title: 'Export job cancelled' })
      fetchData()
    } catch { addToast({ type: 'error', title: 'Cancel failed' }) }
  }

  const handleReplayDLQ = async (id: string) => {
    try {
      await integrationApi.replayDLQEntry(id)
      addToast({ type: 'success', title: 'Message replayed' })
      fetchData()
    } catch { addToast({ type: 'error', title: 'Replay failed' }) }
  }

  const handleIgnoreDLQ = async (id: string) => {
    try {
      await integrationApi.ignoreDLQEntry(id)
      addToast({ type: 'success', title: 'Message ignored' })
      fetchData()
    } catch { addToast({ type: 'error', title: 'Ignore failed' }) }
  }

  const handleGenerateSample = async () => {
    setImportProcessing(true)
    try {
      const blob = await downloadSampleData(importEntityType, 10, importFormat)
      const fileName = `sample_${importEntityType}_${Date.now()}.${importFormat}`
      const file = new File([blob], fileName, { type: blob.type || 'text/csv' })
      setImportFile(file)
      addToast({ type: 'success', title: `Sample ${importEntityType} data generated (10 records)` })
    } catch {
      addToast({ type: 'error', title: 'Failed to generate sample data' })
    } finally {
      setImportProcessing(false)
    }
  }

  const handleImportFile = async () => {
    if (!importFile) return
    setImportProcessing(true)
    setImportResult(null)
    try {
      const token = await importApi.getImportToken(importEntityType)
      const res = await importApi.importFile(importEntityType, importFile, importFormat, token || undefined)
      if (res.success && res.data) {
        setImportResult(res.data)
        addToast({ type: res.data.errorCount > 0 ? 'warning' : 'success', title: `Imported ${res.data.successCount} of ${res.data.totalRecords} ${importEntityType}` })
      } else {
        addToast({ type: 'error', title: res.message || 'Import failed' })
      }
    } catch {
      addToast({ type: 'error', title: 'Import request failed' })
    } finally {
      setImportProcessing(false)
    }
  }

  const resetImportFile = () => {
    setImportFileOpen(false)
    setImportFile(null)
    setImportResult(null)
    setImportEntityType('products')
    setImportFormat('csv')
  }

  const kpiRunning = importJobs.filter(j => j.status === 'PROCESSING' || j.status === 'VALIDATING').length +
    exportJobs.filter(j => j.status === 'PROCESSING').length
  const kpiCompleted = importJobs.filter(j => j.status === 'COMPLETED').length +
    exportJobs.filter(j => j.status === 'COMPLETED').length
  const kpiFailed = importJobs.filter(j => j.status === 'FAILED').length +
    exportJobs.filter(j => j.status === 'FAILED').length

  const tabs: Tab[] = [
    { id: 'import', label: 'Import Jobs', icon: <Upload className="w-4 h-4" />, badge: importJobs.filter(j => ['PROCESSING', 'VALIDATING', 'PENDING'].includes(j.status)).length },
    { id: 'export', label: 'Export Jobs', icon: <Download className="w-4 h-4" />, badge: exportJobs.filter(j => ['PROCESSING', 'PENDING'].includes(j.status)).length },
    { id: 'dlq', label: 'Dead Letter Queue', icon: <AlertTriangle className="w-4 h-4" />, badge: dlqEntries.filter(e => e.status === 'BLOCKED').length },
    { id: 'health', label: 'Integration Health', icon: <Activity className="w-4 h-4" /> },
  ]

const sourceOptions = ['Shopify', 'Amazon', 'BigCommerce', 'WooCommerce', 'SFTP', 'S3 Bucket', 'API Endpoint', 'SAP ERP']
const sourceOpts = sourceOptions.map(o => ({ value: o, label: o }))
const formatOptions = ['JSON', 'XML', 'CSV', 'EDI_X12', 'EXCEL']
const formatOpts = formatOptions.map(f => ({ value: f, label: f }))
const exportTypeOptions = ['Orders', 'Products', 'Inventory', 'Shipments', 'Returns', 'Customers', 'Invoices']
const exportTypeOpts = exportTypeOptions.map(o => ({ value: o, label: o }))

  const importColumns: Column<integrationApi.IntegrationImportJob>[] = [
    { key: 'jobName', label: 'Job Name', sortable: true, minWidth: '180px' },
    { key: 'sourceType', label: 'Source', sortable: true },
    {
      key: 'status', label: 'Status', sortable: true,
      render: (val: string) => (
        <EnterpriseStatusBadge status={
          val === 'COMPLETED' ? 'completed' : val === 'FAILED' ? 'failed' : val === 'CANCELLED' ? 'neutral' : val === 'PROCESSING' || val === 'VALIDATING' ? 'info' : 'pending'
        }>{val}</EnterpriseStatusBadge>
      ),
    },
    {
      key: 'recordCount', label: 'Records T/S/E', sortable: true,
      render: (_: number, row: integrationApi.IntegrationImportJob) => (
        <span className="text-sm">
          <span className="font-medium">{row.recordCount?.toLocaleString() || '0'}</span>
          {' / '}
          <span className="text-emerald-600">{row.successCount?.toLocaleString() || '0'}</span>
          {' / '}
          <span className={row.errorCount > 0 ? 'text-red-500' : 'text-gray-400'}>{row.errorCount?.toLocaleString() || '0'}</span>
        </span>
      ),
    },
    { key: 'processingTimeMs', label: 'Processing Time', sortable: true, render: (val: number) => val ? `${(val / 1000).toFixed(1)}s` : '-' },
    { key: 'createdAt', label: 'Started', sortable: true, minWidth: '150px', render: (val: string) => val ? new Date(val).toLocaleString() : '-' },
    {
      key: 'actions', label: 'Actions', width: '120px',
      render: (_: any, row: integrationApi.IntegrationImportJob) => (
        <div className="flex items-center gap-1">
          <button onClick={() => addToast({ type: 'info', title: 'View details not yet implemented' })} className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1" title="View Details">
            <Eye className="w-4 h-4" />
          </button>
          {row.status === 'FAILED' && (
            <PermissionGate resource="import" action="edit">
              <button onClick={() => handleRetryImport(row.id)} className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1 text-primary-600" title="Retry">
                <RefreshCw className="w-4 h-4" />
              </button>
            </PermissionGate>
          )}
          {['PROCESSING', 'VALIDATING', 'PENDING'].includes(row.status) && (
            <PermissionGate resource="import" action="delete">
              <button onClick={() => handleCancelImport(row.id)} className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1 text-red-500" title="Cancel">
                <XCircle className="w-4 h-4" />
              </button>
            </PermissionGate>
          )}
        </div>
      ),
    },
  ]

  const exportColumns: Column<integrationApi.IntegrationExportJob>[] = [
    { key: 'jobName', label: 'Job Name', sortable: true, minWidth: '180px' },
    { key: 'exportType', label: 'Export Type', sortable: true },
    { key: 'format', label: 'Format', sortable: true },
    {
      key: 'status', label: 'Status', sortable: true,
      render: (val: string) => (
        <EnterpriseStatusBadge status={
          val === 'COMPLETED' ? 'completed' : val === 'FAILED' ? 'failed' : val === 'CANCELLED' ? 'neutral' : val === 'PROCESSING' ? 'info' : 'pending'
        }>{val}</EnterpriseStatusBadge>
      ),
    },
    { key: 'recordCount', label: 'Records', sortable: true, render: (val: number) => val > 0 ? val.toLocaleString() : '-' },
    { key: 'fileSize', label: 'File Size', sortable: true, render: (val: number) => val ? `${(val / 1024).toFixed(1)} KB` : '-' },
    { key: 'createdAt', label: 'Started', sortable: true, minWidth: '150px', render: (val: string) => val ? new Date(val).toLocaleString() : '-' },
    {
      key: 'actions', label: 'Actions', width: '120px',
      render: (_: any, row: integrationApi.IntegrationExportJob) => (
        <div className="flex items-center gap-1">
          <button onClick={() => addToast({ type: 'info', title: 'View details not yet implemented' })} className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1" title="View Details">
            <Eye className="w-4 h-4" />
          </button>
          {row.status === 'FAILED' && (
            <button onClick={() => handleRetryExport(row.id)} className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1 text-primary-600" title="Retry">
              <RefreshCw className="w-4 h-4" />
            </button>
          )}
          {row.status === 'PROCESSING' && (
            <button onClick={() => handleCancelExport(row.id)} className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1 text-red-500" title="Cancel">
              <XCircle className="w-4 h-4" />
            </button>
          )}
        </div>
      ),
    },
  ]

  const dlqColumns: Column<integrationApi.IntegrationDLQ>[] = [
    { key: 'messageId', label: 'Message ID', sortable: true, render: (val: string) => <code className="text-xs font-mono">{val}</code> },
    { key: 'flowName', label: 'Flow', sortable: true, minWidth: '160px' },
    {
      key: 'errorCategory', label: 'Error Category', sortable: true,
      render: (val: string) => (
        <EnterpriseStatusBadge status={
          val === 'VALIDATION' || val === 'NETWORK' ? 'warning' : val === 'TIMEOUT' || val === 'AUTH' || val === 'SERVER_ERROR' ? 'error' : 'info'
        }>{val}</EnterpriseStatusBadge>
      ),
    },
    { key: 'errorMessage', label: 'Error Message', minWidth: '280px', render: (val: string) => (
      <span className="text-sm text-gray-500 truncate block max-w-[280px]" title={val}>{val}</span>
    )},
    { key: 'retryCount', label: 'Retry Count', sortable: true, align: 'center' as const },
    { key: 'lastRetryAt', label: 'Last Retry', sortable: true, minWidth: '140px', render: (val: string) => val ? new Date(val).toLocaleString() : '-' },
    {
      key: 'status', label: 'Status', sortable: true,
      render: (val: string) => (
        <EnterpriseStatusBadge status={val === 'BLOCKED' ? 'error' : val === 'PENDING' ? 'warning' : 'info'}>{val}</EnterpriseStatusBadge>
      ),
    },
    {
      key: 'actions', label: 'Actions', width: '100px',
      render: (_: any, row: integrationApi.IntegrationDLQ) => (
        <div className="flex items-center gap-1">
          <button onClick={() => handleReplayDLQ(row.id)} className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1 text-primary-600" title="Replay">
            <Play className="w-4 h-4" />
          </button>
          <button onClick={() => handleIgnoreDLQ(row.id)} className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm p-1 text-red-500" title="Ignore">
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[
        { label: 'Home', path: '/' },
        { label: 'Integration', path: '/integration' },
        { label: 'Import/Export Center' },
      ]} />

      <EnterpriseToolbar
        title="Import/Export Center"
        subtitle="Monitor and manage data integrations"
        actions={[
          { label: 'Import File', icon: <FileUp className="w-4 h-4" />, onClick: () => { setImportFileOpen(true); setImportResult(null); setImportFile(null) }, variant: 'primary', permission: { resource: 'import', action: 'create' } },
          { label: 'New Import', icon: <Upload className="w-4 h-4" />, onClick: () => { setModalType('import'); setFormData({ jobName: '', sourceOrType: '', format: '', configuration: '', schedule: '' }); setShowModal(true) }, variant: 'secondary', permission: { resource: 'import', action: 'create' } },
          { label: 'New Export', icon: <Download className="w-4 h-4" />, onClick: () => { setModalType('export'); setFormData({ jobName: '', sourceOrType: '', format: '', configuration: '', schedule: '' }); setShowModal(true) }, variant: 'ghost', permission: { resource: 'import', action: 'create' } },
          { label: 'Refresh', icon: <RefreshCw className="w-4 h-4" />, onClick: () => { setLoading(true); fetchData() }, variant: 'ghost' },
        ]}
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <EnterpriseKPICard title="Running Jobs" value={kpiRunning} icon={<Activity className="w-5 h-5" />} color="primary" trend="up" />
        <EnterpriseKPICard title="Completed Today" value={kpiCompleted} icon={<CheckCircle className="w-5 h-5" />} color="success" trend="up" />
        <EnterpriseKPICard title="Failed Jobs" value={kpiFailed} icon={<XCircle className="w-5 h-5" />} color="error" trend={kpiFailed > 0 ? 'down' : 'up'} />
        <EnterpriseKPICard title="Active Endpoints" value={endpoints.filter(e => e.status === 'connected').length} icon={<Database className="w-5 h-5" />} color="info" />
      </div>

      <EnterpriseTabs tabs={tabs} activeTab={activeTab} onChange={setActiveTab} variant="underline" />

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
        </div>
      ) : (
        <>
          {activeTab === 'import' && (
            <EnterpriseDataGrid columns={importColumns} data={importJobs} rowKey="id" pageSize={10} totalElements={importJobs.length} exportable />
          )}

          {activeTab === 'export' && (
            <EnterpriseDataGrid columns={exportColumns} data={exportJobs} rowKey="id" pageSize={10} totalElements={exportJobs.length} exportable />
          )}

          {activeTab === 'dlq' && (
            <EnterpriseDataGrid columns={dlqColumns} data={dlqEntries} rowKey="id" pageSize={10} totalElements={dlqEntries.length} exportable />
          )}

          {activeTab === 'health' && (
            <div className="space-y-6">
              <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-4">
                <EnterpriseKPICard title="Active Endpoints" value={endpoints.filter(e => e.status === 'connected').length} icon={<Database className="w-5 h-5" />} color="success" />
                <EnterpriseKPICard title="Active Flows" value={importJobs.filter(j => j.status !== 'CANCELLED').length + exportJobs.filter(j => j.status !== 'CANCELLED').length} icon={<Activity className="w-5 h-5" />} color="primary" />
                <EnterpriseKPICard title="Messages Processed" value={(importJobs.reduce((s, j) => s + (j.recordCount || 0), 0) + exportJobs.reduce((s, j) => s + (j.recordCount || 0), 0)).toLocaleString()} icon={<BarChart3 className="w-5 h-5" />} color="info" />
                <EnterpriseKPICard title="DLQ Count" value={dlqEntries.length} icon={<AlertTriangle className="w-5 h-5" />} color="error" />
                <EnterpriseKPICard title="Pending CDC" value={stats?.pendingCdc || '0'} icon={<FileText className="w-5 h-5" />} color="warning" />
              </div>

              <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
                <div className="px-5 py-4 border-b border-gray-200 dark:border-gray-700">
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Endpoint & Flow Status</h3>
                </div>
                <div className="divide-y divide-gray-100 dark:divide-gray-700">
                  {endpoints.length === 0 ? (
                    <div className="px-5 py-8 text-center text-sm text-gray-400">No endpoints configured</div>
                  ) : (
                    endpoints.map((ep, i) => (
                      <div key={ep.id || i} className="flex items-center justify-between px-5 py-3 text-sm">
                        <div className="flex items-center gap-3">
                          <span className={clsx('w-2 h-2 rounded-full', ep.status === 'connected' ? 'bg-emerald-500' : 'bg-red-500')} />
                          <span className="font-medium text-gray-900 dark:text-gray-100">{ep.name}</span>
                        </div>
                        <div className="flex items-center gap-4">
                          <span className="text-xs text-gray-400 uppercase">{ep.type}</span>
                          <span className={clsx('text-xs font-medium', ep.status === 'connected' ? 'text-emerald-600' : 'text-red-500')}>
                            {ep.status === 'connected' ? 'Connected' : 'Disconnected'}
                          </span>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {showModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto border border-gray-200 dark:border-gray-700">
            <div className="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300">
                  {modalType === 'import' ? <Upload className="w-5 h-5" /> : <Download className="w-5 h-5" />}
                </div>
                <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  New {modalType === 'import' ? 'Import' : 'Export'} Job
                </h2>
              </div>
              <button onClick={() => setShowModal(false)} className="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors">
                <X className="w-5 h-5 text-gray-400" />
              </button>
            </div>

            <div className="p-6 space-y-5">
              <EnterpriseFormSection title="Job Details" columns={1}>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1.5">Job Name</label>
                  <Autocomplete className="enterprise-input w-full" placeholder={`e.g. ${modalType === 'import' ? 'Shopify Orders Daily Import' : 'Orders CSV Export'}`}
                    value={formData.jobName} onChange={v => setFormData({ ...formData, jobName: v })} minChars={0} />
                </div>
              </EnterpriseFormSection>
              <EnterpriseFormSection title="Configuration" columns={2}>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1.5">{modalType === 'import' ? 'Source' : 'Export Type'}</label>
                  <Autocomplete className="enterprise-input w-full" value={formData.sourceOrType} onChange={v => setFormData({ ...formData, sourceOrType: v })} suggestions={modalType === 'import' ? sourceOpts : exportTypeOpts} getOptionLabel={o => o.label} getOptionValue={o => o.value} minChars={0} />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1.5">Format</label>
                  <Autocomplete className="enterprise-input w-full" value={formData.format} onChange={v => setFormData({ ...formData, format: v })} suggestions={formatOpts} getOptionLabel={o => o.label} getOptionValue={o => o.value} minChars={0} />
                </div>
              </EnterpriseFormSection>
              <EnterpriseFormSection title="Advanced" columns={1}>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1.5">Configuration (JSON)</label>
                  <Autocomplete className="enterprise-input w-full font-mono text-xs"
                    placeholder='{"filter": {"dateFrom": "2026-06-01"}, "batchSize": 1000}'
                    value={formData.configuration} onChange={v => setFormData({ ...formData, configuration: v })} minChars={0} />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1.5">Schedule (Cron Expression)</label>
                  <Autocomplete className="enterprise-input w-full font-mono text-sm" placeholder="0 0 * * * (daily at midnight)"
                    value={formData.schedule} onChange={v => setFormData({ ...formData, schedule: v })} minChars={0} />
                </div>
              </EnterpriseFormSection>
            </div>

            <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200 dark:border-gray-700">
              <button onClick={() => setShowModal(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <PermissionGate resource="import" action="create">
                <button onClick={handleCreateJob} disabled={processing || !formData.jobName} className="enterprise-btn enterprise-btn-primary disabled:opacity-50">
                  {processing && <Loader2 className="w-4 h-4 animate-spin" />}
                  <Play className="w-4 h-4" /> Run {modalType === 'import' ? 'Import' : 'Export'}
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {importFileOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto border border-gray-200 dark:border-gray-700">
            <div className="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300">
                  <FileUp className="w-5 h-5" />
                </div>
                <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Import File</h2>
              </div>
              <button onClick={resetImportFile} className="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors">
                <X className="w-5 h-5 text-gray-400" />
              </button>
            </div>

            <div className="p-6 space-y-5">
              <EnterpriseFormSection title="Import Configuration" columns={2}>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1.5">Entity Type</label>
                  <Autocomplete className="enterprise-input w-full" value={importEntityType} onChange={v => setImportEntityType(v)} suggestions={entityTypes.map(et => ({ value: et, label: et.charAt(0).toUpperCase() + et.slice(1).replace('-', ' ') }))} getOptionLabel={o => o.label} getOptionValue={o => o.value} minChars={0} />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1.5">File Format</label>
                  <Autocomplete className="enterprise-input w-full" value={importFormat} onChange={v => setImportFormat(v)} suggestions={formats.map(f => ({ value: f.id, label: f.label }))} getOptionLabel={o => o.label} getOptionValue={o => o.value} minChars={0} />
                </div>
              </EnterpriseFormSection>

              <EnterpriseFormSection title="File Upload" columns={1}>
                <div
                  onClick={() => fileInputRef.current?.click()}
                  className="border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-xl p-8 text-center cursor-pointer hover:border-primary-400 transition-colors"
                >
                  {importFile ? (
                    <div className="space-y-2">
                      <div className="flex items-center justify-center gap-3">
                        {(() => {
                          const FormatIcon = FORMAT_ICONS[importFormat] || FileText
                          return <FormatIcon className="w-8 h-8 text-primary-500" />
                        })()}
                        <div className="text-left">
                          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{importFile.name}</p>
                          <p className="text-xs text-gray-400">{(importFile.size / 1024).toFixed(1)} KB</p>
                        </div>
                      </div>
                      <button onClick={(e) => { e.stopPropagation(); setImportFile(null) }}
                        className="text-xs text-red-500 hover:text-red-700 mt-1">Remove</button>
                    </div>
                  ) : (
                    <div className="space-y-2">
                      <FileUp className="w-10 h-10 mx-auto text-gray-400" />
                      <p className="text-sm text-gray-500">Drop a file here or click to browse</p>
                      <p className="text-xs text-gray-400">
                        {formats.find(f => f.id === importFormat)?.extensions || '.csv, .json, .xml, .edi'} files
                      </p>
                    </div>
                  )}
                  <input ref={fileInputRef} type="file" className="hidden"
                    accept={formats.find(f => f.id === importFormat)?.extensions || '.csv,.json,.xml,.edi,.xlsx,.xls'}
                    onChange={e => setImportFile(e.target.files?.[0] || null)} />
                </div>
                <div className="flex justify-center mt-3">
                  <button onClick={(e) => { e.stopPropagation(); handleGenerateSample() }}
                    disabled={importProcessing}
                    className="enterprise-btn enterprise-btn-secondary text-xs disabled:opacity-50">
                    <Download className="w-3.5 h-3.5" /> Generate Sample ({importEntityType.replace('-', ' ')}) Data
                  </button>
                </div>
              </EnterpriseFormSection>

              {importResult && (
                <div className={clsx('rounded-xl border p-4 space-y-3',
                  importResult.errorCount > 0 ? 'bg-red-50 border-red-200 dark:bg-red-900/10 dark:border-red-800' :
                  'bg-emerald-50 border-emerald-200 dark:bg-emerald-900/10 dark:border-emerald-800')}>
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Import Results</h4>
                    {importResult.processingTimeMs > 0 && (
                      <span className="text-xs text-gray-400">{importResult.processingTimeMs}ms</span>
                    )}
                  </div>
                  <div className="grid grid-cols-4 gap-3 text-center">
                    <div>
                      <p className="text-2xl font-bold text-gray-900">{importResult.totalRecords}</p>
                      <p className="text-xs text-gray-500">Total</p>
                    </div>
                    <div>
                      <p className="text-2xl font-bold text-emerald-600">{importResult.successCount}</p>
                      <p className="text-xs text-gray-500">Success</p>
                    </div>
                    <div>
                      <p className="text-2xl font-bold text-red-600">{importResult.errorCount}</p>
                      <p className="text-xs text-gray-500">Errors</p>
                    </div>
                    <div>
                      <p className="text-2xl font-bold text-amber-600">{importResult.skippedCount}</p>
                      <p className="text-xs text-gray-500">Skipped</p>
                    </div>
                  </div>
                  {importResult.errors.length > 0 && (
                    <div className="bg-white dark:bg-gray-800 rounded-lg p-3 max-h-32 overflow-y-auto">
                      <p className="text-xs font-medium text-red-600 mb-1">Errors:</p>
                      {importResult.errors.map((err, i) => (
                        <p key={i} className="text-xs text-red-500 font-mono">• {err}</p>
                      ))}
                    </div>
                  )}
                  {importResult.warnings.length > 0 && (
                    <div className="bg-white dark:bg-gray-800 rounded-lg p-3 max-h-24 overflow-y-auto">
                      <p className="text-xs font-medium text-amber-600 mb-1">Warnings:</p>
                      {importResult.warnings.map((w, i) => (
                        <p key={i} className="text-xs text-amber-500">• {w}</p>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200 dark:border-gray-700">
              <button onClick={resetImportFile} className="enterprise-btn enterprise-btn-secondary">Close</button>
              <PermissionGate resource="import" action="create">
                <button onClick={handleImportFile} disabled={importProcessing || !importFile}
                  className="enterprise-btn enterprise-btn-primary disabled:opacity-50">
                  {importProcessing && <Loader2 className="w-4 h-4 animate-spin" />}
                  <FileUp className="w-4 h-4" /> Import {importEntityType.replace('-', ' ')}
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
