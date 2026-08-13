import { Outlet } from 'react-router-dom'
import { BottomNav, OfflineBanner } from './components'

export default function RfLayout() {
  return (
    <div className="min-h-screen bg-[var(--surface-app)]">
      <OfflineBanner />
      <main className="max-w-md mx-auto min-h-screen">
        <Outlet />
      </main>
      <BottomNav />
    </div>
  )
}
