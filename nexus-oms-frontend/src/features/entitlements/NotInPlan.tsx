import { Lock } from 'lucide-react'
import { Link } from 'react-router-dom'
import { MODULE_LABELS, type ProductModule } from './modules'

/** Shown instead of a page whose module the organisation has not licensed. */
export default function NotInPlan({ module }: { module: ProductModule }) {
  return (
    <div className="mx-auto mt-16 max-w-md rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-8 text-center shadow-sm">
      <div className="mx-auto mb-4 grid h-12 w-12 place-items-center rounded-full bg-[var(--surface-sunken)] text-[var(--text-tertiary)]">
        <Lock className="h-5 w-5" />
      </div>
      <h1 className="text-lg font-semibold text-[var(--text-primary)]">{MODULE_LABELS[module]} isn't in your plan</h1>
      <p className="mt-2 text-sm text-[var(--text-tertiary)]">
        Your organisation hasn't added this module. An administrator can add it to unlock this page.
      </p>
      <Link to="/" className="mt-6 inline-block rounded-lg bg-[var(--nexus-primary-600)] px-4 py-2 text-sm font-semibold text-white hover:bg-[var(--nexus-primary-700)]">
        Back to Launch Pad
      </Link>
    </div>
  )
}
