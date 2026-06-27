import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts'
import { OrderVelocity } from '../../types'

interface OrderVelocityChartProps {
  data: OrderVelocity[]
}

export default function OrderVelocityChart({ data }: OrderVelocityChartProps) {
  return (
    <div className="card">
      <div className="card-header">
        <h3 className="text-sm font-semibold text-gray-900">Order Velocity</h3>
        <p className="text-xs text-gray-500">Last 24 hours · hourly volume</p>
      </div>
      <div className="card-body">
        <div className="h-72">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data} margin={{ top: 5, right: 10, left: -10, bottom: 0 }}>
              <defs>
                <linearGradient id="ordersGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#1e40af" stopOpacity={0.15} />
                  <stop offset="95%" stopColor="#1e40af" stopOpacity={0.01} />
                </linearGradient>
                <linearGradient id="fulfilledGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#0f766e" stopOpacity={0.15} />
                  <stop offset="95%" stopColor="#0f766e" stopOpacity={0.01} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
              <XAxis
                dataKey="hour"
                tick={{ fontSize: 11, fill: '#94a3b8' }}
                tickLine={false}
                axisLine={false}
              />
              <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} tickLine={false} axisLine={false} />
              <Tooltip
                contentStyle={{
                  borderRadius: '8px',
                  border: '1px solid #e2e8f0',
                  boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                }}
              />
              <Legend
                wrapperStyle={{ fontSize: '12px', paddingTop: '8px' }}
                iconType="circle"
                iconSize={8}
              />
              <Area
                type="monotone"
                dataKey="orders"
                stroke="#1e40af"
                strokeWidth={2}
                fill="url(#ordersGradient)"
                name="Orders Received"
              />
              <Area
                type="monotone"
                dataKey="fulfilled"
                stroke="#0f766e"
                strokeWidth={2}
                fill="url(#fulfilledGradient)"
                name="Fulfillment Throughput"
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  )
}
