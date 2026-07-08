import { useNavigate } from 'react-router-dom'
import { Home, SearchX } from 'lucide-react'

export default function NotFoundPage() {
  const navigate = useNavigate()
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] text-center">
      <div className="w-20 h-20 rounded-2xl bg-gray-100 dark:bg-gray-700 flex items-center justify-center mb-6">
        <SearchX className="w-10 h-10 text-gray-400 dark:text-gray-500" />
      </div>
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">Page Not Found</h1>
      <p className="text-gray-500 dark:text-gray-400 mb-8 max-w-md">
        The page you're looking for doesn't exist or has been moved.
      </p>
      <button onClick={() => navigate('/')} className="enterprise-btn enterprise-btn-primary">
        <Home className="w-4 h-4" /> Go Home
      </button>
    </div>
  )
}
