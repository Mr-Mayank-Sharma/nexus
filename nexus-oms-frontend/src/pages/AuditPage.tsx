import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Shield, Search, ChevronLeft, ChevronRight, Loader2,
  Clock, AlertTriangle, CheckCircle, XCircle, FileText,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseToolbar from '../components/enterprise/EnterpriseToolbar'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import * as auditApi from '../api/audit'
import PermissionGate from '../components/rbac/PermissionGate'
import type { AuditEntry } from '../api/audit'

export default function AuditPage() {
  const [page, setPage] = useState(0)
  const [searchTerm, setSearchTerm] = useState('')
  const [expandedEntry, setExpandedEntry] = useState<string | null>(null)
  const pageSize = 25

  const { data: response, isLoading } = useQuery({
    queryKey: ['audit-logs', page],
    queryFn: () => auditApi.getAuditLogs(page, pageSize),
  })

  const entries = (response?.data as auditApi.AuditPage | undefined)?.content || []
  const totalElements = (response?.data as auditApi.AuditPage | undefined)?.totalElements || 0
  const totalPages = (response?.data as auditApi.AuditPage | undefined)?.totalPages || 0

  const filtered = entries.filter(e =>
    !searchTerm || e.entityType?.toLowerCase().includes(searchTerm.toLowerCase()) ||
    e.action?.toLowerCase().includes(searchTerm.toLowerCase()) ||
    e.sourceSystem?.toLowerCase().includes(searchTerm.toLowerCase()) ||
    e.entityId?.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const statusIcon: Record<string, React.ReactNode> = {
    SUCCESS: <CheckCircle className="w-4 h-4 text-green-500" />,
    FAILED: <XCircle className="w-4 h-4 text-red-500" />,
    PENDING: <Clock className="w-4 h-4 text-yellow-500" />,
    ERROR: <AlertTriangle className="w-4 h-4 text-red-500" />,
  }

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'System' }, { label: 'Audit & Compliance' }]} />
      <EnterpriseToolbar
        title="Audit & Compliance"
        searchPlaceholder="Search by entity, action, system..."
        searchValue={searchTerm}
        onSearch={setSearchTerm}
        autocomplete={{
          fetchSuggestions: async (q) => {
            if (!q) return entries.slice(0, 10)
            const term = q.toLowerCase()
            return entries.filter(e =>
              e.entityType?.toLowerCase().includes(term) ||
              e.action?.toLowerCase().includes(term) ||
              e.sourceSystem?.toLowerCase().includes(term) ||
              e.entityId?.toLowerCase().includes(term)
            ).slice(0, 10)
          },
          onSelect: (item: any) => setSearchTerm(item.entityId || ''),
          getOptionLabel: (item: any) => `${item.entityType} — ${item.action} (${item.entityId})`,
          getOptionValue: (item: any) => item.id,
          minChars: 1,
        }}
      />

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <EnterpriseKPICard title="Total Events" value={totalElements} icon={<Shield />} color="primary" />
        <EnterpriseKPICard title="Success" value={entries.filter(e => e.status === 'SUCCESS').length} icon={<CheckCircle />} color="success" />
        <EnterpriseKPICard title="Failed" value={entries.filter(e => e.status === 'FAILED' || e.status === 'ERROR').length} icon={<XCircle />} color="error" />
        <EnterpriseKPICard title="This Page" value={entries.length} icon={<FileText />} color="info" />
      </div>

      {isLoading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="enterprise-skeleton h-14 w-full" />)}</div>
      ) : filtered.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <Shield className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No audit entries found</p>
        </div>
      ) : (
        <div className="enterprise-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="enterprise-table w-full">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Time</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Entity</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Action</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Source</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Target</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Duration</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {filtered.map(entry => (
                  <>
                    <tr key={entry.id} className="enterprise-table-row cursor-pointer" onClick={() => setExpandedEntry(expandedEntry === entry.id ? null : entry.id)}>
                      <td className="px-4 py-3 text-xs text-[var(--text-tertiary)] whitespace-nowrap">
                        {new Date(entry.createdAt).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 text-sm">
                        <span className="text-xs font-medium text-[var(--text-secondary)]">{entry.entityType}</span>
                        <span className="text-xs font-mono text-[var(--text-tertiary)] ml-1">#{entry.entityId?.slice(0, 8)}</span>
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-primary)]">{entry.action}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5">
                          {statusIcon[entry.status] || <Clock className="w-4 h-4 text-gray-400" />}
                          <EnterpriseStatusBadge status={entry.status === 'SUCCESS' ? 'success' : entry.status === 'FAILED' || entry.status === 'ERROR' ? 'error' : 'pending'} label={entry.status} size="sm" />
                        </div>
                      </td>
                      <td className="px-4 py-3 text-xs text-[var(--text-secondary)]">{entry.sourceSystem || '-'}</td>
                      <td className="px-4 py-3 text-xs text-[var(--text-secondary)]">{entry.targetSystem || '-'}</td>
                      <td className="px-4 py-3 text-right text-xs text-[var(--text-tertiary)]">
                        {entry.processingTimeMs ? `${entry.processingTimeMs}ms` : '-'}
                      </td>
                    </tr>
                    {expandedEntry === entry.id && (
                      <tr key={`${entry.id}-detail`}>
                        <td colSpan={7} className="px-4 py-3 bg-[var(--bg-tertiary)]/30">
                          <div className="pl-4 border-l-2 border-[var(--color-primary)]/30 space-y-3 text-xs">
                            <div className="grid grid-cols-2 gap-4">
                              <div>
                                <p className="font-semibold text-[var(--text-secondary)] mb-1">Created By</p>
                                <p className="text-[var(--text-primary)]">{entry.createdBy || 'System'}</p>
                              </div>
                              <div>
                                <p className="font-semibold text-[var(--text-secondary)] mb-1">IP Address</p>
                                <p className="text-[var(--text-primary)]">{entry.ipAddress || '-'}</p>
                              </div>
                            </div>
                            {entry.errorMessage && (
                              <div>
                                <p className="font-semibold text-red-500 mb-1">Error</p>
                                <p className="text-red-400">{entry.errorMessage}</p>
                              </div>
                            )}
                            {entry.requestPayload && (
                              <div>
                                <p className="font-semibold text-[var(--text-secondary)] mb-1">Request Payload</p>
                                <pre className="bg-[var(--bg-tertiary)] p-2 rounded text-xs max-h-32 overflow-auto">{entry.requestPayload}</pre>
                              </div>
                            )}
                            {entry.responsePayload && (
                              <div>
                                <p className="font-semibold text-[var(--text-secondary)] mb-1">Response Payload</p>
                                <pre className="bg-[var(--bg-tertiary)] p-2 rounded text-xs max-h-32 overflow-auto">{entry.responsePayload}</pre>
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="flex items-center justify-between">
        <p className="text-sm text-[var(--text-tertiary)]">
          Page {page + 1} of {Math.max(totalPages, 1)} ({totalElements} total)
        </p>
        <div className="flex items-center gap-2">
          <button className="enterprise-btn-secondary text-xs" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
            <ChevronLeft className="w-4 h-4" /> Previous
          </button>
          <button className="enterprise-btn-secondary text-xs" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
            Next <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  )
}
