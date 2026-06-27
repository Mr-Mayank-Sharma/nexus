import { AlertTriangle, XCircle, CheckCircle } from 'lucide-react'
import { clsx } from 'clsx'

interface Exception {
  id: string
  orderId: string
  type: 'HIGH_PRIORITY' | 'MEDIUM' | 'LOW'
  description: string
  timestamp: string
}

const exceptions: Exception[] = [
  { id: 'EXC-001', orderId: 'ORD-7842', type: 'HIGH_PRIORITY', description: 'Address validation failed - invalid ZIP code', timestamp: '2 min ago' },
  { id: 'EXC-002', orderId: 'ORD-7839', type: 'HIGH_PRIORITY', description: 'Credit card declined - payment pending', timestamp: '5 min ago' },
  { id: 'EXC-003', orderId: 'ORD-7835', type: 'MEDIUM', description: 'Inventory shortage - SKU WH-1002', timestamp: '12 min ago' },
  { id: 'EXC-004', orderId: 'ORD-7830', type: 'LOW', description: 'Carrier API timeout - retrying', timestamp: '18 min ago' },
  { id: 'EXC-005', orderId: 'ORD-7828', type: 'MEDIUM', description: 'Shipping address in hurricane zone', timestamp: '25 min ago' },
]

const priorityStyles = {
  HIGH_PRIORITY: 'bg-red-50 text-red-700 border-red-200',
  MEDIUM: 'bg-yellow-50 text-yellow-700 border-yellow-200',
  LOW: 'bg-blue-50 text-blue-700 border-blue-200',
}

export default function ExceptionQueue() {
  return (
    <div className="card">
      <div className="card-header flex items-center justify-between">
        <div>
          <h3 className="text-sm font-semibold text-gray-900">Exception Queue</h3>
          <p className="text-xs text-gray-500">{exceptions.length} pending exceptions</p>
        </div>
        <button className="btn-ghost text-xs">View all</button>
      </div>
      <div className="divide-y divide-gray-100">
        {exceptions.map((exc) => (
          <div key={exc.id} className="px-6 py-3.5 flex items-start gap-3 hover:bg-gray-50 transition-colors group">
            <div className="flex-shrink-0 mt-0.5">
              {exc.type === 'HIGH_PRIORITY' ? (
                <XCircle className="w-4 h-4 text-red-500" />
              ) : (
                <AlertTriangle className={clsx('w-4 h-4', exc.type === 'MEDIUM' ? 'text-yellow-500' : 'text-blue-500')} />
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className={clsx('badge text-[10px] px-1.5 py-0', priorityStyles[exc.type])}>
                  {exc.type === 'HIGH_PRIORITY' ? 'High' : exc.type === 'MEDIUM' ? 'Med' : 'Low'}
                </span>
                <span className="text-xs text-gray-400">{exc.timestamp}</span>
              </div>
              <p className="text-sm text-gray-700 mt-0.5">{exc.description}</p>
              <p className="text-xs text-gray-400 mt-0.5">Order {exc.orderId}</p>
            </div>
            <button className="btn-ghost text-xs text-primary-600 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
              <CheckCircle className="w-3.5 h-3.5" />
              Resolve
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}
