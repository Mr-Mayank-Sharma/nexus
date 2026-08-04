import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import {
  BarChart3, Plus, Calendar, Clock, FileText, Play,
  CheckCircle, Loader2, LayoutDashboard, FileBarChart, CalendarClock,
  X, FileBarChart2,
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import { fetchDashboardWidgets, generateReport, fetchReportTemplates, fetchScheduledReports, createScheduledReport } from '../api/newBackend'

type TabId = 'dashboard' | 'reports' | 'scheduled'

const asArray = (d: unknown): any[] => (Array.isArray(d) ? d : Array.isArray((d as any)?.content) ? (d as any).content : [])

const FORMATS = ['PDF', 'CSV', 'Excel']

export default function ReportBuilderPage() {
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<TabId>('dashboard')
  const [showCustomReport, setShowCustomReport] = useState(false)
  const [customReportName, setCustomReportName] = useState('')
  const [customDateRange, setCustomDateRange] = useState('last30')
  const [customFormat, setCustomFormat] = useState('PDF')
  const [showCreateSchedule, setShowCreateSchedule] = useState(false)
  const [scheduleForm, setScheduleForm] = useState({ name: '', report: '', frequency: 'Daily', recipients: '' })

  const { data: dashboard, isLoading: dashLoading } = useQuery({
    queryKey: ['report-dashboard'],
    queryFn: async () => {
      const res = await fetchDashboardWidgets().catch(() => null)
      return res?.data ?? res ?? {}
    },
  })

  const { data: reportTemplates = [], isLoading: templatesLoading } = useQuery({
    queryKey: ['report-templates'],
    queryFn: async () => {
      const res = await fetchReportTemplates().catch(() => null)
      return asArray(res?.data ?? res)
    },
  })

  const { data: scheduledReports = [], isLoading: schedulesLoading } = useQuery({
    queryKey: ['report-scheduled'],
    queryFn: async () => {
      const res = await fetchScheduledReports().catch(() => null)
      return asArray(res?.data ?? res)
    },
  })

  const runReport = useMutation({
    mutationFn: async ({ id, name }: { id: string; name: string }) => {
      return generateReport(id, 'PDF')
    },
    onSuccess: (res, vars) => {
      addToast({
        type: res?.success ? 'success' : 'error',
        title: res?.success ? 'Report queued' : 'Failed to queue report',
        message: vars.name,
      })
      queryClient.invalidateQueries({ queryKey: ['report-templates'] })
    },
  })

  const generateCustom = useMutation({
    mutationFn: async () => {
      return generateReport(customReportName || 'custom', customFormat)
    },
    onSuccess: (res) => {
      setShowCustomReport(false)
      addToast({ type: res?.success ? 'success' : 'error', title: res?.success ? 'Report generation queued' : 'Failed to generate report' })
    },
  })

  const scheduleMutation = useMutation({
    mutationFn: async () => {
      return createScheduledReport({ ...scheduleForm, dateRange: customDateRange })
    },
    onSuccess: (res) => {
      setShowCreateSchedule(false)
      setScheduleForm({ name: '', report: '', frequency: 'Daily', recipients: '' })
      addToast({ type: res?.success ? 'success' : 'error', title: res?.success ? 'Report scheduled' : 'Failed to schedule report' })
      queryClient.invalidateQueries({ queryKey: ['report-scheduled'] })
    },
  })

  const widgets = asArray(dashboard?.widgets)
  const kpis = [
    { label: 'Total Reports', value: dashboard?.totalReports ?? '—', icon: FileText, color: 'text-[var(--nexus-primary-600)]', bg: 'bg-[var(--nexus-primary-50)]' },
    { label: 'Scheduled Reports', value: dashboard?.scheduledReports ?? '—', icon: CalendarClock, color: 'text-[var(--nexus-success-600)]', bg: 'bg-[var(--nexus-success-50)]' },
    { label: 'Completed Reports', value: dashboard?.completedReports ?? '—', icon: CheckCircle, color: 'text-[var(--nexus-primary-600)]', bg: 'bg-[var(--nexus-primary-50)]' },
  ]

  function renderDashboard() {
    if (dashLoading) {
      return <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" /></div>
    }
    return (
      <>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
          {kpis.map(kpi => {
            const Icon = kpi.icon
            return (
              <div key={kpi.label} className="enterprise-card p-4 flex items-center gap-3">
                <div className={clsx('p-2.5 rounded-lg', kpi.bg)}>
                  <Icon className={clsx('w-5 h-5', kpi.color)} />
                </div>
                <div>
                  <p className="text-xs text-[var(--text-secondary)]">{kpi.label}</p>
                  <p className="text-lg font-bold text-[var(--text-primary)]">{kpi.value}</p>
                </div>
              </div>
            )
          })}
        </div>

        {widgets.length === 0 ? (
          <div className="enterprise-card p-12 text-center">
            <LayoutDashboard className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
            <p className="font-medium text-[var(--text-secondary)]">No dashboard widgets configured yet</p>
            <p className="text-sm text-[var(--text-tertiary)] mt-1">Saved dashboard widgets will appear here.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
            {widgets.map((widget: any) => (
              <div key={widget.id} className="enterprise-card">
                <div className="enterprise-card-header">
                  <h3 className="text-sm font-semibold text-[var(--text-primary)]">{widget.title || widget.name}</h3>
                </div>
                <div className="card-body">
                  <p className="text-xs text-[var(--text-secondary)]">{widget.type || 'widget'}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </>
    )
  }

  function renderReports() {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-[var(--text-primary)]">Report Templates</h2>
          <PermissionGate resource="reports" action="create">
            <button type="button" className="enterprise-btn enterprise-btn-primary" onClick={() => setShowCustomReport(true)}>
              <Plus className="w-4 h-4" /> Create Custom Report
            </button>
          </PermissionGate>
        </div>

        {templatesLoading ? (
          <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" /></div>
        ) : reportTemplates.length === 0 ? (
          <div className="enterprise-card p-12 text-center">
            <FileBarChart className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
            <p className="font-medium text-[var(--text-secondary)]">No report templates yet</p>
            <p className="text-sm text-[var(--text-tertiary)] mt-1">Create a custom report to get started.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {reportTemplates.map((t: any) => (
              <div key={t.id} className="enterprise-card p-5">
                <div className="flex items-start justify-between mb-3">
                  <div className="p-2 rounded-lg bg-[var(--nexus-primary-50)]">
                    <FileBarChart2 className="w-5 h-5 text-[var(--nexus-primary-600)]" />
                  </div>
                  <PermissionGate resource="reports" action="create">
                    <button type="button" className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm" disabled={runReport.isPending} onClick={() => runReport.mutate({ id: t.id, name: t.name })}>
                      <Play className="w-3.5 h-3.5" /> Run Now
                    </button>
                  </PermissionGate>
                </div>
                <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-1">{t.name}</h3>
                {t.description && <p className="text-xs text-[var(--text-secondary)] mb-3">{t.description}</p>}
                <div className="flex items-center gap-1.5 text-xs text-[var(--text-tertiary)]">
                  <Clock className="w-3 h-3" />
                  Last run: {t.lastRun || t.lastRunAt || '—'}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    )
  }

  function renderScheduled() {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-[var(--text-primary)]">Scheduled Reports</h2>
          <PermissionGate resource="reports" action="create">
            <button type="button" className="enterprise-btn enterprise-btn-primary" onClick={() => setShowCreateSchedule(true)}>
              <Plus className="w-4 h-4" /> Create Schedule
            </button>
          </PermissionGate>
        </div>

        {schedulesLoading ? (
          <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" /></div>
        ) : scheduledReports.length === 0 ? (
          <div className="enterprise-card p-12 text-center">
            <CalendarClock className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
            <p className="font-medium text-[var(--text-secondary)]">No schedules yet</p>
            <p className="text-sm text-[var(--text-tertiary)] mt-1">Create a schedule to automate report deliveries.</p>
          </div>
        ) : (
          <div className="enterprise-card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="enterprise-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Report</th>
                    <th>Frequency</th>
                    <th>Recipients</th>
                    <th>Next Send</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {scheduledReports.map((s: any) => (
                    <tr key={s.id}>
                      <td className="font-medium text-[var(--text-primary)]">{s.name}</td>
                      <td>{s.report || '—'}</td>
                      <td><span className="enterprise-badge enterprise-badge-neutral">{s.frequency || '—'}</span></td>
                      <td className="text-xs text-[var(--text-secondary)]">{s.recipients || '—'}</td>
                      <td className="text-sm">{s.nextSend || s.nextRunAt || '—'}</td>
                      <td>
                        <span className={clsx('enterprise-badge', String(s.status).toLowerCase() === 'active' ? 'enterprise-badge-success' : 'enterprise-badge-warning')}>
                          {String(s.status || '—')}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <BarChart3 className="w-5 h-5" /> Report Builder &amp; BI
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Custom reports, saved dashboards &amp; scheduled exports</p>
        </div>
      </div>

      <div className="enterprise-tabs">
        {([
          { id: 'dashboard' as TabId, label: 'Dashboard', icon: LayoutDashboard },
          { id: 'reports' as TabId, label: 'Reports', icon: FileBarChart },
          { id: 'scheduled' as TabId, label: 'Scheduled', icon: CalendarClock },
        ]).map(tab => {
          const Icon = tab.icon
          return (
            <button
              key={tab.id}
              className={clsx('enterprise-tab flex items-center gap-1.5', activeTab === tab.id && 'enterprise-tab-active')}
              onClick={() => setActiveTab(tab.id)}
            >
              <Icon className="w-4 h-4" />
              {tab.label}
            </button>
          )
        })}
      </div>

      {activeTab === 'dashboard' && renderDashboard()}
      {activeTab === 'reports' && renderReports()}
      {activeTab === 'scheduled' && renderScheduled()}

      {showCustomReport && (
        <div className="enterprise-modal-overlay" onClick={() => setShowCustomReport(false)}>
          <div className="enterprise-modal !min-w-[560px]" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-base font-semibold text-[var(--text-primary)]">Create Custom Report</h2>
              <button type="button" className="enterprise-btn enterprise-btn-ghost enterprise-btn-icon" onClick={() => setShowCustomReport(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="enterprise-modal-body space-y-5">
              <div className="enterprise-form-group">
                <label>Report Name</label>
                <Autocomplete value={customReportName} onChange={setCustomReportName} placeholder="e.g. Monthly Operations Summary" minChars={0} inputClassName="enterprise-input w-full" />
              </div>
              <div className="enterprise-form-group">
                <label>Date Range</label>
                <Autocomplete value={customDateRange} onChange={setCustomDateRange} suggestions={['today', 'last7', 'last30', 'last90', 'thisYear', 'custom']} minChars={0} />
              </div>
              <div className="enterprise-form-group">
                <label>Format</label>
                <Autocomplete value={customFormat} onChange={setCustomFormat} suggestions={FORMATS} minChars={0} />
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button type="button" className="enterprise-btn enterprise-btn-secondary" onClick={() => setShowCustomReport(false)}>Cancel</button>
              <PermissionGate resource="reports" action="create">
                <button type="button" className="enterprise-btn enterprise-btn-primary" disabled={generateCustom.isPending} onClick={() => generateCustom.mutate()}>
                  {generateCustom.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                  {generateCustom.isPending ? 'Queuing...' : 'Generate Report'}
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {showCreateSchedule && (
        <div className="enterprise-modal-overlay" onClick={() => setShowCreateSchedule(false)}>
          <div className="enterprise-modal" onClick={e => e.stopPropagation()}>
            <div className="enterprise-modal-header">
              <h2 className="text-base font-semibold text-[var(--text-primary)]">Create Schedule</h2>
              <button type="button" className="enterprise-btn enterprise-btn-ghost enterprise-btn-icon" onClick={() => setShowCreateSchedule(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="enterprise-modal-body space-y-4">
              <div className="enterprise-form-group">
                <label>Schedule Name</label>
                <Autocomplete value={scheduleForm.name} onChange={val => setScheduleForm(p => ({ ...p, name: val }))} placeholder="e.g. Weekly Ops Report" minChars={0} inputClassName="enterprise-input w-full" />
              </div>
              <div className="enterprise-form-group">
                <label>Report</label>
                <Autocomplete value={scheduleForm.report} onChange={val => setScheduleForm(p => ({ ...p, report: val }))} suggestions={reportTemplates.map((t: any) => t.name)} minChars={0} />
              </div>
              <div className="enterprise-form-row">
                <div className="enterprise-form-group">
                  <label>Frequency</label>
                  <Autocomplete value={scheduleForm.frequency} onChange={val => setScheduleForm(p => ({ ...p, frequency: val }))} suggestions={['Daily', 'Weekly', 'Monthly']} minChars={0} />
                </div>
                <div className="enterprise-form-group">
                  <label>Recipients (comma-separated)</label>
                  <Autocomplete value={scheduleForm.recipients} onChange={val => setScheduleForm(p => ({ ...p, recipients: val }))} placeholder="email@domain.com" minChars={0} inputClassName="enterprise-input" />
                </div>
              </div>
            </div>
            <div className="enterprise-modal-footer">
              <button type="button" className="enterprise-btn enterprise-btn-secondary" onClick={() => setShowCreateSchedule(false)}>Cancel</button>
              <PermissionGate resource="reports" action="create">
                <button type="button" className="enterprise-btn enterprise-btn-primary" disabled={scheduleMutation.isPending} onClick={() => scheduleMutation.mutate()}>
                  <Calendar className="w-4 h-4" /> Create Schedule
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
