import { useState } from 'react'
import { Search, Bell, ChevronDown, User, Settings, LogOut, Sparkles, Command, Menu } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import NotificationsPanel from '../enterprise/NotificationsPanel'
import { clsx } from 'clsx'

interface Props {
  onSearchClick: () => void
  onAiToggle: () => void
  aiPanelOpen: boolean
  onMenuToggle: () => void
}

export default function Topbar({ onSearchClick, onAiToggle, aiPanelOpen, onMenuToggle }: Props) {
  const { user, logout } = useAuth()
  const [showUserMenu, setShowUserMenu] = useState(false)
  const [showNotifications, setShowNotifications] = useState(false)

  return (
    <header className="h-14 bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between px-4 lg:px-6 sticky top-0 z-30 shrink-0">
      <button
        onClick={onMenuToggle}
        className="lg:hidden p-2 mr-2 rounded-lg text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
        aria-label="Toggle sidebar"
      >
        <Menu className="w-5 h-5" />
      </button>
      <button
        onClick={onSearchClick}
        className="flex items-center gap-3 w-full max-w-md h-9 px-3.5 rounded-lg bg-gray-100 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-sm text-gray-400 dark:text-gray-500 hover:border-gray-300 dark:hover:border-gray-600 transition-colors group"
      >
        <Search className="w-4 h-4 shrink-0" />
        <span className="flex-1 text-left">Search anything...</span>
        <kbd className="hidden sm:inline-flex items-center gap-0.5 px-1.5 py-0.5 text-[10px] font-mono bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded">
          <Command className="w-2.5 h-2.5" />K
        </kbd>
      </button>

      <div className="flex items-center gap-2">
        <button
          onClick={onAiToggle}
          className={clsx('flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors',
            aiPanelOpen
              ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-300 border border-primary-200 dark:border-primary-800'
              : 'text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'
          )}
        >
          <Sparkles className="w-4 h-4" />
          <span className="hidden sm:inline">AI Assistant</span>
        </button>

        <div className="relative">
          <button
            onClick={() => setShowNotifications(!showNotifications)}
            className="relative p-2 rounded-lg text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
          >
            <Bell className="w-5 h-5" />
            <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full ring-2 ring-white dark:ring-gray-900" />
          </button>
          <NotificationsPanel open={showNotifications} onClose={() => setShowNotifications(false)} />
        </div>

        <div className="relative">
          <button
            onClick={() => setShowUserMenu(!showUserMenu)}
            className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
          >
            <div className="w-8 h-8 bg-primary-600 rounded-xl flex items-center justify-center text-white text-sm font-medium">
              {user?.fullName?.charAt(0) || 'U'}
            </div>
            <div className="text-left hidden md:block">
              <p className="text-sm font-medium text-gray-900 dark:text-gray-100 leading-tight">{user?.fullName || 'User'}</p>
              <p className="text-xs text-gray-500 dark:text-gray-400">{user?.role || 'Operator'}</p>
            </div>
            <ChevronDown className="w-4 h-4 text-gray-400 dark:text-gray-500" />
          </button>

          {showUserMenu && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setShowUserMenu(false)} />
              <div className="absolute right-0 top-full mt-1 w-56 bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 py-1 z-20 animate-[slideUp_200ms_ease-out]">
                <div className="px-4 py-2.5 border-b border-gray-200 dark:border-gray-700">
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{user?.fullName || 'User'}</p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">{user?.email || ''}</p>
                </div>
                <button className="w-full flex items-center gap-2.5 px-4 py-2.5 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                  <User className="w-4 h-4 text-gray-400" />
                  Profile
                </button>
                <button className="w-full flex items-center gap-2.5 px-4 py-2.5 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                  <Settings className="w-4 h-4 text-gray-400" />
                  Settings
                </button>
                <hr className="my-1 border-gray-200 dark:border-gray-700" />
                <button
                  onClick={logout}
                  className="w-full flex items-center gap-2.5 px-4 py-2.5 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                >
                  <LogOut className="w-4 h-4" />
                  Log out
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
