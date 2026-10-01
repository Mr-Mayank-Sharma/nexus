import client from './client'

const WRAPPER_KEYS = ['success', 'data', 'message', 'error', 'errors', 'pagination']

/** Client normalizes every body to {success, data, message, ...}; unwrap back to the payload. */
async function unwrap<T>(promise: Promise<{ data: unknown }>): Promise<{ data: T }> {
  const res = await promise
  const body = res.data
  if (body && typeof body === 'object' && !Array.isArray(body) && 'data' in body) {
    const obj = body as Record<string, unknown>
    if (Object.keys(obj).every(k => WRAPPER_KEYS.includes(k))) return { data: obj.data as T }
  }
  return { data: body as T }
}

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
    unwrap<ApprovalRule[]>(client.get<ApprovalRule[]>('/approvals/rules')),

  getRule: (id: string) =>
    unwrap<ApprovalRule>(client.get<ApprovalRule>(`/approvals/rules/${id}`)),

  createRule: (rule: Partial<ApprovalRule>) =>
    unwrap<ApprovalRule>(client.post<ApprovalRule>('/approvals/rules', rule)),

  updateRule: (id: string, rule: Partial<ApprovalRule>) =>
    unwrap<ApprovalRule>(client.put<ApprovalRule>(`/approvals/rules/${id}`, rule)),

  deleteRule: (id: string) =>
    unwrap<void>(client.delete(`/approvals/rules/${id}`)),

  // Approvals
  evaluateOrder: (orderId: string, orderNumber: string, orderTotal: number, customerId?: string) =>
    unwrap<OrderApproval>(client.post<OrderApproval>('/approvals/evaluate', null, {
      params: { orderId, orderNumber, orderTotal, customerId },
    })),

  manualReview: (id: string, decision: string, reviewer: string, notes?: string) =>
    unwrap<OrderApproval>(client.post<OrderApproval>(`/approvals/${id}/review`, null, {
      params: { decision, reviewer, notes },
    })),

  getPendingReviews: () =>
    unwrap<OrderApproval[]>(client.get<OrderApproval[]>('/approvals/pending')),

  getAllApprovals: () =>
    unwrap<OrderApproval[]>(client.get<OrderApproval[]>('/approvals')),

  getApprovalByOrder: (orderId: string) =>
    unwrap<OrderApproval>(client.get<OrderApproval>(`/approvals/order/${orderId}`)),

  getApprovalStats: () =>
    unwrap<ApprovalStats>(client.get<ApprovalStats>('/approvals/stats')),
}

export default approvalsApi