import { useState, useEffect, Fragment } from 'react'
import { FileText, File, Upload, Download, Plus, Search, X, Clock, Tag } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'
import * as documentsApi from '../api/documents'
import PermissionGate from '../components/rbac/PermissionGate'

const typeColors: Record<string, string> = {
  INVOICE: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]',
  CONTRACT: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]',
  REPORT: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)]',
  OTHER: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
}

const entityTypes = ['ORDER', 'CUSTOMER', 'SUPPLIER', 'PRODUCT', 'INVOICE']

export default function DocumentsPage() {
  const [docs, setDocs] = useState<documentsApi.Document[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [showUploadModal, setShowUploadModal] = useState(false)
  const [showVersionModal, setShowVersionModal] = useState(false)
  const [versionDocId, setVersionDocId] = useState<string | null>(null)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [versions, setVersions] = useState<documentsApi.DocumentVersion[]>([])
  const [versionsLoading, setVersionsLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [entityType, setEntityType] = useState('')
  const [entityId, setEntityId] = useState('')
  const { addToast } = useToast()

  const [form, setForm] = useState({
    title: '', description: '', documentType: 'OTHER', category: '',
    fileName: '', fileSize: 0, mimeType: '', fileUrl: '',
    entityType: '', entityId: '', tags: '',
  })

  const [versionForm, setVersionForm] = useState({
    fileName: '', fileSize: 0, fileUrl: '', changeNotes: '',
  })

  useEffect(() => { fetchDocs() }, [])

  async function fetchDocs(etype?: string, eid?: string) {
    try {
      setLoading(true)
      const res = await documentsApi.getDocuments(etype || undefined, eid || undefined)
      setDocs(Array.isArray(res.data) ? res.data : res.data?.content ?? [])
    } catch {
      addToast({ type: 'error', title: 'Failed to load documents' })
    } finally {
      setLoading(false)
    }
  }

  function openUpload() {
    setForm({ title: '', description: '', documentType: 'OTHER', category: '', fileName: '', fileSize: 0, mimeType: '', fileUrl: '', entityType: '', entityId: '', tags: '' })
    setShowUploadModal(true)
  }

  async function handleUpload() {
    if (!form.title.trim()) { addToast({ type: 'warning', title: 'Title is required' }); return }
    setSaving(true)
    try {
      const tagsArray = form.tags.split(',').map(t => t.trim()).filter(Boolean)
      await documentsApi.createDocument({ ...form, name: form.title, tags: tagsArray, size: form.fileSize, type: form.documentType })
      addToast({ type: 'success', title: 'Document uploaded' })
      setShowUploadModal(false)
      await fetchDocs()
    } catch {
      addToast({ type: 'error', title: 'Failed to upload document' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id: string) {
    try {
      await documentsApi.deleteDocument(id)
      addToast({ type: 'success', title: 'Document deleted' })
      await fetchDocs()
    } catch {
      addToast({ type: 'error', title: 'Failed to delete document' })
    }
  }

  async function toggleExpand(id: string) {
    if (expandedId === id) {
      setExpandedId(null)
      setVersions([])
      return
    }
    setExpandedId(id)
    setVersionsLoading(true)
    try {
      const res = await documentsApi.getDocumentVersions(id)
      setVersions(Array.isArray(res.data) ? res.data : res.data?.content ?? [])
    } catch {
      addToast({ type: 'error', title: 'Failed to load versions' })
    } finally {
      setVersionsLoading(false)
    }
  }

  function openVersionUpload(docId: string) {
    setVersionDocId(docId)
    setVersionForm({ fileName: '', fileSize: 0, fileUrl: '', changeNotes: '' })
    setShowVersionModal(true)
  }

  async function handleVersionUpload() {
    if (!versionForm.fileName.trim()) { addToast({ type: 'warning', title: 'File name is required' }); return }
    if (!versionDocId) return
    setSaving(true)
    try {
      const formData = new FormData()
      formData.append('fileName', versionForm.fileName)
      formData.append('fileSize', String(versionForm.fileSize))
      formData.append('fileUrl', versionForm.fileUrl)
      formData.append('changeNotes', versionForm.changeNotes)
      await documentsApi.uploadNewVersion(versionDocId, formData)
      addToast({ type: 'success', title: 'New version uploaded' })
      setShowVersionModal(false)
      setVersionDocId(null)
      if (expandedId) {
        const res = await documentsApi.getDocumentVersions(expandedId)
        setVersions(Array.isArray(res.data) ? res.data : res.data?.content ?? [])
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to upload version' })
    } finally {
      setSaving(false)
    }
  }

  function handleFilterByEntity() {
    if (!entityType) { addToast({ type: 'warning', title: 'Select an entity type' }); return }
    fetchDocs(entityType, entityId || undefined)
  }

  const filtered = docs.filter(d =>
    !search || d.name?.toLowerCase().includes(search.toLowerCase())
  )

  function formatSize(bytes: number) {
    if (!bytes) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><FileText className="w-6 h-6" />Documents</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Manage documents and files across entities</p>
        </div>
        <PermissionGate resource="settings" action="create">
          <button onClick={openUpload} className="btn-primary text-sm">
            <Plus className="w-4 h-4" /> Upload Document
          </button>
        </PermissionGate>
      </div>

      <div className="flex items-center gap-3 flex-wrap">
        <Autocomplete value={search} onChange={setSearch} placeholder="Search by title..." minChars={0} className="flex-1 max-w-xs" />
        <div className="w-px h-6 bg-[var(--surface-muted)]" />
        <select value={entityType} onChange={e => setEntityType(e.target.value)} className="input w-40">
          <option value="">Entity Type</option>
          {entityTypes.map(et => <option key={et} value={et}>{et}</option>)}
        </select>
        <input
          value={entityId}
          onChange={e => setEntityId(e.target.value)}
          className="input w-40"
          placeholder="Entity ID"
        />
        <button onClick={handleFilterByEntity} className="btn-secondary text-sm">
          <Search className="w-4 h-4" /> Filter
        </button>
        {(entityType || search) && (
          <button onClick={() => { setEntityType(''); setEntityId(''); setSearch(''); fetchDocs() }} className="btn-ghost text-sm text-[var(--text-secondary)]">
            <X className="w-4 h-4" /> Clear
          </button>
        )}
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 card">
          <FileText className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="text-[var(--text-secondary)] text-sm">No documents found. Upload your first document.</p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b border-[var(--border-default)] bg-[var(--surface-sunken)]/50">
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Doc #</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Title</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Type</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Category</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">File Name</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Size</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Version</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Entity</th>
                <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered.map(doc => (
                <Fragment key={doc.id}>
                  <tr
                    className="hover:bg-[var(--surface-sunken)] cursor-pointer transition-colors"
                    onClick={() => toggleExpand(doc.id)}
                  >
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)] font-mono">{doc.id.slice(0, 8)}</td>
                    <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{doc.name}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${typeColors[doc.type] || typeColors.OTHER}`}>
                        {doc.type}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{doc.category}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)] max-w-[160px] truncate">{doc.name}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{formatSize(doc.size)}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">v{doc.currentVersion}</td>
                    <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                      <span className="text-xs bg-[var(--surface-muted)] px-1.5 py-0.5 rounded">{doc.entityType}</span>
                      <span className="text-[var(--text-tertiary)] mx-1">/</span>
                      <span className="font-mono text-xs">{doc.entityId?.slice(0, 8)}</span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <PermissionGate resource="settings" action="edit">
                          <button
                            onClick={e => { e.stopPropagation(); openVersionUpload(doc.id) }}
                            className="p-1.5 hover:bg-[var(--surface-muted)] rounded text-[var(--text-secondary)] hover:text-[var(--text-secondary)]"
                            title="Upload new version"
                          >
                            <Upload className="w-4 h-4" />
                          </button>
                        </PermissionGate>
                        <PermissionGate resource="settings" action="delete">
                          <button
                            onClick={e => { e.stopPropagation(); handleDelete(doc.id) }}
                            className="p-1.5 hover:bg-[var(--nexus-error-50)] rounded text-[var(--text-secondary)] hover:text-[var(--nexus-error-600)]"
                            title="Delete"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </PermissionGate>
                      </div>
                    </td>
                  </tr>
                  {expandedId === doc.id && (
                    <tr key={`${doc.id}-versions`}>
                      <td colSpan={9} className="px-4 py-3 bg-[var(--surface-sunken)]/50">
                        <div className="pl-4 border-l-2 border-[var(--nexus-primary-200)]">
                          <div className="flex items-center justify-between mb-2">
                            <h4 className="text-sm font-semibold text-[var(--text-secondary)] flex items-center gap-1.5">
                              <Clock className="w-4 h-4" /> Document Versions
                            </h4>
                            <PermissionGate resource="settings" action="edit">
                              <button
                                onClick={e => { e.stopPropagation(); openVersionUpload(doc.id) }}
                                className="btn-secondary text-xs"
                              >
                                <Upload className="w-3.5 h-3.5" /> Upload New Version
                              </button>
                            </PermissionGate>
                          </div>
                          {versionsLoading ? (
                            <div className="flex items-center justify-center py-4">
                              <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-[var(--nexus-primary-600)]" />
                            </div>
                          ) : versions.length === 0 ? (
                            <p className="text-xs text-[var(--text-secondary)] py-2">No versions yet</p>
                          ) : (
                            <table className="w-full text-xs">
                              <thead>
                                <tr className="border-b border-[var(--border-default)]">
                                  <th className="px-3 py-2 text-left font-semibold text-[var(--text-secondary)]">Version</th>
                                  <th className="px-3 py-2 text-left font-semibold text-[var(--text-secondary)]">File Name</th>
                                  <th className="px-3 py-2 text-left font-semibold text-[var(--text-secondary)]">Size</th>
                                  <th className="px-3 py-2 text-left font-semibold text-[var(--text-secondary)]">Upload Date</th>
                                  <th className="px-3 py-2 text-left font-semibold text-[var(--text-secondary)]">Change Notes</th>
                                  <th className="px-3 py-2 text-left font-semibold text-[var(--text-secondary)]">Uploaded By</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-gray-100">
                                {versions.map(v => (
                                  <tr key={v.id} className="hover:bg-[var(--surface-sunken)]">
                                    <td className="px-3 py-2 text-[var(--text-secondary)] font-mono">v{v.version}</td>
                                    <td className="px-3 py-2 text-[var(--text-secondary)]">{v.url?.split('/').pop() || '-'}</td>
                                    <td className="px-3 py-2 text-[var(--text-secondary)]">{formatSize(v.size)}</td>
                                    <td className="px-3 py-2 text-[var(--text-secondary)]">{new Date(v.createdAt).toLocaleDateString()}</td>
                                    <td className="px-3 py-2 text-[var(--text-secondary)] max-w-[200px] truncate">{v.notes || '-'}</td>
                                    <td className="px-3 py-2 text-[var(--text-secondary)]">{v.uploadedBy?.slice(0, 8) || '-'}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showUploadModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-lg">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Upload Document</h2>
              <button onClick={() => setShowUploadModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Title *</label>
                <input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} className="input w-full" placeholder="Document title" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Description</label>
                <textarea value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} className="input w-full" rows={2} placeholder="Document description" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Document Type</label>
                <select value={form.documentType} onChange={e => setForm({ ...form, documentType: e.target.value })} className="input w-full">
                  <option value="INVOICE">Invoice</option>
                  <option value="CONTRACT">Contract</option>
                  <option value="REPORT">Report</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Category</label>
                <input value={form.category} onChange={e => setForm({ ...form, category: e.target.value })} className="input w-full" placeholder="e.g. Financial, Legal" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">File Name</label>
                  <input value={form.fileName} onChange={e => setForm({ ...form, fileName: e.target.value })} className="input w-full" placeholder="document.pdf" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">File Size (bytes)</label>
                  <input type="number" value={form.fileSize} onChange={e => setForm({ ...form, fileSize: Number(e.target.value) })} className="input w-full" placeholder="0" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">MIME Type</label>
                <input value={form.mimeType} onChange={e => setForm({ ...form, mimeType: e.target.value })} className="input w-full" placeholder="application/pdf" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">File URL</label>
                <input value={form.fileUrl} onChange={e => setForm({ ...form, fileUrl: e.target.value })} className="input w-full" placeholder="https://..." />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Entity Type</label>
                  <select value={form.entityType} onChange={e => setForm({ ...form, entityType: e.target.value })} className="input w-full">
                    <option value="">Select</option>
                    {entityTypes.map(et => <option key={et} value={et}>{et}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Entity ID</label>
                  <input value={form.entityId} onChange={e => setForm({ ...form, entityId: e.target.value })} className="input w-full" placeholder="Entity ID" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">
                  <Tag className="w-3.5 h-3.5 inline mr-1" /> Tags (comma-separated)
                </label>
                <input value={form.tags} onChange={e => setForm({ ...form, tags: e.target.value })} className="input w-full" placeholder="urgent, confidential" />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowUploadModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleUpload} disabled={saving} className="btn-primary text-sm">
                {saving ? <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" /> : <Upload className="w-4 h-4" />}
                Upload
              </button>
            </div>
          </div>
        </div>
      )}

      {showVersionModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-md">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Upload New Version</h2>
              <button onClick={() => setShowVersionModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">File Name</label>
                <input value={versionForm.fileName} onChange={e => setVersionForm({ ...versionForm, fileName: e.target.value })} className="input w-full" placeholder="v2-document.pdf" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">File Size (bytes)</label>
                <input type="number" value={versionForm.fileSize} onChange={e => setVersionForm({ ...versionForm, fileSize: Number(e.target.value) })} className="input w-full" placeholder="0" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">File URL</label>
                <input value={versionForm.fileUrl} onChange={e => setVersionForm({ ...versionForm, fileUrl: e.target.value })} className="input w-full" placeholder="https://..." />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Change Notes</label>
                <textarea value={versionForm.changeNotes} onChange={e => setVersionForm({ ...versionForm, changeNotes: e.target.value })} className="input w-full" rows={2} placeholder="What changed in this version?" />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowVersionModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleVersionUpload} disabled={saving} className="btn-primary text-sm">
                {saving ? <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" /> : <Upload className="w-4 h-4" />}
                Upload
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
