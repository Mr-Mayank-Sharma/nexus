import { useState, useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import GlobalSearch from '../enterprise/GlobalSearch'
import AIAssistantPanel from '../enterprise/AIAssistantPanel'
import clsx from 'clsx'
import { useKeyboardShortcut } from '../../hooks/useKeyboardShortcut'

export default function AppLayout() {
  const [searchOpen, setSearchOpen] = useState(false)
  const [aiPanelOpen, setAiPanelOpen] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const location = useLocation()

  useKeyboardShortcut('k', () => setSearchOpen(true), { meta: true, ctrl: true })

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [location.pathname])

  useEffect(() => {
    if (window.innerWidth < 1024) {
      setAiPanelOpen(false)
      setSidebarOpen(false)
    }
  }, [location.pathname])

  return (
    <div className="flex h-screen overflow-hidden bg-[var(--bg-secondary)]">
      {/* Mobile sidebar backdrop */}
      {sidebarOpen && (
        <div className="fixed inset-0 bg-black/50 z-40 lg:hidden" onClick={() => setSidebarOpen(false)} />
      )}

      {/* Sidebar - desktop static, mobile overlay */}
      <div className={clsx(
        'shrink-0',
        sidebarOpen
          ? 'fixed inset-y-0 left-0 z-50 block'
          : 'hidden',
        'lg:block lg:relative lg:inset-auto lg:z-auto'
      )}>
        <Sidebar />
      </div>

      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        <Topbar
          onSearchClick={() => setSearchOpen(true)}
          onAiToggle={() => setAiPanelOpen(!aiPanelOpen)}
          aiPanelOpen={aiPanelOpen}
          onMenuToggle={() => setSidebarOpen(!sidebarOpen)}
        />
        <main className={clsx(
          'flex-1 overflow-y-auto transition-all duration-300',
          aiPanelOpen ? 'lg:mr-[var(--ai-panel-width)]' : ''
        )}>
          <div className="p-6 lg:p-8 max-w-[1600px] mx-auto w-full">
            <Outlet />
          </div>
        </main>
      </div>

      <GlobalSearch open={searchOpen} onClose={() => setSearchOpen(false)} />
      <AIAssistantPanel open={aiPanelOpen} onClose={() => setAiPanelOpen(false)} />
    </div>
  )
}
