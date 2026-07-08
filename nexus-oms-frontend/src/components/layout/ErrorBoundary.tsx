import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle } from 'lucide-react'

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('ErrorBoundary caught:', error, errorInfo)
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback
      return (
        <div className="flex flex-col items-center justify-center min-h-[60vh] text-center">
          <div className="w-20 h-20 rounded-2xl bg-red-50 dark:bg-red-900/20 flex items-center justify-center mb-6">
            <AlertTriangle className="w-10 h-10 text-red-500 dark:text-red-400" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">Something went wrong</h1>
          <p className="text-gray-500 dark:text-gray-400 mb-2 max-w-md">
            An unexpected error occurred. Please try refreshing the page.
          </p>
          <p className="text-sm text-gray-400 dark:text-gray-500 mb-6 max-w-md font-mono">
            {this.state.error?.message}
          </p>
          <button onClick={() => window.location.reload()} className="enterprise-btn enterprise-btn-primary">
            Refresh Page
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
