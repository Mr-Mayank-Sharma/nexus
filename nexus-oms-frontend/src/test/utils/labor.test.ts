import { describe, expect, it } from 'vitest'
import { percent, taskEfficiency, taskRate, utilization, weekdayEfficiency } from '../../utils/labor'

describe('weekdayEfficiency', () => {
  it('averages per weekday, Monday first, and leaves empty days null', () => {
    const logs = [
      { loggedAt: '2026-10-12T09:00:00Z', vsStandardPct: 90 },   // Monday
      { loggedAt: '2026-10-12T15:00:00Z', vsStandardPct: 100 },  // Monday
      { loggedAt: '2026-10-18T10:00:00Z', vsStandardPct: 80 },   // Sunday
    ]
    expect(weekdayEfficiency(logs)).toEqual([95, null, null, null, null, null, 80])
  })

  it('treats a timestamp without a zone as UTC and ignores unusable rows', () => {
    const logs = [
      { loggedAt: '2026-10-13T09:00:00', vsStandardPct: 70 },    // Tuesday
      { loggedAt: 'garbage', vsStandardPct: 99 },
      { loggedAt: '2026-10-13T09:00:00Z', vsStandardPct: Number.NaN },
    ]
    expect(weekdayEfficiency(logs)[1]).toBe(70)
    expect(weekdayEfficiency([])).toEqual(Array(7).fill(null))
  })
})

describe('task metrics', () => {
  it('computes efficiency as actual over standard, ignoring rows without a standard', () => {
    const rows = [
      { taskType: 'PACKING', actualRate: 50, standardRate: 50 },
      { taskType: 'PACKING', actualRate: 60, standardRate: 50 },
      { taskType: 'PACKING', actualRate: 10, standardRate: 0 },
      { taskType: 'LOADING', actualRate: 30, standardRate: 40 },
    ]
    expect(taskEfficiency(rows, 'PACKING')).toBe(110)
    expect(taskEfficiency(rows, 'LOADING')).toBe(75)
    expect(taskEfficiency(rows, 'PICKING')).toBeNull()
  })

  it('reads a task rate only when there is one', () => {
    expect(taskRate([{ taskType: 'PICKING', avgItemsPerHour: 141.6 }], 'PICKING')).toBe(142)
    expect(taskRate([{ taskType: 'PICKING', avgItemsPerHour: 0 }], 'PICKING')).toBeNull()
    expect(taskRate([], 'PICKING')).toBeNull()
  })

  it('never yields NaN utilisation and formats missing values as a dash', () => {
    expect(utilization(3, 4)).toBe(75)
    expect(utilization(0, 0)).toBeNull()
    expect(percent(null)).toBe('—')
    expect(percent(88)).toBe('88%')
  })
})
