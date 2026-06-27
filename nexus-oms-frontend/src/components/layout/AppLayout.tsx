import { useState, useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import GlobalSearch from '../enterprise/GlobalSearch'
import AIAssistantPanel from '../enterprise/AIAssistantPanel'
import clsx from 'clsx'
import useKeyboardShortcut from '../../hooks/useKeyboardShortcut'

export default function AppLayout() {
  const [searchOpen, setSearchOpen] = useState(false)
  const [aiPanelOpen, setAiPanelOpen] = useState(false)
  const location = useLocation()

  useKeyboardShortcut('k', () => setSearchOpen(true), { meta: true, ctrl: true })

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [location.pathname])

  useEffect(() => {
    if (window.innerWidth < 1024) setAiPanelOpen(false)
  }, [location.pathname])

  return (
    <div className="flex h-screen overflow-hidden bg-[var(--bg-secondary)]">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        <Topbar onSearchClick={() => setSearchOpen(true)} onAiToggle={() => setAiPanelOpen(!aiPanelOpen)} aiPanelOpen={aiPanelOpen} />
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
