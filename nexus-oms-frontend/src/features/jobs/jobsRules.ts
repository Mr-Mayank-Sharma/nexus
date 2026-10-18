import type { Job, JobType, RunStatus } from './jobsApi'

/**
 * Schedules are stored as a six-field cron (second minute hour day-of-month month day-of-week). People should not
 * have to write one, so the screen offers a few plain choices and turns them into cron, and turns a stored cron
 * back into a sentence.
 */
export type ScheduleKind = 'MANUAL' | 'EVERY_N_MINUTES' | 'HOURLY' | 'DAILY' | 'WEEKLY' | 'CUSTOM'
export interface Schedule { kind: ScheduleKind; minutes: number; time: string; weekday: number; custom: string }

export const MIN_MINUTES = 5
export const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

export const defaultSchedule: Schedule = { kind: 'MANUAL', minutes: 15, time: '06:00', weekday: 1, custom: '0 0 6 * * *' }

function hm(time: string): [number, number] {
  const m = /^(\d{1,2}):(\d{2})$/.exec(time.trim())
  if (!m) return [6, 0]
  return [Math.min(23, Number(m[1])), Math.min(59, Number(m[2]))]
}

export function toCron(s: Schedule): string | null {
  switch (s.kind) {
    case 'MANUAL': return null
    case 'EVERY_N_MINUTES': return `0 */${Math.max(MIN_MINUTES, Math.floor(s.minutes) || MIN_MINUTES)} * * * *`
    case 'HOURLY': return '0 0 * * * *'
    case 'DAILY': { const [h, m] = hm(s.time); return `0 ${m} ${h} * * *` }
    case 'WEEKLY': { const [h, m] = hm(s.time); return `0 ${m} ${h} * * ${s.weekday}` }
    case 'CUSTOM': return s.custom.trim() || null
  }
}

export function fromCron(cron: string | null): Schedule {
  if (!cron) return { ...defaultSchedule }
  const f = cron.trim().split(/\s+/)
  if (f.length === 6) {
    const [sec, min, hour, dom, mon, dow] = f
    if (sec === '0' && /^\*\/\d+$/.test(min) && hour === '*' && dom === '*' && mon === '*' && dow === '*') {
      return { ...defaultSchedule, kind: 'EVERY_N_MINUTES', minutes: Number(min.slice(2)) }
    }
    if (sec === '0' && min === '0' && hour === '*' && dom === '*' && mon === '*' && dow === '*') return { ...defaultSchedule, kind: 'HOURLY' }
    if (sec === '0' && /^\d+$/.test(min) && /^\d+$/.test(hour) && dom === '*' && mon === '*') {
      const time = `${hour.padStart(2, '0')}:${min.padStart(2, '0')}`
      if (dow === '*') return { ...defaultSchedule, kind: 'DAILY', time }
      if (/^[0-6]$/.test(dow)) return { ...defaultSchedule, kind: 'WEEKLY', time, weekday: Number(dow) }
    }
  }
  return { ...defaultSchedule, kind: 'CUSTOM', custom: cron }
}

export function describeSchedule(cron: string | null, timezone: string): string {
  const s = fromCron(cron)
  switch (s.kind) {
    case 'MANUAL': return 'Only when you press Run now'
    case 'EVERY_N_MINUTES': return `Every ${s.minutes} minutes`
    case 'HOURLY': return 'Every hour'
    case 'DAILY': return `Every day at ${s.time} (${timezone})`
    case 'WEEKLY': return `Every ${WEEKDAYS[s.weekday]} at ${s.time} (${timezone})`
    case 'CUSTOM': return `Custom schedule ${cron}`
  }
}

export const STATUS_LABEL: Record<RunStatus, string> = {
  RUNNING: 'Running', COMPLETED: 'Completed', PARTIAL: 'Finished with failures', FAILED: 'Failed',
}

export const STATUS_HELP: Record<RunStatus, string> = {
  RUNNING: 'Still working. Refresh in a moment.',
  COMPLETED: 'Everything was processed.',
  PARTIAL: 'Some rows worked and some did not. Download the errors file for the reason on each row.',
  FAILED: 'The run did not complete. The message and the log say why.',
}

export interface JobForm { name: string; type: string; params: Record<string, unknown>; schedule: Schedule; timezone: string }

/** Everything a person needs to fix before saving, in the words the form uses. */
export function formProblems(form: JobForm, type: JobType | undefined): string[] {
  const out: string[] = []
  if (!form.name.trim()) out.push('Give the job a name.')
  if (!type) { out.push('Choose what the job does.'); return out }
  for (const p of type.params) {
    const v = form.params[p.name]
    if (p.required && (v === undefined || v === null || String(v).trim() === '')) out.push(`Choose a value for "${p.label}".`)
  }
  if (form.schedule.kind === 'EVERY_N_MINUTES' && form.schedule.minutes < MIN_MINUTES) out.push(`A job can run at most every ${MIN_MINUTES} minutes.`)
  if (form.schedule.kind === 'CUSTOM') {
    const fields = form.schedule.custom.trim().split(/\s+/).filter(Boolean)
    if (fields.length !== 6) out.push('A custom schedule needs six fields: second, minute, hour, day of month, month, day of week.')
  }
  return out
}

/** A job that writes stock into Nexus warns before it is scheduled. */
export function writesStock(type: JobType | undefined, params: Record<string, unknown>): boolean {
  return !!type && type.params.some(p => p.name === 'apply') && (params.apply === true || params.apply === 'true')
}

export function nextRunText(job: Job): string {
  if (!job.enabled) return 'Switched off'
  if (!job.nextRunAt) return 'Not scheduled'
  return new Date(job.nextRunAt + (job.nextRunAt.endsWith('Z') ? '' : 'Z')).toLocaleString()
}

export function isTerminal(status: RunStatus): boolean {
  return status !== 'RUNNING'
}
