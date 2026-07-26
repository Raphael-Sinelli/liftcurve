import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export interface ProgressionChartPoint {
  date: string
  value: number
}

interface ProgressionChartProps {
  data: ProgressionChartPoint[]
}

export function ProgressionChart({ data }: ProgressionChartProps) {
  return (
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={data}>
        <XAxis dataKey="date" stroke="var(--color-muted)" fontSize={12} />
        <YAxis stroke="var(--color-muted)" fontSize={12} />
        <Tooltip
          contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-line)', color: 'var(--color-ink)' }}
        />
        <Line type="monotone" dataKey="value" stroke="var(--color-accent)" strokeWidth={2} dot={{ fill: 'var(--color-accent)' }} />
      </LineChart>
    </ResponsiveContainer>
  )
}
