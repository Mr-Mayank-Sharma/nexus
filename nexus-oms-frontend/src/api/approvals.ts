import client from './client'

export interface ApprovalRule {
  id: string
  tenantId: string
  name: string
  ruleType: string // AMOUNT_THRESHOLD, VELOCITY, GEOLOCATION, FRAUD_SCORE, BLACKLIST, REPEAT_CUSTOMER
  action: string // AUTO_APPROVE, HOLD_FOR_REVIEW, REJECT
  thresholdValue?: number
  thresholdString?: string
  priority: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface OrderApproval {
  id: string
  tenantId: string
  orderId: string
  orderNumber: string
  orderTotal?: number
  customerId?: string
  riskScore?: number
  status: string // PENDING, APPROVED, REJECTED, MANUAL_REVIEW
  matchedRules?: string
  reviewedBy?: string
  reviewNotes?: string
  decidedAt?: string
  createdAt: string
  updatedAt: string
}

export interface ApprovalStats {
  totalApprovals: number
  pending: number
  approved: number
  rejected: number
  manualReview: number
}

export const approvalsApi = {
  // Rules
  getRules: () =>
    client.get<ApprovalRule[]>('/approvals/rules'),

  getRule: (id: string) =>
    client.get<ApprovalRule>(`/approvals/rules/${id}`),

  createRule: (rule: Partial<ApprovalRule>) =>
    client.post<ApprovalRule>('/approvals/rules', rule),

  updateRule: (id: string, rule: Partial<ApprovalRule>) =>
    client.put<ApprovalRule>(`/approvals/rules/${id}`, rule),

  deleteRule: (id: string) =>
    client.delete(`/approvals/rules/${id}`),

  // Approvals
  evaluateOrder: (orderId: string, orderNumber: string, orderTotal: number, customerId?: string) =>
    client.post<OrderApproval>('/approvals/evaluate', null, {
      params: { orderId, orderNumber, orderTotal, customerId },
    }),

  manualReview: (id: string, decision: string, reviewer: string, notes?: string) =>
    client.post<OrderApproval>(`/approvals/${id}/review`, null, {
      params: { decision, reviewer, notes },
    }),

  getPendingReviews: () =>
    client.get<OrderApproval[]>('/approvals/pending'),

  getAllApprovals: () =>
    client.get<OrderApproval[]>('/approvals'),

  getApprovalByOrder: (orderId: string) =>
    client.get<OrderApproval>(`/approvals/order/${orderId}`),

  getApprovalStats: () =>
    client.get<ApprovalStats>('/approvals/stats'),
}

export default approvalsApi
