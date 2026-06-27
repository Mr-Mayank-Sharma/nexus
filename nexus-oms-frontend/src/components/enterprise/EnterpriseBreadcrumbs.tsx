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
    <nav className="flex items-center gap-1.5 text-sm text-[var(--text-tertiary)] mb-4">
      <Link to="/" className="hover:text-[var(--text-primary)] transition-colors">
        <Home className="w-4 h-4" />
      </Link>
      {crumbs.map((crumb, i) => (
        <div key={i} className="flex items-center gap-1.5">
          <ChevronRight className="w-3.5 h-3.5" />
          {crumb.path && i < crumbs.length - 1 ? (
            <Link to={crumb.path} className="hover:text-[var(--text-primary)] transition-colors">{crumb.label}</Link>
          ) : (
            <span className={clsx(i === crumbs.length - 1 && 'text-[var(--text-primary)] font-medium')}>{crumb.label}</span>
          )}
        </div>
      ))}
    </nav>
  )
}
