import { useState, useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import GlobalSearch from '../enterprise/GlobalSearch'
import AIAssistantPanel from '../enterprise/AIAssistantPanel'
import clsx from 'clsx'
import { useKeyboardShortcut } from '../../hooks/useKeyboardShortcut'
import { useWebSocket } from '../../hooks/useWebSocket'
import { useToast } from '../../hooks/useToast'

export default function AppLayout() {
  const [searchOpen, setSearchOpen] = useState(false)
  const [aiPanelOpen, setAiPanelOpen] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const location = useLocation()
  const { toast } = useToast()

  useKeyboardShortcut('k', () => setSearchOpen(true), { meta: true, ctrl: true })

  const handleOrderUpdate = (message: { orderId: string; status: string; message: string }) => {
    toast({
      title: 'Order Updated',
      description: `Order ${message.orderId}: ${message.status}`,
      variant: 'info',
    })
  }

  const handleInventoryAlert = (message: { productId: string; alertType: string; message: string }) => {
    toast({
      title: 'Inventory Alert',
      description: message.message,
      variant: message.alertType === 'LOW_STOCK' ? 'warning' : 'error',
    })
  }

  const handleShipmentUpdate = (message: { shipmentId: string; status: string }) => {
    toast({
      title: 'Shipment Update',
      description: `Shipment ${message.shipmentId}: ${message.status}`,
      variant: 'info',
    })
  }

  const handleSystemAlert = (message: { severity: string; title: string; message: string }) => {
    toast({
      title: message.title,
      description: message.message,
      variant: message.severity === 'CRITICAL' ? 'error' : message.severity === 'WARNING' ? 'warning' : 'info',
    })
  }

  useWebSocket({
    onOrderUpdate: handleOrderUpdate,
    onInventoryAlert: handleInventoryAlert,
    onShipmentUpdate: handleShipmentUpdate,
    onSystemAlert: handleSystemAlert,
    autoConnect: true,
  })

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
