/**
 * Null-safe number/currency formatting utilities.
 *
 * Prevents the `.toFixed()` crash class where API responses contain
 * nullable numeric fields.  Every function guards against null/undefined
 * before calling .toFixed().
 *
 * Usage:
 *   import { fmtMoney, fmtNumber, fmtPercent } from '../utils/format';
 *
 *   <span>{fmtMoney(order.total)}</span>           → "$1,234.56"
 *   <span>{fmtMoney(item.unitPrice, 'EUR')}</span> → "€12.50"
 *   <span>{fmtNumber(count, 0)}</span>              → "1,234"
 *   <span>{fmtPercent(rate)}</span>                 → "65.0%"
 */

const _num = (v: unknown): number => {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
};

/** Format as currency.  Falls back to $0.00 when value is null/NaN. */
export function fmtMoney(value: unknown, currency = '$'): string {
  return `${currency}${_num(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/** Format a number with configurable decimal places.  Falls back to 0. */
export function fmtNumber(value: unknown, decimals = 2): string {
  return _num(value).toLocaleString('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

/** Format as percentage (input is a fraction 0-1).  Falls back to 0.0%. */
export function fmtPercent(value: unknown, decimals = 1): string {
  return `${(_num(value) * 100).toFixed(decimals)}%`;
}

/** Format bytes as human-readable size. */
export function fmtBytes(bytes: unknown): string {
  const b = _num(bytes);
  if (b >= 1048576) return `${(b / 1048576).toFixed(1)} MB`;
  if (b >= 1024) return `${(b / 1024).toFixed(1)} KB`;
  return `${b} B`;
}

/** Format milliseconds as seconds with 1 decimal. */
export function fmtMs(ms: unknown): string {
  return `${(_num(ms) / 1000).toFixed(1)}s`;
}
