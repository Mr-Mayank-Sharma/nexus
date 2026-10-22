import React from 'react'
import ReactDOM from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './context/AuthContext'
import { ErrorBoundary } from './components/common/ErrorBoundary'
import { ToastProvider } from './components/common/ToastProvider'
import { ThemeProvider } from './context/ThemeContext'
import { useServiceWorker } from './hooks/useServiceWorker'
import UpdateBanner from './components/pwa/UpdateBanner'
import OfflineReadyBanner from './components/pwa/OfflineReadyBanner'
import App from './App'
// The component styles load before Tailwind, so a utility class on a component (e.g. "enterprise-input pl-9")
// wins over the component's default; loaded after, the default silently overrode it.
import './components/enterprise/enterprise.css'
import './index.css'
import './styles/design-tokens.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 30000,
    },
  },
})

function PWAUpdater() {
  const { needRefresh, offlineReady, updateSW, dismissUpdate, dismissOffline } = useServiceWorker()

  return (
    <>
      {needRefresh && <UpdateBanner onUpdate={updateSW} onDismiss={dismissUpdate} />}
      {offlineReady && <OfflineReadyBanner onDismiss={dismissOffline} />}
    </>
  )
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <HashRouter>
      <QueryClientProvider client={queryClient}>
        <ErrorBoundary>
          <ToastProvider>
            <ThemeProvider>
              <AuthProvider>
                <PWAUpdater />
                <App />
              </AuthProvider>
            </ThemeProvider>
          </ToastProvider>
        </ErrorBoundary>
      </QueryClientProvider>
    </HashRouter>
  </React.StrictMode>,
)
