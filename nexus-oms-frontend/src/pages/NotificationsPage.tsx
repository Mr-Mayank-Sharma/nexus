import { useState, useEffect } from 'react'
import { Bell, BellRing, FileText, AlertTriangle, Plus, Send, Search, X, Check, Loader2, ChevronDown, ChevronRight } from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as notificationsApi from '../api/notifications'

const channelStyles: Record<string, string> = {
  EMAIL: 'bg-blue-100 text-blue-800',
  SMS: 'bg-green-100 text-green-800',
  PUSH: 'bg-purple-100 text-purple-800',
  IN_APP: 'bg-gray-100 text-gray-700',
  SLACK: 'bg-orange-100 text-orange-800',
}

const severityStyles: Record<string, string> = {
  INFO: 'bg-blue-100 text-blue-800',
  WARNING: 'bg-yellow-100 text-yellow-800',
  CRITICAL: 'bg-red-100 text-red-800',
}

const logStatusStyles: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  SENT: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  DELIVERED: 'bg-green-100 text-green-800',
  READ: 'bg-blue-100 text-blue-800',
}

type Tab = 'templates' | 'alertRules'

export default function NotificationsPage() {
  const [activeTab, setActiveTab] = useState<Tab>('templates')
  const { addToast } = useToast()

  // Templates
  const [templates, setTemplates] = useState<notificationsApi.NotificationTemplate[]>([])
  const [templatesLoading, setTemplatesLoading] = useState(true)
  const [showTemplateModal, setShowTemplateModal] = useState(false)
  const [editingTemplate, setEditingTemplate] = useState<notificationsApi.NotificationTemplate | null>(null)
  const [savingTemplate, setSavingTemplate] = useState(false)
  const [templateForm, setTemplateForm] = useState({ code: '', name: '', channel: 'EMAIL' as const, subject: '', body: '', variables: '', isActive: true })

  // Alert Rules
  const [alertRules, setAlertRules] = useState<notificationsApi.AlertRule[]>([])
  const [alertRulesLoading, setAlertRulesLoading] = useState(false)
  const [showAlertRuleModal, setShowAlertRuleModal] = useState(false)
  const [savingAlertRule, setSavingAlertRule] = useState(false)
  const [alertRuleForm, setAlertRuleForm] = useState({ name: '', description: '', eventType: '', severity: 'INFO', channel: 'EMAIL', recipientList: '', throttleMinutes: 5, isActive: true })

  // Notification Logs
  const [logs, setLogs] = useState<notificationsApi.NotificationLog[]>([])
  const [logsLoading, setLogsLoading] = useState(false)
  const [logsExpanded, setLogsExpanded] = useState(false)
  const [expandedLogId, setExpandedLogId] = useState<string | null>(null)

  // Unread Count
  const [unreadCount, setUnreadCount] = useState(0)

  // Send Test
  const [showSendModal, setShowSendModal] = useState(false)
  const [sending, setSending] = useState(false)
  const [sendForm, setSendForm] = useState({ channel: 'EMAIL', recipient: '', templateCode: '', variables: '{}' })

  useEffect(() => { fetchTemplates(); fetchUnreadCount() }, [])

  useEffect(() => {
    if (activeTab === 'alertRules') fetchAlertRules()
  }, [activeTab])

  useEffect(() => {
    if (logsExpanded) fetchLogs()
  }, [logsExpanded])

  async function fetchTemplates() {
    try {
      setTemplatesLoading(true)
      const res = await notificationsApi.getTemplates(0, 100)
      setTemplates(res.data.content)
    } catch { addToast({ type: 'error', title: 'Failed to load templates' })
    } finally { setTemplatesLoading(false) }
  }

  async function fetchAlertRules() {
    try {
      setAlertRulesLoading(true)
      const res = await notificationsApi.getAlertRules(0, 100)
      setAlertRules(res.data.content)
    } catch { addToast({ type: 'error', title: 'Failed to load alert rules' })
    } finally { setAlertRulesLoading(false) }
  }

  async function fetchLogs() {
    try {
      setLogsLoading(true)
      const res = await notificationsApi.getNotificationLogs(0, 50)
      setLogs(res.data.content)
    } catch { addToast({ type: 'error', title: 'Failed to load logs' })
    } finally { setLogsLoading(false) }
  }

  async function fetchUnreadCount() {
    try {
      const res = await notificationsApi.getUnreadCount()
      setUnreadCount(res.data.count)
    } catch { /* ignore */ }
  }

  function openCreateTemplate() {
    setEditingTemplate(null)
    setTemplateForm({ code: '', name: '', channel: 'EMAIL', subject: '', body: '', variables: '', isActive: true })
    setShowTemplateModal(true)
  }

  function openEditTemplate(tpl: notificationsApi.NotificationTemplate) {
    setEditingTemplate(tpl)
    setTemplateForm({
      code: tpl.code,
      name: tpl.name,
      channel: tpl.channel as 'EMAIL',
      subject: tpl.subject,
      body: tpl.body,
      variables: (tpl.variables || []).join(', '),
      isActive: tpl.isActive,
    })
    setShowTemplateModal(true)
  }

  async function handleSaveTemplate() {
    if (!templateForm.name.trim() || !templateForm.code.trim()) {
      addToast({ type: 'warning', title: 'Name and code are required' }); return
    }
    setSavingTemplate(true)
    try {
      const payload = {
        ...templateForm,
        variables: templateForm.variables.split(',').map(v => v.trim()).filter(Boolean),
      }
      if (editingTemplate) {
        await notificationsApi.updateTemplate(editingTemplate.id, payload)
        addToast({ type: 'success', title: 'Template updated' })
      } else {
        await notificationsApi.createTemplate(payload)
        addToast({ type: 'success', title: 'Template created' })
      }
      setShowTemplateModal(false)
      await fetchTemplates()
    } catch { addToast({ type: 'error', title: 'Failed to save template' })
    } finally { setSavingTemplate(false) }
  }

  async function handleToggleTemplateActive(tpl: notificationsApi.NotificationTemplate) {
    try {
      await notificationsApi.updateTemplate(tpl.id, { isActive: !tpl.isActive })
      await fetchTemplates()
    } catch { addToast({ type: 'error', title: 'Failed to toggle template' }) }
  }

  function openCreateAlertRule() {
    setAlertRuleForm({ name: '', description: '', eventType: '', severity: 'INFO', channel: 'EMAIL', recipientList: '', throttleMinutes: 5, isActive: true })
    setShowAlertRuleModal(true)
  }

  async function handleSaveAlertRule() {
    if (!alertRuleForm.name.trim()) {
      addToast({ type: 'warning', title: 'Rule name is required' }); return
    }
    setSavingAlertRule(true)
    try {
      const payload = {
        name: alertRuleForm.name,
        description: alertRuleForm.description,
        metric: alertRuleForm.eventType,
        severity: alertRuleForm.severity,
        channel: alertRuleForm.channel,
        recipients: alertRuleForm.recipientList.split(',').map(r => r.trim()).filter(Boolean),
        cooldownMinutes: Number(alertRuleForm.throttleMinutes),
        isActive: alertRuleForm.isActive,
      }
      await notificationsApi.createAlertRule(payload)
      addToast({ type: 'success', title: 'Alert rule created' })
      setShowAlertRuleModal(false)
      await fetchAlertRules()
    } catch { addToast({ type: 'error', title: 'Failed to save alert rule' })
    } finally { setSavingAlertRule(false) }
  }

  async function handleToggleAlertRuleActive(rule: notificationsApi.AlertRule) {
    try {
      await notificationsApi.toggleAlertRule(rule.id)
      await fetchAlertRules()
    } catch { addToast({ type: 'error', title: 'Failed to toggle alert rule' }) }
  }

  async function handleSendTest() {
    if (!sendForm.recipient.trim() || !sendForm.templateCode.trim()) {
      addToast({ type: 'warning', title: 'Recipient and template are required' }); return
    }
    setSending(true)
    try {
      let variables = {}
      try { variables = JSON.parse(sendForm.variables) } catch { variables = {} }
      await notificationsApi.sendNotification(sendForm.channel, sendForm.recipient, sendForm.templateCode, variables)
      addToast({ type: 'success', title: 'Test notification sent' })
      setShowSendModal(false)
    } catch { addToast({ type: 'error', title: 'Failed to send test notification' })
    } finally { setSending(false) }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Bell className="w-6 h-6 text-gray-700" />
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Notifications</h1>
            <p className="text-sm text-gray-500 mt-1">Manage templates, alert rules, and notification logs</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          {unreadCount > 0 && (
            <div className="flex items-center gap-1.5 px-3 py-1.5 bg-red-50 border border-red-200 rounded-full text-sm text-red-700 font-medium">
              <BellRing className="w-4 h-4" />
              {unreadCount} unread
            </div>
          )}
          <button onClick={() => setShowSendModal(true)} className="btn-secondary text-sm">
            <Send className="w-4 h-4" /> Send Test
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        <button
          onClick={() => setActiveTab('templates')}
          className={clsx(
            'flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
            activeTab === 'templates'
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          )}
        >
          <FileText className="w-4 h-4" /> Templates
        </button>
        <button
          onClick={() => setActiveTab('alertRules')}
          className={clsx(
            'flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
            activeTab === 'alertRules'
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          )}
        >
          <AlertTriangle className="w-4 h-4" /> Alert Rules
        </button>
      </div>

      {/* Templates Tab */}
      {activeTab === 'templates' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-gray-500">{templates.length} template{templates.length !== 1 ? 's' : ''}</p>
            <button onClick={openCreateTemplate} className="btn-primary text-sm">
              <Plus className="w-4 h-4" /> Create Template
            </button>
          </div>

          {templatesLoading ? (
            <div className="flex items-center justify-center h-64">
              <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
            </div>
          ) : templates.length === 0 ? (
            <div className="text-center py-16 card">
              <FileText className="w-12 h-12 text-gray-300 mx-auto mb-3" />
              <p className="text-gray-500 text-sm">No templates yet. Create your first notification template.</p>
            </div>
          ) : (
            <div className="card overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50">
                    <th className="text-left px-4 py-3 font-medium text-gray-600 text-xs uppercase tracking-wider">Template Code</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600 text-xs uppercase tracking-wider">Name</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600 text-xs uppercase tracking-wider">Channel</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600 text-xs uppercase tracking-wider">Subject</th>
                    <th className="text-center px-4 py-3 font-medium text-gray-600 text-xs uppercase tracking-wider">Active</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {templates.map(tpl => (
                    <tr
                      key={tpl.id}
                      className="hover:bg-gray-50 cursor-pointer transition-colors"
                      onClick={() => openEditTemplate(tpl)}
                    >
                      <td className="px-4 py-3 font-mono text-xs text-gray-500">{tpl.code}</td>
                      <td className="px-4 py-3 font-medium text-gray-900">{tpl.name}</td>
                      <td className="px-4 py-3">
                        <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', channelStyles[tpl.channel] || 'bg-gray-100 text-gray-700')}>
                          {tpl.channel}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600 max-w-[200px] truncate">{tpl.subject}</td>
                      <td className="px-4 py-3 text-center">
                        <button
                          onClick={(e) => { e.stopPropagation(); handleToggleTemplateActive(tpl) }}
                          className={clsx(
                            'relative inline-flex h-5 w-9 items-center rounded-full transition-colors',
                            tpl.isActive ? 'bg-green-500' : 'bg-gray-300'
                          )}
                        >
                          <span className={clsx('inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform', tpl.isActive ? 'translate-x-[18px]' : 'translate-x-[3px]')} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Alert Rules Tab */}
      {activeTab === 'alertRules' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-gray-500">{alertRules.length} rule{alertRules.length !== 1 ? 's' : ''}</p>
            <button onClick={openCreateAlertRule} className="btn-primary text-sm">
              <Plus className="w-4 h-4" /> Create Alert Rule
            </button>
          </div>

          {alertRulesLoading ? (
            <div className="flex items-center justify-center h-64">
              <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
            </div>
          ) : alertRules.length === 0 ? (
            <div className="text-center py-16 card">
              <AlertTriangle className="w-12 h-12 text-gray-300 mx-auto mb-3" />
              <p className="text-gray-500 text-sm">No alert rules configured. Create rules to get notified of important events.</p>
            </div>
          ) : (
            <div className="card overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50">
                    <th className="text-left px-4 py-3 font-medium text-gray-600 text-xs uppercase tracking-wider">Name</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600 text-xs uppercase tracking-wider">Event Type</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600 text-xs uppercase tracking-wider">Severity</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600 text-xs uppercase tracking-wider">Channel</th>
                    <th className="text-center px-4 py-3 font-medium text-gray-600 text-xs uppercase tracking-wider">Active</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {alertRules.map(rule => (
                    <tr key={rule.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-900">{rule.name}</td>
                      <td className="px-4 py-3 text-gray-600">{rule.metric || '-'}</td>
                      <td className="px-4 py-3">
                        <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', severityStyles['INFO'])}>
                          INFO
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', channelStyles[rule.channel] || 'bg-gray-100 text-gray-700')}>
                          {rule.channel}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <button
                          onClick={() => handleToggleAlertRuleActive(rule)}
                          className={clsx(
                            'relative inline-flex h-5 w-9 items-center rounded-full transition-colors',
                            rule.isActive ? 'bg-green-500' : 'bg-gray-300'
                          )}
                        >
                          <span className={clsx('inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform', rule.isActive ? 'translate-x-[18px]' : 'translate-x-[3px]')} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Notification Logs */}
      <div className="card">
        <button
          onClick={() => setLogsExpanded(!logsExpanded)}
          className="w-full flex items-center justify-between p-4 hover:bg-gray-50 transition-colors"
        >
          <div className="flex items-center gap-2">
            {logsExpanded ? <ChevronDown className="w-5 h-5 text-gray-500" /> : <ChevronRight className="w-5 h-5 text-gray-500" />}
            <h3 className="text-sm font-semibold text-gray-900">Notification Logs</h3>
          </div>
          {logs.length > 0 && <span className="text-xs text-gray-500">{logs.length} entries</span>}
        </button>

        {logsExpanded && (
          <div className="border-t border-gray-100">
            {logsLoading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 className="w-6 h-6 animate-spin text-primary-600" />
              </div>
            ) : logs.length === 0 ? (
              <div className="text-center py-12">
                <p className="text-gray-400 text-sm">No notification logs yet.</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 bg-gray-50">
                      <th className="text-left px-4 py-2.5 font-medium text-gray-600 text-xs uppercase tracking-wider">Channel</th>
                      <th className="text-left px-4 py-2.5 font-medium text-gray-600 text-xs uppercase tracking-wider">Recipient</th>
                      <th className="text-left px-4 py-2.5 font-medium text-gray-600 text-xs uppercase tracking-wider">Subject</th>
                      <th className="text-left px-4 py-2.5 font-medium text-gray-600 text-xs uppercase tracking-wider">Status</th>
                      <th className="text-left px-4 py-2.5 font-medium text-gray-600 text-xs uppercase tracking-wider">Sent At</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {logs.map(log => (
                      <>
                        <tr
                          key={log.id}
                          className="hover:bg-gray-50 cursor-pointer transition-colors"
                          onClick={() => setExpandedLogId(expandedLogId === log.id ? null : log.id)}
                        >
                          <td className="px-4 py-2.5">
                            <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', channelStyles[log.channel] || 'bg-gray-100 text-gray-700')}>
                              {log.channel}
                            </span>
                          </td>
                          <td className="px-4 py-2.5 text-gray-700">{log.recipient}</td>
                          <td className="px-4 py-2.5 text-gray-600 max-w-[200px] truncate">{log.subject}</td>
                          <td className="px-4 py-2.5">
                            <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', logStatusStyles[log.status] || 'bg-gray-100 text-gray-700')}>
                              {log.status}
                            </span>
                          </td>
                          <td className="px-4 py-2.5 text-gray-500 text-xs">
                            {log.sentAt ? new Date(log.sentAt).toLocaleString() : '-'}
                          </td>
                        </tr>
                        {expandedLogId === log.id && (
                          <tr key={`${log.id}-body`} className="bg-gray-50">
                            <td colSpan={5} className="px-4 py-3">
                              <div className="text-xs text-gray-600 space-y-1">
                                <p className="font-medium text-gray-700">Body:</p>
                                <p className="whitespace-pre-wrap font-mono bg-white p-2 rounded border border-gray-200">{log.body}</p>
                                {log.errorMessage && (
                                  <p className="text-red-600">Error: {log.errorMessage}</p>
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
            )}
          </div>
        )}
      </div>

      {/* Create / Edit Template Modal */}
      {showTemplateModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">{editingTemplate ? 'Edit Template' : 'Create Template'}</h2>
              <button onClick={() => setShowTemplateModal(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Template Code</label>
                <input value={templateForm.code} onChange={e => setTemplateForm({ ...templateForm, code: e.target.value })} className="input w-full" placeholder="e.g. order_confirmation" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
                <input value={templateForm.name} onChange={e => setTemplateForm({ ...templateForm, name: e.target.value })} className="input w-full" placeholder="e.g. Order Confirmation" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Channel</label>
                <select value={templateForm.channel} onChange={e => setTemplateForm({ ...templateForm, channel: e.target.value as 'EMAIL' })} className="input w-full">
                  <option value="EMAIL">Email</option>
                  <option value="SMS">SMS</option>
                  <option value="PUSH">Push</option>
                  <option value="IN_APP">In-App</option>
                  <option value="SLACK">Slack</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Subject</label>
                <input value={templateForm.subject} onChange={e => setTemplateForm({ ...templateForm, subject: e.target.value })} className="input w-full" placeholder="e.g. Your order #{{orderNumber}} is confirmed" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Body</label>
                <textarea value={templateForm.body} onChange={e => setTemplateForm({ ...templateForm, body: e.target.value })} className="input w-full" rows={4} placeholder="Email/SMS body content with {{variables}}" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Variables (comma-separated)</label>
                <input value={templateForm.variables} onChange={e => setTemplateForm({ ...templateForm, variables: e.target.value })} className="input w-full" placeholder="e.g. orderNumber, customerName, total" />
              </div>
              <div className="flex items-center gap-2">
                <label className="text-sm font-medium text-gray-700">Active</label>
                <button
                  onClick={() => setTemplateForm({ ...templateForm, isActive: !templateForm.isActive })}
                  className={clsx('relative inline-flex h-5 w-9 items-center rounded-full transition-colors', templateForm.isActive ? 'bg-green-500' : 'bg-gray-300')}
                >
                  <span className={clsx('inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform', templateForm.isActive ? 'translate-x-[18px]' : 'translate-x-[3px]')} />
                </button>
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowTemplateModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleSaveTemplate} disabled={savingTemplate} className="btn-primary text-sm">
                {savingTemplate && <Loader2 className="w-4 h-4 animate-spin" />}
                {editingTemplate ? 'Update' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create Alert Rule Modal */}
      {showAlertRuleModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Create Alert Rule</h2>
              <button onClick={() => setShowAlertRuleModal(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Rule Name</label>
                <input value={alertRuleForm.name} onChange={e => setAlertRuleForm({ ...alertRuleForm, name: e.target.value })} className="input w-full" placeholder="e.g. High Order Failure Rate" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea value={alertRuleForm.description} onChange={e => setAlertRuleForm({ ...alertRuleForm, description: e.target.value })} className="input w-full" rows={2} placeholder="Describe when this alert triggers" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Event Type</label>
                <input value={alertRuleForm.eventType} onChange={e => setAlertRuleForm({ ...alertRuleForm, eventType: e.target.value })} className="input w-full" placeholder="e.g. ORDER_FAILURE, INVENTORY_LOW" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Severity</label>
                <select value={alertRuleForm.severity} onChange={e => setAlertRuleForm({ ...alertRuleForm, severity: e.target.value })} className="input w-full">
                  <option value="INFO">Info</option>
                  <option value="WARNING">Warning</option>
                  <option value="CRITICAL">Critical</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Channel</label>
                <select value={alertRuleForm.channel} onChange={e => setAlertRuleForm({ ...alertRuleForm, channel: e.target.value })} className="input w-full">
                  <option value="EMAIL">Email</option>
                  <option value="SMS">SMS</option>
                  <option value="PUSH">Push</option>
                  <option value="IN_APP">In-App</option>
                  <option value="SLACK">Slack</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Recipient List (comma-separated emails)</label>
                <input value={alertRuleForm.recipientList} onChange={e => setAlertRuleForm({ ...alertRuleForm, recipientList: e.target.value })} className="input w-full" placeholder="e.g. ops@example.com, alerts@example.com" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Throttle (minutes)</label>
                <input type="number" value={alertRuleForm.throttleMinutes} onChange={e => setAlertRuleForm({ ...alertRuleForm, throttleMinutes: Number(e.target.value) })} className="input w-full" min={1} />
              </div>
              <div className="flex items-center gap-2">
                <label className="text-sm font-medium text-gray-700">Active</label>
                <button
                  onClick={() => setAlertRuleForm({ ...alertRuleForm, isActive: !alertRuleForm.isActive })}
                  className={clsx('relative inline-flex h-5 w-9 items-center rounded-full transition-colors', alertRuleForm.isActive ? 'bg-green-500' : 'bg-gray-300')}
                >
                  <span className={clsx('inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform', alertRuleForm.isActive ? 'translate-x-[18px]' : 'translate-x-[3px]')} />
                </button>
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowAlertRuleModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleSaveAlertRule} disabled={savingAlertRule} className="btn-primary text-sm">
                {savingAlertRule && <Loader2 className="w-4 h-4 animate-spin" />}
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Send Test Modal */}
      {showSendModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Send Test Notification</h2>
              <button onClick={() => setShowSendModal(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Channel</label>
                <select value={sendForm.channel} onChange={e => setSendForm({ ...sendForm, channel: e.target.value })} className="input w-full">
                  <option value="EMAIL">Email</option>
                  <option value="SMS">SMS</option>
                  <option value="PUSH">Push</option>
                  <option value="IN_APP">In-App</option>
                  <option value="SLACK">Slack</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Recipient</label>
                <input value={sendForm.recipient} onChange={e => setSendForm({ ...sendForm, recipient: e.target.value })} className="input w-full" placeholder="e.g. user@example.com or +1234567890" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Template</label>
                <select value={sendForm.templateCode} onChange={e => setSendForm({ ...sendForm, templateCode: e.target.value })} className="input w-full">
                  <option value="">Select a template...</option>
                  {templates.map(tpl => (
                    <option key={tpl.id} value={tpl.code}>{tpl.code} - {tpl.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Variables (JSON)</label>
                <textarea value={sendForm.variables} onChange={e => setSendForm({ ...sendForm, variables: e.target.value })} className="input w-full font-mono text-xs" rows={3} placeholder='{"orderNumber": "ORD-001", "customerName": "John"}' />
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowSendModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleSendTest} disabled={sending} className="btn-primary text-sm">
                {sending && <Loader2 className="w-4 h-4 animate-spin" />}
                <Send className="w-4 h-4" /> Send
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
