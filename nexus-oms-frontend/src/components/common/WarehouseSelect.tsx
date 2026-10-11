import type { Warehouse } from '../../types'

interface Props {
  warehouses: Warehouse[]
  value: string
  onChange: (id: string) => void
  className?: string
}

/** Dropdown of the organisation's warehouses. Renders nothing useful when there are none. */
export default function WarehouseSelect({ warehouses, value, onChange, className }: Props) {
  return (
    <select
      aria-label="Warehouse"
      value={value}
      onChange={e => onChange(e.target.value)}
      disabled={warehouses.length === 0}
      className={className ?? 'rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)] disabled:opacity-60'}
    >
      {warehouses.length === 0 && <option value="">No warehouses</option>}
      {warehouses.map(w => <option key={w.id} value={w.id}>{w.name}{w.code ? ` (${w.code})` : ''}</option>)}
    </select>
  )
}
