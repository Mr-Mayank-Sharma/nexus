import { Link } from 'react-router-dom'
import { Warehouse } from 'lucide-react'

/** Empty state for warehouse-scoped screens in an organisation that has no warehouse yet. */
export default function NoWarehouses({ what }: { what: string }) {
  return (
    <div className="mx-auto mt-16 max-w-md rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-8 text-center shadow-sm">
      <div className="mx-auto mb-4 grid h-12 w-12 place-items-center rounded-full bg-[var(--surface-sunken)] text-[var(--text-tertiary)]">
        <Warehouse className="h-5 w-5" />
      </div>
      <h2 className="text-lg font-semibold text-[var(--text-primary)]">Add a warehouse first</h2>
      <p className="mt-2 text-sm text-[var(--text-tertiary)]">{what} works per warehouse, and this organisation doesn't have one yet.</p>
      <Link to="/warehouse" className="mt-6 inline-block rounded-lg bg-[var(--nexus-primary-600)] px-4 py-2 text-sm font-semibold text-white hover:bg-[var(--nexus-primary-700)]">
        Go to Warehouse Management
      </Link>
    </div>
  )
}
