import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Shield, CheckCircle, XCircle, Clock, Search, Eye, Plus,
  AlertTriangle, Filter, ToggleLeft, ToggleRight, Edit, Trash2,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import { approvalsApi, ApprovalRule, OrderApproval, ApprovalStats } from '../api/approvals'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'
import type { Tab } from '../components/enterprise'

type ApprovalTab = 'pending' | 'rules' | 'all'

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]',
  APPROVED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  REJECTED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
  MANUAL_REVIEW: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-800)]',
}

const RULE_TYPE_LABELS: Record<string, string> = {
  AMOUNT_THRESHOLD: 'Amount Threshold',
  VELOCITY: 'Velocity Check',
  GEOLOCATION: 'Geolocation',
  FRAUD_SCORE: 'Fraud Score',
  BLACKLIST: 'Blacklist',
  REPEAT_CUSTOMER: 'Repeat Customer',
}

const ACTION_COLORS: Record<string, string> = {
  AUTO_APPROVE: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  HOLD_FOR_REVIEW: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]',
  REJECT: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
}

export default function OrderApprovalsPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<ApprovalTab>('pending')
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedApproval, setSelectedApproval] = useState<OrderApproval | null>(null)

  const tabs: Tab[] = [
    { key: 'pending', label: 'Pending Review', icon: Clock },
    { key: 'rules', label: 'Approval Rules', icon: Shield },
    { key: 'all', label: 'All Approvals', icon: CheckCircle },
  ]

  const { data: pendingApprovals = [], isLoading: loadingPending } = useQuery({
    queryKey: ['approvals-pending'],
    queryFn: async () => {
      const res = await approvalsApi.getPendingReviews()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'pending',
  })

  const { data: rules = [], isLoading: loadingRules } = useQuery({
    queryKey: ['approval-rules'],
    queryFn: async () => {
      const res = await approvalsApi.getRules()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'rules',
  })

  const { data: allApprovals = [], isLoading: loadingAll } = useQuery({
    queryKey: ['approvals-all'],
    queryFn: async () => {
      const res = await approvalsApi.getAllApprovals()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'all',
  })

  const { data: stats } = useQuery({
    queryKey: ['approval-stats'],
    queryFn: async () => {
      const res = await approvalsApi.getApprovalStats()
      return res.data as ApprovalStats
    },
  })

  const reviewMutation = useMutation({
    mutationFn: ({ id, decision, reviewer, notes }: { id: string; decision: string; reviewer: string; notes?: string }) =>
      approvalsApi.manualReview(id, decision, reviewer, notes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approvals-pending'] })
      queryClient.invalidateQueries({ queryKey: ['approvals-all'] })
      queryClient.invalidateQueries({ queryKey: ['approval-stats'] })
      addToast({ type: 'success', title: 'Review submitted' })
      setSelectedApproval(null)
    },
    onError: () => addToast({ type: 'error', title: 'Failed to submit review' }),
  })

  const toggleRuleMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      approvalsApi.updateRule(id, { active }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approval-rules'] })
      addToast({ type: 'success', title: 'Rule updated' })
    },
    onError: () => addToast({ type: 'error', title: 'Failed to update rule' }),
  })

  const filteredPending = pendingApprovals.filter(a =>
    a.orderNumber.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const filteredRules = rules.filter(r =>
    !searchTerm || r.name?.toLowerCase().includes(searchTerm.toLowerCase()) || r.ruleType.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const filteredAll = allApprovals.filter(a =>
    a.orderNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
    a.status.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const formatDate = (d?: string) => d ? new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '—'

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Order Approvals</h1>
          <p className="text-[var(--text-secondary)] mt-1">Review and approve orders based on risk rules</p>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Total Approvals"
          value={stats?.totalApprovals || 0}
          icon={Shield}
          color="blue"
        />
        <EnterpriseKPICard
          title="Pending Review"
          value={stats?.pending || 0}
          icon={Clock}
          color="yellow"
        />
        <EnterpriseKPICard
          title="Approved"
          value={stats?.approved || 0}
          icon={CheckCircle}
          color="green"
        />
        <EnterpriseKPICard
          title="Rejected"
          value={stats?.rejected || 0}
          icon={XCircle}
          color="red"
        />
      </div>

      <EnterpriseTabs
        tabs={tabs}
        activeTab={activeTab}
        onTabChange={(key) => setActiveTab(key as ApprovalTab)}
      />

      <div className="bg-white rounded-lg shadow">
        <div className="p-4 border-b">
          <div className="flex items-center gap-4">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
              <input
                type="text"
                placeholder={activeTab === 'rules' ? 'Search rules...' : 'Search approvals...'}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-[var(--nexus-primary-500)]"
              />
            </div>
          </div>
        </div>

        {activeTab === 'rules' && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Name</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Type</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Action</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Threshold</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Priority</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Active</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingRules ? (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td></tr>
                ) : filteredRules.length === 0 ? (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-[var(--text-secondary)]">No rules found</td></tr>
                ) : (
                  filteredRules.map((rule) => (
                    <tr key={rule.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{rule.name}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{RULE_TYPE_LABELS[rule.ruleType] || rule.ruleType}</td>
                      <td className="px-4 py-3">
                        <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', ACTION_COLORS[rule.action])}>
                          {rule.action.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">
                        {rule.thresholdValue != null ? `$${rule.thresholdValue}` : rule.thresholdString || '—'}
                      </td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">{rule.priority}</td>
                      <td className="px-4 py-3 text-center">
                        <PermissionGate permission="approvals.edit">
                          <button onClick={() => toggleRuleMutation.mutate({ id: rule.id, active: !rule.active })}>
                            {rule.active ? <ToggleRight className="w-6 h-6 text-[var(--nexus-primary-600)] mx-auto" /> : <ToggleLeft className="w-6 h-6 text-[var(--text-tertiary)] mx-auto" />}
                          </button>
                        </PermissionGate>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {activeTab !== 'rules' && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[var(--surface-sunken)]">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Order #</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Total</th>
                  <th className="px-4 py-3 text-center text-xs font-medium text-[var(--text-secondary)] uppercase">Risk Score</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Reviewed By</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Date</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {loadingPending || loadingAll ? (
                  <tr><td colSpan={7} className="px-4 py-8 text-center text-[var(--text-secondary)]">Loading...</td></tr>
                ) : (activeTab === 'pending' ? filteredPending : filteredAll).length === 0 ? (
                  <tr><td colSpan={7} className="px-4 py-8 text-center text-[var(--text-secondary)]">No approvals found</td></tr>
                ) : (
                  (activeTab === 'pending' ? filteredPending : filteredAll).map((approval) => (
                    <tr key={approval.id} className="hover:bg-[var(--surface-sunken)]">
                      <td className="px-4 py-3 text-sm font-medium text-[var(--nexus-primary-600)]">{approval.orderNumber}</td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-primary)]">
                        ${approval.orderTotal?.toFixed(2) || '—'}
                      </td>
                      <td className="px-4 py-3 text-center text-sm text-[var(--text-secondary)]">
                        {approval.riskScore != null ? approval.riskScore.toFixed(1) : '—'}
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', STATUS_COLORS[approval.status])}>
                          {approval.status.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{approval.reviewedBy || '—'}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{formatDate(approval.createdAt)}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => setSelectedApproval(approval)}
                            className="p-1 text-[var(--text-tertiary)] hover:text-[var(--nexus-primary-600)]"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                          {approval.status === 'PENDING' && (
                            <PermissionGate permission="approvals.review">
                              <button
                                onClick={() => reviewMutation.mutate({ id: approval.id, decision: 'APPROVED', reviewer: 'admin' })}
                                className="px-2 py-1 text-xs bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] rounded hover:bg-[var(--nexus-success-200)]"
                              >
                                Approve
                              </button>
                              <button
                                onClick={() => reviewMutation.mutate({ id: approval.id, decision: 'REJECTED', reviewer: 'admin' })}
                                className="px-2 py-1 text-xs bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] rounded hover:bg-[var(--nexus-error-200)]"
                              >
                                Reject
                              </button>
                            </PermissionGate>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selectedApproval && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-2xl">
            <div className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-xl font-bold">Approval Details</h2>
                <button onClick={() => setSelectedApproval(null)} className="text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]">
                  <XCircle className="w-6 h-6" />
                </button>
              </div>
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Order Number</p>
                    <p className="font-medium">{selectedApproval.orderNumber}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Order Total</p>
                    <p className="font-medium">${selectedApproval.orderTotal?.toFixed(2) || '—'}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Risk Score</p>
                    <p className="font-medium">{selectedApproval.riskScore != null ? selectedApproval.riskScore.toFixed(1) : '—'}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Status</p>
                    <span className={clsx('px-2 py-1 text-xs rounded-full font-medium', STATUS_COLORS[selectedApproval.status])}>
                      {selectedApproval.status.replace('_', ' ')}
                    </span>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Reviewed By</p>
                    <p className="font-medium">{selectedApproval.reviewedBy || '—'}</p>
                  </div>
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Decided At</p>
                    <p className="font-medium">{formatDate(selectedApproval.decidedAt)}</p>
                  </div>
                </div>
                {selectedApproval.matchedRules && (
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Matched Rules</p>
                    <p className="text-[var(--text-secondary)]">{selectedApproval.matchedRules}</p>
                  </div>
                )}
                {selectedApproval.reviewNotes && (
                  <div>
                    <p className="text-sm text-[var(--text-secondary)]">Review Notes</p>
                    <p className="text-[var(--text-secondary)]">{selectedApproval.reviewNotes}</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
