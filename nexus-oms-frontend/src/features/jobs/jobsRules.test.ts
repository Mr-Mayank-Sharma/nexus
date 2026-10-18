import { describe, expect, it } from 'vitest'
import { defaultSchedule, describeSchedule, formProblems, fromCron, MIN_MINUTES, toCron, writesStock } from './jobsRules'
import type { JobType } from './jobsApi'

const stockJob: JobType = {
  type: 'SHOPIFY_STOCK_BASELINE', label: 'Shopify: bring in current stock', description: '',
  params: [
    { name: 'storeId', label: 'Store connection', kind: 'STORE', required: true, help: '' },
    { name: 'apply', label: 'Write the changes', kind: 'BOOLEAN', required: false, help: '' },
  ],
}

describe('schedule <-> cron', () => {
  it('turns plain choices into six-field cron', () => {
    expect(toCron({ ...defaultSchedule, kind: 'MANUAL' })).toBeNull()
    expect(toCron({ ...defaultSchedule, kind: 'HOURLY' })).toBe('0 0 * * * *')
    expect(toCron({ ...defaultSchedule, kind: 'DAILY', time: '06:30' })).toBe('0 30 6 * * *')
    expect(toCron({ ...defaultSchedule, kind: 'WEEKLY', time: '22:05', weekday: 5 })).toBe('0 5 22 * * 5')
    expect(toCron({ ...defaultSchedule, kind: 'EVERY_N_MINUTES', minutes: 15 })).toBe('0 */15 * * * *')
  })

  it('never produces a schedule faster than the server allows', () => {
    expect(toCron({ ...defaultSchedule, kind: 'EVERY_N_MINUTES', minutes: 1 })).toBe(`0 */${MIN_MINUTES} * * * *`)
    expect(toCron({ ...defaultSchedule, kind: 'EVERY_N_MINUTES', minutes: 0 })).toBe(`0 */${MIN_MINUTES} * * * *`)
  })

  it('reads a stored cron back into the same choice', () => {
    for (const s of [
      { ...defaultSchedule, kind: 'HOURLY' as const },
      { ...defaultSchedule, kind: 'DAILY' as const, time: '06:30' },
      { ...defaultSchedule, kind: 'WEEKLY' as const, time: '22:05', weekday: 5 },
      { ...defaultSchedule, kind: 'EVERY_N_MINUTES' as const, minutes: 10 },
    ]) {
      const back = fromCron(toCron(s))
      expect(back.kind).toBe(s.kind)
      expect(toCron(back)).toBe(toCron(s))
    }
    expect(fromCron(null).kind).toBe('MANUAL')
  })

  it('keeps an unusual cron as custom instead of mangling it', () => {
    const s = fromCron('0 0 6 1 * *')
    expect(s.kind).toBe('CUSTOM')
    expect(toCron(s)).toBe('0 0 6 1 * *')
  })

  it('describes a schedule in a sentence', () => {
    expect(describeSchedule(null, 'UTC')).toBe('Only when you press Run now')
    expect(describeSchedule('0 30 6 * * *', 'Asia/Kolkata')).toBe('Every day at 06:30 (Asia/Kolkata)')
    expect(describeSchedule('0 0 9 * * 1', 'UTC')).toBe('Every Monday at 09:00 (UTC)')
    expect(describeSchedule('0 */15 * * * *', 'UTC')).toBe('Every 15 minutes')
  })
})

describe('formProblems', () => {
  const ok = { name: 'Nightly', type: 'SHOPIFY_STOCK_BASELINE', params: { storeId: 's1' }, schedule: { ...defaultSchedule }, timezone: 'UTC' }

  it('accepts a complete form', () => expect(formProblems(ok, stockJob)).toEqual([]))

  it('names what is missing, in the words of the form', () => {
    const p = formProblems({ ...ok, name: ' ', params: {} }, stockJob)
    expect(p).toContain('Give the job a name.')
    expect(p).toContain('Choose a value for "Store connection".')
  })

  it('requires a type', () => expect(formProblems({ ...ok, type: '' }, undefined)).toContain('Choose what the job does.'))

  it('flags a custom schedule that is not six fields, and one that is too frequent', () => {
    expect(formProblems({ ...ok, schedule: { ...defaultSchedule, kind: 'CUSTOM', custom: '* * * * *' } }, stockJob).join(' ')).toContain('six fields')
    expect(formProblems({ ...ok, schedule: { ...defaultSchedule, kind: 'EVERY_N_MINUTES', minutes: 2 } }, stockJob).join(' ')).toContain('at most every')
  })
})

describe('writesStock', () => {
  it('warns only for a job that can write and has been told to', () => {
    expect(writesStock(stockJob, { apply: true })).toBe(true)
    expect(writesStock(stockJob, { apply: 'true' })).toBe(true)
    expect(writesStock(stockJob, {})).toBe(false)
    expect(writesStock({ ...stockJob, params: [] }, { apply: true })).toBe(false)
    expect(writesStock(undefined, { apply: true })).toBe(false)
  })
})
