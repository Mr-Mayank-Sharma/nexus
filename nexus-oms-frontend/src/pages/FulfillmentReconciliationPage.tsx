import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  RefreshCw, Eye, AlertTriangle, XCircle, CheckCircle, Wrench, History,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import { useWorkspace } from '../hooks/useWorkspace'
import {
  reconciliationApi,
  ReconciliationReportRow,
  NxFulfillmentReconciliation,
} from '../api/reconciliation'
import { EnterpriseTabs, EnterpriseKPICard, EnterpriseToolbar } from '../components/enterprise'
import type { Tab } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'

const MATCH_COLORS: Record<string, string> = {
  FULFILLED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  PARTIAL: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]',
  MISMATCH: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
  UNFULFILLED: 'bg-[var(--surface-muted)] text-[var(--text-primary)]',
  EXCESS: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
}

const RESOLUTION_COLORS: Record<string, string> = {
  FLAGGED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
  REPAIRED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  ACCEPTED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
  MANUAL: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
}

const SYNC_COLORS: Record<string, string> = {
  PENDING: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-800)]',
  SYNCED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  FAILED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
  SKIPPED: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
}

function formatDate(iso?: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}

function parseItems(json?: string): Array<{ sku?: string; qty?: number; title?: string }> {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export default function FulfillmentReconciliationPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const { workspace } = useWorkspace()
  const [activeTab, setActiveTab] = useState<'mismatches' | 'history'>('mismatches')
  const [selectedOrder, setSelectedOrder] = useState<ReconciliationReportRow | null>(null)

  const tabs: Tab[] = [
    { id: 'mismatches', label: 'Mismatch Report', icon: <AlertTriangle className="w-4 h-4" /> },
    { id: 'history', label: 'Order History', icon: <History className="w-4 h-4" /> },
  ]

  const { data: mismatches = [], isLoading: loadingMismatches } = useQuery({
    queryKey: ['reconciliation-mismatches', workspace?.id],
    queryFn: async () => {
      const res = await reconciliationApi.getMismatches()
      return Array.isArray(res.data) ? res.data : []
    },
    enabled: activeTab === 'mismatches',
  })

  const { data: history = [], isLoading: loadingHistory } = useQuery({
    queryKey: ['reconciliation-history', workspace?.id],
    queryFn: async () => {
      const res = await reconciliationApi.getMismatches()
      const rows = Array.isArray(res.data) ? res.data : []
      const all: NxFulfillmentReconciliation[] = []
      for (const row of rows.slice(0, 50)) {
        try {
          const h = await reconciliationApi.getOrderHistory(row.orderId)
          if (Array.isArray(h.data)) all.push(...h.data)
        } catch { /* skip orders without history */ }
      }
      return all
    },
    enabled: activeTab === 'history',
  })

  const repairMutation = useMutation({
    mutationFn: () => reconciliationApi.triggerRepair(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reconciliation-mismatches'] })
      queryClient.invalidateQueries({ queryKey: ['reconciliation-history'] })
      addToast({ type: 'success', title: 'Repair pass completed' })
    },
    onError: () => addToast({ type: 'error', title: 'Repair pass failed' }),
  })

  const flagged = mismatches.filter(m => m.resolution === 'FLAGGED').length
  const repaired = mismatches.filter(m => m.resolution === 'REPAIRED').length
  const syncFailed = mismatches.filter(m => m.netsuiteSyncStatus === 'FAILED').length
  const totalDelta = mismatches.reduce((sum, m) => sum + (m.inventoryDelta ?? 0), 0)

  return (
    <PermissionGate module="reconciliation" fallback={
      <div className="p-8 text-[var(--text-secondary)]">You don't have access to Fulfillment Reconciliation.</div>
    }>
      <div className="space-y-6 p-6">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)]">Fulfillment Reconciliation</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Shopify → OMS fulfillment mismatch detection, repair, and order history.
          </p>
        </div>

        <EnterpriseTabs tabs={tabs} active={activeTab} onChange={(id) => setActiveTab(id as 'mismatches' | 'history')} />

        {activeTab === 'mismatches' && (
          <>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <EnterpriseKPICard label="Total Mismatches" value={mismatches.length} icon={<AlertTriangle className="w-5 h-5" />} />
              <EnterpriseKPICard label="Flagged" value={flagged} icon={<XCircle className="w-5 h-5" />} />
              <EnterpriseKPICard label="Repaired" value={repaired} icon={<CheckCircle className="w-5 h-5" />} />
              <EnterpriseKPICard label="NetSync Failed" value={syncFailed} icon={<RefreshCw className="w-5 h-5" />} />
            </div>

            <EnterpriseToolbar
              title="Mismatch Report"
              actions={
                <button
                  onClick={() => repairMutation.mutate()}
                  disabled={repairMutation.isPending}
                  className="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
                >
                  <Wrench className="w-4 h-4" />
                  {repairMutation.isPending ? 'Repairing…' : 'Run Repair Pass'}
                </button>
              }
            />

            <div className="card overflow-hidden">
              {loadingMismatches ? (
                <div className="p-8 text-center text-[var(--text-secondary)]">Loading mismatches…</div>
              ) : mismatches.length === 0 ? (
                <div className="p-8 text-center text-[var(--text-secondary)]">
                  No mismatches found. {totalDelta !== 0 ? `Net inventory delta: ${totalDelta > 0 ? '+' : ''}${totalDelta}` : ''}
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-[var(--surface-muted)] text-[var(--text-secondary)]">
                    <tr>
                      <th className="text-left px-4 py-3 font-medium">Order #</th>
                      <th className="text-left px-4 py-3 font-medium">Match Type</th>
                      <th className="text-left px-4 py-3 font-medium">Resolution</th>
                      <th className="text-left px-4 py-3 font-medium">Reason</th>
                      <th className="text-left px-4 py-3 font-medium">NetSuite Sync</th>
                      <th className="text-right px-4 py-3 font-medium">Inventory Δ</th>
                      <th className="text-left px-4 py-3 font-medium">Created</th>
                      <th className="px-4 py-3" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[var(--border)]">
                    {mismatches.map((m) => (
                      <tr key={m.orderId} className="hover:bg-[var(--surface-muted)]/40">
                        <td className="px-4 py-3 font-medium text-[var(--text-primary)]">{m.orderNumber}</td>
                        <td className="px-4 py-3">
                          <span className={clsx('badge', MATCH_COLORS[m.matchType] ?? 'badge-neutral')}>{m.matchType}</span>
                        </td>
                        <td className="px-4 py-3">
                          <span className={clsx('badge', RESOLUTION_COLORS[m.resolution] ?? 'badge-neutral')}>{m.resolution}</span>
                        </td>
                        <td className="px-4 py-3 text-[var(--text-secondary)]">{m.reason ?? '—'}</td>
                        <td className="px-4 py-3">
                          <span className={clsx('badge', SYNC_COLORS[m.netsuiteSyncStatus] ?? 'badge-neutral')}>{m.netsuiteSyncStatus}</span>
                        </td>
                        <td className={clsx('px-4 py-3 text-right font-medium', (m.inventoryDelta ?? 0) > 0 ? 'text-[var(--nexus-success-700)]' : (m.inventoryDelta ?? 0) < 0 ? 'text-[var(--nexus-error-700)]' : 'text-[var(--text-secondary)]')}>
                          {m.inventoryDelta !== undefined && m.inventoryDelta !== null ? `${m.inventoryDelta > 0 ? '+' : ''}${m.inventoryDelta}` : '—'}
                        </td>
                        <td className="px-4 py-3 text-[var(--text-secondary)]">{formatDate(m.createdAt)}</td>
                        <td className="px-4 py-3 text-right">
                          <button
                            onClick={() => setSelectedOrder(m)}
                            className="btn-secondary inline-flex items-center gap-1.5"
                            title="View order history"
                          >
                            <Eye className="w-4 h-4" /> History
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </>
        )}

        {activeTab === 'history' && (
          <div className="card overflow-hidden">
            {loadingHistory ? (
              <div className="p-8 text-center text-[var(--text-secondary)]">Loading reconciliation history…</div>
            ) : history.length === 0 ? (
              <div className="p-8 text-center text-[var(--text-secondary)]">No reconciliation history found.</div>
            ) : (
              <table className="w-full text-sm">
                <thead className="bg-[var(--surface-muted)] text-[var(--text-secondary)]">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium">Order #</th>
                    <th className="text-left px-4 py-3 font-medium">Match Type</th>
                    <th className="text-left px-4 py-3 font-medium">Resolution</th>
                    <th className="text-left px-4 py-3 font-medium">Routed → Fulfilled</th>
                    <th className="text-left px-4 py-3 font-medium">NetSuite Sync</th>
                    <th className="text-right px-4 py-3 font-medium">Inventory Δ</th>
                    <th className="text-left px-4 py-3 font-medium">Updated</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border)]">
                  {history.map((h) => (
                    <tr key={h.id} className="hover:bg-[var(--surface-muted)]/40">
                      <td className="px-4 py-3 font-medium text-[var(--text-primary)]">{h.orderNumber}</td>
                      <td className="px-4 py-3">
                        <span className={clsx('badge', MATCH_COLORS[h.matchType] ?? 'badge-neutral')}>{h.matchType}</span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx('badge', RESOLUTION_COLORS[h.resolution] ?? 'badge-neutral')}>{h.resolution}</span>
                      </td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">
                        {h.routedLocation ?? '—'} → {h.fulfilledLocation ?? '—'}
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx('badge', SYNC_COLORS[h.netsuiteSyncStatus] ?? 'badge-neutral')}>{h.netsuiteSyncStatus}</span>
                      </td>
                      <td className={clsx('px-4 py-3 text-right font-medium', (h.inventoryDelta ?? 0) > 0 ? 'text-[var(--nexus-success-700)]' : (h.inventoryDelta ?? 0) < 0 ? 'text-[var(--nexus-error-700)]' : 'text-[var(--text-secondary)]')}>
                        {h.inventoryDelta !== undefined && h.inventoryDelta !== null ? `${h.inventoryDelta > 0 ? '+' : ''}${h.inventoryDelta}` : '—'}
                      </td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">{formatDate(h.updatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>

      {selectedOrder && (
        <HistoryDrawer
          orderId={selectedOrder.orderId}
          orderNumber={selectedOrder.orderNumber}
          onClose={() => setSelectedOrder(null)}
        />
      )}
    </PermissionGate>
  )
}

function HistoryDrawer({ orderId, orderNumber, onClose }: { orderId: string; orderNumber: string; onClose: () => void }) {
  const { data: history = [], isLoading } = useQuery({
    queryKey: ['reconciliation-order-history', orderId],
    queryFn: async () => {
      const res = await reconciliationApi.getOrderHistory(orderId)
      return Array.isArray(res.data) ? res.data : []
    },
  })

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/30" onClick={onClose}>
      <div className="w-full max-w-lg bg-[var(--surface)] h-full overflow-y-auto p-6" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-start justify-between mb-6">
          <div>
            <h2 className="text-lg font-semibold text-[var(--text-primary)]">Order {orderNumber}</h2>
            <p className="text-sm text-[var(--text-secondary)]">Reconciliation history</p>
          </div>
          <button onClick={onClose} className="btn-secondary">Close</button>
        </div>

        {isLoading ? (
          <div className="p-8 text-center text-[var(--text-secondary)]">Loading…</div>
        ) : history.length === 0 ? (
          <div className="p-8 text-center text-[var(--text-secondary)]">No reconciliation records for this order.</div>
        ) : (
          <div className="space-y-4">
            {history.map((h) => (
              <div key={h.id} className="card p-4 space-y-2">
                <div className="flex items-center justify-between">
                  <span className={clsx('badge', MATCH_COLORS[h.matchType] ?? 'badge-neutral')}>{h.matchType}</span>
                  <span className={clsx('badge', RESOLUTION_COLORS[h.resolution] ?? 'badge-neutral')}>{h.resolution}</span>
                </div>
                <div className="text-sm text-[var(--text-secondary)] space-y-1">
                  <div>Routed: <span className="text-[var(--text-primary)]">{h.routedLocation ?? '—'}</span> → Fulfilled: <span className="text-[var(--text-primary)]">{h.fulfilledLocation ?? '—'}</span></div>
                  <div>NetSuite: <span className={clsx('badge', SYNC_COLORS[h.netsuiteSyncStatus] ?? 'badge-neutral')}>{h.netsuiteSyncStatus}</span></div>
                  {h.inventoryDelta !== undefined && h.inventoryDelta !== null && (
                    <div>Inventory Δ: <span className={h.inventoryDelta > 0 ? 'text-[var(--nexus-success-700)]' : 'text-[var(--nexus-error-700)]'}>{h.inventoryDelta > 0 ? '+' : ''}{h.inventoryDelta}</span></div>
                  )}
                  {h.notes && <div>Notes: {h.notes}</div>}
                  <div className="text-xs text-[var(--text-secondary)]">Updated {formatDate(h.updatedAt)}</div>
                </div>
                {parseItems(h.fulfilledItems).length > 0 && (
                  <div className="text-sm">
                    <div className="font-medium text-[var(--text-primary)] mb-1">Fulfilled items</div>
                    <ul className="space-y-0.5 text-[var(--text-secondary)]">
                      {parseItems(h.fulfilledItems).map((it, i) => (
                        <li key={i}>• {it.sku ?? 'SKU'} {it.qty ? `× ${it.qty}` : ''}{it.title ? ` — ${it.title}` : ''}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}