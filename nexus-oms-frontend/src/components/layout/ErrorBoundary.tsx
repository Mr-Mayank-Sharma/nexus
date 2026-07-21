import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle, RotateCcw, Home, Copy, ChevronDown, ChevronUp } from 'lucide-react'

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
  errorInfo: ErrorInfo | null
  showDetails: boolean
}

export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null, errorInfo: null, showDetails: false }
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    this.setState({ errorInfo })
    console.error('ErrorBoundary caught:', error, errorInfo)

    try {
      const errorReport = {
        message: error.message,
        stack: error.stack,
        componentStack: errorInfo.componentStack,
        timestamp: new Date().toISOString(),
        url: window.location.href,
        userAgent: navigator.userAgent,
      }
      localStorage.setItem('nexus_last_error', JSON.stringify(errorReport))
    } catch { /* storage may be full */ }
  }

  handleRetry = (): void => {
    this.setState({ hasError: false, error: null, errorInfo: null, showDetails: false })
  }

  handleCopyError = (): void => {
    const { error, errorInfo } = this.state
    const text = [
      `Error: ${error?.message}`,
      `Stack: ${error?.stack}`,
      errorInfo?.componentStack && `Component Stack:${errorInfo.componentStack}`,
      `URL: ${window.location.href}`,
      `Time: ${new Date().toISOString()}`,
    ].filter(Boolean).join('\n\n')

    navigator.clipboard.writeText(text).catch(() => {})
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback

      const { error, errorInfo, showDetails } = this.state

      return (
        <div className="flex flex-col items-center justify-center min-h-[60vh] text-center px-4">
          <div className="w-20 h-20 rounded-2xl bg-red-50 dark:bg-red-900/20 flex items-center justify-center mb-6">
            <AlertTriangle className="w-10 h-10 text-red-500 dark:text-red-400" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">Something went wrong</h1>
          <p className="text-gray-500 dark:text-gray-400 mb-2 max-w-md">
            An unexpected error occurred. You can try again or return to the home page.
          </p>

          {error && (
            <div className="w-full max-w-lg mt-4 mb-6 text-left">
              <div className="bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-800 rounded-lg p-4">
                <p className="text-sm font-mono text-red-700 dark:text-red-300 break-all">{error.message}</p>
              </div>

              <button
                onClick={() => this.setState({ showDetails: !showDetails })}
                className="flex items-center gap-1.5 mt-2 text-xs text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300"
              >
                {showDetails ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                {showDetails ? 'Hide' : 'Show'} details
              </button>

              {showDetails && (
                <pre className="mt-2 p-3 bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg text-xs text-gray-600 dark:text-gray-400 overflow-auto max-h-48 font-mono whitespace-pre-wrap">
                  {error.stack}
                  {errorInfo?.componentStack && `\n\nComponent Stack:${errorInfo.componentStack}`}
                </pre>
              )}
            </div>
          )}

          <div className="flex items-center gap-3">
            <button onClick={this.handleRetry} className="enterprise-btn enterprise-btn-primary gap-2">
              <RotateCcw className="w-4 h-4" /> Try Again
            </button>
            <button onClick={() => window.location.href = '/#/'} className="enterprise-btn enterprise-btn-secondary gap-2">
              <Home className="w-4 h-4" /> Go Home
            </button>
            <button onClick={this.handleCopyError} className="enterprise-btn enterprise-btn-secondary gap-2">
              <Copy className="w-4 h-4" /> Copy Error
            </button>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}
