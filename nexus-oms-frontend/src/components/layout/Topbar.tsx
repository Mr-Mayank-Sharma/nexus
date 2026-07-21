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
    <header className="h-14 bg-[var(--surface-base)] border-b border-[var(--border-default)] flex items-center justify-between px-4 lg:px-6 sticky top-0 z-30 shrink-0">
      {/* ── Left: Mobile menu + Search ── */}
      <div className="flex items-center gap-3 flex-1 min-w-0">
        <button
          onClick={onMenuToggle}
          className="lg:hidden p-1.5 -ml-1 rounded-lg text-[var(--text-tertiary)] hover:text-[var(--text-primary)] hover:bg-[var(--interactive-hover)] transition-all duration-150"
          aria-label="Toggle sidebar"
        >
          <Menu className="w-5 h-5" />
        </button>
        <button
          onClick={onSearchClick}
          aria-label="Open search"
          className="flex items-center gap-2.5 w-full max-w-md h-9 px-3 rounded-lg bg-[var(--surface-muted)] border border-[var(--border-subtle)] text-[13px] text-[var(--text-tertiary)] hover:border-[var(--border-default)] hover:bg-[var(--surface-base)] transition-all duration-150 group"
        >
          <Search className="w-4 h-4 shrink-0 text-[var(--text-tertiary)] group-hover:text-[var(--text-secondary)] transition-colors" />
          <span className="flex-1 text-left">Search orders, inventory, customers...</span>
          <kbd className="hidden sm:inline-flex items-center gap-0.5 px-1.5 py-0.5 text-[10px] font-mono bg-[var(--surface-base)] border border-[var(--border-default)] rounded text-[var(--text-tertiary)]">
            <Command className="w-2.5 h-2.5" />K
          </kbd>
        </button>
      </div>

      {/* ── Right: Actions ── */}
      <div className="flex items-center gap-1.5 ml-4">
        {/* AI Assistant */}
        <button
          onClick={onAiToggle}
          aria-label={aiPanelOpen ? 'Close AI assistant' : 'Open AI assistant'}
          aria-pressed={aiPanelOpen}
          className={clsx(
            'p-2 rounded-lg transition-all duration-150',
            aiPanelOpen ? 'bg-[var(--color-primary-100)] text-[var(--color-primary-700)]' : 'text-[var(--text-tertiary)] hover:text-[var(--text-primary)] hover:bg-[var(--interactive-hover)]'
          )}>
          <Sparkles className="w-4 h-4" />
          <span className="hidden sm:inline">AI Assistant</span>
        </button>

        {/* Notifications */}
        <div className="relative">
          <button
            onClick={() => setShowNotifications(!showNotifications)}
            aria-label="Notifications"
            aria-haspopup="true"
            aria-expanded={showNotifications}
            className="relative p-2 rounded-lg text-[var(--text-tertiary)] hover:text-[var(--text-primary)] hover:bg-[var(--interactive-hover)] transition-all duration-150"
          >
            <Bell className="w-[18px] h-[18px]" />
            <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-[var(--nexus-error-500)] rounded-full ring-2 ring-[var(--surface-base)]" />
          </button>
          <NotificationsPanel open={showNotifications} onClose={() => setShowNotifications(false)} />
        </div>

        {/* Divider */}
        <div className="w-px h-5 bg-[var(--border-subtle)] mx-1 hidden md:block" />

        {/* User Menu */}
        <div className="relative">
          <button
            onClick={() => setShowUserMenu(!showUserMenu)}
            aria-label="User menu"
            aria-haspopup="true"
            aria-expanded={showUserMenu}
            className="flex items-center gap-2.5 px-2 py-1.5 rounded-lg hover:bg-[var(--interactive-hover)] transition-all duration-150"
          >
            <div className="w-8 h-8 bg-[var(--nexus-primary-600)] rounded-lg flex items-center justify-center text-white text-sm font-semibold shadow-sm">
              {user?.fullName?.charAt(0) || 'U'}
            </div>
            <div className="text-left hidden md:block">
              <p className="text-[13px] font-medium text-[var(--text-primary)] leading-tight">{user?.fullName || 'User'}</p>
              <p className="text-[11px] text-[var(--text-tertiary)] capitalize">{user?.role?.replace(/_/g, ' ').toLowerCase() || 'Operator'}</p>
            </div>
            <ChevronDown className="w-3.5 h-3.5 text-[var(--text-tertiary)] hidden md:block" />
          </button>

          {showUserMenu && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setShowUserMenu(false)} />
              <div className="absolute right-0 top-full mt-1.5 w-60 bg-[var(--surface-base)] rounded-xl shadow-[var(--elevation-4)] border border-[var(--border-default)] py-1.5 z-20 animate-[nexusSlideUp_200ms_var(--ease-default)]" role="menu" aria-label="User menu">
                {/* User info */}
                <div className="px-4 py-3 border-b border-[var(--border-subtle)]">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-[var(--nexus-primary-600)] rounded-lg flex items-center justify-center text-white text-sm font-semibold">
                      {user?.fullName?.charAt(0) || 'U'}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-[var(--text-primary)]">{user?.fullName || 'User'}</p>
                      <p className="text-xs text-[var(--text-tertiary)]">{user?.email || ''}</p>
                    </div>
                  </div>
                </div>

                {/* Menu items */}
                <div className="py-1.5">
                  <button className="w-full flex items-center gap-2.5 px-4 py-2 text-[13px] font-medium text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--interactive-hover)] transition-colors" role="menuitem">
                    <User className="w-4 h-4 text-[var(--text-tertiary)]" />
                    Profile
                  </button>
                  <button className="w-full flex items-center gap-2.5 px-4 py-2 text-[13px] font-medium text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--interactive-hover)] transition-colors" role="menuitem">
                    <Settings className="w-4 h-4 text-[var(--text-tertiary)]" />
                    Settings
                  </button>
                </div>

                {/* Logout */}
                <div className="border-t border-[var(--border-subtle)] pt-1.5 px-1.5">
                  <button
                    onClick={logout}
                    className="w-full flex items-center gap-2.5 px-3.5 py-2 text-[13px] font-medium text-[var(--nexus-error-600)] hover:bg-[var(--nexus-error-50)] rounded-lg transition-colors"
                    role="menuitem"
                  >
                    <LogOut className="w-4 h-4" />
                    Sign out
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
