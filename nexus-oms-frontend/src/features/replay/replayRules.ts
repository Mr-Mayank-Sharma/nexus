import type { ReplayDifference, ReplayMetrics, ReplayResult } from './replayApi'

export const pct = (x: number): string => `${(x * 100).toFixed(1)}%`
export const money = (x: number): string => x.toLocaleString(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 0 })

/** Change from a baseline as a signed percentage; null when the baseline is zero (a percentage would mean nothing). */
export function change(baseline: number, value: number): number | null {
  if (baseline === 0) return null
  return (value - baseline) / baseline
}

export function changeText(baseline: number, value: number): string {
  const c = change(baseline, value)
  if (c === null) return value === baseline ? 'same' : '—'
  if (Math.abs(c) < 0.0005) return 'same'
  return `${c > 0 ? '+' : '−'}${(Math.abs(c) * 100).toFixed(1)}%`
}

/** The sentence to lead with, built only from what was measured. */
export function headline(r: ReplayResult): string {
  const [base, together, aware] = r.strategies
  if (!base || !together || !aware) return ''
  const parts: string[] = []
  if (together.shipments < base.shipments) {
    parts.push(`Keeping each order together instead of splitting it line by line would have cut shipments from ${base.shipments} to ${together.shipments} (${changeText(base.shipments, together.shipments)}) and split orders from ${base.splitOrders} to ${together.splitOrders}.`)
  } else {
    parts.push('On these orders, keeping orders together did not reduce shipments: the nearest-location rule already kept them together.')
  }
  if (r.usedFailureRates) {
    if (aware.expectedFailedUnits < together.expectedFailedUnits) {
      parts.push(`Avoiding the locations that fail picks would lower expected failed picks from ${together.expectedFailedUnits} to ${aware.expectedFailedUnits} units.`)
    } else {
      parts.push('Avoiding unreliable locations made no difference to expected failed picks here.')
    }
  }
  if (aware.totalCost < base.totalCost) {
    parts.push(`Estimated cost moves from ${money(base.totalCost)} to ${money(aware.totalCost)} on the cost model you set.`)
  }
  return parts.join(' ')
}

const cell = (v: unknown): string => {
  let s = v === null || v === undefined ? '' : String(v)
  if (s && '=+-@\t\r'.includes(s[0]) && !/^-?\d+(\.\d+)?$/.test(s)) s = `'${s}`
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}
const line = (cells: unknown[]): string => cells.map(cell).join(',')

export function comparisonCsv(metrics: ReplayMetrics[]): string {
  const rows = [line(['rule', 'orders', 'filled', 'partial', 'unfillable', 'shipments', 'split_orders', 'split_rate', 'shipments_per_order', 'unfilled_units', 'expected_failed_units', 'shipping_cost', 'rework_cost', 'total_cost'])]
  for (const m of metrics) rows.push(line([m.label, m.orders, m.filled, m.partial, m.unfillable, m.shipments, m.splitOrders, m.splitRate, m.shipmentsPerOrder, m.unfilledUnits, m.expectedFailedUnits, m.shippingCost, m.reworkCost, m.totalCost]))
  return rows.join('\r\n') + '\r\n'
}

export function differencesCsv(diffs: ReplayDifference[]): string {
  const rows = [line(['order_id', 'shipments_nearest', 'shipments_failure_aware', 'nearest_from', 'failure_aware_from'])]
  for (const d of diffs) rows.push(line([d.orderId, d.shipmentsBefore, d.shipmentsAfter, d.before, d.after]))
  return rows.join('\r\n') + '\r\n'
}

export function fileFrom(text: string, name: string): File {
  return new File([text], name, { type: 'text/csv' })
}
