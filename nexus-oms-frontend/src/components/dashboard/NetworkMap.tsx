import { MapPin } from 'lucide-react'
import { clsx } from 'clsx'

interface Node {
  id: string
  name: string
  location: string
  status: 'healthy' | 'degraded' | 'down'
  load: number
  ordersProcessing: number
}

const nodes: Node[] = [
  { id: 'NODE-001', name: 'Atlanta FC', location: 'Atlanta, GA', status: 'healthy', load: 72, ordersProcessing: 1243 },
  { id: 'NODE-002', name: 'Los Angeles FC', location: 'Los Angeles, CA', status: 'healthy', load: 88, ordersProcessing: 2156 },
  { id: 'NODE-003', name: 'Chicago FC', location: 'Chicago, IL', status: 'degraded', load: 94, ordersProcessing: 1876 },
  { id: 'NODE-004', name: 'Newark FC', location: 'Newark, NJ', status: 'healthy', load: 65, ordersProcessing: 987 },
  { id: 'NODE-005', name: 'Dallas FC', location: 'Dallas, TX', status: 'down', load: 0, ordersProcessing: 0 },
  { id: 'NODE-006', name: 'Seattle FC', location: 'Seattle, WA', status: 'healthy', load: 55, ordersProcessing: 654 },
]

const statusColors = {
  healthy: 'bg-green-500',
  degraded: 'bg-yellow-500',
  down: 'bg-red-500',
}

const statusBg = {
  healthy: 'bg-green-50 border-green-200',
  degraded: 'bg-yellow-50 border-yellow-200',
  down: 'bg-red-50 border-red-200',
}

export default function NetworkMap() {
  return (
    <div className="card">
      <div className="card-header">
        <h3 className="text-sm font-semibold text-gray-900">Fulfillment Network</h3>
        <p className="text-xs text-gray-500">{nodes.filter((n) => n.status === 'healthy').length} of {nodes.length} nodes online</p>
      </div>
      <div className="card-body space-y-3">
        {nodes.map((node) => (
          <div
            key={node.id}
            className={clsx('flex items-center gap-3 p-3 rounded-lg border transition-colors', statusBg[node.status])}
          >
            <div className="relative flex-shrink-0">
              <MapPin className={clsx('w-5 h-5', node.status === 'down' ? 'text-red-400' : 'text-gray-400')} />
              <span className={clsx('absolute -top-0.5 -right-0.5 w-2.5 h-2.5 rounded-full border-2 border-white', statusColors[node.status])} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium text-gray-900">{node.name}</p>
                <span className={clsx('text-xs font-medium', node.status === 'down' ? 'text-red-600' : node.status === 'degraded' ? 'text-yellow-600' : 'text-green-600')}>
                  {node.status === 'healthy' ? 'Online' : node.status === 'degraded' ? 'Degraded' : 'Offline'}
                </span>
              </div>
              <p className="text-xs text-gray-500">{node.location}</p>
              {node.status !== 'down' && (
                <div className="mt-2 flex items-center gap-3">
                  <div className="flex-1 h-1.5 bg-gray-200 rounded-full overflow-hidden">
                    <div
                      className={clsx('h-full rounded-full', node.load > 80 ? 'bg-yellow-500' : 'bg-green-500')}
                      style={{ width: `${node.load}%` }}
                    />
                  </div>
                  <span className="text-xs text-gray-500">{node.ordersProcessing} orders</span>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
