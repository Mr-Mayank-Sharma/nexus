import { ChevronRight, Home } from 'lucide-react'
import { Link } from 'react-router-dom'
import { clsx } from 'clsx'

interface Crumb {
  label: string
  path?: string
}

interface Props {
  crumbs: Crumb[]
}

export default function EnterpriseBreadcrumbs({ crumbs }: Props) {
  return (
    <nav className="flex items-center gap-1.5 text-sm text-[var(--text-tertiary)] mb-4" aria-label="Breadcrumb">
      <Link to="/" className="hover:text-[var(--text-primary)] transition-colors" aria-label="Home">
        <Home className="w-4 h-4" />
      </Link>
      <ol className="flex items-center gap-1.5 list-none p-0 m-0">
      {crumbs.map((crumb, i) => (
        <li key={i} className="flex items-center gap-1.5">
          <ChevronRight className="w-3.5 h-3.5" aria-hidden="true" />
          {crumb.path && i < crumbs.length - 1 ? (
            <Link to={crumb.path} className="hover:text-[var(--text-primary)] transition-colors">{crumb.label}</Link>
          ) : (
            <span className={clsx(i === crumbs.length - 1 && 'text-[var(--text-primary)] font-medium')} aria-current="page">{crumb.label}</span>
          )}
        </li>
      ))}
      </ol>
    </nav>
  )
}
