import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { PivotedWeek } from './pivotVolumeByWeek'

const CHART_COLORS = [
  'var(--color-chart-iron)',
  'var(--color-chart-brass)',
  'var(--color-chart-olive)',
  'var(--color-chart-copper)',
  'var(--color-chart-gold)',
]

interface VolumeChartProps {
  data: PivotedWeek[]
  groupNames: string[]
}

export function VolumeChart({ data, groupNames }: VolumeChartProps) {
  return (
    <ResponsiveContainer width="100%" height={320}>
      <BarChart data={data}>
        <XAxis dataKey="week" stroke="var(--color-muted)" fontSize={12} />
        <YAxis stroke="var(--color-muted)" fontSize={12} />
        <Tooltip
          contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-line)', color: 'var(--color-ink)' }}
        />
        {groupNames.map((name, index) => (
          <Bar key={name} dataKey={name} stackId="volume" fill={CHART_COLORS[index % CHART_COLORS.length]} />
        ))}
      </BarChart>
    </ResponsiveContainer>
  )
}
