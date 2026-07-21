import { useNavigate } from 'react-router-dom'
import { Home, SearchX } from 'lucide-react'

export default function NotFoundPage() {
  const navigate = useNavigate()
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] text-center">
      <div className="w-20 h-20 rounded-2xl bg-[var(--surface-muted)] flex items-center justify-center mb-6">
        <SearchX className="w-10 h-10 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
      </div>
      <h1 className="text-2xl font-bold text-[var(--text-primary)] mb-2">Page Not Found</h1>
      <p className="text-[var(--text-secondary)] mb-8 max-w-md">
        The page you're looking for doesn't exist or has been moved.
      </p>
      <button onClick={() => navigate('/')} className="enterprise-btn enterprise-btn-primary">
        <Home className="w-4 h-4" /> Go Home
      </button>
    </div>
  )
}
