import { ReactNode } from 'react'
import { clsx } from 'clsx'

interface Props {
  title: string
  description?: string
  children: ReactNode
  columns?: 1 | 2 | 3
}

export default function EnterpriseFormSection({ title, description, children, columns = 2 }: Props) {
  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-base font-semibold text-[var(--text-primary)]">{title}</h3>
        {description && <p className="text-sm text-[var(--text-secondary)] mt-0.5">{description}</p>}
      </div>
      <div className={clsx('grid gap-4',
        columns === 2 && 'grid-cols-1 md:grid-cols-2',
        columns === 3 && 'grid-cols-1 md:grid-cols-3',
        columns === 1 && 'grid-cols-1'
      )}>
        {children}
      </div>
    </div>
  )
}
