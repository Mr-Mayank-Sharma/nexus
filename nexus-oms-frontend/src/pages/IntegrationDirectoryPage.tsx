import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, ChevronDown, CircleDashed, Loader2, MinusCircle, Search, ShieldAlert } from 'lucide-react'
import { getCatalog, getConnectionHealth, type CatalogEntry, type Evidence, type Tier } from '../features/integrations/catalogApi'
import { actionLabel, EVIDENCE_LABEL, filterEntries, groupEntries, headline, TIER_HELP, TIER_LABEL } from '../features/integrations/catalogRules'

const TIERS: (Tier | 'ALL')[] = ['ALL', 'NATIVE', 'CONNECTOR', 'GENERIC']

const TIER_TONE: Record<Tier, string> = {
  NATIVE: 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]',
  CONNECTOR: 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]',
  GENERIC: 'bg-[var(--surface-sunken)] text-[var(--text-tertiary)]',
}

function EvidenceIcon({ evidence }: { evidence: Evidence }) {
  if (evidence === 'TESTED') return <CheckCircle2 aria-hidden className="h-4 w-4 text-[var(--nexus-success-600)]" />
  if (evidence === 'CODE_ONLY') return <CircleDashed aria-hidden className="h-4 w-4 text-[var(--nexus-warning-600)]" />
  return <MinusCircle aria-hidden className="h-4 w-4 text-[var(--text-tertiary)]" />
}

function Card({ entry }: { entry: CatalogEntry }) {
  const [open, setOpen] = useState(false)
  return (
    <article className="rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="font-semibold text-[var(--text-primary)]">{entry.name}</h3>
          <p className="mt-0.5 text-xs text-[var(--text-tertiary)]">{headline(entry)}</p>
        </div>
        <span title={TIER_HELP[entry.tier]} className={`rounded-full px-2 py-0.5 text-xs font-semibold ${TIER_TONE[entry.tier]}`}>{TIER_LABEL[entry.tier]}</span>
      </div>
      <p className="mt-2 text-sm text-[var(--text-secondary)]">{entry.summary}</p>

      <button type="button" aria-expanded={open} onClick={() => setOpen(o => !o)}
        className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-[var(--nexus-primary-600)]">
        <ChevronDown className={`h-3.5 w-3.5 transition ${open ? 'rotate-180' : ''}`} /> {open ? 'Hide' : 'Show'} what it can do
      </button>
      {open && (
        <ul className="mt-2 space-y-1.5 text-sm">
          {entry.capabilities.map(c => (
            <li key={c.name} className="flex gap-2">
              <EvidenceIcon evidence={c.evidence} />
              <div>
                <span className="text-[var(--text-primary)]">{c.name}</span>
                <span className="ml-2 text-xs text-[var(--text-tertiary)]">{EVIDENCE_LABEL[c.evidence]}{c.liveVerified ? ' · checked against the live platform' : c.evidence !== 'NOT_IMPLEMENTED' ? ' · not yet run against the live platform' : ''}</span>
                {c.note && <div className="text-xs text-[var(--text-tertiary)]">{c.note}</div>}
              </div>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-3">
        <Link to={entry.route} className="inline-flex rounded-lg border border-[var(--border-default)] px-3 py-1.5 text-xs font-semibold text-[var(--text-secondary)] hover:bg-[var(--interactive-hover)]">
          {actionLabel(entry)}
        </Link>
      </div>
    </article>
  )
}

export default function IntegrationDirectoryPage() {
  const [tier, setTier] = useState<Tier | 'ALL'>('ALL')
  const [query, setQuery] = useState('')
  const catalog = useQuery({ queryKey: ['integration-catalog'], queryFn: getCatalog, staleTime: 10 * 60_000 })
  const health = useQuery({ queryKey: ['integration-health'], queryFn: getConnectionHealth, staleTime: 60_000, retry: false })

  const groups = useMemo(
    () => (catalog.data ? groupEntries(filterEntries(catalog.data.entries, tier, query), catalog.data.groupOrder) : []),
    [catalog.data, tier, query],
  )

  if (catalog.isLoading) return <div className="grid place-items-center p-16"><Loader2 className="h-6 w-6 animate-spin text-[var(--text-tertiary)]" /></div>
  if (catalog.isError || !catalog.data) return <p className="p-8 text-sm text-[var(--nexus-error-600)]">Could not load the integration directory.</p>

  const h = health.data
  return (
    <div className="space-y-6">
      <div className="enterprise-page-header">
        <div>
          <h1>Integration Directory</h1>
          <p>Every system Nexus can connect to, and exactly what it does with each. Built into Nexus: {catalog.data.counts.native} · Connectors: {catalog.data.counts.connector} · Custom REST only: {catalog.data.counts.generic}.</p>
        </div>
      </div>

      <div role="note" className="flex gap-3 rounded-xl border border-[var(--nexus-warning-300)] bg-[var(--nexus-warning-50)] p-4 text-sm text-[var(--nexus-warning-700)]">
        <ShieldAlert className="mt-0.5 h-4 w-4 flex-shrink-0" />
        <div>
          <strong>{catalog.data.liveVerifiedNote}</strong>
          <div className="mt-1">
            <span className="mr-3">Tested = automated tests cover the logic with the outside system mocked.</span>
            <span className="mr-3">Written, not tested = the code exists but nothing checks it.</span>
            <span>Not built = Nexus does nothing with it.</span>
          </div>
        </div>
      </div>

      {h && h.connections > 0 && (
        <div className="flex flex-wrap items-center gap-4 rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4 text-sm">
          <span className="font-semibold text-[var(--text-primary)]">Your connections</span>
          <span>{h.connections} saved</span>
          <span className="text-[var(--nexus-success-700)]">{h.verified} passed their last test</span>
          <span className={h.failing ? 'font-semibold text-[var(--nexus-error-600)]' : ''}>{h.failing} failing</span>
          <span className="text-[var(--text-tertiary)]">{h.untested} never tested</span>
          <Link to="/integration-hub" className="ml-auto text-xs font-semibold text-[var(--nexus-primary-600)]">View connections</Link>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        {TIERS.map(t => (
          <button key={t} type="button" aria-pressed={tier === t} onClick={() => setTier(t)}
            className={`rounded-full px-3 py-1 text-xs font-semibold ${tier === t ? 'bg-[var(--nexus-primary-600)] text-white' : 'bg-[var(--surface-sunken)] text-[var(--text-secondary)]'}`}>
            {t === 'ALL' ? 'All' : TIER_LABEL[t]}
          </button>
        ))}
        <label className="relative ml-auto">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-[var(--text-tertiary)]" />
          <input aria-label="Search integrations" placeholder="Search integrations"
            className="w-64 rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] py-2 pl-8 pr-3 text-sm text-[var(--text-primary)]"
            value={query} onChange={e => setQuery(e.target.value)} />
        </label>
      </div>

      {groups.length === 0 && (
        <p className="rounded-lg bg-[var(--surface-sunken)] p-6 text-center text-sm text-[var(--text-secondary)]">
          <AlertTriangle className="mr-2 inline h-4 w-4" /> Nothing matches. Try another search.
        </p>
      )}

      {groups.map(g => (
        <section key={g.group} aria-labelledby={`g-${g.group}`}>
          <h2 id={`g-${g.group}`} className="mb-3 text-sm font-semibold uppercase tracking-wide text-[var(--text-tertiary)]">{g.label}</h2>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {g.entries.map(e => <Card key={e.key} entry={e} />)}
          </div>
        </section>
      ))}
    </div>
  )
}
