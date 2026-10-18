import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CalendarClock, CheckCircle2, Download, Loader2, Pencil, Play, Plus, Trash2, XCircle } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import PermissionGate from '../components/rbac/PermissionGate'
import {
  createJob, deleteJob, downloadRunFile, getJobs, getJobTypes, getRun, getRunIssues, getRunLog, getRuns,
  getStoreConnections, runJob, setJobEnabled, updateJob,
  type Job, type JobInput, type JobRun, type JobType, type RunStatus,
} from '../features/jobs/jobsApi'
import {
  defaultSchedule, describeSchedule, formProblems, fromCron, isTerminal, MIN_MINUTES, nextRunText, STATUS_HELP, STATUS_LABEL,
  toCron, WEEKDAYS, writesStock, type JobForm, type ScheduleKind,
} from '../features/jobs/jobsRules'

const field = 'rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)] w-full'
const primary = 'inline-flex items-center gap-2 rounded-lg bg-[var(--nexus-primary-600)] px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-[var(--nexus-primary-700)] disabled:opacity-50'
const ghost = 'inline-flex items-center gap-2 rounded-lg border border-[var(--border-default)] px-3 py-1.5 text-sm font-semibold text-[var(--text-secondary)] transition hover:bg-[var(--interactive-hover)] disabled:opacity-50'
const card = 'rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5 shadow-sm'

const TONE: Record<RunStatus, string> = {
  RUNNING: 'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)]',
  COMPLETED: 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]',
  PARTIAL: 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-700)]',
  FAILED: 'bg-[var(--nexus-danger-50)] text-[var(--nexus-danger-700)]',
}

const SCHEDULE_CHOICES: { kind: ScheduleKind; label: string }[] = [
  { kind: 'MANUAL', label: 'Only when I press Run now' },
  { kind: 'EVERY_N_MINUTES', label: 'Every few minutes' },
  { kind: 'HOURLY', label: 'Every hour' },
  { kind: 'DAILY', label: 'Every day' },
  { kind: 'WEEKLY', label: 'Every week' },
  { kind: 'CUSTOM', label: 'Custom (advanced)' },
]

function StatusBadge({ status }: { status: RunStatus | null }) {
  if (!status) return <span className="text-xs text-[var(--text-tertiary)]">Never run</span>
  return <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${TONE[status]}`} title={STATUS_HELP[status]}>{STATUS_LABEL[status]}</span>
}

function when(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString() : '—'
}

function save(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

const emptyForm = (): JobForm => ({ name: '', type: '', params: {}, schedule: { ...defaultSchedule }, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC' })

export default function JobsPage() {
  const qc = useQueryClient()
  const { addToast } = useToast()
  const [editing, setEditing] = useState<{ id: string | null; form: JobForm } | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [openRun, setOpenRun] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null)

  const types = useQuery({ queryKey: ['job-types'], queryFn: getJobTypes })
  const jobs = useQuery({ queryKey: ['jobs'], queryFn: getJobs, refetchInterval: 15000 })
  const stores = useQuery({ queryKey: ['job-stores'], queryFn: getStoreConnections })
  const runs = useQuery({
    queryKey: ['job-runs', selected], queryFn: () => getRuns(selected!), enabled: !!selected,
    refetchInterval: q => (q.state.data ?? []).some(r => !isTerminal(r.status)) ? 3000 : false,
  })
  const run = useQuery({
    queryKey: ['job-run', openRun], queryFn: () => getRun(openRun!), enabled: !!openRun,
    refetchInterval: q => q.state.data && !isTerminal(q.state.data.status) ? 3000 : false,
  })
  const log = useQuery({ queryKey: ['job-log', openRun, run.data?.status], queryFn: () => getRunLog(openRun!), enabled: !!openRun })
  const issues = useQuery({
    queryKey: ['job-issues', openRun, run.data?.status], queryFn: () => getRunIssues(openRun!),
    enabled: !!openRun && (run.data?.issueCount ?? 0) > 0,
  })

  const typeOf = (t: string): JobType | undefined => (types.data ?? []).find(x => x.type === t)
  const fail = (title: string) => (e: unknown) =>
    addToast({ type: 'error', title, message: (e as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message ?? (e as Error)?.message })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['jobs'] }); qc.invalidateQueries({ queryKey: ['job-runs'] }) }

  const saveMut = useMutation({
    mutationFn: ({ id, input }: { id: string | null; input: JobInput }) => (id ? updateJob(id, input) : createJob(input)),
    onSuccess: j => { addToast({ type: 'success', title: 'Job saved', message: 'It is switched off until you turn it on.' }); setEditing(null); setSelected(j.id); refresh() },
    onError: fail('Could not save the job'),
  })
  const runMut = useMutation({
    mutationFn: runJob,
    onSuccess: r => { addToast({ type: 'success', title: 'Started', message: 'The run is in progress.' }); setSelected(r.jobId); setOpenRun(r.id); refresh() },
    onError: fail('Could not start the job'),
  })
  const enableMut = useMutation({ mutationFn: ({ id, on }: { id: string; on: boolean }) => setJobEnabled(id, on), onSuccess: refresh, onError: fail('Could not change the schedule') })
  const deleteMut = useMutation({
    mutationFn: deleteJob,
    onSuccess: () => { setConfirmDelete(null); setSelected(null); setOpenRun(null); refresh() },
    onError: fail('Could not delete the job'),
  })

  const download = async (runId: string, which: 'errors' | 'output', name: string) => {
    try { save(await downloadRunFile(runId, which), name) } catch (e) { fail('Download failed')(e) }
  }

  const startEdit = (job?: Job) => setEditing(job
    ? { id: job.id, form: { name: job.name, type: job.type, params: { ...job.params }, schedule: fromCron(job.cron), timezone: job.timezone } }
    : { id: null, form: emptyForm() })

  const form = editing?.form
  const formType = form ? typeOf(form.type) : undefined
  const problems = form ? formProblems(form, formType) : []
  const setForm = (patch: Partial<JobForm>) => setEditing(e => e ? { ...e, form: { ...e.form, ...patch } } : e)
  const platformFor = (t: string) => (t.startsWith('SHOPIFY') ? 'SHOPIFY' : t.startsWith('NETSUITE') ? 'NETSUITE' : null)
  const selectedJob = useMemo(() => (jobs.data ?? []).find(j => j.id === selected) ?? null, [jobs.data, selected])

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-semibold text-[var(--text-primary)]"><CalendarClock className="h-6 w-6" aria-hidden /> Scheduled jobs</h1>
          <p className="mt-1 max-w-2xl text-sm text-[var(--text-secondary)]">
            Pick something Nexus can do, say when, and watch every run: how many rows worked, which did not and why, and the file it produced.
            A new job is switched off until you turn it on.
          </p>
        </div>
        <PermissionGate resource="import" action="create">
          <button className={primary} onClick={() => startEdit()}><Plus className="h-4 w-4" aria-hidden /> New job</button>
        </PermissionGate>
      </header>

      {editing && form && (
        <section className={card} aria-label="Job editor">
          <h2 className="mb-3 text-lg font-semibold text-[var(--text-primary)]">{editing.id ? 'Edit job' : 'New job'}</h2>
          <div className="grid gap-4 md:grid-cols-2">
            <label className="text-sm">
              <span className="mb-1 block font-medium text-[var(--text-secondary)]">Name</span>
              <input className={field} value={form.name} onChange={e => setForm({ name: e.target.value })} placeholder="Nightly stock from Shopify" />
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-[var(--text-secondary)]">What it does</span>
              <select className={field} value={form.type} onChange={e => setForm({ type: e.target.value, params: {} })}>
                <option value="">Choose…</option>
                {(types.data ?? []).map(t => <option key={t.type} value={t.type}>{t.label}</option>)}
              </select>
            </label>
          </div>
          {formType && <p className="mt-2 text-sm text-[var(--text-secondary)]">{formType.description}</p>}

          {formType && formType.params.length > 0 && (
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              {formType.params.map(p => (
                <label key={p.name} className="text-sm">
                  {p.kind === 'BOOLEAN' ? (
                    <span className="flex items-start gap-2">
                      <input type="checkbox" className="mt-1" checked={form.params[p.name] === true}
                        onChange={e => setForm({ params: { ...form.params, [p.name]: e.target.checked } })} />
                      <span><span className="font-medium text-[var(--text-primary)]">{p.label}</span><span className="block text-xs text-[var(--text-tertiary)]">{p.help}</span></span>
                    </span>
                  ) : (
                    <>
                      <span className="mb-1 block font-medium text-[var(--text-secondary)]">{p.label}{p.required ? ' *' : ''}</span>
                      {p.kind === 'STORE' ? (
                        <select className={field} value={String(form.params[p.name] ?? '')} onChange={e => setForm({ params: { ...form.params, [p.name]: e.target.value } })}>
                          <option value="">Choose a connection…</option>
                          {(stores.data ?? []).filter(s => !platformFor(form.type) || s.platform?.toUpperCase() === platformFor(form.type)).map(s =>
                            <option key={s.id} value={s.id}>{s.storeName} ({s.platform})</option>)}
                        </select>
                      ) : (
                        <input className={field} value={String(form.params[p.name] ?? '')} onChange={e => setForm({ params: { ...form.params, [p.name]: e.target.value } })} />
                      )}
                      <span className="mt-1 block text-xs text-[var(--text-tertiary)]">{p.help}</span>
                    </>
                  )}
                </label>
              ))}
            </div>
          )}

          {writesStock(formType, form.params) && (
            <p className="mt-3 flex items-start gap-2 rounded-lg bg-[var(--nexus-warning-50)] p-3 text-sm text-[var(--nexus-warning-700)]" role="note">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              This job will create stock rows in Nexus each time it runs. Do a dry run first (leave "Write the changes" off) and read the result before you schedule it.
            </p>
          )}

          <div className="mt-4 grid gap-4 md:grid-cols-3">
            <label className="text-sm">
              <span className="mb-1 block font-medium text-[var(--text-secondary)]">When</span>
              <select className={field} value={form.schedule.kind} onChange={e => setForm({ schedule: { ...form.schedule, kind: e.target.value as ScheduleKind } })}>
                {SCHEDULE_CHOICES.map(c => <option key={c.kind} value={c.kind}>{c.label}</option>)}
              </select>
            </label>
            {form.schedule.kind === 'EVERY_N_MINUTES' && (
              <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Minutes (at least {MIN_MINUTES})</span>
                <input type="number" min={MIN_MINUTES} className={field} value={form.schedule.minutes} onChange={e => setForm({ schedule: { ...form.schedule, minutes: Number(e.target.value) } })} /></label>
            )}
            {(form.schedule.kind === 'DAILY' || form.schedule.kind === 'WEEKLY') && (
              <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Time</span>
                <input type="time" className={field} value={form.schedule.time} onChange={e => setForm({ schedule: { ...form.schedule, time: e.target.value } })} /></label>
            )}
            {form.schedule.kind === 'WEEKLY' && (
              <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Day</span>
                <select className={field} value={form.schedule.weekday} onChange={e => setForm({ schedule: { ...form.schedule, weekday: Number(e.target.value) } })}>
                  {WEEKDAYS.map((d, i) => <option key={d} value={i}>{d}</option>)}</select></label>
            )}
            {form.schedule.kind === 'CUSTOM' && (
              <label className="text-sm md:col-span-2"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Cron (second minute hour day month weekday)</span>
                <input className={field} value={form.schedule.custom} onChange={e => setForm({ schedule: { ...form.schedule, custom: e.target.value } })} /></label>
            )}
            {form.schedule.kind !== 'MANUAL' && (
              <label className="text-sm"><span className="mb-1 block font-medium text-[var(--text-secondary)]">Time zone</span>
                <input className={field} value={form.timezone} onChange={e => setForm({ timezone: e.target.value })} /></label>
            )}
          </div>

          {problems.length > 0 && (
            <ul className="mt-3 list-disc pl-5 text-sm text-[var(--nexus-danger-700)]">{problems.map(p => <li key={p}>{p}</li>)}</ul>
          )}
          <div className="mt-4 flex gap-2">
            <button className={primary} disabled={problems.length > 0 || saveMut.isPending}
              onClick={() => saveMut.mutate({ id: editing.id, input: { name: form.name.trim(), type: form.type, params: form.params, cron: toCron(form.schedule), timezone: form.timezone } })}>
              {saveMut.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />} Save job
            </button>
            <button className={ghost} onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </section>
      )}

      <section className={card} aria-label="Jobs">
        {jobs.isLoading ? <p className="text-sm text-[var(--text-secondary)]">Loading…</p>
          : (jobs.data ?? []).length === 0 ? (
            <div className="py-8 text-center">
              <p className="font-medium text-[var(--text-primary)]">No jobs yet</p>
              <p className="mx-auto mt-1 max-w-md text-sm text-[var(--text-secondary)]">
                Try "Export stock (CSV)" first: it only reads, runs in seconds, and gives you a file to open.
              </p>
            </div>
          ) : (
            <>
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase text-[var(--text-tertiary)]">
                  <tr><th className="py-2 pr-3">Job</th><th className="pr-3">Schedule</th><th className="pr-3">Next run</th><th className="pr-3">Last run</th><th className="pr-3">On</th><th /></tr>
                </thead>
                <tbody>
                  {(jobs.data ?? []).map(j => (
                    <tr key={j.id} className={`border-t border-[var(--border-subtle)] ${selected === j.id ? 'bg-[var(--interactive-hover)]' : ''}`}>
                      <td className="py-3 pr-3">
                        <button className="text-left font-semibold text-[var(--text-primary)] hover:underline" onClick={() => { setSelected(j.id); setOpenRun(null) }}>{j.name}</button>
                        <div className="text-xs text-[var(--text-tertiary)]">{j.typeLabel}</div>
                      </td>
                      <td className="pr-3 text-[var(--text-secondary)]">{describeSchedule(j.cron, j.timezone)}</td>
                      <td className="pr-3 text-[var(--text-secondary)]">{nextRunText(j)}</td>
                      <td className="pr-3"><StatusBadge status={j.lastStatus} /><div className="text-xs text-[var(--text-tertiary)]">{when(j.lastRunAt)}</div></td>
                      <td className="pr-3">
                        <PermissionGate resource="import" action="update">
                          <input type="checkbox" aria-label={`Switch ${j.name} ${j.enabled ? 'off' : 'on'}`} checked={j.enabled}
                            disabled={enableMut.isPending || (!j.cron && !j.enabled)}
                            title={!j.cron ? 'Give the job a schedule before switching it on' : undefined}
                            onChange={e => enableMut.mutate({ id: j.id, on: e.target.checked })} />
                        </PermissionGate>
                      </td>
                      <td className="whitespace-nowrap py-2 text-right">
                        <PermissionGate resource="import" action="create">
                          <button className={ghost} onClick={() => runMut.mutate(j.id)} disabled={runMut.isPending} aria-label={`Run ${j.name} now`}><Play className="h-4 w-4" aria-hidden /> Run now</button>{' '}
                          <button className={ghost} onClick={() => startEdit(j)} aria-label={`Edit ${j.name}`}><Pencil className="h-4 w-4" aria-hidden /></button>{' '}
                          {confirmDelete === j.id ? (
                            <>
                              <button className={ghost} onClick={() => deleteMut.mutate(j.id)}>Yes, delete</button>{' '}
                              <button className={ghost} onClick={() => setConfirmDelete(null)}>No</button>
                            </>
                          ) : (
                            <button className={ghost} onClick={() => setConfirmDelete(j.id)} aria-label={`Delete ${j.name}`}><Trash2 className="h-4 w-4" aria-hidden /></button>
                          )}
                        </PermissionGate>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <ul className="space-y-3 md:hidden" aria-label="Jobs (list)">
              {(jobs.data ?? []).map(j => (
                <li key={j.id} className={`rounded-xl border border-[var(--border-subtle)] p-3 ${selected === j.id ? 'bg-[var(--interactive-hover)]' : ''}`}>
                  <button className="text-left text-base font-semibold text-[var(--text-primary)]" onClick={() => { setSelected(j.id); setOpenRun(null) }}>{j.name}</button>
                  <div className="text-xs text-[var(--text-tertiary)]">{j.typeLabel}</div>
                  <div className="mt-2 text-sm text-[var(--text-secondary)]">{describeSchedule(j.cron, j.timezone)}</div>
                  <div className="text-sm text-[var(--text-secondary)]">Next: {nextRunText(j)}</div>
                  <div className="mt-1 flex items-center gap-2"><StatusBadge status={j.lastStatus} /><span className="text-xs text-[var(--text-tertiary)]">{when(j.lastRunAt)}</span></div>
                  <PermissionGate resource="import" action="create">
                    <div className="mt-3 flex flex-wrap items-center gap-2">
                      <button className={ghost} onClick={() => runMut.mutate(j.id)} disabled={runMut.isPending} aria-label={`Run ${j.name} now (mobile)`}><Play className="h-4 w-4" aria-hidden /> Run now</button>
                      <button className={ghost} onClick={() => startEdit(j)} aria-label={`Edit ${j.name} (mobile)`}><Pencil className="h-4 w-4" aria-hidden /></button>
                      <label className="ml-auto flex items-center gap-2 text-sm text-[var(--text-secondary)]">
                        On
                        <input type="checkbox" checked={j.enabled} disabled={enableMut.isPending || (!j.cron && !j.enabled)}
                          aria-label={`Switch ${j.name} ${j.enabled ? 'off' : 'on'} (mobile)`}
                          onChange={e => enableMut.mutate({ id: j.id, on: e.target.checked })} />
                      </label>
                    </div>
                  </PermissionGate>
                </li>
              ))}
            </ul>
            </>
          )}
      </section>

      {selectedJob && (
        <section className={card} aria-label="Run history">
          <h2 className="mb-3 text-lg font-semibold text-[var(--text-primary)]">Runs of {selectedJob.name}</h2>
          {(runs.data ?? []).length === 0 ? <p className="text-sm text-[var(--text-secondary)]">No runs yet. Press Run now to try it.</p> : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase text-[var(--text-tertiary)]">
                  <tr><th className="py-2 pr-3">Started</th><th className="pr-3">By</th><th className="pr-3">Result</th><th className="pr-3 text-right">Rows</th><th className="pr-3 text-right">OK</th><th className="pr-3 text-right">Failed</th><th className="pr-3 text-right">To check</th></tr>
                </thead>
                <tbody>
                  {(runs.data ?? []).map((r: JobRun) => (
                    <tr key={r.id} className={`cursor-pointer border-t border-[var(--border-subtle)] hover:bg-[var(--interactive-hover)] ${openRun === r.id ? 'bg-[var(--interactive-hover)]' : ''}`} onClick={() => setOpenRun(r.id)}>
                      <td className="py-2 pr-3">{when(r.startedAt)}</td>
                      <td className="pr-3 text-[var(--text-secondary)]">{r.triggerKind === 'SCHEDULE' ? 'Schedule' : r.triggeredBy ?? 'You'}</td>
                      <td className="pr-3"><StatusBadge status={r.status} /></td>
                      <td className="pr-3 text-right">{r.total}</td><td className="pr-3 text-right">{r.succeeded}</td>
                      <td className="pr-3 text-right">{r.failed}</td><td className="pr-3 text-right">{r.issueCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {openRun && run.data && (
        <section className={card} aria-label="Run detail">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              {run.data.status === 'RUNNING' ? <Loader2 className="h-5 w-5 animate-spin text-[var(--nexus-primary-600)]" aria-hidden />
                : run.data.status === 'FAILED' ? <XCircle className="h-5 w-5 text-[var(--nexus-danger-600)]" aria-hidden />
                : run.data.status === 'PARTIAL' ? <AlertTriangle className="h-5 w-5 text-[var(--nexus-warning-600)]" aria-hidden />
                : <CheckCircle2 className="h-5 w-5 text-[var(--nexus-success-600)]" aria-hidden />}
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">{STATUS_LABEL[run.data.status]}</h2>
            </div>
            <div className="flex gap-2">
              {run.data.hasOutput && (
                <button className={ghost} onClick={() => download(run.data!.id, 'output', run.data!.outputName ?? 'output.csv')}><Download className="h-4 w-4" aria-hidden /> {run.data.outputName ?? 'Download file'}</button>
              )}
              {run.data.issueCount > 0 && (
                <button className={ghost} onClick={() => download(run.data!.id, 'errors', `run-${run.data!.id.slice(0, 8)}-errors.csv`)}><Download className="h-4 w-4" aria-hidden /> Rows to check (CSV)</button>
              )}
            </div>
          </div>
          <p className="mt-2 text-sm text-[var(--text-secondary)]">{run.data.message ?? STATUS_HELP[run.data.status]}</p>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm md:grid-cols-4">
            <div><dt className="text-xs text-[var(--text-tertiary)]">Rows</dt><dd className="font-semibold">{run.data.total}</dd></div>
            <div><dt className="text-xs text-[var(--text-tertiary)]">Worked</dt><dd className="font-semibold">{run.data.succeeded}</dd></div>
            <div><dt className="text-xs text-[var(--text-tertiary)]">Failed</dt><dd className="font-semibold">{run.data.failed}</dd></div>
            <div><dt className="text-xs text-[var(--text-tertiary)]">To check</dt><dd className="font-semibold">{run.data.issueCount}</dd></div>
          </dl>
          {run.data.issueCount > 0 && (
            <div className="mt-4 overflow-x-auto">
              <h3 className="mb-1 text-sm font-semibold text-[var(--text-primary)]">Rows to check{(issues.data?.length ?? 0) < run.data.issueCount ? ` (first ${issues.data?.length ?? 0} of ${run.data.issueCount}; the CSV has all)` : ''}</h3>
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase text-[var(--text-tertiary)]"><tr><th className="py-1 pr-3">Row</th><th className="pr-3">Why</th><th>Data</th></tr></thead>
                <tbody>
                  {(issues.data ?? []).map(i => (
                    <tr key={i.id} className="border-t border-[var(--border-subtle)]"><td className="py-1 pr-3 font-mono text-xs">{i.rowRef}</td><td className="pr-3">{i.reason}</td><td className="font-mono text-xs text-[var(--text-tertiary)]">{i.raw}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <details className="mt-4">
            <summary className="cursor-pointer text-sm font-semibold text-[var(--text-secondary)]">Log</summary>
            <pre className="mt-2 max-h-72 overflow-auto rounded-lg bg-[var(--surface-sunken)] p-3 text-xs">{log.data || 'No log lines.'}</pre>
          </details>
        </section>
      )}
    </div>
  )
}
