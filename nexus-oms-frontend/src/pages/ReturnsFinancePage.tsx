import { useState } from 'react'
import { clsx } from 'clsx'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  DollarSign, Split, ArrowLeftRight, Loader2, Receipt, TrendingUp,
  TrendingDown, Wallet, AlertTriangle, Check, Plus, X,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import { useToast } from '../hooks/useToast'
import {
  splitCreditMemos, lesserValueExchange, getReturnsFinanceImpact,
  applyCustomerDeposit, getCreditMemosForReturn,
  type CreditMemo, type ImpactReport,
} from '../api/returnsFinance'

const TABS = [
  { id: 'SPLIT', label: 'Split Credit Memos', icon: <Split className="w-4 h-4" /> },
  { id: 'EXCHANGE', label: 'Lesser-Value Exchange', icon: <ArrowLeftRight className="w-4 h-4" /> },
  { id: 'DEPOSIT', label: 'Customer Deposit', icon: <Wallet className="w-4 h-4" /> },
]

export default function ReturnsFinancePage() {
  const { toast } = useToast()
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState('SPLIT')

  // Split form
  const [splitForm, setSplitForm] = useState({
    returnId: '',
    transactions: '',
  })

  // Exchange form
  const [exchangeForm, setExchangeForm] = useState({
    returnId: '',
    exchangeInvoiceId: '',
    exchangeValue: '',
    sourceLocationId: '',
    targetLocationId: '',
  })

  // Deposit form
  const [depositForm, setDepositForm] = useState({
    memoId: '',
    deposit: '',
  })

  // ── Queries ──
  const impactQuery = useQuery({
    queryKey: ['returns-finance-impact'],
    queryFn: () => getReturnsFinanceImpact(),
  })
  const impact: ImpactReport | undefined = impactQuery.data?.success ? impactQuery.data.data : undefined

  // ── Mutations ──
  const split = useMutation({
    mutationFn: () => splitCreditMemos({
      returnId: splitForm.returnId,
      transactions: splitForm.transactions
        .split('\n')
        .map((line) => line.trim())
        .filter(Boolean)
        .map((line) => {
          const [invoiceId, amount, outcome] = line.split(',').map((s) => s.trim())
          return { invoiceId, amount: Number(amount), outcome: outcome || 'REFUND' }
        }),
    }),
    onSuccess: (res) => {
      if (res.success) {
        toast(`Split into ${res.data?.length ?? 0} credit memos`, 'success')
        setSplitForm({ returnId: '', transactions: '' })
        qc.invalidateQueries({ queryKey: ['returns-finance-impact'] })
      } else {
        toast(res.error || 'Failed to split', 'error')
      }
    },
  })

  const exchange = useMutation({
    mutationFn: () => lesserValueExchange({
      returnId: exchangeForm.returnId,
      exchangeInvoiceId: exchangeForm.exchangeInvoiceId,
      exchangeValue: Number(exchangeForm.exchangeValue),
      sourceLocationId: exchangeForm.sourceLocationId || undefined,
      targetLocationId: exchangeForm.targetLocationId || undefined,
    }),
    onSuccess: (res) => {
      if (res.success) {
        toast('Exchange processed', 'success')
        setExchangeForm({ returnId: '', exchangeInvoiceId: '', exchangeValue: '', sourceLocationId: '', targetLocationId: '' })
        qc.invalidateQueries({ queryKey: ['returns-finance-impact'] })
      } else {
        toast(res.error || 'Failed to process exchange', 'error')
      }
    },
  })

  const deposit = useMutation({
    mutationFn: () => applyCustomerDeposit(depositForm.memoId, Number(depositForm.deposit)),
    onSuccess: (res) => {
      if (res.success) {
        toast('Customer deposit applied', 'success')
        setDepositForm({ memoId: '', deposit: '' })
        qc.invalidateQueries({ queryKey: ['returns-finance-impact'] })
      } else {
        toast(res.error || 'Failed to apply deposit', 'error')
      }
    },
  })

  const multiInvoice = impact?.multiInvoiceMemos ?? 0
  const total = impact?.totalMemos ?? 0

  return (
    <div className="p-6 max-w-[1400px] mx-auto">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Returns' }, { label: 'Returns Finance' }]} />

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)] flex items-center gap-2">
            <DollarSign className="w-6 h-6 text-[var(--text-brand)]" />
            Returns & Exchange Finance
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Reconcile credit memos across multiple invoices, handle lesser-value exchanges, and apply customer deposits.
          </p>
        </div>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <EnterpriseKPICard
          title="Credit Memos"
          value={total}
          subtitle="Total tracked"
          icon={Receipt}
          color="primary"
        />
        <EnterpriseKPICard
          title="Multi-Invoice"
          value={multiInvoice}
          subtitle="Need splitting"
          icon={Split}
          color={multiInvoice > 0 ? 'warning' : 'success'}
        />
        <EnterpriseKPICard
          title="Split Coverage"
          value={total > 0 ? `${Math.round(((total - multiInvoice) / total) * 100)}%` : '—'}
          subtitle="Cleanly applied"
          icon={TrendingUp}
          color="success"
        />
        <EnterpriseKPICard
          title="Exchange Ready"
          value="Live"
          subtitle="Lesser-value flow"
          icon={ArrowLeftRight}
          color="info"
        />
      </div>

      <EnterpriseTabs tabs={TABS} activeTab={activeTab} onChange={setActiveTab} />

      <div className="mt-6">
        {activeTab === 'SPLIT' && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <Split className="w-4 h-4 text-[var(--text-brand)]" /> Split Credit Memo
              </h3>
              <p className="text-xs text-[var(--text-secondary)] mb-4">
                A single return can span multiple invoices. Split a credit memo so each invoice gets its own
                application — while retaining the original claim ID.
              </p>
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Return ID</label>
              <input
                value={splitForm.returnId}
                onChange={(e) => setSplitForm((f) => ({ ...f, returnId: e.target.value }))}
                className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                placeholder="return-001"
              />
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">
                Transactions (one per line: invoiceId, amount, outcome)
              </label>
              <textarea
                value={splitForm.transactions}
                onChange={(e) => setSplitForm((f) => ({ ...f, transactions: e.target.value }))}
                className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm mb-4 font-mono focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                rows={3}
                placeholder={'inv-001, 49.99, REFUND\ninv-002, 29.99, STORE_CREDIT'}
              />
              <button
                onClick={() => split.mutate()}
                disabled={split.isPending || !splitForm.returnId || !splitForm.transactions.trim()}
                className="w-full flex items-center justify-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium py-2.5 hover:opacity-90 disabled:opacity-50"
              >
                {split.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Split className="w-4 h-4" />}
                Split Credit Memos
              </button>
            </div>

            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-[var(--text-warning)]" /> Why This Matters
              </h3>
              <div className="space-y-3">
                <div className="flex items-start gap-3 rounded-lg bg-[var(--surface-sunken)] p-3">
                  <Check className="w-4 h-4 text-[var(--text-success)] mt-0.5 shrink-0" />
                  <p className="text-xs text-[var(--text-secondary)]">
                    <span className="font-medium text-[var(--text-primary)]">NetSuite discount-item mapping.</span>{' '}
                    Each invoice gets its own credit application, so discounts and tax deltas reconcile correctly in your ERP.
                  </p>
                </div>
                <div className="flex items-start gap-3 rounded-lg bg-[var(--surface-sunken)] p-3">
                  <Check className="w-4 h-4 text-[var(--text-success)] mt-0.5 shrink-0" />
                  <p className="text-xs text-[var(--text-secondary)]">
                    <span className="font-medium text-[var(--text-primary)]">Retain claim ID.</span>{' '}
                    Splitting never loses the original return claim — every split memo traces back to the same RMA.
                  </p>
                </div>
                <div className="flex items-start gap-3 rounded-lg bg-[var(--surface-sunken)] p-3">
                  <Check className="w-4 h-4 text-[var(--text-success)] mt-0.5 shrink-0" />
                  <p className="text-xs text-[var(--text-secondary)]">
                    <span className="font-medium text-[var(--text-primary)]">Tax delta handling.</span>{' '}
                    When a return spans invoices with different tax rates, each split memo carries its own tax delta.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'EXCHANGE' && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <ArrowLeftRight className="w-4 h-4 text-[var(--text-brand)]" /> Lesser-Value Exchange
              </h3>
              <p className="text-xs text-[var(--text-secondary)] mb-4">
                When a customer exchanges for a lower-value item, reconcile the price difference and tax delta
                automatically.
              </p>
              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Return ID</label>
                  <input
                    value={exchangeForm.returnId}
                    onChange={(e) => setExchangeForm((f) => ({ ...f, returnId: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="return-001"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Exchange Invoice ID</label>
                  <input
                    value={exchangeForm.exchangeInvoiceId}
                    onChange={(e) => setExchangeForm((f) => ({ ...f, exchangeInvoiceId: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="inv-003"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Exchange Value ($)</label>
                  <input
                    type="number"
                    value={exchangeForm.exchangeValue}
                    onChange={(e) => setExchangeForm((f) => ({ ...f, exchangeValue: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="0.00"
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Source Location</label>
                    <input
                      value={exchangeForm.sourceLocationId}
                      onChange={(e) => setExchangeForm((f) => ({ ...f, sourceLocationId: e.target.value }))}
                      className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                      placeholder="WH-MAIN"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Target Location</label>
                    <input
                      value={exchangeForm.targetLocationId}
                      onChange={(e) => setExchangeForm((f) => ({ ...f, targetLocationId: e.target.value }))}
                      className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                      placeholder="STORE-01"
                    />
                  </div>
                </div>
                <button
                  onClick={() => exchange.mutate()}
                  disabled={exchange.isPending || !exchangeForm.returnId || !exchangeForm.exchangeInvoiceId || !exchangeForm.exchangeValue}
                  className="w-full flex items-center justify-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium py-2.5 hover:opacity-90 disabled:opacity-50"
                >
                  {exchange.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <ArrowLeftRight className="w-4 h-4" />}
                  Process Exchange
                </button>
              </div>
            </div>

            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <TrendingDown className="w-4 h-4 text-[var(--text-warning)]" /> Exchange Outcome
              </h3>
              <div className="space-y-3">
                <div className="flex items-start gap-3 rounded-lg bg-[var(--surface-sunken)] p-3">
                  <ArrowLeftRight className="w-4 h-4 text-[var(--text-brand)] mt-0.5 shrink-0" />
                  <p className="text-xs text-[var(--text-secondary)]">
                    <span className="font-medium text-[var(--text-primary)]">Price difference.</span>{' '}
                    The system computes the difference between the original and exchange value and records it as the memo outcome.
                  </p>
                </div>
                <div className="flex items-start gap-3 rounded-lg bg-[var(--surface-sunken)] p-3">
                  <TrendingDown className="w-4 h-4 text-[var(--text-warning)] mt-0.5 shrink-0" />
                  <p className="text-xs text-[var(--text-secondary)]">
                    <span className="font-medium text-[var(--text-primary)]">Location transfer.</span>{' '}
                    Source and target locations track where the returned item came from and where the exchange item ships from.
                  </p>
                </div>
                <div className="flex items-start gap-3 rounded-lg bg-[var(--surface-sunken)] p-3">
                  <Receipt className="w-4 h-4 text-[var(--text-info)] mt-0.5 shrink-0" />
                  <p className="text-xs text-[var(--text-secondary)]">
                    <span className="font-medium text-[var(--text-primary)]">ERP-ready.</span>{' '}
                    The reconciled memo posts cleanly to NetSuite/QuickBooks with the correct discount-item mapping.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'DEPOSIT' && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <Wallet className="w-4 h-4 text-[var(--text-brand)]" /> Apply Customer Deposit
              </h3>
              <p className="text-xs text-[var(--text-secondary)] mb-4">
                Convert a credit memo into a customer deposit (store credit) that can be applied to future orders.
              </p>
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Credit Memo ID</label>
              <input
                value={depositForm.memoId}
                onChange={(e) => setDepositForm((f) => ({ ...f, memoId: e.target.value }))}
                className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                placeholder="memo-001"
              />
              <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Deposit Amount ($)</label>
              <input
                type="number"
                value={depositForm.deposit}
                onChange={(e) => setDepositForm((f) => ({ ...f, deposit: e.target.value }))}
                className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                placeholder="0.00"
              />
              <button
                onClick={() => deposit.mutate()}
                disabled={deposit.isPending || !depositForm.memoId || !depositForm.deposit}
                className="w-full flex items-center justify-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium py-2.5 hover:opacity-90 disabled:opacity-50"
              >
                {deposit.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Wallet className="w-4 h-4" />}
                Apply Customer Deposit
              </button>
            </div>

            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <Wallet className="w-4 h-4 text-[var(--text-brand)]" /> Store Credit Ledger
              </h3>
              <p className="text-xs text-[var(--text-secondary)]">
                Customer deposits create a store-credit ledger entry that can be applied to future orders,
                giving customers flexibility while keeping your books clean.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
